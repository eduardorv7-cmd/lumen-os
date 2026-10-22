# compilar.ps1 - Instala (una sola vez) JDK 17 y el Android SDK portables y compila Lumen OS.
# No toca el PATH ni el sistema ni pide administrador. Las herramientas quedan en %USERPROFILE%\.lumen-os
# (o en LUMEN_TOOLS); si ya existe la carpeta de una instalacion anterior (Documentos\tv-control\herramientas),
# se reutiliza. Resultado: dist\lumen-os.apk
. (Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) 'comun.ps1')
$Host.UI.RawUI.WindowTitle = 'Lumen OS - compilar'
$old = Join-Path $env:USERPROFILE 'Documents\tv-control\herramientas'
if ($env:LUMEN_TOOLS) { $Tools = $env:LUMEN_TOOLS }
elseif (Test-Path (Join-Path $old 'jdk17\bin\java.exe')) { $Tools = $old }
else { $Tools = Join-Path $env:USERPROFILE '.lumen-os' }
$Jdk   = Join-Path $Tools 'jdk17'
$Sdk   = Join-Path $Tools 'android-sdk'
$Src   = Join-Path $Repo 'launcher'
New-Item -ItemType Directory -Force -Path $Tools, $Dist | Out-Null
Say "Herramientas en $Tools" Cyan

# Valores de tu instalacion (claves, pais, ciudad del clima): launcher\lumen.properties, fuera de git.
$props = Join-Path $Src 'lumen.properties'
# Configuracion ya guardada: se puede empezar de nuevo (la anterior queda en lumen.properties.anterior).
if (Test-Path $props) {
  $again = Read-Host 'Ya hay una configuracion guardada (nombre, ciudad, claves). Escribe S para empezar de nuevo, o Enter para usarla'
  if ($again -match '^\s*[sSyY]') {
    Copy-Item $props (Join-Path $Src 'lumen.properties.anterior') -Force
    Remove-Item $props -Force
    Say 'Empezamos de nuevo. La configuracion anterior quedo en launcher\lumen.properties.anterior' Magenta
  }
}
if (-not (Test-Path $props)) {
  Copy-Item (Join-Path $Src 'lumen.properties.example') $props
  Say 'Cree launcher\lumen.properties desde el ejemplo. Pon ahi tus claves de TMDB/YouTube y tu ciudad (sin ellas compila igual, sin recomendaciones).' Magenta
}
# Writes key=value in lumen.properties (UTF-8 without BOM: Gradle would read a BOM as part of the first key).
function Set-Prop([string]$key, [string]$value) {
  $lines = @(Get-Content $props -Encoding UTF8)
  if ($lines -match ('^' + $key + '=')) { $lines = $lines -replace ('^' + $key + '=.*$'), ($key + '=' + $value) }
  else { $lines += ($key + '=' + $value) }
  [IO.File]::WriteAllLines($props, [string[]]$lines, (New-Object Text.UTF8Encoding $false))
}
function Get-Prop([string]$key) {
  $m = Select-String -Path $props -Pattern ('^' + $key + '=(.*)$') | Select-Object -First 1
  if ($m) { return $m.Matches[0].Groups[1].Value.Trim() } else { return $null }
}

$v = Get-Prop 'nombre'
if ($v -eq $null -or $v -eq '') {
  $n = Read-Host 'Tu nombre para el saludo de la pantalla principal (Enter para omitir)'
  if ($n) { Set-Prop 'nombre' $n.Trim() } elseif ($v -eq $null) { Set-Prop 'nombre' '' }
}

$v = Get-Prop 'pulgadas'
if (-not $v) {
  $p = (Read-Host 'De cuantas pulgadas es tu TV? (32 a 100, Enter = 55)') -replace '[^0-9]', ''
  if (-not $p) { $p = '55' }
  Set-Prop 'pulgadas' $p
}

