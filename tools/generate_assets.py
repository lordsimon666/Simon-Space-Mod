#!/usr/bin/env python3
"""Erzeugt Texturen (16x16 PNG), Block-/Item-Modelle, Blockstates, Loot-Tabellen und Sprachdateien.

Aufruf (aus dem Projektordner):  python3 tools/generate_assets.py
"""
import json
import math
import os
import random
import zlib

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
    """Phaserstreifen: dunkles Rotbraun wie am Studiomodell (nicht hellbraun)."""
    img = new((104, 58, 46))
    px = img.load()
    for x in range(16):
        for y in range(16):
            if y in (0, 15):
                px[x, y] = (70, 40, 34, 255)
            elif x % 4 == 0:
                px[x, y] = (130, 74, 56, 255)
    return img


def tex_deflector_ring():
    img = noise(new((138, 90, 42)), 6, 240)
    px = img.load()
    for x in range(16):
        px[x, 7] = (100, 62, 28, 255)
        px[x, 8] = (170, 116, 60, 255)
    return img


def tex_deflector_violet():
    img = noise(new((154, 120, 184)), 5, 241)
    px = img.load()
    for x in range(16):
        for y in range(16):
            g = 1 - abs(y - 7.5) / 8
            r, gg, b, a = px[x, y]
            px[x, y] = (clamp(r + 25 * g), clamp(gg + 15 * g), clamp(b + 30 * g), 255)
    return img


# ---------------------------------------------------------------------------------------------
#  Waffen: Phaser-Blöcke und Waffenstation
# ---------------------------------------------------------------------------------------------

def tex_phase_cannon():
    """NX-01 Phasenkanone: Metallgrau mit dunkler Mündung und bläulichem Glühen."""
    img = noise(new((84, 90, 98)), 4, 300)
    px = img.load()
    for x in range(16):
        for y in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 3.2:
                px[x, y] = (20, 24, 30, 255)
            elif d < 4.4:
                px[x, y] = (120, 200, 240, 255)
            elif d < 5.4:
                px[x, y] = (50, 54, 60, 255)
    for i in range(16):
        px[i, 0] = px[i, 15] = px[0, i] = px[15, i] = (60, 64, 70, 255)
    return img


def tex_phaser_bank():
    """Phaserbank der Originalserie: dunkles Gehäuse mit drei Emittern."""
    img = noise(new((48, 52, 60)), 3, 301)
    px = img.load()
    for cx in (3, 8, 13):
        for x in range(cx - 1, cx + 1):
            for y in range(6, 10):
                px[x, y] = (110, 160, 255, 255)
        px[cx - 1, 6] = (220, 235, 255, 255)
    for i in range(16):
        px[i, 0] = px[i, 15] = (30, 32, 38, 255)
    return img


def tex_quantum_launcher():
    """Quantentorpedo-Werfer der E: dunkle Klappe mit blau leuchtender Öffnung."""
    img = noise(new((70, 76, 86)), 4, 303)
    px = img.load()
    for x in range(16):
        for y in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 3.0:
                px[x, y] = (200, 230, 255, 255)
            elif d < 4.6:
                px[x, y] = (70, 140, 255, 255)
            elif d < 5.6:
                px[x, y] = (36, 40, 50, 255)
            elif x in (0, 15) or y in (0, 15):
                px[x, y] = (46, 50, 58, 255)
    return img


def tex_phaser_emitter():
    """Refit-/Excelsior-Phaser: rötlicher Sockel mit glühendem Schlitz."""
    img = noise(new((104, 60, 50)), 4, 302)
    px = img.load()
    for x in range(16):
        for y in range(16):
            if 6 <= y <= 9 and 2 <= x <= 13:
                px[x, y] = (255, 110, 50, 255) if y in (7, 8) else (200, 60, 30, 255)
            elif x in (0, 15) or y in (0, 15):
                px[x, y] = (70, 40, 34, 255)
    return img


def tex_weapons_top(v):
    """Zielerfassung: Fadenkreuz und Feuerknöpfe (Farben je Epoche)."""
    bg, glow = [((18, 16, 18), (255, 60, 40)), ((26, 28, 30), (90, 240, 130)),
                ((20, 20, 26), (255, 150, 30)), ((28, 32, 40), (120, 200, 255))][v]
    img = new(bg)
    px = img.load()
    for x in range(16):
        for y in range(16):
            d = math.hypot(x - 7.5, y - 6.5)
            if abs(d - 4.5) < 0.6 or (abs(x - 7.5) < 0.6 and 2 <= y <= 11) or (abs(y - 6.5) < 0.6 and 3 <= x <= 12):
                px[x, y] = glow + (255,)
    px[7, 6] = px[8, 6] = px[7, 7] = px[8, 7] = (255, 255, 255, 255)
    for x in range(2, 14, 3):
        px[x, 13] = (220, 40, 30, 255)
        px[x + 1, 13] = (255, 210, 60, 255)
    return img


WEAPON_HOUSING = ["bridge_railing", "helm_gray", "chair_dark", "ent_wall"]


def weapons_console_model(v):
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": b(WEAPON_HOUSING[v]), "housing": b(WEAPON_HOUSING[v]),
                     "top": b(f"weapons_console_top_{v}"), "black": b("trim_black")},
        "elements": [
            el([1, 0, 4], [15, 9, 13], all6("#housing")),
            el([0, 9, 2], [16, 11, 14], {"up": "#top", "down": "#black", "north": "#black", "south": "#black", "east": "#black", "west": "#black"}),
            el([6, 11, 9], [10, 13, 12], all6("#black")),       # Zielscanner
        ],
    }


# ---------------------------------------------------------------------------------------------
#  Uniformen (Rüstungs-Texturen 64x32 im Spielerlayout)
# ---------------------------------------------------------------------------------------------

ERAS = ["nx", "tos", "movie", "tng", "fc"]
DIVS = ["command", "science", "operations"]
DIV_COLOR = {
    "nx": {"command": (214, 178, 60), "science": (90, 150, 215), "operations": (190, 40, 40)},
    "tos": {"command": (216, 178, 60), "science": (120, 160, 214), "operations": (178, 36, 36)},
    "movie": {"command": (236, 236, 230), "science": (160, 176, 150), "operations": (206, 168, 70)},
    "tng": {"command": (156, 26, 26), "science": (44, 108, 140), "operations": (200, 160, 48)},
    "fc": {"command": (150, 24, 24), "science": (40, 104, 136), "operations": (196, 156, 44)},
}
GOLD = (224, 192, 80)
BLACK = (24, 24, 28)

BODY = {"top": (20, 16, 8, 4), "bottom": (28, 16, 8, 4), "right": (16, 20, 4, 12), "front": (20, 20, 8, 12),
        "left": (28, 20, 4, 12), "back": (32, 20, 8, 12)}
ARM = {"top": (44, 16, 4, 4), "bottom": (48, 16, 4, 4), "right": (40, 20, 4, 12), "front": (44, 20, 4, 12),
       "left": (48, 20, 4, 12), "back": (52, 20, 4, 12)}
LEG = {"top": (4, 16, 4, 4), "bottom": (8, 16, 4, 4), "right": (0, 20, 4, 12), "front": (4, 20, 4, 12),
       "left": (8, 20, 4, 12), "back": (12, 20, 4, 12)}
SIDES = ("right", "front", "left", "back")


def seed_of(*parts):
    import zlib
    return zlib.crc32("/".join(parts).encode())


def fill(px, rect, color, seed=0, n=5):
    rnd = random.Random(seed)
    x0, y0, w, h = rect
    for x in range(x0, x0 + w):
        for y in range(y0, y0 + h):
            px[x, y] = shade(color, rnd.randint(-n, n))


def rows(px, part, r0, r1, color, faces=SIDES):
    """Färbt Zeilen r0..r1 (0 = oben) der Seitenflächen eines Körperteils."""
    for f in faces:
        x0, y0, w, h = part[f]
        for x in range(x0, x0 + w):
            for r in range(r0, r1 + 1):
                if 0 <= r < h:
                    px[x, y0 + r] = tuple(color) + (255,)


