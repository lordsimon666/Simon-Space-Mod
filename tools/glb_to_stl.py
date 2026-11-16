#!/usr/bin/env python3
"""Wandelt ein GLB-Modell (alle Netze mit ihren Knoten-Transformationen) in ein Binär-STL um,
damit tools/voxelize_stl.py es in ein Blockraster umrechnen kann.

Aufruf:
    python3 tools/glb_to_stl.py MODELL.glb AUSGABE.stl
"""
import struct
import sys

import numpy as np

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from colors_from_glb import accessor, load_glb, node_matrix  # noqa: E402


def triangles(path):
    j, binc = load_glb(path)
    out, groups = [], []

    def visit(ni, parent):
        node = j["nodes"][ni]
        m = parent @ node_matrix(node)
        if "mesh" in node:
            for prim in j["meshes"][node["mesh"]]["primitives"]:
                if prim.get("mode", 4) != 4:
                    continue
                pos = accessor(j, binc, prim["attributes"]["POSITION"])
                pos = (np.c_[pos, np.ones(len(pos))] @ m.T)[:, :3]
                idx = accessor(j, binc, prim["indices"]).astype(np.int64) if "indices" in prim else np.arange(len(pos))
                tri = pos[idx.reshape(-1, 3)]
                if np.linalg.det(m[:3, :3]) < 0:  # gespiegelter Knoten: Wicklung umdrehen, damit die Normalen nach außen zeigen
                    tri = tri[:, [0, 2, 1]]
                out.append(tri)
                groups.append(np.full(len(idx) // 3, len(groups), dtype=np.int64))  # Teilnetz-Nummer
        for c in node.get("children", []):
            visit(c, m)

    for r in j["scenes"][j.get("scene", 0)]["nodes"]:
        visit(r, np.eye(4))
    return np.concatenate(out), np.concatenate(groups)


def write_stl(tris, groups, path):
    with open(path, "wb") as f:
        f.write(b"\0" * 80)
        f.write(struct.pack("<I", len(tris)))
        rec = np.zeros(len(tris), np.dtype([("n", "<f4", 3), ("v", "<f4", (3, 3)), ("a", "<u2")]))
        rec["v"] = tris
        rec["a"] = groups.astype(np.uint16)  # Teilnetz im Attributfeld (voxelize_stl füllt je Teilnetz)
        f.write(rec.tobytes())


if __name__ == "__main__":
    t, g = triangles(sys.argv[1])
    lo, hi = t.reshape(-1, 3).min(0), t.reshape(-1, 3).max(0)
    print(f"{len(t)} Dreiecke, Größe {hi - lo}")
    print(f"{g.max() + 1} Teilnetze")
    write_stl(t, g, sys.argv[2])
