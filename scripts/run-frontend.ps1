$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$frontendDir = Join-Path $repoRoot "frontend"

Set-Location -LiteralPath $frontendDir
Write-Host "启动 EviRAG 前端：http://127.0.0.1:3000" -ForegroundColor Green
npm.cmd run dev -- --host 127.0.0.1 --port 3000
