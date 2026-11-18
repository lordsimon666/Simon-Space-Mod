"""Sternenflotten-Dorfbewohner: Shuttle-Pilotensitz, Raumschiff-Bett, Häute der Figuren, Handel, Texte.

Wird von generate_assets.py aufgerufen (run(de, en)) und nutzt dessen Hilfsfunktionen.
"""
import os
import random
import sys

from PIL import Image

g = sys.modules["__main__"]  # generate_assets.py
MODID = g.MODID
ASSETS = g.ASSETS
DATA = g.DATA
ROOT = g.ROOT
b, el, all6, write_json, item_def, loot, new, noise, save, shade = (
    g.b, g.el, g.all6, g.write_json, g.item_def, g.loot, g.new, g.noise, g.save, g.shade)

ROT = {"north": 0, "east": 90, "south": 180, "west": 270}


# ---------------------------------------------------------------------------------------------
#  Blöcke: Shuttle-Pilotensitz und Raumschiff-Bett
# ---------------------------------------------------------------------------------------------

def textures():
    fabric = noise(new((46, 56, 76)), 5, 4101)                          # gesteppter Bezug
    fp = fabric.load()
    for i in range(16):
        for j in (4, 11):
            fp[i, j] = fp[j, i] = shade(fp[i, j], -18)
    save(fabric, "shuttle_fabric")
    frame = noise(new((170, 174, 182)), 4, 4102)
    rp = frame.load()
    for i in range(16):
        rp[i, 0] = rp[i, 15] = (120, 124, 134, 255)
    save(frame, "shuttle_frame")

    dark = noise(new((58, 62, 72)), 3, 4201)
    save(dark, "space_bed_dark")
    light = noise(new((196, 200, 208)), 3, 4202)
    lp = light.load()
    for i in range(16):
        lp[i, 7] = lp[i, 8] = (90, 170, 230, 255)                        # blaue Zierlinie
    save(light, "space_bed_frame")
    strip = new((120, 220, 255))
    sp = strip.load()
    for i in range(16):
        sp[i, 0] = sp[i, 15] = (70, 170, 230, 255)
    save(strip, "space_bed_strip")
    mattress = noise(new((222, 226, 232)), 3, 4203)
    save(mattress, "space_bed_mattress")
    blanket = noise(new((32, 52, 104)), 4, 4204)
    bp = blanket.load()
    for i in range(16):
        bp[i, 3] = bp[i, 4] = (212, 172, 60, 255)                        # goldener Streifen
    save(blanket, "space_bed_blanket")
    pillow = noise(new((236, 238, 242)), 2, 4205)
    save(pillow, "space_bed_pillow")


def glow(element, level=15):
    element["light_emission"] = level
    return element


SHUTTLE_CHAIR = {
    "parent": "minecraft:block/block",
    "textures": {"particle": b("shuttle_fabric"), "fabric": b("shuttle_fabric"), "frame": b("shuttle_frame"),
                 "dark": b("space_bed_dark"), "strip": b("space_bed_strip")},
    "elements": [
        el([4, 0, 4], [12, 1, 12], all6("#dark")),
        el([7, 1, 7], [9, 6, 9], all6("#frame")),
        el([2, 6, 2], [14, 9, 14], {"up": "#fabric", "down": "#dark", "north": "#frame", "south": "#frame", "east": "#frame", "west": "#frame"}),
        el([2, 9, 12], [14, 20, 15], {"north": "#fabric", "south": "#frame", "east": "#frame", "west": "#frame", "up": "#frame", "down": "#frame"}),
        el([4, 20, 12], [12, 23, 15], {"north": "#fabric", "south": "#frame", "east": "#frame", "west": "#frame", "up": "#frame", "down": "#frame"}),
        el([1, 9, 3], [3, 12, 12], {"up": "#fabric", "down": "#frame", "north": "#frame", "south": "#frame", "east": "#frame", "west": "#frame"}),
        el([13, 9, 3], [15, 12, 12], {"up": "#fabric", "down": "#frame", "north": "#frame", "south": "#frame", "east": "#frame", "west": "#frame"}),
        glow(el([1, 10, 2], [3, 11, 3], all6("#strip")), 10),           # Leuchtpunkte vorn an den Armlehnen
        glow(el([13, 10, 2], [15, 11, 3], all6("#strip")), 10),
    ],
}

