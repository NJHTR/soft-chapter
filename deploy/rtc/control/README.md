# RTC control-plane local deployment

This stack is the portable control-plane baseline for RTC-CALL-001. It starts MySQL,
Redis, single-node Kafka KRaft, and the Spring API. Media is still served by the separate
LiveKit/SRS/TURN stack; RTP never passes through Spring, Kafka, or chat WebSocket.

## Local computer

```powershell
Copy-Item deploy/rtc/control/.env.example deploy/rtc/control/.env
# Edit deploy/rtc/control/.env and replace every change-me value.
docker compose --env-file deploy/rtc/control/.env -f docker-compose.rtc-control.yml up -d --build
docker compose --env-file deploy/rtc/control/.env -f docker-compose.rtc-control.yml ps
```

The first MySQL initialization runs the repository SQL files in filename order. A named
volume means later restarts do not rerun migrations. To recreate the disposable local
database, stop the stack and remove only the `rtc-mysql` volume.

The disposable database also creates four local browser identities (`rtc-a@local.test` through
`rtc-d@local.test`). Their password is `password`; change or remove `seed-rtc-users.sql` before
using this compose file outside a local test network.

## Horizontal API replicas

`rtc-api` is stateless with respect to durable calls. MySQL is the durable fact, Redis is
the rebuildable timeout/presence index, and Kafka/outbox is at-least-once event delivery.
For a local smoke test:

```powershell
docker compose --env-file deploy/rtc/control/.env -f docker-compose.rtc-control.yml up -d --scale rtc-api=2 rtc-api
```

The included Caddy `rtc-edge` is the local L7 entrypoint, so replicas do not bind host ports.
For production, replace it with an external load balancer/reverse proxy in front of the replicas and use a shared MySQL/Redis/Kafka
deployment. Copy the same image and environment contract to each server, changing only
`RTC_NODE_ID` and provider/network advertised addresses.

Cross-instance WebSocket notification uses Redis Pub/Sub with `origin_node` suppression.
It is intentionally non-durable: a lost notification is recovered through
`GET /api/rtc/calls/active` or the WebSocket reconnect reconciliation. Global Presence is
not yet complete; `SessionManager.isOnline` remains JVM-local until Redis heartbeat leases
and stale-connection fencing are delivered.

This gives a scalable deployment shape, not a linear-capacity guarantee: `nM` requires
real multi-server load, network, broker, database and media measurements.
