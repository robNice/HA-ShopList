"""Writes the phone app's settings DataStore (settings.preferences_pb) for the help screenshots:
demo connection, three stores and a store per list.

Usage: python help_prefs.py <out file> <language> <entity id> <list name> [<ha url>]
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import help_demo  # noqa: E402


def varint(n: int) -> bytes:
    out = bytearray()
    while True:
        byte, n = n & 0x7F, n >> 7
        out.append(byte | (0x80 if n else 0))
        if not n:
            return bytes(out)


def field(number: int, wire_type: int, payload: bytes) -> bytes:
    key = varint(number << 3 | wire_type)
    return key + (varint(len(payload)) + payload if wire_type == 2 else payload)


def value(v) -> bytes:
    # androidx.datastore.preferences Value: boolean = 1, string = 5
    if isinstance(v, bool):
        return field(1, 0, varint(int(v)))
    return field(5, 2, v.encode("utf-8"))


def preference_map(prefs: dict) -> bytes:
    return b"".join(field(1, 2, field(1, 2, k.encode()) + field(2, 2, value(v))) for k, v in prefs.items())


out, language, entity, list_name = sys.argv[1:5]
url = sys.argv[5] if len(sys.argv) > 5 else "http://10.0.2.2:8123"

_, store_names = help_demo.DEMO.get(language, help_demo.DEMO["en"])
store_ids = ["store_supermarket", "store_discount", "store_market"]
markets = [
    {"id": store_id, "name": name, "order": order, "enabled": enabled}
    for store_id, name, (order, enabled) in zip(store_ids, store_names, help_demo.store_orders())
]
todo_lists = help_demo.lists(language, entity, list_name)
links = {todo_lists[0][0]: store_ids[0], todo_lists[1][0]: store_ids[1], todo_lists[2][0]: store_ids[2]}

with open(out, "wb") as f:
    f.write(preference_map({
        "ha_url": url,
        "ha_token": "demo",
        "todo_entity": entity,
        "todo_list_name": list_name,
        "list_display_mode": "categorized",
        "notifications_enabled": False,
        "markets": json.dumps(markets, ensure_ascii=False),
        "active_market_id": store_ids[0],
        "list_market_links": json.dumps(links),
        "known_lists": json.dumps([{"id": i, "name": n} for i, n in todo_lists], ensure_ascii=False),
    }))
