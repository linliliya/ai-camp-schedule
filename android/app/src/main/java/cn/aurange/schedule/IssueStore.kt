package cn.aurange.schedule

import android.content.Context
import android.annotation.SuppressLint
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object IssueCodec {
    fun draft(value: IssueDraft): String = JSONObject().put("id", value.id).put("body", value.body).put("binding", value.binding)
        .put("uncertain", value.uncertain).put("created", value.created).put("updated", value.updated).put("repository", value.repository).put("images", JSONArray(value.images.map {
            JSONObject().put("key", it.key).put("name", it.name).put("size", it.size).put("mime", it.mime).put("uploaded", it.uploaded)
        })).toString()
    fun draft(text: String): IssueDraft {
        val value = JSONObject(text); val images = value.optJSONArray("images") ?: JSONArray()
        return IssueDraft(value.getString("id"), value.optString("body"), (0 until images.length()).map { index -> images.getJSONObject(index).let {
            IssueImage(it.getString("key"), it.getString("name"), it.getLong("size"), it.getString("mime"), it.optString("uploaded"))
        } }, value.optString("binding"), value.optBoolean("uncertain"), value.getString("created"), value.optString("updated", value.getString("created")), value.optString("repository"))
    }
    fun repo(repo: GitHubRepo) = JSONObject().put("owner", repo.owner).put("name", repo.name).put("id", repo.id).put("branch", repo.branch).toString()
    fun repo(text: String): GitHubRepo = JSONObject(text).let { GitHubRepo(it.getString("owner"), it.getString("name"), it.getLong("id"), it.getString("branch")) }
}

@SuppressLint("ApplySharedPref")
class IssueStore(context: Context) {
    private val prefs = context.getSharedPreferences("issue-inbox-private", Context.MODE_PRIVATE)
    val images = File(context.filesDir, "issue-images").apply { mkdirs() }
    private val keyAlias = "camp-issue-github-v1"
    private var cachedNotebook: IssueNotebook? = null
    @Synchronized fun notebook(): IssueNotebook {
        cachedNotebook?.let { return it }
        val raw = prefs.getString("notebook-v2", null)
        val value = if (raw != null) IssueNotebookCodec.decode(raw) else {
            val legacy = prefs.getString("draft", null)?.let(IssueCodec::draft) ?: IssueDraft()
            IssueNotebook.migrate(legacy, receipt(), repo()).also {
                check(prefs.edit().putString("notebook-v2", IssueNotebookCodec.encode(it)).commit()) { "未能迁移草稿，原数据已保留。" }
            }
        }
        cachedNotebook = value
        return value
    }
    @Synchronized private fun saveNotebook(value: IssueNotebook, durable: Boolean = true) {
        val edit = prefs.edit().putString("notebook-v2", IssueNotebookCodec.encode(value))
        if (durable) check(edit.commit()) { "草稿未能保存，请检查手机存储。" } else edit.apply()
        cachedNotebook = value
    }
    @Synchronized fun newDraft(): IssueDraft = notebook().fresh().also { saveNotebook(it) }.active
    @Synchronized fun selectDraft(id: String): IssueDraft = notebook().select(id).also { saveNotebook(it) }.active
    @Synchronized fun replaceDraft(value: IssueDraft) = saveNotebook(notebook().replace(value))
    @Synchronized fun mergeRecords(records: List<IssueRecord>) = saveNotebook(notebook().merge(records))
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun session(): GitHubSession? = runCatching {
        val parts = (prefs.getString("auth", null) ?: return null).split(':')
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))) }
        val json = JSONObject(String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8))
        GitHubSession(json.getString("token"), json.optString("refresh"), json.getLong("expires"), json.getString("userId"), json.getString("login"))
    }.getOrNull()
    @Synchronized fun session(value: GitHubSession) {
        val json = JSONObject().put("token", value.token).put("refresh", value.refresh).put("expires", value.expires).put("userId", value.userId).put("login", value.login)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val bytes = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val blob = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(bytes, Base64.NO_WRAP)
        check(prefs.edit().putString("auth", blob).commit()) { "未能保存 GitHub 授权，请重试。" }
    }
    fun repo(): GitHubRepo? = prefs.getString("repo", null)?.let { runCatching { IssueCodec.repo(it) }.getOrNull() }
    fun repo(value: GitHubRepo) { check(prefs.edit().putString("repo", IssueCodec.repo(value)).putString("repo-${value.id}", IssueCodec.repo(value)).commit()) { "未能保存目标仓库。" } }
    fun repoFor(binding: String): GitHubRepo? {
        val id = binding.substringAfter(':', "").toLongOrNull() ?: return null
        repo()?.takeIf { it.id == id }?.let { return it }
        return prefs.getString("repo-$id", null)?.let { runCatching { IssueCodec.repo(it) }.getOrNull() }
    }
    fun clearRepo() { check(prefs.edit().remove("repo").remove("receipt").commit()) { "未能重置仓库配置。" } }
    @Synchronized fun draft(): IssueDraft = notebook().active
    @Synchronized fun draft(value: IssueDraft, durable: Boolean = false) = saveNotebook(notebook().save(value), durable)
    fun receipt(): IssueReceipt? = prefs.getString("receipt", null)?.let { runCatching { JSONObject(it).let { v -> IssueReceipt(v.getInt("number"), v.getString("url")) } }.getOrNull() }
    @Synchronized fun success(value: IssueReceipt, fresh: IssueDraft) {
        val old = notebook().active
        val target = repo() ?: throw IllegalStateException("请核对目标仓库。")
        val row = IssueRecord(value.number, value.url, GitHubIssueClient.title(old.body), java.time.Instant.now().toString(), target.fullName, old.binding)
        val next = notebook().replace(fresh).merge(listOf(row))
        check(prefs.edit().putString("receipt", JSONObject().put("number", value.number).put("url", value.url).toString())
            .putString("notebook-v2", IssueNotebookCodec.encode(next)).commit()) { "提交成功，但回执未能保存。请到 GitHub 核对。" }
        cachedNotebook = next
    }
    fun publicApp(): GitHubAppInfo? = prefs.getString("app", null)?.let { runCatching { JSONObject(it).let { v -> GitHubAppInfo(v.getString("id"), v.getString("slug")) } }.getOrNull() }
    fun publicApp(value: GitHubAppInfo) { prefs.edit().putString("app", JSONObject().put("id", value.clientId).put("slug", value.slug).toString()).apply() }
    fun signOut() { check(prefs.edit().remove("auth").remove("repo").remove("receipt").commit()) { "未能退出，请重试。" } }
    fun image(key: String): File { require(key.matches(Regex("[A-Za-z0-9-]+"))); return File(images, key) }
}
