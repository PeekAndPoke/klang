# Typed Kotlin doors: the compiler checks every door

Status: **decided (maintainer, 2026-10-13, Q24), not started.** Large, mostly mechanical.

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
