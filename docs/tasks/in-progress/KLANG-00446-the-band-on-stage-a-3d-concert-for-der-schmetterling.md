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
- 2026-11-16: the audience stands in half a circle around the stage (the maintainer: "as if they are in a giant open air theater - half a big circle"); `CrowdPlaces`, with a spec.
- 2026-11-17: the camera does a little more (the maintainer: "could use more camera cuts and movements. not too much but a little more", and asked for the travel "from the total over the audience back to the FOH" and "the other way around"): a fourth kind of round, the flight to the mixing desk and back (`Flight`, with a spec; `?move=flight`), and two looks in a row in the first half of a round of looks or with an arc.
- 2026-11-17: the reverse total (the maintainer: "a reverse total over the shoulder of the drummer so the crowd can be seen for longer... slight camera movement jitter"): a fifth kind of round holds it twelve bars; `?move=reverse`, key u. The first try, held by hand, was too unsteady (the maintainer: "the camera jitter is too much. Imagine a camera-drone that is almost steady in place"): it hovers now (`hover`, with a spec). A string player no longer turns his head to a camera behind his back (`inSight`), and no dolly back to the total starts from a shot that looks away from it (`looksAway`, with a spec): the key d cuts there, and so does the show's own way back from the drummer's look over his shoulder, which could swing the camera right round before. The show's way back from the frontwoman is a cut too, as she may stroll off meanwhile.
- 2026-11-17: half as many people again in the audience (the maintainer: "say 50% more people in the crowd"): about 12,500 lights.
- 2026-11-17: the audience's lights differ (the maintainer: "slightly different colors and sizes and analog flicker in brightness"): each its own colour, size, brightness and uneven flicker, worked out on the graphics card.
- 2026-11-17: the audience was too jumpy (the maintainer: "too much up and down as if they would jump 2m high"): a light now rises and sinks softly with the beat by 12 cm at most, where it bounced by up to 38 cm.
- 2026-11-17: the string players straighten up now and then (the maintainer: "Currently they are always hunched over"): the back comes up, the chest opens, the head lifts, for 4 to 9 seconds, about every second stretch of 21 (`Straighten`, with a spec; `?straight=1` holds the pose).
- 2026-11-17: the lamps behind change (the maintainer: "sometimes only the two others or only the inner one are active"): a rig holds 14 bars and goes over into the next in 2; three rigs in five are all three lamps, one in five each the outer two and the inner one alone, turned up (`BackRig` in the light plan, with specs). The inner one lights the middle only: the two guitarists at the sides stand in the dark then, and the drummer while only the outer two are on. `?rig=outer`, `inner` or `all` holds one.
- 2026-11-17: the audience's last rows vanished whenever the camera went round the band (the maintainer: "the audience is culled in the far back when the camera rotates"): the camera saw 60 m far and the audience reaches 62 m from the stage. It sees 110 m now (`Stage.FAR`).
- 2026-11-17: the string players' left hand is where a player's would be (the maintainer: "I would like it look realistic on the guitar which string and handposition is used"): each note has its string and fret in the real tuning (E A D G B E, the bass E A D G), the hand holds a position of four frets and leaves it as seldom as the passage allows, open strings are used, a chord's notes each have a string, and a note too low or too high is played octaves nearer (`Tuning.grips`, with a spec). What was played stays as it was held when a live song grows.
- 2026-11-17: the marimba player holds four mallets, two in each hand (the maintainer: "Try four mallets"): a single note with a hand's inner mallet while the outer one rides beside it, notes that sound together shared between the hands (`malletShare`, with a spec).
- 2026-11-17: her weight went from leg to leg too hectically (the maintainer): the body now goes by where the hands play over some five seconds, where it was two, and bends softly into the move (`beyond`, with a spec).
- 2026-11-17: the name on the bass drum went over its ring on the maintainer's MacBook: it was set in whatever font the machine has, with hair space characters between the letters. The letters are now measured and set one by one, so the name is as wide everywhere (`nameSize`, with a spec).
- 2026-11-17: the rhythm guitarist has a bit of a neck and a chin (the maintainer: "instead of the upper body going straight into the head we need a little Kinn when seen from the side"): the neck is longer and slimmer, the throat is set back under the chin, and the chin is broad rather than pointed (`figures.py`; the other five figures come out the same, `check/models.py`).
