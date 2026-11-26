package cn.aurange.schedule

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun TaskScriptCards(task: TaskOccurrence, engine: ScheduleEngine) {
    val key = task.key
    val context = LocalContext.current
    val catalog = remember { runCatching { TaskScripts(context.assets.open("task-scripts.json").bufferedReader().use { it.readText() }) } }
    val rows = remember(task, catalog) {
        catalog.getOrNull()?.forTask(task).orEmpty().map { it.forWeek(task.phase.week) }
    }
    if (rows.isEmpty()) return
    val allRows=remember(task,catalog){catalog.getOrNull()?.forCamp(task,engine).orEmpty()}
    val favoritesStore=remember{context.getSharedPreferences("task-script-favorites", Context.MODE_PRIVATE)}
    var favorites by remember { mutableStateOf(favoritesStore.getStringSet("ids",emptySet()).orEmpty().toSet()) }
    var query by remember(key){mutableStateOf("")}
    var mode by remember(key){mutableStateOf("task")}
    var favoriteMessage by remember(key){mutableStateOf("")}
    var menuOpen by remember(key) { mutableStateOf(false) }
    OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
        Text("查看话术 · ${rows.size} 个版本", modifier = Modifier.weight(1f))
        AppIcon(Glyph.ChevronRight, "进入话术子菜单", tint = Blue)
    }
    if (!menuOpen) return
    Dialog(onDismissRequest = { menuOpen = false }) {
        Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("任务话术", fontSize = 19.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 10.dp))
                    TextButton(onClick = { menuOpen = false }) { Text("返回任务") }
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("发送前请核对本期安排，并补齐留空信息。报名、实名认证截止时间及考试场次以本期通知为准。", color = Amber, fontSize = 12.sp, lineHeight = 20.sp)
    OutlinedTextField(value=query,onValueChange={query=it;if(it.isNotBlank()&&mode=="task")mode="all"},label={Text("搜索全部话术")},singleLine=true,modifier=Modifier.fillMaxWidth())
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        for((value,label) in listOf("task" to "本任务","all" to "全部","favorites" to "收藏")) FilterChip(selected=mode==value,onClick={mode=value},label={Text(label)})
    }
    if(favoriteMessage.isNotEmpty())Text(favoriteMessage,fontSize=12.sp,color=Blue)
    val scoped=if(mode=="task"&&query.isBlank())rows else allRows
    val words=query.trim().lowercase().split(Regex("\\s+")).filter{it.isNotEmpty()}
    val filtered=scoped.filter{row -> (mode!="favorites"||row.id in favorites)&&words.all{word -> "${row.title} ${row.category} ${row.body}".lowercase().replace(Regex("\\s+"),"").contains(word)}}
    val growth = if(mode=="task"&&query.isBlank())filtered.filter { it.category == "成长值公布" } else emptyList()
    var growthId by remember(key) { mutableStateOf(growth.firstOrNull()?.id.orEmpty()) }
    var chooseGrowth by remember(key) { mutableStateOf(false) }
    val selectedGrowth = growth.firstOrNull { it.id == growthId } ?: growth.firstOrNull()
    val displayed = if(growth.isEmpty())filtered else filtered.filter { it.category != "成长值公布" } + listOfNotNull(selectedGrowth)
    val sharedAudience = displayed.map { "${it.category} · ${it.audience}" }.distinct().singleOrNull()
    SectionTitle("相关话术", subtitle = sharedAudience.orEmpty())
    if(displayed.isEmpty())Text("没有匹配的话术，可换个关键词或切换“全部”。",fontSize=13.sp,color=Muted)
    if (growth.isNotEmpty()) Box {
        OutlinedButton(onClick = { chooseGrowth = true }, modifier = Modifier.fillMaxWidth()) {
            Text("${selectedGrowth?.title} · ${growth.size} 套可选", modifier = Modifier.weight(1f))
            AppIcon(Glyph.ChevronDown, "选择榜单话术版本", tint = Blue)
        }
        DropdownMenu(expanded = chooseGrowth, onDismissRequest = { chooseGrowth = false }, modifier = Modifier.heightIn(max = 340.dp)) {
            growth.forEachIndexed { index, row -> DropdownMenuItem(text = { Text(row.title + if (index == 0) "（本周推荐）" else "") }, onClick = { growthId = row.id; chooseGrowth = false }) }
        }
    }
    for (row in displayed) {
        var expanded by remember(key, row.id) { mutableStateOf(false) }
        var copyMessage by remember(key, row.id) { mutableStateOf("") }
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Line)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                if (sharedAudience == null) Text("${row.category} · ${row.audience}", fontSize = 11.sp, color = Blue)
                if(row.contextLabel.isNotEmpty())Text(row.contextLabel,fontSize=12.sp,color=Muted)
                TextButton(onClick = { expanded = !expanded; copyMessage = "" }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                    Text(row.title, fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    AppIcon(if (expanded) Glyph.ChevronDown else Glyph.ChevronRight, if (expanded) "收起话术" else "展开话术", tint = Blue)
                }
                if (row.variables.isNotEmpty()) Text("留空待填：${row.variables.joinToString("、")}", color = Amber, fontSize = 12.sp, lineHeight = 19.sp)
                if (expanded) {
                    TextButton(onClick={
                        val next=if(row.id in favorites)favorites-row.id else favorites+row.id
                        if(favoritesStore.edit().putStringSet("ids",next).commit()){favorites=next;favoriteMessage="收藏已保存在本机。"}else favoriteMessage="收藏未能保存，请稍后重试。"
                    }){Text(if(row.id in favorites)"取消收藏" else "收藏")}
                    row.notes.forEach { Text(it, fontSize = 12.sp, lineHeight = 20.sp, color = Muted) }
                    Text(row.body, fontSize = 14.sp, lineHeight = 25.sp, color = Ink)
                    Text("来源：${row.source}\n版本：${catalog.getOrNull()?.version}", color = Muted, fontSize = 11.sp, lineHeight = 18.sp)
                    OutlinedButton(onClick = {
                        try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(row.title, row.body))
                            copyMessage = (if (row.variables.isEmpty()) "话术已复制。" else "话术已复制，请补齐留空信息后再发送。") + " 建议人工二次核实。"
                        } catch (_: Exception) { copyMessage = "暂时未能复制，请稍后重试。" }
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (row.variables.isEmpty()) "复制话术" else "复制话术（含待填项）") }
                    if (copyMessage.isNotEmpty()) Text(copyMessage, color = if (copyMessage.startsWith("暂时")) Danger else Blue,
                        fontSize = 12.sp, lineHeight = 20.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
        }
    }
                }
            }
        }
    }
}
