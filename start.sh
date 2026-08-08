#!/bin/bash
# Local dev launcher for Net Worth Tracker.
#
# Backing services (Postgres, Redis, optional LLM) run in Docker; the backend and
# frontend run natively so you get fast rebuilds. Idempotent - anything already
# listening on its port is reused rather than restarted.
#
#   bash start.sh              # Postgres + Redis + backend + frontend
#   bash start.sh --ai         # ...and the local LLM on 8082 (AI Chat)
#   bash start.sh --restart    # force-restart the native backend and frontend
#   bash start.sh stop         # stop the native servers (Docker services stay up)
#   bash start.sh status       # show what is running
#
# For an all-in-Docker stack instead (backend and web in containers too):
#   docker compose up -d
set -uo pipefail

APP_DIR="$(cd "$(dirname "$0")" && pwd)"
WEB_DIR="$APP_DIR/web"
cd "$APP_DIR"

BACKEND_PORT=8080
FRONTEND_PORT=3000
LLAMA_PORT=8082

WANT_AI=0
FORCE_RESTART=0
CMD="start"
for arg in "$@"; do
  case "$arg" in
    --ai)              WANT_AI=1 ;;
    --restart)         FORCE_RESTART=1 ;;
    start|stop|status) CMD="$arg" ;;
    -h|--help)         sed -n '2,18p' "$0"; exit 0 ;;
    *) echo "Unknown argument: $arg (try --help)"; exit 1 ;;
  esac
done

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
info() { echo -e "${GREEN}[INFO]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
err()  { echo -e "${RED}[ERR ]${NC} $1"; }
step() { echo -e "${BLUE}==>${NC} $1"; }

port_busy() { lsof -i ":$1" -sTCP:LISTEN >/dev/null 2>&1; }

# True when the process listening on a port is Docker's port proxy. Docker publishes
# every container port through one shared process, so its pid is meaningless as an
# identifier and must never be killed - use `docker compose` for those services.
port_is_docker() {
  local pid comm
  pid="$(lsof -ti ":$1" 2>/dev/null | head -1)"
  [ -z "$pid" ] && return 1
  comm="$(ps -o comm= -p "$pid" 2>/dev/null)"
  case "$comm" in
    *docker*|*vpnkit*|*com.docker*) return 0 ;;
    *) return 1 ;;
  esac
}

# PIDs of the servers this script starts, so Ctrl+C only stops those and never
# touches Docker-published ports.
BACKEND_PID=""
FRONTEND_PID=""

cleanup() {
  [ -n "$BACKEND_PID" ]  && kill "$BACKEND_PID"  2>/dev/null && info "Stopped backend (pid $BACKEND_PID)"
  [ -n "$FRONTEND_PID" ] && kill "$FRONTEND_PID" 2>/dev/null && info "Stopped frontend (pid $FRONTEND_PID)"
  [ -n "$BACKEND_PID$FRONTEND_PID" ] && echo "" && info "Docker services left running - stop them with: docker compose stop"
  return 0
}

# ── Java ───────────────────────────────────────────────────────────
# Honour an existing JAVA_HOME, else ask macOS for a 21, else fall back to PATH.
resolve_java_home() {
  if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
    echo "$JAVA_HOME"; return 0
  fi
  if [ -x /usr/libexec/java_home ]; then
    local jh
    jh="$(/usr/libexec/java_home -v 21 2>/dev/null)"
    if [ -n "$jh" ]; then echo "$jh"; return 0; fi
  fi
  local java_bin
  java_bin="$(command -v java 2>/dev/null)"
  if [ -n "$java_bin" ]; then
    dirname "$(dirname "$(readlink -f "$java_bin" 2>/dev/null || echo "$java_bin")")"
    return 0
  fi
  return 1
}

# ── Environment ────────────────────────────────────────────────────
load_env() {
  if [ -f "$APP_DIR/.env" ]; then
    set -a; . "$APP_DIR/.env"; set +a
    info "Loaded .env"
  else
    warn "No .env found - using defaults from application.properties."
    warn "If Postgres runs via Docker you need DB_USERNAME/DB_PASSWORD; see README."
  fi
}

