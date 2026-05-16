#!/bin/bash
set -e

APP_DIR="$(cd "$(dirname "$0")" && pwd)"
WEB_DIR="$APP_DIR/web"
JAR="$APP_DIR/target/networth-tracker-0.0.1-SNAPSHOT.jar"
JAVA_HOME="/opt/homebrew/opt/openjdk@21"
REDIS_BIN="/opt/homebrew/Cellar/redis/8.6.3/bin"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
err()   { echo -e "${RED}[ERR]${NC} $1"; }

cleanup() {
  info "Shutting down..."
  lsof -ti :6379 2>/dev/null | xargs kill 2>/dev/null || true
  lsof -ti :8080 2>/dev/null | xargs kill 2>/dev/null || true
  lsof -ti :3000 2>/dev/null | xargs kill 2>/dev/null || true
  lsof -ti :8081 2>/dev/null | xargs kill 2>/dev/null || true
  info "Stopped."
}
trap cleanup EXIT INT TERM

# ── Redis ──────────────────────────────────────────────────────────
start_redis() {
  if lsof -i :6379 &>/dev/null; then
    info "Redis already running"
  else
    info "Starting Redis..."
    "$REDIS_BIN/redis-server" --daemonize yes && sleep 1
    "$REDIS_BIN/redis-cli" ping &>/dev/null || { err "Redis failed to start"; exit 1; }
    info "Redis started"
  fi
}

# ── llama.cpp AI Server ────────────────────────────────────────────
start_llama() {
  if lsof -i :8081 &>/dev/null; then
    info "llama-server already running"
    return
  fi
  MODEL_FILE=$(ls "$APP_DIR/models"/*.gguf 2>/dev/null | head -1)
  if [ -z "$MODEL_FILE" ]; then
    warn "No GGUF model found — AI Chat won't be available"
    return
  fi
  info "Starting llama-server..."
  bash "$APP_DIR/llama.sh" start
}

# ── Backend ────────────────────────────────────────────────────────
start_backend() {
  if lsof -i :8080 &>/dev/null; then
    warn "Restarting backend..."
    lsof -ti :8080 | xargs kill -9; sleep 2
  fi
  info "Starting Backend..."
  UPSTOX_ANALYTICS_TOKEN="${UPSTOX_ANALYTICS_TOKEN:-}" \
  ALPHA_VANTAGE_API_KEY="${ALPHA_VANTAGE_API_KEY:-}" \
  JAVA_HOME="$JAVA_HOME" nohup "$JAVA_HOME/bin/java" -jar "$JAR" &>/tmp/backend.log &
  for i in {1..30}; do
    curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/symbols 2>/dev/null | grep -q "403" && { info "Backend ready"; return; }
    sleep 1
  done
  err "Backend failed — check /tmp/backend.log"; exit 1
}

# ── Frontend ───────────────────────────────────────────────────────
start_frontend() {
  if lsof -i :3000 &>/dev/null; then
    warn "Restarting frontend..."
    lsof -ti :3000 | xargs kill -9; sleep 2
  fi
  info "Starting Frontend..."
  nohup npm --prefix "$WEB_DIR" run dev &>/tmp/frontend.log &
  for i in {1..30}; do
    curl -s -o /dev/null -w "%{http_code}" http://localhost:3000 2>/dev/null | grep -q "200\|304" && { info "Frontend ready"; return; }
    sleep 1
  done
  err "Frontend failed — check /tmp/frontend.log"; exit 1
}

# ── Main ───────────────────────────────────────────────────────────
start_redis
start_llama
start_backend
start_frontend

echo ""
info "All services running:"
echo "  Redis      → port 6379"
echo "  llama.cpp  → port 8081"
echo "  Backend    → http://localhost:8080"
echo "  Frontend   → http://localhost:3000"
echo ""
info "Press Ctrl+C to stop all services"

while true; do sleep 10; done
