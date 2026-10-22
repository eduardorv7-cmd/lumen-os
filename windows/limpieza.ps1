# limpieza.ps1 - Desactiva (no desinstala) los paquetes de anuncios, telemetria y demo de una lista de tv-profiles.
param([string]$Perfil = 'tcl-g07')
. (Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) 'comun.ps1')
$Host.UI.RawUI.WindowTitle = 'Lumen OS - limpieza'
$list = Join-Path $Repo "tv-profiles\$Perfil\debloat.txt"
if (-not (Test-Path $list)) { Say "No existe $list" Red; exit 1 }
$pkgs = @(Get-Content $list | ForEach-Object { ($_ -split '#')[0].Trim() } | Where-Object { $_ })
Write-Host "Se van a DESACTIVAR (reversible) estos $($pkgs.Count) paquetes:" -ForegroundColor Cyan
$pkgs | ForEach-Object { Write-Host "  $_" }
if ((Read-Host 'Continuar? (s/N)') -notmatch '^[sS]') { exit 0 }
Ensure-Adb
$tv = Get-Tv
if (-not (Connect-Tv $tv)) { exit 1 }
$stamp = (Get-Date).ToString('yyyyMMdd-HHmmss')
$bk = Join-Path $Here "respaldo-$stamp"
New-Item -ItemType Directory -Force $bk | Out-Null
(A $tv @('shell', 'pm', 'list', 'packages', '-d')) | Set-Content (Join-Path $bk 'desactivados-antes.txt')
(A $tv @('shell', 'settings', 'list', 'global')) | Set-Content (Join-Path $bk 'settings-global.txt')
foreach ($p in $pkgs) { A $tv @('shell', 'pm', 'disable-user', '--user', '0', $p) | Out-Null }
Say "Listo. Respaldo en $bk. Para revertir: DESHACER.bat" Green
