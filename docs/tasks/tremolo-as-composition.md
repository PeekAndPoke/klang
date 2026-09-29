# The tremolo becomes a composition of the oscillators

Status: **BUILT 2026-09-29 on branch `tremolo-composition`** (review: 4 rounds, the last clean). Open: the maintainer's
verdict on the triangle and square start points (listening pairs 80 to 83), then archive.
Decided 2026-09-29 by the maintainer after listening. V1 by the sorting rule
([`_v1-scope.md`](_v1-scope.md)): it changes the surface a tutorial teaches (two knobs go) and how three shapes
sound (their clicks go).

## Why (the maintainer, 2026-09-29)

"I would like to keep the number of primitives as low as possible. We should by now be at a stage where often times
things can be composed from the existing building blocks." And: "we can remove the TremoloIgnitor and model it
through the other means we have. The `.tremolo()` doors would stay, but the dsp surface would shrink: this is a
win."

The tremolo carries its own LFO (`LfoShape`, `TremoloCore`, the `TremoloIgnitor`), a second implementation of five
shapes the oscillators already have. Its square, sawtooth and ramp click (the LFO jumps instantly), because that copy
lacks the soft edges the oscillators have (`flankSamples`, `resetSamples`).

## The evidence (listening folder, not in git; the verdicts are in `by-ear/README.md` owed round 1)

- Pairs 21 to 23: today's square, sawtooth and ramp tremolo click, "not useful".
- Pairs 70 to 73: the same tremolo composed as `Osc.<shape>(rate, o => ...edges... .analog(0)).range(1 - depth, 1)`
  multiplied into the voice: no clicks ("fine"); the composed sine matches today's to -76 dB relative to the signal
  (one sample of phase: `TremoloCore` advances its phase before reading it, the oscillator after).
- Pairs 74 to 78: the edge length. 8 ms still thumps on the sawtooth; **16 ms for all shapes**.
- Pair 20: sine and triangle differ audibly; both stay.

## The decisions

1. **The doors stay**: sprudel `tremolo(...)` and the Ignitor `.tremolo(...)`, with `depth`, the rate (`sync` on
   sprudel, `rate` on the Ignitor; the naming question is [`future/tremolo-rate-naming-parity.md`](future/tremolo-rate-naming-parity.md))
   and `shape` (`sine`, `triangle`, `square`, `sawtooth`, `ramp`, the oscillator names).
2. **`skew` and `phase` are dropped on every surface** (maintainer: "drop the skew", "drop phase too"): the sprudel
   door's slots, the `tremolo.skew` / `tremolo.phase` slot keys (`IgnitorDsl.Slots.tremolo`), the Ignitor door's
   arguments, the node fields, the editor tool (`SprudelTremoloEditorTool`), docs. Removed, not deprecated (one
   word per concept). No built-in or frozen song uses either (checked 2026-09-29); the only tremolo `shape` in the
   songs is `"sine"` (Drunken Sailor).
3. **Edges are 16 ms**, a time, converted to samples at the voice's sample rate (`flankSamples` for the square,
   `resetSamples` for sawtooth and ramp). One constant in `audio_bridge/constants/`.
4. **The law stays**: gain = `1 - depth * (1 - level)`, level = `(osc + 1) / 2`, which is `.range(1 - depth, 1)` on a
   ±1 oscillator. The LFO oscillator runs with `analog = 0` (the tremolo's rate must not drift). The rate is read
   per block, as today.

## What gets built (corrected after the task review of 2026-09-29)

The composition cannot happen when the tree is WRITTEN: the shape is a slot (`classic()` places `tremolo.shape`, and
the script door accepts a slot too), and the edge length in samples needs the sample rate. Both are known only when
the voice is BUILT (`IgnitorBuildCache.sampleRate`). So:

- **The `IgnitorDsl.Tremolo` node stays** as the description both doors build (`inner`, `rate`, `depth`, `shape`), with
  its `@WireName("tremolo")`; `skew` and `phase` leave it.
- **Its runtime branch composes** (`audio_be/src/commonMain/kotlin/ignitor/IgnitorDslRuntime.kt`, the `Tremolo` arm):
  at build it reads the shape index and builds the oscillator (`Ignitors.sine`, `triangle`, the square with
  `flankSamples = TREMOLO_EDGE_SECONDS * cache.sampleRate`, `sawtooth` / `ramp` with `resetSamples` the same) at the
  rate, `analog = 0`, then `range(1 - depth, 1)`, multiplied into the inner signal (`Times`).
- **The off value and the culler stay as they are**: the arm keeps today's gate (depth at or below 0 builds no stage,
  `audio/ref/off-values.md`), so a voice with no tremolo runs no oscillator, and it keeps setting `gatesOutput`, so the
  silence culler never ends a voice in a tremolo's trough. No marker node and no change to `Times`.
