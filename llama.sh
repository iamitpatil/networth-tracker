#!/bin/bash
# llama.cpp server management for Net Worth Tracker
set -e

MODEL_DIR="$(cd "$(dirname "$0")" && pwd)/models"
LLAMA_PORT=8081
LLAMA_HOST="127.0.0.1"

find_model() {
  ls "$MODEL_DIR"/*.gguf 2>/dev/null | head -1
}

start_llama_server() {
  MODEL_FILE=$(find_model)
  if [ -z "$MODEL_FILE" ]; then
    echo "ERROR: No GGUF model found in $MODEL_DIR/"
    echo "Download one first:"
    echo "  curl -L -o $MODEL_DIR/model.gguf <model-url>"
    exit 1
  fi

  if lsof -i :$LLAMA_PORT &>/dev/null; then
    echo "llama-server already running on port $LLAMA_PORT"
    return
  fi

  echo "Starting llama-server with model: $(basename "$MODEL_FILE")"
  MODEL_SIZE=$(stat -f%z "$MODEL_FILE" 2>/dev/null | awk '{printf "%.1f GB", $1/1073741824}')
  echo "Model size: $MODEL_SIZE"

  nohup llama-server \
    -m "$MODEL_FILE" \
    --host "$LLAMA_HOST" \
    --port "$LLAMA_PORT" \
    --ctx-size 4096 \
    --temp 0.3 \
    --no-warmup \
    --mlock \
    &>/tmp/llama_server.log &

  LLAMA_PID=$!
  echo "llama-server PID: $LLAMA_PID"

  for i in {1..60}; do
    if curl -s -o /dev/null -w "%{http_code}" "http://$LLAMA_HOST:$LLAMA_PORT/health" 2>/dev/null | grep -q "200"; then
      echo "llama-server ready (took ${i}s)"
      return
    fi
    sleep 1
  done

  echo "ERROR: llama-server failed to start within 60s"
  tail -20 /tmp/llama_server.log
  exit 1
}

stop_llama_server() {
  PIDS=$(lsof -ti :$LLAMA_PORT 2>/dev/null)
  if [ -n "$PIDS" ]; then
    echo "Stopping llama-server (PID: $PIDS)..."
    kill $PIDS 2>/dev/null
    sleep 1
    # force kill if still running
    lsof -ti :$LLAMA_PORT 2>/dev/null | xargs kill -9 2>/dev/null || true
    echo "llama-server stopped"
  fi
}

status_llama_server() {
  if lsof -i :$LLAMA_PORT &>/dev/null; then
    MODEL_FILE=$(find_model 2>/dev/null)
    echo "llama-server: RUNNING"
    echo "  Port:     $LLAMA_PORT"
    echo "  Model:    $(basename "$MODEL_FILE" 2>/dev/null || echo 'unknown')"
    echo "  PID:      $(lsof -ti :$LLAMA_PORT 2>/dev/null | tr '\n' ' ')"
    echo "  Health:   $(curl -s -o /dev/null -w '%{http_code}' http://$LLAMA_HOST:$LLAMA_PORT/health 2>/dev/null || echo 'unreachable')"
  else
    echo "llama-server: STOPPED"
  fi
}

case "${1:-status}" in
  start)   start_llama_server ;;
  stop)    stop_llama_server ;;
  restart) stop_llama_server; start_llama_server ;;
  status)  status_llama_server ;;
  *)
    echo "Usage: $0 {start|stop|restart|status}"
    exit 1
    ;;
esac
