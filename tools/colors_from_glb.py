#!/usr/bin/env python3
"""Überträgt die Farben eines texturierten 3D-Modells (GLB) auf das Blockraster eines Schiffs.

Aufruf:
    python3 tools/colors_from_glb.py MODELL.glb SCHIFF.bin.gz AUSGABE.colors.gz [VORSCHAU.png]

Das GLB-Modell wird auf das vorhandene Blockraster (aus tools/voxelize_stl.py) ausgerichtet
(Achsen nach Größe sortiert, Vorzeichen per Überdeckung gewählt, Größe auf das Raster gestreckt).
Dann bekommt jeder Oberflächenblock die Farbe der nächsten Modelloberfläche, eingeteilt in
Farbklassen (siehe CLASSES). Leuchtende Teile (Emission) werden eigens erkannt.

Ausgabe (gzip): b"COLR", int32 sx, sy, sz, danach je Block ein Byte Farbklasse (0 = keine),
Reihenfolge x, y, z (z am schnellsten) – wie das Blockraster.
"""
import gzip
import io
import json
import struct
import sys

import numpy as np
from PIL import Image

CLASSES = ["NONE", "LIGHT", "MID", "DARK", "BLACK", "RED", "TEAL", "BLUEPAINT", "COPPER",
           "RED_GLOW", "ORANGE_GLOW", "BLUE_GLOW", "VIOLET_GLOW", "WINDOW"]
C = {n: i for i, n in enumerate(CLASSES)}

COMP = {5120: np.int8, 5121: np.uint8, 5122: np.int16, 5123: np.uint16, 5125: np.uint32, 5126: np.float32}
NCOMP = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}


def load_glb(path):
    data = open(path, "rb").read()
    off = 12
    j = binc = None
    while off < len(data):
        clen, ctype = struct.unpack_from("<II", data, off)
        chunk = data[off + 8:off + 8 + clen]
        if ctype == 0x4E4F534A:
            j = json.loads(chunk)
        elif ctype == 0x004E4942:
            binc = chunk
        off += 8 + clen
    return j, binc


def accessor(j, binc, i):
    a = j["accessors"][i]
    bv = j["bufferViews"][a["bufferView"]]
    dt = np.dtype(COMP[a["componentType"]])
    n = NCOMP[a["type"]]
    start = bv.get("byteOffset", 0) + a.get("byteOffset", 0)
    stride = bv.get("byteStride", 0)
    count = a["count"]
    if stride and stride != dt.itemsize * n:
        raw = np.frombuffer(binc, np.uint8, count * stride, start).reshape(count, stride)
        arr = raw[:, :dt.itemsize * n].copy().view(dt).reshape(count, n)
    else:
        arr = np.frombuffer(binc, dt, count * n, start).reshape(count, n)
    if a.get("normalized") and dt != np.float32:
        arr = arr.astype(np.float64) / np.iinfo(dt).max
    return arr.astype(np.float64) if n > 1 else arr.reshape(-1)


def image(j, binc, tex_index, cache):
    if tex_index is None:
        return None
    src = j["textures"][tex_index]["source"]
    if src not in cache:
        im = j["images"][src]
        bv = j["bufferViews"][im["bufferView"]]
        raw = binc[bv.get("byteOffset", 0):bv.get("byteOffset", 0) + bv["byteLength"]]
        img = Image.open(io.BytesIO(raw)).convert("RGB")
        if max(img.size) > 2048:
            img.thumbnail((2048, 2048))
        cache[src] = np.asarray(img)
    return cache[src]


def node_matrix(n):
    if "matrix" in n:
        return np.array(n["matrix"], dtype=np.float64).reshape(4, 4).T
    t = np.array(n.get("translation", [0, 0, 0]), dtype=np.float64)
    q = n.get("rotation", [0, 0, 0, 1])
    s = np.array(n.get("scale", [1, 1, 1]), dtype=np.float64)
    x, y, z, w = q
    r = np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                  [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                  [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])
    m = np.eye(4)
    m[:3, :3] = r * s
    m[:3, 3] = t
    return m


