package cn.aurange.schedule

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun IssueCaptureScreen(model: IssueCaptureViewModel, configureOnly: Boolean = false) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var settings by remember { mutableStateOf(false) }
    var clear by remember { mutableStateOf(false) }
    var library by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris -> if (uris.isNotEmpty()) model.add(uris) }
    if (configureOnly) {
        IssueSettings(model, state, onDismiss = null)
        return
    }
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LazyColumn(Modifier.fillMaxSize().padding(bottom = if (keyboardOpen) 0.dp else floatingContentBottom()).imePadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("随手记", style = MaterialTheme.typography.headlineMedium)
                    Text("先记下来，周三再慢慢整理。", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 7.dp))
                }
                IconButton(onClick = { settings = true }, enabled = state.loaded && !state.busy) { AppIcon(Glyph.Sliders, "GitHub 配置", tint = Blue) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { library = "drafts" }, enabled = state.loaded && !state.busy && !state.historyBusy) { Text("草稿 ${state.drafts.size}", fontSize = 12.sp) }
                TextButton(onClick = { library = "history" }, enabled = state.loaded && !state.busy) { Text("提交记录", fontSize = 12.sp) }
            }
        }
        item {
            Surface(color = Lavender, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(Glyph.Note, tint = Blue, modifier = Modifier.size(19.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                        Text(state.repo?.fullName ?: "连接自己的 GitHub", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(if (state.repo == null) "配置一次，就能直接提交工作琐记。" else "文字与图片提交到这个仓库", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                    TextButton(onClick = { settings = true }, enabled = state.loaded && !state.busy) { Text(if (state.repo == null) "配置" else "更换", fontSize = 12.sp) }
                }
            }
        }
        item {
            OutlinedTextField(value = state.draft.body, onValueChange = model::body,
                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp).semantics { contentDescription = "记录内容" },
                enabled = state.loaded && !state.busy && !state.draft.uncertain, minLines = 4, maxLines = 10,
                supportingText = { Text(if (state.draft.uncertain) "提交结果待核对，正文暂时保留。" else "首行作为标题 · 草稿自动保存") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), shape = RoundedCornerShape(18.dp))
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = state.loaded && !state.busy && !state.draft.uncertain && state.draft.images.size < 9) {
                        AppIcon(Glyph.Link, modifier = Modifier.size(17.dp), tint = Blue); Spacer(Modifier.width(7.dp)); Text("添加图片")
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("${state.draft.images.size}/9 张 · 每张≤10MB", modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End, color = Muted, fontSize = 12.sp)
                }
                state.draft.images.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { image ->
                            Column(Modifier.weight(1f)) {
                                IssueThumbnail(model, image)
                                TextButton(onClick = { model.remove(image.key) }, enabled = !state.busy && !state.draft.uncertain,
                                    contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth()) { Text("移除", fontSize = 12.sp) }
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { if (state.repo == null || state.session == null) settings = true else model.submit() },
                    enabled = state.loaded && !state.busy && state.draft.body.isNotBlank(), modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp)) {
                    if (state.busy) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(9.dp)) }
                    Text(if (state.busy) state.progress else if (state.draft.uncertain) "核对提交结果" else "提交 Issue")
                }
                if (state.draft.hasContent) TextButton(onClick = { clear = true }, enabled = !state.busy, modifier = Modifier.align(Alignment.End)) { Text("清空草稿", color = Muted, fontSize = 12.sp) }
            }
        }
        if (state.notice.isNotBlank()) item {
            Surface(color = if (state.receipt != null) phaseBackground("regular") else Lavender, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(state.notice, fontSize = 13.sp, color = Ink)
                    state.receipt?.let { receipt -> TextButton(onClick = { ExternalLinks.document(context, receipt.url) }, contentPadding = PaddingValues(0.dp)) { Text("查看 Issue #${receipt.number}") } }
                }
            }
        }
    }
    if (settings) AlertDialog(onDismissRequest = { if (!state.busy || state.userCode.isNotEmpty()) { model.cancelConnect(); settings = false } },
        title = { Text("GitHub 配置") }, text = { IssueSettings(model, state, onDismiss = { settings = false }) },
        confirmButton = { TextButton(onClick = { model.cancelConnect(); settings = false }, enabled = !state.busy || state.userCode.isNotEmpty()) { Text("完成") } })
    library?.let { page -> IssueLibraryDialog(state, page == "history", model, configure = { library = null; settings = true }) { library = null } }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("清空这份草稿？") },
        text = { Column { Text(if (state.draft.uncertain) "提交结果尚未确认。请先到 GitHub 查看，确定不需要这份本机草稿后再清空。GitHub 上的记录和附件会保留。" else "会移除本机保存的文字与所选图片。已经上传到 GitHub 的附件保留。")
            if (state.draft.uncertain) state.repo?.let { repo -> TextButton(onClick = { ExternalLinks.document(context, "https://github.com/${repo.fullName}/issues") }) { Text("打开 GitHub 核对") } }
        } },
        confirmButton = { TextButton(onClick = { clear = false; model.clear(manualVerified = state.draft.uncertain) }) { Text(if (state.draft.uncertain) "已核对，清空本机草稿" else "清空", color = Danger) } },
        dismissButton = { TextButton(onClick = { clear = false }) { Text("保留") } })
}

