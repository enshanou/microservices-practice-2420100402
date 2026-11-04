#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

command -v curl >/dev/null 2>&1 || {
  echo 'FAIL: curl is required' >&2
  exit 1
}

tmp_dir=$(mktemp -d)
trap 'rm -rf "$tmp_dir"' EXIT

console_url="${NACOS_CONSOLE_URL:-http://localhost:18080/}"
echo "Checking Nacos console: $console_url"
curl --fail --silent --show-error --location \
  --connect-timeout 3 --max-time 10 \
  "$console_url" -o "$tmp_dir/console.html"
test -s "$tmp_dir/console.html" || {
  echo 'FAIL: empty console response' >&2
  exit 1
}
grep -Eiq '<!doctype html|<html' "$tmp_dir/console.html" || {
  echo 'FAIL: console did not return an HTML page' >&2
  exit 1
}
echo 'PASS Nacos console HTTP page'

check_health() {
  local service="$1" url="$2"
  echo "Checking $service: $url"
  curl --fail --silent --show-error \
    --connect-timeout 3 --max-time 10 \
    "$url" -o "$tmp_dir/health.json"
  if ! grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' "$tmp_dir/health.json"; then
    echo "FAIL $service: health is not UP" >&2
    cat "$tmp_dir/health.json" >&2
    return 1
  fi
  echo "PASS $service health UP"
}

check_health user-service "${USER_HEALTH_URL:-http://localhost:18081/actuator/health}"
check_health course-service "${COURSE_HEALTH_URL:-http://localhost:18082/actuator/health}"
check_health task-service "${TASK_HEALTH_URL:-http://localhost:18083/actuator/health}"
echo 'PASS all smoke checks'
