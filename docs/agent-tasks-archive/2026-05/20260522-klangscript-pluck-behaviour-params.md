# KlangScript — Expose Pluck Behaviour Params (completed 2026-05-22)

Follow-on to `20260518-ignitor-slots-and-super-osc-cleanup.md`. That cleanup
established `IgnitorDsl.Slots.{decay, brightness, pickPosition, stiffness}` and
wired them into `IgnitorDefaults.kt` so sprudel-side modulation works, but the
klangscript-facing `Osc.pluck()` / `Osc.superpluck()` factories still only
accepted `freq` (and `voices` / `freqSpread` for superpluck). Anything beyond
that required either an `oscparam()` override or a sprudel chain — there was no
way to set behaviour at construction time.

## What shipped

`klangscript/src/commonMain/kotlin/stdlib/KlangScriptOsc.kt`:

- `pluck(freq, decay = 0.996, brightness = 0.5, pickPosition = 0.5, stiffness = 0.0)`
-
`superpluck(freq, voices = 8.0, freqSpread = 0.2, decay = 0.996, brightness = 0.5, pickPosition = 0.5, stiffness = 0.0)`

Defaults mirror the `IgnitorDsl.Pluck` / `IgnitorDsl.SuperPluck` data-class
defaults so every existing call site behaves identically. New params are
appended positionally after the pre-existing ones — existing tests
(`Osc.pluck()`, `Osc.superpluck(Osc.freq(), 4, 0.05)`) continue to pass without
modification.

`.analog(x)` stays on the extension method, matching the convention of every
other oscillator (sine, saw, supersaw, …): identity params (freq, voices,
freqSpread, decay, brightness, pickPosition, stiffness) go in the ctor;
modifiers (analog, filters, envelopes) go via chained extension.

## Usage

```javascript
// Defaults — unchanged behaviour
Osc.pluck()
Osc.superpluck()

// Tweak per-call behaviour positionally
Osc.pluck(/* freq */ Osc.freq(), /* decay */ 0.998, /* brightness */ 0.8)
Osc.superpluck(Osc.freq(), 6, 0.15, /* decay */ 0.999, /* brightness */ 0.6)

// Still works — analog stays a chain modifier
Osc.pluck().analog(0.4)
```

## Verification

- `:klangscript:compileKotlinJvm` — green. KSP regenerates the
  library / completions / docs from the `@KlangScript.Method` annotations on
  the next build; no manual edits to `KlangScriptLibrary.kt`,
  `KlangDocsRegistry.kt`, or `CompletionProvider.kt`.
- `:klangscript:jvmTest --tests "io.peekandpoke.klang.script.stdlib.StdLibOscTest"`
  — green. Existing pluck/superpluck assertions still match.

## Out of scope

- Optimisations to the ignitor DSL (constant folding, block-rate shared
  subtrees, per-voice CSE) discussed alongside this change but not implemented.
  Tracked separately when the work is started.
