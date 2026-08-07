# Audio backend audit — verified nets, then verified code

> **Planned 2026-08-04, not started.** Priority: **SHOULD**, rising to MUST before any launch —
> `audio_be` is the foundation everything else stands on ("sound first"). Multi-session campaign;
> designed to survive interruption. **Entry point: the `voices/` pilot (§5), then re-decide.**

## 1. Why

Everything in `audio_be` was built with **single review runs**, and every test in it was written **without a mutation
check**. Both assumptions were disproved in one week of work on the Master DSL:

- That feature needed **5 review rounds, and rounds 1–4 each found real defects in the previous round's fixes** — a
  multi-MB allocation inside the render callback, an engine leak per stop, a NaN that killed all audio until reload, a
  cached chain replaying a previous section's reverb tail, and a tail bound that counted uptime instead of silence (so
  every song >20 s lost its master tail). None of these were visible from a clean read or a green suite.
- Three guards proved **toothless** when finally mutation-checked: a "normal values untouched"
  transparency test that fed a 128-sample burst into combs 1116 samples long (both branches trivially identity), a
  parity spec that read the `Voice.Reverb` holder instead of the DSP one step further on, and merge helpers that no test
  exercised at all.

The conclusion is uncomfortable and specific: **a green `audio_be` suite is currently an unmeasured net.** We do not
know which of its 943 tests would fail if the thing they name were broken.

## 2. Why this is NOT automated

Investigated and rejected **with evidence**, not preference. Disassembling all 251 compiled classes in
`audio_be/build/classes/kotlin/jvm/main` found **zero bytecode invokes** to `ClippingFuncs.*` or
`PhaserCore.step`. The entire DSP hot path is inlined — 71 `inline` sites, plus 19 `@PublishedApi`
constants that exist *solely* to serve inlining. `DistortionRenderer` holds 32 references to
`ClippingFuncs` and every one is a vestigial `getstatic INSTANCE`; the arithmetic is inlined in place.

So a bytecode mutator (pitest is the only realistic candidate) would:

1. mutate `ClippingFuncs.fastTanh` and friends — **dead bytecode nothing calls** — and report a wall of surviving "no
   coverage" mutants on exactly the code we care about most (`ClippingFuncsBoundsSpec`, 44 tests, would score 0% on its
   own subject);
2. attribute the mutations that *do* execute to the **caller** class at SMAP-shifted line numbers that match no real
   line in the caller's source.

There is also no plugin path (pure KMP, no `java` plugin ⇒ no `sourceSets`, so pitest would have to be driven by CLI),
and Kotest 6 is a third-party JUnit-Platform `TestEngine` — the least-exercised path in `pitest-junit5-plugin`.

**User decision (2026-08-04):** *"I would rather not automate it… you would be moving the problem one level higher — who
says the automation is correct then for all specs? This is an audio engine. Sound first. We might automate mutation
tests in other modules, but not in the audio backend. This is THE foundation of everything."*

**Budget is not the constraint anyway:** the suite is **943 tests in 6.9 s**, so mutate → red → restore is a fast loop.
75% of that runtime is one diagnostic spec (`GuitarClickHuntTest`, 5.4 s) — always narrow with `--tests`.

## 3. Scope

**IN**

- `audio_be/src/commonMain` — 95 files, 14 361 lines
- `audio_be/src/jsMain` + `src/jvmMain` — the backend pumps
- `audio_jsworklet` — 180 lines, **this is the JS audio thread**, zero tests

**OUT** — `audio_bridge`, `audio_fe`, `sprudel`, `klang`. Cross-surface parameter parity is already owned by [
`master-dsl-followups.md`](master-dsl-followups.md) §1.

**Delete on sight:** `audio_be/src/wasmJsMain/AudioProcessingWasm.kt` — 59 lines of self-declared *"pseudo-code logic"*
(`return 0`, `return true`, module-level `var phase`), wired to nothing and not even compiled (the `wasmJs` block in
`audio_be/build.gradle.kts:21-27` is commented out).

## 4. Method

### 4.1 Per-subsystem loop (interleaved, not two separate passes)

No audit judgement should ever rest on an unverified net, so per subsystem:

1. **Verify the net** — mutation-check every spec (§4.2).
2. **Map the holes** — mutate production behaviours *no* spec claims; anything still green is recorded as unguarded.
   This half finds missing coverage rather than weak tests.
