#!/usr/bin/env bash
# Instala (una vez) JDK 17 y el Android SDK con Homebrew y compila Lumen OS -> dist/lumen-os.apk
source "$(dirname "$0")/comun.sh"
need_brew
if ! /usr/libexec/java_home -v 17 >/dev/null 2>&1; then say "Instalando JDK 17 (Eclipse Temurin) ..." 36; brew install --cask temurin@17; fi
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
command -v sdkmanager >/dev/null || { say "Instalando Android command-line tools ..." 36; brew install --cask android-commandlinetools; }
export ANDROID_HOME="${ANDROID_HOME:-$(brew --prefix)/share/android-commandlinetools}"
if [ ! -d "$ANDROID_HOME/platforms/android-34" ] || [ ! -d "$ANDROID_HOME/build-tools/34.0.0" ]; then
  say 'Instalando android-34 y build-tools 34. Si aparece una licencia, leela y responde "y".' 35
  sdkmanager --sdk_root="$ANDROID_HOME" "platforms;android-34" "build-tools;34.0.0"
fi
cd "$REPO/launcher"
if [ -f lumen.properties ]; then
  read -r -p "Ya hay una configuracion guardada (nombre, ciudad, claves). Escribe S para empezar de nuevo, o Enter para usarla: " A
  case "$A" in [sSyY]*) mv lumen.properties lumen.properties.anterior; say "Empezamos de nuevo. La anterior quedo en launcher/lumen.properties.anterior" 35;; esac
fi
[ -f lumen.properties ] || { cp lumen.properties.example lumen.properties; say "Cree launcher/lumen.properties: pon tus claves y tu ciudad (sin ellas compila igual)." 35; }
grep -q "^nombre=" lumen.properties || echo "nombre=" >> lumen.properties
if grep -q "^nombre=[[:space:]]*$" lumen.properties; then
  read -r -p "Tu nombre para el saludo (Enter para omitir): " N
  [ -n "$N" ] && sed -i "" "s/^nombre=.*/nombre=$N/" lumen.properties
fi
grep -q "^pulgadas=" lumen.properties || echo "pulgadas=" >> lumen.properties
if grep -q "^pulgadas=[[:space:]]*$" lumen.properties; then
  read -r -p "De cuantas pulgadas es tu TV? (32 a 100, Enter = 55): " P
  P="${P//[^0-9]/}"
  [ -n "$P" ] || P=55
  sed -i "" "s/^pulgadas=.*/pulgadas=$P/" lumen.properties
fi
# Clima: la ciudad escrita se busca en Open-Meteo (sin clave); Enter = el TV la detecta por su conexion.
grep -q "^weather_city=" lumen.properties || printf "weather_city=\nweather_lat=\nweather_lon=\n" >> lumen.properties
if grep -q "^weather_city=[[:space:]]*$" lumen.properties; then
  read -r -p "Ciudad para el clima (Enter = detectar automaticamente): " C
  CITY=auto; LAT=; LON=
  if [ -n "$C" ]; then
    J=$(curl -fsS --get --data-urlencode "name=$C" "https://geocoding-api.open-meteo.com/v1/search?count=1&language=es" 2>/dev/null || true)
    LA=$(printf '%s' "$J" | plutil -extract results.0.latitude raw -o - - 2>/dev/null || true)
    LO=$(printf '%s' "$J" | plutil -extract results.0.longitude raw -o - - 2>/dev/null || true)
    NM=$(printf '%s' "$J" | plutil -extract results.0.name raw -o - - 2>/dev/null || true)
    CC=$(printf '%s' "$J" | plutil -extract results.0.country_code raw -o - - 2>/dev/null || true)
    if [ -n "$CC" ] && grep -q "^tmdb_region=[[:space:]]*$" lumen.properties; then sed -i "" "s/^tmdb_region=.*/tmdb_region=$CC/" lumen.properties; fi
    if [ -n "$LA" ] && [ -n "$LO" ]; then CITY="$NM"; LAT="$LA"; LON="$LO"; say "Clima: $NM" 32
    else say "No encontre esa ciudad; el TV la detectara automaticamente." 35; fi
  fi
  sed -i "" -e "s|^weather_city=.*|weather_city=$CITY|" -e "s|^weather_lat=.*|weather_lat=$LAT|" -e "s|^weather_lon=.*|weather_lon=$LON|" lumen.properties
fi
# Claves gratuitas (opcionales): se escriben ocultas y solo quedan en launcher/lumen.properties.
ask_key() { # ask_key clave "texto"
  grep -q "^$1=" lumen.properties || echo "$1=" >> lumen.properties
  if grep -q "^$1=[[:space:]]*$" lumen.properties; then
    say "$2" 36
    read -r -s -p "Pega tu clave (no se ve al escribir; Enter para omitir): " K; echo
    K="${K//[^A-Za-z0-9._-]/}"
    [ -n "$K" ] && sed -i "" "s|^$1=.*|$1=$K|" lumen.properties && say "Clave guardada." 32
  fi
}
ask_key tmdb_key "Clave gratuita de TMDB (themoviedb.org > Ajustes > API): peliculas y series de Netflix, Disney+, HBO Max, Apple TV, Prime..."
ask_key youtube_key "Clave gratuita de YouTube (Google Cloud > YouTube Data API v3 > Credenciales): tendencias sobre el icono de YouTube."
echo "sdk.dir=$ANDROID_HOME" > local.properties
say "> ./gradlew assembleRelease (la primera vez descarga Gradle 8.9)" 33
./gradlew assembleRelease --console=plain || { say "La compilacion fallo." 31; exit 1; }
mkdir -p "$DIST" && cp app/build/outputs/apk/release/app-release.apk "$DIST/lumen-os.apk"
say "LISTO: dist/lumen-os.apk. Ahora: mac/instalar.sh" 32
