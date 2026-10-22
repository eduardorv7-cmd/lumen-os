# tv-agent.ps1 - Control de tu TV por ADB desde esta PC.
# Solo ejecuta adb.exe (Android SDK Platform-Tools oficial de Google) con los
# comandos que aparecen en la carpeta "cola". Cada comando se imprime con su hora,
# y mientras corre se muestra el logcat del TV. Cierra esta ventana para detener todo.
$ErrorActionPreference = 'Continue'
$Root  = Split-Path -Parent $MyInvocation.MyCommand.Path
$Adb   = Join-Path $Root 'platform-tools\adb.exe'
$Queue = Join-Path $Root 'cola'
$Done  = Join-Path $Root 'cola\hecho'
$Out   = Join-Path $Root 'resultados'
$Apks  = Join-Path $Root 'apks'
$Log   = Join-Path $Root 'monitor.log'
New-Item -ItemType Directory -Force -Path $Queue,$Done,$Out,$Apks | Out-Null
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$Host.UI.RawUI.WindowTitle = 'TV Control - monitor en vivo'

function Stamp { (Get-Date).ToString('yyyy-MM-dd HH:mm:ss') }
function Say([string]$msg, [string]$color = 'Gray') {
  $line = "[$(Stamp)] $msg"
  Write-Host $line -ForegroundColor $color
  Add-Content -Path $Log -Value $line -Encoding UTF8
}

# ---------- 1. adb oficial ----------
if (-not (Test-Path $Adb)) {
  Say 'Descargando Android SDK Platform-Tools oficial desde dl.google.com ...' Cyan
  $zip = Join-Path $Root 'platform-tools-latest-windows.zip'
  Invoke-WebRequest -UseBasicParsing -Uri 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile $zip
  Expand-Archive -Force -Path $zip -DestinationPath $Root
}
$sig  = Get-AuthenticodeSignature $Adb
$hash = (Get-FileHash $Adb -Algorithm SHA256).Hash
Say "adb.exe firma: $($sig.Status) / $($sig.SignerCertificate.Subject)" Cyan
Say "adb.exe SHA256: $hash" Cyan
& $Adb version | ForEach-Object { Say $_ Cyan }
& $Adb start-server 2>&1 | Out-Null

# ---------- 2. utilidades ----------
function Tail-File([string]$path, [ref]$pos) {
  if (-not (Test-Path $path)) { return }
  try {
    $fs = [IO.File]::Open($path, 'Open', 'Read', 'ReadWrite')
    $fs.Seek($pos.Value, 'Begin') | Out-Null
    $sr = New-Object IO.StreamReader($fs)
    $txt = $sr.ReadToEnd()
    $pos.Value = $fs.Position
    $sr.Close()
    if ($txt) { $txt -split "`r?`n" | Where-Object { $_ } | ForEach-Object { Write-Host "    logcat| $_" -ForegroundColor DarkGray } }
  } catch {}
}

