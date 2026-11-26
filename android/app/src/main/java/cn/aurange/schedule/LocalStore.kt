package cn.aurange.schedule

import android.content.Context
import android.annotation.SuppressLint
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.time.LocalTime
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Durable session/alarm writes happen on IO workers and must finish before the receiver exits.
@SuppressLint("ApplySharedPref")
class LocalStore(context: Context) {
    companion object {
        // AtomicFile does not lock readers against writers. All stores for this process share the same cache path.
        private val snapshotLock = Any()
    }
    private val prefs = context.getSharedPreferences("schedule-private", Context.MODE_PRIVATE)
    private val cache = AtomicFile(File(context.filesDir, "snapshot.json"))
    private val keyAlias = "camp-schedule-session-v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    @Synchronized fun session(): Session? = runCatching {
        val blob = prefs.getString("session", null) ?: return null
        val parts = blob.split(':')
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        val json = JSONObject(String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8))
        Session(json.getString("access"), json.getString("refresh"), json.getLong("expires"), json.getString("email"), json.getString("user"))
    }.getOrNull()

    @Synchronized fun session(session: Session) {
        val text = JSONObject().put("access", session.accessToken).put("refresh", session.refreshToken)
            .put("expires", session.expiresAt).put("email", session.email).put("user", session.userId).toString()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        val blob = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
        check(prefs.edit().putString("session", blob).commit()) { "无法保存本机登录状态。" }
    }

    @Synchronized fun snapshot(): Snapshot = synchronized(snapshotLock) {
        runCatching {
            if (session() == null) return Snapshot()
            NativeJson.snapshot(cache.openRead().bufferedReader().use { it.readText() })
        }.getOrDefault(Snapshot())
    }

    @Synchronized fun snapshot(snapshot: Snapshot) = synchronized(snapshotLock) {
        val stream = cache.startWrite()
        try { stream.write(NativeJson.snapshot(snapshot).toByteArray(Charsets.UTF_8)); cache.finishWrite(stream) }
        catch (e: Exception) { cache.failWrite(stream); throw e }
    }

    @Synchronized fun clearAccount() = synchronized(snapshotLock) {
        prefs.edit().remove("session").remove("custom").remove("delivered").remove("batch").putBoolean("demo", false).commit()
        cache.delete()
    }
    fun demo(): Boolean = prefs.getBoolean("demo", false)
    fun demo(value: Boolean) { prefs.edit().putBoolean("demo", value).apply() }

    fun settings(): ReminderSettings = ReminderSettings(prefs.getBoolean("enabled", true),
        runCatching { LocalTime.parse(prefs.getString("summary", "09:00")) }.getOrDefault(LocalTime.of(9, 0)),
        prefs.getInt("lead", 15).coerceIn(0, 1440))
    fun settings(value: ReminderSettings) {
        prefs.edit().putBoolean("enabled", value.enabled).putString("summary", value.summaryTime.toString())
            .putInt("lead", value.leadMinutes).apply()
    }
    @Synchronized fun custom(): Map<String, Long> = runCatching {
        val json = JSONObject(prefs.getString("custom", "{}")!!)
        json.keys().asSequence().associateWith { json.getLong(it) }
    }.getOrDefault(emptyMap())
    @Synchronized fun custom(taskId: String, at: Long?) {
        val map = custom().toMutableMap()
        if (at == null) map.remove(taskId) else map[taskId] = at
        prefs.edit().putString("custom", JSONObject(map as Map<*, *>).toString()).commit()
    }
    @Synchronized fun delivered(): Map<String, Long> = runCatching {
        val json = JSONObject(prefs.getString("delivered", "{}")!!)
        json.keys().asSequence().associateWith { json.getLong(it) }
    }.getOrDefault(emptyMap())
    @Synchronized fun markDelivered(events: List<ReminderEvent>, now: Long) {
        val recent = delivered().filterValues { it > now - 14L * 86400_000 }.toMutableMap()
        events.forEach { recent[it.id] = now }
        prefs.edit().putString("delivered", JSONObject(recent as Map<*, *>).toString()).commit()
    }
    @Synchronized fun batch(value: AlarmBatch?) {
        if (value == null) { prefs.edit().remove("batch").commit(); return }
        val json = JSONObject().put("at", value.at).put("events", org.json.JSONArray(value.events.map { event ->
            JSONObject().put("id", event.id).put("at", event.at).put("taskId", event.taskId).put("summary", event.summary)
        }))
        prefs.edit().putString("batch", json.toString()).commit()
    }
    @Synchronized fun batch(): AlarmBatch? = runCatching {
        val json = JSONObject(prefs.getString("batch", null) ?: return null)
        AlarmBatch(json.getLong("at"), json.getJSONArray("events").objects().map {
            ReminderEvent(it.getString("id"), it.getLong("at"), it.getString("taskId"), it.getBoolean("summary"))
        })
    }.getOrNull()
}
