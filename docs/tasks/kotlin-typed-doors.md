# Typed Kotlin doors: the compiler checks every door

Status: **decided (maintainer, 2026-10-13, Q24); design step done 2026-10-16 (option C below, awaiting the maintainer's
answers to Q44 to Q51 in `_maintainer-questions.md`), no code yet.** Large, mostly mechanical.

## Why

"I chose Kotlin exactly for the compiler." Today's Kotlin door types every Ignitor value as `IgnitorDslLike`, a
`typealias` for `Any` (`klangscript-libs/.../KlangScriptIgnitorExtensions.kt`), so `osc.vibrato(rate = "fast")`
compiles and fails only at run time. Beside it, the `audio_bridge` extensions (`fun IgnitorDsl.vibrato(rate: Double,
semitones: Double)` and friends) are a third spelling with limits of their own: eleven take only numbers, the
arithmetic and the filters take all numbers or all signals, `phaser` and `shimmer` have no `floor`.

## The decisions

1. **One typed Kotlin door per DSL.** Every value parameter takes the DSL's own type (`IgnitorDsl`,
   `KatalystKnobDsl`), so a number or a signal fits each argument on its own and there are no 2^n overloads.
   Numbers come in through two plain extension properties, `.ign` (an `IgnitorDsl.Constant`) and `.kat` (a
   `KatalystKnobDsl.Constant`):
   ```kotlin
   osc.vibrato(rate = 0.5.ign, semitones = 0.3.ign)
   osc.vibrato(rate = Ign.perlin(), semitones = 0.3.ign)   // a mix, the same one function
   ```
   One shared `.const` class in both sealed wire hierarchies was considered and dropped (the wire codec would need a
   class with two parents; complexity is the enemy).
2. **The mechanical changes are accepted.** Engine code, `classic()`, the defaults and the specs that write plain
   numbers today gain `.ign` / `.kat`; the `audio_bridge` shorthand extensions retire into the typed door. A pure
   replacement needs no mutation checks: everything replaced and every test still green is done.
3. **KlangScript keeps its own door.** The KlangScript door functions (`@KlangScript...`, number-or-signal at run
   time, as the script needs) become internal to their module and are no longer the Kotlin door. The Kotlin door is
   not exposed to KlangScript. Either the Kotlin door calls into the KlangScript door, or both call into one
   underlying layer; which one is the first design step. Also mostly mechanical.

## Open for the first step

- The layering in 3: the Kotlin door over the KlangScript door, or both over a shared layer.
- The "Two doors, one DSL" rule (`CLAUDE.md`, `/dsl-design` §3) and the door-parity specs: what parity means once the
  Kotlin door is typed and the script door is not (same names, meaning and scale stay; the types differ by design).
- The order: Ignitor first, then Katalyst; how the sweep is cut into reviewable steps.
- Related, separate: curves as nodes ([`future/curves.md`](future/curves.md)), where values plug into nodes the same way.

## Design (2026-10-16, read-only design step)

**Recommended layering, option C: the KlangScript door calls the Kotlin door; the Kotlin door is flat, in `audio_bridge`.**
Filters and `adsr` already work this way (`IgnitorDsl.kt:2768-2774`, the script door converts and calls it,
`KlangScriptIgnitorExtensions.kt:239`). The one public Kotlin door is the `audio_bridge` extension set, typed only
(about 28 `Double` twins deleted, 10 number-only doors retyped), holding every door's logic; oscillators and
Katalyst modulators use their node constructors. The script door (`KlangScriptIgnitor`, `KlangScriptIgnitorExtensions`,
the builders) becomes `internal`, converts (`toIgnitorDsl`, `toKatalystKnob`) and delegates. Why C: it is the only
option the engine, `IgnitorDefaults.kt` and the `audio_be` specs can reach (`audio_be` depends on `audio_bridge` only);
it extends a precedent instead of adding a layer; types flow inward and convert once at the script edge; fewest new
functions. Options A (Kotlin door wraps the script door in `klangscript-libs`, about 270 new wrappers, unreachable from
the engine) and B (both build on a shared layer, every door written twice) were weighed and not recommended.

```kotlin
// audio_bridge: THE Kotlin vibrato
fun IgnitorDsl.vibrato(rate: IgnitorDsl, semitones: IgnitorDsl, rangeFrom: IgnitorDsl = VIBRATO_RANGE_FROM.ign,
                       rangeTo: IgnitorDsl = VIBRATO_RANGE_TO.ign, phase: IgnitorDsl = VIBRATO_PHASE.ign) = ...
saw.vibrato(5.ign, 0.5.ign, rangeFrom = 0.ign, phase = 0.75.ign)
saw.vibrato(rate = IgnitorDsl.Sine(freq = 0.5.ign), semitones = 0.3.ign)   // a mix, one function
```

**Extensions:** `Double.ign`, `Int.ign`, `Double.kat`, `Int.kat` returning the precise `Constant`, plus `unaryMinus` on
`Constant` so `-1.ign` is `Constant(-1)` like the script's `-1` (boxing at build time is fine, maintainer 2026-10-16).
Name knobs through catalogue indices (`BodyMaterials.indexOf("wood").kat`).

**Rule text (proposed):** "Two doors, one DSL: every surface addition lands on the typed Kotlin door (`audio_bridge`;
every value an `IgnitorDsl` / `KatalystKnobDsl`, numbers through `.ign` / `.kat`) AND the KlangScript door (stdlib,
internal, number or signal at run time) in the same deliverable, with a door-parity spec. Same word, meaning, scale
and default; the types differ by design." Plus `/dsl-design` §3 and `door-shapes.md` adapted.

**Steps:** 1 the extensions and rule texts (about 7 files); 2a filters/eq (34 files), 2b adsr (16), 2c arithmetic
(31), 2d effects and pitch (34), each mechanical apart from the `IgnitorDsl.kt` hunk; 3 the Ignitor script door goes
internal and delegates (about 45, needs a reviewer); 4 the Katalyst chain door in `audio_bridge` (about 15, new door,
logic moves); 5 optional `.ign`/`.kat` for existing `Constant(x)` lines, only where edited anyway.
