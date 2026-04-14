#!/usr/bin/env bash
#
# Manual end-to-end checks for all job types.
#
# Prerequisites:
#   - docker compose: postgres + kafka (or equivalent) running
#   - api-service on API_URL (default http://127.0.0.1:8080)
#   - worker-service running with the same POSTGRES_* and Kafka bootstrap as the API
#   - OPTIONAL: CALLBACK_SECRET — if set in the API, export the same value here so /jobs/callback succeeds
#
# EXTERNAL scenarios use a public HTTPS endpoint that returns 2xx (default: httpbin.org).
# Override with WEBHOOK_URL if your network blocks it.
#
set -euo pipefail

API_URL="${API_URL:-http://127.0.0.1:8080}"
MAX_WAIT_SEC="${MAX_WAIT_SEC:-60}"
WEBHOOK_URL="${WEBHOOK_URL:-https://httpbin.org/post}"

json_field() {
  python3 -c 'import json,sys; print(json.loads(sys.argv[1])[sys.argv[2]])' "$1" "$2"
}

post_job() {
  local body="$1"
  curl -sS -f -X POST "${API_URL}/jobs" -H 'Content-Type: application/json' -d "${body}"
}

get_job() {
  curl -sS -f "${API_URL}/jobs/$1"
}

wait_status() {
  local id="$1"
  local want="$2"
  local deadline=$((SECONDS + MAX_WAIT_SEC))
  while (( SECONDS < deadline )); do
    local body st
    body="$(get_job "${id}")"
    st="$(json_field "${body}" status)"
    if [[ "${st}" == "${want}" ]]; then
      return 0
    fi
    sleep 1
  done
  echo "FAIL: job ${id} did not reach status ${want} within ${MAX_WAIT_SEC}s" >&2
  return 1
}

post_callback() {
  local id="$1"
  local status="$2"
  local detail="$3"
  local payload
  payload="$(python3 -c 'import json,sys; print(json.dumps({"jobId":sys.argv[1],"status":sys.argv[2],"detail":sys.argv[3]}))' "${id}" "${status}" "${detail}")"
  # Avoid "${array[@]}" with set -u when optional headers are empty (unbound variable on some Bash).
  if [[ -n "${CALLBACK_SECRET:-}" ]]; then
    curl -sS -f -X POST "${API_URL}/jobs/callback" \
      -H 'Content-Type: application/json' \
      -H "X-Callback-Secret: ${CALLBACK_SECRET}" \
      -d "${payload}"
  else
    curl -sS -f -X POST "${API_URL}/jobs/callback" \
      -H 'Content-Type: application/json' \
      -d "${payload}"
  fi
}

echo "==1) EMAIL → COMPLETED"
created="$(post_job '{"type":"EMAIL","payloadJson":"{\"to\":\"smoke-email@example.com\"}"}')"
id1="$(json_field "${created}" id)"
echo "    job id=${id1}"
wait_status "${id1}" "COMPLETED"
echo "    OK"

echo "== 2) REPORT → COMPLETED"
created="$(post_job '{"type":"REPORT","payloadJson":"{\"reportType\":\"daily\"}"}')"
id2="$(json_field "${created}" id)"
echo "    job id=${id2}"
wait_status "${id2}" "COMPLETED"
echo "    OK"

echo "== 3) EXTERNAL → RUNNING (webhook 2xx) → callback COMPLETED"
external_body="$(
  WEBHOOK_URL="${WEBHOOK_URL}" python3 - <<'PY'
import json, os

w = os.environ["WEBHOOK_URL"]
inner = json.dumps({"webhookUrl": w})
print(json.dumps({"type": "EXTERNAL", "payloadJson": inner}))
PY
)"
created="$(post_job "${external_body}")"
id3="$(json_field "${created}" id)"
echo "    job id=${id3} webhook=${WEBHOOK_URL}"
wait_status "${id3}" "RUNNING"
post_callback "${id3}" "COMPLETED" "e2e-external-ok"
wait_status "${id3}" "COMPLETED"
echo "    OK"

echo "== 4) EXTERNAL → RUNNING → callback FAILED"
external_body="$(
  WEBHOOK_URL="${WEBHOOK_URL}" python3 - <<'PY'
import json, os

w = os.environ["WEBHOOK_URL"]
inner = json.dumps({"webhookUrl": w})
print(json.dumps({"type": "EXTERNAL", "payloadJson": inner}))
PY
)"
created="$(post_job "${external_body}")"
id4="$(json_field "${created}" id)"
echo "    job id=${id4}"
wait_status "${id4}" "RUNNING"
post_callback "${id4}" "FAILED" "e2e-external-fail"
wait_status "${id4}" "FAILED"
echo "    OK"

echo ""
echo "All manual E2E scenarios passed."
