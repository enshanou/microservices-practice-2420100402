param(
    [string]$RuntimeRoot = 'D:\courselens-local-mysql',
    [string]$MySqlBin = '',
    [string]$JdkHome = 'D:\study\jdk-25\jdk-25.0.4.1+1',
    [int]$Port = 8080
)
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'Initialize-LocalMySql.ps1') -RuntimeRoot $RuntimeRoot -MySqlBin $MySqlBin
$settings = Get-Content -LiteralPath (Join-Path $RuntimeRoot 'connection.private.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$env:DB_URL = "jdbc:mysql://127.0.0.1:$($settings.Port)/courselens?serverTimezone=UTC&characterEncoding=UTF-8"
$env:DB_USER = $settings.User
$env:DB_PASSWORD = $settings.Password
$env:JAVA_HOME = $JdkHome
$env:Path = "$JdkHome\bin;$env:Path"
$env:MAVEN_USER_HOME = 'D:\study\maven-user-home'
$env:MAVEN_OPTS = '-Dmaven.repo.local=D:\study\maven-repository'
Push-Location (Split-Path -Parent $PSScriptRoot)
try { & .\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=$Port" }
finally { Pop-Location }
