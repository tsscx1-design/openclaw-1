# OpenClaw_StartAll.ps1  (ASCII only on purpose: Windows PowerShell 5.1 misreads BOM-less UTF-8)
# Starts (1) llama-server for Bonsai 2 on 127.0.0.1:11434, (2) the OpenClaw Gateway on 18789.
# Non-destructive: it never stops or restarts anything that is already running.
param(
  [switch]$SkipGateway,
  [int]$ModelWaitSeconds = 180,
  [int]$GatewayWaitSeconds = 60
)

$openclawDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$stateDir = Join-Path $env:USERPROFILE '.openclaw'
$bin      = Join-Path $stateDir 'bonsai\bin\llama-server.exe'
$model    = Join-Path $stateDir 'bonsai\model\Swift-Bonsai-2-PTQ1_0.gguf'
$logDir   = Join-Path $stateDir 'logs'
$mainPort = 11434
$gwPort   = 18789

function Test-Listening([int]$Port) {
  return [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}
function Test-LlamaHealthy {
  try {
    $r = Invoke-WebRequest -Uri "http://127.0.0.1:$mainPort/health" -UseBasicParsing -TimeoutSec 3
    return ($r.StatusCode -eq 200)
  } catch { return $false }
}

Write-Host ''
Write-Host '=== OpenClaw start (llama-server + Gateway) ===' -ForegroundColor Cyan

# ---------- 1. llama-server ----------
Write-Host '[1/2] llama-server (Bonsai 2, port 11434)' -ForegroundColor Cyan
$llamaOk = $false
if (Test-LlamaHealthy) {
  Write-Host '  already running and healthy.' -ForegroundColor Green
  $llamaOk = $true
} elseif (Test-Listening $mainPort) {
  Write-Host "  port $mainPort is already in use (model may still be loading, or another program owns it)." -ForegroundColor Yellow
  Write-Host '  Nothing was started. Check: netstat -ano | findstr 11434' -ForegroundColor Yellow
} else {
  if (-not (Test-Path -LiteralPath $bin))   { Write-Host "  NOT FOUND: $bin" -ForegroundColor Red; exit 1 }
  if (-not (Test-Path -LiteralPath $model)) { Write-Host "  NOT FOUND: $model" -ForegroundColor Red; exit 1 }
  New-Item -ItemType Directory -Force -Path $logDir | Out-Null
  $stamp  = Get-Date -Format 'yyyyMMdd-HHmmss'
  $outLog = Join-Path $logDir "llama-server-$stamp.out.log"
  $errLog = Join-Path $logDir "llama-server-$stamp.err.log"
  # Same parameters as scratch\run_bonsai_optimal.ps1 (the verified configuration).
  $argList = @(
    '-m', "`"$model`"",
    '--host', '127.0.0.1', '--port', "$mainPort",
    '-t', '2', '-ngl', '999', '-np', '1', '-c', '24576', '-fa', 'on',
    '-ctk', 'q4_0', '-ctv', 'q4_0',
    '-b', '2048', '-ub', '1024', '-n', '4096',
    '--repeat-penalty', '1.0', '--temp', '1.0', '--top-p', '0.95', '--top-k', '20',
    '--reasoning-budget', '512', '--prio', '2',
    '-a', 'swift-bonsai-ptq1,swift-bonsai-pq2,swift-bonsai'
  )
  $p = Start-Process -FilePath $bin -ArgumentList $argList -WorkingDirectory (Split-Path $bin) `
        -WindowStyle Hidden -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru
  Write-Host "  started PID $($p.Id). log: $errLog" -ForegroundColor Gray
  $deadline = (Get-Date).AddSeconds($ModelWaitSeconds)
  while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 2
    if ($p.HasExited) { break }
    if (Test-LlamaHealthy) { $llamaOk = $true; break }
  }
  if ($llamaOk) {
    Write-Host '  OK: model loaded, /health is 200.' -ForegroundColor Green
  } else {
    Write-Host '  FAILED to become healthy. Last log lines:' -ForegroundColor Red
    if (Test-Path -LiteralPath $errLog) { Get-Content -LiteralPath $errLog -Tail 15 }
    Write-Host '  Hint: if the log says out of memory, close GPU-heavy apps (browser, games) and retry.' -ForegroundColor Yellow
  }
}

# ---------- 2. Gateway ----------
if ($SkipGateway) {
  Write-Host '[2/2] Gateway: skipped (-SkipGateway)' -ForegroundColor Gray
} else {
  Write-Host "[2/2] OpenClaw Gateway (port $gwPort)" -ForegroundColor Cyan
  if (Test-Listening $gwPort) {
    Write-Host '  already running.' -ForegroundColor Green
  } else {
    $viaTask = $false
    schtasks /query /tn "OpenClaw Gateway" 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
      schtasks /run /tn "OpenClaw Gateway" 2>&1 | Out-Null
      $viaTask = $true
      Write-Host '  started via scheduled task "OpenClaw Gateway".' -ForegroundColor Gray
    } else {
      Start-Process -FilePath 'cmd.exe' `
        -ArgumentList '/k', 'title OpenClaw Gateway Server && node openclaw.mjs gateway run --allow-unconfigured' `
        -WorkingDirectory $openclawDir
      Write-Host '  started in a new console window (node openclaw.mjs gateway run).' -ForegroundColor Gray
    }
    $gwOk = $false
    $deadline = (Get-Date).AddSeconds($GatewayWaitSeconds)
    while ((Get-Date) -lt $deadline) {
      Start-Sleep -Seconds 2
      if (Test-Listening $gwPort) { $gwOk = $true; break }
    }
    if ($gwOk) {
      Write-Host "  OK: Gateway is listening on $gwPort." -ForegroundColor Green
    } else {
      Write-Host '  Gateway did not start listening.' -ForegroundColor Red
      Write-Host '  If its console says "Another gateway ... already owns this state directory",' -ForegroundColor Yellow
      Write-Host '  a stale node process holds the lock. Check: netstat -ano | findstr 18789, then Task Manager.' -ForegroundColor Yellow
    }
  }
}

Write-Host ''
if ($llamaOk) {
  Write-Host 'Ready. Open a NEW session in the app (send /new) before chatting.' -ForegroundColor Green
} else {
  Write-Host 'llama-server is not ready. The app cannot get replies until it is.' -ForegroundColor Red
}
