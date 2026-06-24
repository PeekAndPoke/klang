# Copyright Audit 02 — De-port Euclidean generation (Bjorklund + rotate)

**Bucket B (high confidence) · 🔴 must-fix before non-AGPL relicensing · code change**

> **Status: ✅ DONE (2026-06-24).** `bjorklund` rewritten as an independent iterative grouping loop
> (own naming: `primary`/`tail`/`fold`/`folded`/`leftover`); `recursiveBjorklund` deleted. In
> `EuclideanPattern.kt` the `jsSlice`/`rotateJs`/`bjorklundSprudel` helpers were replaced by one
> idiomatic `rotate(list, by)` (split-index + subList, preserving the original clamping), call sites
> use the canonical `bjorklund`, all Strudel/JS-citing comments removed, unused imports dropped.
> Verified: `EuclideanPatternSpec`, `LangEuclidRotSpec`, `EuclideanMorphPatternSpec`, `LangEuclidishSpec`
> pass. Code-review (independent agent) ran an exhaustive 625-case old-vs-new comparison — identical
> output — and confirmed `rotate` reproduces the old clamping exactly. Verdict: APPROVE.

## Context

The euclidean **rhythm generation** (Bjorklund) and the bitmap **rotation** helpers track Strudel's JS
implementation structurally, and the comments explicitly cite "the JS source" / "Strudel's rotate". Two
distinct but co-located transliterations, handled together because they live in the same area.

- **Ours:**
    - `common/src/commonMain/kotlin/math/bjorklund.kt` — `recursiveBjorklund` (~L41–66), `bjorklund` (~L14).
    - `sprudel/src/commonMain/kotlin/pattern/EuclideanPattern.kt` — duplicate `bjorklundSprudel` (~L254–277),
      `rotateJs` (~L249–252), `jsSlice` (~L229–247); call sites ~L145, ~L360; comments ~L141, ~L249.
- **Theirs (AGPL):** `/opt/dev/strudel/packages/core/euclid.mjs` — `_bjorklund`/`left`/`right`/`bjorklund`
  (~L17–52); `/opt/dev/strudel/packages/core/util.mjs` — `rotate` (~L153); `euclid.mjs` `_euclidRot` (~L130).

## Evidence

**Bjorklund** — same termination `Math.min(ons, offs) <= 1`, same branch predicate `ons > offs`, same nested
`take`/`drop` + `zip(concat)` decomposition, same recursion args; comment at `bjorklund.kt:~47`:
`// JS logic: Math.min(ons, offs) <= 1 ...`.

**Rotate** — Strudel `util.mjs:153`: `rotate = (arr, n) => arr.slice(n).concat(arr.slice(0, n))`; Sprudel
`rotateJs(list, n) = list.jsSlice(n) + list.jsSlice(0, n)` with comment `// Replicates Strudel's rotate
function: array.slice(n).concat(array.slice(0, n))`, plus `jsSlice` reimplements JS `Array.prototype.slice`
"exactly" and `EuclideanPattern.kt:141` `// Strudel JS rotates by shifting right for positive numbers.`

## Important mitigation (why this is fixable, not fatal)

The **algorithm** is Bjorklund's published 2005 algorithm, and Strudel's own `euclid.mjs` header credits it as
"ported from the Haskell Music Theory module by Rohan Drape" — i.e. the structure traces to a third party, not
to original Strudel authorship. So we only need to replace the *specific copied expression*, not invent a new
algorithm.

## Plan

1. **Bjorklund:** reimplement euclidean onset generation from the standard *imperative* description — e.g. the
   Bresenham form `onset(i) = floor(i * pulses / steps)`, or a bucket-distribution loop — **not** the
   `xs`/`ys` nested-list + `zip(concat)` recursion. Drop the `// JS logic:` comments.
2. **Rotate:** delete `rotateJs` and `jsSlice`; rotate the bitmap with idiomatic Kotlin, e.g. with normalized
   `r = ((-rotation) % steps + steps) % steps`, build `List(steps) { bitmap[(it + r) % steps] }`. Drop the
   "Replicates Strudel's rotate" / "Strudel JS rotates" comments.
3. **De-duplicate:** there are two Bjorklund copies (`common/.../bjorklund.kt` and `bjorklundSprudel` in
   `EuclideanPattern.kt`). Collapse to one canonical implementation and import it (see `/code-style` rule 3).

## Verification

- Run `LangEuclidRotSpec`, `EuclideanPatternSpec`, and any euclid/legato specs — output must be identical
  (rotation + generation are deterministic; the bit patterns must not change).
- Use the kotest `--tests` + unquoted FQCN convention to run single specs.
- `grep -rniE "JS logic|Replicates Strudel|Strudel JS rotates|Array.prototype.slice" common sprudel/src/commonMain`
  → must return nothing.

## Done when

Euclidean generation + rotation are independently expressed (one canonical Bjorklund, idiomatic Kotlin
rotation), no JS-citing comments remain, and all euclid specs pass unchanged.