# Clima: una ciudad escrita se busca en Open-Meteo (sin clave); Enter = el TV la detecta por su conexion.
if (-not (Get-Prop 'weather_city')) {
  $c = Read-Host 'Ciudad para el clima, p. ej. "Cuenca, Ecuador" (Enter = detectar automaticamente)'
  $done = $false
  if ($c) {
    try {
      $q = [uri]::EscapeDataString(($c -split ',')[0].Trim())
      # Searched in Spanish and in English ("Nueva York" and "New York" both work), biggest city first.
      # Bytes decoded as UTF-8 by hand: PowerShell 5.1 can garble accents (Bogota, Medellin) otherwise.
      $all = @{}
      foreach ($lang in 'es', 'en') {
        $raw = (Invoke-WebRequest -UseBasicParsing "https://geocoding-api.open-meteo.com/v1/search?name=$q&count=10&language=$lang").RawContentStream.ToArray()
        $r = [Text.Encoding]::UTF8.GetString($raw) | ConvertFrom-Json
        foreach ($x in @($r.results)) { if ($x -and -not $all.ContainsKey([string]$x.id)) { $all[[string]$x.id] = $x } }
      }
      $hits = @($all.Values | Sort-Object -Property @{ Expression = { [double]$_.population }; Descending = $true })
      if ($c -match ',') {
        $want = ($c -split ',', 2)[1].Trim()
        $f = @($hits | Where-Object { $_.country -like "*$want*" -or $_.admin1 -like "*$want*" -or $_.country_code -eq $want })
        if ($f.Count) { $hits = $f }
      }
      if ($hits.Count -and $hits[0]) {
        $h = $hits[0]
        Set-Prop 'weather_city' $h.name
        Set-Prop 'weather_lat' ([string]::Format([Globalization.CultureInfo]::InvariantCulture, '{0:0.####}', $h.latitude))
        Set-Prop 'weather_lon' ([string]::Format([Globalization.CultureInfo]::InvariantCulture, '{0:0.####}', $h.longitude))
        Say ('Clima: ' + $h.name + ', ' + $h.admin1 + ', ' + $h.country) Green
        # The catalogue of that country for the recommendations, unless one was already chosen.
        $reg = Get-Prop 'tmdb_region'
        if ($h.country_code -and (-not $reg -or $reg -eq 'auto')) { Set-Prop 'tmdb_region' $h.country_code }
        $done = $true
      } else { Say 'No encontre esa ciudad; el TV la detectara automaticamente.' Magenta }
    } catch { Say 'Sin conexion para buscar la ciudad; el TV la detectara automaticamente.' Magenta }
  }
  if (-not $done) { Set-Prop 'weather_city' 'auto'; Set-Prop 'weather_lat' ''; Set-Prop 'weather_lon' '' }
}

# Claves gratuitas (opcionales). Se escriben ocultas (asteriscos) y solo se guardan en launcher\lumen.properties.
function Read-Secret([string]$prompt) {
  $s = Read-Host $prompt -AsSecureString
  $b = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($s)
  try { return ([Runtime.InteropServices.Marshal]::PtrToStringBSTR($b)).Trim() } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($b) }
}
function Test-Url([string]$url) {
  try { Invoke-WebRequest -UseBasicParsing -TimeoutSec 15 $url | Out-Null; return $true } catch { return $false }
}
if (-not (Get-Prop 'tmdb_key')) {
  Say 'Clave gratuita de TMDB: peliculas y series de Netflix, Disney+, HBO Max, Apple TV, Prime Video, Crunchyroll...' Cyan
  Say '  Se saca en themoviedb.org > Ajustes > API > "Clave de la API". Sin ella esas apps muestran solo su color.' Cyan
  $k = (Read-Secret 'Pega tu clave de TMDB (no se ve al escribir; Enter para omitir)') -replace '[^A-Za-z0-9._-]', ''
  if ($k) {
    Set-Prop 'tmdb_key' $k
    if ($k.Length -gt 40 -or (Test-Url "https://api.themoviedb.org/3/configuration?api_key=$k")) { Say 'Clave de TMDB guardada.' Green }
    else { Say 'Guardada, pero TMDB no la acepto: revisala en launcher\lumen.properties.' Magenta }
  }
}
if (-not (Get-Prop 'youtube_key')) {
  Say 'Clave gratuita de YouTube: videos en tendencia sobre el icono de YouTube.' Cyan
  Say '  Google Cloud > YouTube Data API v3 > Credenciales > Clave de API. Sin ella YouTube se ve normal.' Cyan
  $k = (Read-Secret 'Pega tu clave de YouTube (no se ve al escribir; Enter para omitir)') -replace '[^A-Za-z0-9._-]', ''
  if ($k) {
    Set-Prop 'youtube_key' $k
    if (Test-Url "https://www.googleapis.com/youtube/v3/videos?part=id&chart=mostPopular&maxResults=1&key=$k") { Say 'Clave de YouTube guardada.' Green }
    else { Say 'Guardada, pero YouTube no la acepto: revisala en launcher\lumen.properties.' Magenta }
  }
}

# ---- 1. JDK 17 (Eclipse Temurin, api.adoptium.net, checksum SHA256 oficial) ----
if (-not (Test-Path (Join-Path $Jdk 'bin\java.exe'))) {
  Say 'JDK 17: consultando Eclipse Temurin (adoptium.net) ...' Cyan
  $a = (Invoke-RestMethod -UseBasicParsing 'https://api.adoptium.net/v3/assets/latest/17/hotspot?os=windows&architecture=x64&image_type=jdk&vendor=eclipse') | Select-Object -First 1
  $pkg = $a.binary.package
  $zip = Join-Path $Tools $pkg.name
  Say "Descargando $($pkg.link)" Cyan
  Invoke-WebRequest -UseBasicParsing -Uri $pkg.link -OutFile $zip
  $h = (Get-FileHash $zip -Algorithm SHA256).Hash.ToLower()
  if ($h -ne $pkg.checksum.ToLower()) { throw "SHA256 del JDK no coincide: $h vs $($pkg.checksum)" }
  Say "SHA256 OK $h" Green
  $tmp = Join-Path $Tools 'jdk-tmp'; Expand-Archive -Force $zip $tmp
  Move-Item (Get-ChildItem $tmp -Directory | Select-Object -First 1).FullName $Jdk
  Remove-Item $tmp -Recurse -Force; Remove-Item $zip -Force
}
& (Join-Path $Jdk 'bin\java.exe') -version 2>&1 | ForEach-Object { Say "$_" Cyan }

