<#
.SYNOPSIS
    檢查前端建置產物是否仍內嵌本機位址（localhost:8080）或外部佔位圖。

.DESCRIPTION
    production build 應使用 environment.prod.ts 的同源相對路徑（/api/v1、/uploads/）。
    若 angular.json 的 configurations.production 少了 fileReplacements，
    或模板又硬編了 host/port，這裡會抓到並以 exit 1 結束（可直接用作 CI 步驟）。

.EXAMPLE
    pwsh ./scripts/verify-frontend-dist.ps1
#>
[CmdletBinding()]
param(
    [string]$DistPath = 'frontend/dist/frontend/browser/browser'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$distDir = Join-Path $repoRoot $DistPath

if (-not (Test-Path -LiteralPath $distDir)) {
    Write-Host "找不到建置產物：$distDir" -ForegroundColor Red
    Write-Host "請先執行：cd frontend; npm run build" -ForegroundColor Yellow
    exit 1
}

$bundles = @(Get-ChildItem -LiteralPath $distDir -Filter '*.js' -File)
if ($bundles.Count -eq 0) {
    Write-Host "產物目錄內沒有 .js bundle：$distDir" -ForegroundColor Red
    exit 1
}

Write-Host ("檢查 {0} 個 bundle：{1}" -f $bundles.Count, $DistPath) -ForegroundColor Cyan

# ---- 1. 不得出現的內容 ----
$banned = @(
    @{ Pattern = 'localhost:8080';      Reason = '本機位址被內嵌（environment.prod.ts 未生效或模板硬編）' }
    @{ Pattern = 'via.placeholder.com'; Reason = '外部佔位圖（應改用 assets/placeholder.svg）' }
    @{ Pattern = '127.0.0.1';           Reason = '本機位址被內嵌' }
)

$violations = @()
foreach ($b in $banned) {
    foreach ($hit in @(Select-String -Path $bundles.FullName -Pattern $b.Pattern -SimpleMatch -List)) {
        $violations += [pscustomobject]@{
            File   = [IO.Path]::GetFileName($hit.Path)
            Reason = $b.Reason
        }
    }
}

# ---- 2. 必須出現的內容（證明 production 環境檔確實被套用） ----
$required = @('/api/v1', '/uploads/')
$missing = @()
foreach ($r in $required) {
    if (-not (Select-String -Path $bundles.FullName -Pattern $r -SimpleMatch -Quiet)) {
        $missing += $r
    }
}

# ---- 3. 報告 ----
if ($violations.Count -eq 0 -and $missing.Count -eq 0) {
    Write-Host ""
    Write-Host "OK  產物使用同源相對路徑，且未內嵌 localhost:8080。" -ForegroundColor Green
    exit 0
}

if ($violations.Count -gt 0) {
    Write-Host ""
    Write-Host "發現 $($violations.Count) 處不應出現在產物中的內容：" -ForegroundColor Red
    $violations | Format-Table -AutoSize File, Reason
}
if ($missing.Count -gt 0) {
    Write-Host ""
    Write-Host ("產物中找不到預期的同源路徑：{0}" -f ($missing -join '、')) -ForegroundColor Red
    Write-Host "可能原因：angular.json 的 configurations.production 缺少 fileReplacements。" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "處理方式：修正 environment.prod.ts / 模板，重新 npm run build 後再檢查。" -ForegroundColor Yellow
exit 1
