package cn.aurange.schedule

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import java.time.Instant

data class IssueUiState(val loaded: Boolean = false, val session: GitHubSession? = null, val repo: GitHubRepo? = null,
                        val repositories: List<GitHubRepo> = emptyList(), val draft: IssueDraft = IssueDraft(),
                        val receipt: IssueReceipt? = null, val busy: Boolean = false, val notice: String = "",
                        val progress: String = "", val userCode: String = "", val verificationUrl: String = "", val activationRequired: Boolean = false,
                        val drafts: List<IssueDraft> = emptyList(), val records: List<IssueRecord> = emptyList(),
                        val historyBusy: Boolean = false, val historyError: String = "", val nextHistoryPage: Int? = 1,
                        val historyChecked: String = "") {
    val hasSavedContent get() = draft.hasContent || drafts.any { it.hasContent }
    fun failed(error: GitHubProblem) = copy(notice = error.message.orEmpty(), activationRequired = error.activationRequired)
}

class IssueCaptureViewModel(application: Application) : AndroidViewModel(application) {
    val store = IssueStore(application)
    private val mutable = MutableStateFlow(IssueUiState())
    val state = mutable.asStateFlow()
    private var running: Job? = null
    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { runCatching {
                val book = store.notebook()
                IssueUiState(true, store.session(), store.repo(), draft = book.active, receipt = store.receipt(), drafts = book.saved)
            } }
            mutable.value = loaded.getOrElse { IssueUiState(loaded = false, notice = "旧草稿读取失败，未覆盖原文件。请先检查手机存储。") }
            if (mutable.value.loaded) updateLibrary()
        }
    }
    private fun updateLibrary() {
        val book = store.notebook(); val state = mutable.value
        val binding = if (state.repo != null && state.session != null) GitHubIssueClient.binding(state.session, state.repo) else ""
        val rows = if (binding.isEmpty()) emptyList() else book.records.filter {
            it.repository.equals(state.repo?.fullName, true) && (it.binding == binding || it.binding.isEmpty())
        }.sortedByDescending { it.created }
        mutable.update { it.copy(drafts = book.saved, records = rows) }
    }
    private fun showDraft(draft: IssueDraft, notice: String = "") {
        mutable.update { it.copy(draft = draft, receipt = null, notice = notice) }; updateLibrary()
    }
    fun newDraft() = job {
        val draft = withContext(Dispatchers.IO) { store.draft(mutable.value.draft, true); store.newDraft() }
        showDraft(draft, "原稿已保存，可以继续记下一条。")
    }
    fun noteFromTask(text: String): Boolean {
        if (!mutable.value.loaded || mutable.value.busy || mutable.value.historyBusy) return false
        job {
            val fresh = withContext(Dispatchers.IO) {
                store.draft(mutable.value.draft, true)
                val next = store.newDraft().copy(body = text, updated = Instant.now().toString())
                store.draft(next, true); next
            }
            showDraft(fresh, "已另存一份草稿，任务来源可以修改或删除。")
        }
        return true
    }
    fun selectDraft(id: String) = job {
        val state = mutable.value
        val target = store.notebook().drafts.single { it.id == id }
        val destination = if (target.binding.isEmpty()) state.repo else store.repoFor(target.binding)
        if (target.binding.isNotEmpty()) {
            check(state.session != null && target.binding.startsWith(state.session.userId + ":")) { "这份草稿属于原账号，请先连接对应账号。" }
            check(destination != null && destination.owner.equals(state.session.login, true)) { "请先在配置中选择这份草稿的仓库 ${target.repository}。" }
        }
        val draft = withContext(Dispatchers.IO) {
            store.draft(state.draft, true)
            destination?.let(store::repo)
            store.selectDraft(id)
        }
        mutable.update { it.copy(repo = destination, historyChecked = "", nextHistoryPage = 1) }
        showDraft(draft)
    }
    fun history(reset: Boolean = true) {
        val state = mutable.value
        if (!state.loaded || state.busy || state.historyBusy || state.repo == null || state.session == null) return
        val page = if (reset) 1 else state.nextHistoryPage ?: return
        val binding = GitHubIssueClient.binding(state.session, state.repo)
        mutable.update { it.copy(historyBusy = true, historyError = "") }
        viewModelScope.launch {
            try {
                val api = client(); val rows = withContext(Dispatchers.IO) { api.issues(state.repo, binding, page) }
                withContext(Dispatchers.IO) { store.mergeRecords(rows.entries) }
                updateLibrary()
                mutable.update { it.copy(nextHistoryPage = rows.nextPage, historyChecked = Instant.now().toString()) }
            } catch (error: Exception) {
                mutable.update { it.copy(historyError = "${error.message?.take(180) ?: "未能刷新记录"} 本机记录仍保留。") }
            } finally { mutable.update { it.copy(historyBusy = false) } }
        }
    }
    private fun job(action: suspend () -> Unit) {
        if (!mutable.value.loaded || mutable.value.busy || mutable.value.historyBusy) return
        mutable.update { it.copy(busy = true, notice = "", progress = "正在处理…", receipt = null) }
        running = viewModelScope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: GitHubProblem) { mutable.update { it.failed(e) } }
            catch (_: org.json.JSONException) { mutable.update { it.copy(notice = "GitHub 返回内容未能读取，请稍后重试；草稿仍保留。") } }
            catch (e: Exception) { mutable.update { it.copy(notice = e.message?.take(240) ?: "操作未完成，草稿仍保留。") } }
            finally { mutable.update { it.copy(busy = false, progress = "", userCode = "", verificationUrl = "") } }
        }
    }
    private suspend fun app(force: Boolean = false): GitHubAppInfo = withContext(Dispatchers.IO) {
        val config = JSONObject(getApplication<Application>().assets.open("github-app.json").bufferedReader().use { it.readText() })
        val configuredId = config.optString("clientId")
        if (configuredId.isNotEmpty()) {
            check(configuredId.matches(Regex("[A-Za-z0-9_.]{4,100}"))) { "GitHub 应用配置无效。" }
            return@withContext GitHubAppInfo(configuredId, config.getString("appSlug")).also {
                mutable.update { state -> state.copy(activationRequired = false) }
            }
        }
        if (!force) store.publicApp()?.let { return@withContext it }
        GitHubIssueClient().app(config.getString("appSlug"), config.getString("appOwner")).also {
            store.publicApp(it); mutable.update { state -> state.copy(activationRequired = false) }
        }
    }
    private suspend fun client(): GitHubIssueClient = withContext(Dispatchers.IO) {
        var session = mutable.value.session ?: throw GitHubProblem("请先连接自己的 GitHub。")
        if (session.expires <= System.currentTimeMillis() / 1000 + 60) {
            if (session.refresh.isEmpty()) throw GitHubProblem("GitHub 授权已失效，请重新连接。")
            val refreshed = GitHubIssueClient().refresh(app().clientId, session.refresh)
            if (refreshed.has("error")) throw GitHubProblem("GitHub 授权已失效，请重新连接；草稿仍保留。")
            session = session.copy(token = refreshed.getString("access_token"), refresh = refreshed.getString("refresh_token"),
                expires = System.currentTimeMillis() / 1000 + refreshed.getLong("expires_in"))
            store.session(session); mutable.update { it.copy(session = session) }
        }
        GitHubIssueClient(token = session.token)
    }
    fun body(text: String) {
        if (!mutable.value.loaded || mutable.value.busy || mutable.value.draft.uncertain) return
        val draft = mutable.value.draft.copy(body = text, updated = Instant.now().toString())
        mutable.update { it.copy(draft = draft, receipt = null, notice = "") }
        try { store.draft(draft); updateLibrary() }
        catch (_: Exception) { mutable.update { it.copy(notice = "草稿未能保存，请保留当前输入，再重试保存。") } }
    }
    fun connect() = job {
        val info = app(true)
        val device = withContext(Dispatchers.IO) { GitHubIssueClient().device(info.clientId) }
        val verification = device.getString("verification_uri")
        check(verification == "https://github.com/login/device") { "GitHub 授权地址异常。" }
        mutable.update { it.copy(userCode = device.getString("user_code"), verificationUrl = verification, progress = "请在 GitHub 输入短码并确认，再返回这里。") }
        val end = SystemClock.elapsedRealtime() + device.getLong("expires_in") * 1000
        var interval = device.getLong("interval").coerceAtLeast(5)
        while (SystemClock.elapsedRealtime() < end) {
            delay(interval * 1000)
            val value = withContext(Dispatchers.IO) { GitHubIssueClient().poll(info.clientId, device.getString("device_code")) }
            when (value.optString("error")) {
                "authorization_pending" -> continue
                "slow_down" -> { interval = maxOf(interval + 5, value.optLong("interval", interval + 5)); continue }
                "access_denied" -> throw GitHubProblem("你取消了 GitHub 授权，草稿仍保留。")
                "expired_token", "token_expired" -> break
                "" -> {
                    val api = GitHubIssueClient(token = value.getString("access_token"))
                    val me = withContext(Dispatchers.IO) { api.me() }
                    val session = GitHubSession(value.getString("access_token"), value.optString("refresh_token"),
                        System.currentTimeMillis() / 1000 + value.optLong("expires_in", 3600), me.getLong("id").toString(), me.getString("login"))
                    val draft = mutable.value.draft
                    if ((mutable.value.drafts + draft).any { it.hasContent && it.binding.isNotEmpty() && !it.binding.startsWith(session.userId + ":") }) throw GitHubProblem("有草稿属于原账号，请先处理草稿后再更换账号。")
                    val target = mutable.value.repo?.takeIf { it.owner.equals(session.login, true) }
                    val boundDraft = if (target == null) draft.copy(binding = "") else draft
                    withContext(Dispatchers.IO) { store.session(session); if (target == null) store.clearRepo(); store.draft(boundDraft, true) }
                    mutable.update { it.copy(session = session, repo = target, draft = boundDraft, repositories = emptyList(), userCode = "", verificationUrl = "", notice = "已连接 ${session.login}，请选择自己的仓库。") }
                    val repos = withContext(Dispatchers.IO) { api.repositories(session.login) }
                    mutable.update { it.copy(repositories = repos) }; updateLibrary(); return@job
                }
                else -> throw GitHubProblem("GitHub 授权暂未完成，请重新连接。")
            }
        }
        throw GitHubProblem("授权码已过期，请重新连接；草稿仍保留。")
    }
    fun cancelConnect() { if (mutable.value.userCode.isNotEmpty()) { running?.cancel(); mutable.update { it.copy(notice = "已停止等待授权。") } } }
    fun repositories() = job {
        val api = client(); val login = mutable.value.session!!.login
        val repos = withContext(Dispatchers.IO) { api.repositories(login) }
        mutable.update { it.copy(repositories = repos, notice = if (repos.isEmpty()) "尚未找到已授权的个人仓库，请在 GitHub 选择仓库授权后刷新。" else "已刷新可用仓库。") }
    }
    fun install(open: (String) -> Unit) = job { open(app().installUrl) }
    fun choose(repo: GitHubRepo) = job {
        val old = mutable.value.repo; val draft = mutable.value.draft; val session = mutable.value.session!!
        if (old?.id != repo.id && draft.hasContent && draft.binding.isNotEmpty()) throw GitHubProblem("请先提交或清空草稿，再更换仓库。")
        check(repo.owner.equals(session.login, true)) { "请选择自己的仓库。" }
        val bound = draft.copy(binding = GitHubIssueClient.binding(session, repo), repository = repo.fullName)
        withContext(Dispatchers.IO) { store.repo(repo); store.draft(bound, true) }
        mutable.update { it.copy(repo = repo, draft = bound, notice = "提交到 ${repo.fullName}", historyChecked = "", nextHistoryPage = 1) }; updateLibrary()
    }
    fun signOut() = job {
        check(!mutable.value.hasSavedContent) { "请先提交或清空全部未提交草稿，再更换账号。" }
        withContext(Dispatchers.IO) { store.signOut(); store.replaceDraft(IssueDraft()) }
        mutable.update { IssueUiState(loaded = true, notice = "已退出本机 GitHub。") }
    }
    fun clear(manualVerified: Boolean = false) = job {
        val old = mutable.value.draft
        check(!old.uncertain || manualVerified) { "请先核对提交结果，再清空草稿。" }
        val fresh = IssueDraft(binding = old.binding, repository = old.repository)
        withContext(Dispatchers.IO) { store.replaceDraft(fresh); old.images.forEach { store.image(it.key).delete() } }
        showDraft(fresh, "已清空当前草稿，其他草稿保留。")
    }
    fun remove(key: String) {
        if (mutable.value.busy || mutable.value.draft.uncertain) return
        val draft = mutable.value.draft.copy(images = mutable.value.draft.images.filterNot { it.key == key }, updated = Instant.now().toString())
        try { store.draft(draft, true) } catch (_: Exception) { mutable.update { it.copy(notice = "图片移除未能保存，请稍后重试。") }; return }
        showDraft(draft)
        viewModelScope.launch(Dispatchers.IO) { store.image(key).delete() }
    }
    fun add(uris: List<Uri>) = job {
        val draft = mutable.value.draft
        check(!draft.uncertain) { "请先核对提交结果，再修改草稿。" }
        val available = 9 - draft.images.size
        val imported = mutableListOf<IssueImage>(); val messages = mutableListOf<String>()
        if (uris.size > available) messages += "每条最多 9 张，已跳过超出的图片。"
        withContext(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            uris.take(available).forEach { uri ->
                val key = UUID.randomUUID().toString(); val file = store.image(key)
                try {
                    val mime = resolver.getType(uri).orEmpty()
                    check(mime in listOf("image/jpeg", "image/png", "image/webp", "image/gif")) { "请使用 JPG、PNG、WebP 或 GIF 图片。" }
                    var name = "图片"
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) name = cursor.getString(0).orEmpty() }
                    resolver.openInputStream(uri).use { input ->
                        checkNotNull(input) { "图片无法读取，请重新选择。" }
                        file.outputStream().use { output ->
                            val buffer = ByteArray(8192); var count = 0L
                            while (true) { val read = input.read(buffer); if (read < 0) break; count += read; check(count <= GitHubIssueClient.MAX_IMAGE) { "单张图片超过 10MB，请换图。" }; output.write(buffer, 0, read) }
                        }
                    }
                    check(file.length() > 0) { "图片为空，请重新选择。" }
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(file.path, bounds)
                    check(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outMimeType in listOf("image/jpeg", "image/png", "image/webp", "image/gif")) { "图片无法读取，请换一张图片。" }
                    imported += IssueImage(key, name, file.length(), bounds.outMimeType)
                } catch (e: Exception) { file.delete(); messages += e.message ?: "图片未能添加，请重选。" }
            }
            val updated = draft.copy(images = draft.images + imported, updated = Instant.now().toString())
            store.draft(updated, true); showDraft(updated, messages.distinct().joinToString("\n"))
        }
    }
    private suspend fun success(receipt: IssueReceipt, draft: IssueDraft) {
        val fresh = IssueDraft(binding = draft.binding, repository = draft.repository)
        withContext(Dispatchers.IO) { store.success(receipt, fresh); draft.images.forEach { store.image(it.key).delete() } }
        mutable.update { it.copy(draft = fresh, receipt = receipt, notice = "已提交 #${receipt.number}") }; updateLibrary()
    }
    fun submit() = job {
        var draft = mutable.value.draft
        GitHubIssueClient.validate(draft)
        val repo = mutable.value.repo ?: throw GitHubProblem("请先选择提交仓库。")
        val api = client(); val session = mutable.value.session!!
        check(draft.binding.isEmpty() || draft.binding == GitHubIssueClient.binding(session, repo)) { "草稿属于其他仓库，请先核对配置。" }
        if (draft.uncertain) {
            mutable.update { it.copy(progress = "正在核对 GitHub 提交结果…") }
            val found = withContext(Dispatchers.IO) { api.find(repo, draft) }
            if (found != null) { success(found, draft); return@job }
            draft = draft.copy(uncertain = false)
            withContext(Dispatchers.IO) { store.draft(draft, true) }
            mutable.update { it.copy(draft = draft, notice = "完整核对后未找到这条记录。你可以再次点击提交。") }; return@job
        }
        draft = draft.copy(binding = GitHubIssueClient.binding(session, repo), repository = repo.fullName)
        withContext(Dispatchers.IO) { store.draft(draft, true) }; mutable.update { it.copy(draft = draft) }
        for ((index, image) in draft.images.withIndex()) {
            mutable.update { it.copy(progress = "正在上传图片 ${index + 1}/${draft.images.size}…") }
            // Verify the deterministic remote path even after a previous attempt returned ambiguously.
            val path = withContext(Dispatchers.IO) { api.upload(repo, draft, image, store.image(image.key).readBytes()) }
            draft = draft.copy(images = draft.images.map { if (it.key == image.key) it.copy(uploaded = path) else it })
            withContext(Dispatchers.IO) { store.draft(draft, true) }; mutable.update { it.copy(draft = draft) }
        }
        draft = draft.copy(uncertain = true)
        withContext(Dispatchers.IO) { store.draft(draft, true) }; mutable.update { it.copy(draft = draft, progress = "正在提交 Issue…") }
        try { val receipt = withContext(Dispatchers.IO) { api.create(repo, draft) }; success(receipt, draft) }
        catch (e: GitHubProblem) {
            if (!e.uncertain) { draft = draft.copy(uncertain = false); withContext(Dispatchers.IO) { store.draft(draft, true) }; mutable.update { it.copy(draft = draft) } }
            throw e
        }
    }
}
