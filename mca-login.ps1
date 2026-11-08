# Registers or logs in to the MCA backend and writes config/mca.properties for the mod.
param(
    [string]$Server = "http://localhost:8080",
    [string]$GameDir = "$env:APPDATA\.tlauncher\legacy\Minecraft\game"
)

$Server = $Server.TrimEnd('/')
$mode = Read-Host "Type R to register a new account, or L to log in"
$username = Read-Host "Username (3-32 letters, numbers, underscore)"
$secure = Read-Host "Password (min 8 characters)" -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
$password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)

$action = if ($mode -match '^[Rr]') { "register" } else { "login" }
$body = @{ username = $username; password = $password } | ConvertTo-Json

try {
    $res = Invoke-RestMethod -Method Post -Uri "$Server/v1/auth/$action" `
        -ContentType "application/json" -Body $body
} catch {
    $detail = $_.ErrorDetails.Message
    if (-not $detail) { $detail = $_.Exception.Message }
    Write-Host "Failed: $detail" -ForegroundColor Red
    exit 1
}

$wsUrl = $Server -replace '^https://', 'wss://' -replace '^http://', 'ws://'
$configDir = Join-Path $GameDir "config"
New-Item -ItemType Directory -Force -Path $configDir | Out-Null
$file = Join-Path $configDir "mca.properties"
[IO.File]::WriteAllLines($file, @("serverUrl=$wsUrl", "authToken=$($res.token)"))

Write-Host "Logged in as $($res.user.username) (ELO $($res.user.elo))." -ForegroundColor Green
Write-Host "Saved: $file"
