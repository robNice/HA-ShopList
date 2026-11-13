"""Minimal Home Assistant todo API with demo items, for store screenshots.

Usage: python mock_ha.py <language> [phone [entity id]]   (listens on port 8123)
       python mock_ha.py <language> letter                  (prints a letter that gives several suggestions)
The emulator reaches it at http://10.0.2.2:8123.

Without "phone" it serves the short watch demo list (en, de) over the REST API.
With "phone" it serves the longer phone demo list over the REST and the WebSocket API.
"""
import base64
import hashlib
import http.server
import json
import sys
import threading

# Same separator as the app uses between item name and area key
SEP = "\x00 | "
WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

WATCH_NAMES = {
    "en": ["Bananas", "Tomatoes", "Chicken breast", "Milk", "Fresh bread", "Coffee beans", "Cat food", "Butter", "Apples"],
    "de": ["Bananen", "Tomaten", "Hähnchenbrust", "Milch", "Frisches Brot", "Kaffeebohnen", "Katzenfutter", "Butter", "Äpfel"],
}
WATCH_AREAS = ["produce", "produce", "meat", "milk", "bakery", "coffee_tea", "pet", "milk", "produce"]
WATCH_COMPLETED_FROM = 7  # the last two items are shown as done

PHONE_AREAS = ["produce", "produce", "bakery", "bakery", "meat", "dairy_eggs", "dairy_eggs", "frozen", "coffee_tea",
               "drinks", "cleaning", "dairy_eggs", "produce", "dry_goods"]
PHONE_COMPLETED_FROM = 11  # the last three items are shown as done
PHONE_LISTS = {
    "en": ("Shopping list", ["Tomatoes", "Avocados", "Sourdough bread", "Croissants", "Chicken breast", "Milk", "Eggs",
                             "Ice cream", "Coffee beans", "Orange juice", "Dish soap", "Butter", "Bananas", "Pasta"]),
    "de": ("Einkaufsliste", ["Tomaten", "Avocados", "Sauerteigbrot", "Croissants", "Hähnchenbrust", "Milch", "Eier",
                             "Eiscreme", "Kaffeebohnen", "Orangensaft", "Spülmittel", "Butter", "Bananen", "Nudeln"]),
    "es": ("Lista de la compra", ["Tomates", "Aguacates", "Pan de masa madre", "Cruasanes", "Pechuga de pollo", "Leche",
                                  "Huevos", "Helado", "Café en grano", "Zumo de naranja", "Lavavajillas", "Mantequilla",
                                  "Plátanos", "Pasta"]),
    "fr": ("Liste de courses", ["Tomates", "Avocats", "Pain au levain", "Croissants", "Blanc de poulet", "Lait", "Œufs",
                                "Glace", "Café en grains", "Jus d'orange", "Liquide vaisselle", "Beurre", "Bananes",
                                "Pâtes"]),
    "it": ("Lista della spesa", ["Pomodori", "Avocado", "Pane a lievitazione naturale", "Cornetti", "Petto di pollo",
                                 "Latte", "Uova", "Gelato", "Caffè in grani", "Succo d'arancia", "Detersivo per piatti",
                                 "Burro", "Banane", "Pasta"]),
    "ja": ("買い物リスト", ["トマト", "アボカド", "サワードウブレッド", "クロワッサン", "鶏むね肉", "牛乳", "卵", "アイスクリーム",
                         "コーヒー豆", "オレンジジュース", "食器用洗剤", "バター", "バナナ", "パスタ"]),
    "ko": ("장보기 목록", ["토마토", "아보카도", "사워도우 빵", "크루아상", "닭가슴살", "우유", "달걀", "아이스크림", "원두",
                       "오렌지 주스", "주방 세제", "버터", "바나나", "파스타"]),
    "nl": ("Boodschappenlijst", ["Tomaten", "Avocado's", "Zuurdesembrood", "Croissants", "Kipfilet", "Melk", "Eieren",
                                 "IJs", "Koffiebonen", "Sinaasappelsap", "Afwasmiddel", "Boter", "Bananen", "Pasta"]),
    "pl": ("Lista zakupów", ["Pomidory", "Awokado", "Chleb na zakwasie", "Croissanty", "Pierś z kurczaka", "Mleko", "Jajka",
                             "Lody", "Kawa ziarnista", "Sok pomarańczowy", "Płyn do naczyń", "Masło", "Banany",
                             "Makaron"]),
    "pt": ("Lista de compras", ["Tomates", "Abacates", "Pão de fermentação natural", "Croissants", "Peito de frango",
                                "Leite", "Ovos", "Sorvete", "Café em grão", "Suco de laranja", "Detergente", "Manteiga",
                                "Bananas", "Macarrão"]),
    "ru": ("Список покупок", ["Помидоры", "Авокадо", "Хлеб на закваске", "Круассаны", "Куриное филе", "Молоко", "Яйца",
                              "Мороженое", "Кофе в зёрнах", "Апельсиновый сок", "Средство для посуды", "Сливочное масло",
                              "Бананы", "Макароны"]),
    "tr": ("Alışveriş listesi", ["Domates", "Avokado", "Ekşi mayalı ekmek", "Kruvasan", "Tavuk göğsü", "Süt", "Yumurta",
                                 "Dondurma", "Çekirdek kahve", "Portakal suyu", "Bulaşık deterjanı", "Tereyağı", "Muz",
                                 "Makarna"]),
    "uk": ("Список покупок", ["Помідори", "Авокадо", "Хліб на заквасці", "Круасани", "Куряче філе", "Молоко", "Яйця",
                              "Морозиво", "Кава в зернах", "Апельсиновий сік", "Засіб для посуду", "Вершкове масло",
                              "Банани", "Макарони"]),
}

