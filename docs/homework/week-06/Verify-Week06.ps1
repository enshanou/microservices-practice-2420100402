param([switch]$Persistence)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
Push-Location $root
function Check-Exit { if ($LASTEXITCODE -ne 0) { throw "Command failed: exit $LASTEXITCODE" } }
function Run-Checks([string]$Phase) {
    docker run --rm --network courselens-week06_default --mount "type=bind,source=$root,target=/work" python:3.13-alpine python -u /work/scripts/smoke-test.py $Phase
    Check-Exit
}
try {
    Run-Checks core
    try {
        docker compose stop course-service
        Check-Exit
        Run-Checks unavailable
    } finally {
        docker compose start course-service
        Check-Exit
    }
    Run-Checks recovered
    if ($Persistence) {
        docker compose down
        Check-Exit
        docker compose up -d
        Check-Exit
        Run-Checks recovered
        'PASS persistence: original user/course/task IDs survive container recreation'
    }
    'PASS all smoke checks'
} finally { Pop-Location }