def uniform_texture(era, div):
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()
    dc = DIV_COLOR[era][div]
    base = {"nx": (44, 61, 94), "tos": dc, "movie": (122, 22, 22), "tng": dc, "fc": (30, 30, 34)}[era]
    for f, r in BODY.items():
        fill(px, r, base, seed_of(era, div, f), 4)
    for f, r in ARM.items():
        fill(px, r, base, seed_of(era, div, "a", f), 4)
    fx, fy = BODY["front"][0], BODY["front"][1]   # Vorderseite: x 20..27, y 20..31 (x=20 rechte Körperseite)
    if era == "tos":
        for x in range(22, 26):
            px[x, fy] = BLACK + (255,)                       # schwarzer Kragen
        for x in range(32, 40):
            px[x, 20] = BLACK + (255,)
        px[25, 22] = px[26, 22] = px[25, 23] = GOLD + (255,)  # Abzeichen
        rows(px, ARM, 9, 9, GOLD)                           # Ärmelstreifen
        rows(px, ARM, 11, 11, GOLD)
    elif era == "movie":
        light = (160, 46, 46)
        for i in range(8):                                  # Brustklappe schräg
            px[fx + i, fy + 1 + i // 2] = light + (255,)
        for x in range(22, 26):
            px[x, fy] = dc + (255,)                          # Rollkragen in Abteilungsfarbe
        px[23, fy + 1] = px[24, fy + 1] = dc + (255,)
        rows(px, BODY, 10, 11, BLACK)                        # Gürtel
        px[23, fy + 10] = px[24, fy + 10] = GOLD + (255,)
        px[26, fy + 2] = px[26, fy + 3] = GOLD + (255,)      # Abzeichen
        rows(px, ARM, 10, 10, GOLD)                         # Rangstreifen
    elif era == "tng":
        rows(px, BODY, 0, 2, BLACK)                          # schwarze Schulterpasse
        fill(px, BODY["top"], BLACK, 7, 2)
        rows(px, ARM, 0, 1, BLACK)
        fill(px, ARM["top"], BLACK, 8, 2)
        px[25, fy + 3] = px[26, fy + 3] = px[25, fy + 4] = GOLD + (255,)  # Kommunikator
        px[21, fy + 1] = px[22, fy + 1] = GOLD + (255,)      # Rangpins
    elif era == "fc":
        grey = (92, 94, 100)
        rows(px, BODY, 0, 2, grey)                           # gesteppte graue Schultern
        fill(px, BODY["top"], grey, 9, 6)
        rows(px, ARM, 0, 1, grey)
        fill(px, ARM["top"], grey, 10, 6)
        for x in range(20, 28, 2):
            px[x, fy + 1] = (120, 122, 128, 255)
        for x in range(22, 26):
            px[x, fy] = dc + (255,)                          # farbiges Unterhemd am Kragen
        px[23, fy + 1] = px[24, fy + 1] = dc + (255,)
        px[25, fy + 4] = px[26, fy + 4] = px[25, fy + 5] = GOLD + (255,)  # Kommunikator
    elif era == "nx":
        for x in (20, 21, 26, 27):
            px[x, fy] = dc + (255,)                          # Paspel an den Schultern
        rows(px, ARM, 0, 0, dc)
        for x in range(21, 23):
            px[x, fy + 4] = (70, 90, 130, 255)               # Reißverschlusstasche
        px[23, fy + 1] = px[24, fy + 1] = (30, 40, 60, 255)  # Kragen
        for f in ("front", "back", "left", "right"):          # Missionsabzeichen am Oberarm
            x0, y0, _, _ = ARM[f]
            px[x0 + 1, y0 + 2] = px[x0 + 2, y0 + 2] = (180, 200, 230, 255)
            px[x0 + 1, y0 + 3] = px[x0 + 2, y0 + 3] = (60, 100, 170, 255)
    return img


def trousers_texture(color):
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()
    for f, r in LEG.items():
        fill(px, r, color, seed_of("leg", f), 3)
    for f in SIDES:                                          # Hosenbund am Körper
        x0, y0, w, h = BODY[f]
        fill(px, (x0, y0 + 6, w, 6), color, 410, 3)
    fill(px, BODY["bottom"], color, 411, 3)
    return img


def boots_texture():
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()
    for f in SIDES:
        x0, y0, w, h = LEG[f]
        fill(px, (x0, y0 + 7, w, 5), (22, 22, 26), 420, 3)
        for x in range(x0, x0 + w):
            px[x, y0 + 7] = (50, 50, 56, 255)
    fill(px, LEG["bottom"], (14, 14, 16), 421, 2)
    return img


def icon_tunic(era, div):
    dc = DIV_COLOR[era][div]
    base = {"nx": (44, 61, 94), "tos": dc, "movie": (122, 22, 22), "tng": dc, "fc": (30, 30, 34)}[era]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for x in range(16):
        for y in range(16):
            body = 4 <= x <= 11 and 2 <= y <= 14
            sleeve = (1 <= x <= 3 or 12 <= x <= 14) and 3 <= y <= 9
            if body or sleeve:
                px[x, y] = shade(base, -12 if x in (1, 14) or y == 14 else 0)
    for x in range(6, 10):
        px[x, 2] = (0, 0, 0, 0)
    if era == "tos":
        for x in range(6, 10):
            px[x, 3] = BLACK + (255,)
        px[9, 5] = GOLD + (255,)
        for x in (1, 2, 3, 12, 13, 14):
            px[x, 8] = GOLD + (255,)
    elif era == "tng":
        for x in range(1, 15):
            for y in range(2, 6):
                if px[x, y][3]:
                    px[x, y] = BLACK + (255,)
        px[9, 7] = GOLD + (255,)
    elif era == "fc":
        for x in range(1, 15):
            for y in range(2, 5):
                if px[x, y][3]:
                    px[x, y] = (92, 94, 100, 255)
        px[7, 3] = px[8, 3] = dc + (255,)
        px[9, 7] = GOLD + (255,)
    elif era == "movie":
        for i in range(6):
            px[4 + i, 4 + i // 2] = (170, 50, 50, 255)
        px[7, 3] = px[8, 3] = dc + (255,)
        for x in range(4, 12):
            px[x, 12] = BLACK + (255,)
    elif era == "nx":
        for x in (4, 5, 10, 11, 1, 2, 13, 14):
            px[x, 3] = dc + (255,)
    return img


def icon_trousers(color):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for x in range(4, 12):
        for y in range(2, 5):
            px[x, y] = shade(color, 0)
    for x in list(range(4, 8)) + list(range(8, 12)):
        for y in range(5, 15):
            if x in (7, 8) and y > 5:
                continue
            px[x, y] = shade(color, -10 if x in (4, 11) else 0)
    return img


def icon_boots():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for (x0, x1) in ((2, 6), (9, 13)):
        for x in range(x0, x1 + 1):
            for y in range(4, 13):
                px[x, y] = (30, 30, 34, 255)
        for x in range(x0 - 1, x1 + 3):
            for y in range(12, 14):
                if x < 16:
                    px[x, y] = (18, 18, 20, 255)
    return img


ERA_NAMES = {
    "nx": ("NX-01, 2150er", "NX-01, 2150s"),
    "tos": ("Originalserie, 2260er", "Original Series, 2260s"),
    "movie": ("Kinofilme, 2280er/90er", "Movie Era, 2280s/90s"),
    "tng": ("Next Generation, 2360er", "Next Generation, 2360s"),
    "fc": ("Enterprise-E, 2370er", "Enterprise-E, 2370s"),
}
DIV_NAMES = {
    "nx": {"command": ("Kommando, goldene Paspel", "Command, gold piping"),
           "science": ("Wissenschaft, blaue Paspel", "Sciences, blue piping"),
           "operations": ("Technik & Sicherheit, rote Paspel", "Engineering & Security, red piping")},
    "tos": {"command": ("Kommando, gold", "Command, gold"), "science": ("Wissenschaft, blau", "Sciences, blue"),
            "operations": ("Technik & Sicherheit, rot", "Engineering & Security, red")},
    "movie": {"command": ("Kommando, weißer Kragen", "Command, white collar"),
              "science": ("Wissenschaft & Medizin, grauer Kragen", "Sciences & Medical, grey collar"),
              "operations": ("Technik, gelber Kragen", "Engineering, yellow collar")},
    "tng": {"command": ("Kommando, rot", "Command, red"), "science": ("Wissenschaft, blau", "Sciences, blue"),
            "operations": ("Technik & Sicherheit, gold", "Engineering & Security, gold")},
    "fc": {"command": ("Kommando, rot", "Command, red"), "science": ("Wissenschaft, blau", "Sciences, blue"),
           "operations": ("Technik & Sicherheit, gold", "Engineering & Security, gold")},
}


def uniform_assets(de, en):
    tex_dir = os.path.join(ASSETS, "textures", "entity", "equipment")

    def armor_item(name, layer, img, icon, dn, enn):
        p = os.path.join(tex_dir, layer, name + ".png")
        os.makedirs(os.path.dirname(p), exist_ok=True)
        img.save(p)
        write_json(os.path.join(ASSETS, "equipment", name + ".json"),
                   {"layers": {layer: [{"texture": f"{MODID}:{name}"}]}})
        save(icon, "item/" + name)
        write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MODID}:item/{name}"}})
        write_json(os.path.join(ASSETS, "items", name + ".json"), item_def(f"{MODID}:item/{name}"))
        de[f"item.{MODID}.{name}"] = dn
        en[f"item.{MODID}.{name}"] = enn

    for era in ERAS:
        for div in DIVS:
            d, e = DIV_NAMES[era][div]
            armor_item(f"uniform_{era}_{div}", "humanoid", uniform_texture(era, div), icon_tunic(era, div),
                       f"Uniform ({ERA_NAMES[era][0]}) – {d}", f"Uniform ({ERA_NAMES[era][1]}) – {e}")
    armor_item("trousers_nx", "humanoid_leggings", trousers_texture((40, 56, 88)), icon_trousers((40, 56, 88)),
               "Uniformhose (NX-01, dunkelblau)", "Uniform Trousers (NX-01, navy)")
    armor_item("trousers_black", "humanoid_leggings", trousers_texture((26, 26, 30)), icon_trousers((26, 26, 30)),
               "Uniformhose (schwarz)", "Uniform Trousers (black)")
    armor_item("boots_starfleet", "humanoid", boots_texture(), icon_boots(),
               "Sternenflotten-Stiefel", "Starfleet Boots")


# ---------------------------------------------------------------------------------------------
#  Warpkerne der NX-01 und der E, Waffenkammer und Handwaffen
# ---------------------------------------------------------------------------------------------

def tex_warp_core_nx():
    """NX-01: waagerechter Reaktor, hellblau mit dunklen Ringen (schwächer)."""
    img = new((0, 0, 0))
    px = img.load()
    for x in range(16):
        for y in range(16):
            center = 1 - abs(y - 7.5) / 8
            ring = x % 5 == 0
            px[x, y] = (40, 60, 80, 255) if ring else (clamp(70 + 80 * center), clamp(150 + 80 * center), clamp(200 + 55 * center), 255)
    return img


def tex_warp_core_nx_top():
    img = new((60, 70, 86))
    px = img.load()
    for x in range(16):
        for y in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 5:
                px[x, y] = (clamp(120 + 20 * (5 - d)), clamp(190 + 10 * (5 - d)), 255, 255)
    return img


def tex_warp_core_e():
    """Enterprise-E: kräftig blau mit hellen Energiebändern (am stärksten)."""
    img = new((0, 0, 0))
    px = img.load()
    for x in range(16):
        for y in range(16):
            band = (math.sin(y * math.pi / 2) + 1) / 2
            center = 1 - abs(x - 7.5) / 8
            px[x, y] = (clamp(40 + 160 * band * center), clamp(80 + 150 * center * (0.5 + band / 2)), 255, 255)
    for y in range(16):
        px[0, y] = px[15, y] = (50, 60, 90, 255)
    return img


def tex_warp_core_e_top():
    img = new((50, 60, 90))
    px = img.load()
    for x in range(16):
        for y in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 6.5:
                px[x, y] = (clamp(120 + 20 * (6.5 - d)), clamp(170 + 13 * (6.5 - d)), 255, 255)
    return img


def tex_armory_metal():
    img = noise(new((78, 82, 92)), 4, 500)
    px = img.load()
    for i in range(16):
        px[i, 0] = px[i, 15] = px[0, i] = px[15, i] = (56, 60, 68, 255)
    for x in (4, 8, 12):
        for y in range(2, 14):
            px[x, y] = (60, 64, 72, 255)
    return img


def tex_holder_plate():
    img = noise(new((96, 102, 112)), 3, 501)
    px = img.load()
    for (x0, y0) in ((1, 1), (9, 1), (1, 9), (9, 9)):
        for x in range(x0, x0 + 6):
            for y in range(y0, y0 + 6):
                px[x, y] = (64, 68, 76, 255)
    return img


def tex_charger_side():
    img = noise(new((70, 76, 86)), 3, 502)
    px = img.load()
    for x in range(3, 13):
        px[x, 5] = (90, 200, 255, 255)
    for x in range(3, 13, 3):
        px[x, 9] = (255, 180, 60, 255)
    return img


def tex_charger_glass():
    """Durchsichtiges Glas mit leichtem Blaustich und Rahmenkante."""
    img = Image.new("RGBA", (16, 16), (170, 220, 255, 60))
    px = img.load()
    for i in range(16):
        px[i, 0] = px[i, 15] = px[0, i] = px[15, i] = (120, 150, 170, 200)
    for i in range(3, 7):
        px[i, i - 1] = (230, 245, 255, 120)  # Lichtreflex
    return img


def tex_charger_top():
    img = new((40, 46, 56))
    px = img.load()
    for x in range(16):
        for y in range(16):
            if 2 <= x <= 13 and 3 <= y <= 12:
                g = 1 - abs(y - 7.5) / 6
                px[x, y] = (clamp(60 + 60 * g), clamp(150 + 80 * g), 255, 255)
    return img


# Farbfelder für die Handwaffen (4 x 4 Felder je 4 x 4 Pixel)
WEAPON_COLORS = [
    (70, 74, 80), (28, 28, 32), (180, 184, 190), (150, 150, 154),
    (90, 90, 96), (196, 186, 160), (132, 130, 126), (255, 140, 40),
    (110, 190, 255), (255, 60, 40), (210, 170, 70), (230, 230, 235),
    (50, 60, 75), (60, 200, 120), (90, 60, 40), (230, 200, 60),
]


def tex_weapon_palette():
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    rnd = random.Random(510)
    for i, c in enumerate(WEAPON_COLORS):
        x0, y0 = (i % 4) * 4, (i // 4) * 4
        for x in range(x0, x0 + 4):
            for y in range(y0, y0 + 4):
                px[x, y] = shade(c, rnd.randint(-5, 5))
    return img


def wbox(fr, to, color, rot=None):
    x0, y0 = (color % 4) * 4, (color // 4) * 4
    uv = [x0 + 1, y0 + 1, x0 + 3, y0 + 3]
    e = {"from": fr, "to": to, "faces": {f: {"texture": "#p", "uv": uv} for f in ("north", "south", "east", "west", "up", "down")}}
    if rot:
        axis, angle, origin = rot
        e["rotation"] = {"axis": axis, "angle": angle, "origin": origin}
    return e


GM, BK, SV, LG, DG, BE, TG, OR, BL, RD, GD, WH, DB, GN = range(14)


def grip(x0, x1, y0, y1, c, angle=-22.5, z0=7, z1=9):
    """Schräg nach hinten geneigter Griff (dreht um die Oberkante)."""
    return ([x0, y0, z0], [x1, y1, z1], c, ("z", angle, [(x0 + x1) / 2, y1, 8]))


def slope(x0, x1, y0, y1, c, angle, ox, oy, z0=6.6, z1=9.4):
    return ([x0, y0, z0], [x1, y1, z1], c, ("z", angle, [ox, oy, 8]))


# Seitenansicht in der x/y-Ebene, Lauf nach +x, Griff nach unten, Dicke um z = 8; Schrägen über gedrehte Teile
WEAPON_MODELS = {
    # NX-01: Phasenpistole EM-33 – kantig, metallgrau, schräger Griff, abgeschrägte Front, blaue Mündung
    "phaser_nx": [grip(3, 6, 2, 8.6, BK), ([2, 8, 6.5], [11, 11, 9.5], GM), slope(9, 12.5, 9.2, 11, GM, -22.5, 12.5, 9.2),
                  ([3, 11, 7], [9, 12, 9], DG), ([11, 8.4, 7], [14.5, 10.2, 9], GM), ([14.5, 8.7, 7.3], [15.2, 9.9, 8.7], DG),
                  ([15.2, 8.9, 7.5], [15.6, 9.7, 8.5], BL), ([6, 6.6, 7.6], [8, 8, 8.4], BK), ([4.5, 9, 9.5], [8.5, 10, 9.8], SV),
                  ([4.5, 9, 6.2], [8.5, 10, 6.5], SV)],
    # Originalserie: Typ-2-Phaser (Pistolengriff schräg) mit aufgestecktem Typ 1
    "phaser_tos": [grip(2, 5, 1, 8.4, BK), ([1, 8, 6.5], [9, 10.5, 9.5], BK), ([2, 10.5, 6.8], [7, 12, 9.2], BK),
                   slope(6, 8.6, 10.5, 12, BK, -22.5, 8.6, 10.5, 6.8, 9.2), ([2.5, 12, 7.2], [6.5, 12.4, 8.8], SV),
                   ([9, 8.6, 7.3], [11, 10, 8.7], SV), ([11, 8.9, 7.6], [12, 9.7, 8.4], SV), ([12, 9.0, 7.7], [12.4, 9.6, 8.3], BL),
                   ([3.5, 8.3, 9.5], [5.5, 9.5, 9.8], SV), ([3.5, 8.3, 6.2], [5.5, 9.5, 6.5], SV)],
    # Kinofilme: grauer Phaser mit dunklem, schrägem Griff, abgeschrägte Nase, Anzeige oben
    "phaser_movie": [grip(2, 5.5, 1, 8.4, DG), ([1.5, 8, 6.5], [9.5, 11, 9.5], LG), slope(8.5, 12, 8.6, 11, LG, -22.5, 12, 8.6),
                     ([11, 8.8, 7.2], [13, 10.2, 8.8], BK), ([13, 9.1, 7.5], [13.5, 9.9, 8.5], RD), ([3.5, 11, 7], [7.5, 11.5, 9], GN),
                     grip(5.5, 8, 6.8, 8, GD, -22.5, 7.2, 8.8)],
    # Next Generation: Typ 2 ("Cobra Head") – langer, schräg nach vorne steigender Körper mit Kopf
    "phaser_tng": [grip(2.5, 5.5, 1, 7, TG, -22.5), grip(5.1, 5.7, 1, 7, BK, -22.5, 7.2, 8.8),
                   slope(4.5, 9, 6, 9, TG, 22.5, 4.5, 6, 7, 9), ([7, 8.6, 6.6], [12, 11.4, 9.4], TG),
                   slope(11, 13, 8.9, 11.4, TG, -22.5, 13, 8.9), ([12.5, 9.2, 7.2], [13.4, 10.6, 8.8], BK),
                   ([13.4, 9.5, 7.6], [13.8, 10.3, 8.4], OR), ([7.5, 11.4, 7.5], [10.5, 11.7, 8.5], BK)],
    # Enterprise-E: Typ 2 der 2370er – dunkel, schlank, schräger Griff, Goldstreifen
    "phaser_fc": [grip(2, 5, 1, 7.4, BK), ([2, 7, 6.6], [10.5, 10, 9.4], DG), slope(9.5, 13, 7.6, 10, DG, -22.5, 13, 7.6),
                  ([3, 10, 7], [9, 10.8, 9], GM), ([12, 7.9, 7.2], [14, 9.3, 8.8], BK), ([14, 8.2, 7.6], [14.4, 9, 8.4], OR),
                  ([3.5, 8, 9.4], [10, 8.6, 9.6], GD), ([3.5, 8, 6.4], [10, 8.6, 6.6], GD)],
    # NX-01: Phasengewehr der MACOs – schräger Schaft, Magazin, Zielfernrohr
    "rifle_nx": [slope(-6, 0.5, 4.5, 9, BK, 22.5, 0.5, 9, 7, 9), ([0, 6, 6.5], [12, 10, 9.5], GM), grip(3, 6, 1.5, 6.2, BK),
                 grip(7, 9.5, 2.5, 6.2, DG, 22.5, 7.2, 8.8), ([12, 7, 7], [20, 9, 9], GM), ([12, 6, 7.3], [17, 7, 8.7], BK),
                 slope(19, 21, 7, 9, GM, -22.5, 21, 7, 7.2, 8.8), ([3, 10, 7.2], [10, 11.8, 8.8], BK),
                 ([10, 10.4, 7.5], [10.4, 11.4, 8.5], BL), ([20.6, 7.5, 7.5], [21.3, 8.5, 8.5], BL)],
    # Originalserie: Phasergewehr – langer grauer Lauf, großer Emitter, schräger Schaft
    "rifle_tos": [slope(-6, 0, 4, 8.5, BK, 22.5, 0, 8.5, 7, 9), ([-1, 6, 6.5], [10, 10, 9.5], LG), grip(2, 5, 1.5, 6.2, BK),
                  ([10, 7, 7], [18.5, 9.5, 9], LG), ([18.5, 6.4, 6.6], [21.5, 10.1, 9.4], SV),
                  slope(21, 22.6, 6.8, 9.7, SV, -22.5, 22.6, 6.8, 6.9, 9.1), ([22.2, 7.7, 7.6], [22.7, 8.8, 8.4], BL),
                  ([1, 10, 7.4], [7, 11.5, 8.6], BK)],
    # Next Generation: Typ-3-Phasergewehr – schräger hinterer und vorderer Griff, kein Schaft
    "rifle_tng": [slope(-4, 2, 6, 10, TG, -22.5, -4, 10, 6.6, 9.4), ([1, 6.5, 6.6], [14, 10, 9.4], TG), grip(0.5, 3.5, 1.5, 6.6, BK),
                  grip(9, 11, 2.5, 6.6, BK, 22.5), ([14, 7, 7], [19, 9.5, 9], TG), slope(18.5, 20.5, 7, 9.5, TG, -22.5, 20.5, 7, 7.2, 8.8),
                  ([20, 7.6, 7.5], [20.5, 9, 8.5], OR), ([3, 10, 7.4], [12, 10.8, 8.6], BK)],
    # Enterprise-E: Kompressions-Phasergewehr – dunkel, schräger Schaft und Vordergriff, orange Streifen
    "rifle_fc": [slope(-6, 0.5, 4.5, 9.5, BK, 22.5, 0.5, 9.5, 7, 9), ([0, 6, 6.5], [13, 10.5, 9.5], DG), grip(2.5, 5.5, 1.5, 6.2, BK),
                 grip(9, 11, 3, 6.2, BK, 22.5, 7.2, 8.8), ([13, 7, 7], [20, 9.5, 9], DG), slope(19.5, 21.5, 7, 9.5, DG, -22.5, 21.5, 7, 7.2, 8.8),
                 ([21, 7.4, 7.4], [21.5, 9.1, 8.6], OR), ([1, 9, 9.5], [12, 9.6, 9.7], OR), ([1, 9, 6.3], [12, 9.6, 6.5], OR),
                 ([4, 10.5, 7.4], [10, 12, 8.6], BK)],
}


def weapon_display(rifle):
    s = 0.62 if rifle else 1.0
    return {
        # Außenansicht: Lauf waagerecht nach vorne statt wie ein Schwert nach oben; Gewehre größer
        # Außenansicht mit nach vorne gestrecktem Arm (siehe WeaponClientExtensions): Lauf verlängert den Arm, Griff nach unten
        "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [0.75 if rifle else 0.85] * 3},
        "thirdperson_lefthand": {"rotation": [0, -90, 0], "translation": [0, 0, 0], "scale": [0.75 if rifle else 0.85] * 3},
        # Ego-Ansicht: Lauf zeigt gerade nach vorne (im Spiel geprüft)
        "firstperson_righthand": {"rotation": [0, 90, 0], "translation": [1.5, 2.5, 0], "scale": [0.5 * s] * 3},
        "firstperson_lefthand": {"rotation": [0, -90, 0], "translation": [1.5, 2.5, 0], "scale": [0.5 * s] * 3},
        "gui": {"rotation": [10, -20, 0], "translation": [0, 0, 0], "scale": [0.9 * s] * 3},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5 * s] * 3},
        "fixed": {"rotation": [0, 0, 0], "scale": [1, 1, 1]},
        "on_shelf": {"rotation": [0, 0, 0], "scale": [1, 1, 1]},
    }


WEAPON_NAMES = {
    "phaser_nx": ("Phasenpistole EM-33 (NX-01)", "EM-33 Phase Pistol (NX-01)"),
    "phaser_tos": ("Handphaser Typ 2 (Originalserie)", "Type 2 Phaser (Original Series)"),
    "phaser_movie": ("Handphaser (Kinofilme)", "Hand Phaser (Movie Era)"),
    "phaser_tng": ("Handphaser Typ 2 (Next Generation)", "Type 2 Phaser (Next Generation)"),
    "phaser_fc": ("Handphaser Typ 2 (Enterprise-E)", "Type 2 Phaser (Enterprise-E)"),
    "rifle_nx": ("Phasengewehr der MACOs (NX-01)", "MACO Phase Rifle (NX-01)"),
    "rifle_tos": ("Phasergewehr (Originalserie)", "Phaser Rifle (Original Series)"),
    "rifle_tng": ("Phasergewehr Typ 3 (Next Generation)", "Type 3 Phaser Rifle (Next Generation)"),
    "rifle_fc": ("Kompressions-Phasergewehr (Enterprise-E)", "Compression Phaser Rifle (Enterprise-E)"),
}


# ---------------------------------------------------------------------------------------------
#  Türen (2 x 2) je Epoche und Art, Schlüsselkarten
# ---------------------------------------------------------------------------------------------

DOOR_ERAS = {
    # Grundfarbe, Rahmen/Fuge, Akzent
    "nx": ((108, 114, 122), (44, 48, 54), (150, 158, 168)),
    "tos": ((176, 72, 54), (92, 34, 28), (214, 120, 70)),
    "refit": ((156, 160, 170), (62, 66, 76), (90, 150, 230)),
    "tng": ((184, 172, 150), (96, 88, 74), (150, 140, 120)),
}
DOOR_KINDS = {"normal": None, "security": (236, 196, 40), "command": (200, 40, 36)}


def tex_door_full(era, kind):
    base, dark, accent = DOOR_ERAS[era]
    rnd = random.Random(zlib.crc32(f"door_{era}_{kind}".encode()))
    img = Image.new("RGBA", (32, 32), base + (255,))
    px = img.load()
    for x in range(32):
        for y in range(32):
            c = base
            if x in (0, 31) or y in (0, 31):
                c = dark                                   # Rahmen
            elif x in (15, 16):
                c = dark                                   # Mittelfuge (Schiebetür)
            elif era == "nx" and y % 6 == 3:
                c = shade(base, -18)[:3]                   # Rippen
            elif era == "tos" and (x in (1, 14, 17, 30)):
                c = shade(base, -30)[:3]                   # Kanten der Flügel
            elif era == "refit" and (abs(x - 15.5) + abs(y - 16) < 6 and abs(x - 15.5) > 1.5):
                c = shade(base, -22)[:3]                   # Raute um die Fuge
            elif era == "tng" and y in (11, 12, 21, 22):
                c = dark                                   # dunkle Querstreifen
            px[x, y] = shade(c, rnd.randint(-4, 4))
    # Akzent: kleine Lichter an der Fuge
    for y in (6, 7, 25, 26):
        px[13, y] = accent + (255,)
        px[18, y] = accent + (255,)
    k = DOOR_KINDS[kind]
    if k:
        for x in range(2, 30):
            if x in (15, 16):
                continue
            for y in (15, 16, 17):
                stripe = kind == "security" and (x + y) % 6 < 3
                px[x, y] = ((30, 30, 30) if stripe else k) + (255,)
        # Kartenleser
        for y in range(9, 12):
            for x in (3, 4):
                px[x, y] = (40, 40, 44, 255)
        px[3, 10] = k + (255,)
    return img


def tex_keycard(color, captain=False):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for x in range(1, 15):
        for y in range(4, 13):
            corner = (x in (1, 14)) and (y in (4, 12))
            if corner:
                continue
            px[x, y] = (232, 232, 228, 255)
    for x in range(1, 15):
        for y in (5, 6):
            px[x, y] = color + (255,)
    for x in range(3, 6):
        for y in range(8, 11):
            px[x, y] = (210, 170, 60, 255)              # Chip
    for x, y in ((10, 8), (11, 9), (12, 10), (10, 10), (11, 11)):
        px[x, y] = (color if not captain else (230, 190, 40)) + (255,)   # Delta-Abzeichen
    if captain:
        for x in range(8, 14, 2):
            px[x, 11] = (230, 190, 40, 255)
    return img


def door_assets(de, en):
    names_de = {"nx": "NX-01", "tos": "Originalserie", "refit": "Kinofilme", "tng": "Next Generation"}
    names_en = {"nx": "NX-01", "tos": "Original Series", "refit": "Movie Era", "tng": "Next Generation"}
    kind_de = {"normal": "Tür", "security": "Sicherheitstür", "command": "Kommandotür"}
    kind_en = {"normal": "Door", "security": "Security Door", "command": "Command Door"}
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    out = []
    for era in DOOR_ERAS:
        frame = b(f"door_{era}_frame")
        fr = Image.new("RGBA", (16, 16), DOOR_ERAS[era][1] + (255,))
        save(noise(fr, 5, zlib.crc32(era.encode())), f"door_{era}_frame")
        for kind in DOOR_KINDS:
            name = f"door_{era}_{kind}"
            save(tex_door_full(era, kind), name)
            img = tex_door_full(era, kind)
            save(img, "item/" + name)
            t = b(name)
            for part in range(4):
                col, row = part % 2, part // 2
                u0, v0 = col * 8, (1 - row) * 8
                front = {"texture": "#d", "uv": [u0, v0, u0 + 8, v0 + 8]}
                back = {"texture": "#d", "uv": [u0 + 8, v0, u0, v0 + 8]}
                closed = {"parent": "minecraft:block/block", "textures": {"particle": t, "d": t, "f": frame},
                          "elements": [{"from": [0, 0, 6], "to": [16, 16, 10], "faces": {
                              "north": front, "south": back, "east": {"texture": "#f"}, "west": {"texture": "#f"},
                              "up": {"texture": "#f"}, "down": {"texture": "#f"}}}]}
                # offen: Flügel in die Wand geschoben, nur der Rand bleibt sichtbar
                jx = [13, 16] if col == 0 else [0, 3]
                elems = [{"from": [jx[0], 0, 6], "to": [jx[1], 16, 10], "faces": {
                    "north": {"texture": "#d", "uv": [u0 + (0 if col == 0 else 6.5), v0, u0 + (1.5 if col == 0 else 8), v0 + 8]},
                    "south": {"texture": "#f"}, "east": {"texture": "#f"}, "west": {"texture": "#f"},
                    "up": {"texture": "#f"}, "down": {"texture": "#f"}}}]
                if row == 1:
                    elems.append(el([0, 14, 6], [16, 16, 10], all6("#f")))
                opened = {"parent": "minecraft:block/block", "textures": {"particle": t, "d": t, "f": frame}, "elements": elems}
                write_json(os.path.join(ASSETS, "models", "block", f"{name}_{part}.json"), closed)
                write_json(os.path.join(ASSETS, "models", "block", f"{name}_{part}_open.json"), opened)
            variants = {}
            for d, r in rot.items():
                for part in range(4):
                    for op in ("false", "true"):
                        m = {"model": b(f"{name}_{part}" + ("_open" if op == "true" else ""))}
                        if r:
                            m["y"] = r
                        variants[f"facing={d},open={op},part={part}"] = m
            write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": variants})
            write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
                       {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MODID}:item/{name}"}})
            write_json(os.path.join(ASSETS, "items", name + ".json"), item_def(f"{MODID}:item/{name}"))
            write_json(os.path.join(DATA, "loot_table", "blocks", name + ".json"), loot(name))
            de[f"block.{MODID}.{name}"] = f"{kind_de[kind]} ({names_de[era]}, 2 x 2)"
            en[f"block.{MODID}.{name}"] = f"{kind_en[kind]} ({names_en[era]}, 2 x 2)"
            out.append(name)
    cards = {"security": ((236, 196, 40), "Schlüsselkarte Sicherheit (gelb)", "Security Keycard (yellow)"),
             "command": ((200, 40, 36), "Schlüsselkarte Kommando (rot)", "Command Keycard (red)"),
             "captain": ((40, 60, 140), "Schlüsselkarte Captain", "Captain's Keycard")}
    for k, (color, nde, nen) in cards.items():
        name = "keycard_" + k
        save(tex_keycard(color, k == "captain"), "item/" + name)
        write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MODID}:item/{name}"}})
        write_json(os.path.join(ASSETS, "items", name + ".json"), item_def(f"{MODID}:item/{name}"))
        de[f"item.{MODID}.{name}"], en[f"item.{MODID}.{name}"] = nde, nen
    de.update({
        f"keycard.{MODID}.security": "Sicherheit", f"keycard.{MODID}.command": "Kommando", f"keycard.{MODID}.captain": "Captain",
        f"tooltip.{MODID}.keycard.security": "Öffnet Sicherheitstüren (gelb)",
        f"tooltip.{MODID}.keycard.command": "Öffnet Kommandotüren (rot)",
        f"tooltip.{MODID}.keycard.captain": "Öffnet alle Türen, auch vom Captain verschlossene",
        f"tooltip.{MODID}.keycard.lock": "Shift + Rechtsklick auf eine Tür: verschließen / öffnen",
        f"message.{MODID}.door.locked": "Tür verschlossen (%s)",
        f"message.{MODID}.door.unlocked": "Tür entriegelt",
        f"message.{MODID}.door.denied_captain": "Vom Captain verschlossen – nur die Captain-Karte öffnet diese Tür.",
        f"message.{MODID}.door.is_locked": "Verschlossen (%s)",
        f"message.{MODID}.door.need_security": "Zugang nur mit Sicherheitskarte (gelb)",
        f"message.{MODID}.door.need_command": "Zugang nur mit Kommandokarte (rot)",
    })
    en.update({
        f"keycard.{MODID}.security": "Security", f"keycard.{MODID}.command": "Command", f"keycard.{MODID}.captain": "Captain",
        f"tooltip.{MODID}.keycard.security": "Opens security doors (yellow)",
        f"tooltip.{MODID}.keycard.command": "Opens command doors (red)",
        f"tooltip.{MODID}.keycard.captain": "Opens every door, including ones the captain locked",
        f"tooltip.{MODID}.keycard.lock": "Shift + right click a door: lock / unlock",
        f"message.{MODID}.door.locked": "Door locked (%s)",
        f"message.{MODID}.door.unlocked": "Door unlocked",
        f"message.{MODID}.door.denied_captain": "Locked by the captain – only the captain's keycard opens this door.",
        f"message.{MODID}.door.is_locked": "Locked (%s)",
        f"message.{MODID}.door.need_security": "Access requires a security keycard (yellow)",
        f"message.{MODID}.door.need_command": "Access requires a command keycard (red)",
    })
    return out



