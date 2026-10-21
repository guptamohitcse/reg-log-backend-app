# ============================================================
#  setup-database.ps1
#  Creates the `reglog_ecommerce` database and the `reglog` user.
# ============================================================
. (Join-Path $PSScriptRoot 'common.ps1')

# native tools print warnings on stderr - never let them abort the script
$ErrorActionPreference = 'Continue'

$bin = Get-MySqlBin
if (-not $bin) {
    Write-Host "[db] ERROR: mysql client not found. Install MySQL 8 or edit common.ps1 -> Get-MySqlBin" -ForegroundColor Red
    exit 1
}

if (-not (Test-Port -Port $MySqlPort)) {
    Write-Host "[db] MySQL is not running on port $MySqlPort - starting it first ..." -ForegroundColor Cyan
    & (Join-Path $PSScriptRoot 'start-mysql.ps1')
}

$mysql = Join-Path $bin 'mysql.exe'

# first boot of the project instance: root has an empty password
$env:MYSQL_PWD = ''
& $mysql -u root -P $MySqlPort -e 'SELECT 1;' *>$null
if ($LASTEXITCODE -ne 0) {
    $env:MYSQL_PWD = $DbPassword
    & $mysql -u root -P $MySqlPort -e 'SELECT 1;' *>$null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[db] ERROR: cannot connect to MySQL as root on port $MySqlPort" -ForegroundColor Red
        exit 1
    }
}

$sql = @"
CREATE DATABASE IF NOT EXISTS $DbName CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '$DbUser'@'localhost' IDENTIFIED BY '$DbPassword';
CREATE USER IF NOT EXISTS '$DbUser'@'%' IDENTIFIED BY '$DbPassword';
GRANT ALL PRIVILEGES ON $DbName.* TO '$DbUser'@'localhost';
GRANT ALL PRIVILEGES ON $DbName.* TO '$DbUser'@'%';
FLUSH PRIVILEGES;
"@

& $mysql -u root -P $MySqlPort -e $sql *>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host "[db] ERROR: could not create the database / user" -ForegroundColor Red
    exit 1
}

# verify the application user can use it
$env:MYSQL_PWD = $DbPassword
& $mysql -u $DbUser -P $MySqlPort -D $DbName -e 'SELECT 1;' *>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host "[db] ERROR: the '$DbUser' user cannot reach the '$DbName' database" -ForegroundColor Red
    exit 1
}

$env:MYSQL_PWD = ''
Write-Host "[db] database '$DbName' ready (user '$DbUser' / password '$DbPassword')" -ForegroundColor Green
exit 0
