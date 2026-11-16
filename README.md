# EZStats for Android

A native Material 3 app for the [EZStats](https://github.com/colebolebole/EZStats) dashboard. It talks to the
server's own JSON API (the same one its web page uses), so everything you can do on the website works here,
and you choose which server (URL) to connect to.

## What it does

- **Servers** – add, edit, delete and switch between EZStats servers. Each remembers its own login.
  URLs without a scheme (`192.168.1.10:8765`) default to `http://`.
- **Sign in** – with your Minecraft name and the password from `/ezstats register`, or paste a one-time link from `/ezstats link`.
- **Devices** – a live dashboard (every 2 s by default) grouped by home, with a summary of what is heating, cooling or needs a look:
  - thermostats open full screen with a draggable dial (with haptic detents), −/+, power, eco/normal/turbo profile,
    attention chips and the nearest weather station's week
  - fridges / freezers / refrigerators (targets, defrost, auto-defrost, door, frost), humidifiers and dehumidifiers
    (target, tank) and air purifiers (fan mode, filter, AQI) open full screen too
  - heat pumps / ACs / furnaces / boilers switch on and off right on their tile; thermometers, sensors, monitors and vents show their readings
- **Rename devices, make homes, move devices between homes** – long-press a tile (or ⋮ on a full-screen device).
- **Weather** tab (if the server has it on) – built like a weather app: swipe between stations under a live animated sky
  (sun, cloud, rain, snow, thunderstorms), each with a huge temperature, a summary of today, a week of range bars,
  and wind, air quality, rain and lightning tiles. Star the stations you care about and the page shows just those; the list button
  still shows every station (search, filters, favorites first), and tapping one you haven't starred opens it as an extra page.
- **Settings** – theme (system, light, dark), wallpaper colours, °F/°C (or follow the server), refresh rate,
  animated weather, and haptics on/off with a strength.
- Material 3 Expressive: springy motion throughout, shape-morphing buttons, connected button groups, and
  elements that stretch a little under your finger.

Note: over `http://` your password and session cookie are unencrypted. Use an `https://` URL (reverse proxy) for anything on the internet.

## Code layout

```
app/src/main/java/com/ezstats/android/
  EzViewModel.kt        app state, polling, device changes
  data/                 the server's JSON API (EzApi), models and parsing, saved servers and settings
  ui/EzApp.kt           which screen is showing
  ui/theme/             colours, type, shapes, motion (springs), status tones
  ui/common/            shared components, formatting, animations (rolling numbers, springy pull), haptics
  ui/onboarding/        servers, sign-in, offline
  ui/dashboard/         the main scaffold, the Devices tab, device tiles and the long-press sheet
  ui/detail/            full-screen device views and the thermostat dial
  ui/weather/           the Weather tab: pager, sky, station page, forecast, station list
  ui/settings/          Settings
```

## Working without a Minecraft server

`tools/mock_server.py` is a stand-in EZStats server (Python 3, no dependencies) with homes, every kind of device,
and nine weather stations across two worlds. Rooms drift toward their targets and the weather moves while it runs.

```
python tools/mock_server.py                # also: --celsius, --readonly, --no-weather, --latency 0.5
```

Sign in at `http://10.0.2.2:8765` from the emulator with any name and the password `test`. Or, with a debug build on
a running emulator, `tools/seed_emulator.sh` installs it already pointed at the mock server and signed in.

## Build

Requires JDK 17+ and the Android SDK (or open the folder in Android Studio).

```
./gradlew assembleRelease      # optimized (R8, baseline profile): app/build/outputs/apk/release/app-release.apk
./gradlew assembleDebug        # unoptimized, for debugging: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # API client + parsing tests against a mock EZStats server
```

The release build is signed with the debug key so it installs over (and keeps the logins of) earlier sideloaded builds.
Publishing to a store would need a real signing key.

Min Android 8.0 (API 26).

## License

[MIT](LICENSE)
