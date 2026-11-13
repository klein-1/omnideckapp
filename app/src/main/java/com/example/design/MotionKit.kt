package com.example.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Tactile spring press scale modifier for lively button & card interactions.
 */
@Composable
fun Modifier.springPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f
): Modifier {
    val look = LocalDotkitLook.current
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && look.motionMode != MotionMode.OFF) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "press_scale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Staggered entrance wrapper for lists and bento cards.
 */
@Composable
fun StaggeredEntrance(
    index: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val look = LocalDotkitLook.current
    if (look.motionMode == MotionMode.OFF) {
        Box(modifier = modifier) { content() }
        return
    }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val delayMs = (index.coerceAtMost(8) * 26L)
        if (delayMs > 0L) delay(delayMs)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(220)) +
            slideInVertically(
                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                initialOffsetY = { it / 8 }
            ) +
            scaleIn(initialScale = 0.97f, animationSpec = tween(200))
    ) {
        content()
    }
}

@Composable
fun TypedDisplayTitle(
    text: String,
    sizeSp: Int = 28,
    color: Color = LocalDotkitLook.current.palette.ink,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = DotkitType.displayPixel(sizeSp),
        color = color,
        modifier = modifier
    )
}

@Composable
fun DotMatrixReadout(
    valueText: String,
    sizeSp: Int = 40,
    accentColor: Color = LocalDotkitLook.current.palette.ink,
    modifier: Modifier = Modifier
) {
    val look = LocalDotkitLook.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        valueText.forEachIndexed { idx, ch ->
            if (look.motionMode == MotionMode.OFF) {
                Text(
                    text = ch.toString(),
                    style = DotkitType.readoutDoto(sizeSp),
                    color = accentColor
                )
            } else {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        (slideInVertically(tween(130)) { h -> h / 3 } + fadeIn(tween(110)))
                            .togetherWith(slideOutVertically(tween(130)) { h -> -h / 3 } + fadeOut(tween(110)))
                    },
                    label = "digit_$idx"
                ) { targetChar ->
                    Text(
                        text = targetChar.toString(),
                        style = DotkitType.readoutDoto(sizeSp),
                        color = accentColor
                    )
                }
            }
        }
    }
}

@Composable
fun DotMatrixReadout(
    value: String,
    label: String,
    chapterHue: ChapterHue,
    sizeSp: Int = 32,
    modifier: Modifier = Modifier
) {
    val palette = LocalDotkitLook.current.palette
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        DotMatrixReadout(
            valueText = value,
            sizeSp = sizeSp,
            accentColor = palette.chapterColor(chapterHue)
        )
        if (label.isNotBlank()) {
            Text(text = label, style = DotkitType.monoLabel(10), color = palette.ink2)
        }
    }
}

@Composable
fun PixelConfettiBurst(
    triggerKey: Int,
    chapterHue: ChapterHue,
    modifier: Modifier = Modifier
) {
    val look = LocalDotkitLook.current
    val progress = remember(triggerKey) { Animatable(0f) }

    LaunchedEffect(triggerKey) {
        if (triggerKey > 0 && look.confettiEnabled && look.motionMode != MotionMode.OFF) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = 420, easing = LinearEasing))
        }
    }

    if (triggerKey > 0 && progress.value > 0f && progress.value < 1f) {
        val p = progress.value
        val c1 = look.palette.chapterColor(chapterHue)
        val c2 = look.palette.ink
        val c3 = look.palette.ok
        Canvas(modifier = modifier.fillMaxWidth().height(28.dp)) {
            val count = 16
            for (i in 0 until count) {
                val x = (size.width / count) * i + ((i % 3 - 1) * 18f * p)
                val y = size.height * (0.15f + 0.75f * p * ((i % 4 + 1) / 4f))
                val col = when (i % 3) {
                    0 -> c1
                    1 -> c2
                    else -> c3
                }
                drawCircle(col, radius = 3.5.dp.toPx(), center = Offset(x, y))
            }
        }
    }
}

@Composable
fun rememberErrorShakeOffset(errorTrigger: Int): IntOffset {
    val look = LocalDotkitLook.current
    val anim = remember { Animatable(0f) }
    LaunchedEffect(errorTrigger) {
        if (errorTrigger > 0 && look.motionMode != MotionMode.OFF) {
            anim.animateTo(-1f, tween(45))
            anim.animateTo(1f, tween(45))
            anim.animateTo(-1f, tween(45))
            anim.animateTo(0f, tween(45))
        }
    }
    return IntOffset((anim.value * 10f).roundToInt(), 0)
}

enum class ToolStateKind { LOADING, EMPTY, ERROR, OFFLINE, SUCCESS }

@Composable
fun ToolStateBanner(
    kind: ToolStateKind,
    title: String,
    detail: String,
    chapterHue: ChapterHue,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalDotkitLook.current.palette
    val (accent, stamp) = when (kind) {
        ToolStateKind.LOADING -> palette.info to "WORKING"
        ToolStateKind.EMPTY -> palette.chapterColor(chapterHue) to "READY"
        ToolStateKind.ERROR -> palette.err to "ERROR"
        ToolStateKind.OFFLINE -> palette.warn to "OFFLINE"
        ToolStateKind.SUCCESS -> palette.ok to "DONE"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.paper2)
            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelStamp(stamp, accent)
                Text(text = title, style = DotkitType.editorialHeading(15), color = palette.ink)
            }
            Spacer(Modifier.height(4.dp))
            Text(text = detail, style = DotkitType.bodyUi(13), color = palette.ink2)
            if (kind == ToolStateKind.LOADING) {
                Spacer(Modifier.height(8.dp))
                PixelProgressBar(progress = 0.62f, chapterHue = chapterHue)
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(10.dp))
                PixelChip(label = actionLabel, selected = true, chapterHue = chapterHue, onClick = onAction)
            }
        }
    }
}
