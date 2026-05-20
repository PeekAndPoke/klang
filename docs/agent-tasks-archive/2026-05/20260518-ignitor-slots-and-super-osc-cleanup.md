# Ignitor Slots + Super-Osc Surface Cleanup

> **Status**: planned, ready to implement. No klangscript taxonomy changes required.
> The bigger annotation-taxonomy redesign (`@Constant` rename + member-`@Property` +
> KSP scope enforcement) is captured at the bottom as a deferred follow-up.

## Goal

Three small, related cleanups in the Ignitor DSL surface:

1. Remove the `analog` constructor argument from super-oscillator script factories
   (`supersaw`, `supersine`, `supersquare`, `supertri`, `superramp`, `superpluck`).
   The chained `.analog(x)` extension already covers all oscillators uniformly, so
   keeping `analog` only on the super variants creates an asymmetric surface.
2. De-duplicate the well-known `ParamIgnitor("name", default)` literals scattered
   across `Ignitors.kt` factory defaults and `IgnitorDefaults.kt` registrations,
   by introducing a shared `IgnitorDsl.Slots` registry of canonical open slots.
3. Expose those slots to KlangScript as `OscSlot.analog()` / `OscSlot.voices()` etc.,
   so user-registered sounds can opt-in to sprudel modulation without having to
   memorise the slot name + default pair.

## Background — what's in place today

### Two layers of "default analog"

The `analog` parameter exists at three levels:

| Level                                         | Default                            | Behaviour                                                                 |
|-----------------------------------------------|------------------------------------|---------------------------------------------------------------------------|
| `IgnitorDsl.Sine(analog = …)` (data class)    | `Constant(0.0)`                    | Sealed — sprudel `.analog(x)` is **ignored**.                             |
| `IgnitorDefaults.kt` registered "sine" sound  | `Param("analog", 0.0)` (open slot) | Opt-in — sprudel `.analog(x)` flows through `oscParams["analog"]` lookup. |
| `Ignitors.sine(analog = …)` (runtime factory) | `ParamIgnitor("analog", 0.0)`      | Functionally a constant filler at 0.0. The `name` is a debug label only.  |

The sprudel flow is:

```
note("a").analog(2)
  → withOscParam("analog", 2.0)              [sprudel/lang_osc_addons.kt:128]
  → SprudelVoiceData.oscParams["analog"] = 2.0
  → IgnitorDsl.toExciter(oscParams)          [audio_be/.../IgnitorDslRuntime.kt:28]
  → is Param("analog", 0.0) → ParamIgnitor("analog", 2.0)
```

So the only step that listens is `IgnitorDsl.Param(name, default)` — and that's
controlled at sound-registration time, not at DSL-construction time. The data-class
defaults stay sealed; the registration in `IgnitorDefaults.kt` upgrades them to
open slots.

### Asymmetry in the script-facing factories

Today in `klangscript/.../stdlib/KlangScriptOsc.kt`:

- `sine(freq = Freq)` — no `analog` argument.
- `supersaw(freq = Freq, voices = 8.0, freqSpread = 0.2, analog = 0.0)` — has `analog`.

But `.analog(x)` works on both, via the extension in `KlangScriptOscExtensions.kt:248`.
So `analog` as a constructor arg on the super variants is redundant.

### Slot literal duplication

`audio_be/.../ignitor/Ignitors.kt`: every oscillator/super-oscillator/pluck factory
re-declares `analog: Ignitor = ParamIgnitor("analog", 0.0)`, `voices: Ignitor =
ParamIgnitor("voices", 8.0)`, `freqSpread: Ignitor = ParamIgnitor("freqSpread", 0.2)`,
`duty: Ignitor = ParamIgnitor("duty", 0.5)`, etc. — ~30 occurrences across the file.

`audio_be/.../ignitor/IgnitorDefaults.kt`: same names + defaults reappear in
`Param("analog", 0.0)`, `Param("voices", 8.0)`, … one per built-in sound.

Centralising as named singletons reduces the chance of a future drift (e.g. one
factory using `0.0` and another using `0.001`) and makes the canonical set of
"sprudel-known parameter names" discoverable in one place.

## Plan

### Step 1 — `IgnitorDsl.Slots` registry

In `audio_bridge/.../IgnitorDsl.kt`, inside the sealed interface, add:

