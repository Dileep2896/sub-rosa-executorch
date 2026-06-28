package com.subrosa.app.ui.theme

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

/** A small tiling alpha-noise tile, generated once, for paper grain. */
@Composable
fun rememberGrain(): ImageBitmap = remember {
    val s = 96
    val pixels = IntArray(s * s)
    val rnd = Random(7)
    for (i in pixels.indices) {
        val a = rnd.nextInt(46) // 0..45 alpha, black
        pixels[i] = a shl 24
    }
    Bitmap.createBitmap(pixels, s, s, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Warm paper: base tone + tiled grain + a soft oxblood vignette. Draw behind content. */
@Composable
fun rememberDossierPaper(): Modifier {
    val grain = rememberGrain()
    return remember(grain) {
        Modifier.drawWithCache {
            val grainBrush = ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated))
            val vignette = Brush.radialGradient(
                colors = listOf(Color.Transparent, OxbloodDeep.copy(alpha = 0.06f)),
                center = Offset(size.width * 0.5f, size.height * 0.30f),
                radius = size.maxDimension * 0.78f,
            )
            onDrawBehind {
                drawRect(Paper)
                drawRect(grainBrush, alpha = 0.55f)
                drawRect(vignette)
            }
        }
    }
}

/** Letter-spaced monospace small-caps label, e.g. "CONFIDENTIAL · ON-DEVICE". */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = InkFaint) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.2.sp),
        color = color,
        modifier = modifier,
    )
}

/** A taupe hairline rule. */
@Composable
fun RuleLine(modifier: Modifier = Modifier, color: Color = Hairline) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** Editorial numbered section header: brass numeral, mono title, trailing rule. */
@Composable
fun SectionNumeral(numeral: String, title: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(numeral, style = MaterialTheme.typography.titleLarge, color = Brass)
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
            color = Ink,
        )
        Box(Modifier.weight(1f).height(1.dp).background(Hairline))
    }
}

/** A printed-ledger row with a dotted leader between [label] and [value] (the "proof" look). */
@Composable
fun LedgerRow(label: String, value: String, valueColor: Color = Ink) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = InkSoft)
        Canvas(
            Modifier
                .weight(1f)
                .height(13.dp)
                .padding(horizontal = 6.dp),
        ) {
            drawLine(
                color = Hairline,
                start = Offset(0f, size.height - 3f),
                end = Offset(size.width, size.height - 3f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 5f), 0f),
            )
        }
        Text(value, style = MaterialTheme.typography.labelLarge, color = valueColor)
    }
}

/** A wax-seal rose emblem, drawn — the mark of sworn secrecy. */
@Composable
fun SealEmblem(modifier: Modifier = Modifier, diameter: Dp = 72.dp) {
    Canvas(modifier.size(diameter)) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)

        drawCircle(Oxblood, radius = r, center = c)
        drawCircle(OxbloodDeep, radius = r, center = c, style = Stroke(width = r * 0.07f))
        drawCircle(BrassSoft.copy(alpha = 0.85f), radius = r * 0.80f, center = c, style = Stroke(width = r * 0.03f))

        val petalLen = r * 0.56f
        val petalWid = r * 0.36f
        // outer whorl
        for (i in 0 until 5) {
            rotate(degrees = i * 72f, pivot = c) {
                drawOval(
                    color = Rose.copy(alpha = 0.92f),
                    topLeft = Offset(c.x - petalWid / 2f, c.y - petalLen),
                    size = Size(petalWid, petalLen),
                )
            }
        }
        // inner whorl, offset
        for (i in 0 until 5) {
            rotate(degrees = 36f + i * 72f, pivot = c) {
                drawOval(
                    color = BrassSoft.copy(alpha = 0.80f),
                    topLeft = Offset(c.x - petalWid * 0.30f, c.y - petalLen * 0.64f),
                    size = Size(petalWid * 0.60f, petalLen * 0.64f),
                )
            }
        }
        drawCircle(OxbloodDeep, radius = r * 0.11f, center = c)
    }
}

/** Staggered entrance: fade + slight rise, gated by [visible] and offset by [delayMillis]. */
@Composable
fun Reveal(visible: Boolean, delayMillis: Int = 0, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(durationMillis = 650, delayMillis = delayMillis)) +
            slideInVertically(tween(durationMillis = 650, delayMillis = delayMillis)) { it / 6 },
    ) { content() }
}

/** A destructive-action confirmation, dossier-styled. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PaperHigh,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = Ink) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium, color = InkSoft) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = MissingRed) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = InkFaint) } },
    )
}

/** A dossier-styled single-line search box: paper field, hairline border, oxblood cursor. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
        cursorBrush = SolidColor(Oxblood),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(PaperHigh)
            .border(1.dp, Hairline, RoundedCornerShape(4.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = InkFaint)
                }
                inner()
            }
        },
    )
}

/** A small selectable filter pill — oxblood when active, hairline outline when not. */
@Composable
fun FilterPill(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) Oxblood else Color.Transparent)
            .border(1.dp, if (selected) Oxblood else Hairline, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else InkSoft,
        )
    }
}
