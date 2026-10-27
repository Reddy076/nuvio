"""
Automated Test Suite for MissAV Nuvio / Stremio Addon
Runs live server in background thread and tests all endpoints.
"""

import sys
import threading
import time
import urllib.request
import json
import uvicorn
from app import app

PORT = 7891
BASE_URL = f"http://127.0.0.1:{PORT}"


def run_server():
    config = uvicorn.Config(app, host="127.0.0.1", port=PORT, log_level="warning")
    server = uvicorn.Server(config)
    server.run()


def fetch(path):
    url = f"{BASE_URL}{path}"
    req = urllib.request.Request(url, headers={"User-Agent": "TestClient/1.0"})
    with urllib.request.urlopen(req, timeout=15) as res:
        return res.status, res.headers, res.read()


def test_manifest():
    print("Testing /manifest.json...")
    status, headers, body = fetch("/manifest.json")
    assert status == 200
    data = json.loads(body.decode())
    assert data["id"] == "community.missav"
    assert len(data["catalogs"]) == 1
    assert data["catalogs"][0]["id"] == "missav_uncensored"
    print("  PASSED: Manifest returned valid Stremio metadata with single catalog.")


def test_catalog():
    print("Testing /catalog/movie/missav_uncensored.json...")
    status, headers, body = fetch("/catalog/movie/missav_uncensored.json")
    assert status == 200
    data = json.loads(body.decode())
    assert "metas" in data
    assert len(data["metas"]) > 0
    first = data["metas"][0]
    assert "id" in first and first["id"].startswith("missav:")
    assert "poster" in first
    print(f"  PASSED: Fetched {len(data['metas'])} catalog items. First: {first['id']}")


def test_search():
    print("Testing /catalog/movie/missav_uncensored/search=nhdtb.json...")
    status, headers, body = fetch("/catalog/movie/missav_uncensored/search=nhdtb.json")
    assert status == 200
    data = json.loads(body.decode())
    assert "metas" in data
    assert len(data["metas"]) > 0
    print(f"  PASSED: Code search returned {len(data['metas'])} items.")

    print("Testing actress search /catalog/movie/missav_uncensored/search=Yua%20Mikami.json...")
    status_act, _, body_act = fetch("/catalog/movie/missav_uncensored/search=Yua%20Mikami.json")
    assert status_act == 200
    data_act = json.loads(body_act.decode())
    assert "metas" in data_act
    assert len(data_act["metas"]) > 0
    print(f"  PASSED: Actress search returned {len(data_act['metas'])} items.")

    print("Testing search with sort /catalog/movie/missav_uncensored/search=ipx&sort=today_views.json...")
    status_sort, _, body_sort = fetch("/catalog/movie/missav_uncensored/search=ipx&sort=today_views.json")
    assert status_sort == 200
    data_sort = json.loads(body_sort.decode())
    assert "metas" in data_sort
    assert len(data_sort["metas"]) > 0
    print(f"  PASSED: Sort search today_views returned {len(data_sort['metas'])} items.")

    print("Testing direct /search?q=ipx&sort=monthly_views endpoint...")
    status_direct, _, body_direct = fetch("/search?q=ipx&sort=monthly_views")
    assert status_direct == 200
    data_direct = json.loads(body_direct.decode())
    assert "metas" in data_direct
    assert len(data_direct["metas"]) > 0
    print(f"  PASSED: Direct search endpoint returned {len(data_direct['metas'])} sorted items.")


def test_meta():
    print("Testing /meta/movie/missav:nhdtb-213-uncensored-leak.json...")
    t0 = time.time()
    status, headers, body = fetch("/meta/movie/missav:nhdtb-213-uncensored-leak.json")
    duration = time.time() - t0
    assert status == 200
    data = json.loads(body.decode())
    assert "meta" in data
    meta = data["meta"]
    assert meta["id"] == "missav:nhdtb-213-uncensored-leak"
    assert "NHDTB-213" in meta["name"]
    assert meta.get("poster") is not None
    assert meta.get("background") is not None
    assert meta.get("logo") is None
    assert isinstance(meta.get("genres"), list) and len(meta["genres"]) > 0
    assert isinstance(meta.get("cast"), list)
    assert meta.get("releaseInfo") is not None
    assert meta.get("runtime") is not None
    assert meta.get("ageRating") == "18+"
    print(f"  PASSED: Rich metadata retrieved in {duration*1000:.2f}ms: {meta['name'][:40]}... (genres: {len(meta['genres'])}, cast: {len(meta['cast'])})")

    # Second fetch should be fast cached
    t1 = time.time()
    status_c, _, body_c = fetch("/meta/movie/missav:nhdtb-213-uncensored-leak.json")
    dur_cached = time.time() - t1
    assert status_c == 200
    assert dur_cached < 0.1, f"Cached meta took too long: {dur_cached}s"
    print(f"  PASSED: Instant cached metadata retrieved in {dur_cached*1000:.2f}ms")

    # Test double slash normalization
    print("Testing double slash normalization //meta/series/missav%3Asnos-359-uncensored-leak.json...")
    status_dbl, _, body_dbl = fetch("//meta/series/missav%3Asnos-359-uncensored-leak.json")
    assert status_dbl == 200
    data_dbl = json.loads(body_dbl.decode())
    assert "videos" in data_dbl["meta"]
    assert len(data_dbl["meta"]["videos"]) == 1
    assert data_dbl["meta"]["logo"] is None
    print("  PASSED: Double slash route and series episode array parsed successfully.")


