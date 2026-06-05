# Mutable single-owner `SprudelVoiceData`

Last updated: 2026-06-05.

Status: **Phase 0 (golden safety net) done.** Phases 1–3 not started. Designed and
audited in the CycleTime-migration session; the CycleTime migration it depended on
is now **done** — `Rational` timing is fully replaced and
`CycleTime.T = 2²⁰·3·5·7 = 110 100 480` is locked in.

---

## Goal

Eliminate the per-event allocation/GC churn caused by immutable `SprudelVoiceData`
being rebuilt via `copy(field = …)` on every control application. After the
CycleTime migration killed the `Rational` BigInt cost, the remaining hot spots in
`queryArcContextual` for *Der Schmetterling* are:

- `new …SprudelVoiceData…` construction
- `copy` / `copy$default` of `SprudelVoiceData`
- the Minor GC that those allocations drive

Each played voice currently passes through ~1 allocation **per modifier** (gain,
note, filters, effects, …) — on the order of ~20 allocations per voice. The aim is
**one allocation per voice**, mutated in place down the modifier chain.

## The idea (user's proposal)

Make `SprudelVoiceData` fields **mutable** (`var` + a `clone()`), and mutate in
place instead of `copy()`-ing. This is sound **only** under a strict invariant:

> **No-aliasing invariant:** every query terminates at a leaf/atomic pattern that
> emits a **fresh** `SprudelVoiceData` instance, and no pattern ever passes the
> *same* `data` reference downstream to two consumers or back to a stored source.

If that holds, each event's `data` is single-owner and free to mutate. `SprudelPattern`
is `internal`, so exposing mutability is acceptable.

## Audit results (from the design session — re-verified 2026-06-05)

**Favorable. The invariant is achievable with a small, well-bounded correctness fix.**

### Aliasing — only 3 leaf emitters violate single-ownership

All three hand out a *stored / shared* `data` reference:

| Leaf pattern            | File                                 | Violation                                                                        |
|-------------------------|--------------------------------------|----------------------------------------------------------------------------------|
| `AtomicPattern`         | `pattern/AtomicPattern.kt:39`        | emits its stored `data` field directly in every `SprudelPatternEvent`            |
| `AtomicInfinitePattern` | `pattern/AtomicInfinitePattern.kt`   | same shared-`data` emission; also backs `ply`/`plyWith`/`plyForEach`             |
| `StaticSprudelPattern`  | `pattern/StaticSprudelPattern.kt:54` | `it.copy(part=…, whole=…)` keeps the **same** `data` ref as the stored recording |

Everything downstream is already safe **transitively**: all fan-out / joins /
time-shifts / `ply` / `echo` / `jux` either re-query (producing fresh leaf events)
or build fresh `data`. Fixing the 3 leaves to **clone `data` per emission** makes
the invariant hold engine-wide. (Fixing `AtomicInfinitePattern` covers the whole
`ply` family.)

### Mutation surface — one class, all flat

- `SprudelVoiceData` is a `data class` with ~120 **flat** fields
  (`SprudelVoiceData.kt:24`). Nested `FilterDefs` / `AdsrDef` are **not** stored —
  they're built lazily in `toVoiceData()`, which is **read-only**. So mutation
  touches **only `SprudelVoiceData`**; no nested-type cloning.
- The modifier funnel is concentrated: **114** `voiceModifier { copy(field = …) }`
  sites (e.g. `gainMutation = voiceModifier { copy(gain = it?.asDoubleOrNull()) }`,
  `lang_dynamics.kt:18`). `VoiceModifierFn = SprudelVoiceData.(Any?) -> SprudelVoiceData`
  (`lang/lang.kt:29`). They are invoked through one mapper in
  `_applyControlFromParams` (`SprudelPattern.kt:1147`):
  ```kotlin
  val mapper: (SprudelVoiceData) -> SprudelVoiceData = { data ->
      val value = data.value
      if (value != null) data.modify(value) else data
  }
  ```
- `merge()` (`SprudelVoiceData.kt:365`) and `mergeOscParamsFrom()` also allocate.
- `toVoiceData()` is read-only; no `Set`/`Map` uses `SprudelVoiceData` as a key;
  `@Serializable` is unaffected by `val → var`.

**Net:** the only true correctness work is cloning at 3 leaves. The rest is the
mechanical conversion of the 114 modifier sites + `merge()` from `copy()`/return-new
to mutate-`this`/return-`this`.

---

## Rollout strategy

The user's stated preference: **start narrow, not the full 114-site sweep.**

> "I would first only update property-setting functions like `gain()`. Things
> should be fine. But let us wait…"

Mutable and immutable modifiers **coexist safely**: a `copy()`-based modifier
returns a fresh instance (always safe, just no perf win); a mutate-in-place
modifier requires single-ownership, which the leaf clones guarantee. So we can
convert incrementally.

### Phase 0 — Safety net (DONE, 2026-06-05)

Differential golden test landed and green. Aliasing bugs are silent — one voice's
gain bleeding into the next event — so this guard is the prerequisite for any
in-place mutation.

**What was built**

- `sprudel/src/jvmTest/kotlin/golden/MutableVoiceDataGoldenSpec.kt` — compiles the
  corpus, queries each pattern **cycle-by-cycle** (the way playback does), and
  serializes every event's wire-format `toVoiceData()` (`VoiceData`, `@Serializable`)
  plus tick-exact timing (`whole`/`part` begin/end as integer ticks + `isOnset`).
  Output is compared byte-for-byte against a committed golden.
