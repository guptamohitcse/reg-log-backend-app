# ============================================================
#  start-mysql.ps1
#  Starts the project local MySQL 8 instance (port 3307) so the
#  app never depends on the root password of an existing server.
# ============================================================
param([switch]$Force)

. (Join-Path $PSScriptRoot 'common.ps1')

if (Test-Port -Port $MySqlPort) {
    Write-Host "[mysql] already listening on port $MySqlPort" -ForegroundColor DarkGray
    exit 0
}

$serverBin = Get-MySqlServerBin
if (-not $serverBin) {
    Write-Host "[mysql] ERROR: mysqld.exe not found. Install MySQL 8 or edit common.ps1 -> Get-MySqlServerBin" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path (Join-Path $MySqlData 'mysql'))) {
    Write-Host "[mysql] initialising data folder at $MySqlData ..." -ForegroundColor Cyan
    New-Item -ItemType Directory -Force -Path $MySqlData | Out-Null
    & (Join-Path $serverBin 'mysqld.exe') --initialize-insecure --datadir=$MySqlData --console 2>&1 | Out-Null
}

Write-Host "[mysql] starting server on port $MySqlPort ..." -ForegroundColor Cyan
Start-Process -FilePath (Join-Path $serverBin 'mysqld.exe') `
    -ArgumentList "--defaults-file=`"$MySqlIni`"" -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $MySqlHome 'out.log') `
    -RedirectStandardError (Join-Path $MySqlHome 'err.log')

if (-not (Wait-Port -Port $MySqlPort -Seconds 45)) {
    Write-Host "[mysql] ERROR: server did not come up. Check $MySqlHome\error.log" -ForegroundColor Red
    exit 1
}
Write-Host "[mysql] up on 127.0.0.1:$MySqlPort" -ForegroundColor Green
