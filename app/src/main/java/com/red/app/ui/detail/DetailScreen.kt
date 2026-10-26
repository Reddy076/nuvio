package com.red.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.app.data.MissAvRepository
import com.red.app.model.MediaItem
import com.red.app.model.StreamQualityOption
import com.red.app.theme.NuvioTokens
import com.red.app.ui.components.AsyncImageLoader

@Composable
fun DetailScreen(
    item: MediaItem,
    onBackClick: () -> Unit,
    onPlayStream: (StreamQualityOption) -> Unit
) {
    val streamOptions = remember(item) { MissAvRepository.getStreamOptions(item) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTokens.Colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Backdrop Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
            ) {
                AsyncImageLoader(
                    url = item.bannerUrl.ifBlank { item.posterUrl },
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Dark Bottom Fade
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x66000000),
                                    Color.Transparent,
                                    NuvioTokens.Colors.background
                                ),
                                startY = 0f
                            )
                        )
                )

                // Top Back Button
                Box(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(16.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(NuvioTokens.Colors.surfaceGlass)
                        .border(1.dp, NuvioTokens.Colors.border, CircleShape)
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
            }

            // Metadata Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                // Code & Badges
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (item.code.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = NuvioTokens.Colors.primaryGlow,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = item.code,
                                color = NuvioTokens.Colors.primaryVariant,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (item.isUncensored) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = NuvioTokens.Colors.primary,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "UNCENSORED",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                color = NuvioTokens.Colors.surfaceCard,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = item.rating,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = item.duration,
                        color = NuvioTokens.Colors.textSecondary,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 28.sp
                )

                if (item.actresses.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Actresses: " + item.actresses.joinToString(" • "),
                        color = NuvioTokens.Colors.cyanAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Overview
                Text(
                    text = "Overview",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.description,
                    color = NuvioTokens.Colors.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Stream Quality Selector Header
                Text(
                    text = "Available Streams (MissAV HLS)",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Select your desired streaming bitrate:",
                    color = NuvioTokens.Colors.textMuted,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Quality Option Cards
                streamOptions.forEach { opt ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clip(RoundedCornerShape(NuvioTokens.Radius.lg))
                            .background(NuvioTokens.Colors.surfaceGlass)
                            .border(
                                width = 1.dp,
                                color = if (opt.isDirect) NuvioTokens.Colors.primaryVariant else NuvioTokens.Colors.border,
                                shape = RoundedCornerShape(NuvioTokens.Radius.lg)
                            )
                            .clickable { onPlayStream(opt) }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = opt.label,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (opt.isDirect) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    color = NuvioTokens.Colors.primary,
                                                    shape = RoundedCornerShape(3.dp)
                                                )
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "RECOMMENDED",
                                                color = Color.White,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = opt.resolution,
                                    color = NuvioTokens.Colors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(NuvioTokens.Colors.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}
