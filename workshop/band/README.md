# The band on stage (KLANG-00446)

A standalone prototype: a 3d band that plays Der Schmetterling, for the demo that closes Klang's first year. It is
a plain page with three.js, outside the app and outside the build, so that a change shows after a reload. It is
parked in the workshop folder (the maintainer, 2026-11-16) until the show is right; then it is ported into the app
in Kotlin, and this folder goes. It is Klang's own and not bound for Kraft or Ultra, unlike the pieces beside it.

What it is, what was decided and what is open: KLANG-00446.

## Run it

```
python3 workshop/band/serve.py        # then open http://localhost:18750/web/
```

Click to start. Space pauses, left and right jump 10 s (right skips the walk-in). `?still` keeps the camera on the
total, `?t=62.1` freezes a moment, `?view=drums` or `?cam=x,y,z&at=x,y,z` sets a camera, `?test` plays test notes
without the recording.

Two things are not in git and have to be there. They live in `ignored/`, the one folder git does not see:

- **three.js**: a copy in `ignored/three/`, or else served from the app's own (`build/js/node_modules/three`, there
  after any Kotlin/JS build of the app).
- **The recording** (in `ignored/`: `schmetterling.wav`, `schmetterling-events.json`, `schmetterling-energy.json`): see below.
  Without it the page plays test notes.

## The recording

The audio and the events must come from one render: the song is seeded by the time of day.

1. Copy `BandRecordingScratch.kt.txt` to `src/jvmTest/kotlin/BandRecordingScratch.kt` (never commit it there).
2. Write the number of cycles into `workshop/band/ignored/RECORD` (328 is nine minutes).
3. Run `./gradlew :jvmTest --rerun --tests io.peekandpoke.klang.BandRecordingScratch` through the build lock.
4. Remove the spec and `RECORD` again, then `python3 workshop/band/energy.py` (needs numpy) for the loudness.

## What is where

| Path | What it is |
|------|------------|
| `web/main.js` | The stage: lamps, who stands where, each player's character, the walk-in, the audio's clock |
| `web/rig.js` | A figure's skeleton by joint names, its own measures, the two-bone reach |
| `web/strike.js` | Striking with sticks and mallets; the nod and the characters (`Mover`) |
| `web/drummer.js`, `marimba.js`, `trumpet.js`, `strings.js` | The players and their instruments |
| `web/song.js` | Reads the recording; who plays which event |
| `web/camera.js`, `entrance.js`, `pattern.js` | The camera's choreography; the walk-in; the body patterns |
| `blender/figures.py` | Builds the six figures and exports them: `blender -b --factory-startup -P blender/figures.py -- <a folder> export` |
| `models/*.glb` | The exported figures (rebuilt by the script; kept so the page runs without Blender) |
| `energy.py`, `serve.py` | The recording's loudness; the page's server |

The figures share one skeleton: a bone per real joint, named after the joint it ends at. A figure's shape can
change freely; the motion reads only the bones.
