#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOLUTION_DIR="$ROOT_DIR/solution"
SRC_DIR="$SOLUTION_DIR/src"
LIB_JAR="$SOLUTION_DIR/lib/gson-2.10.1.jar"
BUILD_DIR="$SOLUTION_DIR/build/classes"
STAMP_FILE="$SOLUTION_DIR/build/.compile-stamp"

compile_if_needed() {
  local needs_compile=0

  if [[ ! -f "$LIB_JAR" ]]; then
    echo "Missing dependency jar: $LIB_JAR" >&2
    exit 1
  fi

  if [[ ! -f "$STAMP_FILE" ]] || [[ ! -d "$BUILD_DIR" ]]; then
    needs_compile=1
  fi

  if [[ $needs_compile -eq 0 ]] && find "$SRC_DIR" -name '*.java' -newer "$STAMP_FILE" -print -quit | grep -q .; then
    needs_compile=1
  fi

  if [[ $needs_compile -eq 0 ]] && [[ "$LIB_JAR" -nt "$STAMP_FILE" ]]; then
    needs_compile=1
  fi

  if [[ $needs_compile -eq 1 ]]; then
    rm -rf "$BUILD_DIR"
    mkdir -p "$BUILD_DIR"
    find "$SRC_DIR" -name '*.java' -print0 | sort -z | xargs -0 javac -cp "$LIB_JAR" -d "$BUILD_DIR"
    touch "$STAMP_FILE"
  fi
}
