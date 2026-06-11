$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $repoRoot "backend\.env"

if (-not (Test-Path -LiteralPath $envFile)) {
    Write-Host "缺少 backend/.env。请复制 backend/.env.example 为 backend/.env 后再填写配置。" -ForegroundColor Red
    exit 1
}

$requiredKeys = @(
    "APP_PORT",
    "APP_UPLOAD_DIR",
    "APP_MAX_FILE_SIZE_MB",
    "DB_HOST",
    "DB_PORT",
    "DB_NAME",
    "DB_USERNAME",
    "DB_PASSWORD",
    "JWT_SECRET",
    "JWT_EXPIRE_MINUTES",
    "MAIL_HOST",
    "MAIL_PORT",
    "MAIL_USERNAME",
    "MAIL_PASSWORD",
    "MAIL_FROM",
    "LLM_BASE_URL",
    "LLM_API_KEY",
    "LLM_MODEL",
    "EMBEDDING_BASE_URL",
    "EMBEDDING_API_KEY",
    "EMBEDDING_MODEL",
    "TAVILY_ENABLED",
    "TAVILY_CURL_EXECUTABLE",
    "CHROMA_HOST",
    "CHROMA_PORT",
    "CHROMA_TENANT",
    "CHROMA_DATABASE",
    "CHROMA_COLLECTION_PREFIX",
    "CHUNK_MAX_CHARS",
    "CHUNK_OVERLAP_CHARS",
    "RAG_TOP_K",
    "RAG_LOW_SCORE_THRESHOLD",
    "RAG_HISTORY_TURNS"
)

$values = @{}
Get-Content -Encoding UTF8 -LiteralPath $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) {
        return
    }
    $equalsIndex = $line.IndexOf("=")
    if ($equalsIndex -le 0) {
        return
    }
    $key = $line.Substring(0, $equalsIndex).Trim()
    $value = $line.Substring($equalsIndex + 1).Trim()
    $values[$key] = $value
}

$missing = @()
foreach ($key in $requiredKeys) {
    if (-not $values.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($values[$key])) {
        $missing += $key
    }
}

if ($values.ContainsKey("JWT_SECRET") -and $values["JWT_SECRET"] -match "change-me") {
    $missing += "JWT_SECRET 仍是示例弱密钥"
}

if ($values.ContainsKey("TAVILY_ENABLED") -and $values["TAVILY_ENABLED"].ToLowerInvariant() -eq "true") {
    if (-not $values.ContainsKey("TAVILY_API_KEY") -or [string]::IsNullOrWhiteSpace($values["TAVILY_API_KEY"])) {
        $missing += "TAVILY_API_KEY（TAVILY_ENABLED=true 时必填）"
    }
}

if ($missing.Count -gt 0) {
    Write-Host "环境配置检查未通过：" -ForegroundColor Red
    $missing | ForEach-Object { Write-Host " - $_" -ForegroundColor Red }
    exit 1
}

Write-Host "环境配置检查通过。敏感配置只检查是否存在，不输出明文。" -ForegroundColor Green