@Composable private fun IssueThumbnail(model: IssueCaptureViewModel, image: IssueImage) {
    var bitmap by remember(image.key) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(image.key) {
        bitmap = withContext(Dispatchers.IO) {
            val file = model.store.image(image.key)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, options)
            var sample = 1
            while (options.outWidth / sample > 360 || options.outHeight / sample > 360) sample *= 2
            runCatching { BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) }.getOrNull()
        }
    }
    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(13.dp)).background(Lavender), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), image.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: Text("图片", color = Muted, fontSize = 12.sp)
    }
}

@Suppress("DEPRECATION")
@Composable private fun IssueSettings(model: IssueCaptureViewModel, state: IssueUiState, onDismiss: (() -> Unit)?) {
    val context = LocalContext.current; val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (onDismiss == null) Text("GitHub 配置", style = MaterialTheme.typography.titleLarge)
        Text(state.session?.let { "已连接 ${it.login}" } ?: "连接自己的账号，提交到自己的仓库。", color = Ink, fontSize = 14.sp)
        state.repo?.let { Text("当前仓库：${it.fullName}", color = Muted, fontSize = 13.sp) }
        if (state.userCode.isNotEmpty()) {
            Surface(color = Lavender, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(state.userCode, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Blue)
                    Text("在 GitHub 官方网页输入短码并确认，再返回 App。", fontSize = 12.sp, color = Muted)
                    Button(onClick = { clipboard.setText(AnnotatedString(state.userCode)); ExternalLinks.document(context, state.verificationUrl) }) { Text("复制短码并打开 GitHub") }
                    TextButton(onClick = model::cancelConnect) { Text("取消等待") }
                }
            }
        } else if (state.activationRequired) {
            Surface(color = Lavender, shape = RoundedCornerShape(12.dp)) {
                Text("GitHub 连接暂未启用。草稿已保留，管理员启用后即可连接。", fontSize = 13.sp, color = Ink, modifier = Modifier.padding(14.dp))
            }
            OutlinedButton(onClick = model::connect, enabled = state.loaded && !state.busy, modifier = Modifier.fillMaxWidth()) { Text("重新检查连接") }
        } else {
            Button(onClick = model::connect, enabled = state.loaded && !state.busy, modifier = Modifier.fillMaxWidth()) { Text(if (state.session == null) "连接 GitHub" else "重新授权当前账号") }
        }
        if (state.session != null && state.userCode.isEmpty()) {
            OutlinedButton(onClick = { model.install { ExternalLinks.document(context, it) } }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("在 GitHub 授权仓库") }
            TextButton(onClick = { ExternalLinks.document(context, "https://github.com/new") }, enabled = !state.busy) { Text("没有仓库？去 GitHub 新建私有仓库") }
            OutlinedButton(onClick = model::repositories, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("刷新可用仓库") }
            state.repositories.forEach { repo ->
                OutlinedButton(onClick = { model.choose(repo) }, enabled = !state.busy && (state.repo?.id == repo.id || !state.draft.hasContent || state.draft.binding.isEmpty()),
                    modifier = Modifier.fillMaxWidth()) { Text((if (state.repo?.id == repo.id) "✓ " else "") + repo.fullName, fontSize = 12.sp) }
            }
            Text("只显示自己拥有且已授权的私有仓库。没有找到时，先在 GitHub 给仓库授权，再刷新。", color = Muted, fontSize = 12.sp)
            TextButton(onClick = model::signOut, enabled = !state.busy && !state.historyBusy && !state.hasSavedContent) { Text("退出本机 GitHub / 更换账号", color = Muted, fontSize = 12.sp) }
            if (state.hasSavedContent) Text("更换账号前先处理未提交草稿；切换草稿时会恢复它原先的仓库。", color = Muted, fontSize = 12.sp)
        }
        if (state.busy) Text(state.progress, color = Muted, fontSize = 12.sp)
        if (state.notice.isNotBlank() && !state.activationRequired) Text(state.notice, color = Ink, fontSize = 12.sp)
    }
}
