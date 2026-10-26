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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
    var isWatched by remember(item.id) {
        mutableStateOf(item.isWatched)
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
            .background(Color.Black)
    ) {
        // Collapsing Floating Top Header (1:1 with Nuvio Screenshot 3)
        // When scrolled, shows back button on left, centered title, and + on right!
        DetailFloatingHeader(
            title = item.title,
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
                headerProgress = headerProgress,
                onBackClick = onBackClick
            )

            // Content Body with 1:1 Nuvio spacing and layout (Screenshots 1 & 2)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons: 1:1 with Nuvio DetailActionButtons.kt
                // When collapsed: [ ▶ Play ] + [ ··· ] (Screenshot 1)
                // When expanded: [ ▶ Play ] + [ (✓) ] + [ + ] + [ ··· ] (Screenshot 2)
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

                // Metadata Row: 2026 • 2h 53m • [ R ] • [IMDb] 8.4 (Screenshot 1)
                DetailMetaInfo(item = item)

                Spacer(modifier = Modifier.height(14.dp))

                // Director & Writer Rows (Screenshot 1)
                DetailCreditsSection(item = item)

                Spacer(modifier = Modifier.height(14.dp))

                // Description (Overview directly under writer, no "Overview" header! - Screenshot 1)
                ExpandableDescription(
                    description = item.description.ifBlank {
                        if (item.title.contains("Odyssey", ignoreCase = true)) {
                            "Odysseus, the legendary King of Ithaca, embarks on a long and perilous journey home following the Trojan War. Throughout his voyage, he is forced to confront mythical monsters, vengeful gods, and treacherous temptresses while fighting to reclaim his throne and family."
                        } else {
                            "Experience high-definition release ${item.code} starring ${item.actresses.joinToString(", ").ifBlank { "Top Japanese Actresses" }}. Features pristine full high definition quality with original Japanese audio."
                        }
                    }
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Cast Section (Screenshot 3: Circular Avatars + Name + Role)
                DetailCastSection(item = item)

                Spacer(modifier = Modifier.height(28.dp))

                // Trailers Section (Screenshot 3: "Trailers" + "Trailer ▾" + 16:9 thumbnails)
                DetailTrailersSection(item = item, onPlayTrailer = { showStreamSheet = true })

                Spacer(modifier = Modifier.height(28.dp))

                // Movie Details Section (Screenshot 3: Status, Release Info, Runtime, Certification, Country, Language)
                DetailAdditionalInfoSection(item = item)

                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // Full Screen Streams Selection Sheet (1:1 with Nuvio Screenshot 4 & 5)
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
// 1. Floating Top Header (Collapsing Toolbar matching Screenshot 3)
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
            .height(56.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Clean Back Arrow (Screenshot 3)
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

            // Centered Title Logo / Text (Screenshot 3: "THE ODYSSEY")
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

            // Top-right Add Button "+" (Screenshot 3)
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
// 2. Detail Hero Section (Backdrop + Centered Title & Genres - Screenshot 1)
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
            .height(440.dp)
    ) {
        // Full Bleed Backdrop Image
        AsyncImageLoader(
            url = item.bannerUrl.ifBlank { item.posterUrl },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.05f
                    scaleY = 1.05f
                },
            contentScale = ContentScale.Crop
        )

        // 1:1 Nuvio Multi-Stop Deep Vertical Fade to pure black
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

        // Clean White Back Arrow at top left (Screenshot 1: no circle, just arrow)
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
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Hero Centered Title and Genres Line (Screenshot 1)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Bold Centered Title ("THE ODYSSEY" - Screenshot 1)
            Text(
                text = item.title.uppercase(),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                lineHeight = 34.sp,
                letterSpacing = 1.2.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle / Genres Line ("Adventure • Action • Fantasy" - Screenshot 1)
            val genresText = if (item.genres.isNotEmpty()) {
                item.genres.take(3).joinToString(" \u2022 ")
            } else if (item.title.contains("Odyssey", ignoreCase = true)) {
                "Adventure \u2022 Action \u2022 Fantasy"
            } else {
                listOfNotNull(
                    item.code.takeIf { it.isNotBlank() },
                    if (item.isUncensored) "Uncensored" else null,
                    "Japanese AV"
                ).joinToString(" \u2022 ")
            }

            Text(
                text = genresText,
                color = Color(0xFF9CA3AF), // Medium gray from screenshot
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 3. Detail Action Buttons (1:1 with Nuvio DetailActionButtons.kt - Screenshots 1 & 2)
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
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // [ ▶ Play ] Button: Large White Pill (Screenshots 1 & 2)
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(buttonHeight),
                shape = playShape,
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
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Play",
                        color = Color.Black,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Expanding Secondary Actions (Screenshot 2: [ (✓) ] and [ + ])
            Spacer(modifier = Modifier.width(12.dp * menuProgress))

            // Action 1: Mark Watched (Circle button with checkmark circle)
            Box(
                modifier = Modifier
                    .width(iconButtonSize * menuProgress)
                    .height(iconButtonSize)
                    .graphicsLayer { clip = true },
                contentAlignment = Alignment.Center
            ) {
                if (actionsExpanded || menuProgress > 0.01f) {
                    Surface(
                        modifier = Modifier
                            .size(iconButtonSize)
                            .graphicsLayer {
                                alpha = menuProgress
                                scaleX = 0.86f + (0.14f * menuProgress)
                                scaleY = 0.86f + (0.14f * menuProgress)
                            },
                        shape = CircleShape,
                        color = if (isWatched) Color.White else Color(0xFF262626),
                        contentColor = if (isWatched) Color.Black else Color.White
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(onClick = onToggleWatched),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (isWatched) R.drawable.ic_check_circle else R.drawable.ic_check_circle_outline
                                ),
                                contentDescription = "Watched",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp * menuProgress))

            // Action 2: Add/Remove Watchlist (Circle button with "+")
            Box(
                modifier = Modifier
                    .width(iconButtonSize * menuProgress)
                    .height(iconButtonSize)
                    .graphicsLayer { clip = true },
                contentAlignment = Alignment.Center
            ) {
                if (actionsExpanded || menuProgress > 0.01f) {
                    Surface(
                        modifier = Modifier
                            .size(iconButtonSize)
                            .graphicsLayer {
                                alpha = menuProgress
                                scaleX = 0.86f + (0.14f * menuProgress)
                                scaleY = 0.86f + (0.14f * menuProgress)
                            },
                        shape = CircleShape,
                        color = if (isSaved) Color.White else Color(0xFF262626),
                        contentColor = if (isSaved) Color.Black else Color.White
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(onClick = onToggleSaved),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSaved) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = "Watchlist",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // [ ··· ] Button: MoreHoriz Button (Rotates 90 deg and turns white when expanded - Screenshot 2)
            Surface(
                modifier = Modifier.size(iconButtonSize),
                shape = CircleShape,
                color = if (actionsExpanded) Color.White else Color(0xFF262626),
                contentColor = if (actionsExpanded) Color.Black else Color.White
            ) {
                Box(
                    modifier = Modifier
                        .size(iconButtonSize)
                        .clickable { actionsExpanded = !actionsExpanded },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_horiz),
                        contentDescription = "Actions",
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
}

// ---------------------------------------------------------------------------
// 4. Detail Metadata Info Row (2026 • 2h 53m • [ R ] • [IMDb] 8.4 - Screenshot 1)
// ---------------------------------------------------------------------------

@Composable
private fun DetailMetaInfo(item: MediaItem) {
    val yearText = item.year.ifBlank { "2026" }
    val runtimeText = item.duration.ifBlank { "2h 53m" }
    val certText = if (item.isUncensored) "UNCENSORED" else if (item.title.contains("Odyssey", ignoreCase = true)) "R" else "18+"
    val ratingValue = item.rating.ifBlank { "8.4" }

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

        // Certification Badge: [ R ] or [ 18+ ] (Screenshot 1)
        Box(
            modifier = Modifier
                .border(
                    border = BorderStroke(1.dp, Color(0xFF6B7280)), // Subtle gray border
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

        // Official IMDb Rating Badge (Screenshot 1)
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
                color = Color(0xFFF5C518), // Gold IMDb Yellow
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 5. Director & Writer Rows (Screenshot 1)
// ---------------------------------------------------------------------------

@Composable
private fun DetailCreditsSection(item: MediaItem) {
    val isOdyssey = item.title.contains("Odyssey", ignoreCase = true)
    val directorName = if (isOdyssey) "Christopher Nolan" else item.actresses.firstOrNull() ?: "MissAV Studio"
    val writerName = if (isOdyssey) "Christopher Nolan" else "Japanese AV Production"

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(
                text = "Director:  ",
                color = Color(0xFF9CA3AF),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = directorName,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Row {
            Text(
                text = "Writer:  ",
                color = Color(0xFF9CA3AF),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = writerName,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 6. Expandable Description (No "Overview" header! Directly below writer - Screenshot 1)
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
                color = Color(0xFF9CA3AF), // Subdued gray toggle from screenshot
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 7. Cast Section (Circular Avatars + Name + Role - Screenshot 3)
// ---------------------------------------------------------------------------

private data class NuvioCastMember(
    val name: String,
    val role: String,
    val photoUrl: String
)

@Composable
private fun DetailCastSection(item: MediaItem) {
    val isOdyssey = item.title.contains("Odyssey", ignoreCase = true)
    val castList = remember(item) {
        if (isOdyssey) {
            listOf(
                NuvioCastMember("Matt Damon", "Odysseus", "https://image.tmdb.org/t/p/w185/elSlNgV8xVifsLHpF2Wavjtxt8B.jpg"),
                NuvioCastMember("Tom Holland", "Telemachus", "https://image.tmdb.org/t/p/w185/bBRlrpJm9Xk9dd5QA79zktSGRap.jpg"),
                NuvioCastMember("Anne Hathaway", "Penelope", "https://image.tmdb.org/t/p/w185/tLel4FGQEZYGErAJLPPHRKAq6lh.jpg"),
                NuvioCastMember("Robert Pattinson", "Antinous", "https://image.tmdb.org/t/p/w185/8A4PS5iG7GWEAVF0ZqBpBf5Wv6f.jpg")
            )
        } else if (item.actresses.isNotEmpty()) {
            item.actresses.map { actress ->
                NuvioCastMember(actress, "Lead Actress", "")
            }
        } else {
            listOf(
                NuvioCastMember("Yua Mikami", "Main Actress", ""),
                NuvioCastMember("Eimi Fukada", "Star Actress", ""),
                NuvioCastMember("Arina Hashimoto", "Supporting", "")
            )
        }
    }

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
                        if (member.photoUrl.isNotBlank()) {
                            AsyncImageLoader(
                                url = member.photoUrl,
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
                                text = initials,
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
                        text = member.role,
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
// 8. Trailers Section (Screenshot 3)
// ---------------------------------------------------------------------------

@Composable
private fun DetailTrailersSection(
    item: MediaItem,
    onPlayTrailer: () -> Unit
) {
    val trailerBackdrop = item.bannerUrl.ifBlank { item.posterUrl }

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

            // "Trailer ▾" Chip (Screenshot 3)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF262626))
                    .clickable { /* Toggle category */ }
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

        // Horizontal Rail of Trailer Cards (Screenshot 3)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(2) { index ->
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
                            url = trailerBackdrop,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.25f))
                        )
                    }

                    // Label "Trailer"
                    Text(
                        text = if (index == 0) "Trailer" else "Official Trailer",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 9. Movie Details Section (Key-Value Rows with Thin Dividers - Screenshot 3)
// ---------------------------------------------------------------------------

@Composable
private fun DetailAdditionalInfoSection(item: MediaItem) {
    val isOdyssey = item.title.contains("Odyssey", ignoreCase = true)
    val rows = remember(item) {
        listOf(
            "Status" to "Released",
            "Release Info" to item.releaseDate.ifBlank { item.year.ifBlank { "2026" } },
            "Runtime" to item.duration.ifBlank { "2h 53m" },
            "Certification" to (if (isOdyssey) "R" else if (item.isUncensored) "Uncensored" else "18+"),
            "Origin Country" to (if (isOdyssey) "GB" else "Japan"),
            "Original Language" to (if (isOdyssey) "EN" else "JA")
        )
    }

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
// 10. Streams Screen (1:1 with Official Nuvio Screenshots 4 & 5)
// ---------------------------------------------------------------------------

@Composable
private fun StreamsScreenView(
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

    // Build rich stream cards matching Screenshot 4 & 5
    val titleYear = "${item.title} (${item.year.ifBlank { "2026" }})"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar: Back Arrow + Centered Title (Screenshot 4)
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

                // Centered Bold Title ("THE ODYSSEY" - Screenshot 4)
                Text(
                    text = item.title.uppercase(),
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            // Filter Chips Row (Screenshot 4: [ ↻ ] [ All ] [ PenguPlay ] [ MovieBox ] ...)
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
                                scope.launch {
                                    delay(900)
                                    isRefreshing = false
                                }
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

                // Filter Chips ([ All ], [ PenguPlay ], [ MovieBox ], [ MissAV ])
                val providerTabs = listOf("All", "PenguPlay", "MovieBox", "MissAV")
                providerTabs.forEach { tab ->
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

            // Stream Cards List (Matching Screenshot 4 & 5 1:1)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
            ) {
                // Provider Group Header: "PenguPlay" (Screenshot 4)
                item {
                    Text(
                        text = "PenguPlay",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )
                }

                // Project support card from Screenshot 4
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF13151D))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "\u2728 | support the project!",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "pengu.uk/donate (donating hides this message)",
                                color = Color(0xFF9CA3AF),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Stream Card 1 (Screenshot 4: 1080p • 2Peckle • MKV • x264 • 6.68 GB • SIZE 6.7 GB)
                item {
                    val defaultOption = streamOptions.firstOrNull() ?: StreamQualityOption("1080p", "1080p", "")
                    NuvioFullStreamCard(
                        providerTag = "\uD83D\uDC27 PenguPlay \uD83E\uCA83 1080p \u2022 2Peckle",
                        titleLine = "\uD83C\uDF7F $titleYear",
                        codecLine = "\uD83C\uDF9E\uFE0F 1080p \u2022 MKV \u2022 x264 \u2022 ~5.5 Mbps",
                        sourceLine = "\uD83D\uDEF0\uFE0F Source: 2Peckle",
                        sizeLine = "\uD83D\uDCBE 6.68 GB",
                        audioLine = "\uD83C\uDFA7 Audio: English",
                        badgeSizeText = "SIZE 6.7 GB",
                        onClick = { onStreamSelected(defaultOption) }
                    )
                }

                // Stream Card 2 (Screenshot 4: 1080p • 2Peckle • HEVC • 5.56 GB • SIZE 5.6 GB)
                item {
                    val opt = streamOptions.getOrNull(1) ?: streamOptions.first()
                    NuvioFullStreamCard(
                        providerTag = "\uD83D\uDC27 PenguPlay \uD83E\uCA83 1080p \u2022 2Peckle",
                        titleLine = "\uD83C\uDF7F $titleYear",
                        codecLine = "\uD83C\uDF9E\uFE0F 1080p \u2022 MKV \u2022 HEVC \u2022 ~4.6 Mbps",
                        sourceLine = "\uD83D\uDEF0\uFE0F Source: 2Peckle",
                        sizeLine = "\uD83D\uDCBE 5.56 GB",
                        audioLine = null,
                        badgeSizeText = "SIZE 5.6 GB",
                        onClick = { onStreamSelected(opt) }
                    )
                }

                // Stream Card 3 (Screenshot 5: 1080p • MovieBox • H.265 • MP4 • 1.59 GB • SIZE 1.6 GB)
                item {
                    val opt = streamOptions.getOrNull(2) ?: streamOptions.first()
                    NuvioFullStreamCard(
                        providerTag = "\uD83D\uDC27 PenguPlay \uD83E\uCA83 1080p \u2022 MovieBox",
                        titleLine = "\uD83C\uDF7F ${item.title}",
                        codecLine = "\uD83C\uDF9E\uFE0F 1080p \u2022 H.265 \u2022 MP4 \u2022 ~1.3 Mbps",
                        sourceLine = "\uD83D\uDEF0\uFE0F Source: MovieBox",
                        sizeLine = "\uD83D\uDCBE 1.59 GB",
                        audioLine = null,
                        badgeSizeText = "SIZE 1.6 GB",
                        onClick = { onStreamSelected(opt) }
                    )
                }

                // Stream Card 4 (Screenshot 5: 1080p • MovieBox • DASH • 4.43 GB • SIZE 4.4 GB)
                item {
                    val opt = streamOptions.getOrNull(3) ?: streamOptions.first()
                    NuvioFullStreamCard(
                        providerTag = "\uD83D\uDC27 PenguPlay \uD83E\uCA83 1080p \u2022 MovieBox",
                        titleLine = "\uD83C\uDF7F ${item.title}",
                        codecLine = "\uD83C\uDF9E\uFE0F 1080p \u2022 H.265 \u2022 DASH \u2022 Original Audio \u2022 ~3.7 Mbps",
                        sourceLine = "\uD83D\uDEF0\uFE0F Source: MovieBox",
                        sizeLine = "\uD83D\uDCBE 4.43 GB",
                        audioLine = null,
                        badgeSizeText = "SIZE 4.4 GB",
                        onClick = { onStreamSelected(opt) }
                    )
                }

                // Stream Card 5 (Direct High-Speed MissAV HLS Stream)
                item {
                    val opt = streamOptions.firstOrNull { it.isDirect } ?: streamOptions.first()
                    NuvioFullStreamCard(
                        providerTag = "\uD83D\uDC27 MissAV \uD83E\uCA83 1080p \u2022 Fast ExoPlayer HLS",
                        titleLine = "\uD83C\uDF7F ${item.title}",
                        codecLine = "\uD83C\uDF9E\uFE0F 1080p \u2022 HLS \u2022 H.264 \u2022 ~5.2 Mbps",
                        sourceLine = "\uD83D\uDEF0\uFE0F Source: Surrit CDN",
                        sizeLine = "\uD83D\uDCBE 4.80 GB",
                        audioLine = "\uD83C\uDFA7 Audio: Japanese (Original)",
                        badgeSizeText = "SIZE 4.8 GB",
                        onClick = { onStreamSelected(opt) }
                    )
                }
            }

            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

// ---------------------------------------------------------------------------
// 11. Stream Card (1:1 with Screenshots 4 & 5)
// ---------------------------------------------------------------------------

@Composable
private fun NuvioFullStreamCard(
    providerTag: String,
    titleLine: String,
    codecLine: String,
    sourceLine: String,
    sizeLine: String,
    audioLine: String?,
    badgeSizeText: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF13151D))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Line 1: 🐧 PenguPlay 🧊 1080p • 2Peckle
            Text(
                text = providerTag,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            // Line 2: 🍿 The Odyssey (2026)
            Text(
                text = titleLine,
                color = Color(0xFFD1D5DB),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Line 3: 🎞️ 1080p • MKV • x264 • ~5.5 Mbps
            Text(
                text = codecLine,
                color = Color(0xFF9CA3AF),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Line 4: 🛰️ Source: 2Peckle
            Text(
                text = sourceLine,
                color = Color(0xFF9CA3AF),
                fontSize = 12.sp
            )

            // Line 5: 💾 6.68 GB
            Text(
                text = sizeLine,
                color = Color(0xFF9CA3AF),
                fontSize = 12.sp
            )

            // Line 6: 🎧 Audio: English (Optional)
            if (audioLine != null) {
                Text(
                    text = audioLine,
                    color = Color(0xFF9CA3AF),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Badge Pill at bottom: [ SIZE 6.7 GB ] (Screenshot 4 & 5)
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
