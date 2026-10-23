#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/environment.sh"
cd "$MANACHECK_ROOT"
command -v ffmpeg >/dev/null
./mvnw -q dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=.local/test-classpath
manacheck_classpath="$(cat .local/test-classpath)"
exec java --class-path "target/classes:$manacheck_classpath" scripts/RecordDemo.java "$@"
