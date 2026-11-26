package cn.aurange.schedule

import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

class ScheduleEngine(val sop: SopData) {
    fun phases(camp: Camp): List<Phase> {
        val monday = camp.openDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val result = mutableListOf(
            Phase("prep", "开营筹备", "筹", "prep", camp.openDate.minusMonths(1), monday.minusDays(1), examOffset = -1 - camp.examWeek),
            Phase("open", "开营周", "开", "open", monday, monday.plusDays(6), examOffset = -camp.examWeek, courseScript = "course-w1")
        )
        var start = monday.plusWeeks(1)
        for (n in 1..13) {
            if (n in camp.skips) {
                result += Phase("rest-$n", "休息周", "休", "rest", start, start.plusDays(6))
                start = start.plusWeeks(1)
            }
            val type = when (n) { camp.examWeek -> "exam"; 13 -> "close"; else -> "regular" }
            val label = when (n) { camp.examWeek -> "第${n}周 · 考试"; 12 -> "第12周 · 毕业设计"; 13 -> "第13周 · 结营"; else -> "第${n}周" }
            val course = when {
                n == camp.examWeek -> "course-exam"
                n < camp.examWeek -> "course-w${n + 1}"
                n <= 12 -> "course-w$n"
                else -> null
            }
            result += Phase("week-$n", label, "$n", type, start, start.plusDays(6), n, n - camp.examWeek, course)
            start = start.plusWeeks(1)
        }
        return result
    }

    fun templates(phase: Phase): List<TaskTemplate> = when (phase.type) {
        "prep" -> sop.prep + sop.exam[phase.examOffset].orEmpty()
        "open" -> sop.open + sop.exam[phase.examOffset].orEmpty()
        "rest" -> sop.rest
        "close" -> sop.closing
        else -> sop.regular.filter { phase.week != 1 || !it.notW1 } +
            sop.exam[phase.examOffset].orEmpty() + sop.extras[phase.week].orEmpty()
    }

    fun tasks(camp: Camp, phase: Phase): List<TaskOccurrence> = templates(phase).map { task ->
        val date = if (task.day == "all") phase.start else phase.start.plusDays(((task.day.toInt() + 6) % 7).toLong())
        val key = "${task.originalWeek ?: phase.key}|${task.originalDay ?: task.day}|${task.originalTitle ?: task.title}"
        TaskOccurrence(camp, phase, task, date, key)
    }

    fun trackingTasks(camp: Camp): List<TaskOccurrence> {
        val closing = phases(camp).last().end
        return sop.tracking.map { task ->
            val date = closing.plusMonths(task.afterMonths).plusDays(task.afterDays)
            val phase = Phase("tracking-${task.title}", task.time, "访", "tracking", date, date)
            val key = "${task.originalWeek}|${task.originalDay ?: task.day}|${task.originalTitle ?: task.title}"
            TaskOccurrence(camp, phase, task, date, key)
        }
    }

    fun allTasks(snapshot: Snapshot): List<TaskOccurrence> = snapshot.camps.flatMap { camp -> phases(camp).flatMap { tasks(camp, it) } + trackingTasks(camp) }
    fun complete(snapshot: Snapshot, task: TaskOccurrence): Boolean = snapshot.tasks[task.id]?.completed == true
    fun current(camp: Camp, date: LocalDate): Phase? = phases(camp).firstOrNull { !date.isBefore(it.start) && !date.isAfter(it.end) }

    fun today(snapshot: Snapshot, date: LocalDate): List<TaskOccurrence> {
        return allTasks(snapshot).filter { task ->
            if (task.template.follow) !task.date.isAfter(date) && !complete(snapshot, task)
            else !date.isBefore(task.phase.start) && !date.isAfter(task.phase.end) &&
                (task.template.day == "all" || task.date == date || (task.date < date && !complete(snapshot, task)))
        }.distinctBy { it.id }.sortedWith(compareBy<TaskOccurrence>(
            { if (!it.template.follow && it.template.day != "all" && it.date < date) 0 else if (it.template.follow) 2 else 1 },
            { it.clockTime ?: java.time.LocalTime.MAX }, { it.camp.term },
            { if (it.phase.week == 13) sop.closing.indexOf(it.template) else 0 }, { it.template.title }
        ))
    }
}
