#!/bin/bash
set -e

export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"

check_port() {
    lsof -i:"$1" -sTCP:LISTEN -t >/dev/null 2>&1
}

BACKEND_OCCUPIED=false
FRONTEND_OCCUPIED=false
if check_port 8080; then BACKEND_OCCUPIED=true; fi
if check_port 5173; then FRONTEND_OCCUPIED=true; fi

if $BACKEND_OCCUPIED || $FRONTEND_OCCUPIED; then
    msg="Port occupied:"
    $BACKEND_OCCUPIED && msg="$msg backend(8080)"
    $FRONTEND_OCCUPIED && msg="$msg frontend(5173)"
    echo "$msg"
    exit 1
fi

cleanup() {
    [ -n "$BACKEND_PID" ] && kill "$BACKEND_PID" 2>/dev/null
    [ -n "$FRONTEND_PID" ] && kill "$FRONTEND_PID" 2>/dev/null
    wait 2>/dev/null
}
trap cleanup EXIT INT TERM

./gradlew :fireworks-server:bootRun &
BACKEND_PID=$!

cd "$ROOT_DIR/fireworks-web" && npm run dev &
FRONTEND_PID=$!

wait
