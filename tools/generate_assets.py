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


def tex_ceiling_panel():
    img = noise(new((226, 226, 222)), 3, 22)
    px = img.load()
    for i in range(16):
        px[i, 0] = shade(px[i, 0], -25)
        px[0, i] = shade(px[0, i], -25)
    for x, y in ((4, 4), (11, 4), (4, 11), (11, 11)):
        px[x, y] = (170, 170, 168, 255)
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


def tex_bussard_orange():
    img = new((0, 0, 0))
    px = img.load()
    for x in range(16):
        for y in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            a = math.atan2(y - 7.5, x - 7.5)
            swirl = (math.sin(a * 4 + d * 1.1) + 1) / 2
            core = max(0.0, 1 - d / 9)
            px[x, y] = (255, clamp(110 + 90 * core + 40 * swirl), clamp(30 + 80 * core * swirl), 255)
    return img


def tex_gray_carpet():
    return noise(new((92, 94, 98)), 7, 30)


def tex_bridge_wall():
    img = noise(new((170, 172, 176)), 3, 31)
    px = img.load()
    for i in range(16):
        px[i, 15] = shade(px[i, 15], -25)
        px[15, i] = shade(px[15, i], -12)
    return img


def tex_bridge_trim():
    img = noise(new((196, 30, 32)), 5, 32)
    px = img.load()
    for i in range(16):
        px[i, 0] = (120, 16, 18, 255)
        px[i, 15] = (120, 16, 18, 255)
    return img


STATION_LIGHTS = [(255, 120, 200), (110, 255, 150), (240, 240, 255), (255, 170, 80)]


def tex_station_front(v):
    img = new((26, 26, 30))
    px = img.load()
    c = STATION_LIGHTS[v]
    for x in range(16):
        for y in range(16):
            if y < 4:
                px[x, y] = (18, 18, 20, 255)          # schwarze Pultkante
            elif y == 4:
                px[x, y] = (200, 30, 32, 255)         # rote Linie
            elif 7 <= y <= 13 and 1 <= x <= 14:
                g = 0.75 + 0.25 * ((x + y) % 3 == 0)
                px[x, y] = tuple(clamp(ch * g) for ch in c) + (255,)
            else:
                px[x, y] = (150, 152, 156, 255)
    return img


def tex_station_top(v):
    img = new((16, 16, 18))
    rnd = random.Random(40 + v)
    px = img.load()
    for _ in range(26):
        x, y = rnd.randrange(1, 15), rnd.randrange(2, 15)
        px[x, y] = rnd.choice(TOS_BUTTONS) + (255,)
    # kleiner Bildschirm hinten
    for x in range(4, 12):
        for y in range(0, 2):
            px[x, y] = [(40, 200, 120), (230, 160, 40), (90, 120, 255), (220, 60, 60)][v] + (255,)
    return img


MONITOR_IMAGES = ["planet", "grid", "stars", "graph"]


