package com.red.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.app.data.MissAvRepository
import com.red.app.model.MediaItem
import com.red.app.ui.components.NuvioPortraitPosterCard

/**
 * Library Screen matching Screenshot 4.
 */
@Composable
fun LibraryScreen(
    onItemClick: (MediaItem) -> Unit,
    onDownloadsClick: () -> Unit
) {
    var selectedTab by remember { mutableStateOf("Saved") }
    val movies = remember { MissAvRepository.libraryMovies }
    val series = remember { MissAvRepository.librarySeries }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // 1. Header Row: "Library" on left, Grid + Downloads icons on right (Screenshot 4)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Library",
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 4-squares Grid icon
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clickable { },
                            contentAlignment = Alignment.Center
                        ) {
                            GridFourIcon(tint = Color.White)
                        }

                        // Downloads icon (arrow into tray) -> opens Downloads screen (Screenshot 5)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clickable(onClick = onDownloadsClick),
                            contentAlignment = Alignment.Center
                        ) {
                            DownloadTrayIcon(tint = Color.White)
                        }
                    }
                }
            }

            // 2. Saved / Cloud Toggle Pills (Screenshot 4)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Saved Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selectedTab == "Saved") Color(0xFF2C2F3C) else Color(0xFF1B1C23))
                            .border(
                                width = 1.dp,
                                color = if (selectedTab == "Saved") Color(0xFF4A4E62) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedTab = "Saved" }
                            .padding(horizontal = 22.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = "Saved",
                            color = if (selectedTab == "Saved") Color.White else Color(0xFF8E92A4),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Cloud Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selectedTab == "Cloud") Color(0xFF2C2F3C) else Color(0xFF1B1C23))
                            .border(
                                width = 1.dp,
                                color = if (selectedTab == "Cloud") Color(0xFF4A4E62) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedTab = "Cloud" }
                            .padding(horizontal = 22.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = "Cloud",
                            color = if (selectedTab == "Cloud") Color.White else Color(0xFF8E92A4),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 3. Sorting Dropdown Chip: "Recently added ⌵" (Screenshot 4)
            item {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF21232C))
                        .clickable { }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Recently added",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 4. Section: Movie (Screenshot 4)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Text(
                        text = "Movie",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(movies) { item ->
                            NuvioPortraitPosterCard(
                                item = item,
                                onClick = { onItemClick(item) }
                            )
                        }
                    }
                }
            }

            // 5. Section: Series with Circular '>' Button (Screenshot 4)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Series",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E212B))
                                .clickable { },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(series) { item ->
                            NuvioPortraitPosterCard(
                                item = item,
                                onClick = { onItemClick(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GridFourIcon(tint: Color) {
    Column(
        modifier = Modifier.size(18.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.size(7.dp).clip(RoundedCornerShape(1.5.dp)).background(tint))
            Box(modifier = Modifier.size(7.dp).clip(RoundedCornerShape(1.5.dp)).background(tint))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.size(7.dp).clip(RoundedCornerShape(1.5.dp)).background(tint))
            Box(modifier = Modifier.size(7.dp).clip(RoundedCornerShape(1.5.dp)).background(tint))
        }
    }
}

@Composable
fun DownloadTrayIcon(tint: Color) {
    Column(
        modifier = Modifier.size(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Arrow down
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.width(2.5.dp).height(8.dp).background(tint))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
                    .background(tint)
            )
        }
        // Tray
        Box(
            modifier = Modifier
                .width(16.dp)
                .height(4.dp)
                .border(2.dp, tint, RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
        )
    }
}
