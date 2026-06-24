# Copyright Audit 01 — Rewrite `EuclideanMorphPattern.calculateMorphedArcs`

**Bucket B (high confidence) · 🔴 must-fix before non-AGPL relicensing · code change**

## Context

This is the single clearest piece of copied Strudel *expression* in the pattern engine. The morph helper is a
direct transliteration of Strudel's `_morph`, down to internal variable names, the helper decomposition, and
**comments that verbatim-quote the Strudel JS source**.

- **Ours:** `sprudel/src/commonMain/kotlin/pattern/EuclideanMorphPattern.kt` — `calculateMorphedArcs`
  (~lines 73–112), incl. the local `getPositions` helper.
- **Theirs (AGPL):** `/opt/dev/strudel/packages/core/pattern.mjs` — `_morph` (~lines 3523–3543).

## Evidence

| Strudel `_morph` (pattern.mjs)                                         | Sprudel `calculateMorphedArcs`                            |
|------------------------------------------------------------------------|-----------------------------------------------------------|
| `const dur = Fraction(1).div(from.length)`                             | `val dur = 1.0 / steps`                                   |
| `positions = (list) => …push([Fraction(pos).div(list.length), value])` | `fun getPositions(list)` pushing `index.toDouble() / len` |
| `const b = by.mul(posb - posa).add(posa); const e = b.add(dur);`       | `val b = by * (posB - posA) + posA; val e = b + dur`      |

Plus the Kotlin contains verbatim JS quotes in comments: `// zipWith logic from JS`,
`// const b = by.mul(posb - posa).add(posa);`, `// const e = b.add(dur);`, `// to: Array(pulses).fill(1)`.

The morph *formula* alone is arguably a math fact (Bucket A), but the matching internal names (`b`, `e`,
`dur`), the `positions`/`getPositions` helper, and the source-quoting comments together make this copied
expression.

## Plan

1. Re-derive the euclidean-morph interpolation **from the behavior** (linear interpolation between the onset
   positions of the two euclidean patterns), expressed in idiomatic Kotlin.
2. Rename the internal locals (`b`, `e`, `dur`, `getPositions`) to Klang's own naming.
3. **Delete every comment that quotes or paraphrases the JS source.** Replace with a short description of the
   algorithm in our own words if helpful.
4. Keep the public behavior identical (this is interpolation math — output must be unchanged).

## Verification

- Run the EuclideanMorph spec(s) under `sprudel/src/commonTest` — behavior/output must be byte-identical.
- Run the full sprudel `commonTest` suite (no regressions).
-
`grep -rn "from JS\|by.mul\|Array(pulses)\|zipWith logic" sprudel/src/commonMain/kotlin/pattern/EuclideanMorphPattern.kt`
→ must return nothing.

## Done when

`calculateMorphedArcs` is independently expressed with Klang-native naming, carries no JS-derived comments,
and the morph specs still pass.