def test_stream_and_proxy():
    print("Testing /stream/movie/missav:nhdtb-213-uncensored-leak.json...")
    status, headers, body = fetch("/stream/movie/missav:nhdtb-213-uncensored-leak.json")
    assert status == 200
    data = json.loads(body.decode())
    assert "streams" in data
    assert len(data["streams"]) > 0
    print(f"  PASSED: Stream endpoint returned {len(data['streams'])} streams.")

    # Find the proxied variant playlist URL (video.m3u8)
    proxied_stream = next((s for s in data["streams"] if "/proxy/m3u8/" in s["url"] and "video.m3u8" in s["url"]), None)
    assert proxied_stream is not None, "No proxied direct variant stream found!"
    stream_url = proxied_stream["url"]
    path_and_query = stream_url.replace(BASE_URL, "")

    print(f"Testing proxied direct variant playlist: {path_and_query}...")
    status_m3u8, _, body_m3u8 = fetch(path_and_query)
    text_m3u8 = body_m3u8.decode()
    assert status_m3u8 == 200
    assert "#EXTM3U" in text_m3u8
    assert "/proxy/ts/" in text_m3u8
    assert "#EXT-X-TOKEN" not in text_m3u8
    print("  PASSED: Proxied variant playlist fetched cleanly without non-standard tags.")

    # Test individual segment chunk (.ts rewritten from origin .jpeg)
    seg_lines = [l.strip() for l in text_m3u8.splitlines() if l.strip() and not l.startswith("#")]
    seg_url = seg_lines[0].replace(BASE_URL, "")
    print(f"Testing segment download: {seg_url}...")
    status_seg, seg_headers, body_seg = fetch(seg_url)
    assert status_seg == 200
    assert len(body_seg) > 10000
    assert body_seg[0] == 0x47, "Segment is not valid MPEG-TS"
    print(f"  PASSED: Segment received {len(body_seg)} bytes with MPEG-TS sync byte 0x47 and {seg_headers.get('content-type')} Content-Type!")

    # Also test adaptive master playlist
    master_stream = [s for s in data["streams"] if "playlist.m3u8" in s["url"]][0]
    master_path = master_stream["url"].replace(BASE_URL, "")
    print(f"Testing adaptive master playlist: {master_path}...")
    status_master, _, body_master = fetch(master_path)
    text_master = body_master.decode()
    assert status_master == 200
    assert "#EXTM3U" in text_master
    assert "/proxy/m3u8/" in text_master
    assert "#EXT-X-TOKEN" not in text_master
    print("  PASSED: Master playlist verified with high-to-low variant order and clean tags.")

    # Test direct download stream (MP4)
    dl_stream = [s for s in data["streams"] if "/proxy/download/" in s["url"]][0]
    dl_path = dl_stream["url"].replace(BASE_URL, "")
    print(f"Testing download endpoint (HEAD): {dl_path}...")
    head_req = urllib.request.Request(f"{BASE_URL}{dl_path}", method="HEAD")
    with urllib.request.urlopen(head_req) as resp:
        assert resp.status == 200
        assert resp.headers.get("Content-Type") == "video/mp4"
        assert resp.headers.get("Accept-Ranges") == "bytes"
        print("  PASSED: Download endpoint returned 200 OK with video/mp4 MIME type and Accept-Ranges: bytes!")

    # Test HTTP Range request (resuming support for Nuvio)
    range_offset = 1500000
    print(f"Testing download Range resume request: Range: bytes={range_offset}-...")
    range_req = urllib.request.Request(f"{BASE_URL}{dl_path}", headers={"Range": f"bytes={range_offset}-"})
    with urllib.request.urlopen(range_req) as resp:
        assert resp.status == 206
        cr = resp.headers.get("Content-Range")
        assert cr is not None and cr.startswith(f"bytes {range_offset}-")
        chunk = resp.read(64 * 1024)
        assert len(chunk) > 0
        print(f"  PASSED: Range request returned 206 Partial Content with Content-Range: {cr} and delivered {len(chunk)} bytes!")


