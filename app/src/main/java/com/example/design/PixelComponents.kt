package com.example.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.services.sound.PixelSoundEngine
import kotlin.math.roundToInt

enum class PixelButtonVariant { SOLID, OUTLINE, GHOST, DESTRUCTIVE }

val SwissCardShape = RoundedCornerShape(16.dp)
val SwissControlShape = RoundedCornerShape(12.dp)
val SwissPillShape = RoundedCornerShape(20.dp)

/**
 * Prevents the leading-zero typing bug ("0" + "653" -> "0653" becomes "653")
 * while keeping decimals like "0.5" and empty-to-"0" handling intact.
 */
fun sanitizeNumericInput(
    previous: String,
    rawNext: String,
    allowDecimal: Boolean = true,
    allowNegative: Boolean = false
): String {
    val sb = StringBuilder()
    var hasDot = false
    rawNext.forEachIndexed { idx, ch ->
        when {
            ch in '0'..'9' -> sb.append(ch)
            ch == '.' && allowDecimal && !hasDot -> {
                hasDot = true
                if (sb.isEmpty() || (sb.length == 1 && sb[0] == '-')) sb.append('0')
                sb.append('.')
            }
            ch == '-' && allowNegative && idx == 0 && sb.isEmpty() -> sb.append('-')
        }
    }
    var cleaned = sb.toString()
    if (cleaned.isEmpty()) return "0"
    if (cleaned == "-") return "-"

    val neg = cleaned.startsWith("-")
    val body = if (neg) cleaned.substring(1) else cleaned

    val normalizedBody = when {
        body == "0" -> "0"
        body.startsWith("0.") -> body
        previous == "0" && body.length > 1 && body.startsWith("0") -> {
            body.trimStart('0').ifEmpty { "0" }
        }
        body.length > 1 && body.startsWith("0") && !body.startsWith("0.") -> {
            body.trimStart('0').ifEmpty { "0" }
        }
        else -> body
    }
    cleaned = if (neg) "-$normalizedBody" else normalizedBody
    return cleaned
}

fun Modifier.drawDitherPattern(
    pattern: DitherPattern,
    color: Color,
    stepPx: Float = 12f
): Modifier = this.drawBehind {
    val cols = (size.width / stepPx).toInt().coerceAtLeast(1)
    val rows = (size.height / stepPx).toInt().coerceAtLeast(1)
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            val drawCell = when (pattern) {
                DitherPattern.DIAG -> (r + c) % 4 == 0
                DitherPattern.WAVES -> (r + (c % 4)) % 4 == 0
                DitherPattern.GRID -> r % 4 == 0 || c % 4 == 0
                DitherPattern.BRICK -> r % 3 == 0 || (c + (r / 2) * 2) % 4 == 0
                DitherPattern.DOTS -> r % 2 == 0 && c % 2 == 0
                else -> (r + c) % 4 == 0
            }
            if (drawCell) {
                drawRect(
                    color = color,
                    topLeft = Offset(c * stepPx, r * stepPx),
                    size = Size(stepPx * 0.3f, stepPx * 0.3f)
                )
            }
        }
    }
}

/**
 * Refined Material 3 tonal surface container with gentle rounded corners and soft hairline.
 */
@Composable
fun PixelBox(
    modifier: Modifier = Modifier,
    backgroundColor: Color = LocalDotkitLook.current.palette.paper2,
    borderColor: Color = LocalDotkitLook.current.palette.hairline.copy(alpha = 0.65f),
    shadowOffset: Dp = 0.dp,
    cropMarks: Boolean = false,
    padding: PaddingValues? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val look = LocalDotkitLook.current
    val resolvedPadding = padding ?: PaddingValues((16 * look.density.padMultiplier).dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(SwissCardShape)
            .background(backgroundColor)
            .border(0.8.dp, borderColor, SwissCardShape)
            .padding(resolvedPadding),
        content = content
    )
}

