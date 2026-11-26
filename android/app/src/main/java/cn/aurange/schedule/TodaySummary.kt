package cn.aurange.schedule

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class)
@Composable fun TodaySummary(completed: Int, total: Int, overdue: Int, follow: Int, modifier: Modifier = Modifier,
                            embedded: Boolean = false) {
    val count = total.coerceAtLeast(0)
    val done = completed.coerceIn(0, count)
    val target = if (count == 0) 0f else done.toFloat() / count
    val allDone = count > 0 && done == count
    val progress by animateFloatAsState(target, tween(240), label = "day-progress")
    val accent by animateColorAsState(if (allDone) Sage else Blue, tween(180), label = "day-progress-color")
    val percentage = if (count == 0) "—" else "${done.toLong() * 100 / count}%"
    Surface(modifier.fillMaxWidth(), color = if (embedded) Color.Transparent else Color.White,
        shape = RoundedCornerShape(16.dp), border = if (embedded) null else BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(when { count == 0 -> "当天暂无安排"; allDone -> "今天已全部完成"; else -> "还有 ${count - done} 项待办" },
                    style = MaterialTheme.typography.titleMedium, color = if (allDone) Sage else Ink, modifier = Modifier.weight(1f))
                Box(Modifier.semantics { contentDescription = "完成比例"; stateDescription = if (count == 0) "暂无任务" else "已完成 $percentage" }
                    .background(if (allDone) phaseBackground("regular") else Lavender, RoundedCornerShape(10.dp))
                    .widthIn(min = 66.dp).heightIn(min = 34.dp).padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center) {
                    Text(percentage, fontFamily = EditorialFont, fontWeight = FontWeight.Normal, fontSize = 19.sp, lineHeight = 22.sp,
                        textAlign = TextAlign.Center, color = accent,
                        style = MaterialTheme.typography.labelLarge.copy(platformStyle = PlatformTextStyle(includeFontPadding = false)))
                }
            }
            if (count == 0) Text("可以切换日期，或到「营期」查看完整排期。",
                style = MaterialTheme.typography.bodySmall, color = Muted)
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (label in listOf("已完成 $done 项", "逾期 ${overdue.coerceAtLeast(0)} 项", "跟进 ${follow.coerceAtLeast(0)} 项")) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
            if (count > 0) LinearProgressIndicator(progress = { progress }, color = accent, trackColor = Color(0xFFE4E9F2),
                modifier = Modifier.fillMaxWidth().height(4.dp).semantics { contentDescription = "日程完成进度" },
                drawStopIndicator = {})
        }
    }
}
