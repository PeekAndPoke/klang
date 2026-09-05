# Unify the filter-frequency parameter name across all DSLs

> ## ✅ DONE — shipped 2026-08-25, commit `5332d52d`
>
> Ran as its own round, as the Method section prescribes. `freq` is now the name on every DSL
> surface and wire type; the `Hz`-suffix sub-decision below was closed in favour of the
> recommendation (dropped). The engine's EQ layer (`EqIgnitor.Section`, `EqCore.configureSection`)
> was renamed too, beyond the scope drawn below, because keeping it produced
> `freqHz = readParam(s.freqHz, freqHz, ctx)` — one name, two concepts, one line. The engine's DSP
> primitives (`createLPF`/`createHPF`, the `Svf*` constructors, `Tunable.setCutoff`) deliberately
> keep `cutoffHz`: there the parameter genuinely is a cutoff. Known follow-up, NOT done: the
> band-shaped engine filters still document `@param cutoffHz Center frequency`, which is the same
> semantic wrongness one layer down.
>
> The `band`/`eq` collision noted below was resolved on the SPRUDEL side instead (the ignitor keeps
> `.eq()` and `.band()`); sprudel's bitwise family is being renamed to
> `bitAnd`/`bitOr`/`bitXor`/`bitShl`/`bitShr` in its own round.
>
> Everything below is the original proposal, kept as the record of what was decided and why.

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

## Decisions settled 2026-08-25 (maintainer, at the start of the rename round)

- **The name is `freq`, no `Hz` suffix.** (The sub-decision below is closed in favour of the
  recommendation.)
- **`Hz` suffix: dropped.**
- **The `band`/`eq` collision below is fixed on the SPRUDEL side, not the ignitor side.** The
  ignitor KEEPS `.eq()` and `.band()` — both are concise, and dispatch is by receiver type.
  Instead sprudel's bitwise family is renamed, which is better naming on its own merits
  (`band` for bitwise-AND is strudel heritage): `band`→`bitAnd`, `bor`→`bitOr`,
  `bxor`→`bitXor`, `blshift`→`bitShl`, `brshift`→`bitShr` (shift names follow Kotlin's
  `shl`/`shr` per the Kotlin-style stdlib rule). 5 functions x 4 surface forms = 20
  declarations. **Its own commit**, separate from the `freq` rename.
- **`DerSchmetterling.kt` gets its OWN commit** — 4 named-arg sites, in a file that carries the
  maintainer's uncommitted by-ear tuning.
- **Survey refresh 2026-08-25:** sprudel's filter door ALREADY says `freq` (`lpf(freq, q,
  passes)`, `bpf`, `notchf`) — no work there. In scope: 130 `cutoffHz =` named-arg call sites,
  and the 29 `freqHz` occurrences in `audio_bridge` (the EqSection variants). The 379 `freqHz`
  occurrences in `audio_be` are the note-pitch meaning and stay.
- **Method:** rename the DECLARATIONS first and let the compiler find every call site. Do not
  run a mechanical find-and-replace on `freqHz` — that is exactly the pitch trap.

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
