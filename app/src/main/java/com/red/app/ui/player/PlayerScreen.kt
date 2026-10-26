package com.red.app.ui.player

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.red.app.data.MissAvRepository
import com.red.app.model.MediaItem
import com.red.app.theme.NuvioTokens
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun PlayerScreen(
    item: MediaItem,
    streamUrl: String,
    qualityTitle: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableStateOf(0) }
    var totalDurationMs by remember { mutableStateOf(1) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

    // Gesture HUD feedback states
    var feedbackText by remember { mutableStateOf<String?>(null) }

    // Keep screen on during playback
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Auto-hide controls after 4 seconds of inactivity
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(4000)
            showControls = false
        }
    }

    // Playback time tracking loop
    LaunchedEffect(isPlaying) {
        while (true) {
            videoViewRef?.let { vv ->
                if (vv.isPlaying) {
                    currentPositionMs = vv.currentPosition
                    val dur = vv.duration
                    if (dur > 0) totalDurationMs = dur
                }
            }
            delay(500)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Hardware Accelerated Video Surface
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                VideoView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    val headers = MissAvRepository.getStreamHeaders()
                    setVideoURI(Uri.parse(streamUrl), headers)
                    setOnPreparedListener { mp ->
                        totalDurationMs = mp.duration.coerceAtLeast(1)
                        mp.isLooping = false
                        start()
                        isPlaying = true
                    }
                    setOnCompletionListener {
                        isPlaying = false
                        showControls = true
                    }
                    setOnErrorListener { _, _, _ ->
                        feedbackText = "Buffer / Stream Error"
                        true
                    }
                    videoViewRef = this
                }
            }
        )

        // Gesture Detection Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            showControls = !showControls
                        },
                        onDoubleTap = { offset ->
                            val width = size.width
                            videoViewRef?.let { vv ->
                                if (offset.x < width / 2) {
                                    // Rewind 10s
                                    val newPos = (vv.currentPosition - 10000).coerceAtLeast(0)
                                    vv.seekTo(newPos)
                                    currentPositionMs = newPos
                                    feedbackText = "⏪ -10s"
                                } else {
                                    // Forward 10s
                                    val newPos = (vv.currentPosition + 10000).coerceAtMost(vv.duration)
                                    vv.seekTo(newPos)
                                    currentPositionMs = newPos
                                    feedbackText = "⏩ +10s"
                                }
                            }
                        }
                    )
                }
        )

        // Temporary Feedback Pill (Brightness / Volume / Seek)
        if (feedbackText != null) {
            LaunchedEffect(feedbackText) {
                delay(1200)
                feedbackText = null
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = feedbackText!!,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Overlay Controls HUD
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xCC000000),
                                Color.Transparent,
                                Color(0xEE000000)
                            )
                        )
                    )
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(NuvioTokens.Colors.surfaceGlass)
                                .clickable(onClick = onBackClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = item.title,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = item.code + " • " + qualityTitle,
                                color = NuvioTokens.Colors.cyanAccent,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Quality Badge
                    Box(
                        modifier = Modifier
                            .background(NuvioTokens.Colors.primary, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "HLS PROXY",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Center Play/Pause Button
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(NuvioTokens.Colors.primary.copy(alpha = 0.9f))
                        .clickable {
                            videoViewRef?.let { vv ->
                                if (vv.isPlaying) {
                                    vv.pause()
                                    isPlaying = false
                                } else {
                                    vv.start()
                                    isPlaying = true
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Refresh else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Bottom Scrub Bar & Controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    // Timeline Slider
                    Slider(
                        value = currentPositionMs.toFloat(),
                        onValueChange = { newVal ->
                            currentPositionMs = newVal.roundToInt()
                            videoViewRef?.seekTo(newVal.roundToInt())
                        },
                        valueRange = 0f..totalDurationMs.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = NuvioTokens.Colors.primary,
                            activeTrackColor = NuvioTokens.Colors.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(currentPositionMs),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = formatTime(totalDurationMs),
                            color = NuvioTokens.Colors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Int): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
