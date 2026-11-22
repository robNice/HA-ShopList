"""Prints the center "x y" of the node with the given resource id (Compose test tag) from a
uiautomator dump on stdin. Exits with 1 if it is not found.

Usage: adb exec-out uiautomator dump /dev/tty | python ui_find.py <test tag>
"""
import re
import sys

dump = sys.stdin.buffer.read().decode("utf-8", "replace")
tag = sys.argv[1]
for node in re.findall(r"<node [^>]*>", dump):
    if f'resource-id="{tag}"' in node:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", re.search(r'bounds="([^"]*)"', node).group(1)))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        sys.exit(0)
sys.exit(1)
