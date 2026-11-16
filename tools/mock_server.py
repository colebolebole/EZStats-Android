#!/usr/bin/env python3
"""
A stand-in EZStats server for working on the app without a Minecraft server.

    python tools/mock_server.py [--port 8765] [--celsius] [--readonly] [--no-weather]

Sign in with any name and the password "test" (or paste a link containing token=TEST).
From the Android emulator the server is at http://10.0.2.2:8765.

It speaks the same JSON API as the real plugin (the shapes the app's parser reads), keeps state while it runs,
and moves things along: rooms drift toward their targets, outdoor temperatures wander, tanks fill.
"""
import argparse
import json
import math
import random
import secrets
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

COOKIE = "ezstats_session"
lock = threading.Lock()
sessions = {"dev"}  # a fixed session, so tools can seed the app already signed in
started = time.time()

# --- world -----------------------------------------------------------------------------------------------------

homes = [
    {"id": "h1", "name": "Spawn Cottage"},
    {"id": "h2", "name": "Mountain Lodge"},
    {"id": "h3", "name": "Beach House"},
]


def dev(key, kind, type_, label, home, x, y, z, **kw):
    d = {"key": key, "world": "world", "x": x, "y": y, "z": z, "kind": kind, "type": type_, "label": label,
         "home": home}
    d.update(kw)
    return d


devices = [
    dev("t1", "thermostat", "room", "Living room", "h1", 120, 64, -40, nickname="Living room", roomF=68.4, targetF=71,
        mode="heating", enabled=True, humidity=41, runningUnits=1, totalUnits=2, profile="normal", attention=[],
        weather={"station": "s1", "distance": 84}),
    dev("t2", "thermostat", "room", "Bedroom", "h1", 128, 70, -36, roomF=72.9, targetF=69, mode="cooling",
        enabled=True, humidity=47, runningUnits=1, totalUnits=1, profile="eco", attention=[],
        weather={"station": "s1", "distance": 91}),
    dev("u1", "equipment", "heat_pump", "", "h1", 118, 63, -44, mode="heating", enabled=True, running=True, rpm=1420,
        tier=2),
    dev("u2", "equipment", "furnace", "", "h1", 119, 58, -41, mode="heating", enabled=False, running=False, tier=1),
    dev("f1", "unit", "refrigerator", "Kitchen fridge", "h1", 125, 64, -42, powered=True, state="running", insideF=1.5,
        setpoint=0, humidity=30,
        cold={"doorOpen": False, "frost": 0.34, "defrosting": False, "autoDefrost": True, "fridgeInsideF": 37.2,
              "fridgeSetpointF": 37}),
    dev("p1", "unit", "air_purifier", "", "h1", 123, 64, -38, powered=True, state="running", fanMode="auto",
        fanSpeed="medium", filter=0.62, air={"aqi": 38, "band": "good"}),
    dev("i1", "instrument", "thermo_hygrometer", "Hall sensor", "h1", 121, 65, -39,
        reading={"tempF": 69.1, "humidity": 42, "weather": "Clear"}),
    dev("t3", "thermostat", "room", "Great hall", "h2", -820, 112, 340, roomF=58.2, targetF=66, mode="heating",
        enabled=True, humidity=35, runningUnits=0, totalUnits=1, profile="turbo", attention=["cant_keep_up"],
        weather={"station": "s3", "distance": 40}),
    dev("d1", "unit", "dehumidifier", "", "h2", -818, 111, 338, powered=True, state="running", humidity=58,
        setpoint=45, tank=0.71),
    dev("z1", "unit", "deep_freezer", "Cellar freezer", "h2", -825, 104, 342, powered=True, state="idle", insideF=-4.2,
        setpoint=-5, cold={"doorOpen": True, "frost": 0.78, "defrosting": False, "autoDefrost": False}),
    dev("t4", "thermostat", "pool", "Pool", "h3", 610, 63, 900, roomF=82.0, targetF=84, mode="heating", enabled=True,
        pool=True, runningUnits=1, totalUnits=1, profile="normal", attention=[],
        weather={"station": "s4", "distance": 22}),
    dev("t5", "thermostat", "room", "Sunroom", "h3", 604, 66, 896, roomF=75.0, targetF=74, mode="idle", enabled=False,
        humidity=55, runningUnits=0, totalUnits=1, profile="normal", attention=["units_off"],
        weather={"station": "s4", "distance": 30}),
    dev("m1", "unit", "mini_fridge", "", "h3", 606, 64, 902, powered=True, state="running", insideF=38.4, setpoint=38,
        cold={"doorOpen": False, "frost": 0.05, "defrosting": False, "autoDefrost": False, "chillerF": 31.0}),
    dev("v1", "vent", "air", "", "", 130, 64, -30),
    dev("w1", "instrument", "wind_sensor", "Roof vane", "", 126, 80, -40,
        reading={"windMs": 6.4, "windLevel": "breezy", "windFrom": "NW"}),
]

SKIES = ["clear", "clear", "rain", "clear", "thunder", "snow", "rain"]


