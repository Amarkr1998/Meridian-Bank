#!/bin/bash
# Creates the Meridian Bank topic catalog (see docs/kafka/topics.md) against the
# local single-node KRaft broker. Run by the one-shot `kafka-init` compose service
# after the broker reports healthy. Safe to re-run — --if-not-exists makes it idempotent.
set -euo pipefail

BOOTSTRAP_SERVER="kafka:19092"
PARTITIONS=3
REPLICATION_FACTOR=1

TOPICS=(
  "customer.created"
  "kyc.updated"
  "account.created"
  "account.approved"
  "payment.initiated"
  "payment.completed"
  "payment.failed"
  "payment.reversed"
  "fraud.detected"
  "notification.requested"
  "audit.event"
  "reconciliation.completed"
  # Dead-letter topics (Phase 8: OutboxPublisher's producer-side retry/DLQ — see
  # docs/architecture/kafka-architecture.md "Retry + DLQ"). One per event type actually produced
  # so far; a future phase's own outbox wiring adds its own DLQ topic alongside it, same as here.
  "customer.created.DLQ"
  "kyc.updated.DLQ"
  "account.created.DLQ"
  "account.approved.DLQ"
  "payment.initiated.DLQ"
  "payment.completed.DLQ"
  "payment.failed.DLQ"
  "fraud.detected.DLQ"
  # Phase 11: audit-service is this project's first Kafka *consumer* — this is a consumer-side
  # DLQ (Spring Kafka's DeadLetterPublishingRecoverer), not a producer-side one like the DLQs
  # above, but it follows the identical <topic>.DLQ naming convention.
  "audit.event.DLQ"
  # Phase 12: ledger-service's own outbox (reconciliation.completed) — its first use of the
  # outbox pattern.
  "reconciliation.completed.DLQ"
  # Phase 13: notification-service is this project's second Kafka consumer — its consumer-side
  # DLQs for payment.completed/payment.failed/kyc.updated/account.approved/fraud.detected already
  # exist above (shared with those topics' producer-side DLQ, same convergence as audit.event.DLQ
  # in Phase 11 — a `<topic>.DLQ` topic is "anything that went wrong with this event type,"
  # whichever side of the pipe it happened on). notification.requested had no producer before this
  # phase (customer-kyc-service's SupportRequestService is its first), so its DLQ is new here.
  "notification.requested.DLQ"
)

for topic in "${TOPICS[@]}"; do
  echo "Ensuring topic '${topic}' exists..."
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server "${BOOTSTRAP_SERVER}" \
    --create --if-not-exists --topic "${topic}" \
    --partitions "${PARTITIONS}" --replication-factor "${REPLICATION_FACTOR}"
done

echo "Meridian Bank: Kafka topic bootstrap complete. Current topics:"
/opt/kafka/bin/kafka-topics.sh --bootstrap-server "${BOOTSTRAP_SERVER}" --list
