#!/usr/bin/env python3
"""Wandelt ein (geschlossenes) Binär-STL-Modell in ein Blockraster um ("Voxelisieren").

Aufruf:
    python3 tools/voxelize_stl.py MODELL.stl LÄNGE_IN_BLÖCKEN AUSGABE.bin.gz [VORSCHAU.png] [--vote | --winding]

--vote: jedes Teilnetz aus drei Richtungen füllen, Mehrheit entscheidet (am robustesten; NCC-1701-F).

--winding: innen/außen nach der Richtung der Flächen statt nach Gerade/Ungerade (robuster bei offenen
Flächen wie Aufklebern; verwendet für die NCC-1701-F).

Das Modell wird so gedreht, dass die längste Achse die Schiffslänge ist (Bug bei x = 0),
die kürzeste Achse nach oben zeigt und die mittlere die Breite ist. Gefüllt wird mit
Strahlen entlang der Hochachse (gerade/ungerade Schnittregel), d.h. das Innere des
Rumpfes ist massiv – die Mod höhlt es später selbst aus und baut Decks und Räume hinein.

Ausgabeformat (gzip): b"SHIP", int32 sizeX (Länge), sizeY (Höhe), sizeZ (Breite), danach
ein Bitfeld in der Reihenfolge x, y, z (z am schnellsten), 1 = Hülle/massiv.
"""
import gzip
import struct
import sys

import numpy as np


def load_stl(path):
    dt = np.dtype([('n', '<f4', 3), ('v', '<f4', (3, 3)), ('a', '<u2')])
    with open(path, 'rb') as f:
        f.read(80)
        n = struct.unpack('<I', f.read(4))[0]
        tris = np.frombuffer(f.read(50 * n), dt)
    return tris['v'].astype(np.float64), tris['a'].astype(np.int64)