```kotlin
sealed interface IgnitorDsl {
    /**
     * Canonical open parameter slots that mirror sprudel's `withOscParam(name)` calls.
     * Use these when registering a sound whose parameters should respond to sprudel
     * modulation (e.g. `note("c").analog(0.3)`).
     */
    object Slots {
        val analog: IgnitorDsl = Param("analog", 0.0)
        val voices: IgnitorDsl = Param("voices", 8.0)
        val freqSpread: IgnitorDsl = Param("freqSpread", 0.2)
        val duty: IgnitorDsl = Param("duty", 0.5)
        val density: IgnitorDsl = Param("density", 0.2)
        val decay: IgnitorDsl = Param("decay", 0.996)
        val brightness: IgnitorDsl = Param("brightness", 0.5)
        val pickPosition: IgnitorDsl = Param("pickPosition", 0.5)
        val stiffness: IgnitorDsl = Param("stiffness", 0.0)
        val rate: IgnitorDsl = Param("rate", 1.0)
    }
    // … rest unchanged
}
```

Sealed-default behaviour (e.g. `Sine(analog = Constant(0.0))`) is unchanged.

### Step 2 — Drop `analog` from super-osc script constructors

In `klangscript/.../stdlib/KlangScriptOsc.kt`, remove the `analog` parameter from
six factories:

- `supersaw`
- `supersine`
- `supersquare`
- `supertri`
- `superramp`
- `superpluck`

Each call site continues to pass `analog = Constant(0.0)` to the underlying
`IgnitorDsl.SuperSaw(…)` etc., matching the simple oscillators. Users opt in via
the chained `.analog(x)` extension as before.

`voices` and `freqSpread` stay as constructor args — they are part of the
super-oscillator's identity, not opt-in modulation, and have no chain equivalent.

### Step 3 — Share `ParamIgnitor` singletons in `Ignitors.kt`

At the top of `object Ignitors` (in `audio_be/.../ignitor/Ignitors.kt`), add a
small block of shared defaults:

```kotlin
object Ignitors {
    // Shared parameter defaults — see IgnitorDsl.Slots for the DSL-side mirror.
    private val analogDefault = ParamIgnitor("analog", 0.0)
    private val voicesDefault = ParamIgnitor("voices", 8.0)
    private val freqSpreadDefault = ParamIgnitor("freqSpread", 0.2)
    private val dutyDefault = ParamIgnitor("duty", 0.5)
    // …only the slots we actually re-use; add more lazily.

    fun sine(
        freq: Ignitor = FreqIgnitor,
        analog: Ignitor = analogDefault,
    ): Ignitor = SineIgnitor(freq, analog)

    // … all other factories use the shared singletons
}
```

`ParamIgnitor` is stateless (`ParamIgnitor.kt` — just `name` + `defaultF` + a
`buffer.fill(...)`), so sharing one instance across all factories is safe. No
behaviour change.

### Step 4 — Use `IgnitorDsl.Slots` in `IgnitorDefaults.kt`

Replace the local helper `fun p(name, default) = IgnitorDsl.Param(name, default)`
with direct references to `IgnitorDsl.Slots.analog` etc. Example:

```kotlin
// before
val sine = IgnitorDsl.Sine(freq = IgnitorDsl.Freq, analog = p("analog", 0.0))

// after
val sine = IgnitorDsl.Sine(freq = IgnitorDsl.Freq, analog = IgnitorDsl.Slots.analog)
```

Same for `superSaw`, `superSine`, `superSquare`, `superTri`, `superRamp`, `pluck`,
`superPluck`, `pulze`, `dust`, `crackle`, `perlinNoise`, `berlinNoise`.

### Step 5 — `KlangScriptOscSlot` (new file)

`klangscript/.../stdlib/KlangScriptOscSlot.kt`:

```kotlin
package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

/**
 * Canonical open parameter slots for sprudel-compatible custom sounds.
 *
 * Use these when registering a custom sound that should respond to sprudel
 * modulation calls like `.analog()`, `.voices()`, `.freqSpread()`, etc.
 *
 * ```KlangScript(Executable)
 * let pad = *   Osc.sine().analog(OscSlot.analog()).lowpass(2000)
 *
 * ```

*/
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.Object("OscSlot")
object KlangScriptOscSlot {
override fun toString(): String = "[OscSlot object]"

    @KlangScript.Method fun analog(): IgnitorDsl       = IgnitorDsl.Slots.analog
    @KlangScript.Method fun voices(): IgnitorDsl       = IgnitorDsl.Slots.voices
    @KlangScript.Method fun freqSpread(): IgnitorDsl   = IgnitorDsl.Slots.freqSpread
    @KlangScript.Method fun duty(): IgnitorDsl         = IgnitorDsl.Slots.duty
    @KlangScript.Method fun density(): IgnitorDsl      = IgnitorDsl.Slots.density
    @KlangScript.Method fun decay(): IgnitorDsl        = IgnitorDsl.Slots.decay
    @KlangScript.Method fun brightness(): IgnitorDsl   = IgnitorDsl.Slots.brightness
    @KlangScript.Method fun pickPosition(): IgnitorDsl = IgnitorDsl.Slots.pickPosition
    @KlangScript.Method fun stiffness(): IgnitorDsl    = IgnitorDsl.Slots.stiffness
    @KlangScript.Method fun rate(): IgnitorDsl         = IgnitorDsl.Slots.rate

}

```

