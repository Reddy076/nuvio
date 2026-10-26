"""
MissAV Scraper Module
Extracts video metadata, catalogs, and streaming playlists (HLS) from MissAV.
Bypasses Cloudflare protections using curl_cffi with Chrome impersonation and session reuse.
"""

import os
import json
import re
import html
import time
import urllib.parse
import logging
from typing import List, Dict, Optional, Tuple
from curl_cffi import requests

logger = logging.getLogger("missav_scraper")
logging.basicConfig(level=logging.INFO)

MIRRORS = [
    "https://missav.ai",
    "https://missav.ws",
    "https://missav.live",
    "https://missav.media",
    "https://missav.fans",
    "https://missav123.com",
]

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.9",
    "Sec-Ch-Ua": '"Chromium";v="124", "Google Chrome";v="124", "Not-A.Brand";v="99"',
    "Sec-Ch-Ua-Mobile": "?0",
    "Sec-Ch-Ua-Platform": '"Windows"',
    "Sec-Fetch-Dest": "document",
    "Sec-Fetch-Mode": "navigate",
    "Sec-Fetch-Site": "none",
    "Sec-Fetch-User": "?1",
    "Upgrade-Insecure-Requests": "1",
    "Referer": "https://missav.ai/",
    "Origin": "https://missav.ai",
}

# Persistent thread-local Session for HTTP/2 & TLS connection reuse across requests
import threading
_local_storage = threading.local()


def get_session() -> requests.Session:
    """Returns a thread-local curl_cffi session with Chrome TLS impersonation."""
    sess = getattr(_local_storage, "session", None)
    if sess is None:
        sess = requests.Session(impersonate="chrome120")
        _local_storage.session = sess
    return sess


def reset_session():
    """Resets the thread-local session if a connection stall or curl error occurs."""
    sess = getattr(_local_storage, "session", None)
    if sess is not None:
        try:
            sess.close()
        except Exception:
            pass
        _local_storage.session = None


# Persistent Disk & In-Memory Cache with TTL
CACHE_DIR = os.path.join(os.path.dirname(__file__), ".cache")
os.makedirs(CACHE_DIR, exist_ok=True)
CACHE_FILE = os.path.join(CACHE_DIR, "data_cache.json")

_cache: Dict[str, Tuple[float, any]] = {}
CACHE_TTL = 1800  # 30 minutes
_meta_store: Dict[str, Dict] = {}


def load_disk_cache():
    """Loads cached catalogs and metadata from persistent storage."""
    global _cache, _meta_store
    if os.path.exists(CACHE_FILE):
        try:
            with open(CACHE_FILE, "r", encoding="utf-8") as f:
                saved = json.load(f)
                _cache = saved.get("cache", {})
                _meta_store = saved.get("meta_store", {})
                logger.info(f"Loaded disk cache: {len(_cache)} entries, {len(_meta_store)} metas.")
        except Exception as e:
            logger.warning(f"Error loading disk cache: {e}")


def save_disk_cache():
    """Saves cached catalogs and metadata to persistent disk storage."""
    try:
        with open(CACHE_FILE, "w", encoding="utf-8") as f:
            json.dump({"cache": _cache, "meta_store": _meta_store}, f)
    except Exception as e:
        logger.warning(f"Error saving disk cache: {e}")


load_disk_cache()


def _get_cache(key: str, allow_stale: bool = False):
    if key in _cache:
        timestamp, data = _cache[key]
        if (time.time() - timestamp < CACHE_TTL) or allow_stale:
            return data
    return None


def _set_cache(key: str, data: any):
    _cache[key] = (time.time(), data)
    save_disk_cache()


def baseN(num: int, b: int) -> str:
    """Converts a number to a base-b string (supports up to base 62)."""
    digits = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    if num == 0:
        return "0"
    res = []
    while num > 0:
        res.append(digits[num % b])
        num //= b
    return "".join(reversed(res))


def unpack_packer(p: str, a: int, c: int, k: List[str]) -> str:
    """Deobfuscates Dean Edwards' Packer payload."""
    d = {}
    while c > 0:
        c -= 1
        key = baseN(c, a)
        d[key] = k[c] if c < len(k) and k[c] else key
    return re.sub(r'\b\w+\b', lambda m: d.get(m.group(0), m.group(0)), p)


