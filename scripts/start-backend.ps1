# ============================================================
#  start-backend.ps1
#  Builds (if needed) and runs the Spring Boot backend on :8080
#  talking to the local MySQL instance on :3307
# ============================================================
param([switch]$Rebuild, [switch]$NoBuild)

. (Join-Path $PSScriptRoot 'common.ps1')

$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = "$MySqlPort"
$env:DB_NAME = $DbName
$env:DB_USER = $DbUser
$env:DB_PASSWORD = $DbPassword
$env:SERVER_PORT = '8080'
$env:CORS_ORIGINS = 'http://localhost:5173,http://127.0.0.1:5173,http://localhost:4173,http://127.0.0.1:4173'

$jar = Join-Path $backend 'target\reg-log-backend-app.jar'

if ($Rebuild -or -not (Test-Path $jar)) {
    if ($NoBuild) {
        Write-Host "[backend] ERROR: jar missing and -NoBuild was given" -ForegroundColor Red
        exit 1
    }
    Write-Host "[backend] building with maven ..." -ForegroundColor Cyan
    Push-Location $backend
    & mvn -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { Pop-Location; Write-Host '[backend] build failed' -ForegroundColor Red; exit 1 }
    Pop-Location
}

Write-Host '[backend] starting http://localhost:8080 (ctrl+c to stop)' -ForegroundColor Green
Push-Location $backend
& java -jar $jar
Pop-Location
