package com.red.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.app.data.MissAvRepository
import com.red.app.model.CatalogSection
import com.red.app.model.ContinueWatchingItem
import com.red.app.model.MediaItem
import com.red.app.theme.NuvioTokens
import com.red.app.ui.components.CatalogShelfSection
import com.red.app.ui.components.ContinueWatchingCard
import com.red.app.ui.components.HomeHeroSection
import com.red.app.ui.navigation.NuvioNavBarScrollState

/**
 * 1:1 Nuvio Home Screen matching Screenshot 1 & 2.
 */
@Composable
fun HomeScreen(
    onItemClick: (MediaItem) -> Unit,
    onContinueWatchingClick: (ContinueWatchingItem) -> Unit,
    scrollState: NuvioNavBarScrollState? = null,
    modifier: Modifier = Modifier
) {
    val heroItems = remember { MissAvRepository.heroItems }
    val continueWatchingList = remember { MissAvRepository.continueWatching }
    val initialSections = remember { MissAvRepository.getInitialHomeSections() }
    var catalogSections by remember { mutableStateOf(initialSections) }
    val homeListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        val remote = MissAvRepository.getHomeSections()
        if (remote.isNotEmpty() && remote.any { it.items.isNotEmpty() }) {
            catalogSections = remote
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NuvioTokens.Colors.background)
    ) {
        val scrollModifier = if (scrollState != null) {
            Modifier.nestedScroll(scrollState.nestedScrollConnection)
        } else {
            Modifier
        }

        LazyColumn(
            state = homeListState,
            modifier = Modifier
                .fillMaxSize()
                .then(scrollModifier),
            contentPadding = PaddingValues(bottom = 110.dp)
        ) {
            // 1. Nuvio Hero Billboard Carousel (Screenshot 1 & 2)
            item(key = "home_hero", contentType = "hero") {
                HomeHeroSection(
                    items = heroItems,
                    onItemClick = onItemClick,
                    listState = homeListState
                )
            }

            // 2. Nuvio Continue Watching Section (Screenshot 1 & 2)
            item(key = "home_continue_watching", contentType = "continue_watching") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Text(
                        text = "Continue Watching",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(
                            items = continueWatchingList,
                            key = { it.id },
                            contentType = { "cw_card" }
                        ) { cwItem ->
                            ContinueWatchingCard(
                                item = cwItem,
                                onClick = { onContinueWatchingClick(cwItem) }
                            )
                        }
                    }
                }
            }

            // 3. Nuvio Catalog Shelves (Screenshot 2: Trending Series, Trending Movies, MissAV Catalogs)
            items(
                items = catalogSections,
                key = { it.id },
                contentType = { "shelf" }
            ) { section ->
                CatalogShelfSection(
                    title = section.title,
                    items = section.items,
                    onItemClick = onItemClick
                )
            }
        }
    }
}
