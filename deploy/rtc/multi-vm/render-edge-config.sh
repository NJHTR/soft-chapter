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

: "${API_UPSTREAMS:?set API_UPSTREAMS (space-separated host:port values)}"
CONFIG_PATH="${EDGE_CONFIG_PATH:-$SCRIPT_DIR/runtime/Caddyfile}"
case "$CONFIG_PATH" in
  /*) ;;
  *) CONFIG_PATH="$SCRIPT_DIR/${CONFIG_PATH#./}" ;;
esac
mkdir -p "$(dirname -- "$CONFIG_PATH")"
umask 077
tmp="${CONFIG_PATH}.tmp.$$"
trap 'rm -f "$tmp"' EXIT
{
  printf ':%s {\n' "${EDGE_PORT:-8080}"
  printf '  respond /healthz 200\n'
  printf '  reverse_proxy %s\n' "$API_UPSTREAMS"
  printf '}\n'
} > "$tmp"
mv -f "$tmp" "$CONFIG_PATH"
printf 'rendered edge config: %s\n' "$CONFIG_PATH"
