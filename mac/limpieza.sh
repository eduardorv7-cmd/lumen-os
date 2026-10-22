#!/usr/bin/env bash
# Desactiva (no desinstala) la lista de tv-profiles/<perfil>/debloat.txt (por defecto tcl-g07).
source "$(dirname "$0")/comun.sh"
LIST="$REPO/tv-profiles/${1:-tcl-g07}/debloat.txt"; [ -f "$LIST" ] || { say "No existe $LIST" 31; exit 1; }
PKGS=$(sed 's/#.*//' "$LIST" | tr -d ' \r' | grep -v '^$')
echo "Se van a DESACTIVAR (reversible):"; echo "$PKGS" | sed 's/^/  /'
read -r -p "Continuar? (s/N) " ok; case "$ok" in s|S) ;; *) exit 0;; esac
ensure_adb; get_tv; connect_tv || exit 1
BK="$HERE/respaldo-$(date +%Y%m%d-%H%M%S)"; mkdir -p "$BK"
adb -s "$TV" shell pm list packages -d > "$BK/desactivados-antes.txt"
adb -s "$TV" shell settings list global > "$BK/settings-global.txt"
for p in $PKGS; do a shell pm disable-user --user 0 "$p"; done
say "Listo. Respaldo en $BK. Para revertir: mac/deshacer.sh" 32
