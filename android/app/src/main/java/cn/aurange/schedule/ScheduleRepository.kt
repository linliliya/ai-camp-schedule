package cn.aurange.schedule

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class ScheduleRepository internal constructor(context: Context,
    private val transport: ((String, JSONObject, String?) -> JSONObject)? = null) {
    val store = LocalStore(context.applicationContext)
    val sop: SopData = context.assets.open("schedule.json").bufferedReader().use { NativeJson.sop(it.readText()) }
    val engine = ScheduleEngine(sop)
    private val mutex = Mutex()

    private fun request(path: String, body: JSONObject, token: String? = null): JSONObject {
        transport?.let { return it(path, body, token) }
        val connection = URL(sop.url + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000; connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("apikey", sop.publicKey)
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
            if (status !in 200..299) throw CloudFailure(status, json.optString("code", json.optString("error_code")),
                json.optString("message", json.optString("msg", json.optString("error_description", "云端连接暂时不可用。"))))
            return json
        } finally { connection.disconnect() }
    }

    private fun parseSession(json: JSONObject): Session {
        val user = json.getJSONObject("user")
        return Session(json.getString("access_token"), json.getString("refresh_token"),
            json.optLong("expires_at", System.currentTimeMillis() / 1000 + json.optLong("expires_in", 3600)),
            user.optString("email"), user.getString("id"))
    }
    private fun validSession(): Session {
        val session = store.session() ?: throw LoginExpired()
        if (session.expiresAt > System.currentTimeMillis() / 1000 + 90) return session
        val refreshed = try {
            parseSession(request("/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", session.refreshToken)))
        } catch (e: CloudFailure) {
            if (e.status in listOf(400, 401, 403)) throw LoginExpired() else throw e
        }
        store.session(refreshed)
        return refreshed
    }
    private fun rpc(name: String, body: JSONObject = JSONObject()): JSONObject {
        val session = validSession()
        return try { request("/rest/v1/rpc/$name", body, session.accessToken) }
        catch (e: CloudFailure) {
            when {
                e.status == 401 -> throw LoginExpired()
                e.status == 403 || e.code == "42501" -> throw AccessDenied("这个账号没有排期访问权限，请联系管理员。")
                else -> throw e
            }
        }
    }
    private fun checkAccess() {
        val access = rpc("my_app_access")
        val apps = access.optJSONArray("apps")
        if (!access.optBoolean("allowed") || apps == null || (0 until apps.length()).none { apps.optString(it) == "ai" }) {
            throw AccessDenied("这个账号没有 AI 训练营的访问权限，请联系管理员。")
        }
    }
    private fun refreshLocked(): Snapshot {
        try {
            checkAccess()
            val snapshot = NativeJson.snapshot(rpc("schedule_snapshot", JSONObject().put("p_program", "ai")).toString(), System.currentTimeMillis())
            store.snapshot(snapshot)
            return snapshot
        } catch (e: Exception) {
            if (e is AccessDenied || e is LoginExpired) store.clearAccount()
            throw e
        }
    }
    suspend fun signIn(email: String, password: String): Snapshot = withContext(Dispatchers.IO) {
        mutex.withLock {
            val session = parseSession(request("/auth/v1/token?grant_type=password", JSONObject().put("email", email.trim()).put("password", password)))
            store.clearAccount(); store.session(session)
            refreshLocked()
        }
    }
    suspend fun refresh(): Snapshot = withContext(Dispatchers.IO) { mutex.withLock { refreshLocked() } }
    suspend fun signOut() = withContext(Dispatchers.IO) { mutex.withLock { store.clearAccount() } }
    suspend fun createCamp(draft: CampDraft, original: Camp? = null): Snapshot = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                checkAccess()
                draft.error(store.snapshot().camps, original)?.let { throw IllegalStateException(it) }
                val parameters = JSONObject().put("p_program", "ai")
                    .put("p_id", original?.id ?: JSONObject.NULL).put("p_version", original?.version ?: 0)
                    .put("p_term", draft.term).put("p_open_date", draft.openDate.toString())
                    .put("p_skips", JSONArray(draft.skips.sorted()))
                if (draft.examWeek != 7 || (original?.examWeek ?: 7) != 7) parameters.put("p_exam_week", draft.examWeek)
                val saved = rpc("save_schedule_camp", parameters)
                val camp = NativeJson.snapshot(JSONObject().put("camps", JSONArray().put(saved))
                    .put("tasks", JSONArray()).toString()).camps.single()
                // Cache the acknowledged save directly, even if a later refresh fails.
                val cached = store.snapshot()
                cached.copy(camps = cached.camps.filterNot { it.id == camp.id } + camp).also(store::snapshot)
            } catch (e: Exception) {
                if (e is AccessDenied || e is LoginExpired) store.clearAccount()
                if (e is CloudFailure && e.code == "P0001") {
                    throw IllegalStateException("这期营期已被其他人修改，请刷新排期，再重新打开编辑。", e)
                }
                if (e is CloudFailure && e.code == "PGRST202" && draft.examWeek != 7) {
                    throw IllegalStateException("团队数据库尚未启用自选考试周，请联系管理员更新。", e)
                }
                if (e is CloudFailure && e.code == "23505") {
                    throw IllegalStateException("第 ${draft.term} 期已存在，请刷新排期后换一个期数。")
                }
                if (e is java.io.IOException) throw IllegalStateException("未能确认营期是否保存，请联网刷新排期、核对期数后再试。", e)
                throw e
            }
        }
    }
    suspend fun complete(task: TaskOccurrence, completed: Boolean, expectedVersion: Int): Snapshot = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                checkAccess()
                val confirmed = rpc("set_schedule_task", JSONObject().put("p_program", "ai").put("p_camp_id", task.camp.id)
                    .put("p_task_key", task.key).put("p_version", expectedVersion).put("p_completed", completed))
                // The RPC acknowledges this version; persist it for alarms without another full refresh.
                val old = store.snapshot()
                store.snapshot(old.copy(tasks = old.tasks + (task.id to TaskState(completed, confirmed.getInt("version")))))
                if (completed) store.custom(task.id, null)
                store.snapshot()
            } catch (e: Exception) {
                if (e is AccessDenied || e is LoginExpired) store.clearAccount()
                if (e is CloudFailure && (e.code == "P0001" || e.message.orEmpty().contains("schedule_conflict"))) {
                    refreshLocked()
                    throw IllegalStateException("这条任务已被其他人修改，已刷新排期，请核对后重试。")
                }
                throw e
            }
        }
    }
    companion object {
        @Volatile private var instance: ScheduleRepository? = null
        fun get(context: Context): ScheduleRepository = instance ?: synchronized(this) {
            instance ?: ScheduleRepository(context.applicationContext).also { instance = it }
        }
    }
}

fun friendlyError(error: Throwable): String = when (error) {
    is AccessDenied, is LoginExpired -> error.message.orEmpty()
    is CloudFailure -> when {
        error.message.orEmpty().contains("Invalid login", ignoreCase = true) -> "邮箱或密码不正确。"
        error.message.orEmpty().contains("confirm", ignoreCase = true) -> "账号需要验证，请联系管理员。"
        error.status == 429 -> "操作频繁，请稍后再试。"
        error.status >= 500 -> "云端暂时不可用，请稍后重试。"
        else -> "登录或同步未完成，请检查账号权限后重试。"
    }
    is java.io.IOException -> "暂时无法连接云端，正在显示上次同步的排期。"
    is IllegalStateException -> error.message ?: "操作未完成，请重试。"
    else -> "操作未完成，请稍后重试。"
}
