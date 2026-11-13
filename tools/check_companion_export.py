"""Compile the full companion under LuaJIT and exercise the actual door export fragment."""
import argparse
import json
from pathlib import Path
import sys

parser = argparse.ArgumentParser()
parser.add_argument("--lupa-path", type=Path)
args = parser.parse_args()
if args.lupa_path:
    sys.path.insert(0, str(args.lupa_path))
from lupa import luajit21
lua = luajit21.LuaRuntime(unpack_returned_tuples=True)
source = (Path(__file__).resolve().parents[1] / "app/src/main/assets/companion/scripts/companion/companion.lua").read_text(encoding="utf-8")
lua.eval("function(s) local f,e=loadstring(s); if not f then error(e) end return true end")(source)
start = source.index("local lastDoorMarkersStr = nil")
end = source.index("-- Play the material/type-specific", start)
lua.execute("""rows={}; emit=function(s) rows[#rows+1]=s end; jsonEscape=function(s) return s end
nearby={doors={{position={x=10,y=20,z=350}}}}
types={Door={isTeleport=function() return true end,destCell=function() return {name="Upper Room"} end}}
core={regions={records={}}}
""" + source[start:end] + "\nexportDoorMarkers()")
rows = list(lua.globals().rows.values())
item = json.loads(next(s.split(":",1)[1] for s in rows if s.startswith("COMPANION_DOORMARKER_ITEM:")))
expected = {"x":10.0,"y":20.0,"z":350.0,"name":"Upper Room"}
if item != expected:
    raise AssertionError((item, expected))
print("PASS: full LuaJIT compilation and actual door elevation export")
