package com.red.app.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.red.app.R
import com.red.app.model.MediaItem
import com.red.app.theme.NuvioTokens
import com.red.app.ui.glass.GlassBarSurface
import com.red.app.ui.jelly.FloatingNavigationItem
import com.red.app.ui.jelly.JellyMotion
import com.red.app.ui.jelly.JellyTabRow
import com.red.app.ui.jelly.JellyTabTargets
import com.red.app.ui.jelly.NuvioAvatarIcon
import com.red.app.ui.jelly.detectJellyTabGestures
import com.red.app.ui.jelly.drawJellyGlow
import com.red.app.ui.jelly.drawJellyPill
import com.red.app.ui.jelly.jellyPillPath

enum class NavTab(val label: String) {
    HOME("Home"),
    SEARCH("Search"),
    LIBRARY("Library"),
    PROFILE("Profile")
}

sealed class Screen {
    object Main : Screen()
    object Downloads : Screen()
    data class Detail(val item: MediaItem) : Screen()
    data class Player(val item: MediaItem, val streamUrl: String, val qualityTitle: String) : Screen()
}

/**
 * Scroll-aware state for the floating navigation bar.
 * Matches Nuvio's NuvioNavBarScrollState with dynamic label collapsing.
 */
@Stable
class NuvioNavBarScrollState {
    var labelVisibility by mutableStateOf(1f)
        private set

    private var accumulatedDelta = 0f

    fun expand() {
        labelVisibility = 1f
        accumulatedDelta = 0f
    }

    fun collapse() {
        labelVisibility = 0f
        accumulatedDelta = 0f
    }

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val deltaY = available.y
            if (deltaY == 0f) return Offset.Zero

            accumulatedDelta += deltaY

            if (accumulatedDelta < -SCROLL_THRESHOLD && labelVisibility != 0f) {
                labelVisibility = 0f
                accumulatedDelta = 0f
            } else if (accumulatedDelta > SCROLL_THRESHOLD && labelVisibility != 1f) {
                labelVisibility = 1f
                accumulatedDelta = 0f
            }

            if (deltaY < 0f && accumulatedDelta > 0f) accumulatedDelta = deltaY
            if (deltaY > 0f && accumulatedDelta < 0f) accumulatedDelta = deltaY

            return Offset.Zero
        }
    }

    companion object {
        private const val SCROLL_THRESHOLD = 60f
    }
}

@Composable
fun rememberNuvioNavBarScrollState(): NuvioNavBarScrollState {
    return remember { NuvioNavBarScrollState() }
}

/**
 * 1:1 Port of Nuvio's FloatingNavigationBar with Jelly elastic physics,
 * AGSL Glass shader, and scroll-driven morphing.
 */
