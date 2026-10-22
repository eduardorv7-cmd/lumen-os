# youtube.ps1 "nombre del video"  -> abre YouTube en el TV, busca y reproduce el primer resultado.
param([Parameter(Mandatory=$true)][string]$Video, [string]$Tv = '')
. (Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) 'comun.ps1')
Ensure-Adb
if (-not $Tv) { $Tv = Get-Tv }
function A([string[]]$a) { Say "> adb $($a -join ' ')" Yellow; $o = & $Adb -s $Tv @a 2>&1 | Out-String; return $o }
function UiText { A @('shell','uiautomator','dump','/sdcard/yt.xml') | Out-Null; return (& $Adb -s $Tv shell cat /sdcard/yt.xml 2>&1 | Out-String) }

& $Adb connect $Tv | Out-Null
$q = [uri]::EscapeDataString($Video)
A @('shell','input','keyevent','KEYCODE_HOME') | Out-Null; Start-Sleep 2
A @('shell','am','start','-a','android.intent.action.VIEW','-d',"'https://www.youtube.com/results?search_query=$q'",'com.google.android.youtube.tv') | Out-Null
Start-Sleep 8
$ui = UiText
if ($ui -match '(?i)qui.n est. viendo|who.s watching|elige una cuenta|choose an account') {
  Say 'Pantalla "Quien esta viendo?": elijo el perfil con foco (OK)' Magenta
  A @('shell','input','keyevent','KEYCODE_DPAD_CENTER') | Out-Null; Start-Sleep 5
  A @('shell','am','start','-a','android.intent.action.VIEW','-d',"'https://www.youtube.com/results?search_query=$q'",'com.google.android.youtube.tv') | Out-Null
  Start-Sleep 8
}
A @('shell','input','keyevent','KEYCODE_DPAD_CENTER') | Out-Null
for ($i = 0; $i -lt 10; $i++) {
  Start-Sleep 2
  $ms = & $Adb -s $Tv shell dumpsys media_session 2>&1 | Out-String
  $blk = ($ms -split 'package=') | Where-Object { $_ -like 'com.google.android.youtube.tv*' } | Select-Object -First 1
  if ($blk -match 'state=PlaybackState \{state=3' -and $blk -match 'description=([^\r\n]+)') { Say "Reproduciendo: $($Matches[1])" Green; exit 0 }
}
Say 'No confirme la reproduccion; mira el TV (puede haber un anuncio o un dialogo).' Red; exit 1