def tex_monitor(v):
    img = tex_bridge_wall()
    px = img.load()
    for x in range(1, 15):
        for y in range(2, 13):
            frame = x in (1, 14) or y in (2, 12)
            px[x, y] = (110, 130, 175, 255) if frame else (6, 6, 12, 255)
    rnd = random.Random(50 + v)
    kind = MONITOR_IMAGES[v]
    for x in range(2, 14):
        for y in range(3, 12):
            if kind == "planet":
                d = math.hypot(x - 7, y - 7.5)
                if d < 3.5:
                    px[x, y] = (clamp(200 - d * 20), clamp(80 - d * 10), 40, 255)
            elif kind == "grid":
                if x % 3 == 0 or y % 3 == 0:
                    px[x, y] = (200, 170, 60, 255)
            elif kind == "graph":
                if abs((y - 7) - 3 * math.sin(x / 2)) < 0.8:
                    px[x, y] = (255, 120, 60, 255)
    if kind in ("stars", "planet"):
        for _ in range(8):
            x, y = rnd.randrange(2, 14), rnd.randrange(3, 12)
            if px[x, y][:3] == (6, 6, 12):
                px[x, y] = (230, 230, 255, 255)
    if kind == "stars":
        for i in range(2, 14):
            px[i, 3 + (i * 8) // 12] = (200, 200, 230, 255)
    return img


def tex_railing():
    img = noise(new((205, 34, 36)), 6, 33)
    return img


def tex_trim_black():
    return noise(new((24, 24, 26)), 3, 34)


def tex_chair_blue():
    return noise(new((150, 170, 225)), 5, 35)


def tex_white():
    return noise(new((225, 225, 222)), 3, 36)


def tex_helm_top():
    img = new((16, 16, 18))
    rnd = random.Random(37)
    px = img.load()
    for _ in range(30):
        x, y = rnd.randrange(1, 15), rnd.randrange(1, 15)
        px[x, y] = rnd.choice(TOS_BUTTONS) + (255,)
    for x in range(5, 11):
        for y in range(5, 10):
            px[x, y] = (40, 46, 50, 255)  # Astrogator-Schirm
    return img


def tex_turbolift_door_lower():
    img = new((190, 40, 38))
    px = img.load()
    for x in range(16):
        for y in range(16):
            v = 1 if (x * 3 + y * 5) % 7 == 0 else 0
            px[x, y] = (clamp(195 - 20 * v), 42, 40, 255)
            if x in (7, 8):
                px[x, y] = (90, 20, 20, 255)
    return img


def tex_turbolift_frame():
    img = new((240, 160, 70))
    px = img.load()
    for x in range(16):
        for y in range(16):
            if (x + y) % 2 == 0 and x % 2 == 0:
                px[x, y] = (255, 210, 130, 255)
            if x in (0, 15):
                px[x, y] = (120, 120, 124, 255)
    return img


def tex_transporter_wall():
    return noise(new((168, 128, 210)), 3, 38)


def tex_transporter_platform():
    return noise(new((150, 28, 40)), 6, 39)


def tex_energizer():
    img = new((0, 0, 0))
    px = img.load()
    for x in range(16):
        for y in range(16):
            v = (math.sin(x * 0.9 + math.sin(y * 0.7) * 2.5) + 1) / 2
            px[x, y] = (clamp(40 + 80 * v), clamp(150 + 100 * v), clamp(60 + 60 * v), 255)
    return img


def tex_pillar():
    img = new((60, 60, 66))
    px = img.load()
    for y in range(16):
        for x in range(6, 10):
            px[x, y] = (190, 190, 200, 255)
        if y % 4 == 1:
            px[7, y] = (255, 240, 180, 255)
            px[8, y] = (255, 240, 180, 255)
    return img


def tex_emitter():
    img = new((150, 150, 158))
    px = img.load()
    for x in range(16):
        for y in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 7:
                px[x, y] = (40, 40, 44, 255)
            if 4.5 < d < 6.5:
                px[x, y] = (255, 70, 60, 255)
            if d < 2:
                px[x, y] = (255, 220, 200, 255)
    return img


def tex_transporter_console_top():
    img = new((200, 34, 36))
    rnd = random.Random(41)
    px = img.load()
    for x in range(2, 7):
        for y in range(3, 12):
            px[x, y] = (20, 20, 22, 255)
    for x in range(8, 14):
        for y in range(9, 13):
            px[x, y] = (230, 200, 90, 255)
    for _ in range(14):
        x, y = rnd.randrange(8, 15), rnd.randrange(1, 8)
        px[x, y] = rnd.choice([(255, 200, 40), (255, 255, 255), (60, 220, 60)]) + (255,)
    return img


def tex_pad_disk():
    img = new((150, 28, 40))  # Plattform-Rot in den Ecken
    px = img.load()
    for x in range(16):
        for y in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 7.6:
                px[x, y] = (200, 200, 208, 255)
            if 5.5 < d < 6.4:
                px[x, y] = (120, 120, 130, 255)
            if d < 2.5:
                px[x, y] = (255, 250, 210, 255)
    return img


def tex_nav(c):
    img = new(c)
    px = img.load()
    for x in range(16):
        for y in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 4:
                px[x, y] = (255, 255, 255, 255)
            elif d < 7:
                px[x, y] = tuple(clamp(ch + 40) for ch in c) + (255,)
    return img


def tex_pennant():
    return noise(new((176, 36, 36)), 4, 42)


def tex_table_top():
    img = noise(new((34, 34, 40)), 3, 60)
    px = img.load()
    for i in range(16):
        px[i, 0] = (70, 70, 80, 255)
        px[i, 15] = (70, 70, 80, 255)
    return img


def tex_viewer_screen():
    img = new((50, 54, 64))
    px = img.load()
    for x in range(2, 14):
        for y in range(2, 13):
            px[x, y] = (8, 10, 20, 255)
    for x in range(3, 13):
        px[x, 6 + int(2 * math.sin(x))] = (90, 220, 255, 255)
    for x in range(3, 13, 3):
        px[x, 11] = (255, 170, 60, 255)
    return img


def tex_bio_white():
    return noise(new((232, 232, 238)), 3, 61)


def tex_bio_pad():
    return noise(new((120, 140, 190)), 4, 62)


def tex_bio_monitor():
    img = new((30, 30, 36))
    px = img.load()
    for x in range(1, 15):
        px[x, 4 + (2 if x % 5 == 2 else 0)] = (80, 255, 120, 255)
        px[x, 9] = (255, 80, 80, 255) if x < 9 else (60, 60, 70, 255)
        px[x, 12] = (255, 220, 80, 255) if x < 12 else (60, 60, 70, 255)
    return img


def tex_blanket():
    img = noise(new((70, 90, 150)), 5, 63)
    px = img.load()
    for x in range(16):
        px[x, 7] = (200, 60, 50, 255)
    return img


def tex_pillow():
    return noise(new((236, 236, 236)), 2, 64)


def tex_desk_top():
    img = noise(new((88, 88, 96)), 3, 65)
    return img


def tex_cargo():
    img = noise(new((140, 136, 120)), 5, 66)
    px = img.load()
    for i in range(16):
        for e in (0, 15):
            px[i, e] = (90, 88, 80, 255)
            px[e, i] = (90, 88, 80, 255)
        if i % 4 < 2:
            px[i, 2] = (230, 190, 40, 255)
            px[i, 13] = (230, 190, 40, 255)
    for x in range(5, 11):
        for y in range(6, 10):
            px[x, y] = (240, 240, 230, 255)
    return img


def tex_mess_top():
    return noise(new((215, 215, 212)), 3, 67)


def tex_sickbay_wall():
    img = noise(new((150, 182, 192)), 3, 70)
    px = img.load()
    for i in range(16):
        px[i, 15] = shade(px[i, 15], -18)
    return img


def tex_sickbay_floor():
    return noise(new((44, 74, 58)), 6, 71)


def tex_bio_mattress():
    img = noise(new((176, 104, 58)), 6, 72)
    px = img.load()
    for x in range(16):
        if x % 5 == 0:
            for y in range(16):
                px[x, y] = shade(px[x, y], -25)
    return img


def tex_bio_body():
    img = noise(new((196, 196, 192)), 3, 73)
    px = img.load()
    for x in range(16):
        px[x, 0] = (150, 150, 148, 255)
    return img


def tex_bio_screen():
    img = new((10, 10, 14))
    px = img.load()
    bars = [(2, 11, (255, 80, 60)), (5, 7, (255, 200, 60)), (8, 12, (255, 80, 60)), (11, 5, (80, 255, 120)), (13, 9, (255, 200, 60))]
    for x, h, c in bars:
        for y in range(14 - h, 14):
            px[x, y] = c + (255,)
            px[x + 1, y] = tuple(clamp(ch * 0.7) for ch in c) + (255,)
        px[x - 1, 14 - h + 2] = (240, 240, 240, 255)  # Zeiger
    for x in range(16):
        px[x, 0] = (70, 70, 76, 255)
        px[x, 15] = (70, 70, 76, 255)
    return img


def tex_monitor_housing():
    return noise(new((70, 72, 78)), 3, 74)


def tex_exam_drawers():
    img = noise(new((200, 202, 204)), 3, 75)
    px = img.load()
    for y in (5, 10):
        for x in range(1, 15):
            px[x, y] = (130, 130, 134, 255)
    for y in (2, 7, 12):
        for x in range(7, 9):
            px[x, y] = (90, 90, 96, 255)
    return img


def tex_exam_pad():
    return noise(new((22, 22, 24)), 3, 76)


def tex_cabinet_front():
    img = tex_sickbay_wall()
    px = img.load()
    for x in range(2, 14):
        for y in range(2, 14):
            px[x, y] = (210, 220, 224, 255) if (x in (2, 13) or y in (2, 13)) else (40, 50, 56, 255)
    rnd = random.Random(77)
    for y in (5, 9):
        for x in range(3, 13):
            px[x, y + 1] = (160, 170, 175, 255)
        for _ in range(3):
            x = rnd.randrange(3, 12)
            px[x, y] = rnd.choice([(230, 230, 240), (255, 80, 60), (90, 200, 255)]) + (255,)
    return img


def tex_hull_aztec():
    img = noise(new((190, 200, 210)), 3, 80)
    px = img.load()
    for i in range(16):
        px[i, 0] = shade(px[i, 0], -14)
        px[0, i] = shade(px[0, i], -14)
    return img


def tex_dish_blue():
    img = new((0, 0, 0))
    px = img.load()
    for x in range(16):
        for y in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            ring = (math.sin(d * 1.8) + 1) / 2
            px[x, y] = (clamp(60 + 60 * ring), clamp(140 + 80 * ring), 255, 255)
            if d < 2:
                px[x, y] = (220, 240, 255, 255)
    return img


def tex_bussard_red():
    img = new((0, 0, 0))
    px = img.load()
    for x in range(16):
        for y in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            a = math.atan2(y - 7.5, x - 7.5)
            swirl = (math.sin(a * 3 + d * 1.2) + 1) / 2
            core = max(0.0, 1 - d / 9)
            px[x, y] = (255, clamp(30 + 60 * core * swirl), clamp(40 + 50 * core), 255)
    return img


def tex_torpedo():
    img = noise(new((60, 62, 68)), 3, 81)
    px = img.load()
    for x in range(16):
        for y in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 5:
                px[x, y] = (255, clamp(90 + 25 * (5 - d)), 40, 255)
            elif d < 6:
                px[x, y] = (30, 30, 34, 255)
    return img


def tex_impulse():
    img = new((0, 0, 0))
    px = img.load()
    for x in range(16):
        for y in range(16):
            glow = 1 - abs(y - 7.5) / 8
            px[x, y] = (255, clamp(40 + 120 * glow * glow), clamp(20 + 40 * glow), 255)
        px[x, 0] = (80, 80, 86, 255)
        px[x, 15] = (80, 80, 86, 255)
    for y in range(16):
        if y % 5 == 0:
            for x in range(16):
                px[x, y] = shade(px[x, y], -60)
    return img


def tex_hull_teal():
    img = noise(new((42, 104, 112)), 4, 90)
    px = img.load()
    for i in range(16):
        px[i, 0] = shade(px[i, 0], -15)
    return img


def tex_bussard_blue():
    img = new((0, 0, 0))
    px = img.load()
    for x in range(16):
        for y in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            glow = max(0.0, 1 - d / 10)
            px[x, y] = (clamp(80 + 150 * glow), clamp(170 + 85 * glow), 255, 255)
    return img


def tex_railing_wood():
    img = noise(new((122, 74, 42)), 6, 91)
    px = img.load()
    for y in range(16):
        if y % 4 == 1:
            for x in range(16):
                px[x, y] = shade(px[x, y], -18)
    return img


def tex_trim_cream():
    img = new((240, 224, 176))
    px = img.load()
    for x in range(16):
        for y in range(16):
            if y % 4 == 3:
                px[x, y] = (110, 100, 80, 255)  # Lamellen wie an den Säulen der Excelsior-Brücke
    return img


def tex_carpet_teal():
    return noise(new((44, 90, 96)), 6, 92)


def tex_deck_plate():
    img = noise(new((106, 108, 112)), 3, 93)
    px = img.load()
    for x in range(16):
        for y in range(16):
            if (x + 2 * y) % 8 == 0 or (x - 2 * y) % 8 == 4:
                px[x, y] = (150, 152, 156, 255)  # Riffelblech
    return img


def tex_wall_dark():
    img = noise(new((58, 60, 66)), 3, 94)
    px = img.load()
    for y in range(16):
        px[0, y] = (40, 42, 46, 255)
        px[15, y] = (80, 82, 88, 255)
    return img


def tex_ex_console_front(v):
    img = new((44, 46, 52))
    px = img.load()
    for x in range(16):
        for y in range(16):
            if y < 3:
                px[x, y] = (24, 26, 30, 255)
            elif 10 <= y <= 13:
                px[x, y] = (230, 214, 170, 255) if y % 2 == 0 else (120, 110, 90, 255)  # cremefarbene Lamellen unten
    return img


def tex_ex_console_top(v):
    img = new((14, 16, 18))
    rnd = random.Random(100 + v)
    px = img.load()
    for x0 in (1, 9):
        for x in range(x0, x0 + 6):
            for y in range(2, 8):
                px[x, y] = (10, 40, 20, 255)
        for _ in range(8):
            px[x0 + rnd.randrange(6), 2 + rnd.randrange(6)] = rnd.choice([(80, 255, 120), (60, 200, 255), (200, 255, 120)]) + (255,)
    for x in range(1, 15):
        if rnd.random() < 0.6:
            px[x, 11] = (90, 230, 130, 255)
    return img


def tex_ex_display(v):
    img = new((50, 52, 58))
    px = img.load()
    for x in range(1, 15):
        for y in range(1, 15):
            frame = x in (1, 14) or y in (1, 14)
            px[x, y] = (40, 90, 200, 255) if frame else (4, 10, 6, 255)
    rnd = random.Random(110 + v)
    green = (60, 230, 110)
    if v == 0:  # Schiffs-Schema
        for x in range(3, 13):
            px[x, 6] = green + (255,)
        for x in range(4, 9):
            px[x, 9] = green + (255,)
        for y in range(6, 10):
            px[4, y] = green + (255,)
    elif v == 1:  # Balken
        for x in range(3, 13, 2):
            for y in range(13 - rnd.randint(2, 8), 13):
                px[x, y] = green + (255,)
    elif v == 2:  # Kreis
        for x in range(2, 14):
            for y in range(2, 14):
                if abs(math.hypot(x - 7.5, y - 7.5) - 4) < 0.7:
                    px[x, y] = green + (255,)
    else:  # Text
        for y in range(3, 13, 2):
            for x in range(3, 3 + rnd.randint(4, 10)):
                px[x, y] = (90, 200, 255, 255) if y == 3 else green + (255,)
    return img


def tex_light_pillar():
    img = new((60, 62, 66))
    px = img.load()
    for y in range(16):
        for x in range(4, 12):
            px[x, y] = (245, 232, 196, 255) if y % 3 != 2 else (150, 140, 116, 255)
        px[3, y] = (90, 92, 96, 255)
        px[12, y] = (90, 92, 96, 255)
    return img


def tex_chair_teal():
    return noise(new((44, 104, 112)), 5, 120)


def tex_door_teal():
    img = noise(new((30, 68, 72)), 3, 121)
    px = img.load()
    for y in range(16):
        px[7, y] = (14, 30, 32, 255)
        px[8, y] = (14, 30, 32, 255)
        px[0, y] = (70, 72, 78, 255)
        px[15, y] = (70, 72, 78, 255)
    return img


def tex_helm_gray():
    return noise(new((138, 140, 144)), 4, 122)


def tex_helm_gray_top():
    img = new((20, 22, 24))
    px = img.load()
    rnd = random.Random(123)
    for x in range(2, 14):
        for y in range(3, 9):
            px[x, y] = (12, 40, 24, 255)
    for _ in range(14):
        px[rnd.randrange(2, 14), rnd.randrange(3, 9)] = (90, 240, 130, 255)
    for x in range(1, 15, 2):
        px[x, 12] = rnd.choice([(255, 80, 60), (90, 240, 130), (240, 240, 240)]) + (255,)
    return img


def tex_railing_gray():
    return noise(new((150, 154, 162)), 5, 82)


def tex_trim_blue():
    img = new((50, 110, 210))
    px = img.load()
    for x in range(16):
        for y in range(16):
            glow = 1 - abs(y - 7.5) / 8
            px[x, y] = (clamp(40 + 80 * glow), clamp(100 + 110 * glow), 255, 255)
        px[x, 0] = (60, 64, 72, 255)
        px[x, 15] = (60, 64, 72, 255)
    return img


# ---------------------------------------------------------------------------------------------
#  Next Generation (NCC-1701-C, -D, -E)
# ---------------------------------------------------------------------------------------------


def tex_tng_carpet():
    img = noise(new((96, 90, 104)), 7, 200)
    px = img.load()
    rnd = random.Random(201)
    for _ in range(10):
        px[rnd.randrange(16), rnd.randrange(16)] = (120, 96, 80, 255)
    return img


def tex_tng_wall():
    img = noise(new((214, 198, 166)), 3, 202)
    px = img.load()
    for x in range(16):
        px[x, 0] = (170, 152, 124, 255)
        px[x, 11] = (186, 168, 138, 255)
        px[x, 12] = (232, 220, 196, 255)
    for y in range(16):
        px[15, y] = (180, 162, 134, 255)
    return img


def tex_tng_console_top():
    img = new((10, 10, 16))
    px = img.load()
    rnd = random.Random(203)
    for x in range(1, 15):
        px[x, 2] = LCARS[0] + (255,)
        px[x, 3] = LCARS[0] + (255,)
    for row in (6, 8, 10, 12):
        x = 1
        while x < 15:
            w = rnd.randint(2, 4)
            c = rnd.choice(LCARS)
            for i in range(x, min(15, x + w)):
                px[i, row] = c + (255,)
            x += w + 1
    return img


def tex_chair_brown():
    return noise(new((112, 74, 54)), 6, 204)


def tex_chair_dark():
    return noise(new((54, 52, 58)), 4, 205)


def tex_warp_core_tng():
    img = new((30, 60, 200))
    px = img.load()
    for y in range(16):
        glow = 0.5 + 0.5 * math.sin(y / 16 * math.pi * 2)
        for x in range(16):
            edge = abs(x - 7.5) / 8
            px[x, y] = (clamp(60 + 120 * glow - 60 * edge), clamp(110 + 120 * glow - 60 * edge), clamp(255 - 40 * edge), 255)
        if y % 8 == 0:
            for x in range(16):
                px[x, y] = (70, 72, 80, 255)
    for y in range(16):
        px[0, y] = (60, 62, 70, 255)
        px[15, y] = (60, 62, 70, 255)
    return img


def tex_warp_core_tng_top():
    img = new((60, 62, 70))
    px = img.load()
    for x in range(16):
        for y in range(16):
            if math.hypot(x - 7.5, y - 7.5) < 5:
                px[x, y] = (120, 180, 255, 255)
    return img


def msd_strip():
    """Master Systems Display: Querschnitt der Galaxy-Klasse auf 64 x 16 Pixeln (vier Kacheln)."""
    img = Image.new("RGBA", (64, 16), (4, 6, 20, 255))
    px = img.load()
    for x in range(64):
        px[x, 0] = LCARS[0] + (255,)
        px[x, 15] = LCARS[2] + (255,)
    blue, white = (90, 150, 255, 255), (220, 230, 255, 255)
    for x in range(2, 62):
        # Untertasse (vorne, flach), Hals, Unterrumpf, Gondel oben
        if x < 26:
            top, bot = 5, 7 + (1 if 6 < x < 22 else 0)
            for y in range(top, bot + 1):
                px[x, y] = blue
            px[x, top] = white
        if 24 <= x < 30:
            for y in range(7, 10):
                px[x, y] = blue
        if 26 <= x < 56:
            for y in range(9, 13 - (1 if x > 50 else 0)):
                px[x, y] = blue
            px[x, 9] = white
        if 34 <= x < 62:
            px[x, 4] = white
            px[x, 5] = blue
            px[x, 6] = blue
    for x in (8, 16, 36, 44):
        for y in range(5, 13):
            if px[x, y] == blue:
                px[x, y] = (255, 160, 60, 255)
    return img


def tex_msd(v):
    return msd_strip().crop((v * 16, 0, v * 16 + 16, 16))


def tex_situation_top():
    img = new((8, 8, 14))
    px = img.load()
    for x in range(16):
        px[x, 0] = LCARS[1] + (255,)
        px[x, 15] = LCARS[1] + (255,)
    for x in range(2, 14):
        for y in range(4, 12):
            if (x - 8) ** 2 / 30 + (y - 8) ** 2 / 10 < 1:
                px[x, y] = (60, 110, 220, 255)
    px[8, 8] = (255, 160, 60, 255)
    return img


def tex_holodeck_grid():
    img = new((14, 14, 14))
    px = img.load()
    for i in range(16):
        px[i, 0] = (235, 200, 40, 255)
        px[0, i] = (235, 200, 40, 255)
    return img


def tex_force_field():
    img = Image.new("RGBA", (16, 16), (90, 160, 255, 80))
    px = img.load()
    for x in range(16):
        for y in range(16):
            if (x + y) % 5 == 0:
                px[x, y] = (150, 200, 255, 130)
    return img


def tex_star_map():
    img = new((3, 4, 14))
    px = img.load()
    rnd = random.Random(206)
    for x in range(16):
        for y in range(16):
            neb = math.sin(x * 0.5) * math.cos(y * 0.4)
            if neb > 0.5:
                px[x, y] = (40, 20, 70, 255)
    for _ in range(14):
        px[rnd.randrange(16), rnd.randrange(16)] = rnd.choice([(255, 255, 255), (200, 220, 255), (255, 230, 180)]) + (255,)
    return img


def tex_bar_counter():
    img = noise(new((70, 50, 80)), 4, 207)
    px = img.load()
    for x in range(16):
        px[x, 0] = (200, 190, 220, 255)
        px[x, 1] = (150, 140, 170, 255)
        px[x, 8] = (120, 160, 255, 255)
    return img



def tex_ent_wall():
    img = noise(new((88, 94, 102)), 4, 220)
    px = img.load()
    for x in range(16):
        px[x, 0] = (60, 64, 70, 255)
        px[x, 8] = (70, 74, 82, 255)
    for y in range(16):
        px[0, y] = (60, 64, 70, 255)
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px[x, y] = (130, 136, 144, 255)
    return img


def tex_ent_console_top():
    img = new((30, 34, 40))
    px = img.load()
    rnd = random.Random(221)
    for x in range(2, 14):
        for y in range(2, 8):
            px[x, y] = (20, 60, 110, 255)
    for _ in range(12):
        px[rnd.randrange(2, 14), rnd.randrange(2, 8)] = (150, 210, 255, 255)
    for x in range(2, 14, 3):
        px[x, 11] = (220, 220, 230, 255)
        px[x, 13] = rnd.choice([(255, 170, 60), (120, 200, 255)]) + (255,)
    return img


def tex_ent_console_front():
    img = new((40, 44, 50))
    px = img.load()
    for x in range(3, 13):
        for y in range(3, 10):
            px[x, y] = (30, 90, 150, 255)
    for x in range(4, 12, 2):
        px[x, 6] = (170, 220, 255, 255)
    return img


def tex_decon_light():
    img = new((150, 210, 255))
    px = img.load()
    for x in range(16):
        for y in range(16):
            if x in (0, 15) or y in (0, 15):
                px[x, y] = (90, 100, 110, 255)
            elif y % 4 == 2:
                px[x, y] = (220, 245, 255, 255)
    return img



def lift_door(base, seam, accent, seed):
    """Zweiteilige Schiebetür: Grundfarbe, dunkle Mittelfuge, farbige Akzentstreifen."""
    img = noise(new(base), 3, seed)
    px = img.load()
    for y in range(16):
        px[7, y] = seam + (255,)
        px[8, y] = seam + (255,)
        px[0, y] = shade(base + (255,), -40)
        px[15, y] = shade(base + (255,), -40)
    for x in range(1, 15):
        if x not in (7, 8):
            px[x, 3] = accent + (255,)
            px[x, 12] = accent + (255,)
    return img


def lift_panel(door_fn, screen):
    """Turbolift-Bedienfeld: Tür mit kleinem leuchtendem Schaltfeld."""
    img = door_fn()
    px = img.load()
    for x in range(10, 14):
        for y in range(6, 10):
            px[x, y] = screen + (255,)
    px[11, 7] = (255, 255, 255, 255)
    return img


def tex_door_nx():
    return lift_door((104, 110, 118), (40, 44, 50), (150, 156, 164), 230)


def tex_door_refit():
    return lift_door((150, 156, 168), (60, 64, 74), (80, 140, 230), 231)


def tex_door_tng():
    return lift_door((176, 168, 148), (90, 84, 72), (140, 132, 116), 232)


def tex_phaser_strip():
    img = new((150, 86, 46))
    px = img.load()
    for x in range(16):
        for y in range(16):
            if y in (0, 15):
                px[x, y] = (90, 50, 30, 255)
            elif x % 4 == 0:
                px[x, y] = (190, 120, 60, 255)
    return img


def tex_deflector_ring():
    img = noise(new((138, 90, 42)), 6, 240)
    px = img.load()
    for x in range(16):
        px[x, 7] = (100, 62, 28, 255)
        px[x, 8] = (170, 116, 60, 255)
    return img


TEXTURES = {
    "hull_plating": tex_hull_plating,
    "hull_panel": tex_hull_panel,
    "hull_dark": tex_hull_dark,
    "hull_window": tex_hull_window,
    "hull_marking": tex_hull_marking,
    "deflector_dish": tex_deflector,
    "bussard_collector": tex_bussard_orange,
    "nacelle_grille": tex_nacelle_grille,
    "deck_floor": tex_deck_floor,
    "bridge_deck": tex_gray_carpet,
    "bridge_wall": tex_bridge_wall,
    "bridge_trim": tex_bridge_trim,
    "bridge_railing": tex_railing,
    "trim_black": tex_trim_black,
    "chair_blue": tex_chair_blue,
    "chair_white": tex_white,
    "helm_top": tex_helm_top,
    "turbolift_door_lower": tex_turbolift_door_lower,
    "turbolift_frame": tex_turbolift_frame,
    "transporter_wall": tex_transporter_wall,
    "transporter_platform": tex_transporter_platform,
    "transporter_energizer": tex_energizer,
    "transporter_pillar": tex_pillar,
    "transporter_emitter": tex_emitter,
    "transporter_console_top": tex_transporter_console_top,
    "transporter_pad_disk": tex_pad_disk,
    "nav_light_red": lambda: tex_nav((210, 20, 20)),
    "nav_light_green": lambda: tex_nav((20, 200, 50)),
    "hull_pennant": tex_pennant,
    "table_top": tex_table_top,
    "viewer_screen": tex_viewer_screen,
    "bio_white": tex_bio_white,
    "bio_pad": tex_bio_pad,
    "bio_monitor": tex_bio_monitor,
    "blanket": tex_blanket,
    "pillow": tex_pillow,
    "desk_top": tex_desk_top,
    "cargo_container": tex_cargo,
    "mess_top": tex_mess_top,
    "sickbay_wall": tex_sickbay_wall,
    "hull_aztec": tex_hull_aztec,
    "deflector_dish_blue": tex_dish_blue,
    "bussard_red": tex_bussard_red,
    "torpedo_launcher": tex_torpedo,
    "impulse_engine": tex_impulse,
    "bridge_railing_gray": tex_railing_gray,
    "bridge_trim_blue": tex_trim_blue,
    "hull_teal": tex_hull_teal,
    "bussard_blue": tex_bussard_blue,
    "bridge_railing_wood": tex_railing_wood,
    "bridge_trim_cream": tex_trim_cream,
    "bridge_carpet_teal": tex_carpet_teal,
    "deck_plate": tex_deck_plate,
    "bridge_wall_dark": tex_wall_dark,
    "light_pillar": tex_light_pillar,
    "chair_teal": tex_chair_teal,
    "turbolift_door_teal": tex_door_teal,
    "helm_gray": tex_helm_gray,
    "helm_gray_top": tex_helm_gray_top,
    **{f"excelsior_console_front_{v}": (lambda v=v: tex_ex_console_front(v)) for v in range(4)},
    **{f"excelsior_console_top_{v}": (lambda v=v: tex_ex_console_top(v)) for v in range(4)},
    **{f"excelsior_display_{v}": (lambda v=v: tex_ex_display(v)) for v in range(4)},
    "sickbay_floor": tex_sickbay_floor,
    "bio_mattress": tex_bio_mattress,
    "bio_body": tex_bio_body,
    "bio_screen": tex_bio_screen,
    "monitor_housing": tex_monitor_housing,
    "exam_drawers": tex_exam_drawers,
    "exam_pad": tex_exam_pad,
    "cabinet_front": tex_cabinet_front,
    **{f"bridge_station_front_{v}": (lambda v=v: tex_station_front(v)) for v in range(4)},
    **{f"bridge_station_top_{v}": (lambda v=v: tex_station_top(v)) for v in range(4)},
    **{f"bridge_monitor_{v}": (lambda v=v: tex_monitor(v)) for v in range(4)},
    "corridor_wall": tex_corridor_wall,
    "ceiling_light": tex_ceiling_light,
    "ceiling_panel": tex_ceiling_panel,
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
    "turbolift_front": tex_turbolift_door_lower,
    "item/communicator": tex_communicator,
    "tng_carpet": tex_tng_carpet,
    "tng_wall": tex_tng_wall,
    "tng_console_top": tex_tng_console_top,
    "chair_brown": tex_chair_brown,
    "chair_dark": tex_chair_dark,
    "warp_core_tng": tex_warp_core_tng,
    "warp_core_tng_top": tex_warp_core_tng_top,
    **{f"msd_display_{v}": (lambda v=v: tex_msd(v)) for v in range(4)},
    "situation_top": tex_situation_top,
    "holodeck_grid": tex_holodeck_grid,
    "force_field": tex_force_field,
    "star_map": tex_star_map,
    "bar_counter": tex_bar_counter,
    "ent_wall": tex_ent_wall,
    "ent_console_top": tex_ent_console_top,
    "ent_console_front": tex_ent_console_front,
    "decon_light": tex_decon_light,
    "phaser_strip": tex_phaser_strip,
    "deflector_ring": tex_deflector_ring,
    "turbolift_door_nx": tex_door_nx,
    "turbolift_door_refit": tex_door_refit,
    "turbolift_door_tng": tex_door_tng,
    "turbolift_nx": lambda: lift_panel(tex_door_nx, (90, 170, 255)),
    "turbolift_refit": lambda: lift_panel(tex_door_refit, (120, 190, 255)),
    "turbolift_excelsior": lambda: lift_panel(tex_door_teal, (90, 240, 130)),
    "turbolift_tng": lambda: lift_panel(tex_door_tng, (255, 153, 0)),
}

# ---------------------------------------------------------------------------------------------
#  Modelle / Blockstates
# ---------------------------------------------------------------------------------------------


def b(name):
    return f"{MODID}:block/{name}"


SIMPLE = ["deflector_ring", "phaser_strip", "ent_wall", "decon_light", "tng_carpet", "tng_wall", "holodeck_grid", "star_map", "bar_counter", "light_pillar", "hull_teal", "bussard_blue", "bridge_trim_cream", "bridge_carpet_teal", "deck_plate", "bridge_wall_dark", "impulse_engine", "hull_aztec", "deflector_dish_blue", "bussard_red", "torpedo_launcher", "bridge_trim_blue", "sickbay_wall", "sickbay_floor", "cargo_container", "bridge_wall", "bridge_trim", "turbolift_frame", "transporter_wall", "transporter_platform",
          "transporter_energizer", "transporter_emitter", "nav_light_red", "nav_light_green", "hull_pennant",
          "hull_plating", "hull_panel", "hull_dark", "hull_window", "hull_marking", "deflector_dish",
          "bussard_collector", "nacelle_grille", "deck_floor", "bridge_deck", "corridor_wall",
          "ceiling_light", "ceiling_panel", "plasma_conduit"]

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
    "turbolift_door": {"parent": "minecraft:block/orientable",
                       "textures": {"front": b("turbolift_door_lower"), "side": b("bridge_wall"), "top": b("bridge_wall")}},
    "station_chair": None,
    "helm_console": None,
    "transporter_console": None,
    "biobed_monitor": None,
    "captains_chair_teal": None,
    "station_chair_teal": None,
    "helm_console_gray": None,
    "turbolift_door_teal": {"parent": "minecraft:block/orientable",
                            "textures": {"front": b("turbolift_door_teal"), "side": b("bridge_wall_dark"), "top": b("bridge_wall_dark")}},
    "exam_table": None,
    "medical_cabinet": {"parent": "minecraft:block/orientable",
                        "textures": {"front": b("cabinet_front"), "side": b("sickbay_wall"), "top": b("sickbay_wall")}},
    "crew_bed": None,
    "crew_desk": None,
    "tng_console": None,
    "tng_chair": None,
    "captains_chair_tng": None,
    "ent_console": None,
    "ent_chair": None,
    **{f"turbolift_{e}": {"parent": "minecraft:block/orientable",
                          "textures": {"front": b(f"turbolift_{e}"), "side": b(side), "top": b(side)}}
       for e, side in (("nx", "ent_wall"), ("refit", "bridge_wall"), ("excelsior", "bridge_wall_dark"), ("tng", "tng_wall"))},
    **{f"turbolift_door_{e}": {"parent": "minecraft:block/orientable",
                               "textures": {"front": b(f"turbolift_door_{e}"), "side": b(side), "top": b(side)}}
       for e, side in (("nx", "ent_wall"), ("refit", "bridge_wall"), ("tng", "tng_wall"))},
    "captains_chair_nx": None,
}


