package cn.aurange.schedule

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class IssueRecord(val number: Int, val url: String, val title: String, val created: String,
                       val repository: String, val binding: String, val remoteState: String = "")
data class IssuePage(val entries: List<IssueRecord>, val nextPage: Int?)
data class IssueNotebook(val activeId: String, val drafts: List<IssueDraft>, val records: List<IssueRecord> = emptyList()) {
    val active get() = drafts.single { it.id == activeId }
    val saved get() = drafts.filter { it.hasContent }.sortedByDescending { it.updated }
    fun save(value: IssueDraft): IssueNotebook {
        require(value.id == activeId) { "草稿已切换，未覆盖其他内容。" }
        return copy(drafts = drafts.map { if (it.id == activeId) value else it })
    }
    fun replace(value: IssueDraft) = copy(activeId = value.id, drafts = drafts.filter { it.id != activeId && it.hasContent } + value)
    fun fresh(binding: String = active.binding, repository: String = active.repository): IssueNotebook {
        val next = IssueDraft(binding = binding, repository = repository)
        return copy(activeId = next.id, drafts = saved + next)
    }
    fun select(id: String): IssueNotebook {
        require(drafts.any { it.id == id }) { "这份草稿已不存在。" }
        return copy(activeId = id, drafts = drafts.filter { it.id == id || it.hasContent })
    }
    fun merge(rows: List<IssueRecord>) = copy(records = (records + rows).associateBy { "${it.binding}:${it.repository.lowercase()}:${it.number}" }.values.toList())
    companion object {
        fun empty(): IssueNotebook { val draft = IssueDraft(); return IssueNotebook(draft.id, listOf(draft)) }
        fun migrate(draft: IssueDraft, receipt: IssueReceipt?, repo: GitHubRepo?): IssueNotebook {
            val current = draft.copy(repository = draft.repository.ifEmpty { repo?.fullName.orEmpty() })
            val row = if (receipt != null && repo != null) listOf(IssueRecord(receipt.number, receipt.url, "历史提交 #${receipt.number}", "", repo.fullName, draft.binding)) else emptyList()
            return IssueNotebook(current.id, listOf(current), row)
        }
    }
}

object IssueNotebookCodec {
    fun encode(value: IssueNotebook): String = JSONObject().put("schema", 2).put("activeId", value.activeId)
        .put("drafts", JSONArray(value.drafts.map { JSONObject(IssueCodec.draft(it)) }))
        .put("records", JSONArray(value.records.map { JSONObject().put("number", it.number).put("url", it.url).put("title", it.title)
            .put("created", it.created).put("repository", it.repository).put("binding", it.binding).put("state", it.remoteState) })).toString()
    fun decode(text: String): IssueNotebook {
        val json = JSONObject(text); require(json.getInt("schema") == 2) { "无法读取草稿版本，原数据已保留。" }
        val draftsJson = json.getJSONArray("drafts")
        val drafts = (0 until draftsJson.length()).map { IssueCodec.draft(draftsJson.getJSONObject(it).toString()) }
        require(drafts.map { it.id }.distinct().size == drafts.size && drafts.count { it.id == json.getString("activeId") } == 1)
        val rows = json.getJSONArray("records")
        val records = (0 until rows.length()).map { rows.getJSONObject(it).let { row ->
            val number = row.getInt("number"); val repository = row.getString("repository")
            require(number > 0 && row.getString("url") == "https://github.com/$repository/issues/$number")
            IssueRecord(number, row.getString("url"), row.getString("title"), row.getString("created"), repository, row.getString("binding"), row.optString("state"))
        } }
        return IssueNotebook(json.getString("activeId"), drafts, records)
    }
}
