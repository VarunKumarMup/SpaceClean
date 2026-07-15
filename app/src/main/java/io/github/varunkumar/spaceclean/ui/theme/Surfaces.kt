package io.github.varunkumar.spaceclean.ui.theme

import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Aurora mesh backdrop — soft cyan / violet / blue light blooms over a near-black
 * canvas. Gives the dark UI an atmospheric, premium "Aurora UI" depth instead of a
 * flat fill. Cheap: three radial gradients painted once behind the content.
 */
fun Modifier.auroraBackground(): Modifier = drawBehind {
    drawRect(AuroraBase)
    val w = size.width
    val h = size.height
    // top-left cyan bloom
    drawRect(Brush.radialGradient(
        colors = listOf(AuroraCyan, Color.Transparent),
        center = Offset(w * 0.12f, h * 0.06f),
        radius = w * 0.85f,
    ))
    // top-right violet bloom
    drawRect(Brush.radialGradient(
        colors = listOf(AuroraViolet, Color.Transparent),
        center = Offset(w * 0.96f, h * 0.02f),
        radius = w * 0.80f,
    ))
    // lower blue bloom
    drawRect(Brush.radialGradient(
        colors = listOf(AuroraBlue, Color.Transparent),
        center = Offset(w * 0.55f, h * 0.85f),
        radius = w * 1.0f,
    ))
}

/**
 * Frosted-glass surface — translucent fill, a top light reflection, an optional accent
 * tint bleeding from the top-left corner, and a gradient hairline edge. Layered over
 * the aurora backdrop it reads as real glassmorphism without an expensive backdrop blur.
 */
fun Modifier.glassSurface(
    corner: Dp = 20.dp,
    accent: Color = Color.Transparent,
    strong: Boolean = false,
): Modifier = this
    .clip(RoundedCornerShape(corner))
    .drawBehind {
        drawRect(if (strong) GlassFillStrong else GlassFill)
        if (accent != Color.Transparent) {
            drawRect(Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(0f, 0f),
                radius = size.width * 0.9f,
            ))
        }
        // light reflection across the top edge
        drawRect(Brush.verticalGradient(
            colors = listOf(GlassHighlight, Color.Transparent),
            startY = 0f,
            endY   = size.height * 0.45f,
        ))
    }
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(listOf(GlassBorder, GlassBorderSoft)),
        shape = RoundedCornerShape(corner),
    )
