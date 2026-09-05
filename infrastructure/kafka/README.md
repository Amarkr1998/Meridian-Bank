# infrastructure/kafka

**Status:** Implemented (Phase 1 — single-node broker + topic bootstrap; outbox publishing,
consumer retry/DLQ wiring land in Phase 8).

## What's here

- The `kafka` service in `docker-compose.yml` runs a single-node Kafka broker in **KRaft mode**
  (`apache/kafka:3.8.0`) — no Zookeeper. Combined broker+controller roles, which is appropriate for
  local/demo use only.
- `create-topics.sh` is run once by the one-shot `kafka-init` compose service after `kafka` reports
  healthy. It creates the full topic catalog from [docs/kafka/topics.md](../../docs/kafka/topics.md)
  (3 partitions, replication factor 1 — single broker) and is safe to re-run (`--if-not-exists`).
  `KAFKA_AUTO_CREATE_TOPICS_ENABLE` is disabled so topics only ever come from this script, not an
  accidental producer typo.

## Local connection

| | |
|---|---|
| Bootstrap server (from host) | `localhost:9092` (override via `KAFKA_PORT` in `.env`) |
| Bootstrap server (from another container on `meridian-net`) | `kafka:19092` |

## Verifying locally

```bash
docker compose up -d
docker compose logs -f kafka-init   # should end with the full topic list and exit 0
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:19092 --list
```

## Notes for later phases

- DLQ topics are added alongside the outbox/retry implementation in Phase 8, not created ahead of
  that — see [ADR-0005](../../docs/adr/0005-outbox-pattern.md).
- A single broker with replication factor 1 has no fault tolerance; that's an accepted trade-off for
  local/demo use and is called out here so it isn't mistaken for a production topology
  recommendation.
