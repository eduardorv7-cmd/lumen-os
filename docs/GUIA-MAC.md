# Instalar Lumen OS desde macOS

Funciona en Mac con Apple Silicon o Intel. Usa [Homebrew](https://brew.sh) para instalar las herramientas oficiales (JDK 17 de Eclipse Temurin, Android command‑line tools y adb).

> Los scripts de Mac son equivalentes a los de Windows pero todavía no se han probado en un Mac. Si algo falla, el mensaje de error dice qué paso fue.

## 1. Preparar el TV

Igual que en Windows: [GUIA-WINDOWS.md, paso 1](GUIA-WINDOWS.md#1-preparar-el-tv-una-sola-vez-con-el-control) (Opciones de desarrollador → Depuración USB, y anotar la IP).

## 2. Herramientas

Si no tienes Homebrew, en **Terminal**:

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

El resto lo instala `mac/compilar.sh` la primera vez (`temurin@17`, `android-commandlinetools`, `android-platform-tools`).

## 3. Descargar Lumen OS

```bash
git clone https://github.com/eduardorv7-cmd/lumen-os
cd lumen-os
cp launcher/lumen.properties.example launcher/lumen.properties   # y ponle tus claves y ciudad
```

## 4. Compilar e instalar

```bash
mac/compilar.sh      # -> dist/lumen-os.apk
mac/instalar.sh      # pide la IP del TV la primera vez y la guarda en mac/tv.txt
```

En el TV acepta **"¿Permitir la depuración USB?"** con **Permitir siempre**.

### "No route to host"

macOS bloquea la red local a las apps que no tienen permiso. Ve a **Ajustes del Sistema → Privacidad y seguridad → Red local** y activa **Terminal** (o iTerm / la app que uses). Cierra y vuelve a abrir la terminal y reintenta.

## 5. Opcional

```bash
mac/limpieza.sh            # limpieza de anuncios del perfil tcl-g07 (pide confirmación, guarda respaldo)
mac/deshacer.sh            # volver a Google TV
adb -s IP:5555 shell input keyevent KEYCODE_HOME    # cualquier comando adb a mano
```

Cuando termines, apaga la **Depuración USB** en el TV.
