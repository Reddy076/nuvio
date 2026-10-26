package com.red.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

enum class NuvioCardDepthSurface {
    Posters,
    ContinueWatching,
    Cards,
    Dialogs
}

/**
 * Applies Nuvio's signature 3D depth and glass specular sheen effect.
 * Optimized for 60/120 FPS scrolling without per-frame Path allocations.
 */
@Composable
fun Modifier.nuvioCardDepth(
    shape: Shape,
    surface: NuvioCardDepthSurface = NuvioCardDepthSurface.Posters,
    edgeStrength: Float = when (surface) {
        NuvioCardDepthSurface.Posters -> 24f
        NuvioCardDepthSurface.ContinueWatching -> 20f
        NuvioCardDepthSurface.Cards -> 22f
        NuvioCardDepthSurface.Dialogs -> 28f
    },
    sheenStrength: Float = 14f,
    edgeCoverage: Float = 25f
): Modifier {
    return cardDepthVisual(
        shape = shape,
        edgeStrength = edgeStrength,
        sheenStrength = sheenStrength,
        edgeCoverage = edgeCoverage
    )
}

fun Modifier.cardDepthVisual(
    shape: Shape,
    edgeStrength: Float = 24f,
    sheenStrength: Float = 14f,
    edgeCoverage: Float = 25f
): Modifier {
    val edgeTop = (edgeStrength.coerceIn(0f, 100f) / 100f)
    val sheen = (sheenStrength.coerceIn(0f, 100f) / 100f)
    val coverage = (edgeCoverage.coerceIn(0f, 100f) / 100f)

    val withEdge = if (edgeTop > 0f) {
        border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = edgeTop),
                    Color.White.copy(alpha = edgeTop * (0.33f + 0.67f * coverage)),
                    Color.White.copy(alpha = edgeTop * coverage * 0.4f),
                )
            ),
            shape = shape
        )
    } else {
        this
    }

    return if (sheen > 0f) {
        withEdge.drawWithContent {
            drawContent()
            val sheenHeight = size.height * 0.22f
            if (sheenHeight > 0f) {
                // The parent composable is already clipped to `shape`,
                // so drawing the top vertical gradient sheen directly renders
                // without expensive Path allocation or software clipPath overhead.
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = sheen),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = sheenHeight,
                    ),
                    size = Size(size.width, sheenHeight),
                )
            }
        }
    } else {
        withEdge
    }
}
