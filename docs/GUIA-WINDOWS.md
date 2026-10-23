# Instalar Lumen OS desde Windows

Tiempo: unos 15 minutos la primera vez (casi todo es descarga). Necesitas Windows 10 u 11 en la misma red Wi‑Fi que el TV. No hace falta ser administrador ni instalar nada a mano: los scripts descargan todo de fuentes oficiales y lo dejan en tu carpeta de usuario (`%USERPROFILE%\.lumen-os`), sin tocar el sistema.

📺 Todo este proceso en video: **[tutorial de instalación en YouTube](https://youtu.be/p3jJC1I-lsw)** (2:33).

## 1. Preparar el TV (una sola vez, con el control)

1. **Ajustes** (engranaje) → **Preferencias del dispositivo** → **Información**. En Google TV nuevos: **Sistema** → **Información**.
2. Baja hasta **Compilación del SO de Android TV** y presiona **OK 7 veces** hasta que diga "Ahora eres desarrollador".
3. Vuelve atrás → **Opciones de desarrollador** → activa **Depuración USB**. Si ves **Depuración de red** o **ADB por red**, actívala también (funciona por Wi‑Fi, sin cable).
4. Anota la **IP del TV**: Ajustes → **Red e Internet** → tu red (por ejemplo `192.168.1.50`).

## 2. Descargar Lumen OS

En GitHub: botón **Code** → **Download ZIP**, y descomprímelo (por ejemplo en `Documentos\lumen-os`). Si usas git: `git clone https://github.com/eduardorv7-cmd/lumen-os`.

## 3. Tus claves (opcional, recomendado)

`COMPILAR.bat` te pregunta tu nombre, las pulgadas del TV, tu ciudad y tus claves gratuitas de TMDB y YouTube (no se ven al escribirlas; Enter para omitir). Cómo sacar cada clave: [PERSONALIZAR.md](PERSONALIZAR.md). Sin claves Lumen OS funciona igual, sin recomendaciones.

## 4. Compilar

Doble clic en **`windows\COMPILAR.bat`**. La primera vez:
- descarga **JDK 17 (Eclipse Temurin)** y verifica su SHA256 oficial;
- descarga las **Android command‑line tools** de dl.google.com y verifica su checksum;
- instala la plataforma Android 34 (muestra los términos del Android SDK; continuar es aceptarlos);
- compila con Gradle (descarga Gradle 8.9 la primera vez).

Al terminar dice **`LISTO: dist\lumen-os.apk`**. Las siguientes veces tarda menos de un minuto.

## 5. Instalar en el TV

Doble clic en **`windows\INSTALAR.bat`**. Te pide la IP del TV (la recuerda en `windows\tv.txt`). En el TV aparecerá **"¿Permitir la depuración USB?"**: marca **Permitir siempre desde esta computadora** y **OK**.

El script instala, comprueba que Lumen OS abre, lo optimiza para el procesador del TV y lo deja como Inicio. Si algo no sale bien, revierte solo.

Si Windows pregunta por el firewall para `adb.exe`, permite **redes privadas**.

## 6. Limpieza de anuncios (opcional)

**`windows\LIMPIEZA.bat`** muestra la lista de `tv-profiles\tcl-g07\debloat.txt`, pide confirmación, guarda un respaldo y desactiva esos paquetes (no los desinstala). Para otro modelo, crea tu propia lista en `tv-profiles\<modelo>\debloat.txt` y ejecuta `windows\limpieza.ps1 -Perfil <modelo>`.

## Otros

- **`MONITOR.bat`**: ventana que muestra cada comando adb con su hora y el logcat del TV mientras corre.
- **`YOUTUBE.bat "nombre del video"`**: abre YouTube en el TV, busca y reproduce el primer resultado.
- **`DESHACER.bat`**: vuelve al Inicio de Google TV; pregunta si desinstalar Lumen OS y si revertir la limpieza.

## Al terminar (seguridad)

Mientras la depuración esté activa, cualquiera en tu Wi‑Fi puede conectarse al TV. Cuando no la uses: **Opciones de desarrollador** → **Depuración USB** desactivada (y opcionalmente "Revocar autorizaciones de depuración USB"). Para actualizar Lumen OS más adelante, vuelve a activarla.

## Problemas comunes

| Síntoma | Solución |
|---|---|
| `No pude conectar` | Revisa la IP, que el TV y la PC estén en la misma red y que la Depuración USB siga activa. |
| `unauthorized` | Mira el TV y acepta el mensaje "Permitir depuración USB". |
| El control deja de responder | Cambia las pilas; si sigue, mantén **Inicio + Atrás** 5 s para re‑emparejar o reinicia el TV. |
| Sin recomendaciones | Revisa las claves en `launcher\lumen.properties`, vuelve a compilar e instalar. |
| Después de reinstalar no van los botones Netflix/YouTube | `INSTALAR.bat` reactiva el servicio; Android lo apaga en cada reinstalación. |
