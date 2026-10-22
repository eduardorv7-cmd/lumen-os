# comun.ps1 - shared helpers for the Lumen OS Windows scripts (dot-source it).
$ErrorActionPreference = 'Continue'
$Here  = Split-Path -Parent $MyInvocation.MyCommand.Path
$Repo  = Split-Path -Parent $Here
$Adb   = Join-Path $Here 'platform-tools\adb.exe'
$Dist  = Join-Path $Repo 'dist'
$Log   = Join-Path $Here 'monitor.log'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$ProgressPreference = 'SilentlyContinue'

function Say([string]$m, [string]$c = 'Gray') {
  $l = "[$((Get-Date).ToString('yyyy-MM-dd HH:mm:ss'))] $m"
  Write-Host $l -ForegroundColor $c
  Add-Content -Path $Log -Value $l -Encoding UTF8
}

# Official Android SDK Platform-Tools (adb) from dl.google.com, checked for Google's signature.
function Ensure-Adb {
  if (-not (Test-Path $Adb)) {
    Say 'Descargando Android SDK Platform-Tools oficial (dl.google.com) ...' Cyan
    $zip = Join-Path $Here 'platform-tools.zip'
    Invoke-WebRequest -UseBasicParsing -Uri 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile $zip
    Expand-Archive -Force -Path $zip -DestinationPath $Here
    Remove-Item $zip -Force
  }
  $sig = Get-AuthenticodeSignature $Adb
  if ($sig.Status -ne 'Valid') { Say "AVISO: la firma de adb.exe no es valida ($($sig.Status))" Red }
  & $Adb start-server 2>&1 | Out-Null
}

# The TV's address: LUMEN_TV, or windows\tv.txt, or asked once and saved there.
function Get-Tv {
  if ($env:LUMEN_TV) { $t = $env:LUMEN_TV }
  elseif (Test-Path (Join-Path $Here 'tv.txt')) { $t = (Get-Content (Join-Path $Here 'tv.txt') -Raw).Trim() }
  else {
    $t = Read-Host 'IP del TV (Ajustes > Red e Internet > tu red), por ejemplo 192.168.1.50'
    Set-Content -Path (Join-Path $Here 'tv.txt') -Value $t.Trim() -Encoding ASCII
  }
  if ($t -notmatch ':\d+$') { $t = "$t`:5555" }
  return $t
}

# adb against the TV, printed with a timestamp like the live monitor.
function A([string]$tv, [string[]]$a) {
  Say "> adb $($a -join ' ')" Yellow
  $o = & $Adb -s $tv @a 2>&1 | Out-String
  if ($o.Trim()) { $o.Trim() -split "`r?`n" | Select-Object -First 12 | ForEach-Object { Write-Host "    $_" } }
  return $o
}

function Connect-Tv([string]$tv) {
  Say "> adb connect $tv" Yellow
  $o = & $Adb connect $tv 2>&1 | Out-String
  Write-Host "    $($o.Trim())"
  for ($i = 0; $i -lt 30; $i++) {
    $state = (& $Adb -s $tv get-state 2>&1 | Out-String).Trim()
    if ($state -eq 'device') { Say "Conectado a $tv" Green; return $true }
    if ($i -eq 0) { Say 'Mira el TV: si pregunta "Permitir depuracion USB?", marca "Permitir siempre" y OK.' Magenta }
    Start-Sleep 2
    & $Adb connect $tv 2>&1 | Out-Null
  }
  Say "No pude conectar con $tv. Revisa la IP y que la Depuracion USB este activa." Red
  return $false
}
