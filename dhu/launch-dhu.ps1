# Lanza el DHU solo cuando el servidor de unidad principal esta activo en el movil.
$ErrorActionPreference = "Stop"

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$dhu = "$env:LOCALAPPDATA\Android\Sdk\extras\google\auto\desktop-head-unit.exe"
$config = Join-Path $PSScriptRoot "car-display.ini"
$serial = "RZCX52DS5ZW"

function Test-HeadUnitServer {
    $out = & $adb -s $serial shell "ss -tln 2>/dev/null | grep ':5277 ' || netstat -an 2>/dev/null | grep ':5277'" 2>$null
    return [bool]($out -match "5277")
}

if (-not (Test-Path $dhu)) {
    Write-Error "DHU no encontrado. Instalalo desde Android Studio > SDK Manager > Android Auto Desktop Head Unit Emulator."
    exit 1
}

Get-Process desktop-head-unit -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Milliseconds 400

& $adb -s $serial forward --remove tcp:5277 2>$null
& $adb -s $serial forward tcp:5277 tcp:5277 | Out-Null

if (-not (Test-HeadUnitServer)) {
    Write-Host ""
    Write-Host "=== SERVIDOR NO ACTIVO EN EL MOVIL ===" -ForegroundColor Yellow
    Write-Host "Abriendo Ajustes de desarrollador de Android Auto en el telefono..."
    & $adb -s $serial shell am start -a com.google.android.projection.gearhead.SETTINGS | Out-Null
    Write-Host ""
    Write-Host "En el MOVIL haz esto (en orden):"
    Write-Host "  1. Modo de aplicacion -> DESARROLLADOR"
    Write-Host "  2. Menu tres puntos (arriba) -> INICIAR SERVIDOR DE UNIDAD PRINCIPAL"
    Write-Host "  3. Debe aparecer una notificacion de Android Auto (servidor en marcha)"
    Write-Host "  4. Cierra Android Auto desde recientes (desliza la app fuera)"
    Write-Host ""
    Write-Host "Esperando servidor en puerto 5277 (max 120 s)..."

    $deadline = (Get-Date).AddSeconds(120)
    while ((Get-Date) -lt $deadline) {
        if (Test-HeadUnitServer) {
            Write-Host "Servidor detectado." -ForegroundColor Green
            break
        }
        Start-Sleep -Seconds 2
        Write-Host "." -NoNewline
    }
    Write-Host ""
}

if (-not (Test-HeadUnitServer)) {
    Write-Host "Sigue sin servidor. No se puede conectar el DHU." -ForegroundColor Red
    Write-Host "Comprueba: modo desarrollador activado (pulsa Version 10 veces en Android Auto)."
    exit 1
}

$defaultIni = Join-Path $env:USERPROFILE ".android\headunit.ini"
$androidDir = Split-Path $defaultIni -Parent
if (-not (Test-Path $androidDir)) { New-Item -ItemType Directory -Path $androidDir | Out-Null }
Copy-Item -Path $config -Destination $defaultIni -Force

Write-Host "Iniciando DHU (1280x720, dpi 160)..."
$dhuDir = Split-Path $dhu
Start-Process -FilePath $dhu -ArgumentList "-c `"$config`"","--adb=5277" -WorkingDirectory $dhuDir
Write-Host "DHU lanzado. Pantalla desbloqueada en el movil."
