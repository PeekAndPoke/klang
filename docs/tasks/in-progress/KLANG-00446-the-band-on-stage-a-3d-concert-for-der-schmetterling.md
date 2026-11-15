# KLANG-00446: The band on stage: a 3d concert for Der Schmetterling

> **Status (2026-11-15): IN PROGRESS.** Standalone in `tmp/band/` (git-ignored): six figures, a stage, and the drummer playing a test beat; the maintainer: "we keep it like this for now". Next: the other instruments, then the song's real events.

## Why this exists

The maintainer (2026-11-15): a demo to close Klang's first year, due about 2026-12-15. A 3d band plays Der
Schmetterling: two guitarists, a bass player, a marimba player, a "Gummieimer" player and a drummer, on a stage with
lights. The musicians know what to play from the song's tags, and the lights follow the music too.

The look (maintainer): caricatures, "dark" and "gloomy", "almost only visible as silhouettes", with glowing eyes that
follow how hard the player plays at the moment.

Later, not in this ticket's first steps: a two-way concert. The user throws things (eggs, tomatoes) at the
musicians, and a player who is hit makes an audible mistake. That needs changes in the song and a way to hand
parameters from outside into a running KlangScript, which does not exist yet.

"It will for sure not be a oneshot, and this is totally fine": the look is found in rounds with the maintainer.

## What is known

- The events are there: `spine.playbacks.nowPlaying`'s `signals` carry `KlangPlaybackSignal.VoicesScheduled`, each
  voice with its start and end time and its `VoiceData` (tags, note, gain). `VoiceRain` reads them this way.
- The song's tags: `lead`, `guitar1`, `guitar2`, `guitar3`, `bass`, `trommel`, `kick`, `snare`, `hats`, `shaker`,
  `countin`. Which tag belongs to which musician is to decide (below).
- three.js is in the app (Kraft's `addons_threejs`, used by `MotorBackground`). Whether the addon brings the glTF
  loader and skinned meshes is not checked yet.
- Blender 5.3 is on the machine (`/snap/bin/blender`) and runs headless as the agents' user: a Python script builds
  a figure, renders pictures (Cycles on the CPU, about 20 seconds for two) and exports glTF. No MCP server is needed.
- A first figure (2026-11-15): a skeleton of points with a thickness each (the Skin modifier), smoothed, and an
  armature made from the same points (22 bones). The mood of the pictures is right; the figure is a plain mannequin,
  no caricature yet. The glTF export left the body out: the modifiers have to be applied before the export.

## Decide before implementing

1. The figures' shapes: one skeleton for all six, each a caricature by its proportions (maintainer's eye, in rounds).
2. Which tags each musician plays (three guitar tags and a lead for two guitarists; `trommel` and the Gummieimer).
3. Where the demo lives in the app: a page of its own, or a visual on the visuals screen.
4. Where the Blender scripts and the exported models live in the repo. Blender is run by hand and its output is
   committed; it never becomes part of the Gradle build.

## The work

The maintainer (2026-11-15): "develop everything in a tmp folder, standalone first before porting it to kotlin". So
steps 1 to 5 are built as a plain page with three.js outside the app, fed by a recording of the song's events, and
ported to Kotlin once the show is right.

1. The figures in Blender, by script: shapes, the armature, the eyes; pictures for the maintainer.
2. The instruments, by script as well.
3. The stage in the app: three.js, the models loaded, the lights, the camera.
4. The motion: each voice sets a target (a stick on a drum, a mallet on a bar, a hand on the neck), the arm follows
   by inverse kinematics; a sway on the beat in between. The eyes glow with how hard the player plays.
5. The lights from the music: kick, snare, the song's parts, the level.
6. Later: the throwing and the audible mistake.

## Acceptance

The maintainer watches Der Schmetterling played by the band and says it is good enough to show.

## History

- 2026-11-15: created. Blender headless proven, a first figure rendered.
- 2026-11-15: the standalone stage (`tmp/band/`, not in git): `blender/figures.py` builds six figures on one
  skeleton (a bone per real joint, named after the joint it ends at; shaping points add no bone) and exports glTF;
  `web/` is a three.js page (`rig.js`: bones by name, the figure's own measures, a two-bone reach; `drummer.js`: stool,
  kit, sticks, hits as events). Maintainer's steers: figures less comic and closer in size; the nod on the beat with
  small variations; the hand in line with the forearm, the stick along the hand; the kit far enough out that the
  elbows hang. Stills by `app-shot` with WebGL; the maintainer watches it live through a local server he starts.
