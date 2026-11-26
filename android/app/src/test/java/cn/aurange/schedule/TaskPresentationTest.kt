package cn.aurange.schedule

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TaskPresentationTest {
    private val date = LocalDate.parse("2026-11-05")
    private val camp = Camp("test", 1, LocalDate.parse("2026-10-25"))
    private val phase = Phase("week-2", "第2周", "2", "regular", date.minusDays(3), date.plusDays(3), 2)
    private fun task(key: String, day: String = "1", follow: Boolean = false, due: LocalDate = date.minusDays(3)) =
        TaskOccurrence(camp, phase, TaskTemplate(day, key, follow = follow), due, "week-2|$day|$key")

    @Test fun completedAndPendingFiltersFormADisjointPartitionWithoutDuplicates() {
        val tasks = listOf(task("overdue"), task("today", "4", due = date), task("daily", "all"), task("follow", follow = true))
        val completed = setOf(tasks[0].id, tasks[3].id)
        val all = taskGroups(tasks + tasks.first(), date, completed, TaskFilter.ALL).flatMap { it.tasks }.map { it.id }
        val pending = taskGroups(tasks, date, completed, TaskFilter.PENDING).flatMap { it.tasks }.map { it.id }.toSet()
        val done = taskGroups(tasks, date, completed, TaskFilter.COMPLETED).flatMap { it.tasks }.map { it.id }.toSet()
        assertEquals(tasks.map { it.id }.toSet(), all.toSet())
        assertEquals(tasks.size, all.size)
        assertTrue(pending.intersect(done).isEmpty())
        assertEquals(all.toSet(), pending + done)
        assertEquals(completed, done)
    }

    @Test fun followAndDailyTasksAreNeverMistakenForOverdueDayTasks() {
        val tasks = listOf(task("overdue"), task("today", "4", due = date), task("daily", "all"), task("follow", follow = true))
        val groups = taskGroups(tasks, date, emptySet(), TaskFilter.ALL).associate { it.bucket to it.tasks.map { t -> t.template.title } }
        assertEquals(listOf("overdue"), groups[TaskBucket.OVERDUE])
        assertEquals(listOf("today"), groups[TaskBucket.TODAY])
        assertEquals(listOf("daily"), groups[TaskBucket.DAILY])
        assertEquals(listOf("follow"), groups[TaskBucket.FOLLOW])
        assertFalse(isOverdue(tasks[2], date)); assertFalse(isOverdue(tasks[3], date))
    }

    @Test fun completedStatusTakesPriorityAndEmptyFiltersRemainEmpty() {
        val follow = task("follow", follow = true)
        val groups = taskGroups(listOf(follow), date, setOf(follow.id), TaskFilter.ALL)
        assertEquals(TaskBucket.COMPLETED, groups.single().bucket)
        assertTrue(taskGroups(listOf(follow), date, setOf(follow.id), TaskFilter.PENDING).isEmpty())
        assertTrue(taskGroups(emptyList(), date, emptySet(), TaskFilter.COMPLETED).isEmpty())
    }
}