BED_TEX = {"particle": b("space_bed_blanket"), "dark": b("space_bed_dark"), "frame": b("space_bed_frame"),
           "strip": b("space_bed_strip"), "mattress": b("space_bed_mattress"), "blanket": b("space_bed_blanket"),
           "pillow": b("space_bed_pillow")}

# Blick nach Norden: das Kopfteil liegt nördlich des Fußteils
BED_HEAD = [
    el([2, 0, 2], [14, 3, 16], all6("#dark")),
    el([0, 3, 0], [16, 5, 16], all6("#frame")),
    el([1, 5, 2], [15, 8, 16], all6("#mattress")),
    el([3, 8, 3], [13, 10, 7], all6("#pillow")),
    el([0, 5, 0], [16, 15, 2], {"south": "#dark", "north": "#frame", "east": "#frame", "west": "#frame", "up": "#frame", "down": "#frame"}),
    glow(el([2, 12, 2], [14, 13, 3], all6("#strip"))),                  # Leselicht am Kopfteil
    glow(el([0, 2, 2], [1, 3, 16], all6("#strip")), 8),                 # Leuchtstreifen unten an den Seiten
    glow(el([15, 2, 2], [16, 3, 16], all6("#strip")), 8),
]
BED_FOOT = [
    el([2, 0, 0], [14, 3, 14], all6("#dark")),
    el([0, 3, 0], [16, 5, 16], all6("#frame")),
    el([1, 5, 0], [15, 8, 15], all6("#mattress")),
    el([1, 8, 0], [15, 9, 13], all6("#blanket")),
    el([0, 5, 1], [1, 8, 13], all6("#blanket")),
    el([15, 5, 1], [16, 8, 13], all6("#blanket")),
    el([0, 5, 15], [16, 9, 16], all6("#frame")),
    glow(el([0, 2, 0], [1, 3, 14], all6("#strip")), 8),
    glow(el([15, 2, 0], [16, 3, 14], all6("#strip")), 8),
]


def shifted(elements, dz):
    out = []
    for e in elements:
        e2 = dict(e)
        e2["from"] = [e["from"][0], e["from"][1], e["from"][2] + dz]
        e2["to"] = [e["to"][0], e["to"][1], e["to"][2] + dz]
        out.append(e2)
    return out


def block_assets():
    models = os.path.join(ASSETS, "models", "block")
    write_json(os.path.join(models, "shuttle_chair.json"), SHUTTLE_CHAIR)
    write_json(os.path.join(ASSETS, "blockstates", "shuttle_chair.json"), g.facing_blockstate("shuttle_chair"))
    write_json(os.path.join(ASSETS, "items", "shuttle_chair.json"), item_def(b("shuttle_chair")))
    write_json(os.path.join(DATA, "loot_table", "blocks", "shuttle_chair.json"), loot("shuttle_chair"))

    write_json(os.path.join(models, "space_bed_head.json"), {"parent": "minecraft:block/block", "textures": BED_TEX, "elements": BED_HEAD})
    write_json(os.path.join(models, "space_bed_foot.json"), {"parent": "minecraft:block/block", "textures": BED_TEX, "elements": BED_FOOT})
    # Gegenstand: beide Hälften hintereinander, kleiner dargestellt
    write_json(os.path.join(models, "space_bed_item.json"), {
        "parent": "minecraft:block/block", "textures": BED_TEX,
        "elements": shifted(BED_HEAD, -8) + shifted(BED_FOOT, 8),
        "display": {
            "gui": {"rotation": [30, 160, 0], "translation": [0, 2, 0], "scale": [0.42, 0.42, 0.42]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.2, 0.2, 0.2]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.3, 0.3, 0.3]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.3, 0.3, 0.3]},
        }})
    variants = {}
    for d, r in ROT.items():
        for part in ("head", "foot"):
            for occ in ("false", "true"):
                m = {"model": b("space_bed_" + part)}
                if r:
                    m["y"] = r
                variants[f"facing={d},occupied={occ},part={part}"] = m
    write_json(os.path.join(ASSETS, "blockstates", "space_bed.json"), {"variants": variants})
    write_json(os.path.join(ASSETS, "items", "space_bed.json"), item_def(b("space_bed_item")))
    write_json(os.path.join(DATA, "loot_table", "blocks", "space_bed.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MODID}:space_bed"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"},
                                  {"condition": "minecraft:block_state_property", "block": f"{MODID}:space_bed",
                                   "properties": {"part": "head"}}]}],
        "random_sequence": f"{MODID}:blocks/space_bed"})


