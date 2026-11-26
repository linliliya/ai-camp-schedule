package cn.aurange.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val campDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable fun AddCampSheet(state: AppState, onDismiss: () -> Unit, onSave: (CampDraft) -> Unit, onRefresh: () -> Unit = {}, original: Camp? = null) {
    var term by rememberSaveable { mutableStateOf((original?.term ?: ((state.snapshot.camps.maxOfOrNull { it.term } ?: 0) + 1)).toString()) }
    var dateText by rememberSaveable { mutableStateOf(original?.openDate?.toString()) }
    var skips by rememberSaveable { mutableStateOf(original?.skips?.sorted().orEmpty()) }
    var examWeek by rememberSaveable { mutableIntStateOf(original?.examWeek ?: 6) }
    var examMenuOpen by remember { mutableStateOf(false) }
    var chooseDate by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val date = dateText?.let(LocalDate::parse)
    val draft = date?.let { CampDraft(term.toIntOrNull() ?: 0, it, skips.toSet(), examWeek) }
    val error = when {
        (term.toIntOrNull() ?: 0) !in 1..10000 -> "期数请输入 1～10000 之间的整数。"
        state.snapshot.camps.any { it.term == term.toIntOrNull() && it.id != original?.id } -> "该期数已存在，请换一个期数。"
        date == null -> "请选择开营日期。"
        else -> draft?.error(state.snapshot.camps, original)
    }
    ModalBottomSheet(onDismissRequest = { if (!state.busy) onDismiss() }, containerColor = Canvas,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
            .padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (original == null) "添加营期" else "编辑第 ${original.term} 期", style = MaterialTheme.typography.headlineMedium)
                Text("设置开营日期，自动排好本期日程", style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
            OutlinedTextField(term, { term = it; submitted = false }, label = { Text("期数") }, singleLine = true,
                enabled = !state.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(), isError = submitted && error != null && date != null)
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().clickable(enabled = !state.busy) { chooseDate = true }) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("开营日期", style = MaterialTheme.typography.labelLarge, color = Muted)
                        Text(date?.format(campDateFormat) ?: "选择开营日期", style = MaterialTheme.typography.titleMedium)
                    }
                    AppIcon(Glyph.Calendar, "设置开营日期", tint = Blue)
                }
            }
            Text(if (date == null) "开营典礼直播一般在周日。"
                else date.format(weekDay) + if (date.dayOfWeek == DayOfWeek.SUNDAY) " · 开营直播日" else " · 开营直播通常在周日，请核对日期。",
                style = MaterialTheme.typography.bodySmall, color = Muted)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("考试周", style = MaterialTheme.typography.titleMedium)
                Text("按工信部确认的周次选择，报名、认证和考试提醒会随之调整。", style = MaterialTheme.typography.bodySmall, color = Muted)
                Box {
                    OutlinedButton(onClick = { examMenuOpen = true }, enabled = !state.busy) { Text("第${examWeek}周 ▾") }
                    DropdownMenu(expanded = examMenuOpen, onDismissRequest = { examMenuOpen = false },
                        modifier = Modifier.heightIn(max = 320.dp)) {
                        (1..12).forEach { week ->
                            DropdownMenuItem(text = { Text("第${week}周") }, onClick = {
                                examWeek = week
                                examMenuOpen = false
                            })
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("假期休息周", style = MaterialTheme.typography.titleMedium)
                Text("可选。选中的课程周休息，后续课程整体顺延。", style = MaterialTheme.typography.bodySmall, color = Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..12).forEach { week -> FilterChip(selected = week in skips, enabled = !state.busy,
                        onClick = { skips = if (week in skips) skips - week else skips + week }, label = { Text("第${week}周") }) }
                }
            }
            if (submitted && error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            if (state.campError.isNotEmpty() && !state.busy) {
                Text(state.campError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onRefresh) { Text("刷新排期并核对") }
            }
            Button(onClick = { submitted = true; if (error == null && draft != null) onSave(draft) }, enabled = !state.busy && state.online && !state.demo,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (state.busy) "正在保存…" else if (original == null) "添加并生成日程" else "保存营期")
            }
            Text("保存到团队排期，网站和 App 会同步显示。", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
    if (chooseDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = (date ?: state.today).atStartOfDay(ZoneOffset.UTC)
            .toInstant().toEpochMilli(), yearRange = 2000..2100)
        DatePickerDialog(onDismissRequest = { chooseDate = false }, confirmButton = {
            TextButton(onClick = { picker.selectedDateMillis?.let { dateText = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }; chooseDate = false },
                enabled = picker.selectedDateMillis != null) { Text("确定") }
        }, dismissButton = { TextButton(onClick = { chooseDate = false }) { Text("取消") } }) {
            DatePicker(picker, title = { Text("选择开营日期", Modifier.padding(24.dp)) }, showModeToggle = true)
        }
    }
}
