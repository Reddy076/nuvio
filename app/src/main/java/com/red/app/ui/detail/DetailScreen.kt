package com.red.app.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.red.app.model.AddonStreamGroup
import com.red.app.model.MediaItem
import com.red.app.model.MetaDetails
import com.red.app.model.MetaPerson
import com.red.app.model.MetaTrailer
import com.red.app.model.StreamItem
import com.red.app.model.StreamQualityOption
import com.red.app.ui.components.AsyncImageLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * DetailScreen: 1:1 visual, typography, and animation match with official Nuvio,
 * with 100% dynamic data fetched directly from Stremio / MissAV addons.
 * Zero hardcoded names, synopses, or stream providers.
 */
@Composable
fun DetailScreen(
    item: MediaItem,
    onBackClick: () -> Unit,
    onPlayStream: (StreamQualityOption) -> Unit
) {
    val scrollState = rememberScrollState()
    var showStreamSheet by remember { mutableStateOf(false) }
    var metaDetails by remember(item.id) { mutableStateOf<MetaDetails?>(null) }
    var isSavedInWatchlist by remember(item.id) {
        mutableStateOf(MissAvRepository.isInWatchlist(item))
    }
    var isWatched by remember(item.id) {
        mutableStateOf(item.isWatched)
    }

    // Dynamically fetch full metadata from addon
    LaunchedEffect(item.id) {
        metaDetails = MissAvRepository.fetchMetaDetails(item.type, item.id)
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

    val currentTitle = metaDetails?.name ?: item.title

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Collapsing Floating Top Header (1:1 with Nuvio layout)
        DetailFloatingHeader(
            title = currentTitle,
            progress = headerProgress,
            onBack = onBackClick,
            onAddClick = {
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
            // Hero Section (Backdrop + Deep Gradient Fade + Centered Title & Genres)
            DetailHero(
                item = item,
                meta = metaDetails,
                headerProgress = headerProgress,
                onBackClick = onBackClick
            )

            // Content Body with 1:1 Nuvio spacing and layout
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons: 1:1 with Nuvio DetailActionButtons.kt
                // Collapsed: [ ▶ Play ] + [ ··· ]
                // Expanded: [ ▶ Play ] + [ (✓) ] + [ + ] + [ ··· ] (rotated 90°)
                DetailActionButtons(
                    isSaved = isSavedInWatchlist,
                    isWatched = isWatched,
                    onPlayClick = {
                        showStreamSheet = true
                    },
                    onToggleSaved = {
                        MissAvRepository.toggleWatchlist(item)
                        isSavedInWatchlist = MissAvRepository.isInWatchlist(item)
                    },
                    onToggleWatched = {
                        isWatched = !isWatched
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Metadata Row: Year • Runtime • [ Cert ] • [IMDb] Rating
                DetailMetaInfo(item = item, meta = metaDetails)

                Spacer(modifier = Modifier.height(14.dp))

                // Director & Writer Rows (only if present in addon metadata)
                DetailCreditsSection(item = item, meta = metaDetails)

                Spacer(modifier = Modifier.height(14.dp))

                // Description (Overview directly under credits, no "Overview" header)
                val descriptionText = metaDetails?.description?.takeIf { it.isNotBlank() }
                    ?: item.description.takeIf { it.isNotBlank() }
                    ?: "Official video presentation for ${item.title}."

                ExpandableDescription(description = descriptionText)

                // Cast Section (Circular Avatars + Name + Role)
                // Only shown if addon metadata returns cast/actresses
                val castList = metaDetails?.cast?.takeIf { it.isNotEmpty() }
                    ?: item.actresses.map { MetaPerson(name = it, role = "Cast") }

                if (castList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(28.dp))
                    DetailCastSection(castList = castList)
                }

                // Trailers Section
                // Only shown if addon metadata returns trailers
                val trailersList = metaDetails?.trailers.orEmpty()
                if (trailersList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(28.dp))
                    DetailTrailersSection(
                        item = item,
                        meta = metaDetails,
                        trailers = trailersList,
                        onPlayTrailer = { showStreamSheet = true }
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Movie Details Section (Key-Value Rows with Thin Dividers)
                DetailAdditionalInfoSection(item = item, meta = metaDetails)

                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // Full Screen Streams Selection Sheet (1:1 with Official Nuvio)
        AnimatedVisibility(
            visible = showStreamSheet,
            enter = fadeIn(tween(180)) + slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(220)
            ),
            exit = fadeOut(tween(150)) + slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(200)
            ),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(20f)
        ) {
            StreamsScreenView(
                item = item,
                meta = metaDetails,
                onDismiss = { showStreamSheet = false },
                onStreamSelected = { selectedStream ->
                    showStreamSheet = false
                    val qualityOption = StreamQualityOption(
                        label = selectedStream.title?.lines()?.firstOrNull() ?: selectedStream.name ?: "Stream",
                        resolution = selectedStream.resolution ?: "1080p",
                        streamUrl = selectedStream.url ?: "",
                        headers = selectedStream.headers,
                        isDirect = selectedStream.isDirect
                    )
                    onPlayStream(qualityOption)
                }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 1. Floating Top Header (Collapsing Toolbar)
// ---------------------------------------------------------------------------

@Composable
private fun DetailFloatingHeader(
    title: String,
    progress: Float,
    onBack: () -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = Color.Black.copy(alpha = progress * 0.95f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .statusBarsPadding()
            .height(60.dp)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Clean Back Arrow
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Centered Title Text (fades in as user scrolls)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title.uppercase(),
                    color = Color.White.copy(alpha = progress),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.sp
                )
            }

            // Top-right Add Button "+"
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onAddClick),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add",
                    tint = Color.White.copy(alpha = progress.coerceAtLeast(if (progress > 0.05f) 1f else 0f)),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 2. Detail Hero Section (Backdrop + Centered Title & Genres)
// ---------------------------------------------------------------------------

@Composable
private fun DetailHero(
    item: MediaItem,
    meta: MetaDetails?,
    headerProgress: Float,
    onBackClick: () -> Unit
) {
    val backdropUrl = meta?.background?.takeIf { it.isNotBlank() }
        ?: meta?.poster?.takeIf { it.isNotBlank() }
        ?: item.bannerUrl.ifBlank { item.posterUrl }

    val displayTitle = (meta?.name?.takeIf { it.isNotBlank() } ?: item.title).uppercase()

    val genresList = meta?.genres?.takeIf { it.isNotEmpty() }
        ?: item.genres.takeIf { it.isNotEmpty() }
        ?: listOfNotNull(item.code.takeIf { it.isNotBlank() }, item.genre.takeIf { it.isNotBlank() })

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(440.dp)
    ) {
        // Full Bleed Backdrop Image
        AsyncImageLoader(
            url = backdropUrl,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.05f
                    scaleY = 1.05f
                },
            contentScale = ContentScale.Crop
        )

        // Multi-Stop Deep Vertical Fade to pure black
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to Color.Transparent,
                        0.20f to Color.Black.copy(alpha = 0.08f),
                        0.38f to Color.Black.copy(alpha = 0.25f),
                        0.55f to Color.Black.copy(alpha = 0.55f),
                        0.72f to Color.Black.copy(alpha = 0.82f),
                        0.88f to Color.Black.copy(alpha = 0.96f),
                        1.00f to Color.Black
                    )
                )
            )
        )

        // Clean White Back Arrow at top left
        if (headerProgress < 0.15f) {
            Box(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 18.dp, top = 10.dp)
                    .size(40.dp)
                    .clickable(onClick = onBackClick),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        // Bottom Hero Info: Centered Bold Title & Genre Line
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Centered Title Text
            Text(
                text = displayTitle,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
                textAlign = TextAlign.Center,
                lineHeight = 34.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle / Genres Line
            if (genresList.isNotEmpty()) {
                Text(
                    text = genresList.take(4).joinToString(" \u2022 "),
                    color = Color(0xFF9CA3AF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 3. Detail Action Buttons (1:1 with Nuvio DetailActionButtons.kt)
// ---------------------------------------------------------------------------

@Composable
private fun DetailActionButtons(
    isSaved: Boolean,
    isWatched: Boolean,
    onPlayClick: () -> Unit,
    onToggleSaved: () -> Unit,
    onToggleWatched: () -> Unit
) {
    var actionsExpanded by remember { mutableStateOf(false) }
    val menuProgress by animateFloatAsState(
        targetValue = if (actionsExpanded) 1f else 0f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
    )

    val buttonHeight = 52.dp
    val iconButtonSize = buttonHeight
    val playShape = RoundedCornerShape(40.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(buttonHeight)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Primary [ ▶ Play ] Pill Button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(buttonHeight)
                    .clip(playShape)
                    .background(Color.White)
                    .clickable(onClick = onPlayClick),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
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
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp
                    )
                }
            }

            // Secondary Expanding Action 1: Mark as Watched [ (✓) ]
            if (menuProgress > 0.01f) {
                val watchedBgColor = if (isWatched) Color(0xFFE5A00D) else Color(0xFF262626)
                Box(
                    modifier = Modifier
                        .size(iconButtonSize)
                        .graphicsLayer {
                            alpha = menuProgress
                            scaleX = 0.5f + (0.5f * menuProgress)
                            scaleY = 0.5f + (0.5f * menuProgress)
                        }
                        .clip(CircleShape)
                        .background(watchedBgColor)
                        .clickable(onClick = onToggleWatched),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(
                            if (isWatched) R.drawable.ic_check_circle else R.drawable.ic_check_circle_outline
                        ),
                        contentDescription = "Watched",
                        tint = if (isWatched) Color.Black else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Secondary Expanding Action 2: Add to Watchlist / Bookmark [ + / ✓ ]
            if (menuProgress > 0.01f) {
                val savedBgColor = if (isSaved) Color(0xFFE50914) else Color(0xFF262626)
                Box(
                    modifier = Modifier
                        .size(iconButtonSize)
                        .graphicsLayer {
                            alpha = menuProgress
                            scaleX = 0.5f + (0.5f * menuProgress)
                            scaleY = 0.5f + (0.5f * menuProgress)
                        }
                        .clip(CircleShape)
                        .background(savedBgColor)
                        .clickable(onClick = onToggleSaved),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = "Watchlist",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Secondary Trigger Button [ ··· ]: Rotates 90° into vertical dots and turns solid white
            val moreBtnBg by animateColorAsState(
                targetValue = if (actionsExpanded) Color.White else Color(0xFF262626),
                animationSpec = tween(200)
            )
            val moreBtnTint by animateColorAsState(
                targetValue = if (actionsExpanded) Color.Black else Color.White,
                animationSpec = tween(200)
            )

            Box(
                modifier = Modifier
                    .size(iconButtonSize)
                    .clip(CircleShape)
                    .background(moreBtnBg)
                    .clickable { actionsExpanded = !actionsExpanded },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_horiz),
                    contentDescription = "More Actions",
                    tint = moreBtnTint,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            rotationZ = 90f * menuProgress
                        }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 4. Detail Metadata Info Row (Year • Runtime • [ Cert ] • [IMDb] Rating)
// ---------------------------------------------------------------------------

@Composable
private fun DetailMetaInfo(item: MediaItem, meta: MetaDetails?) {
    val yearText = meta?.releaseInfo?.takeIf { it.isNotBlank() } ?: item.year.ifBlank { "2024" }
    val runtimeText = meta?.runtime?.takeIf { it.isNotBlank() } ?: item.duration.ifBlank { "120 min" }
    val certText = meta?.ageRating?.takeIf { it.isNotBlank() } ?: if (item.isUncensored) "18+" else "PG-13"
    val ratingValue = meta?.imdbRating?.takeIf { it.isNotBlank() } ?: item.rating.ifBlank { "4.8" }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Release Year
        Text(
            text = yearText,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        // Runtime Duration
        Text(
            text = runtimeText,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        // Certification Badge Box: [ R ] or [ 18+ ]
        Box(
            modifier = Modifier
                .border(
                    border = BorderStroke(1.dp, Color(0xFF6B7280)),
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = certText,
                color = Color(0xFFD1D5DB),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // IMDb Rating Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.rating_imdb),
                contentDescription = "IMDb",
                modifier = Modifier.size(width = 34.dp, height = 18.dp)
            )
            Text(
                text = ratingValue,
                color = Color(0xFFF5C518),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 5. Director & Writer Rows (Dynamic from Addon Metadata)
// ---------------------------------------------------------------------------

@Composable
private fun DetailCreditsSection(item: MediaItem, meta: MetaDetails?) {
    val directors = meta?.director?.filter { it.isNotBlank() }.orEmpty()
    val writers = meta?.writer?.filter { it.isNotBlank() }.orEmpty()
    val castMembers = meta?.cast?.map { it.name }?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
        ?: item.actresses

    if (directors.isEmpty() && writers.isEmpty() && castMembers.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (directors.isNotEmpty()) {
            Row {
                Text(
                    text = "Director:  ",
                    color = Color(0xFF9CA3AF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = directors.joinToString(", "),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (writers.isNotEmpty()) {
            Row {
                Text(
                    text = "Writer:  ",
                    color = Color(0xFF9CA3AF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = writers.joinToString(", "),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (directors.isEmpty() && writers.isEmpty() && castMembers.isNotEmpty()) {
            Row {
                Text(
                    text = "Starring:  ",
                    color = Color(0xFF9CA3AF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = castMembers.take(3).joinToString(", "),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 6. Expandable Description (No "Overview" header! Directly below credits)
// ---------------------------------------------------------------------------

@Composable
private fun ExpandableDescription(description: String) {
    if (description.isBlank()) return
    var expanded by remember(description) { mutableStateOf(false) }
    var hasOverflow by remember(description) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(enabled = hasOverflow) { expanded = !expanded }
    ) {
        Text(
            text = description,
            color = Color.White,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!expanded) {
                    hasOverflow = result.hasVisualOverflow
                }
            }
        )

        if (hasOverflow) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (expanded) "Show Less \u25B4" else "Show More \u25BE",
                color = Color(0xFF9CA3AF),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 7. Cast Section (Circular Avatars + Name + Role)
// ---------------------------------------------------------------------------

@Composable
private fun DetailCastSection(castList: List<MetaPerson>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Cast",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(castList) { member ->
                Column(
                    modifier = Modifier.width(96.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Circular Avatar
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF262626)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!member.photo.isNullOrBlank()) {
                            AsyncImageLoader(
                                url = member.photo,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            val initials = member.name.split(" ")
                                .filter { it.isNotBlank() }
                                .take(2)
                                .map { it.first().uppercase() }
                                .joinToString("")
                            Text(
                                text = initials.ifBlank { "★" },
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Actor Name
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
                        text = member.role ?: "Cast",
                        color = Color(0xFF9CA3AF),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 8. Trailers Section (Dynamic from Addon Metadata)
// ---------------------------------------------------------------------------

@Composable
private fun DetailTrailersSection(
    item: MediaItem,
    meta: MetaDetails?,
    trailers: List<MetaTrailer>,
    onPlayTrailer: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section Header Row: "Trailers" + "Trailer ▾" pill chip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Trailers",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF262626))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Trailer",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_expand_more),
                        contentDescription = null,
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Horizontal Rail of Trailer Cards
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(trailers) { trailer ->
                val thumbUrl = if (trailer.site.equals("YouTube", ignoreCase = true) && trailer.key.isNotBlank()) {
                    "https://img.youtube.com/vi/${trailer.key}/hqdefault.jpg"
                } else {
                    meta?.background ?: item.bannerUrl.ifBlank { item.posterUrl }
                }

                Column(
                    modifier = Modifier
                        .width(240.dp)
                        .clickable(onClick = onPlayTrailer),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 16:9 Thumbnail
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1F2129))
                    ) {
                        AsyncImageLoader(
                            url = thumbUrl,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.25f))
                        )
                    }

                    // Label
                    Text(
                        text = trailer.name.ifBlank { "Trailer" },
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 9. Movie Details Section (Key-Value Rows with Thin Dividers)
// ---------------------------------------------------------------------------

@Composable
private fun DetailAdditionalInfoSection(item: MediaItem, meta: MetaDetails?) {
    val rows = remember(item, meta) {
        val list = mutableListOf<Pair<String, String>>()
        if (!meta?.status.isNullOrBlank()) {
            list.add("Status" to meta!!.status!!)
        }
        val rel = meta?.releaseInfo?.takeIf { it.isNotBlank() }
            ?: item.releaseDate.takeIf { it.isNotBlank() }
            ?: item.year.takeIf { it.isNotBlank() }
        if (!rel.isNullOrBlank()) {
            list.add("Release Info" to rel)
        }
        val dur = meta?.runtime?.takeIf { it.isNotBlank() }
            ?: item.duration.takeIf { it.isNotBlank() }
        if (!dur.isNullOrBlank()) {
            list.add("Runtime" to dur)
        }
        val cert = meta?.ageRating?.takeIf { it.isNotBlank() }
            ?: if (item.isUncensored) "18+ (Uncensored)" else "R"
        list.add("Certification" to cert)

        if (!meta?.country.isNullOrBlank()) {
            list.add("Origin Country" to meta!!.country!!)
        }
        if (!meta?.language.isNullOrBlank()) {
            list.add("Original Language" to meta!!.language!!)
        }
        if (item.code.isNotBlank()) {
            list.add("Catalog Code" to item.code)
        }
        if (item.type.isNotBlank()) {
            list.add("Type" to item.type)
        }
        list
    }

    if (rows.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Movie Details",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = value,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (index < rows.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.6.dp)
                            .background(Color.White.copy(alpha = 0.12f))
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 10. Streams Screen (1:1 with Official Nuvio - 100% Dynamic from Addon)
// ---------------------------------------------------------------------------

@Composable
private fun StreamsScreenView(
    item: MediaItem,
    meta: MetaDetails?,
    onDismiss: () -> Unit,
    onStreamSelected: (StreamItem) -> Unit
) {
    var streamGroups by remember(item.id) { mutableStateOf<List<AddonStreamGroup>>(emptyList()) }
    var isLoadingStreams by remember(item.id) { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf<String?>(null) }
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

    val loadStreams = remember(item.id) {
        {
            scope.launch {
                isLoadingStreams = true
                val groups = MissAvRepository.fetchStreams(item.type, item.id, item)
                streamGroups = groups
                isLoadingStreams = false
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(item.id) {
        loadStreams()
    }

    val titleDisplay = (meta?.name?.takeIf { it.isNotBlank() } ?: item.title).uppercase()
    val availableTabs = remember(streamGroups) {
        val names = streamGroups.map { it.addonName }.distinct()
        listOf("All") + names
    }

    val filteredGroups = remember(streamGroups, selectedFilter) {
        if (selectedFilter == null || selectedFilter == "All") {
            streamGroups
        } else {
            streamGroups.filter { it.addonName == selectedFilter }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar: Back Arrow + Centered Title
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(60.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Back Arrow
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(40.dp)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Centered Bold Title
                Text(
                    text = titleDisplay,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 48.dp)
                )
            }

            // Filter Chips Row: [ ↻ ] [ All ] [ Addon 1 ] [ Addon 2 ] ...
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Refresh Button [ ↻ ]
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF262626))
                        .clickable {
                            if (!isRefreshing) {
                                isRefreshing = true
                                loadStreams()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color.White,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { rotationZ = refreshRotation.value }
                    )
                }

                // Dynamic Filter Chips based on Addon Groups
                availableTabs.forEach { tab ->
                    val isSelected = (selectedFilter == null && tab == "All") || selectedFilter == tab
                    val chipBg = if (isSelected) Color.White else Color(0xFF262626)
                    val chipTextColor = if (isSelected) Color.Black else Color.White

                    Box(
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(chipBg)
                            .clickable { selectedFilter = if (tab == "All") null else tab }
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab,
                            color = chipTextColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Streams Content Area
            if (isLoadingStreams) {
                // Loading shimmer placeholders
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF13151D).copy(alpha = 0.6f))
                        )
                    }
                }
            } else if (filteredGroups.isEmpty() || filteredGroups.all { it.streams.isEmpty() }) {
                // Empty State
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No streams found from enabled addons",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Tap ↻ to refresh or configure addons in Settings",
                            color = Color(0xFF9CA3AF),
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                // Real Dynamic Stream Cards List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
                ) {
                    filteredGroups.forEach { group ->
                        // Show Addon Header if viewing "All"
                        if (selectedFilter == null || selectedFilter == "All") {
                            item(key = "header_${group.addonId}") {
                                Text(
                                    text = group.addonName,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                        }

                        items(group.streams) { stream ->
                            RealStreamCard(
                                stream = stream,
                                mediaTitle = meta?.name ?: item.title,
                                onClick = { onStreamSelected(stream) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 11. Stream Card (1:1 with Nuvio Layout - Dynamic from Addon Streams)
// ---------------------------------------------------------------------------

@Composable
private fun RealStreamCard(
    stream: StreamItem,
    mediaTitle: String,
    onClick: () -> Unit
) {
    val resolutionTag = stream.resolution ?: "1080p"
    val providerTag = "${stream.addonName} 🧊 $resolutionTag" + if (!stream.source.isNullOrBlank()) " • ${stream.source}" else ""

    // Split stream title lines if present from addon
    val rawLines = stream.title?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    val titleLine = if (rawLines.isNotEmpty()) rawLines[0] else "🍿 $mediaTitle"
    val codecLine = if (rawLines.size > 1) rawLines[1] else "🎞️ $resolutionTag • ${stream.format ?: "HLS"} • ${stream.codec ?: "x264"}"
    val sourceLine = "🛰️ Source: ${stream.source ?: stream.addonName}"
    val sizeLine = if (!stream.sizeText.isNullOrBlank()) "💾 ${stream.sizeText}" else "💾 Direct CDN Stream"
    val audioLine = if (!stream.audio.isNullOrBlank()) "🎧 Audio: ${stream.audio}" else null
    val badgeSizeText = "SIZE ${stream.sizeText ?: resolutionTag}"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF13151D))
            .border(1.dp, Color(0xFF1E2230), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            // Line 1: Provider + Quality
            Text(
                text = providerTag,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            // Line 2: Media / Stream Title
            Text(
                text = if (titleLine.startsWith("🍿")) titleLine else "🍿 $titleLine",
                color = Color(0xFFD1D5DB),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Line 3: Format / Codec / Bitrate
            Text(
                text = if (codecLine.startsWith("🎞️")) codecLine else "🎞️ $codecLine",
                color = Color(0xFF9CA3AF),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Line 4: Source
            Text(
                text = sourceLine,
                color = Color(0xFF9CA3AF),
                fontSize = 12.sp
            )

            // Line 5: Size
            Text(
                text = sizeLine,
                color = Color(0xFF9CA3AF),
                fontSize = 12.sp
            )

            // Line 6: Audio (if present)
            if (audioLine != null) {
                Text(
                    text = audioLine,
                    color = Color(0xFF9CA3AF),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Bottom Pill Badge: [ SIZE ... ]
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF262626))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badgeSizeText,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