def box(fr, to, tex):
    return {"from": fr, "to": to, "faces": {f: {"texture": tex} for f in ["north", "south", "east", "west", "up", "down"]}}


def el(fr, to, faces):
    return {"from": fr, "to": to, "faces": {f: {"texture": t} for f, t in faces.items()}}


def all6(t):
    return {f: t for f in ["north", "south", "east", "west", "up", "down"]}


CHAIR = {
    "parent": "minecraft:block/block",
    "textures": {"particle": b("chair_white"), "seat": b("chair_seat"), "white": b("chair_white"),
                 "metal": b("chair_metal"), "panel": b("chair_panel")},
    "elements": [
        el([2, 0, 2], [14, 3, 14], all6("#white")),             # weißer Sockel
        el([5, 3, 5], [11, 7, 11], all6("#metal")),             # Säule
        el([3, 7, 3], [13, 10, 13], all6("#seat")),             # Sitz
        el([3, 10, 12], [13, 21, 15], all6("#seat")),           # Rückenlehne (Süden)
        el([0, 7, 2], [3, 13, 13], all6("#white")),             # Armlehne links
        el([13, 7, 2], [16, 13, 13], all6("#white")),           # Armlehne rechts
        el([0, 13, 2], [3, 13.5, 8], {"up": "#panel", "north": "#metal"}),
        el([13, 13, 2], [16, 13.5, 8], {"up": "#panel", "north": "#metal"}),
    ],
}

