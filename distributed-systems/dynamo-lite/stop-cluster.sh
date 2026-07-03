#!/bin/bash
# ─────────────────────────────────────────────────────────────────
# stop-cluster.sh  —  Gracefully stop all Dynamo-lite nodes
# ─────────────────────────────────────────────────────────────────

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$PROJECT_DIR/logs"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; RESET='\033[0m'

echo ""
echo "  Stopping Dynamo-lite cluster…"
echo ""

stopped=0

for node in nodeA nodeB nodeC; do
    pidfile="$LOG_DIR/${node}.pid"
    if [ -f "$pidfile" ]; then
        pid=$(cat "$pidfile")
        if kill -0 "$pid" 2>/dev/null; then
            kill "$pid" 2>/dev/null
            echo -e "${GREEN}  ✓ ${node} stopped (PID $pid)${RESET}"
            stopped=$((stopped + 1))
        else
            echo -e "${YELLOW}  — ${node} was not running${RESET}"
        fi
        rm -f "$pidfile"
    else
        echo -e "${YELLOW}  — ${node} PID file not found${RESET}"
    fi
done

# Also kill by port in case PID files are stale
for port in 7001 7002 7003; do
    pid=$(lsof -ti tcp:$port 2>/dev/null || true)
    if [ -n "$pid" ]; then
        kill -9 $pid 2>/dev/null || true
    fi
done

echo ""
echo -e "  Stopped $stopped node(s)."
echo ""