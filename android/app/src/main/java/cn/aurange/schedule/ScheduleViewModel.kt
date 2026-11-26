package cn.aurange.schedule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

data class AppState(
    val snapshot: Snapshot = Snapshot(), val email: String = "", val loggedIn: Boolean = false,
    val busy: Boolean = false, val online: Boolean = false, val demo: Boolean = false,
    val message: String = "", val settings: ReminderSettings = ReminderSettings(),
    val custom: Map<String, Long> = emptyMap(), val today: LocalDate = LocalDate.now(BUSINESS_ZONE),
    val pendingCompletions: Map<String, Boolean> = emptyMap(), val createdCampId: String? = null,
    val campError: String = "", val savedCampRevision: Int = 0
) {
    val displaySnapshot: Snapshot get() = if (pendingCompletions.isEmpty()) snapshot else snapshot.copy(
        tasks = snapshot.tasks + pendingCompletions.mapValues { (id, completed) -> TaskState(completed, snapshot.tasks[id]?.version ?: 0) })
}

class ScheduleViewModel @JvmOverloads constructor(application: Application,
    val repo: ScheduleRepository = ScheduleRepository.get(application),
    private val backgroundSyncEnabled: Boolean = true) : AndroidViewModel(application) {
    val engine get() = repo.engine
    private val mutableState = MutableStateFlow(AppState())
    val state = mutableState.asStateFlow()
    private var active: Job? = null
    private val completionJobs = mutableMapOf<String, Job>()
    private val alarmMutex = Mutex()
    private var accountGeneration = 0

    private suspend fun updateReminders(cancel: Boolean = false) = withContext(Dispatchers.IO) {
        alarmMutex.withLock {
            if (cancel) ReminderScheduler(getApplication()).cancel() else ReminderScheduler(getApplication()).rebuild()
        }
    }

    private fun invalidateCompletions() {
        accountGeneration++
        completionJobs.values.forEach { it.cancel() }
        completionJobs.clear()
        mutableState.value = mutableState.value.copy(pendingCompletions = emptyMap())
    }

    private fun loseAccount(message: String = "") {
        invalidateCompletions()
        mutableState.value = AppState(settings = mutableState.value.settings, message = message)
        if (backgroundSyncEnabled) SyncWorker.disable(getApplication())
        viewModelScope.launch { updateReminders(cancel = true) }
    }

    init {
        val session = repo.store.session()
        mutableState.value = AppState(repo.store.snapshot(), session?.email.orEmpty(), session != null,
            settings = repo.store.settings(), custom = repo.store.custom())
        if (repo.store.demo() && session == null) demo() else if (session != null) refresh()
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                val date = LocalDate.now(BUSINESS_ZONE)
                if (date != mutableState.value.today) mutableState.value = mutableState.value.copy(today = date)
            }
        }
    }
    private fun run(onSuccess: (Snapshot) -> Unit = {}, onFailure: (Exception) -> Unit = {}, block: suspend () -> Snapshot) {
        if (active?.isActive == true) return
        mutableState.value = mutableState.value.copy(busy = true, message = "")
        active = viewModelScope.launch {
            try {
                val snapshot = block()
                val session = repo.store.session()
                mutableState.value = mutableState.value.copy(snapshot = snapshot, loggedIn = session != null,
                    email = session?.email.orEmpty(), online = true, demo = false, custom = repo.store.custom())
                onSuccess(snapshot)
                if (backgroundSyncEnabled) SyncWorker.enable(getApplication())
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                val session = repo.store.session()
                mutableState.value = mutableState.value.copy(snapshot = repo.store.snapshot(), loggedIn = session != null,
                    email = session?.email.orEmpty(), online = false, message = friendlyError(error), custom = repo.store.custom())
                onFailure(error)
            } finally {
                mutableState.value = mutableState.value.copy(busy = false)
                updateReminders()
            }
        }
    }
    fun signIn(email: String, password: String) {
        if (active?.isActive == true) return
        invalidateCompletions()
        run { repo.signIn(email, password) }
    }
    fun refresh() {
        val date = LocalDate.now(BUSINESS_ZONE)
        if (mutableState.value.today != date) mutableState.value = mutableState.value.copy(today = date)
        if (!mutableState.value.demo && mutableState.value.loggedIn && mutableState.value.pendingCompletions.isEmpty()) run { repo.refresh() }
    }
    fun createCamp(draft: CampDraft) = saveCamp(draft)

    fun saveCamp(draft: CampDraft, original: Camp? = null) {
        val current = mutableState.value
        if (current.demo || !current.loggedIn || !current.online || current.busy) return
        draft.error(current.snapshot.camps, original)?.let { message(it); return }
        mutableState.value = current.copy(campError = "")
        run(onSuccess = { snapshot ->
            mutableState.value = mutableState.value.copy(createdCampId = snapshot.camps.first { it.term == draft.term }.id,
                campError = "", savedCampRevision = mutableState.value.savedCampRevision + 1,
                message = "第 ${draft.term} 期已保存，日程和提醒已更新。")
        }, onFailure = { error ->
            mutableState.value = mutableState.value.copy(campError = friendlyError(error),
                online = error is IllegalStateException && error.cause !is java.io.IOException)
        }) { repo.createCamp(draft, original) }
    }
    fun complete(task: TaskOccurrence) {
        val state = mutableState.value
        if (!state.online || state.demo || state.busy || task.id in state.pendingCompletions) return
        val completed = !engine.complete(state.displaySnapshot, task)
        val version = state.snapshot.tasks[task.id]?.version ?: 0
        val generation = accountGeneration
        mutableState.value = state.copy(pendingCompletions = state.pendingCompletions + (task.id to completed), message = "")
        completionJobs[task.id] = viewModelScope.launch {
            try {
                val confirmed = repo.complete(task, completed, version)
                val (session, custom) = withContext(Dispatchers.IO) { repo.store.session() to repo.store.custom() }
                if (generation == accountGeneration) {
                    if (session == null) loseAccount()
                    else mutableState.value = mutableState.value.copy(snapshot = mergeConfirmedSnapshots(mutableState.value.snapshot, confirmed),
                        loggedIn = true, email = session.email, online = true, custom = custom,
                        pendingCompletions = mutableState.value.pendingCompletions - task.id)
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                val (session, cached) = withContext(Dispatchers.IO) { repo.store.session() to repo.store.snapshot() }
                if (generation == accountGeneration) {
                    if (session == null) loseAccount(friendlyError(error))
                    else mutableState.value = mutableState.value.copy(snapshot = mergeConfirmedSnapshots(mutableState.value.snapshot, cached),
                        pendingCompletions = mutableState.value.pendingCompletions - task.id,
                        online = error !is java.io.IOException,
                        message = "「${task.template.title}」未能确认保存，已恢复已同步记录。${friendlyError(error)}")
                }
            } finally {
                if (generation == accountGeneration) {
                    completionJobs.remove(task.id)
                    updateReminders()
                }
            }
        }
    }
    fun demo() {
        if (active?.isActive == true) return
        invalidateCompletions()
        val today = LocalDate.now(BUSINESS_ZONE)
        val sunday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        val sample = Snapshot(listOf(Camp("demo-1", 8, sunday.minusWeeks(6)), Camp("demo-2", 9, sunday.plusWeeks(2))))
        val completed = engine.today(sample, today)
            .filter { !it.template.follow && (it.template.day == "all" || it.date == today) }.take(2)
            .associate { it.id to TaskState(true, 1) }
        val snapshot = sample.copy(tasks = completed)
        repo.store.demo(true)
        mutableState.value = AppState(snapshot, demo = true, settings = repo.store.settings(), today = today)
    }
    fun signOut() {
        if (active?.isActive == true) return
        invalidateCompletions()
        mutableState.value = mutableState.value.copy(busy = true)
        active = viewModelScope.launch {
            repo.signOut()
            updateReminders(cancel = true)
            if (backgroundSyncEnabled) SyncWorker.disable(getApplication())
            mutableState.value = AppState(settings = repo.store.settings())
        }
    }
    fun settings(value: ReminderSettings) {
        repo.store.settings(value)
        mutableState.value = mutableState.value.copy(settings = value)
        viewModelScope.launch(Dispatchers.IO) { ReminderScheduler(getApplication()).rebuild() }
    }
    fun custom(taskId: String, at: Long?) {
        if (mutableState.value.demo) { message("演示模式不保存任务提醒；登录后可设置。"); return }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.store.custom(taskId, at)
                updateReminders()
            }
            mutableState.value = mutableState.value.copy(custom = repo.store.custom(), message = if (at == null) "已取消本机提醒。" else "已设置本机提醒。")
        }
    }
    fun message(value: String) { mutableState.value = mutableState.value.copy(message = value) }
    fun clearMessage() { message("") }
}
