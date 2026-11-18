# The band on stage (KLANG-00446)

A standalone prototype: a 3d band that plays Der Schmetterling, for the demo that closes Klang's first year. It is
a plain page with three.js, outside the app and outside the build, so that a change shows after a reload. It
lives in `prototypes/` (the maintainer, 2026-11-16) until the show is right; then it is ported into the app in
Kotlin, and this folder goes. Licensed like Klang: AGPL-3.0-or-later.

What it is, what was decided and what is open: KLANG-00446.

## Run it

```
python3 prototypes/band/serve.py        # then open http://localhost:18750/web/
```

Click to start. Space pauses, left and right jump 10 s (right skips the walk-in). Every opening of the page is
another performance; the seed is shown in the corner, and `?seed=7` plays one again. The other switches (`?t=`,
`?still`, `?cam=`, `?head=`, `?front=`, `?nostrobe`, `?test`, `?nohud`) are listed at the top of `web/show.js`.

The stroboscope flashes about ten times a second: a public showing needs a warning before it (photosensitive
epilepsy).

Two things are not in git and have to be there. They live in `ignored/`, the one folder git does not see:

- **three.js**: a copy in `ignored/three/`, or else served from the app's own (`build/js/node_modules/three`, there
  after any Kotlin/JS build of the app).
- **The recording** (in `ignored/`: `schmetterling.wav`, `schmetterling-events.json`, `schmetterling-energy.json`): see below.
  Without it the page plays test notes.

## The recording

The audio and the events must come from one render: the song is seeded by the time of day.

1. Copy `BandRecordingScratch.kt.txt` to `apps/studio/src/jvmTest/kotlin/BandRecordingScratch.kt` (never commit it there).
2. Write the number of cycles into `prototypes/band/ignored/RECORD` (328 is nine minutes).
3. Run `./gradlew :apps:studio:jvmTest --rerun --tests io.peekandpoke.klang.studio.BandRecordingScratch` through the build lock.
4. Remove the spec and `RECORD` again, then `python3 prototypes/band/energy.py` (needs numpy) for the loudness.

## What is where

| Path | What it is |
|------|------------|
| `web/main.js` | Puts the pieces together and runs the frames; the order of a frame is written there |
| `web/show.js` | What the show is given: the page's switches, the seed, the recorded song and its loudness |
| `web/stage.js` | Floor, the wall behind, the lamps (back light, the low front lamp, the stroboscope), the riser |
| `web/band.js` | Who stands where, each player's look and character, the figures, the players, the walk-in |
| `web/life.js` | Eye contact, the eyes' wavering glow, breath |
| `web/clock.js` | The show's time: the click to start, the walk-in's countdown, the audio |
| `web/view.js`, `camera.js` | Where the camera is; its choreography |
| `web/rig.js` | A figure's skeleton by joint names, its own measures, the two-bone reach, turning the face, breathing |
| `web/strike.js` | Striking with sticks and mallets; the nod and the characters (`Mover`); the show's seeded chance |
| `web/drummer.js`, `marimba.js`, `trumpet.js`, `strings.js` | The players and their instruments |
| `web/entrance.js` | Walking in: the way, the stepping legs, sitting down |
| `web/song.js` | Reads the recording; who plays which event |
| `web/pattern.js` | The cloth: each body's pattern in the light, the glitter, the wall's two faces |
| `web/util.js` | The few numeric helpers everything shares |
| `models/*.glb` | The figures as this page last had them (below) |
| `check/stills.py`, `check/models.py` | Proof for a change that must not change the show (below) |
| `energy.py`, `serve.py` | The recording's loudness; the page's server |
| `INVENTORY.md` | What the show holds, by kind of movement, and what is uneven |

The figures share one skeleton: a bone per real joint, named after the joint it ends at. A figure's shape can
change freely; the motion reads only the bones. Every motion is computed each frame from the time and the seed
alone, so any moment can be shown on its own and always looks the same.

## The figures

The script that builds the figures has moved to the show: `klangviz/figures/` (2026-11-17), with a file for each
character, who have names of their own now (Wucht, Beben, Skalpell, Sirene, Rune, Klaue). It writes its models into
the show's resources. `models/*.glb` here are the figures as this page last had them, under their old names
(`drums`, `bass`, `guitar1`, `eimer`, `marimba`, `guitar2`); they are no longer rebuilt.

`check/models.py` compares two folders of models by content (Blender does not keep the order of the vertices from
run to run, so two exports differ as files though they are the same figures).

## Changing the code without changing the show

Kind: scaffolding for the sorting of 2026-11-16 and for the port to come; it goes with this folder.

```
python3 prototypes/band/check/stills.py take before      # 18 stills: fixed moments, seeds and cameras
# ... change the code ...
python3 prototypes/band/check/stills.py take after
python3 prototypes/band/check/stills.py compare before after     # every pixel
python3 -I prototypes/band/check/models.py <models before> klangviz/src/jsMain/resources/liveact/notstrom   # after a change to klangviz/figures/
```

The stills are frozen moments: they do not see the click to start, the audio's clock or anything that depends on
the real time.
