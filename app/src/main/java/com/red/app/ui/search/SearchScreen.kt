package com.red.app.ui.search

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.app.data.MissAvRepository
import com.red.app.model.MediaItem
import com.red.app.ui.components.DiscoverGridPosterItem
import kotlinx.coroutines.delay

data class SearchSortOption(
    val key: String,
    val label: String,
    val icon: String
)

val searchSortOptions = listOf(
    SearchSortOption("views", "Total Views", "🔥"),
    SearchSortOption("monthly_views", "This Month", "📅"),
    SearchSortOption("weekly_views", "This Week", "📈"),
    SearchSortOption("today_views", "Today", "⚡"),
    SearchSortOption("saved", "Most Saved", "❤️"),
    SearchSortOption("published_at", "Recent", "🆕"),
    SearchSortOption("released_at", "Release Date", "🎬")
)

/**
 * Search & Discover Screen with MissAV Sorting & Categorization Filters.
 */
@Composable
fun SearchScreen(
    onItemClick: (MediaItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var allLoadedItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    var selectedType by remember { mutableStateOf("All") }
    var selectedSort by remember { mutableStateOf("views") }

    LaunchedEffect(searchQuery, selectedSort) {
        if (searchQuery.isNotBlank()) {
            delay(250) // Debounce keystrokes
        }
        isLoading = true
        val base = if (searchQuery.isNotBlank()) {
            MissAvRepository.search(searchQuery, sort = selectedSort)
        } else {
            MissAvRepository.getDiscoverItems(sort = selectedSort)
        }
        allLoadedItems = base
        isLoading = false
    }

    val displayedItems = remember(allLoadedItems, selectedType) {
        when (selectedType) {
            "Uncensored" -> allLoadedItems.filter { it.isUncensored }
            "Chinese" -> allLoadedItems.filter {
                it.genres.any { g -> g.contains("Chinese", ignoreCase = true) } ||
                it.id.contains("chinese") ||
                it.title.contains("Chinese", ignoreCase = true)
            }
            "Recent" -> allLoadedItems.take(30)
            else -> allLoadedItems
        }
    }

    val currentSortOption = searchSortOptions.find { it.key == selectedSort } ?: searchSortOptions.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
    ) {
        // 1. Title: "Search"
        Text(
            text = "Search",
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Search Input Field
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1B1D25))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = Color(0xFF6B7082),
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search MissAV titles, code, actress...",
                            color = Color(0xFF6B7082),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                if (searchQuery.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = Color(0xFFA0A4B8),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { searchQuery = "" }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Horizontal Sort Filter Bar (Total Views, This Month, Today, Most Saved, etc.)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(searchSortOptions) { opt ->
                val isSelected = selectedSort == opt.key
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) Color(0xFFE50914) else Color(0xFF1B1D25)
                        )
                        .clickable {
                            selectedSort = opt.key
                        }
                        .padding(horizontal = 13.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = opt.icon,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = opt.label,
                            color = if (isSelected) Color.White else Color(0xFFA0A4B8),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Section Title: "Discover" or "Search Results"
        Text(
            text = if (searchQuery.isNotBlank()) "Search Results" else "Discover",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 5. Horizontal Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Dropdown: Category Filter
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF21232C))
                    .clickable {
                        selectedType = when (selectedType) {
                            "All" -> "Uncensored"
                            "Uncensored" -> "Chinese"
                            "Chinese" -> "Recent"
                            else -> "All"
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = selectedType,
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

            // Chip 2: Quick filter for Uncensored
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selectedType == "Uncensored") Color(0xFFE50914) else Color(0xFF21232C))
                    .clickable { selectedType = if (selectedType == "Uncensored") "All" else "Uncensored" }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Uncensored",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Chip 3: Chinese Subtitles
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selectedType == "Chinese") Color(0xFF0284C7) else Color(0xFF21232C))
                    .clickable { selectedType = if (selectedType == "Chinese") "All" else "Chinese" }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Chinese Subs",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 6. Addon Subtitle Line with Active Sort & Result Count
        Text(
            text = "MissAV • $selectedType • ${currentSortOption.icon} ${currentSortOption.label} (${displayedItems.size} titles)",
            color = Color(0xFF7E8398),
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 7. Content Area: Loading / Empty / 3-Column Grid
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 100.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = Color(0xFFE50914),
                    strokeWidth = 2.5.dp
                )
            }
        } else if (displayedItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No results found",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Try adjusting your search query or sorting filter.",
                        color = Color(0xFF7E8398),
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayedItems) { item ->
                    DiscoverGridPosterItem(
                        item = item,
                        onClick = { onItemClick(item) }
                    )
                }
            }
        }
    }
}
