#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ENV_FILE="${ENV_FILE:-$SCRIPT_DIR/.env}"
role="${1:-api}"
if [[ ! "$role" =~ ^(api|livekit|turn|edge)$ ]]; then
  printf 'usage: %s <api|livekit|turn|edge>\n' "$0" >&2
  exit 2
fi
if [[ ! -f "$ENV_FILE" ]]; then
  printf 'missing env file: %s\n' "$ENV_FILE" >&2
  exit 2
fi

# shellcheck disable=SC1090
set -a
. "$ENV_FILE"
set +a

case "$role" in
  api) compose_file="$SCRIPT_DIR/docker-compose.api.yml" ;;
  livekit) compose_file="$SCRIPT_DIR/docker-compose.livekit.yml" ;;
  turn) compose_file="$SCRIPT_DIR/docker-compose.turn.yml" ;;
  edge) compose_file="$SCRIPT_DIR/docker-compose.edge.yml" ;;
esac

cd "$SCRIPT_DIR"
docker compose --project-name "${COMPOSE_PROJECT_NAME:-douyin-rtc-$role}" \
  --env-file "$ENV_FILE" -f "$compose_file" config --quiet

if ! command -v nc >/dev/null 2>&1; then
  printf 'compose config OK; nc is unavailable, skipping endpoint probes\n'
  exit 0
fi

probe() {
  local name="$1" host="$2" port="$3"
  if nc -z -w 3 "$host" "$port" >/dev/null 2>&1; then
    printf '%s reachable (%s:%s)\n' "$name" "$host" "$port"
  else
    printf '%s unreachable (%s:%s)\n' "$name" "$host" "$port" >&2
    return 1
  fi
}

case "$role" in
  api)
    : "${DB_URL:?set DB_URL}"
    : "${REDIS_HOST:?set REDIS_HOST}"
    : "${KAFKA_BOOTSTRAP_SERVERS:?set KAFKA_BOOTSTRAP_SERVERS}"
    if [[ -n "${RTC_LIVEKIT_API_SECRET:-}" && -n "${RTC_LIVEKIT_WEBHOOK_SECRET:-}" &&
          "$RTC_LIVEKIT_API_SECRET" != "$RTC_LIVEKIT_WEBHOOK_SECRET" ]]; then
      printf 'RTC_LIVEKIT_WEBHOOK_SECRET must match RTC_LIVEKIT_API_SECRET\n' >&2
      exit 1
    fi
    db_authority="${DB_URL#jdbc:mysql://}"
    db_authority="${db_authority%%/*}"
    db_host="${db_authority%%:*}"
    db_port="${db_authority##*:}"
    [[ "$db_port" == "$db_authority" ]] && db_port=3306
    kafka_authority="${KAFKA_BOOTSTRAP_SERVERS%%,*}"
    kafka_host="${kafka_authority%%:*}"
    kafka_port="${kafka_authority##*:}"
    [[ "$kafka_port" == "$kafka_authority" ]] && kafka_port=9092
    probe mysql "$db_host" "$db_port"
    probe redis "$REDIS_HOST" "${REDIS_PORT:-6379}"
    probe kafka "$kafka_host" "$kafka_port"
    ;;
  livekit)
    : "${NODE_IP:?set NODE_IP}"
    : "${REDIS_HOST:?set REDIS_HOST}"
    # Redis authentication is optional for the disposable LAN deployment.
    : "${REDIS_PASSWORD:=}"
    : "${LIVEKIT_API_KEY:?set LIVEKIT_API_KEY}"
    : "${LIVEKIT_API_SECRET:?set LIVEKIT_API_SECRET}"
    probe redis "$REDIS_HOST" "${REDIS_PORT:-6379}"
    ;;
  turn)
    : "${TURN_EXTERNAL_IP:?set TURN_EXTERNAL_IP}"
    : "${TURN_SHARED_SECRET:?set TURN_SHARED_SECRET}"
    ;;
  edge)
    : "${API_UPSTREAMS:?set API_UPSTREAMS}"
    : "${EDGE_PORT:=8080}"
    probe edge "127.0.0.1" "$EDGE_PORT"
    ;;
esac

printf '%s validation OK\n' "$role"
