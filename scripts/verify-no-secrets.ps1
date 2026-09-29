<#
.SYNOPSIS
    推送至 GitHub 前的機密掃描：確認沒有備份檔、金鑰檔、硬編碼密碼會進入版控。

.DESCRIPTION
    1) 取得「會被抓握（tracked / untracked but not ignored）」的檔案清單。
       若專案尚未 git init，會先在暫存目錄建立一個空的 git-dir 來套用 .gitignore 規則，
       不會污染專案。
    2) 比對禁止的路徑（資料庫備份、.env、憑證、建置產物...）。
    3) 掃描这些檔案的内容，找出 BCrypt 雜湊、私鑰、AWS/GitHub token、硬編碼密碼。

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-no-secrets.ps1
    發現問題時 exit 1，可直接用作 CI 步驟。
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$originalLocation = Get-Location
$useRepoGit = Test-Path (Join-Path $repoRoot '.git')
$gitDir = $null
$tempGit = $null

function Invoke-Git {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$GitArgs)
    if ($useRepoGit) {
        & git -C $repoRoot @GitArgs
    }
    else {
        & git --git-dir $gitDir --work-tree $repoRoot @GitArgs
    }
}

try {
    if ($useRepoGit) {
        Write-Host "使用專案既有的 .git" -ForegroundColor DarkGray
    }
    else {
        Write-Host "專案尚未 git init -> 改用暫存 git-dir 模擬 .gitignore 行為" -ForegroundColor DarkGray
        $tempGit = Join-Path $env:TEMP ("secret-scan-" + [guid]::NewGuid().ToString('N'))
        & git init --bare -q $tempGit | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "git init 失敗" }
        $gitDir = $tempGit
    }

    # ---- 1. 取得會被提交的檔案清單 ----
    $files = Invoke-Git -GitArgs @('ls-files', '--cached', '--others', '--exclude-standard') |
        Where-Object { $_ -and $_.Trim().Length -gt 0 } |
        ForEach-Object { $_.Trim() }

    Write-Host ("待檢查檔案數：{0}" -f $files.Count) -ForegroundColor Cyan

    # ---- 2. 禁止的路徑 ----
    $bannedPaths = @(
        @{ Pattern = 'mysql/backup/';              Reason = '資料庫即時備份（含密碼雜湊與客戶個資）' }
        @{ Pattern = '05-latest-backup\.sql$';      Reason = 'mysqldump 產出的即時備份' }
        @{ Pattern = '(^|/)\.env$';                 Reason = '本機環境變數（含 DB 密碼與 JWT_SECRET）' }
        @{ Pattern = '\.env\.(?!example$)[^/]+$';   Reason = '環境變數變體' }
        @{ Pattern = '(^|/)backups/';               Reason = '備份壓縮檔' }
        @{ Pattern = '\.sql\.bak$';                 Reason = 'SQL 備份' }
        @{ Pattern = '\.(pem|key|p12|pfx)$';        Reason = '私鑰 / 憑證' }
        @{ Pattern = '(^|/)id_rsa';                 Reason = 'SSH 私鑰' }
        @{ Pattern = '\.jar$';                      Reason = '建置產物' }
        @{ Pattern = '(^|/)uploads/';               Reason = '使用者上傳的圖片' }
    )

    $pathViolations = @()
    foreach ($f in $files) {
        $normalised = $f -replace '\\', '/'
        if ($normalised -like '*.gitkeep') { continue }
        foreach ($b in $bannedPaths) {
            if ($normalised -match $b.Pattern) {
                $pathViolations += [pscustomobject]@{ File = $normalised; Reason = $b.Reason }
                break
            }
        }
    }

    # ---- 3. 內容掃描 ----
    # ${VAR} / $VAR 佔位、範例值、測試用假值視為安全
    # （但「${VAR:預設值}」的預設值本身是硬編碼機密，由下方 strictRules 單獨處理）
    $placeholder = '(?i)\$\{|\$[A-Za-z_]|changeme|change_me|your[_-]|example|placeholder|xxxx|dummy|fake|test[_-]|<[^>]+>|\*{3,}|\.\.\.'

    # 無條件禁止：出現在任何被追蹤檔案都視為外洩（含測試檔與文件）
    $strictRules = @(
        @{ Name = 'BCrypt 密碼雜湊'; Pattern = '\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}' }
        @{ Name = '私鑰檔頭';         Pattern = '-----BEGIN [A-Z ]*PRIVATE KEY-----' }
        @{ Name = 'AWS Access Key';   Pattern = '\bAKIA[0-9A-Z]{16}\b' }
        @{ Name = 'GitHub Token';     Pattern = '\b(ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{36}\b' }
        @{ Name = 'Slack Token';      Pattern = '\bxox[baprs]-[0-9A-Za-z-]{10,}\b' }
        # 環境變數殼、但內含非空預設值：jwt.secret=${JWT_SECRET:handSoapSecretKey...}
        # 這種寫法會被上面的 ${ 佔位豁免放過，而預設值本身就是硬編碼機密
        # （本專案真實踩過的坑：該金鑰會進版控並被 COPY 進 Docker image）。
        # 豁免 ${VAR:?必須設定} 這種 fail-fast 寫法（值以 ? 開頭）。
        @{ Name = '環境變數內含機密預設值'; Pattern = '(?i)[\w.-]*(secret|passwd|password|pwd|token|api[_-]?key|access[_-]?key|credential)[\w.-]*\s*[:=]\s*["'']?\$\{[^}:]+:(?![-?:]|changeme|change_me|your[_-]|example|placeholder|xxxx|dummy|fake|<)[^}\s"'']{6,}' }
    )

    # 泛用比對：先取出 key 後面的「值」再判定，避免把
    # String accessToken = jwtUtil.generateToken(...) 這類變數指派誤判成硬編碼金鑰。
    # 前後綴允許 DB_ / SPRING_DATASOURCE_ / setPassword 等常見寫法。
    $genericKey = '(?i)[A-Za-z0-9_-]*(password|passwd|pass|pwd|secret|api[_-]?key|access[_-]?token|refresh[_-]?token|auth[_-]?token)[A-Za-z0-9_-]*\s*[:=(]\s*(?<v>.*)$'

    $configExt = @('.properties', '.yml', '.yaml', '.conf', '.ini')
    $codeExt   = @('.java', '.ts', '.js', '.mjs', '.sh', '.py')
    $otherExt  = @('.sql', '.json', '.xml', '.md', '.html', '.txt')
    # 測試檔允許假金鑰（但仍會套用 strictRules）
    $testPath  = '(?i)(\.spec\.|\.test\.|/test/|/tests/|__tests__)'

    $contentViolations = @()

    foreach ($f in $files) {
        $normalised = $f -replace '\\', '/'
        $ext = [IO.Path]::GetExtension($normalised).ToLowerInvariant()
        if (($configExt + $codeExt + $otherExt) -notcontains $ext) { continue }

        $full = Join-Path $repoRoot $normalised
        if (-not (Test-Path -LiteralPath $full)) { continue }
        if ((Get-Item -LiteralPath $full).Length -gt 2MB) { continue }

        $isConfig = $configExt -contains $ext
        $isCode   = $codeExt -contains $ext
        $isTest   = $normalised -match $testPath

        $lines = @(Get-Content -LiteralPath $full -Encoding UTF8 -ErrorAction SilentlyContinue)
        for ($i = 0; $i -lt $lines.Count; $i++) {
            $line = [string]$lines[$i]

            foreach ($r in $strictRules) {
                # strictRules 不做任何豁免：60 字元 BCrypt、私鑰、AKIA 金鑰
                # 不會以 $VAR 形式出現，豁免只會讓真實外洩漏抓。
                if ($line -match $r.Pattern) {
                    $contentViolations += [pscustomobject]@{
                        File    = ('{0}:{1}' -f $normalised, ($i + 1))
                        Reason  = $r.Name
                        Excerpt = $line.Trim()
                    }
                }
            }

            if (-not ($isConfig -or $isCode)) { continue }
            if ($isTest) { continue }
            if ($line -notmatch $genericKey) { continue }

            $raw   = [string]$Matches['v']
            $quoted = $raw -match '^\s*["'']'
            $value = ($raw.Trim().TrimEnd(',', ';', ')').Trim() -replace '^["'']|["'']$', '')

            if ($value.Length -eq 0) { continue }
            if ($value -match $placeholder) { continue }

            # 設定檔：裸值即視為機密；程式碼：只有字串常值才算（排除運算式与方法呼叫）
            $hit = if ($isConfig) { $value.Length -ge 6 } else { $quoted -and $value.Length -ge 8 }
            if ($hit) {
                $contentViolations += [pscustomobject]@{
                    File    = ('{0}:{1}' -f $normalised, ($i + 1))
                    Reason  = '硬編碼憑證／金鑰'
                    Excerpt = $line.Trim()
                }
            }
        }
    }

    # ---- 4. 報告 ----
    if ($pathViolations.Count -eq 0 -and $contentViolations.Count -eq 0) {
        Write-Host ""
        Write-Host "OK  未發現機密路徑或硬編碼金鑰。" -ForegroundColor Green
        exit 0
    }

    if ($pathViolations.Count -gt 0) {
        Write-Host ""
        Write-Host "發現 $($pathViolations.Count) 個不應進版控的路徑：" -ForegroundColor Red
        $pathViolations | Format-Table -AutoSize File, Reason
    }
    if ($contentViolations.Count -gt 0) {
        Write-Host "發現 $($contentViolations.Count) 處疑似硬編碼機密：" -ForegroundColor Red
        $contentViolations | Format-Table -AutoSize File, Reason, Excerpt
    }
    Write-Host ""
    Write-Host "處理方式：加入 .gitignore / 改用環境變數 / 從歷史移除（git filter-repo）並輪替金鑰。" -ForegroundColor Yellow
    exit 1
}
finally {
    Set-Location $originalLocation
    if ($tempGit -and (Test-Path $tempGit)) {
        Remove-Item -LiteralPath $tempGit -Recurse -Force -ErrorAction SilentlyContinue
    }
}
