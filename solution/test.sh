#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

compile_if_needed

java -cp "$BUILD_DIR:$LIB_JAR" boxboxbox.SelfTest
if [[ -f "$SOLUTION_DIR/model_single.json" ]]; then
  if command -v jq >/dev/null 2>&1; then
    bash "$SOLUTION_DIR/run.sh" < "$ROOT_DIR/data/test_cases/inputs/test_001.json" | jq empty >/dev/null
    bash "$SOLUTION_DIR/run.sh" < "$ROOT_DIR/data/test_cases/inputs/test_050.json" | jq empty >/dev/null
  else
    bash "$SOLUTION_DIR/run.sh" < "$ROOT_DIR/data/test_cases/inputs/test_001.json" >/dev/null
    bash "$SOLUTION_DIR/run.sh" < "$ROOT_DIR/data/test_cases/inputs/test_050.json" >/dev/null
  fi
fi
echo "solution/test.sh: all checks passed"