def extract_packer_blocks(page_html: str) -> List[str]:
    """Extracts and unpacks all packed JS blocks on the page."""
    pattern = r"eval\(function\(p,a,c,k,e,d\)\{.*?\}\('(.*?)',(\d+),(\d+),'(.*?)'\.split\('\|'\)"
    matches = re.findall(pattern, page_html, re.DOTALL)
    unpacked_list = []
    for p, a, c, k_str in matches:
        try:
            k = k_str.split('|')
            unpacked = unpack_packer(p, int(a), int(c), k)
            unpacked_list.append(unpacked)
        except Exception:
            pass
    return unpacked_list


def parse_video_cards(page_html: str) -> List[Dict]:
    """
    Parses video cards from a MissAV HTML page.
    Saves metadata to _meta_store for instant <1ms /meta responses.
    """
    items = []
    seen = set()

    # Pattern targeting lozad thumbnail image cards with any CDN (fourhoi.com, fourhoi.mrstcdn.store, etc.)
    img_pattern = r'data-src="([^"]*?/([a-zA-Z0-9_-]+)/cover-[tn]\.jpg)"[\s\S]*?alt="([^"]*)"'
    matches = re.findall(img_pattern, page_html)

    for full_img_url, slug, alt in matches:
        if slug in seen:
            continue
        seen.add(slug)

        title = html.unescape(alt).strip()
        if not title:
            title = format_slug_title(slug)

        poster = f"https://fourhoi.com/{slug}/cover-n.jpg"
        thumbnail = f"https://fourhoi.com/{slug}/cover-t.jpg"

        # Pre-cache in _meta_store so details load with zero network delay
        _meta_store[slug] = {
            "slug": slug,
            "title": title,
            "poster": poster,
            "thumbnail": thumbnail,
        }

        items.append({
            "id": f"missav:{slug}",
            "slug": slug,
            "name": title,
            "poster": poster,
            "thumbnail": thumbnail,
            "type": "movie",
            "description": f"MissAV Video: {title}",
        })

    return items


def format_slug_title(slug: str) -> str:
    """Formats a clean title from a video slug e.g. 'snos-359-uncensored-leak' -> 'SNOS-359 (Uncensored Leak)'."""
    parts = slug.split("-")
    if len(parts) >= 2 and parts[0].isalpha() and parts[1].isdigit():
        code = f"{parts[0].upper()}-{parts[1]}"
        rest = " ".join(p.capitalize() for p in parts[2:])
        return f"{code} ({rest})" if rest else code
    return slug.upper().replace("-", " ")


def get_video_meta(slug: str, media_type: str = "movie") -> Dict:
    """
    Returns complete, instant (<1ms) metadata for Stremio / Nuvio.
    Never fails or times out, guaranteeing the details screen always loads cleanly.
    """
    clean_slug = slug.replace("missav:", "").strip()
    clean_slug = re.sub(r'(:\d+)+$', '', clean_slug).strip("/")

    # 1. Check in-memory card store (populated by catalogs & search)
    cached_info = _meta_store.get(clean_slug)
    video_cache = _get_cache(f"video:{clean_slug}")

    if video_cache and video_cache.get("title"):
        title = video_cache["title"]
        poster = video_cache.get("poster") or f"https://fourhoi.com/{clean_slug}/cover-n.jpg"
    elif cached_info:
        title = cached_info["title"]
        poster = cached_info["poster"]
    else:
        title = format_slug_title(clean_slug)
        poster = f"https://fourhoi.com/{clean_slug}/cover-n.jpg"

    meta_obj = {
        "id": f"missav:{clean_slug}",
        "type": media_type,
        "name": title,
        "poster": poster,
        "background": poster,
        "logo": "https://missav.ws/img/flags/united-kingdom.png",
        "description": f"MissAV Video: {title}",
        "genres": ["MissAV", "Japanese AV", "Uncensored"],
        "releaseInfo": "2024",
        "runtime": "120 min",
    }

    if media_type in ["series", "tv"]:
        meta_obj["videos"] = [
            {
                "id": f"missav:{clean_slug}:1:1",
                "title": title,
                "season": 1,
                "episode": 1,
                "released": "2024-01-01T00:00:00.000Z",
                "thumbnail": poster,
            }
        ]
    else:
        meta_obj["behaviorHints"] = {
            "defaultVideoId": f"missav:{clean_slug}",
        }

    return meta_obj


