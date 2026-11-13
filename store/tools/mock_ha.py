"""Minimal Home Assistant todo API with demo items, for store screenshots.

Usage: python mock_ha.py [en|de]   (listens on port 8123)
The emulator reaches it at http://10.0.2.2:8123.
"""
import json
import http.server
import sys

# Same separator as the app uses between item name and area key
SEP = "\x00 | "

NAMES = {
    "en": ["Bananas", "Tomatoes", "Chicken breast", "Milk", "Fresh bread", "Coffee beans", "Cat food", "Butter", "Apples"],
    "de": ["Bananen", "Tomaten", "Hähnchenbrust", "Milch", "Frisches Brot", "Kaffeebohnen", "Katzenfutter", "Butter", "Äpfel"],
}
AREAS = ["produce", "produce", "meat", "milk", "bakery", "coffee_tea", "pet", "milk", "produce"]
COMPLETED_FROM = 7  # the last two items are shown as done

names = NAMES.get(sys.argv[1] if len(sys.argv) > 1 else "en", NAMES["en"])
items = [
    [str(i + 1), name, area, "completed" if i >= COMPLETED_FROM else "needs_action"]
    for i, (name, area) in enumerate(zip(names, AREAS))
]


class Handler(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
        if "get_items" in self.path:
            out = {"service_response": {body.get("entity_id", "todo.shopping"): {"items": [
                {"uid": uid, "summary": name + SEP + area, "status": status}
                for uid, name, area, status in items
            ]}}}
        else:  # update_item / remove_item
            for item in items:
                if item[0] == body.get("item"):
                    item[3] = body.get("status", item[3])
            out = []
        data = json.dumps(out).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


http.server.HTTPServer(("0.0.0.0", 8123), Handler).serve_forever()
