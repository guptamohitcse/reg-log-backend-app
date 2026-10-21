# ============================================================
#  reg-log ecommerce — local development helper scripts
#  Run them with:  powershell -ExecutionPolicy Bypass -File .\scripts\<script>.ps1
# ============================================================

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root 'reg-log backend app'
$frontend = Join-Path $root 'reg-log frontend app'
$local = Join-Path $root '.local'

# --- shared settings -------------------------------------------------------
$MySqlPort = 3307
$MySqlHome = Join-Path $local 'mysql'
$MySqlData = Join-Path $MySqlHome 'data'
$MySqlIni = Join-Path $MySqlHome 'my.ini'
$DbName = 'reglog_ecommerce'
$DbUser = 'reglog'
$DbPassword = 'reglog@123'

function Get-MySqlBin {
    $candidates = @(
        'C:\Program Files\MySQL\MySQL Server 8.0\bin',
        'C:\Program Files\MySQL\MySQL Server 8.1\bin',
        'C:\xampp\mysql\bin',
        'C:\Program Files\MariaDB* 10.11\bin'
    )
    foreach ($candidate in $candidates) {
        $resolved = Resolve-Path -Path $candidate -ErrorAction SilentlyContinue
        if ($resolved) {
            $exe = Join-Path $resolved.Path 'mysql.exe'
            if (Test-Path $exe) { return $resolved.Path }
        }
    }
    # fall back to whatever is on the PATH
    $onPath = (Get-Command mysql.exe -ErrorAction SilentlyContinue).Source
    if ($onPath) { return (Split-Path -Parent $onPath) }
    return $null
}

function Get-MySqlServerBin {
    $candidates = @(
        'C:\Program Files\MySQL\MySQL Server 8.0\bin',
        'C:\Program Files\MySQL\MySQL Server 8.1\bin'
    )
    foreach ($candidate in $candidates) {
        if (Test-Path (Join-Path $candidate 'mysqld.exe')) { return $candidate }
    }
    $onPath = (Get-Command mysqld.exe -ErrorAction SilentlyContinue).Source
    if ($onPath) { return (Split-Path -Parent $onPath) }
    return $null
}

function Test-Port {
    param([int]$Port)
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $client.Connect('127.0.0.1', $Port)
        $client.Close()
        return $true
    } catch { return $false }
}

function Wait-Port {
    param([int]$Port, [int]$Seconds = 45)
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-Port -Port $Port) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Invoke-MySql {
    param([string]$Sql, [string]$Bin)
    & (Join-Path $Bin 'mysql.exe') -u root "-p$DbPassword" -P $MySqlPort -e $Sql 2>&1 | Out-String
}
