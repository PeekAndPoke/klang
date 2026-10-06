# One name per oscillator shape across the DSLs

Status: **DONE 2026-10-06, archived.** Decided by the maintainer on 2026-10-06, built on branch `oscillator-phase`.

## The ask (maintainer, 2026-10-05)

"Also now that i saw the oscillator list in sprudel. I think we also have to align these in the Ign.xxx() family."
The sprudel side was cleaned first (`docs/tasks-archive/2026-10/20261005-sprudel-signals-range-cleanup.md`): its
signals are now `sine`, `cosine`, `saw`, `tri`, `square`, `perlin`, `berlin`, `rand`.

## Where the names stand (2026-10-05)

| Shape           | sprudel signal | Ignitor door                  | sound name (`s("...")`) | LFO shape (`LfoShapes`) |
|-----------------|----------------|-------------------------------|-------------------------|-------------------------|
| sine            | `sine`         | `Ign.sine`, `supersine`       | `sine`, `sin`           | `sine` (alias `sin`)    |
| cosine          | `cosine`       | none                          | none                    | none                    |
| rising saw      | `saw`          | `Ign.saw`, `supersaw`         | `saw`, `sawtooth`       | `sawtooth` (alias `saw`) |
| falling saw     | `saw.range(1, 0)` | `Ign.ramp`, `superramp`    | `ramp`                  | `ramp`                  |
| triangle        | `tri`          | `Ign.triangle` (now `Ign.tri`), `supertri` | `triangle`, `tri`       | `triangle` (alias `tri`) |
| square / pulse  | `square`       | `Ign.square`, `supersquare`   | `square`, `sqr`, `pulse` | `square` (aliases `sqr`, `pulse`) |
| noise           | `perlin`, `berlin`, `rand` | `Ign.perlin`, `berlin`, the noise colours | the noise names | none |

## What is already covered elsewhere

- **cosine on the Ignitor**: arrives with the phase knob, as a sine with `phase = 0.25`
  (`docs/tasks-archive/2026-10/20261006-oscillator-phase-knob.md`).
- **`ramp`, `zamp`, `zawtooth`, `pulze`**: folding the raw twins into their rounded oscillators with an edge knob is
  `docs/tasks/future/chip-style-instruments.md`, which needs a bit-identity proof per fold.

## Decisions (maintainer, 2026-10-06)

1. **The Ignitor's triangle door is `tri`**, matching sprudel's signal `tri` and the super variant `supertri`:
   `Ign.tri()` / `Ignitor.tri()`. `triangle` as a door is removed, not deprecated (one word per concept). End to end:
   the door on both doors (`KlangScriptIgnitor.tri`), the builder `OscTriBuilder` (as `OscSuperTriBuilder`), the wire
   node `IgnitorDsl.Tri` with `@WireName("tri")` (the wire ships with the frontend and the worklet, nothing is
   persisted) and the engine factory `Ignitors.tri`. Prose still says "a triangle wave"; only names changed.
   Guard: `RetiredIgnitorNamesSpec` (the triangle rows).
   **Stays as it is, on purpose:** the built-in SOUND names (`s("triangle")` and `s("tri")` both, in
   `audio_be/.../ignitor/IgnitorDefaults.kt`), the LFO shape names in `audio_bridge/.../LfoShapes.kt` (`triangle`
   canonical, `tri` an alias; the index order is append-only), the warmup sound list, and the sound aliases
   `saw`/`sawtooth` and `square`/`sqr`/`pulse`. The maintainer's answer named the Ignitor door; the sound and LFO
   names keep every spelling they accept today until a question about them is asked.
   **Not aligned, recorded (review, 2026-10-06):** two neighbours keep a door/node split that the triangle no longer
   has: `Ign.saw` builds `IgnitorDsl.Sawtooth` (wire name `sawtooth`, factory `Ignitors.sawtooth`), and `Ign.square`
   builds `IgnitorDsl.Pulze` while `Ign.pulze` builds `RawPulze`. The second goes with the chip-style folds
   (`docs/tasks/future/chip-style-instruments.md`); the first is an open maintainer question in
   `docs/tasks/_priorities.md`.
2. **Sprudel sounds get no `phase` slot.** `s("sine")` cannot reach an oscillator's `phase`, and that is the decision,
   not a gap: an instrument author opens it per note with `x.phase(Ignitor.param("phase", 0))` and `ignp("phase", ...)`.
   Recorded in `docs/tasks-archive/2026-10/20261006-oscillator-phase-knob.md` ("Recorded asymmetries") and
   `.claude/skills/dsl-design/door-shapes.md`.

Verified: the 18 corpus songs render identically to the phase-knob baseline (the rename moves no sample).

Left as it is, not part of decision 1: sprudel's sound door `sndTriangle()` (it sets the sound name `triangle`, as
`sndSaw()` sets `sawtooth`); it is a sound door, and sound names stay. Renaming the `snd*` family would be its own
question for the maintainer.

## The question as it stood (2026-10-05)

**The triangle.** The Ignitor door says `triangle` but its super variant says `supertri`; sprudel says `tri`; the sound
names and the LFO shapes accept both. The options:

- `triangle` everywhere with `tri` as the short name on every surface (the Ign/Ignitor pattern: a full and a short
  name for one word), `supertri` stays as the super variant's name;
- `tri` only, with `triangle` removed as a door and kept as a sound alias or not;
- leave it as it is, recorded as an asymmetry with its reason.

The same question then holds for `saw` / `sawtooth` and `square` / `sqr` / `pulse` in the sound and LFO names.
