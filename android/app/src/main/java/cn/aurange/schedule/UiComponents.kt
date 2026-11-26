package cn.aurange.schedule

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import java.util.Locale

val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
val fullDate: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA)
val weekDay: DateTimeFormatter = DateTimeFormatter.ofPattern("E", Locale.CHINA)

@Composable fun AppMark(modifier: Modifier = Modifier) {
    Box(modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Blue), contentAlignment = Alignment.Center) {
        AppIcon(Glyph.CalendarCheck, modifier = Modifier.size(21.dp), tint = Color.White)
    }
}

@Composable fun PhaseBadge(phase: Phase?, label: String? = null) {
    Surface(color = phaseBackground(phase?.type.orEmpty()), shape = RoundedCornerShape(8.dp)) {
        Text(label ?: phase?.label ?: "未开始", color = phaseColor(phase?.type.orEmpty()), fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable fun SectionTitle(title: String, subtitle: String = "", trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 3.dp))
        }
        trailing?.invoke()
    }
}

@Composable fun WhiteCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable fun TaskCard(task: TaskOccurrence, completed: Boolean, canEdit: Boolean, overdue: Boolean,
                        onClick: () -> Unit, onComplete: () -> Unit, saving: Boolean = false,
                        firstInGroup: Boolean = true, lastInGroup: Boolean = true) {
    val background by animateColorAsState(if (completed) Color(0xFFF4F6F8) else Color.White, tween(180), label = "task-color")
    val shape = RoundedCornerShape(topStart = if (firstInGroup) 18.dp else 0.dp,
        topEnd = if (firstInGroup) 18.dp else 0.dp,
        bottomStart = if (lastInGroup) 18.dp else 0.dp, bottomEnd = if (lastInGroup) 18.dp else 0.dp)
    Surface(Modifier.fillMaxWidth().clip(shape).clickable(onClick = onClick), color = background, shape = shape) {
        Column {
            Row(Modifier.padding(start = 6.dp, end = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.Top) {
                TaskCompletionControl(completed, canEdit, onComplete, saving)
                Column(Modifier.weight(1f).padding(top = 11.dp)) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(task.template.title, fontSize = 15.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium,
                            color = if (completed) Navy else Ink, modifier = Modifier.weight(1f),
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (task.template.links.isNotEmpty()) AppIcon(Glyph.ArrowUpRight,
                            modifier = Modifier.padding(top = 4.dp).size(15.dp), tint = Muted)
                    }
                    if (task.template.description.isNotEmpty()) Text(task.template.description, fontSize = 13.sp, lineHeight = 21.sp,
                        color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(5.dp).background(phaseColor(task.phase.type), CircleShape))
                        Text("第${task.camp.term}期 · ${task.phase.label}", fontSize = 12.sp, color = Muted, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (task.template.time.isNotEmpty()) Text(task.template.time, fontSize = 12.sp,
                            color = if (overdue && !completed) Danger else Blue, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
            }
            if (!lastInGroup) HorizontalDivider(Modifier.padding(start = 54.dp, end = 16.dp), color = Line.copy(alpha = .65f))
        }
    }
}

@Composable fun EmptyCard(title: String, description: String) {
    WhiteCard {
        Box(Modifier.size(44.dp).background(Lavender, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { AppIcon(Glyph.Coffee, tint = Blue) }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable fun ActionRow(icon: Glyph, title: String, description: String = "", status: String = "", onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(35.dp).background(Lavender, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) { AppIcon(icon, tint = Blue, modifier = Modifier.size(18.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink)
            if (description.isNotEmpty()) Text(description, fontSize = 12.sp, lineHeight = 17.sp, color = Muted,
                modifier = Modifier.padding(top = 4.dp))
        }
        if (status.isNotEmpty()) Text(status, fontSize = 12.sp, color = Blue)
        AppIcon(Glyph.ChevronRight, modifier = Modifier.size(14.dp), tint = Muted.copy(alpha = .6f))
    }
}
