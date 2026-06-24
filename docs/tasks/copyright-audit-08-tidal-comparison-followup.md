# Copyright Audit 08 — Tidal Cycles (GPL) comparison follow-up

**Bucket: unknown (not yet audited) · 🟢 later · audit task**

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
