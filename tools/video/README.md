# Video de demostración

Proyecto de [Remotion](https://github.com/remotion-dev/remotion) con el que se montó `docs/video/lumen-os-demo.mp4`.

1. Graba clips del TV con `adb shell screenrecord --size 1280x720 /sdcard/lumen_dock.mp4` (los nombres están en `src/timeline.json`) y cópialos a `public/`, junto con `launcher/app/src/main/res/font/inter_tight.ttf` y `launcher/app/src/main/res/raw/sound_startup.ogg`.
2. `npm install`
3. `npm run render` → `out/lumen-os.mp4`

El orden, los títulos y los cortes de cada clip están en `src/timeline.json`.
