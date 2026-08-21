#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ENV_FILE="${ENV_FILE:-$SCRIPT_DIR/.env}"
if [[ ! -f "$ENV_FILE" ]]; then
  printf 'missing env file: %s (copy .env.example first)\n' "$ENV_FILE" >&2
  exit 2
fi

# Load local, operator-owned settings for project naming and build selection.
# shellcheck disable=SC1090
set -a
. "$ENV_FILE"
set +a

role="${1:-}"
action="${2:-up}"
if [[ -z "$role" || ! "$role" =~ ^(api|livekit|turn|edge)$ ]]; then
  printf 'usage: %s <api|livekit|turn|edge> [up|down|restart|status|config]\n' "$0" >&2
  exit 2
fi

case "$role" in
  api) compose_file="$SCRIPT_DIR/docker-compose.api.yml" ;;
  livekit) compose_file="$SCRIPT_DIR/docker-compose.livekit.yml" ;;
  turn) compose_file="$SCRIPT_DIR/docker-compose.turn.yml" ;;
  edge) compose_file="$SCRIPT_DIR/docker-compose.edge.yml" ;;
esac

project="${COMPOSE_PROJECT_NAME:-douyin-rtc-$role}"
compose=(docker compose --project-name "$project" --env-file "$ENV_FILE" -f "$compose_file")
cd "$SCRIPT_DIR"

case "$action" in
  config)
    if [[ "$role" == livekit ]]; then
      ENV_FILE="$ENV_FILE" bash "$SCRIPT_DIR/render-livekit-config.sh"
    elif [[ "$role" == edge ]]; then
      ENV_FILE="$ENV_FILE" bash "$SCRIPT_DIR/render-edge-config.sh"
    fi
    "${compose[@]}" config --quiet
    ;;
  up)
    if [[ "$role" == livekit ]]; then
      ENV_FILE="$ENV_FILE" bash "$SCRIPT_DIR/render-livekit-config.sh"
    elif [[ "$role" == edge ]]; then
      ENV_FILE="$ENV_FILE" bash "$SCRIPT_DIR/render-edge-config.sh"
    fi
    build_args=()
    if [[ "${BUILD_API:-true}" == "false" || "${BUILD_API:-true}" == "0" ]]; then
      build_args+=(--no-build)
    fi
    "${compose[@]}" up -d "${build_args[@]}"
    "${compose[@]}" ps
    ;;
  down)
    "${compose[@]}" down
    ;;
  restart)
    "${compose[@]}" restart
    ;;
  status)
    "${compose[@]}" ps
    ;;
  *)
    printf 'unknown action: %s\n' "$action" >&2
    exit 2
    ;;
esac
