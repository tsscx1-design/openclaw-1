<#
.SYNOPSIS
    OpenClaw / llama-server 長時間推論（30秒超過）および低速PP（<100 tok/s）自動診断ダンプスクリプト
.DESCRIPTION
    llama-server (11434) のスロットを監視し、30秒以上継続しているリクエスト、
    または Prompt Processing (PP) 速度が 100 tok/s を下回ったリクエストを検知した場合に、
    CPU/メモリ/共有GPUメモリ/GPUクロック/プロセスの詳細スナップショットをログファイルに自動記録します。
#>

param(
    [string]$ServerUrl = "http://127.0.0.1:11434",
    [int]$TimeThresholdSeconds = 30,
    [double]$PpSpeedThreshold = 100.0,
    [int]$CheckIntervalSeconds = 3,
    [string]$DumpDir = "C:\Users\sd190901\.openclaw\slow_dumps"
)

if (-not (Test-Path $DumpDir)) {
    New-Item -ItemType Directory -Path $DumpDir -Force | Out-Null
}

Write-Host "=== OpenClaw Advanced Diagnostic Watcher Started ===" -ForegroundColor Cyan
Write-Host "Target: $ServerUrl/slots"
Write-Host "Triggers: Duration >= $TimeThresholdSeconds s OR PP Speed < $PpSpeedThreshold tok/s"
Write-Host "Dump Directory: $DumpDir"
Write-Host "Press Ctrl+C to stop.`n"

$alertedSlots = @{}

function Get-SharedGpuMemory() {
    try {
        $samples = Get-Counter -Counter "\GPU Adapter Memory(*)\Shared Usage" -ErrorAction Stop | Select-Object -ExpandProperty CounterSamples
        $res = @()
        foreach ($s in $samples) {
            $mb = [math]::Round($s.CookedValue / 1MB, 2)
            $res += "$($s.InstanceName): $mb MiB ($($s.CookedValue) bytes)"
        }
        return $res -join "`r`n"
    } catch {
        return "Failed to query Shared GPU Memory counter: $_"
    }
}

while ($true) {
    try {
        $slotsJson = Invoke-RestMethod -Uri "$ServerUrl/slots" -TimeoutSec 3 -ErrorAction Stop
        $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()

        foreach ($slot in $slotsJson) {
            $isProcessing = $slot.is_processing -or ($slot.state -eq 1) -or ($slot.n_decoded -gt 0)
            
            if ($isProcessing) {
                $slotId = $slot.id
                if (-not $alertedSlots.ContainsKey($slotId)) {
                    $alertedSlots[$slotId] = @{
                        firstSeen = $now
                        dumped = $false
                    }
                }

                $elapsed = $now - $alertedSlots[$slotId].firstSeen
                
                # PP 速度の推定/取得
                # slot のプロパティにある prompt_tokens や n_past 等をチェック
                $promptTokens = if ($slot.n_prompt_tokens) { $slot.n_prompt_tokens } else { $slot.n_ctx }
                $ppTokPerSec = 999.0
                if ($elapsed -gt 2 -and $slot.n_decoded -eq 0 -and $promptTokens -gt 100) {
                    $ppTokPerSec = [math]::Round($promptTokens / $elapsed, 2)
                }

                $isTimeExceeded = ($elapsed -ge $TimeThresholdSeconds)
                $isPpTooSlow = ($elapsed -ge 5 -and $slot.n_decoded -eq 0 -and $ppTokPerSec -lt $PpSpeedThreshold)

                if (($isTimeExceeded -or $isPpTooSlow) -and -not $alertedSlots[$slotId].dumped) {
                    $triggerReason = if ($isTimeExceeded) { "Duration Exceeded ($elapsed s >= $TimeThresholdSeconds s)" } else { "Slow PP Speed ($ppTokPerSec tok/s < $PpSpeedThreshold tok/s)" }
                    $timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
                    $dumpFile = Join-Path $DumpDir "diag_${timestamp}_slot${slotId}.log"

                    Write-Host "[ALERT] Slot $slotId triggered: $triggerReason! Dumping diagnostics to $dumpFile..." -ForegroundColor Red

                    $dumpContent = @()
                    $dumpContent += "================================================================================"
                    $dumpContent += "  OPENCLAW SLOW / STALLED INFERENCE DIAGNOSTIC DUMP"
                    $dumpContent += "  Timestamp: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss.fff')"
                    $dumpContent += "  Trigger: $triggerReason"
                    $dumpContent += "  Slot ID: $slotId | Elapsed: $elapsed seconds | Estimated PP: $ppTokPerSec tok/s"
                    $dumpContent += "================================================================================`n"

                    $dumpContent += "--- [1] SLOT RAW DETAILS ---"
                    $dumpContent += ($slot | ConvertTo-Json -Depth 5)
                    $dumpContent += "`n"

                    $dumpContent += "--- [2] WINDOWS SHARED GPU MEMORY USAGE (\GPU Adapter Memory(*)\Shared Usage) ---"
                    $dumpContent += (Get-SharedGpuMemory)
                    $dumpContent += "`n"

                    $dumpContent += "--- [3] DEDICATED GPU STATUS (nvidia-smi) ---"
                    try {
                        $gpuStatus = & nvidia-smi 2>&1
                        $dumpContent += $gpuStatus
                    } catch {
                        $dumpContent += "nvidia-smi error: $_"
                    }
                    $dumpContent += "`n"

                    $dumpContent += "--- [4] TOP 10 CPU PROCESSES ---"
                    $topCpu = Get-Process | Sort-Object CPU -Descending | Select-Object -First 10 -Property Id, ProcessName, CPU, WorkingSet64
                    $dumpContent += ($topCpu | Out-String)
                    $dumpContent += "`n"

                    $dumpContent += "--- [5] TOP 10 MEMORY (WORKING SET) PROCESSES ---"
                    $topMem = Get-Process | Sort-Object WorkingSet64 -Descending | Select-Object -First 10 -Property Id, ProcessName, CPU, WorkingSet64
                    $dumpContent += ($topMem | Out-String)
                    $dumpContent += "`n"

                    $dumpContent += "--- [6] SYSTEM PHYSICAL RAM ---"
                    $os = Get-CimInstance Win32_OperatingSystem
                    $freeGb = [math]::Round($os.FreePhysicalMemory / 1MB, 2)
                    $totalGb = [math]::Round($os.TotalVisibleMemorySize / 1MB, 2)
                    $dumpContent += "Total Physical RAM: $totalGb GB | Free RAM: $freeGb GB"

                    $dumpContent -join "`r`n" | Out-File -FilePath $dumpFile -Encoding utf8

                    $alertedSlots[$slotId].dumped = $true
                    Write-Host "Diagnostic dump saved successfully." -ForegroundColor Green
                }
            } else {
                if ($alertedSlots.ContainsKey($slot.id)) {
                    $alertedSlots.Remove($slot.id)
                }
            }
        }
    } catch {
        # サーバー停止中等の場合はスキップ
    }

    Start-Sleep -Seconds $CheckIntervalSeconds
}
