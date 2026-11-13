"""Writes the phone app's settings DataStore (settings.preferences_pb) for the demo server.

Usage: python phone_prefs.py <out file> <entity id> <list name> <simple|categorized>
"""
import sys

AREA_ORDER = "produce,bakery,meat,dairy_eggs,frozen,coffee_tea,drinks,cleaning,dry_goods"


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


out, entity, list_name, mode = sys.argv[1:5]
with open(out, "wb") as f:
    f.write(preference_map({
        "ha_url": "http://10.0.2.2:8123",
        "ha_token": "demo",
        "todo_entity": entity,
        "todo_list_name": list_name,
        "list_display_mode": mode,
        "area_order": AREA_ORDER,
        "notifications_enabled": False,
    }))
