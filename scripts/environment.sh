#!/usr/bin/env bash
# Sourced only by this project's scripts; never edits shell or Docker defaults.
MANACHECK_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [ -z "${JAVA_HOME:-}" ] && [ -x /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/java ]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
fi
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
if command -v docker >/dev/null 2>&1; then
  MANACHECK_DOCKER="$(command -v docker)"
elif [ -x /Applications/Docker.app/Contents/Resources/bin/docker ]; then
  MANACHECK_DOCKER=/Applications/Docker.app/Contents/Resources/bin/docker
else
  printf '%s\n' 'Install Docker Desktop or a Docker-compatible runtime first.' >&2
  return 78
fi
if [ -z "${DOCKER_HOST:-}" ] && [ -S "$HOME/.colima/manacheck/docker.sock" ]; then
  export DOCKER_HOST="unix://$HOME/.colima/manacheck/docker.sock"
  # Public images need no registry account. Isolate this from Desktop credential helpers.
  if [ -z "${DOCKER_CONFIG:-}" ]; then
    export DOCKER_CONFIG="$MANACHECK_ROOT/.local/docker"
    mkdir -p "$DOCKER_CONFIG"
    if [ ! -f "$DOCKER_CONFIG/config.json" ]; then printf '%s\n' '{"auths":{}}' > "$DOCKER_CONFIG/config.json"; fi
  fi
  export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
fi
manacheck_compose() {
  if "$MANACHECK_DOCKER" compose version >/dev/null 2>&1; then "$MANACHECK_DOCKER" compose "$@"
  elif [ -x /Applications/Docker.app/Contents/Resources/cli-plugins/docker-compose ]; then /Applications/Docker.app/Contents/Resources/cli-plugins/docker-compose "$@"
  else printf '%s\n' 'Docker Compose v2 is required.' >&2; return 78; fi
}