STATION_CHAIR = {
    "parent": "minecraft:block/block",
    "textures": {"particle": b("chair_blue"), "blue": b("chair_blue"), "seat": b("chair_seat"), "metal": b("chair_metal")},
    "elements": [
        el([4, 0, 4], [12, 1, 12], all6("#metal")),
        el([7, 1, 7], [9, 7, 9], all6("#metal")),
        el([3, 7, 3], [13, 9, 13], {"up": "#seat", "down": "#blue", "north": "#blue", "south": "#blue", "east": "#blue", "west": "#blue"}),
        el([3, 9, 11], [13, 20, 14], {"north": "#seat", "south": "#seat", "east": "#blue", "west": "#blue", "up": "#blue", "down": "#blue"}),
    ],
}

HELM = {
    "parent": "minecraft:block/block",
    "textures": {"particle": b("bridge_railing"), "red": b("bridge_railing"), "top": b("helm_top"), "black": b("trim_black")},
    "elements": [
        el([1, 0, 4], [15, 9, 13], all6("#red")),
        el([0, 9, 2], [16, 11, 14], {"up": "#top", "down": "#black", "north": "#black", "south": "#black", "east": "#black", "west": "#black"}),
        el([6, 11, 7], [10, 13, 10], all6("#black")),           # Astrogator-Haube
    ],
}