# ---------------------------------------------------------------------------------------------
#  Häute der Figuren (64×64, Schnittmuster wie eine Spielerhaut)
# ---------------------------------------------------------------------------------------------

LIGHT, TAN, OLIVE, BROWN, DARK, DEEP = (236, 196, 166), (214, 168, 128), (198, 160, 120), (150, 104, 72), (110, 74, 50), (84, 56, 40)
BLACK_H, BROWN_H, DBROWN_H, RED_H, AUBURN_H, BLOND_H, GREY_H, WHITE_H = (
    (30, 26, 26), (96, 64, 40), (60, 42, 30), (178, 72, 40), (138, 62, 36), (226, 196, 110), (150, 150, 150), (206, 206, 206))

# Name in StarfleetCrew -> Aussehen
CREW = {
    "picard":  dict(skin=LIGHT, hair=GREY_H, style="fringe", eyes=(70, 90, 110)),
    "riker":   dict(skin=LIGHT, hair=DBROWN_H, style="short", beard="full", eyes=(60, 90, 130)),
    "data":    dict(skin=(232, 222, 176), hair=BLACK_H, style="slick", eyes=(214, 178, 40)),
    "worf":    dict(skin=DARK, hair=BLACK_H, style="long", beard="goatee", ridges=True, eyes=(40, 30, 24)),
    "laforge": dict(skin=DARK, hair=BLACK_H, style="buzz", visor=True),
    "crusher": dict(skin=LIGHT, hair=RED_H, style="bob", eyes=(70, 110, 140)),
    "troi":    dict(skin=TAN, hair=BLACK_H, style="curly_long", eyes=(40, 30, 24)),
    "kirk":    dict(skin=LIGHT, hair=BROWN_H, style="short", eyes=(110, 120, 80)),
    "spock":   dict(skin=(222, 206, 170), hair=BLACK_H, style="bowl", vulcan=True, eyes=(40, 30, 24)),
    "mccoy":   dict(skin=LIGHT, hair=DBROWN_H, style="short", eyes=(60, 100, 150)),
    "scott":   dict(skin=LIGHT, hair=DBROWN_H, style="short", beard="mustache", eyes=(80, 70, 50)),
    "uhura":   dict(skin=DEEP, hair=BLACK_H, style="updo", eyes=(40, 30, 24)),
    "sulu":    dict(skin=OLIVE, hair=BLACK_H, style="short", eyes=(40, 30, 24)),
    "chekov":  dict(skin=LIGHT, hair=BROWN_H, style="mop", eyes=(80, 70, 50)),
    "janeway": dict(skin=LIGHT, hair=AUBURN_H, style="bun", eyes=(70, 100, 130)),
    "sisko":   dict(skin=DEEP, hair=None, style="bald", beard="goatee_black", eyes=(40, 30, 24)),
    "seven":   dict(skin=LIGHT, hair=BLOND_H, style="bun", implant=True, eyes=(70, 110, 160)),
    "tuvok":   dict(skin=DEEP, hair=BLACK_H, style="bowl", vulcan=True, eyes=(40, 30, 24)),
    "archer":  dict(skin=LIGHT, hair=BROWN_H, style="short", eyes=(70, 90, 120)),
    "tpol":    dict(skin=LIGHT, hair=DBROWN_H, style="bowl", vulcan=True, eyes=(80, 60, 40)),
    "sato":    dict(skin=OLIVE, hair=BLACK_H, style="long", eyes=(40, 30, 24)),
    "burnham": dict(skin=BROWN, hair=BLACK_H, style="buzz", eyes=(40, 30, 24)),
    "pike":    dict(skin=LIGHT, hair=WHITE_H, style="swept", eyes=(70, 100, 140)),
    "obrien":  dict(skin=LIGHT, hair=(140, 92, 56), style="short", eyes=(80, 100, 90)),
    "bashir":  dict(skin=TAN, hair=BLACK_H, style="short", eyes=(50, 36, 26)),
}

