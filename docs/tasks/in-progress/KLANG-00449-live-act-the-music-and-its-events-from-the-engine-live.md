# KLANG-00449: Live act: the music and its events from the engine, live

> **Status (2026-11-16): IN PROGRESS.** The band plays live in the app, a first version (steps 1 to 4). Waiting for KLANG-00450 (the same cycles asked twice give the same events); open: a song that grows and changes, which instruments a song has.
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
   will change how the musicians behave". Measured (2026-11-16, a throwaway test: Der Schmetterling's pattern asked
   four times for cycles 64 to 96): **not for every part.**
   - Bass, the three guitars, kick, snare, hats, shaker: the same notes at the same places every time. Only their
     starts wander between two askings, by up to 0.6 ms (bass, kick), 1.7 ms (hats) and 3.3 ms (snare): the song's
     small random delays are drawn anew on every asking. Nothing the eye sees.
   - `lead` (the marimba): other notes every time, and another number of them (692, 550, 512 and 672 events in the
     same 32 cycles). Its random doubling is drawn anew on every asking, not from the time asked about.

   So as things are, asking ahead is a forecast, not the truth.

   **The maintainer's decision (2026-11-16):** "we need to fix the randomness even when there is no seed set...
   no seed set could mean 'set a random seed' so that every playback differs. But querying the same cycles must
   be idempotent". So: a song without a seed gets one random seed for the whole playback, and the same cycles
   asked twice give the same events. Once that holds, the live act can know the song ahead exactly.

   **Where it comes from: not found yet.** Probed (2026-11-16, throwaway tests): every random construct of the
   pattern language asked twice on its own gives the same events (`a|b`, `ply` with a choice, `rand`, `perlin`,
   `berlin`, `degradeBy`, `sometimes`, `shuffle`, `late` with a noise, `seed`, also sped up and combined as the
   song's `lead` has them). Only the whole song does not. One of them is not the same when asked in two halves
   (`late` with `berlin`, without `seg`). The next step is to ask the song part by part (each of its exports)
   until the part and the construct are found. The fix belongs to the pattern language (`sprudel`).

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
2. Point 1 above is measured and decided: asking the same cycles twice is to give the same events. Open: find
   where the song's askings differ (part by part), and fix it in the pattern language.
3. Done: the clock beside music it does not own (points 2 and 3). A `Transport` without `controls` is followed:
   the music's time at once, no walk-in, no notice, no click, no key.
4. Done, a first version: the app's side. `LiveActVisual` (the visual "Live act" in the Visuals tab) compiles the
   playing code once more, reads its events for 512 cycles ahead, and mounts the live act on the playback's clock
   and the analyser's loudness (`LiveLoudness`). Seen running in the built app. Its limits:
   - the band appears only once the whole stretch is read (seconds; the page counts meanwhile): gone with step 5;
   - the marimba is not played note for note until KLANG-00450 lands;
   - the stage tells the editor's code, not the code that plays (`KlangCodePlaybackCtrl.publishNowPlaying`): a
     song edited and not played again is read as edited;
   - a change of the code or the tempo makes the whole band anew; a tempo change shifts heard seconds against
     the song's cycles;
   - in the app Esc closes the visual, so only 0 gives the camera back to the choreography.
5. Done, as a trial (the maintainer, 2026-11-16: "keep a rolling look ahead of 8 cycles and try if things still
   work"): the song grows while it plays. `LiveActVisual` reads from one cycle before what is heard to 8 cycles
   ahead, then reads on, a whole cycle at a time; the band is there after the first stretch. Never less than 14
   seconds ahead, though (the review's finding): the frontwoman must know her next note 13 seconds ahead to come
   back from a stroll without a jump, and 8 cycles are fewer seconds in a faster song. For Der Schmetterling (a
   cycle is 1.64 s) that makes 9 cycles. `Song` takes more
   events (`add`, `version`), a player reads its `Part` and plans again when the song has grown. What had to get
   a rule of its own, because it was worked out from the whole song before:
   - the marimba's bars: a growing song gets bars up to the c three octaves above the middle one, and a note
     beyond the bars is played an octave nearer;
   - a string player's fingering: a pitch keeps the string it got, new pitches are shared out by the notes known
     then;
   - the frontwoman's strolls: after her last known note she is free for as long as nothing more is known; a
     stroll that has begun stays when the song then says her next note comes sooner than half a minute, and she
     is back four seconds before it;
   - how hard a note is played is its gain against the loudest of its tag known when it is read, and stays that.
   Its limits: everything read is kept, so a very long set grows in memory and each new cycle costs a little more
   to plan; whether it runs smoothly is for the maintainer's eyes (the browser here draws one frame a second).
   Klang-Main-Dev found (KLANG-00450) that a pattern asked in pieces smaller than a cycle can give other events,
   and that the live scheduler asks in whole cycles: so does the band.
6. Plans that follow a changing song (point 5): an edit or a tempo change still makes the band anew.
7. An idea of the maintainer's (2026-11-16), for later: the song sends choreography signals to the band, "f.e. in
   the form of 'empty voices' with special tags or similar... this can than also use the sprudel patterns". So a
   move, a light or a camera is asked for from the song itself, in time with it, by a voice that makes no sound.
   Today the reader drops events without tags and control events; a signal would come through there. Likely a
   ticket of its own in KLANG-00447, together with how a song says who plays what.

## Acceptance

In the app, with Der Schmetterling playing, the maintainer opens the live act and the band plays what is heard,
from the moment it is opened.

## History

- 2026-11-16: created.
- 2026-11-16: the rolling look ahead of 8 cycles, as a trial (step 5); the idea of choreography signals noted (step 7).