TRANSPORTER_CONSOLE = {
    "parent": "minecraft:block/block",
    "textures": {"particle": b("transporter_console_top"), "top": b("transporter_console_top"),
                 "gray": b("console_side"), "red": b("bridge_railing")},
    "elements": [
        el([5, 0, 3], [11, 1, 13], all6("#gray")),
        el([6, 1, 6], [10, 9, 10], all6("#gray")),              # Ständer
        {"from": [1, 9, 2], "to": [15, 12, 14],
         "rotation": {"origin": [8, 10, 8], "axis": "x", "angle": 22.5},
         "faces": {"up": {"texture": "#top"}, "down": {"texture": "#gray"}, "north": {"texture": "#red"},
                   "south": {"texture": "#red"}, "east": {"texture": "#red"}, "west": {"texture": "#red"}}},
    ],
}


def furniture(textures, elements):
    t = dict(textures)
    t.setdefault("particle", next(iter(textures.values())))
    return {"parent": "minecraft:block/block", "textures": t, "elements": elements}


CONFERENCE_TABLE = furniture({"top": b("table_top"), "metal": b("chair_metal")}, [
    el([0, 12, 0], [16, 14, 16], all6("#top")),
    el([6, 0, 6], [10, 12, 10], all6("#metal")),
])

TABLE_VIEWER = furniture({"screen": b("viewer_screen"), "metal": b("chair_metal")}, [
    el([6, 0, 6], [10, 2, 10], all6("#metal")),
    el([4, 2, 4], [12, 9, 12], {"north": "#screen", "south": "#screen", "east": "#screen", "west": "#screen",
                                "up": "#metal", "down": "#metal"}),
])

# Biobett (FACING = Richtung Fußende; im Modell zeigt das Fußende nach Norden)
BIOBED_FOOT = furniture({"body": b("bio_body"), "mattress": b("bio_mattress"), "dark": b("monitor_housing")}, [
    el([4, 0, 2], [12, 3, 16], all6("#dark")),                      # schräger Sockel
    el([1, 3, 0], [15, 6, 16], all6("#body")),
    el([1, 6, 0], [15, 8, 16], {"up": "#mattress", "north": "#mattress", "east": "#mattress", "west": "#mattress",
                                "south": "#mattress", "down": "#body"}),
])
BIOBED_HEAD = furniture({"body": b("bio_body"), "mattress": b("bio_mattress"), "dark": b("monitor_housing")}, [
    el([4, 0, 0], [12, 3, 12], all6("#dark")),
    el([1, 3, 0], [15, 6, 16], all6("#body")),
    el([1, 6, 0], [15, 8, 16], {"up": "#mattress", "north": "#mattress", "east": "#mattress", "west": "#mattress",
                                "south": "#mattress", "down": "#body"}),
    el([2, 8, 9], [14, 10, 15], all6("#mattress")),                 # Kissen am Kopfende
    el([0, 3, 15], [16, 9, 16], all6("#body")),                     # Kopfteil an der Wand
])

# Diagnosemonitor: sitzt in der Wand, das schräge Gehäuse ragt in den Raum (Norden)
BIOBED_MONITOR = furniture({"wall": b("sickbay_wall"), "housing": b("monitor_housing"), "screen": b("bio_screen")}, [
    el([0, 0, 0], [16, 16, 16], all6("#wall")),
    el([1, 3, -5], [15, 15, 0], {"north": "#screen", "east": "#housing", "west": "#housing", "up": "#housing",
                                 "down": "#housing", "south": "#housing"}),
])

EXAM_TABLE = furniture({"drawers": b("exam_drawers"), "pad": b("exam_pad"), "body": b("bio_body")}, [
    el([0, 0, 1], [16, 12, 15], {"north": "#drawers", "south": "#drawers", "east": "#body", "west": "#body",
                                 "up": "#body", "down": "#body"}),
    el([0, 12, 0], [16, 14, 16], all6("#pad")),
])

CREW_BED = furniture({"frame": b("chair_metal"), "blanket": b("blanket"), "pillow": b("pillow")}, [
    el([0, 0, 0], [16, 5, 16], all6("#frame")),
    el([0, 5, 0], [16, 8, 11], {"up": "#blanket", "north": "#blanket", "east": "#blanket", "west": "#blanket", "south": "#blanket", "down": "#frame"}),
    el([1, 5, 11], [15, 9, 16], all6("#pillow")),
])

CREW_DESK = furniture({"top": b("desk_top"), "metal": b("chair_metal"), "screen": b("viewer_screen")}, [
    el([0, 12, 0], [16, 14, 16], all6("#top")),
    el([0, 0, 1], [2, 12, 15], all6("#metal")),
    el([14, 0, 1], [16, 12, 15], all6("#metal")),
    el([4, 14, 11], [12, 20, 13], {"north": "#screen", "south": "#metal", "east": "#metal", "west": "#metal", "up": "#metal", "down": "#metal"}),
])

