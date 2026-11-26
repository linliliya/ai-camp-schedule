package cn.aurange.schedule

import java.time.LocalDate

enum class TaskFilter(val label: String) { ALL("全部"), PENDING("待办"), COMPLETED("已完成") }
enum class TaskBucket(val label: String) {
    OVERDUE("逾期事项"), TODAY("当天安排"), DAILY("日常事务"), FOLLOW("持续跟进"), COMPLETED("已完成")
}
data class TaskGroup(val bucket: TaskBucket, val tasks: List<TaskOccurrence>)

fun isOverdue(task: TaskOccurrence, date: LocalDate): Boolean =
    !task.template.follow && task.template.day != "all" && task.date < date

fun taskGroups(tasks: List<TaskOccurrence>, date: LocalDate, completedIds: Set<String>, filter: TaskFilter): List<TaskGroup> {
    val visible = tasks.distinctBy { it.id }.filter { task -> when (filter) {
        TaskFilter.ALL -> true
        TaskFilter.PENDING -> task.id !in completedIds
        TaskFilter.COMPLETED -> task.id in completedIds
    } }
    val grouped = visible.groupBy { task -> when {
        task.id in completedIds -> TaskBucket.COMPLETED
        task.template.follow -> TaskBucket.FOLLOW
        isOverdue(task, date) -> TaskBucket.OVERDUE
        task.template.day == "all" -> TaskBucket.DAILY
        else -> TaskBucket.TODAY
    } }
    return TaskBucket.entries.mapNotNull { bucket -> grouped[bucket]?.let { TaskGroup(bucket, it) } }
}
