param([string]$RuntimeRoot = 'D:\courselens-local-mysql')
$ErrorActionPreference = 'Stop'
$settings = Get-Content -LiteralPath (Join-Path $RuntimeRoot 'connection.private.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$previous = $env:MYSQL_PWD
try {
    $env:MYSQL_PWD = $settings.RootPassword
    & (Join-Path $settings.MySqlBin 'mysqladmin.exe') --protocol=TCP --host=127.0.0.1 "--port=$($settings.Port)" --user=root shutdown
    if ($LASTEXITCODE -ne 0) { throw 'Project MySQL could not be stopped.' }
} finally { $env:MYSQL_PWD = $previous }