# Kopf-Flächen: (x, y) der 8×8-Felder
TOP, BOTTOM, RIGHT, FRONT, LEFT, BACK = (8, 0), (16, 0), (0, 8), (8, 8), (16, 8), (24, 8)
HAT = 32  # Versatz der Hut-Ebene


def rgba(c, a=255):
    return tuple(c[:3]) + (a,)


def skin_image(spec, seed):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()

    def fill(x0, y0, w, h, c, var=4):
        for x in range(x0, x0 + w):
            for y in range(y0, y0 + h):
                px[x, y] = shade(rgba(c), rnd.randint(-var, var))

    sk = spec["skin"]
    # Kopf, Hals/Hände, Rumpf (Unterhemd), Beine (dunkel) – Uniform, Hose und Stiefel kommen im Spiel darüber
    for f in (TOP, BOTTOM, RIGHT, FRONT, LEFT, BACK):
        fill(f[0], f[1], 8, 8, sk, 3)
    fill(16, 16, 24, 16, (24, 24, 28))          # Rumpf
    fill(40, 16, 16, 16, sk, 3)                 # rechter Arm (Hände sichtbar)
    fill(32, 48, 16, 16, sk, 3)                 # linker Arm
    fill(0, 16, 16, 16, (20, 20, 24))           # rechtes Bein
    fill(16, 48, 16, 16, (20, 20, 24))          # linkes Bein

    def face(x, y, c):
        px[FRONT[0] + x, FRONT[1] + y] = rgba(c)

    def side_px(f, x, y, c, layer=0):
        px[f[0] + x + layer, f[1] + y] = rgba(c)

    hair = spec.get("hair")
    style = spec["style"]
    if hair:
        hv = lambda: shade(rgba(hair), rnd.randint(-10, 10))
        top_rows = 8
        for x in range(8):
            for y in range(8):
                px[TOP[0] + x, TOP[1] + y] = hv()
        if style in ("short", "mop", "swept", "slick", "bun", "updo", "buzz"):
            back_rows, side_rows, front_rows = 5, 2, 1
            if style == "slick":
                front_rows = 1
            if style == "buzz":
                back_rows, side_rows, front_rows = 3, 1, 1
            if style == "mop":
                front_rows = 2
            if style in ("bun", "updo"):
                back_rows = 6
        elif style in ("long", "curly_long"):
            back_rows, side_rows, front_rows = 8, 8, 1
        elif style == "bob":
            back_rows, side_rows, front_rows = 6, 6, 1
        elif style == "bowl":
            back_rows, side_rows, front_rows = 6, 4, 2
        elif style == "fringe":
            back_rows, side_rows, front_rows = 0, 0, 0
            for x in range(8):                                            # Glatze, Haarkranz an Seiten und hinten
                for y in range(8):
                    px[TOP[0] + x, TOP[1] + y] = shade(rgba(spec["skin"]), rnd.randint(-3, 3))
            for f in (RIGHT, LEFT):
                for x in range(8):
                    for y in (2, 3, 4):
                        if (f == RIGHT and x < 5) or (f == LEFT and x > 2):
                            px[f[0] + x, f[1] + y] = hv()
            for x in range(8):
                for y in (2, 3, 4, 5):
                    px[BACK[0] + x, BACK[1] + y] = hv()
        else:
            back_rows = side_rows = front_rows = 0
        for x in range(8):
            for y in range(back_rows):
                px[BACK[0] + x, BACK[1] + y] = hv()
        for f in (RIGHT, LEFT):
            for x in range(8):
                for y in range(8):
                    # hintere Hälfte der Seiten bis side_rows, vordere Hälfte bis 2 (bzw. Pony)
                    back_half = x < 4 if f == RIGHT else x >= 4
                    limit = side_rows if back_half or style in ("long", "curly_long", "bob") else min(side_rows, 2)
                    if y < limit:
                        px[f[0] + x, f[1] + y] = hv()
        for x in range(8):
            for y in range(front_rows):
                face(x, y, hv())
        if style in ("long", "curly_long", "bob"):                       # Haare rahmen das Gesicht
            for y in range(2, 7 if style == "bob" else 8):
                face(0, y, hv())
                face(7, y, hv())
        if style == "bowl":                                               # gerader Pony
            for x in range(8):
                face(x, 1, shade(rgba(hair), -12))
        if style == "curly_long":
            for (x, y) in ((1, 0), (3, 1), (5, 0), (6, 1)):
                px[BACK[0] + x, BACK[1] + y] = shade(rgba(hair), 18)
        if style in ("bun", "updo"):                                      # Dutt hinten auf der Hut-Ebene
            for x in range(2, 6):
                for y in range(1, 4):
                    px[BACK[0] + HAT + x, BACK[1] + y] = hv()
        if style == "swept":
            for x in range(1, 7):
                px[TOP[0] + x, TOP[1] + 7] = shade(rgba(hair), 15)
    # Gesicht
    brow = hair if hair else shade(rgba(sk), -50)
    if spec.get("vulcan"):                                                # schräge Augenbrauen
        face(1, 3, brow); face(2, 2, brow); face(5, 2, brow); face(6, 3, brow)
    else:
        face(1, 3, brow); face(2, 3, brow); face(5, 3, brow); face(6, 3, brow)
    eyes = spec.get("eyes", (60, 80, 110))
    face(1, 4, (236, 236, 236)); face(2, 4, eyes); face(5, 4, eyes); face(6, 4, (236, 236, 236))
    face(3, 5, shade(rgba(sk), -18)); face(4, 5, shade(rgba(sk), -18))
    face(3, 6, shade(rgba(sk), -45)); face(4, 6, shade(rgba(sk), -45))
    beard = spec.get("beard")
    if beard == "full":
        for x in range(8):
            for y in (5, 6, 7):
                if not (y == 5 and 2 < x < 5) and not (y == 6 and 2 < x < 5):
                    face(x, y, shade(rgba(hair), rnd.randint(-8, 8)))
        for f in (RIGHT, LEFT):
            for x in range(8):
                front_half = x >= 4 if f == RIGHT else x < 4
                if front_half:
                    for y in (5, 6, 7):
                        px[f[0] + x, f[1] + y] = shade(rgba(hair), rnd.randint(-8, 8))
    elif beard in ("goatee", "goatee_black"):
        bc = hair if beard == "goatee" else BLACK_H
        for x in range(2, 6):
            face(x, 7, bc)
        face(2, 6, bc); face(5, 6, bc)
        face(2, 5, bc); face(5, 5, bc)
    elif beard == "mustache":
        for x in range(2, 6):
            face(x, 5, hair)
    if spec.get("ridges"):                                                # Stirnwülste
        for x in range(1, 7):
            face(x, 1, shade(rgba(sk), -22 if x % 2 else 8))
            face(x, 2, shade(rgba(sk), 6 if x % 2 else -18))
    if spec.get("visor"):                                                 # silbernes Visier auf der Hut-Ebene
        for x in range(8):
            px[FRONT[0] + HAT + x, FRONT[1] + 4] = (200, 206, 214, 255)
            px[FRONT[0] + HAT + x, FRONT[1] + 3] = (150, 156, 166, 255) if x in (0, 7) else (0, 0, 0, 0)
        for f in (RIGHT, LEFT):
            xs = (6, 7) if f == RIGHT else (0, 1)
            for x in xs:
                px[f[0] + HAT + x, f[1] + 4] = (170, 176, 186, 255)
    if spec.get("implant"):                                               # Borg-Implantat über dem linken Auge
        for (x, y) in ((6, 3), (7, 3), (7, 4), (6, 2)):
            face(x, y, (176, 180, 188))
        px[LEFT[0] + 0, LEFT[1] + 3] = (176, 180, 188, 255)
    return img


