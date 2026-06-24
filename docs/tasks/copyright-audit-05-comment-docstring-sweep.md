# Copyright Audit 05 — Provenance comment & doc-string sweep

**Bucket C (low) · 🟡 should-fix · hygiene, no structural code change**

## Context

Across the engine, the author documented Strudel's JS formula in comments and then implemented it. Where the
Kotlin is independently structured (most cases), these comments are *not* infringement — but they are direct
evidence the code was written *from* the AGPL source, which is exactly the narrative a plaintiff would use.
Removing/softening them is cheap and strengthens the independent-expression posture. **No behavior changes.**

This task is comment/doc-only. (The comments that sit on genuinely-copied code are handled by tasks 01–03; this
task covers the rest.)

## Items

1. **Soften "Equivalent to X in JS Strudel" KDoc** in `sprudel/src/commonMain/kotlin/SprudelPattern.kt`
   (e.g. ~L658 `_fmap`, ~L679 `_squeezeJoin`, ~L724 `_innerJoin`, ~L786) → reword to "matches standard
   cyclic-pattern / Tidal semantics" (the code is independent; only the wording reads as derivation).
2. **Strip "(matches JS behavior)" / "JS Strudel semantics" provenance comments** (code is Bucket A
   forced-behavior):
    - `pattern/PickResetPattern.kt` (~L69), `pattern/PickRestartPattern.kt` (~L66)
    - `pattern/StructurePattern.kt` (~L113–119, esp. the `// JS behavior` on `whole = maskEvent.whole`)
3. **Remove verbatim `// JavaScript:` / `// JS:` formula comments** on functions whose Kotlin is independent:
   `pressBy`, `loopAt`, `run`, `iter`, `binaryN`, `brak` (in `lang/lang_structural.kt`, `lang/lang_sample.kt`,
   `lang/lang_tempo.kt` — grep to locate exact lines).
4. **Reword the copied `berlin` doc-string** in `sprudel/src/commonMain/kotlin/lang/lang_continuous.kt` (~L985):
   the line "Conceived by James Coyne and Jade Rowland as a joke but turned out to be surprisingly useful" is
   near-verbatim from Strudel's `berlin` doc — paraphrase it (or attribute it explicitly if kept).
5. **Test-only references are fine to keep but tidy:** `JsCompatTestData.kt`, `*Spec.kt` mention "Strudel" to
   describe differential-test intent — those are legitimate (they document why the oracle exists). Leave, or
   reword for consistency.

## Verification

- `grep -rniE "in JS Strudel|matches JS behavior|JS Strudel semantics|JavaScript:|JS logic" sprudel/src/commonMain`
  → only intentional, reworded references remain.
- Full sprudel test suite still green (comments only — must be a no-op behaviorally).
- A diff that touches **only comments/KDoc** (no statements).

## Done when

Provenance comments in shipped (`commonMain`) code are removed or reworded to describe behavior in Klang's own
voice, with no behavioral change.
