# IgnitorDsl — Arithmetic Batch (completed 2026-04-27)

Tracked in `docs/agent-tasks/ignitor-dsl-open-items.md` until 2026-04-27.

## What shipped

### Phase 1 — `Mul` → `Times` consolidation

- Dropped `IgnitorDsl.Mul`. Both surface entry points (`mul`, `times`) construct
  `IgnitorDsl.Times`. Updated `IgnitorDslRuntime.kt`, `maxReleaseSec()` walker, and
  affected tests (`IgnitorDslSerializationTest`, `CompositionPropertiesSpec`,
  `StdLibOscTest`).

### Phase 2 — Tier 1 arithmetic

New `IgnitorDsl` subtypes + runtime kernels + KS surface methods:

- `Neg`, `Minus`, `Abs`, `Pow`, `Min`, `Max`, `Clamp`
- `Pow` uses signed-magnitude semantics (no `NaN` for negative bases).

### Phase 3 — Tier 2 arithmetic

- `Exp`, `Log`, `Sqrt`, `Sign`, `Tanh`, `Lerp`, `Range`, `Bipolar`, `Unipolar`
- `Log` uses signed-magnitude with `log(0) = 0` (no `-Inf` propagation).
- `Sqrt` uses signed-magnitude (no `NaN` for negatives).
- `Range`, `Bipolar`, `Unipolar`, `Lerp`, `Clamp` are dedicated DSL nodes
  (single virtual call per block, no Kotlin-side expansion).

### Phase 4 — Tier 3 arithmetic

- `Floor`, `Ceil`, `Round`, `Frac`, `Mod`, `Recip`, `Sq`, `Select`
- `Mod` and `Recip` use the same epsilon (`1e-30`) substitution as `Div`.
- `Select` evaluates both branches at audio rate (no short-circuit) so stateful
  sources advance regardless of the gate.

### Phase 5 — Surface aliases + `Div` epsilon

- Alias pairs at the KS surface (each surface fn constructs the same DSL class,
  `@alias` cross-links in KDoc):
    - `plus` ↔ `add`
    - `minus` ↔ `sub`
    - `times` ↔ `mul`
    - `pow` ↔ `power`
    - `mod` ↔ `rem`
    - `lerp` ↔ `mix`
    - `neg` ↔ `negate`
    - `recip` ↔ `reciprocal`
- `Ignitor.div(divisor: Ignitor)` switched from "result = 0 on zero divisor" to
  "epsilon substitution" for parity with `Mod`/`Recip` — finite-large value
  flows through, master limiter handles the spike.

### Phase 6 — `oscp` / `oscparam` cleanup

- `oscp` overloads in `sprudel/lang/addons/lang_osc_addons.kt` rewritten as
  one-line wrappers around the corresponding `oscparam` overload (no extra
  hop through the alias).
- `@alias oscp` / `@alias oscparam` tags added to every overload so they
  cross-link in IntelliSense and the docs registry.

## Files touched

- `audio_bridge/src/commonMain/kotlin/IgnitorDsl.kt` — 24 new DSL classes,
  builder extensions, `maxReleaseSec()` walker entries; `Mul` removed.
- `audio_be/src/commonMain/kotlin/ignitor/Ignitor.kt` — 24 new runtime kernels
    + `DIV_EPSILON` private constant.
- `audio_be/src/commonMain/kotlin/ignitor/IgnitorDslRuntime.kt` — DSL→runtime
  wiring for all new nodes; `Mul` arm removed.
- `klangscript/src/commonMain/kotlin/stdlib/KlangScriptOscExtensions.kt` —
  surface methods + `@alias` KDoc tags.
- `sprudel/src/commonMain/kotlin/lang/addons/lang_osc_addons.kt` —
  oscp ↔ oscparam alias cleanup.
- Round-trip tests added in `IgnitorDslSerializationTest`.
- Pre-existing tests updated to handle the additional `IgnitorDsl` variants
  alongside Math counterparts (`StdlibDocsInferenceTest`,
  `GeneratedRegistrationTest`).

## Phase 7 — Numerical safety contract (2026-04-27, post-batch)

After the batch landed, a follow-up review identified that the per-op safety
guards used ad-hoc constants (e.g. `DIV_EPSILON = 1e-30f`) without a coherent
contract, and that `Recip` of subnormal-but-nonzero inputs still overflowed to
`+Inf`. Researched established conventions in JUCE, SuperCollider, Faust,
ChucK/STK, Pure Data, CSound, and Web Audio (full report:
`tmp/audio-safety-bounds-research.md`; condensed reference:
`audio/ref/numerical-safety.md`).

