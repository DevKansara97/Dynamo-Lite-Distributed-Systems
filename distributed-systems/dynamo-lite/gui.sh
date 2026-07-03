#!/bin/bash
# ─────────────────────────────────────────────────────────────────
# gui.sh  —  Launch the Dynamo-lite GUI dashboard standalone
# Start your nodes first with ./start-cluster.sh NOGUI=1
# ─────────────────────────────────────────────────────────────────

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUT_DIR="$PROJECT_DIR/out"

echo ""
echo "  Launching Dynamo-lite GUI…"
echo "  Make sure nodes are running first (./start-cluster.sh)"
echo ""

java -cp "$OUT_DIR" com.dynamo.lite.client.DynamoGUI