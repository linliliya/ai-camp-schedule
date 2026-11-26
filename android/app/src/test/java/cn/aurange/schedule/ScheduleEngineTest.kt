package cn.aurange.schedule

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate
import org.json.JSONArray

class ScheduleEngineTest {
    private val sop = NativeJson.sop(File(System.getProperty("schedule.assets"), "schedule.json").readText())
    private val engine = ScheduleEngine(sop)
    private fun camp(date: String = "2026-10-25", skips: Set<Int> = emptySet()) = Camp("camp-id", 1, LocalDate.parse(date), skips)

    @Test fun monthEndAndLeapYearPreparation() {
        assertEquals(LocalDate.parse("2027-02-28"), engine.phases(camp("2027-03-31"))[0].start)
        assertEquals(LocalDate.parse("2028-02-29"), engine.phases(camp("2028-03-31"))[0].start)
        val phases = engine.phases(camp())
        assertEquals(LocalDate.parse("2026-10-18"), phases[0].end)
        assertEquals("open", engine.current(camp(), LocalDate.parse("2026-10-19"))?.key)
    }
    @Test fun allSkipCombinationsKeepTwelveTeachingWeeksAndContinuousDates() {
        for (mask in 0 until 4096) {
            val skips = (1..12).filter { mask and (1 shl (it - 1)) != 0 }.toSet()
            val phases = engine.phases(camp(skips = skips))
            assertEquals(15 + skips.size, phases.size)
            assertEquals((1..13).toList(), phases.filter { it.week > 0 }.map { it.week })
            phases.zipWithNext().forEach { (a, b) -> assertEquals(a.end.plusDays(1), b.start) }
        }
    }
    @Test fun preservedCloudKeysAndWeekOneRules() {
        val c = camp(); val phases = engine.phases(c)
        val prep = engine.tasks(c, phases.first())
        assertEquals("open|all|创建本期课程", prep[1].key)
        assertEquals("open|all|更新本期云空间配套资料", prep[2].key)
        val open = engine.tasks(c, phases[1])
        assertTrue(open.any { it.key.startsWith("open|6|鹅圈子") && it.template.day == "0" })
        assertFalse(engine.templates(phases.first { it.week == 1 }).any { it.notW1 })
        assertTrue(engine.templates(phases.first { it.week == 2 }).any { it.notW1 })
        assertFalse(engine.templates(phases.last()).any { it.title == "提醒本周作业提交截止" })
        assertTrue(engine.templates(phases.last()).any { it.title == "结营典礼直播" })
        assertEquals("exam", phases.first { it.week == 7 }.type)
    }
    @Test fun followupsSurviveClosingAndCompletionSuppressesThem() {
        val c = camp(); val snapshot = Snapshot(listOf(c))
        val after = engine.phases(c).last().end.plusMonths(1)
        val follow = engine.today(snapshot, after)
        assertTrue(follow.isNotEmpty()); assertTrue(follow.all { it.template.follow })
        val complete = snapshot.copy(tasks = follow.associate { it.id to TaskState(true, 1) })
        assertTrue(engine.today(complete, after).isEmpty())
    }
    @Test fun overdueIsLimitedToCurrentWeekAndTaskIdsAreUnique() {
        val c = camp(); val snapshot = Snapshot(listOf(c))
        val date = LocalDate.parse("2026-11-05")
        val today = engine.today(snapshot, date)
        assertTrue(today.any { it.date < date && !it.template.follow && it.template.day != "all" })
        assertTrue(today.filterNot { it.template.follow }.all { it.phase.key == "week-2" })
        val all = engine.allTasks(snapshot)
        assertEquals(all.size, all.map { it.id }.distinct().size)
        assertEquals(snapshot, NativeJson.snapshot(NativeJson.snapshot(snapshot)))
    }
    @Test fun referralMilestonesUseTeachingWeeksAndPreservePostLiveClosingCompletion() {
        for (c in listOf(camp(), camp("2026-11-01", setOf(3, 8)))) {
            val snapshot = Snapshot(listOf(c))
            val referrals = engine.allTasks(snapshot).filter {
                it.template.title in setOf("发布老带新话术", "群发优惠券与推广返现说明")
            }
            assertEquals(listOf(3, 11, 13), referrals.map { it.phase.week })
            for (task in referrals.take(2)) {
                assertEquals(java.time.DayOfWeek.WEDNESDAY, task.date.dayOfWeek)
                assertEquals(task.phase.start.plusDays(2), task.date)
                assertEquals("", task.template.time)
                assertNull(task.clockTime)
                assertTrue(ReminderPlan(engine).summaryTasks(snapshot, task.date).any { it.id == task.id })
            }
            val closing = referrals.last()
            assertEquals(java.time.DayOfWeek.SUNDAY, closing.date.dayOfWeek)
            assertEquals("直播后", closing.template.time)
            assertEquals("week-12|0|群发优惠券与推广返现说明", closing.key)
            val completed = snapshot.copy(tasks = mapOf(closing.id to TaskState(true, 1)))
            assertTrue(engine.complete(completed, closing))
            assertFalse(ReminderPlan(engine).summaryTasks(completed, closing.date).any { it.id == closing.id })
        }
    }

