param (
    [ValidateSet("start", "stop", "restart", "status")]
    [string]$Action = "status",
    [ValidateSet("general", "coder", "gemma", "phi", "reader", "all")]
    [string]$Worker = "all"
)

$BonsaiDir = "C:\Users\sd190901\.openclaw\bonsai"
$ServerExe = Join-Path $BonsaiDir "bin\llama-server.exe"
$ModelDir = Join-Path $BonsaiDir "model"
# Large worker models live on the D: SSD (C: is short on space)
$ExtModelDir = "D:\llm-models"
$DefaultFallbackModel = "Qwen2.5-1.5B-Instruct-Q8_0.gguf"
# Uncensored (abliterated/heretic) worker builds (2026-10-02); the stock files are used until they exist.
$UncensoredDir = Join-Path $ExtModelDir "uncensored"
function Select-ModelFile([string]$Preferred, [string]$Fallback) {
    if (Test-Path $Preferred) { return $Preferred }
    return $Fallback
}
$GemmaUncensored = Join-Path $UncensoredDir "Gemma-4-E4B-Uncensored-HauhauCS-Aggressive-Q4_K_M.gguf"
$GemmaUncensoredMmproj = Join-Path $UncensoredDir "mmproj-Gemma-4-E4B-Uncensored-HauhauCS-Aggressive-f16.gguf"
$UseGemmaUncensored = (Test-Path $GemmaUncensored) -and (Test-Path $GemmaUncensoredMmproj)

# general, coder and phi share one MoE server (Nemotron 3 Nano 30B-A3B, ~21 GB RAM) on port 11435:
# it beat the old 1.5B-4B workers on CPU speed while being far larger. Starting coder or phi
# when general is up is a no-op because the port is already listening.
$SharedGeneral = @{
    Port = 11435
    ModelFile = Select-ModelFile (Join-Path $UncensoredDir "NVIDIA-Nemotron-3-Nano-30B-A3B-BF16-heretic.i1-Q4_K_S.gguf") (Join-Path $ExtModelDir "Nemotron-3-Nano-30B-A3B-UD-Q4_K_XL.gguf")
    Alias = "nemotron-general-worker"
    Parallel = 2
    CtxSize = 16384
}

$WorkersConfig = @{
    "general" = $SharedGeneral + @{ Role = "General Summary, Memory, and Classification" }
    "coder" = $SharedGeneral + @{ Role = "Code Generation, Review, and Debugging (shares the general server)" }
    "gemma" = @{
        Port = 11437
        ModelFile = if ($UseGemmaUncensored) { $GemmaUncensored } else { Join-Path $ExtModelDir "gemma-4-E4B-it-UD-Q4_K_XL.gguf" }
        MmprojFile = if ($UseGemmaUncensored) { $GemmaUncensoredMmproj } else { Join-Path $ExtModelDir "mmproj-gemma-4-E4B-it-f16.gguf" }
        Alias = "gemma-multilingual-worker"
        Parallel = 2
        CtxSize = 16384
        Role = "Multilingual, Translation, Proofreading and photo-album vision"
    }
    "phi" = $SharedGeneral + @{ Role = "Math, Data Analysis, and Logical Reasoning (shares the general server)" }
    "reader" = @{
        Port = 11439
        ModelFile = if (Test-Path (Join-Path $ModelDir "readerlm-v2-q8_0.gguf")) { "readerlm-v2-q8_0.gguf" } else { $DefaultFallbackModel }
        Alias = "readerlm-web-worker"
        Parallel = 2
        CtxSize = 16384
        Role = "Web Page Scraping, HTML to Clean Markdown / Knowledge Extraction"
    }
}

function Resolve-ModelPath([string]$File) {
    if ([System.IO.Path]::IsPathRooted($File)) { return $File }
    return Join-Path $ModelDir $File
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
    $modelPath = Resolve-ModelPath $cfg.ModelFile
    if (-not (Test-Path $modelPath)) {
        Write-Warning "[$Key] Model file not found: $modelPath"
        return
    }
    # --device none keeps workers off the GPU entirely: with only -ngl 0 each one still opened a CUDA
    # context and compute buffers (~0.35 GB of the 8 GB card), which the main model and media jobs need.
    $cmdLine = "`"" + $ServerExe + "`" -m `"" + $modelPath + "`" --port " + $cfg.Port + " --host 127.0.0.1 -ngl 0 --device none -t 4 --poll 0 --parallel " + $cfg.Parallel + " -c " + $cfg.CtxSize + " -a `"" + $cfg.Alias + "`""
    # The gemma worker doubles as the photo-album vision model when its mmproj file is present.
    if ($cfg.MmprojFile -and (Test-Path $cfg.MmprojFile)) {
        $cmdLine += " --mmproj `"" + $cfg.MmprojFile + "`" --no-mmproj-offload"
    }
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
    $modelPath = Resolve-ModelPath $cfg.ModelFile
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