### Step 6 — Tests + verification

- `audio_be:jvmTest` — make sure `IgnitorRegistryTest`, `IgnitorsTest`, and
  `VibratoConsistencyTest` still pass.
- `klangscript:jvmTest` — make sure `StdLibOscTest` still passes (it already covers
  `.analog(0.3)` on `Osc.sine()` and `Osc.whitenoise()` — line 382/391).
- `sprudel:jvmTest` — `LangAnalogSpec` and other addon specs.
- Add a small `KlangScriptOscSlotTest` that verifies `OscSlot.analog()` evaluates
  to `IgnitorDsl.Param("analog", 0.0)` and that wiring it via `.analog(...)`
  produces a `Sine(analog = Param("analog", 0.0))`.
- Whole-project build: `./gradlew build` to catch any caller of the removed
  super-osc `analog` constructor parameter.

### Step 7 — Search for direct callers of the removed `analog` arg

`grep -rn 'supersaw(.*analog'`, same for `supersine`, `supersquare`, `supertri`,
`superramp`, `superpluck` in both `.kt` files and `.klang` script content
(`grep` `src/commonMain/kotlin/builtinsongs/` and `.../pages/docs/tutorials/`).
Any hits get rewritten to use chained `.analog(x)`.

## Out of scope — deferred follow-ups

### KlangScript annotation taxonomy redesign

A separate task. Captured here so we don't lose it:

```kotlin
annotation class KlangScript {
    // Top-level (file-level) declarations
    annotation class Object(val name: String)
    annotation class Function       // top-level fun
    annotation class Constant       // top-level val   (renamed from Property)

    // Members of @Object or @TypeExtensions
    annotation class Method
    annotation class Property       // member val      (NEW semantics)
}
```

Migration scope: 47 `@Property` rename, KSP processor changes
(`klangscript-ksp/.../KlangScriptProcessor.kt`,
`klangscript-ksp/.../RegistrationModel.kt`), runtime `NativeInterop` member-access
extension, intel/type-inferrer updates. KSP also gains parent-scope validation:

```
@Function   → parent must be KSFile
@Constant   → parent must be KSFile
@Object     → parent is KSFile OR @Object   (nested allowed)
@Method     → parent is @Object or @TypeExtensions
@Property   → parent is @Object or @TypeExtensions
```

Trigger: the next concrete need for a property-style accessor (e.g. `pattern.cycles`
without parens, or `Osc.slots.analog` if/when we expose `slots` as a member
property on `Osc`).

### Per-orbit / per-playback attributes

Already tracked in `ignitor-dsl-open-items.md`.

## Why not also remove the analog-as-`ParamIgnitor("analog", 0.0)` default in `Ignitors.kt`?

It's misleading naming (the "analog" string is a dead debug label at the runtime
layer — lookup happens earlier in `IgnitorDslRuntime.buildIgnitor`), but it's not
incorrect. The DSL builder path always passes the `analog` argument explicitly,
so the default never fires in practice. Replacing it with a `ConstantIgnitor(0.0)`
or renaming to remove the misleading label is pure cosmetic — defer until
someone is reading these factories and gets confused.

## Acceptance criteria

- `Osc.sine()`, `Osc.supersaw()` etc. continue to produce sealed `analog = Constant(0.0)`
  defaults — sprudel `.analog(x)` is ignored unless the sound explicitly opts in.
- Built-in sounds registered in `IgnitorDefaults.kt` continue to respond to sprudel
  `.analog(x)` via the existing open-slot mechanism — registry uses
  `IgnitorDsl.Slots.analog` (= `Param("analog", 0.0)`).
- `OscSlot.analog()` in klangscript returns the same `Param("analog", 0.0)`
  instance as `IgnitorDsl.Slots.analog`. Same for every other slot.
- `Osc.supersaw(analog = 0.3)` no longer compiles in klangscript (constructor arg
  removed). Tutorial/builtin-song callers, if any, get migrated to `.analog(0.3)`.
- All test suites green: `audio_be`, `audio_bridge`, `klangscript`, `sprudel`, and
  a project-wide `./gradlew build`.
