# KLANG-00449: Live act: the music and its events from the engine, live

> **Status (2026-11-16): IN PROGRESS.** Step 1 (the small interface, the recording moved onto it) is built and in review.
>
> **Part of:** KLANG-00447 (Live act: a band on a stage, played by the song)

## Why this exists

The live act (KLANG-00447) plays a recording so far: one render of a song as an audio file, beside the events of
that render. It is meant to be a visualization in the app: the band plays whatever song the engine plays, live,
also while its code is edited.

## What is known

- **The live act needs the song ahead of the sound.** A stick goes up before its hit, the frontwoman raises her
  arm four seconds before a phrase and strolls where she has nothing to play for half a minute, the camera goes to
  who will play next, the stroboscope knows a bass drum run when it starts.
- **Live voices arrive two cycles ahead** (`KlangCyclicPlayback.Options.lookaheadCycles`, about 3.3 s for Der
  Schmetterling): enough for a stick, too short for the rest.
- **A pattern can be asked ahead.** `KlangPattern.queryEvents(fromCycles, toCycles, cps)` gives the events of any
  stretch, at any moment; the recording was made by exactly this. So the app can hand the live act the playing
  song's events for the bars to come.
- **The seam is built** (KLANG-00446, step 1 of this ticket): `Music` (the song's events ahead, the sound's energy
  at a time, `listen()` for a `Transport`), in `klangviz/src/commonMain/kotlin/liveact/Music.kt`. The recording is
  a `RecordedMusic`; the live act depends on nothing else of whoever plays.
- **Everything plans from the whole song when it is made** (the review of step 1): the show's tempo, the light
  plan's bass drum runs, the camera's choreography, the players' plans (which string for which pitch, the marimba's
  range, the frontwoman's strolls and phrases). A live song has no end and changes on an edit.

## Decide before implementing

1. **Does asking ahead give what will be played?** The maintainer (2026-11-16): "the music has randomness which
   will change how the musicians behave". To find out first: whether a pattern asked twice for the same stretch
   gives the same events (its random parts seeded by time), or other ones each time. If other ones, the band would
   play notes that are not the ones heard, and the events must come from the scheduler's own queries instead (as
   the recording's did), which are only two cycles ahead.
2. **Joining a song that already plays.** The maintainer: "probably skip the walk-in or similar". The clock today
   owns the start (a click, a walk-in of 15 s, then the music): mounted beside music that runs, it must take the
   music's time as it is.
3. **Who owns the transport.** The live act must not start or stop the app's playback by itself; its keys (space,
   the arrows) are the recording's. `Transport.release` already means only "this listener is done".
4. **Which instruments a song has, and who plays them.** The maintainer: "we also need to figure out how a song can
   announce which instruments it has and which musician should play it". Today the band Notstrom is wired to Der
   Schmetterling's tags by hand (`kick`, `snare`, `hats`, `bass`, `guitar1` to `guitar3`, `lead`, `trommel`). A
   broader topic, likely a ticket of its own: what a song says about its parts, what a band can play, and what
   happens to a part nobody plays and a player with no part.
5. **A song that changes while it plays** (an edit): a horizon ("known as far as here") and a signal that it
   changed, and plans that can be made again from a moment on.
6. **The loudness** live: from the analyser, which knows only now; the players ask only about now.

## The work

1. Done (in review): the `Music` seam, the recording moved onto it, the same picture as before.
2. Find out point 1 above, with a small spec on a pattern with random parts.
3. The clock beside music it does not own (points 2 and 3).
4. The app's side: a `Music` over the playing song (events asked ahead, the playback's clock, the analyser's
   loudness), and a visual in the app's `visuals/` that mounts the live act.
5. Plans that follow a changing song (point 5).

## Acceptance

In the app, with Der Schmetterling playing, the maintainer opens the live act and the band plays what is heard,
from the moment it is opened.

## History

- 2026-11-16: created.