3. **Audit** — run `/review-loop` on the subsystem: fresh coding + audio reviewers each round, loop until a clean round,
   a wall, or a user decision.
4. **Fix + guard** — every fix gets a mutation-checked test; every rejection names the rule it invokes.
5. **Ledger entry** (§4.3), then move on. Tree green and committable at each boundary.

### 4.2 Mutation protocol (per spec)

`/review-loop` Standard 2, applied retroactively:

- Read the spec and write down **each behaviour it claims to guard** — the claim, not the assertion.
- For each claim: mutate the **production code** (flip an operator, off-by-one a constant, drop a term, swap a branch),
  run *only* that spec, confirm **RED**; restore, confirm **GREEN**, and verify with
  `git diff` that nothing is left behind.
  ```
  ./gradlew :audio_be:jvmTest --tests io.peekandpoke.klang.audio_be.<pkg>.<Class>
  ```
  UNQUOTED FQCN, no wildcards — quoted/wildcard filters match nothing here.
- Verdict per claim:
    - **LOAD-BEARING** — fails for the stated reason.
    - **TOOTHLESS** — passes with the behaviour broken. Strengthen (preferred) or delete if the behaviour genuinely is
      not worth guarding.
    - **MISATTRIBUTED** — fails, but for a different reason than its name claims. Two of these appeared this session;
      they are the most dangerous kind because they look like coverage.
- Never leave a mutation behind. Never silently drop a finding.

> ⚠ **Filename ≠ class name.** After a Cylinder→Orbit rename, `CylinderCleanupTest.kt` declares
> `OrbitCleanupTest`, `IgnitorsTest.kt` declares `ExcitersTest`, `CylinderKatalystPipelineSpec.kt`
> declares `OrbitBusPipelineSpec`, and 5 more. Take FQCNs from the compiled classes or the JUnit XML
> under `audio_be/build/test-results/jvmTest/`, never from filenames.

### 4.3 Ledger

`docs/audio-audit/` — an `INDEX.md` plus one file per subsystem, following the established
`console/` + `docs/benchmarks/` convention (long-running job → timestamped markdown in `docs/`).

Per spec:

| Spec (FQCN)            | Claimed behaviour                                 | Mutation used                   | Verdict      | Action |
|------------------------|---------------------------------------------------|---------------------------------|--------------|--------|
| `…EnvelopeDeclickSpec` | gain slew stays < 0.1 across an ADSR segment join | set `ENV_DECLICK_SECONDS = 0.0` | LOAD-BEARING | —      |

Plus, per subsystem: **unguarded behaviours** found in step 2; **audit findings** with disposition (fixed / rejected +
the rule named / user-decision); and **what is now guarded that was not before**.

The ledger is the deliverable that outlives the campaign. It is what makes "our tests are worth something" checkable
next time — and it survives another project pause.

## 5. Pilot — `voices/` (start here)

24 production files / 2 518 lines · 13 specs + 2 support files. Chosen because it is simultaneously the
**highest-churn** code in the module and the **biggest hole**, so it calibrates cost on the worst case rather than the
easiest.

### 5.1 Specs to verify (13, ~140 tests)

All in package `io.peekandpoke.klang.audio_be.voices`; filename = class name for all of these.

| Spec                          | Lines | Tests |
|-------------------------------|------:|------:|
| `PitchModulationTest`         |   597 |    21 |
| `VoiceLifecycleTest`          |   359 |    18 |
| `SampleVoiceSpecificTest`     |   458 |    16 |
| `FmSynthesisTest`             |   456 |    15 |
| `VoicePipelineTest`           |   211 |    15 |
| `EnvelopeTest`                |   388 |    13 |
| `SynthVoiceTest`              |   254 |    12 |
| `FilterModulationTest`        |   642 |    10 |
| `EnvelopeShapeTest`           |   112 |    10 |
| `VoiceCompressorSpec`         |    45 |     4 |
| `EnvelopeDeclickSpec`         |    96 |     2 |
| `VoiceFactoryFilterOrderSpec` |   105 |     2 |
| `SampleVoiceRenderTest`       |    74 |     2 |

Support (not specs): `VoiceTestHelpers.kt` (409), `strip/filter/BlockRendererTestSupport.kt` (51).

