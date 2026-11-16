# KLANG-00446: The band on stage: a 3d concert for Der Schmetterling

> **Status (2026-11-15): IN PROGRESS.** Standalone in `tmp/band/` (git-ignored): six figures, a stage, and the drummer playing a test beat; the maintainer: "we keep it like this for now". Next: the other instruments, then the song's real events.
>
> **Part of:** KLANG-00447 (Live act: a band on a stage, played by the song)

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
7. Later, asked for by the maintainer (2026-11-16): a camera movement that very slowly zooms out from the total.
8. Later, from the maintainer (2026-11-16): the stroboscope "is still a bit too bright ... hurts the eyes".
9. Later, seen 2026-11-16 (recording and live alike, seed 2897 at 157 s): the frontwoman on a stroll can stand
   right in front of the camera during a close look at another player, and fills the picture. A look should
   avoid her, or she the look.

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
- 2026-11-15: the whole band plays the recorded song in the standalone page. One render writes the audio and the
  events of that very render (a scratch spec, kept as `tmp/band/BandRecordingScratch.kt.txt`, never in the sources;
  the song is seeded by the time of day, so audio and events must come from one run). Who plays what: drums `kick`,
  `snare`, `hats`, `countin`; marimba `lead`; Gummieimer `trommel` (maintainer); bass `bass`; lead guitar `guitar1`;
  rhythm guitar `guitar2` and `guitar3` (my choice, open). Known: the events carry almost no dynamics (the gain of a
  part is nearly constant), so strokes and eyes do not follow how hard a part is played yet.
- 2026-11-16: the maintainer on the standalone show: "this really makes me laugh ... it is good". What was settled on
  the way, all in `tmp/band/web/`: `trommel` is a trumpet (it sounds like a held melody, the bucket drum did not fit;
  between phrases he holds it at his side in one hand); the marimba floats, without legs, and is smaller; the drummer
  sits on a riser, the open hat goes to the crash, a crash is the free left hand's; a played string alone lights up
  (flash, hold at 30 %, fade); the band stands in an arc, the trumpet player in front but left of the bass drum. The
  nod: as strong as the sound's energy over a second and a half (measured once from the audio, `energy.py`), never a
  single loud moment for the head; a character per player (`CHARACTERS` in `main.js`: depth, beats, lateness,
  chance, which body parts take the beat, steady, fits), the trumpet player as frontman the balanced one, the bass
  player steady, the rhythm guitarist headbanging only in rare fits. The recording is 328 cycles now (9 min); the
  song's parts come and go on schedules of their own and the recording is a cut, not an ending.
- 2026-11-16: more settled in the standalone show (still only in `tmp/band/`, not in git). The frontwoman: the
  trumpet player is a woman, a little athletic, level shoulders, a long neck, a long rounded skull after ancient
  Egyptian sculpture (a sea shell hat was tried and is kept in the script, unworn); she holds the trumpet loosely at
  her side between phrases and strolls the front of the stage in rounds, a slow dance-walk in time, back to her place
  with a wait; upright, little nodding. The drummer: both feet on the kick, a second, open hat for `oh`. The marimba
  player: relaxed arms between phrases, into position three cycles ahead, arms before body. The string players: the
  hands go to the played string (pitches shared over the strings by how often they are played), the fretting hand
  stays in one position, guitarists strum from the wrist; the rhythm guitarist looks at the viewer now and then.
  A body pattern per player, seen only in the reflections (`pattern.js`). Eyes set into the faces, a chin for all.
  A walk-in of 15 s before the song: dark stage until the click, the lamps up at once on it, the players in one
  after another, the marimba in place from the start. The camera (`camera.js`): mostly the total, three looks in a
  row about every 80 s, back by a cut or now and then a slow dolly zoom. Lamps with an analog wander, shiver and sag.
- 2026-11-16: the show grew in the prototype, now `prototypes/band` (a new top-level folder for prototypes): a wall
  with an ornament behind the band, light moods for the two main lamps, a low front lamp in several colours, a
  stroboscope on the bass drum's fast runs, fog on the floor, the audience as held-up lights with a stadium shot,
  camera rounds (looks, a way round one player, a flight round the band) and camera keys, one seed for a whole
  performance, eye sockets, a chin for the frontwoman, NOTSTROM on the bass drum, oversampling on a key. The code
  was sorted and reviewed (no change in what it shows, by 18 identical stills).
- 2026-11-16: ported to Kotlin (cb169d069): the module `klangviz` (package `io.peekandpoke.klang.viz.liveact`), the
  demo app `klangviz-demo` that plays the recording, and the three.js types it needs in `workshop/threejs`. The same
  picture as the prototype: of 18 fixed stills 17 are identical pixel for pixel, one differs in one pixel. Next:
  the music and its events from the engine, live (no ticket yet; see the epic, KLANG-00447).
