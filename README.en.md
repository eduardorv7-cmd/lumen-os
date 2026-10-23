# Lumen OS

[Español](README.md) · **English**

**An Apple TV–style Home screen for Android TV / Google TV**: clean, fast and ad-free, with full-screen recommendations from your own apps, the weather and its own boot animation. You install it from a computer on the same Wi‑Fi network. No root, no flashing and nothing uninstalled; everything can be undone with one command.

![Lumen OS on a TCL TV](docs/img/demo.gif)

▶️ **[Watch the full video (1:48)](docs/video/lumen-os-demo.mp4)**: boot, recommendations for each app, detail card with trailer, My list, weather and screensaver, recorded on a real TV.

📺 **[Installation tutorial on YouTube (2:33, in Spanish)](https://youtu.be/p3jJC1I-lsw)**: step by step on Windows, from turning on developer mode to seeing it on the TV.

**Languages:** the interface follows your TV's language, in **English** or **Spanish**, and so do the titles and synopses. The install scripts and guides are in Spanish for now.

## Features

- **Full-screen recommendations, like Apple TV's Top Shelf.** Move over an app in the dock and a movie or series from that app fills the screen (what's trending and new in your country), with title, type, year, rating and a **New** label. Every visit shows another title, and they change by themselves every 12 s. Lists refresh every 3 hours.
  - Netflix, Disney+, HBO Max, Apple TV, Prime Video, Crunchyroll, Paramount+, MUBI, Pluto TV and Tubi, via [TMDB](https://www.themoviedb.org/).
  - **YouTube**: trending videos in your country, via the YouTube Data API.
  - Apps in the grid (Prime Video, Pluto TV…) show their recommendations too.
  - An app without a catalogue shows a gradient in its own colour.
- **Detail card with trailer and My list**: hold OK (or press Menu) on an app to see the synopsis, cast and trailer, and save the title to **My list**.
- **Featured** (optional): pick your favourite series to show first in each app and in the screensaver.
- **Frosted glass** (Liquid Glass style): the dock and pills show the blurred picture behind them, with a sheen and a light rim. Runs on Android 11 without live blur, so it stays light.
- **Time and weather** in one pill (Open‑Meteo, no key). Press OK for the **hour-by-hour** forecast. Your city is detected automatically or chosen when you build. Fahrenheit in the US.
- **Greeting** with your name ("Good evening, Ana") and an automatic **night look** after sunset.
- **Lumen screensaver**: your apps' pictures full screen, with the time, date and weather.
- **Fits your TV**: from 32" to 100", the interface scales to the screen size.
- **"Lumen OS" boot animation**: a beam of light, the name letter by letter with a golden sheen and "Your cinema at home", with its own sound. See [`docs/arranque-preview.html`](docs/arranque-preview.html).
- **6-app dock** with all your other apps below.
- **Remote buttons** (Netflix, YouTube, settings) keep working without Google's Home.
- **Reversible clean-up** of the maker's ads, telemetry and demos (tested profile for TCL G07).
- **Live monitor** (Windows): every command with its time, and the TV's logcat while it runs.

## Screenshots

| Boot | Home with Apple TV |
|---|---|
| ![Boot](docs/img/capturas/00-arranque.jpg) | ![Apple TV](docs/img/capturas/01b-severance.jpg) |
| **Netflix** | **Trending on YouTube** |
| ![Netflix](docs/img/capturas/02-netflix.jpg) | ![YouTube](docs/img/capturas/03-youtube.jpg) |
| **HBO Max** | **Disney+** |
| ![HBO Max](docs/img/capturas/04-hbo-max.jpg) | ![Disney+](docs/img/capturas/05-disney.jpg) |
| **Crunchyroll** | **All your apps** |
| ![Crunchyroll](docs/img/capturas/06-crunchyroll.jpg) | ![Grid](docs/img/capturas/07-cuadricula-pluto-tv.jpg) |
| **Detail card with trailer** | **My list** |
| ![Detail](docs/img/capturas/08-ficha.jpg) | ![My list](docs/img/capturas/09-mi-lista.jpg) |
| **Hour-by-hour weather** | **Screensaver** |
| ![Weather](docs/img/capturas/10-clima.jpg) | ![Screensaver](docs/img/capturas/11-salvapantallas.jpg) |

(Screenshots taken on a TV set to Spanish.)

## Quick install

Prefer to watch? Follow the **[installation video on YouTube](https://youtu.be/p3jJC1I-lsw)** (2:33, in Spanish).

1. **On the TV**: turn on *Developer options* and *USB debugging* (Settings → System → About → press *Android TV OS build* 7 times; then Settings → System → Developer options).
2. **On the computer**, download this repository:
   - Windows: run `windows\COMPILAR.bat`, then `windows\INSTALAR.bat` ([guide](docs/GUIA-WINDOWS.md), in Spanish). The first build downloads Java 17 and the Android SDK by itself; ADB is downloaded too.
   - macOS: `mac/compilar.sh`, then `mac/instalar.sh` ([guide](docs/GUIA-MAC.md)).
   - INSTALAR asks for the TV's IP address (Settings → Network on the TV). Accept the "Allow USB debugging?" prompt on the TV the first time.
3. **Optional**: `COMPILAR.bat` asks for your free TMDB and YouTube keys (hidden while you type; Enter to skip). See below for what each one does.

### Free keys

`COMPILAR.bat` asks for both (hidden while you type). **If you don't have one, just press Enter.**

| Key | What it does | Without it |
|---|---|---|
| **TMDB** (needed for recommendations) | Full-screen movies and series for Netflix, Disney+, HBO Max, Apple TV, Prime Video, Crunchyroll and more; the detail card with synopsis, cast and trailer; **My list**; the screensaver pictures. | Those apps only show a background in their colour; no detail card or My list. |
| **YouTube** (optional) | Trending videos in your country over the YouTube icon. | YouTube looks like a normal app, in its colour. |

**Getting a TMDB key (free):**
1. Create an account at [themoviedb.org/signup](https://www.themoviedb.org/signup) and confirm your email.
2. Go to [themoviedb.org/settings/api](https://www.themoviedb.org/settings/api) → **Request an API key** → **Developer**, personal use.
3. Copy the **API Key** (32 letters and numbers).

**Getting a YouTube key (free):**
1. Go to [console.cloud.google.com](https://console.cloud.google.com/) with your Google account and create a project (e.g. "Lumen OS").
2. Open [YouTube Data API v3](https://console.cloud.google.com/apis/library/youtube.googleapis.com) → **Enable**.
3. Go to **Credentials** → **Create credentials** → **API key** and copy it (it starts with `AIza`).
4. Recommended: on the key, **API restrictions** → only *YouTube Data API v3*. It's free: Lumen OS uses about 8 of the 10,000 free daily units.

Everything else (dock, glass, weather, greeting, boot animation, remote buttons) works without keys. Your keys stay on your computer: `lumen.properties` is never committed. **Don't share the built APK**, since it carries your keys.

To go back to Google TV: `DESHACER.bat` (Windows) or `mac/deshacer.sh` (Mac).

## Works with

Android TV and Google TV on Android 9 or newer (TCL, Hisense, Philips, Sony, Xiaomi, Chromecast with Google TV, etc.). Tested on a **TCL G07 (Google TV, Android 11, 2 GB)**. Not for Samsung (Tizen), LG (webOS), Roku or Fire TV OS 8+.

## What changes on your TV

- Installs the **Lumen OS** app (`com.gathin.tvhome`) and makes it the Home screen.
- **Disables** (never uninstalls) Google TV's Home and, if needed, the setup wizard. Before that it checks the wizard does not own your saved Wi‑Fi network; if it does, it leaves it alone and reverts.
- Turns on a small accessibility service that only reads the remote's Netflix, YouTube and settings buttons.
- If Home did not end up on Lumen OS, everything is reverted automatically.
- The clean-up (`LIMPIEZA.bat`) is separate and optional: it only disables the list in `tv-profiles/`.

It never touches live TV, HDMI inputs, the keyboard, Play Store, Play Services or settings.

## Credits and licences

Author: **Eduardo Rojas** ([@eduardorv7-cmd](https://github.com/eduardorv7-cmd)).

It includes open-source parts under the MIT licence; their licence notices are in [THIRD-PARTY-NOTICES.txt](THIRD-PARTY-NOTICES.txt). Details in [CREDITS.md](CREDITS.md).

This product uses the TMDB API but is not endorsed or certified by TMDB. Weather data by [Open‑Meteo](https://open-meteo.com/) (CC BY 4.0). Not affiliated with Google, TCL, Apple, Netflix or any service; app names and logos belong to their owners.
