# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later

# Builds the band's six figures (KLANG-00446) and writes them where the show loads them from. Each character has a
# file of its own beside this one; what they share is in figure.py.
#
#   blender -b --factory-startup -P klangviz/figures/figures.py -- <pictures dir> [export[=<models dir>]] [preview] [close=<name>]
#
# export: writes <name>.glb for each, into the show's resources unless another folder is named.
# preview: two pictures of the line-up, as on a stage, into the pictures dir.
# close=<name>: six pictures of that character alone in plain light (the head and the body: front, three quarters,
#   side), into the pictures dir: for shaping a figure, where the stage's light hides too much.
import bpy, os, sys
from mathutils import Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from beben import Beben
from klaue import Klaue
from rune import Rune
from sirene import Sirene
from skalpell import Skalpell
from wucht import Wucht
from figure import material

args = sys.argv[sys.argv.index("--") + 1:]
OUT = args[0]
PREVIEW = "preview" in args
EXPORT = any(a == "export" or a.startswith("export=") for a in args)
MODELS = next((a[len("export="):] for a in args if a.startswith("export=")),
              os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "jsMain", "resources", "liveact", "notstrom"))
CLOSE = next((a[len("close="):] for a in args if a.startswith("close=")), None)

# The characters, in the line-up's order (the previews), left to right.
ORDER = [Skalpell(), Beben(), Wucht(), Sirene(), Rune(), Klaue()]

if "=" in OUT or OUT in ("export", "preview"):
    sys.exit("The first argument is the folder for the pictures; got '" + OUT + "'")
if CLOSE and CLOSE not in [f.key for f in ORDER]:
    sys.exit("No character '" + CLOSE + "'; there are: " + ", ".join(f.key for f in ORDER))


def spot(loc, at, color, energy, size=0.8):
    bpy.ops.object.light_add(type='SPOT', location=loc)
    l = bpy.context.object
    l.data.energy = energy; l.data.color = color; l.data.spot_size = size; l.data.spot_blend = 0.7
    l.rotation_euler = (Vector(at) - Vector(loc)).to_track_quat('-Z', 'Y').to_euler()


def camera(loc, at, lens):
    bpy.ops.object.camera_add(location=loc)
    cam = bpy.context.object
    cam.rotation_euler = (Vector(at) - Vector(loc)).to_track_quat('-Z', 'Y').to_euler()
    cam.data.lens = lens
    bpy.context.scene.camera = cam


bpy.ops.wm.read_factory_settings(use_empty=True)
built = {}
for i, figure in enumerate(ORDER):
    body, rig = figure.build()
    if EXPORT:
        bpy.ops.object.select_all(action='DESELECT')
        body.select_set(True); rig.select_set(True)
        bpy.ops.export_scene.gltf(filepath=os.path.join(MODELS, figure.key + ".glb"), use_selection=True)
    rig.location.x = (i - (len(ORDER) - 1) / 2) * 1.05
    built[figure.key] = (figure, body, rig)

if PREVIEW:
    bpy.ops.mesh.primitive_plane_add(size=60)
    bpy.context.object.data.materials.append(material("floor", (0.02, 0.02, 0.025), rough=0.22))
    # a dim glowing backdrop: the figures stand against it as silhouettes
    bpy.ops.mesh.primitive_plane_add(size=1, location=(0, 5, 3))
    back = bpy.context.object
    back.scale = (30, 8, 1); back.rotation_euler = (1.5708, 0, 0)
    back.data.materials.append(material("back", (0, 0, 0), emission=(0.10, 0.20, 0.55), strength=0.22))
    for x, c in ((-3.3, (0.2, 0.5, 1.0)), (-1.1, (1.0, 0.2, 0.5)), (1.1, (0.2, 0.5, 1.0)), (3.3, (1.0, 0.2, 0.5))):
        spot((x, 3.0, 3.6), (x * 0.8, 0, 1.2), c, 3000)
    spot((-2, -6, 4), (0, 0, 1), (1.0, 0.9, 0.8), 500, size=1.4)
    world = bpy.data.worlds.new("W"); bpy.context.scene.world = world
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.003, 0.003, 0.006, 1)
    s = bpy.context.scene
    s.render.engine = 'CYCLES'; s.cycles.samples = 40; s.cycles.device = 'CPU'; s.cycles.use_denoising = True
    s.render.resolution_x = 1500; s.render.resolution_y = 700
    camera((0, -9.5, 1.3), (0, 0, 0.98), 50)
    s.render.filepath = os.path.join(OUT, "lineup-front.png")
    bpy.ops.render.render(write_still=True)
    camera((6.5, -7.0, 1.7), (0, 0, 0.95), 50)
    s.render.filepath = os.path.join(OUT, "lineup-side.png")
    bpy.ops.render.render(write_still=True)

if CLOSE:
    figure, body, rig = built[CLOSE]
    # alone, in plain light, in a pale grey: the figure's own near black shows no shape
    for key, (_, other, _) in built.items():
        other.hide_render = key != CLOSE
    for m in bpy.data.materials:
        if m.name.endswith("_dark") or m.name.endswith("_hair"):
            b = m.node_tree.nodes["Principled BSDF"]
            b.inputs["Base Color"].default_value = (0.45, 0.45, 0.48, 1)
            b.inputs["Roughness"].default_value = 0.7
    for o in list(bpy.data.objects):
        if o.type in ('LIGHT', 'CAMERA') or o.name in ("Plane", "Plane.001"):
            o.hide_render = True
    world = bpy.data.worlds.new("Plain"); bpy.context.scene.world = world
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.25, 0.25, 0.28, 1)
    # where the head really is: the bone "head" ends at the head's point (a long neck and a stoop move it)
    bpy.context.view_layer.update()
    top = rig.matrix_world @ rig.data.bones["head"].tail_local
    x = top.x
    bpy.ops.object.light_add(type='SUN', location=(x - 2, -4, 4))
    bpy.context.object.data.energy = 3.0
    bpy.context.object.rotation_euler = (Vector((x, 0, 1.4)) - Vector((x - 2, -4, 4))).to_track_quat('-Z', 'Y').to_euler()
    s = bpy.context.scene
    s.render.engine = 'CYCLES'; s.cycles.samples = 24; s.cycles.device = 'CPU'; s.cycles.use_denoising = True
    s.render.resolution_x = 500; s.render.resolution_y = 500
    for part, z, lens in (("head", top.z, 130), ("body", 0.72 * top.z, 55)):
        y = top.y if part == "head" else 0.0
        for view, loc in (("front", (x, y - 2.2, z + 0.06)), ("three", (x + 1.5, y - 1.7, z + 0.06)), ("side", (x + 2.2, y, z + 0.06))):
            camera(loc, (x, y, z), lens)
            s.render.filepath = os.path.join(OUT, CLOSE + "-" + part + "-" + view + ".png")
            bpy.ops.render.render(write_still=True)
print("DONE")
