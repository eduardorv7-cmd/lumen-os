#!/usr/bin/env bash
# Instala dist/lumen-os.apk y la deja como Inicio, con reversion automatica si algo sale mal (igual que en Windows).
source "$(dirname "$0")/comun.sh"
APK="$DIST/lumen-os.apk"; [ -f "$APK" ] || { say "No existe dist/lumen-os.apk: primero mac/compilar.sh" 31; exit 1; }
ensure_adb; get_tv; connect_tv || exit 1
home_now() { adb -s "$TV" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | grep / | tail -n 1; }
BEFORE="$(home_now)"; say "Inicio actual: $BEFORE" 36
a install -r "$APK" | grep -q Success || { say "La instalacion fallo; no se cambio nada mas." 31; exit 1; }
a shell am start -W -n com.gathin.tvhome/.MainActivity | grep -q 'Status: ok' || { say "Lumen OS no arranco; el Inicio queda como estaba." 31; exit 1; }
a shell pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION
say "Optimizando para el procesador del TV (1-2 min) ..." 36
a shell cmd package compile -m speed -f com.gathin.tvhome >/dev/null
a shell settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService
a shell settings put secure accessibility_enabled 1
SS=$(adb -s "$TV" shell settings get secure screensaver_components | tr -d "\r")
case "$SS" in com.gathin.tvhome/*) ;; *) [ -f "$HERE/deshacer-salvapantallas.txt" ] || echo "$SS" > "$HERE/deshacer-salvapantallas.txt";; esac
a shell settings put secure screensaver_components com.gathin.tvhome/.LumenDream
case "$BEFORE" in com.gathin.tvhome/*) say "Lumen OS ya era el Inicio. Listo." 32; exit 0;; esac
STOCK="${BEFORE%%/*}"
case " com.google.android.apps.tv.launcherx com.google.android.tvlauncher com.google.android.leanbacklauncher " in
  *" $STOCK "*) ;; *) say "El Inicio actual ($STOCK) no es uno que conozca: lo dejo. Abre Lumen OS desde Apps." 31; exit 0;; esac
echo "$STOCK" > "$HERE/deshacer-inicio.txt"
a shell pm disable-user --user 0 "$STOCK"
a shell cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity
sleep 2; NOW="$(home_now)"
case "$NOW" in com.google.android.tungsten.setupwraith/*)
  if adb -s "$TV" shell dumpsys wifi | grep -q 'cname=com.google.android.tungsten.setupwraith'; then
    say "El asistente de configuracion es dueno de tu Wi-Fi guardado: no lo desactivo. Revierto." 31; NOW=""
  else
    echo com.google.android.tungsten.setupwraith >> "$HERE/deshacer-inicio.txt"
    a shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
    a shell cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity
    sleep 2; NOW="$(home_now)"
  fi;; esac
case "$NOW" in com.gathin.tvhome/*) a shell input keyevent KEYCODE_HOME; say "Lumen OS es tu Inicio. Para volver: mac/deshacer.sh" 32;;
  *) say "El Inicio quedo en '$NOW': revierto." 31
     while read -r p; do a shell pm enable --user 0 "$p"; done < "$HERE/deshacer-inicio.txt"
     a shell cmd package set-home-activity --user 0 "$BEFORE"; exit 1;; esac