- **Removed**: `TremoloIgnitor` and the public `Ignitor.tremolo(...)` (`audio_be/.../ignitor/IgnitorEffects.kt`),
  `TremoloCore.kt`, `LfoShape.kt` (its helpers are used by nothing else). `LfoShapes` in `audio_bridge` STAYS: it is the
  shape-name vocabulary (sprudel `_classic_slot_params.kt`, the editor tool, the culling spec).
- **Both doors lose `skew` and `phase`**: sprudel `tremolo(...)`, its slots and `SprudelTremoloEditorTool`; the Ignitor
  script door (`EffectBuilders.kt`) and the Kotlin door `IgnitorDsl.tremolo(...)`; `Slots.tremolo`,
  `KlangScriptClassicSlots`, `KlangScriptOscSlot`; `IgnitorDslWalk`, `GraphCensus`, `LexikonData`,
  `GraalSprudelPattern`. The compiler finds the rest.
- **Negative depth**: today a depth at or below 0 bypasses; the gate keeps that, so `range(1 - depth, 1)` only ever
  sees a positive depth.

## As built (2026-09-29)

- `tremoloGain` (`IgnitorDslRuntime.kt`) floors EVERY depth: `range(1 - max(depth, 0), 1)`. Three review rounds each
  found another spelling of a depth at or below 0 that still boosted (a moving signal, block-constant arithmetic,
  `Variants` / `OptimizerHint` dissolving to a bare leaf); the unconditional floor closes the class by construction,
  and a block-constant depth folds to one value per block, so the baselines did not move.
- Found on the way and fixed: a saw or ramp at a NaN frequency emitted NaN every sample (a NaN flyback), in the single
  and the unison oscillator; a NaN guard now substitutes `shapeMax`.
- Above about 31.25 Hz the 16 ms edge no longer fits, and square, sawtooth and ramp all become the same symmetric
  triangle (stated in the `TREMOLO_EDGE_SECONDS` KDoc). The sine LFO takes 3 rng draws at its first block, even at
  analog 0 (accepted).

## Guards (mandatory tier, every new row mutation-checked)

- The law as a relation: the composed stage's gain at every sample equals `1 - depth * (1 - (osc + 1) / 2)` of the
  same oscillator on the same clock (not a restated constant).
- The edges: at a rate below about 31 Hz (above it the edge is capped by the duty or `shapeMax` and gets shorter),
  the largest sample-to-sample gain step on square, sawtooth and ramp is at most `depth / (0.016 * sampleRate)` plus a
  small tolerance; a step edge turns it red.
- The culler: a voice with a full-depth square tremolo is not culled in its trough (today's culling spec row, kept).
- Door parity: the sprudel and Ignitor doors build the same tree; `skew` and `phase` are gone from both
  (`KlangScriptWaveshaperDoorParitySpec`, `ClassicDoorRenderParitySpec`).
- Baselines with a tremolo row (`ClassicVoiceBaselineSpec`, `BuiltInVoiceMatrixSpec` if any) are regenerated from the
  tree after the change, never hand-edited.

## Migration

- Sound: the sine moves by one sample of phase (measured -76 dB, inaudible). Square, sawtooth and ramp lose their
  clicks (the point). **Start points change for two shapes** (the start-phase knob was declined, and the coordinator
  chose the oscillators' own start points over a hidden phase mechanism): the oscillator triangle starts at its lowest
  point where today's starts at the midpoint rising (a quarter cycle), and the square starts low and fades in over
  16 ms where today's starts high. No song uses either shape (Drunken Sailor's tremolo is a sine); a listening pair
  after the build gets the verdict.
- Docs: `.claude/skills/klang-music-writing/ref/sprudel-reference.md` and `ignitor-reference.md` (the `tremolo(depth,
  sync, shape, skew, phase)` rows), the Lexikon, `audio/MEMORY.md` and `audio/ref/`, the rules register's retired list
  (`tremolo.skew`, `tremolo.phase`, `TremoloIgnitor`, `TremoloCore`, `LfoShape`), the tutorials (their own session).

## Links

- The listening verdicts: [`by-ear/README.md`](by-ear/README.md), owed round 1.
- The oscillator edge knobs: `klangscript-libs/src/commonMain/kotlin/stdlib/IgnitorBuilders.kt` (`OscSquareBuilder.flankSamples`,
  `OscSawBuilder.resetSamples`, `OscRampBuilder.resetSamples`), `audio_bridge/.../constants/OscillatorTuning.kt`.
- A start-phase knob on the oscillators was considered and not added (no use today); add it to the oscillators,
  not to the tremolo, when a real use appears.
