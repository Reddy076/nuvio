package com.red.app.ui.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.red.app.R
import com.red.app.data.MissAvRepository
import com.red.app.model.MediaItem
import com.red.app.theme.NuvioTokens
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

enum class PlayerResizeMode(val label: String) {
    FIT("FIT"),
    FILL("FILL"),
    ZOOM("ZOOM")
}

@Composable
fun PlayerScreen(
    item: MediaItem,
    streamUrl: String,
    qualityTitle: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableStateOf(0) }
    var totalDurationMs by remember { mutableStateOf(1) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var resizeMode by remember { mutableStateOf(PlayerResizeMode.FIT) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }

    // User scrubbing state for smooth timeline dragging
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableStateOf(0f) }

    // Gesture HUD feedback (Seek, Volume, Brightness)
    var feedbackText by remember { mutableStateOf<String?>(null) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Keep screen on during playback
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // Save continue watching progress
            if (totalDurationMs > 0 && currentPositionMs > 1000) {
                val frac = (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                val timeLeftMins = ((totalDurationMs - currentPositionMs) / 60000).coerceAtLeast(1)
                MissAvRepository.addContinueWatching(
                    item = item,
                    progressPercent = frac,
                    timeLeft = "${timeLeftMins}m left"
                )
            }
        }
    }

    // Auto-hide controls after 4.5 seconds of inactivity
    LaunchedEffect(showControls, isPlaying, lastInteractionTime, isLocked) {
        if (showControls && isPlaying && !isLocked) {
            delay(4500)
            showControls = false
        }
    }

    // Playback time tracking loop
    LaunchedEffect(isPlaying) {
        while (true) {
            videoViewRef?.let { vv ->
                if (vv.isPlaying) {
                    if (!isScrubbing) {
                        currentPositionMs = vv.currentPosition
                    }
                    val dur = vv.duration
                    if (dur > 0) totalDurationMs = dur
                    if (isBuffering) isBuffering = false
                }
            }
            delay(400)
        }
    }

    // Helper to register interaction
    fun onUserTouch() {
        lastInteractionTime = System.currentTimeMillis()
    }

    // Seek helper
    fun seekRelative(deltaMs: Int) {
        videoViewRef?.let { vv ->
            val target = (vv.currentPosition + deltaMs).coerceIn(0, totalDurationMs)
            vv.seekTo(target)
            currentPositionMs = target
            feedbackText = if (deltaMs < 0) "⏪ -10s" else "+10s ⏩"
            onUserTouch()
        }
    }

    // Cycle Playback Speed
    fun cycleSpeed() {
        val nextSpeed = when (playbackSpeed) {
            1.0f -> 1.25f
            1.25f -> 1.5f
            1.5f -> 2.0f
            2.0f -> 0.75f
            else -> 1.0f
        }
        playbackSpeed = nextSpeed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                videoViewRef?.let { vv ->
                    val mpField = VideoView::class.java.getDeclaredField("mMediaPlayer")
                    mpField.isAccessible = true
                    val mp = mpField.get(vv) as? android.media.MediaPlayer
                    mp?.playbackParams = PlaybackParams().setSpeed(nextSpeed)
                }
            } catch (ignored: Exception) {}
        }
        feedbackText = "Speed: ${playbackSpeed}x"
        onUserTouch()
    }

    // Back handling
    BackHandler {
        if (isLocked) {
            isLocked = false
            feedbackText = "Controls Unlocked"
        } else {
            onBackClick()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video Surface with aspect ratio scaling
        val videoScaleModifier = when (resizeMode) {
            PlayerResizeMode.FIT -> Modifier.fillMaxSize()
            PlayerResizeMode.FILL -> Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.15f
                    scaleY = 1.15f
                }
            PlayerResizeMode.ZOOM -> Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.35f
                    scaleY = 1.35f
                }
        }

        AndroidView(
            modifier = videoScaleModifier,
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
                        isBuffering = false
                    }
                    setOnCompletionListener {
                        isPlaying = false
                        showControls = true
                    }
                    setOnErrorListener { _, _, _ ->
                        isBuffering = false
                        feedbackText = "Stream Reconnecting..."
                        true
                    }
                    videoViewRef = this
                }
            }
        )

        // Gesture Detection Layer (Taps, Double Taps, Brightness & Volume Swipes)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isLocked) {
                    if (isLocked) {
                        detectTapGestures(onTap = {
                            showControls = !showControls
                            onUserTouch()
                        })
                    } else {
                        detectTapGestures(
                            onTap = {
                                showControls = !showControls
                                onUserTouch()
                            },
                            onDoubleTap = { offset ->
                                val width = size.width
                                if (offset.x < width * 0.45f) {
                                    seekRelative(-10000)
                                } else if (offset.x > width * 0.55f) {
                                    seekRelative(10000)
                                } else {
                                    // Center double-tap: toggle play/pause
                                    videoViewRef?.let { vv ->
                                        if (vv.isPlaying) {
                                            vv.pause()
                                            isPlaying = false
                                        } else {
                                            vv.start()
                                            isPlaying = true
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
                .pointerInput(isLocked) {
                    if (!isLocked) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val width = size.width
                            val isLeft = change.position.x < width / 2
                            val deltaY = -dragAmount.y

                            if (isLeft) {
                                // Adjust Brightness on Left Half
                                activity?.let { act ->
                                    val lp = act.window.attributes
                                    val currentBrightness = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                                    val newBrightness = (currentBrightness + deltaY / 400f).coerceIn(0.05f, 1.0f)
                                    lp.screenBrightness = newBrightness
                                    act.window.attributes = lp
                                    feedbackText = "☀️ ${(newBrightness * 100).roundToInt()}%"
                                    onUserTouch()
                                }
                            } else {
                                // Adjust Volume on Right Half
                                audioManager?.let { am ->
                                    val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    val step = if (deltaY > 0) 1 else -1
                                    if (Math.abs(deltaY) > 8f) {
                                        val newVol = (currentVol + step).coerceIn(0, maxVol)
                                        am.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                        val percent = ((newVol.toFloat() / maxVol.toFloat()) * 100).roundToInt()
                                        feedbackText = "🔊 $percent%"
                                        onUserTouch()
                                    }
                                }
                            }
                        }
                    }
                }
        )

        // Loading / Buffering Spinner
        if (isBuffering) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = NuvioTokens.Colors.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // Temporary Floating Gesture HUD Pill
        AnimatedVisibility(
            visible = feedbackText != null,
            enter = fadeIn(tween(100)) + scaleIn(initialScale = 0.85f),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.85f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            feedbackText?.let { text ->
                LaunchedEffect(text) {
                    delay(1200)
                    feedbackText = null
                }
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.82f), RoundedCornerShape(24.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 22.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = text,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Lock Toggle Icon when Locked
        if (isLocked && showControls) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                    .clickable {
                        isLocked = false
                        feedbackText = "Controls Unlocked"
                        onUserTouch()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Unlock",
                    tint = NuvioTokens.Colors.primaryVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Overlay Controls HUD (1:1 with Nuvio PlayerControlsShell)
        AnimatedVisibility(
            visible = showControls && !isLocked,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(220)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Shadow Gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.82f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Bottom Shadow Gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.88f)
                                )
                            )
                        )
                )

                // 1. Top HUD Bar (Back Arrow, Centered Title, Speed & Aspect Ratio)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back Button
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                            .clickable {
                                onUserTouch()
                                onBackClick()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Centered Title & Metadata
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = item.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "${item.code.ifBlank { "MISSAV" }} \u2022 $qualityTitle",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Top Right Controls (Speed, Aspect Ratio, Lock)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Playback Speed Button Pill
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .clip(RoundedCornerShape(17.dp))
                                .background(Color.Black.copy(alpha = 0.45f))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(17.dp))
                            .clickable { cycleSpeed() }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${playbackSpeed}x",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Aspect Ratio Toggle Button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                .clickable {
                                    resizeMode = when (resizeMode) {
                                        PlayerResizeMode.FIT -> PlayerResizeMode.FILL
                                        PlayerResizeMode.FILL -> PlayerResizeMode.ZOOM
                                        PlayerResizeMode.ZOOM -> PlayerResizeMode.FIT
                                    }
                                    feedbackText = "Ratio: ${resizeMode.label}"
                                    onUserTouch()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_player_aspect_ratio),
                                contentDescription = "Aspect Ratio",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Lock Controls Button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                .clickable {
                                    isLocked = true
                                    feedbackText = "Controls Locked"
                                    onUserTouch()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // 2. Center Playback Controls (10s Rewind, Large Play/Pause, 10s Forward)
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 10s Rewind Button
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                            .clickable { seekRelative(-10000) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_replay_10),
                            contentDescription = "Rewind 10s",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Center Large Play/Pause Button
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.22f))
                            .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
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
                                onUserTouch()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(
                                if (isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play
                            ),
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    // 10s Forward Button
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                            .clickable { seekRelative(10000) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_forward_10),
                            contentDescription = "Forward 10s",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // 3. Bottom Timeline & Metadata (1:1 with Nuvio PlayerTimeline)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    val displayPosMs = if (isScrubbing) scrubPositionMs.toInt() else currentPositionMs
                    val remainingMs = (totalDurationMs - displayPosMs).coerceAtLeast(0)

                    // Time Labels Row (Current Time on Left, Remaining Time on Right)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatPlaybackClock(displayPosMs),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = "-${formatPlaybackClock(remainingMs)}",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Interactive Scrub Slider with Nuvio Red Track
                    Slider(
                        value = if (isScrubbing) scrubPositionMs else currentPositionMs.toFloat(),
                        onValueChange = { newVal ->
                            isScrubbing = true
                            scrubPositionMs = newVal
                            onUserTouch()
                        },
                        onValueChangeFinished = {
                            isScrubbing = false
                            val seekTarget = scrubPositionMs.roundToInt()
                            videoViewRef?.seekTo(seekTarget)
                            currentPositionMs = seekTarget
                            onUserTouch()
                        },
                        valueRange = 0f..totalDurationMs.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color(0xFFE50914), // Nuvio Red
                            inactiveTrackColor = Color.White.copy(alpha = 0.22f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Bottom Pill Badges Row (Quality Pill, Ratio Pill, Audio Pill)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Quality Pill Badge
                            Box(
                                modifier = Modifier
                                    .background(
                                        Color(0xFFE50914).copy(alpha = 0.2f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .border(1.dp, Color(0xFFE50914).copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (qualityTitle.contains("1080")) "🌟 1080p FHD" else qualityTitle,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Audio Format Pill
                            Box(
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "AAC \u2022 Stereo",
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Resize Mode Indicator Pill
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = resizeMode.label,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.navigationBarsPadding())
                }
            }
        }
    }
}

private fun formatPlaybackClock(millis: Int): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
