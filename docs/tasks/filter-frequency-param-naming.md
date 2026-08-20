# Unify the filter-frequency parameter name across all DSLs

**Decision (maintainer, 2026-08-20):** one concept, one word. A filter's operating frequency is
called **`freq`** on every DSL surface. Today the same concept wears three different names, and
two of them collide inside a single DSL.

**Why now:** the unified-EQ work added `EqSection.*` with `freqHz` next to the chained filter
nodes that say `cutoffHz`, so `IgnitorDsl` now describes the SAME lowpass two different ways
depending on whether it is fused. The Master DSL has no EQ yet, which makes this the last cheap
moment to fix the vocabulary before a third surface inherits the mess.

**Also wrong today, independent of consistency:** `cutoffHz` is semantically incorrect on
bandpass, notch and bell. Those have a CENTRE frequency, not a cutoff. `freq` is the only word
that is correct for lowpass, highpass, bandpass, notch, bell and (future) shelves alike.

## The three names in the tree (surveyed 2026-08-20)

| Name | Where | Concept |
|---|---|---|
| `cutoffHz` | `IgnitorDsl.Lowpass/Highpass/Bandpass/Notch/OnePole*`, `FilterDef.LowPass/HighPass/BandPass/Notch`, the KlangScript stdlib filter methods, the Kotlin fluent extensions | filter frequency |
| `freqHz` | ALL six `IgnitorDsl.EqSection` variants (Lowpass, Highpass, Bandpass, Notch, Bell, RawTap) | filter frequency (same concept, different word) |
| `freq` | ~17 oscillator nodes in `IgnitorDsl`, `FilterDef.Formant.Band`, `FilterDef.Body` bands, the new `.band(freq, ...)` surface | a node's own frequency |

Rough scale: 275 `cutoffHz` occurrences repo-wide; 84 of them are named-argument CALL sites,
which are the ones that break on a rename.

## Explicitly OUT of scope: the render argument `freqHz`

`Ignitor.generate(buffer, freqHz, ctx)`, `VoiceData.freqHz` and friends (167 declarations, ~900
uses) mean **the playing note's pitch**, not a filter setting. That is a genuinely different
concept and keeps its own name. Do not sweep it up in a mechanical find-and-replace; that is the
main way this task could go wrong.

## Scope

1. **Wire fields** in `audio_bridge`: `IgnitorDsl` chained filters (`cutoffHz` → `freq`), all
   `IgnitorDsl.EqSection` variants (`freqHz` → `freq`), `FilterDef` filters (`cutoffHz` → `freq`).
   The KSP wire codec is NAME-keyed, so a field rename is a wire-format change. Safe here: main
   and worklet always ship from the same build, and songs persist as source, not as wire. Confirm
   the schema-hash story in the same commit.
2. **KlangScript stdlib**: the filter methods' parameter names. Positional calls (`lpf(800)`,
   `.lowpass(2000)`) are unaffected; only named-argument calls change.
3. **Kotlin fluent extensions** in `audio_bridge` (dual-surface rule: both doors, same commit).
4. **Master DSL / Pipeline DSL / future Katalyst DSL**: adopt `freq` from the start. Master has
   no EQ yet, so this is free.
5. **Example code and teaching material** (maintainer-requested):
   - built-in songs that use named args, incl. `DerSchmetterling.kt` (`.notch(cutoffHz = ...)`,
     `.highpass(cutoffHz = ...)`) and `Sakura.kt`;
   - tutorials under `src/commonMain/kotlin/pages/docs/tutorials/`;
   - the lexikon entries;
   - `.claude/skills/klang-music-writing/ref/ignitor-reference.md`;
   - `docs/whitepaper/klang-whitepaper.html`.

## Keep the WORD "cutoff" in prose

`tut_Filters.kt` teaches "the cutoff is a dial, not a switch" and that phrasing is good musical
writing. This task renames a PARAMETER, not a concept. Prose that explains what a lowpass cutoff
does stays exactly as it is; only `cutoffHz = ` call sites and param names change.

## Related vocabulary collision, worth deciding in the same pass

`band` and `eq` now each name two unrelated concepts in one script namespace: sprudel ships
`band(mask)` (bitwise AND) and `eq(...)` (equality) in `lang_arithmetic.kt`, while the ignitor
DSL ships `.band(...)` (EQ band) and `.eq()` (open an equalizer). Both libraries are imported
into every song. Nothing breaks — dispatch is by receiver type — but it is the same "one word
per concept" debt this task exists to pay down, and the docs registry merges symbols by NAME, so
the shared entries can take each other's category. Decide deliberately now, before songs and
tutorials are written against the new surface.

## Open sub-decision: keep the `Hz` suffix or not?

Recommendation: **drop it** (`freq`, not `freqHz`). `Osc.freq()` already establishes `freq` as
the house word, everything frequency-shaped in this engine is in Hz, and the suffix is exactly
what fragmented the vocabulary in the first place. Counter-argument worth one minute of thought:
the suffix documents the unit at the call site, and `notch(cutoffHz = snareHz)` reads well.

## Method

Run it as its own deliverable, the way the `detune` → `spread` rename went (see memory
`osc-detune-spread-rename`), NOT folded into the unified-EQ work. Mechanical rename plus a
review round; the risk is not difficulty, it is the blast radius and the `freqHz`-means-pitch
trap above. Wire-codec round-trip specs and the ignitor parity specs are the safety net.

Related: [[dsl-kotlin-surface-parity]], the unified-EQ plan's D8 parity-table item (record the
ignitor `.band()` names against the Master `eqMidHz/eqMidQ/eqMidDb` proposal so the two agree on
day one).
