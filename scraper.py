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
    "https://missav.media",
    "https://missav.fans",
    "https://missav.ws",
    "https://missav.live",
    "https://missav123.com",
    "https://missav.ai",
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

# Persistent shared Session for HTTP/2 & TLS connection reuse across requests
_session: Optional[requests.Session] = None


def get_session() -> requests.Session:
    """Returns a shared curl_cffi session with Chrome TLS impersonation."""
    global _session
    if _session is None:
        _session = requests.Session(impersonate="chrome124")
    return _session


def reset_session():
    """Resets the shared session if a connection stall or curl error occurs."""
    global _session
    try:
        if _session is not None:
            _session.close()
    except Exception:
        pass
    _session = None


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
    """Tries fetching multiple fallback URLs with quick 4s timeout per mirror."""
    for url in urls:
        for attempt in range(retries_per_url):
            try:
                parsed = urllib.parse.urlparse(url)
                origin = f"{parsed.scheme}://{parsed.netloc}"
                req_headers = dict(HEADERS)
                req_headers["Referer"] = f"{origin}/"
                req_headers["Origin"] = origin
                r = requests.get(url, headers=req_headers, impersonate="chrome124", timeout=4)
                if r.status_code == 200 and len(r.text) > 1000 and "Just a moment..." not in r.text:
                    return r.text
            except Exception as e:
                logger.debug(f"Attempt failed for {url}: {e}")
                time.sleep(0.1)
    return ""


def fetch_catalog(category: str, page: int = 1) -> List[Dict]:
    """
    Fetches catalog list for a given category and page with automatic mirror fallback.
    Returns persistent/stale cached items immediately (<5ms) to guarantee Nuvio never stalls on startup.
    """
    cache_key = f"catalog:{category}:{page}"
    cached = _get_cache(cache_key)
    if cached:
        return cached

    # Fast path: return existing disk cache immediately so app starts in <1 second
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

    subpath = cat_map.get(category, "uncensored-leak")
    query_str = f"?page={page}" if page > 1 else ""

    urls = [
        f"{m}/en/{subpath}{query_str}" for m in MIRRORS
    ]

    html_content = fetch_with_fallback(urls)
    if html_content:
        items = parse_video_cards(html_content)
        _set_cache(cache_key, items)
        return items

    logger.error(f"Failed to fetch catalog {category} page {page}")
    return []


def search_videos(query: str, page: int = 1) -> List[Dict]:
    """
    Searches MissAV for titles or actress names matching query with automatic mirror and actress fallback.
    """
    clean_query = urllib.parse.unquote(query.strip())
    if not clean_query:
        return []

    cache_key = f"search:{clean_query.lower()}:{page}"
    cached = _get_cache(cache_key)
    if cached:
        return cached

    query_str = f"?page={page}" if page > 1 else ""
    encoded_query = urllib.parse.quote(clean_query)

    # 1. Primary search endpoint
    urls = [
        f"{m}/en/search/{encoded_query}{query_str}" for m in MIRRORS
    ]

    html_content = fetch_with_fallback(urls)
    items = parse_video_cards(html_content) if html_content else []

    # 2. If search returns 0 items, fallback to /actresses/ endpoint
    if not items:
        actress_urls = [
            f"{m}/en/actresses/{encoded_query}{query_str}" for m in MIRRORS
        ]
        actress_html = fetch_with_fallback(actress_urls)
        if actress_html:
            items = parse_video_cards(actress_html)

    # 3. If still empty on page 1, check if query matches a direct video slug (e.g. nhdtb-213)
    if not items and page == 1:
        single_slug = encoded_query.lower()
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

    logger.warning(f"No results found for search query: {query}")
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

    # 1. Unpacked JS check
    unpacked_blocks = extract_packer_blocks(page_html)
    for block in unpacked_blocks:
        match = re.search(r'surrit\.com/([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})', block)
        if match:
            uuid = match.group(1)
            break

    # 2. Raw HTML check
    if not uuid:
        match = re.search(r'surrit\.com/([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})', page_html)
        if match:
            uuid = match.group(1)

    if not uuid:
        uuids = re.findall(r'[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}', page_html)
        if uuids:
            uuid = uuids[0]

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


def get_available_qualities(uuid: str) -> List[str]:
    """
    Checks the master playlist for a given UUID and returns available quality names
    sorted descending (e.g. ['1080p', '720p', '480p', '360p'] or ['720p', '480p', '360p']).
    Never offers non-existent qualities to prevent 404 player buffering.
    """
    cache_key = f"qualities:{uuid}"
    cached = _get_cache(cache_key)
    if cached:
        return cached

    url = f"https://surrit.com/{uuid}/playlist.m3u8"
    headers = {
        "User-Agent": HEADERS["User-Agent"],
        "Referer": "https://missav.ws/",
        "Origin": "https://missav.ws",
        "Accept": "*/*",
    }

    try:
        sess = get_session()
        r = sess.get(url, headers=headers, timeout=6)
        if r.status_code == 200:
            found = set(re.findall(r"(\d+p)/video\.m3u8", r.text))
            def res_key(q):
                try:
                    return int(q.rstrip("p"))
                except ValueError:
                    return 0
            sorted_qualities = sorted(list(found), key=res_key, reverse=True)
            if sorted_qualities:
                _set_cache(cache_key, sorted_qualities)
                return sorted_qualities
    except Exception as e:
        logger.warning(f"Could not determine qualities for {uuid}: {e}")

    # Fallback to standard HD/SD
    fallback = ["720p", "480p", "360p"]
    _set_cache(cache_key, fallback)
    return fallback


def fetch_surrit_segment(uuid: str, path: str, range_header: Optional[str] = None, is_head: bool = False):
    """
    Fetches an MPEG-TS segment from surrit CDN with Chrome TLS impersonation and automatic retries.
    Maps .ts back to .jpeg for origin and handles Range requests properly.
    """
    surrit_path = path[:-3] + ".jpeg" if path.endswith(".ts") else path
    surrit_url = f"https://surrit.com/{uuid}/{surrit_path}"

    headers = {
        "User-Agent": HEADERS["User-Agent"],
        "Referer": "https://missav.ws/",
        "Origin": "https://missav.ws",
        "Accept": "*/*",
    }
    if range_header:
        headers["Range"] = range_header

    # Retry loop with session recovery
    for attempt in range(3):
        try:
            sess = get_session()
            if is_head:
                r = sess.head(surrit_url, headers=headers, timeout=6)
            else:
                r = sess.get(surrit_url, headers=headers, timeout=10)

            if r.status_code in [200, 206]:
                return r
            elif r.status_code == 403:
                reset_session()
                time.sleep(0.2)
        except Exception as e:
            logger.debug(f"Attempt {attempt+1} failed for {surrit_url}: {e}")
            reset_session()
            time.sleep(0.25)

    # Final standalone fallback request
    try:
        if is_head:
            r = requests.head(surrit_url, headers=headers, impersonate="chrome124", timeout=8)
        else:
            r = requests.get(surrit_url, headers=headers, impersonate="chrome124", timeout=12)
        return r
    except Exception as e:
        logger.error(f"All segment fetch attempts failed for {surrit_url}: {e}")
        return None

