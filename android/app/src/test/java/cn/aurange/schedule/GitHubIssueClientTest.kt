package cn.aurange.schedule

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class GitHubIssueClientTest {
    private val repo = GitHubRepo("person", "inbox", 42, "main")
    @Test fun rateLimitAfterMissingApplicationReplacesThePreviousActivationMessage() {
        var response = GitHubReply(404, "{}")
        val client = GitHubIssueClient(GitHubTransport { _, _, _, _ -> response })
        var state = IssueUiState(loaded = true)
        try { client.app("expected-app", "person"); fail() } catch (error: GitHubProblem) { state = state.failed(error) }
        assertTrue(state.activationRequired)
        response = GitHubReply(403, """{"message":"API rate limit exceeded for 192.0.2.1"}""", mapOf("x-ratelimit-remaining" to "0"))
        try { client.app("expected-app", "person"); fail() } catch (error: GitHubProblem) { state = state.failed(error) }
        assertFalse(state.activationRequired)
        assertTrue(state.notice.contains("限流"))
        assertFalse(state.notice.contains("192.0.2.1"))
        assertFalse(state.notice.contains("仓库授权"))
    }
    @Test fun disabledDeviceFlowHasAnActionableSetupError() {
        val client = GitHubIssueClient(GitHubTransport { _, _, _, _ -> GitHubReply(400, """{"error":"device_flow_disabled"}""") })
        try { client.device("public-client"); fail() }
        catch (error: GitHubProblem) { assertTrue(error.activationRequired); assertTrue(error.message.orEmpty().contains("设备流")) }
    }
    @Test fun draftRoundTripRetainsDestinationImagesAndUncertainSubmission() {
        val draft = IssueDraft(body = "标题\n正文", binding = "5:42", uncertain = true,
            images = listOf(IssueImage("image-id", "课程.png", 12, "image/png", "attachments/path.png")))
        assertEquals(draft, IssueCodec.draft(IssueCodec.draft(draft)))
        assertEquals(repo, IssueCodec.repo(IssueCodec.repo(repo)))
    }
    @Test fun issueCreationKeepsFullBodyAndUsesAutomaticTitleAndStableMarker() {
        val draft = IssueDraft(body = "\n# 第3周\n完整话术与链接 https://example.com?a=1&b=2")
        val client = GitHubIssueClient(GitHubTransport { method, url, token, body ->
            assertEquals("POST", method); assertEquals("https://api.github.com/repos/person/inbox/issues", url); assertEquals("private-test-token", token)
            val data = JSONObject(body!!)
            assertEquals("第3周", data.getString("title")); assertTrue(data.getString("body").contains("完整话术与链接")); assertTrue(data.getString("body").contains(GitHubIssueClient.marker(draft)))
            GitHubReply(201, """{"number":7,"html_url":"https://github.com/person/inbox/issues/7"}""")
        }, "private-test-token")
        assertEquals(7, client.create(repo, draft).number)
    }
    @Test fun incompleteImageUploadNeverCreatesAnIssue() {
        var calls = 0
        val client = GitHubIssueClient(GitHubTransport { _, _, _, _ -> calls++; GitHubReply(201, "{}") })
        try { client.create(repo, IssueDraft(body = "图片说明", images = listOf(IssueImage("a", "x.png", 2, "image/png")))); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(0, calls)
    }
    @Test fun existingImageIsVerifiedByGitBlobHashAndNotUploadedTwice() {
        val bytes = byteArrayOf(1, 2, 3); val draft = IssueDraft(body = "截图")
        val image = IssueImage("stable-image", "原图.png", 3, "image/png")
        val sha = MessageDigest.getInstance("SHA-1").digest("blob 3\u0000".toByteArray() + bytes).joinToString("") { "%02x".format(it) }
        val methods = mutableListOf<String>()
        val client = GitHubIssueClient(GitHubTransport { method, _, _, _ -> methods += method; GitHubReply(200, JSONObject().put("sha", sha).toString()) })
        assertEquals("attachments/${draft.id}/stable-image.png", client.upload(repo, draft, image, bytes))
        assertEquals(listOf("GET"), methods)
    }
    @Test fun newImageUploadPreservesOriginalBytesAndDoesNotOverwriteFiles() {
        val bytes = byteArrayOf(0, 1, 2, -1, 100); val draft = IssueDraft(body = "原图")
        var calls = 0
        val client = GitHubIssueClient(GitHubTransport { method, url, _, body ->
            calls++; assertTrue(url.contains("/contents/attachments/${draft.id}/picture.png"))
            if (method == "GET") GitHubReply(404, "{}") else {
                assertEquals("PUT", method)
                val request = JSONObject(body!!)
                assertArrayEquals(bytes, java.util.Base64.getDecoder().decode(request.getString("content")))
                assertFalse(request.has("sha"))
                GitHubReply(201, "{}")
            }
        })
        client.upload(repo, draft, IssueImage("picture", "截图.png", bytes.size.toLong(), "image/png"), bytes)
        assertEquals(2, calls)
    }
    @Test fun repositoryPickerReadsEveryPageAndExcludesOtherOwnersPublicAndDisabledRepositories() {
        var repoPages = 0
        fun item(id: Int) = JSONObject().put("id", id).put("name", "inbox-$id").put("private", true).put("has_issues", true)
            .put("owner", JSONObject().put("login", "person")).put("default_branch", "main")
        val client = GitHubIssueClient(GitHubTransport { _, url, _, _ ->
            if (url.contains("/user/installations?")) GitHubReply(200, JSONObject().put("installations", JSONArray().put(JSONObject().put("id", 8).put("account", JSONObject().put("login", "person")))).toString())
            else {
                repoPages++
                val entries = if (repoPages == 1) (1..100).map { id -> item(id).apply {
                    if (id == 1) put("private", false)
                    if (id == 2) put("archived", true)
                    if (id == 3) put("owner", JSONObject().put("login", "other"))
                    if (id == 4) put("has_issues", false)
                } } else listOf(item(101))
                GitHubReply(200, JSONObject().put("repositories", JSONArray(entries)).toString())
            }
        })
        val repos = client.repositories("person")
        assertEquals(97, repos.size); assertEquals(2, repoPages)
        assertTrue(repos.all { it.owner == "person" && it.id >= 5 })
        assertTrue(repos.any { it.id == 101L })
    }
    @Test fun uncertainSubmissionLookupTraversesPagesAndDoesNotTreatPullRequestsAsMatches() {
        val draft = IssueDraft(body = "需要核对", uncertain = true); var calls = 0
        val client = GitHubIssueClient(GitHubTransport { method, url, _, _ ->
            calls++; assertEquals("GET", method); assertTrue(url.contains("state=all"))
            if (calls == 1) GitHubReply(200, JSONArray((1..100).map { JSONObject().put("number", it).put("body", GitHubIssueClient.marker(draft)).put("pull_request", JSONObject()) }).toString())
            else GitHubReply(200, JSONArray().put(JSONObject().put("number", 101).put("body", GitHubIssueClient.marker(draft))).toString())
        })
        assertEquals(101, client.find(repo, draft)!!.number); assertEquals(2, calls)
    }
    @Test fun serverErrorsAndMalformedCreatedReceiptsRequireReconciliation() {
        for (reply in listOf(GitHubReply(503, "private details"), GitHubReply(201, "{}"))) {
            try { GitHubIssueClient(GitHubTransport { _, _, _, _ -> reply }).create(repo, IssueDraft(body = "测试")); fail() }
            catch (error: GitHubProblem) { assertTrue(error.uncertain); assertFalse(error.message.orEmpty().contains("private details")) }
        }
        try { GitHubIssueClient(GitHubTransport { _, _, _, _ -> GitHubReply(401, "secret token") }).create(repo, IssueDraft(body = "测试")); fail() }
        catch (error: GitHubProblem) { assertFalse(error.uncertain); assertFalse(error.message.orEmpty().contains("secret token")) }
    }
    @Test fun deviceRequestsAndRefreshDoNotIncludeAnApplicationSecret() {
        val requests = mutableListOf<JSONObject>()
        val client = GitHubIssueClient(GitHubTransport { _, url, token, body ->
            assertTrue(url.startsWith("https://github.com/login/")); assertEquals("", token)
            requests += JSONObject(body!!); GitHubReply(200, "{}")
        })
        client.device("public-client"); client.poll("public-client", "device-code"); client.refresh("public-client", "refresh-code")
        assertTrue(requests.all { !it.has("client_secret") }); assertEquals("refresh_token", requests.last().getString("grant_type"))
    }
    @Test fun applicationIdentityAndPermissionsMustMatchBeforeDeviceAuthorization() {
        val reply = JSONObject().put("owner", JSONObject().put("login", "someone-else")).put("permissions", JSONObject().put("issues", "write").put("contents", "write"))
        try { GitHubIssueClient(GitHubTransport { _, _, _, _ -> GitHubReply(200, reply.toString()) }).app("expected-app", "person"); fail() }
        catch (_: IllegalStateException) { }
    }
    @Test fun inputLimitsAndRepositoryPathsCannotBypassValidation() {
        for (draft in listOf(IssueDraft(body = " "), IssueDraft(body = "x".repeat(55001)), IssueDraft(body = "x", images = List(10) { IssueImage("a", "x.png", 1, "image/png") }), IssueDraft(body = "x", images = listOf(IssueImage("a", "x.png", 10_000_001, "image/png"))))) {
            try { GitHubIssueClient.validate(draft); fail() } catch (_: IllegalArgumentException) { }
        }
        try { GitHubRepo("person", "../other", 5, "main"); fail() } catch (_: IllegalArgumentException) { }
    }
}