Note `EnvelopeTest` (388 lines / 13 tests) and `FilterModulationTest` (642 / 10) have the lowest assertions-per-line in
the set — start there, big specs with few tests are where padding hides.

### 5.2 Known holes — verify, then fill

- **`VoiceScheduler.kt` (407 lines) has NO spec at all.** Its old `VoiceSchedulerDiagnosticsTest` was *deleted* when
  diagnostics moved to the dispatcher and nothing replaced it. It is also the highest-churn file in the module (56
  commits in the last 200 touching `audio_be`).
- **`strip/send/SendRenderer.kt` — zero test references.** Every voice routes through it (gain / pan / postGain into the
  orbit mix, plus the reverb/delay send writes).
- **solo/mute and cut/choke — zero tests anywhere.** Grepping the whole test tree for `solo` and
  `choke` returns nothing, yet `VoiceScheduler.process` computes solo gain and runs a `soloMuteRamp`, and
  `promoteScheduled` implements cut-group hard-kill.
- Zero direct coverage: `strip/filter/TremoloRenderer`, `strip/filter/StripPhaserRenderer`,
  `strip/pitch/{FmRenderer, AccelerateRenderer, PitchEnvelopeRenderer, VibratoRenderer}`,
  `strip/BlockContext`, `PlaybackCtx`. (The pitch ones are covered end-to-end by
  `PitchModulationTest`/`FmSynthesisTest` — step 2 decides whether that is real coverage.)

### 5.3 Known risks — audit targets

- `promoteScheduled` runs **on the audio thread** and calls `voiceFactory.makeVoice(...)`, which builds the entire
  ignitor graph, filter chain and renderer lists — plus `head.copy(...)` and
  `ActiveVoice(...)`. The largest allocation site in the render callback.
- `VoiceScheduler.process` uses swap-with-last + `removeLast()` **while iterating**; an off-by-one here silently skips a
  voice's block.
- `SoloSourceTracker.update` allocates (`mutableListOf<Pair>`, `state.copy`) and mutates during iteration with
  `iterator.remove()`; `dedupAgainstActive` allocates a `BooleanArray` + `.filter{}`
  per call — reachable from `replaceVoices`, which is on the audio thread on JS.
- `Voice.kt:81` — `private val pipeline = pipeline + SendRenderer(voice = this)`: a list concat **and**
  a `this` leak in the constructor.
- `VoiceScheduler.kt:388` — `// TODO: Use a fade out / release phase instead of hard cut?` A known, untested click
  source.
- `strip/BlockContext.kt:28` documents *"The shared buffers are not thread-safe."*

### 5.4 Pilot exit criteria

Real minutes-per-spec, a proven ledger format, and a decision on the remaining subsystems backed by numbers instead of
the 15–25 h estimate. Explicitly OK to stop, narrow, or change the protocol here.

## 6. Risk order after the pilot

1. **Host / lifecycle root files** — `PlaybackEngine` (no spec), `PlaybackEngineDispatcher` (allocates a `Diagnostics` +
   N `CylinderState` per ~20 ms *inside* the callback, then writes across the thread boundary), **`WarmupRunner` (130
   lines, zero tests)**, **`BackendClock` (no spec)** — the single source of truth for the timeline, introduced to fix
   the first-note-loss class — and `MasterStage`
   (**a 42-line spec for the final limiter + DC blockers + clip that every sample passes through**).
2. **`cylinders/` + `katalyst/`** — **`KatalystFilterSwap.kt` (102 lines, zero tests)** is a *click-free crossfade*,
   i.e. the exact thing whose failure mode is an audible click;
   `Cylinders.processAndMix` makes three full 256-entry map traversals per block; `getOrInit` allocates a whole
   `Cylinder` on the audio thread; `VoiceLease` first-writer-wins semantics are subtle (compressor/ducking envelopes
   survive across notes only while consecutive owners keep the effect).
3. **`effects/`** — `Reverb.kt` (384 lines, one 82-line stability spec: the thinnest coverage-to-complexity ratio in the
   module), **`PhaserCore.kt` + `Phaser.kt` (zero direct coverage)**, `DelayLine`, `Compressor`.
4. **`filters/`** — `LowPassHighPassFilters.kt` is the best-covered file in the module (931-line spec), so mostly a
   verification pass; but **`FormantFilter` and `FilterHumanizationCoeffs` have zero test references**, and
   `DcBlocker` — on the final master output — is never named in a test.
