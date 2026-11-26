package cn.aurange.schedule

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModelStore
import androidx.work.WorkManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class CompletionLatencyTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as Application
    private val owner = ViewModelStore()
    private lateinit var server: Server
    private lateinit var repo: ScheduleRepository
    private lateinit var model: ScheduleViewModel
    private var prepared = false

    private class Server {
        @Volatile var snapshot = Snapshot(listOf(Camp("latency-fixture", 999, LocalDate.now(BUSINESS_ZONE).plusWeeks(2))))
        @Volatile var failure = ""
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val writes = mutableListOf<String>()
        val calls = mutableListOf<String>()
        fun post(path: String, body: JSONObject, token: String?): JSONObject {
            synchronized(calls) { calls += path }
            assertEquals("latency-access", token)
            return when {
                path.endsWith("my_app_access") -> JSONObject().put("allowed", true).put("apps", JSONArray(listOf("ai")))
                path.endsWith("schedule_snapshot") -> JSONObject(NativeJson.snapshot(snapshot))
                path.endsWith("set_schedule_task") -> {
                    entered.countDown()
                    check(release.await(8, TimeUnit.SECONDS)) { "Test did not release the slow server" }
                    if (failure == "network") throw IOException("fixture offline")
                    if (failure == "denied") throw CloudFailure(403, "42501", "fixture access revoked")
                    val id = body.getString("p_camp_id") + "|" + body.getString("p_task_key")
                    if ((snapshot.tasks[id]?.version ?: 0) != body.getInt("p_version")) throw CloudFailure(400, "P0001", "schedule_conflict")
                    val changed = TaskState(body.getBoolean("p_completed"), body.getInt("p_version") + 1)
                    synchronized(writes) { writes += id }
                    snapshot = snapshot.copy(tasks = snapshot.tasks + (id to changed))
                    JSONObject().put("version", changed.version)
                }
                else -> error("Unexpected fixture request: $path")
            }
        }
    }

    @Before fun prepare() {
        assertTrue("Latency fixtures require an emulator", Build.HARDWARE in listOf("ranchu", "goldfish"))
        prepared = true
        server = Server()
        repo = ScheduleRepository(app, server::post)
        repo.store.clearAccount()
        app.packageManager.setComponentEnabledSetting(ComponentName(app, RestoreReceiver::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        WorkManager.getInstance(app).cancelAllWork().result.get(5, TimeUnit.SECONDS)
        repo.store.session(Session("latency-access", "latency-refresh", System.currentTimeMillis()/1000 + 3600, "fixture@example.invalid", "latency-user"))
        repo.store.snapshot(server.snapshot)
        instrumentation.runOnMainSync { model = ScheduleViewModel(app, repo, backgroundSyncEnabled = false); owner.put("latency", model) }
        until { !model.state.value.busy && model.state.value.online }
        // Let startup alarm reconstruction settle before measuring a user action.
        Thread.sleep(200)
        synchronized(server.calls) { server.calls.clear() }
    }

    @After fun cleanup() {
        if (!prepared) return
        server.release.countDown()
        instrumentation.runOnMainSync { owner.clear() }
        runBlocking { repo.signOut() }
        SyncWorker.disable(app)
        ReminderScheduler(app).cancel()
        app.packageManager.setComponentEnabledSetting(ComponentName(app, RestoreReceiver::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP)
    }

    private fun until(check: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            if (check()) return
            Thread.sleep(20)
        }
        assertTrue("Timed out: busy=${model.state.value.busy}, loggedIn=${model.state.value.loggedIn}, pending=${model.state.value.pendingCompletions.size}, session=${repo.store.session() != null}, message=${model.state.value.message}, calls=${server.calls}, writes=${server.writes}, uiTasks=${model.state.value.snapshot.tasks}, cacheTasks=${repo.store.snapshot().tasks}", check())
    }
    private fun tasks() = repo.engine.today(server.snapshot, LocalDate.now(BUSINESS_ZONE)).take(2)

    @Test fun slowSaveRespondsImmediatelyWithoutLockingOtherTasks() {
        val (first, second) = tasks()
        instrumentation.runOnMainSync { model.complete(first) }
        assertTrue(server.entered.await(2, TimeUnit.SECONDS))
        assertTrue("A slow server must not delay the check animation", repo.engine.complete(model.state.value.displaySnapshot, first))
        assertFalse("Completion must not trigger full-page loading", model.state.value.busy)
        assertFalse("Unconfirmed completion must not enter durable alarm data", repo.engine.complete(repo.store.snapshot(), first))
        instrumentation.runOnMainSync { model.complete(first); model.complete(second); model.refresh() }
        assertTrue(repo.engine.complete(model.state.value.displaySnapshot, second))
        assertEquals(2, model.state.value.pendingCompletions.size)
        assertFalse(model.state.value.busy)
        server.release.countDown()
        until { model.state.value.pendingCompletions.isEmpty() && repo.engine.complete(model.state.value.snapshot, second) }
        assertEquals(listOf(first.id, second.id), server.writes)
        assertEquals("A saved task needs access check and mutation, without an extra full refresh", 4, server.calls.size)
    }

    @Test fun failedSaveRollsBackOnlyTheUnconfirmedChange() {
        val first = tasks().first()
        server.failure = "network"
        instrumentation.runOnMainSync { model.complete(first) }
        assertTrue(server.entered.await(2, TimeUnit.SECONDS))
        assertTrue(repo.engine.complete(model.state.value.displaySnapshot, first))
        server.release.countDown()
        until { model.state.value.pendingCompletions.isEmpty() && model.state.value.message.isNotEmpty() }
        assertFalse(repo.engine.complete(model.state.value.displaySnapshot, first))
        assertFalse(repo.engine.complete(repo.store.snapshot(), first))
        assertFalse(model.state.value.online)
    }

    @Test fun conflictKeepsTheServerVersionAndAllowsRetry() {
        val first = tasks().first()
        instrumentation.runOnMainSync { model.complete(first) }
        assertTrue(server.entered.await(2, TimeUnit.SECONDS))
        server.snapshot = server.snapshot.copy(tasks = mapOf(first.id to TaskState(false, 2)))
        server.release.countDown()
        until { model.state.value.pendingCompletions.isEmpty() && model.state.value.message.isNotEmpty() }
        assertEquals(TaskState(false, 2), model.state.value.displaySnapshot.tasks[first.id])
        assertTrue(model.state.value.online)
        instrumentation.runOnMainSync { model.complete(first) }
        until { model.state.value.pendingCompletions.isEmpty() && model.state.value.snapshot.tasks[first.id] == TaskState(true, 3) }
    }

    @Test fun revokedAccessClearsOptimisticStateAndCachedAccount() {
        val first = tasks().first()
        server.failure = "denied"
        instrumentation.runOnMainSync { model.complete(first) }
        assertTrue(server.entered.await(2, TimeUnit.SECONDS))
        server.release.countDown()
        until { !model.state.value.loggedIn }
        assertTrue(model.state.value.pendingCompletions.isEmpty())
        assertTrue(model.state.value.displaySnapshot.camps.isEmpty())
        assertNull(repo.store.session())
    }

    @Test fun signingOutDuringSlowSaveCannotRestoreTheOldAccount() {
        val first = tasks().first()
        instrumentation.runOnMainSync { model.complete(first) }
        assertTrue(server.entered.await(2, TimeUnit.SECONDS))
        instrumentation.runOnMainSync { model.signOut() }
        server.release.countDown()
        until { !model.state.value.loggedIn && repo.store.session() == null }
        assertTrue(model.state.value.pendingCompletions.isEmpty())
        assertTrue(model.state.value.snapshot.camps.isEmpty())
    }
}
