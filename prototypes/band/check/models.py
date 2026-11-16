#!/usr/bin/env python3
# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later

"""Compares two folders of .glb by content, whatever order the vertices come in (Blender's export does not keep it
from run to run): per primitive every vertex as (position, its weight on each joint by the joint's name), sorted.
Also the node names, the materials and the bones' rest places. Needs numpy; exit 1 when they differ.

  models.py <folder A> <folder B>"""
import json, struct, sys, os, glob
import numpy as np
T = {5120: np.int8, 5121: np.uint8, 5122: np.int16, 5123: np.uint16, 5125: np.uint32, 5126: np.float32}
N = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}
def load(f):
    b = open(f, 'rb').read(); n = struct.unpack('<I', b[12:16])[0]
    j = json.loads(b[20:20 + n]); bin_ = b[20 + n + 8:]
    def acc(i):
        a = j['accessors'][i]; v = j['bufferViews'][a['bufferView']]
        off = v.get('byteOffset', 0) + a.get('byteOffset', 0); k = N[a['type']]
        stride = v.get('byteStride')
        dt = np.dtype(T[a['componentType']])
        if stride and stride != dt.itemsize * k:
            raw = np.frombuffer(bin_, dtype=np.uint8, count=(a['count'] - 1) * stride + dt.itemsize * k, offset=off)
            rows = np.stack([np.frombuffer(raw[r * stride:r * stride + dt.itemsize * k].tobytes(), dtype=dt) for r in range(a['count'])])
            return rows.astype(np.float64)
        return np.frombuffer(bin_, dtype=dt, count=a['count'] * k, offset=off).reshape(a['count'], k).astype(np.float64)
    out = {'nodes': sorted(str(n.get('name')) for n in j['nodes']), 'materials': [m['name'] for m in j['materials']], 'prims': []}
    skin = j['skins'][0]; joints = [j['nodes'][i]['name'] for i in skin['joints']]
    order = sorted(range(len(joints)), key=lambda i: joints[i])
    out['joints'] = sorted(joints)
    out['rest'] = acc(skin['inverseBindMatrices'])[order]
    for m in j['meshes']:
        for p in m['primitives']:
            at = p['attributes']; pos = acc(at['POSITION']); ji = acc(at['JOINTS_0']).astype(int); w = acc(at['WEIGHTS_0'])
            full = np.zeros((len(pos), len(joints)))
            for c in range(4):
                np.add.at(full, (np.arange(len(pos)), ji[:, c]), w[:, c])
            rows = np.round(np.hstack([pos, full[:, order]]), 4)
            rows = rows[np.lexsort(rows.T[::-1])]
            out['prims'].append((j['materials'][p['material']]['name'], rows))
    return out
bad = False
for f in sorted(glob.glob(os.path.join(sys.argv[1], '*.glb'))):
    a, b = load(f), load(os.path.join(sys.argv[2], os.path.basename(f)))
    same = a['nodes'] == b['nodes'] and a['materials'] == b['materials'] and a['joints'] == b['joints'] and \
        [(n, r.shape) for n, r in a['prims']] == [(n, r.shape) for n, r in b['prims']]
    d = max([float(np.abs(x[1] - y[1]).max()) for x, y in zip(a['prims'], b['prims'])] + [float(np.abs(a['rest'] - b['rest']).max())]) if same else float('nan')
    ok = same and d < 2e-4
    bad = bad or not ok
    print(os.path.basename(f), 'names and counts', 'same' if same else 'DIFFERENT', '| largest difference', d, '|', 'OK' if ok else 'NOT THE SAME')
sys.exit(1 if bad else 0)
