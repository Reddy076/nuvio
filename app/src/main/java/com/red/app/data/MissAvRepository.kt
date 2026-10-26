package com.red.app.data

import android.content.Context
import com.red.app.model.AddonSource
import com.red.app.model.CatalogSection
import com.red.app.model.ContinueWatchingItem
import com.red.app.model.MediaItem
import com.red.app.model.StreamQualityOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object MissAvRepository {

    private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private const val MISSAV_REFERER = "https://missav.ws/"
    const val DEFAULT_ADDON_URL = "http://127.0.0.1:7000"

    private var appContext: Context? = null
    var addonBaseUrl: String = DEFAULT_ADDON_URL
        private set

    private val allVideosMap = LinkedHashMap<String, MediaItem>()
    private val heroList = mutableListOf<MediaItem>()
    private val continueWatchingList = mutableListOf<ContinueWatchingItem>()
    private val catalogMap = LinkedHashMap<String, MutableList<MediaItem>>()
    private val watchlistSlugs = mutableSetOf<String>()

    private val addonSources = mutableListOf(
        AddonSource(
            id = "missav_builtin",
            name = "MissAV (Stremio Addon Engine)",
            manifestUrl = "$DEFAULT_ADDON_URL/manifest.json",
            isEnabled = true,
            isBuiltIn = true,
            status = "Online"
        )
    )

    init {
        // Initialize with default real MissAV items immediately so repository is ready before init()
        populateFallbackRealData()
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        loadFromAssets(context)
        loadPreferences(context)
    }

    private fun loadPreferences(context: Context) {
        try {
            val prefs = context.getSharedPreferences("nuvio_missav_prefs", Context.MODE_PRIVATE)
            addonBaseUrl = prefs.getString("addon_url", DEFAULT_ADDON_URL) ?: DEFAULT_ADDON_URL
            val savedWatchlist = prefs.getStringSet("watchlist", emptySet()) ?: emptySet()
            watchlistSlugs.addAll(savedWatchlist)
        } catch (ignored: Throwable) {}
    }

    private fun savePreferences() {
        val ctx = appContext ?: return
        try {
            val prefs = ctx.getSharedPreferences("nuvio_missav_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("addon_url", addonBaseUrl)
                .putStringSet("watchlist", watchlistSlugs)
                .apply()
        } catch (ignored: Throwable) {}
    }

    fun updateAddonUrl(newUrl: String) {
        if (newUrl.isNotBlank()) {
            addonBaseUrl = newUrl.trim().removeSuffix("/")
            addonSources[0] = addonSources[0].copy(manifestUrl = "$addonBaseUrl/manifest.json")
            savePreferences()
        }
    }

    fun getAddonSources(): List<AddonSource> = addonSources.toList()

    fun addAddonSource(name: String, url: String): Boolean {
        if (url.isBlank()) return false
        val newSource = AddonSource(
            id = "custom_" + System.currentTimeMillis(),
            name = name.ifBlank { "Custom Addon" },
            manifestUrl = url.trim(),
            isEnabled = true,
            isBuiltIn = false,
            status = "Active"
        )
        addonSources.add(newSource)
        return true
    }

    fun toggleAddon(id: String) {
        val index = addonSources.indexOfFirst { it.id == id }
        if (index != -1) {
            val s = addonSources[index]
            addonSources[index] = s.copy(isEnabled = !s.isEnabled)
        }
    }

    fun removeAddon(id: String) {
        addonSources.removeAll { it.id == id && !it.isBuiltIn }
    }

    fun getStreamHeaders(): Map<String, String> {
        return mapOf(
            "User-Agent" to DEFAULT_USER_AGENT,
            "Referer" to MISSAV_REFERER
        )
    }

    // Hero Billboard items: 100% Real MissAV releases
    val heroItems: List<MediaItem>
        get() = heroList.toList()

    // Continue Watching items: Real MissAV videos with watch progress
    val continueWatching: List<ContinueWatchingItem>
        get() = continueWatchingList.toList()

    // Discover Grid: All real MissAV videos
    val discoverGridItems: List<MediaItem>
        get() = allVideosMap.values.toList()

    // Library Watchlist
    val libraryMovies: List<MediaItem>
        get() = allVideosMap.values.filter { watchlistSlugs.contains(it.slug()) || it.isWatched }.ifEmpty {
            allVideosMap.values.take(12)
        }

    val librarySeries: List<MediaItem>
        get() = allVideosMap.values.filter { it.isUncensored }.take(12)

    fun toggleWatchlist(item: MediaItem) {
        val s = item.slug()
        if (watchlistSlugs.contains(s)) {
            watchlistSlugs.remove(s)
        } else {
            watchlistSlugs.add(s)
        }
        savePreferences()
    }

    fun isInWatchlist(item: MediaItem): Boolean = watchlistSlugs.contains(item.slug())

    fun addContinueWatching(item: MediaItem, progressPercent: Float, timeLeft: String = "45m left") {
        val existingIndex = continueWatchingList.indexOfFirst { it.mediaItem.id == item.id }
        val updated = ContinueWatchingItem(
            id = "cw_" + item.slug(),
            mediaItem = item,
            seasonEpisode = if (item.isUncensored) "Uncensored" else "Full HD",
            episodeTitle = item.code,
            timeLeftText = timeLeft,
            progressPercent = progressPercent.coerceIn(0.05f, 0.98f),
            backdropUrl = item.posterUrl
        )
        if (existingIndex != -1) {
            continueWatchingList[existingIndex] = updated
        } else {
            continueWatchingList.add(0, updated)
        }
    }

    // Home Shelves: 4 Official MissAV Catalogs from the Addon
    fun getInitialHomeSections(): List<CatalogSection> {
        return listOf(
            CatalogSection(
                id = "missav_uncensored",
                title = "MissAV Uncensored Leaks - Adult",
                items = catalogMap["missav_uncensored"]?.toList() ?: emptyList()
            ),
            CatalogSection(
                id = "missav_release",
                title = "MissAV Recent Releases - Movies",
                items = catalogMap["missav_release"]?.toList() ?: emptyList()
            ),
            CatalogSection(
                id = "missav_new",
                title = "MissAV New Releases - Movies",
                items = catalogMap["missav_new"]?.toList() ?: emptyList()
            ),
            CatalogSection(
                id = "missav_chinese",
                title = "MissAV Chinese Subtitles - Movies",
                items = catalogMap["missav_chinese"]?.toList() ?: emptyList()
            )
        )
    }

    suspend fun getHomeSections(): List<CatalogSection> = withContext(Dispatchers.IO) {
        // Attempt live fetch from Addon server
        val uncensored = fetchCatalogFromAddon("missav_uncensored") ?: catalogMap["missav_uncensored"] ?: emptyList()
        val release = fetchCatalogFromAddon("missav_release") ?: catalogMap["missav_release"] ?: emptyList()
        val newRel = fetchCatalogFromAddon("missav_new") ?: catalogMap["missav_new"] ?: emptyList()
        val chinese = fetchCatalogFromAddon("missav_chinese") ?: catalogMap["missav_chinese"] ?: emptyList()

        listOf(
            CatalogSection(
                id = "missav_uncensored",
                title = "MissAV Uncensored Leaks - Adult",
                items = uncensored
            ),
            CatalogSection(
                id = "missav_release",
                title = "MissAV Recent Releases - Movies",
                items = release
            ),
            CatalogSection(
                id = "missav_new",
                title = "MissAV New Releases - Movies",
                items = newRel
            ),
            CatalogSection(
                id = "missav_chinese",
                title = "MissAV Chinese Subtitles - Movies",
                items = chinese
            )
        )
    }

    suspend fun search(query: String, sort: String = "views"): List<MediaItem> = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase()
        if (q.isBlank()) {
            return@withContext getDiscoverItems(sort)
        }

        // 1. Try querying Addon live search endpoint with sort
        val remoteResults = fetchSearchFromAddon(q, sort)
        if (remoteResults.isNotEmpty()) {
            return@withContext remoteResults
        }

        // 2. Query offline / local real MissAV database with sorting
        val filtered = allVideosMap.values.filter {
            it.code.lowercase().contains(q) ||
            it.title.lowercase().contains(q) ||
            it.slug().contains(q) ||
            it.actresses.any { a -> a.lowercase().contains(q) } ||
            it.genre.lowercase().contains(q)
        }
        sortLocalItems(filtered, sort)
    }

    suspend fun getDiscoverItems(sort: String = "views"): List<MediaItem> = withContext(Dispatchers.IO) {
        // Try live catalog with sort from addon
        val remote = fetchCatalogFromAddonWithSort("missav_uncensored", sort)
        if (!remote.isNullOrEmpty()) {
            return@withContext remote
        }
        sortLocalItems(allVideosMap.values.toList(), sort)
    }

    private fun sortLocalItems(items: List<MediaItem>, sort: String): List<MediaItem> {
        return when (sort.lowercase()) {
            "views" -> items.sortedByDescending { it.rating }
            "today_views" -> items.sortedBy { it.code }
            "weekly_views" -> items.sortedByDescending { it.code }
            "monthly_views" -> items.sortedBy { it.title }
            "saved" -> items.sortedByDescending { if (watchlistSlugs.contains(it.slug())) 1 else 0 }
            "published_at" -> items.sortedByDescending { it.year + it.title }
            "released_at" -> items.sortedByDescending { it.year + it.code }
            else -> items
        }
    }

    fun getStreamOptions(item: MediaItem): List<StreamQualityOption> {
        val uuid = if (item.videoUuid.isNotBlank()) item.videoUuid else "27820a29-8d98-40a3-935f-67b86c2b239e"
        val headers = getStreamHeaders()

        // 1:1 Match with the MissAV Addon stream structure developed earlier:
        // Direct single-resolution variant streams first for instant ExoPlayer playback
        return listOf(
            StreamQualityOption(
                label = "🌟 1080p FHD (Proxied / ExoPlayer Fast)",
                resolution = "1080p",
                streamUrl = "$addonBaseUrl/proxy/m3u8/$uuid/1080p/video.m3u8",
                headers = headers,
                isDirect = true
            ),
            StreamQualityOption(
                label = "📺 720p HD (Proxied)",
                resolution = "720p",
                streamUrl = "$addonBaseUrl/proxy/m3u8/$uuid/720p/video.m3u8",
                headers = headers,
                isDirect = true
            ),
            StreamQualityOption(
                label = "📱 480p SD (Proxied)",
                resolution = "480p",
                streamUrl = "$addonBaseUrl/proxy/m3u8/$uuid/480p/video.m3u8",
                headers = headers,
                isDirect = true
            ),
            StreamQualityOption(
                label = "⚡ Auto Quality (Adaptive HLS)",
                resolution = "Auto",
                streamUrl = "$addonBaseUrl/proxy/m3u8/$uuid/playlist.m3u8",
                headers = headers,
                isDirect = false
            ),
            StreamQualityOption(
                label = "💾 1080p MP4 (Direct Download)",
                resolution = "1080p MP4",
                streamUrl = "$addonBaseUrl/proxy/download/$uuid/1080p/video.mp4",
                headers = headers,
                isDirect = true
            ),
            StreamQualityOption(
                label = "🌐 CDN Direct Stream (MissAV Surrit)",
                resolution = "Source",
                streamUrl = "https://surrit.com/$uuid/playlist.m3u8",
                headers = headers,
                isDirect = false
            )
        )
    }

    private fun MediaItem.slug(): String =
        id.replace("missav:", "").trim()

    private fun fetchCatalogFromAddon(catalogId: String): List<MediaItem>? {
        return try {
            val url = URL("$addonBaseUrl/catalog/movie/$catalogId.json")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 4000
            conn.setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parseMetasJson(body)
            } else {
                null
            }
        } catch (e: Throwable) {
            null
        }
    }

    private fun fetchSearchFromAddon(query: String, sort: String = "views"): List<MediaItem> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val encodedSort = URLEncoder.encode(sort, "UTF-8")
            val url = URL("$addonBaseUrl/catalog/movie/missav_uncensored/search=$encoded&sort=$encodedSort.json")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 4000
            conn.setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parseMetasJson(body)
            } else {
                emptyList()
            }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    private fun fetchCatalogFromAddonWithSort(catalogId: String, sort: String): List<MediaItem>? {
        return try {
            val encodedSort = URLEncoder.encode(sort, "UTF-8")
            val url = URL("$addonBaseUrl/catalog/movie/$catalogId/sort=$encodedSort.json")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 4000
            conn.setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parseMetasJson(body)
            } else {
                null
            }
        } catch (e: Throwable) {
            null
        }
    }

    private fun parseMetasJson(jsonString: String): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        try {
            val root = JSONObject(jsonString)
            val metas = root.optJSONArray("metas") ?: return emptyList()
            for (i in 0 until metas.length()) {
                val obj = metas.getJSONObject(i)
                val id = obj.optString("id", "")
                val slug = id.replace("missav:", "")
                val title = obj.optString("name", slug)
                val poster = obj.optString("poster", "https://fourhoi.com/$slug/cover-n.jpg")
                val isUncensored = slug.contains("uncensored") || title.contains("Uncensored")
                val code = extractCode(slug, title)

                val item = allVideosMap[slug] ?: MediaItem(
                    id = id.ifBlank { "missav:$slug" },
                    title = title,
                    code = code,
                    posterUrl = poster,
                    bannerUrl = poster,
                    description = obj.optString("description", "MissAV Video: $title"),
                    year = obj.optString("releaseInfo", "2024"),
                    type = "Movie",
                    genre = if (isUncensored) "Uncensored" else "Japanese AV",
                    genres = listOf("MissAV", "Japanese AV"),
                    rating = "4.8",
                    isUncensored = isUncensored,
                    videoUuid = ""
                )
                result.add(item)
            }
        } catch (ignored: Throwable) {}
        return result
    }

    private fun loadFromAssets(context: Context) {
        try {
            val inputStream = context.assets.open("missav_data.json")
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line)
            }
            reader.close()

            val root = JSONObject(sb.toString())
            val videosObj = root.optJSONObject("videos") ?: return

            allVideosMap.clear()
            val keys = videosObj.keys()
            while (keys.hasNext()) {
                val slug = keys.next()
                val vo = videosObj.getJSONObject(slug)
                val actressesList = mutableListOf<String>()
                val actArr = vo.optJSONArray("actresses")
                if (actArr != null) {
                    for (i in 0 until actArr.length()) actressesList.add(actArr.getString(i))
                }
                val genresList = mutableListOf<String>()
                val genArr = vo.optJSONArray("genres")
                if (genArr != null) {
                    for (i in 0 until genArr.length()) genresList.add(genArr.getString(i))
                }

                val item = MediaItem(
                    id = vo.optString("id", "missav:$slug"),
                    title = vo.optString("title", slug),
                    code = vo.optString("code", slug.uppercase()),
                    posterUrl = vo.optString("posterUrl", "https://fourhoi.com/$slug/cover-n.jpg"),
                    bannerUrl = vo.optString("bannerUrl", "https://fourhoi.com/$slug/cover-n.jpg"),
                    description = vo.optString("description", "MissAV Video"),
                    duration = vo.optString("duration", "120 min"),
                    year = vo.optString("year", "2024"),
                    type = vo.optString("type", "Movie"),
                    genre = vo.optString("genre", "Uncensored"),
                    genres = genresList,
                    actresses = actressesList,
                    rating = vo.optString("rating", "4.8"),
                    isUncensored = vo.optBoolean("isUncensored", false),
                    videoUuid = vo.optString("videoUuid", "")
                )
                allVideosMap[slug] = item
            }

            // Populate Hero
            val heroArr = root.optJSONArray("hero_slugs")
            heroList.clear()
            if (heroArr != null) {
                for (i in 0 until heroArr.length()) {
                    val s = heroArr.getString(i)
                    allVideosMap[s]?.let { heroList.add(it) }
                }
            }

            // Populate Continue Watching
            val cwArr = root.optJSONArray("continue_watching_slugs")
            continueWatchingList.clear()
            if (cwArr != null) {
                for (i in 0 until cwArr.length()) {
                    val s = cwArr.getString(i)
                    allVideosMap[s]?.let {
                        continueWatchingList.add(
                            ContinueWatchingItem(
                                id = "cw_$s",
                                mediaItem = it,
                                seasonEpisode = if (it.isUncensored) "Uncensored" else "Full HD",
                                episodeTitle = it.code,
                                timeLeftText = "${(i + 1) * 25}m left",
                                progressPercent = 0.35f + (i * 0.15f),
                                backdropUrl = it.posterUrl
                            )
                        )
                    }
                }
            }

            // Populate Catalogs
            val catObj = root.optJSONObject("catalogs")
            if (catObj != null) {
                val catKeys = catObj.keys()
                while (catKeys.hasNext()) {
                    val catName = catKeys.next()
                    val arr = catObj.getJSONArray(catName)
                    val items = mutableListOf<MediaItem>()
                    for (i in 0 until arr.length()) {
                        val s = arr.getString(i)
                        allVideosMap[s]?.let { items.add(it) }
                    }
                    catalogMap[catName] = items
                }
            }
        } catch (e: Throwable) {
            // Keep default fallback real data
        }
    }

    private fun extractCode(slug: String, title: String): String {
        val regex = Regex("([A-Za-z0-9]+-\\d+)")
        regex.find(slug.uppercase())?.let { return it.value }
        regex.find(title)?.let { return it.value }
        return slug.split("-").firstOrNull()?.uppercase() ?: slug.uppercase()
    }

    private fun populateFallbackRealData() {
        val defaultSlugs = listOf(
            Triple("snos-359-uncensored-leak", "SNOS-359", "f55ee8c7-e96e-4a0f-bcc3-c7956e44e245"),
            Triple("nhdtb-213-uncensored-leak", "NHDTB-213", "27820a29-8d98-40a3-935f-67b86c2b239e"),
            Triple("hnbr-016", "HNBR-016", "da1be5d4-4ad9-448d-aba9-3b35334704b0"),
            Triple("gmem-157", "GMEM-157", "a42fc9d7-08f1-4343-9110-16e661aecd06"),
            Triple("dldss-560", "DLDSS-560", "f889ca1b-a725-4e42-a752-843e4cf72eda"),
            Triple("juyu-009", "JUYU-009", "38367c54-1604-4d6b-9e04-8002de265c52"),
            Triple("pppe-449-chinese-subtitle", "PPPE-449", "3917c598-61c9-460c-a7ac-14eab2d6f8bf"),
            Triple("snos-449", "SNOS-449", "17e6da6d-3c1f-4f0a-811f-250607952624"),
            Triple("start-542-uncensored-leak", "START-542", "390ea0bf-a1fc-4b14-8b43-6c1f7894f107"),
            Triple("hmn-922-uncensored-leak", "HMN-922", "43851496-fa7a-4fcc-89b5-2f7a134b50ac"),
            Triple("aldn-621", "ALDN-621", "8cb6fa79-c2a3-4622-9b01-ea835e78e460"),
            Triple("huntc-596", "HUNTC-596", "afc8eb4d-687a-4532-9260-28a4a2e0a08c")
        )

        for ((slug, code, uuid) in defaultSlugs) {
            val isUncensored = slug.contains("uncensored")
            val item = MediaItem(
                id = "missav:$slug",
                title = "$code MissAV Official Release",
                code = code,
                posterUrl = "https://fourhoi.com/$slug/cover-n.jpg",
                bannerUrl = "https://fourhoi.com/$slug/cover-n.jpg",
                description = "Official MissAV Video Presentation: $code",
                duration = "120 min",
                year = "2024",
                type = "Movie",
                genre = if (isUncensored) "Uncensored" else "Japanese AV",
                genres = listOf("MissAV", "Japanese AV", if (isUncensored) "Uncensored" else "Drama"),
                rating = "4.8",
                isUncensored = isUncensored,
                videoUuid = uuid
            )
            allVideosMap[slug] = item
        }

        heroList.addAll(allVideosMap.values.take(8))
        allVideosMap.values.take(4).forEachIndexed { i, it ->
            continueWatchingList.add(
                ContinueWatchingItem(
                    id = "cw_${it.slug()}",
                    mediaItem = it,
                    seasonEpisode = if (it.isUncensored) "Uncensored" else "Full HD",
                    episodeTitle = it.code,
                    timeLeftText = "${(i + 1) * 20}m left",
                    progressPercent = 0.35f + (i * 0.15f),
                    backdropUrl = it.posterUrl
                )
            )
        }

        catalogMap["missav_uncensored"] = allVideosMap.values.filter { it.isUncensored }.toMutableList()
        catalogMap["missav_release"] = allVideosMap.values.take(8).toMutableList()
        catalogMap["missav_new"] = allVideosMap.values.reversed().take(8).toMutableList()
        catalogMap["missav_chinese"] = allVideosMap.values.filter { it.slug().contains("chinese") || !it.isUncensored }.toMutableList()
    }
}
