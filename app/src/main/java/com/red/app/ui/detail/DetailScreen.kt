package com.red.app.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.red.app.R
import com.red.app.data.MissAvRepository
import com.red.app.model.MediaItem
import com.red.app.model.StreamQualityOption
import com.red.app.theme.NuvioTokens
import com.red.app.ui.components.AsyncImageLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    item: MediaItem,
    onBackClick: () -> Unit,
    onPlayStream: (StreamQualityOption) -> Unit
) {
    val scrollState = rememberScrollState()
    val streamOptions = remember(item) { MissAvRepository.getStreamOptions(item) }
    var showStreamSheet by remember { mutableStateOf(false) }
    var isSavedInWatchlist by remember(item.id) {
        mutableStateOf(MissAvRepository.isInWatchlist(item))
    }

    // Header progress: 0f at top, 1f when scrolled down past hero
    val headerProgress by remember {
        derivedStateOf {
            (scrollState.value.toFloat() / 320f).coerceIn(0f, 1f)
        }
    }

    BackHandler(enabled = showStreamSheet) {
        showStreamSheet = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTokens.Colors.background)
    ) {
        // Collapsing Floating Top Header (1:1 with Nuvio DetailFloatingHeader)
        DetailFloatingHeader(
            title = item.title,
            isSaved = isSavedInWatchlist,
            progress = headerProgress,
            onBack = onBackClick,
            onToggleSaved = {
                MissAvRepository.toggleWatchlist(item)
                isSavedInWatchlist = MissAvRepository.isInWatchlist(item)
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(10f)
        )

        // Main Scrollable Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Hero Section (Backdrop + Gradient Fade + Centered Title & Genres)
            DetailHero(
                item = item,
                headerProgress = headerProgress,
                onBackClick = onBackClick
            )

            // Content Body with 1:1 Nuvio spacing and layout
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons: Large White Pill [ ▶ Play ] + Glass [ + / ✓ ] Bookmark Button
                DetailActionButtons(
                    isSaved = isSavedInWatchlist,
                    onPlayClick = {
                        showStreamSheet = true
                    },
                    onToggleSaved = {
                        MissAvRepository.toggleWatchlist(item)
                        isSavedInWatchlist = MissAvRepository.isInWatchlist(item)
                    }
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Metadata Row: Year • Runtime • Age Badge • IMDb Rating
                DetailMetaInfo(item = item)

                Spacer(modifier = Modifier.height(18.dp))

                // Expandable Description / Overview
                ExpandableDescription(
                    description = item.description.ifBlank {
                        "Experience high-definition Japanese video release ${item.code} starring ${item.actresses.joinToString(", ").ifBlank { "Top Japanese Actresses" }}. Features pristine 1080p full high definition quality with original audio."
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Cast Section (Circular Avatars + Names + Roles)
                DetailCastSection(item = item)

                Spacer(modifier = Modifier.height(24.dp))

                // Movie Details Section (Status, Release Date, Runtime, Certification, Country)
                DetailAdditionalInfoSection(item = item)

                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // Stream Selection Modal Sheet (1:1 with Nuvio Screenshot 3)
        AnimatedVisibility(
            visible = showStreamSheet,
            enter = fadeIn(tween(200)) + slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = tween(250)
            ),
            exit = fadeOut(tween(180)) + slideOutVertically(
                targetOffsetY = { it / 2 },
                animationSpec = tween(220)
            ),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(20f)
        ) {
            StreamsModalSheet(
                item = item,
                streamOptions = streamOptions,
                onDismiss = { showStreamSheet = false },
                onStreamSelected = { selected ->
                    showStreamSheet = false
                    onPlayStream(selected)
                }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 1. Floating Top Header
// ---------------------------------------------------------------------------

@Composable
private fun DetailFloatingHeader(
    title: String,
    isSaved: Boolean,
    progress: Float,
    onBack: () -> Unit,
    onToggleSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = NuvioTokens.Colors.background.copy(alpha = progress)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back Arrow (Standard Clean White Arrow)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Centered Title on scroll fade
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    color = Color.White.copy(alpha = progress),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            // Add to Watchlist / Bookmark Action
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f * progress))
                    .clickable(onClick = onToggleSaved),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSaved) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = "Watchlist",
                    tint = if (isSaved) NuvioTokens.Colors.primaryVariant else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 2. Detail Hero Section
// ---------------------------------------------------------------------------

@Composable
private fun DetailHero(
    item: MediaItem,
    headerProgress: Float,
    onBackClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(410.dp)
    ) {
        // Full Bleed Backdrop Image
        AsyncImageLoader(
            url = item.bannerUrl.ifBlank { item.posterUrl },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Subtle parallax zoom
                    scaleX = 1.05f
                    scaleY = 1.05f
                },
            contentScale = ContentScale.Crop
        )

        // 1:1 Nuvio Multi-Stop Deep Vertical Fade
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.20f to NuvioTokens.Colors.background.copy(alpha = 0.05f),
                            0.38f to NuvioTokens.Colors.background.copy(alpha = 0.20f),
                            0.55f to NuvioTokens.Colors.background.copy(alpha = 0.48f),
                            0.72f to NuvioTokens.Colors.background.copy(alpha = 0.78f),
                            0.88f to NuvioTokens.Colors.background.copy(alpha = 0.94f),
                            1.00f to NuvioTokens.Colors.background
                        )
                    )
                )
        )

        // Clean White Back Arrow (Visible at top when headerProgress is 0)
        if (headerProgress < 0.1f) {
            Box(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 14.dp, top = 8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBackClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Hero Centered Title and Genres Line
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Bold Centered Title
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                lineHeight = 30.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle / Genre Line
            val genreLine = buildList {
                if (item.code.isNotBlank()) add(item.code)
                if (item.isUncensored) add("Uncensored")
                if (item.genres.isNotEmpty()) {
                    addAll(item.genres.take(2))
                } else {
                    add("Japanese AV")
                }
            }.joinToString(" \u2022 ")

            Text(
                text = genreLine,
                color = NuvioTokens.Colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 3. Detail Action Buttons
// ---------------------------------------------------------------------------

@Composable
private fun DetailActionButtons(
    isSaved: Boolean,
    onPlayClick: () -> Unit,
    onToggleSaved: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Large White Rounded Pill [ ▶ Play ] Button (Matching Nuvio 1:1)
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            shape = RoundedCornerShape(40.dp),
            color = Color.White,
            contentColor = Color.Black
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onPlayClick),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Play",
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Circular Secondary Glass Button [ + / ✓ ] for Watchlist
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                .clickable(onClick = onToggleSaved),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSaved) Icons.Default.Check else Icons.Default.Add,
                contentDescription = "Watchlist",
                tint = if (isSaved) NuvioTokens.Colors.primaryVariant else Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 4. Detail Metadata Info Row
// ---------------------------------------------------------------------------

@Composable
private fun DetailMetaInfo(item: MediaItem) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Year
            Text(
                text = item.year.ifBlank { "2024" },
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            // Runtime Duration
            Text(
                text = item.duration.ifBlank { "120 min" },
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            // Age Rating Badge: [UNCENSORED] or [18+]
            Box(
                modifier = Modifier
                    .border(
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (item.isUncensored) "UNCENSORED" else "18+",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Official IMDb Rating Badge Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.rating_imdb),
                    contentDescription = "IMDb",
                    modifier = Modifier.size(width = 30.dp, height = 15.dp)
                )
                Text(
                    text = item.rating.ifBlank { "4.8" },
                    color = Color(0xFFF5C518),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Actress / Cast Quick Line
        if (item.actresses.isNotEmpty()) {
            Row {
                Text(
                    text = "Cast:  ",
                    color = NuvioTokens.Colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = item.actresses.joinToString(", "),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 5. Expandable Description (Overview with Show More / Show Less)
// ---------------------------------------------------------------------------

@Composable
private fun ExpandableDescription(description: String) {
    var expanded by remember(description) { mutableStateOf(false) }
    var hasOverflow by remember(description) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(enabled = hasOverflow) { expanded = !expanded }
    ) {
        Text(
            text = "Overview",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = description,
            color = NuvioTokens.Colors.textSecondary,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { textLayoutResult ->
                if (!expanded) {
                    hasOverflow = textLayoutResult.hasVisualOverflow
                }
            }
        )

        if (hasOverflow) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (expanded) "Show Less \u25B4" else "Show More \u25BE",
                color = NuvioTokens.Colors.primaryVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 6. Cast Section (1:1 with Nuvio Circular Avatars Rail)
// ---------------------------------------------------------------------------

@Composable
private fun DetailCastSection(item: MediaItem) {
    val castList = remember(item) {
        if (item.actresses.isNotEmpty()) {
            item.actresses.map { actress ->
                CastMember(
                    name = actress,
                    role = "Actress",
                    photoUrl = ""
                )
            }
        } else {
            listOf(
                CastMember(name = "Japanese Star", role = "Main Cast", photoUrl = ""),
                CastMember(name = "SOD Star", role = "Lead Actress", photoUrl = "")
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Cast",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(castList) { member ->
                CastAvatarItem(member = member)
            }
        }
    }
}

private data class CastMember(
    val name: String,
    val role: String,
    val photoUrl: String
)

@Composable
private fun CastAvatarItem(member: CastMember) {
    Column(
        modifier = Modifier.width(88.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Circular Avatar Surface
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF2A2D3D),
                            Color(0xFF161722)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (member.photoUrl.isNotBlank()) {
                AsyncImageLoader(
                    url = member.photoUrl,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Initials placeholder
                val initials = member.name.split(" ")
                    .filter { it.isNotBlank() }
                    .take(2)
                    .map { it.first().uppercase() }
                    .joinToString("")
                    .ifBlank { member.name.take(2).uppercase() }

                Text(
                    text = initials,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Name
        Text(
            text = member.name,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        // Role
        Text(
            text = member.role,
            color = NuvioTokens.Colors.textMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

// ---------------------------------------------------------------------------
// 7. Movie Details Section (Key-Value Rows with Hairline Dividers)
// ---------------------------------------------------------------------------

@Composable
private fun DetailAdditionalInfoSection(item: MediaItem) {
    val rows = remember(item) {
        listOf(
            "Status" to "Released",
            "Release Info" to item.releaseDate.ifBlank { item.year.ifBlank { "2024" } },
            "Runtime" to item.duration.ifBlank { "120 min" },
            "Certification" to (if (item.isUncensored) "Uncensored" else "18+"),
            "Origin Country" to "Japan",
            "Original Language" to "JA"
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Movie Details",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            rows.forEachIndexed { index, (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = value,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (index < rows.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.08f))
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 8. Stream Selection Modal Sheet (1:1 with Nuvio Screenshot 3)
// ---------------------------------------------------------------------------

@Composable
private fun StreamsModalSheet(
    item: MediaItem,
    streamOptions: List<StreamQualityOption>,
    onDismiss: () -> Unit,
    onStreamSelected: (StreamQualityOption) -> Unit
) {
    var selectedFilter by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val refreshRotation = remember { Animatable(0f) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            refreshRotation.animateTo(
                targetValue = refreshRotation.value + 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        } else {
            refreshRotation.snapTo(0f)
        }
    }

    val filteredStreams = remember(selectedFilter, streamOptions) {
        if (selectedFilter == null || selectedFilter == "All") {
            streamOptions
        } else if (selectedFilter == "1080p") {
            streamOptions.filter { it.resolution.contains("1080") }
        } else if (selectedFilter == "HLS") {
            streamOptions.filter { !it.isDirect || it.streamUrl.endsWith(".m3u8") }
        } else if (selectedFilter == "Direct") {
            streamOptions.filter { it.isDirect }
        } else {
            streamOptions
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onDismiss() }
    ) {
        // Sheet Content Container
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* Consume taps */ }
        ) {
            // Top Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Filter Chips Row (Screenshot 3: [ ↻ ] [ All ] [ 1080p ] [ HLS ] [ Direct ])
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Refresh Chip
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable {
                            if (!isRefreshing) {
                                isRefreshing = true
                                scope.launch {
                                    delay(1000)
                                    isRefreshing = false
                                }
                            }
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color.White,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer { rotationZ = refreshRotation.value }
                    )
                }

                // Filter Chips
                val filterTags = listOf("All", "1080p", "HLS", "Direct")
                filterTags.forEach { tag ->
                    val isSelected = (selectedFilter == null && tag == "All") || selectedFilter == tag
                    val chipBg by animateColorAsState(
                        if (isSelected) Color.White else Color.White.copy(alpha = 0.08f)
                    )
                    val chipText = if (isSelected) Color.Black else Color.White

                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(chipBg)
                            .clickable {
                                selectedFilter = if (tag == "All") null else tag
                            }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tag,
                            color = chipText,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // Stream Cards List (Matching Nuvio StreamCard.kt 1:1)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(filteredStreams) { opt ->
                    NuvioStreamCard(
                        option = opt,
                        onClick = { onStreamSelected(opt) }
                    )
                }
            }

            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

// ---------------------------------------------------------------------------
// 9. Nuvio Stream Card (1:1 with Official Nuvio StreamCard)
// ---------------------------------------------------------------------------

@Composable
private fun NuvioStreamCard(
    option: StreamQualityOption,
    onClick: () -> Unit
) {
    val estimatedSize = when (option.resolution) {
        "1080p", "1080p MP4" -> "4.4 GB"
        "720p" -> "2.1 GB"
        "480p" -> "950 MB"
        else -> "Adaptive"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Top Badges Row (Provider + Quality + Size Pill)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Quality / Provider Chip
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (option.isDirect) NuvioTokens.Colors.primary.copy(alpha = 0.85f) else Color(0xFF2C3247),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (option.resolution.contains("1080")) "🧊 1080p" else "⚡ ${option.resolution}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Format Badge
                    Text(
                        text = if (option.streamUrl.endsWith(".mp4")) "MP4 Direct" else "MissAV HLS",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // File Size Pill [ SIZE 4.4 GB ]
                Box(
                    modifier = Modifier
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "SIZE $estimatedSize",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Stream Name / Title
            Text(
                text = option.label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Subtitle Details Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Proxied via OnRender CDN \u2022 Low Latency \u2022 Fast Buffer",
                    color = NuvioTokens.Colors.textSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}
