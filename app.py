"""
MissAV Nuvio / Stremio Addon Server
Provides Stremio Addon Protocol v3 endpoints and a high-performance HLS proxy.
"""

import re
import os
import time
import urllib.parse
from fastapi import FastAPI, Request, Response, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, HTMLResponse
import scraper

LOG_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "access.log")

app = FastAPI(title="MissAV Nuvio Addon", version="1.2.0")

# Enable CORS for all domains so Nuvio / Stremio web & mobile can access
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.middleware("http")
async def request_logging_middleware(request: Request, call_next):
    """Normalizes duplicate slashes and logs every request for real-time monitoring."""
    raw_path = request.scope.get("path", "/")
    normalized = re.sub(r"/+", "/", raw_path)
    if normalized != raw_path:
        request.scope["path"] = normalized

    t0 = time.time()
    response = await call_next(request)
    dt = time.time() - t0

    try:
        with open(LOG_FILE, "a", encoding="utf-8") as f:
            f.write(f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] {request.method} {normalized} -> {response.status_code} ({dt:.3f}s)\n")
    except Exception:
        pass

    return response


MANIFEST = {
    "id": "community.missav",
    "version": "1.3.0",
    "name": "MissAV",
    "description": "Watch MissAV releases, uncensored videos, and search directly in Nuvio",
    "logo": "https://missav.ws/img/flags/united-kingdom.png",
    "background": "https://fourhoi.com/nhdtb-213-uncensored-leak/cover-n.jpg",
    "resources": [
        "catalog",
        {
            "name": "meta",
            "types": ["movie", "series", "other"],
            "idPrefixes": ["missav:"],
        },
        {
            "name": "stream",
            "types": ["movie", "series", "other"],
            "idPrefixes": ["missav:"],
        },
    ],
    "types": ["movie", "series", "other"],
    "idPrefixes": ["missav:"],
    "behaviorHints": {
        "configurable": False,
        "adult": False,
        "p2p": False,
    },
    "catalogs": [
        {
            "type": "movie",
            "id": "missav_uncensored",
            "name": "MissAV Uncensored Leak",
            "extra": [
                {"name": "search", "isRequired": False},
                {"name": "skip", "isRequired": False},
            ],
            "extraSupported": ["search", "skip"],
        },
        {
            "type": "movie",
            "id": "missav_release",
            "name": "MissAV Recent Releases",
            "extra": [
                {"name": "search", "isRequired": False},
                {"name": "skip", "isRequired": False},
            ],
            "extraSupported": ["search", "skip"],
        },
        {
            "type": "movie",
            "id": "missav_new",
            "name": "MissAV New",
            "extra": [
                {"name": "search", "isRequired": False},
                {"name": "skip", "isRequired": False},
            ],
            "extraSupported": ["search", "skip"],
        },
        {
            "type": "movie",
            "id": "missav_chinese",
            "name": "MissAV Chinese Subtitles",
            "extra": [
                {"name": "search", "isRequired": False},
                {"name": "skip", "isRequired": False},
            ],
            "extraSupported": ["search", "skip"],
        },
    ],
}

# Playlist cache: (uuid, path, base_url) -> (timestamp, content)
_playlist_cache = {}


def get_base_url(request: Request) -> str:
    """Returns the base public URL of this running server."""
    forwarded_proto = request.headers.get("x-forwarded-proto", request.url.scheme)
    forwarded_host = request.headers.get("x-forwarded-host", request.headers.get("host", request.url.netloc))
    return f"{forwarded_proto}://{forwarded_host}"


