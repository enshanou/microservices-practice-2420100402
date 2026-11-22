#!/usr/bin/env bash
set +x
set -euo pipefail

base_url=${GATEWAY_URL:-http://127.0.0.1:8080}
base_url=${base_url%/}
body=$(mktemp)
trap 'rm -f "$body"' EXIT

fail() {
  printf 'FAIL %s\n' "$*" >&2
  exit 1
}

command -v curl >/dev/null || fail 'curl is required'
command -v node >/dev/null || fail 'Node.js is required'

request() {
  local expected=$1 label=$2 path=$3 status
  shift 3
  status=$(curl --silent --show-error --connect-timeout 5 --max-time 20 \
    --output "$body" --write-out '%{http_code}' "$@" "$base_url$path") ||
    fail "$label (request failed)"
  [[ $status == "$expected" ]] || fail "$label (expected HTTP $expected, got $status)"
  printf 'PASS %-48s HTTP %s\n' "$label" "$status"
}

login() {
  local expected=$1 label=$2 username=$3 password=$4
  printf '%s' "$password" | node -e \
    'const fs = require("node:fs"); process.stdout.write(JSON.stringify({
      username: process.argv[1], password: fs.readFileSync(0, "utf8")
    }));' "$username" | request "$expected" "$label" /api/auth/login \
      --header 'Content-Type: application/json' --data-binary @-
}

read_token() {
  node -e '
    const data = JSON.parse(require("node:fs").readFileSync(process.argv[1], "utf8"));
    if (data.role !== process.argv[2] || data.tokenType !== "Bearer" || data.expiresIn !== 900 ||
        !/^[\w-]+\.[\w-]+\.[\w-]+$/.test(data.accessToken)) process.exit(1);
    process.stdout.write(data.accessToken);
  ' "$body" "$1"
}

printf '\n[Gateway and anonymous access]\n'
request 200 'gateway health GET /api/health' /api/health
request 401 'anonymous GET /api/courses' /api/courses
request 401 'anonymous request with forged identity headers' /api/auth/me \
  -H 'X-Auth-User: week09-admin' -H 'X-Auth-Role: ADMIN'

while [[ ! ${AUTH_USER_PASSWORD:-} =~ [^[:space:]] ]]; do
  IFS= read -rsp 'week09-user password: ' AUTH_USER_PASSWORD || fail 'Missing USER password'
  printf '\n'
  if [[ ! $AUTH_USER_PASSWORD =~ [^[:space:]] ]]; then
    printf 'Password is empty. Paste the USER password, then press Enter.\n'
  fi
done
while [[ ! ${AUTH_ADMIN_PASSWORD:-} =~ [^[:space:]] ]]; do
  IFS= read -rsp 'week09-admin password: ' AUTH_ADMIN_PASSWORD || fail 'Missing ADMIN password'
  printf '\n'
  if [[ ! $AUTH_ADMIN_PASSWORD =~ [^[:space:]] ]]; then
    printf 'Password is empty. Paste the ADMIN password, then press Enter.\n'
  fi
done

printf '\n[Login and JWT]\n'
login 401 'incorrect login password' week09-user "invalid-${AUTH_USER_PASSWORD}"
login 200 'USER login POST /api/auth/login' week09-user "$AUTH_USER_PASSWORD"
user_token=$(read_token USER) || fail 'Expected a USER JWT'
printf 'PASS USER bearer token, role and 900-second lifetime\n'
login 200 'ADMIN login POST /api/auth/login' week09-admin "$AUTH_ADMIN_PASSWORD"
admin_token=$(read_token ADMIN) || fail 'Expected an ADMIN JWT'
printf 'PASS ADMIN bearer token, role and 900-second lifetime\n'
unset AUTH_USER_PASSWORD AUTH_ADMIN_PASSWORD

printf '\n[Authorization and service routes]\n'
token_signature=${user_token##*.}
signature_start=A
if [[ ${token_signature:0:1} == A ]]; then signature_start=B; fi
invalid_token="${user_token%.*}.$signature_start${token_signature:1}"
request 401 'protected endpoint with invalid JWT signature' /api/auth/me \
  -H "Authorization: Bearer $invalid_token"
request 200 'USER GET /api/auth/me with forged headers' /api/auth/me \
  -H "Authorization: Bearer $user_token" -H 'x-auth-user: week09-admin' -H 'X-Auth-Role: ADMIN'
node -e '
  const data = JSON.parse(require("node:fs").readFileSync(process.argv[1], "utf8"));
  if (data.username !== "week09-user" || data.role !== "USER") process.exit(1);
' "$body" || fail 'Gateway did not overwrite client identity headers with verified claims'
printf 'PASS downstream received username=week09-user role=USER\n'
request 200 'USER GET /api/courses -> course-service' /api/courses -H "Authorization: Bearer $user_token"
request 200 'USER GET /api/tasks -> task-service' /api/tasks -H "Authorization: Bearer $user_token"
request 403 'USER GET /api/users with forged ADMIN role' /api/users \
  -H "Authorization: Bearer $user_token" -H 'X-Auth-Role: ADMIN'
request 403 'USER DELETE /api/courses/{id}' /api/courses/00000000-0000-0000-0000-000000000000 \
  -X DELETE -H "Authorization: Bearer $user_token"
request 200 'ADMIN GET /api/users -> user-service' /api/users -H "Authorization: Bearer $admin_token"
printf '\nPASS Week 09 auth tests\n'
