# ============================================================
#  stop-all.ps1
#  Stops the local backend, the React dev server and the
#  project local MySQL instance.
# ============================================================
param([switch]$KeepMysql)

function Stop-ByPort {
    param([int]$Port)
    $connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    foreach ($connection in $connections) {
        $pid_ = $connection.OwningProcess
        if ($pid_ -and $pid_ -ne $PID) {
            Write-Host "[stop] killing pid $pid_ on port $Port" -ForegroundColor DarkGray
            Stop-Process -Id $pid_ -Force -ErrorAction SilentlyContinue
        }
    }
}

Stop-ByPort -Port 8080
Stop-ByPort -Port 5173
Stop-ByPort -Port 4173
if (-not $KeepMysql) {
    Stop-ByPort -Port 3307
}
Write-Host '[stop] done' -ForegroundColor Green
