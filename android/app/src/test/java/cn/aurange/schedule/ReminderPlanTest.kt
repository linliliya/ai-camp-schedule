package cn.aurange.schedule

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class ReminderPlanTest {
    private val engine = ScheduleEngine(NativeJson.sop(File(System.getProperty("schedule.assets"), "schedule.json").readText()))
    private val plan = ReminderPlan(engine)
    private val snapshot = Snapshot(listOf(Camp("c", 1, LocalDate.parse("2026-10-25"))))
    private fun time(local: String) = java.time.LocalDateTime.parse(local).atZone(BUSINESS_ZONE).toInstant()
    private fun settings() = ReminderSettings(summaryTime = LocalTime.of(9, 0))

    @Test fun summaryIsAtNineShanghaiTimeAndCanBeDisabled() {
        val next = plan.next(snapshot, settings(), time("2026-10-31T08:00:00"))!!
        assertEquals(Instant.parse("2026-10-31T01:00:00Z").toEpochMilli(), next.at)
        assertTrue(next.events.single().summary)
        assertNull(plan.next(snapshot, settings().copy(enabled = false), Instant.now()))
    }
    @Test fun timedTaskFiresFifteenMinutesEarlyAndCompletedTasksDoNotFire() {
        val now = time("2026-10-31T13:00:00")
        val next = plan.next(snapshot, settings(), now)!!
        assertEquals(time("2026-10-31T13:45:00").toEpochMilli(), next.at)
        assertEquals(2, next.events.size) // '14:00前' and '14:00' share a reminder batch.
        val completed = snapshot.copy(tasks = next.events.associate { it.taskId to TaskState(true, 1) })
        assertEquals(time("2026-10-31T19:45:00").toEpochMilli(), plan.next(completed, settings(), now)!!.at)
    }
    @Test fun sundayMidnightRolloverAndNoGuessedTimes() {
        val now = time("2026-11-01T23:00:00")
        val next = plan.next(snapshot, settings(), now)!!
        assertEquals(time("2026-11-01T23:45:00").toEpochMilli(), next.at)
        assertTrue(next.events.single().taskId.contains("提醒本周作业提交截止"))
        assertTrue(engine.allTasks(snapshot).filter { it.template.time in listOf("上午", "直播前", "直播后", "考前") }.all { it.clockTime == null })
    }
    @Test fun customReminderOverridesDefaultAndDeliveredEventsAreSkipped() {
        val now = time("2026-10-31T13:00:00")
        val default = plan.next(snapshot, settings(), now)!!
        val custom = default.events.associate { it.taskId to time("2026-10-31T13:30:00").toEpochMilli() }
        val next = plan.next(snapshot, settings(), now, custom)!!
        assertEquals(time("2026-10-31T13:30:00").toEpochMilli(), next.at)
        val after = plan.next(snapshot, settings(), now, custom, next.events.map { it.id }.toSet())!!
        assertEquals(time("2026-10-31T19:45:00").toEpochMilli(), after.at)
    }
    @Test fun idleAppAlwaysRearmsAtNextMidnight() {
        val next = plan.next(Snapshot(), settings(), time("2026-10-31T23:59:00"))!!
        assertEquals(time("2026-11-01T00:00:00").toEpochMilli(), next.at)
        assertTrue(next.events.isEmpty())
    }
    @Test fun cloudSyncAtAlarmTimeDoesNotDiscardAnUndeliveredNotification() {
        val old = plan.next(snapshot, settings(), time("2026-10-31T08:59:00"))!!
        val afterSync = plan.next(snapshot, settings(), time("2026-10-31T09:00:02"), previous = old)!!
        assertEquals(old, afterSync)
        val delivered = plan.next(snapshot, settings(), time("2026-10-31T09:00:02"), delivered = old.events.map { it.id }.toSet(), previous = old)!!
        assertTrue(delivered.at > afterSync.at)
        val changedSettings = plan.next(snapshot, settings().copy(summaryTime = LocalTime.of(10, 0)), time("2026-10-31T09:00:02"), previous = old)!!
        assertEquals(time("2026-10-31T10:00:00").toEpochMilli(), changedSettings.at)
    }
    @Test fun midnightMaintenanceDoesNotRepeatForeverAndVeryLateAlarmsAreDropped() {
        val old = plan.next(Snapshot(), settings(), time("2026-10-31T23:00:00"))!!
        val after = plan.next(Snapshot(), settings(), time("2026-11-01T00:00:02"), previous = old)!!
        assertEquals(time("2026-11-02T00:00:00").toEpochMilli(), after.at)
        val summary = plan.next(snapshot, settings(), time("2026-10-31T08:59:00"))!!
        assertTrue(plan.next(snapshot, settings(), time("2026-10-31T11:00:00"), previous = summary)!!.at > summary.at)
    }
}