5. **`ignitor/`** — biggest (5 699 lines) but best covered (26 specs, ~6.3k lines). The real gap is *attribution*:
   `IgnitorEffects.kt` (736) and `IgnitorFilters.kt` (676) have **no eponymous spec**, so which of ~50 operators are
   exercised is unknown. Also `ScratchBuffers.acquire` allocates on the audio thread when the pool is exhausted, and
   `release()` is unguarded (`nextFree--` can go negative).
6. **Platform / audio thread** — `audio_jsworklet/KlangAudioWorklet.kt`, `JsAudioBackend`,
   `JvmAudioBackend`. **Zero tests.** Note the JS command drain happens on the audio thread via
   `port.onmessage`, so `dispatcher.handle(cmd)` → `makeVoice` executes there.
7. **`master/`** — light pass only; just went through 5 review rounds.

`engines/` is 1:1 covered and 114 lines — skip unless something points at it.

## 6b. Error class to sweep for: **unbounded `Int` counters** (found 2026-08-06)

A whole-module scan target, added because the limiter work turned one up and it would have taken the audio thread down.

**The shape.** A counter that increments per sample or per block, is never reset, and is used in an arithmetic
comparison. At 48 kHz an `Int` overflows in **12.4 hours** (13.5 h at 44.1 kHz) — and critically, of *uptime*, not of
playback: `MasterStage.process` runs on every block even with zero engines, so a browser tab left open overnight is
enough.

**Why it is nasty rather than merely wrong.** Overflow does not throw. It silently inverts a comparison. The instance
found in `Compressor.lookaheadStep` was a deque eviction guarded by
`minIdx[minHead] <= sampleCounter - (delayFrames + 1)`; past the wrap the right-hand side becomes a large *positive*
number, so the condition is true for every stored index and the loop **never terminates — inside the render callback**.
Audio stops dead, with no exception and no log.

**The fix pattern** — compare by difference, which is wrap-safe in two's complement whenever the true difference is
bounded, and never widen to `Long` (house rule: no boxed/64-bit types in audio paths):

```kotlin
//  BAD: breaks at the wrap
while (index <= counter - window)
//  GOOD: wrap-safe, plus an emptiness guard
while (head != tail && counter - index > window - 1)
```

**Candidates found by the first scan** (`grep` for `var …(Counter|Frame|Index|Blocks|Elapsed) = 0`). Ring positions that
wrap by modulo (`delayPos`, `writePos`, `boxAPos`) are **safe** — they are bounded by construction. The ones to check
are those that grow without bound:

| Site                                                                            | Counter                                             | Notes                                                                                                                                                                                                                                                                             |
|---------------------------------------------------------------------------------|-----------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **`BackendClock.kt:40`**                                                        | **`cursorFrame: Int`**                              | ⚠️ **Highest priority.** The engine's entire timeline. Advances per block for the life of the backend and is threaded into `scheduler.process(cursorFrame)`, voice `startFrame`/`endFrame`/`gateEndFrame` comparisons, and `secAt()`. If this wraps, scheduling inverts globally. |
| `cylinders/katalyst/VoiceLease.kt:34`                                           | `lastSeenFrame`                                     | Compared against a frame counter — inherits whatever `cursorFrame` does.                                                                                                                                                                                                          |
| `ignitor/IgniteContext.kt:36`                                                   | `voiceElapsedFrames`                                | Bounded by voice lifetime, so safe *unless* a drone voice runs 12 h.                                                                                                                                                                                                              |
| `master/MasterBus.kt:117` · `PlaybackEngine.kt:35` · `cylinders/Cylinder.kt:98` | `silentBlocks` / `quietBlocks` / `silentBlockCount` | Reset when sound returns; check the never-audible path.                                                                                                                                                                                                                           |
| `voices/Voice.kt:263`                                                           | `idCounter`                                         | 2³¹ voices ≈ months at realistic densities. Low risk, still unbounded.                                                                                                                                                                                                            |
| `filters/LowPassHighPassFilters.kt:484`                                         | `transitionSamples`                                 | Confirm it is bounded by the transition.                                                                                                                                                                                                                                          |

### `cursorFrame` — CONFIRMED BY TEST 2026-08-07, and worse than the existing comment claims

`audio_jsworklet/.../KlangAudioWorklet.kt:60-65` already documents this overflow, calls 12.4 h *"sufficient for any
continuous session"*, and states the symptom as *"silent audio stop (no crash)"*. Both the premise and the scope are
wrong.