def sample_model(path, n_samples):
    j, binc = load_glb(path)
    cache = {}
    tris, uvs, mats = [], [], []
    def visit(ni, parent):
        node = j["nodes"][ni]
        m = parent @ node_matrix(node)
        if "mesh" in node:
            for prim in j["meshes"][node["mesh"]]["primitives"]:
                if prim.get("mode", 4) != 4:
                    continue
                pos = accessor(j, binc, prim["attributes"]["POSITION"])
                pos = (np.c_[pos, np.ones(len(pos))] @ m.T)[:, :3]
                uv = accessor(j, binc, prim["attributes"]["TEXCOORD_0"]) if "TEXCOORD_0" in prim["attributes"] else np.zeros((len(pos), 2))
                idx = accessor(j, binc, prim["indices"]).astype(np.int64) if "indices" in prim else np.arange(len(pos))
                idx = idx.reshape(-1, 3)
                tris.append(pos[idx])
                uvs.append(uv[idx])
                mats.append(np.full(len(idx), prim.get("material", -1)))
        for c in node.get("children", []):
            visit(c, m)
    scene = j["scenes"][j.get("scene", 0)]
    for r in scene["nodes"]:
        visit(r, np.eye(4))
    T = np.concatenate(tris)
    U = np.concatenate(uvs)
    M = np.concatenate(mats)
    area = 0.5 * np.linalg.norm(np.cross(T[:, 1] - T[:, 0], T[:, 2] - T[:, 0]), axis=1)
    rng = np.random.default_rng(1)
    pick = rng.choice(len(T), n_samples, p=area / area.sum())
    a, b = rng.random(n_samples), rng.random(n_samples)
    flip = a + b > 1
    a[flip], b[flip] = 1 - a[flip], 1 - b[flip]
    w0 = 1 - a - b
    P = T[pick, 0] * w0[:, None] + T[pick, 1] * a[:, None] + T[pick, 2] * b[:, None]
    UV = U[pick, 0] * w0[:, None] + U[pick, 1] * a[:, None] + U[pick, 2] * b[:, None]
    base = np.zeros((n_samples, 3))
    emis = np.zeros((n_samples, 3))
    for mi in np.unique(M[pick]):
        sel = M[pick] == mi
        mat = j["materials"][mi] if mi >= 0 else {}
        sg = mat.get("extensions", {}).get("KHR_materials_pbrSpecularGlossiness", {})
        pbr = mat.get("pbrMetallicRoughness", {})
        btex = (sg.get("diffuseTexture") or pbr.get("baseColorTexture") or {}).get("index")
        bfac = np.array((sg.get("diffuseFactor") or pbr.get("baseColorFactor") or [1, 1, 1, 1])[:3])
        etex = (mat.get("emissiveTexture") or {}).get("index")
        efac = np.array(mat.get("emissiveFactor", [0, 0, 0]))
        uv = UV[sel] % 1.0
        img = image(j, binc, btex, cache)
        if img is not None:
            h, w = img.shape[:2]
            base[sel] = img[(uv[:, 1] * h).astype(int).clip(0, h - 1), (uv[:, 0] * w).astype(int).clip(0, w - 1)] * bfac
        else:
            base[sel] = 255 * bfac
        img = image(j, binc, etex, cache)
        if img is not None and efac.max() > 0:
            h, w = img.shape[:2]
            emis[sel] = img[(uv[:, 1] * h).astype(int).clip(0, h - 1), (uv[:, 0] * w).astype(int).clip(0, w - 1)] * efac
        else:
            emis[sel] = 255 * efac if etex is None and efac.max() > 0 else 0
    return P, base.clip(0, 255), emis.clip(0, 255)


def load_grid(path):
    d = gzip.open(path).read()
    sx, sy, sz = struct.unpack(">iii", d[4:16])
    g = np.unpackbits(np.frombuffer(d[16:], np.uint8))[:sx * sy * sz].reshape(sx, sy, sz).astype(bool)
    return g