language = sys.argv[1] if len(sys.argv) > 1 else "en"
phone = len(sys.argv) > 2 and sys.argv[2] in ("phone", "letter")
entity = sys.argv[3] if len(sys.argv) > 3 else "todo.shopping_list"
if len(sys.argv) > 2 and sys.argv[2] == "letter":
    # The first letter shared by most item names, so typing it shows several suggestions
    words = [n.lower() for n in PHONE_LISTS.get(language, PHONE_LISTS["en"])[1]]
    letters = {w[0] for w in words}
    best = max(sorted(letters), key=lambda c: (sum(w.startswith(c) for w in words), sum(c in w for w in words)))
    sys.stdout.buffer.write(best.upper().encode("utf-8"))
    sys.exit()
if phone:
    list_name, names = PHONE_LISTS.get(language, PHONE_LISTS["en"])
    areas, completed_from = PHONE_AREAS, PHONE_COMPLETED_FROM
else:
    list_name, names = "Shopping list", WATCH_NAMES.get(language, WATCH_NAMES["en"])
    areas, completed_from = WATCH_AREAS, WATCH_COMPLETED_FROM

items = [
    {"uid": str(i + 1), "summary": name + SEP + area, "status": "completed" if i >= completed_from else "needs_action"}
    for i, (name, area) in enumerate(zip(names, areas))
]
lock = threading.Lock()
subscribers = []  # (send function, subscription id)


def apply_service(service: str, data: dict):
    with lock:
        if service == "add_item":
            uid = str(max(int(i["uid"]) for i in items) + 1)
            items.append({"uid": uid, "summary": data.get("item", ""), "status": "needs_action"})
        elif service == "update_item":
            for item in items:
                if item["uid"] == data.get("item") or item["summary"] == data.get("item"):
                    item["summary"] = data.get("rename", item["summary"])
                    item["status"] = data.get("status", item["status"])
        elif service == "remove_item":
            gone = data.get("item")
            gone = gone if isinstance(gone, list) else [gone]
            items[:] = [i for i in items if i["uid"] not in gone]


