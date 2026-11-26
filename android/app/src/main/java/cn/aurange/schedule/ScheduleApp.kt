package cn.aurange.schedule

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

@Composable fun ScheduleApp(model: ScheduleViewModel, route: String?, onRouteHandled: () -> Unit,
                            permissionVersion: Int, askNotifications: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val issueModel: IssueCaptureViewModel = viewModel()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedTask by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val savedScreens = rememberSaveableStateHolder()
    val engine = model.engine
    val haze = remember { HazeState() }
    val tasks = remember(state.snapshot) { engine.allTasks(state.snapshot).associateBy { it.id } }
    LaunchedEffect(state.message) {
        if (state.message.isNotEmpty()) { snackbar.showSnackbar(state.message, duration = SnackbarDuration.Long); model.clearMessage() }
    }
    LaunchedEffect(route, state.loggedIn, state.snapshot.syncedAt) {
        if (route != null && state.loggedIn) {
            tab = 0
            if (route.isEmpty()) { selectedTask = null; onRouteHandled() }
            else if (tasks.containsKey(route)) { selectedTask = route; onRouteHandled() }
            else if (state.snapshot.syncedAt > 0 && !state.busy) {
                model.message("这条任务已不在当前排期中，已打开今日待办。"); onRouteHandled()
            }
        }
    }
    LaunchedEffect(state.loggedIn, state.demo) {
        if (!state.loggedIn && !state.demo) {
            selectedTask = null
            for (key in 0..1) savedScreens.removeState(key)
        }
    }
    BackHandler(enabled = selectedTask != null || tab != 0) {
        if (selectedTask != null) selectedTask = null else tab = 0
    }
    Scaffold(containerColor = Canvas, contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding().padding(bottom = 88.dp)) }
    ) { padding ->
      Box(Modifier.fillMaxSize().padding(padding)) {
        Column(Modifier.fillMaxSize().haze(haze).background(Canvas).statusBarsPadding()) {
            AnimatedVisibility(state.busy) { LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Blue, trackColor = Lavender) }
            if (!state.demo && state.loggedIn && !state.online && tab < 2) OfflineBanner(state)
                Crossfade(tab, modifier = Modifier.weight(1f), animationSpec = tween(160), label = "page") { page ->
                    // The login page must not hold a team screen's saveable registry:
                    // clearing account state would otherwise invalidate that active registry.
                    if (page < 2 && !state.loggedIn && !state.demo) LoginScreen(state, model)
                    else savedScreens.SaveableStateProvider(page) {
                        when (page) {
                            0 -> TodayScreen(state, engine, { selectedTask = it.id }, model::complete) {
                                AppStatus(state, haze, model::refresh, model::signOut, Modifier, glass = false)
                            }
                            1 -> CampsScreen(state, engine, { selectedTask = it.id }, model::complete, model::saveCamp, model::refresh)
                            2 -> IssueCaptureScreen(issueModel)
                            3 -> SettingsScreen(state, model, permissionVersion, askNotifications, issueModel)
                        }
                    }
                }
        }
            if ((state.loggedIn || state.demo) && (tab == 1 || tab == 3)) AppStatus(state, haze, model::refresh, model::signOut, Modifier.align(Alignment.TopEnd).statusBarsPadding()
                .padding(end = 20.dp, top = 5.dp))
            if (WindowInsets.ime.getBottom(LocalDensity.current) == 0) {
                AppNavigation(tab, haze, Modifier.align(Alignment.BottomCenter)) { tab = it }
            }
      }
    }
    selectedTask?.let { id -> tasks[id]?.let { task -> TaskDetail(task, state, model, onDismiss = { selectedTask = null }, onNote = { text ->
        if (issueModel.noteFromTask(text)) { selectedTask = null; tab = 2; true } else false
    }) } }
}

