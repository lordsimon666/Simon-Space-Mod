#!/usr/bin/env python3
"""Wandelt ein (geschlossenes) Binär-STL-Modell in ein Blockraster um ("Voxelisieren").

Aufruf:
    python3 tools/voxelize_stl.py MODELL.stl LÄNGE_IN_BLÖCKEN AUSGABE.bin.gz [VORSCHAU.png]

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
    return tris['v'].astype(np.float64)


def voxelize(tris, length):
    lo = tris.reshape(-1, 3).min(0)
    hi = tris.reshape(-1, 3).max(0)
    size = hi - lo
    order = np.argsort(-size)              # längste, mittlere, kürzeste Achse
    ax_len, ax_beam, ax_up = order
    scale = (length - 1) / size[ax_len]
    t = (tris - lo) * scale                # in Blöcken
    t = t[:, :, [ax_len, ax_up, ax_beam]]  # -> (Länge, Höhe, Breite)
    sx = int(np.ceil(size[ax_len] * scale)) + 1
    sy = int(np.ceil(size[ax_up] * scale)) + 1
    sz = int(np.ceil(size[ax_beam] * scale)) + 1

    # Strahlen entlang der Hochachse (y) durch die Mitte jeder (x, z)-Säule
    hits = [[] for _ in range(sx * sz)]
    for tri in t:
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
                hits[ix * sz + iz].append(l1 * a[1] + l2 * b[1] + l3 * c[1])

    grid = np.zeros((sx, sy, sz), dtype=bool)
    for ix in range(sx):
        for iz in range(sz):
            h = hits[ix * sz + iz]
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
    stl, length, out = sys.argv[1], int(sys.argv[2]), sys.argv[3]
    g = orient_bow_first(voxelize(load_stl(stl), length))
    save(g, out)
    print('size (Länge, Höhe, Breite):', g.shape, 'Blöcke:', int(g.sum()))
    if len(sys.argv) > 4:
        preview(g, sys.argv[4])