**Measured** by driving `PlaybackEngineDispatcher.renderBlock` at two cursor positions with an otherwise identical
scheduled voice:

| cursor               | blocks with audio |
|----------------------|-------------------|
| 0                    | **55 / 60**       |
| near `Int.MAX_VALUE` | **0 / 60**        |

Two things that changes:

1. **The failure starts BEFORE the wrap, not at it.** Scheduling goes through *seconds*
   (`startTime = frame / sampleRate`) and converting back to frames overflows while `cursorFrame` is still positive — so
   the timeline degrades on approach rather than flipping at the boundary.
2. **"Sufficient for any continuous session" is the wrong test.** This is a browser tab in a live-coding tool. 12.4 h at
   48 kHz (13.5 h at 44.1 kHz) is one long working day; left open overnight it silently stops, with no error and no
   crash to explain it.

**Same exposure on the JVM path** — `JvmAudioBackend.kt:53` has the identical `var currentFrame = 0`.

**Fix options — (d) CHOSEN (user, 2026-08-07):**

- **(a) Rebase periodically** — past a threshold, subtract it from the cursor *and* from every stored frame on
  scheduled/active voices. Keeps `Int`, bounded work, runs about twice a day. The classic solution; the risk is missing
  a stored frame and producing a large timing glitch.
- **(b) Reset when idle** — the existing comment's own suggestion, and cheapest, but only correct when nothing is
  scheduled or still ringing.
- **(c) `Double` instead of `Int`** — exact integers to 2^53 ≈ 5900 years at 48 kHz. On Kotlin/JS
  `Int` is *already* a JS number with truncation on every operation, so this may even be faster there; but it touches ~
  126 sites and changes hot-loop arithmetic on the JVM too.
- **(d) `Long`, as a documented exception — CHOSEN (user, 2026-08-07).** See the rule below.

### The rule: `Long` is allowed for absolute counters OUTSIDE hot loops, with a comment saying why

The house rule "no `Long`/boxed types in audio paths" exists because `Long` is emulated on Kotlin/JS and allocates on
every operation. That reasoning applies to **per-sample** arithmetic. It does not apply to a value touched **once per
block**.

`cursorFrame` is the case that shows the distinction, and the split already exists in the code —
`EnvelopeRenderer`'s KDoc states it outright: *"All per-sample arithmetic uses Int to avoid Long boxing on Kotlin/JS.
Voice-relative offsets are computed once at the block boundary."*

|                                                                                               | type       | frequency      |
|-----------------------------------------------------------------------------------------------|------------|----------------|
| **absolute** timeline (`cursorFrame`, `blockStart`, `startFrame`, `endFrame`, `gateEndFrame`) | **`Long`** | once per block |
| **relative** position (`absPos`, `offset`, `length`, ring indices)                            | **`Int`**  | per sample     |

The conversion happens at one line per renderer, e.g. `EnvelopeRenderer.kt:66`
`var absPos = (ctx.blockStart + ctx.offset) - startFrame` — a single `Long` subtraction per block per voice, feeding 128
`Int` operations. Scoped: **43 fields become `Long`, 13 conversion points gain an explicit `.toInt()`, and 56 per-sample
sites are untouched.**

**Every such `Long` must carry a comment stating (a) that it is not in a hot loop and (b) why the width is needed** —
otherwise the next person applying the house rule mechanically will "fix" it back to `Int` and silently reintroduce a
12-hour time bomb.

**How to verify each**: this class is invisible to normal tests — nobody runs a spec for 12 h. Test it by seeding the
counter near `Int.MAX_VALUE` and stepping across the boundary, which needs the field to be settable or the arithmetic
extracted. Where that is impractical, prove wrap-safety by inspection and record the argument.

## 7. Constraints — put these in EVERY review prompt

The module carries a large body of **deliberate** decisions. An auditor who "fixes" one of these makes things worse.
Full list: `audio/MEMORY.md` + `docs/tasks-archive/`.

- **Raw Motör** — no defensive checks in the inner math, no safety clamps on user-facing params; defend at integration
  points (`ClippingFunctions.kt:14-23`).
- **Reverb's `+ ANTI_DENORMAL` is a deliberate exception** to the engine-wide `flushDenormal()`
  convention; the consistent version cost ~+11%/sample and was reverted 2026-05-19.