# ── Docker ─────────────────────────────────────────────────────────
ensure_docker() {
  if docker info >/dev/null 2>&1; then
    info "Docker daemon already running"
    return 0
  fi
  if [ ! -d /Applications/Docker.app ]; then
    err "Docker daemon is not running and Docker Desktop was not found in /Applications."
    return 1
  fi
  step "Starting Docker Desktop..."
  open -a Docker
  for _ in $(seq 1 90); do
    docker info >/dev/null 2>&1 && { info "Docker daemon ready"; return 0; }
    sleep 1
  done
  err "Docker daemon did not come up within 90s"
  return 1
}

ensure_services() {
  local services="postgres redis"
  [ "$WANT_AI" -eq 1 ] && services="$services llm"

  step "Bringing up Docker services: $services"
  # `up -d` is idempotent: it starts what is missing and leaves running ones alone.
  docker compose up -d $services || { err "docker compose up failed"; return 1; }

  step "Waiting for Postgres..."
  for _ in $(seq 1 60); do
    docker compose exec -T postgres pg_isready -U "${DB_USERNAME:-postgres}" >/dev/null 2>&1 \
      && { info "Postgres ready"; break; }
    sleep 1
  done

  step "Waiting for Redis..."
  for _ in $(seq 1 30); do
    docker compose exec -T redis redis-cli ping 2>/dev/null | grep -q PONG \
      && { info "Redis ready"; break; }
    sleep 1
  done

  if [ "$WANT_AI" -eq 1 ]; then
    info "LLM starting on port $LLAMA_PORT (first run downloads the model - watch: docker compose logs -f llm)"
  fi
  return 0
}

# ── Backend ────────────────────────────────────────────────────────
start_backend() {
  if port_busy "$BACKEND_PORT"; then
    if port_is_docker "$BACKEND_PORT"; then
      info "Backend already served by the Docker 'app' container on port $BACKEND_PORT - reusing it"
      info "  (to run it natively instead: docker compose stop app, then re-run this script)"
      return 0
    fi
    if [ "$FORCE_RESTART" -eq 1 ]; then
      warn "Stopping backend on port $BACKEND_PORT..."
      lsof -ti ":$BACKEND_PORT" | xargs kill 2>/dev/null
      for _ in $(seq 1 20); do port_busy "$BACKEND_PORT" || break; sleep 1; done
      port_busy "$BACKEND_PORT" && { lsof -ti ":$BACKEND_PORT" | xargs kill -9 2>/dev/null; sleep 2; }
    else
      info "Backend already running on port $BACKEND_PORT (use --restart to replace it)"
      return 0
    fi
  fi

  local jh
  jh="$(resolve_java_home)" || { err "No Java found. Install JDK 21."; return 1; }
  export JAVA_HOME="$jh"
  info "JAVA_HOME=$JAVA_HOME"

  if ! command -v mvn >/dev/null 2>&1; then
    err "mvn not found on PATH. Install Maven 3.9+."
    return 1
  fi

  step "Starting backend (Flyway migrates on boot)..."
  nohup mvn spring-boot:run >/tmp/backend.log 2>&1 &
  BACKEND_PID=$!

  for i in $(seq 1 180); do
    if curl -sf "http://localhost:$BACKEND_PORT/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
      info "Backend ready in ${i}s -> http://localhost:$BACKEND_PORT"
      return 0
    fi
    if grep -q "APPLICATION FAILED TO START\|BUILD FAILURE" /tmp/backend.log 2>/dev/null; then
      err "Backend failed to start. Last lines of /tmp/backend.log:"
      grep -E "APPLICATION FAILED TO START|Web server failed|Caused by|ERROR" /tmp/backend.log | tail -10
      return 1
    fi
    sleep 1
  done
  err "Backend did not become healthy within 180s - see /tmp/backend.log"
  return 1
}

