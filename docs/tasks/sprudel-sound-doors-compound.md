# The snd* sound doors as compound objects

Opened 2026-09-07 as the follow-up of the field-accessor rollout
(`docs/tasks-archive/2026-09/20260907-sprudel-field-accessors.md`). Every other compound door is an
object with named slots and reader children; the `snd*` family in
`sprudel/src/commonMain/kotlin/lang/addons/lang_snd_addons.kt` is the one surface still built from
per-sound functions. Maintainer's idea (2026-09-07): "the snd* prefix looks like it would happily
live on an object as well: `object Snd { object supersaw { fields } }`. Not fully sure."

## The surface today (19 doors, all `@KlangScript.Function`, no objects, no readers)

| Door             | Slots                                                 |
|------------------|-------------------------------------------------------|
| `sndPluck`       | `decay, brightness, pickPosition, stiffness`          |
| `sndSuperPluck`  | `voices, spread, decay, brightness, pickPosition, stiffness` |
| `sndSine`, `sndSaw`, `sndSquare`, `sndTriangle`, `sndRamp`, `sndZamp`, `sndPink` | `params` (one slot) |
| `sndNoise`       | `color`                                               |
| `sndBrown`       | `depth`                                               |
| `sndPulze`       | `duty`                                                |
| `sndDust`        | `density, tail`                                       |
| `sndCrackle`     | `chaos`                                               |
| `sndSuperSaw`, `sndSuperSine`, `sndSuperSquare`, `sndSuperTri`, `sndSuperRamp` | `voices, spread` |

Each sets `sound(...)` plus oscillator params (`oscParams`), which is also what `unison(voices,
spread, pan)` and `analog`, `duty`, `onepole` write today.

## Questions to settle before building

1. **Shape.** One nested object `Snd` with a child object per sound (`Snd.supersaw(voices = 9)`,
   `Snd.supersaw.voices` reads), or flat objects `sndSupersaw` like every other compound. KSP
   supports member properties on objects (`@KlangScript.Property val supersaw = ...` holding an
   object with its own `@Invoke`); whether the analyzer resolves a call on a nested object's
   `invoke` and offers its children in completion needs a check (`FreqAccessorIntelSpec` shape).
2. **Overlap with `unison`.** `sndSuperSaw(voices, spread)` writes the same `oscParams` keys as
   `unison(voices, spread)`. Either the sound door keeps only what is specific to the sound
   (`sound("supersaw")` + `unison(...)` for the rest) or it keeps the convenience and the two
   doors share one set of readers (`unison.voices`), never a second set.
3. **The `params` doors.** `sndSine(params)` and friends take a single opaque slot; decide what it
   is (a string of oscillator params?) and whether it survives as a named slot or goes.
4. **Readers.** Numeric slots get children like every compound (`Snd.pluck.decay`); `color` on
   `sndNoise` is a name and gets none (`docs/tasks/future/string-slot-readers.md`).
5. **Retirement.** The per-sound functions go when the objects land (replaced surfaces are removed,
   `LangRetiredDoorsSpec`); the corpus (songs, tutorials, skill refs) migrates in the same change.

## Not before

The `/dsl-design` rules apply (two doors, parity, one word per concept, coerce not require). The
editor-tools rework (`editor-tools-named-arguments.md`) is independent.
