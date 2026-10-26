package com.red.app.ui.jelly

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.app.theme.NuvioTokens
import kotlin.math.abs
import kotlin.math.max

data class FloatingNavigationItem(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val drawableRes: Int? = null,
    val content: (@Composable (() -> Unit) -> Unit)? = null
)

/**
 * 1:1 Rendering of Nuvio Profile Avatar (Screenshots 1, 2, 3, 4).
 * Circular cyan face, dark ring border, eyes, cute orange hair/cap and smile.
 */
@Composable
fun NuvioAvatarIcon(
    selected: Boolean,
    modifier: Modifier = Modifier,
    size: Int = 26
) {
    val borderColor = if (selected) Color.White else Color(0x66FFFFFF)
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .border(1.5.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // 1. Cyan/Blue base face background
            drawCircle(
                color = Color(0xFF38BDF8),
                radius = w / 2,
                center = Offset(w / 2, h / 2)
            )

            // 2. Orange hair / cap on top
            drawArc(
                color = Color(0xFFF97316),
                startAngle = 195f,
                sweepAngle = 150f,
                useCenter = true,
                topLeft = Offset(w * 0.12f, h * 0.08f),
                size = androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.55f)
            )

            // 3. Left eye
            drawCircle(
                color = Color(0xFF0F172A),
                radius = w * 0.07f,
                center = Offset(w * 0.35f, h * 0.52f)
            )

            // 4. Right eye
            drawCircle(
                color = Color(0xFF0F172A),
                radius = w * 0.07f,
                center = Offset(w * 0.65f, h * 0.52f)
            )

            // 5. Smiling arc mouth
            drawArc(
                color = Color(0xFF0F172A),
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(w * 0.36f, h * 0.62f),
                size = androidx.compose.ui.geometry.Size(w * 0.28f, h * 0.18f),
                style = Stroke(width = w * 0.06f, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
internal fun JellyTabRow(
    items: List<FloatingNavigationItem>,
    labelFraction: Float,
    motion: JellyMotion,
    active: Boolean,
    compactSize: Boolean,
    modifier: Modifier,
) {
    val color = if (active) Color.White else NuvioTokens.Colors.textMuted
    val iconSize = if (compactSize) 22.dp else 26.dp
    val labelHeight = if (compactSize) 14.dp else 16.dp
    val iconModifier = Modifier.size(iconSize)
    val iconTint = if (active) Color.White else color

    Row(
        modifier = modifier.padding(4.dp).clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            Box(
                Modifier.weight(1f).fillMaxHeight().graphicsLayer {
                    val scale = if (active) motion.frame.contentScale else 1f
                    scaleX = scale
                    scaleY = scale
                },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(iconSize).graphicsLayer { translationY = 2.dp.toPx() * labelFraction }, contentAlignment = Alignment.Center) {
                        when {
                            item.drawableRes != null -> {
                                Icon(
                                    painter = painterResource(item.drawableRes),
                                    contentDescription = null,
                                    modifier = iconModifier,
                                    tint = iconTint
                                )
                            }
                            item.icon != null -> {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    modifier = iconModifier,
                                    tint = iconTint
                                )
                            }
                            else -> {
                                Spacer(modifier = iconModifier)
                            }
                        }
                    }
                    Box(Modifier.height(labelHeight * labelFraction).fillMaxWidth().clipToBounds().alpha(labelFraction)) {
                        Text(
                            text = item.label,
                            color = color,
                            style = TextStyle(
                                fontSize = if (compactSize) 11.sp else 12.sp,
                                lineHeight = if (compactSize) 13.sp else 15.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun JellyTabTargets(
    items: List<FloatingNavigationItem>,
    labelFraction: Float,
    motion: JellyMotion,
    compactSize: Boolean,
    modifier: Modifier,
) {
    Row(modifier = modifier.padding(horizontal = 4.dp).selectableGroup()) {
        items.forEachIndexed { index, item ->
            val onClick = {
                motion.select(index)
                item.onClick()
            }
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(
                        selected = item.selected,
                        role = Role.Tab,
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                    .clearAndSetSemantics {
                        role = Role.Tab
                        selected = item.selected
                        contentDescription = item.label
                        onClick { onClick(); true }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (item.content != null) {
                    Column(
                        modifier = Modifier.graphicsLayer {
                            val frame = motion.frame
                            val coverage = (1f - abs(frame.position - index)).coerceIn(0f, 1f)
                            val scale = 1f + (frame.contentScale - 1f) * coverage
                            scaleX = scale
                            scaleY = scale
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .then(if (compactSize) Modifier.size(22.dp) else Modifier.size(26.dp))
                                .graphicsLayer { translationY = 2.dp.toPx() * labelFraction },
                            contentAlignment = Alignment.Center
                        ) {
                            item.content.invoke(onClick)
                        }
                        Spacer(Modifier.height((if (compactSize) 14.dp else 16.dp) * labelFraction))
                    }
                }
            }
        }
    }
}

internal suspend fun PointerInputScope.detectJellyTabGestures(
    motion: JellyMotion,
    density: Float,
    currentItems: () -> List<FloatingNavigationItem>,
) {
    forEachGesture {
        awaitPointerEventScope {
            val down = awaitFirstDown(requireUnconsumed = false)
            motion.begin(down.position.x / density, down.position.y / density)
            var claimed = false
            var finished = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (!motion.dragging) {
                        finished = true
                        break
                    }
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (event.changes.count { it.pressed } > 1) break
                    val delta = change.position - down.position
                    if (max(abs(delta.x), abs(delta.y)) > viewConfiguration.touchSlop) claimed = true
                    if (claimed) change.consume()
                    awaitPointerEvent(PointerEventPass.Main)
                    if (!motion.dragging) {
                        finished = true
                        break
                    }
                    if (change.isConsumed && !claimed) break
                    motion.drag(delta.x / density, delta.y / density)
                    if (!change.pressed) {
                        val visualIndex = motion.finish()
                        val items = currentItems()
                        finished = true
                        items.getOrNull(visualIndex)?.onClick?.invoke()
                        change.consume()
                        break
                    }
                }
            } finally {
                if (!finished) {
                    val items = currentItems()
                    val selectedIndex = items.indexOfFirst { it.selected }
                    motion.cancel(selectedIndex)
                }
            }
        }
    }
}
