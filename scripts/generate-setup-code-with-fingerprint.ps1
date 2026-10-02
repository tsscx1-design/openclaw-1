# generate-setup-code-with-fingerprint.ps1
# 使い方:
#   .\generate-setup-code-with-fingerprint.ps1 `
#     -SetupCode "eyJ1cmwiOi..." `
#     -Fingerprint "78:CF:A4:CB:4B:BF:76:5C:5A:B1:AC:D6:A5:91:E2:64:A7:13:B0:C7:27:B6:FA:2F:A9:F4:8C:9F:41:8E:63:06"
#
# または URL 直接指定:
#   .\generate-setup-code-with-fingerprint.ps1 `
#     -Url "https://node.tailf20516.ts.net" `
#     -Fingerprint "78:CF:A4:..."

param(
    [string]$SetupCode,
    [string]$Url,
    [string]$BootstrapToken,
    [string]$Token,
    [Parameter(Mandatory)][string]$Fingerprint
)

# フィンガープリントを正規化（コロン区切り大文字）
$hex = $Fingerprint -replace '[:\s]', ''
if ($hex.Length -ne 64) {
    Write-Error "フィンガープリントが不正です（64文字のHEXが必要）: $Fingerprint"; exit 1
}
$formatted = ($hex -replace '(.{2})', '$1:').TrimEnd(':').ToUpper()

if ($SetupCode) {
    # 既存コードをデコード
    $padded = $SetupCode.Replace('-', '+').Replace('_', '/') 
    $padLen = $padded.Length % 4
    if ($padLen -ne 0) { $padded += "=" * (4 - $padLen) }
    $decoded = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($padded))
    $payload = $decoded | ConvertFrom-Json -AsHashtable
} elseif ($Url) {
    $payload = @{
        url = $Url
        expiresAtMs = [long]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()) + 1800000
    }
    if ($BootstrapToken) { $payload.bootstrapToken = $BootstrapToken }
    if ($Token)          { $payload.token = $Token }
} else {
    Write-Error "-SetupCode または -Url を指定してください"; exit 1
}

$payload.tlsFingerprintSha256 = $formatted

$json = $payload | ConvertTo-Json -Compress
$newCode = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($json)).Replace('+','-').Replace('/','_').TrimEnd('=')

Write-Host ""
Write-Host "📱 フィンガープリント埋め込み済みセットアップコード" -ForegroundColor Cyan
Write-Host ("=" * 60) -ForegroundColor DarkGray
Write-Host "Gateway   : $($payload.url)" -ForegroundColor Green
Write-Host "Fingerprint: $formatted" -ForegroundColor Yellow
Write-Host ("=" * 60) -ForegroundColor DarkGray
Write-Host ""
Write-Host "▼ アプリの「コードを入力」欄に貼り付け:" -ForegroundColor White
Write-Host $newCode -ForegroundColor Cyan
Write-Host ""
Write-Host "※ このコードには TLS フィンガープリントが含まれており、" -ForegroundColor DarkYellow
Write-Host "  接続時にフィンガープリントの手動入力をスキップできます。" -ForegroundColor DarkYellow
$newCode | Set-Clipboard
Write-Host "✅ クリップボードにコピーしました" -ForegroundColor Green