MESS_TABLE = furniture({"top": b("mess_top"), "metal": b("chair_metal")}, [
    el([1, 12, 1], [15, 14, 15], all6("#top")),
    el([6, 0, 6], [10, 12, 10], all6("#metal")),
    el([4, 0, 4], [12, 1, 12], all6("#metal")),
])


def station_model(v):
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": b("console_side"), "front": b(f"bridge_station_front_{v}"),
                     "top": b(f"bridge_station_top_{v}"), "side": b("console_side"), "black": b("trim_black")},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 10, 16],
             "faces": {"north": {"texture": "#front", "uv": [0, 0, 16, 16]}, "south": {"texture": "#side"},
                       "east": {"texture": "#side"}, "west": {"texture": "#side"},
                       "up": {"texture": "#black"}, "down": {"texture": "#side"}}},
            {"from": [0, 10, 5], "to": [16, 13, 16],
             "rotation": {"origin": [8, 10, 8], "axis": "x", "angle": -22.5},
             "faces": {"up": {"texture": "#top"}, "north": {"texture": "#black"}, "south": {"texture": "#black"},
                       "east": {"texture": "#black"}, "west": {"texture": "#black"}, "down": {"texture": "#black"}}},
        ],
    }


def ex_console_model(v):
    m = station_model(v)
    m = json.loads(json.dumps(m))
    m["textures"].update({"particle": b("bridge_wall_dark"), "front": b(f"excelsior_console_front_{v}"),
                          "top": b(f"excelsior_console_top_{v}"), "side": b("bridge_wall_dark")})
    return m


def ex_display_model(v):
    return {"parent": "minecraft:block/orientable",
            "textures": {"front": b(f"excelsior_display_{v}"), "side": b("bridge_wall_dark"), "top": b("bridge_wall_dark")}}


def tng_console_model():
    m = json.loads(json.dumps(station_model(0)))
    m["textures"].update({"particle": b("chair_dark"), "front": b("lcars_panel"), "top": b("tng_console_top"),
                          "side": b("chair_dark"), "black": b("chair_dark")})
    return m


def ent_console_model():
    m = json.loads(json.dumps(station_model(0)))
    m["textures"].update({"particle": b("ent_wall"), "front": b("ent_console_front"), "top": b("ent_console_top"),
                          "side": b("ent_wall"), "black": b("chair_dark")})
    return m


def msd_model(v):
    return {"parent": "minecraft:block/orientable",
            "textures": {"front": b(f"msd_display_{v}"), "side": b("tng_wall"), "top": b("tng_wall")}}


SITUATION_TABLE = furniture({"top": b("situation_top"), "side": b("chair_dark")}, [
    el([0, 0, 1], [16, 12, 15], all6("#side")),
    el([0, 12, 0], [16, 14, 16], {"up": "#top", "down": "#side", "north": "#side", "south": "#side", "east": "#side", "west": "#side"}),
])


def monitor_model(v):
    return {"parent": "minecraft:block/orientable",
            "textures": {"front": b(f"bridge_monitor_{v}"), "side": b("bridge_wall"), "top": b("bridge_wall")}}


RAIL_POST = {
    "parent": "minecraft:block/block",
    "ambientocclusion": False,
    "textures": {"particle": b("bridge_railing"), "red": b("bridge_railing"), "black": b("trim_black")},
    "elements": [
        el([7.25, 0, 7.25], [8.75, 12, 8.75], all6("#black")),
        el([6.5, 12, 6.5], [9.5, 14.5, 9.5], all6("#red")),
    ],
}

RAIL_SIDE = {
    "parent": "minecraft:block/block",
    "ambientocclusion": False,
    "textures": {"particle": b("bridge_railing"), "red": b("bridge_railing"), "black": b("trim_black")},
    "elements": [
        el([6.5, 12, 0], [9.5, 14.5, 8], all6("#red")),
    ],
}


def railing_blockstate(name="bridge_railing"):
    parts = [{"apply": {"model": b(name + "_post")}}]
    for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        m = {"model": b(name + "_side")}
        if y:
            m["y"] = y
        parts.append({"apply": m, "when": {d: "true"}})
    return {"multipart": parts}


def variant_facing_blockstate(name):
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    for d, r in rot.items():
        for v in range(4):
            m = {"model": b(f"{name}_{v}")}
            if r:
                m["y"] = r
            variants[f"facing={d},variant={v}"] = m
    return {"variants": variants}


def slab_assets(name, tex):
    write_json(os.path.join(ASSETS, "models", "block", name + ".json"),
               {"parent": "minecraft:block/slab", "textures": {"bottom": b(tex), "top": b(tex), "side": b(tex)}})
    write_json(os.path.join(ASSETS, "models", "block", name + "_top.json"),
               {"parent": "minecraft:block/slab_top", "textures": {"bottom": b(tex), "top": b(tex), "side": b(tex)}})
    write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": {
        "type=bottom": {"model": b(name)}, "type=top": {"model": b(name + "_top")}, "type=double": {"model": b(tex)}}})
    write_json(os.path.join(ASSETS, "items", name + ".json"), item_def(b(name)))
    write_json(os.path.join(DATA, "loot_table", "blocks", name + ".json"), loot(name))


