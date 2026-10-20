param(
    [string]$MySqlBin = '',
    [string]$RuntimeRoot = 'D:\courselens-local-mysql',
    [int]$Port = 13306
)
$ErrorActionPreference = 'Stop'
if (-not $MySqlBin) {
    $savedSettingsPath = Join-Path $RuntimeRoot 'connection.private.json'
    if (Test-Path -LiteralPath $savedSettingsPath) {
        $savedSettings = Get-Content -LiteralPath $savedSettingsPath -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($savedSettings.MySqlBin -and (Test-Path -LiteralPath (Join-Path $savedSettings.MySqlBin 'mysqld.exe'))) {
            $MySqlBin = $savedSettings.MySqlBin
        }
    }
    if (-not $MySqlBin) {
        $MySqlBin = Split-Path -Parent (Get-Command mysqld.exe -ErrorAction Stop).Source
    }
}
$mysqlExe = Join-Path $MySqlBin 'mysql.exe'
$serverExe = Join-Path $MySqlBin 'mysqld.exe'
if (-not (Test-Path -LiteralPath $mysqlExe) -or -not (Test-Path -LiteralPath $serverExe)) {
    throw 'MySQL binaries are missing. Install MySQL or provide -MySqlBin.'
}
$RuntimeRoot = [System.IO.Path]::GetFullPath($RuntimeRoot)
New-Item -ItemType Directory -Path $RuntimeRoot -Force | Out-Null
$dataDir = Join-Path $RuntimeRoot 'data'
$settingsPath = Join-Path $RuntimeRoot 'connection.private.json'
$iniPath = Join-Path $RuntimeRoot 'my.ini'
$pidPath = Join-Path $RuntimeRoot 'mysql.pid'
$encoding = New-Object System.Text.UTF8Encoding($false)

function Test-LocalPort {
    $client = New-Object System.Net.Sockets.TcpClient
    try { $client.Connect('127.0.0.1', $Port); return $true } catch { return $false } finally { $client.Dispose() }
}

function Invoke-LocalSql([string]$Sql, [string]$Password) {
    $previous = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $Password
        $Sql | & $mysqlExe --protocol=TCP --host=127.0.0.1 "--port=$Port" --user=root --batch --raw
        if ($LASTEXITCODE -ne 0) { throw 'Local MySQL SQL command failed.' }
    } finally { $env:MYSQL_PWD = $previous }
}

if (Test-Path -LiteralPath $settingsPath) {
    $settings = Get-Content -LiteralPath $settingsPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($settings.Port -ne $Port) { throw 'Runtime directory belongs to a different port.' }
} else {
    if (Test-Path -LiteralPath $dataDir) { throw 'Existing data directory has no project credentials; choose a new RuntimeRoot.' }
    $settings = [pscustomobject]@{
        Port = $Port
        Database = 'courselens'
        User = 'courselens'
        Password = [Guid]::NewGuid().ToString('N')
        RootPassword = [Guid]::NewGuid().ToString('N')
        RootConfigured = $false
        MySqlBin = $MySqlBin
    }
    [System.IO.File]::WriteAllText($settingsPath, ($settings | ConvertTo-Json), $encoding)
}

if (-not (Test-Path -LiteralPath (Join-Path $dataDir 'mysql'))) {
    $args = @('--no-defaults', '--initialize-insecure', ('--basedir="' + (Split-Path -Parent $MySqlBin) + '"'), ('--datadir="' + $dataDir + '"'))
    $initialization = Start-Process -FilePath $serverExe -ArgumentList $args -WindowStyle Hidden -Wait -PassThru `
        -RedirectStandardOutput (Join-Path $RuntimeRoot 'initialize-output.log') `
        -RedirectStandardError (Join-Path $RuntimeRoot 'initialize-error.log')
    if ($initialization.ExitCode -ne 0) { throw 'MySQL initialization failed; inspect initialize-error.log.' }
}

$ini = @"
[mysqld]
basedir=$((Split-Path -Parent $MySqlBin).Replace('\','/'))
datadir=$($dataDir.Replace('\','/'))
port=$Port
bind-address=127.0.0.1
mysqlx=0
skip-log-bin
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
pid-file=$($pidPath.Replace('\','/'))
log-error=$((Join-Path $RuntimeRoot 'mysql-error.log').Replace('\','/'))
"@
[System.IO.File]::WriteAllText($iniPath, $ini, $encoding)
if (Test-LocalPort) {
    if (-not (Test-Path -LiteralPath $pidPath)) { throw 'Port is occupied by another server; choose another port.' }
    $serverProcessId = [int](Get-Content -LiteralPath $pidPath -Raw)
    $serverProcess = Get-CimInstance Win32_Process -Filter ("ProcessId=" + $serverProcessId)
    if (-not $serverProcess -or $serverProcess.CommandLine -notlike ('*' + $iniPath + '*')) {
        throw 'Port is not confirmed to belong to this project MySQL instance.'
    }
} else {
    Start-Process -FilePath $serverExe -ArgumentList ('--defaults-file="' + $iniPath + '"') -WindowStyle Hidden | Out-Null
    $deadline = [DateTime]::UtcNow.AddSeconds(45)
    while (-not (Test-LocalPort)) {
        if ([DateTime]::UtcNow -ge $deadline) { throw 'MySQL did not start; inspect mysql-error.log.' }
        Start-Sleep -Milliseconds 500
    }
}

if (-not $settings.RootConfigured) {
    Invoke-LocalSql ("ALTER USER 'root'@'localhost' IDENTIFIED BY '" + $settings.RootPassword + "';") ''
    $settings.RootConfigured = $true
    [System.IO.File]::WriteAllText($settingsPath, ($settings | ConvertTo-Json), $encoding)
}
Invoke-LocalSql ([System.IO.File]::ReadAllText((Join-Path $PSScriptRoot 'schema.sql'))) $settings.RootPassword
$sql = "CREATE USER IF NOT EXISTS 'courselens'@'localhost' IDENTIFIED BY '" + $settings.Password + "'; " +
       "GRANT SELECT, INSERT, UPDATE, DELETE ON courselens.* TO 'courselens'@'localhost';"
Invoke-LocalSql $sql $settings.RootPassword
Invoke-LocalSql ([System.IO.File]::ReadAllText((Join-Path $PSScriptRoot 'demo-data.sql'))) $settings.RootPassword
Write-Output "CourseLens MySQL ready: 127.0.0.1:$Port, database=courselens. Credentials stay outside the repository."
