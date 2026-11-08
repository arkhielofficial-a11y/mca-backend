# Acts as a second player: logs in (or registers), joins the matchmaking queue
# and prints every message the server sends. Use it to test a match with one Minecraft.
param(
    [Parameter(Mandatory = $true)][string]$Server,
    [string]$Username = "botplayer",
    [string]$Password = "botplayer123",
    [int]$FinishMs = 0   # >0: send match.finish with this time 5 seconds after the match starts
)

$Server = $Server.TrimEnd('/')
$body = @{ username = $Username; password = $Password } | ConvertTo-Json

function Get-Auth($action) {
    Invoke-RestMethod -Method Post -Uri "$Server/v1/auth/$action" -ContentType "application/json" -Body $body
}

try {
    $auth = Get-Auth "login"
    Write-Host "Logged in as $($auth.user.username) (ELO $($auth.user.elo))" -ForegroundColor Green
} catch {
    try {
        $auth = Get-Auth "register"
        Write-Host "Registered $($auth.user.username) (ELO $($auth.user.elo))" -ForegroundColor Green
    } catch {
        $detail = $_.ErrorDetails.Message
        if (-not $detail) { $detail = $_.Exception.Message }
        Write-Host "Login/register failed: $detail" -ForegroundColor Red
        exit 1
    }
}

$wsUrl = ($Server -replace '^https://', 'wss://' -replace '^http://', 'ws://') + "/ws?token=" + [Uri]::EscapeDataString($auth.token)
$cts = New-Object System.Threading.CancellationTokenSource
$ws = New-Object System.Net.WebSockets.ClientWebSocket

try {
    $ws.ConnectAsync([Uri]$wsUrl, $cts.Token).Wait()
} catch {
    Write-Host "WebSocket connect failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
Write-Host "WebSocket connected." -ForegroundColor Green

function Send-Json($object) {
    $bytes = [Text.Encoding]::UTF8.GetBytes(($object | ConvertTo-Json -Compress))
    $segment = New-Object 'System.ArraySegment[byte]' -ArgumentList (, $bytes)
    $ws.SendAsync($segment, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $cts.Token).Wait()
}

Send-Json @{ type = "queue.join" }
Write-Host "Joined queue. Now click Find Match in Minecraft. Press Ctrl+C to stop." -ForegroundColor Yellow

$buffer = New-Object byte[] 16384
$finishAt = $null

try {
    while ($ws.State -eq [System.Net.WebSockets.WebSocketState]::Open) {
        $segment = New-Object 'System.ArraySegment[byte]' -ArgumentList (, $buffer)
        $task = $ws.ReceiveAsync($segment, $cts.Token)
        while (-not $task.IsCompleted) {
            Start-Sleep -Milliseconds 100
            if ($finishAt -and (Get-Date) -ge $finishAt) {
                Write-Host "Sending match.finish ($FinishMs ms)" -ForegroundColor Cyan
                Send-Json @{ type = "match.finish"; finishTimeMs = $FinishMs }
                $finishAt = $null
            }
        }
        $result = $task.Result
        if ($result.MessageType -eq [System.Net.WebSockets.WebSocketMessageType]::Close) { break }
        $text = [Text.Encoding]::UTF8.GetString($buffer, 0, $result.Count)
        Write-Host "<< $text"

        $message = $text | ConvertFrom-Json
        if ($message.type -eq "match.found") {
            Write-Host "MATCH FOUND! seed=$($message.seed) opponent=$($message.opponent.username)" -ForegroundColor Green
            if ($FinishMs -gt 0) { $finishAt = (Get-Date).AddSeconds(5) }
        }
        if ($message.type -eq "match.finished") {
            Write-Host "Match finished." -ForegroundColor Green
            break
        }
    }
} finally {
    $ws.Dispose()
}