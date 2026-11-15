# EZStats for Android

A native Material 3 app for the [EZStats](https://github.com/colebolebole/EZStats) dashboard. It talks to the
server's own JSON API (the same one its web page uses), so everything you can do on the website works here,
and you choose which server (URL) to connect to.

## What it does

- **Servers** – add, edit, delete and switch between EZStats servers. Each remembers its own login.
  URLs without a scheme (`192.168.1.10:8765`) default to `http://`.
- **Sign in** – with your Minecraft name and the password from `/ezstats register`, or paste a one-time link from `/ezstats link`.
- **Devices** – live (every 2 s) cards for every device you own:
  - thermostats with a draggable dial, −/+, power, eco/normal/turbo profile, attention chips and the nearest weather station with a 7-day forecast
  - heat pumps / ACs / furnaces / boilers (switch on and off)
  - humidifiers and dehumidifiers (target, tank), air purifiers (fan mode, filter, AQI), fridges / freezers (targets, defrost, auto-defrost, door, frost)
  - thermometers, sensors, monitors and vents
- **Rename devices, make homes, move devices between homes** – the same as the website.
- **Weather** tab (if the server has it on) – every station, search and filters, 7-day strips and day-by-day details.
- Follows the server's °F/°C setting, light/dark theme, and wallpaper colours on Android 12+.

Note: over `http://` your password and session cookie are unencrypted. Use an `https://` URL (reverse proxy) for anything on the internet.

## Build

Requires JDK 17+ and the Android SDK (or open the folder in Android Studio).

```
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # API client + parsing tests against a mock EZStats server
```

Min Android 8.0 (API 26).