def fetch_with_fallback(urls: List[str], retries_per_url: int = 1) -> str:
    """Tries fetching multiple fallback URLs with session reuse and quick 4s timeout per mirror."""
    sess = get_session()
    for url in urls:
        for attempt in range(retries_per_url):
            try:
                parsed = urllib.parse.urlparse(url)
                origin = f"{parsed.scheme}://{parsed.netloc}"
                req_headers = dict(HEADERS)
                req_headers["Referer"] = f"{origin}/"
                req_headers["Origin"] = origin
                r = sess.get(url, headers=req_headers, timeout=4)
                if r.status_code == 200 and len(r.text) > 1000 and "Just a moment..." not in r.text:
                    return r.text
            except Exception as e:
                logger.debug(f"Attempt failed for {url}: {e}")
                reset_session()
                sess = get_session()
                time.sleep(0.05)
    return ""


SORT_TAG_MAP = {
    "t": "today_views",
    "today": "today_views",
    "today_views": "today_views",
    "m": "monthly_views",
    "month": "monthly_views",
    "monthly": "monthly_views",
    "monthly_views": "monthly_views",
    "w": "weekly_views",
    "week": "weekly_views",
    "weekly": "weekly_views",
    "weekly_views": "weekly_views",
    "r": "released_at",
    "rel": "released_at",
    "release": "released_at",
    "released": "released_at",
    "released_at": "released_at",
    "new": "published_at",
    "recent": "published_at",
    "published": "published_at",
    "published_at": "published_at",
    "v": "views",
    "view": "views",
    "views": "views",
    "total": "views",
}


def parse_search_query_modifiers(raw_query: str) -> Tuple[str, str, int]:
    """
    Parses human search queries for sort flags and batch numbers.
    E.g.:
      'Yua Mikami' -> ('Yua Mikami', 'views', 1)
      'Yua Mikami 2' -> ('Yua Mikami', 'views', 2)
      'Yua Mikami t' -> ('Yua Mikami', 'today_views', 1)
      'Yua Mikami t 2' -> ('Yua Mikami', 'today_views', 2)
      'Yua Mikami m' -> ('Yua Mikami', 'monthly_views', 1)
      'SSIS-062' -> ('SSIS-062', 'views', 1)
    """
    q = (raw_query or "").strip()
    if not q:
        return "", "views", 1

    # Check if the entire query looks like a standard JAV video code with 3+ digits (e.g. SSIS-062, IPX 123, FC2-PPV-1234567)
    if re.match(r"^[A-Za-z]{2,8}[-_ ]?\d{3,7}$", q, re.IGNORECASE):
        return q, "views", 1

    tokens = q.split()
    if not tokens:
        return "", "views", 1

    sort_found = None
    batch_found = 1
    consumed_indices = set()

    # Inspect up to the last two tokens for modifiers (e.g. 'Yua Mikami t 2' or 'Yua Mikami 2' or 'Yua Mikami m')
    for idx in range(len(tokens) - 1, max(-1, len(tokens) - 3), -1):
        tok = tokens[idx].lower().strip()

        # Check for explicit page syntax: p2, page2, page:2, #2
        p_match = re.match(r"^(?:p|page:?|#)(\d+)$", tok)
        if p_match and batch_found == 1:
            batch_found = max(1, int(p_match.group(1)))
            consumed_indices.add(idx)
            continue

        # Check for single digit batch (1-9)
        if tok.isdigit() and len(tok) == 1 and batch_found == 1:
            batch_found = max(1, int(tok))
            consumed_indices.add(idx)
            continue

        # Check for sort tag
        clean_tag = tok.lstrip("#").lstrip(":")
        if clean_tag in SORT_TAG_MAP and sort_found is None:
            sort_found = SORT_TAG_MAP[clean_tag]
            consumed_indices.add(idx)
            continue

    remaining_tokens = [t for i, t in enumerate(tokens) if i not in consumed_indices]
    clean_query = " ".join(remaining_tokens).strip()

    if not clean_query:
        clean_query = q
        sort_found = "views"
        batch_found = 1

    final_sort = sort_found if sort_found else "views"
    return clean_query, final_sort, batch_found


