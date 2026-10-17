"""Prints the centre "x y" of the nth UI node whose attribute equals value (uiautomator dump)."""
import re
import sys
import xml.etree.ElementTree as ET

path, attr, value, nth = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
try:
    root = ET.parse(path).getroot()
except Exception:
    sys.exit(0)
hits = []
for node in root.iter("node"):
    if node.get(attr) == value:
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if m:
            x0, y0, x1, y1 = map(int, m.groups())
            hits.append(((x0 + x1) // 2, (y0 + y1) // 2))
if len(hits) > nth:
    print(f"{hits[nth][0]} {hits[nth][1]}")
