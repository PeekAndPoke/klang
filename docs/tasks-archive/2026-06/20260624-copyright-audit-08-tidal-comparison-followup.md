# Copyright Audit 08 — Tidal Cycles (GPL) comparison follow-up

**Bucket A (audited — clean) · ✅ done · audit task**

> **Status: ✅ DONE (2026-06-24).** Compared `sprudel` against Tidal Cycles (Haskell, GPL-3.0) at
> `/opt/dev/Tidal/tidal-core/src/Sound/Tidal/`. Two parallel agents + a manual spot-check of `Params.hs`.
>
> **Result: sprudel is independent of Tidal's protected expression — no Bucket B/C.**
> - **Pattern engine** (`Pattern.hs`/`Core.hs`/`UI.hs`): independent. Tidal = record-of-a-function + typeclass
    > mountain + `Rational` time + Parsec; sprudel = `interface` + ~35 named subclasses + `CycleTime` + hand-rolled
    > parser. Same paradigm/behavior/public combinator names (Bucket A), zero transliterated structure.
> - **Bjorklund** (`Bjorklund.hs`): Tidal is the *canonical Rohan Drape* recursive `STEP`/`left`/`right` version;
    > Klang's `bjorklund.kt` is an independent iterative grouping loop (1/0 ints, clamping, negative-inversion).
    > Reinforces task 02 — the algorithm traces to Drape (published), not to Tidal's expression.
> - **Mini-notation parser** (`ParseBP.hs`): Tidal = Parsec + `TPat` GADT; sprudel = manual tokenizer + recursive
    > descent + `MnNode` sealed AST. Only the (unprotectable) syntax is shared.
>
> **Control-vocabulary prior-art (the valuable part — feeds task 07):** a LARGE core of the vocabulary
> originates in **Tidal `Params.hs`** (Copyright 2021 Alex McLean+contributors), predating Strudel — verified by
> direct grep of line numbers:
> - Base names: `cutoff`, `resonance`, `hcutoff`, `hresonance`, `bandf`, `bandq`, `gain`, `pan`, `velocity`,
    > `note`, `n`, `sound`, `bank`, `begin`, `end`, `speed`, `coarse`, `crush`, `cut`, `distort`, `accelerate`,
    > `legato`, `sustain`, `attack`, `decay`, `release`, `room`, `size`, `delay`, `delaytime`, `delayfeedback`,
    > `orbit`, `vowel`, `shape`.
> - **Short aliases that one might assume came from Strudel but are DEFINED IN TIDAL:** `lpf = cutoff` (4034),
    > `hpf = hcutoff` (4160), `bpf = bandf` (4271), `ctf = cutoff` (4244), `lpq = resonance` (4025),
    > `hpq = hresonance` (4151), `sz = size` (3863), `s = sound` (3938), `delayfb = delayfeedback` (4226),
    > `delayt = delaytime` (4217), `sus = sustain` (3872), `clip = legato` (1719).
> - **The `:`-compound concept is Tidal's** (`grp` + `wordsBy (== ':')`, lines 33-39); `sound = grp [mS "s", mF "n"]`
    > (142) → `s:n` ordering is straight from Tidal; `grain' = grp [begin, end]` (166) is colon-packing precedent.
>
> **Optional hygiene noted (not done):** the "Equivalent to … in Tidal" doc cross-references are unprotectable
> name-references; could be neutralized for optics. A one-line Bjorklund/Drape credit in `bjorklund.kt` would
> document the independent reimplementation of a published method (defensive, not required).

## Context

The audit so far compared sprudel against **Strudel** (AGPL) only, because that's the codebase available
locally at `/opt/dev/strudel` and the one sprudel actually worked *from* ("started as a copycat of Strudel").
**Tidal Cycles** (the Haskell ancestor, **GPL-3.0**) has not been directly compared. Risk is lower — sprudel
descends from Strudel, not Tidal, and a Haskell→Kotlin path makes literal copying unlikely — but completeness
before a commercial launch warrants a spot-check.

## Plan

1. Obtain the Tidal source (`https://github.com/tidalcycles/Tidal`, GPL-3.0) read-only for comparison.
2. Spot-check the areas most likely to share lineage **through** Strudel:
    - the core pattern/`Hap`/`Arc` query model (Tidal `Sound.Tidal.Pattern`),
    - the combinator names/semantics (`every`, `jux`, `chunk`, `struct`, `euclid`, `degrade`),
    - the euclidean/Bjorklund code (Tidal's is the Rohan Drape lineage — relevant to task 02),
    - the control/parameter vocabulary (Tidal/SuperDirt is the *origin* of much of it — relevant to task 07).
3. Apply the same legal filter (expression vs idea). Expectation: Bucket A throughout (different language,
   different paradigm expression), with the control vocabulary tracing to Tidal/SuperDirt as **prior art that
   helps** the task-07 defense rather than adding risk.
4. File remediation tasks only if any literal expression surfaces.

## Done when

Tidal has been spot-checked against the high-lineage areas, with findings recorded here (expected: confirms
independence + reinforces the task-07 prior-art defense).
