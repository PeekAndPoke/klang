# Master DSL — follow-ups

> Carried out of [`../tasks-archive/2026-08/20260803-master-dsl.md`](../tasks-archive/2026-08/20260803-master-dsl.md)
> when that shipped (2026-08-03). Nothing here blocks anything; each item is small and independent.
> Priority: **NICE**, except the parity audit, which the user raised to a standing principle.

## 1. Parameter parity audit — the principle, applied everywhere

**The rule (user, 2026-08-02):** *"The same params must be available in the sprudel DSL, Ignitor, Master etc. and they
need to mean the same thing. This is important for developer experience."*

The master DSL work proved this is not theoretical: `roomSize` silently meant a ~1 s tail on an orbit and ~12.5 s on the
master because sprudel divided by 10 and the master did not — and `roomFade`, the parameter that *actually* sets the
tail, existed on only one of them. Both are fixed for reverb/delay; nothing has audited the rest.

What an audit would cover, per effect: does the same concept have the same **name**, the same **scale**, the same
**availability** on every surface it could sensibly appear on (sprudel per-voice, sprudel per-orbit, Ignitor,
Pipeline/Stage, Master)?

Known asymmetries already spotted, as a starting list:

- **`room` (orbit) vs `wet` (master)** — same thing, two words. The master reuses `wet` across reverb and delay; sprudel
  inherits `room` from Strudel.
- **`roomsize` is ~0..10 but `roomfade` is 0..1**, and `roomfade` silently makes `roomsize` inert. Inherited from the
  `room("a:b:c")` packing; documented rather than fixed, because redefining it would retune shipped songs.
- **`damp` is master-only**; sprudel reaches damping through `roomlp` (Hz) instead.
- **`delaycap`/`dcap` has no slot** in the `delay("wet:time:feedback")` compound string, while the reverb's compound
  documents all five slots.
- **`roomDim` / `iResponse`** are stored but never read on **both** paths (`Reverb.kt` TODO) — dead vocabulary that
  still appears in the DSL and docs.

**A DELIBERATE exception, recorded so nobody "fixes" it:** `lookahead` exists on the master limiter **only** — not on
sprudel's `compressor()`, not per-orbit, not per-voice. A lookahead limiter delays the signal it protects; on the summed
master that delay is uniform and harmless, but on an orbit it would shift that orbit late against every other one — a
silent timing bug that reads as "my drums feel loose". Same reasoning one level up keeps the *authored*
`MasterFx.limiter()` at
`lookaheadSeconds = 0` while the house limiter runs at 5 ms: the house one is global and post-sum, the authored one is
per-playback. `MasterDefaultsSyncSpec` asserts both the shared values and the divergence. See
`master-limiter-lookahead.md` §3 and §4.

Wants its own task doc once someone starts it; this is the brief.

## 2. Engine disposal truncates a long delay tail (shared orbit + master)

`PlaybackEngine.isIdle()` bounds the master tail hold at 20 s of silence, then disposes the engine **between two
samples** — a step, not a fade. A genuine (non-runaway) delay, e.g.
`time(2.0).feedback(0.85)`, can still be ~14 dB down at that point, so it clicks.

The orbit path has the *opposite* half of the same hole: a delay at `feedback >= 1.0` keeps
`cylinders.anyActive()` true forever, so `hasOwnSound()` never clears and the engine is never disposed at all — one
leaked engine per stop, and the drone survives deleting the pattern line.

Both are the same question ("when is a tail finished, and how do we stop rendering it politely"), so a shared fix is
likely better than two. Options for the master half: fade the engine out over the last N blocks before disposal; raise
the bound; or accept the step. The new `delaycap` knob and its doc example (`delayfeedback(1.0).delaycap(2.0)`) actively
invite the orbit half.

## 3. Cache eviction can rebuild a master chain on the audio thread

Returning to a master last used more than `MAX_CACHED_CHAINS` (8) edits ago is a cache miss, and
`MasterBus.chainFor` rebuilds inside `process()` — Freeverb buffers plus a delay ring allocated in the render callback.
Bounded and documented, but reachable by ordinary live-coding A/B ("was 2.0 better?").

**Proper fix belongs to the resource warehouse pool** — already written up there, see
[`resource-warehouse-pool.md`](resource-warehouse-pool.md) §"Master chains — the second customer".

