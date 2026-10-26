package com.red.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.app.model.ContinueWatchingItem
import com.red.app.model.MediaItem
import com.red.app.theme.NuvioTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
private val cacheSizeKb = (maxMemoryKb / 6).coerceIn(1024 * 24, 1024 * 96)
private val imageMemoryCache = object : LruCache<String, Bitmap>(cacheSizeKb) {
    override fun sizeOf(key: String, bitmap: Bitmap): Int {
        return bitmap.byteCount / 1024
    }
}

private fun decodeSampledBitmap(bytes: ByteArray, reqWidth: Int = 500, reqHeight: Int = 750): Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        var inSampleSize = 1
        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
            val halfHeight: Int = options.outHeight / 2
            val halfWidth: Int = options.outWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight || (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
    } catch (e: Throwable) {
        null
    }
}

@Composable
fun AsyncImageLoader(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    targetWidth: Int = 500,
    targetHeight: Int = 750
) {
    val context = LocalContext.current
    var bitmap by remember(url) {
        mutableStateOf(if (url.isNotBlank()) imageMemoryCache.get(url) else null)
    }

    LaunchedEffect(url) {
        if (bitmap == null && url.isNotBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val cacheKey = "img_" + (url.hashCode().toString())
                    val cacheFile = File(context.cacheDir, cacheKey)
                    val bytes: ByteArray = if (cacheFile.exists() && cacheFile.length() > 0) {
                        cacheFile.readBytes()
                    } else {
                        val conn = URL(url).openConnection() as HttpURLConnection
                        conn.connectTimeout = 6000
                        conn.readTimeout = 8000
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                        conn.doInput = true
                        conn.connect()
                        val input = conn.inputStream
                        val buffer = ByteArrayOutputStream()
                        val data = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(data, 0, data.size).also { bytesRead = it } != -1) {
                            buffer.write(data, 0, bytesRead)
                        }
                        input.close()
                        conn.disconnect()
                        val downloaded = buffer.toByteArray()
                        try {
                            cacheFile.writeBytes(downloaded)
                        } catch (ignored: Exception) {}
                        downloaded
                    }

                    val decoded = decodeSampledBitmap(bytes, targetWidth, targetHeight)
                    if (decoded != null) {
                        imageMemoryCache.put(url, decoded)
                        withContext(Dispatchers.Main) {
                            bitmap = decoded
                        }
                    }
                } catch (e: Exception) {
                    // Fallback to placeholder
                }
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        ShimmerPlaceholder(modifier = modifier)
    }
}

@Composable
fun ShimmerPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(NuvioTokens.Colors.surfaceCard)
            .border(
                width = 0.5.dp,
                color = NuvioTokens.Colors.borderDefault,
                shape = RoundedCornerShape(NuvioTokens.Radius.lg)
            )
    )
}

// Parallax constants ported from Nuvio HomeHeroSection
private const val HERO_BACKGROUND_PARALLAX = 0.055f
private const val HERO_BACKGROUND_SCALE = 1.14f
private const val HERO_SCROLL_PARALLAX = 0.3f
private const val HERO_SCROLL_DOWN_SCALE_MULTIPLIER = 0.0001f
private const val HERO_SCROLL_UP_SCALE_MULTIPLIER = 0.002f
private const val HERO_SCROLL_MAX_SCALE = 1.3f

private fun heroBackgroundScrollScale(scrollOffsetPx: Float): Float {
    val scaleIncrease = if (scrollOffsetPx < 0f) {
        abs(scrollOffsetPx) * HERO_SCROLL_UP_SCALE_MULTIPLIER
    } else {
        scrollOffsetPx * HERO_SCROLL_DOWN_SCALE_MULTIPLIER
    }
    return (1f + scaleIncrease).coerceAtMost(HERO_SCROLL_MAX_SCALE)
}

private fun heroBackgroundScrollTranslationY(scrollOffsetPx: Float): Float {
    return scrollOffsetPx * HERO_SCROLL_PARALLAX
}

/**
 * 1:1 Nuvio Hero Billboard Section with Parallax, Scale, and Auto-Scroll (Screenshots 1, 2, 4).
 */
