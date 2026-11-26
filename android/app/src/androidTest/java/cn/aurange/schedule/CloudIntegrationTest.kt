package cn.aurange.schedule

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
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
class CloudIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val store get() = ScheduleRepository.get(context).store
    @Before fun reset() { store.clearAccount() }
    @After fun cleanup() { store.clearAccount() }

    private class Server {
        val camp = Camp("cloud-fixture", 1, LocalDate.parse("2026-10-25"))
        var snapshot = Snapshot(listOf(camp))
        var allowed = true
        var offline = false
        var expired = false
        var expectedVersion = 0
        var duplicateCamp = false
        var onSnapshot: (() -> Unit)? = null
        val calls = mutableListOf<String>()
        fun post(path: String, body: JSONObject, token: String?): JSONObject {
            synchronized(calls) { calls += path }
            if (offline) throw IOException("fixture offline")
            if (path.startsWith("/auth/")) {
                if (expired && path.contains("refresh_token")) throw CloudFailure(400, "refresh_token_not_found", "invalid fixture refresh token")
                return JSONObject().put("access_token", "fixture-access").put("refresh_token", "fixture-refresh")
                    .put("expires_in", 3600).put("user", JSONObject().put("id", "fixture-user").put("email", "fixture@example.invalid"))
            }
            assertEquals("fixture-access", token)
            return when {
                path.endsWith("my_app_access") -> JSONObject().put("allowed", allowed).put("apps", JSONArray(if (allowed) listOf("ai") else emptyList<String>()))
                path.endsWith("schedule_snapshot") -> { onSnapshot?.invoke(); JSONObject(NativeJson.snapshot(snapshot)) }
                path.endsWith("save_schedule_camp") -> {
                    assertEquals("ai", body.getString("p_program"))
                    assertTrue(body.isNull("p_id")); assertEquals(0, body.getInt("p_version"))
                    if (duplicateCamp) throw CloudFailure(409, "23505", "schedule_camps_active_term")
                    val skips = body.getJSONArray("p_skips")
                    val saved = Camp("created-fixture", body.getInt("p_term"), LocalDate.parse(body.getString("p_open_date")),
                        (0 until skips.length()).map { skips.getInt(it) }.toSet())
                    snapshot = snapshot.copy(camps = snapshot.camps + saved)
                    JSONObject(NativeJson.snapshot(Snapshot(listOf(saved)))).getJSONArray("camps").getJSONObject(0)
                }
                path.endsWith("set_schedule_task") -> {
                    assertEquals("ai", body.getString("p_program"))
                    assertEquals(expectedVersion, body.getInt("p_version"))
                    val id = body.getString("p_camp_id") + "|" + body.getString("p_task_key")
                    if ((snapshot.tasks[id]?.version ?: 0) != body.getInt("p_version")) throw CloudFailure(400, "P0001", "schedule_conflict")
                    val state = TaskState(body.getBoolean("p_completed"), expectedVersion + 1)
                    snapshot = snapshot.copy(tasks = snapshot.tasks + (id to state))
                    JSONObject().put("version", state.version)
                }
                else -> error("Unexpected fixture request: $path")
            }
        }
    }

    @Test fun signInAndSharedCompletionUseAccessChecksAndCapturedVersion() = runBlocking {
        val server = Server()
        val repo = ScheduleRepository(context, server::post)
        val first = repo.signIn("fixture@example.invalid", "fixture-password")
        val task = repo.engine.allTasks(first).first()
        val completed = repo.complete(task, true, 0)
        assertEquals(TaskState(true, 1), completed.tasks[task.id])
        assertEquals(TaskState(true, 1), store.snapshot().tasks[task.id])
        assertTrue(server.calls.indexOfFirst { it.endsWith("my_app_access") } < server.calls.indexOfFirst { it.endsWith("schedule_snapshot") })
        // The displayed version is 1, while a background refresh has advanced the local/server version to 2.
        server.snapshot = server.snapshot.copy(tasks = mapOf(task.id to TaskState(true, 2)))
        repo.refresh()
        server.expectedVersion = 1
        try { repo.complete(task, false, 1); fail("Expected a version conflict") }
        catch (e: IllegalStateException) { assertTrue(e.message.orEmpty().contains("其他人")) }
        assertEquals(TaskState(true, 2), store.snapshot().tasks[task.id])
    }

    @Test fun permissionRevocationClearsCachedAccountAndPendingTaskSettings() = runBlocking {
        val server = Server(); val repo = ScheduleRepository(context, server::post)
        val first = repo.signIn("fixture@example.invalid", "fixture-password")
        store.custom(repo.engine.allTasks(first).first().id, System.currentTimeMillis() + 60_000)
        server.allowed = false
        val snapshotsBefore = server.calls.count { it.endsWith("schedule_snapshot") }
        try { repo.refresh(); fail("Expected AccessDenied") } catch (_: AccessDenied) { }
        assertNull(store.session()); assertTrue(store.snapshot().camps.isEmpty()); assertTrue(store.custom().isEmpty())
        assertEquals(snapshotsBefore, server.calls.count { it.endsWith("schedule_snapshot") })
    }

    @Test fun networkFailureKeepsCacheButInvalidRefreshTokenClearsIt() = runBlocking {
        val server = Server(); val repo = ScheduleRepository(context, server::post)
        val first = repo.signIn("fixture@example.invalid", "fixture-password")
        server.offline = true
        try { repo.refresh(); fail("Expected IOException") } catch (_: IOException) { }
        assertEquals(first, store.snapshot()); assertNotNull(store.session())
        server.offline = false; server.expired = true
        store.session(store.session()!!.copy(expiresAt = 0))
        try { repo.refresh(); fail("Expected LoginExpired") } catch (_: LoginExpired) { }
        assertNull(store.session()); assertTrue(store.snapshot().camps.isEmpty())
    }

    @Test fun logoutCannotBeUndoneByAnInflightSnapshot() = runBlocking {
        val server = Server(); val repo = ScheduleRepository(context, server::post)
        repo.signIn("fixture@example.invalid", "fixture-password")
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        server.onSnapshot = { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) }
        val refresh = async(kotlinx.coroutines.Dispatchers.Default) { repo.refresh() }
        check(entered.await(5, TimeUnit.SECONDS))
        val signOutStarted = CompletableDeferred<Unit>()
        val logout = async(kotlinx.coroutines.Dispatchers.Default) { signOutStarted.complete(Unit); repo.signOut() }
        signOutStarted.await()
        release.countDown()
        refresh.await(); logout.await()
        assertNull(store.session()); assertTrue(store.snapshot().camps.isEmpty())
    }

    @Test fun createCampUsesWebsiteRpcAndImmediatelyFeedsScheduleCacheAndReminders() = runBlocking {
        val server = Server(); val repo = ScheduleRepository(context, server::post)
        repo.signIn("fixture@example.invalid", "fixture-password")
        val created = repo.createCamp(CampDraft(2, LocalDate.parse("2026-11-01"), setOf(2, 4)))
        val camp = created.camps.single { it.term == 2 }
        assertEquals(server.snapshot.camps.last(), camp)
        assertEquals(created, store.snapshot())
        assertEquals(2, repo.engine.phases(camp).count { it.type == "rest" })
        assertTrue(repo.engine.today(created, camp.openDate).any { it.camp.id == camp.id })
        val now = camp.openDate.atTime(8, 0).atZone(BUSINESS_ZONE).toInstant()
        assertTrue(ReminderPlan(repo.engine).next(Snapshot(listOf(camp)), ReminderSettings(), now)!!.events.isNotEmpty())
        val task = repo.engine.allTasks(created).first { it.camp.id == camp.id }
        assertTrue(repo.complete(task, true, 0).tasks[task.id]!!.completed)
        assertEquals(1, server.calls.count { it.endsWith("save_schedule_camp") })
    }

    @Test fun campValidationDuplicateNetworkAndDeniedWritesPreserveConfirmedData() = runBlocking {
        val server = Server(); val repo = ScheduleRepository(context, server::post)
        val before = repo.signIn("fixture@example.invalid", "fixture-password")
        try { repo.createCamp(CampDraft(1, server.camp.openDate)); fail("duplicate local term") } catch (_: IllegalStateException) { }
        assertFalse(server.calls.any { it.endsWith("save_schedule_camp") })
        server.duplicateCamp = true
        try { repo.createCamp(CampDraft(2, server.camp.openDate)); fail("duplicate shared term") }
        catch (e: IllegalStateException) { assertTrue(e.message.orEmpty().contains("已存在")) }
        assertEquals(before, store.snapshot())
        server.offline = true
        try { repo.createCamp(CampDraft(2, server.camp.openDate)); fail("offline") }
        catch (e: IllegalStateException) { assertTrue(e.message.orEmpty().contains("核对")) }
        assertEquals(before, store.snapshot())
        server.offline = false; server.allowed = false
        val writes = server.calls.count { it.endsWith("save_schedule_camp") }
        try { repo.createCamp(CampDraft(2, server.camp.openDate)); fail("denied") } catch (_: AccessDenied) { }
        assertEquals(writes, server.calls.count { it.endsWith("save_schedule_camp") })
        assertNull(store.session()); assertTrue(store.snapshot().camps.isEmpty())
    }
}
