#!/usr/bin/env python3
"""Erzeugt Texturen (16x16 PNG), Block-/Item-Modelle, Blockstates, Loot-Tabellen und Sprachdateien.

Aufruf (aus dem Projektordner):  python3 tools/generate_assets.py
"""
import json
import math
import os
import random

from PIL import Image

MODID = "simonspacemod"
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", MODID)
DATA = os.path.join(ROOT, "data", MODID)


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def clamp(v):
    return max(0, min(255, int(v)))


def shade(c, d):
    return tuple(clamp(x + d) for x in c[:3]) + ((c[3],) if len(c) > 3 else (255,))


def new(color):
    return Image.new("RGBA", (16, 16), tuple(color) + (255,))


def noise(img, amount, seed):
    rnd = random.Random(seed)
    px = img.load()
    for x in range(16):
        for y in range(16):
            px[x, y] = shade(px[x, y], rnd.randint(-amount, amount))
    return img


def save(img, name):
    """name ohne Präfix -> textures/block/, mit "item/" -> textures/item/"""
    sub = name if name.startswith("item/") else "block/" + name
    path = os.path.join(ASSETS, "textures", sub + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


# ---------------------------------------------------------------------------------------------
#  Texturen
# ---------------------------------------------------------------------------------------------

HULL = (206, 206, 200)


def tex_hull_plating():
    img = noise(new(HULL), 4, 1)
    px = img.load()
    for i in range(16):
        px[i, 0] = shade(px[i, 0], -18)
        px[0, i] = shade(px[0, i], -18)
        px[i, 15] = shade(px[i, 15], 10)
        px[15, i] = shade(px[15, i], 10)
    return img


def tex_hull_panel():
    img = noise(new((190, 190, 186)), 4, 2)
    px = img.load()
    for i in range(16):
        for j in (0, 8):
            px[i, j] = shade(px[i, j], -22)
            px[j, i] = shade(px[j, i], -22)
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        px[x, y] = (150, 150, 148, 255)
    return img


def tex_hull_dark():
    img = noise(new((120, 122, 126)), 5, 3)
    px = img.load()
    for i in range(16):
        px[i, 0] = shade(px[i, 0], -20)
        px[0, i] = shade(px[0, i], -20)
        if i % 4 == 0:
            for j in range(1, 16):
                px[i, j] = shade(px[i, j], -8)
    return img


def tex_hull_window():
    img = tex_hull_plating()
    px = img.load()
    for x in range(3, 13):
        for y in range(5, 11):
            edge = x in (3, 12) or y in (5, 10)
            px[x, y] = (70, 70, 74, 255) if edge else (255, 236, 170, 255)
    for x in range(4, 12):
        px[x, 6] = (255, 250, 215, 255)
    return img


def tex_hull_marking():
    return noise(new((28, 28, 30)), 3, 5)


def tex_deflector():
    img = new((0, 0, 0))
    px = img.load()
    for x in range(16):
        for y in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            ring = (math.sin(d * 1.6) + 1) / 2
            base = (215 + 30 * ring, 120 + 50 * ring, 40 + 30 * ring)
            if d < 2:
                base = (255, 230, 160)
            px[x, y] = tuple(clamp(c) for c in base) + (255,)
    return img


def tex_bussard():
    img = new((0, 0, 0))
    px = img.load()
    for x in range(16):
        for y in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            a = math.atan2(y - 7.5, x - 7.5)
            swirl = (math.sin(a * 3 + d * 0.9) + 1) / 2
            r = 255
            g = 40 + 110 * swirl * max(0, 1 - d / 10)
            b = 20 + 30 * swirl
            px[x, y] = (clamp(r - d * 4), clamp(g), clamp(b), 255)
    return img


def tex_nacelle_grille():
    img = new((30, 40, 70))
    px = img.load()
    for x in range(16):
        for y in range(16):
            if y % 4 == 0:
                px[x, y] = (60, 70, 90, 255)
            else:
                glow = 1 - abs((y % 4) - 2) / 2
                px[x, y] = (clamp(70 + 80 * glow), clamp(150 + 80 * glow), 255, 255)
    return img


def tex_deck_floor():
    img = noise(new((78, 80, 86)), 6, 7)
    px = img.load()
    for i in range(16):
        px[i, 0] = shade(px[i, 0], -12)
        px[0, i] = shade(px[0, i], -12)
    return img


def tex_bridge_deck():
    img = noise(new((150, 30, 34)), 8, 8)
    return img


def tex_corridor_wall():
    img = noise(new((222, 212, 190)), 3, 9)
    px = img.load()
    for x in range(16):
        for y in (10, 11):
            px[x, y] = (176, 62, 52, 255)
        px[x, 15] = shade(px[x, 15], -20)
    return img


def tex_ceiling_light():
    img = new((250, 250, 240))
    px = img.load()
    for i in range(16):
        for e in (0, 15):
            px[i, e] = (190, 190, 186, 255)
            px[e, i] = (190, 190, 186, 255)
    return img


def tex_console_side():
    img = noise(new((60, 60, 66)), 3, 10)
    px = img.load()
    for i in range(16):
        px[i, 0] = (90, 90, 96, 255)
    return img


def buttons(img, seed, colors, rows):
    rnd = random.Random(seed)
    px = img.load()
    for (y0, y1) in rows:
        x = 1
        while x < 15:
            w = rnd.choice((1, 2, 2, 3))
            c = rnd.choice(colors)
            for xx in range(x, min(15, x + w)):
                for yy in range(y0, y1):
                    px[xx, yy] = c + (255,)
            x += w + 1
    return img


LCARS = [(255, 153, 0), (204, 153, 204), (153, 153, 255), (255, 204, 102), (204, 102, 102), (102, 204, 255)]
TOS_BUTTONS = [(255, 40, 40), (40, 220, 60), (255, 220, 40), (60, 140, 255), (255, 255, 255), (255, 120, 20)]


def tex_bridge_console_front():
    img = new((20, 20, 24))
    buttons(img, 11, TOS_BUTTONS, [(3, 5), (7, 9)])
    px = img.load()
    for x in range(2, 14):
        for y in (11, 12, 13):
            px[x, y] = (40, 50, 60, 255)
    for x in range(3, 13, 3):
        px[x, 12] = (120, 255, 200, 255)
    return img


def tex_bridge_console_top():
    img = new((25, 25, 30))
    buttons(img, 12, TOS_BUTTONS, [(2, 4), (6, 8), (10, 12)])
    return img


def tex_engineering_front():
    img = new((22, 22, 26))
    px = img.load()
    for i, y in enumerate(range(2, 14, 3)):
        level = [11, 7, 13, 5][i]
        for x in range(2, 14):
            px[x, y] = (60, 60, 70, 255)
            if x < level:
                px[x, y] = (80, 180, 255, 255) if x < 9 else (255, 120, 40, 255)
            px[x, y + 1] = (40, 40, 48, 255)
    return img


def tex_lcars():
    img = new((5, 5, 10))
    px = img.load()
    # typischer LCARS-Bogen links + Balken
    for y in range(16):
        for x in range(0, 4):
            px[x, y] = LCARS[0] + (255,)
    for x in range(4, 16):
        for y in (0, 1):
            px[x, y] = LCARS[1] + (255,)
        for y in (14, 15):
            px[x, y] = LCARS[2] + (255,)
    px[3, 2] = (5, 5, 10, 255)
    px[3, 13] = (5, 5, 10, 255)
    rnd = random.Random(14)
    for y in range(4, 13, 2):
        w = rnd.randint(3, 10)
        c = rnd.choice(LCARS)
        for x in range(6, 6 + w):
            px[x, y] = c + (255,)
    return img


def tex_viewscreen():
    img = new((2, 2, 8))
    rnd = random.Random(15)
    px = img.load()
    for _ in range(14):
        x, y = rnd.randrange(16), rnd.randrange(16)
        b = rnd.randint(150, 255)
        px[x, y] = (b, b, clamp(b + 20), 255)
    return img


def tex_viewscreen_side():
    return noise(new((45, 45, 50)), 3, 16)


def tex_chair_seat():
    return noise(new((30, 28, 30)), 5, 17)


def tex_chair_metal():
    return noise(new((150, 150, 156)), 4, 18)


def tex_chair_panel():
    img = new((40, 40, 46))
    buttons(img, 19, TOS_BUTTONS, [(5, 7), (9, 11)])
    return img


def tex_warp_core():
    img = new((0, 0, 0))
    px = img.load()
    for x in range(16):
        for y in range(16):
            band = (math.sin(y * math.pi / 4) + 1) / 2
            center = 1 - abs(x - 7.5) / 8
            px[x, y] = (clamp(60 + 90 * band * center), clamp(130 + 110 * center), 255, 255)
        px[x, 0] = (80, 90, 110, 255)
    for y in range(16):
        px[0, y] = (90, 100, 120, 255)
        px[15, y] = (90, 100, 120, 255)
    return img


def tex_warp_core_top():
    img = new((80, 90, 110))
    px = img.load()
    for x in range(16):
        for y in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 6:
                px[x, y] = (clamp(150 + 15 * (6 - d)), clamp(200 + 9 * (6 - d)), 255, 255)
    return img


def tex_plasma_conduit():
    img = noise(new((60, 62, 70)), 3, 20)
    px = img.load()
    for x in range(16):
        for y in range(5, 11):
            glow = 1 - abs(y - 7.5) / 3
            px[x, y] = (clamp(60 + 120 * glow), clamp(110 + 130 * glow), 255, 255)
    return img


def tex_transporter_pad_top():
    img = new((150, 150, 158))
    px = img.load()
    for x in range(16):
        for y in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 7:
                px[x, y] = (205, 205, 215, 255)
            if 5 < d < 6.2:
                px[x, y] = (110, 110, 120, 255)
            if d < 2.2:
                px[x, y] = (255, 250, 200, 255)
    for (x, y) in ((7, 2), (8, 2), (7, 13), (8, 13), (2, 7), (2, 8), (13, 7), (13, 8)):
        px[x, y] = (255, 230, 120, 255)
    return img


def tex_transporter_pad_side():
    img = noise(new((120, 120, 128)), 3, 21)
    px = img.load()
    for x in range(16):
        for y in range(13, 16):
            px[x, y] = (90, 90, 100, 255)
        px[x, 13] = (255, 220, 110, 255) if x % 3 == 0 else px[x, 13]
    return img


def tex_turbolift_front():
    img = new((200, 160, 40))
    px = img.load()
    for x in range(16):
        for y in range(16):
            px[x, y] = (180, 40, 36, 255) if x < 8 else (200, 52, 44, 255)
            if x in (7, 8):
                px[x, y] = (40, 30, 30, 255)
            if y in (0, 15) or x in (0, 15):
                px[x, y] = (120, 120, 124, 255)
    for y in range(3, 6):
        for x in range(10, 14):
            px[x, y] = (255, 230, 90, 255)
    return img


def tex_communicator():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for x in range(4, 12):
        for y in range(3, 14):
            px[x, y] = (30, 30, 34, 255)
    for x in range(4, 12):
        for y in range(1, 4):
            px[x, y] = (210, 170, 70, 255)  # goldene Gitterklappe
        if x % 2 == 0:
            px[x, 2] = (150, 110, 40, 255)
    for (x, y, c) in ((6, 6, (255, 50, 50)), (8, 6, (60, 220, 60)), (10, 6, (60, 140, 255)),
                      (6, 9, (255, 220, 40)), (8, 9, (220, 220, 220)), (10, 9, (255, 120, 20))):
        px[x, y] = c + (255,)
    for x in range(5, 11):
        px[x, 12] = (90, 90, 100, 255)
    return img


TEXTURES = {
    "hull_plating": tex_hull_plating,
    "hull_panel": tex_hull_panel,
    "hull_dark": tex_hull_dark,
    "hull_window": tex_hull_window,
    "hull_marking": tex_hull_marking,
    "deflector_dish": tex_deflector,
    "bussard_collector": tex_bussard,
    "nacelle_grille": tex_nacelle_grille,
    "deck_floor": tex_deck_floor,
    "bridge_deck": tex_bridge_deck,
    "corridor_wall": tex_corridor_wall,
    "ceiling_light": tex_ceiling_light,
    "console_side": tex_console_side,
    "bridge_console_front": tex_bridge_console_front,
    "bridge_console_top": tex_bridge_console_top,
    "engineering_console_front": tex_engineering_front,
    "lcars_panel": tex_lcars,
    "viewscreen": tex_viewscreen,
    "viewscreen_side": tex_viewscreen_side,
    "chair_seat": tex_chair_seat,
    "chair_metal": tex_chair_metal,
    "chair_panel": tex_chair_panel,
    "warp_core": tex_warp_core,
    "warp_core_top": tex_warp_core_top,
    "plasma_conduit": tex_plasma_conduit,
    "transporter_pad_top": tex_transporter_pad_top,
    "transporter_pad_side": tex_transporter_pad_side,
    "turbolift_front": tex_turbolift_front,
    "item/communicator": tex_communicator,
}

# ---------------------------------------------------------------------------------------------
#  Modelle / Blockstates
# ---------------------------------------------------------------------------------------------


def b(name):
    return f"{MODID}:block/{name}"


SIMPLE = ["hull_plating", "hull_panel", "hull_dark", "hull_window", "hull_marking", "deflector_dish",
          "bussard_collector", "nacelle_grille", "deck_floor", "bridge_deck", "corridor_wall",
          "ceiling_light", "plasma_conduit"]

# Blöcke mit Blickrichtung: Name -> Modell-JSON
FACING = {
    "bridge_console": {"parent": "minecraft:block/orientable",
                       "textures": {"front": b("bridge_console_front"), "side": b("console_side"), "top": b("bridge_console_top")}},
    "engineering_console": {"parent": "minecraft:block/orientable",
                            "textures": {"front": b("engineering_console_front"), "side": b("console_side"), "top": b("console_side")}},
    "lcars_panel": {"parent": "minecraft:block/orientable",
                    "textures": {"front": b("lcars_panel"), "side": b("console_side"), "top": b("console_side")}},
    "viewscreen": {"parent": "minecraft:block/orientable",
                   "textures": {"front": b("viewscreen"), "side": b("viewscreen_side"), "top": b("viewscreen_side")}},
    "turbolift": {"parent": "minecraft:block/orientable",
                  "textures": {"front": b("turbolift_front"), "side": b("hull_panel"), "top": b("hull_panel")}},
    "captains_chair": None,  # eigenes Modell, siehe unten
}


def box(fr, to, tex):
    return {"from": fr, "to": to, "faces": {f: {"texture": tex} for f in ["north", "south", "east", "west", "up", "down"]}}


CHAIR = {
    "parent": "minecraft:block/block",
    "textures": {"particle": b("chair_metal"), "seat": b("chair_seat"), "metal": b("chair_metal"), "panel": b("chair_panel")},
    "elements": [
        box([5, 0, 5], [11, 1, 11], "#metal"),           # Fuß
        box([7, 1, 7], [9, 6, 9], "#metal"),             # Säule
        box([2, 6, 2], [14, 9, 14], "#seat"),            # Sitz
        box([2, 9, 12], [14, 20, 15], "#seat"),          # Rückenlehne (Rückseite = Süden)
        box([0, 9, 3], [2, 12, 14], "#metal"),           # Armlehne links
        box([14, 9, 3], [16, 12, 14], "#metal"),         # Armlehne rechts
        {"from": [0, 12, 3], "to": [2, 12.5, 9], "faces": {"up": {"texture": "#panel"}, "north": {"texture": "#metal"}}},
        {"from": [14, 12, 3], "to": [16, 12.5, 9], "faces": {"up": {"texture": "#panel"}, "north": {"texture": "#metal"}}},
    ],
}

PAD = {
    "parent": "minecraft:block/block",
    "textures": {"particle": b("transporter_pad_side"), "top": b("transporter_pad_top"), "side": b("transporter_pad_side")},
    "elements": [{
        "from": [0, 0, 0], "to": [16, 3, 16],
        "faces": {
            "up": {"texture": "#top"},
            "down": {"texture": "#side"},
            "north": {"texture": "#side", "uv": [0, 13, 16, 16]},
            "south": {"texture": "#side", "uv": [0, 13, 16, 16]},
            "east": {"texture": "#side", "uv": [0, 13, 16, 16]},
            "west": {"texture": "#side", "uv": [0, 13, 16, 16]},
        },
    }],
}

WARP_CORE = {"parent": "minecraft:block/cube_column", "textures": {"end": b("warp_core_top"), "side": b("warp_core")}}

NAMES = {
    "hull_plating": ("Hüllenpanzerung", "Hull Plating"),
    "hull_panel": ("Hüllenplatte", "Hull Panel"),
    "hull_dark": ("Dunkle Hüllenplatte", "Dark Hull Plating"),
    "hull_window": ("Hüllenfenster", "Hull Window"),
    "hull_marking": ("Kennungsmarkierung", "Registry Marking"),
    "deflector_dish": ("Deflektorschüssel", "Deflector Dish"),
    "bussard_collector": ("Bussard-Kollektor", "Bussard Collector"),
    "nacelle_grille": ("Warpgondel-Gitter", "Warp Nacelle Grille"),
    "deck_floor": ("Deckboden", "Deck Floor"),
    "bridge_deck": ("Brückenboden", "Bridge Floor"),
    "corridor_wall": ("Korridorwand", "Corridor Wall"),
    "ceiling_light": ("Deckenleuchte", "Ceiling Light"),
    "bridge_console": ("Brückenkonsole", "Bridge Console"),
    "engineering_console": ("Maschinenraum-Konsole", "Engineering Console"),
    "lcars_panel": ("LCARS-Anzeige", "LCARS Display"),
    "viewscreen": ("Hauptbildschirm", "Viewscreen"),
    "captains_chair": ("Kommandantensessel", "Captain's Chair"),
    "warp_core": ("Warpkern", "Warp Core"),
    "plasma_conduit": ("Plasmaleitung", "Plasma Conduit"),
    "transporter_pad": ("Transporterplattform", "Transporter Pad"),
    "turbolift": ("Turbolift", "Turbolift"),
}


def facing_blockstate(name):
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    return {"variants": {f"facing={d}": ({"model": b(name), "y": r} if r else {"model": b(name)}) for d, r in rot.items()}}


def item_def(model):
    return {"model": {"type": "minecraft:model", "model": model}}


def loot(name):
    return {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "entries": [{"type": "minecraft:item", "name": f"{MODID}:{name}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
        "random_sequence": f"{MODID}:blocks/{name}",
    }


def main():
    for name, fn in TEXTURES.items():
        save(fn(), name)

    models = {}
    for name in SIMPLE:
        models[name] = {"parent": "minecraft:block/cube_all", "textures": {"all": b(name)}}
    for name, model in FACING.items():
        models[name] = model if model else CHAIR
    models["transporter_pad"] = PAD
    models["warp_core"] = WARP_CORE

    for name, model in models.items():
        write_json(os.path.join(ASSETS, "models", "block", name + ".json"), model)
        if name in FACING:
            write_json(os.path.join(ASSETS, "blockstates", name + ".json"), facing_blockstate(name))
        else:
            write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": {"": {"model": b(name)}}})
        write_json(os.path.join(ASSETS, "items", name + ".json"), item_def(b(name)))
        write_json(os.path.join(DATA, "loot_table", "blocks", name + ".json"), loot(name))

    # Kommunikator (Item)
    write_json(os.path.join(ASSETS, "models", "item", "communicator.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MODID}:item/communicator"}})
    write_json(os.path.join(ASSETS, "items", "communicator.json"), item_def(f"{MODID}:item/communicator"))

    # Mit der Spitzhacke abbaubar
    write_json(os.path.join(ROOT, "data", "minecraft", "tags", "block", "mineable", "pickaxe.json"),
               {"values": [f"{MODID}:{n}" for n in models]})

    de = {f"block.{MODID}.{k}": v[0] for k, v in NAMES.items()}
    en = {f"block.{MODID}.{k}": v[1] for k, v in NAMES.items()}
    de.update({
        f"item.{MODID}.communicator": "Kommunikator",
        f"itemGroup.{MODID}.space": "Simon Space Mod",
        f"deck.{MODID}.bridge": "Brücke",
        f"deck.{MODID}.engineering": "Maschinenraum",
        f"deck.{MODID}.transporter": "Transporterraum",
        f"message.{MODID}.turbolift.arrived": "Turbolift: %s",
        f"message.{MODID}.turbolift.unlinked": "Dieser Turbolift ist mit keinem Deck verbunden.",
        f"message.{MODID}.transporter.energize": "Energie! Beamen Sie mit dem Kommunikator zurück.",
        f"message.{MODID}.transporter.no_ground": "Kein Boden unter dem Schiff gefunden.",
        f"message.{MODID}.communicator.no_pad": "Keine Transporterplattform gespeichert.",
        f"message.{MODID}.communicator.pad_gone": "Die Transporterplattform existiert nicht mehr.",
        f"message.{MODID}.communicator.beam_up": "Enterprise an Landetrupp: Wir beamen Sie hoch.",
        f"command.{MODID}.enterprise.computing": "Berechne Bauplan der USS Enterprise NCC-1701 (Hülle %s, wichtige Räume 1:1) ...",
        f"command.{MODID}.enterprise.started": "Baue die Enterprise (%s x %s x %s Blöcke) bei %s ...",
        f"command.{MODID}.enterprise.done": "USS Enterprise fertig gebaut (%s Blöcke).",
        f"command.{MODID}.enterprise.to_bridge": "[Zur Brücke]",
        f"command.{MODID}.enterprise.too_high": "Das Schiff ist %s Blöcke hoch und passt hier nicht in die Bauhöhe (%s bis %s). Wähle eine andere Y-Position oder eine kleinere Größe.",
    })
    en.update({
        f"item.{MODID}.communicator": "Communicator",
        f"itemGroup.{MODID}.space": "Simon Space Mod",
        f"deck.{MODID}.bridge": "Bridge",
        f"deck.{MODID}.engineering": "Engineering",
        f"deck.{MODID}.transporter": "Transporter Room",
        f"message.{MODID}.turbolift.arrived": "Turbolift: %s",
        f"message.{MODID}.turbolift.unlinked": "This turbolift is not linked to any deck.",
        f"message.{MODID}.transporter.energize": "Energize! Use the communicator to beam back up.",
        f"message.{MODID}.transporter.no_ground": "No ground found below the ship.",
        f"message.{MODID}.communicator.no_pad": "No transporter pad stored.",
        f"message.{MODID}.communicator.pad_gone": "The transporter pad no longer exists.",
        f"message.{MODID}.communicator.beam_up": "Enterprise to landing party: beaming you up.",
        f"command.{MODID}.enterprise.computing": "Computing blueprint of the USS Enterprise NCC-1701 (hull %s, key rooms 1:1) ...",
        f"command.{MODID}.enterprise.started": "Building the Enterprise (%s x %s x %s blocks) at %s ...",
        f"command.{MODID}.enterprise.done": "USS Enterprise complete (%s blocks).",
        f"command.{MODID}.enterprise.to_bridge": "[To the bridge]",
        f"command.{MODID}.enterprise.too_high": "The ship is %s blocks tall and does not fit into the build height here (%s to %s). Choose another Y position or a smaller size.",
    })
    write_json(os.path.join(ASSETS, "lang", "de_de.json"), de)
    write_json(os.path.join(ASSETS, "lang", "en_us.json"), en)


if __name__ == "__main__":
    main()