def voxelize(tris, length, groups=None, winding=False):
    """groups: Teilnetz je Dreieck (aus dem Attributfeld der STL). Jedes Teilnetz wird für sich nach der
    Gerade/Ungerade-Regel gefüllt und die Ergebnisse vereinigt – so heben sich überlappende Teile
    (z. B. Untertasse und Sekundärrumpf) nicht gegenseitig auf."""
    if groups is None:
        groups = np.zeros(len(tris), dtype=np.int64)
    # Flächenrichtung (für winding): Normale im Modellraum; Teilnetze, die nach innen gewickelt sind
    # (negatives Volumen), werden umgedreht
    normals = np.cross(tris[:, 1] - tris[:, 0], tris[:, 2] - tris[:, 0])
    if winding:
        vol = np.einsum('ij,ij->i', tris[:, 0], np.cross(tris[:, 1], tris[:, 2]))
        for g in np.unique(groups):
            if vol[groups == g].sum() < 0:
                normals[groups == g] *= -1
    lo = tris.reshape(-1, 3).min(0)
    hi = tris.reshape(-1, 3).max(0)
    size = hi - lo
    order = np.argsort(-size)              # längste, mittlere, kürzeste Achse
    ax_len, ax_beam, ax_up = order
    up_sign = np.sign(normals[:, ax_up])  # < 0: Fläche zeigt nach unten = Strahl von unten tritt ein
    scale = (length - 1) / size[ax_len]
    t = (tris - lo) * scale                # in Blöcken
    t = t[:, :, [ax_len, ax_up, ax_beam]]  # -> (Länge, Höhe, Breite)
    sx = int(np.ceil(size[ax_len] * scale)) + 1
    sy = int(np.ceil(size[ax_up] * scale)) + 1
    sz = int(np.ceil(size[ax_beam] * scale)) + 1

    # Strahlen entlang der Hochachse (y) durch die Mitte jeder (x, z)-Säule
    hits = [[] for _ in range(sx * sz)]
    for tri, grp, us in zip(t, groups, up_sign):
        a, b, c = tri
        x0, x1 = min(a[0], b[0], c[0]), max(a[0], b[0], c[0])
        z0, z1 = min(a[2], b[2], c[2]), max(a[2], b[2], c[2])
        ix0, ix1 = int(np.ceil(x0 - 0.5)), int(np.floor(x1 - 0.5))
        iz0, iz1 = int(np.ceil(z0 - 0.5)), int(np.floor(z1 - 0.5))
        if ix1 < ix0 or iz1 < iz0:
            continue
        # Baryzentrische Koordinaten in der x-z-Ebene
        d = (b[2] - c[2]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[2] - c[2])
        if abs(d) < 1e-12:
            continue
        for ix in range(max(ix0, 0), min(ix1, sx - 1) + 1):
            px = ix + 0.5
            for iz in range(max(iz0, 0), min(iz1, sz - 1) + 1):
                pz = iz + 0.5
                l1 = ((b[2] - c[2]) * (px - c[0]) + (c[0] - b[0]) * (pz - c[2])) / d
                l2 = ((c[2] - a[2]) * (px - c[0]) + (a[0] - c[0]) * (pz - c[2])) / d
                l3 = 1 - l1 - l2
                if l1 < 0 or l2 < 0 or l3 < 0:
                    continue
                hits[ix * sz + iz].append((int(grp), l1 * a[1] + l2 * b[1] + l3 * c[1], us))

    grid = np.zeros((sx, sy, sz), dtype=bool)
    for ix in range(sx):
        for iz in range(sz):
            col = hits[ix * sz + iz]
            if len(col) < 2:
                continue
            by_group = {}
            for g, v, us in col:
                by_group.setdefault(g, []).append((v, us))
            for hs in by_group.values():
                if winding:
                    # Eintritt (Fläche nach unten) +1, Austritt (Fläche nach oben) -1; innen, solange > 0.
                    # Offene Flächen (Aufkleber) ohne Gegenstück füllen so nichts zwischen Untertasse und Rumpf.
                    hs.sort()
                    depth = 0
                    for k, (v, us) in enumerate(hs):
                        depth = max(0, depth + (1 if us < 0 else -1 if us > 0 else 0))
                        if depth > 0 and k + 1 < len(hs):
                            y0, y1 = v, hs[k + 1][0]
                            grid[ix, max(0, int(np.floor(y0 + 0.5))):min(sy, int(np.floor(y1 + 0.5)) + 1), iz] = True
                    continue
                h = [v for v, _ in hs]
                if len(h) < 2:
                    continue
                h.sort()
                # doppelte Treffer (Kanten zwischen zwei Dreiecken) zusammenfassen
                u = [h[0]]
                for v in h[1:]:
                    if v - u[-1] > 1e-6:
                        u.append(v)
                for k in range(0, len(u) - 1, 2):
                    y0, y1 = u[k], u[k + 1]
                    grid[ix, max(0, int(np.floor(y0 + 0.5))):min(sy, int(np.floor(y1 + 0.5)) + 1), iz] = True
    return grid



def _fill_vertical(t, sx, sy, sz):
    """Gerade/Ungerade-Füllung mit Strahlen entlang Index 1 (t: Dreiecke in Blockkoordinaten)."""
    hits = [[] for _ in range(sx * sz)]
    for a, b, c in t:
        x0, x1 = min(a[0], b[0], c[0]), max(a[0], b[0], c[0])
        z0, z1 = min(a[2], b[2], c[2]), max(a[2], b[2], c[2])
        ix0, ix1 = int(np.ceil(x0 - 0.5)), int(np.floor(x1 - 0.5))
        iz0, iz1 = int(np.ceil(z0 - 0.5)), int(np.floor(z1 - 0.5))
        if ix1 < ix0 or iz1 < iz0:
            continue
        d = (b[2] - c[2]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[2] - c[2])
        if abs(d) < 1e-12:
            continue
        for ix in range(max(ix0, 0), min(ix1, sx - 1) + 1):
            px = ix + 0.5
            for iz in range(max(iz0, 0), min(iz1, sz - 1) + 1):
                pz = iz + 0.5
                l1 = ((b[2] - c[2]) * (px - c[0]) + (c[0] - b[0]) * (pz - c[2])) / d
                l2 = ((c[2] - a[2]) * (px - c[0]) + (a[0] - c[0]) * (pz - c[2])) / d
                l3 = 1 - l1 - l2
                if l1 < 0 or l2 < 0 or l3 < 0:
                    continue
                hits[ix * sz + iz].append(l1 * a[1] + l2 * b[1] + l3 * c[1])
    grid = np.zeros((sx, sy, sz), dtype=bool)
    for ix in range(sx):
        for iz in range(sz):
            h = hits[ix * sz + iz]
            if len(h) < 2:
                continue
            h.sort()
            u = [h[0]]
            for v in h[1:]:
                if v - u[-1] > 1e-6:
                    u.append(v)
            for k in range(0, len(u) - 1, 2):
                grid[ix, max(0, int(np.floor(u[k] + 0.5))):min(sy, int(np.floor(u[k + 1] + 0.5)) + 1), iz] = True
    return grid


