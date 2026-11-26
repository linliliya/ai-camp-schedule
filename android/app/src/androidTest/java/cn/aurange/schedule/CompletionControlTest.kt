package cn.aurange.schedule

import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import android.graphics.Rect
import android.graphics.Bitmap
import java.io.File
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class CompletionControlTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var activity: MainActivity? = null
    private var fixturePrepared = false

    @Before fun prepare() {
        assertTrue("Fixture UI checks require an emulator", Build.HARDWARE in listOf("ranchu", "goldfish"))
        fixturePrepared = true
        ScheduleRepository.get(context).store.clearAccount()
        activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
    }

    @After fun cleanup() {
        if (!fixturePrepared) return
        activity?.let { instrumentation.runOnMainSync { it.finish() } }
        ScheduleRepository.get(context).store.clearAccount()
    }

    // Match actual accessibility nodes directly; Compose labels include descriptions.
    private fun node(description: String): AccessibilityNodeInfo? {
        fun find(current: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (current.contentDescription?.toString() == description || current.text?.toString() == description) return current
            for (index in 0 until current.childCount) {
                current.getChild(index)?.let { child -> find(child)?.let { return it } }
            }
            return null
        }
        return instrumentation.uiAutomation.rootInActiveWindow?.let(::find)
    }

    private fun waitFor(description: String): AccessibilityNodeInfo {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            node(description)?.let { return it }
            Thread.sleep(50)
        }
        val labels = mutableListOf<String>()
        fun describe(current: AccessibilityNodeInfo) {
            if (!current.text.isNullOrEmpty() || !current.contentDescription.isNullOrEmpty()) {
                labels += "${current.text} / ${current.contentDescription}"
            }
            for (index in 0 until current.childCount) current.getChild(index)?.let(::describe)
        }
        instrumentation.uiAutomation.rootInActiveWindow?.let(::describe)
        error("Completion control not exposed to accessibility: $description; visible=$labels")
    }

    @Test fun completionAndRestoreDoNotOpenTaskDetails() {
        val completed = mutableStateOf(false)
        val edits = AtomicInteger()
        val opened = AtomicInteger()
        val engine = ScheduleRepository.get(context).engine
        val task = engine.allTasks(Snapshot(listOf(Camp("completion-fixture", 999, LocalDate.now(BUSINESS_ZONE))))).first()
        instrumentation.runOnMainSync {
            activity!!.setContent {
                ScheduleTheme {
                    TaskCard(task, completed.value, true, false,
                        onClick = { opened.incrementAndGet() },
                        onComplete = { edits.incrementAndGet(); completed.value = !completed.value })
                }
            }
        }
        val pending = waitFor("标记完成")
        assertTrue(pending.isCheckable)
        assertFalse(pending.isChecked)
        assertTrue(pending.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        val done = waitFor("取消完成")
        assertTrue(done.isChecked)
        if (Build.VERSION.SDK_INT >= 30) assertEquals("已完成", done.stateDescription?.toString())
        assertEquals(1, edits.get())
        assertEquals("The nested completion action must not open the task", 0, opened.get())
        assertTrue(done.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        assertFalse(waitFor("标记完成").isChecked)
        assertEquals(2, edits.get())
        assertEquals(0, opened.get())
    }

    @Test fun hundredPercentStaysCenteredAtLargeFontScale() {
        instrumentation.runOnMainSync {
            activity!!.setContent {
                ScheduleTheme {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            TodaySummary(100, 100, 0, 0)
                        }
                    }
                }
            }
        }
        val badge = waitFor("完成比例")
        val percent = waitFor("100%")
        Thread.sleep(450)
        assertEquals("cn.aurange.schedule", instrumentation.uiAutomation.rootInActiveWindow.packageName.toString())
        File(context.getExternalFilesDir(null), "progress-100-large.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val outer = Rect().also(badge::getBoundsInScreen)
        val inner = Rect().also(percent::getBoundsInScreen)
        assertTrue("Percentage must fit: badge=$outer text=$inner", outer.contains(inner))
        assertEquals("Horizontal percentage center", outer.exactCenterX(), inner.exactCenterX(), 1.5f)
        assertEquals("Vertical percentage center", outer.exactCenterY(), inner.exactCenterY(), 1.5f)
    }

    @Test fun partialCompleteAndEmptyDaysHaveDistinctProgressStates() {
        val done = mutableStateOf(29)
        val total = mutableStateOf(100)
        instrumentation.runOnMainSync {
            activity!!.setContent { ScheduleTheme { TodaySummary(done.value, total.value, 0, 0) } }
        }
        waitFor("29%")
        waitFor("还有 71 项待办")
        instrumentation.runOnMainSync { done.value = 99 }
        waitFor("99%")
        waitFor("还有 1 项待办")
        instrumentation.runOnMainSync { done.value = 100 }
        waitFor("100%")
        waitFor("今天已全部完成")
        instrumentation.runOnMainSync { total.value = 0; done.value = 0 }
        waitFor("—")
        waitFor("当天暂无安排")
        instrumentation.waitForIdleSync()
        assertNull("An empty day must not pretend to be 100% complete", node("100%"))
        assertNull("An empty day should not show a misleading progress bar", node("日程完成进度"))
    }

    @Test fun readOnlyCompletionIsDisabledWhileCardDetailsRemainAvailable() {
        val edits = AtomicInteger()
        val opened = AtomicInteger()
        val engine = ScheduleRepository.get(context).engine
        val task = engine.allTasks(Snapshot(listOf(Camp("completion-fixture", 999, LocalDate.now(BUSINESS_ZONE))))).first()
        instrumentation.runOnMainSync {
            activity!!.setContent {
                ScheduleTheme {
                    TaskCard(task, false, false, false,
                        onClick = { opened.incrementAndGet() }, onComplete = { edits.incrementAndGet() })
                }
            }
        }
        val checkbox = waitFor("标记完成")
        assertFalse(checkbox.isEnabled)
        checkbox.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        instrumentation.waitForIdleSync()
        assertEquals(0, edits.get())
        var card: AccessibilityNodeInfo? = checkbox.parent
        while (card != null && !card.isClickable) card = card.parent
        assertNotNull("Read-only tasks must still open details", card)
        assertTrue(card!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
        assertEquals(1, opened.get())
        assertEquals(0, edits.get())
    }

    @Test fun confirmedFollowUpCompletionLeavesTodayAfterTransition() {
        val engine = ScheduleRepository.get(context).engine
        val today = LocalDate.now(BUSINESS_ZONE)
        val sample = Snapshot(listOf(Camp("completion-fixture", 999, today.plusWeeks(2))))
        val tasks = engine.allTasks(sample)
        val target = engine.today(sample, today).first { it.template.follow }
        val snapshot = sample.copy(tasks = tasks.filterNot { it.id == target.id }.associate { it.id to TaskState(true, 1) })
        assertEquals(listOf(target.id), engine.today(snapshot, today).map { it.id })
        val state = mutableStateOf(AppState(snapshot = snapshot, loggedIn = true, online = true, today = today))
        val opened = AtomicInteger()
        val edits = AtomicInteger()
        instrumentation.runOnMainSync {
            activity!!.setContent {
                ScheduleTheme {
                    TodayScreen(state.value, engine, onTask = { opened.incrementAndGet() }, onComplete = { task ->
                        edits.incrementAndGet()
                        state.value = state.value.copy(snapshot = snapshot.copy(tasks = snapshot.tasks + (task.id to TaskState(true, 1))))
                    })
                }
            }
        }
        assertTrue(waitFor("标记完成").performAction(AccessibilityNodeInfo.ACTION_CLICK))
        waitFor("这一天暂无待办")
        assertEquals(1, edits.get())
        assertEquals(0, opened.get())
        assertNull("Confirmed follow-up must not remain as a stale card", node("取消完成"))
    }
}