def outfits():
    """Eigene Kleidung der Dorfbewohner im Schnittmuster einer Rüstung (64×32): Sevens Anzug, Kirks grüne Tunika."""
    folder = os.path.join(ASSETS, "textures", "entity", "starfleet")
    os.makedirs(folder, exist_ok=True)

    def base(color, seed, legs):
        rnd = random.Random(seed)
        img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
        px = img.load()
        regions = [(16, 16, 24, 16), (40, 16, 16, 16)] + ([(0, 16, 16, 16)] if legs else [])
        for x0, y0, w, h in regions:
            for x in range(x0, x0 + w):
                for y in range(y0, y0 + h):
                    if (x0, y0) != (16, 16) or not (y < 20 and (x < 20 or x >= 36)):   # leere Ecken des Rumpf-Schnitts
                        px[x, y] = shade(rgba(color), rnd.randint(-5, 5))
        return img, px

    # Seven of Nine: silbrig-blauer Anzug, hoher Kragen, dunklere Nähte, Schuhe in Anzugfarbe
    suit = (150, 162, 184)
    img, px = base(suit, 9501, True)
    for y in range(20, 32):                                              # Nähte vorn und an den Seiten
        px[23, y] = px[24, y] = shade(rgba(suit), -22)
        px[16, y] = px[27, y] = px[28, y] = px[31, y] = shade(rgba(suit), -16)
    for x in range(20, 28):                                              # hoher Kragen
        px[x, 20] = shade(rgba(suit), 16)
    for x in list(range(0, 16)):
        px[x, 31] = shade(rgba(suit), -40)                               # Schuhe
    img.save(os.path.join(folder, "outfit_seven.png"))

    # Kirk: grüne Wickeltunika (Originalserie), schwarzer V-Kragen, goldene Ärmelborten, Abzeichen
    green = (98, 120, 62)
    img, px = base(green, 9502, False)
    for i in range(7):                                                   # Wickelnaht diagonal über die Brust
        px[21 + i, 21 + i] = shade(rgba(green), -28)
    for (x, y) in ((23, 20), (24, 20), (23, 21), (24, 21), (22, 20), (25, 20)):
        px[x, y] = (24, 24, 26, 255)                                     # V-Kragen
    for x in range(40, 56):                                              # Borten an den Ärmeln
        px[x, 28] = (206, 170, 64, 255)
        px[x, 27] = (206, 170, 64, 255)
    px[26, 22] = px[26, 23] = (214, 180, 70, 255)                        # Abzeichen
    px[25, 23] = (214, 180, 70, 255)
    img.save(os.path.join(folder, "outfit_kirk_green.png"))