SORT_MAP = {
    "views": "views",
    "total_views": "views",
    "popular": "views",
    "monthly_views": "monthly_views",
    "monthly": "monthly_views",
    "month": "monthly_views",
    "this_month": "monthly_views",
    "weekly_views": "weekly_views",
    "weekly": "weekly_views",
    "week": "weekly_views",
    "this_week": "weekly_views",
    "today_views": "today_views",
    "today": "today_views",
    "saved": "saved",
    "most_saved": "saved",
    "favorites": "saved",
    "published_at": "published_at",
    "recent": "published_at",
    "new": "published_at",
    "released_at": "released_at",
    "release": "released_at",
}


def fetch_catalog(category: str, page: int = 1, sort: Optional[str] = None) -> List[Dict]:
    """
    Fetches catalog list for a given category and page with automatic mirror fallback.
    Supports sorting: views, monthly_views, weekly_views, today_views, saved, published_at, released_at.
    Returns persistent/stale cached items immediately (<5ms) to guarantee Nuvio never stalls on startup.
    """
    norm_sort = SORT_MAP.get(sort.lower().replace("-", "_").replace(" ", "_"), sort) if sort else None
    cache_key = f"catalog:{category}:{norm_sort or 'default'}:{page}"
    cached = _get_cache(cache_key)
    if cached:
        return cached

    # Fast path: return existing disk cache immediately so app starts in <1 second
    if not norm_sort:
        stale = _get_cache(cache_key, allow_stale=True)
        if stale:
            return stale

    cat_map = {
        "missav_uncensored": "uncensored-leak",
        "missav_new": "new",
        "missav_release": "release",
        "missav_chinese": "chinese-subtitle",
        "missav_uncensored_series": "uncensored-leak",
        "missav_new_series": "new",
        "missav_release_series": "release",
        "missav_chinese_series": "chinese-subtitle",
    }

    subpath = cat_map.get(category, category)
    params = []
    if norm_sort:
        params.append(f"sort={norm_sort}")
    if page > 1:
        params.append(f"page={page}")
    query_str = ("?" + "&".join(params)) if params else ""

    urls = [
        f"{m}/en/{subpath}{query_str}" for m in MIRRORS
    ]

    html_content = fetch_with_fallback(urls)
    if html_content:
        items = parse_video_cards(html_content)
        _set_cache(cache_key, items)
        return items

    logger.error(f"Failed to fetch catalog {category} page {page} sort {sort}")
    return []


def _fetch_search_page_with_session(
    sess: requests.Session,
    clean_query: str,
    page: int = 1,
    norm_sort: str = "views",
    preferred_endpoint: str = "search",
) -> Tuple[List[Dict], str]:
    """
    Fetches a single search page reusing the session with fast mirror fallback.
    Returns (items, endpoint_used).
    """
    params = []
    if norm_sort:
        params.append(f"sort={norm_sort}")
    if page > 1:
        params.append(f"page={page}")
    query_str = ("?" + "&".join(params)) if params else ""
    encoded_query = urllib.parse.quote(clean_query)

    endpoints_to_try = [preferred_endpoint]
    if preferred_endpoint == "search":
        endpoints_to_try.append("actresses")
    else:
        endpoints_to_try.append("search")

    for ep in endpoints_to_try:
        urls = [f"{m}/en/{ep}/{encoded_query}{query_str}" for m in MIRRORS]
        for url in urls:
            try:
                parsed = urllib.parse.urlparse(url)
                origin = f"{parsed.scheme}://{parsed.netloc}"
                req_headers = dict(HEADERS)
                req_headers["Referer"] = f"{origin}/"
                req_headers["Origin"] = origin
                r = sess.get(url, headers=req_headers, timeout=4)
                if r.status_code == 200 and len(r.text) > 1000 and "Just a moment..." not in r.text:
                    items = parse_video_cards(r.text)
                    if items or page > 1:
                        return items, ep
            except Exception as e:
                logger.debug(f"Search fetch failed for {url}: {e}")
                time.sleep(0.05)

    return [], preferred_endpoint


