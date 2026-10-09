# An authoring diagnostic: an fm above a forking detune

Status: **decided (maintainer, 2026-10-09), queued after the pitch pipeline; not started.** The first editor
diagnostic of its kind.

## What it is

Pitch pipeline step 3b made every fm topology follow the pitch exactly (`in-progress/pitch-pipeline-into-the-tree.md`, step 3b),
with one exception the engine cannot process: an fm (or any pitch mod keyed by frequency) above a `detune` that forks
the note into two pitches:

```
(x + x.detune(7)).fm(m)    // one modulator would serve two carrier pitches and advance twice per block
```

The maintainer, 2026-10-09: "in this case the user would need to define the FM on both x and x.detune() and sum both,
that's fine" (the author rule of 2026-10-07's shared-modulator record), and: "If not possible we should report an
error, when something is authored that the engine cannot process."

```
x.fm(m1, ...) + x.fm(m2, ...).detune(7)   // what the author writes instead: each layer its own fm, inside its detune
```

`x.detune(7).fm(m)` is fine: the detune sits below the fm, but the fm's carrier holds ONE pitch (the note detuned), so
its one modulator serves one pitch. The shape is an fm whose carrier holds TWO pitches.

## The work

- A static walk over an instrument's `IgnitorDsl` at registration, on the editor side, never on the audio thread and
  never an exception: flag a frequency-keyed pitch mod (an `fm` with its default `freq`, or a vibrato, `pitchMod`,
  `pitchEnvelope` or `accelerate` whose knobs read `Ignitor.freq()`) whose BENT CHILD (the fm's carrier, the inner of
  a vibrato and the like), followed along signal edges only, reaches the note at two pitches: a forking `Detune` (its
  inner reads `Freq`) on that walk, beside another path to a pitched source that does not pass through that detune
  (or passes through a detune of another amount). The walk does not enter a nested fm's modulator or a parameter
  position (step 3b review round 2, MINOR 3): those read the mod at one pitch through their own wrapper, and the
  engine processes them exactly. Not flagged, therefore: a plain `x.detune(7).fm(m)` (one pitch); the recipe
  `x.fm(m1) + x.fm(m2).detune(7)` (each fm inside its layer, review round 1, A MINOR 3); a forking modulator
  `x.fm((s + s.detune(7)).vibrato(5, 0.2), ...)` under a pitch node (correct since step 3b's round 1 fix); a forking
  modulator of an inner fm; a detuned oscillator in a parameter position.
- **Part of the shape it reports** (step 3b review round 2, MINOR 1, recorded as part of the residue): when the
  flagged mod reads the note and the fm's modulator holds a pitch node whose knobs read no `Freq`, that node's memo is
  filled under the first carrier pitch, so the second pitch's modulator reads the first pitch's outer mod (an exact
  identity `pitchMod(0)` in the modulator changes the output by -21.5 dB diff RMS under a vibrato on the note, -3.4 dB
  under an outer fm). The same message and the same recipe cover it; `FmModulatorTopologySpec`'s RESIDUE rows pin both
  halves.
  The predicates exist (`usesMusicalFreq`, the freq-key rule in `combineMods`); the walk sits beside
  `IgnitorDslWalk` in `audio_bridge`.
- Show it in the editor like a script error, naming the line and the fix. Example message: "`fm` (line 12) sits above
  `detune(7)` (line 10): one modulator would serve both pitches. Give each layer its own fm:
  `x.fm(...) + x.fm(...).detune(7)`."
- The voice still plays today's defined sound.
- **Out of scope for now:** a sprudel door over a built-in (`s("sgpad").fm(...)`, the classic FM stage above sgpad's
  detune) stays quiet; the maintainer: "one of the things we can only really decide once they happen to us outside the
  lab on a real song."
- Design the diagnostic surface once, since more will follow (the planned "declares no slot" one in
  `docs/plans/signal-flow-redesign.md`).