def skins():
    folder = os.path.join(ASSETS, "textures", "entity", "starfleet")
    os.makedirs(folder, exist_ok=True)
    for i, (name, spec) in enumerate(CREW.items()):
        skin_image(spec, 9000 + i).save(os.path.join(folder, f"skin_{name}.png"))


# ---------------------------------------------------------------------------------------------
#  Handel: hauptsächlich Uniformen, auf höheren Stufen Stühle und Deko – nie funktionale Blöcke
# ---------------------------------------------------------------------------------------------

DIVISIONS = ("command", "science", "operations")
# Stufe -> [(Gegenstand, Preis in Smaragden, Anzahl)]
TRADES = {
    1: [(f"uniform_nx_{d}", 4, 1) for d in DIVISIONS] + [(f"uniform_tos_{d}", 5, 1) for d in DIVISIONS] + [("trousers_black", 3, 1)],
    2: [(f"uniform_movie_{d}", 7, 1) for d in DIVISIONS] + [("trousers_nx", 3, 1), ("boots_starfleet", 4, 1),
                                                           ("tng_carpet", 1, 4), ("bridge_carpet_teal", 1, 4)],
    3: [(f"uniform_tng_{d}", 9, 1) for d in DIVISIONS] + [("station_chair", 6, 1), ("tng_chair", 6, 1), ("ent_chair", 6, 1),
                                                         ("station_chair_teal", 6, 1), ("shuttle_chair", 8, 1)],
    4: [(f"uniform_fc_{d}", 12, 1) for d in DIVISIONS] + [("conference_table", 8, 1), ("crew_desk", 8, 1), ("mess_table", 7, 1),
                                                         ("bridge_railing", 2, 4), ("bridge_railing_wood", 2, 4), ("cargo_container", 3, 2)],
    5: [("captains_chair", 20, 1), ("captains_chair_tng", 20, 1), ("captains_chair_nx", 20, 1), ("captains_chair_teal", 20, 1),
        ("situation_table", 16, 1), ("table_viewer", 14, 1), ("hull_pennant", 6, 4)],
}
AMOUNT = {1: 3, 2: 3, 3: 3, 4: 3, 5: 2}
XP = {1: 2, 2: 10, 3: 20, 4: 30, 5: 30}