def forecast(seed, base):
    rnd = random.Random(seed)
    days = []
    for n in range(7):
        sky = rnd.choice(SKIES) if n else ["clear", "rain", "snow", "thunder", "clear"][seed % 5]
        hi = base + rnd.uniform(-6, 9) - (10 if sky == "snow" else 0)
        lo = hi - rnd.uniform(8, 18)
        rain = 0.0 if sky == "clear" else round(rnd.uniform(0.3, 0.95), 2)
        days.append({
            "daysAhead": n, "highF": round(hi, 1), "lowF": round(lo, 1), "sky": sky, "rain": rain,
            "thunder": round(rnd.uniform(0.2, 0.8), 2) if sky == "thunder" else 0.0,
            "note": rnd.choice(["", "", "Gusty afternoon", "Fog early", "Clearing later", "Frost overnight"]),
            "aqi": rnd.randint(15, 120),
            "wind": {"speedMs": round(rnd.uniform(0.5, 14), 1), "level": rnd.choice(["calm", "breezy", "windy", "gale"]),
                     "from": rnd.choice(["N", "NE", "E", "SE", "S", "SW", "W", "NW"])},
        })
    return days


def station(key, label, world, x, y, z, base, seed, enabled=True, intact=True, working=True, lightning=0):
    return {"key": key, "label": label, "world": world, "x": x, "y": y, "z": z, "enabled": enabled, "intact": intact,
            "working": working, "lightningStrikes": lightning, "base": base, "seed": seed}


stations = [
    station("s1", "Spawn Weather Tower", "world", 100, 90, -20, 66, 0),
    station("s2", "Northern Outpost", "world", 40, 120, -1600, 34, 2),
    station("s3", "Mountain Lodge Station", "world", -800, 140, 330, 48, 3, lightning=3),
    station("s4", "Beach Lighthouse", "world", 620, 70, 910, 82, 4),
    station("s5", "Desert Spire", "world", 2200, 80, 400, 95, 1),
    station("s6", "Mesa Ridge", "survival2", 300, 100, 300, 78, 5),
    station("s7", "Swamp Watch", "survival2", -120, 64, 700, 70, 6, lightning=1),
    station("s8", "Old Mill Station", "world", 900, 72, -300, 60, 7, intact=False, working=False),
    station("s9", "Admin Test Tower", "survival2", 0, 64, 0, 60, 8, enabled=False, working=False),
]


def station_json(s):
    t = time.time() - started
    out = {k: v for k, v in s.items() if k not in ("base", "seed")}
    if s["working"]:
        out["outdoorF"] = round(s["base"] + 4 * math.sin(t / 90 + s["seed"]), 1)
        rnd = random.Random(s["seed"])
        out["wind"] = {"speedMs": round(3 + 3 * abs(math.sin(t / 40 + s["seed"])), 1),
                       "level": rnd.choice(["calm", "breezy", "windy"]), "from": rnd.choice(["N", "SW", "E", "NW"])}
        out["air"] = {"aqi": 20 + s["seed"] * 11, "band": "good" if s["seed"] < 3 else "moderate"}
        out["forecast"] = forecast(s["seed"], s["base"])
    else:
        out["forecast"] = []
    return out


def tick():
    """Moves the world along a little every second."""
    while True:
        time.sleep(1)
        with lock:
            for d in devices:
                if d["kind"] == "thermostat" and d.get("roomF") is not None:
                    tgt = d.get("targetF") or d["roomF"]
                    if d.get("enabled"):
                        diff = tgt - d["roomF"]
                        d["mode"] = "heating" if diff > 0.4 else "cooling" if diff < -0.4 else "idle"
                        d["roomF"] = round(d["roomF"] + max(-0.08, min(0.08, diff * 0.02)), 2)
                        d["runningUnits"] = 0 if d["mode"] == "idle" else max(1, d.get("runningUnits", 1))
                    else:
                        d["mode"] = "idle"
                        d["roomF"] = round(d["roomF"] + random.uniform(-0.03, 0.03), 2)
                elif d["type"] == "dehumidifier" and d.get("powered"):
                    d["tank"] = min(1.0, round(d.get("tank", 0) + 0.002, 3))
                    d["state"] = "emptying" if d["tank"] >= 1 else "running"


