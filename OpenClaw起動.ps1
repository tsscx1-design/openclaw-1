[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Write-Host "===================================================" -ForegroundColor Cyan
Write-Host "       OpenClaw Gateway 起動ランチャー" -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Cyan

$port = 18789
$openclawDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $openclawDir

# 既にポート18789がリッスン中か確認
$listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue

if ($listener) {
    Write-Host "[情報] OpenClaw Gateway は既に稼働しています (ポート $port)。" -ForegroundColor Green
    Write-Host "[情報] ブラウザでダッシュボードを開きます..." -ForegroundColor Yellow
    Start-Process "http://127.0.0.1:$port"
    Start-Sleep -Seconds 2
    exit 0
}

# 未起動の場合はGatewayを起動
Write-Host "[起動] Gatewayプロセスを開始します..." -ForegroundColor Yellow
# 管理タスクがあればそれ経由で起動する（タスク外で2つ目のGatewayを立てると状態ディレクトリのロックが衝突する）
$gatewayTask = Get-ScheduledTask -TaskName "OpenClaw Gateway" -ErrorAction SilentlyContinue
if ($gatewayTask) {
    Start-ScheduledTask -TaskName "OpenClaw Gateway"
} else {
    Start-Process -FilePath "cmd.exe" -ArgumentList "/k", "node openclaw.mjs gateway run --allow-unconfigured" -WorkingDirectory $openclawDir
}

# Gatewayが起動してポートがリッスンするまで待機（最大15秒）
Write-Host "[待機] Gatewayの起動完了を待機中..." -ForegroundColor Gray
$started = $false
for ($i = 1; $i -le 45; $i++) {
    Start-Sleep -Seconds 1
    $listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($listener) {
        $started = $true
        break
    }
}

if ($started) {
    Write-Host "[成功] Gatewayが正常に起動しました！ダッシュボードを開きます。" -ForegroundColor Green
    Start-Sleep -Seconds 1
    Start-Process "http://127.0.0.1:$port"
} else {
    Write-Host "[注意] 起動に時間がかかっています。ブラウザを開いて接続を試みます..." -ForegroundColor Yellow
    Start-Process "http://127.0.0.1:$port"
}
Start-Sleep -Seconds 2