def surface(g):
    s = np.zeros_like(g)
    p = np.pad(g, 1)
    inner = p[2:, 1:-1, 1:-1] & p[:-2, 1:-1, 1:-1] & p[1:-1, 2:, 1:-1] & p[1:-1, :-2, 1:-1] & p[1:-1, 1:-1, 2:] & p[1:-1, 1:-1, :-2]
    return g & ~inner


def align(P, g):
    """Bringt die Punkte ins Raster: Achsen nach Größe (Länge, Breite, Höhe), beste Vorzeichen,
    danach Feinabgleich von Streckung und Verschiebung je Achse (größte Überdeckung)."""
    sx, sy, sz = g.shape
    size = np.array([sx, sy, sz])
    ext = P.max(0) - P.min(0)
    order = np.argsort(-ext)              # Länge, Breite, Höhe
    Q = P[:, [order[0], order[2], order[1]]]  # -> (Länge, Höhe, Breite)
    lo, hi = np.percentile(Q, 0.2, axis=0), np.percentile(Q, 99.8, axis=0)
    surf = surface(g)
    dil = surf.copy()
    for ax in range(3):
        dil |= np.roll(surf, 1, ax) | np.roll(surf, -1, ax)
    glo = np.array([np.argwhere(g.any((1, 2)))[[0, -1]].ravel(), np.argwhere(g.any((0, 2)))[[0, -1]].ravel(),
                    np.argwhere(g.any((0, 1)))[[0, -1]].ravel()], dtype=float)  # belegter Bereich je Achse
    sub = Q[:: max(1, len(Q) // 150000)]

    def score(f, a0, a1, pts):
        R = (pts - lo) / (hi - lo)
        R = np.where(f > 0, R, 1 - R)
        vox = (a0 + R * (a1 - a0) + 0.5).astype(int)
        ok = (vox >= 0).all(1) & (vox < size).all(1)
        v = vox[ok]
        return dil[v[:, 0], v[:, 1], v[:, 2]].sum() / len(pts), vox
    best = None
    for fx in (1, -1):
        for fy in (1, -1):
            for fz in (1, -1):
                f = np.array([fx, fy, fz])
                sc, _ = score(f, glo[:, 0], glo[:, 1], sub)
                if best is None or sc > best[0]:
                    best = (sc, f)
    f = best[1]
    a0, a1 = glo[:, 0].copy(), glo[:, 1].copy()
    cur = best[0]
    for step in (4, 2, 1, 0.5):
        changed = True
        while changed:
            changed = False
            for ax in range(3):
                for which in (0, 1):
                    for d in (-step, step):
                        t0, t1 = a0.copy(), a1.copy()
                        (t0 if which == 0 else t1)[ax] += d
                        sc, _ = score(f, t0, t1, sub)
                        if sc > cur + 1e-4:
                            cur, a0, a1, changed = sc, t0, t1, True
    print("Ausrichtung: Vorzeichen %s, Überdeckung %.2f" % (f, cur))
    return score(f, a0, a1, Q)[1]


def classify(base, emis):
    cls = np.zeros(len(base), np.uint8)
    r, g, b = base[:, 0], base[:, 1], base[:, 2]
    er, eg, eb = emis[:, 0], emis[:, 1], emis[:, 2]
    emax = emis.max(1)
    glow = emax > 110
    mx, mn = base.max(1), base.min(1)
    sat = (mx - mn) / np.maximum(mx, 1)
    lum = 0.3 * r + 0.59 * g + 0.11 * b
    gray = sat < 0.22
    # Grautöne relativ zur typischen Hüllenfarbe: der Großteil bleibt hell, nur echte Abstufungen werden dunkler
    med = np.median(lum[gray & ~glow]) if (gray & ~glow).any() else 180
    cls[gray & (lum >= 0.86 * med)] = C["LIGHT"]
    cls[gray & (lum < 0.86 * med) & (lum >= 0.66 * med)] = C["MID"]
    cls[gray & (lum < 0.66 * med) & (lum >= 0.3 * med)] = C["DARK"]
    cls[gray & (lum < 0.3 * med)] = C["BLACK"]
    col = ~gray
    hue = np.degrees(np.arctan2(np.sqrt(3) * (g - b), 2 * r - g - b)) % 360
    cls[col & ((hue < 18) | (hue >= 330))] = C["RED"]
    cls[col & (hue >= 18) & (hue < 50)] = C["COPPER"]
    cls[col & (hue >= 50) & (hue < 160)] = C["LIGHT"]       # gelblich/grünlich: als helle Hülle
    cls[col & (hue >= 160) & (hue < 200)] = C["TEAL"]
    cls[col & (hue >= 200) & (hue < 270)] = C["BLUEPAINT"]
    cls[col & (hue >= 270) & (hue < 330)] = C["MID"]
    if emis.max() <= 0:
        # Modell ohne Leuchttextur: kräftige, helle Farben gelten als Leuchten
        bright = lum > 0.9 * med
        cls[col & bright & ((hue < 18) | (hue >= 330)) & (sat > 0.45)] = C["RED_GLOW"]
        cls[col & bright & (hue >= 160) & (hue < 260) & (sat > 0.35)] = C["BLUE_GLOW"]
        cls[col & bright & (hue >= 260) & (hue < 330) & (sat > 0.3)] = C["VIOLET_GLOW"]
        cls[gray & (lum > 1.25 * med) & (lum > 200)] = C["WINDOW"]
        return cls
    # Leuchtende Teile
    ehue = np.degrees(np.arctan2(np.sqrt(3) * (eg - eb), 2 * er - eg - eb)) % 360
    esat = (emax - emis.min(1)) / np.maximum(emax, 1)
    white = glow & (esat < 0.25)
    cls[white] = C["WINDOW"]
    g2 = glow & ~white
    cls[g2 & ((ehue < 20) | (ehue >= 340))] = C["RED_GLOW"]
    cls[g2 & (ehue >= 20) & (ehue < 55)] = C["ORANGE_GLOW"]
    cls[g2 & (ehue >= 55) & (ehue < 160)] = C["WINDOW"]
    cls[g2 & (ehue >= 160) & (ehue < 260)] = C["BLUE_GLOW"]
    cls[g2 & (ehue >= 260) & (ehue < 340)] = C["VIOLET_GLOW"]
    return cls


def transfer(vox, cls, g):
    """Jeder Oberflächenblock bekommt die häufigste Klasse der Punkte in seiner Nähe."""
    sx, sy, sz = g.shape
    surf = surface(g)
    lin = (vox[:, 0] * sy + vox[:, 1]) * sz + vox[:, 2]
    ok = (vox[:, 0] >= 0) & (vox[:, 0] < sx) & (vox[:, 1] >= 0) & (vox[:, 1] < sy) & (vox[:, 2] >= 0) & (vox[:, 2] < sz)
    lin, c = lin[ok], cls[ok]
    nc = len(CLASSES)
    key = lin * nc + c
    uk, cnt = np.unique(key, return_counts=True)
    vid, vcl = uk // nc, uk % nc
    # Stimmen je Block (dichter Puffer nur für belegte Blöcke)
    uvid, inv = np.unique(vid, return_inverse=True)
    votes = np.zeros((len(uvid), nc))
    # leuchtende Klassen und Markierungen zählen stärker (sie sind klein)
    weight = np.ones(nc)
    for n in ("RED_GLOW", "ORANGE_GLOW", "BLUE_GLOW", "VIOLET_GLOW", "WINDOW", "RED", "BLACK", "TEAL"):
        weight[C[n]] = 2.5
    np.add.at(votes, (inv, vcl), cnt * weight[vcl])
    out = np.zeros(g.shape, np.uint8)
    sidx = np.argwhere(surf)
    slin = (sidx[:, 0] * sy + sidx[:, 1]) * sz + sidx[:, 2]
    acc = np.zeros((len(sidx), nc))
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                nl = slin + (dx * sy + dy) * sz + dz
                pos = np.searchsorted(uvid, nl)
                pos = pos.clip(0, len(uvid) - 1)
                hit = uvid[pos] == nl
                wgt = 1.0 if (dx, dy, dz) == (0, 0, 0) else 0.35
                acc[hit] += votes[pos[hit]] * wgt
    best = acc.argmax(1)
    best[acc.max(1) <= 0] = 0
    out[sidx[:, 0], sidx[:, 1], sidx[:, 2]] = best
    return clean(out)


def clean(out):
    """Dünne Linien (Plattenfugen der Textur) entfernen: graue Abstufungen und Braun bleiben nur,
    wo sie eine Fläche bilden (mind. 5 gleiche Nachbarn im 3x3x3-Würfel), sonst wird es helle Hülle."""
    res = out.copy()
    for k in (C["MID"], C["DARK"], C["COPPER"], C["BLUEPAINT"], C["TEAL"]):
        m = (out == k).astype(np.int16)
        p = np.pad(m, 1)
        cnt = np.zeros_like(m)
        for dx in (0, 1, 2):
            for dy in (0, 1, 2):
                for dz in (0, 1, 2):
                    cnt += p[dx:dx + m.shape[0], dy:dy + m.shape[1], dz:dz + m.shape[2]]
        thin = (m == 1) & (cnt - 1 < 5)
        res[thin] = C["LIGHT"]
    return res


PREVIEW = {0: (0, 0, 0), 1: (215, 215, 210), 2: (175, 175, 172), 3: (110, 112, 116), 4: (25, 25, 28), 5: (190, 40, 40),
           6: (40, 110, 115), 7: (150, 180, 215), 8: (190, 120, 60), 9: (255, 50, 30), 10: (255, 150, 40),
           11: (80, 170, 255), 12: (180, 120, 230), 13: (255, 240, 170)}


def preview(out, path):
    sx, sy, sz = out.shape
    pal = np.array([PREVIEW[i] for i in range(len(CLASSES))], np.uint8)
    # oben: oberster Block, Seite: vorderster von Backbord, vorne
    top = np.zeros((sz, sx), np.uint8)
    ys = np.where(out.any(1), sy - 1 - np.argmax(out[:, ::-1, :] > 0, axis=1), -1)
    for x in range(sx):
        for z in range(sz):
            if ys[x, z] >= 0:
                top[z, x] = out[x, ys[x, z], z]
    side = np.zeros((sy, sx), np.uint8)
    zs = np.argmax(out > 0, axis=2)
    has = out.any(2)
    for x in range(sx):
        for y in range(sy):
            if has[x, y]:
                side[sy - 1 - y, x] = out[x, y, zs[x, y]]
    front = np.zeros((sy, sz), np.uint8)
    xs = np.argmax(out > 0, axis=0)
    hasf = out.any(0)
    for y in range(sy):
        for z in range(sz):
            if hasf[y, z]:
                front[sy - 1 - y, sz - 1 - z] = out[xs[y, z], y, z]
    h = sz + sy + sy + 8
    w = max(sx, sz)
    img = np.zeros((h, w, 3), np.uint8)
    img[:sz, :sx] = pal[top[::-1]]
    img[sz + 4:sz + 4 + sy, :sx] = pal[side]
    img[sz + sy + 8:, :sz] = pal[front]
    Image.fromarray(img).resize((w * 2, h * 2), Image.NEAREST).save(path)


def main():
    glb, grid, outp = sys.argv[1:4]
    g = load_grid(grid)
    n = int(min(6_000_000, max(1_500_000, g.sum() * 1.5)))
    P, base, emis = sample_model(glb, n)
    vox = align(P, g)
    cls = classify(base, emis)
    out = transfer(vox, cls, g)
    sx, sy, sz = out.shape
    with gzip.open(outp, "wb") as f:
        f.write(b"COLR" + struct.pack(">iii", sx, sy, sz) + out.tobytes())
    counts = np.bincount(out.reshape(-1), minlength=len(CLASSES))
    print({CLASSES[i]: int(counts[i]) for i in range(1, len(CLASSES)) if counts[i]})
    if len(sys.argv) > 4:
        preview(out, sys.argv[4])


if __name__ == "__main__":
    main()
