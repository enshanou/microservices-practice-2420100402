param(
    [Parameter(Mandatory)][string]$Username,
    [Parameter(Mandatory)][string]$UserId
)
$ErrorActionPreference = 'Stop'
$secret = Read-Host 'CourseLens password (12-256 characters)' -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
try {
    $env:COURSELENS_NEW_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    node (Join-Path $PSScriptRoot '..\frontend\configure-account.mjs') $Username $UserId
    if ($LASTEXITCODE -ne 0) { throw 'Account configuration failed.' }
} finally {
    Remove-Item Env:COURSELENS_NEW_PASSWORD -ErrorAction SilentlyContinue
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    $secret.Dispose()
}
