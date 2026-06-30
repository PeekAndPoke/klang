# Sprudel DSL — test-coverage gap + overall module review

> **STATUS (2026-06-30, branch `engine-dsl-osc-dsl-parameterization`):** IN PROGRESS, user-paced. ~5 addon
> files done; the rest happen as the user reviews the module file-by-file (pasting IDE warnings). This doc
> captures the systematic gap so it can be finished deliberately rather than ad-hoc.

## The gap (root cause of the "Function `X` is never used" warnings)

Each sprudel DSL operation is defined in (up to) four overload shapes in `lang/` + `lang/addons/`:

- (a) `SprudelPattern.X(...)` — the pattern-receiver form
- (b) `String.X(...)` — the string-receiver form
- (c) `X(...): PatternMapperFn` — the top-level factory form (used via `apply(X(...))`)
- (d) `PatternMapperFn.X(...): PatternMapperFn` — the **chained-mapper** form (used via `apply(Y(...).X(...))`)

The shared `dslInterfaceTests(...)` harness only exercises forms **(a)/(b)/(c)**. Form **(d)** — the
`PatternMapperFn.X` chained mapper — is **uncovered codebase-wide**. Because nothing calls it, the IDE flags
every form-(d) function as "Function `X` is never used." The warning is real: the overload is genuinely
untested, not dead.

## The fix (per operation)

1. Add a **chained form-(d) case** to that op's `dslInterfaceTests` block, in both Kotlin and KlangScript, e.g.:
   ```kotlin
   "pattern.apply(gain(1.0).X(c))" to note("c3").apply(gain(1.0).X(c)),
   "script apply(gain(1.0).X(c))" to SprudelPattern.compile("""note("c3").apply(gain(1.0).X(c))"""),
   ```
   (Pick any cheap leading mapper — `gain(1.0)` — so the chain `.X()` is the thing under test.)
2. For ops that have **no spec at all**, write a fresh spec (don't only patch the chained case).
3. Where a parameter is genuinely vestigial (e.g. an unused `callInfo` on a pure delegator), annotate with
   `@Suppress("UNUSED_PARAMETER")` + a one-line why — don't invent fake usage.

## Done so far (branch history)

5 addon files cleared (per the working memory): the `snd*` addon block (`sndZamp` + a consolidated
`PatternMapperFn.sndX` form-(d) case via `gain(1.0).sndX()`), `LangDutySpec` (new), and form-(d) cases added to
`LangHpadsr/Bpadsr/Nfadsr/Nresonance/Nfattack/Nfdecay/Nfsustain/Nfrelease/Nfenv/Oscparam` specs; `@Suppress`
on the `lang_arithmetic_addons.kt` vestigial params (`flipSign`/`oneMinusValue`/`not`/`abs`).

## Remaining

Walk the rest of `lang/` + `lang/addons/` file-by-file. For each file: run the IDE inspection, and for every
"Function `X` is never used" on a `PatternMapperFn.X` overload, apply the fix above. The new `voices()` alias
(2026-06-30) already ships with its form-(d) covered in `LangUnisonSpec` — use it as the template.

## Overall module review (fold in while sweeping)

Since we're touching every file anyway, also note/fix:

- **Naming consistency** — sprudel DSL functions are lowercase (`note`/`gain`/`spread`); the `snd*` camelCase
  family is the known exception, tracked separately in `docs/tasks/sprudel-sound-function-surface.md`.
- **KDoc drift** — params/examples that lag renames (e.g. the `detune→spread` rename; the `density()` doc no
  longer applies to `crackle`). The `2026-06-30` docs-sweep already fixed the music-writing skill refs.
- **Alias completeness** — ops that should have a short alias but don't (template: `unison`→`uni`/`voices`).
- **Control-pattern acceptance** — confirm each numeric param flows through `_applyControlFromParams` /
  `_innerJoin` so it accepts control patterns (the project rule "all params accept control patterns").

## Critical files

- `sprudel/src/commonMain/kotlin/lang/**` and `lang/addons/**` (definitions).
- `sprudel/src/commonTest/kotlin/lang/**` (specs + the `dslInterfaceTests` harness).
- Working memory: `sprudel_dsl_test_coverage.md`.
