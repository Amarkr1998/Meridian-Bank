#!/bin/bash
# Creates one PostgreSQL database per service on first container start.
# Runs automatically via /docker-entrypoint-initdb.d — only executes against
# a fresh, empty data volume (see infrastructure/postgres/README.md).
set -euo pipefail

DATABASES=(
  auth_service
  customer_kyc_service
  account_service
  payment_service
  ledger_service
  fraud_risk_service
  notification_service
  audit_service
)

for db in "${DATABASES[@]}"; do
  echo "Creating database '${db}' (owner: ${POSTGRES_USER}) if it does not exist..."
  psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "postgres" <<-EOSQL
    SELECT 'CREATE DATABASE ${db} OWNER ${POSTGRES_USER}'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '${db}')\gexec
EOSQL
done

echo "Meridian Bank: all per-service databases are ready."
