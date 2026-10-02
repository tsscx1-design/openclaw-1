param (
    [ValidateSet("start", "stop", "restart", "status")]
    [string]$Action = "status",
    [ValidateSet("general", "coder", "gemma", "phi", "reader", "all")]
    [string]$Worker = "all"
)

$BonsaiDir = "C:\Users\sd190901\.openclaw\bonsai"
$ServerExe = Join-Path $BonsaiDir "bin\llama-server.exe"
$ModelDir = Join-Path $BonsaiDir "model"
$DefaultFallbackModel = "Qwen2.5-1.5B-Instruct-Q8_0.gguf"

$WorkersConfig = @{
    "general" = @{
        Port = 11435
        ModelFile = if (Test-Path (Join-Path $ModelDir "Qwen2.5-3B-Instruct-Q8_0.gguf")) { "Qwen2.5-3B-Instruct-Q8_0.gguf" } else { "Qwen2.5-1.5B-Instruct-Q8_0.gguf" }
        Alias = "qwen-general-worker"
        Parallel = 2
        CtxSize = 16384
        Role = "General Summary, Memory, and Classification"
    }
    "coder" = @{
        Port = 11436
        ModelFile = if (Test-Path (Join-Path $ModelDir "Qwen2.5-Coder-3B-Instruct-Q8_0.gguf")) { "Qwen2.5-Coder-3B-Instruct-Q8_0.gguf" } else { $DefaultFallbackModel }
        Alias = "qwen-coder-worker"
        Parallel = 2
        CtxSize = 16384
        Role = "Code Generation, Review, and Debugging"
    }
    "gemma" = @{
        Port = 11437
        ModelFile = if (Test-Path (Join-Path $ModelDir "gemma-3-4b-it-Q6_K.gguf")) { "gemma-3-4b-it-Q6_K.gguf" } else { $DefaultFallbackModel }
        Alias = "gemma-multilingual-worker"
        Parallel = 2
        CtxSize = 16384
        Role = "Multilingual, Translation, and Proofreading"
    }
    "phi" = @{
        Port = 11438
        ModelFile = if (Test-Path (Join-Path $ModelDir "Phi-4-mini-instruct-Q8_0.gguf")) { "Phi-4-mini-instruct-Q8_0.gguf" } else { $DefaultFallbackModel }
        Alias = "phi-reasoning-worker"
        Parallel = 2
        CtxSize = 16384
        Role = "Math, Data Analysis, and Logical Reasoning"
    }
    "reader" = @{
        Port = 11439
        ModelFile = if (Test-Path (Join-Path $ModelDir "readerlm-v2-q8_0.gguf")) { "readerlm-v2-q8_0.gguf" } else { $DefaultFallbackModel }
        Alias = "readerlm-web-worker"
        Parallel = 2
        CtxSize = 16384
        Role = "Web Page Scraping, HTML to Clean Markdown / Knowledge Extraction"
    }
}

function Get-PortProcessId([int]$Port) {
    try {
        $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($conn) { return $conn.OwningProcess }
    } catch {}
    return $null
}

function Start-WorkerInstance([string]$Key) {
    $cfg = $WorkersConfig[$Key]
    if (-not $cfg) { Write-Warning "Unknown worker: $Key"; return }
    $existingPid = Get-PortProcessId $cfg.Port
    if ($existingPid) {
        Write-Host "[$Key] Already running on port $($cfg.Port) (PID: $existingPid)" -ForegroundColor Green
        return
    }
    $modelPath = Join-Path $ModelDir $cfg.ModelFile
    if (-not (Test-Path $modelPath)) {
        Write-Warning "[$Key] Model file not found: $modelPath"
        return
    }
    $cmdLine = "`"" + $ServerExe + "`" -m `"" + $modelPath + "`" --port " + $cfg.Port + " --host 127.0.0.1 -ngl 0 -t 4 --poll 0 --parallel " + $cfg.Parallel + " -c " + $cfg.CtxSize + " -a `"" + $cfg.Alias + "`""
    Write-Host "[$Key] Starting on Port: $($cfg.Port), Model: $($cfg.ModelFile) (CPU-only, parallel $($cfg.Parallel))" -ForegroundColor Cyan
    $process = ([wmiclass]"Win32_Process").Create($cmdLine, "$BonsaiDir\bin")
    if ($process.ReturnValue -ne 0) {
        Write-Error "[$Key] Failed to spawn: ReturnValue=$($process.ReturnValue)"
        return
    }
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Milliseconds 500
        $newPid = Get-PortProcessId $cfg.Port
        if ($newPid) {
            Write-Host "[$Key] Started successfully! (PID: $newPid, Port: $($cfg.Port))" -ForegroundColor Green
            return
        }
    }
    Write-Warning "[$Key] Timeout waiting for port $($cfg.Port)"
}

function Stop-WorkerInstance([string]$Key) {
    $cfg = $WorkersConfig[$Key]
    if (-not $cfg) { return }
    $targetPid = Get-PortProcessId $cfg.Port
    if ($targetPid) {
        Write-Host "[$Key] Stopping process (PID: $targetPid, Port: $($cfg.Port))..." -ForegroundColor Yellow
        Stop-Process -Id $targetPid -Force -ErrorAction SilentlyContinue
        Write-Host "[$Key] Stopped successfully" -ForegroundColor Green
    } else {
        Write-Host "[$Key] Not running (Port $($cfg.Port) is free)" -ForegroundColor Gray
    }
}

function Show-WorkerStatus([string]$Key) {
    $cfg = $WorkersConfig[$Key]
    if (-not $cfg) { return }
    $targetPid = Get-PortProcessId $cfg.Port
    $modelPath = Join-Path $ModelDir $cfg.ModelFile
    $modelExists = Test-Path $modelPath
    $statusStr = if ($targetPid) { "ONLINE (PID: $targetPid)" } else { "OFFLINE" }
    $color = if ($targetPid) { "Green" } else { "DarkGray" }
    $fileStatus = if ($modelExists) { "Ready ($($cfg.ModelFile))" } else { "Missing file" }
    Write-Host ("[{0,-7}] Port: {1,-5} | Status: {2,-20} | File: {3,-38} | Role: {4}" -f $Key, $cfg.Port, $statusStr, $fileStatus, $cfg.Role) -ForegroundColor $color
}

$targetKeys = if ($Worker -eq "all") { @("general", "coder", "gemma", "phi", "reader") } else { @($Worker) }

switch ($Action) {
    "start" { foreach ($k in $targetKeys) { Start-WorkerInstance $k } }
    "stop" { foreach ($k in $targetKeys) { Stop-WorkerInstance $k } }
    "restart" { foreach ($k in $targetKeys) { Stop-WorkerInstance $k }; Start-Sleep -Seconds 1; foreach ($k in $targetKeys) { Start-WorkerInstance $k } }
    "status" {
        Write-Host "=== Specialist Model Squad Status ===" -ForegroundColor Cyan
        foreach ($k in $targetKeys) { Show-WorkerStatus $k }
    }
}
