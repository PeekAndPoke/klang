# Chip-style instruments by composition (NES, C64 and friends)

Status: **future, not planned.** Opened 2026-09-29 by the maintainer, in the conversation about the old Engine DSL.

## The idea (maintainer, 2026-09-29)

"The idea of the EngineDsl at some point was to be able to configure how the oscillators behave, f.e. saw vs. zaw,
f.e. what the saw flyback time is. ... Then a user could create a custom saw like `buildMySaw(freq) { return
Ignitor.saw(freq, x => x...) }`. So people could try to build oscillators that sound like their favorite 80s hardware like
nintendo, commodore 64 etc."

The mechanism exists: every oscillator takes a configure lambda with its character knobs, and an instrument is a
value a KlangScript function can return. The engine-level "tuning profile" the old design planned (`EngineTuning`, a
`c64` / `nes` identity: `../../tasks-archive/2026-09/20260927-engine-tuning-profile.md`, closed 2026-09-27) is not
needed: a chip is a small library of instrument functions, composed from the building blocks.

```javascript
let nesPulse = (duty) => Ignitor.square(x => x.duty(duty).flankSamples(0).analog(0))  // hard edges, no drift
let nesTri   = Ignitor.triangle().crush(4)                                            // a stepped, low-bit triangle
let mySaw    = (freq) => Ignitor.saw(freq, x => x.resetSamples(0).analog(0))          // an instant flyback
```

(Sketches: verify each against the stdlib before they go into a doc.)

## What exists (2026-09-29)

- Character knobs on the builders: `Ignitor.saw` / `Ignitor.ramp` `resetSamples`, `shapeMax`, `analog`; `Ignitor.square` `duty`,
  `flankSamples`, `riseFlank`, `fallFlank`, `analog`; the super oscillators' unison knobs
  (`klangscript-libs/src/commonMain/kotlin/stdlib/IgnitorBuilders.kt`).
- Stages and math: `crush` (bit depth), `coarse` (sample rate), per-sample `round`, `floor`, `ceil`, `range`
  (`KlangScriptIgnitorExtensions.kt`).
- Sharing: KlangScript modules (`export` / `import`), so a `chips` script could ship the functions.

## The work

1. **Fewer primitives.** `Ignitor.zawtooth`, `Ignitor.zamp` and `Ignitor.pulze` (`IgnitorDsl.Zawtooth`, `Zamp`, `Pulze` wire
   nodes; `Ignitors.zawtooth` / `zamp` / `rawPulze`) are saw, ramp and square with their edge knob at 0. Fold them
   into the knobs (`Ignitor.saw(x => x.resetSamples(0))` and so on), removed rather than deprecated, IF a render shows the
   folded form is bit-identical (or the difference is stated and heard). Their built-in sound names (`s("zawtooth")`
   and friends, if registered) map onto the knob form. The same move as `docs/tasks-archive/2026-10/20261002-tremolo-as-composition.md`.
2. **The gaps, for a design round** (each only if a chip really needs it; composition first):
   - hard sync (one oscillator restarting another's cycle; the C64 SID's signature),
   - the NES noise channel's short mode (a shift-register noise that repeats and sounds pitched),
   - the SID's combined waveforms and its filter (chip-specific non-linearities),
   - pitch quantised to a chip's timer steps (may compose from `Ignitor.freq()`, division and `round`; check first).
3. **Proof:** three or four 80s-style prototypes in `docs/instrument-prototypes.md` (an NES pulse and triangle, a
   C64-ish saw), rendered and heard.