@app.get("/")
def home(request: Request):
    """User-friendly landing page with installation links."""
    base_url = get_base_url(request)
    manifest_url = f"{base_url}/manifest.json"
    stremio_link = f"stremio://{request.headers.get('host', request.url.netloc)}/manifest.json"

    html_content = f"""
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>MissAV Nuvio / Stremio Addon</title>
        <style>
            body {{
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                background: #0f172a;
                color: #f8fafc;
                margin: 0;
                padding: 40px 20px;
                display: flex;
                justify-content: center;
                align-items: center;
                min-height: 100vh;
                box-sizing: border-box;
            }}
            .card {{
                background: #1e293b;
                border-radius: 16px;
                padding: 32px;
                max-width: 600px;
                width: 100%;
                box-shadow: 0 10px 25px rgba(0,0,0,0.5);
                border: 1px solid #334155;
            }}
            h1 {{
                margin-top: 0;
                font-size: 26px;
                color: #38bdf8;
                display: flex;
                align-items: center;
                gap: 12px;
            }}
            .badge {{
                display: inline-block;
                background: #10b981;
                color: #fff;
                font-size: 12px;
                font-weight: bold;
                padding: 4px 10px;
                border-radius: 9999px;
            }}
            p {{
                color: #94a3b8;
                line-height: 1.6;
            }}
            .code-box {{
                background: #090d16;
                padding: 14px 18px;
                border-radius: 8px;
                font-family: monospace;
                color: #e2e8f0;
                word-break: break-all;
                border: 1px solid #334155;
                margin: 16px 0;
                user-select: all;
            }}
            .btn {{
                display: inline-block;
                background: #0284c7;
                color: white;
                text-decoration: none;
                padding: 12px 24px;
                border-radius: 8px;
                font-weight: 600;
                transition: background 0.2s;
                text-align: center;
                margin-top: 10px;
            }}
            .btn:hover {{
                background: #0369a1;
            }}
            .features {{
                margin: 20px 0;
                padding-left: 20px;
                color: #cbd5e1;
            }}
            .features li {{
                margin-bottom: 8px;
            }}
        </style>
    </head>
    <body>
        <div class="card">
            <h1>MissAV Addon <span class="badge">Running</span></h1>
            <p>Ready to stream with <strong>Nuvio</strong> and <strong>Stremio</strong> with built-in Cloudflare bypass and HLS stream proxying.</p>
            
            <h3>Install in Nuvio:</h3>
            <p>Copy this Addon Manifest URL and paste it into Nuvio's <strong>Addons &gt; Add Addon URL</strong>:</p>
            <div class="code-box">{manifest_url}</div>

            <a href="{stremio_link}" class="btn">Install into Stremio / Nuvio</a>

            <h3>Features:</h3>
            <ul class="features">
                <li><strong>Multiple Catalogs:</strong> Uncensored Leaks, New Releases, Recent, and Chinese Subtitles.</li>
                <li><strong>Instant Details:</strong> Sub-millisecond metadata delivery preventing Android detail load timeouts.</li>
                <li><strong>Full Search:</strong> Search by video ID (e.g. <code>nhdtb-213</code>), actress, or title.</li>
                <li><strong>Built-in HLS Proxy:</strong> Seamless playback on Android / ExoPlayer avoiding Cloudflare 403 Forbidden blocks.</li>
                <li><strong>Quality Selection:</strong> Auto-Adaptive Quality, 1080p FHD, 720p HD.</li>
            </ul>
        </div>
    </body>
    </html>
    """
    return HTMLResponse(content=html_content)


@app.api_route("/manifest.json", methods=["GET", "HEAD"])
def get_manifest():
    """Returns the addon manifest."""
    return JSONResponse(MANIFEST)


@app.get("/health")
@app.get("/debug")
def health_endpoint():
    """Health check endpoint confirming scraper connectivity."""
    cat = scraper.fetch_catalog("missav_uncensored", page=1)
    return JSONResponse({
        "status": "healthy",
        "catalog_items": len(cat),
    })



def parse_extra_params(extra_str: str) -> dict:
    """Parses extra parameters formatted like 'key=value&key2=value2' or 'key=value'."""
    params = {}
    if not extra_str:
        return params

    clean_str = extra_str.replace(".json", "")
    for pair in clean_str.split("&"):
        if "=" in pair:
            k, v = pair.split("=", 1)
            params[k] = urllib.parse.unquote_plus(v)
    return params