def search_videos(
    query: str,
    page: int = 1,
    sort: str = "views",
    batch: int = 1,
    batch_mode: bool = True,
    batch_size: int = 5,
) -> List[Dict]:
    """
    Searches MissAV for titles or actress names matching query with automatic mirror and actress fallback.
    If batch_mode=True, retrieves up to `batch_size` pages (default 5 = ~60 items) in one response,
    allowing smooth deep-scrolling in official Nuvio.
    Supports sort filters: views, monthly_views, weekly_views, today_views, saved, published_at, released_at.
    """
    clean_query = urllib.parse.unquote(query.strip())
    if not clean_query:
        return []

    norm_sort = SORT_MAP.get(sort.lower().replace("-", "_").replace(" ", "_"), "views") if sort else "views"

    # Single-page mode (for skip offset or manual single-page calls)
    if not batch_mode:
        cache_key = f"search:{clean_query.lower()}:{norm_sort}:{page}"
        cached = _get_cache(cache_key)
        if cached:
            return cached

        sess = get_session()
        items, _ = _fetch_search_page_with_session(sess, clean_query, page=page, norm_sort=norm_sort)
        if not items and page == 1:
            single_slug = urllib.parse.quote(clean_query).lower()
            single_info = get_video_stream_info(single_slug)
            if single_info:
                items = [{
                    "id": f"missav:{single_info['slug']}",
                    "slug": single_info["slug"],
                    "name": single_info["title"],
                    "poster": single_info["poster"],
                    "thumbnail": single_info["poster"],
                    "type": "movie",
                    "description": f"MissAV Video: {single_info['title']}",
                }]
        if items:
            _set_cache(cache_key, items)
            return items
        return []

    # Multi-page Batch mode (default for Nuvio search)
    batch_cache_key = f"search_batch:{clean_query.lower()}:{norm_sort}:{batch}:{batch_size}"
    cached_batch = _get_cache(batch_cache_key)
    if cached_batch:
        return cached_batch

    start_page = (batch - 1) * batch_size + 1
    end_page = start_page + batch_size

    all_items = []
    seen_ids = set()
    endpoint_type = "search"

    sess = get_session()
    for p in range(start_page, end_page):
        p_items, detected_endpoint = _fetch_search_page_with_session(
            sess, clean_query, page=p, norm_sort=norm_sort, preferred_endpoint=endpoint_type
        )
        if detected_endpoint:
            endpoint_type = detected_endpoint

        for it in p_items:
            if it["id"] not in seen_ids:
                seen_ids.add(it["id"])
                all_items.append(it)

        # If a page returns fewer than 12 cards, no more pages exist
        if len(p_items) < 12:
            break

    # If still empty on batch 1, check direct video slug (e.g. nhdtb-213)
    if not all_items and batch == 1:
        single_slug = urllib.parse.quote(clean_query).lower()
        single_info = get_video_stream_info(single_slug)
        if single_info:
            all_items = [{
                "id": f"missav:{single_info['slug']}",
                "slug": single_info["slug"],
                "name": single_info["title"],
                "poster": single_info["poster"],
                "thumbnail": single_info["poster"],
                "type": "movie",
                "description": f"MissAV Video: {single_info['title']}",
            }]

    if all_items:
        _set_cache(batch_cache_key, all_items)
        return all_items

    logger.warning(f"No results found for search query: {query} (sort: {norm_sort}, batch: {batch})")
    return []


def get_video_stream_info(slug: str) -> Optional[Dict]:
    """
    Fetches video page, unpacks stream script, and extracts UUID + playlist URLs.
    Called when resolving streams for playback.
    """
    clean_slug = slug.replace("missav:", "").strip()
    clean_slug = re.sub(r'(:\d+)+$', '', clean_slug).strip("/")

    cache_key = f"video:{clean_slug}"
    cached = _get_cache(cache_key)
    if cached:
        return cached

    urls = [
        f"{m}/en/{clean_slug}" for m in MIRRORS
    ]

    page_html = fetch_with_fallback(urls)
    if not page_html:
        logger.error(f"Could not load page for slug {clean_slug}")
        return None

    # Extract title
    title_match = re.search(r'<h1[^>]*class="[^"]*text-base[^"]*"[^>]*>(.*?)</h1>', page_html, re.DOTALL)
    title = html.unescape(title_match.group(1)).strip() if title_match else format_slug_title(clean_slug)

    # Extract UUID
    uuid = None

    # 1. Unpacked JS check (match stream URL first, ignoring preview UUIDs)
    unpacked_blocks = extract_packer_blocks(page_html)
    for block in unpacked_blocks:
        match = re.search(r'https?://[^/]+/([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})/(?:playlist|\d+p)', block)
        if match:
            uuid = match.group(1)
            break
        match_surrit = re.search(r'([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})/(?:playlist|\d+p)', block)
        if match_surrit:
            uuid = match_surrit.group(1)
            break

    # 2. Raw HTML check for video stream URL (not preview)
    if not uuid:
        match = re.search(r'https?://(?:surrit\.com|surrit\.mrstcdn\.store)/([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})', page_html)
        if match:
            uuid = match.group(1)

    # 3. Fallback to any UUID in packed blocks
    if not uuid:
        for block in unpacked_blocks:
            uuids = re.findall(r'[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}', block)
            if uuids:
                uuid = uuids[0]
                break

    if not uuid:
        logger.error(f"Failed to find UUID for slug {clean_slug}")
        return None

    poster = f"https://fourhoi.com/{clean_slug}/cover-n.jpg"

    info = {
        "slug": clean_slug,
        "title": title,
        "uuid": uuid,
        "poster": poster,
        "master_url": f"https://surrit.com/{uuid}/playlist.m3u8",
        "url_1080p": f"https://surrit.com/{uuid}/1080p/video.m3u8",
        "url_720p": f"https://surrit.com/{uuid}/720p/video.m3u8",
        "url_480p": f"https://surrit.com/{uuid}/480p/video.m3u8",
    }

    # Also record in _meta_store
    _meta_store[clean_slug] = {
        "slug": clean_slug,
        "title": title,
        "poster": poster,
        "thumbnail": poster,
    }

    _set_cache(cache_key, info)
    return info


