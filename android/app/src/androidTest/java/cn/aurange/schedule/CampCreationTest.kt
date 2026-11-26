package cn.aurange.schedule

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class CampCreationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as Application
    private val owner = ViewModelStore()
    private var activity: MainActivity? = null
    private lateinit var repo: ScheduleRepository
    private lateinit var model: ScheduleViewModel
    private var snapshot = Snapshot(listOf(Camp("camp-fixture", 1, LocalDate.now(BUSINESS_ZONE).plusWeeks(2))))
    private val entered = CountDownLatch(1)
    private val release = CountDownLatch(1)
    private var writes = 0
    private var failSave = false
    private var prepared = false

    private fun post(path: String, body: JSONObject, token: String?): JSONObject {
        assertEquals("camp-access", token)
        return when {
            path.endsWith("my_app_access") -> JSONObject().put("allowed", true).put("apps", JSONArray(listOf("ai")))
            path.endsWith("schedule_snapshot") -> JSONObject(NativeJson.snapshot(snapshot))
            path.endsWith("save_schedule_camp") -> {
                writes++; entered.countDown(); check(release.await(8, TimeUnit.SECONDS))
                if (failSave) throw CloudFailure(409, "23505", "duplicate fixture")
                val weeks = body.getJSONArray("p_skips")
                val camp = Camp("created-camp", body.getInt("p_term"), LocalDate.parse(body.getString("p_open_date")),
                    (0 until weeks.length()).map { weeks.getInt(it) }.toSet())
                snapshot = snapshot.copy(camps = snapshot.camps + camp)
                JSONObject(NativeJson.snapshot(Snapshot(listOf(camp)))).getJSONArray("camps").getJSONObject(0)
            }
            else -> error("Unexpected fixture path $path")
        }
    }

    @Before fun prepare() {
        assertTrue("Camp UI fixtures require an emulator", Build.HARDWARE in listOf("ranchu", "goldfish"))
        prepared = true
        repo = ScheduleRepository(app, ::post)
        repo.store.clearAccount()
        app.packageManager.setComponentEnabledSetting(ComponentName(app, RestoreReceiver::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        WorkManager.getInstance(app).cancelAllWork().result.get(5, TimeUnit.SECONDS)
        activity = instrumentation.startActivitySync(Intent(app, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        repo.store.session(Session("camp-access", "camp-refresh", System.currentTimeMillis()/1000 + 3600, "fixture@example.invalid", "camp-user"))
        repo.store.snapshot(snapshot)
        instrumentation.runOnMainSync {
            model = ScheduleViewModel(app, repo, backgroundSyncEnabled = false)
            owner.put("camp", model)
            activity!!.setContent { ScheduleTheme { ScheduleApp(model, null, {}, 0, {}) } }
        }
        until { !model.state.value.busy && model.state.value.online }
    }

    @After fun cleanup() {
        if (!prepared) return
        release.countDown()
        instrumentation.runOnMainSync { owner.clear(); activity?.finish() }
        runBlocking { repo.signOut() }
        WorkManager.getInstance(app).cancelAllWork().result.get(5, TimeUnit.SECONDS)
        ReminderScheduler(app).cancel()
        app.packageManager.setComponentEnabledSetting(ComponentName(app, RestoreReceiver::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP)
    }

    private fun until(check: () -> Boolean) {
        val end = System.currentTimeMillis() + 6000
        while (System.currentTimeMillis() < end) { if (check()) return; Thread.sleep(40) }
        assertTrue("Camp fixture timed out: ${model.state.value}", check())
    }
    private fun node(text: String): AccessibilityNodeInfo? {
        fun find(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (n.text?.toString() == text || n.contentDescription?.toString() == text) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { find(it)?.let { match -> return match } }
            return null
        }
        return instrumentation.uiAutomation.rootInActiveWindow?.let(::find)
    }
    private fun waitFor(text: String): AccessibilityNodeInfo {
        until { node(text) != null }; return node(text)!!
    }
    private fun click(text: String) {
        var n = waitFor(text)
        while (!n.isClickable && n.parent != null) n = n.parent
        assertTrue("Cannot click $text", n.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        Thread.sleep(600) // Capture a settled frame after the sheet/date-picker transitions.
        assertEquals("cn.aurange.schedule", instrumentation.uiAutomation.rootInActiveWindow.packageName.toString())
        File(app.getExternalFilesDir(null), name).outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    private fun fill() {
        click("营期"); click("添加营期")
        click("添加并生成日程")
        waitFor("请选择开营日期。")
        assertEquals(0, writes)
        click("设置开营日期"); waitFor("选择开营日期"); click("确定")
        click("第2周")
    }

    @Test fun nativeFormCreatesCampOnceAndSelectsItsGeneratedSchedule() {
        fill(); capture("add-camp-form.png")
        click("添加并生成日程")
        assertTrue(entered.await(3, TimeUnit.SECONDS))
        assertTrue(model.state.value.busy)
        // Even another event while the HTTP request is pending cannot insert a second camp.
        instrumentation.runOnMainSync { model.createCamp(CampDraft(2, model.state.value.today, setOf(2))) }
        release.countDown()
        until { model.state.value.createdCampId == "created-camp" && !model.state.value.busy }
        val camp = model.state.value.snapshot.camps.single { it.id == "created-camp" }
        assertEquals(LocalDate.now(BUSINESS_ZONE), camp.openDate)
        assertEquals(setOf(2), camp.skips)
        assertEquals(1, writes)
        assertTrue(model.engine.today(repo.store.snapshot(), camp.openDate).any { it.camp.id == camp.id })
        waitFor("本期休息 1 周，课程已顺延"); capture("created-camp.png")
    }

    @Test fun serverConflictKeepsDateAndWeeksInFormForCorrection() {
        failSave = true; release.countDown(); fill()
        click("添加并生成日程")
        until { model.state.value.campError.isNotEmpty() && !model.state.value.busy }
        waitFor("刷新排期并核对")
        assertTrue(node(model.state.value.today.format(campDateFormat)) != null)
        var week = waitFor("第2周")
        while (!week.isClickable && week.parent != null) week = week.parent
        assertTrue("Selected week accessibility: $week", week.isChecked)
        assertEquals(1, writes)
        assertEquals(1, repo.store.snapshot().camps.size)
    }
}