# ---- 2. Android command-line tools (dl.google.com, checksum SHA1 del repositorio oficial) ----
$SdkMgr = Join-Path $Sdk 'cmdline-tools\latest\bin\sdkmanager.bat'
if (-not (Test-Path $SdkMgr)) {
  Say 'Android SDK: leyendo el repositorio oficial de dl.google.com ...' Cyan
  [xml]$repo = (Invoke-WebRequest -UseBasicParsing 'https://dl.google.com/android/repository/repository2-3.xml').Content
  $p = $repo.DocumentElement.remotePackage | Where-Object { $_.path -eq 'cmdline-tools;latest' } | Select-Object -First 1
  $ar = $p.archives.archive | Where-Object { $_.'host-os' -eq 'windows' } | Select-Object -First 1
  $url = 'https://dl.google.com/android/repository/' + $ar.complete.url
  $zip = Join-Path $Tools $ar.complete.url
  Say "Descargando $url" Cyan
  Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $zip
  $h = (Get-FileHash $zip -Algorithm SHA1).Hash.ToLower()
  if ($h -ne $ar.complete.checksum.'#text'.ToLower() -and $h -ne "$($ar.complete.checksum)".ToLower()) { throw "SHA1 de cmdline-tools no coincide: $h" }
  Say "SHA1 OK $h" Green
  $tmp = Join-Path $Tools 'clt-tmp'; Expand-Archive -Force $zip $tmp
  New-Item -ItemType Directory -Force (Join-Path $Sdk 'cmdline-tools') | Out-Null
  Move-Item (Join-Path $tmp 'cmdline-tools') (Join-Path $Sdk 'cmdline-tools\latest')
  Remove-Item $tmp -Recurse -Force; Remove-Item $zip -Force
}
$env:JAVA_HOME = $Jdk
$env:ANDROID_HOME = $Sdk
$env:Path = "$Jdk\bin;$env:Path"

# ---- 3. Paquetes del SDK. Si pregunta por una licencia, TU decides: leela y responde "y" (o "n" para cancelar). ----
$plat = Join-Path $Sdk 'platforms\android-34'
$bt   = Join-Path $Sdk 'build-tools\34.0.0'
if (-not ((Test-Path $plat) -and (Test-Path $bt))) {
  Say 'Instalando platforms;android-34 y build-tools;34.0.0. Si aparece una licencia, leela y responde "y" para aceptarla.' Magenta
  & $SdkMgr "--sdk_root=$Sdk" '"platforms;android-34"' '"build-tools;34.0.0"'
}
if (-not ((Test-Path $plat) -and (Test-Path $bt))) { Say 'No se instalaron android-34 / build-tools 34 (licencia no aceptada o error): no se puede compilar.' Red; exit 1 }
Say 'SDK listo: android-34 y build-tools 34.0.0' Green

# ---- 4. Compilar ----
Set-Content -Path (Join-Path $Src 'local.properties') -Value ('sdk.dir=' + ($Sdk -replace '\\','\\' -replace ':','\:')) -Encoding ASCII
Push-Location $Src
Say '> gradlew.bat assembleRelease  (la primera vez descarga Gradle 8.9, 1-3 min)' Yellow
& .\gradlew.bat assembleRelease --console=plain
$code = $LASTEXITCODE
if ($code -ne 0) {
  # Windows: el antivirus u otro proceso a veces bloquea un archivo de la compilacion un instante
  # ("esta siendo utilizado por otro proceso"). Se detiene Gradle y se reintenta una vez.
  Say 'La compilacion fallo; reintento una vez (suele ser un archivo bloqueado por el antivirus) ...' Magenta
  & .\gradlew.bat --stop | Out-Null
  Start-Sleep 3
  & .\gradlew.bat assembleRelease --console=plain
  $code = $LASTEXITCODE
}
Pop-Location
$apk = Join-Path $Src 'app\build\outputs\apk\release\app-release.apk'
if ($code -eq 0 -and (Test-Path $apk)) {
  Copy-Item $apk (Join-Path $Dist 'lumen-os.apk') -Force
  Say "LISTO: dist\lumen-os.apk  ($([int]((Get-Item $apk).Length/1KB)) KB). Ahora: INSTALAR.bat" Green
} else { Say "La compilacion fallo (exit $code). Revisa el error de arriba." Red }