## 4. Delete-to-undo for `master(...)`

`master == null` means "no change", so deleting the line leaves the last chain in place until the playback stops.
**Decided 2026-08-02: accept it**, with `Master.default()` as the named, discoverable way back — it swaps to the empty
chain through the normal crossfade and releases the engine properly.

Recorded here only because the underlying tension stays: the FE cannot distinguish "the code no longer contains
`master(...)`" from "this query chunk happens to contain none", and a sectioned song legitimately has cycles without a
master event. Revisit only if it bites in practice.

## 5. `MasterFx.eq()` — a gentle master-bus EQ (new, 2026-08-11)

Wanted by [`auto-mix-advisor.md`](auto-mix-advisor.md) §3 (closed-loop master correction), and independently useful as
an authored mastering tool. Scope: **2–3 bands — low shelf, high shelf, optionally one mid bell** — deliberately not a
full parametric EQ.

- **Chain position (decided by mechanism, not taste):** before gain and both limiters — reverb → **eq** → gain →
  authored limiter → house limiter. The limiter's detector must see the corrected spectrum; that is what converts a
  low-shelf cut into loudness headroom (measured 2026-08-11 in the klang-ai sessions: −2 dB of low trim bought +1.3 dB
  RMS *and* reduced beat-rate pumping ~10 % at higher drive).
- **Parity (§1 applies):** shelf/bell EQ has no per-voice counterpart in sprudel today (`lpf`/`hpf`/`bandf` are filters,
  not gain shelves). If a per-orbit or per-voice shelf ever appears, names and units must match from day one — propose
  `eqLowDb/eqLowHz`, `eqHighDb/eqHighHz`, `eqMidDb/eqMidHz/eqMidQ` (dB gain + Hz corner, the industry-standard meaning)
  and record them in the parity table before shipping.
  **UPDATE 2026-08-20 — the per-voice counterpart NOW EXISTS**, so this is no longer hypothetical:
  the ignitor DSL ships `.eq()` plus `.band(freq, q, db)` (serial bell, dB gain, `q` = the ordinary
  width scale, 0 dB transparent) and `.tap(freq, q, gain)` (parallel boost, LINEAR gain). Whoever
  implements `MasterFx.eq()` must reconcile with those names and units rather than inventing a
  parallel vocabulary — in particular `db` means the same thing on both, and a master `q` should
  mean the same width it means on `.band()`. See also the pending `cutoffHz`/`freqHz`/`freq`
  unification in `docs/tasks/filter-frequency-param-naming.md`, which will touch these names.
- **Implementation:** stereo biquad shelves from the existing SVF/biquad infra; plain `var` params (no buffer sizing —
  unlike `lookaheadSeconds` there is no constructor-val constraint); wire model + KSP codec + `MasterDefaultsSyncSpec`
  -style assertion for defaults (all gains 0 dB = bit-transparent, so existing songs are untouched).

## 6. Small documentation / convention debts

- The `delaycap` KDoc example `delayfeedback(1.0).delaycap(2.0) // endless echo, held at 2.0` is misleading: at exactly
  1.0 the cap never engages (the echo holds at the *input* level); the cap only sets the level once feedback > 1.
- Several `dcap` overloads (top-level, `String.dcap`, both `PatternMapperFn` forms) lack
  `@category`/`@tags`/`@alias`, which `sprudel/ref/dsl-conventions.md` requires — so they will not surface in the
  effects category or in search.
- Freeverb's ~0.71 s tail floor (`FEEDBACK_OFFSET = 0.7`): nothing on either bus can go shorter. Worth stating in the
  user-facing docs, or revisiting the constant.

## Links

- Shipped plan + full review history: [
  `../tasks-archive/2026-08/20260803-master-dsl.md`](../tasks-archive/2026-08/20260803-master-dsl.md)
- Foundation: [`per-playback-engine.md`](per-playback-engine.md) §H
- Next in the same family: [`katalyst-dsl.md`](katalyst-dsl.md) — follows this application-path and effect-reuse
  precedent
- [`resource-warehouse-pool.md`](resource-warehouse-pool.md) — owns item 3
- [`auto-mix-advisor.md`](auto-mix-advisor.md) — wants item 5 (`MasterFx.eq`) for its closed-loop phase