    @Test fun graduationDeadlineAndFlexibleCeremonyUseWeekThirteen() {
        val c = camp(skips = setOf(3, 8))
        val phases = engine.phases(c)
        val closing = phases.single { it.week == 13 }
        val tasks = engine.tasks(c, closing)
        val deadline = tasks.single { it.template.title == "截止收集毕业作品与展示意愿" }
        assertEquals(closing.start.plusDays(2), deadline.date)
        assertEquals(java.time.LocalTime.of(23, 59), deadline.clockTime)
        val ceremony = tasks.single { it.template.title == "结营典礼直播" }
        assertEquals(closing.end, ceremony.date)
        assertNull(ceremony.clockTime)
        assertEquals("week-12|0|结营典礼直播", ceremony.key)
        val selection = tasks.single { it.template.title == "整理作品，与队长确定展示成员" }
        assertEquals(closing.start.plusDays(3), selection.date)
        assertEquals("week-12|5|每队评选并公布 1–2 位毕业展示代表", selection.key)
        val snapshot = Snapshot(listOf(c))
        val ceremonyTitles = engine.today(snapshot, ceremony.date).filter { it.date == ceremony.date && !it.template.follow }.map { it.template.title }
        assertTrue(ceremonyTitles.indexOf("结营典礼直播") < ceremonyTitles.indexOf("各小队代表进行毕业作品展示"))
        assertTrue(ceremonyTitles.indexOf("各小队代表进行毕业作品展示") < ceremonyTitles.indexOf("群发优惠券与推广返现说明"))
        assertTrue(ReminderPlan(engine).summaryTasks(snapshot, ceremony.date).any { it.id == ceremony.id })
        val now = ceremony.date.atTime(8, 0).atZone(BUSINESS_ZONE).toInstant()
        val custom = ceremony.date.atTime(16, 30).atZone(BUSINESS_ZONE).toInstant().toEpochMilli()
        val next = ReminderPlan(engine).next(snapshot, ReminderSettings(summaryTime = java.time.LocalTime.of(7, 0)), now, mapOf(ceremony.id to custom))!!
        assertTrue(next.events.any { it.taskId == ceremony.id })
        assertEquals(custom, next.at)
    }

    @Test fun longTermTrackingUsesClosingDateAndSurvivesTheCampEnd() {
        val c = camp("2028-10-01")
        val phases = engine.phases(c)
        val closing = phases.last().end
        assertEquals(LocalDate.of(2028,12,31), closing)
        val tracking = engine.trackingTasks(c)
        assertEquals(listOf(closing.plusDays(14), LocalDate.of(2029,2,28), LocalDate.of(2029,4,30)), tracking.map { it.date })
        assertTrue(tracking.all { it.phase !in phases })
        val snapshot = Snapshot(listOf(c))
        assertFalse(engine.today(snapshot, closing.plusDays(13)).any { it.id == tracking.first().id })
        assertTrue(ReminderPlan(engine).summaryTasks(snapshot, closing.plusDays(14)).any { it.id == tracking.first().id })
        val completed = snapshot.copy(tasks = mapOf(tracking.first().id to TaskState(true, 1)))
        assertFalse(engine.today(completed, closing.plusMonths(1)).any { it.id == tracking.first().id })
        assertTrue(engine.today(snapshot, closing.plusMonths(5)).containsAll(tracking))
    }

    @Test fun nativePhasesDatesAndEveryTaskKeyMatchActualWebsiteImplementation() {
        val cases = JSONArray(File(requireNotNull(System.getProperty("schedule.webFixture"))).readText()).objects()
        for (case in cases) {
            val skips = case.getJSONArray("skips")
            val c = camp(case.getString("openDate"), (0 until skips.length()).map { skips.getInt(it) }.toSet())
                .copy(examWeek = case.getInt("examWeek"))
            val expected = case.getJSONArray("phases").objects()
            val phases = engine.phases(c)
            assertEquals(expected.size, phases.size)
            expected.zip(phases).forEach { (web, native) ->
                val label = "${c.openDate} ${c.skips} ${native.key}"
                assertEquals(label, web.getString("key"), native.key)
                assertEquals(label, web.getString("type"), native.type)
                assertEquals(label, web.getString("start"), native.start.toString())
                assertEquals(label, web.getString("end"), native.end.toString())
                assertEquals(label, web.optionalString("courseScript"), native.courseScript)
                val keys = web.getJSONArray("keys")
                assertEquals(label, (0 until keys.length()).map { keys.getString(it) }, engine.tasks(c, native).map { it.key })
            }
        }
    }
}
