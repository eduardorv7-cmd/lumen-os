#!/usr/bin/env bash
# Shared helpers for the Lumen OS macOS scripts (source it).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
DIST="$REPO/dist"
say() { printf '\033[%sm[%s] %s\033[0m\n' "${2:-0}" "$(date '+%Y-%m-%d %H:%M:%S')" "$1"; }
need_brew() {
  command -v brew >/dev/null || { say "Falta Homebrew: instalalo desde https://brew.sh y vuelve a ejecutar." 31; exit 1; }
}
ensure_adb() {
  command -v adb >/dev/null || { need_brew; say "Instalando adb (Android Platform-Tools oficiales) con Homebrew ..." 36; brew install --cask android-platform-tools; }
  adb start-server >/dev/null 2>&1
}
get_tv() {
  if [ -n "${LUMEN_TV:-}" ]; then TV="$LUMEN_TV"
  elif [ -f "$HERE/tv.txt" ]; then TV="$(tr -d ' \r\n' < "$HERE/tv.txt")"
  else read -r -p "IP del TV (Ajustes > Red e Internet > tu red), p. ej. 192.168.1.50: " TV; echo "$TV" > "$HERE/tv.txt"; fi
  case "$TV" in *:*) ;; *) TV="$TV:5555";; esac
}
a() { say "> adb $*" 33; adb -s "$TV" "$@"; }
connect_tv() {
  say "> adb connect $TV" 33
  adb connect "$TV"
  for i in $(seq 1 30); do
    [ "$(adb -s "$TV" get-state 2>/dev/null)" = device ] && { say "Conectado a $TV" 32; return 0; }
    [ "$i" = 1 ] && say 'Mira el TV: si pregunta "Permitir depuracion USB?", marca "Permitir siempre" y OK.' 35
    sleep 2; adb connect "$TV" >/dev/null 2>&1
  done
  say "No pude conectar con $TV. Si dice 'No route to host': Ajustes del Sistema > Privacidad y seguridad > Red local > activa Terminal, y reintenta." 31
  return 1
}