@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: PixelButtonVariant = PixelButtonVariant.SOLID,
    chapterHue: ChapterHue = LocalDotkitLook.current.accentHue,
    enabled: Boolean = true,
    tag: String? = null
) {
    val look = LocalDotkitLook.current
    val palette = look.palette
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    val targetBg = when {
        !enabled -> palette.paper3
        variant == PixelButtonVariant.SOLID -> palette.chapterColor(chapterHue)
        variant == PixelButtonVariant.DESTRUCTIVE -> palette.err
        variant == PixelButtonVariant.OUTLINE -> palette.paper2
        else -> Color.Transparent
    }
    val bg by animateColorAsState(
        targetValue = targetBg,
        animationSpec = tween(if (look.motionMode == MotionMode.OFF) 0 else 150),
        label = "btn_bg"
    )

    val fg = when {
        !enabled -> palette.ink3
        variant == PixelButtonVariant.SOLID -> chapterHue.onColor
        variant == PixelButtonVariant.DESTRUCTIVE -> Color.White
        else -> palette.ink
    }

    val borderCol = when {
        !enabled -> palette.hairline
        variant == PixelButtonVariant.GHOST -> Color.Transparent
        variant == PixelButtonVariant.SOLID -> Color.Transparent
        variant == PixelButtonVariant.DESTRUCTIVE -> Color.Transparent
        else -> palette.hairline
    }

    val finalMod = if (tag != null) modifier.testTag(tag) else modifier

    Box(
        modifier = finalMod
            .springPressScale(interactionSource, pressedScale = 0.96f)
            .minimumInteractiveComponentSize()
            .defaultMinSize(minHeight = 46.dp)
            .clip(SwissControlShape)
            .background(bg)
            .border(1.dp, borderCol, SwissControlShape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                enabled = enabled,
                role = Role.Button
            ) {
                if (look.hapticsEnabled) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                PixelSoundEngine.playBlip(look.soundEnabled, 680, 18)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = DotkitType.bodyUi(sizeSp = 14, bold = true),
            color = fg
        )
    }
}

@Composable
fun PixelChip(
    label: String,
    selected: Boolean = false,
    chapterHue: ChapterHue = LocalDotkitLook.current.accentHue,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val look = LocalDotkitLook.current
    val palette = look.palette
    val accent = palette.chapterColor(chapterHue)
    val interactionSource = remember { MutableInteractionSource() }
    val bg by animateColorAsState(
        targetValue = if (selected) accent else palette.paper3.copy(alpha = 0.65f),
        animationSpec = tween(if (look.motionMode == MotionMode.OFF) 0 else 150),
        label = "chip_bg"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) chapterHue.onColor else palette.ink,
        animationSpec = tween(if (look.motionMode == MotionMode.OFF) 0 else 150),
        label = "chip_fg"
    )
    val borderCol = if (selected) accent else palette.hairline.copy(alpha = 0.7f)

    val clickMod = if (onClick != null) {
        Modifier
            .springPressScale(interactionSource, pressedScale = 0.95f)
            .minimumInteractiveComponentSize()
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                role = Role.Button
            ) {
                PixelSoundEngine.playBlip(look.soundEnabled, 780, 14)
                onClick()
            }
    } else Modifier

    Box(
        modifier = modifier
            .clip(SwissPillShape)
            .then(clickMod)
            .background(bg)
            .border(1.dp, borderCol, SwissPillShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = DotkitType.bodyUi(13, bold = selected),
            color = fg,
            maxLines = 1
        )
    }
}

@Composable
fun PixelStamp(
    text: String,
    color: Color = LocalDotkitLook.current.palette.ok,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = DotkitType.monoLabel(10),
            color = color
        )
    }
}

@Composable
fun PixelSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    chapterHue: ChapterHue = LocalDotkitLook.current.accentHue,
    modifier: Modifier = Modifier
) {
    val look = LocalDotkitLook.current
    val palette = look.palette
    val trackColor by animateColorAsState(
        targetValue = if (checked) palette.chapterColor(chapterHue) else palette.paper3,
        animationSpec = tween(if (look.motionMode == MotionMode.OFF) 0 else 160),
        label = "sw_bg"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "sw_thumb"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(SwissControlShape)
            .clickable(role = Role.Switch) {
                PixelSoundEngine.playBlip(look.soundEnabled, if (!checked) 880 else 520, 16)
                onCheckedChange(!checked)
            }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = DotkitType.bodyUi(14, bold = false), color = palette.ink, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .width(50.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(trackColor)
                .border(1.dp, if (checked) Color.Transparent else palette.hairline, RoundedCornerShape(14.dp))
                .padding(3.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (checked) chapterHue.onColor else palette.ink2)
            )
        }
    }
}

/**
 * Stepped Slider + Direct Custom Value Editor.
 * Keeps - / + step buttons and interactive slider segments, and adds an editable numeric field
 * so users can always type any exact custom integer value directly.
 */