# --- http ---------------------------------------------------------------------------------------------------


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        if self.command != "GET":
            super().log_message(fmt, *args)

    def send(self, code, body, cookie=None, location=None):
        data = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        if cookie is not None:
            self.send_header("Set-Cookie", f"{COOKIE}={cookie}; Path=/; HttpOnly; SameSite=Strict")
        if location:
            self.send_header("Location", location)
        self.end_headers()
        self.wfile.write(data)

    def session(self):
        for part in (self.headers.get("Cookie") or "").split(";"):
            k, _, v = part.strip().partition("=")
            if k == COOKIE and v in sessions:
                return v
        return None

    def body(self):
        n = int(self.headers.get("Content-Length") or 0)
        return json.loads(self.rfile.read(n) or b"{}") if n else {}

    def do_GET(self):
        url = urlparse(self.path)
        time.sleep(ARGS.latency)
        if url.path == "/link":
            if "TEST" in parse_qs(url.query).get("token", [""])[0]:
                sid = secrets.token_hex(8)
                sessions.add(sid)
                return self.send(302, {}, cookie=sid, location="/")
            return self.send(410, {"error": "Link expired"})
        if not self.session():
            return self.send(401, {"error": "Not signed in."})
        with lock:
            if url.path == "/api/state":
                return self.send(200, {
                    "player": "Steve",
                    "weatherTab": not ARGS.no_weather,
                    "state": {
                        "units": "C" if ARGS.celsius else "F",
                        "writable": not ARGS.readonly,
                        "readOnlyReason": "The server owner has turned off remote control." if ARGS.readonly else None,
                        "homes": homes,
                        "devices": devices,
                        "worlds": [
                            {"name": "world", "season": {"name": "autumn", "day": 12, "days": 28, "paused": False},
                             "outdoor": {"airF": stations[0]["base"], "weather": "Clear"}},
                            {"name": "survival2", "season": {"name": "summer", "day": 3, "days": 28, "paused": True},
                             "outdoor": {"airF": 78, "weather": "Rain"}},
                        ],
                        "stations": {d["weather"]["station"]: station_json(next(s for s in stations if s["key"] == d["weather"]["station"]))
                                     for d in devices if d.get("weather")},
                    },
                })
            if url.path == "/api/weather":
                if ARGS.no_weather:
                    return self.send(404, {"error": "Weather is off"})
                return self.send(200, {"units": "C" if ARGS.celsius else "F", "generatedAt": int(time.time() * 1000),
                                       "stations": [station_json(s) for s in stations]})
        self.send(404, {"error": "Not found"})

    def do_POST(self):
        url = urlparse(self.path)
        time.sleep(ARGS.latency)
        if not self.headers.get("X-EZStats"):
            return self.send(403, {"error": "Missing X-EZStats header"})
        b = self.body()
        if url.path == "/api/login":
            if b.get("password") == "test" and b.get("username"):
                sid = secrets.token_hex(8)
                sessions.add(sid)
                return self.send(200, {"ok": True}, cookie=sid)
            return self.send(401, {"error": "Wrong username or password."})
        if url.path == "/api/logout":
            sessions.discard(self.session())
            return self.send(200, {"ok": True}, cookie="")
        if not self.session():
            return self.send(401, {"error": "Not signed in."})
        with lock:
            if url.path == "/api/device/control":
                if ARGS.readonly:
                    return self.send(403, {"error": "Remote control is off."})
                d = next((d for d in devices if d["key"] == b.get("key")), None)
                if d is None:
                    return self.send(404, {"error": "No such device"})
                a, v = b.get("action"), b.get("value")
                field = {"target": "targetF", "enabled": "enabled", "profile": "profile", "powered": "powered",
                         "setpoint": "setpoint", "fan": "fanMode", "equipment-enabled": "enabled"}.get(a)
                if field:
                    d[field] = v
                elif a == "fridge-setpoint":
                    d["cold"]["fridgeSetpointF"] = v
                elif a == "defrost":
                    d["cold"]["defrosting"] = True
                    threading.Timer(8, lambda: d["cold"].update(defrosting=False, frost=0.0)).start()
                elif a == "auto-defrost":
                    d["cold"]["autoDefrost"] = bool(v)
                return self.send(200, {"adjusted": False, "message": ""})
            if url.path == "/api/device/nickname":
                d = next(d for d in devices if d["key"] == b["key"])
                d["nickname"] = b.get("nickname") or None
                return self.send(200, {"ok": True})
            if url.path == "/api/device/home":
                d = next(d for d in devices if d["key"] == b["key"])
                d["home"] = b.get("home", "")
                return self.send(200, {"ok": True})
            if url.path == "/api/homes":
                homes.append({"id": "h" + secrets.token_hex(3), "name": b["name"]})
                return self.send(200, {"ok": True})
            if url.path == "/api/homes/rename":
                next(h for h in homes if h["id"] == b["id"])["name"] = b["name"]
                return self.send(200, {"ok": True})
            if url.path == "/api/homes/delete":
                homes[:] = [h for h in homes if h["id"] != b["id"]]
                for d in devices:
                    if d["home"] == b["id"]:
                        d["home"] = ""
                return self.send(200, {"ok": True})
        self.send(404, {"error": "Not found"})


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--port", type=int, default=8765)
    p.add_argument("--celsius", action="store_true")
    p.add_argument("--readonly", action="store_true")
    p.add_argument("--no-weather", action="store_true")
    p.add_argument("--latency", type=float, default=0.05, help="seconds added to every answer")
    ARGS = p.parse_args()
    threading.Thread(target=tick, daemon=True).start()
    print(f"Mock EZStats on http://127.0.0.1:{ARGS.port} (emulator: http://10.0.2.2:{ARGS.port}); password: test", flush=True)
    ThreadingHTTPServer(("127.0.0.1", ARGS.port), Handler).serve_forever()
