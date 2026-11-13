package com.example.tools.image

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.design.ChapterHue
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import kotlin.math.roundToInt

/**
 * Interactive Before/After split-slider comparison viewer.
 */
@Composable
fun ImageBeforeAfterSlider(
    before: Bitmap,
    after: Bitmap,
    chapterHue: ChapterHue,
    modifier: Modifier = Modifier
) {
    val palette = LocalDotkitLook.current.palette
    val accent = palette.chapterColor(chapterHue)
    var splitFraction by remember { mutableFloatStateOf(0.5f) }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(palette.paper)
            .border(1.dp, palette.hairline, RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    splitFraction = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0.05f, 0.95f)
                }
            }
    ) {
        val totalWidthPx = with(density) { maxWidth.toPx() }
        val splitX = (totalWidthPx * splitFraction).roundToInt()

        // Full AFTER on background
        Image(
            bitmap = after.asImageBitmap(),
            contentDescription = "Edited After Image",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        // Clipped BEFORE on left side
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(splitFraction)
                .clip(RectangleShape)
                .clipToBounds()
        ) {
            Image(
                bitmap = before.asImageBitmap(),
                contentDescription = "Original Before Image",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(this@BoxWithConstraints.maxWidth)
                    .fillMaxHeight()
            )
        }

        // Divider bar
        Box(
            modifier = Modifier
                .offset { IntOffset(splitX - 2, 0) }
                .width(3.dp)
                .fillMaxHeight()
                .background(accent)
        )

        Text(
            "BEFORE",
            style = DotkitType.monoLabel(9),
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
        Text(
            "AFTER",
            style = DotkitType.monoLabel(9),
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(accent.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