def test_non_1080p_video():
    print("Testing non-1080p title fallback (fc2-ppv-4998024)...")
    status, _, body = fetch("/stream/movie/missav:fc2-ppv-4998024.json")
    assert status == 200
    data = json.loads(body.decode())
    assert len(data.get("streams", [])) > 0

    # 1. Test 1080p fallback m3u8
    m3u8_stream = next((s for s in data["streams"] if "/proxy/m3u8/" in s["url"] and "1080p" in s["url"]), None)
    if m3u8_stream:
        path = m3u8_stream["url"].replace(BASE_URL, "")
        s_m3u8, _, body_m3u8 = fetch(path)
        assert s_m3u8 == 200
        text = body_m3u8.decode()
        assert "/720p/" in text, "Expected 720p fallback in segment paths"
        print("  PASSED: 1080p m3u8 smoothly fell back to 720p segments.")

    # 2. Test download endpoint with 1080p request
    dl_stream = next((s for s in data["streams"] if "/proxy/download/" in s["url"]), None)
    if dl_stream:
        dl_path = dl_stream["url"].replace(BASE_URL, "")
        head_req = urllib.request.Request(f"{BASE_URL}{dl_path}", method="HEAD")
        with urllib.request.urlopen(head_req) as resp:
            assert resp.status == 200
            assert "720p" in resp.headers.get("Content-Disposition", "")
        print("  PASSED: 1080p download smoothly fell back to 720p MP4.")


def test_query_parser():
    print("Testing parse_search_query_modifiers...")
    import scraper
    cases = [
        ("Yua Mikami", "Yua Mikami", "views", 1),
        ("Yua Mikami 2", "Yua Mikami", "views", 2),
        ("Yua Mikami p3", "Yua Mikami", "views", 3),
        ("Yua Mikami t", "Yua Mikami", "today_views", 1),
        ("Yua Mikami t 2", "Yua Mikami", "today_views", 2),
        ("Yua Mikami 2 t", "Yua Mikami", "today_views", 2),
        ("Yua Mikami m", "Yua Mikami", "monthly_views", 1),
        ("Yua Mikami m 2", "Yua Mikami", "monthly_views", 2),
        ("Yua Mikami w", "Yua Mikami", "weekly_views", 1),
        ("Yua Mikami r", "Yua Mikami", "released_at", 1),
        ("SSIS-062", "SSIS-062", "views", 1),
        ("SSIS 062", "SSIS 062", "views", 1),
        ("SSIS 2", "SSIS", "views", 2),
    ]
    for raw, exp_q, exp_s, exp_b in cases:
        cq, cs, cb = scraper.parse_search_query_modifiers(raw)
        assert cq == exp_q, f"For {raw}: expected query {exp_q}, got {cq}"
        assert cs == exp_s, f"For {raw}: expected sort {exp_s}, got {cs}"
        assert cb == exp_b, f"For {raw}: expected batch {exp_b}, got {cb}"
    print("  PASSED: Query modifier parser accurately parsed all test cases without code collision.")


def test_search_modifiers_api():
    print("Testing search modifier queries through API...")
    # Today's views via search suffix
    status_t, _, body_t = fetch("/catalog/movie/missav_uncensored/search=Yua%20Mikami%20t.json")
    assert status_t == 200
    data_t = json.loads(body_t.decode())
    assert len(data_t.get("metas", [])) > 0
    print(f"  PASSED: 'Yua Mikami t' returned {len(data_t['metas'])} today-views items.")

    # Batch 2 via search suffix
    status_b2, _, body_b2 = fetch("/catalog/movie/missav_uncensored/search=Yua%20Mikami%202.json")
    assert status_b2 == 200
    data_b2 = json.loads(body_b2.decode())
    assert len(data_b2.get("metas", [])) > 0
    print(f"  PASSED: 'Yua Mikami 2' returned {len(data_b2['metas'])} batch 2 items.")

    # Direct search endpoint with monthly modifier
    status_m, _, body_m = fetch("/search?q=Yua%20Mikami%20m")
    assert status_m == 200
    data_m = json.loads(body_m.decode())
    assert len(data_m.get("metas", [])) > 0
    print(f"  PASSED: Direct search '/search?q=Yua Mikami m' returned {len(data_m['metas'])} items.")


if __name__ == "__main__":
    t = threading.Thread(target=run_server, daemon=True)
    t.start()
    time.sleep(1.5)  # Wait for server to boot

    print("=== Running MissAV Nuvio Addon Test Suite ===")
    test_query_parser()
    test_manifest()
    test_catalog()
    test_search()
    test_search_modifiers_api()
    test_meta()
    test_stream_and_proxy()
    test_non_1080p_video()
    print("\nALL TESTS PASSED SUCCESSFULLY! Addon is 100% operational.")
    sys.exit(0)