@Composable
fun PixelSteppedSlider(
    label: String,
    value: Int,
    valueRange: IntRange,
    step: Int = 1,
    unitSuffix: String = "",
    chapterHue: ChapterHue = LocalDotkitLook.current.accentHue,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDotkitLook.current.palette
    val accent = palette.chapterColor(chapterHue)
    val totalSteps = ((valueRange.last - valueRange.first) / step.coerceAtLeast(1)).coerceIn(2, 16)
    val fraction = ((value - valueRange.first).toFloat() / (valueRange.last - valueRange.first).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val filledSegments = (fraction * totalSteps).roundToInt()

    var editingCustom by remember { mutableStateOf(false) }
    var customDraft by remember(value) { mutableStateOf(value.toString()) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = DotkitType.monoLabel(11), color = palette.ink2, modifier = Modifier.weight(1f))
            AnimatedContent(targetState = editingCustom, label = "slider_edit") { isEditing ->
                if (!isEditing) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(accent.copy(alpha = 0.12f))
                            .clickable {
                                customDraft = value.toString()
                                editingCustom = true
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("$value$unitSuffix", style = DotkitType.monoData(13), color = accent)
                        Text("✎", style = DotkitType.monoLabel(10), color = palette.ink2)
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(min = 64.dp, max = 110.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.paper)
                                .border(1.5.dp, accent, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            BasicTextField(
                                value = customDraft,
                                onValueChange = { raw ->
                                    val clean = sanitizeNumericInput(customDraft, raw, allowDecimal = false, allowNegative = valueRange.first < 0)
                                    customDraft = clean
                                    clean.toIntOrNull()?.let { parsed ->
                                        onValueChange(parsed.coerceIn(valueRange.first, valueRange.last))
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = DotkitType.monoData(13).copy(color = palette.ink),
                                cursorBrush = SolidColor(accent)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(accent)
                                .clickable {
                                    customDraft.toIntOrNull()?.let { parsed ->
                                        onValueChange(parsed.coerceIn(valueRange.first, valueRange.last))
                                    }
                                    editingCustom = false
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text("✓", style = DotkitType.monoLabel(11), color = chapterHue.onColor)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PixelChip(
                label = "−",
                onClick = { onValueChange((value - step).coerceAtLeast(valueRange.first)) }
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.paper3)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                for (i in 0 until totalSteps) {
                    val segVal = valueRange.first + ((i + 1) * (valueRange.last - valueRange.first)) / totalSteps
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (i < filledSegments) accent else palette.paper2)
                            .clickable { onValueChange(segVal) }
                    )
                }
            }
            PixelChip(
                label = "+",
                onClick = { onValueChange((value + step).coerceAtMost(valueRange.last)) }
            )
        }
    }
}

@Composable
fun PixelProgressBar(
    progress: Float,
    chapterHue: ChapterHue = LocalDotkitLook.current.accentHue,
    segments: Int = 10,
    modifier: Modifier = Modifier
) {
    val palette = LocalDotkitLook.current.palette
    val clamped = progress.coerceIn(0f, 1f)
    val animatedPct by animateFloatAsState(
        targetValue = clamped,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "progress_bar"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(palette.paper3)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedPct)
                .height(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(palette.chapterColor(chapterHue))
        )
    }
}

/**
 * Dedicated numeric input field using [sanitizeNumericInput] so typing after clearing to "0"
 * produces "653" instead of "0653".
 */
@Composable
fun PixelNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "0",
    modifier: Modifier = Modifier,
    allowDecimal: Boolean = true,
    allowNegative: Boolean = false,
    chapterHue: ChapterHue = LocalDotkitLook.current.accentHue,
    tag: String? = null
) {
    PixelTextField(
        value = value,
        onValueChange = { raw ->
            onValueChange(sanitizeNumericInput(value, raw, allowDecimal = allowDecimal, allowNegative = allowNegative))
        },
        placeholder = placeholder,
        modifier = modifier,
        singleLine = true,
        chapterHue = chapterHue,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number
        ),
        tag = tag
    )
}

@Composable
fun PixelTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minHeight: Dp = 48.dp,
    chapterHue: ChapterHue = LocalDotkitLook.current.accentHue,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    tag: String? = null
) {
    val look = LocalDotkitLook.current
    val palette = look.palette
    val accent = palette.chapterColor(chapterHue)
    var isFocused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) accent else palette.hairline,
        animationSpec = tween(if (look.motionMode == MotionMode.OFF) 0 else 150),
        label = "tf_border"
    )
    val mod = if (tag != null) modifier.testTag(tag) else modifier
    Box(
        modifier = mod
            .fillMaxWidth()
            .defaultMinSize(minHeight = minHeight)
            .clip(SwissControlShape)
            .background(palette.paper)
            .border(if (isFocused) 1.2.dp else 0.8.dp, borderColor, SwissControlShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = DotkitType.bodyUi(14),
                color = palette.ink3
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = DotkitType.monoData(14).copy(color = palette.ink),
            cursorBrush = SolidColor(accent),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused }
        )
    }
}

@Composable
fun HelpSheetCard(
    specimenCode: String,
    title: String,
    paragraph: String,
    privacyNote: String = "Runs locally on your device.",
    modifier: Modifier = Modifier
) {
    val palette = LocalDotkitLook.current.palette
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SwissCardShape)
            .background(palette.paper2)
            .border(1.dp, palette.hairline, SwissCardShape)
            .padding(16.dp)
    ) {
        Text(title.uppercase(), style = DotkitType.monoLabel(10), color = palette.ink2)
        Spacer(Modifier.height(4.dp))
        Text(text = paragraph, style = DotkitType.bodyUi(13), color = palette.ink)
        Spacer(Modifier.height(6.dp))
        Text(text = privacyNote, style = DotkitType.monoLabel(10), color = palette.ink3)
    }
}
