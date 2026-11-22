#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

for command in docker curl node; do
  command -v "$command" >/dev/null || { echo "FAIL: $command is required" >&2; exit 1; }
done

user_url=http://127.0.0.1:18081
course_url=http://127.0.0.1:18082
task_url=http://127.0.0.1:18083
course_services=(course-service course-service-replica)
restore_courses=false

cleanup() {
  if [[ $restore_courses == true ]]; then
    echo 'Restoring course-service instances after interruption or failure'
    docker compose start "${course_services[@]}" >&2
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

request() {
  local method=$1 url=$2 payload=$3 raw
  raw=$(curl --silent --show-error --connect-timeout 3 --max-time 12 \
    -X "$method" -H 'Content-Type: application/json' -d "$payload" \
    -w '\n%{http_code}' "$url") || fail "$method $url"
  http_status=${raw##*$'\n'}
  http_body=${raw%$'\n'*}
}

expect_status() {
  [[ $http_status == "$1" ]] || fail "$2: expected HTTP $1, got $http_status $http_body"
}

json_field() {
  node -e '
    const value = JSON.parse(require("node:fs").readFileSync(0, "utf8"))[process.argv[1]];
    if (typeof value !== "string" || value.length === 0) process.exit(1);
    console.log(value);
  ' "$1" <<< "$http_body"
}

wait_for_health() {
  local url=$1 attempt
  for ((attempt=0; attempt<60; attempt++)); do
    if curl --fail --silent --connect-timeout 2 --max-time 3 "$url/actuator/health" >/dev/null; then
      return
    fi
    sleep 2
  done
  fail "$url/actuator/health did not become ready"
}

# Retry explicit 503 responses during registration. A transport timeout aborts.
post_when_discovered() {
  local url=$1 payload=$2 attempt
  for ((attempt=0; attempt<30; attempt++)); do
    request POST "$url" "$payload"
    [[ $http_status == 503 ]] || return 0
    sleep 2
  done
  fail "$url stayed unavailable: $http_body"
}

verify_task() {
  local label=$1 task_id
  expect_status 201 "$label"
  task_id=$(json_field id) || fail "$label: missing task id"
  [[ $(json_field courseId) == "$course_id" && $(json_field status) == PENDING ]] ||
    fail "$label: unexpected task JSON $http_body"
  printf 'PASS %s: HTTP 201 %s\n' "$label" "$http_body"

  request GET "$task_url/api/tasks/$task_id" ''
  expect_status 200 'read persisted task'
  [[ $(json_field id) == "$task_id" && $(json_field courseId) == "$course_id" &&
     $(json_field status) == PENDING ]] || fail 'persisted task JSON mismatch'

  request POST "$task_url/api/tasks/$task_id/cancel" ''
  expect_status 200 'cancel test task to release the active slot'
  [[ $(json_field status) == FAILED ]] || fail 'test task was not canceled'
}

task_count() {
  request GET "$task_url/api/tasks" ''
  expect_status 200 'list tasks'
  node -e '
    const tasks = JSON.parse(require("node:fs").readFileSync(0, "utf8"));
    console.log(tasks.filter(task => task.courseId === process.argv[1]).length);
  ' "$1" <<< "$http_body"
}

wait_for_health "$user_url"
wait_for_health "$course_url"
wait_for_health "$task_url"

request POST "$user_url/api/users" '{"displayName":"Week 08 E2E"}'
expect_status 201 'create test user'
user_id=$(json_field id) || fail 'missing user id'
post_when_discovered "$course_url/api/courses" \
  "{\"ownerId\":\"$user_id\",\"title\":\"Week 08 E2E\",\"sourceReference\":\"week08-e2e\"}"
expect_status 201 'create test course'
course_id=$(json_field id) || fail 'missing course id'
[[ $(json_field ownerId) == "$user_id" ]] || fail 'test course has the wrong owner'
payload="{\"courseId\":\"$course_id\"}"
printf 'Test courseId=%s\n' "$course_id"

post_when_discovered "$task_url/api/tasks" "$payload"
verify_task 'Feign task creation #1'
for ((run=2; run<=12; run++)); do
  request POST "$task_url/api/tasks" "$payload"
  verify_task "Feign task creation #$run"
  if (( run < 4 )); then
    continue
  fi
  logs=$(docker compose logs --no-color task-service)
  matches=$(grep -F "Feign course-service courseId=$course_id instance=" <<< "$logs") ||
    fail 'task-service did not log any successful Feign responses for this course'
  if grep -Eq 'instance=course-1[[:space:]]*$' <<< "$matches" &&
     grep -Eq 'instance=course-2[[:space:]]*$' <<< "$matches"; then
    break
  fi
  (( run < 12 )) || fail 'expected Feign responses from course-1 and course-2'
  sleep 2
done
echo 'PASS all created tasks were readable before cancellation'
printf '%s\n' "$matches"
echo 'PASS load balancing: Feign received responses from course-1 and course-2'

missing_id=$(node -p 'require("node:crypto").randomUUID()')
request POST "$task_url/api/tasks" "{\"courseId\":\"$missing_id\"}"
expect_status 404 'missing course'
[[ $(json_field error) == 'Course does not exist' ]] || fail "unexpected 404 body $http_body"
printf 'PASS missing course: HTTP 404 %s\n' "$http_body"
[[ $(task_count "$missing_id") == 0 ]] || fail 'task was written for a missing course'

before=$(task_count "$course_id")
restore_courses=true
docker compose stop "${course_services[@]}"
request POST "$task_url/api/tasks" "$payload"
expect_status 503 'both course instances stopped'
[[ $(json_field error) == 'course-service unavailable or timed out' ]] ||
  fail "unexpected 503 body $http_body"
printf 'PASS both course instances stopped: HTTP 503 %s\n' "$http_body"
after=$(task_count "$course_id")
[[ $after == "$before" ]] || fail "task count changed from $before to $after during outage"
printf 'PASS no task written during outage: before=%s after=%s\n' "$before" "$after"

docker compose start "${course_services[@]}"
restore_courses=false
post_when_discovered "$task_url/api/tasks" "$payload"
verify_task 'task creation after recovery'
[[ $(task_count "$course_id") -eq $((before + 1)) ]] || fail 'expected one task after recovery'
echo 'PASS Week 08 E2E'
