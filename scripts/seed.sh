#!/usr/bin/env bash
set -euo pipefail

API_PORT="${API_PORT:-8080}"
BASE="http://localhost:${API_PORT}/api/v1"
EMAIL="${SEED_EMAIL:-demo@example.com}"
PASSWORD="${SEED_PASSWORD:-demo12345}"

for _ in $(seq 1 60); do
  if curl -fsS "${BASE}/health" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

if ! curl -fsS "${BASE}/health" >/dev/null 2>&1; then
  echo "api did not become healthy at ${BASE}/health" >&2
  exit 1
fi

response=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "${BASE}/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\",\"name\":\"Demo User\"}")

case "$response" in
  201) echo "seeded demo account: ${EMAIL} / ${PASSWORD}" ;;
  409) echo "demo account already present: ${EMAIL}" ;;
  *)   echo "unexpected response while seeding: HTTP ${response}" >&2; exit 1 ;;
esac
