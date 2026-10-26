param([string]$BaseUrl = 'http://localhost:18080')
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
Set-Location -LiteralPath $repoRoot
Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromSeconds(10)
$evidence = Join-Path $PSScriptRoot 'evidence'
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
function Call-Api([string]$Method, [string]$Path, [int]$Expected, $Body = $null) {
    $url = $BaseUrl.TrimEnd('/') + $Path
    $request = New-Object System.Net.Http.HttpRequestMessage
    $request.Method = New-Object System.Net.Http.HttpMethod($Method)
    $request.RequestUri = [Uri]$url
    if ($null -ne $Body) {
        $request.Content = New-Object System.Net.Http.StringContent(($Body | ConvertTo-Json -Compress), [Text.Encoding]::UTF8, 'application/json')
    }
    $response = $client.SendAsync($request).GetAwaiter().GetResult()
    try {
        $raw = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        Write-Host "$Method $url -> $([int]$response.StatusCode)"
        Write-Host $raw
        if ([int]$response.StatusCode -ne $Expected) { throw "Expected $Expected, got $([int]$response.StatusCode): $raw" }
        return ($raw | ConvertFrom-Json)
    } finally { $response.Dispose(); $request.Dispose() }
}
function Wait-App {
    for ($attempt = 1; $attempt -le 90; $attempt++) {
        try {
            $health = Call-Api GET '/actuator/health' 200
            if ($health.status -eq 'UP') { return }
        } catch { }
        Start-Sleep -Seconds 2
    }
    throw 'Application did not become healthy within 180 seconds.'
}
try {
    Wait-App
    $course = Call-Api POST '/api/courses' 201 @{ownerId='2420100402'; title=('Week05 persistence ' + [Guid]::NewGuid().ToString('N').Substring(0,8)); sourceReference='imports/week05-demo.srt'}
    $before = Call-Api GET "/api/courses/$($course.id)" 200
    $before | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $evidence 'course-before.json')
    Write-Host 'Recreating the app and database containers; keeping the named volume.'
    docker compose down
    if ($LASTEXITCODE -ne 0) { throw 'docker compose down failed' }
    docker compose up -d
    if ($LASTEXITCODE -ne 0) { throw 'docker compose up failed' }
    Wait-App
    $after = Call-Api GET "/api/courses/$($course.id)" 200
    $after | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $evidence 'course-after.json')
    foreach ($field in @('id','ownerId','title','sourceReference','createdAt')) {
        if ($before.$field -ne $after.$field) { throw "Persistence mismatch: $field" }
    }
    docker compose ps
    if ($LASTEXITCODE -ne 0) { throw 'docker compose ps failed' }
    Write-Host "PASS: health 200/UP, create 201, query 200, same record after container recreation. ID=$($course.id)"
    Write-Host 'Before and after responses are saved in docs/homework/week-05/evidence/.'
} finally { $client.Dispose() }
