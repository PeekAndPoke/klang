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

## Out of scope (and why)

- **`IgnitorDsl.simplify()`** (constant folding + identity elimination):
  considered and dropped — users don't write `plus(1).plus(1)` in practice,
  cross-op folds need distributive law (out of scope), and dedicated DSL
  nodes for composite shortcuts (`range`, `lerp`, etc.) leave nothing
  meaningful to fold.
- **Runtime fusion** of arithmetic chains: skipped — block-based per-node
  processing is already cheap (tight L1-cached loops, vectorizable, no
  branches), AST-walking interpretation would be slower for short chains.
