#!/bin/bash
# ─────────────────────────────────────────────────────────────────
# start-cluster.sh  —  Start all 3 Dynamo-lite nodes + GUI dashboard
# Usage: ./start-cluster.sh
# ─────────────────────────────────────────────────────────────────

set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUT_DIR="$PROJECT_DIR/out"
CONFIG_DIR="$PROJECT_DIR/config"
LOG_DIR="$PROJECT_DIR/logs"

# Colours
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

echo ""
echo -e "${BOLD}${CYAN}  ╔══════════════════════════════════════╗${RESET}"
echo -e "${BOLD}${CYAN}  ║       DYNAMO-LITE  CLUSTER           ║${RESET}"
echo -e "${BOLD}${CYAN}  ╚══════════════════════════════════════╝${RESET}"
echo ""

# ── 1. Compile if needed ──────────────────────────────────────────
if [ ! -d "$OUT_DIR" ] || [ -z "$(ls -A "$OUT_DIR" 2>/dev/null)" ]; then
    echo -e "${YELLOW}  Compiling…${RESET}"
    mkdir -p "$OUT_DIR"
    find "$PROJECT_DIR/src" -name "*.java" | xargs javac -d "$OUT_DIR"
    echo -e "${GREEN}  ✓ Compiled${RESET}"
else
    echo -e "${GREEN}  ✓ Already compiled  (run ./compile.sh to force recompile)${RESET}"
fi

# ── 2. Create log directory ───────────────────────────────────────
mkdir -p "$LOG_DIR"

# ── 3. Kill any leftover nodes on ports 7001-7003 ────────────────
for port in 7001 7002 7003; do
    pid=$(lsof -ti tcp:$port 2>/dev/null || true)
    if [ -n "$pid" ]; then
        echo -e "${YELLOW}  Killing stale process on port $port (PID $pid)${RESET}"
        kill -9 $pid 2>/dev/null || true
        sleep 0.3
    fi
done

# ── 4. Start nodes ────────────────────────────────────────────────
NODES=("nodeA" "nodeB" "nodeC")
PORTS=(7001 7002 7003)

for i in "${!NODES[@]}"; do
    node="${NODES[$i]}"
    port="${PORTS[$i]}"
    logfile="$LOG_DIR/${node}.log"

    java -cp "$OUT_DIR" com.dynamo.lite.DynamoNode \
        "$CONFIG_DIR/${node}.config" \
        > "$logfile" 2>&1 &

    echo $! > "$LOG_DIR/${node}.pid"
    sleep 0.4

    # Verify it started
    if kill -0 $! 2>/dev/null; then
        echo -e "${GREEN}  ✓ ${node} started on port ${port}  (log: logs/${node}.log)${RESET}"
    else
        echo -e "${RED}  ✗ ${node} failed to start — check logs/${node}.log${RESET}"
    fi
done

echo ""
echo -e "  ${BOLD}Cluster:${RESET}  NodeA :7001  ·  NodeB :7002  ·  NodeC :7003"
echo -e "  ${BOLD}Config:${RESET}   N=3  W=2  R=2  ·  150 vnodes per node"
echo ""

# ── 5. Launch GUI (optional — skip if NOGUI is set) ───────────────
if [ "${NOGUI:-0}" = "1" ]; then
    echo -e "  ${YELLOW}GUI skipped (NOGUI=1).${RESET}"
    echo -e "  Run  ${CYAN}./gui.sh${RESET}  to open the dashboard later."
else
    echo -e "  Launching GUI dashboard…"
    java -cp "$OUT_DIR" com.dynamo.lite.client.DynamoGUI &
    echo -e "${GREEN}  ✓ GUI started${RESET}"
fi

echo ""
echo -e "  ${BOLD}Logs:${RESET}     tail -f logs/nodeA.log"
echo -e "  ${BOLD}Stop:${RESET}     ./stop-cluster.sh"
echo -e "  ${BOLD}CLI:${RESET}      java -cp out com.dynamo.lite.client.DynamoCLI"
echo ""