**Decision: adopt SuperCollider/ChucK's `1e-15 / 1e15` convention** as
`SAFE_MIN` / `SAFE_MAX`. Round-trip-safe through reciprocal, ~±300 dBFS, well
above subnormal threshold.

**Implementation in `Ignitor.kt`:**

- Two new helpers: `safeDiv(d: Float)` (sign-preserving magnitude clamp ≥
  `SAFE_MIN`, scrubs `NaN`) and `safeOut(v: Float)` (clamp to `±SAFE_MAX`,
  scrubs `NaN` to `0`).
- Divisor-class ops (`Div`, `Mod`, `Recip`) now use `safeDiv` on the divisor.
- Output-clamp ops (`Times`, `Pow`, `Exp`, `Sq`, `mul-by-const`) now use `safeOut`.
- `Ignitor.div(divisor: Double)` constant overload now also handles
  `divisor == 0.0` via `safeDiv`.

**Naturally bounded ops left unguarded** (output bounded by inputs algebraically):
`Plus`, `Minus`, `Lerp`, `Range`, `Min`, `Max`, `Clamp`, `Bipolar`, `Unipolar`,
`Tanh`, `Abs`, `Neg`, `Sign`, `Floor`, `Ceil`, `Round`, `Frac`, `Sqrt`, `Log`.

## Phase 8 — Distort DC-lock fix (2026-04-27, post-batch)

User reported that very high `distort` amounts produced output locked at `-1`
("DC lock"), risking speaker damage. Root cause: at extreme drive, even
"symmetric" shapes (`soft`, `hard`, etc.) produce a near-square wave that
becomes asymmetric whenever the input has any DC bias (e.g. ADSR release
through a heavily-driven shape). The DC blocker was previously only applied
to shapes flagged `needsDcBlock = true` (only `diode`, `rectify`).

**Fix:** apply DC blocker unconditionally to all `distort`/`clip` outputs in
both runtime paths:

- `audio_be/.../ignitor/IgnitorEffects.kt` — ignitor `distort` and `clip`
- `audio_be/.../voices/strip/filter/DistortionRenderer.kt` — legacy renderer

Removed the now-vestigial `needsDcBlock` field from `ResolvedShape`
(`audio_be/.../DistortionShape.kt`).

**Trade-off:** transient overshoots up to ~2x at sharp transitions of
square-like signals (inherent behavior of any HP-filter-based DC blocker on
step inputs). Existing tests updated to allow this; a new test pins the
no-DC-lock contract:

```kotlin
"distort at extreme drive does NOT DC-lock — the safety contract" {
  val buf = generate(Ignitors.sine().distort(5.0, "soft"), freqHz = 440.0)
  val steady = buf.sliceArray(1024 until buf.size)  // skip startup transient
  val mean = steady.map { it.toDouble() }.average()
  abs(mean) shouldBeLessThan 0.1   // mean near zero
  steady.none { it.isNaN() || it.isInfinite() } shouldBe true
}
```

## Phase 9 — Runtime semantic tests (2026-04-27, post-batch)

New file `audio_be/src/commonTest/kotlin/ignitor/IgnitorArithmeticTest.kt` —
60 tests covering math correctness, edge cases, and the safety contract for all
24 arithmetic ops. Includes `pow(1e10, 10)`, `exp(40)`, `recip(1e-25)`,
`1e20 * 1e20` etc. — all assert `allFinite()` + `≤ SAFE_MAX`. Also covers
deeply-nested chains and `1/sin near zero crossing` (audio-rate scenario).

## Known gaps surfaced by the post-batch review

The pitch-mod factories in `audio_be/.../ignitor/PitchModFactories.kt`
(`vibratoModIgnitor`, `accelerateModIgnitor`, `pitchEnvelopeModIgnitor`, FM mod)
all do unguarded `2.0.pow(...)` or `effectiveDepth / freqHz` arithmetic that
can overflow Float for extreme user inputs and poison phase accumulators. Same
class of hazard the safety contract was designed to catch — but these
ignitors weren't part of the batch and were not updated. Documented in
`audio/ref/numerical-safety.md` under "Known gaps". To be addressed in a
follow-up.

## Out of scope (and why)

- **`IgnitorDsl.simplify()`** (constant folding + identity elimination):
  considered and dropped — users don't write `plus(1).plus(1)` in practice,
  cross-op folds need distributive law (out of scope), and dedicated DSL
  nodes for composite shortcuts (`range`, `lerp`, etc.) leave nothing
  meaningful to fold.
- **Runtime fusion** of arithmetic chains: skipped — block-based per-node
  processing is already cheap (tight L1-cached loops, vectorizable, no
  branches), AST-walking interpretation would be slower for short chains.
