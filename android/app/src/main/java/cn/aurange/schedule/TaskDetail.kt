package cn.aurange.schedule

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TaskDetail(task: TaskOccurrence, state: AppState, model: ScheduleViewModel, onDismiss: () -> Unit,
                          onNote: ((String) -> Boolean)? = null) {
    val context = LocalContext.current
    val completed = model.engine.complete(state.displaySnapshot, task)
    val saving = task.id in state.pendingCompletions
    val custom = state.custom[task.id]
    var detailMessage by remember(task.id) { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Canvas) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.94f)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PhaseBadge(task.phase, "第${task.camp.term}期 · ${task.phase.label}")
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) { AppIcon(Glyph.ChevronDown, "收起任务", tint = Ink) }
                }
                Text(task.template.title, style = MaterialTheme.typography.headlineMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppIcon(Glyph.Calendar, modifier = Modifier.size(15.dp), tint = Blue)
                    val label = if (task.template.day == "all" && task.phase.type != "tracking") "全周 / 日常" else task.date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", java.util.Locale.CHINA))
                    Text(label + if (task.template.time.isNotEmpty()) " · ${task.template.time}" else "", fontSize = 12.sp, color = Muted)
                }
                if (task.template.description.isNotEmpty()) WhiteCard {
                    Text("任务说明", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(task.template.description, color = Navy, fontSize = 14.sp, lineHeight = 24.sp)
                    if (task.template.follow) Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(Glyph.Repeat, tint = phaseColor("prep"), modifier = Modifier.size(14.dp))
                        Text("持续跟进，完成后移出每日待办", color = phaseColor("prep"), fontSize = 12.sp)
                    }
                }
                TaskScriptCards(task, model.engine)
                if (onNote != null) TextButton(onClick = {
                    val text = "${task.template.title}\n\n补充内容：\n\n来源：第${task.camp.term}期 · ${task.phase.label} · ${task.date}\n"
                    if (!onNote(text)) detailMessage = "随手记正在处理，请稍后重试。"
                }) { AppIcon(Glyph.Note, tint = Blue); Spacer(Modifier.width(8.dp)); Text("围绕这项任务记一条") }
                val links = task.template.links.mapNotNull { model.repo.sop.links[it] }
                if (links.isNotEmpty()) {
                    SectionTitle("相关资料")
                    for (link in links) OutlinedButton(onClick = {
                        if (!ExternalLinks.document(context, link.url)) detailMessage = "暂时无法打开链接，请检查是否已安装飞书或浏览器。"
                    }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 15.dp, vertical = 14.dp), shape = RoundedCornerShape(15.dp)) {
                        AppIcon(Glyph.Book, tint = Blue, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(9.dp))
                        Text((if (java.net.URI(link.url).host.orEmpty().endsWith(".feishu.cn")) "在飞书打开 · " else "打开 · ") + link.text,
                            fontSize = 12.sp, modifier = Modifier.weight(1f))
                        AppIcon(Glyph.ArrowUpRight, tint = Blue, modifier = Modifier.size(16.dp))
                    }
                }
                WhiteCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppIcon(Glyph.Bell, tint = Blue, modifier = Modifier.size(17.dp))
                        Text("本机提醒", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(if (custom != null) "已设为 " + Instant.ofEpochMilli(custom).atZone(BUSINESS_ZONE).format(DateTimeFormatter.ofPattern("M月d日 HH:mm")) + "（北京时间）"
                        else if (task.clockTime != null) { if (state.settings.leadMinutes == 0) "默认按任务时间准时提醒" else "默认提前 ${state.settings.leadMinutes} 分钟提醒" }
                        else "此任务会进入每日汇总，也可以设置具体提醒时刻。", color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
                    OutlinedButton(onClick = {
                        if (state.demo) { detailMessage = "演示模式不保存提醒，请先登录。"; return@OutlinedButton }
                        val initial = custom?.let { Instant.ofEpochMilli(it).atZone(BUSINESS_ZONE).toLocalDateTime() }
                            ?.takeIf { it.isAfter(LocalDateTime.now(BUSINESS_ZONE)) } ?: LocalDateTime.now(BUSINESS_ZONE).plusHours(1)
                        DatePickerDialog(context, { _, year, month, day ->
                            TimePickerDialog(context, { _, hour, minute ->
                                val at = LocalDate.of(year, month + 1, day).atTime(hour, minute).atZone(BUSINESS_ZONE).toInstant().toEpochMilli()
                                if (at <= System.currentTimeMillis()) detailMessage = "请选择未来的提醒时间。"
                                else { model.custom(task.id, at); onDismiss() }
                            }, initial.hour, initial.minute, true).show()
                        }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
                    }, enabled = !completed && !state.busy && !saving, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Text(if (custom == null) "设定提醒时间" else "修改提醒时间", fontSize = 12.sp)
                    }
                    if (custom != null) TextButton(onClick = { model.custom(task.id, null) }, enabled = !state.busy) { Text("取消单独提醒", fontSize = 12.sp) }
                    if (!state.settings.enabled) Text("日程提醒已暂停，请在设置中开启。", color = Amber, fontSize = 12.sp)
                }
                if (detailMessage.isNotEmpty()) Text(detailMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, lineHeight = 20.sp)
                if (state.demo || !state.online) Text(if (state.demo) "演示模式仅供预览，登录后可同步完成状态。" else "当前显示离线缓存，联网同步后可标记完成。", color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
            }
            Column(Modifier.background(Canvas).padding(horizontal = 22.dp, vertical = 12.dp)) {
                Button(onClick = { model.complete(task) }, enabled = state.online && !state.demo && !state.busy && !saving,
                    modifier = Modifier.fillMaxWidth().height(49.dp), shape = RoundedCornerShape(15.dp)) {
                    val locked = state.demo || !state.online || state.busy
                    AppIcon(if (locked) Glyph.Lock else if (completed) Glyph.Repeat else Glyph.Check,
                        tint = if (state.online && !state.demo && !state.busy) Color.White else Muted, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp)); Text(when {
                        saving -> "正在保存…"
                        state.demo -> if (completed) "已完成 · 演示只读" else "演示模式（只读）"
                        !state.online -> if (completed) "已完成 · 联网后可修改" else "联网后可标记完成"
                        state.busy -> "同步中，请稍后操作"
                        completed -> "恢复为未完成"
                        else -> "标记为已完成"
                    })
                }
            }
        }
    }
}
