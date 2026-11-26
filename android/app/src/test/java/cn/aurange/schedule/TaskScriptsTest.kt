package cn.aurange.schedule

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TaskScriptsTest {
    private val catalog = TaskScripts(File(System.getProperty("schedule.assets"), "task-scripts.json").readText())
    @Test fun allLibraryUsesTheTemplatesOwnWeekWhenSearchingFromAnotherWeek() {
        val sop=NativeJson.sop(File(System.getProperty("schedule.assets"),"schedule.json").readText())
        val engine=ScheduleEngine(sop);val camp=Camp("test",1,java.time.LocalDate.of(2026,10,25),setOf(2,6))
        val phase=engine.phases(camp).first{it.key=="week-2"};val task=engine.tasks(camp,phase).first{it.template.title=="公布上周成长值榜单"}
        val rows=catalog.forCamp(task,engine);assertEquals(48,rows.size)
        val closing=rows.first{it.id=="course-w12"}
        assertTrue(closing.contextLabel.contains("第12周"))
        assertTrue(closing.body.contains("第 13 周周三 23:59"))
        assertTrue(rows.none { Regex("\\d{4}年\\d+月\\d+日").containsMatchIn(it.body) })
    }
    @Test fun scriptsUseRelativeDeadlinesAndLeaveUnknownFactsBlank() {
        val homework=catalog.forKey("week-6|5|提醒本周作业截止时间").single().forWeek(6)
        assertTrue(homework.body.contains("本周日晚24:00"))
        assertTrue(homework.body.contains("第6周"))
        assertFalse(homework.body.contains("本周日（）"))
        assertFalse(homework.body.contains("【本周答疑问卷链接】"))
        assertTrue(homework.blankFields.contains("【本周答疑问卷链接】"))
        assertFalse(homework.blankFields.contains("【作业截止日期】"))
        val identity=catalog.forKey("week-7|4|提醒报考学员完成实名认证").single().forWeek(7)
        assertTrue(identity.body.contains("实名认证截止时间：以本期通知为准"))
        assertFalse(identity.blankFields.contains("【本期实名认证截止日期及时间】"))
        assertFalse(identity.body.contains("https://"))
        val registration=catalog.forKey("week-6|5|发布本期工信部证书报考信息收集问卷").single().forWeek(6)
        assertTrue(registration.body.contains("截止时间以本期报名通知为准"))
        assertFalse(registration.blankFields.contains("【报名截止日期】"))
    }
    @Test fun flexibleCampDatesDoNotChangeScriptBodies() {
        val sop=NativeJson.sop(File(System.getProperty("schedule.assets"),"schedule.json").readText())
        val engine=ScheduleEngine(sop)
        val camps=listOf(
            Camp("original",1,java.time.LocalDate.of(2026,10,25)),
            Camp("rescheduled",2,java.time.LocalDate.of(2027,1,6),setOf(2,6))
        )
        val libraries=camps.map { camp ->
            val phase=engine.phases(camp).first { it.key=="week-2" }
            val task=engine.tasks(camp,phase).first { it.template.title=="公布上周成长值榜单" }
            catalog.forCamp(task,engine).associate { it.id to it.body }
        }
        assertEquals(48,libraries.first().size)
        assertEquals(libraries.first(),libraries.last())
    }
    @Test fun keysMatchAllTwelveCourseWeeksAndReferralsWithoutChangingOccurrences() {
        val sop = NativeJson.sop(File(System.getProperty("schedule.assets"), "schedule.json").readText())
        val engine = ScheduleEngine(sop)
        for (examWeek in listOf(6, 7)) {
            val c = Camp("test", 1, java.time.LocalDate.of(2026,10,25), setOf(6), examWeek = examWeek)
            val phases = engine.phases(c)
            val notices = phases.filter { it.week in 1..12 }.map { phase ->
                val task = engine.tasks(c, phase).single { it.template.title == "发布课程及打卡等更新提醒" }
                catalog.forTask(task).single().id
            }
            assertEquals((1..12).map { week -> when {
                week < examWeek -> "course-w${week + 1}"
                week == examWeek -> "course-exam"
                else -> "course-w$week"
            } }, notices)
        }
        for (week in listOf(3,11)) assertEquals(if (week == 3) "referral" else "referral-w11",catalog.forKey("week-$week|3|发布老带新话术").single().id)
        assertEquals("referral-closing",catalog.forKey("week-12|0|群发优惠券与推广返现说明").single().id)
        assertTrue(catalog.forKey("unknown").isEmpty())
    }
    @Test fun variablesAndOperationalNotesAreSeparateFromCopyBody() {
        val row = catalog.forKey("week-2|5|提醒本周作业截止时间").single()
        assertTrue(row.variables.contains("【周次】"));assertTrue(row.variables.contains("【本周答疑问卷链接】"))
        assertFalse(row.body.contains(row.notes.first()))
    }
    @Test fun growthVersionsRotateAndExamTemplatesHaveCurrentCampVariables() {
        assertTrue(catalog.forKey("week-1|1|公布上周成长值榜单").isEmpty())
        for (week in 2..12) {
            val rows = catalog.forKey("week-$week|1|公布上周成长值榜单")
            assertEquals(10, rows.size)
            assertEquals("growth-${(week-2)%10+1}", rows.first().id)
            assertTrue(rows.none { it.body.contains("本周") })
            assertTrue(rows.all { it.variables.isEmpty() })
        }
        val exam = catalog.forKey("week-7|5|核对本期考试日期与场次，发送考前提醒")
        assertEquals(2, exam.size)
        assertTrue(exam.first().variables.contains("【上午场开考时间】"))
        assertTrue(exam.last().variables.contains("【下午场开考时间】"))
    }
}