@Composable private fun AppStatus(state: AppState, haze: HazeState, refresh: () -> Unit, signOut: () -> Unit,
                                 modifier: Modifier, glass: Boolean = true) {
    val content: @Composable () -> Unit = {
      Row(Modifier.padding(start = 12.dp, end = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            state.demo -> {
                Text("演示预览", color = Muted, fontSize = 12.sp)
                TextButton(onClick = signOut, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("登录", fontSize = 12.sp) }
            }
            state.loggedIn -> {
                if (state.pendingCompletions.isNotEmpty()) Text("保存中 ${state.pendingCompletions.size}", color = Muted, fontSize = 12.sp)
                AppIcon(if (state.online) Glyph.Cloud else Glyph.Offline, modifier = Modifier.size(15.dp), tint = if (state.online) Sage else Amber)
                IconButton(onClick = refresh, enabled = !state.busy, modifier = Modifier.size(40.dp)) { AppIcon(Glyph.Refresh, "刷新排期", modifier = Modifier.size(18.dp), tint = Blue) }
            }
        }
      }
    }
    // The inline header belongs to the captured scrolling source. Only sibling overlays sample it.
    if (glass) GlassSurface(haze, modifier, corner = 22) { content() }
    else Surface(modifier, shape = RoundedCornerShape(22.dp), color = Color.White) { content() }
}

@Composable private fun OfflineBanner(state: AppState) {
    Row(Modifier.fillMaxWidth().background(phaseBackground("exam")).padding(horizontal = 22.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(Glyph.Offline, modifier = Modifier.size(13.dp), tint = Amber)
        val last = if (state.snapshot.syncedAt > 0) " · 上次同步 " + java.time.Instant.ofEpochMilli(state.snapshot.syncedAt)
            .atZone(BUSINESS_ZONE).format(java.time.format.DateTimeFormatter.ofPattern("M/d HH:mm")) else "，请刷新重试"
        Text("离线缓存$last", color = Amber, fontSize = 12.sp)
    }
}

@Composable private fun AppNavigation(tab: Int, haze: HazeState, modifier: Modifier, select: (Int) -> Unit) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp)
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp)) {
        GlassSurface(haze) {
          BoxWithConstraints(Modifier.fillMaxWidth().padding(7.dp)) {
            val itemWidth = maxWidth / 4
            val measuredWidth = textMeasurer.measure(AnnotatedString("随手记"), style = labelStyle, maxLines = 1, softWrap = false).size.width.coerceAtLeast(1)
            val labelWidth = with(density) { (itemWidth - 8.dp).toPx().coerceAtLeast(1f) }
            val labelSize = (13f * (labelWidth / measuredWidth).coerceAtMost(1f)).sp
            val offset by animateDpAsState(itemWidth * tab, spring(dampingRatio = .85f, stiffness = 480f), label = "navigation-position")
            Box(Modifier.offset { IntOffset(offset.roundToPx(), 0) }.width(itemWidth).height(52.dp).clip(RoundedCornerShape(22.dp))
                .background(Blue.copy(alpha = .14f)))
            Row(Modifier.fillMaxWidth()) {
                val labels = listOf("今日", "营期", "随手记", "设置")
                val icons = listOf(Glyph.Sun, Glyph.Layers, Glyph.Note, Glyph.Sliders)
                labels.forEachIndexed { index, title ->
                    val tint by animateColorAsState(if (tab == index) Blue else Muted, tween(180), label = "navigation-tint")
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
                        .selectable(tab == index, role = Role.Tab, onClick = { select(index) })
                        .height(52.dp).padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                        AppIcon(icons[index], tint = tint, modifier = Modifier.size(19.dp))
                        Text(title, style = labelStyle, color = tint, fontSize = labelSize, lineHeight = (labelSize.value * 1.23f).sp,
                            maxLines = 1, softWrap = false, fontWeight = if (tab == index) FontWeight.Medium else FontWeight.Normal)
                    }
                }
            }
          }
        }
    }
}
