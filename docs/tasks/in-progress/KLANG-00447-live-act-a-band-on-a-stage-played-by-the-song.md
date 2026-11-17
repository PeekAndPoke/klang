# KLANG-00447: Live act: a band on a stage, played by the song

> **Status (2026-11-16): IN PROGRESS.** The epic for the live act: the band, its stage, lights and camera, the port to Kotlin (klangviz), and what follows.

## Why this exists

A band on a stage that plays the song: figures driven by the song's tags, lights from the music, a camera that
shows it. It began as a demo to close Klang's first year (KLANG-00446) and is meant to stay: a visualization
beside the others, for any song with the right tags.

The maintainer (2026-11-16): all tickets of the live act go into this epic.

## Where it lives

- `klangviz/`: the module (package `io.peekandpoke.klang.viz.liveact`; why it is cut so: KLANG-00456). `band/`
  holds `members/` (a sheet per member: dress, character, bearing, skills), `skills/` (what a member can do on
  their own, equipped on the sheet: the eye contact, the glance at the camera; Rune's shadow snakes are her
  dress), `StagePlot.kt` (who stands where, the walk-in), `figure/` (the skeleton and how it moves), `dress/`,
  `instruments/` (a folder each for drums, marimba, guitar, bass and trumpet, and `strings/` for what guitar and
  bass share) and `notstrom/` (the band put together). Beside it: `stage/`, `theatre/`, `sound/`, `song/`, `lights/`, `camera/`, and `refactorme/`, what
  ties the live act to the one song (who plays which part, the song's tags, the test notes). Shared arithmetic is
  shared code, so the JVM runs its specs.
- `klangviz-demo/`: a small app that depends on klangviz only and plays a recording.
- `workshop/src/jsMain/kotlin/threejs/`: the three.js types Kraft's addon lacks, heading for the addon.
- `prototypes/band/`: the standalone page it was developed in, and the Blender script that makes the figures.

## The stages

1. The show found, standalone (KLANG-00446).
2. Ported to Kotlin, playing the recording in the demo app, the same picture as the prototype (KLANG-00446).
3. The music and its events from the engine, live, as a visualization in the app (KLANG-00449).
4. One body for every player: rules it never breaks, poses asked of it from outside, a background movement laid
   under specific ones; walking that comes from the body. No ticket yet.
5. Faster drawing (KLANG-00448), last.

## Acceptance

The maintainer watches a song played by the band in the app and says it is good enough to show.

## History

- 2026-11-16: created.