PAD = {
    "parent": "minecraft:block/block",
    "textures": {"particle": b("transporter_pad_side"), "top": b("transporter_pad_disk"), "side": b("transporter_pad_side")},
    "elements": [{
        "from": [0, 0, 0], "to": [16, 1, 16],
        "faces": {
            "up": {"texture": "#top"},
            "down": {"texture": "#side"},
            "north": {"texture": "#side", "uv": [0, 15, 16, 16]},
            "south": {"texture": "#side", "uv": [0, 15, 16, 16]},
            "east": {"texture": "#side", "uv": [0, 15, 16, 16]},
            "west": {"texture": "#side", "uv": [0, 15, 16, 16]},
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
    "ceiling_panel": ("Deckenverkleidung", "Ceiling Panel"),
    "bridge_console": ("Brückenkonsole", "Bridge Console"),
    "engineering_console": ("Maschinenraum-Konsole", "Engineering Console"),
    "lcars_panel": ("LCARS-Anzeige", "LCARS Display"),
    "viewscreen": ("Hauptbildschirm", "Viewscreen"),
    "captains_chair": ("Kommandantensessel", "Captain's Chair"),
    "warp_core": ("Warpkern", "Warp Core"),
    "plasma_conduit": ("Plasmaleitung", "Plasma Conduit"),
    "transporter_pad": ("Transporterfeld", "Transporter Pad"),
    "turbolift": ("Turbolift", "Turbolift"),
    "hull_plating_slab": ("Hüllenpanzerungs-Stufe", "Hull Plating Slab"),
    "hull_marking_slab": ("Kennungsmarkierungs-Stufe", "Registry Marking Slab"),
    "hull_pennant": ("Roter Zierstreifen", "Red Pennant Stripe"),
    "nav_light_red": ("Positionslicht (rot)", "Navigation Light (Red)"),
    "nav_light_green": ("Positionslicht (grün)", "Navigation Light (Green)"),
    "bridge_wall": ("Brückenwand", "Bridge Wall"),
    "bridge_trim": ("Roter Brücken-Abschlussstreifen", "Red Bridge Trim"),
    "bridge_station": ("Brückenstation", "Bridge Station"),
    "bridge_monitor": ("Brückenmonitor", "Bridge Monitor"),
    "bridge_railing": ("Brückengeländer", "Bridge Railing"),
    "station_chair": ("Stationsstuhl", "Station Chair"),
    "helm_console": ("Steuerkonsole", "Helm Console"),
    "turbolift_door": ("Turbolift-Tür", "Turbolift Door"),
    "turbolift_frame": ("Turbolift-Rahmen", "Turbolift Frame"),
    "step_slab": ("Stufe", "Step"),
    "transporter_wall": ("Transporterraum-Wand", "Transporter Room Wall"),
    "transporter_platform": ("Transporterplattform-Boden", "Transporter Platform Floor"),
    "transporter_energizer": ("Transporter-Energiewand", "Transporter Energizer"),
    "transporter_pillar": ("Transporter-Säule", "Transporter Pillar"),
    "transporter_emitter": ("Transporter-Strahler", "Transporter Emitter"),
    "transporter_console": ("Transporterkonsole", "Transporter Console"),
    "conference_table": ("Konferenztisch", "Conference Table"),
    "table_viewer": ("Dreiseitiger Tischbildschirm", "Three-Sided Table Viewer"),
    "biobed": ("Biobett", "Biobed"),
    "biobed_monitor": ("Diagnosemonitor", "Diagnostic Monitor"),
    "exam_table": ("Untersuchungstisch", "Examination Table"),
    "medical_cabinet": ("Medizinschrank", "Medical Cabinet"),
    "sickbay_wall": ("Krankenstations-Wand", "Sickbay Wall"),
    "hull_aztec": ("Refit-Hüllenplatte (Aztec)", "Refit Hull Panel (Aztec)"),
    "deflector_dish_blue": ("Deflektorschüssel (Refit, blau)", "Deflector Dish (Refit, Blue)"),
    "bussard_red": ("Bussard-Kollektor (Refit, rot)", "Bussard Collector (Refit, Red)"),
    "torpedo_launcher": ("Photonentorpedo-Werfer", "Photon Torpedo Launcher"),
    "impulse_engine": ("Impulstriebwerk", "Impulse Engine"),
    "hull_teal": ("Petrolfarbene Hüllenplatte (Excelsior)", "Teal Hull Panel (Excelsior)"),
    "bussard_blue": ("Bussard-Kollektor (Excelsior, blau)", "Bussard Collector (Excelsior, Blue)"),
    "bridge_railing_wood": ("Brückengeländer (Excelsior, Holz)", "Bridge Railing (Excelsior, Wood)"),
    "bridge_trim_cream": ("Cremefarbene Lichtleiste (Excelsior)", "Cream Light Strip (Excelsior)"),
    "bridge_carpet_teal": ("Petrolfarbener Teppich (Excelsior)", "Teal Carpet (Excelsior)"),
    "deck_plate": ("Riffelblech-Boden", "Diamond Plate Floor"),
    "bridge_wall_dark": ("Dunkle Brückenwand (Excelsior)", "Dark Bridge Wall (Excelsior)"),
    "excelsior_console": ("Excelsior-Konsole", "Excelsior Console"),
    "excelsior_display": ("Excelsior-Wandbildschirm", "Excelsior Wall Display"),
    "light_pillar": ("Lichtsäule", "Light Pillar"),
    "captains_chair_teal": ("Kommandantensessel (Excelsior)", "Captain's Chair (Excelsior)"),
    "station_chair_teal": ("Stationsstuhl (Excelsior)", "Station Chair (Excelsior)"),
    "turbolift_door_teal": ("Turbolift-Tür (Excelsior)", "Turbolift Door (Excelsior)"),
    "helm_console_gray": ("Steuerkonsole (Excelsior)", "Helm Console (Excelsior)"),
    "bridge_railing_gray": ("Brückengeländer (Refit, grau)", "Bridge Railing (Refit, Gray)"),
    "bridge_trim_blue": ("Blaue Lichtleiste (Refit)", "Blue Light Strip (Refit)"),
    "sickbay_floor": ("Krankenstations-Boden", "Sickbay Floor"),
    "crew_bed": ("Koje", "Crew Bunk"),
    "crew_desk": ("Schreibtisch", "Crew Desk"),
    "cargo_container": ("Frachtcontainer", "Cargo Container"),
    "mess_table": ("Messetisch", "Mess Table"),
    "tng_carpet": ("Teppichboden (Next Generation)", "Carpet (Next Generation)"),
    "tng_wall": ("Wandverkleidung (Next Generation)", "Wall Panel (Next Generation)"),
    "tng_console": ("LCARS-Konsole", "LCARS Console"),
    "tng_chair": ("Sessel (Next Generation)", "Chair (Next Generation)"),
    "captains_chair_tng": ("Kommandantensessel (Next Generation)", "Captain's Chair (Next Generation)"),
    "warp_core_tng": ("Warpkern (Next Generation)", "Warp Core (Next Generation)"),
    "msd_display": ("Master Systems Display", "Master Systems Display"),
    "situation_table": ("Lagetisch (Maschinenraum)", "Situation Table (Engineering)"),
    "holodeck_grid": ("Holodeck-Gitter", "Holodeck Grid"),
    "force_field": ("Kraftfeld", "Force Field"),
    "star_map": ("Sternenkarte", "Star Map"),
    "bar_counter": ("Bartresen (Ten Forward)", "Bar Counter (Ten Forward)"),
    "ent_wall": ("Metallwand (NX-01)", "Metal Wall (NX-01)"),
    "ent_console": ("Konsole (NX-01)", "Console (NX-01)"),
    "ent_chair": ("Sitz (NX-01)", "Seat (NX-01)"),
    "captains_chair_nx": ("Kommandantensessel (NX-01)", "Captain's Chair (NX-01)"),
    "decon_light": ("Dekontaminations-Leuchte", "Decontamination Light"),
    "phaser_strip": ("Phaserstreifen", "Phaser Strip"),
    "deflector_ring": ("Deflektorring (Kupfer)", "Deflector Ring (Copper)"),
    "turbolift_nx": ("Turbolift (NX-01)", "Turbolift (NX-01)"),
    "turbolift_refit": ("Turbolift (Refit)", "Turbolift (Refit)"),
    "turbolift_excelsior": ("Turbolift (Excelsior)", "Turbolift (Excelsior)"),
    "turbolift_tng": ("Turbolift (Next Generation)", "Turbolift (Next Generation)"),
    "turbolift_door_nx": ("Turbolift-Tür (NX-01)", "Turbolift Door (NX-01)"),
    "turbolift_door_refit": ("Turbolift-Tür (Refit)", "Turbolift Door (Refit)"),
    "turbolift_door_tng": ("Turbolift-Tür (Next Generation)", "Turbolift Door (Next Generation)"),
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
    models_extra = []
    for name in SIMPLE:
        models[name] = {"parent": "minecraft:block/cube_all", "textures": {"all": b(name)}}
    custom = {"captains_chair": CHAIR, "station_chair": STATION_CHAIR, "helm_console": HELM,
              "transporter_console": TRANSPORTER_CONSOLE, "crew_bed": CREW_BED, "crew_desk": CREW_DESK,
              "biobed_monitor": BIOBED_MONITOR, "exam_table": EXAM_TABLE,
              "captains_chair_teal": dict(CHAIR, textures=dict(CHAIR["textures"], seat=b("chair_teal"), white=b("helm_gray"), particle=b("chair_teal"))),
              "station_chair_teal": dict(STATION_CHAIR, textures=dict(STATION_CHAIR["textures"], blue=b("helm_gray"), seat=b("chair_teal"), particle=b("chair_teal"))),
              "tng_console": tng_console_model(),
              "ent_console": ent_console_model(),
              "ent_chair": dict(STATION_CHAIR, textures=dict(STATION_CHAIR["textures"], blue=b("ent_wall"), seat=b("chair_dark"), particle=b("chair_dark"))),
              "captains_chair_nx": dict(CHAIR, textures=dict(CHAIR["textures"], seat=b("chair_dark"), white=b("ent_wall"), particle=b("chair_dark"))),
              "tng_chair": dict(STATION_CHAIR, textures=dict(STATION_CHAIR["textures"], blue=b("chair_dark"), seat=b("chair_brown"), particle=b("chair_brown"))),
              "captains_chair_tng": dict(CHAIR, textures=dict(CHAIR["textures"], seat=b("chair_brown"), white=b("chair_dark"), particle=b("chair_brown"))),
              "helm_console_gray": dict(HELM, textures={"particle": b("helm_gray"), "red": b("helm_gray"), "top": b("helm_gray_top"), "black": b("bridge_wall_dark")})}
    for name, model in FACING.items():
        models[name] = model if model else custom[name]
    models["conference_table"] = CONFERENCE_TABLE
    models["table_viewer"] = TABLE_VIEWER
    models["mess_table"] = MESS_TABLE
    models["transporter_pillar"] = {"parent": "minecraft:block/cube_column",
                                    "textures": {"end": b("trim_black"), "side": b("transporter_pillar")}}
    models["transporter_pad"] = PAD
    models["warp_core"] = WARP_CORE
    models["warp_core_tng"] = {"parent": "minecraft:block/cube_column", "textures": {"end": b("warp_core_tng_top"), "side": b("warp_core_tng")}}
    models["situation_table"] = SITUATION_TABLE
    models["force_field"] = {"parent": "minecraft:block/cube_all", "render_type": "minecraft:translucent", "textures": {"all": b("force_field")}}

    for name, model in models.items():
        write_json(os.path.join(ASSETS, "models", "block", name + ".json"), model)
        if name in FACING:
            write_json(os.path.join(ASSETS, "blockstates", name + ".json"), facing_blockstate(name))
        else:
            write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": {"": {"model": b(name)}}})
        write_json(os.path.join(ASSETS, "items", name + ".json"), item_def(b(name)))
        write_json(os.path.join(DATA, "loot_table", "blocks", name + ".json"), loot(name))

    # Turbolifte: zwei Hälften wie eine Tür (unten Bedienfeld, oben Tür)
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    for lift, door in (("turbolift", "turbolift_door"), ("turbolift_nx", "turbolift_door_nx"),
                       ("turbolift_refit", "turbolift_door_refit"), ("turbolift_excelsior", "turbolift_door_teal"),
                       ("turbolift_tng", "turbolift_door_tng")):
        variants = {}
        for d, r in rot.items():
            for half, model in (("lower", lift), ("upper", door)):
                m = {"model": b(model)}
                if r:
                    m["y"] = r
                variants[f"facing={d},half={half}"] = m
        write_json(os.path.join(ASSETS, "blockstates", lift + ".json"), {"variants": variants})

    # Blöcke mit Varianten (Stationen, Monitore)
    for name, fn in (("bridge_station", station_model), ("bridge_monitor", monitor_model),
                     ("excelsior_console", ex_console_model), ("excelsior_display", ex_display_model),
                     ("msd_display", msd_model)):
        for v in range(4):
            write_json(os.path.join(ASSETS, "models", "block", f"{name}_{v}.json"), fn(v))
        write_json(os.path.join(ASSETS, "blockstates", name + ".json"), variant_facing_blockstate(name))
        write_json(os.path.join(ASSETS, "items", name + ".json"), item_def(b(f"{name}_0")))
        write_json(os.path.join(DATA, "loot_table", "blocks", name + ".json"), loot(name))
        models_extra.append(name)
    # Geländer (verbindet sich wie Eisengitter): rot (Originalserie) und grau (Refit)
    for rail in ("bridge_railing", "bridge_railing_gray", "bridge_railing_wood"):
        tex = {"particle": b(rail), "red": b(rail), "black": b("trim_black")}
        post = dict(RAIL_POST, textures=tex)
        side = dict(RAIL_SIDE, textures=tex)
        write_json(os.path.join(ASSETS, "models", "block", rail + "_post.json"), post)
        write_json(os.path.join(ASSETS, "models", "block", rail + "_side.json"), side)
        write_json(os.path.join(ASSETS, "models", "block", rail + "_inventory.json"),
                   {"parent": "minecraft:block/block", "textures": tex, "elements": post["elements"] + side["elements"]})
        write_json(os.path.join(ASSETS, "blockstates", rail + ".json"), railing_blockstate(rail))
        write_json(os.path.join(ASSETS, "items", rail + ".json"), item_def(b(rail + "_inventory")))
        write_json(os.path.join(DATA, "loot_table", "blocks", rail + ".json"), loot(rail))
        models_extra.append(rail)
    # Biobett (zwei Hälften)
    write_json(os.path.join(ASSETS, "models", "block", "biobed_foot.json"), BIOBED_FOOT)
    write_json(os.path.join(ASSETS, "models", "block", "biobed_head.json"), BIOBED_HEAD)
    variants = {}
    for d, r in {"north": 0, "east": 90, "south": 180, "west": 270}.items():
        for head in ("false", "true"):
            m = {"model": b("biobed_head" if head == "true" else "biobed_foot")}
            if r:
                m["y"] = r
            variants[f"facing={d},head={head}"] = m
    write_json(os.path.join(ASSETS, "blockstates", "biobed.json"), {"variants": variants})
    write_json(os.path.join(ASSETS, "items", "biobed.json"), item_def(b("biobed_head")))
    write_json(os.path.join(DATA, "loot_table", "blocks", "biobed.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MODID}:biobed"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"},
                                  {"condition": "minecraft:block_state_property", "block": f"{MODID}:biobed",
                                   "properties": {"head": "false"}}]}],
        "random_sequence": f"{MODID}:blocks/biobed"})
    models_extra.append("biobed")

    # Halbstufen
    for name, tex in (("hull_plating_slab", "hull_plating"), ("hull_marking_slab", "hull_marking"), ("step_slab", "trim_black")):
        slab_assets(name, tex)
        models_extra.append(name)
    write_json(os.path.join(ASSETS, "models", "block", "trim_black.json"),
               {"parent": "minecraft:block/cube_all", "textures": {"all": b("trim_black")}})

    # Kommunikator (Item)
    write_json(os.path.join(ASSETS, "models", "item", "communicator.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MODID}:item/communicator"}})
    write_json(os.path.join(ASSETS, "items", "communicator.json"), item_def(f"{MODID}:item/communicator"))

    # Mit der Spitzhacke abbaubar
    write_json(os.path.join(ROOT, "data", "minecraft", "tags", "block", "mineable", "pickaxe.json"),
               {"values": [f"{MODID}:{n}" for n in list(models) + models_extra]})

    # Rezepte (Überlebensmodus): Turbolift und Kommunikator
    write_json(os.path.join(DATA, "recipe", "turbolift.json"), {
        "type": "minecraft:crafting_shaped", "category": "redstone",
        "key": {"I": "minecraft:iron_ingot", "R": "minecraft:redstone", "E": "minecraft:ender_pearl", "D": "minecraft:red_dye"},
        "pattern": ["IDI", "IEI", "IRI"],
        "result": {"id": f"{MODID}:turbolift", "count": 2}})
    # Turbolift in einen anderen Stil umfärben (funktioniert genauso)
    for e, dye in (("nx", "gray_dye"), ("refit", "light_blue_dye"), ("excelsior", "cyan_dye"), ("tng", "orange_dye")):
        write_json(os.path.join(DATA, "recipe", f"turbolift_{e}.json"), {
            "type": "minecraft:crafting_shapeless", "category": "redstone",
            "ingredients": [f"{MODID}:turbolift", f"minecraft:{dye}"],
            "result": {"id": f"{MODID}:turbolift_{e}"}})
    write_json(os.path.join(DATA, "recipe", "communicator.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {"G": "minecraft:gold_ingot", "I": "minecraft:iron_ingot", "R": "minecraft:redstone", "E": "minecraft:ender_pearl"},
        "pattern": [" G ", "IEI", "IRI"],
        "result": {"id": f"{MODID}:communicator"}})

    de = {f"block.{MODID}.{k}": v[0] for k, v in NAMES.items()}
    en = {f"block.{MODID}.{k}": v[1] for k, v in NAMES.items()}
    de.update({
        f"item.{MODID}.communicator": "Kommunikator",
        f"itemGroup.{MODID}.space": "Simon Space Mod",
        f"message.{MODID}.turbolift.arrived": "Turbolift: %s",
        f"message.{MODID}.turbolift.gone": "Dieser Turbolift existiert nicht mehr.",
        f"gui.{MODID}.turbolift": "Turbolift",
        f"gui.{MODID}.turbolift.name": "Deckname",
        f"gui.{MODID}.turbolift.network": "Netz",
        f"gui.{MODID}.turbolift.save": "Speichern",
        f"gui.{MODID}.turbolift.choose": "Deck wählen (%s Halte):",
        f"gui.{MODID}.turbolift.here": "%s (hier)",
        f"message.{MODID}.transporter.energize": "Energie! Beamen Sie mit dem Kommunikator zurück.",
        f"message.{MODID}.transporter.no_ground": "Kein Boden unter dem Schiff gefunden.",
        f"message.{MODID}.communicator.no_pad": "Keine Transporterplattform gespeichert.",
        f"message.{MODID}.communicator.pad_gone": "Die Transporterplattform existiert nicht mehr.",
        f"message.{MODID}.communicator.beam_up": "Enterprise an Landetrupp: Wir beamen Sie hoch.",
        f"command.{MODID}.enterprise.computing": "Berechne Bauplan der USS Enterprise %s (Hülle %s, wichtige Räume 1:1) ...",
        f"command.{MODID}.enterprise.started": "Baue die Enterprise (%s x %s x %s Blöcke) bei %s ...",
        f"command.{MODID}.enterprise.done": "USS Enterprise %s fertig gebaut (%s Blöcke).",
        f"command.{MODID}.enterprise.to_bridge": "[Zur Brücke]",
        f"command.{MODID}.enterprise.too_high": "Das Schiff ist %s Blöcke hoch und passt hier nicht in die Bauhöhe (%s bis %s). Wähle eine andere Y-Position oder eine kleinere Größe.",
    })
    en.update({
        f"item.{MODID}.communicator": "Communicator",
        f"itemGroup.{MODID}.space": "Simon Space Mod",
        f"message.{MODID}.turbolift.arrived": "Turbolift: %s",
        f"message.{MODID}.turbolift.gone": "This turbolift no longer exists.",
        f"gui.{MODID}.turbolift": "Turbolift",
        f"gui.{MODID}.turbolift.name": "Deck name",
        f"gui.{MODID}.turbolift.network": "Network",
        f"gui.{MODID}.turbolift.save": "Save",
        f"gui.{MODID}.turbolift.choose": "Choose deck (%s stops):",
        f"gui.{MODID}.turbolift.here": "%s (here)",
        f"message.{MODID}.transporter.energize": "Energize! Use the communicator to beam back up.",
        f"message.{MODID}.transporter.no_ground": "No ground found below the ship.",
        f"message.{MODID}.communicator.no_pad": "No transporter pad stored.",
        f"message.{MODID}.communicator.pad_gone": "The transporter pad no longer exists.",
        f"message.{MODID}.communicator.beam_up": "Enterprise to landing party: beaming you up.",
        f"command.{MODID}.enterprise.computing": "Computing blueprint of the USS Enterprise %s (hull %s, key rooms 1:1) ...",
        f"command.{MODID}.enterprise.started": "Building the Enterprise (%s x %s x %s blocks) at %s ...",
        f"command.{MODID}.enterprise.done": "USS Enterprise %s complete (%s blocks).",
        f"command.{MODID}.enterprise.to_bridge": "[To the bridge]",
        f"command.{MODID}.enterprise.too_high": "The ship is %s blocks tall and does not fit into the build height here (%s to %s). Choose another Y position or a smaller size.",
    })
    write_json(os.path.join(ASSETS, "lang", "de_de.json"), de)
    write_json(os.path.join(ASSETS, "lang", "en_us.json"), en)


if __name__ == "__main__":
    main()
