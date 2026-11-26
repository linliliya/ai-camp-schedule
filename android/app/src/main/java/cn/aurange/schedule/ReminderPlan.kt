package cn.aurange.schedule

import java.time.Instant
import java.time.LocalDate

data class ReminderEvent(val id: String, val at: Long, val taskId: String = "", val summary: Boolean = false)
data class AlarmBatch(val at: Long, val events: List<ReminderEvent>)

class ReminderPlan(private val engine: ScheduleEngine) {
    fun next(snapshot: Snapshot, settings: ReminderSettings, now: Instant,
             custom: Map<String, Long> = emptyMap(), delivered: Set<String> = emptySet(), previous: AlarmBatch? = null): AlarmBatch? {
        if (!settings.enabled) return null
        // A sync may rebuild the alarm just after it becomes due, before its receiver runs.
        // Recover only a real, recent, undelivered batch; empty midnight maintenance must advance.
        val planningTime = previous?.takeIf { batch -> batch.events.isNotEmpty() && batch.at <= now.toEpochMilli() &&
            now.toEpochMilli() - batch.at < 3_600_000 && batch.events.any { it.id !in delivered } }
            ?.let { Instant.ofEpochMilli(it.at - 1) } ?: now
        val today = planningTime.atZone(BUSINESS_ZONE).toLocalDate()
        val candidates = mutableListOf<ReminderEvent>()
        val all = engine.allTasks(snapshot).filterNot { engine.complete(snapshot, it) }
        for (offset in 0L..2L) {
            val date = today.plusDays(offset)
            if (engine.today(snapshot, date).any { !engine.complete(snapshot, it) }) {
                val at = date.atTime(settings.summaryTime).atZone(BUSINESS_ZONE).toInstant().toEpochMilli()
                candidates += ReminderEvent("summary:$date:$at", at, summary = true)
            }
        }
        for (task in all) {
            val customAt = custom[task.id]
            val at = if (customAt != null) customAt else {
                val time = task.clockTime ?: continue
                // Look one day behind as well: 24:00 belongs to the following civil day.
                if (task.clockDate < today.minusDays(1) || task.clockDate > today.plusDays(3)) continue
                task.clockDate.atTime(time).atZone(BUSINESS_ZONE).toInstant()
                    .minusSeconds(settings.leadMinutes.toLong() * 60).toEpochMilli()
            }
            candidates += ReminderEvent("${task.id}:$at", at, task.id)
        }
        val pending = candidates.filter { it.at > planningTime.toEpochMilli() && it.id !in delivered }
        val midnight = today.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant().toEpochMilli()
        val first = minOf(pending.minOfOrNull { it.at } ?: midnight, midnight)
        // Empty midnight batches keep plans fresh without posting an empty notification.
        return AlarmBatch(first, pending.filter { it.at == first })
    }

    fun summaryTasks(snapshot: Snapshot, date: LocalDate): List<TaskOccurrence> =
        engine.today(snapshot, date).filterNot { engine.complete(snapshot, it) }
}
