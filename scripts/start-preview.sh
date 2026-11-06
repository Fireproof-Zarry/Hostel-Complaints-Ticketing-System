#!/usr/bin/env bash
# Builds the frontend, starts the backend and production preview, and prints
# "ready" once both respond. Press Ctrl+C to stop everything it started.
# Full output goes to a temporary log folder, shown only if something fails.

set -u
set -m # Puts each server in its own process group, so cleanup stops all of it.

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="$(mktemp -d)"
PIDS=()

cleanup() {
  for pid in "${PIDS[@]}"; do
    kill -- "-$pid" 2>/dev/null
  done
}
trap cleanup EXIT
trap 'exit 130' INT TERM

fail() {
  echo "failed: $1"
  [ -n "${2:-}" ] && echo "log: $2"
  exit 1
}

port_in_use() {
  ss -ltn "sport = :$1" | grep -q LISTEN
}

# Any HTTP response counts, because the backend answers 401 without a token.
wait_for() {
  local url="$1" pid="$2" log="$3" name="$4"

  for _ in $(seq 1 180); do
    curl -s -o /dev/null -m 2 "$url" && return 0
    kill -0 "$pid" 2>/dev/null || fail "$name stopped during startup" "$log"
    sleep 1
  done

  fail "$name did not start within 3 minutes" "$log"
}

port_in_use 5173 && fail "port 5173 is in use, stop npm run dev or preview first"

cd "$ROOT/frontend" || fail "frontend folder not found"
npm run build >"$LOG_DIR/build.log" 2>&1 </dev/null ||
  fail "frontend build" "$LOG_DIR/build.log"

# Reuse a backend that is already running.
if ! port_in_use 8080; then
  cd "$ROOT/backend" || fail "backend folder not found"
  ./mvnw spring-boot:run >"$LOG_DIR/backend.log" 2>&1 </dev/null &
  PIDS+=("$!")
  wait_for "http://localhost:8080/" "$!" "$LOG_DIR/backend.log" "backend"
fi

cd "$ROOT/frontend" || fail "frontend folder not found"
npm run preview -- --port 5173 --strictPort >"$LOG_DIR/preview.log" 2>&1 </dev/null &
PIDS+=("$!")
wait_for "http://localhost:5173/" "$!" "$LOG_DIR/preview.log" "preview"

echo "ready"
wait