def voxelize_vote(tris, length, groups):
    """Robust: jedes Teilnetz mit Strahlen in drei Richtungen füllen; massiv, wenn mindestens zwei Richtungen
    „innen“ sagen. Offene Flächen (Aufkleber, Leuchtstreifen), die nach Gerade/Ungerade ganze Spalten füllen
    würden, werden so von den anderen Richtungen überstimmt. Ergebnis: Vereinigung aller Teilnetze."""
    lo = tris.reshape(-1, 3).min(0)
    hi = tris.reshape(-1, 3).max(0)
    size = hi - lo
    ax_len, ax_beam, ax_up = np.argsort(-size)
    scale = (length - 1) / size[ax_len]
    t = ((tris - lo) * scale)[:, :, [ax_len, ax_up, ax_beam]]
    sx = int(np.ceil(size[ax_len] * scale)) + 1
    sy = int(np.ceil(size[ax_up] * scale)) + 1
    sz = int(np.ceil(size[ax_beam] * scale)) + 1
    out = np.zeros((sx, sy, sz), dtype=bool)
    for g in np.unique(groups):
        tg = t[groups == g]
        votes = _fill_vertical(tg, sx, sy, sz).astype(np.uint8)
        # Strahlen entlang x: Index 0 <-> 1 tauschen
        gx = _fill_vertical(tg[:, :, [1, 0, 2]], sy, sx, sz)
        votes += gx.transpose(1, 0, 2)
        # Strahlen entlang z: Index 2 <-> 1 tauschen
        gz = _fill_vertical(tg[:, :, [0, 2, 1]], sx, sz, sy)
        votes += gz.transpose(0, 2, 1)
        out |= votes >= 2
        print(f"  Teilnetz {g}: {int((votes >= 2).sum())} Blöcke", flush=True)
    out = keep_main_body(out)
    return fill_enclosed(out)



def spread(mask, start):
    """Alle Zellen von mask, die mit start (Teilmenge von mask) über Nachbarn verbunden sind. Je Durchgang
    übernimmt entlang jeder Achse eine ganze zusammenhängende Strecke, sobald eine Zelle darin erreicht ist."""
    reach = start & mask
    for _ in range(400):
        before = int(reach.sum())
        for axis in range(3):
            e = np.moveaxis(mask, axis, -1)
            x = np.moveaxis(reach, axis, -1)
            seg = np.cumsum(~e, axis=-1)
            n = e.shape[-1] + 1
            base = np.arange(e.shape[0] * e.shape[1]).reshape(e.shape[0], e.shape[1], 1) * n
            ids = base + seg
            hit = np.zeros(e.shape[0] * e.shape[1] * n, dtype=bool)
            hit[ids[x & e]] = True
            add = np.zeros_like(e)
            add[e] = hit[ids[e]]
            x |= add
        if int(reach.sum()) == before:
            break
    return reach


def keep_main_body(grid):
    """Nur den zusammenhängenden Schiffskörper behalten (lose Splitter aus dem Modell fallen weg).
    Start: die massive Zelle mit den meisten massiven Zellen in ihrer Spalte (sicher im Rumpf)."""
    counts = grid.sum(axis=1)
    ix, iz = np.unravel_index(np.argmax(counts), counts.shape)
    iy = int(np.nonzero(grid[ix, :, iz])[0].mean())
    while not grid[ix, iy, iz]:
        iy += 1
    start = np.zeros_like(grid)
    start[ix, iy, iz] = True
    return spread(grid, start)