def get_available_variants(uuid: str) -> List[Dict[str, any]]:
    """
    Parses playlist.m3u8 on origin CDNs and returns all available stream variants,
    supporting both standard shorthand ('1080p/video.m3u8') and explicit dimension
    folders ('1280x720/video.m3u8', '842x480/video.m3u8', '640x360/video.m3u8').
    Each variant dict contains:
      - 'quality': '1080p', '720p', '480p', or '360p'
      - 'path': actual subpath on Surrit, e.g. '1280x720/video.m3u8'
      - 'height': integer height (e.g. 720)
      - 'bandwidth': integer bandwidth
    """
    cache_key = f"variants:{uuid}"
    cached = _get_cache(cache_key)
    if cached:
        return cached

    headers = {
        "User-Agent": HEADERS["User-Agent"],
        "Referer": "https://missav.ws/",
        "Origin": "https://missav.ws",
        "Accept": "*/*",
    }

    sess = get_session()
    playlist_text = ""
    for domain in ["surrit.com", "surrit.mrstcdn.store"]:
        url = f"https://{domain}/{uuid}/playlist.m3u8"
        try:
            r = sess.get(url, headers=headers, timeout=8)
            if r.status_code == 200 and "#EXTM3U" in r.text:
                playlist_text = r.text
                break
        except Exception as e:
            logger.debug(f"Failed to check playlist on {domain} for {uuid}: {e}")

    variants = []
    seen_qualities = set()

    if playlist_text:
        lines = playlist_text.splitlines()
        current_inf = None
        for line in lines:
            trimmed = line.strip()
            if not trimmed or trimmed.startswith("#EXT-X-TOKEN"):
                continue
            if trimmed.startswith("#EXT-X-STREAM-INF"):
                current_inf = trimmed
            elif current_inf and not trimmed.startswith("#"):
                uri = trimmed
                res_match = re.search(r'RESOLUTION=(\d+)x(\d+)', current_inf)
                height = int(res_match.group(2)) if res_match else None
                if not height:
                    if "1080" in uri:
                        height = 1080
                    elif "720" in uri:
                        height = 720
                    elif "480" in uri:
                        height = 480
                    elif "360" in uri:
                        height = 360

                if height:
                    if height >= 1000:
                        label = "1080p"
                    elif height >= 700:
                        label = "720p"
                    elif height >= 450:
                        label = "480p"
                    else:
                        label = "360p"
                else:
                    label = "720p"

                bw_match = re.search(r'BANDWIDTH=(\d+)', current_inf)
                bw = int(bw_match.group(1)) if bw_match else 0

                variants.append({
                    "quality": label,
                    "path": uri,
                    "height": height or 0,
                    "bandwidth": bw
                })
                seen_qualities.add(label)
                current_inf = None

    # Check if 1080p is directly accessible even if missing from master playlist
    if "1080p" not in seen_qualities:
        for domain in ["surrit.com", "surrit.mrstcdn.store"]:
            for candidate_1080 in ["1080p/video.m3u8", "1920x1080/video.m3u8"]:
                try:
                    r = sess.head(f"https://{domain}/{uuid}/{candidate_1080}", headers=headers, timeout=4)
                    if r.status_code == 200:
                        variants.append({
                            "quality": "1080p",
                            "path": candidate_1080,
                            "height": 1080,
                            "bandwidth": 8000000
                        })
                        seen_qualities.add("1080p")
                        break
                except Exception:
                    pass
            if "1080p" in seen_qualities:
                break

    if variants:
        variants.sort(key=lambda x: (x["height"], x["bandwidth"]), reverse=True)
        _set_cache(cache_key, variants)
        return variants

    # Default fallback if network was temporarily unreachable
    return [
        {"quality": "1080p", "path": "1080p/video.m3u8", "height": 1080, "bandwidth": 8000000},
        {"quality": "720p", "path": "720p/video.m3u8", "height": 720, "bandwidth": 4000000},
        {"quality": "480p", "path": "480p/video.m3u8", "height": 480, "bandwidth": 2000000},
        {"quality": "360p", "path": "360p/video.m3u8", "height": 360, "bandwidth": 1000000},
    ]


