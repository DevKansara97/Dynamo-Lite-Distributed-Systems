#!/bin/bash
# ─────────────────────────────────────────────────────────────────
# compile.sh  —  Compile the entire Dynamo-lite project
# ─────────────────────────────────────────────────────────────────

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUT_DIR="$PROJECT_DIR/out"

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; RESET='\033[0m'

echo ""
echo "  Compiling Dynamo-lite…"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

if find "$PROJECT_DIR/src" -name "*.java" | xargs javac -d "$OUT_DIR" 2>&1; then
    count=$(find "$OUT_DIR" -name "*.class" | wc -l | tr -d ' ')
    echo -e "${GREEN}  ✓ Success — ${count} class files compiled${RESET}"
    echo ""
else
    echo -e "${RED}  ✗ Compilation failed${RESET}"
    echo ""
    exit 1
fi