@app.api_route("/catalog/{type}/{id:path}", methods=["GET", "HEAD"])
def get_catalog(type: str, id: str, request: Request):
    """
    Returns catalogs or search results for Nuvio.
    Supports ?search=query, /search=query.json, and skip=number.
    """
    raw_path = urllib.parse.unquote(id)
    # id could be "missav_uncensored", "missav_uncensored/search=foo.json", "missav_uncensored.json", etc.
    parts = raw_path.split("/")
    catalog_id = parts[0].replace(".json", "")
    extra_str = "/".join(parts[1:]) if len(parts) > 1 else ""

    extra_params = parse_extra_params(extra_str)
    extra_params.update(dict(request.query_params))

    search_query = extra_params.get("search")
    skip_val = extra_params.get("skip", "0")
    try:
        skip = int(skip_val)
    except ValueError:
        skip = 0

    page = 1 + (skip // 12)

    if search_query:
        items = scraper.search_videos(search_query, page=page)
    else:
        items = scraper.fetch_catalog(catalog_id, page=page)

    if type == "series":
        series_items = []
        for it in items:
            c = dict(it)
            c["type"] = "series"
            series_items.append(c)
        return JSONResponse({"metas": series_items})

    return JSONResponse({"metas": items})


@app.api_route("/meta/{type}/{id:path}", methods=["GET", "HEAD"])
def get_meta(type: str, id: str):
    """
    Returns metadata for a specific video.
    Guaranteed instant (<1ms) response to eliminate 'Could not load details' timeouts.
    """
    clean_id = urllib.parse.unquote(id).strip()
    clean_slug = clean_id.replace("missav:", "").replace(".json", "").strip()
    clean_slug = re.sub(r'(:\d+)+$', '', clean_slug).strip("/")

    meta_obj = scraper.get_video_meta(clean_slug, media_type=type)
    return JSONResponse({"meta": meta_obj})


@app.api_route("/stream/{type}/{id:path}", methods=["GET", "HEAD"])
def get_stream(type: str, id: str, request: Request):
    """
    Returns streams for a given video ID.
    Queries available qualities so only valid, working streams are offered,
    preventing 404 player buffering.
    """
    clean_id = urllib.parse.unquote(id).strip()
    clean_slug = clean_id.replace("missav:", "").replace(".json", "").strip()
    clean_slug = re.sub(r'(:\d+)+$', '', clean_slug).strip("/")

    info = scraper.get_video_stream_info(clean_slug)
    if not info or not info.get("uuid"):
        return JSONResponse({"streams": []})

    uuid = info["uuid"]
    base_url = get_base_url(request)

    # Dynamically verify which qualities actually exist on CDN for this video
    available_qualities = scraper.get_available_qualities(uuid)

    quality_labels = {
        "1080p": "🌟 1080p FHD",
        "720p": "📺 720p HD",
        "480p": "📱 480p SD",
        "360p": "📶 360p Low",
    }

    streams = []

    # 1. Direct single-resolution streams FIRST (highest to lowest).
    # Direct variant streams start INSTANTLY in ExoPlayer without ABR track negotiation delays!
    for q in available_qualities:
        label = quality_labels.get(q, f"🎬 {q}")
        streams.append({
            "name": "MissAV [Proxied]",
            "title": f"{info['title']}\n{label}",
            "url": f"{base_url}/proxy/m3u8/{uuid}/{q}/video.m3u8",
            "behaviorHints": {
                "notWebReady": False,
            },
        })

    # 2. Adaptive HLS as an alternative option
    streams.append({
        "name": "MissAV [Proxied]",
        "title": f"{info['title']}\n⚡ Auto Quality (Adaptive HLS)",
        "url": f"{base_url}/proxy/m3u8/{uuid}/playlist.m3u8",
        "behaviorHints": {
            "notWebReady": False,
        },
    })

    return JSONResponse({"streams": streams})


@app.api_route("/proxy/m3u8/{uuid}/{path:path}", methods=["GET", "HEAD"])
def proxy_m3u8(uuid: str, path: str, request: Request):
    """
    HLS Master & Variant Playlist Proxy.
    Fetches playlist from surrit.com, rewrites segment URLs to .ts for Android ExoPlayer,
    strips proprietary non-standard tags (like #EXT-X-TOKEN) that break Android parsers,
    includes automatic 404 quality fallback, and caches responses in memory.
    """
    base_url = get_base_url(request)
    cache_key = (uuid, path, base_url)

    cached = _playlist_cache.get(cache_key)
    if cached and time.time() - cached[0] < 3600:
        rewritten_content = cached[1]
    else:
        surrit_url = f"https://surrit.com/{uuid}/{path}"
        headers = {
            "User-Agent": scraper.HEADERS["User-Agent"],
            "Referer": "https://missav.ws/",
            "Origin": "https://missav.ws",
            "Accept": "*/*",
        }

        session = scraper.get_session()
        try:
            r = session.get(surrit_url, headers=headers, timeout=10)
            # Automatic fallback if requested resolution (e.g. 1080p) does not exist on origin
            if r.status_code == 404 and re.match(r'^\d+p/', path):
                qualities = scraper.get_available_qualities(uuid)
                fallback_q = qualities[0] if qualities else "720p"
                fallback_path = re.sub(r'^\d+p', fallback_q, path)
                if fallback_path != path:
                    surrit_url = f"https://surrit.com/{uuid}/{fallback_path}"
                    r = session.get(surrit_url, headers=headers, timeout=10)

            if r.status_code != 200:
                raise HTTPException(status_code=r.status_code, detail="Failed to fetch playlist from origin")
        except HTTPException:
            raise
        except Exception as e:
            scraper.reset_session()
            raise HTTPException(status_code=502, detail=f"Proxy error: {e}")

        content = r.text

        # Determine directory path for relative resolutions (e.g. '720p' if path is '720p/video.m3u8')
        path_parts = path.split("/")
        dir_prefix = "/".join(path_parts[:-1]) if len(path_parts) > 1 else ""

        # Master playlist reorganization: sort stream variants from highest bandwidth to lowest
        if path.endswith("playlist.m3u8"):
            stream_entries = []
            current_inf = None
            header_lines = []

            for line in content.splitlines():
                trimmed = line.strip()
                if not trimmed or trimmed.startswith("#EXT-X-TOKEN"):
                    continue
                if trimmed.startswith("#EXT-X-STREAM-INF"):
                    current_inf = trimmed
                elif current_inf:
                    stream_entries.append((current_inf, trimmed))
                    current_inf = None
                else:
                    header_lines.append(trimmed)

            # Extract bandwidth and sort descending
            def get_bw(inf_line):
                bw_match = re.search(r'BANDWIDTH=(\d+)', inf_line)
                return int(bw_match.group(1)) if bw_match else 0

            stream_entries.sort(key=lambda x: get_bw(x[0]), reverse=True)

            rewritten_lines = list(header_lines)
            for inf, sub_url in stream_entries:
                rewritten_lines.append(inf)
                if sub_url.startswith("http://") or sub_url.startswith("https://"):
                    rewritten_lines.append(sub_url)
                else:
                    rewritten_lines.append(f"{base_url}/proxy/m3u8/{uuid}/{sub_url}")

            rewritten_content = "\n".join(rewritten_lines)
        else:
            rewritten_lines = []
            for line in content.splitlines():
                trimmed = line.strip()
                if not trimmed or trimmed.startswith("#EXT-X-TOKEN"):
                    continue
                if trimmed.startswith("#"):
                    rewritten_lines.append(line)
                    continue

                if trimmed.endswith(".m3u8"):
                    if trimmed.startswith("http://") or trimmed.startswith("https://"):
                        rewritten_lines.append(trimmed)
                    else:
                        rewritten_lines.append(f"{base_url}/proxy/m3u8/{uuid}/{trimmed}")
                elif trimmed.endswith(".jpeg") or trimmed.endswith(".ts") or trimmed.endswith(".mp4"):
                    seg_name = trimmed.replace(".jpeg", ".ts")
                    seg_path = f"{dir_prefix}/{seg_name}".lstrip("/")
                    rewritten_lines.append(f"{base_url}/proxy/ts/{uuid}/{seg_path}")
                else:
                    rewritten_lines.append(line)

            rewritten_content = "\n".join(rewritten_lines)

        _playlist_cache[cache_key] = (time.time(), rewritten_content)

    resp_headers = {
        "Access-Control-Allow-Origin": "*",
        "Cache-Control": "public, max-age=3600",
        "Content-Type": "application/vnd.apple.mpegurl",
    }

    if request.method == "HEAD":
        return Response(status_code=200, headers=resp_headers)

    return Response(
        content=rewritten_content,
        media_type="application/vnd.apple.mpegurl",
        headers=resp_headers,
    )


@app.api_route("/proxy/ts/{uuid}/{path:path}", methods=["GET", "HEAD"])
def proxy_ts(uuid: str, path: str, request: Request):
    """
    Streams individual MPEG-TS video chunks from surrit.com with Chrome impersonation.
    Maps .ts back to .jpeg for surrit.com and supports HTTP byte-range requests and retries.
    """
    range_header = request.headers.get("range")
    is_head = (request.method == "HEAD")

    r = scraper.fetch_surrit_segment(uuid, path, range_header=range_header, is_head=is_head)
    if not r or r.status_code not in [200, 206]:
        status = r.status_code if r else 502
        raise HTTPException(status_code=status, detail="Segment request failed")

    resp_headers = {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Expose-Headers": "Content-Length, Content-Range",
        "Accept-Ranges": "bytes",
        "Content-Type": "video/mp2t",
        "Cache-Control": "public, max-age=86400",
    }
    if "content-length" in r.headers:
        resp_headers["Content-Length"] = r.headers["content-length"]
    if "content-range" in r.headers:
        resp_headers["Content-Range"] = r.headers["content-range"]

    if is_head:
        return Response(status_code=r.status_code, headers=resp_headers)

    return Response(
        content=r.content,
        status_code=r.status_code,
        headers=resp_headers,
    )


if __name__ == "__main__":
    import uvicorn
    import argparse

    default_port = int(os.environ.get("PORT", 7000))
    default_host = os.environ.get("HOST", "0.0.0.0")

    parser = argparse.ArgumentParser(description="Run MissAV Nuvio Addon Server")
    parser.add_argument("--host", default=default_host, help=f"Host address (default {default_host})")
    parser.add_argument("--port", type=int, default=default_port, help=f"Port (default {default_port})")
    args = parser.parse_args()

    print(f"Starting MissAV Nuvio Addon at http://{args.host}:{args.port}")
    uvicorn.run("app:app", host=args.host, port=args.port, reload=False, timeout_keep_alive=120)
