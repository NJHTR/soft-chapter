# RTC multi-VM deployment

This directory provides host-independent role stacks for three Linux VMs. It does not
start MySQL, Redis, or Kafka. Point every API and LiveKit node at the same private
dependency endpoints, then run only the role needed on each VM.

## Suggested test topology

| VM | Role | Example values |
|---|---|---|
| `192.168.59.128` | API replica and HTTP/WebSocket edge | `RTC_NODE_ID=api-a` |
| `192.168.59.129` | API replica, LiveKit A, optional TURN A | `NODE_IP=192.168.59.129`, `LIVEKIT_REGION=cn-east-1` |
| `192.168.59.130` | API replica, LiveKit B, optional TURN B | `NODE_IP=192.168.59.130`, `LIVEKIT_REGION=cn-east-2` |

The validated local lab keeps Redis and Kafka on the Windows/VMware host at
`192.168.59.1` and MySQL on the existing remote host. A server deployment may instead
place those shared dependencies on dedicated private hosts. Kafka must advertise a
LAN address; `localhost` advertised listeners are not usable by the VMs. Keep `TZ` and
`-Duser.timezone` aligned with the MySQL session timezone;
the example uses `Asia/Shanghai` so Java `LocalDateTime` and SQL `NOW()` produce the
same 180-second ringing window.

## Install and start an API replica

Copy the repository to `/opt/douyin` (or build and transfer `API_IMAGE`), then on each
API VM:

```bash
cd /opt/douyin/deploy/rtc/multi-vm
cp .env.example .env
# Edit .env: DB_URL, DB_USERNAME, DB_PASSWORD, REDIS_*, KAFKA_BOOTSTRAP_SERVERS,
# JWT_SECRET, RTC_NODE_ID, and the LiveKit credentials.
bash deploy.sh api config
bash deploy.sh api up
bash deploy.sh api status
bash validate.sh api
```

For a disposable acceptance database, run `seed-test-users.sql` once with a MySQL
client. It creates A/B/C accounts under `example.test` and mutual A/B/A-C follow
relations; all three passwords are `password`.

`BUILD_API=true` builds `server/Dockerfile` on the VM. For an image transferred with
`docker save`/`docker load`, set `BUILD_API=false` and `API_IMAGE` to the loaded tag.
The env file is also read by the Bash renderer, so keep it shell-compatible and quote
values containing `&`, spaces, `#`, or shell metacharacters (the example `DB_URL` is
already quoted).
The API listens on `API_HOST_PORT` (default `9191`). Put an HTTPS/WSS reverse proxy or
load balancer in front of the replicas; this stack intentionally does not claim to be
the global entrypoint.

## Start a LiveKit node

Use a separate `.env` on each media VM. Set a unique `NODE_IP`, `LIVEKIT_REGION`, and
RTP range, and point `REDIS_HOST` to the shared Redis. The script renders a host-local
config without writing the Redis password to that YAML; the password is passed to the
container at startup.

```bash
bash deploy.sh livekit config
bash deploy.sh livekit up
bash deploy.sh livekit status
bash validate.sh livekit
```

Open TCP `7880`, `7881`, metrics `7889`, and the configured UDP RTP range on the VM
firewall. `LIVEKIT_WEBHOOK_URL` may point to the API replica/LB. The two LiveKit nodes
must use the same API key/secret and Redis, but different advertised IPs and regions.

## Optional TURN node

```bash
bash deploy.sh turn config
bash deploy.sh turn up
bash validate.sh turn
```

The TURN stack uses Linux host networking so relay candidates contain the VM address;
it is self-contained and starts without TLS because the bootstrap deployment has no
certificate files.
Open TCP/UDP `TURN_HOST_PORT` and the configured relay UDP range. Set
`TURN_LISTENING_IP`, `TURN_RELAY_IP`, and `TURN_EXTERNAL_IP` to the browser-reachable
address; the external address can differ from the VM address when the VM is behind NAT.
TLS TURN (5349) is intentionally disabled by this bootstrap stack because it requires a
real certificate.

## Optional API edge

Run the edge role on the VM that should be the LAN entry point. It proxies both REST and
WebSocket traffic to every API replica; add another `host:port` to `API_UPSTREAMS` when a
new replica is deployed.

```bash
# API_UPSTREAMS is a space-separated list in .env
bash deploy.sh edge config
bash deploy.sh edge up
bash validate.sh edge
```

The bootstrap listener is plain HTTP on `EDGE_PORT` (default `8080`); `/healthz` is a
local edge-only health response. Put it behind an HTTPS reverse proxy and use a real
certificate before exposing it beyond the test LAN.

## Validation and operational notes

`config` only checks Compose interpolation. Before starting, test from each VM that
the configured MySQL (`3306`), Redis (`6379`) and Kafka (`9092`) endpoints are reachable.
Keep those ports private. API replicas are stateless for durable RTC calls, but all
must share the same database, Redis and Kafka. Existing rooms are not transparently
migrated when a LiveKit node is stopped; drain a node before maintenance.

The stacks are intended for a reproducible test deployment, not a production capacity
claim. Real multi-host load, TLS, NAT/TURN traversal, and 10k/100k/1m pending-call
capacity remain separate acceptance work.
