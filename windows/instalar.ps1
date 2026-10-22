# instalar.ps1 - Instala dist\lumen-os.apk en el TV y la deja como Inicio (reversible con DESHACER.bat).
# Orden seguro: instalar, arrancar y comprobar que abre, y solo entonces desactivar (no desinstalar) el Inicio
# de Google TV. Si al final el Inicio no es Lumen OS, todo se revierte solo.
. (Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) 'comun.ps1')
$Host.UI.RawUI.WindowTitle = 'Lumen OS - instalar'
$apk = Join-Path $Dist 'lumen-os.apk'
if (-not (Test-Path $apk)) { Say 'No existe dist\lumen-os.apk: primero ejecuta COMPILAR.bat' Red; exit 1 }
Ensure-Adb
$tv = Get-Tv
if (-not (Connect-Tv $tv)) { exit 1 }

$homeQ = @('shell', 'cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME')
function HomeNow { ((A $tv $homeQ) -split "`r?`n" | Where-Object { $_ -match '/' } | Select-Object -Last 1).Trim() }
$before = HomeNow
Say "Inicio actual: $before" Cyan

# 1. Instalar y comprobar que arranca.
$out = A $tv @('install', '-r', $apk)
if ($out -notmatch 'Success') { Say 'La instalacion fallo; no se cambio nada mas.' Red; exit 1 }
$start = A $tv @('shell', 'am', 'start', '-W', '-n', 'com.gathin.tvhome/.MainActivity')
if ($start -notmatch 'Status: ok') { Say 'Lumen OS no arranco; el Inicio de Google TV queda como estaba.' Red; exit 1 }
A $tv @('shell', 'pm', 'grant', 'com.gathin.tvhome', 'android.permission.ACCESS_FINE_LOCATION') | Out-Null
Say 'Optimizando para el procesador del TV (1-2 min) ...' Cyan
A $tv @('shell', 'cmd', 'package', 'compile', '-m', 'speed', '-f', 'com.gathin.tvhome') | Out-Null

# 2. Botones del control (Netflix, YouTube, engranaje) sin el Inicio de Google: servicio de accesibilidad.
A $tv @('shell', 'settings', 'put', 'secure', 'enabled_accessibility_services', 'com.gathin.tvhome/.HomeKeyService') | Out-Null
A $tv @('shell', 'settings', 'put', 'secure', 'accessibility_enabled', '1') | Out-Null

# Salvapantallas Lumen OS (fotos de tus apps + reloj). El anterior se guarda para DESHACER.
$ssFile = Join-Path $Here 'deshacer-salvapantallas.txt'
$ss = ((A $tv @('shell', 'settings', 'get', 'secure', 'screensaver_components')) -split "`r?`n" | Select-Object -First 1).Trim()
if ($ss -and $ss -notlike 'com.gathin.tvhome/*' -and -not (Test-Path $ssFile)) { Set-Content -Path $ssFile -Value $ss -Encoding ASCII }
A $tv @('shell', 'settings', 'put', 'secure', 'screensaver_components', 'com.gathin.tvhome/.LumenDream') | Out-Null

# 3. Inicio: solo la primera vez (si ya es Lumen OS, no se toca nada).
if ($before -like 'com.gathin.tvhome/*') { Say 'Lumen OS ya era el Inicio. Listo.' Green; exit 0 }
$stock = ($before -split '/')[0]
$known = @('com.google.android.apps.tv.launcherx', 'com.google.android.tvlauncher', 'com.google.android.leanbacklauncher')
if ($known -notcontains $stock) {
  Say "El Inicio actual ($stock) no es uno que conozca: lo dejo como esta. Abre Lumen OS desde Apps." Red
  exit 0
}
$undo = Join-Path $Here 'deshacer-inicio.txt'
Set-Content -Path $undo -Value "$stock" -Encoding ASCII
A $tv @('shell', 'pm', 'disable-user', '--user', '0', $stock) | Out-Null
A $tv @('shell', 'cmd', 'package', 'set-home-activity', '--user', '0', 'com.gathin.tvhome/.MainActivity') | Out-Null
Start-Sleep 2
$now = HomeNow
if ($now -like 'com.google.android.tungsten.setupwraith/*') {
  # Google TV's setup wizard takes Home once the stock launcher is off. It can be turned off too, but only if it
  # does not own a saved Wi-Fi network (Android would delete that network and the TV would drop off Wi-Fi).
  $wifi = A $tv @('shell', 'dumpsys', 'wifi')
  if ($wifi -match 'cname=com\.google\.android\.tungsten\.setupwraith') {
    Say 'El asistente de configuracion es dueno de tu Wi-Fi guardado: no lo desactivo. Revierto.' Red
    $now = ''
  } else {
    Add-Content -Path $undo -Value 'com.google.android.tungsten.setupwraith' -Encoding ASCII
    A $tv @('shell', 'pm', 'disable-user', '--user', '0', 'com.google.android.tungsten.setupwraith') | Out-Null
    A $tv @('shell', 'cmd', 'package', 'set-home-activity', '--user', '0', 'com.gathin.tvhome/.MainActivity') | Out-Null
    Start-Sleep 2
    $now = HomeNow
  }
}
if ($now -like 'com.gathin.tvhome/*') {
  A $tv @('shell', 'input', 'keyevent', 'KEYCODE_HOME') | Out-Null
  Say 'Lumen OS es tu Inicio. Para volver a Google TV: DESHACER.bat' Green
} else {
  Say "El Inicio quedo en '$now', no en Lumen OS: revierto." Red
  foreach ($p in (Get-Content $undo)) { A $tv @('shell', 'pm', 'enable', '--user', '0', $p) | Out-Null }
  A $tv @('shell', 'cmd', 'package', 'set-home-activity', '--user', '0', $before) | Out-Null
  exit 1
}
