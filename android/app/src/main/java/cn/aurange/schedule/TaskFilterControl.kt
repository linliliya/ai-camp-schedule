package cn.aurange.schedule

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One selection plate follows the measured control height, including enlarged text. */
@Composable fun TaskFilterControl(selected: TaskFilter, onSelect: (TaskFilter) -> Unit) {
    val position by animateFloatAsState(selected.ordinal.toFloat(), spring(dampingRatio = .9f, stiffness = 500f),
        label = "task-filter-position")
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFE7EBF1)).padding(4.dp)) {
        Box(Modifier.matchParentSize().layout { measurable, constraints ->
            val width = constraints.maxWidth / TaskFilter.entries.size
            val plate = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
            layout(constraints.maxWidth, plate.height) { plate.placeRelative((width * position).toInt(), 0) }
        }.clip(RoundedCornerShape(10.dp)).background(Color.White))
        Row(Modifier.fillMaxWidth()) {
            TaskFilter.entries.forEach { option ->
                val tint by animateColorAsState(if (selected == option) Blue else Muted, tween(160), label = "task-filter-tint")
                Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .selectable(selected == option, role = Role.Tab, onClick = { onSelect(option) })
                    .heightIn(min = 44.dp).padding(horizontal = 6.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(option.label, color = tint, fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
                        fontWeight = if (selected == option) FontWeight.Medium else FontWeight.Normal)
                }
            }
        }
    }
}