def trade_data():
    tags_poi = os.path.join(ROOT, "data", "minecraft", "tags", "point_of_interest_type", "acquirable_job_site.json")
    write_json(tags_poi, {"replace": False, "values": [f"{MODID}:shuttle_chair"]})
    for level, trades in TRADES.items():
        ids = []
        for item, price, count in trades:
            name = f"starfleet/{level}/{item}"
            write_json(os.path.join(DATA, "villager_trade", name + ".json"), {
                "gives": {"id": f"{MODID}:{item}", "count": count},
                "max_uses": 8.0 if level < 5 else 3.0,
                "reputation_discount": 0.05,
                "wants": {"id": "minecraft:emerald", "count": float(price)},
                "xp": float(XP[level]),
            })
            ids.append(f"{MODID}:{name}")
        write_json(os.path.join(DATA, "trade_set", "starfleet", f"level_{level}.json"), {
            "amount": float(AMOUNT[level]),
            "random_sequence": f"{MODID}:trade_set/starfleet/level_{level}",
            "trades": ids,
        })


def lang(de, en):
    de.update({
        f"block.{MODID}.shuttle_chair": "Shuttle-Pilotensitz",
        f"block.{MODID}.space_bed": "Raumschiff-Bett",
        f"entity.{MODID}.villager.starfleet": "Sternenflotten-Offizier",
    })
    en.update({
        f"block.{MODID}.shuttle_chair": "Shuttle Pilot Seat",
        f"block.{MODID}.space_bed": "Starship Bed",
        f"entity.{MODID}.villager.starfleet": "Starfleet Officer",
    })


def run(de, en):
    textures()
    block_assets()
    skins()
    outfits()
    trade_data()
    lang(de, en)
    import starfleet_house
    starfleet_house.run(g)
