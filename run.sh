#!/bin/bash
# MissAV Nuvio Addon Startup Script

PORT=${PORT:-7000}
HOST=${HOST:-0.0.0.0}

echo "=================================================="
echo " Starting MissAV Addon for Nuvio & Stremio"
echo " Host: $HOST | Port: $PORT"
echo " Manifest URL: http://localhost:$PORT/manifest.json"
echo "=================================================="

exec python3 app.py --host "$HOST" --port "$PORT"