- `sprudel/src/jvmTest/kotlin/golden/GoldenCorpus.kt` — the corpus:
    - **`der-schmetterling`** — a **frozen copy** of the built-in song (24 cycles),
      seed pinned to a constant (`seed(42)` instead of the live `seed(timeOfDay…)`)
      so the random `|`-choices and `berlin`/`saw` noise are reproducible. Real-world
      complexity: `stack` + `superimpose` + `struct` + `scale`. **2822 events.**
    - **Targeted patterns** for the constructs the song lacks: `ply` (leaf #2,
      `AtomicInfinitePattern`), `echo`, `jux`, `superimpose`+`ply`, and a
      **`static-recording`** entry that wraps queried events in `StaticSprudelPattern`
      (leaf #3) and re-queries them.
- Golden file: `sprudel/src/jvmTest/resources/golden/voicedata_golden.txt`
  (3160 lines, ~2.3 MB). `Json { explicitNulls = false }` drops null fields —
  full fidelity on set values, far smaller/readable.

**Verified:** three independent JVM runs (one writing, two comparing with
`--rerun-tasks`) produce byte-identical output → deterministic. `oscParams` is an
insertion-ordered `LinkedHashMap`, so its serialization is stable.

**Run / regenerate**

```bash
# Run (note: FQN, NO quotes — quoted/wildcard filters report "No tests found"):
./gradlew :sprudel:jvmTest --tests io.peekandpoke.klang.sprudel.golden.MutableVoiceDataGoldenSpec
# Regenerate after an INTENTIONAL change: delete the golden and run (it rewrites,
# fails once asking for a verify run), or: UPDATE_GOLDEN=true ./gradlew … (env var,
# since -D system props are not forwarded to the test JVM by this build).
```

**Resolved decisions:** goldens captured & committed **now on this branch**
(option A of open-Q #1 — the corpus is self-contained and seed-pinned, no baseline
pin needed). Corpus = **pinned song + targeted patterns** (per user).

### Phase 1 — Enable mutability + close the 3 leaks

1. `SprudelVoiceData`: `val → var` on all fields; add `fun clone(): SprudelVoiceData`
   (an explicit shallow copy — keep `copy()` available during transition).
2. Clone `data` per emission in `AtomicPattern`, `AtomicInfinitePattern`,
   `StaticSprudelPattern`. **This is the actual correctness fix** and is required
   before *any* in-place mutation is safe.
3. Golden test stays green (no modifier converted yet — pure no-op + clone).

### Phase 2 — Convert the hot path (narrow, per user's preference)

- Convert the highest-traffic `voiceModifier { copy(…) }` setters (gain, note,
  velocity, the per-voice filter/adsr setters) to mutate `this` and return `this`.
- Re-run golden test after each batch.
- Measure *Der Schmetterling* in the browser perf tab — confirm
  `SprudelVoiceData` construction / `copy$default` / Minor GC drop.

### Phase 3 — Finish the long tail (optional / later)

- Convert remaining modifier sites + `merge()` to in-place.
- Once all 114 are converted and the leaf clones cover ownership, remove the
  transitional `copy()` reliance.

---

## Open questions (decide before / during Phase 0–1)

1. ~~**Golden capture baseline**~~ — RESOLVED: captured & committed on this branch
   (seed-pinned, self-contained corpus; no baseline-commit pin needed).
2. **Scope commitment** — hot-path-only (leaves codebase in a sound mixed state)
   vs full 114-site sweep in one pass. User leans hot-path-first. *(Decide at Phase 2.)*
3. **`clone()` cost** — cloning per leaf event partly offsets the win; confirm the
   net is still strongly positive (it should be: 1 clone at the leaf vs ~20 copies
   down the chain).
4. **`merge()` direction** — `merge()` mutating `this` has a left/right-bias
   pitfall; decide which operand owns the result.
5. **`SprudelPatternEvent` wrapper** — leave immutable (only `data` mutates) — most
   likely yes; revisit only if event-wrapper churn shows up in profiles.

## Key files

- `sprudel/src/commonMain/kotlin/SprudelVoiceData.kt` — the class (`:24`),
  `merge()` (`:365`), `mergeOscParamsFrom()` (`:1063`)
- `sprudel/src/commonMain/kotlin/pattern/AtomicPattern.kt:39` — leaf #1
- `sprudel/src/commonMain/kotlin/pattern/AtomicInfinitePattern.kt` — leaf #2 (+ `ply` family)
- `sprudel/src/commonMain/kotlin/pattern/StaticSprudelPattern.kt:54` — leaf #3
- `sprudel/src/commonMain/kotlin/SprudelPattern.kt:1137` — `_applyControlFromParams` / `_applyControl` mapper
- `sprudel/src/commonMain/kotlin/lang/lang.kt:29` — `VoiceModifierFn` typealias
- `sprudel/src/commonMain/kotlin/lang/lang_*.kt` — the 114 `voiceModifier { copy(…) }` sites
- New: `sprudel/src/commonTest/kotlin/…` — differential golden spec (Phase 0)

## Verification

- `./gradlew :sprudel:jvmTest` after each phase (golden + existing suites);
  `:sprudel:jsTest` for JS-specific behaviour.
- Browser perf-tab A/B on *Der Schmetterling*: next-cycle query under 16 ms;
  `SprudelVoiceData` alloc / `copy$default` / Minor GC reduced.
