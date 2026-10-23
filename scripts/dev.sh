#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/environment.sh"
cd "$MANACHECK_ROOT"
java -version
manacheck_compose up -d --wait
exec ./mvnw -q spring-boot:run
