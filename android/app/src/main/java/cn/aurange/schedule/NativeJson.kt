package cn.aurange.schedule

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
fun JSONObject.optionalString(key: String): String? = if (has(key) && !isNull(key)) get(key).toString() else null

object NativeJson {
    fun sop(text: String): SopData {
        val json = JSONObject(text)
        val config = json.getJSONObject("config")
        fun tasks(array: JSONArray): List<TaskTemplate> = array.objects().map { t ->
            val links = t.optJSONArray("links")?.let { a -> (0 until a.length()).map { a.getString(it) } }
                ?: t.optionalString("link")?.let { listOf(it) }.orEmpty()
            TaskTemplate(t.get("day").toString(), t.getString("t"), t.optString("d"), t.optString("time"), links,
                t.optBoolean("follow"), t.optBoolean("notW1"), t.optionalString("id"),
                t.optionalString("idDay"), t.optionalString("idWeek"), t.optLong("afterDays"), t.optLong("afterMonths"))
        }
        val linkJson = json.getJSONObject("links")
        val links = linkJson.keys().asSequence().associateWith { key ->
            val link = linkJson.getJSONObject(key); DocumentLink(link.getString("text"), link.getString("url"))
        }
        fun taskMap(value: JSONObject) = value.keys().asSequence().associate { it.toInt() to tasks(value.getJSONArray(it)) }
        return SopData(config.getString("url"), config.getString("key"), links,
            tasks(json.getJSONArray("prep")), tasks(json.getJSONArray("open")), tasks(json.getJSONArray("rest")),
            tasks(json.getJSONArray("regular")), taskMap(json.getJSONObject("extras")),
            tasks(json.getJSONArray("closing")), tasks(json.optJSONArray("tracking") ?: JSONArray()),
            taskMap(json.optJSONObject("exam") ?: JSONObject()))
    }

    fun snapshot(text: String, syncedAt: Long? = null): Snapshot {
        val json = JSONObject(text)
        val camps = json.getJSONArray("camps").objects().map { c ->
            val skips = c.getJSONArray("skips")
            Camp(c.getString("id"), c.getInt("term"), LocalDate.parse(c.getString("open_date")),
                (0 until skips.length()).map { skips.getInt(it) }.toSet(), c.getInt("version"), c.optInt("exam_week", 7))
        }
        val tasks = json.getJSONArray("tasks").objects().associate { t ->
            "${t.getString("camp_id")}|${t.getString("task_key")}" to TaskState(t.getBoolean("completed"), t.getInt("version"))
        }
        return Snapshot(camps, tasks, syncedAt ?: json.optLong("syncedAt"))
    }

    fun snapshot(snapshot: Snapshot): String = JSONObject().apply {
        put("syncedAt", snapshot.syncedAt)
        put("camps", JSONArray(snapshot.camps.map { c -> JSONObject().apply {
            put("id", c.id); put("term", c.term); put("open_date", c.openDate.toString())
            put("skips", JSONArray(c.skips.sorted())); put("version", c.version); put("exam_week", c.examWeek)
        } }))
        put("tasks", JSONArray(snapshot.tasks.map { (key, state) -> JSONObject().apply {
            put("camp_id", key.substringBefore('|')); put("task_key", key.substringAfter('|'))
            put("completed", state.completed); put("version", state.version)
        } }))
    }.toString()
}
