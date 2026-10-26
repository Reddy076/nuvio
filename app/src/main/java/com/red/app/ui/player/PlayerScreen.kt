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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.zIndex
import com.red.app.R
import com.red.app.data.MissAvRepository
import com.red.app.model.MediaItem
import com.red.app.model.StreamQualityOption
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
    streamOptions: List<StreamQualityOption> = emptyList(),
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    // Quality formatting helper
    fun cleanQualityText(raw: String): String {
        return when {
            raw.contains("1080") -> "🌟 1080p FHD"
            raw.contains("720") -> "📺 720p HD"
            raw.contains("480") -> "📱 480p SD"
            raw.contains("360") -> "📶 360p Low"
            raw.contains("Auto", ignoreCase = true) -> "⚡ Auto Quality"
            raw.contains("Direct", ignoreCase = true) || raw.contains("MP4", ignoreCase = true) -> "💾 Direct Stream"
            raw.length > 25 -> raw.lines().drop(1).firstOrNull() ?: "HD"
            else -> raw
        }
    }

    val availableOptions = remember(item.id, streamOptions) {
        if (streamOptions.isNotEmpty()) {
            streamOptions
        } else {
            MissAvRepository.getStreamOptions(item)
        }
    }

    var activeStreamUrl by remember(streamUrl) { mutableStateOf(streamUrl) }
    var activeQualityLabel by remember(qualityTitle) { mutableStateOf(cleanQualityText(qualityTitle)) }
    var failedUrls by remember { mutableStateOf(setOf<String>()) }
    var resumePositionMs by remember { mutableStateOf(0) }

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var showQualitySheet by remember { mutableStateOf(false) }
    var playbackErrorMessage by remember { mutableStateOf<String?>(null) }

    var currentPositionMs by remember { mutableStateOf(0) }
    var totalDurationMs by remember { mutableStateOf(1) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var resizeMode by remember { mutableStateOf(PlayerResizeMode.FIT) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }

    // User scrubbing state for smooth timeline dragging
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableStateOf(0f) }

    // Gesture HUD feedback (Seek, Volume, Brightness, Quality switch)
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
        if (showControls && isPlaying && !isLocked && !showQualitySheet && playbackErrorMessage == null) {
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
                    if (playbackErrorMessage != null) playbackErrorMessage = null
                }
            }
            delay(400)
        }
    }

    fun onUserTouch() {
        lastInteractionTime = System.currentTimeMillis()
    }

    // Switch to a new stream option (retaining current playback position)
    fun switchStream(option: StreamQualityOption) {
        val cur = videoViewRef?.currentPosition ?: currentPositionMs
        if (cur > 0) resumePositionMs = cur
        activeStreamUrl = option.streamUrl
        activeQualityLabel = cleanQualityText(option.label)
        playbackErrorMessage = null
        isBuffering = true
        showQualitySheet = false
        feedbackText = "Switching to $activeQualityLabel..."
        onUserTouch()
    }

    // Automatic error recovery & quality fallback
    fun handlePlaybackError(what: Int, extra: Int) {
        val failed = activeStreamUrl
        failedUrls = failedUrls + failed

        // Find next candidate stream not yet failed
        val nextOption = availableOptions.firstOrNull { it.streamUrl != failed && !failedUrls.contains(it.streamUrl) }
        if (nextOption != null) {
            val nextLabel = cleanQualityText(nextOption.label)
            feedbackText = "$activeQualityLabel unavailable. Switching to $nextLabel..."
            val cur = videoViewRef?.currentPosition ?: currentPositionMs
            if (cur > 0) resumePositionMs = cur
            activeStreamUrl = nextOption.streamUrl
            activeQualityLabel = nextLabel
            isBuffering = true
            playbackErrorMessage = null
        } else {
            isBuffering = false
            playbackErrorMessage = "Stream disconnected. Tap Retry or select another quality below."
            feedbackText = "Stream Reconnecting..."
        }
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
        if (showQualitySheet) {
            showQualitySheet = false
        } else if (isLocked) {
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
                    tag = activeStreamUrl
                    // Direct setVideoURI without custom headers prevents Android MediaPlayer Stagefright header bugs
                    setVideoURI(Uri.parse(activeStreamUrl))
                    setOnPreparedListener { mp ->
                        totalDurationMs = mp.duration.coerceAtLeast(1)
                        mp.isLooping = false
                        if (resumePositionMs > 0) {
                            seekTo(resumePositionMs)
                        }
                        start()
                        isPlaying = true
                        isBuffering = false
                        playbackErrorMessage = null
                    }
                    setOnCompletionListener {
                        isPlaying = false
                        showControls = true
                    }
                    setOnErrorListener { _, what, extra ->
                        handlePlaybackError(what, extra)
                        true
                    }
                    videoViewRef = this
                }
            },
            update = { vv ->
                if (vv.tag != activeStreamUrl) {
                    vv.tag = activeStreamUrl
                    isBuffering = true
                    playbackErrorMessage = null
                    vv.stopPlayback()
                    vv.setVideoURI(Uri.parse(activeStreamUrl))
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
        if (isBuffering && playbackErrorMessage == null) {
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
                    delay(1500)
                    feedbackText = null
                }
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(24.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 22.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = text,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // In-Player Error & Recovery Card (Shown if all automatic fallbacks failed)
        if (playbackErrorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .zIndex(15f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF161822))
                        .border(1.dp, Color(0xFF282C3D), RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = NuvioTokens.Colors.primary,
                        modifier = Modifier.size(44.dp)
                    )

                    Text(
                        text = "Playback Interrupted",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = playbackErrorMessage ?: "Stream is reconnecting or resolution unavailable.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    // Alternate Quality Chips
                    Text(
                        text = "Try switching resolution:",
                        color = Color(0xFF9CA3AF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        availableOptions.take(4).forEach { opt ->
                            val label = cleanQualityText(opt.label)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF232738))
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    .clickable { switchStream(opt) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Retry Current Stream Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(NuvioTokens.Colors.primary)
                                .clickable {
                                    failedUrls = emptySet()
                                    playbackErrorMessage = null
                                    isBuffering = true
                                    videoViewRef?.let { vv ->
                                        vv.stopPlayback()
                                        vv.setVideoURI(Uri.parse(activeStreamUrl))
                                    }
                                }
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "↻ Retry Stream",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Back Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF262626))
                                .clickable { onBackClick() }
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "Go Back",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
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
            visible = showControls && !isLocked && playbackErrorMessage == null,
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
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                            .clickable { onBackClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Centered Title & Clean Metadata
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
                            text = "${item.code.ifBlank { "MISSAV" }} \u2022 $activeQualityLabel",
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

                        // Aspect Ratio Mode Button Pill
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                .clickable {
                                    val nextMode = when (resizeMode) {
                                        PlayerResizeMode.FIT -> PlayerResizeMode.FILL
                                        PlayerResizeMode.FILL -> PlayerResizeMode.ZOOM
                                        PlayerResizeMode.ZOOM -> PlayerResizeMode.FIT
                                    }
                                    resizeMode = nextMode
                                    feedbackText = "Ratio: ${nextMode.label}"
                                    onUserTouch()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_player_aspect_ratio),
                                contentDescription = "Aspect Ratio",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Lock Controls Button
                        Box(
                            modifier = Modifier
                                .size(34.dp)
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

                // 2. Center Action Transport: [ -10s ]  [ Play/Pause ]  [ +10s ]
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(36.dp)
                ) {
                    // Replay -10s
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                            .clickable { seekRelative(-10000) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_replay_10),
                            contentDescription = "-10 Seconds",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Main Center Play / Pause Floating Orb
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable {
                                videoViewRef?.let { vv ->
                                    if (vv.isPlaying) {
                                        vv.pause()
                                        isPlaying = false
                                    } else {
                                        vv.start()
                                        isPlaying = true
                                    }
                                    onUserTouch()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play
                            ),
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    // Forward +10s
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                            .clickable { seekRelative(10000) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_forward_10),
                            contentDescription = "+10 Seconds",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // 3. Bottom HUD Section (Time Labels, Red Scrubber, Quality Pill, Audio Pill, Resize Pill)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    // Time Labels Row: [ 00:00 ] ----------------- [ -00:00 ]
                    val displayPos = if (isScrubbing) scrubPositionMs.roundToInt() else currentPositionMs
                    val remainingMs = (totalDurationMs - displayPos).coerceAtLeast(0)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatPlaybackClock(displayPos),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = "-${formatPlaybackClock(remainingMs)}",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Scrubber Slider
                    Slider(
                        value = (if (isScrubbing) scrubPositionMs else currentPositionMs.toFloat())
                            .coerceIn(0f, totalDurationMs.toFloat().coerceAtLeast(1f)),
                        onValueChange = { newVal ->
                            isScrubbing = true
                            scrubPositionMs = newVal
                            onUserTouch()
                        },
                        onValueChangeFinished = {
                            isScrubbing = false
                            videoViewRef?.seekTo(scrubPositionMs.roundToInt())
                            currentPositionMs = scrubPositionMs.roundToInt()
                            onUserTouch()
                        },
                        valueRange = 0f..totalDurationMs.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFE50914), // Nuvio Red
                            activeTrackColor = Color(0xFFE50914), // Nuvio Red
                            inactiveTrackColor = Color.White.copy(alpha = 0.22f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Bottom Pill Badges Row (Quality Pill [Clickable], Ratio Pill, Audio Pill)
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
                            // Quality Pill Badge (Clickable to switch resolutions!)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFE50914).copy(alpha = 0.25f))
                                    .border(1.dp, Color(0xFFE50914).copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                    .clickable {
                                        showQualitySheet = true
                                        onUserTouch()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = activeQualityLabel,
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

        // 4. Quality Selector Modal / Sheet
        if (showQualitySheet) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .clickable { showQualitySheet = false }
                    .zIndex(20f),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .background(Color(0xFF13151D))
                        .border(1.dp, Color(0xFF1E2230), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .clickable(enabled = false) {}
                        .padding(20.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Select Stream Resolution",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF262626))
                                .clickable { showQualitySheet = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableOptions) { opt ->
                            val label = cleanQualityText(opt.label)
                            val isSelected = opt.streamUrl == activeStreamUrl

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Color(0xFFE50914).copy(alpha = 0.18f) else Color(0xFF1C1F2E))
                                    .border(
                                        1.dp,
                                        if (isSelected) Color(0xFFE50914) else Color(0xFF2B3045),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { switchStream(opt) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color(0xFFE50914),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
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
