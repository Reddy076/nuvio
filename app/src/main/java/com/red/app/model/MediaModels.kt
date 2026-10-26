package com.red.app.model

data class MediaItem(
    val id: String,
    val title: String,
    val code: String = "",
    val posterUrl: String = "",
    val bannerUrl: String = "",
    val description: String = "",
    val duration: String = "",
    val year: String = "2024",
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

data class MetaDetails(
    val id: String,
    val type: String = "movie",
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val status: String? = null,
    val imdbRating: String? = null,
    val ageRating: String? = null,
    val runtime: String? = null,
    val genres: List<String> = emptyList(),
    val director: List<String> = emptyList(),
    val writer: List<String> = emptyList(),
    val cast: List<MetaPerson> = emptyList(),
    val country: String? = null,
    val language: String? = null,
    val trailers: List<MetaTrailer> = emptyList(),
    val videos: List<MetaVideo> = emptyList()
)

data class MetaPerson(
    val name: String,
    val role: String? = null,
    val photo: String? = null
)

data class MetaTrailer(
    val id: String,
    val name: String,
    val key: String,
    val site: String = "YouTube",
    val type: String = "Trailer"
)

data class MetaVideo(
    val id: String,
    val title: String,
    val season: Int? = null,
    val episode: Int? = null,
    val thumbnail: String? = null,
    val overview: String? = null
)

data class StreamItem(
    val name: String? = null,
    val title: String? = null,
    val description: String? = null,
    val url: String? = null,
    val addonName: String = "MissAV",
    val addonId: String = "missav",
    val resolution: String? = null,
    val sizeBytes: Long? = null,
    val sizeText: String? = null,
    val format: String? = null,
    val codec: String? = null,
    val source: String? = null,
    val audio: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val isDirect: Boolean = false
)

data class AddonStreamGroup(
    val addonName: String,
    val addonId: String,
    val streams: List<StreamItem> = emptyList(),
    val isLoading: Boolean = false
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
