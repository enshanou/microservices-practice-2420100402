param()
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root '.env'
if (!(Test-Path -LiteralPath $path)) {
    Copy-Item -LiteralPath (Join-Path $root '.env.example') -Destination $path
}
$text = [IO.File]::ReadAllText($path)
$bytes = New-Object byte[] 48
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
$defaults = [ordered]@{
    NACOS_AUTH_TOKEN = [Convert]::ToBase64String($bytes)
    NACOS_AUTH_IDENTITY_KEY = 'courselensIdentity'
    NACOS_AUTH_IDENTITY_VALUE = [guid]::NewGuid().ToString('N')
    NACOS_USERNAME = 'nacos'
    NACOS_PASSWORD = ([guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N'))
    NACOS_NAMESPACE = ''
    NACOS_GROUP = 'COURSELENS_2420100402'
}
foreach ($key in $defaults.Keys) {
    $pattern = '(?m)^' + [regex]::Escape($key) + '=([^\r\n]*)\r?$'
    $match = [regex]::Match($text, $pattern)
    $replacement = $key + '=' + $defaults[$key]
    if (!$match.Success) {
        $text = $text.TrimEnd() + "`n" + $replacement + "`n"
    } elseif ($match.Groups[1].Value -match '^(replace_with_|CHANGE_ME)' -or
            ($match.Groups[1].Value -eq '' -and $key -ne 'NACOS_NAMESPACE')) {
        $text = [regex]::Replace($text, $pattern, $replacement)
    }
}
$utf8 = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllText($path, $text, $utf8)
Write-Host 'Local Nacos settings are ready in ignored .env. Existing configured values and MySQL credentials were preserved.'
Write-Host 'For first-time console initialization, open .env locally and use its NACOS_PASSWORD. Do not share screenshots of it.'
