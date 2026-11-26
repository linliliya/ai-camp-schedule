package cn.aurange.schedule

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun TaskCompletionControl(completed: Boolean, enabled: Boolean, onComplete: () -> Unit, saving: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val rebound = remember { Animatable(1f) }
    val ring = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(completed) }
    LaunchedEffect(completed) {
        val newlyCompleted = completed && !previous
        previous = completed
        if (newlyCompleted) coroutineScope {
            launch { rebound.snapTo(.82f); rebound.animateTo(1f, spring(dampingRatio = .55f, stiffness = 600f)) }
            launch { ring.snapTo(0f); ring.animateTo(1f, tween(300)) }
        } else {
            rebound.snapTo(1f)
            ring.snapTo(1f)
        }
    }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .9f else 1f, tween(100), label = "completion-press")
    val fill by animateFloatAsState(if (completed) 1f else 0f, tween(180), label = "completion-fill")
    val check by animateFloatAsState(if (completed) 1f else 0f, tween(200), label = "completion-check")
    val outline by animateColorAsState(if (completed) Sage else Color(0xFF7C8BA1), tween(180), label = "completion-outline")
    // The visual stays small; the complete/restore target remains 48dp and accessible.
    Box(Modifier.size(48.dp)
        .semantics {
            contentDescription = if (completed) "取消完成" else "标记完成"
            stateDescription = (if (completed) "已完成" else "待办") + if (saving) "，正在保存" else ""
        }
        .toggleable(value = completed, enabled = enabled, role = Role.Checkbox,
            interactionSource = interaction, indication = null, onValueChange = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onComplete()
            }),
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(18.dp).scale(scale * rebound.value)) {
            val stroke = 1.2.dp.toPx()
            val radius = (size.minDimension - stroke) / 2
            if (ring.value < 1f) drawCircle(Sage.copy(alpha = (1f - ring.value) * .22f),
                radius + 8.dp.toPx() * ring.value, style = Stroke(1.dp.toPx()))
            if (fill > 0f) drawCircle(Sage.copy(alpha = fill), radius)
            drawCircle(outline, radius, style = Stroke(stroke))
            if (check > 0f) {
                val path = Path().apply {
                    moveTo(size.width * .28f, size.height * .51f)
                    lineTo(size.width * .43f, size.height * .66f)
                    lineTo(size.width * .73f, size.height * .35f)
                }
                val measure = PathMeasure().apply { setPath(path, false) }
                val visible = Path()
                measure.getSegment(0f, measure.length * check, visible, true)
                drawPath(visible, Color.White.copy(alpha = fill), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
