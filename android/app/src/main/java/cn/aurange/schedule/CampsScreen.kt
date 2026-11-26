package cn.aurange.schedule

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun CampsScreen(state: AppState, engine: ScheduleEngine, onTask: (TaskOccurrence) -> Unit,
                            onComplete: (TaskOccurrence) -> Unit, onSaveCamp: (CampDraft, Camp?) -> Unit = { _, _ -> }, onRefresh: () -> Unit = {}) {
    var selectedCamp by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPhase by rememberSaveable { mutableStateOf<String?>(null) }
    var handledRevision by rememberSaveable { mutableIntStateOf(0) }
    var showCampSheet by remember { mutableStateOf(false) }
    var editingCamp by remember { mutableStateOf<Camp?>(null) }
    LaunchedEffect(state.savedCampRevision) {
        if (state.savedCampRevision > handledRevision) {
            selectedCamp = state.createdCampId
            selectedPhase = null
            showCampSheet = false
            handledRevision = state.savedCampRevision
        }
    }
    val camps = remember(state.snapshot.camps, state.today) {
        state.snapshot.camps.sortedWith(compareBy<Camp>({ if (engine.current(it, state.today) != null) 0 else if (it.openDate > state.today) 1 else 2 }, { it.openDate }))
    }
    val camp = camps.firstOrNull { it.id == selectedCamp } ?: camps.firstOrNull()
    val campScroll = rememberLazyListState()
    LaunchedEffect(camp?.id) {
        val index = camps.indexOfFirst { it.id == camp?.id }
        if (index >= 0) campScroll.animateScrollToItem(index)
    }
    val phases = remember(camp) { camp?.let(engine::phases).orEmpty() }
    val phase = camp?.let { phases.firstOrNull { p -> p.key == selectedPhase } ?: engine.current(it, state.today)
        ?: if (state.today < phases.first().start) phases.first() else phases.last() }
    val tasks = remember(camp, phase) { if (camp != null && phase != null) engine.tasks(camp, phase) else emptyList() }
    val done = tasks.count { engine.complete(state.displaySnapshot, it) }
    val progress by animateFloatAsState(if (tasks.isEmpty()) 0f else done.toFloat() / tasks.size, tween(250), label = "camp-progress")

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 60.dp, bottom = floatingContentBottom()),
        verticalArrangement = Arrangement.spacedBy(0.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("我的营期", style = MaterialTheme.typography.headlineMedium)
                    Text("${camps.size} 个营期 · ${camps.count { engine.current(it, state.today) != null }} 个进行中", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
                }
                FilledTonalButton(onClick = { editingCamp = null; showCampSheet = true }, enabled = state.online && !state.demo && !state.busy,
                    contentPadding = PaddingValues(horizontal = 15.dp)) { Text("添加营期") }
            }
        }
        if (camps.isEmpty()) item { EmptyCard("暂无团队营期", "点击「添加营期」，设置开营日期后自动生成日程。") }
        item {
            LazyRow(state = campScroll, modifier = Modifier.padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(camps, key = { it.id }) { c ->
                    val current = engine.current(c, state.today)
                    val selected = camp?.id == c.id
                    Surface(Modifier.width(160.dp).clip(RoundedCornerShape(16.dp)).selectable(selected, role = Role.Tab,
                        onClick = { selectedCamp = c.id; selectedPhase = null }),
                        color = if (selected) Lavender else Color.White, shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, if (selected) Blue.copy(alpha = .25f) else Line)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("第 ${c.term} 期", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (selected) Blue else Ink)
                                if (selected) AppIcon(Glyph.CircleCheck, tint = Blue, modifier = Modifier.size(16.dp))
                            }
                            PhaseBadge(current, current?.label ?: if (c.openDate > state.today) "未开始" else "已结营")
                            Text(c.openDate.format(shortDate) + " 开营", fontSize = 12.sp, color = Muted)
                        }
                    }
                }
            }
        }
        if (camp != null && phase != null) {
            item {
                WhiteCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(phase.label, style = MaterialTheme.typography.titleLarge)
                            Text("${phase.start.format(shortDate)} — ${phase.end.format(shortDate)}", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 5.dp))
                            if (camp.skips.isNotEmpty()) Text("本期休息 ${camp.skips.size} 周，课程已顺延", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 5.dp))
                        }
                        Box(Modifier.size(42.dp).background(phaseBackground(phase.type), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                            AppIcon(if (phase.type == "prep") Glyph.Book else if (phase.type == "rest") Glyph.Coffee else Glyph.Layers, tint = phaseColor(phase.type))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("考试安排在第 ${camp.examWeek} 周", modifier = Modifier.weight(1f), color = Muted, fontSize = 12.sp)
                        TextButton(onClick = { editingCamp = camp; showCampSheet = true },
                            enabled = state.online && !state.demo && !state.busy) { Text("编辑营期") }
                    }
                    Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)).background(phaseBackground(phase.type))) {
                        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(phaseColor(phase.type)))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("本阶段完成", fontSize = 12.sp, color = Muted)
                        Text("$done / ${tasks.size}", fontSize = 12.sp, color = phaseColor(phase.type), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item {
                val phaseIndex = phases.indexOf(phase).coerceAtLeast(0)
                val phaseScroll = rememberLazyListState(initialFirstVisibleItemIndex = (phaseIndex - 2).coerceAtLeast(0))
                LaunchedEffect(camp.id, phase.key) { phaseScroll.animateScrollToItem((phaseIndex - 2).coerceAtLeast(0)) }
                LazyRow(state = phaseScroll, modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                    items(phases, key = { it.key }) { p ->
                        FilterChip(selected = phase.key == p.key, onClick = { selectedPhase = p.key }, label = { Text(p.short, fontSize = 12.sp) },
                            shape = RoundedCornerShape(11.dp), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Blue,
                                selectedLabelColor = Color.White, containerColor = Color.White),
                            border = FilterChipDefaults.filterChipBorder(enabled = true, selected = phase.key == p.key, borderColor = Line, selectedBorderColor = Blue))
                    }
                }
            }
            for (day in listOf("all", "1", "2", "3", "4", "5", "6", "0")) {
                val dayTasks = tasks.filter { it.template.day == day }
                if (dayTasks.isEmpty()) continue
                item(key = "${camp.id}-${phase.key}-$day-label") {
                    val label = if (day == "all") "全周 / 日常" else dayTasks.first().date.let { it.format(weekDay) + " · " + it.format(shortDate) }
                    Box(Modifier.padding(top = 16.dp, bottom = 10.dp)) {
                        SectionTitle(label) { Text("${dayTasks.size} 项", fontSize = 12.sp, color = Muted) }
                    }
                }
                itemsIndexed(dayTasks, key = { _, task -> "camp:${task.id}" }) { index, task ->
                    TaskCard(task, engine.complete(state.displaySnapshot, task), state.online && !state.busy && !state.demo && task.id !in state.pendingCompletions,
                        isOverdue(task, state.today), { onTask(task) }, { onComplete(task) }, saving = task.id in state.pendingCompletions,
                        firstInGroup = index == 0, lastInGroup = index == dayTasks.lastIndex)
                }
            }
        }
    }
    if (showCampSheet) {
        val original = editingCamp
        AddCampSheet(state, { showCampSheet = false }, { onSaveCamp(it, original) }, onRefresh, original)
    }
}
