package com.red.app.model

data class MediaItem(
    val id: String,
    val title: String,
    val code: String = "",
    val posterUrl: String = "",
    val bannerUrl: String = "",
    val description: String = "",
    val duration: String = "",
    val year: String = "2026",
    val type: String = "Movie", // Movie, Series
    val genre: String = "Drama",
    val releaseDate: String = "",
    val actresses: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val rating: String = "4.8",
    val quality: String = "FHD",
    val isUncensored: Boolean = false,
    val isWatched: Boolean = false,
    val videoUuid: String = ""
)

data class ContinueWatchingItem(
    val id: String,
    val mediaItem: MediaItem,
    val seasonEpisode: String = "S1 E1",
    val episodeTitle: String = "Episode 1",
    val timeLeftText: String = "45m left",
    val progressPercent: Float = 0.5f,
    val backdropUrl: String = ""
)

data class StreamQualityOption(
    val label: String,
    val resolution: String,
    val streamUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val isDirect: Boolean = false
)

data class CatalogSection(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val items: List<MediaItem> = emptyList(),
    val hasSeeAll: Boolean = true
)

data class AddonSource(
    val id: String,
    val name: String,
    val manifestUrl: String,
    val isEnabled: Boolean = true,
    val isBuiltIn: Boolean = false,
    val status: String = "Active"
)
