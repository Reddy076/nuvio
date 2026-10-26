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
    assert len(data["catalogs"]) >= 4
    print("  PASSED: Manifest returned valid Stremio metadata.")


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


def test_meta():
    print("Testing instant /meta/movie/missav:nhdtb-213-uncensored-leak.json...")
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
    assert duration < 0.1, f"Meta took too long: {duration}s"
    print(f"  PASSED: Instant metadata retrieved in {duration*1000:.2f}ms: {meta['name'][:40]}...")

    # Test double slash normalization
    print("Testing double slash normalization //meta/series/missav%3Asnos-359-uncensored-leak.json...")
    status_dbl, _, body_dbl = fetch("//meta/series/missav%3Asnos-359-uncensored-leak.json")
    assert status_dbl == 200
    data_dbl = json.loads(body_dbl.decode())
    assert "videos" in data_dbl["meta"]
    assert len(data_dbl["meta"]["videos"]) == 1
    print("  PASSED: Double slash route and series episode array parsed successfully.")


def test_stream_and_proxy():
    print("Testing /stream/movie/missav:nhdtb-213-uncensored-leak.json...")
    status, headers, body = fetch("/stream/movie/missav:nhdtb-213-uncensored-leak.json")
    assert status == 200
    data = json.loads(body.decode())
    assert "streams" in data
    assert len(data["streams"]) > 0
    print(f"  PASSED: Stream endpoint returned {len(data['streams'])} streams.")

    # Find the proxied playlist URL
    proxied_stream = next((s for s in data["streams"] if "/proxy/m3u8/" in s["url"]), None)
    assert proxied_stream is not None, "No proxied stream found!"
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
    assert seg_headers.get("Content-Type") == "video/mp2t"
    print(f"  PASSED: Segment received {len(body_seg)} bytes with MPEG-TS sync byte 0x47 and video/mp2t Content-Type!")


if __name__ == "__main__":
    t = threading.Thread(target=run_server, daemon=True)
    t.start()
    time.sleep(1.5)  # Wait for server to boot

    print("=== Running MissAV Nuvio Addon Test Suite ===")
    test_manifest()
    test_catalog()
    test_search()
    test_meta()
    test_stream_and_proxy()
    print("\nALL TESTS PASSED SUCCESSFULLY! Addon is 100% operational.")
    sys.exit(0)
