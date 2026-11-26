package cn.aurange.schedule

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

val BUSINESS_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

data class Camp(val id: String, val term: Int, val openDate: LocalDate, val skips: Set<Int> = emptySet(), val version: Int = 1, val examWeek: Int = 7)
data class TaskTemplate(
    val day: String, val title: String, val description: String = "", val time: String = "",
    val links: List<String> = emptyList(), val follow: Boolean = false, val notW1: Boolean = false,
    val originalTitle: String? = null, val originalDay: String? = null, val originalWeek: String? = null, val afterDays: Long = 0, val afterMonths: Long = 0
)
data class DocumentLink(val text: String, val url: String)
data class SopData(
    val url: String, val publicKey: String, val links: Map<String, DocumentLink>,
    val prep: List<TaskTemplate>, val open: List<TaskTemplate>, val rest: List<TaskTemplate>,
    val regular: List<TaskTemplate>, val extras: Map<Int, List<TaskTemplate>>, val closing: List<TaskTemplate>, val tracking: List<TaskTemplate> = emptyList(), val exam: Map<Int, List<TaskTemplate>> = emptyMap()
)
data class Phase(val key: String, val label: String, val short: String, val type: String, val start: LocalDate, val end: LocalDate, val week: Int = 0,
                 val examOffset: Int? = null, val courseScript: String? = null)
data class TaskState(val completed: Boolean, val version: Int)
data class Snapshot(val camps: List<Camp> = emptyList(), val tasks: Map<String, TaskState> = emptyMap(), val syncedAt: Long = 0)
data class TaskOccurrence(val camp: Camp, val phase: Phase, val template: TaskTemplate, val date: LocalDate, val key: String) {
    val id: String get() = "${camp.id}|$key"
    val clockTime: LocalTime? get() {
        val match = Regex("^(\\d{1,2}):(\\d{2})(?:前)?$").matchEntire(template.time) ?: return null
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        if (hour == 24 && minute == 0) return LocalTime.MIDNIGHT
        return if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
    }
    val clockDate: LocalDate get() = if (template.time == "24:00" || template.time == "24:00前") date.plusDays(1) else date
}
data class ReminderSettings(val enabled: Boolean = true, val summaryTime: LocalTime = LocalTime.of(9, 0), val leadMinutes: Int = 15)
data class Session(val accessToken: String, val refreshToken: String, val expiresAt: Long, val email: String, val userId: String)

class AccessDenied(message: String) : Exception(message)
class LoginExpired : Exception("登录已过期，请重新登录。")
class CloudFailure(val status: Int, val code: String, message: String) : Exception(message)
