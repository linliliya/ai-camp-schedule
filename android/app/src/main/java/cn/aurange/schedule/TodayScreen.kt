package cn.aurange.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TodayScreen(state: AppState, engine: ScheduleEngine, onTask: (TaskOccurrence) -> Unit,
                            onComplete: (TaskOccurrence) -> Unit, status: @Composable () -> Unit = {}) {
    var selectedDate by rememberSaveable(state.today.toString()) { mutableStateOf(state.today.toString()) }
    var showCalendar by remember { mutableStateOf(false) }
    var filterName by rememberSaveable { mutableStateOf(TaskFilter.ALL.name) }
    val filter = TaskFilter.valueOf(filterName)
    val date = LocalDate.parse(selectedDate)
    val snapshot = state.displaySnapshot
    val tasks = remember(snapshot, date) { engine.today(snapshot, date) }
    // Only grouping/removal waits for the animation; display status responds on tap.
    val presentation = rememberCompletionPresentation(snapshot)
    val shownTasks = remember(presentation, date) { engine.today(presentation, date) }
    val completedIds = remember(snapshot) { snapshot.tasks.filterValues { it.completed }.keys }
    val groupedCompletedIds = remember(presentation) { presentation.tasks.filterValues { it.completed }.keys }
    val completed = tasks.count { it.id in completedIds }
    val groups = remember(shownTasks, date, groupedCompletedIds, filter) { taskGroups(shownTasks, date, groupedCompletedIds, filter) }
    val canEdit = state.online && !state.demo && !state.busy

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = floatingContentBottom()),
        verticalArrangement = Arrangement.spacedBy(0.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (date == state.today) "今天的安排" else date.format(shortDate),
                        style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    status()
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(date.format(DateTimeFormatter.ofPattern("M月d日 · EEEE", Locale.CHINA)), color = Muted,
                        fontSize = 12.sp, modifier = Modifier.weight(1f))
                    if (date == state.today) Text("Today", fontFamily = EditorialFont,
                        fontSize = 19.sp, color = Blue, maxLines = 1)
                    else TextButton(onClick = { selectedDate = state.today.toString() }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("今天", fontSize = 12.sp) }
                    OutlinedIconButton(onClick = { showCalendar = true }, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Line)) {
                        AppIcon(Glyph.Calendar, "选择日期", tint = Ink)
                    }
                }
            }
        }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(20.dp)) {
                Column {
                    TodaySummary(completed, tasks.size, tasks.count { isOverdue(it, date) && it.id !in completedIds },
                        tasks.count { it.template.follow && it.id !in completedIds }, embedded = true)
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Line.copy(alpha = .65f))
                    Box(Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
                        WeekDateStrip(date, state.today) { selectedDate = it.toString() }
                    }
                }
            }
        }
        item {
            Box(Modifier.padding(top = 14.dp, bottom = 2.dp)) {
                TaskFilterControl(filter) { filterName = it.name }
            }
        }
        if (state.snapshot.camps.isEmpty()) item {
            EmptyCard("还没有营期", "同步团队排期后，就能在这里查看每天的安排。")
        } else if (groups.isEmpty()) item {
            EmptyCard(when (filter) { TaskFilter.COMPLETED -> "还没有已完成的事项"; TaskFilter.PENDING -> "待办已全部完成"; else -> "这一天暂无待办" },
                if (filter == TaskFilter.COMPLETED) "完成当天待办后，会在这里显示。" else "到「营期」查看各周安排与相关资料。")
        }
        for (group in groups) {
            item(key = "group-${group.bucket.name}") {
                val accent = when (group.bucket) { TaskBucket.OVERDUE -> Amber; TaskBucket.FOLLOW -> phaseColor("prep"); TaskBucket.COMPLETED -> Sage; else -> Muted }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp, start = 3.dp, end = 3.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(group.bucket.label, fontSize = 13.sp, color = accent, fontWeight = FontWeight.Medium)
                    Text("${group.tasks.size}", fontSize = 13.sp, color = Muted)
                }
            }
            itemsIndexed(group.tasks, key = { _, task -> task.id }) { index, task ->
                Box(Modifier.animateItem(fadeInSpec = tween(160), fadeOutSpec = tween(160),
                    placementSpec = spring(dampingRatio = .85f, stiffness = 500f))) {
                    TaskCard(task, task.id in completedIds, canEdit && task.id !in state.pendingCompletions,
                        isOverdue(task, date), { onTask(task) }, { onComplete(task) }, saving = task.id in state.pendingCompletions,
                        firstInGroup = index == 0, lastInGroup = index == group.tasks.lastIndex)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                AppIcon(Glyph.Clock, modifier = Modifier.size(12.dp), tint = Muted.copy(alpha = .7f))
                Text("  北京时间 · 点击任务查看详情", fontSize = 12.sp, color = Muted.copy(alpha = .8f))
            }
        }
    }
    if (showCalendar) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), yearRange = 2000..2100)
        DatePickerDialog(onDismissRequest = { showCalendar = false }, confirmButton = {
            TextButton(onClick = { picker.selectedDateMillis?.let {
                selectedDate = java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
            }; showCalendar = false }) { Text("确定") }
        }, dismissButton = { TextButton(onClick = { selectedDate = state.today.toString(); showCalendar = false }) { Text("回到今天") } }) {
            DatePicker(picker, title = { Text("选择日程日期", modifier = Modifier.padding(24.dp)) }, showModeToggle = false)
        }
    }
}

@Composable
private fun rememberCompletionPresentation(snapshot: Snapshot): Snapshot {
    var displayed by remember { mutableStateOf(snapshot) }
    LaunchedEffect(snapshot) {
        if (displayed.camps == snapshot.camps &&
            displayed.tasks.filterValues { it.completed }.keys != snapshot.tasks.filterValues { it.completed }.keys) {
            delay(320)
        }
        displayed = snapshot
    }
    return displayed
}
