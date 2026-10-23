#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/environment.sh"
cd "$MANACHECK_ROOT"
"$MANACHECK_DOCKER" info --format '{{.ServerVersion}}' >/dev/null
exec ./mvnw -B -Pbrowser clean verify "$@"
