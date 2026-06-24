# Copyright Audit 03 — Reimplement the `degrade` family

**Bucket B/C (medium confidence) · 🔴 should-fix before non-AGPL relicensing · code change**

> **Status: ✅ DONE (2026-06-24).** Implemented a dedicated `pattern/DegradePattern.kt` (per the
> engine's class-per-combinator convention, like `StructurePattern`). `applyDegradeByWith` /
> `applyUndegradeByWith` now build `DegradePattern(src, withPat, threshold, keepStrictlyAbove)` via
> the existing `_lift` instead of `src.appLeft(withPat.filterValues { v -> v > x })`; the
> `appLeft`/`filterValues` imports and the verbatim JS comment were removed. Behavior preserved
> exactly (the class inlines the appLeft sample-over-whole + clip + threshold, folding in
> `filterValues`). `sometimesBy`/`someCyclesBy` were already independent (`when`+`lt`), so no change
> needed there. Verified: LangDegradeBy/DegradeByWith/UndegradeBy/UndegradeByWith/SometimesBy/SomeCyclesBy
> specs pass. Code-review (independent agent, traced event-by-event vs `appLeft`): APPROVE.

## Context

`degradeByWith` reproduces Strudel's specific applicative construction (`fmap(const).appLeft(filterValues(v >
x))`) rather than the more obvious "filter events by a per-event random". The doc-comment quotes the JS
verbatim. Because the whole degrade family is built on this one helper, fixing the base clears the rest.

- **Ours:** `sprudel/src/commonMain/kotlin/lang/lang_random.kt` — `applyDegradeByWith` (~L446).
  Cascades to: `degradeBy` (~L345), `undegradeBy` (~L517), `undegradeByWith` (~L579), `someCyclesBy` (~L1041).
- **Theirs (AGPL):** `/opt/dev/strudel/packages/core/signal.mjs` — `degradeByWith` (~L674):
  `pat.fmap((a) => (_) => a).appLeft(withPat.filterValues((v) => v > x))`.

## Evidence

Sprudel mirrors the idiom and names: lift each event to a constant function, then `appLeft` against
`withPat.filterValues { v -> v > x }` — same local name `withPat`, same `appLeft` + `filterValues(v > x)`
order, with a comment that quotes the JS.

Note: `appLeft` itself is a generic Tidal/Strudel combinator (Bucket A). What pushes this to B/C is using *this
particular* construction to implement degrade, plus the JS-quoting comment. (Confidence is only medium because
in this FRP model the construction is close to forced.)

## Plan

1. Reimplement degrade by **filtering events directly** against a seeded per-event random, e.g.
   `pattern.filterEvents { event -> perEventRandom(event) >= x }` (using Klang's existing seeded-RNG /
   `QueryContext` mechanism — the same one already used elsewhere in the engine, which is itself independent
   of Strudel's RNG).
2. Express `undegradeBy` by keeping the complementary set (Klang already inverts the threshold rather than the
   RNG source — keep that independent approach).
3. Confirm `degradeBy`, `undegradeBy`, `someCyclesBy` (which delegate to the base) now route through the new
   implementation; remove the verbatim JS-quoting comment(s).

## Verification

- Run the degrade/undegrade/someCycles specs in `sprudel/src/commonTest` — **note:** the *exact* events kept
  may shift if the RNG mapping changes. Decide intentionally:
    - if specs assert specific kept-event sets, update them to match the (independent) RNG and document why, or
    - keep the per-event random mapping equivalent so output is unchanged.
- `grep -n "fmap.*appLeft\|filterValues.*> x\|JavaScript:" sprudel/src/commonMain/kotlin/lang/lang_random.kt`
  → no JS-quoting comment remains.

## Done when

The degrade family is implemented via direct seeded-random event filtering (no `fmap-const + appLeft +
filterValues` transliteration), the JS comment is gone, and the random specs pass (updated deliberately if the
RNG mapping intentionally changed).
