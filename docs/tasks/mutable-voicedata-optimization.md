# Mutable single-owner `SprudelVoiceData`

Last updated: 2026-06-05.

Status: **Phases 0–2 done** (golden net + enablement + all setters converted).
Phase 3 (combiners / `merge()`) not started. The CycleTime migration this depended
on is also done — `Rational` timing fully replaced, `CycleTime.T = 2²⁰·3·5·7 =
110 100 480` locked in.

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

### Phase 1 — Enable mutability + close the 3 leaks (DONE, 2026-06-05)

1. `SprudelVoiceData`: 105 constructor fields flipped `val → var`; added
   `fun clone(): SprudelVoiceData = copy()`.
2. Clone `data` per emission in `AtomicPattern`, `AtomicInfinitePattern`,
   `StaticSprudelPattern` — the actual correctness fix.
3. Golden stayed green (pure no-op + clone). Only fallout: 3 sprudel **test** files
   relied on smart-casting the now-`var` fields (`data.value`/`data.gain`/
   `data.soundIndex`) — fixed with local captures (`shouldNotBeNull()` return,
   `val x = …`). The 66 main-app consumers compiled unchanged.

### Phase 2 — Convert the setters (DONE, 2026-06-05)

Converted **all 113** `voiceModifier { copy(…) }` setters to in-place mutation
(the user asked for the full sweep, not just the hot path). Mechanism:

- New helper `voiceSetter { … }` (`lang_helpers.kt`) — runs a
  `SprudelVoiceData.(Any?) -> Unit` body that mutates the receiver and returns `this`.
- In-place oscParam helpers on `SprudelVoiceData`: `putOscParam` /`putOscParams`
  (counterparts of `withOscParam`/`withOscParams`).
- Each `copy(field = v)` → `field = v`; `return@voiceModifier this/copy(…)` →
  `return@voiceSetter`; the `snd_*` `copy(sound=…).withOscParams(…)` →
  `sound = …; putOscParams(…)`.

