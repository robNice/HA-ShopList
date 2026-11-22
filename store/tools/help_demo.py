"""Demo data for the help screenshots (settings tab "?"), shared by mock_ha.py and help_prefs.py.

Per language: the two extra lists and the three stores. The names match the examples in
app/src/main/assets/help/<language>/help.md.
"""

# language: (extra lists, stores)
#   extra lists: (drugstore list, farmers market list)
#   stores:      (supermarket, discount store, farmers market)
DEMO = {
    "en": (("Drugstore", "Farmers market"), ("Supermarket", "Discount store", "Farmers market")),
    "de": (("Drogerie", "Wochenmarkt"), ("Supermarkt", "Discounter", "Wochenmarkt")),
    "es": (("Droguería", "Mercadillo"), ("Supermercado", "Tienda de descuento", "Mercadillo")),
    "fr": (("Droguerie", "Marché"), ("Supermarché", "Hard-discount", "Marché")),
    "it": (("Drogheria", "Mercato"), ("Supermercato", "Discount", "Mercato")),
    "ja": (("ドラッグストア", "朝市"), ("スーパー", "ディスカウント店", "朝市")),
    "ko": (("드러그스토어", "시장"), ("슈퍼마켓", "할인점", "시장")),
    "nl": (("Drogist", "Markt"), ("Supermarkt", "Discounter", "Markt")),
    "pl": (("Drogeria", "Targ"), ("Supermarket", "Dyskont", "Targ")),
    "pt": (("Farmácia", "Feira"), ("Supermercado", "Atacarejo", "Feira")),
    "ru": (("Аптека", "Рынок"), ("Супермаркет", "Дискаунтер", "Рынок")),
    "tr": (("Kozmetik", "Pazar"), ("Süpermarket", "İndirim marketi", "Pazar")),
    "uk": (("Аптека", "Ринок"), ("Супермаркет", "Дискаунтер", "Ринок")),
}

# Entity ids of the extra lists (not shown in the app)
DRUGSTORE_ENTITY = "todo.drugstore"
MARKET_ENTITY = "todo.farmers_market"
# Only this list belongs to "Local To-do", so it gets the delete icon in the "Lists" tab
DELETABLE_ENTITY = MARKET_ENTITY

ALL_AREAS = ("produce,bakery,meat,fish_seafood,dairy_eggs,cheese_deli,groceries,milk,eggs,cold_cuts,"
             "refrigerated_cases,butcher_counter,area_1,area_2,area_3,area_4,area_5,area_6,area_7,area_8,"
             "impulse_buy,frozen,dry_goods,canned_jars,sauces_spices,breakfast,snacks_sweets,drinks,alcohol,"
             "coffee_tea,household,cleaning,paper_goods,personal_care,baby,pet,health,non_food,other").split(",")


def store_orders():
    """(order, enabled) per store: supermarket, discount store, farmers market."""
    supermarket = ["produce", "bakery", "meat", "dairy_eggs", "frozen", "coffee_tea", "drinks", "cleaning",
                   "dry_goods"]
    discount = ["drinks", "frozen", "dairy_eggs", "dry_goods", "produce", "bakery", "cleaning", "coffee_tea"]
    market = ["produce", "bakery", "cheese_deli", "eggs"]

    def complete(order):
        return order + [a for a in ALL_AREAS if a not in order]

    return [
        (complete(supermarket), complete(supermarket)),
        # The discount store has no deli or butcher counter
        (complete(discount), [a for a in complete(discount) if a not in ("cheese_deli", "butcher_counter", "fish_seafood")]),
        # The farmers market only sells fresh food
        (complete(market), market),
    ]


def lists(language: str, main_entity: str, main_name: str):
    (drugstore, farmers_market), _ = DEMO.get(language, DEMO["en"])
    return [(main_entity, main_name), (DRUGSTORE_ENTITY, drugstore), (MARKET_ENTITY, farmers_market)]
