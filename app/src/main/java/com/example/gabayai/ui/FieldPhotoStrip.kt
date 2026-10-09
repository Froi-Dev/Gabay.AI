package com.example.gabayai.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.gabayai.domain.FieldMatch
import com.example.gabayai.domain.OcrBox
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Draws from the very same upright bitmap ML Kit read; no crop bitmap allocation. */
@Composable
fun FieldPhotoStrip(bitmap: Bitmap, match: FieldMatch, fil: Boolean) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val crop = remember(bitmap, match.line.box, match.context) {
        val label = match.line.box
        val section = match.context?.box
        val padding = max(24, label.height)
        val left = min(label.left, section?.left ?: label.left) - padding
        val right = max(label.right, section?.right ?: label.right) + padding * 2
        val top = min(label.top, section?.top ?: label.top) - padding
        val bottom = max(label.bottom, section?.bottom ?: label.bottom) + padding * 2
        OcrBox(left.coerceIn(0, bitmap.width - 1), top.coerceIn(0, bitmap.height - 1),
            right.coerceIn(1, bitmap.width), bottom.coerceIn(1, bitmap.height))
    }
    Canvas(Modifier.fillMaxWidth().height(156.dp).background(Color.White)
        .border(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outline)
        .semantics { contentDescription = word(fil, "Bahagi ng orihinal na picture: ${match.line.text}", "Original photo detail: ${match.line.text}") }) {
        val scale = min(size.width / crop.width.coerceAtLeast(1), size.height / crop.height.coerceAtLeast(1))
        val rendered = Size(crop.width * scale, crop.height * scale)
        val offset = Offset((size.width - rendered.width) / 2, (size.height - rendered.height) / 2)
        drawImage(image, IntOffset(crop.left, crop.top), IntSize(crop.width.coerceAtLeast(1), crop.height.coerceAtLeast(1)),
            IntOffset(offset.x.roundToInt(), offset.y.roundToInt()), IntSize(rendered.width.roundToInt().coerceAtLeast(1), rendered.height.roundToInt().coerceAtLeast(1)))
        val line = match.line.box
        val highlight = Offset(offset.x + (line.left - crop.left) * scale, offset.y + (line.top - crop.top) * scale)
        val highlightSize = Size(line.width.coerceAtLeast(1) * scale, line.height.coerceAtLeast(1) * scale)
        drawRect(GabayBlue.copy(alpha = .08f), highlight, highlightSize)
        drawRect(GabayBlue, highlight, highlightSize, style = Stroke(2.dp.toPx()))
    }
}

/** Scrolls the actual photo to the selected OCR label, without duplicating pixels. */
@Composable
fun FullFieldPhoto(bitmap: Bitmap, match: FieldMatch, fil: Boolean) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    var zoom by rememberSaveable { mutableIntStateOf(1) }
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(word(fil, "Laki ng picture", "Photo zoom"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            listOf(1, 2).forEach { level ->
                FilterChip(selected = zoom == level, onClick = { zoom = level }, label = { Text("${level}×") },
                    modifier = Modifier.heightIn(min = 48.dp).semantics {
                        contentDescription = word(fil, "Ipakita ang picture sa ${level}×", "Show photo at ${level}×")
                    })
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(320.dp).border(1.dp, MaterialTheme.colorScheme.outline)) {
            val viewportWidth = constraints.maxWidth.toFloat()
            val viewportHeight = constraints.maxHeight.toFloat()
            // Bound in physical pixels before converting to dp/Int. Even a very
            // narrow source cannot create million-pixel Compose constraints.
            val baseScale = min(viewportWidth / bitmap.width, 8_000f / max(bitmap.width, bitmap.height))
            val scale = baseScale * zoom
            val renderedWidth = (bitmap.width * scale).coerceAtLeast(1f)
            val renderedHeight = (bitmap.height * scale).coerceAtLeast(1f)
            val canvasWidth = max(viewportWidth, renderedWidth)
            val imageLeft = (canvasWidth - renderedWidth) / 2
            val density = androidx.compose.ui.platform.LocalDensity.current
            val canvasWidthDp = with(density) { canvasWidth.toDp() }
            val canvasHeightDp = with(density) { renderedHeight.toDp() }
            val line = match.line.box
            val selectedX = (imageLeft + (line.left + line.width / 2f) * scale - viewportWidth / 2).roundToInt().coerceAtLeast(0)
            val selectedY = ((line.top + line.height / 2f) * scale - viewportHeight / 2).roundToInt().coerceAtLeast(0)
            LaunchedEffect(line, zoom, vertical.maxValue, horizontal.maxValue, viewportWidth) {
                vertical.scrollTo(selectedY.coerceAtMost(vertical.maxValue))
                horizontal.scrollTo(selectedX.coerceAtMost(horizontal.maxValue))
            }
            Box(Modifier.fillMaxSize().verticalScroll(vertical).horizontalScroll(horizontal)) {
                Canvas(Modifier.width(canvasWidthDp).height(canvasHeightDp)
                    .semantics { contentDescription = word(fil, "Orihinal na picture. I-scroll para makita ang ibang bahagi.", "Original photo. Scroll to see other sections.") }) {
                    drawImage(image, dstOffset = IntOffset(imageLeft.roundToInt(), 0), dstSize = IntSize(renderedWidth.roundToInt().coerceAtLeast(1), renderedHeight.roundToInt().coerceAtLeast(1)))
                    val highlight = Offset(imageLeft + line.left * scale, line.top * scale)
                    val highlightSize = Size(line.width * scale, line.height * scale)
                    drawRect(GabayBlue.copy(alpha = .08f), highlight, highlightSize)
                    drawRect(GabayBlue, highlight, highlightSize, style = Stroke(2.dp.toPx()))
                }
            }
        }
    }
}