@Composable
fun HomeHeroSection(
    items: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState? = null
) {
    if (items.isEmpty()) return

    var currentPage by remember { mutableStateOf(0) }
    var dragOffset by remember { mutableStateOf(0f) }

    // Auto-scroll every 8 seconds (exact Nuvio timing)
    LaunchedEffect(currentPage, items.size) {
        if (items.size > 1) {
            delay(8000L)
            currentPage = (currentPage + 1) % items.size
        }
    }

    val currentItem = items[currentPage % items.size]

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(490.dp)
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Color.Black)
            .pointerInput(items.size) {
                if (items.size <= 1) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragOffset < -60f) {
                            currentPage = (currentPage + 1) % items.size
                        } else if (dragOffset > 60f) {
                            currentPage = if (currentPage > 0) currentPage - 1 else items.size - 1
                        }
                        dragOffset = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount
                    }
                )
            }
    ) {
        // 1. Hero Background Image with Parallax & Scroll Scale
        AsyncImageLoader(
            url = currentItem.bannerUrl.ifBlank { currentItem.posterUrl },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scrollOffsetPx = if (listState == null) 0f
                    else if (listState.firstVisibleItemIndex > 0) 500f
                    else listState.firstVisibleItemScrollOffset.toFloat()

                    val scrollScale = heroBackgroundScrollScale(scrollOffsetPx)
                    scaleX = HERO_BACKGROUND_SCALE * scrollScale
                    scaleY = HERO_BACKGROUND_SCALE * scrollScale
                    this.translationY = heroBackgroundScrollTranslationY(scrollOffsetPx)
                },
            contentScale = ContentScale.Crop,
            targetWidth = 1080,
            targetHeight = 720
        )

        // 2. Multi-tier gradient overlay (exact Nuvio shading)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x99000000),
                            Color(0x22000000),
                            Color(0x55000000),
                            Color(0xC40B0C0E),
                            Color(0xFF0B0C0E)
                        )
                    )
                )
        )

        // 3. Bottom Centered Hero Information
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (currentItem.code.isNotBlank()) {
                Surface(
                    color = Color.White.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = currentItem.code,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            if (currentItem.title.isNotBlank()) {
                Text(
                    text = currentItem.title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Subtitle line (e.g. Movie • Comedy • 2026 or Movie • Uncensored • 2026)
            Text(
                text = "${currentItem.type} • ${currentItem.genre} • ${currentItem.year}",
                color = Color(0xFFD4D8E8),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            // White Pill Button: "View Details" (Matching Screenshot 1 & 2)
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(40.dp))
                    .clickable { onItemClick(currentItem) },
                color = Color.White,
                shape = RoundedCornerShape(40.dp)
            ) {
                Text(
                    text = "View Details",
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 11.dp),
                    color = Color.Black,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Animated interactive page indicator dots/pill
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, _ ->
                    val isActive = index == (currentPage % items.size)
                    val width by animateDpAsState(
                        targetValue = if (isActive) 28.dp else 7.dp,
                        animationSpec = tween(300, easing = NuvioTokens.Motion.standard)
                    )
                    val alpha by animateFloatAsState(
                        targetValue = if (isActive) 1f else 0.4f,
                        animationSpec = tween(300)
                    )

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = alpha))
                            .width(width)
                            .height(7.dp)
                            .clickable { currentPage = index }
                    )
                }
            }
        }
    }
}

/**
 * 16:9 Landscape Continue Watching Card matching Screenshot 1 & 2.
 */
@Composable
fun ContinueWatchingCard(
    item: ContinueWatchingItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .width(236.dp)
            .height(134.dp)
            .clip(cardShape)
            .background(NuvioTokens.Colors.surface)
            .nuvioCardDepth(shape = cardShape, surface = NuvioCardDepthSurface.ContinueWatching)
            .clickable(onClick = onClick)
    ) {
        // 1. Background Artwork (16:9 cinematic crop)
        AsyncImageLoader(
            url = item.backdropUrl.ifBlank { item.mediaItem.bannerUrl.ifBlank { item.mediaItem.posterUrl } },
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            targetWidth = 480,
            targetHeight = 270
        )

        // 2. Dark bottom gradient for text contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x33000000),
                            Color(0x99000000),
                            Color(0xE6000000)
                        ),
                        startY = 50f
                    )
                )
        )

        // 3. Top-right Time Left Pill Badge ("1h 9m left")
        if (item.timeLeftText.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xD910121A))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = item.timeLeftText,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // 4. Bottom Info: Episode Code, Title, Episode Name, and Progress Bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = item.seasonEpisode,
                color = Color(0xFFC8CCD8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = item.mediaItem.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = item.episodeTitle,
                color = Color(0xFFA0A4B8),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Inset Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x40FFFFFF))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(item.progressPercent.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White)
                )
            }
        }
    }
}

/**
 * Catalog Shelf Row Section matching Screenshot 2 (Trending Series, Trending Movies, etc.)
 */
@Composable
fun CatalogShelfSection(
    title: String,
    items: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit,
    onSeeAllClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 22.dp)
    ) {
        // Section Header Row: Title on Left, Circular '>' button on Right (Screenshot 2)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )

            // Circular '>' button (Screenshot 2)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(NuvioTokens.Colors.surfaceCard)
                    .clickable(onClick = onSeeAllClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = "See All",
                    tint = NuvioTokens.Colors.textMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Horizontal Poster Rail
        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = items,
                key = { it.id },
                contentType = { "poster_card" }
            ) { item ->
                NuvioPortraitPosterCard(
                    item = item,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

/**
 * 2:3 Portrait Poster Card with nuvioCardDepth specular sheen,
 * Top Watched Checkmark and Title/Year Below (Screenshot 2).
 */
@Composable
fun NuvioPortraitPosterCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Int = 125,
    height: Int = 188
) {
    val cardShape = RoundedCornerShape(14.dp)

    Column(
        modifier = modifier
            .width(width.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .width(width.dp)
                .height(height.dp)
                .clip(cardShape)
                .background(NuvioTokens.Colors.surface)
                .nuvioCardDepth(shape = cardShape, surface = NuvioCardDepthSurface.Posters)
        ) {
            AsyncImageLoader(
                url = item.posterUrl,
                modifier = Modifier.fillMaxSize(),
                targetWidth = 260,
                targetHeight = 390
            )

            // Watched checkmark badge in top-right corner (Matching Screenshot 2 & 4)
            if (item.isWatched) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Watched",
                        tint = Color.Black,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title below poster (bold white, 13sp)
        Text(
            text = item.title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Year below title (muted grey, 12sp)
        Text(
            text = item.year,
            color = NuvioTokens.Colors.textMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

/**
 * 3-Column Grid Poster Item matching Screenshot 3 (Discover Grid).
 */
@Composable
fun DiscoverGridPosterItem(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(14.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.675f)
                .clip(cardShape)
                .background(NuvioTokens.Colors.surface)
                .nuvioCardDepth(shape = cardShape, surface = NuvioCardDepthSurface.Posters)
        ) {
            AsyncImageLoader(
                url = item.posterUrl,
                modifier = Modifier.fillMaxSize(),
                targetWidth = 280,
                targetHeight = 420
            )

            if (item.isWatched) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Watched",
                        tint = Color.Black,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = item.year,
            color = NuvioTokens.Colors.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal
        )
    }
}