# ── Frontend ───────────────────────────────────────────────────────
start_frontend() {
  if port_busy "$FRONTEND_PORT"; then
    if port_is_docker "$FRONTEND_PORT"; then
      info "Frontend already served by the Docker 'web' container on port $FRONTEND_PORT - reusing it"
      info "  (to run Vite natively instead: docker compose stop web, then re-run this script)"
      return 0
    fi
    if [ "$FORCE_RESTART" -eq 1 ]; then
      warn "Stopping frontend on port $FRONTEND_PORT..."
      lsof -ti ":$FRONTEND_PORT" | xargs kill 2>/dev/null; sleep 2
    else
      info "Frontend already running on port $FRONTEND_PORT (use --restart to replace it)"
      return 0
    fi
  fi

  if [ ! -d "$WEB_DIR/node_modules" ]; then
    step "Installing frontend dependencies..."
    npm --prefix "$WEB_DIR" install || { err "npm install failed"; return 1; }
  fi

  step "Starting frontend..."
  nohup npm --prefix "$WEB_DIR" run dev >/tmp/frontend.log 2>&1 &
  FRONTEND_PID=$!

  for i in $(seq 1 60); do
    code="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$FRONTEND_PORT" 2>/dev/null)"
    case "$code" in
      200|304) info "Frontend ready in ${i}s -> http://localhost:$FRONTEND_PORT"; return 0 ;;
    esac
    sleep 1
  done
  err "Frontend did not respond within 60s - see /tmp/frontend.log"
  return 1
}

# ── Subcommands ────────────────────────────────────────────────────
do_status() {
  echo ""
  printf "%-12s %-8s %s\n" "SERVICE" "PORT" "STATUS"
  for row in "backend:$BACKEND_PORT" "frontend:$FRONTEND_PORT" "llm:$LLAMA_PORT"; do
    name="${row%%:*}"; port="${row##*:}"
    if port_busy "$port"; then
      pid="$(lsof -ti ":$port" | head -1)"
      # Docker publishes every container port through one shared proxy process, so a
      # bare pid would be the same for all of them and tell you nothing. Say "docker"
      # instead when the listener belongs to Docker.
      comm="$(ps -o comm= -p "$pid" 2>/dev/null)"
      case "$comm" in
        *docker*|*vpnkit*|*com.docker*) printf "%-12s %-8s ${GREEN}running${NC} (docker)\n" "$name" "$port" ;;
        *)                              printf "%-12s %-8s ${GREEN}running${NC} (native, pid %s)\n" "$name" "$port" "$pid" ;;
      esac
    else
      printf "%-12s %-8s ${YELLOW}stopped${NC}\n" "$name" "$port"
    fi
  done
  echo ""
  if docker info >/dev/null 2>&1; then
    docker compose ps --format "table {{.Service}}\t{{.Status}}" 2>/dev/null
  else
    warn "Docker daemon not running"
  fi
  echo ""
}

do_stop() {
  for row in "backend:$BACKEND_PORT" "frontend:$FRONTEND_PORT"; do
    name="${row%%:*}"; port="${row##*:}"
    if port_busy "$port"; then
      pid="$(lsof -ti ":$port" | head -1)"
      comm="$(ps -o comm= -p "$pid" 2>/dev/null)"
      case "$comm" in
        # Never kill Docker's port proxy - that would take down the daemon's
        # networking rather than the service. Use docker compose for those.
        *docker*|*vpnkit*|*com.docker*)
          case "$name" in
            backend)  svc="app" ;;
            frontend) svc="web" ;;
            *)        svc="$name" ;;
          esac
          warn "$name on port $port is served by Docker - stop it with: docker compose stop $svc"
          ;;
        *)
          info "Stopping $name on port $port..."
          lsof -ti ":$port" | xargs kill 2>/dev/null
          ;;
      esac
    fi
  done
  info "Docker services are untouched - stop them with: docker compose stop"
}

case "$CMD" in
  status) do_status; exit 0 ;;
  stop)   do_stop;   exit 0 ;;
esac

trap cleanup EXIT INT TERM

load_env
ensure_docker  || exit 1
ensure_services || exit 1
start_backend  || exit 1
start_frontend || exit 1

echo ""
info "All services running:"
echo "  Frontend   -> http://localhost:$FRONTEND_PORT"
echo "  Backend    -> http://localhost:$BACKEND_PORT  (Swagger: /docs)"
echo "  Postgres   -> localhost:5432  (Docker)"
echo "  Redis      -> localhost:6379  (Docker)"
if [ "$WANT_AI" -eq 1 ]; then
  echo "  LLM        -> http://localhost:$LLAMA_PORT  (Docker)"
else
  echo "  LLM        -> not started (re-run with --ai for AI Chat)"
fi
echo ""
info "Logs: /tmp/backend.log  /tmp/frontend.log"
info "Press Ctrl+C to stop the backend and frontend"

while true; do sleep 10; done
