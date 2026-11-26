package cn.aurange.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.*

// Scrolling content extends behind the floating bar; this space keeps its final item reachable.
@Composable fun floatingContentBottom() = 92.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

@OptIn(ExperimentalHazeApi::class)
@Composable fun GlassSurface(state: HazeState, modifier: Modifier = Modifier,
                            corner: Int = 24, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(corner.dp)
    Box(modifier.shadow(8.dp, shape, ambientColor = Navy.copy(alpha = .10f), spotColor = Navy.copy(alpha = .10f))
        .clip(shape).hazeChild(state) {
            backgroundColor = Canvas
            tints = listOf(HazeTint(Color.White.copy(alpha = .76f)))
            blurRadius = 22.dp
            noiseFactor = .025f
            fallbackTint = HazeTint(Canvas.copy(alpha = .96f))
            inputScale = HazeInputScale.Fixed(.66f)
        }.background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .22f), Color.White.copy(alpha = .02f))))
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = .9f), Line.copy(alpha = .4f))), shape),
        content = content)
}
