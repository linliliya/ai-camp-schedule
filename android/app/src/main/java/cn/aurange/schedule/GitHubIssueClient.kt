package cn.aurange.schedule

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID

data class GitHubReply(val status: Int, val text: String, val headers: Map<String, String> = emptyMap())
fun interface GitHubTransport { fun send(method: String, url: String, token: String, body: String?): GitHubReply }
class GitHubProblem(message: String, val uncertain: Boolean = false, val activationRequired: Boolean = false) : IOException(message)
data class GitHubAppInfo(val clientId: String, val slug: String) { val installUrl get() = "https://github.com/apps/$slug/installations/new" }
data class GitHubSession(val token: String, val refresh: String, val expires: Long, val userId: String, val login: String)
data class GitHubRepo(val owner: String, val name: String, val id: Long, val branch: String) {
    val fullName get() = "$owner/$name"
    init { require(owner.matches(Regex("[A-Za-z0-9-]{1,39}")) && name.matches(Regex("[A-Za-z0-9_.-]{1,100}")) && name !in listOf(".", "..")) }
}
data class IssueImage(val key: String, val name: String, val size: Long, val mime: String, val uploaded: String = "")
data class IssueDraft(val id: String = UUID.randomUUID().toString(), val body: String = "", val images: List<IssueImage> = emptyList(),
                      val binding: String = "", val uncertain: Boolean = false, val created: String = Instant.now().toString(),
                      val updated: String = created, val repository: String = "") {
    val hasContent get() = body.isNotBlank() || images.isNotEmpty() || uncertain
}
data class IssueReceipt(val number: Int, val url: String)

object GitHubHttps : GitHubTransport {
    override fun send(method: String, url: String, token: String, body: String?): GitHubReply {
        val address = URL(url)
        require(address.protocol == "https" && address.host in setOf("api.github.com", "github.com"))
        val connection = address.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method; connection.instanceFollowRedirects = false
            connection.connectTimeout = 20_000; connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", if (address.host == "github.com") "application/json" else "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "CampSchedule-IssueInbox/${BuildConfig.VERSION_NAME}")
            connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
            if (token.isNotEmpty()) connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true; connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                val result = StringBuilder(); val buffer = CharArray(8192)
                while (true) { val count = reader.read(buffer); if (count < 0) break; result.append(buffer, 0, count); if (result.length > 16_000_000) throw IOException("GitHub response too large") }
                result.toString()
            }.orEmpty()
            val headers = listOf("x-ratelimit-remaining", "x-ratelimit-reset", "retry-after").mapNotNull { name -> connection.getHeaderField(name)?.let { name to it } }.toMap()
            return GitHubReply(status, text, headers)
        } catch (_: IOException) {
            if (address.host == "github.com" && address.path.startsWith("/login/"))
                throw GitHubProblem("无法连接 GitHub 授权服务，请检查网络后重新连接。草稿仍保留。")
            throw GitHubProblem(if (method == "GET") "未能连接 GitHub，请检查网络后重试。" else "连接中断，提交结果需要核对，草稿已保留。", method != "GET")
        } finally { connection.disconnect() }
    }
}