# ---------------------------------------------------------------------------------------------
#  Zweiseitige Wand / Decke (jede Hälfte im Material ihres Raums)
# ---------------------------------------------------------------------------------------------

DUAL_MATERIALS = ["deck_floor", "bridge_deck", "corridor_wall", "ceiling_panel", "bridge_wall", "transporter_wall",
                  "sickbay_wall", "sickbay_floor", "bridge_carpet_teal", "deck_plate", "bridge_wall_dark", "tng_carpet",
                  "tng_wall", "holodeck_grid", "ent_wall", "transporter_platform"]


def dual_assets(de, en):
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}

    def half(fr, to, faces):
        return {"from": fr, "to": to, "faces": faces}

    def wall_half(front):
        z0, z1 = (0, 8) if front else (8, 16)
        outer = "north" if front else "south"
        f = {outer: {"texture": "#t", "cullface": outer},
             "east": {"texture": "#t", "uv": [16 - z1, 0, 16 - z0, 16], "cullface": "east"},
             "west": {"texture": "#t", "uv": [z0, 0, z1, 16], "cullface": "west"},
             "up": {"texture": "#t", "uv": [0, z0, 16, z1], "cullface": "up"},
             "down": {"texture": "#t", "uv": [0, 16 - z1, 16, 16 - z0], "cullface": "down"}}
        return half([0, 0, z0], [16, 16, z1], f)

    def floor_half(top):
        y0, y1 = (8, 16) if top else (0, 8)
        outer = "up" if top else "down"
        f = {outer: {"texture": "#t", "cullface": outer}}
        for side in ("north", "south", "east", "west"):
            f[side] = {"texture": "#t", "uv": [0, 16 - y1, 16, 16 - y0], "cullface": side}
        return half([0, y0, 0], [16, y1, 16], f)

    wall_parts, floor_parts = [], []
    for m in DUAL_MATERIALS:
        t = b(m)
        for front in (True, False):
            n = f"dual_wall_{'front' if front else 'back'}_{m}"
            write_json(os.path.join(ASSETS, "models", "block", n + ".json"),
                       {"parent": "minecraft:block/block", "textures": {"particle": t, "t": t}, "elements": [wall_half(front)]})
            # Eckstück (schräge Wand): vorne Nord- und Westseite, hinten Süd- und Ostseite
            nc = f"dual_wall_corner_{'front' if front else 'back'}_{m}"
            faces = {"north": {"texture": "#t", "cullface": "north"}, "west": {"texture": "#t", "cullface": "west"},
                     "up": {"texture": "#t", "cullface": "up"}} if front else \
                    {"south": {"texture": "#t", "cullface": "south"}, "east": {"texture": "#t", "cullface": "east"},
                     "down": {"texture": "#t", "cullface": "down"}}
            write_json(os.path.join(ASSETS, "models", "block", nc + ".json"),
                       {"parent": "minecraft:block/block", "textures": {"particle": t, "t": t}, "elements": [half([0, 0, 0], [16, 16, 16], faces)]})
            for d, r in rot.items():
                for corner, model in (("false", n), ("true", nc)):
                    mm = {"model": b(model)}
                    if r:
                        mm["y"] = r
                    wall_parts.append({"when": {"facing": d, "corner": corner, "front" if front else "back": m}, "apply": mm})
        for top in (True, False):
            n = f"dual_floor_{'top' if top else 'bottom'}_{m}"
            write_json(os.path.join(ASSETS, "models", "block", n + ".json"),
                       {"parent": "minecraft:block/block", "textures": {"particle": t, "t": t}, "elements": [floor_half(top)]})
            floor_parts.append({"when": {"top" if top else "bottom": m}, "apply": {"model": b(n)}})
    write_json(os.path.join(ASSETS, "blockstates", "dual_wall.json"), {"multipart": wall_parts})
    write_json(os.path.join(ASSETS, "blockstates", "dual_floor.json"), {"multipart": floor_parts})
    # Inventar: beide Hälften zusammen
    write_json(os.path.join(ASSETS, "models", "block", "dual_wall.json"),
               {"parent": "minecraft:block/block", "textures": {"particle": b("corridor_wall"), "a": b("corridor_wall"), "b": b("tng_wall"), "t": b("corridor_wall")},
                "elements": [wall_half(True), dict(wall_half(False), faces={k: dict(v, texture="#b") for k, v in wall_half(False)["faces"].items()})]})
    write_json(os.path.join(ASSETS, "models", "block", "dual_floor.json"),
               {"parent": "minecraft:block/block", "textures": {"particle": b("deck_floor"), "t": b("deck_floor"), "b": b("ceiling_panel")},
                "elements": [floor_half(True), dict(floor_half(False), faces={k: dict(v, texture="#b") for k, v in floor_half(False)["faces"].items()})]})
    for name in ("dual_wall", "dual_floor"):
        write_json(os.path.join(ASSETS, "items", name + ".json"), item_def(b(name)))
        write_json(os.path.join(DATA, "loot_table", "blocks", name + ".json"), loot(name))
    de[f"block.{MODID}.dual_wall"], en[f"block.{MODID}.dual_wall"] = "Zweiseitige Wand", "Two-Sided Wall"
    de[f"block.{MODID}.dual_floor"], en[f"block.{MODID}.dual_floor"] = "Boden/Decke (zweiseitig)", "Floor/Ceiling (two-sided)"



