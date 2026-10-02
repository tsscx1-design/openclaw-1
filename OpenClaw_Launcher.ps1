[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Write-Host ""
Write-Host "  ====================================================" -ForegroundColor Cyan
Write-Host "         OpenClaw Gateway Launcher" -ForegroundColor Cyan
Write-Host "  ====================================================" -ForegroundColor Cyan
Write-Host ""

$port = 18789
$openclawDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location -LiteralPath $openclawDir

# 1. Check if OpenClaw Gateway is already running on port 18789
$listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue

if ($listener) {
    Write-Host "  [OK] OpenClaw Gateway is already running (Port $port)." -ForegroundColor Green
    Write-Host "  [OK] Opening dashboard in browser..." -ForegroundColor Yellow
    Start-Process "http://127.0.0.1:$port"
    Write-Host ""
    Write-Host "  Dashboard opened in browser." -ForegroundColor Green
    Write-Host ""
} else {
    # 2. Start OpenClaw Gateway in a separate visible console window
    Write-Host "  [LAUNCH] Starting OpenClaw Gateway Server..." -ForegroundColor Yellow
    $argList = "/k", "cd /d `"$openclawDir`" && title OpenClaw Gateway Server && node openclaw.mjs gateway run --allow-unconfigured"
    Start-Process -FilePath "cmd.exe" -ArgumentList $argList -WorkingDirectory $openclawDir

    # 3. Wait for Gateway to initialize (up to 30 seconds)
    Write-Host "  [WAIT] Waiting for Gateway to start..." -ForegroundColor Gray
    $started = $false
    for ($i = 1; $i -le 30; $i++) {
        Start-Sleep -Seconds 1
        $listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
        if ($listener) {
            $started = $true
            Write-Host "  [OK] Gateway is UP on port $port! ($i sec)" -ForegroundColor Green
            break
        }
        if ($i % 5 -eq 0) {
            Write-Host "    ... waiting ($i/30s)" -ForegroundColor DarkGray
        }
    }

    # 4. Open dashboard in browser
    if ($started) {
        Start-Sleep -Seconds 1
        Write-Host "  [BROWSER] Opening Control UI dashboard..." -ForegroundColor Cyan
        Start-Process "http://127.0.0.1:$port"
        Write-Host ""
        Write-Host "  Gateway started. Dashboard opened in browser." -ForegroundColor Green
        Write-Host ""
    } else {
        Write-Host ""
        Write-Host "  [WARN] Gateway is taking longer than usual." -ForegroundColor Yellow
        Start-Process "http://127.0.0.1:$port"
        Write-Host ""
    }
}