@Composable
fun NuvioFloatingBottomBar(
    currentTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
    scrollState: NuvioNavBarScrollState? = null,
    compactSize: Boolean = false,
    glowEnabled: Boolean = true
) {
    val items = listOf(
        FloatingNavigationItem(
            label = "Home",
            selected = currentTab == NavTab.HOME,
            onClick = { onTabSelected(NavTab.HOME) },
            drawableRes = R.drawable.sidebar_home
        ),
        FloatingNavigationItem(
            label = "Search",
            selected = currentTab == NavTab.SEARCH,
            onClick = { onTabSelected(NavTab.SEARCH) },
            drawableRes = R.drawable.sidebar_search
        ),
        FloatingNavigationItem(
            label = "Library",
            selected = currentTab == NavTab.LIBRARY,
            onClick = { onTabSelected(NavTab.LIBRARY) },
            drawableRes = R.drawable.sidebar_library
        ),
        FloatingNavigationItem(
            label = "Profile",
            selected = currentTab == NavTab.PROFILE,
            onClick = { onTabSelected(NavTab.PROFILE) },
            content = {
                NuvioAvatarIcon(selected = currentTab == NavTab.PROFILE)
            }
        )
    )

    val glowStrength by animateFloatAsState(
        targetValue = if (glowEnabled) 1f else 0f,
        animationSpec = tween(420, easing = NuvioTokens.Motion.standard)
    )
    val accentColor = Color.White
    val selectedSurface = Color.White.copy(alpha = 0.16f)

    val labelFraction by animateFloatAsState(
        targetValue = scrollState?.labelVisibility ?: 1f,
        animationSpec = tween(NuvioTokens.Motion.sheetEnterMillis, easing = NuvioTokens.Motion.standard)
    )

    val selectedIndex = items.indexOfFirst { it.selected }.coerceAtLeast(0)
    val motion = remember(items.size) { JellyMotion(selectedIndex, items.size) }
    val currentItems by rememberUpdatedState(items)
    val density = LocalDensity.current

    val trackHeight = 48.dp + (if (compactSize) 8.dp else 16.dp) * labelFraction
    val horizontalPadding = 58.dp - 30.dp * labelFraction

    LaunchedEffect(selectedIndex, items.size) {
        motion.select(selectedIndex)
    }

    LaunchedEffect(motion.running) {
        if (!motion.running) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (motion.running) {
            withFrameNanos { now ->
                motion.advance((now - previous) / 1_000_000_000.0)
                previous = now
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 8.dp)
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.BottomCenter,
    ) {
        var trackWidthPx by remember { mutableStateOf(0f) }

        Box(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .height(trackHeight)
                .onSizeChanged {
                    trackWidthPx = it.width.toFloat()
                    motion.resize(it.width / density.density, it.height / density.density, items.size)
                }
                .pointerInput(motion, density, items.size) {
                    detectJellyTabGestures(motion, density.density) { currentItems }
                },
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val frame = motion.frame
                        scaleX = frame.trackScale
                        scaleY = frame.trackScale
                    },
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            val frame = motion.frame
                            transformOrigin = TransformOrigin(
                                if (trackWidthPx > 0f) (frame.originX * density.density / trackWidthPx).coerceIn(0f, 1f) else 0.5f,
                                0.5f,
                            )
                            scaleX = frame.trackScaleX
                            translationY = frame.trackOffsetY * density.density
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer { translationX = motion.frame.panelOffset * density.density },
                    ) {
                        // 1. Glass backdrop surface
                        Box(
                            Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(50))
                                .drawWithContent {
                                    drawContent()
                                    drawJellyGlow(motion.frame, accentColor.copy(alpha = 0.25f * glowStrength))
                                },
                        ) {
                            GlassBarSurface(Modifier.matchParentSize(), glowStrength)
                        }

                        // 2. Unselected background tab row (clipped with ClipOp.Difference)
                        Box(
                            Modifier.matchParentSize().drawWithContent {
                                if (selectedIndex >= 0) {
                                    clipPath(jellyPillPath(motion.frame, items.size), ClipOp.Difference) {
                                        this@drawWithContent.drawContent()
                                    }
                                } else {
                                    drawContent()
                                }
                            },
                        ) {
                            JellyTabRow(items, labelFraction, motion, active = false, compactSize = compactSize, modifier = Modifier.matchParentSize())
                        }

                        // 3. Highlighted active pill & active tab row
                        if (selectedIndex >= 0) {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .clearAndSetSemantics {}
                                    .drawWithContent {
                                        drawJellyPill(
                                            motion.frame,
                                            items.size,
                                            selectedSurface,
                                            accentColor.copy(alpha = 0.35f * glowStrength),
                                        ) { drawContent() }
                                    },
                            ) {
                                JellyTabRow(items, labelFraction, motion, active = true, compactSize = compactSize, modifier = Modifier.matchParentSize())
                            }
                        }

                        // 4. Hit targets
                        JellyTabTargets(items, labelFraction, motion, compactSize, Modifier.matchParentSize())
                    }
                }
            }
        }
    }
}
