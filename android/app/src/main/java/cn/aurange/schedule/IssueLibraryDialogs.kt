package cn.aurange.schedule

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.format.DateTimeFormatter

private fun noteTime(value: String): String = runCatching {
    Instant.parse(value).atZone(BUSINESS_ZONE).format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
}.getOrElse { "较早记录" }

@Composable fun IssueLibraryDialog(state: IssueUiState, history: Boolean, model: IssueCaptureViewModel, configure: () -> Unit, dismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (history) "提交记录" else "本机草稿") }, text = {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 470.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (!history) {
                item {
                    Text("保存在这台手机。打开草稿后，可用“清空草稿”删除当前稿。", color = Muted, fontSize = 12.sp)
                    OutlinedButton(onClick = { model.newDraft(); dismiss() }, enabled = state.loaded && !state.busy && !state.historyBusy,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("存下这条，再记一条") }
                }
                if (state.drafts.isEmpty()) item { Text("还没有草稿，直接记下第一条吧。", fontSize = 13.sp, color = Muted) }
                items(state.drafts.size, key = { state.drafts[it].id }) { index ->
                    val draft = state.drafts[index]
                    OutlinedButton(onClick = { model.selectDraft(draft.id); dismiss() }, enabled = !state.busy && !state.historyBusy, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(GitHubIssueClient.title(draft.body).ifBlank { "图片草稿" }, maxLines = 2, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(listOfNotNull(noteTime(draft.updated), if (draft.images.isNotEmpty()) "${draft.images.size} 张图片" else null,
                                if (draft.uncertain) "提交待核对" else null, if (draft.id == state.draft.id) "当前稿" else null).joinToString(" · "), fontSize = 11.sp, color = Muted)
                            if (draft.repository.isNotEmpty()) Text(draft.repository, fontSize = 11.sp, color = Muted)
                        }
                    }
                }
            } else {
                item {
                    Text("已提交表示 GitHub 已接收。入库与飞书同步进度，请查看对应 Issue 或电脑看板。", color = Muted, fontSize = 12.sp)
                    Text(if (state.historyChecked.isEmpty()) "当前显示本机记录" else "最近刷新：${noteTime(state.historyChecked)}（北京时间）", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp))
                    if (state.session == null || state.repo == null) {
                        OutlinedButton(onClick = configure, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("配置 GitHub") }
                    } else OutlinedButton(onClick = { model.history() }, enabled = !state.historyBusy && !state.busy,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text(if (state.historyBusy) "正在刷新…" else "刷新 GitHub 记录") }
                    state.repo?.let { repo -> TextButton(onClick = { ExternalLinks.document(context, "https://github.com/${repo.fullName}/issues") }) { Text("打开仓库全部记录") } }
                }
                if (state.historyError.isNotEmpty()) item { Text(state.historyError, fontSize = 12.sp, color = Danger) }
                if (state.records.isEmpty()) item { Text(if (state.session == null || state.repo == null) "暂无本机提交记录。配置 GitHub 后可读取自己的仓库。" else "暂无记录。可刷新或在 GitHub 核对。", color = Muted, fontSize = 13.sp) }
                items(state.records.size, key = { "${state.records[it].repository}:${state.records[it].number}" }) { index ->
                    val row = state.records[index]
                    OutlinedButton(onClick = { ExternalLinks.document(context, row.url) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("#${row.number} ${row.title}", fontSize = 14.sp, color = Ink, maxLines = 3)
                            Text(noteTime(row.created) + " · " + when (row.remoteState) {
                                "closed" -> "GitHub 已关闭"; "open" -> "GitHub 未关闭"; else -> "已提交"
                            }, fontSize = 11.sp, color = Muted)
                        }
                    }
                }
                if (state.nextHistoryPage != null && state.historyChecked.isNotEmpty()) item {
                    TextButton(onClick = { model.history(reset = false) }, enabled = !state.historyBusy && !state.busy, modifier = Modifier.fillMaxWidth()) { Text("加载更多") }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("返回") } })
}
