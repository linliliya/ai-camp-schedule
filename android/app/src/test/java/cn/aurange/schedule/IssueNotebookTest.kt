package cn.aurange.schedule

import org.junit.Assert.*
import org.junit.Test

class IssueNotebookTest {
    @Test fun migrationPreservesImagePathsUncertaintyAndStableIdentifier() {
        val old = IssueDraft(id = "legacy", body = "旧稿", images = listOf(IssueImage("image", "原图", 42, "image/png", "attachments/legacy/image.png")), binding = "1:2", uncertain = true)
        val repo = GitHubRepo("me", "inbox", 2, "main")
        val migrated = IssueNotebook.migrate(old, IssueReceipt(7, "https://github.com/me/inbox/issues/7"), repo)
        val loaded = IssueNotebookCodec.decode(IssueNotebookCodec.encode(migrated))
        assertEquals(old.id, loaded.active.id); assertEquals(old.images, loaded.active.images)
        assertTrue(loaded.active.uncertain); assertEquals(7, loaded.records.single().number)
    }
    @Test fun independentDraftsSurviveSwitchingAndSuccessfulSubmission() {
        val first = IssueDraft(body = "第一条", binding = "1:2")
        val book = IssueNotebook(first.id, listOf(first)).fresh()
        val second = book.active.copy(body = "第二条")
        val saved = book.save(second).select(first.id)
        assertEquals("第一条", saved.active.body)
        val success = saved.replace(IssueDraft(binding = first.binding)).merge(listOf(IssueRecord(1,"https://github.com/me/inbox/issues/1","第一条","2026-10-27","me/inbox","1:2")))
        assertEquals(listOf("第二条"), success.saved.map { it.body }); assertEquals(1,success.records.size)
        assertEquals("第二条", success.select(second.id).active.body)
    }
    @Test fun historiesAreMergedByAccountRepositoryAndNumberRatherThanTitle() {
        val row = IssueRecord(3,"https://github.com/me/inbox/issues/3","同名","2026-10-27","me/inbox","1:2")
        val book = IssueNotebook.empty().merge(listOf(row)).merge(listOf(row.copy(remoteState = "closed"), row.copy(number = 4, url = "https://github.com/me/inbox/issues/4")))
        assertEquals(2,book.records.size); assertEquals("closed",book.records.first { it.number==3 }.remoteState)
    }
    @Test fun corruptedSnapshotsAndStaleActiveSavesAreRejected() {
        val book=IssueNotebook.empty(); assertThrows(IllegalArgumentException::class.java) { book.save(IssueDraft(body="其他稿")) }
        assertThrows(Exception::class.java) { IssueNotebookCodec.decode("""{"schema":2,"activeId":"missing","drafts":[],"records":[]}""") }
    }
    @Test fun remoteHistoryExcludesPullRequestsAndKeepsPaginationFromRawResponse() {
        val repo=GitHubRepo("me","inbox",2,"main")
        val values=org.json.JSONArray()
        for(i in 1..20) values.put(org.json.JSONObject().put("number",i).put("html_url","https://github.com/me/inbox/issues/$i").put("title","条目 $i").put("created_at","2026-10-27T00:00:00Z").put("state","open").also { if(i==1)it.put("pull_request",org.json.JSONObject()) })
        val api=GitHubIssueClient(GitHubTransport { _,_,_,_->GitHubReply(200,values.toString()) })
        val page=api.issues(repo,"1:2")
        assertEquals(19,page.entries.size);assertEquals(2,page.nextPage);assertFalse(page.entries.any { it.number==1 })
    }
}
