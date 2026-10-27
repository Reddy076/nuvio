package com.red.app.data

import android.content.Context
import com.red.app.model.*
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
    const val DEFAULT_ADDON_URL = "https://nuvio-c4dd.onrender.com"

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
            val saved = prefs.getString("addon_url", DEFAULT_ADDON_URL) ?: DEFAULT_ADDON_URL
            addonBaseUrl = if (saved.contains("127.0.0.1") || saved.contains("localhost")) {
                DEFAULT_ADDON_URL
            } else {
                saved
            }
            addonSources[0] = addonSources[0].copy(manifestUrl = "$addonBaseUrl/manifest.json")
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

    // Home Shelf: Single Official MissAV Catalog from the Addon
    fun getInitialHomeSections(): List<CatalogSection> {
        return listOf(
            CatalogSection(
                id = "missav_uncensored",
                title = "MissAV",
                items = catalogMap["missav_uncensored"]?.toList() ?: allVideosMap.values.toList()
            )
        )
    }

    suspend fun getHomeSections(): List<CatalogSection> = withContext(Dispatchers.IO) {
        // Attempt live fetch from Addon server
        val uncensored = fetchCatalogFromAddon("missav_uncensored") ?: catalogMap["missav_uncensored"] ?: allVideosMap.values.toList()

        listOf(
            CatalogSection(
                id = "missav_uncensored",
                title = "MissAV",
                items = uncensored
            )
        )
    }

    suspend fun search(query: String, sort: String = "views", page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase()
        if (q.isBlank()) {
            return@withContext getDiscoverItems(sort, page)
        }

        // 1. Try querying Addon live search endpoint with sort and page
        val remoteResults = fetchSearchFromAddon(q, sort, page)
        if (remoteResults.isNotEmpty()) {
            return@withContext remoteResults
        }

        // 2. Query offline / local real MissAV database with sorting (page 1 only)
        if (page == 1) {
            val filtered = allVideosMap.values.filter {
                it.code.lowercase().contains(q) ||
                it.title.lowercase().contains(q) ||
                it.slug().contains(q) ||
                it.actresses.any { a -> a.lowercase().contains(q) } ||
                it.genre.lowercase().contains(q)
            }
            sortLocalItems(filtered, sort)
        } else {
            emptyList()
        }
    }

    suspend fun getDiscoverItems(sort: String = "views", page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        // Try live catalog with sort from addon
        val remote = fetchCatalogFromAddonWithSort("missav_uncensored", sort, page)
        if (!remote.isNullOrEmpty()) {
            return@withContext remote
        }
        if (page == 1) {
            sortLocalItems(allVideosMap.values.toList(), sort)
        } else {
            emptyList()
        }
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

        // Auto Quality (Adaptive HLS) FIRST for reliable instant start and dynamic bitrate adaptation
        return listOf(
            StreamQualityOption(
                label = "⚡ Auto Quality (Adaptive HLS)",
                resolution = "Auto",
                streamUrl = "$addonBaseUrl/proxy/m3u8/$uuid/playlist.m3u8",
                headers = headers,
                isDirect = false
            ),
            StreamQualityOption(
                label = "🌟 1080p FHD (Proxied)",
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
                label = "📶 360p Low (Proxied)",
                resolution = "360p",
                streamUrl = "$addonBaseUrl/proxy/m3u8/$uuid/360p/video.m3u8",
                headers = headers,
                isDirect = true
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

    private fun fetchSearchFromAddon(query: String, sort: String = "views", page: Int = 1): List<MediaItem> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val encodedSort = URLEncoder.encode(sort, "UTF-8")
            // Try direct /search endpoint with sort and page
            try {
                val directUrl = URL("$addonBaseUrl/search?q=$encoded&sort=$encodedSort&page=$page")
                val conn = directUrl.openConnection() as HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 6000
                conn.setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val items = parseMetasJson(body)
                    if (items.isNotEmpty()) return items
                }
            } catch (ignored: Throwable) {}

            // Stremio skip fallback: skip = (page - 1) * 12
            val skip = (page - 1) * 12
            val url = URL("$addonBaseUrl/catalog/movie/missav_uncensored/search=$encoded&sort=$encodedSort&skip=$skip.json")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 6000
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

    private fun fetchCatalogFromAddonWithSort(catalogId: String, sort: String, page: Int = 1): List<MediaItem>? {
        return try {
            val encodedSort = URLEncoder.encode(sort, "UTF-8")
            val skip = (page - 1) * 12
            val url = URL("$addonBaseUrl/catalog/movie/$catalogId/sort=$encodedSort&skip=$skip.json")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 6000
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
    }

    suspend fun fetchMetaDetails(type: String, id: String): MetaDetails = withContext(Dispatchers.IO) {
        val cleanSlug = id.replace("missav:", "").trim()
        val local = allVideosMap[cleanSlug]
        val cleanType = type.lowercase().ifBlank { "movie" }

        try {
            val url = URL("$addonBaseUrl/meta/$cleanType/$id.json")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 5000
            conn.setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(body)
                val meta = root.optJSONObject("meta")
                if (meta != null) {
                    val genresList = mutableListOf<String>()
                    val genArr = meta.optJSONArray("genres")
                    if (genArr != null) {
                        for (i in 0 until genArr.length()) genresList.add(genArr.getString(i))
                    }
                    if (genresList.isEmpty() && local != null) {
                        genresList.addAll(local.genres)
                    }

                    val directorsList = mutableListOf<String>()
                    val dirArr = meta.optJSONArray("director")
                    if (dirArr != null) {
                        for (i in 0 until dirArr.length()) directorsList.add(dirArr.getString(i))
                    } else if (meta.optString("director").isNotBlank()) {
                        directorsList.add(meta.optString("director"))
                    }

                    val writersList = mutableListOf<String>()
                    val wrArr = meta.optJSONArray("writer")
                    if (wrArr != null) {
                        for (i in 0 until wrArr.length()) writersList.add(wrArr.getString(i))
                    } else if (meta.optString("writer").isNotBlank()) {
                        writersList.add(meta.optString("writer"))
                    }

                    val castList = mutableListOf<MetaPerson>()
                    val appExtras = meta.optJSONObject("app_extras")
                    val appExtrasCast = appExtras?.optJSONArray("cast")
                    val castArr = appExtrasCast ?: meta.optJSONArray("cast")
                    if (castArr != null) {
                        for (i in 0 until castArr.length()) {
                            val cObj = castArr.optJSONObject(i)
                            if (cObj != null) {
                                castList.add(
                                    MetaPerson(
                                        name = cObj.optString("name", "Cast"),
                                        role = cObj.optString("role", "Cast"),
                                        photo = cObj.optString("photo").takeIf { it.isNotBlank() }
                                    )
                                )
                            } else {
                                val cName = castArr.getString(i)
                                castList.add(MetaPerson(name = cName, role = "Cast"))
                            }
                        }
                    } else if (local != null && local.actresses.isNotEmpty()) {
                        for (act in local.actresses) {
                            castList.add(MetaPerson(name = act, role = "Cast"))
                        }
                    }

                    val trailersList = mutableListOf<MetaTrailer>()
                    val trArr = meta.optJSONArray("trailers")
                    if (trArr != null) {
                        for (i in 0 until trArr.length()) {
                            val tObj = trArr.optJSONObject(i)
                            if (tObj != null) {
                                trailersList.add(
                                    MetaTrailer(
                                        id = tObj.optString("id", "trailer_$i"),
                                        name = tObj.optString("name", "Trailer"),
                                        key = tObj.optString("key", ""),
                                        site = tObj.optString("site", "YouTube"),
                                        type = tObj.optString("type", "Trailer")
                                    )
                                )
                            }
                        }
                    }

                    return@withContext MetaDetails(
                        id = meta.optString("id", id),
                        type = meta.optString("type", cleanType),
                        name = meta.optString("name", local?.title ?: id),
                        poster = meta.optString("poster").takeIf { it.isNotBlank() } ?: local?.posterUrl,
                        background = meta.optString("background").takeIf { it.isNotBlank() } ?: local?.bannerUrl ?: local?.posterUrl,
                        logo = meta.optString("logo").takeIf { it.isNotBlank() },
                        description = meta.optString("description").takeIf { it.isNotBlank() } ?: local?.description,
                        releaseInfo = meta.optString("releaseInfo").takeIf { it.isNotBlank() } ?: local?.year ?: "2024",
                        status = meta.optString("status").takeIf { it.isNotBlank() },
                        imdbRating = meta.optString("imdbRating").takeIf { it.isNotBlank() } ?: local?.rating ?: "4.8",
                        ageRating = meta.optString("ageRating").takeIf { it.isNotBlank() } ?: if (local?.isUncensored == true) "18+" else "PG-13",
                        runtime = meta.optString("runtime").takeIf { it.isNotBlank() } ?: local?.duration ?: "120 min",
                        genres = genresList,
                        director = directorsList,
                        writer = writersList,
                        cast = castList,
                        country = meta.optString("country").takeIf { it.isNotBlank() },
                        language = meta.optString("language").takeIf { it.isNotBlank() },
                        trailers = trailersList
                    )
                }
            }
        } catch (ignored: Throwable) {}

        // Fallback to local item if network fails or meta is empty
        val fallbackCast = local?.actresses?.map { MetaPerson(name = it, role = "Cast") } ?: emptyList()
        MetaDetails(
            id = id,
            type = cleanType,
            name = local?.title ?: id,
            poster = local?.posterUrl,
            background = local?.bannerUrl ?: local?.posterUrl,
            description = local?.description ?: "Official video details for ${local?.title ?: id}",
            releaseInfo = local?.year ?: "2024",
            status = "Released",
            imdbRating = local?.rating ?: "4.8",
            ageRating = if (local?.isUncensored == true) "18+" else "PG-13",
            runtime = local?.duration ?: "120 min",
            genres = local?.genres ?: listOf(local?.genre ?: "Movie"),
            director = emptyList(),
            writer = emptyList(),
            cast = fallbackCast,
            country = "JP",
            language = "JA",
            trailers = emptyList()
        )
    }

    suspend fun fetchStreams(type: String, id: String, item: MediaItem?): List<AddonStreamGroup> = withContext(Dispatchers.IO) {
        val cleanType = type.lowercase().ifBlank { "movie" }
        val streamGroups = mutableListOf<AddonStreamGroup>()

        // 1. Fetch from main addon server
        val mainStreams = mutableListOf<StreamItem>()
        try {
            val url = URL("$addonBaseUrl/stream/$cleanType/$id.json")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 6000
            conn.setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(body)
                val arr = root.optJSONArray("streams")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val sObj = arr.getJSONObject(i)
                        val name = sObj.optString("name", "MissAV [Proxied]")
                        val title = sObj.optString("title", "")
                        val sUrl = sObj.optString("url", "")
                        if (sUrl.isNotBlank()) {
                            val bh = sObj.optJSONObject("behaviorHints")
                            val sizeBytes = bh?.optLong("videoSize")?.takeIf { it > 0 }
                            val explicitRes = sObj.optString("resolution", "")
                            val res = if (explicitRes.isNotBlank()) explicitRes else parseResolution(name, title)
                            val sizeStr = sizeBytes?.let { formatBytes(it) } ?: parseSizeText(title)
                            val isDirect = sUrl.endsWith(".mp4") || sUrl.contains("/download/") || sUrl.contains("/1080p/")

                            mainStreams.add(
                                StreamItem(
                                    name = name,
                                    title = title,
                                    description = sObj.optString("description", title),
                                    url = sUrl,
                                    addonName = "MissAV",
                                    addonId = "missav",
                                    resolution = res,
                                    sizeBytes = sizeBytes,
                                    sizeText = sizeStr,
                                    format = if (sUrl.endsWith(".mp4")) "MP4 Direct" else "HLS Proxied",
                                    codec = if (title.contains("x265", ignoreCase = true) || title.contains("HEVC", ignoreCase = true)) "HEVC" else "x264",
                                    source = "Surrit CDN",
                                    audio = if (title.contains("English", ignoreCase = true)) "English" else "Japanese (Original)",
                                    headers = getStreamHeaders(),
                                    isDirect = isDirect
                                )
                            )
                        }
                    }
                }
            }
        } catch (ignored: Throwable) {}

        // Fallback to local stream options if main addon stream endpoint is empty
        if (mainStreams.isEmpty()) {
            val targetItem = item ?: allVideosMap[id.replace("missav:", "").trim()]
            if (targetItem != null) {
                val options = getStreamOptions(targetItem)
                for (opt in options) {
                    mainStreams.add(
                        StreamItem(
                            name = if (opt.isDirect) "MissAV [Fast Direct]" else "MissAV [Adaptive]",
                            title = "${targetItem.code} ${targetItem.title}\n${opt.label}",
                            description = opt.label,
                            url = opt.streamUrl,
                            addonName = "MissAV",
                            addonId = "missav",
                            resolution = opt.resolution,
                            sizeText = if (opt.resolution.contains("1080")) "6.4 GB" else if (opt.resolution.contains("720")) "3.8 GB" else "2.1 GB",
                            format = if (opt.streamUrl.endsWith(".mp4")) "MP4 Direct" else "HLS Proxied",
                            codec = "x264",
                            source = "MissAV Proxy",
                            audio = "Original Audio",
                            headers = opt.headers,
                            isDirect = opt.isDirect
                        )
                    )
                }
            }
        }

        if (mainStreams.isNotEmpty()) {
            streamGroups.add(
                AddonStreamGroup(
                    addonName = "MissAV",
                    addonId = "missav",
                    streams = mainStreams,
                    isLoading = false
                )
            )
        }

        // 2. Query any additional enabled addons configured in addonSources
        for (source in addonSources) {
            if (source.isBuiltIn || !source.isEnabled) continue
            try {
                val base = source.manifestUrl.removeSuffix("/manifest.json").removeSuffix("/")
                val customUrl = URL("$base/stream/$cleanType/$id.json")
                val conn = customUrl.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 4000
                conn.setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val root = JSONObject(body)
                    val arr = root.optJSONArray("streams")
                    if (arr != null && arr.length() > 0) {
                        val cStreams = mutableListOf<StreamItem>()
                        for (i in 0 until arr.length()) {
                            val sObj = arr.getJSONObject(i)
                            val sUrl = sObj.optString("url", "")
                            if (sUrl.isNotBlank()) {
                                val sName = sObj.optString("name", source.name)
                                val sTitle = sObj.optString("title", "")
                                val explicitRes = sObj.optString("resolution", "")
                                val res = if (explicitRes.isNotBlank()) explicitRes else parseResolution(sName, sTitle)
                                val sizeStr = parseSizeText(sTitle)
                                cStreams.add(
                                    StreamItem(
                                        name = sName,
                                        title = sTitle,
                                        description = sObj.optString("description", sTitle),
                                        url = sUrl,
                                        addonName = source.name,
                                        addonId = source.id,
                                        resolution = res,
                                        sizeText = sizeStr,
                                        headers = getStreamHeaders()
                                    )
                                )
                            }
                        }
                        if (cStreams.isNotEmpty()) {
                            streamGroups.add(
                                AddonStreamGroup(
                                    addonName = source.name,
                                    addonId = source.id,
                                    streams = cStreams,
                                    isLoading = false
                                )
                            )
                        }
                    }
                }
            } catch (ignored: Throwable) {}
        }

        streamGroups
    }

    private fun parseResolution(name: String, title: String): String {
        val nameParts = name.split("\n")
        if (nameParts.size > 1) {
            val part = nameParts[1].lowercase()
            when {
                part.contains("2160") || part.contains("4k") -> return "4K"
                part.contains("1080") || part.contains("fhd") -> return "1080p"
                part.contains("720") || part.contains("hd") -> return "720p"
                part.contains("480") || part.contains("sd") -> return "480p"
                part.contains("360") -> return "360p"
                part.contains("auto") -> return "Auto"
            }
        }
        val combined = "$name $title".lowercase()
        return when {
            combined.contains("2160p") || combined.contains("4k") -> "4K"
            combined.contains("1080p") || combined.contains("1080") || combined.contains("fhd") -> "1080p"
            combined.contains("720p") || combined.contains("720") || combined.contains("hd") -> "720p"
            combined.contains("480p") || combined.contains("480") || combined.contains("sd") -> "480p"
            combined.contains("360p") || combined.contains("360") -> "360p"
            combined.contains("auto") -> "Auto"
            else -> "Auto"
        }
    }

    private fun parseSizeText(title: String): String? {
        val regex = Regex("(\\d+(?:\\.\\d+)?\\s*(?:GB|MB|GiB|MiB))", RegexOption.IGNORE_CASE)
        val match = regex.find(title)
        return match?.value?.uppercase()
    }

    private fun formatBytes(bytes: Long): String {
        return if (bytes >= 1024L * 1024L * 1024L) {
            String.format(java.util.Locale.US, "%.2f GB", bytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
        } else {
            String.format(java.util.Locale.US, "%.1f MB", bytes.toDouble() / (1024.0 * 1024.0))
        }
    }
}