class GitHubIssueClient(private val transport: GitHubTransport = GitHubHttps, private val token: String = "") {
    companion object {
        const val MAX_IMAGE = 10_000_000L
        fun marker(draft: IssueDraft) = "<!-- camp-issue:${draft.id} -->"
        fun title(body: String): String = body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim().removePrefix("# ")
            .codePoints().limit(200).toArray().let { String(it, 0, it.size) }
        fun binding(session: GitHubSession, repo: GitHubRepo) = "${session.userId}:${repo.id}"
        fun validate(draft: IssueDraft) {
            require(title(draft.body).isNotEmpty()) { "请先粘贴文字，只有图片时也请补一句说明。" }
            require(draft.body.length <= 55_000) { "文字较长，请拆分成两条记录。" }
            require(draft.images.size <= 9) { "每条最多添加 9 张图片。" }
            require(draft.images.all { it.size in 1..MAX_IMAGE }) { "单张图片不能超过 10MB。" }
        }
    }
    private fun request(method: String, path: String, body: JSONObject? = null, oauth: Boolean = false): GitHubReply =
        transport.send(method, (if (oauth) "https://github.com" else "https://api.github.com") + path, if (oauth) "" else token, body?.toString())
    private fun accepted(reply: GitHubReply, expected: Set<Int> = setOf(200)): String {
        if (reply.status in expected) return reply.text
        val error = runCatching { JSONObject(reply.text) }.getOrNull()
        if (error?.optString("error") == "device_flow_disabled") throw GitHubProblem("GitHub 应用尚未开启设备授权，请启用“设备流”后重试。", activationRequired = true)
        if (reply.status == 429 || (reply.status == 403 && (reply.headers["x-ratelimit-remaining"] == "0" || error?.optString("message").orEmpty().contains("rate limit", ignoreCase = true))))
            throw GitHubProblem("GitHub 请求已限流，请稍后重新连接。草稿仍保留。")
        throw GitHubProblem(when (reply.status) {
            401 -> "GitHub 授权已失效，请重新连接；草稿仍保留。"
            403, 429 -> "GitHub 暂时拒绝请求，请检查仓库授权或稍后重试。"
            404 -> "无法访问目标仓库，请检查仓库是否已授权。"
            409, 422 -> "GitHub 未接受这次请求，请检查仓库和内容后重试。"
            else -> "GitHub 暂时不可用（${reply.status}），请稍后核对。"
        }, reply.status >= 500)
    }
    fun app(slug: String, owner: String): GitHubAppInfo {
        require(slug.matches(Regex("[a-zA-Z0-9-]+")))
        val reply = request("GET", "/apps/$slug")
        if (reply.status == 404) throw GitHubProblem("GitHub 连接尚待管理员启用，草稿可以先保留。", activationRequired = true)
        val json = JSONObject(accepted(reply))
        check(json.getJSONObject("owner").getString("login").equals(owner, true)) { "GitHub 应用身份不匹配。" }
        val permissions = json.getJSONObject("permissions")
        check(permissions.optString("issues") == "write" && permissions.optString("contents") == "write") { "GitHub 应用尚未开启所需仓库权限。" }
        return GitHubAppInfo(json.getString("client_id"), json.getString("slug"))
    }
    fun device(clientId: String): JSONObject = JSONObject(accepted(request("POST", "/login/device/code", JSONObject().put("client_id", clientId).put("scope", "offline_access"), true)))
    fun poll(clientId: String, deviceCode: String): JSONObject = JSONObject(accepted(request("POST", "/login/oauth/access_token", JSONObject().put("client_id", clientId).put("device_code", deviceCode).put("grant_type", "urn:ietf:params:oauth:grant-type:device_code"), true)))
    fun refresh(clientId: String, refresh: String): JSONObject = JSONObject(accepted(request("POST", "/login/oauth/access_token", JSONObject().put("client_id", clientId).put("refresh_token", refresh).put("grant_type", "refresh_token"), true)))
    fun me(): JSONObject = JSONObject(accepted(request("GET", "/user")))
    fun repositories(login: String): List<GitHubRepo> {
        val result = mutableListOf<GitHubRepo>()
        for (page in 1..100) {
            val installations = JSONObject(accepted(request("GET", "/user/installations?per_page=100&page=$page"))).getJSONArray("installations")
            for (i in 0 until installations.length()) {
                val installation = installations.getJSONObject(i)
                if (!installation.getJSONObject("account").getString("login").equals(login, true)) continue
                for (repoPage in 1..100) {
                    val repos = JSONObject(accepted(request("GET", "/user/installations/${installation.getLong("id")}/repositories?per_page=100&page=$repoPage"))).getJSONArray("repositories")
                    for (j in 0 until repos.length()) {
                        val repo = repos.getJSONObject(j)
                        if (!repo.getJSONObject("owner").getString("login").equals(login, true) || !repo.optBoolean("private") || repo.optBoolean("archived") || !repo.optBoolean("has_issues", true)) continue
                        result += GitHubRepo(login, repo.getString("name"), repo.getLong("id"), repo.optString("default_branch", "main"))
                    }
                    if (repos.length() < 100) break
                    if (repoPage == 100) throw GitHubProblem("仓库数量过多，请缩小 GitHub 应用的授权范围。")
                }
            }
            if (installations.length() < 100) return result.distinctBy { it.id }.sortedBy { it.name.lowercase() }
        }
        throw GitHubProblem("授权安装数量过多，请缩小范围后重试。")
    }
    private fun base(repo: GitHubRepo) = "/repos/${repo.fullName}"
    fun issues(repo: GitHubRepo, binding: String, page: Int = 1): IssuePage {
        require(page in 1..10000)
        val array = JSONArray(accepted(request("GET", "${base(repo)}/issues?state=all&sort=created&direction=desc&per_page=20&page=$page")))
        val entries = (0 until array.length()).map { array.getJSONObject(it) }.filter { !it.has("pull_request") }.map {
            val number = it.getInt("number")
            require(number > 0 && it.getString("html_url") == "https://github.com/${repo.fullName}/issues/$number")
            IssueRecord(number, it.getString("html_url"), it.getString("title"), it.getString("created_at"), repo.fullName, binding, it.getString("state"))
        }
        return IssuePage(entries, if (array.length() == 20) page + 1 else null)
    }
    fun upload(repo: GitHubRepo, draft: IssueDraft, image: IssueImage, bytes: ByteArray): String {
        require(bytes.size.toLong() == image.size && image.size in 1..MAX_IMAGE)
        require(image.key.matches(Regex("[a-zA-Z0-9-]+")) && draft.id.matches(Regex("[a-zA-Z0-9-]+")))
        val extension = when (image.mime) { "image/png" -> "png"; "image/jpeg" -> "jpg"; "image/webp" -> "webp"; "image/gif" -> "gif"; else -> throw IllegalArgumentException("请使用 JPG、PNG、WebP 或 GIF 图片。") }
        val path = "attachments/${draft.id}/${image.key}.$extension"
        val existing = request("GET", "${base(repo)}/contents/$path")
        if (existing.status == 200) {
            val expected = MessageDigest.getInstance("SHA-1").digest("blob ${bytes.size}\u0000".toByteArray(Charsets.UTF_8) + bytes).joinToString("") { "%02x".format(it) }
            check(JSONObject(existing.text).getString("sha") == expected) { "同名附件内容不同，已保留草稿，请重新选图。" }
            return path
        }
        if (existing.status != 404) accepted(existing)
        accepted(request("PUT", "${base(repo)}/contents/$path", JSONObject().put("message", "Add issue attachment ${draft.id}").put("content", Base64.getEncoder().encodeToString(bytes))), setOf(201))
        return path
    }
    fun create(repo: GitHubRepo, draft: IssueDraft): IssueReceipt {
        validate(draft)
        require(draft.images.all { it.uploaded.matches(Regex("attachments/${Regex.escape(draft.id)}/[A-Za-z0-9-]+\\.(png|jpg|webp|gif)")) }) { "还有图片未完成上传。" }
        val images = draft.images.mapIndexed { index, image -> "![图片 ${index + 1}](../blob/${URLEncoder.encode(repo.branch, "UTF-8").replace("+", "%20")}/${image.uploaded}?raw=true)" }.joinToString("\n\n")
        val body = draft.body.trimEnd() + (if (images.isEmpty()) "" else "\n\n$images") + "\n\n" + marker(draft)
        val reply = request("POST", "${base(repo)}/issues", JSONObject().put("title", title(draft.body)).put("body", body))
        val text = accepted(reply, setOf(201))
        return try {
            val json = JSONObject(text); val number = json.getInt("number")
            val url = "https://github.com/${repo.fullName}/issues/$number"
            check(number > 0 && json.getString("html_url") == url)
            IssueReceipt(number, url)
        } catch (_: Exception) { throw GitHubProblem("GitHub 回执未能确认，请先核对提交结果。", true) }
    }
    fun find(repo: GitHubRepo, draft: IssueDraft): IssueReceipt? {
        for (page in 1..100) {
            val array = JSONArray(accepted(request("GET", "${base(repo)}/issues?state=all&sort=created&direction=desc&per_page=100&page=$page")))
            for (i in 0 until array.length()) {
                val issue = array.getJSONObject(i)
                if (!issue.has("pull_request") && issue.optString("body").contains(marker(draft))) {
                    val number = issue.getInt("number"); return IssueReceipt(number, "https://github.com/${repo.fullName}/issues/$number")
                }
            }
            if (array.length() < 100) return null
        }
        throw GitHubProblem("记录较多，尚未完整核对；请到 GitHub 检查，当前不会重发。")
    }
}
