# An authoring diagnostic: an fm above a forking detune

Status: **decided (maintainer, 2026-10-09), queued after the pitch pipeline; not started.** The first editor
diagnostic of its kind.

## What it is

Pitch pipeline step 3b made every fm topology follow the pitch exactly (`pitch-pipeline-into-the-tree.md`, step 3b),
with one exception the engine cannot process: an fm (or any pitch mod keyed by frequency) above a `detune` that forks
the note into two pitches:

```
(x + x.detune(7)).fm(m)    // one modulator would serve two carrier pitches and advance twice per block
```

The maintainer, 2026-10-09: "in this case the user would need to define the FM on both x and x.detune() and sum both,
that's fine" (the author rule of 2026-10-07's shared-modulator record), and: "If not possible we should report an
error, when something is authored that the engine cannot process."

```
x.fm(m1, ...) + x.detune(7).fm(m2, ...)   // what the author writes instead: each layer its own fm
```

## The work

- A static walk over an instrument's `IgnitorDsl` at registration, on the editor side, never on the audio thread and
  never an exception: flag a `Detune` whose inner reads `Freq` with a frequency-keyed pitch mod above it (an `fm` with
  its default `freq`, or a vibrato, `pitchMod`, `pitchEnvelope` or `accelerate` whose knobs read `Ignitor.freq()`).
  The predicates exist (`usesMusicalFreq`, the freq-key rule in `combineMods`); the walk sits beside
  `IgnitorDslWalk` in `audio_bridge`.
- Show it in the editor like a script error, naming the line and the fix. Example message: "`fm` (line 12) sits above
  `detune(7)` (line 10): one modulator would serve both pitches. Give each layer its own fm:
  `x.fm(...) + x.detune(7).fm(...)`."
- The voice still plays today's defined sound.
- **Out of scope for now:** a sprudel door over a built-in (`s("sgpad").fm(...)`, the classic FM stage above sgpad's
  detune) stays quiet; the maintainer: "one of the things we can only really decide once they happen to us outside the
  lab on a real song."
- Design the diagnostic surface once, since more will follow (the planned "declares no slot" one in
  `docs/plans/signal-flow-redesign.md`).
