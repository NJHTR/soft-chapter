#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ENV_FILE="${ENV_FILE:-$SCRIPT_DIR/.env}"
if [[ ! -f "$ENV_FILE" ]]; then
  printf 'missing env file: %s\n' "$ENV_FILE" >&2
  exit 2
fi

# shellcheck disable=SC1090
set -a
. "$ENV_FILE"
set +a

: "${NODE_IP:?set NODE_IP}"
: "${REDIS_HOST:?set REDIS_HOST}"
: "${LIVEKIT_API_KEY:?set LIVEKIT_API_KEY}"
: "${LIVEKIT_CONFIG_PATH:=$SCRIPT_DIR/runtime/livekit.yaml}"

case "$LIVEKIT_CONFIG_PATH" in
  /*) CONFIG_PATH="$LIVEKIT_CONFIG_PATH" ;;
  *) CONFIG_PATH="$SCRIPT_DIR/${LIVEKIT_CONFIG_PATH#./}" ;;
esac
mkdir -p "$(dirname -- "$CONFIG_PATH")"
umask 077

tmp="${CONFIG_PATH}.tmp.$$"
trap 'rm -f "$tmp"' EXIT
{
  printf 'port: %s\n' "${LIVEKIT_CONTAINER_SIGNAL_PORT:-7880}"
  printf 'rtc:\n'
  printf '  tcp_port: %s\n' "${LIVEKIT_CONTAINER_TCP_PORT:-7881}"
  printf '  port_range_start: %s\n' "${LIVEKIT_RTP_MIN_PORT:-51000}"
  printf '  port_range_end: %s\n' "${LIVEKIT_RTP_MAX_PORT:-51050}"
  printf '  node_ip: %s\n' "$NODE_IP"
  printf '  use_external_ip: %s\n' "${LIVEKIT_USE_EXTERNAL_IP:-false}"
  printf 'redis:\n'
  printf '  address: %s:%s\n' "$REDIS_HOST" "${REDIS_PORT:-6379}"
  printf '  db: %s\n' "${REDIS_DB:-0}"
  printf '  use_tls: %s\n' "${REDIS_USE_TLS:-false}"
  printf 'prometheus:\n'
  printf '  port: %s\n' "${LIVEKIT_CONTAINER_METRICS_PORT:-7889}"
  if [[ -n "${LIVEKIT_REGION:-}" ]]; then
    printf 'region: %s\n' "$LIVEKIT_REGION"
  fi
  printf 'logging:\n  level: %s\n' "${LIVEKIT_LOG_LEVEL:-info}"
  if [[ -n "${LIVEKIT_WEBHOOK_URL:-}" ]]; then
    printf 'webhook:\n'
    printf '  api_key: %s\n' "$LIVEKIT_API_KEY"
    printf '  urls:\n    - %s\n' "$LIVEKIT_WEBHOOK_URL"
  fi
} > "$tmp"
mv -f "$tmp" "$CONFIG_PATH"
printf 'rendered LiveKit config: %s\n' "$CONFIG_PATH"