def move_item(uid: str, previous_uid):
    with lock:
        moving = next((i for i in items if i["uid"] == uid), None)
        if moving is None:
            return
        items.remove(moving)
        index = next((n + 1 for n, i in enumerate(items) if i["uid"] == previous_uid), 0)
        items.insert(index, moving)


def notify():
    with lock:
        snapshot = [dict(i) for i in items]
    for send, sub_id in list(subscribers):
        try:
            send({"id": sub_id, "type": "event", "event": {"items": snapshot}})
        except OSError:
            subscribers.remove((send, sub_id))


class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
        if "get_items" in self.path:
            with lock:
                out = {"service_response": {body.get("entity_id", "todo.shopping"): {"items": [dict(i) for i in items]}}}
        else:  # update_item / remove_item from the watch
            service = self.path.rstrip("/").rsplit("/", 1)[-1]
            apply_service(service, body)
            notify()
            out = []
        data = json.dumps(out).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.headers.get("Upgrade", "").lower() != "websocket":
            self.send_error(404)
            return
        accept = base64.b64encode(hashlib.sha1((self.headers["Sec-WebSocket-Key"] + WS_GUID).encode()).digest())
        self.send_response(101)
        self.send_header("Upgrade", "websocket")
        self.send_header("Connection", "Upgrade")
        self.send_header("Sec-WebSocket-Accept", accept.decode())
        self.end_headers()
        self.wfile.flush()
        self.websocket()

    # ---------------------------------------------------------------- WebSocket

    def ws_send(self, message: dict):
        payload = json.dumps(message).encode()
        header = bytearray([0x81])
        if len(payload) < 126:
            header.append(len(payload))
        elif len(payload) < 65536:
            header += bytes([126]) + len(payload).to_bytes(2, "big")
        else:
            header += bytes([127]) + len(payload).to_bytes(8, "big")
        with self.send_lock:
            self.wfile.write(bytes(header) + payload)
            self.wfile.flush()

    def ws_receive(self):
        while True:
            head = self.rfile.read(2)
            if len(head) < 2:
                return None
            opcode, length = head[0] & 0x0F, head[1] & 0x7F
            if length == 126:
                length = int.from_bytes(self.rfile.read(2), "big")
            elif length == 127:
                length = int.from_bytes(self.rfile.read(8), "big")
            mask = self.rfile.read(4) if head[1] & 0x80 else b"\0\0\0\0"
            data = bytes(b ^ mask[n % 4] for n, b in enumerate(self.rfile.read(length)))
            if opcode == 0x8:
                return None
            if opcode == 0x9:  # ping
                with self.send_lock:
                    self.wfile.write(bytes([0x8A, len(data)]) + data)
                    self.wfile.flush()
                continue
            if opcode == 0x1:
                return json.loads(data)

    def websocket(self):
        self.send_lock = threading.Lock()
        self.ws_send({"type": "auth_required", "ha_version": "2025.11.0"})
        while (message := self.ws_receive()) is not None:
            kind = message.get("type")
            if kind == "auth":
                self.ws_send({"type": "auth_ok", "ha_version": "2025.11.0"})
                continue
            msg_id = message.get("id")
            result = None
            if kind == "get_states":
                result = [{"entity_id": entity, "state": str(sum(i["status"] == "needs_action" for i in items)),
                           "attributes": {"friendly_name": list_name}}]
            elif kind == "todo/item/list":
                with lock:
                    result = {"items": [dict(i) for i in items]}
            elif kind == "todo/item/subscribe":
                subscribers.append((self.ws_send, msg_id))
            elif kind == "todo/item/move":
                move_item(message.get("uid"), message.get("previous_uid"))
            elif kind == "call_service":
                apply_service(message.get("service"), message.get("service_data") or {})
            self.ws_send({"id": msg_id, "type": "result", "success": True, "result": result})
            if kind == "todo/item/subscribe":
                notify()
            elif kind in ("todo/item/move", "call_service"):
                notify()
        subscribers[:] = [s for s in subscribers if s[0] != self.ws_send]


http.server.ThreadingHTTPServer(("0.0.0.0", 8123), Handler).serve_forever()
