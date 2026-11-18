"""Strukturvorlage data/simonspacemod/structure/village/shuttle_house.nbt: ein umgebautes Shuttle zum Wohnen.

Braucht nbtlib (pip install nbtlib). Aufbau (x = Länge, Bug bei x = 10; z = Breite, Eingang bei z = 0):
Rumpf aus Hüllenplatten mit abgeschrägten Dachkanten, ausgebessert mit dunklen Platten, Cockpitfenster vorn,
ausgebaute Impulsantriebe hinten. Innen: Raumschiff-Bett, Shuttle-Pilotensitz am alten Steuerpult (Arbeitsplatz),
Truhe, Werkbank, Blumentopf; Holztür in der Seitenwand, davor der Anschluss an den Dorfweg. Ein
Sternenflotten-Dorfbewohner wohnt schon darin.
"""
import os
import random

try:
    import nbtlib
    from nbtlib import Byte, Compound, Double, Int, List, String
except ImportError:  # pragma: no cover
    nbtlib = None

MODID = "simonspacemod"
DATA_VERSION = 4903  # wie die Dorfhäuser von Minecraft 26.2


def run(g):
    if nbtlib is None:
        print("nbtlib fehlt – shuttle_house.nbt wird nicht neu erzeugt")
        return
    rnd = random.Random(7010)
    palette = []
    index = {}

    def state(name, **props):
        key = (name, tuple(sorted(props.items())))
        if key not in index:
            entry = {"Name": String(name)}
            if props:
                entry["Properties"] = Compound({k: String(str(v)) for k, v in props.items()})
            index[key] = len(palette)
            palette.append(Compound(entry))
        return index[key]

    blocks = {}

    def put(x, y, z, s, nbt=None):
        blocks[(x, y, z)] = (s, nbt)

    m = lambda n: f"{MODID}:{n}"
    air = state("minecraft:air")
    hull = state(m("hull_plating"))
    patch = state(m("hull_dark"))
    panel = state(m("hull_panel"))
    deck = state(m("deck_plate"))
    window = state(m("hull_window"))

    X0, X1, Z0, Z1, TOP = 1, 9, 1, 5, 4
    for x in range(X0, X1 + 1):
        for y in range(0, TOP + 1):
            for z in range(Z0, Z1 + 1):
                shell = x in (X0, X1) or y in (0, TOP) or z in (Z0, Z1)
                if (z in (Z0, Z1)) and y == TOP:
                    continue                                              # abgeschrägte Dachkanten
                if not shell:
                    put(x, y, z, air)
                elif y == 0:
                    put(x, y, z, deck)
                elif y == TOP:
                    put(x, y, z, panel)
                else:
                    put(x, y, z, patch if rnd.random() < 0.14 else hull)  # ausgebesserte Stellen
    # Bug mit Cockpitfenstern
    for z in range(2, 5):
        put(X1, 2, z, window)
        put(X1, 3, z, window)
        put(X1 + 1, 1, z, panel)
        put(X1 + 1, 2, z, panel)
    # Seitenfenster
    put(3, 2, Z1, window)
    put(7, 2, Z1, window)
    put(3, 2, Z0, window)
    # alte Impulsantriebe hinten
    put(X0 - 1, 2, 2, state(m("impulse_engine")))
    put(X0 - 1, 2, 4, state(m("impulse_engine")))
    # Positionslichter am Dach
    put(8, TOP, 2, state(m("nav_light_red")))
    put(8, TOP, 4, state(m("nav_light_green")))

    # Eingang: Holztür in der Seitenwand (z = 1), davor der Anschluss an den Weg
    put(5, 1, Z0, state("minecraft:spruce_door", facing="south", half="lower", hinge="left", open="false", powered="false"))
    put(5, 2, Z0, state("minecraft:spruce_door", facing="south", half="upper", hinge="left", open="false", powered="false"))
    put(5, 0, 0, state("minecraft:jigsaw", orientation="north_up"), Compound({
        "id": String("minecraft:jigsaw"), "name": String("minecraft:building_entrance"),
        "target": String("minecraft:building_entrance"), "pool": String("minecraft:empty"),
        "joint": String("aligned"), "final_state": String("minecraft:dirt_path"),
        "selection_priority": Int(0), "placement_priority": Int(0)}))

    # Einrichtung
    put(2, 1, 4, state(m("space_bed"), facing="west", part="head", occupied="false"))
    put(3, 1, 4, state(m("space_bed"), facing="west", part="foot", occupied="false"))
    put(7, 1, 3, state(m("shuttle_chair"), facing="east"))               # Arbeitsplatz: Blick zum Cockpit
    put(8, 1, 3, state(m("helm_console"), facing="west"))
    put(2, 1, 2, state("minecraft:chest", facing="south", type="single", waterlogged="false"), Compound({
        "id": String("minecraft:chest"), "LootTable": String("minecraft:chests/village/village_plains_house")}))
    put(3, 1, 2, state("minecraft:crafting_table"))
    put(8, 1, 2, state("minecraft:potted_red_tulip"))
    put(8, 1, 4, state("minecraft:potted_fern"))

    entities = List[Compound]([Compound({
        "pos": List[Double]([Double(5.5), Double(1.0), Double(3.5)]),
        "blockPos": List[Int]([Int(5), Int(1), Int(3)]),
        "nbt": Compound({
            "id": String("minecraft:villager"),
            "VillagerData": Compound({"profession": String(f"{MODID}:starfleet"), "type": String("minecraft:plains"),
                                      "level": Int(1)}),
            "PersistenceRequired": Byte(1),
        }),
    })])

    xs = [p[0] for p in blocks]
    ys = [p[1] for p in blocks]
    zs = [p[2] for p in blocks]
    size = [max(xs) + 1, max(ys) + 1, max(zs) + 1]
    block_list = []
    for (x, y, z), (s, nbt) in sorted(blocks.items()):
        b = {"pos": List[Int]([Int(x), Int(y), Int(z)]), "state": Int(s)}
        if nbt is not None:
            b["nbt"] = nbt
        block_list.append(Compound(b))
    root = Compound({
        "size": List[Int]([Int(v) for v in size]),
        "entities": entities,
        "blocks": List[Compound](block_list),
        "palette": List[Compound](palette),
        "DataVersion": Int(DATA_VERSION),
    })
    path = os.path.join(g.DATA, "structure", "village", "shuttle_house.nbt")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    nbtlib.File(root).save(path, gzipped=True)
