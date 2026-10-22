#!/usr/bin/env bash
# Vuelve al Inicio de Google TV y reactiva lo que Lumen OS desactivo.
source "$(dirname "$0")/comun.sh"
ensure_adb; get_tv; connect_tv || exit 1
PK="com.google.android.tungsten.setupwraith com.google.android.apps.tv.launcherx"
[ -f "$HERE/deshacer-inicio.txt" ] && PK="$(cat "$HERE/deshacer-inicio.txt") $PK"
for p in $PK; do a shell pm enable --user 0 "$p"; done
L=$(adb -s "$TV" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME com.google.android.apps.tv.launcherx | tr -d '\r' | grep / | tail -n 1)
[ -n "$L" ] && a shell cmd package set-home-activity --user 0 "$L"
a shell settings delete secure enabled_accessibility_services
[ -f "$HERE/deshacer-salvapantallas.txt" ] && a shell settings put secure screensaver_components "$(cat "$HERE/deshacer-salvapantallas.txt")"
read -r -p "Desinstalar tambien Lumen OS del TV? (s/N) " u; case "$u" in s|S) a uninstall com.gathin.tvhome;; esac
read -r -p "Reactivar las apps de la limpieza de TCL? (s/N) " r
case "$r" in s|S) for p in $(sed 's/#.*//' "$REPO/tv-profiles/tcl-g07/debloat.txt" | tr -d ' \r' | grep -v '^$'); do a shell pm enable --user 0 "$p"; done;; esac
a shell input keyevent KEYCODE_HOME; say "Listo: el TV vuelve a Google TV." 32
