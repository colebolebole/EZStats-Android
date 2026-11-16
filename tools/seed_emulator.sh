#!/usr/bin/env bash
# Installs a debug build on the running emulator and points it at tools/mock_server.py, already signed in.
#   tools/seed_emulator.sh [path/to/apk]
set -e
ADB="${ADB:-adb}"
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
PKG=com.ezstats.android
"$ADB" install -r "$APK" >/dev/null
"$ADB" shell am force-stop $PKG
"$ADB" shell "run-as $PKG sh -c 'mkdir -p shared_prefs && cat > shared_prefs/servers.xml'" <<XML
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="servers">[{&quot;id&quot;:&quot;mock&quot;,&quot;name&quot;:&quot;Mock server&quot;,&quot;url&quot;:&quot;http://10.0.2.2:8765&quot;}]</string>
    <string name="active">mock</string>
    <string name="cookie_mock">dev</string>
</map>
XML
"$ADB" shell am start -n $PKG/.MainActivity >/dev/null
