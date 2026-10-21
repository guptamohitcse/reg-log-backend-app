# ============================================================
#  start-frontend.ps1
#  Runs the Vite dev server for the React app on :5173
# ============================================================
. (Join-Path $PSScriptRoot 'common.ps1')

if (-not (Test-Path (Join-Path $frontend 'node_modules'))) {
    Write-Host '[frontend] installing npm dependencies ...' -ForegroundColor Cyan
    Push-Location $frontend
    & npm.cmd install --no-audit --no-fund
    Pop-Location
}

Write-Host '[frontend] starting http://localhost:5173 (ctrl+c to stop)' -ForegroundColor Green
Push-Location $frontend
& npm.cmd run dev
Pop-Location