def ceiling_light_assets(de, en):
    """Deckenleuchte: Paneel 1/8 Block unter der Decke, an (hell) und aus (dunkel)."""
    img = tex_ceiling_light()
    off = img.copy()
    px = off.load()
    for x in range(16):
        for y in range(16):
            r, g, b_, a = px[x, y]
            px[x, y] = (int(r * 0.45), int(g * 0.47), int(b_ * 0.5), a)
    save(off, "ceiling_light_off")
    frame = b("ceiling_panel")
    for name, tex in (("ceiling_light", b("ceiling_light")), ("ceiling_light_off", b("ceiling_light_off"))):
        write_json(os.path.join(ASSETS, "models", "block", name + ".json"), {
            "parent": "minecraft:block/block", "textures": {"particle": tex, "l": tex, "f": frame},
            "elements": [{"from": [0, 14, 0], "to": [16, 16, 16], "faces": {
                "down": {"texture": "#l"}, "up": {"texture": "#f", "cullface": "up"},
                "north": {"texture": "#f", "uv": [0, 0, 16, 2]}, "south": {"texture": "#f", "uv": [0, 0, 16, 2]},
                "east": {"texture": "#f", "uv": [0, 0, 16, 2]}, "west": {"texture": "#f", "uv": [0, 0, 16, 2]}}}]})
    write_json(os.path.join(ASSETS, "blockstates", "ceiling_light.json"),
               {"variants": {"lit=true": {"model": b("ceiling_light")}, "lit=false": {"model": b("ceiling_light_off")}}})
    write_json(os.path.join(ASSETS, "items", "ceiling_light.json"), item_def(b("ceiling_light")))
    write_json(os.path.join(DATA, "loot_table", "blocks", "ceiling_light.json"), loot("ceiling_light"))



# ---------------------------------------------------------------------------------------------
#  Uran und Reaktor
# ---------------------------------------------------------------------------------------------

def _speckled(base, spot, seed, n_spots=9, glow=False):
    rnd = random.Random(seed)
    img = noise(new(base), 10, seed)
    px = img.load()
    for _ in range(n_spots):
        x, y = rnd.randrange(1, 14), rnd.randrange(1, 14)
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
            if rnd.random() < 0.85:
                px[x + dx, y + dy] = shade(spot, rnd.randint(-25, 25))
        if glow:
            px[x, y] = shade(spot, 60)
    return img


def tex_item_raw_uranium():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    rnd = random.Random(77)
    for x in range(16):
        for y in range(16):
            d = ((x - 7.5) ** 2 + (y - 8) ** 2) ** 0.5
            if d < 6 + rnd.random():
                px[x, y] = shade((70, 150, 60), rnd.randint(-30, 20))
    for x, y in ((5, 6), (9, 9), (7, 4), (10, 6)):
        px[x, y] = (170, 255, 120, 255)
    return img


def tex_item_uranium_ingot():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(5, 12):
        inset = 1 if y in (5, 11) else 0
        for x in range(2 + inset, 14 - inset):
            c = (90, 200, 70) if y > 6 else (150, 240, 120)
            px[x, y] = shade(c, -20 if y == 11 or x in (2, 13) else 0)
    for x in (5, 9):
        px[x, 7] = (210, 255, 180, 255)
    return img


