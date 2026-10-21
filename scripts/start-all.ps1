# ============================================================
#  start-all.ps1
#  One command that brings the whole stack up locally:
#      MySQL (3307)  ->  Spring Boot (8080)  ->  React (5173)
#  Then it opens the browser on the signup page.
# ============================================================
param([switch]$OpenBrowser)

. (Join-Path $PSScriptRoot 'common.ps1')

Write-Host '=== 1/4  MySQL ===' -ForegroundColor Cyan
& (Join-Path $PSScriptRoot 'start-mysql.ps1')

Write-Host '=== 2/4  Database schema ===' -ForegroundColor Cyan
& (Join-Path $PSScriptRoot 'setup-database.ps1')

Write-Host '=== 3/4  Spring Boot backend (background) ===' -ForegroundColor Cyan
$logDir = Join-Path $root '.local\logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$backendScript = Join-Path $PSScriptRoot 'start-backend.ps1'
$frontendScript = Join-Path $PSScriptRoot 'start-frontend.ps1'

Start-Process -FilePath 'powershell.exe' `
    -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$backendScript`"" `
    -WorkingDirectory $backend -WindowStyle Minimized `
    -RedirectStandardOutput (Join-Path $logDir 'backend.out.log') `
    -RedirectStandardError (Join-Path $logDir 'backend.err.log')

Write-Host '=== 4/4  React frontend (background) ===' -ForegroundColor Cyan
Start-Process -FilePath 'powershell.exe' `
    -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$frontendScript`"" `
    -WorkingDirectory $frontend -WindowStyle Minimized `
    -RedirectStandardOutput (Join-Path $logDir 'frontend.out.log') `
    -RedirectStandardError (Join-Path $logDir 'frontend.err.log')

Write-Host ''
Write-Host 'Waiting for the backend to answer on :8080 ...' -ForegroundColor DarkGray
if (Wait-Port -Port 8080 -Seconds 90) {
    Write-Host '[ok] backend  -> http://localhost:8080/api/health' -ForegroundColor Green
} else {
    Write-Host '[!!] backend did not start - check the backend window for errors' -ForegroundColor Red
}
if (Wait-Port -Port 5173 -Seconds 60) {
    Write-Host '[ok] frontend -> http://localhost:5173' -ForegroundColor Green
} else {
    Write-Host '[!!] frontend did not start - check the frontend window for errors' -ForegroundColor Red
}

Write-Host ''
Write-Host '  Pages : http://localhost:5173/signup  |  /login  |  /home' -ForegroundColor White
Write-Host '  Demo  : admin / admin@123' -ForegroundColor White
Write-Host '  Stop  : powershell -ExecutionPolicy Bypass -File .\scripts\stop-all.ps1' -ForegroundColor White

if ($OpenBrowser) {
    Start-Process 'http://localhost:5173/signup'
}