def fill_enclosed(grid):
    """Leere Bereiche, die nicht mit dem Außenraum verbunden sind (Hohlräume im Rumpf), werden massiv.
    Außenluft wird vom Rand her ausgebreitet: entlang jeder Achse übernimmt eine ganze leere Strecke die
    Außenluft, sobald ein Block davon außen ist (wenige Durchgänge statt Block-für-Block-Füllung)."""
    empty = ~grid
    ext = np.zeros_like(grid)
    ext[0, :, :] = empty[0, :, :]; ext[-1, :, :] = empty[-1, :, :]
    ext[:, 0, :] = empty[:, 0, :]; ext[:, -1, :] = empty[:, -1, :]
    ext[:, :, 0] = empty[:, :, 0]; ext[:, :, -1] = empty[:, :, -1]
    for it in range(200):
        before = int(ext.sum())
        for axis in range(3):
            e = np.moveaxis(empty, axis, -1)
            x = np.moveaxis(ext, axis, -1)
            # Nummer der leeren Strecke je Zelle (steigt an jedem massiven Block)
            seg = np.cumsum(~e, axis=-1)
            n = e.shape[-1] + 1
            base = (np.arange(e.shape[0] * e.shape[1]).reshape(e.shape[0], e.shape[1], 1) * n)
            key = (base + seg)[e]
            hit = np.zeros(e.shape[0] * e.shape[1] * n, dtype=bool)
            hit[(base + seg)[x & e]] = True
            spread = np.zeros_like(e)
            spread[e] = hit[key]
            x |= spread
        after = int(ext.sum())
        if after == before:
            break
    return grid | (empty & ~ext)


def orient_bow_first(grid):
    """Bug an x = 0: am Bug ist die Mittellinie (Untertasse) gefüllt, am Heck sind es zwei Gondeln."""
    sx, sy, sz = grid.shape
    mid = sz // 2
    k = max(1, int(sx * 0.03))
    front = grid[:k, :, mid - 2:mid + 3].sum()
    back = grid[-k:, :, mid - 2:mid + 3].sum()
    return grid if front >= back else grid[::-1]


def save(grid, path):
    sx, sy, sz = grid.shape
    bits = np.packbits(grid.reshape(-1).astype(np.uint8))
    with gzip.open(path, 'wb') as f:
        f.write(b'SHIP' + struct.pack('>iii', sx, sy, sz) + bits.tobytes())


def preview(grid, path):
    from PIL import Image
    sx, sy, sz = grid.shape
    side = grid.any(axis=2).T[::-1]            # (y, x)
    top = grid.any(axis=1).T                   # (z, x)
    front = grid.any(axis=0)[::-1]             # (y, z)
    w = max(sx, sz)
    img = Image.new('L', (w, sy + sz + sy + 4), 0)
    img.paste(Image.fromarray((side * 255).astype(np.uint8)), (0, 0))
    img.paste(Image.fromarray((top * 255).astype(np.uint8)), (0, sy + 2))
    img.paste(Image.fromarray((front * 255).astype(np.uint8)), (0, sy + sz + 4))
    img.save(path)


if __name__ == '__main__':
    winding = "--winding" in sys.argv
    vote = "--vote" in sys.argv
    args = [a for a in sys.argv[1:] if a not in ("--winding", "--vote")]
    sys.argv = [sys.argv[0]] + args
    stl, length, out = sys.argv[1], int(sys.argv[2]), sys.argv[3]
    tris, groups = load_stl(stl)
    g = orient_bow_first(voxelize_vote(tris, length, groups) if vote else voxelize(tris, length, groups, winding))
    save(g, out)
    print('size (Länge, Höhe, Breite):', g.shape, 'Blöcke:', int(g.sum()))
    if len(sys.argv) > 4:
        preview(g, sys.argv[4])
