#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

compile_if_needed

exec java -cp "$BUILD_DIR:$LIB_JAR" boxboxbox.TrainModel "$@"
