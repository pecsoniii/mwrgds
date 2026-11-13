"""Verify the reviewed map engine and, optionally, the final Android APK."""
import argparse
import json
import hashlib
from pathlib import Path
import struct
import zipfile

PIN = json.loads(Path(__file__).with_name("map3d-engine.json").read_text(encoding="utf-8"))
BASE_SHA256 = PIN["baseApkSha256"]
NATIVE_SHA256 = PIN["nativeSha256"]
LIB = "lib/arm64-v8a/libopenmw.so"

def require(condition, message):
    if not condition:
        raise ValueError(message)

def verify(base, native, apk=None):
    require(hashlib.sha256(base.read_bytes()).hexdigest() == BASE_SHA256, "Base APK hash mismatch")
    engine = native.read_bytes()
    require(hashlib.sha256(engine).hexdigest() == NATIVE_SHA256, "Native library hash mismatch")
    require(engine[:4] == b"\x7fELF" and engine[4] == 2 and engine[5] == 1, "Expected a 64-bit little-endian ELF")
    require(struct.unpack_from("<H", engine, 18)[0] == 183, "Expected AArch64")
    for symbol in (b"onCompanionMap3D", b"CMP:map3d:"):
        require(symbol in engine, f"Missing native map bridge: {symbol!r}")
    if apk is None:
        print("PASS: reviewed base APK and arm64 3D-map engine")
        return
    with zipfile.ZipFile(base) as old, zipfile.ZipFile(apk) as new:
        require(new.read(LIB) == engine, "Final APK does not contain the reviewed engine")
        libs = [n for n in old.namelist() if n.startswith("lib/") and n.endswith(".so")]
        require(sorted(libs) == sorted(n for n in new.namelist() if n.startswith("lib/") and n.endswith(".so")), "Native library inventory changed")
        for name in libs:
            if name != LIB:
                require(old.read(name) == new.read(name), f"Unexpected native library change: {name}")
        for name in old.namelist():
            if name.startswith("assets/libopenmw/") and not name.endswith("/"):
                require(old.read(name) == new.read(name), f"Unexpected engine asset change: {name}")
        dex = b"\n".join(new.read(n) for n in new.namelist() if n.endswith(".dex"))
        for marker in (b"onCompanionMap3D", b"map_3d", b"CMP:map3d:"):
            require(marker in dex, f"Missing Android map integration: {marker!r}")
        lua = new.read("assets/companion/scripts/companion/companion.lua")
        require(b'"z":%.1f' in lua, "Door heights are absent from packaged Lua")
    print(f"PASS: final APK contains matching native/UI map bridge, door-height export, and {len(libs)-1} unchanged companion libraries")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", type=Path, required=True)
    parser.add_argument("--native", type=Path, required=True)
    parser.add_argument("--apk", type=Path)
    args = parser.parse_args()
    verify(args.base, args.native, args.apk)