def get_available_qualities(uuid: str) -> List[str]:
    """
    Checks origin CDNs for a given UUID and returns available quality names
    sorted descending (e.g. ['1080p', '720p', '480p', '360p']).
    """
    variants = get_available_variants(uuid)
    return [v["quality"] for v in variants]


def fetch_surrit_segment(uuid: str, path: str, range_header: Optional[str] = None, is_head: bool = False):
    """
    Fetches an MPEG-TS segment from origin CDN with automatic dimension alias resolution
    (e.g. 720p <-> 1280x720, 480p <-> 842x480/854x480, 360p <-> 640x360),
    automatic quality fallback (1080p -> 720p -> 480p -> 360p), and multi-CDN support.
    Maps .ts back to .jpeg for origin.
    """
    surrit_path = path[:-3] + ".jpeg" if path.endswith(".ts") else path

    headers = {
        "User-Agent": HEADERS["User-Agent"],
        "Referer": "https://missav.ws/",
        "Origin": "https://missav.ws",
        "Accept": "*/*",
    }
    if range_header:
        headers["Range"] = range_header

    # Dimension and shorthand folder aliases
    aliases = {
        "1080p/": ["1920x1080/"],
        "1920x1080/": ["1080p/"],
        "720p/": ["1280x720/"],
        "1280x720/": ["720p/"],
        "480p/": ["842x480/", "854x480/"],
        "842x480/": ["480p/", "854x480/"],
        "854x480/": ["480p/", "842x480/"],
        "360p/": ["640x360/"],
        "640x360/": ["360p/"],
    }
    paths_to_try = [surrit_path]
    for prefix, alt_prefixes in aliases.items():
        if surrit_path.startswith(prefix):
            for alt_p in alt_prefixes:
                alt = surrit_path.replace(prefix, alt_p, 1)
                if alt not in paths_to_try:
                    paths_to_try.append(alt)
            break

    # Resolution downscale fallbacks (1080 -> 720 -> 480 -> 360)
    downscale_chain = [
        (["1080p/", "1920x1080/"], ["720p/", "1280x720/"]),
        (["720p/", "1280x720/"], ["480p/", "842x480/", "854x480/"]),
        (["480p/", "842x480/", "854x480/"], ["360p/", "640x360/"])
    ]
    for from_list, to_list in downscale_chain:
        for p in list(paths_to_try):
            for f in from_list:
                if p.startswith(f):
                    for t in to_list:
                        new_p = p.replace(f, t, 1)
                        if new_p not in paths_to_try:
                            paths_to_try.append(new_p)

    sess = get_session()
    for current_path in paths_to_try:
        for domain in ["surrit.com", "surrit.mrstcdn.store"]:
            surrit_url = f"https://{domain}/{uuid}/{current_path}"
            for attempt in range(2):
                try:
                    if is_head:
                        r = sess.head(surrit_url, headers=headers, timeout=6)
                    else:
                        r = sess.get(surrit_url, headers=headers, timeout=8)

                    if r.status_code in [200, 206]:
                        return r
                    elif r.status_code == 404:
                        # File does not exist on this domain/path; try next path candidate
                        break
                    elif r.status_code == 403:
                        reset_session()
                        sess = get_session()
                        time.sleep(0.1)
                except Exception:
                    time.sleep(0.1)

    return None

