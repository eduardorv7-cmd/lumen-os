# Personalizar Lumen OS

## Tus claves y tu ciudad: `launcher/lumen.properties`

`COMPILAR.bat` crea `launcher/lumen.properties` y te pregunta tu nombre, las pulgadas del TV, tu ciudad y tus claves (las claves no se ven al escribirlas). Puedes editarlo después a mano. Este archivo **no se sube a git**: ahí van tus claves.

| Clave | Para qué | Cómo conseguirla |
|---|---|---|
| `nombre` | Saludo arriba a la izquierda ("Buenas tardes, Ana") | Tu nombre. `COMPILAR.bat` te lo pregunta; vacío = solo "Buenas tardes". |
| `pulgadas` | Tamaño de la interfaz según tu TV | Las pulgadas de tu TV (32 a 100). `COMPILAR.bat` te lo pregunta. Referencia 55": en un 32" todo se ve un 15 % más grande y en un 85" un 10 % más pequeño. |
| `destacados` | Tus títulos favoritos primero en cada app y en el salvapantallas | `app:tv/ID` o `app:movie/ID` (el número de la dirección del título en themoviedb.org) y, en YouTube, el id del video. Ejemplo: `destacados=appletv:tv/95396 netflix:tv/1408 hbo:tv/1399 youtube:QCZZwZQ4qNs`. Apps: `appletv netflix youtube hbo disney crunchyroll prime pluto tubi paramount mubi dgo vix`. |
| `tmdb_key` | Recomendaciones de Netflix, Disney+, HBO Max, Apple TV, Prime, etc. | Gratis: crea una cuenta en [themoviedb.org](https://www.themoviedb.org/signup) → Ajustes → API → solicita una clave (uso personal). Usa la **Clave de la API** de 32 caracteres. |
| `tmdb_region` | País del catálogo | Dos letras: `EC`, `MX`, `CO`, `ES`, `US`… Vacío: el país de tu ciudad del clima, o el del TV. |
| `youtube_key` | Tendencias de YouTube | Gratis: [Google Cloud](https://console.cloud.google.com/apis/library/youtube.googleapis.com) → habilita **YouTube Data API v3** → Credenciales → **Crear credenciales → Clave de API** → en *Restricciones de API* deja solo YouTube Data API v3. Esta API no cobra (10.000 unidades diarias; Lumen OS usa unas 8). |
| `weather_city`, `weather_lat`, `weather_lon` | Clima en la píldora | `COMPILAR.bat` te pregunta la ciudad y busca sus coordenadas. Si lo dejas vacío (o `auto`), el TV detecta tu ciudad por su conexión a internet. Open‑Meteo no pide clave. |

Después de cambiarlo: compilar e instalar otra vez.

## Diseño: `launcher/app/src/main/res/values/config.xml`

| Ajuste | Qué hace | Valor actual |
|---|---|---|
| `dock_apps` | Las apps del dock, en orden (nombre de paquete) | Apple TV, Netflix, YouTube, HBO Max, Disney+, Crunchyroll |
| `columns` | Apps por fila (y por dock) | 6 |
| `brand_name` | Nombre arriba a la izquierda y en el arranque | `Lumen OS` |
| `splash_tagline` | Frase de la animación de arranque | `Your cinema at home` |
| `lumen_splash` | Animación de arranque Lumen | `true` |
| `lumen_emblem`, `lumen_aurora` | Emblema de vidrio y aura de color en el arranque | `false` (fondo negro) |
| `cinematic_home` | Fondo a pantalla completa con recomendaciones | `true` |
| `weather_pill` | Píldora de hora y clima | `true` |
| `builtin_wallpaper` | Fondo fijo cuando no hay recomendación | `false` (usa el color de la app) |
| `tmdb_language` | Idioma de los títulos | `es-MX` |
| `live_tv_package` | App de TV en vivo para el menú Entradas | `com.tcl.tv` |
| `app_keys`, `settings_key_*` | Botones del control (TCL) | Netflix, YouTube, Prime, engranaje |

Paquetes de tus apps: `adb -s IP:5555 shell pm list packages -3`.

## Sonido de arranque

`launcher/app/src/main/res/raw/sound_startup.ogg` (3,4 s, sincronizado con la animación). Reemplázalo por otro `.ogg` del mismo nombre si quieres otro sonido.

## Funciones de la pantalla de inicio

| Función | Cómo se usa |
|---|---|
| Saludo | Arriba a la izquierda, según la hora y el `nombre` de `lumen.properties`. |
| Modo noche | Automático después del atardecer en tu ciudad: la imagen de fondo se atenúa; los widgets siguen siendo de vidrio claro. |
| Clima por horas | Sube hasta la píldora de hora y clima y pulsa OK. |
| Detalles, tráiler y Mi lista | Mantén OK (o pulsa Menú) sobre una app con recomendaciones (en el dock o en la cuadrícula): ficha de la recomendación con tráiler y botón para guardarla. |
| Mi lista | Sube hasta ★ Mi lista y pulsa OK. |
| Salvapantallas Lumen | `INSTALAR.bat` lo activa; `DESHACER.bat` devuelve el que tenías. |
