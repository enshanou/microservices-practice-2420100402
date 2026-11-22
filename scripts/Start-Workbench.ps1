param([int]$Port = 18090, [switch]$SkipBackend, [switch]$Build, [switch]$RestartFrontend, [switch]$RestartWorker, [switch]$SkipFrontend)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$front = Join-Path $root 'frontend'
function Wait-Http([string]$Url, [int]$Attempts = 60) {
    for ($attempt=0; $attempt -lt $Attempts; $attempt++) {
        try {
            $response=Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 3
            if ($response.StatusCode -eq 200) { return }
        } catch { }
        Start-Sleep -Seconds 1
    }
    throw "Service not ready: $Url"
}
Push-Location $root
try {
    if (!$SkipBackend) {
        $composeFiles = @('-f', 'compose.yaml', '-f', 'deploy/compose.legacy-local.yaml')
        Write-Host 'Checking Docker engine...'
        docker info --format '{{.ServerVersion}}' 2>$null
        if ($LASTEXITCODE -ne 0) {
            $desktop = Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\Docker Desktop.exe'
            if (!(Test-Path -LiteralPath $desktop)) { $desktop='C:\Program Files\Docker\Docker\Docker Desktop.exe' }
            if (!(Test-Path -LiteralPath $desktop)) { throw 'Start Docker Desktop first, then run this script again.' }
            Start-Process -FilePath $desktop -WindowStyle Hidden
            for ($attempt=0; $attempt -lt 60; $attempt++) {
                docker info --format '{{.ServerVersion}}' 2>$null
                if ($LASTEXITCODE -eq 0) { break }
                Start-Sleep -Seconds 2
            }
            if ($LASTEXITCODE -ne 0) { throw 'Docker engine did not become ready.' }
        }
        Write-Host 'Starting Nacos; waiting for its console...'
        docker compose up -d nacos
        if ($LASTEXITCODE -ne 0) { throw 'Nacos startup failed. Check .env and Docker logs.' }
        Wait-Http 'http://127.0.0.1:18080/'
        Write-Host 'Starting the three business services and their databases...'
        $buildOption = @()
        if ($Build) { $buildOption += '--build' }
        docker compose @composeFiles up -d @buildOption user-service course-service task-service
        if ($LASTEXITCODE -ne 0) { throw 'Business service startup failed.' }
        foreach ($servicePort in @(18081,18082,18083)) {
            Wait-Http "http://127.0.0.1:$servicePort/actuator/health"
            Write-Host "Ready: $servicePort"
        }
    }
    if (!$SkipFrontend) {
        if ($RestartFrontend) {
            $pidPath = Join-Path $front '.workbench.pid'
            if (Test-Path -LiteralPath $pidPath) {
                $pidFile = Get-Item -LiteralPath $pidPath
                $workbenchPid = Get-Content -LiteralPath $pidPath -ErrorAction SilentlyContinue
                if ($workbenchPid -match '^\d+$') {
                    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$workbenchPid" -ErrorAction SilentlyContinue
                    if ($process.Name -eq 'node.exe' -and $process.CommandLine -match '(^|[ "])server\.mjs([ "]|$)' -and
                        $process.CreationDate -le $pidFile.LastWriteTime) {
                        Stop-Process -Id $workbenchPid
                        Wait-Process -Id $workbenchPid -Timeout 10 -ErrorAction SilentlyContinue
                    }
                }
            }
        }
        $nodeCommand = Get-Command node -ErrorAction SilentlyContinue
        $nodePath = if ($nodeCommand) { $nodeCommand.Source } else { $null }
        if (!$nodePath) {
            $candidate = Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'
            if (Test-Path -LiteralPath $candidate) { $nodePath=$candidate }
        }
        if (!$nodePath) { throw 'Node.js 18+ is required. Install Node.js or add node to PATH.' }
        $alreadyRunning=$false
        try {
            $page=Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$Port/" -TimeoutSec 2
            if ($page.Content -match 'CourseLens') { $alreadyRunning=$true }
            else { throw "Port $Port is occupied by another application." }
        } catch {
            if ($_.Exception.Message -match 'occupied') { throw }
        }
        if (!$alreadyRunning) {
            $env:PORT="$Port"
            $nodeProcess = Start-Process -FilePath $nodePath -ArgumentList 'server.mjs' -WorkingDirectory $front -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $front 'workbench.log') -RedirectStandardError (Join-Path $front 'workbench-error.log')
            Set-Content -LiteralPath (Join-Path $front '.workbench.pid') -Value $nodeProcess.Id -Encoding ascii
            Wait-Http "http://127.0.0.1:$Port/" 15
        }
    }
    $videoSetting = Select-String -LiteralPath (Join-Path $root '.env') -Pattern '^VIDEO_PYTHON=' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($videoSetting) {
        $videoPython = $videoSetting.Line.Substring('VIDEO_PYTHON='.Length).Trim().Trim("'", '"')
        if ($videoPython -and (Test-Path -LiteralPath $videoPython)) {
            $workerPidFile = Join-Path $front '.video-worker.pid'
            $runningWorker = $null
            if (Test-Path -LiteralPath $workerPidFile) {
                $workerPid = Get-Content -LiteralPath $workerPidFile -ErrorAction SilentlyContinue
                if ($workerPid -match '^\d+$') {
                    $runningWorker = Get-CimInstance Win32_Process -Filter "ProcessId=$workerPid" -ErrorAction SilentlyContinue |
                        Where-Object { $_.Name -match '^python' -and $_.CommandLine -match 'video_worker\.py' }
                }
            }
            if ($runningWorker -and $RestartWorker) {
                Stop-Process -Id $runningWorker.ProcessId
                Wait-Process -Id $runningWorker.ProcessId -Timeout 10 -ErrorAction SilentlyContinue
                $runningWorker = $null
            }
            if (!$runningWorker) {
                $worker = Start-Process -FilePath $videoPython -ArgumentList '-u','scripts/video_worker.py' -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $front 'video-worker.log') -RedirectStandardError (Join-Path $front 'video-worker-error.log')
                Set-Content -LiteralPath $workerPidFile -Value $worker.Id -Encoding ascii
            }
        }
    }
    if (!$SkipFrontend) {
        Write-Host "CourseLens workbench is ready: http://127.0.0.1:$Port/"
    } else {
        Write-Host 'CourseLens backend and local worker startup complete.'
    }
} finally { Pop-Location }
