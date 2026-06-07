$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $repoRoot "backend"
$envFile = Join-Path $backendDir ".env"

if (-not (Test-Path -LiteralPath $envFile)) {
    Write-Host "未找到 backend/.env，请先复制 backend/.env.example 并填写本地配置。" -ForegroundColor Yellow
}

Set-Location -LiteralPath $backendDir
Write-Host "启动 EviRAG 后端：http://127.0.0.1:8080" -ForegroundColor Green
mvn spring-boot:run