**Critical aliasing fix found by the golden** (do NOT revert): `toListOfPatterns`
built atoms via `SprudelVoiceData.empty.modify(text)` — `empty` is a **shared
singleton**, so an in-place modifier corrupted it and leaked into every later
pattern (a guitar's `adsrCurves` Square bled into the drums). First fixed by cloning
(`empty.clone().modify(text)…`), then **hardened by removing the shared singleton
entirely** (below).

**`SprudelVoiceData.empty` removed (hardening, 2026-06-06):** the shared mutable
singleton was the root footgun, so it's gone. All 105 constructor params now default
to `null`, so a fresh empty is just `SprudelVoiceData()` and the common
`empty.copy(value = x)` collapses to single-alloc `SprudelVoiceData(value = x)`. All
~146 `SprudelVoiceData.empty` sites (commonMain + commonTest) rewritten; the line-318
`.clone()` became redundant and was dropped. The class KDoc now documents the
**`var`-by-design / caller-must-clone** contract. `voiceValueModifier` stays
copy-based (still fine — it now runs on a fresh `SprudelVoiceData()`, not a shared one).

**Verified:** golden green; **full `:sprudel:jvmTest` green** (incl. Graal JS-compat +
all lang specs); main app compiles JVM+JS. *(Note: never run a second Gradle build
concurrently with `:sprudel:jvmTest` — it clobbers `:sprudel` class outputs and
throws spurious `NoClassDefFoundError`s.)*

### Phase 3 — Cut the copy/clone hot path (IN PROGRESS)

Corrected profile showed the remaining query-path cost is dominated by **`clone()` ≈ 25%**
(the per-emission leaf clones, `clone() = copy()` routing through `copy$default`) plus the combiner
`src.copy(...)` calls — not the pattern logic. Plan: `docs` / `~/.claude/plans/gleaming-inventing-gosling.md`.

**Part 1 — faster `clone()` (TRIED, REVERTED).** Hand-wrote `clone()` as an explicit 105-field
constructor call to bypass `copy$default`. Profiling showed **no improvement** (slightly worse — the
105-arg constructor call is as costly as `copy$default`). Reverted to `clone() = copy()`. Kept the
`SprudelVoiceDataSpec` "clone copies every field" guard test (now also guards via `populatedVoiceData`).
Takeaway: `clone()`/`copy()` is at the floor for a 105-field object; the only way to reduce it is
**fewer clones** (deferred fast-path) or a **faster native copy** (see fastCopy note below).

**Hardening — no-default constructor + `createSprudelVoiceData()` factory (DONE, 2026-06-06).**
Per user: removed the `= null` defaults from all 105 constructor params, and added a **top-level**
`createSprudelVoiceData(<all params = null>)` factory (top-level, not a companion member, to avoid the
`getCompanion()` runtime tax on JS). Rationale: with no constructor defaults, the only full-field
constructor calls — `clone()`, `merge()`, and the factory body — **fail to compile** if a field is ever
added, so a field can never be silently dropped from `clone()`. All ~149 partial construction sites
(commonMain + commonTest + jvmMain/graal) were renamed `SprudelVoiceData(…)` → `createSprudelVoiceData(…)`
(simple rename; factory has matching default params) and given the factory import. Verified: golden green,
full `:sprudel:jvmTest` green, app compiles JVM+JS.

**Part 2 — in-place combiners (DONE, 2026-06-06).** Converted all 35 `_applyControlFromParams { src, ctrl ->
src.copy(field = ctrl.field ?: src.field) }` to mutate `src` in place + return it
(`lang_filters`/`lang_effects`/`lang_dynamics`/`lang_engine`/`lang_tonal` + `effects_addons`/`filters_addons`).
`n`/`chord` were already in-place; `_liftNumericField` already in-place; event-timing `copy(part/whole)`
left alone.

**Part 3 — in-place `merge()` (DONE, 2026-06-06).** Added `mergeFrom(other)` (in-place, mirrors `merge()`,
preserves `patternId`) + `putOscParamsFrom`. Converted the 2 `merge()` sites (`SprudelPattern.kt` `_liftData`,
`MergeVoiceDataPattern.kt`) and the 10 `snd_*` combiners (`src.copy(sound=…).mergeOscParamsFrom` →
`src.sound = …; src.putOscParamsFrom(ctrl); src`). `merge()` kept as the oracle for a new
`mergeFrom`-vs-`merge` guard test in `SprudelVoiceDataSpec` (mergeFrom isn't covered by the golden corpus).

**Verified:** golden green after every file, full `:sprudel:jvmTest` green, app compiles JVM+JS.

Then: user re-profiles *Der Schmetterling* (expect `copy$default`/`copy` + Minor GC to drop from the
combiner/merge side; `clone()` leaf cost unchanged — see fastCopy/fast-path below).

**Native `fastCopy` for `clone()` on JS (DONE, 2026-06-06).** `clone()` now delegates to an
`internal expect fun fastCopy(src): SprudelVoiceData` — JVM actual = `src.copy()`, JS actual =
`js("Object.assign(Object.create(Object.getPrototypeOf(src)), src)")` (one native own-property copy,
prototype preserved → methods / `is` / `equals` intact; shallow, exactly like `copy()`, shares the
`oscParams` map ref). Files: `sprudel/src/commonMain/kotlin/fastCopy.kt` (expect) +
`fastCopy_jvm.kt` / `fastCopy_js.kt` (actuals). **The caveat is verified**: the commonTest
`SprudelVoiceDataSpec` "clone() copies every field" guard (all 105 fields set distinctly →
`clone() shouldBe populated`) passes on `:sprudel:jsTest`, proving Kotlin/JS stores the `var` fields as
own enumerable props and `Object.assign` reproduces them. `:sprudel:jvmTest` (golden + full suite) green;
root app `:compileKotlinJs` green. **Pending: user browser re-profile of *Der Schmetterling*** to confirm
the `clone`/`copy$default` self-time actually drops (the test only proves correctness, not the win).

**Other deferred follow-ups (for the remaining `clone()`/`new` cost):**

- **Constant-control fast-path to cut clone COUNT.** Skip building+sampling+cloning a control atom for
  constant scalar args (`gain(0.5)`, `lpf(1625)`); apply directly. Bigger/riskier change to the lift/control
  helpers.
- **Not worth it:** a `blueprint.clone().apply{}` config-factory — profiling showed `createSprudelVoiceData`
  is only ~0.8% / 0.5 ms, and it doesn't touch `clone()`.

---

## Open questions (decide before / during Phase 0–1)

1. ~~**Golden capture baseline**~~ — RESOLVED: captured & committed on this branch
   (seed-pinned, self-contained corpus; no baseline-commit pin needed).
2. ~~**Scope commitment**~~ — RESOLVED: user chose the **full sweep**; all 113
   setters converted in one pass.
3. **`clone()` cost** — cloning per leaf event partly offsets the win; confirm the
   net is still strongly positive (1 clone at the leaf vs ~20 copies down the chain).
   *(Pending the Phase-3 browser perf measurement.)*
4. **`merge()` direction** — `merge()` mutating `this` has a left/right-bias
   pitfall; decide which operand owns the result. *(Phase 3.)*
5. **`SprudelPatternEvent` wrapper** — left immutable (only `data` mutates); revisit
   only if event-wrapper churn shows up in profiles.

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

---

# Checkpoint & backlog (2026-06-06)

This section supersedes the older "deferred follow-ups / open questions" text above. Live working copy:
`~/.claude/plans/gleaming-inventing-gosling.md`.

## Done & verified green (golden + full `:sprudel:jvmTest` + app JVM/JS compile)

- Phases 0–2: golden net; `var` fields + `clone()`; 3 leaf emitters clone on emission; all 113
  `voiceModifier{copy}` setters → in-place `voiceSetter`; shared-`empty` aliasing leak fixed then `empty`
  **removed** → top-level inline `createSprudelVoiceData{…}` factory (clones a `@PublishedApi internal blueprint`)
    + **no-default constructor** (compile-forces field completeness in clone/merge/blueprint).
- Parts 2/3: all 35 `_applyControlFromParams` combiners → in-place; `merge()` → in-place `mergeFrom()` +
  `putOscParamsFrom()` (2 merge sites + 10 `snd_*` combiners). Guard tests: clone-all-fields + mergeFrom-vs-merge
  in `SprudelVoiceDataSpec` (`populatedVoiceData`).

## Rejected (do not retry)

- Hand-written explicit `clone()`: no faster than `copy()`. Reverted to `clone() = copy()`.
- **`fastCopy`** (`Object.assign(Object.create(getPrototypeOf(src)),src)`): **benchmarked ~22× SLOWER on JS**
  (820 vs 18,390 ns/op). V8 optimizes the constructor-based `copy()`; Object.create/assign → slow dictionary-mode
  object. Removed; KDoc warns against it. Microbenchmark kept at
  `audio_benchmark/src/commonMain/kotlin/VoiceDataCopyBenchmark.kt` (`:audio_benchmark:jsNodeProductionRun`/`:jvmRun`).
  ⇒ `copy()` (~820 ns/op JS) is the floor for a 105-field object. Remaining wins = FEWER copies or SMALLER object.

## Pending decision

- **blueprint + inline config-factory** (`createSprudelVoiceData { … }`) — implemented, behavior-preserving but
  perf-neutral. Cost a 119-site rewrite; bulk perl pass left cosmetic misplaced `}` in ~25 test files (compiles +
  passes). KEEP (clean test formatting) vs REVERT (simpler param-factory). Likely moot if Item B lands.

## Backlog

### Item A — Constant-control fast-path (small, low-risk; do first)

Constant args (`gain(0.5)`, `lpf(1625)`, `distort("0.3:tube:4")`) currently build a control pattern and
`sampleAt`+`clone()` it **per source event** — ~15–25 redundant control-atom clones per event on heavy voices.
Detect via a polymorphic `SprudelPattern.constantValueOrNull(): SprudelVoiceData? = null` (override only in
`AtomicPattern` → its `data`; exclude nested-`Pattern`-valued atoms) — **no `is` checks, no string heuristics**.
In the lift/control helpers (`_liftNumericField`, `_liftOrReinterpretNumericalField`, `_liftStringField`,
`_applyControlFromParams`, `_liftData`), if `control.constantValueOrNull()` ≠ null → apply directly via
`reinterpret`/`reinterpretVoice` (one clone+map total, mutate source in place) instead of per-event sampling.
Subtleties: reuse mapped control (combiner contract: mutate `src`, read `ctrl`); replicate
`prependLocations(ctrl.sourceLocations)` via event-level `reinterpret` (live-highlighting; `@Transient` so golden
won't catch it). Golden-guarded. ~1 interface method + 1 `AtomicPattern` override + ~5 helper sites.

### Item B — Group `SprudelVoiceData` fields into sub-objects (big structural lever)

Group the ~105 flat fields into ~12 cohesive sub-objects. With **immutable groups + copy-on-write**, `clone()`
copies ~12 shared group refs (not 105 fields) and a setter copy-on-writes one small group — cuts both `clone()`
and per-modifier cost; `toVoiceData()` flattens less.

- **Reuse** `audio_bridge` types: `AdsrDef.Std` (adsr), `FilterDefs`/`FilterDef.*`/`FilterEnvDef` (filters+env).
- **New group types** (not yet grouped in `VoiceData`): FM, phaser (`phaserRate`↔`phaser` mismatch), tremolo,
  ducking, delay, reverb, sample, pitch-envelope. Define once, share with `VoiceData`.
- **Blast radius ~515 sites, ALL inside sprudel** (setters, merge/mergeFrom, `toVoiceData`, `GraalSprudelPattern`,
  tests); **zero external** (app/audio see only `VoiceData`; `SprudelVoiceData` `@Serializable` but never
  persisted — songs store code). Golden file changes shape (regenerate).
- **Design fork:** immutable-groups + copy-on-write (recommended; *supersedes* the var/in-place model from
  Phases 1–2 — re-evaluate) vs mutable group objects. Consider flat delegating accessors to shrink churn.
- Large, phased: define types → restructure → migrate setters/merge/toVoiceData/Graal → regen golden → tests →
  re-profile. Highest-leverage remaining item.
