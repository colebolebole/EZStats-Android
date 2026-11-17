<p align="center">
  <img src="docs/logo.svg" width="128" height="128" alt="EZStats logo: three rising bars on teal">
</p>

<h1 align="center">EZStats for Android</h1>

<p align="center">
  A native Material 3 Expressive app for your <a href="https://github.com/colebolebole/EZStats">EZStats</a> Minecraft dashboard.<br>
  Watch and control your <a href="https://github.com/EZvac-Plugin/EZvac">EZvac</a> thermostats, fridges and more, and check the weather, from your phone.
</p>

<p align="center">
  <a href="https://github.com/colebolebole/EZStats-Android/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/colebolebole/EZStats-Android?color=0F766E"></a>
  <a href="LICENSE"><img alt="MIT license" src="https://img.shields.io/badge/license-MIT-0F766E"></a>
  <img alt="Android 8.0 and up" src="https://img.shields.io/badge/Android-8.0%2B-0F766E">
</p>

The app talks to your server's own JSON API, the same one its web page uses, so everything you can do on the website
works here. You choose which server (URL) to connect to, and can keep several.

## Download

Get the APK from the [latest release](https://github.com/colebolebole/EZStats-Android/releases/latest) and open it on
your phone (Android will ask you to allow installing from your browser or file manager). Every release is signed with
the same key, so a new version installs over the old one and keeps your servers, logins and favorite stations.

## What it does

**Devices**
- A live dashboard (refreshed every 2 s by default), grouped by home, with a summary of what is heating, cooling or
  needs a look.
- **Thermostats** open full screen with a draggable dial (with haptic detents), −/+, power, an eco / normal / turbo
  profile, attention chips and the nearest weather station's week.
  - Says how long it has been heating or cooling (`Cooling for 12 min`).
  - A **log** of its last 5 runs, newest first (`Heated for 3 min · 11:44 PM`: how long it ran and when it stopped).
    The card on the dashboard shows the newest one.
- **Fridges, freezers and refrigerators** (targets, defrost, auto-defrost, door, frost), **humidifiers and
  dehumidifiers** (target, tank) and **air purifiers** (fan mode, filter, AQI) open full screen too.
- **Heat pumps, ACs, furnaces and boilers** switch on and off right on their tile. Thermometers, sensors, monitors and
  vents show their readings.
- **Rename devices, make homes, move devices between homes:** long-press a tile (or use ⋮ on a full-screen device).
  Homes fold away to keep things tidy.

**Weather** (if the server has the Weather tab on)
- Built like a weather app: swipe between stations under a live animated sky (sun, cloud, rain, snow, thunderstorms),
  each with a huge temperature, a summary of today, the days ahead as range bars, and wind, air quality, rain and lightning tiles.
- Star the stations you care about and the page shows just those. The list button still shows every station (search,
  filters, favorites first), and tapping one you haven't starred opens it as an extra page.

**Everything else**
- **Servers:** add, edit, delete and switch between EZStats servers, each with its own login. A URL without a scheme
  (`192.168.1.10:8765`) defaults to `http://`.
- **Sign in** with your Minecraft name and password, with a one-time **QuickLogin** code, or reset a forgotten
  password with **Forgot password?** (see below).
- **Settings:** theme (system, light, dark), wallpaper colors, °F/°C (or follow the server), refresh rate, animated
  weather, and haptics on/off with a strength.
- Opens instantly on the last thing it saw, and stays quick: springy Material 3 Expressive motion throughout,
  shape-morphing buttons and elements that stretch a little under your finger.

## Signing in

| | In game | In the app |
| --- | --- | --- |
| **Password** | `/ezstats register <password>` | Minecraft name and password |
| **QuickLogin** | `/ezstats quicklogin` sends an 8 digit code in chat | **QuickLogin** tab: name and code |
| **Forgot password?** | `/ezstats resetpassword` sends an 8 digit code in chat | **Forgot password?**: name, code and a new password |

A code works once and expires after 5 minutes. A new password typed in the app goes straight to the server and is never
typed into Minecraft, so it can't end up in the console log.

> Over plain `http://` your password, codes and session cookie are not encrypted. Use an `https://` address (behind a
> reverse proxy) for anything on the internet.

## Which server version do I need?

The app reads and changes what the EZStats server exposes. Newer features need a newer server, and the app simply
leaves out whatever an older one doesn't send.

| Feature | Needs |
| --- | --- |
| Password sign-in, devices, weather | EZStats with the JSON API |
| QuickLogin | EZStats 0.7.0+ |
| Forgot password? | EZStats 0.7.1+ |
| How long a thermostat has been heating or cooling | EZStats 0.7.2+ on EZvac BETA 1.0 Pilot 8+ |
| Thermostat run log | EZStats 0.7.3+ |
| Controlling devices (otherwise the app is read-only) | EZvac BETA 1.0 Pilot 7+ |

## Building

Requires JDK 17+ and the Android SDK, or open the folder in Android Studio.

```
./gradlew assembleRelease      # optimized (R8, baseline profile): app/build/outputs/apk/release/app-release.apk
./gradlew assembleDebug        # unoptimized, for debugging:        app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # API client, parsing and UI logic tests against a mock EZStats server
```

The release build is signed with the debug key, so it installs over earlier sideloaded builds and keeps their logins.
Publishing to a store would need a real signing key.

### Working without a Minecraft server

`tools/mock_server.py` is a stand-in EZStats server (Python 3, no dependencies) with homes, every kind of device and
nine weather stations across two worlds. Rooms drift toward their targets and the weather moves while it runs.

```
python tools/mock_server.py                # also: --celsius, --readonly, --no-weather, --latency 0.5
```

Sign in at `http://10.0.2.2:8765` from the emulator with any name and the password `test`. With a debug build on a
running emulator, `tools/seed_emulator.sh` installs it already pointed at the mock server and signed in. The mock only
does password sign-in: it has no QuickLogin, password reset or run log.

## Code layout

```
app/src/main/java/com/ezstats/android/
  EzViewModel.kt        app state, polling, device changes
  data/                 the server's JSON API (EzApi), models and parsing, saved servers and settings
  ui/EzApp.kt           which screen is showing
  ui/theme/             colors, type, shapes, motion (springs), status tones
  ui/common/            shared components, formatting, animations (rolling numbers, springy pull), haptics
  ui/onboarding/        servers, sign-in, offline
  ui/dashboard/         the main scaffold, the Devices tab, device tiles and the long-press sheet
  ui/detail/            full-screen device views and the thermostat dial
  ui/weather/           the Weather tab: pager, sky, station page, forecast, station list
  ui/settings/          Settings
```

## License

[MIT](LICENSE)