- **The SVF is purely linear by design.** Two saturation attempts failed and were reverted; the
  `analog`/`bpFb` infrastructure is kept for a future re-introduction. Warmth comes from upstream.
- **`roomSize`/`roomFade` clamped to 0..1 is NOT a taste clamp** — past unity the comb network has no steady state. The
  soft-cap alternative was measured (DC rail, AC-RMS 0.0) and reverted 2026-08-03.
- **Accepted/intentional, each documented:** triangle aliasing (no PolyBLEP), hard clip at
  `IgniteRenderer`, unconditional DC-block on distort, cylinder last-writer-wins,
  `Ducking.attackSeconds`'s misleading name, `rectify()`'s hard clip, body-filter fixed (non-note-tracking) resonances,
  `crackle`'s sound change, body/vowel at orbit level.
- **`Ignitor` is deliberately NOT a `fun interface`** — SAM turns captured `var`s into JS `ObjectRef`.
- **`PhaserCore.step` must stay `inline`**, its state `internal` (~25% JVM / ~60% JS otherwise).
- **`DelayLine`: no per-sample `isFinite`** — cost ~+33% JVM / +30% JS, removed 2026-05-22.
- **`Reverb.hasTail()` is not for per-block use** (~28k samples); `MasterBus` throttles it on purpose.
- **`KatalystBodyEffect` / `KatalystFormantEffect` are intentional un-deduped twins** — change one, mirror the other.
- **Numerical contract** — `SAFE_MIN 1e-15` / `SAFE_MAX 1e15` (matches SuperCollider `zapgremlins`);
  "safe" means finite, **not** small: consumers must be O (1) regardless of magnitude.
- **Rejected optimisations, do not retry:** `fastCopy` (~22× slower on JS), ProtoBuf for the wire format,
  snapshot-into-locals as a perf rule, the two SuperSaw *phase* fixes (dead ends, reverted by ear — the shipped fix is
  on the gain axis).

## 8. Verification & gotchas

- `./gradlew :audio_be:jvmTest` green at every subsystem boundary; full `jvmTest` + the JS suites before anything is
  committed.
- **Never run two Gradle invocations concurrently** — corrupts the sprudel KSP cache; recover with
  `:sprudel:clean`. Partial cleans also produced stale-class / IR-lowering errors twice on 2026-08-03; the fix is the
  same.
- **Anything touching a hot path gets a before/after benchmark.** Both harnesses exist:
  `console/run-dsp-benchmarks.sh` (per-effect RTF, JVM + Node + comparison matrix) and
  `./gradlew runSongBenchmark` (real songs through the real graph; medianRtf + peakBlockRtf). Baselines not to
  re-derive: browser ≈ **2.7×** JVM; Der Schmetterling is ~98% three super-synth voices; cost ranking `superimpose` ≫
  `body` ≈ `analog` > multi-band filters > distort-oversample >
  `unison` > `pipeline` (~free).
- **Anything touching sound gets a by-ear check** before it is called done — the part no test covers.
  `GuitarClickHuntTest` is the standing click-regression harness; house rule is to *add* setups to it whenever a new
  click symptom appears.

## 9. Links

- Method: [`.claude/skills/review-loop/SKILL.md`](../../.claude/skills/review-loop/SKILL.md)
- Worked example of the loop paying off:
  [`../tasks-archive/2026-08/20260803-master-dsl.md`](../tasks-archive/2026-08/20260803-master-dsl.md)
- Overlapping open work: [`master-dsl-followups.md`](master-dsl-followups.md) (§2 shared orbit+master tail hole), [
  `resource-warehouse-pool.md`](resource-warehouse-pool.md) (audio-thread allocation),
  [`audio-pipeline-open-topics.md`](audio-pipeline-open-topics.md), [`voice-culling.md`](voice-culling.md)
- Orientation for a fresh reviewer — signal flow + file-by-file map:
  [`../audio-backend-file-map.md`](../audio-backend-file-map.md)
- Engine knowledge: `audio/MEMORY.md`, `audio/ref/performance.md`, `audio/ref/numerical-safety.md`
- Memory: `feedback_review_loop`, `feedback_parameter_parity`, `feedback_raw_motor`,
  `feedback_kotest_test_filter`, `project_gradle_no_concurrent_builds`