def tex_reactor_casing():
    img = noise(new((96, 100, 106)), 6, 501)
    px = img.load()
    for i in range(16):
        px[i, 0] = px[i, 15] = px[0, i] = px[15, i] = (60, 62, 66, 255)
    for i in range(2, 14):  # gelb-schwarze Warnstreifen an der Unterkante
        px[i, 13] = ((230, 190, 40) if (i // 2) % 2 == 0 else (40, 40, 40)) + (255,)
    for x, y in ((3, 3), (12, 3), (3, 10), (12, 10)):
        px[x, y] = (150, 154, 160, 255)
    return img


def tex_reactor_glass():
    img = Image.new("RGBA", (16, 16), (90, 220, 110, 70))
    px = img.load()
    for i in range(16):
        px[i, 0] = px[i, 15] = px[0, i] = px[15, i] = (70, 90, 76, 255)
    for i in range(3, 7):
        px[i, i - 1] = (210, 255, 220, 140)
    return img


def tex_reactor_front(lit):
    img = tex_reactor_casing()
    px = img.load()
    for x in range(3, 13):
        for y in range(3, 11):
            px[x, y] = (20, 30, 24, 255)
    core = (120, 255, 110) if lit else (40, 90, 40)
    for x in range(6, 10):
        for y in range(5, 9):
            px[x, y] = core + (255,)
    for x in (4, 11):
        px[x, 4] = ((230, 190, 40) if lit else (90, 80, 40)) + (255,)
    return img


def uranium_reactor_assets(de, en):
    save(_speckled((125, 125, 125), (80, 200, 70), 601, glow=True), "uranium_ore")
    save(_speckled((70, 70, 78), (80, 200, 70), 602, glow=True), "deepslate_uranium_ore")
    save(tex_item_raw_uranium(), "item/raw_uranium")
    save(tex_item_uranium_ingot(), "item/uranium_ingot")
    save(tex_reactor_casing(), "reactor_casing")
    save(tex_reactor_glass(), "reactor_glass")
    save(tex_reactor_front(False), "reactor_front")
    save(tex_reactor_front(True), "reactor_front_lit")
    for name in ("uranium_ore", "deepslate_uranium_ore", "reactor_casing"):
        write_json(os.path.join(ASSETS, "models", "block", name + ".json"), {"parent": "minecraft:block/cube_all", "textures": {"all": b(name)}})
        write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": {"": {"model": b(name)}}})
        write_json(os.path.join(ASSETS, "items", name + ".json"), item_def(b(name)))
    write_json(os.path.join(ASSETS, "models", "block", "reactor_glass.json"),
               {"parent": "minecraft:block/cube_all", "render_type": "minecraft:translucent", "textures": {"all": b("reactor_glass")}})
    write_json(os.path.join(ASSETS, "blockstates", "reactor_glass.json"), {"variants": {"": {"model": b("reactor_glass")}}})
    write_json(os.path.join(ASSETS, "items", "reactor_glass.json"), item_def(b("reactor_glass")))
    for lit in (False, True):
        n = "reactor_controller" + ("_lit" if lit else "")
        write_json(os.path.join(ASSETS, "models", "block", n + ".json"), {"parent": "minecraft:block/orientable",
            "textures": {"front": b("reactor_front_lit" if lit else "reactor_front"), "side": b("reactor_casing"), "top": b("reactor_casing")}})
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    write_json(os.path.join(ASSETS, "blockstates", "reactor_controller.json"), {"variants": {
        f"facing={d},lit={str(l).lower()}": dict({"model": b("reactor_controller" + ("_lit" if l else ""))}, **({"y": r} if r else {}))
        for d, r in rot.items() for l in (False, True)}})
    write_json(os.path.join(ASSETS, "items", "reactor_controller.json"), item_def(b("reactor_controller")))
    for it in ("raw_uranium", "uranium_ingot"):
        write_json(os.path.join(ASSETS, "models", "item", it + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MODID}:item/{it}"}})
        write_json(os.path.join(ASSETS, "items", it + ".json"), item_def(f"{MODID}:item/{it}"))
    # Loot: Erze mit Behutsamkeit/Glück, Rest droppt sich selbst
    for ore in ("uranium_ore", "deepslate_uranium_ore"):
        write_json(os.path.join(DATA, "loot_table", "blocks", ore + ".json"), {
            "type": "minecraft:block", "random_sequence": f"{MODID}:blocks/{ore}",
            "pools": [{"rolls": 1.0, "entries": [{"type": "minecraft:alternatives", "children": [
                {"type": "minecraft:item", "name": f"{MODID}:{ore}", "conditions": [{"condition": "minecraft:match_tool", "predicate": {
                    "predicates": {"minecraft:enchantments": [{"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]},
                {"type": "minecraft:item", "name": f"{MODID}:raw_uranium", "functions": [
                    {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                    {"function": "minecraft:explosion_decay"}]}]}]}]})
    for n in ("reactor_casing", "reactor_glass", "reactor_controller"):
        write_json(os.path.join(DATA, "loot_table", "blocks", n + ".json"), loot(n))
    # Rezepte: Schmelzen/Hochofen, Reaktorteile
    for kind in ("smelting", "blasting"):
        for src in ("raw_uranium", "uranium_ore", "deepslate_uranium_ore"):
            write_json(os.path.join(DATA, "recipe", f"uranium_ingot_from_{kind}_{src}.json"), {
                "type": f"minecraft:{kind}", "experience": 1.0, "group": "uranium_ingot",
                "cookingtime": 200 if kind == "smelting" else 100,
                "ingredient": f"{MODID}:{src}", "result": {"id": f"{MODID}:uranium_ingot"}})
    write_json(os.path.join(DATA, "recipe", "reactor_casing.json"), {"type": "minecraft:crafting_shaped", "category": "building",
        "key": {"I": "minecraft:iron_ingot", "U": f"{MODID}:uranium_ingot", "C": "minecraft:copper_ingot"},
        "pattern": ["ICI", "CUC", "ICI"], "result": {"id": f"{MODID}:reactor_casing", "count": 8}})
    write_json(os.path.join(DATA, "recipe", "reactor_glass.json"), {"type": "minecraft:crafting_shaped", "category": "building",
        "key": {"G": "minecraft:glass", "R": f"{MODID}:reactor_casing"},
        "pattern": ["GRG", "R R", "GRG"], "result": {"id": f"{MODID}:reactor_glass", "count": 4}})
    write_json(os.path.join(DATA, "recipe", "reactor_controller.json"), {"type": "minecraft:crafting_shaped", "category": "redstone",
        "key": {"R": f"{MODID}:reactor_casing", "D": "minecraft:diamond", "S": "minecraft:redstone_block", "U": f"{MODID}:uranium_ingot"},
        "pattern": ["RDR", "USU", "RDR"], "result": {"id": f"{MODID}:reactor_controller"}})
    # Weltgenerierung: wie Lapis (Trapez -32..32), etwas seltener
    write_json(os.path.join(DATA, "worldgen", "configured_feature", "ore_uranium.json"), {"type": "minecraft:ore", "config": {
        "discard_chance_on_air_exposure": 0.0, "size": 6, "targets": [
            {"state": {"Name": f"{MODID}:uranium_ore"}, "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"}},
            {"state": {"Name": f"{MODID}:deepslate_uranium_ore"}, "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"}}]}})
    write_json(os.path.join(DATA, "worldgen", "placed_feature", "ore_uranium.json"), {"feature": f"{MODID}:ore_uranium", "placement": [
        {"type": "minecraft:count", "count": 2}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:trapezoid", "min_inclusive": {"absolute": -32}, "max_inclusive": {"absolute": 32}}},
        {"type": "minecraft:biome"}]})
    write_json(os.path.join(DATA, "forge", "biome_modifier", "add_uranium_ore.json"), {"type": "forge:add_features",
        "biomes": "#minecraft:is_overworld", "features": f"{MODID}:ore_uranium", "step": "underground_ores"})
    # Abbau: Eisenspitzhacke
    write_json(os.path.join(ROOT, "data", "minecraft", "tags", "block", "needs_iron_tool.json"),
               {"values": [f"{MODID}:uranium_ore", f"{MODID}:deepslate_uranium_ore"]})
    de.update({
        f"block.{MODID}.uranium_ore": "Uranerz", f"block.{MODID}.deepslate_uranium_ore": "Tiefenschiefer-Uranerz",
        f"item.{MODID}.raw_uranium": "Roh-Uran", f"item.{MODID}.uranium_ingot": "Uranbarren",
        f"block.{MODID}.reactor_casing": "Reaktorhülle", f"block.{MODID}.reactor_glass": "Reaktorglas",
        f"block.{MODID}.reactor_controller": "Reaktor-Steuerblock",
        f"gui.{MODID}.reactor": "Kernreaktor", f"gui.{MODID}.reactor.invalid": "Kein gültiger",
        f"gui.{MODID}.reactor.invalid2": "Würfel (3-8)", f"gui.{MODID}.reactor.slots": "%s Plätze",
        f"message.{MODID}.ship.no_use": "Das lässt sich im Flug nicht bedienen.",
    })
    en.update({
        f"block.{MODID}.uranium_ore": "Uranium Ore", f"block.{MODID}.deepslate_uranium_ore": "Deepslate Uranium Ore",
        f"item.{MODID}.raw_uranium": "Raw Uranium", f"item.{MODID}.uranium_ingot": "Uranium Ingot",
        f"block.{MODID}.reactor_casing": "Reactor Casing", f"block.{MODID}.reactor_glass": "Reactor Glass",
        f"block.{MODID}.reactor_controller": "Reactor Controller",
        f"gui.{MODID}.reactor": "Nuclear Reactor", f"gui.{MODID}.reactor.invalid": "No valid",
        f"gui.{MODID}.reactor.invalid2": "cube (3-8)", f"gui.{MODID}.reactor.slots": "%s slots",
        f"message.{MODID}.ship.no_use": "That cannot be used in flight.",
    })



# ---------------------------------------------------------------------------------------------
#  Luftschleusen und Weltraum-Rohstoffe (Asteroiden: Dilithium, Antimaterie, Deuterium)
# ---------------------------------------------------------------------------------------------

def tex_airlock(upper, small=False):
    img = noise(new((170, 174, 180)), 6, 811 + upper + 2 * small)
    px = img.load()
    for i in range(16):
        px[i, 0] = px[i, 15] = px[0, i] = px[15, i] = (90, 94, 100, 255)
        px[1, i] = px[14, i] = (120, 124, 130, 255)
    if upper:
        for x in range(4, 12):  # Bullauge
            for y in range(4, 11):
                if (x - 7.5) ** 2 + (y - 7.5) ** 2 < 13:
                    px[x, y] = (40, 70, 110, 255) if (x + y) % 5 else (120, 170, 220, 255)
        for x in range(2, 14):
            px[x, 14] = (60, 64, 70, 255)
    else:
        for x in range(2, 14):  # gelb-schwarze Warnstreifen
            for y in range(10, 14):
                px[x, y] = ((230, 190, 40) if ((x + y) // 2) % 2 == 0 else (35, 35, 35)) + (255,)
        px[7, 4] = px[8, 4] = (200, 40, 36, 255) if not small else (60, 200, 80, 255)  # Statuslicht
        for x in range(2, 14):
            px[x, 1] = (60, 64, 70, 255)
    return img


def tex_airlock_item(small):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    x0, x1 = (5, 11) if small else (2, 14)
    for x in range(x0, x1):
        for y in range(1, 15):
            edge = x in (x0, x1 - 1) or y in (1, 14) or (not small and x in (7, 8))
            px[x, y] = (90, 94, 100, 255) if edge else (175, 178, 184, 255)
    for x in range(x0 + 1, x1 - 1):
        for y in range(11, 14):
            px[x, y] = ((230, 190, 40) if ((x + y) // 2) % 2 == 0 else (35, 35, 35)) + (255,)
    for cx in ((8,) if small else (5, 11)):
        for x, y in ((cx - 1, 4), (cx, 4), (cx - 1, 5), (cx, 5)):
            if x0 < x < x1 - 1:
                px[x, y] = (60, 100, 150, 255)
    return img


def tex_crystal_item(color, seed):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    rnd = random.Random(seed)
    for x in range(16):
        for y in range(16):
            if abs(x - 7.5) * 1.6 + abs(y - 8) * 0.9 < 7:
                px[x, y] = shade(color, rnd.randint(-30, 25) + (30 if x < 8 and y < 8 else 0))
    px[6, 5] = px[7, 4] = (255, 255, 255, 255)
    return img


def tex_antimatter_item():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for x in range(16):
        for y in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 3:
                px[x, y] = (255, 230, 255, 255)
            elif d < 5.5:
                px[x, y] = (210, 60, 230, 255) if (x + y) % 3 else (150, 30, 180, 255)
            elif d < 6.5 and (x + y) % 2 == 0:
                px[x, y] = (120, 40, 160, 200)
    return img


def tex_pod_item():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(2, 14):
        for x in range(5, 11):
            edge = x in (5, 10) or y in (2, 13)
            px[x, y] = (110, 115, 122, 255) if edge else ((210, 60, 230, 255) if 4 < y < 11 else (150, 155, 160, 255))
    for x in range(4, 12):
        px[x, 2] = px[x, 13] = (80, 84, 90, 255)
    return img


def tex_deuterium_item():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(3, 15):
        for x in range(4, 12):
            edge = x in (4, 11) or y in (3, 14)
            px[x, y] = (200, 205, 210, 255) if edge else ((90, 170, 255, 255) if y > 6 else (220, 240, 255, 255))
    for x in range(6, 10):
        px[x, 2] = (120, 125, 130, 255)
    return img


def tex_antimatter_cluster():
    img = noise(new((40, 20, 50)), 10, 903)
    px = img.load()
    rnd = random.Random(904)
    for _ in range(14):
        x, y = rnd.randrange(1, 15), rnd.randrange(1, 15)
        px[x, y] = (230, 120, 255, 255)
        for dx, dy in ((1, 0), (0, 1), (-1, 0), (0, -1)):
            if 0 <= x + dx < 16 and 0 <= y + dy < 16 and rnd.random() < 0.6:
                px[x + dx, y + dy] = (170, 50, 210, 255)
    return img


def space_assets(de, en):
    # --- Luftschleusen ---
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    for name, width in (("airlock", 2), ("airlock_small", 1)):
        small = width == 1
        save(tex_airlock(0, small), f"{name}_lower")
        save(tex_airlock(1, small), f"{name}_upper")
        save(tex_airlock_item(small), "item/" + name)
        frame = b("airlock_frame")
        for h in (0, 1):
            t = b(f"{name}_{'upper' if h else 'lower'}")
            for d in (0, 1):
                z0, z1 = (0, 2) if d == 0 else (14, 16)
                closed = {"parent": "minecraft:block/block", "textures": {"particle": t, "t": t, "f": frame},
                          "elements": [el([0, 0, z0], [16, 16, z1], {"north": "#t", "south": "#t", "east": "#f", "west": "#f", "up": "#f", "down": "#f"})]}
                write_json(os.path.join(ASSETS, "models", "block", f"{name}_closed_{h}_{d}.json"), closed)
                for edge in (("both",) if small else ("east", "west")):
                    elems = []
                    if edge in ("east", "both"):
                        elems.append(el([14, 0, z0], [16, 16, z1], all6("#f")))
                    if edge in ("west", "both"):
                        elems.append(el([0, 0, z0], [2, 16, z1], all6("#f")))
                    if h == 1:
                        elems.append(el([0, 14, z0], [16, 16, z1], all6("#f")))
                    write_json(os.path.join(ASSETS, "models", "block", f"{name}_open_{edge}_{h}_{d}.json"),
                               {"parent": "minecraft:block/block", "textures": {"particle": t, "f": frame}, "elements": elems})
        variants = {}
        for fac, r in rot.items():
            for part in range(width * 4):
                w, hh, dd = (part % 2, (part // 2) % 2, part // 4) if width == 2 else (0, part % 2, part // 2)
                edge = "both" if small else ("east" if w == 0 else "west")
                for op in ("none", "front", "back"):
                    is_open = op != "none" and ((op == "front") == (dd == 0))
                    model = f"{name}_open_{edge}_{hh}_{dd}" if is_open else f"{name}_closed_{hh}_{dd}"
                    for pw in ("false", "true"):
                        m = {"model": b(model)}
                        if r:
                            m["y"] = r
                        variants[f"facing={fac},open={op},part={part},powered={pw}"] = m
        write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": variants})
        write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MODID}:item/{name}"}})
        write_json(os.path.join(ASSETS, "items", name + ".json"), item_def(f"{MODID}:item/{name}"))
        write_json(os.path.join(DATA, "loot_table", "blocks", name + ".json"), loot(name))
    save(noise(new((96, 100, 106)), 5, 812), "airlock_frame")
    write_json(os.path.join(DATA, "recipe", "airlock.json"), {"type": "minecraft:crafting_shaped", "category": "redstone",
        "key": {"I": "minecraft:iron_ingot", "G": "minecraft:glass_pane", "R": "minecraft:redstone", "D": "minecraft:iron_door"},
        "pattern": ["IGI", "DRD", "IGI"], "result": {"id": f"{MODID}:airlock"}})
    write_json(os.path.join(DATA, "recipe", "airlock_small.json"), {"type": "minecraft:crafting_shaped", "category": "redstone",
        "key": {"I": "minecraft:iron_ingot", "G": "minecraft:glass_pane", "R": "minecraft:redstone", "D": "minecraft:iron_door"},
        "pattern": ["IGI", "IDI", "IRI"], "result": {"id": f"{MODID}:airlock_small"}})

    # --- Asteroiden-Rohstoffe ---
    save(_speckled((60, 60, 68), (240, 120, 200), 905, n_spots=7, glow=True), "dilithium_ore")
    save(tex_antimatter_cluster(), "antimatter_cluster")
    ice = noise(new((150, 200, 245)), 12, 906)
    ipx = ice.load()
    for x, y in ((3, 4), (10, 2), (12, 11), (5, 12), (8, 7)):
        ipx[x, y] = (230, 245, 255, 255)
    save(ice, "deuterium_ice")
    for n in ("dilithium_ore", "antimatter_cluster", "deuterium_ice"):
        write_json(os.path.join(ASSETS, "models", "block", n + ".json"), {"parent": "minecraft:block/cube_all", "textures": {"all": b(n)}})
        write_json(os.path.join(ASSETS, "blockstates", n + ".json"), {"variants": {"": {"model": b(n)}}})
        write_json(os.path.join(ASSETS, "items", n + ".json"), item_def(b(n)))
    save(tex_crystal_item((235, 110, 200), 907), "item/dilithium_crystal")
    save(tex_antimatter_item(), "item/antimatter")
    save(tex_deuterium_item(), "item/deuterium")
    save(tex_pod_item(), "item/antimatter_pod")
    for it in ("dilithium_crystal", "antimatter", "deuterium", "antimatter_pod"):
        write_json(os.path.join(ASSETS, "models", "item", it + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MODID}:item/{it}"}})
        write_json(os.path.join(ASSETS, "items", it + ".json"), item_def(f"{MODID}:item/{it}"))

    def ore_loot(block, item, lo, hi):
        drop = {"type": "minecraft:item", "name": f"{MODID}:{item}", "functions": [
            {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}},
            {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
            {"function": "minecraft:explosion_decay"}]}
        write_json(os.path.join(DATA, "loot_table", "blocks", block + ".json"), {
            "type": "minecraft:block", "random_sequence": f"{MODID}:blocks/{block}",
            "pools": [{"rolls": 1.0, "entries": [{"type": "minecraft:alternatives", "children": [
                {"type": "minecraft:item", "name": f"{MODID}:{block}", "conditions": [{"condition": "minecraft:match_tool", "predicate": {
                    "predicates": {"minecraft:enchantments": [{"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]},
                drop]}]}]})
    ore_loot("dilithium_ore", "dilithium_crystal", 1, 1)
    ore_loot("antimatter_cluster", "antimatter", 1, 2)
    ore_loot("deuterium_ice", "deuterium", 2, 4)

    # Rezepte: Antimaterie-Kapsel und Warpkerne (je neuer, desto aufwendiger)
    def shaped(name, pattern, key, count=1, cat="misc"):
        write_json(os.path.join(DATA, "recipe", name + ".json"), {"type": "minecraft:crafting_shaped", "category": cat,
            "key": key, "pattern": pattern, "result": {"id": f"{MODID}:{name}", "count": count}})
    M = MODID
    shaped("antimatter_pod", [" I ", "GAG", " I "], {"I": "minecraft:iron_ingot", "G": "minecraft:glass", "A": f"{M}:antimatter"}, 2)
    shaped("warp_core_nx", ["IDI", "PRP", "IDI"], {"I": "minecraft:iron_block", "D": f"{M}:deuterium", "P": f"{M}:antimatter_pod",
           "R": "minecraft:redstone_block"}, 2, "redstone")
    shaped("warp_core", ["IDI", "CPC", "IDI"], {"I": "minecraft:iron_block", "D": f"{M}:deuterium", "C": f"{M}:dilithium_crystal",
           "P": f"{M}:antimatter_pod"}, 2, "redstone")
    shaped("warp_core_tng", ["CDC", "PGP", "CDC"], {"C": f"{M}:dilithium_crystal", "D": f"{M}:deuterium", "P": f"{M}:antimatter_pod",
           "G": "minecraft:gold_block"}, 2, "redstone")
    shaped("warp_core_e", ["CDC", "PBP", "CDC"], {"C": f"{M}:dilithium_crystal", "D": "minecraft:diamond", "P": f"{M}:antimatter_pod",
           "B": "minecraft:diamond_block"}, 2, "redstone")
    write_json(os.path.join(ROOT, "data", "minecraft", "tags", "block", "needs_iron_tool.json"),
               {"values": [f"{M}:uranium_ore", f"{M}:deepslate_uranium_ore", f"{M}:dilithium_ore", f"{M}:antimatter_cluster"]})

    de.update({
        f"block.{M}.airlock": "Luftschleuse (2 x 2 x 2)", f"block.{M}.airlock_small": "Luftschleuse (1 x 2 x 2)",
        f"block.{M}.dilithium_ore": "Dilithium-Erz", f"block.{M}.antimatter_cluster": "Antimaterie-Einschluss",
        f"block.{M}.deuterium_ice": "Deuterium-Eis",
        f"item.{M}.dilithium_crystal": "Dilithiumkristall", f"item.{M}.antimatter": "Antimaterie",
        f"item.{M}.deuterium": "Deuterium-Kanister", f"item.{M}.antimatter_pod": "Antimaterie-Kapsel",
        f"message.{M}.space.enter": "Weltraum erreicht",
        f"message.{M}.space.leave": "Wiedereintritt in die Atmosphäre",
    })
    en.update({
        f"block.{M}.airlock": "Airlock (2 x 2 x 2)", f"block.{M}.airlock_small": "Airlock (1 x 2 x 2)",
        f"block.{M}.dilithium_ore": "Dilithium Ore", f"block.{M}.antimatter_cluster": "Antimatter Inclusion",
        f"block.{M}.deuterium_ice": "Deuterium Ice",
        f"item.{M}.dilithium_crystal": "Dilithium Crystal", f"item.{M}.antimatter": "Antimatter",
        f"item.{M}.deuterium": "Deuterium Canister", f"item.{M}.antimatter_pod": "Antimatter Pod",
        f"message.{M}.space.enter": "Space reached",
        f"message.{M}.space.leave": "Re-entering the atmosphere",
    })


# ---------------------------------------------------------------------------------------------
#  Raumanzug und Schiffsteil-Fertigung
# ---------------------------------------------------------------------------------------------

def space_suit_texture():
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()
    white = (225, 228, 232)
    for f, r in BODY.items():
        fill(px, r, white, seed_of("suit", f), 5)
    for f, r in ARM.items():
        fill(px, r, white, seed_of("suit", "a", f), 5)
    rows(px, BODY, 0, 1, (200, 120, 40))            # orangefarbener Kragenring
    rows(px, ARM, 0, 1, (200, 120, 40))
    rows(px, ARM, 10, 11, (90, 94, 100))            # Handschuhe
    rows(px, BODY, 9, 9, (90, 94, 100))             # Gürtel
    fx, fy = BODY["front"][0], BODY["front"][1]
    for x, y in ((2, 3), (3, 3), (2, 4), (3, 4)):    # Brustkontrollen
        px[fx + x, fy + y] = (60, 140, 220, 255)
    px[fx + 5, fy + 3] = (220, 60, 60, 255)
    px[fx + 5, fy + 4] = (80, 220, 80, 255)
    bx, by = BODY["back"][0], BODY["back"][1]        # Tornister mit Düsen
    for x in range(1, 7):
        for y in range(1, 9):
            px[bx + x, by + y] = (120, 124, 130, 255) if x in (1, 6) or y in (1, 8) else (160, 164, 170, 255)
    for x in (2, 5):
        px[bx + x, by + 10] = px[bx + x, by + 11] = (60, 160, 255, 255)
    return img


def icon_space_suit():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(2, 15):
        for x in range(2, 14):
            body = 4 <= x <= 11
            arm = (x in (2, 3, 12, 13)) and 3 <= y <= 10
            if body or arm:
                px[x, y] = (225, 228, 232, 255)
    for x in range(4, 12):
        px[x, 2] = (200, 120, 40, 255)
        px[x, 11] = (90, 94, 100, 255)
    px[6, 5] = px[7, 5] = (60, 140, 220, 255)
    px[9, 5] = (220, 60, 60, 255)
    for x in (2, 3, 12, 13):
        px[x, 10] = (90, 94, 100, 255)
    for y in range(2, 15):
        for x in range(2, 14):
            if px[x, y][3] and (px[x - 1, y][3] == 0 or px[x + 1, y][3] == 0):
                px[x, y] = shade(px[x, y][:3], -40)
    return img


def tex_assembly_front(lit):
    img = noise(new((150, 160, 172)), 6, 921)
    px = img.load()
    for i in range(16):
        px[i, 0] = px[i, 15] = px[0, i] = px[15, i] = (80, 86, 94, 255)
    for x in range(3, 13):                          # Sichtfenster mit Greifarm
        for y in range(3, 10):
            px[x, y] = (30, 40, 55, 255)
    for y in range(3, 7):
        px[8, y] = (200, 200, 205, 255)
    px[7, 7] = px[9, 7] = (200, 200, 205, 255)
    if lit:
        for x in range(4, 12):
            px[x, 9] = (90, 220, 255, 255)
        px[8, 8] = (255, 240, 150, 255)
    for x in range(3, 13):                          # Bedienleiste
        px[x, 12] = (60, 64, 70, 255)
    px[4, 12] = (80, 220, 80, 255) if lit else (40, 90, 40, 255)
    px[6, 12] = (220, 180, 40, 255)
    return img


def tex_assembly_side():
    img = noise(new((140, 150, 162)), 6, 922)
    px = img.load()
    for i in range(16):
        px[i, 0] = px[i, 15] = px[0, i] = px[15, i] = (80, 86, 94, 255)
    for y in range(4, 12, 2):
        for x in range(3, 13):
            px[x, y] = (95, 100, 108, 255)
    return img


def tex_assembly_top():
    img = noise(new((130, 140, 152)), 6, 923)
    px = img.load()
    for i in range(16):
        px[i, 0] = px[i, 15] = px[0, i] = px[15, i] = (80, 86, 94, 255)
    for x in range(3, 13):
        for y in range(3, 13):
            if x in (3, 12) or y in (3, 12):
                px[x, y] = (230, 190, 40, 255) if (x + y) % 2 == 0 else (40, 40, 40, 255)
    return img


def suit_assembly_assets(de, en):
    M = MODID
    tex_dir = os.path.join(ASSETS, "textures", "entity", "equipment", "humanoid")
    os.makedirs(tex_dir, exist_ok=True)
    space_suit_texture().save(os.path.join(tex_dir, "space_suit.png"))
    write_json(os.path.join(ASSETS, "equipment", "space_suit.json"), {"layers": {"humanoid": [{"texture": f"{M}:space_suit"}]}})
    save(icon_space_suit(), "item/space_suit")
    write_json(os.path.join(ASSETS, "models", "item", "space_suit.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{M}:item/space_suit"}})
    write_json(os.path.join(ASSETS, "items", "space_suit.json"), item_def(f"{M}:item/space_suit"))
    write_json(os.path.join(DATA, "recipe", "space_suit.json"), {"type": "minecraft:crafting_shaped", "category": "equipment",
        "key": {"L": "minecraft:leather", "G": "minecraft:glass", "I": "minecraft:iron_ingot", "D": "minecraft:diamond",
                "R": "minecraft:redstone_block", "F": "minecraft:feather"},
        "pattern": ["LGL", "IDI", "FRF"], "result": {"id": f"{M}:space_suit"}})

    save(tex_assembly_front(False), "assembly_front")
    save(tex_assembly_front(True), "assembly_front_lit")
    save(tex_assembly_side(), "assembly_side")
    save(tex_assembly_top(), "assembly_top")
    for lit in (False, True):
        n = "ship_part_assembly" + ("_lit" if lit else "")
        write_json(os.path.join(ASSETS, "models", "block", n + ".json"), {"parent": "minecraft:block/orientable",
            "textures": {"front": b("assembly_front_lit" if lit else "assembly_front"), "side": b("assembly_side"), "top": b("assembly_top")}})
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    write_json(os.path.join(ASSETS, "blockstates", "ship_part_assembly.json"), {"variants": {
        f"facing={d},lit={str(l).lower()}": dict({"model": b("ship_part_assembly" + ("_lit" if l else ""))}, **({"y": r} if r else {}))
        for d, r in rot.items() for l in (False, True)}})
    write_json(os.path.join(ASSETS, "items", "ship_part_assembly.json"), item_def(b("ship_part_assembly")))
    write_json(os.path.join(DATA, "loot_table", "blocks", "ship_part_assembly.json"), loot("ship_part_assembly"))
    write_json(os.path.join(DATA, "recipe", "ship_part_assembly.json"), {"type": "minecraft:crafting_shaped", "category": "redstone",
        "key": {"I": "minecraft:iron_ingot", "D": "minecraft:diamond", "G": "minecraft:glass"},
        "pattern": ["IGI", "DGD", "III"], "result": {"id": f"{M}:ship_part_assembly"}})

    de.update({
        f"item.{M}.space_suit": "Raumanzug",
        f"tooltip.{M}.suit": "Angezogen: fliegen wie im Kreativmodus (doppelt springen). Fliegen verbraucht Ladung – laden an der Ladestation.",
        f"message.{M}.suit.empty": "Raumanzug leer – an der Ladestation aufladen!",
        f"block.{M}.ship_part_assembly": "Schiffsteil-Fertigung",
        f"gui.{M}.assembly.choose": "Rechts einen Block wählen",
        f"gui.{M}.assembly.no_power": "Kein Strom",
        f"gui.{M}.assembly.cost": "%s s, %s FE/t",
    })
    en.update({
        f"item.{M}.space_suit": "Space Suit",
        f"tooltip.{M}.suit": "Worn: fly like in creative mode (double jump). Flying uses charge – recharge at a charging station.",
        f"message.{M}.suit.empty": "Space suit empty – recharge it at a charging station!",
        f"block.{M}.ship_part_assembly": "Ship Part Assembly",
        f"gui.{M}.assembly.choose": "Pick a block on the right",
        f"gui.{M}.assembly.no_power": "No power",
        f"gui.{M}.assembly.cost": "%s s, %s FE/t",
    })


def sound_assets(de, en):
    """sounds.json und Untertitel für die Waffensounds (die .ogg erzeugt tools/generate_sounds.py)."""
    names = {
        "phaser_hand_nx": ("Phasenpistole feuert", "Phase pistol fires"),
        "phaser_hand_tos": ("Phaser feuert", "Phaser fires"),
        "phaser_hand_tng": ("Phaser feuert", "Phaser fires"),
        "phaser_rifle": ("Phasergewehr feuert", "Phaser rifle fires"),
        "phaser_ship_nx": ("Phasenkanone feuert", "Phase cannon fires"),
        "phaser_ship_tos": ("Schiffsphaser feuern", "Ship phasers fire"),
        "phaser_ship_refit": ("Schiffsphaser feuern", "Ship phasers fire"),
        "phaser_ship_tng": ("Phaserstreifen feuert", "Phaser array fires"),
        "torpedo_photon": ("Photonentorpedo abgefeuert", "Photon torpedo launched"),
        "torpedo_quantum": ("Quantentorpedo abgefeuert", "Quantum torpedo launched"),
        "red_alert": ("Alarmstufe Rot", "Red alert"),
    }
    data = {}
    for n, (d, e) in names.items():
        data[n] = {"sounds": [{"name": f"{MODID}:{n}"}], "subtitle": f"subtitles.{MODID}.{n}"}
        de[f"subtitles.{MODID}.{n}"] = d
        en[f"subtitles.{MODID}.{n}"] = e
    write_json(os.path.join(ASSETS, "sounds.json"), data)


# ---------------------------------------------------------------------------------------------
#  Energieverteilung, Alarmleuchte, Schilde
# ---------------------------------------------------------------------------------------------

def tex_console_front(bars, accent):
    img = noise(new((70, 74, 82)), 5, 931 + len(bars))
    px = img.load()
    for i in range(16):
        px[i, 0] = px[i, 15] = px[0, i] = px[15, i] = (40, 42, 48, 255)
    for x in range(2, 14):
        for y in range(2, 11):
            px[x, y] = (12, 14, 20, 255)
    for k, (h, c) in enumerate(bars):  # Balkenanzeige
        x = 3 + k * 2
        for y in range(10 - h, 10):
            px[x, y] = c + (255,)
    for x in range(2, 14):
        px[x, 12] = accent + (255,)
    px[3, 13] = (220, 60, 60, 255)
    px[5, 13] = (60, 200, 80, 255)
    return img


def tex_shield_emitter(tier):
    img = noise(new((90, 96, 108)), 6, 940 + tier)
    px = img.load()
    for i in range(16):
        px[i, 0] = px[i, 15] = px[0, i] = px[15, i] = (55, 58, 66, 255)
    glow = [(90, 160, 255), (110, 180, 255), (80, 200, 255), (120, 200, 255), (150, 210, 255), (180, 225, 255)][tier - 1]
    for x in range(16):
        for y in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 2.5 + tier * 0.35:
                px[x, y] = shade(glow, 30)
            elif d < 4 + tier * 0.4:
                px[x, y] = shade(glow, -40)
    for k in range(tier):  # Stufe als Punkte
        px[2 + 2 * k, 13] = (230, 230, 240, 255)
    return img


def tex_alarm(lit):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for x in range(16):
        for y in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 7.5:
                base = (255, 60, 40) if lit else (110, 20, 18)
                px[x, y] = shade(base, int(40 - d * 8) if lit else -int(d * 3))
    for x in range(16):
        px[x, 0] = px[x, 15] = (50, 50, 55, 255)
        px[0, x] = px[15, x] = (50, 50, 55, 255)
    return img


def defense_assets(de, en):
    M = MODID
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    side = b("reactor_casing")
    # Konsolen (vorne Anzeige, sonst Metall)
    save(tex_console_front([(6, (230, 120, 40)), (4, (230, 190, 40)), (7, (90, 200, 90)), (3, (90, 160, 255)), (5, (200, 200, 200))],
                           (230, 140, 40)), "power_console_front")
    save(tex_console_front([(7, (90, 160, 255)), (7, (90, 160, 255)), (5, (90, 160, 255)), (6, (90, 160, 255)), (7, (90, 160, 255))],
                           (80, 150, 255)), "shield_console_front")
    for n in ("power_console", "shield_console"):
        write_json(os.path.join(ASSETS, "models", "block", n + ".json"), {"parent": "minecraft:block/orientable",
            "textures": {"front": b(n + "_front"), "side": side, "top": side}})
        write_json(os.path.join(ASSETS, "blockstates", n + ".json"), {"variants": {
            f"facing={d}": dict({"model": b(n)}, **({"y": r} if r else {})) for d, r in rot.items()}})
        write_json(os.path.join(ASSETS, "items", n + ".json"), item_def(b(n)))
        write_json(os.path.join(DATA, "loot_table", "blocks", n + ".json"), loot(n))
    # Alarmleuchte (an der Wand, Lampe zeigt in Blickrichtung)
    save(tex_alarm(False), "alarm_light")
    save(tex_alarm(True), "alarm_light_lit")
    for lit in (False, True):
        n = "alarm_light" + ("_lit" if lit else "")
        t = b(n)
        write_json(os.path.join(ASSETS, "models", "block", n + ".json"), {"parent": "minecraft:block/block",
            "textures": {"particle": t, "l": t, "m": b("reactor_casing")},
            "elements": [el([4, 3, 14], [12, 13, 16], all6("#m")), el([5, 4, 11], [11, 12, 14], all6("#l"))]})
    write_json(os.path.join(ASSETS, "blockstates", "alarm_light.json"), {"variants": {
        f"facing={d},lit={str(l).lower()}": dict({"model": b("alarm_light" + ("_lit" if l else ""))}, **({"y": r} if r else {}))
        for d, r in rot.items() for l in (False, True)}})
    write_json(os.path.join(ASSETS, "items", "alarm_light.json"), item_def(b("alarm_light_lit")))
    write_json(os.path.join(DATA, "loot_table", "blocks", "alarm_light.json"), loot("alarm_light"))
    # Schildemitter je Schiff
    names = {"tos": ("NCC-1701", "NCC-1701"), "refit": ("NCC-1701-A", "NCC-1701-A"), "excelsior": ("NCC-1701-B", "NCC-1701-B"),
             "c": ("NCC-1701-C", "NCC-1701-C"), "d": ("NCC-1701-D", "NCC-1701-D"), "e": ("NCC-1701-E", "NCC-1701-E")}
    for i, k in enumerate(names):
        n = f"shield_emitter_{k}"
        save(tex_shield_emitter(i + 1), n)
        write_json(os.path.join(ASSETS, "models", "block", n + ".json"), {"parent": "minecraft:block/cube_all", "textures": {"all": b(n)}})
        write_json(os.path.join(ASSETS, "blockstates", n + ".json"), {"variants": {"": {"model": b(n)}}})
        write_json(os.path.join(ASSETS, "items", n + ".json"), item_def(b(n)))
        write_json(os.path.join(DATA, "loot_table", "blocks", n + ".json"), loot(n))
        de[f"block.{M}.{n}"] = f"Schildemitter ({names[k][0]}, Stufe {i + 1})"
        en[f"block.{M}.{n}"] = f"Shield Emitter ({names[k][1]}, tier {i + 1})"
    # Rezepte (Werkbank)
    def shaped(name, pattern, key):
        write_json(os.path.join(DATA, "recipe", name + ".json"), {"type": "minecraft:crafting_shaped", "category": "redstone",
            "key": key, "pattern": pattern, "result": {"id": f"{M}:{name}"}})
    shaped("power_console", ["IGI", "RCR", "IDI"], {"I": "minecraft:iron_ingot", "G": "minecraft:glass_pane", "R": "minecraft:redstone",
           "C": "minecraft:comparator", "D": "minecraft:diamond"})
    shaped("shield_console", ["IGI", "RCR", "ILI"], {"I": "minecraft:iron_ingot", "G": "minecraft:glass_pane", "R": "minecraft:redstone",
           "C": "minecraft:comparator", "L": "minecraft:lapis_block"})
    write_json(os.path.join(DATA, "recipe", "alarm_light.json"), {"type": "minecraft:crafting_shaped", "category": "redstone",
        "key": {"I": "minecraft:iron_ingot", "R": "minecraft:redstone_lamp", "D": "minecraft:red_dye"},
        "pattern": [" I ", "DRD", " I "], "result": {"id": f"{M}:alarm_light", "count": 2}})
    extra = {1: "minecraft:lapis_block", 2: "minecraft:gold_block", 3: "minecraft:emerald", 4: f"{M}:dilithium_crystal",
             5: f"{M}:dilithium_crystal", 6: f"{M}:antimatter_pod"}
    prev = None
    for i, k in enumerate(names):
        n = f"shield_emitter_{k}"
        key = {"I": "minecraft:iron_block", "D": "minecraft:diamond", "X": extra[i + 1]}
        if prev:
            key["P"] = f"{M}:{prev}"
            pattern = ["IXI", "DPD", "IXI"]
        else:
            key["R"] = "minecraft:redstone_block"
            pattern = ["IXI", "DRD", "IXI"]
        shaped(n, pattern, key)
        prev = n
    de.update({
        f"block.{M}.power_console": "Energieverteilung", f"block.{M}.shield_console": "Schildsteuerung",
        f"block.{M}.alarm_light": "Alarmleuchte (rot)",
        f"gui.{M}.power.weapons": "Waffen", f"gui.{M}.power.shields": "Schilde", f"gui.{M}.power.engines": "Antrieb",
        f"gui.{M}.power.lights": "Beleuchtung", f"gui.{M}.power.machines": "Maschinen",
        f"gui.{M}.power.overdrive_on": "Warpkern-Overdrive: AN (+50 %)", f"gui.{M}.power.overdrive_off": "Warpkern-Overdrive: aus",
        f"gui.{M}.power.overdrive_time": "Overdrive – Kern kritisch in %s:%s",
        f"gui.{M}.power.cooling": "Kern kühlt ab (%s s)", f"gui.{M}.power.normal": "Warpkern stabil",
        f"gui.{M}.power.breach": "WARPKERNBRUCH! Explosion in %s s",
        f"message.{M}.power.overdrive_on": "Warpkern-Overdrive aktiv: +50 % Energie – nicht länger als 5 Minuten!",
        f"message.{M}.power.overdrive_off": "Warpkern-Overdrive aus",
        f"message.{M}.power.overheat": "Warnung: Warpkern überhitzt – Eindämmungsbruch in 1 Minute!",
        f"message.{M}.power.breach": "ALARMSTUFE ROT! Warpkern-Eindämmungsbruch – Explosion in 2 Minuten! Kern entfernen oder Schiff verlassen!",
        f"message.{M}.power.countdown": "WARPKERNBRUCH – Explosion in %s s",
        f"message.{M}.power.breach_averted": "Warpkern entfernt – Eindämmungsbruch abgewendet.",
        f"gui.{M}.shield.off": "Schild aus", f"gui.{M}.shield.scanning": "Hülle wird vermessen …",
        f"gui.{M}.shield.up": "Schild aktiv", f"gui.{M}.shield.no_emitter": "Kein Schildemitter im Schiff",
        f"gui.{M}.shield.no_power": "Zu wenig Energie – Schild unten",
        f"gui.{M}.shield.info": "Stufe %s, ~%s Blöcke", f"gui.{M}.shield.average": "Stärke", f"gui.{M}.shield.weakest": "Schwächste",
        f"gui.{M}.shield.turn_on": "Schild an", f"gui.{M}.shield.turn_off": "Schild aus", f"gui.{M}.shield.rebalance": "Umverteilen",
        f"gui.{M}.shield.hint": "Umverteilen: von starken zu schwachen Stellen",
        f"message.{M}.shield.on": "Schilde hoch!", f"message.{M}.shield.off": "Schilde runter",
        f"message.{M}.shield.rebalanced": "Schildenergie umverteilt: alle Bereiche auf %s %%",
        f"subtitles.{M}.red_alert": "Alarmstufe Rot",
    })
    en.update({
        f"block.{M}.power_console": "Power Distribution", f"block.{M}.shield_console": "Shield Control",
        f"block.{M}.alarm_light": "Alarm Light (red)",
        f"gui.{M}.power.weapons": "Weapons", f"gui.{M}.power.shields": "Shields", f"gui.{M}.power.engines": "Engines",
        f"gui.{M}.power.lights": "Lighting", f"gui.{M}.power.machines": "Machines",
        f"gui.{M}.power.overdrive_on": "Warp core overdrive: ON (+50 %)", f"gui.{M}.power.overdrive_off": "Warp core overdrive: off",
        f"gui.{M}.power.overdrive_time": "Overdrive – core critical in %s:%s",
        f"gui.{M}.power.cooling": "Core cooling down (%s s)", f"gui.{M}.power.normal": "Warp core stable",
        f"gui.{M}.power.breach": "WARP CORE BREACH! Explosion in %s s",
        f"message.{M}.power.overdrive_on": "Warp core overdrive active: +50 % power – no longer than 5 minutes!",
        f"message.{M}.power.overdrive_off": "Warp core overdrive off",
        f"message.{M}.power.overheat": "Warning: warp core overheating – containment breach in 1 minute!",
        f"message.{M}.power.breach": "RED ALERT! Warp core containment breach – explosion in 2 minutes! Remove the core or abandon ship!",
        f"message.{M}.power.countdown": "WARP CORE BREACH – explosion in %s s",
        f"message.{M}.power.breach_averted": "Warp core removed – containment breach averted.",
        f"gui.{M}.shield.off": "Shields off", f"gui.{M}.shield.scanning": "Mapping the hull …",
        f"gui.{M}.shield.up": "Shields up", f"gui.{M}.shield.no_emitter": "No shield emitter in the ship",
        f"gui.{M}.shield.no_power": "Not enough power – shields down",
        f"gui.{M}.shield.info": "Tier %s, ~%s blocks", f"gui.{M}.shield.average": "Strength", f"gui.{M}.shield.weakest": "Weakest",
        f"gui.{M}.shield.turn_on": "Shields on", f"gui.{M}.shield.turn_off": "Shields off", f"gui.{M}.shield.rebalance": "Rebalance",
        f"gui.{M}.shield.hint": "Rebalance: move energy from strong to weak areas",
        f"message.{M}.shield.on": "Shields up!", f"message.{M}.shield.off": "Shields down",
        f"message.{M}.shield.rebalanced": "Shield energy rebalanced: all areas at %s %%",
        f"subtitles.{M}.red_alert": "Red alert",
    })


# ---------------------------------------------------------------------------------------------
#  Batterie, Shuttle-Wrack, Transporter-Menü
# ---------------------------------------------------------------------------------------------

def tex_battery_side(level):
    img = noise(new((70, 74, 82)), 5, 951)
    px = img.load()
    for i in range(16):
        px[i, 0] = px[i, 15] = px[0, i] = px[15, i] = (40, 42, 48, 255)
    for x in range(4, 12):  # Anzeige mit 4 Segmenten
        for y in range(2, 14):
            px[x, y] = (20, 22, 26, 255)
    for seg in range(4):
        y0 = 11 - seg * 3
        col = (90, 220, 255) if seg < level else (35, 50, 60)
        for x in range(5, 11):
            for y in range(y0, y0 + 2):
                px[x, y] = col + (255,)
    return img


def tex_battery_top():
    img = noise(new((90, 94, 102)), 5, 952)
    px = img.load()
    for i in range(16):
        px[i, 0] = px[i, 15] = px[0, i] = px[15, i] = (40, 42, 48, 255)
    for x, y in ((4, 4), (5, 4), (4, 5), (5, 5)):
        px[x, y] = (200, 50, 50, 255)        # Pluspol
    for x, y in ((10, 10), (11, 10), (10, 11), (11, 11)):
        px[x, y] = (40, 40, 40, 255)         # Minuspol
    return img


def misc_assets(de, en):
    M = MODID
    save(tex_battery_top(), "battery_top")
    for lvl in range(5):
        save(tex_battery_side(lvl), f"battery_side_{lvl}")
        write_json(os.path.join(ASSETS, "models", "block", f"battery_{lvl}.json"), {"parent": "minecraft:block/cube_column",
            "textures": {"side": b(f"battery_side_{lvl}"), "end": b("battery_top")}})
    write_json(os.path.join(ASSETS, "blockstates", "battery.json"), {"variants": {f"charge={c}": {"model": b(f"battery_{c}")} for c in range(5)}})
    write_json(os.path.join(ASSETS, "items", "battery.json"), item_def(b("battery_4")))
    write_json(os.path.join(DATA, "loot_table", "blocks", "battery.json"), {
        "type": "minecraft:block", "random_sequence": f"{M}:blocks/battery",
        "pools": [{"rolls": 1.0, "entries": [{"type": "minecraft:item", "name": f"{M}:battery", "functions": [
            {"function": "minecraft:copy_components", "source": "block_entity", "include": [f"{M}:weapon_energy"]}]}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    write_json(os.path.join(DATA, "recipe", "battery.json"), {"type": "minecraft:crafting_shaped", "category": "redstone",
        "key": {"I": "minecraft:iron_ingot", "C": "minecraft:copper_block", "R": "minecraft:redstone_block", "G": "minecraft:gold_ingot"},
        "pattern": ["IGI", "CRC", "ICI"], "result": {"id": f"{M}:battery"}})
    # Shuttle-Wrack: selten, an der Oberfläche der Oberwelt
    write_json(os.path.join(DATA, "worldgen", "configured_feature", "shuttle_wreck.json"), {"type": f"{M}:shuttle_wreck", "config": {}})
    write_json(os.path.join(DATA, "worldgen", "placed_feature", "shuttle_wreck.json"), {"feature": f"{M}:shuttle_wreck", "placement": [
        {"type": "minecraft:rarity_filter", "chance": 450}, {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"}]})
    write_json(os.path.join(DATA, "forge", "biome_modifier", "add_shuttle_wreck.json"), {"type": "forge:add_features",
        "biomes": "#minecraft:is_overworld", "features": f"{M}:shuttle_wreck", "step": "surface_structures"})

    def item(name, lo, hi, w):
        e = {"type": "minecraft:item", "name": name, "weight": w}
        if hi > 1:
            e["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
        return e
    write_json(os.path.join(DATA, "loot_table", "chests", "shuttle_wreck.json"), {"type": "minecraft:chest",
        "random_sequence": f"{M}:chests/shuttle_wreck", "pools": [
            # immer: Uran für einen ersten Reaktor, Eisen und Kupfer für die Fertigung
            {"rolls": 1, "entries": [item(f"{M}:uranium_ingot", 6, 9, 1)]},
            {"rolls": 1, "entries": [item("minecraft:iron_ingot", 8, 16, 1)]},
            {"rolls": 1, "entries": [item("minecraft:copper_ingot", 8, 16, 1)]},
            {"rolls": {"type": "minecraft:uniform", "min": 3, "max": 6}, "entries": [
                item("minecraft:redstone", 3, 9, 15), item("minecraft:glass_pane", 2, 6, 10), item("minecraft:diamond", 1, 2, 5),
                item(f"{M}:raw_uranium", 1, 4, 8), item(f"{M}:hull_plating", 4, 12, 12), item(f"{M}:ceiling_light", 1, 4, 6),
                item("minecraft:gold_ingot", 1, 4, 6), item(f"{M}:dilithium_crystal", 1, 1, 2), item(f"{M}:keycard_security", 1, 1, 2),
                item(f"{M}:phaser_tos", 1, 1, 2), item(f"{M}:communicator", 1, 1, 3), item("minecraft:bread", 2, 5, 6)]}]})
    de.update({
        f"block.{M}.battery": "Batterie",
        f"gui.{M}.transporter.title": "Transporter", f"gui.{M}.transporter.surface": "Auf die Oberfläche beamen",
        f"gui.{M}.transporter.target": "Transporter: %s m %s, Höhe %s (%s Plattformen)",
        f"gui.{M}.transporter.target_ship": "Fliegendes Schiff: %s m %s, Höhe %s (%s Plattformen)",
        f"message.{M}.communicator.ship_gone": "Das Schiff ist nicht mehr im Flug (gelandet oder zerstört).",
        f"message.{M}.communicator.ship_far": "Das Schiff ist in einer anderen Dimension – außer Reichweite.",
        f"gui.{M}.transporter.none": "Keine anderen Transporter im Umkreis von 500 Blöcken",
        f"message.{M}.transporter.shielded": "Transport nicht möglich – ein aktives Schild liegt zwischen Start und Ziel!",
        f"message.{M}.transporter.target_gone": "Ziel-Transporter existiert nicht mehr.",
    })
    en.update({
        f"block.{M}.battery": "Battery",
        f"gui.{M}.transporter.title": "Transporter", f"gui.{M}.transporter.surface": "Beam down to the surface",
        f"gui.{M}.transporter.target": "Transporter: %s m %s, height %s (%s pads)",
        f"gui.{M}.transporter.target_ship": "Flying ship: %s m %s, height %s (%s pads)",
        f"message.{M}.communicator.ship_gone": "The ship is no longer flying (landed or destroyed).",
        f"message.{M}.communicator.ship_far": "The ship is in another dimension – out of range.",
        f"gui.{M}.transporter.none": "No other transporters within 500 blocks",
        f"message.{M}.transporter.shielded": "Transport impossible – an active shield lies between start and target!",
        f"message.{M}.transporter.target_gone": "The target transporter no longer exists.",
    })


def armory_assets(de, en):
    tex = f"{MODID}:item/weapon_palette"
    for name, boxes in WEAPON_MODELS.items():
        write_json(os.path.join(ASSETS, "models", "item", name + ".json"), {
            "textures": {"p": tex, "particle": tex},
            "display": weapon_display(name.startswith("rifle")),
            "elements": [wbox(*e) for e in boxes]})
        write_json(os.path.join(ASSETS, "items", name + ".json"), item_def(f"{MODID}:item/{name}"))
        de[f"item.{MODID}.{name}"], en[f"item.{MODID}.{name}"] = WEAPON_NAMES[name]

    # Gewehrständer: zwei Hälften
    metal = b("armory_metal")
    lower = {"parent": "minecraft:block/block", "textures": {"particle": metal, "m": metal, "d": b("trim_black")},
             "elements": [el([0, 0, 4], [16, 3, 16], all6("#m")), el([0, 0, 13], [16, 16, 16], all6("#m"))]
             + [el([x, 3, 5], [x + 0.6, 6, 13], all6("#d")) for x in (0, 3.7, 7.7, 11.7, 15.4)]}
    upper = {"parent": "minecraft:block/block", "textures": {"particle": metal, "m": metal, "d": b("trim_black")},
             "elements": [el([0, 0, 13], [16, 16, 16], all6("#m")), el([0, 6, 10], [16, 8, 13], all6("#d"))]
             + [el([x, 5, 9], [x + 0.6, 9, 13], all6("#m")) for x in (0, 3.7, 7.7, 11.7, 15.4)]}
    write_json(os.path.join(ASSETS, "models", "block", "rifle_rack_lower.json"), lower)
    write_json(os.path.join(ASSETS, "models", "block", "rifle_rack_upper.json"), upper)
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    for d, r in rot.items():
        for half in ("lower", "upper"):
            m = {"model": b("rifle_rack_" + half)}
            if r:
                m["y"] = r
            variants[f"facing={d},half={half}"] = m
    write_json(os.path.join(ASSETS, "blockstates", "rifle_rack.json"), {"variants": variants})
    write_json(os.path.join(ASSETS, "items", "rifle_rack.json"), item_def(b("rifle_rack_lower")))
    write_json(os.path.join(DATA, "loot_table", "blocks", "rifle_rack.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MODID}:rifle_rack"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"},
                                  {"condition": "minecraft:block_state_property", "block": f"{MODID}:rifle_rack",
                                   "properties": {"half": "lower"}}]}],
        "random_sequence": f"{MODID}:blocks/rifle_rack"})

    # Phaser-Wandhalter und Ladestation (mit Blickrichtung)
    plate = b("holder_plate")
    holder = {"parent": "minecraft:block/block", "textures": {"particle": plate, "p": plate, "m": metal},
              "elements": [el([0, 0, 14], [16, 16, 16], {"north": "#p", "south": "#m", "east": "#m", "west": "#m", "up": "#m", "down": "#m"})]
              + [el([x, y, 12], [x + 6, y + 0.8, 14], all6("#m")) for x in (1, 9) for y in (1.2, 9.2)]}
    # Ladestation: Schrank mit Glasfront (man sieht den Phaser darin), Haken für ein Gewehr an der Seite
    glass, side, top = b("charger_glass"), b("charger_side"), b("charger_top")
    charger = {"parent": "minecraft:block/block", "render_type": "minecraft:translucent",
               "textures": {"particle": side, "s": side, "t": top, "m": metal, "g": glass, "d": b("trim_black")},
               "elements": [el([2, 0, 13], [14, 16, 15], all6("#s")),                       # Rückwand
                            el([2, 0, 2], [3, 16, 13], all6("#s")), el([13, 0, 2], [14, 16, 13], all6("#s")),
                            el([2, 0, 2], [14, 2, 13], all6("#m")),                        # Boden
                            el([2, 14, 2], [14, 16, 13], {"north": "#m", "south": "#m", "east": "#m", "west": "#m", "up": "#t", "down": "#t"}),
                            el([5, 2, 7], [11, 3, 10], all6("#d")),                        # Halterung innen
                            el([3, 2, 2], [13, 14, 2.5], all6("#g")),                      # Glasfront
                            el([0.5, 9, 6], [2, 10, 10], all6("#d")), el([0.5, 3, 6], [2, 4, 10], all6("#d")),     # Gewehrhaken rechts
                            el([14, 9, 6], [15.5, 10, 10], all6("#d")), el([14, 3, 6], [15.5, 4, 10], all6("#d"))]}  # und links
    # rote Warnleuchte oben an der Front (leuchtet ohne Energie, blinkt beim Laden ohne Strom)
    charger_red = dict(charger, textures=dict(charger["textures"], r=b("nav_light_red")),
                       elements=charger["elements"] + [dict(el([5, 14.6, 1.2], [11, 15.6, 2.2], all6("#r")), light_emission=15)])
    write_json(os.path.join(ASSETS, "models", "block", "charging_station_red.json"), charger_red)
    for name, model in (("phaser_holder", holder), ("charging_station", charger)):
        write_json(os.path.join(ASSETS, "models", "block", name + ".json"), model)
        bs = facing_blockstate(name)
        if name == "charging_station":
            rot = {"north": 0, "east": 90, "south": 180, "west": 270}
            bs = {"variants": {f"facing={d},red={str(red).lower()}": dict({"model": b(name + ("_red" if red else ""))}, **({"y": r} if r else {}))
                               for d, r in rot.items() for red in (False, True)}}
        write_json(os.path.join(ASSETS, "blockstates", name + ".json"), bs)
        write_json(os.path.join(ASSETS, "items", name + ".json"), item_def(b(name)))
        write_json(os.path.join(DATA, "loot_table", "blocks", name + ".json"), loot(name))
    for name in ("warp_core_nx", "warp_core_e"):
        write_json(os.path.join(ASSETS, "models", "block", name + ".json"),
                   {"parent": "minecraft:block/cube_column", "textures": {"end": b(name + "_top"), "side": b(name)}})
        write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": {"": {"model": b(name)}}})
        write_json(os.path.join(ASSETS, "items", name + ".json"), item_def(b(name)))
        write_json(os.path.join(DATA, "loot_table", "blocks", name + ".json"), loot(name))
    for k, v in {"rifle_rack": ("Gewehrständer (4 Gewehre, 2 hoch)", "Rifle Rack (4 rifles, 2 high)"),
                 "phaser_holder": ("Phaser-Wandhalter (4 Phaser)", "Phaser Wall Holder (4 phasers)"),
                 "charging_station": ("Ladestation", "Charging Station"),
                 "warp_core_nx": ("Warpkern (NX-01)", "Warp Core (NX-01)"),
                 "warp_core_e": ("Warpkern (Enterprise-E)", "Warp Core (Enterprise-E)")}.items():
        de[f"block.{MODID}.{k}"], en[f"block.{MODID}.{k}"] = v
    return ["rifle_rack", "phaser_holder", "charging_station", "warp_core_nx", "warp_core_e"]


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
    "deflector_violet": tex_deflector_violet,
    "phase_cannon": tex_phase_cannon,
    "phaser_bank": tex_phaser_bank,
    "phaser_emitter": tex_phaser_emitter,
    "quantum_launcher": tex_quantum_launcher,
    "warp_core_nx": tex_warp_core_nx,
    "warp_core_nx_top": tex_warp_core_nx_top,
    "warp_core_e": tex_warp_core_e,
    "warp_core_e_top": tex_warp_core_e_top,
    "armory_metal": tex_armory_metal,
    "holder_plate": tex_holder_plate,
    "charger_side": tex_charger_side,
    "charger_top": tex_charger_top,
    "charger_glass": tex_charger_glass,
    "item/weapon_palette": tex_weapon_palette,
    **{f"weapons_console_top_{v}": (lambda v=v: tex_weapons_top(v)) for v in range(4)},
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


SIMPLE = ["quantum_launcher", "phase_cannon", "phaser_bank", "phaser_emitter", "deflector_violet", "deflector_ring", "phaser_strip", "ent_wall", "decon_light", "tng_carpet", "tng_wall", "holodeck_grid", "star_map", "bar_counter", "light_pillar", "hull_teal", "bussard_blue", "bridge_trim_cream", "bridge_carpet_teal", "deck_plate", "bridge_wall_dark", "impulse_engine", "hull_aztec", "deflector_dish_blue", "bussard_red", "torpedo_launcher", "bridge_trim_blue", "sickbay_wall", "sickbay_floor", "cargo_container", "bridge_wall", "bridge_trim", "turbolift_frame", "transporter_wall", "transporter_platform",
          "transporter_energizer", "transporter_emitter", "nav_light_red", "nav_light_green", "hull_pennant",
          "hull_plating", "hull_panel", "hull_dark", "hull_window", "hull_marking", "deflector_dish",
          "bussard_collector", "nacelle_grille", "deck_floor", "bridge_deck", "corridor_wall",
          "ceiling_panel", "plasma_conduit"]

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
    "ship_helm": None,
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
    "phaser_strip": ("Phaserstreifen (Next Generation)", "Phaser Strip (Next Generation)"),
    "phase_cannon": ("Phasenkanone (NX-01)", "Phase Cannon (NX-01)"),
    "phaser_bank": ("Phaserbank (Originalserie)", "Phaser Bank (Original Series)"),
    "phaser_emitter": ("Phaser-Emitter (Refit/Excelsior)", "Phaser Emitter (Refit/Excelsior)"),
    "weapons_console": ("Waffenstation", "Weapons Console"),
    "quantum_launcher": ("Quantentorpedo-Werfer", "Quantum Torpedo Launcher"),
    "deflector_ring": ("Deflektorring (Kupfer)", "Deflector Ring (Copper)"),
    "deflector_violet": ("Deflektorschüssel (Galaxy, violett)", "Deflector Dish (Galaxy, Violet)"),
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
              "helm_console_gray": dict(HELM, textures={"particle": b("helm_gray"), "red": b("helm_gray"), "top": b("helm_gray_top"), "black": b("bridge_wall_dark")}),
              # Steuerkonsole für eigene fliegende Schiffe: dunkles Pult mit LCARS-Oberseite
              "ship_helm": dict(HELM, textures={"particle": b("bridge_wall_dark"), "red": b("bridge_wall_dark"), "top": b("lcars_panel"), "black": b("trim_black")})}
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
                     ("msd_display", msd_model), ("weapons_console", weapons_console_model)):
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

    armory_de, armory_en = {}, {}
    models_extra += armory_assets(armory_de, armory_en)
    models_extra += ["ceiling_light", "dual_wall", "dual_floor", "uranium_ore", "deepslate_uranium_ore", "reactor_casing",
                     "reactor_glass", "reactor_controller", "ship_helm", "airlock", "airlock_small", "dilithium_ore",
                     "antimatter_cluster", "deuterium_ice", "ship_part_assembly",
                     "power_console", "shield_console", "alarm_light", "battery"] + [f"shield_emitter_{k}" for k in ("tos", "refit", "excelsior", "c", "d", "e")]
    models_extra += [f"door_{e}_{k}" for e in DOOR_ERAS for k in DOOR_KINDS]
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
    de.update({
        f"message.{MODID}.phaser.fire": "Phaser feuern!",
        f"message.{MODID}.phaser.recharging": "Phaser laden nach ...",
        f"message.{MODID}.phaser.none": "Keine Phaser mit dieser Waffenstation verbunden.",
        f"message.{MODID}.phaser.no_arc": "Kein Phaser hat freie Schussbahn zum Ziel.",
        f"message.{MODID}.phaser.no_power": "Phaser: zu wenig Strom",
        f"message.{MODID}.camera.show": "Kamera %s/%s: %s | Stärke %s – Linksklick: Phaser, P/O: Torpedo, Rechtsklick: Kamera, Mausrad: Stärke, Shift: zurück",
        f"message.{MODID}.torpedo.photon": "Photonentorpedo abgefeuert!",
        f"message.{MODID}.torpedo.quantum": "Quantentorpedo abgefeuert!",
        f"message.{MODID}.torpedo.loading": "Torpedorohr wird nachgeladen ...",
        f"message.{MODID}.torpedo.none": "Dieses Schiff hat keine Torpedowerfer.",
        f"message.{MODID}.torpedo.no_quantum": "Quantentorpedos hat nur die Enterprise-E.",
        f"key.{MODID}.photon": "Photonentorpedo (Kamerasicht)",
        f"key.{MODID}.quantum": "Quantentorpedo (Kamerasicht)",
        f"key.category.{MODID}.weapons": "Simon Space Mod – Waffen",
        f"message.{MODID}.phaser.power.1": "Phaserstärke 1: Betäuben – Schaden ohne Blockschaden",
        f"message.{MODID}.phaser.power.2": "Phaserstärke 2: mehr Schaden und Blockschaden",
        f"message.{MODID}.phaser.power.3": "Phaserstärke 3: volle Leistung – großer Blockschaden",
    })
    en.update({
        f"message.{MODID}.phaser.fire": "Firing phasers!",
        f"message.{MODID}.phaser.recharging": "Phasers recharging ...",
        f"message.{MODID}.phaser.none": "No phasers linked to this weapons console.",
        f"message.{MODID}.phaser.no_arc": "No phaser has a clear line of fire to the target.",
        f"message.{MODID}.phaser.no_power": "Phasers: not enough power",
        f"message.{MODID}.camera.show": "Camera %s/%s: %s | power %s – left click: phasers, P/O: torpedo, right click: camera, mouse wheel: power, shift: leave",
        f"message.{MODID}.torpedo.photon": "Photon torpedo away!",
        f"message.{MODID}.torpedo.quantum": "Quantum torpedo away!",
        f"message.{MODID}.torpedo.loading": "Reloading torpedo tube ...",
        f"message.{MODID}.torpedo.none": "This ship has no torpedo launchers.",
        f"message.{MODID}.torpedo.no_quantum": "Only the Enterprise-E carries quantum torpedoes.",
        f"key.{MODID}.photon": "Photon torpedo (camera view)",
        f"key.{MODID}.quantum": "Quantum torpedo (camera view)",
        f"key.category.{MODID}.weapons": "Simon Space Mod – Weapons",
        f"message.{MODID}.phaser.power.1": "Phaser power 1: stun – damage without block damage",
        f"message.{MODID}.phaser.power.2": "Phaser power 2: more damage and block damage",
        f"message.{MODID}.phaser.power.3": "Phaser power 3: full power – heavy block damage",
    })
    de.update({
        f"block.{MODID}.ship_helm": "Steuerkonsole (eigenes Schiff)",
        f"key.{MODID}.ship_land": "Schiff landen",
        f"message.{MODID}.ship.launched": "Schiff gestartet: %s Blöcke, %s Impulsantriebe – am Steuer: W/S Schub, Fadenkreuz lenkt, L landen; H Steuer abgeben/übernehmen, sonst frei herumlaufen; Shift aussteigen",
        f"key.{MODID}.ship_helm": "Schiff: Steuer übernehmen/abgeben",
        f"gamerule.{MODID}.max_ship_blocks": "Größte Schiffsgröße (Blöcke) für die Steuerkonsole",
        f"message.{MODID}.ship.too_big_huge": "Zu groß: mehr als %s Blöcke hängen zusammen (erlaubt: %s).",
        f"message.{MODID}.ship.hull_open": "Hülle nicht geschlossen: %s Blöcke außen sind keine Hüllenblöcke (z. B. %s bei %s %s %s). Außen erlaubt: Hüllenplatten, Hüllenfenster, Impulsantriebe, Torpedowerfer, Phaser, Deflektor, Bussard, Gondelgitter, Positionslichter, Türen.",
        f"message.{MODID}.ship.helm_on": "Steuer übernommen: W/S Schub, Fadenkreuz lenkt, Linksklick Phaser (Mausrad Stärke), P/O Torpedos, L landen, H abgeben.",
        f"message.{MODID}.ship.helm_off": "Steuer abgegeben – du kannst im Schiff herumlaufen.",
        f"message.{MODID}.ship.helm_taken": "Jemand anderes steuert gerade.",
        f"message.{MODID}.ship.helm_far": "Dafür musst du an der Steuerkonsole stehen.",
        f"message.{MODID}.ship.helm_needed": "Landen kann nur, wer am Steuer steht (H).",
        f"message.{MODID}.ship.hud": "%s m/s  Schub %s  Energie %s%%  Antriebe %s  Masse %s",
        f"message.{MODID}.ship.too_big": "Zu groß: %s Blöcke, erlaubt sind %s – %s Blöcke zu viel. (Spielregel simonspacemod:max_ship_blocks)",
        f"message.{MODID}.ship.no_engine": "Kein Impulsantrieb in der Struktur – das Schiff kann sich nicht bewegen.",
        f"message.{MODID}.ship.too_fast": "Zu schnell zum Landen – erst abbremsen (S).",
        f"message.{MODID}.ship.level_first": "Zum Landen erst waagerecht ausrichten.",
        f"message.{MODID}.ship.no_room": "Kein Platz zum Landen – dort stehen schon Blöcke.",
    })
    en.update({
        f"block.{MODID}.ship_helm": "Helm Console (own ship)",
        f"key.{MODID}.ship_land": "Land ship",
        f"message.{MODID}.ship.launched": "Ship launched: %s blocks, %s impulse engines – at the helm: W/S thrust, crosshair steers, L land; H take/leave helm, otherwise walk around; shift leave",
        f"key.{MODID}.ship_helm": "Ship: take/leave helm",
        f"gamerule.{MODID}.max_ship_blocks": "Largest ship size (blocks) for the helm console",
        f"message.{MODID}.ship.too_big_huge": "Too large: more than %s connected blocks (allowed: %s).",
        f"message.{MODID}.ship.hull_open": "Hull not closed: %s outer blocks are not hull blocks (e.g. %s at %s %s %s). Allowed outside: hull plates, hull windows, impulse engines, torpedo launchers, phasers, deflector, bussard, nacelle grille, navigation lights, doors.",
        f"message.{MODID}.ship.helm_on": "You took the helm: W/S thrust, crosshair steers, left click phasers (wheel: power), P/O torpedoes, L land, H leave.",
        f"message.{MODID}.ship.helm_off": "Helm released – you can walk around the ship.",
        f"message.{MODID}.ship.helm_taken": "Someone else is at the helm.",
        f"message.{MODID}.ship.helm_far": "You need to stand at the helm console.",
        f"message.{MODID}.ship.helm_needed": "Only the person at the helm can land (H).",
        f"message.{MODID}.ship.hud": "%s m/s  thrust %s  power %s%%  engines %s  mass %s",
        f"message.{MODID}.ship.too_big": "Too large: %s blocks, %s allowed – %s blocks too many. (game rule simonspacemod:max_ship_blocks)",
        f"message.{MODID}.ship.no_engine": "No impulse engine in the structure – the ship cannot move.",
        f"message.{MODID}.ship.too_fast": "Too fast to land – brake first (S).",
        f"message.{MODID}.ship.level_first": "Level the ship before landing.",
        f"message.{MODID}.ship.no_room": "No room to land – there are blocks in the way.",
    })
    uniform_assets(de, en)
    door_assets(de, en)
    dual_assets(de, en)
    ceiling_light_assets(de, en)
    uranium_reactor_assets(de, en)
    space_assets(de, en)
    suit_assembly_assets(de, en)
    sound_assets(de, en)
    defense_assets(de, en)
    misc_assets(de, en)
    de.update(armory_de)
    en.update(armory_en)
    de.update({
        f"key.{MODID}.weapon_mode": "Phaser-Modus wechseln",
        f"mode.{MODID}.stun": "Betäuben",
        f"mode.{MODID}.kill": "Töten",
        f"mode.{MODID}.mining": "Abbauen",
        f"message.{MODID}.weapon.mode": "Phaser-Modus: %s",
        f"message.{MODID}.weapon.empty": "Energiezelle leer – an der Ladestation aufladen.",
        f"tooltip.{MODID}.weapon.energy": "Energie: %s / %s RF",
        f"tooltip.{MODID}.weapon.mode": "Modus: %s",
        f"tooltip.{MODID}.weapon.keys": "Linksklick halten: feuern, V: Modus wechseln",
        f"tooltip.{MODID}.rifle.keys": "Linksklick halten: feuern, Rechtsklick halten: zielen, V: Modus wechseln",
        f"message.{MODID}.charger.status": "Ladestation: %s / %s RF, Bordnetz: %s RF/t",
    })
    en.update({
        f"key.{MODID}.weapon_mode": "Switch phaser mode",
        f"mode.{MODID}.stun": "Stun",
        f"mode.{MODID}.kill": "Kill",
        f"mode.{MODID}.mining": "Mining",
        f"message.{MODID}.weapon.mode": "Phaser mode: %s",
        f"message.{MODID}.weapon.empty": "Power cell empty – recharge at a charging station.",
        f"tooltip.{MODID}.weapon.energy": "Energy: %s / %s RF",
        f"tooltip.{MODID}.weapon.mode": "Mode: %s",
        f"tooltip.{MODID}.weapon.keys": "Hold left click: fire, V: switch mode",
        f"tooltip.{MODID}.rifle.keys": "Hold left click: fire, hold right click: aim, V: switch mode",
        f"message.{MODID}.charger.status": "Charging station: %s / %s RF, ship grid: %s RF/t",
    })
    write_json(os.path.join(ASSETS, "lang", "de_de.json"), de)
    write_json(os.path.join(ASSETS, "lang", "en_us.json"), en)


if __name__ == "__main__":
    main()
