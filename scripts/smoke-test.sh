#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Python runs in a disposable container; no local Python or curl is needed.
mount_root=$(pwd)
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) mount_root=$(pwd -W) ;; esac
run_checks() {
  MSYS_NO_PATHCONV=1 docker run --rm --network courselens-week06_default \
    --mount "type=bind,source=$mount_root,target=/work" python:3.13-alpine \
    python -u /work/scripts/smoke-test.py "$1"
}
run_checks core
restore() { docker compose start course-service >/dev/null; }
trap restore EXIT
docker compose stop course-service
run_checks unavailable
docker compose start course-service
run_checks recovered
trap - EXIT
echo 'PASS all smoke checks'
