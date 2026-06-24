# Copyright Audit — Strudel/Tidal derivation cleanup (overview & tracker)

## Why this exists

Klang is currently **AGPL v3** — the same license as Strudel — so **none of the items below are an
infringement problem today**. They matter for exactly one future goal: **relicensing parts of the engine
under a commercial / proprietary license** (dual-licensing).

As the copyright holder (Karsten J. Gerber — see `AUTHORS.MD`), Karsten can dual-license **his own
expression** freely. He **cannot** relicense any **Strudel (AGPL) / Tidal (GPL)** expression that survives in
the code. This audit locates that surviving expression so it can be rewritten or removed *before* any non-AGPL
move.

## Scope & method

- Compared `sprudel` (Kotlin) against the local Strudel checkout (`/opt/dev/strudel`, AGPL): a first cut
  (data tables / comments / git archaeology) **plus** a deep Layer-4 SSO pass (engine combinators, DSL
  function bodies) **plus** `audio_be`/`audio_bridge` vs Strudel `superdough`/`webaudio`.
- **Legal filter** applied to every finding: copyright protects **expression**, not ideas, public names, or
  standard math (idea–expression dichotomy; the EU also exempts a language + its functionality, *SAS v. WPL*).
    - **Bucket A** = unprotectable (paradigm, public DSL names, standard math, behavior forced by the op). Fine.
    - **Bucket B** = copied expression (transliterated structure, identical internal names, copied comments). Fix.
    - **Bucket C** = ambiguous → lawyer.
- **NOT yet done:** direct comparison against **Tidal Cycles** (GPL, Haskell) — source not available locally.
  See task 08. sprudel worked *from Strudel*, not Tidal, so this is lower-risk completeness work.
- ⚠️ These are **candidate findings for counsel, not a legal verdict.**

## Headline result

The engine architecture, the mini-notation parser, RNG/signals, the time-transform combinators, and the
**entire audio DSP engine** are **independent reimplementations (Bucket A).** Real exposure is narrow and
concentrated in: a few euclidean-rhythm helpers, the `degrade` family, "I-wrote-this-from-the-JS" provenance
comments, and the vendored Strudel JS bundle used as a JVM test oracle.

## Tasks (recommended execution order)

| #  | Task                                                                                                                                     | Bucket       | Priority  | Type             |
|----|------------------------------------------------------------------------------------------------------------------------------------------|--------------|-----------|------------------|
| 01 | [Rewrite `EuclideanMorphPattern.calculateMorphedArcs`](../tasks-archive/2026-06/20260624-copyright-audit-01-euclidean-morph-rewrite.md)  | B (high)     | ✅ done    | code             |
| 02 | [De-port Euclidean generation (Bjorklund + rotate)](../tasks-archive/2026-06/20260624-copyright-audit-02-euclidean-generation-deport.md) | B (high)     | ✅ done    | code             |
| 03 | [Reimplement the `degrade` family](../tasks-archive/2026-06/20260624-copyright-audit-03-degrade-reimplement.md)                          | B/C (med)    | ✅ done    | code             |
| 04 | [`strudel-bundle.mjs` — notice + release exclusion](copyright-audit-04-strudel-bundle-notice-exclusion.md)                               | B (verbatim) | 🟡 should | compliance/build |
| 05 | [Provenance comment & doc-string sweep](copyright-audit-05-comment-docstring-sweep.md)                                                   | C (low)      | 🟡 should | hygiene          |
| 06 | [Attribution touch-ups (chord-voicings, fast_tanh, PolyBLEP)](copyright-audit-06-attribution-touchups.md)                                | A            | 🟢 nice   | docs             |
| 07 | [Control-vocabulary legal review](copyright-audit-07-control-vocabulary-legal-review.md)                                                 | C            | 🟡 lawyer | decision         |
| 08 | [Tidal (GPL) comparison follow-up](copyright-audit-08-tidal-comparison-followup.md)                                                      | —            | 🟢 later  | audit            |

## What is CONFIRMED CLEAN (no task needed)

- **Mini-notation parser** (`lang/parser/*`) — hand-written recursive descent + own sealed-class AST; Strudel
  uses a generated PEG parser. Only the (unprotectable) syntax is shared.
- **Pattern engine core** — `CycleTime` fixed-point model vs Strudel `Fraction`; the combinator algebra in
  `SprudelPattern.kt` (`_fmap`/`_squeezeJoin`/`_innerJoin`/`_outerJoin`/`_bindReset`/`_bindRestart`/
  `_focusSpan`/`_splitQueries`/`appLeft`) is independently expressed (own names, no copied comments, none of
  Strudel's idiosyncratic tricks). ~30 of 35 `pattern/*.kt` files are Bucket A.
- **RNG / signals** — different model (seeded `kotlin.random.Random` per event); Strudel's magic constants
  (`536870912`, the `300`-cycle xorshift stretch, `randrun` `+0.5`) are **absent**.
- **Audio DSP engine** (`audio_be`, `audio_bridge`) — **fully independent of `superdough`.** Reverb = Freeverb
  (Jezar tunings), filters = Zavalishin/Cytomic TPT-SVF, PolyBLEP = Välimäki, waveshapers = textbook — all
  cited in `CREDITS.MD`. Only verbatim overlap is the public-domain `fast_tanh` Padé one-liner (→ task 06).
- **Music theory** — lives in `:tones` (the tonal.js MIT port, already relicensed MIT). Bucket A.

## Commercial-readiness gate

Before any code is shipped under a non-AGPL license:

- ✅ tasks **01, 02, 03** completed (copied expression rewritten),
- ✅ task **04** enforced (the Strudel bundle excluded from the proprietary artifact),
- ✅ task **07** — IP-lawyer sign-off on the control vocabulary.

Tasks 05, 06, 08 strengthen the record but are not strictly blocking.