function Run-Adb([string]$id, [string]$argLine, [int]$timeoutSec = 120, [string]$stdoutFile = $null) {
  $so = if ($stdoutFile) { $stdoutFile } else { Join-Path $Out "$id.stdout" }
  $se = Join-Path $Out "$id.stderr"
  $useLogcat = -not ($argLine -match '^\s*(connect|disconnect|pair|devices|kill-server|start-server|version|logcat)\b')
  $lc = $null; $lcFile = Join-Path $Out "$id.logcat.txt"; $lcPos = [long]0
  if ($useLogcat) {
    $lc = Start-Process -FilePath $Adb -ArgumentList 'logcat -T 1 -v time *:I' -NoNewWindow -PassThru `
          -RedirectStandardOutput $lcFile -RedirectStandardError (Join-Path $Out "$id.logcat.err")
  }
  Say "> adb $argLine" Yellow
  $p = Start-Process -FilePath $Adb -ArgumentList $argLine -NoNewWindow -PassThru -RedirectStandardOutput $so -RedirectStandardError $se
  $null = $p.Handle
  $sw = [Diagnostics.Stopwatch]::StartNew()
  while (-not $p.HasExited) {
    if ($lc) { Tail-File $lcFile ([ref]$lcPos) }
    if ($sw.Elapsed.TotalSeconds -gt $timeoutSec) { Say "  timeout ($timeoutSec s), detenido" Red; try { $p.Kill() } catch {}; break }
    Start-Sleep -Milliseconds 200
  }
  Start-Sleep -Milliseconds 400
  if ($lc) { Tail-File $lcFile ([ref]$lcPos); try { Stop-Process -Id $lc.Id -Force } catch {} }
  $p.WaitForExit(2000) | Out-Null
  $code = $p.ExitCode
  $color = if ($code -eq 0) { 'Green' } else { 'Red' }
  Say "  exit=$code  ($([int]$sw.Elapsed.TotalMilliseconds) ms)" $color
  return $code
}

function Write-Result([string]$id, [string]$text) {
  Set-Content -Path (Join-Path $Out "$id.txt") -Value $text -Encoding UTF8
  Set-Content -Path (Join-Path $Out "$id.done") -Value 'ok' -Encoding ASCII
}

function Read-Txt([string]$p) { if (Test-Path $p) { [IO.File]::ReadAllText($p) } else { '' } }

function Run-Item([string]$id, [string]$line) {
  $verb, $rest = $line -split '\s+', 2
  switch ($verb.ToUpper()) {
    'ADB' {
      $code = Run-Adb $id $rest
      $o = Read-Txt (Join-Path $Out "$id.stdout"); $e = Read-Txt (Join-Path $Out "$id.stderr")
      $short = ($o.Trim() -split "`r?`n" | Select-Object -First 8) -join "`n"
      if ($short) { $short -split "`n" | ForEach-Object { Write-Host "    $_" } }
      if ($e.Trim()) { Write-Host "    $($e.Trim())" -ForegroundColor Red }
      Write-Result $id "EXIT=$code`n--- STDOUT ---`n$o`n--- STDERR ---`n$e"
    }
    'SCREENCAP' {
      $png = Join-Path $Out "$rest.png"
      $code = Run-Adb $id 'exec-out screencap -p' 60 $png
      Write-Result $id "EXIT=$code`nFILE=$png"
    }
    'UIDUMP' {
      $c1 = Run-Adb "$id-a" 'shell uiautomator dump /sdcard/window_dump.xml' 60
      $xml = Join-Path $Out "$rest.xml"
      $c2 = Run-Adb "$id-b" "pull /sdcard/window_dump.xml `"$xml`"" 60
      Write-Result $id "EXIT=$c2`nFILE=$xml`n$(Read-Txt (Join-Path $Out "$id-a.stderr"))"
    }
    'GHRELEASE' {
      # GHRELEASE owner/repo regex-del-asset  -> descarga del release oficial mas reciente en GitHub
      $repo, $rx = $rest -split '\s+', 2
      Say "> GitHub releases/latest de $repo (asset ~ $rx)" Yellow
      try {
        $rel = Invoke-RestMethod -UseBasicParsing -Headers @{ 'User-Agent' = 'tv-control' } -Uri "https://api.github.com/repos/$repo/releases/latest"
        $assets = $rel.assets | Where-Object { $_.name -match $rx }
        $list = ($rel.assets | ForEach-Object { "$($_.name)  $($_.size)  $($_.browser_download_url)" }) -join "`n"
        if (-not $assets) { Write-Result $id "EXIT=1`nTAG=$($rel.tag_name)`nNo asset matched.`n$list"; return }
        $a = $assets | Select-Object -First 1
        $dest = Join-Path $Apks $a.name
        Invoke-WebRequest -UseBasicParsing -Uri $a.browser_download_url -OutFile $dest
        $h = (Get-FileHash $dest -Algorithm SHA256).Hash
        Say "  $($a.name) tag=$($rel.tag_name) sha256=$h" Green
        Write-Result $id "EXIT=0`nTAG=$($rel.tag_name)`nFILE=$dest`nURL=$($a.browser_download_url)`nSHA256=$h`nDIGEST=$($a.digest)`nASSETS:`n$list"
      } catch { Say "  error: $_" Red; Write-Result $id "EXIT=1`n$_" }
    }
    'NOTE' { Say "# $rest" Magenta; Write-Result $id 'EXIT=0' }
    default { Say "Comando ignorado (solo ADB/SCREENCAP/UIDUMP/GHRELEASE/NOTE): $line" Red; Write-Result $id "EXIT=2`nunknown verb" }
  }
}

# ---------- 3. bucle ----------
Say "Listo. Vigilando $Queue  (cierra esta ventana para detener)." Green
while ($true) {
  Get-ChildItem -Path $Queue -Filter '*.cmd' -File | Sort-Object Name | ForEach-Object {
    $id = $_.BaseName
    $line = ([IO.File]::ReadAllText($_.FullName)).Trim([char]0xFEFF, ' ', "`r", "`n", "`t")
    Move-Item -Force -Path $_.FullName -Destination (Join-Path $Done $_.Name)
    try { Run-Item $id $line } catch { Say "error: $_" Red; Write-Result $id "EXIT=99`n$_" }
  }
  Start-Sleep -Milliseconds 300
}
