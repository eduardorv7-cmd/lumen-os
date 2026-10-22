# deshacer.ps1 - Vuelve el TV al Inicio de Google TV y reactiva todo lo que Lumen OS desactivo.
. (Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) 'comun.ps1')
$Host.UI.RawUI.WindowTitle = 'Lumen OS - deshacer'
Ensure-Adb
$tv = Get-Tv
if (-not (Connect-Tv $tv)) { exit 1 }
$pk = @('com.google.android.tungsten.setupwraith', 'com.google.android.apps.tv.launcherx')
$undo = Join-Path $Here 'deshacer-inicio.txt'
if (Test-Path $undo) { $pk = @(Get-Content $undo) + $pk | Select-Object -Unique }
foreach ($p in $pk) { A $tv @('shell', 'pm', 'enable', '--user', '0', $p) | Out-Null }
$launcher = ((A $tv @('shell', 'cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME', 'com.google.android.apps.tv.launcherx')) -split "`r?`n" | Where-Object { $_ -match '/' } | Select-Object -Last 1)
if ($launcher) { A $tv @('shell', 'cmd', 'package', 'set-home-activity', '--user', '0', $launcher.Trim()) | Out-Null }
A $tv @('shell', 'settings', 'delete', 'secure', 'enabled_accessibility_services') | Out-Null
$ssFile = Join-Path $Here 'deshacer-salvapantallas.txt'
if (Test-Path $ssFile) { A $tv @('shell', 'settings', 'put', 'secure', 'screensaver_components', (Get-Content $ssFile -Raw).Trim()) | Out-Null }
$ans = Read-Host 'Tambien desinstalar Lumen OS del TV? (s/N)'
if ($ans -match '^[sS]') { A $tv @('uninstall', 'com.gathin.tvhome') | Out-Null }
$ans = Read-Host 'Reactivar tambien las apps de la limpieza de TCL (tv-profiles)? (s/N)'
if ($ans -match '^[sS]') {
  $list = Join-Path $Repo 'tv-profiles\tcl-g07\debloat.txt'
  foreach ($line in (Get-Content $list)) { $p = ($line -split '#')[0].Trim(); if ($p) { A $tv @('shell', 'pm', 'enable', '--user', '0', $p) | Out-Null } }
}
A $tv @('shell', 'input', 'keyevent', 'KEYCODE_HOME') | Out-Null
Say 'Listo: el TV vuelve a Google TV.' Green
