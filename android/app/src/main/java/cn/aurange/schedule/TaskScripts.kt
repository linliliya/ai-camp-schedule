package cn.aurange.schedule

import org.json.JSONObject

data class TaskScript(val id: String, val title: String, val category: String, val audience: String, val body: String,
                      val notes: List<String>, val source: String, val blankFields: List<String> = emptyList(), val contextLabel: String = "") {
    val variables get() = Regex("【[^【】\\n]+】|\\{\\{[^}\\n]+\\}\\}").findAll(body).map { it.value }.distinct().filterNot {
        Regex("^【第\\d+周").containsMatchIn(it) || it in setOf("【学习成果调研问卷】", "【电科云】", "【上周学习打卡排行榜·重磅出炉】", "【重磅发布！上周学习王者是谁？】", "【上周排名战报！你上榜了吗？】")
    }.toList().plus(blankFields).distinct()
}
fun TaskScript.forWeek(week: Int): TaskScript {
    var text = body.replace("【周次】", week.toString())
        .replace("本周日（【作业截止日期】）", "本周日")
        .replace("【作业截止日期】", "本周日")
        .replace("截止：【报名截止日期】中午12点", "截止时间以本期报名通知为准")
        .replace("【本期实名认证截止日期及时间】", "以本期通知为准")
    val missing = copy(body = text).variables.toMutableList()
    missing.forEach { text = text.replace(it, "") }
    val links = Regex("https?://[^\\s]+")
    if (id !in setOf("outcome-survey", "outcome-followup")) {
        if (links.containsMatchIn(text)) missing += "相关资料链接"
        text = text.replace(links, "")
    }
    return copy(body = text, blankFields = missing.distinct())
}
class TaskScripts(text: String) {
    private val json = JSONObject(text)
    val version: String = json.getString("version")
    private val scripts = json.getJSONArray("scripts").let { array -> (0 until array.length()).map { array.getJSONObject(it) }.associate { value ->
        val source = value.getJSONObject("source")
        val notes = value.getJSONArray("notes")
        value.getString("id") to TaskScript(value.getString("id"), value.getString("title"), value.getString("category"), value.getString("audience"),
            value.getString("body"), (0 until notes.length()).map { notes.getString(it) }, source.getString("file").substringAfterLast('/') + " · " + source.getString("section"))
    } }
    init { require(json.getInt("schema") == 1) }
    fun forKey(key: String): List<TaskScript> = json.getJSONObject("mapping").optJSONArray(key)?.let { ids -> (0 until ids.length()).mapNotNull { scripts[ids.getString(it)] } }.orEmpty()
    fun forTask(task: TaskOccurrence): List<TaskScript> =
        if (task.template.title == "发布课程及打卡等更新提醒") listOfNotNull(task.phase.courseScript?.let(scripts::get))
        else forKey(task.key)

    fun forCamp(task: TaskOccurrence, engine: ScheduleEngine): List<TaskScript> {
        val occurrences=engine.phases(task.camp).flatMap { engine.tasks(task.camp,it) } + engine.trackingTasks(task.camp)
        return scripts.values.mapNotNull { row ->
            val candidates=occurrences.filter { occurrence -> forTask(occurrence).any { it.id==row.id } }
            val selected=candidates.firstOrNull { it.phase.key==task.phase.key } ?: candidates.firstOrNull() ?: return@mapNotNull null
            row.forWeek(selected.phase.week).copy(contextLabel="第${task.camp.term}期 · ${selected.phase.label}")
        }
    }
}
