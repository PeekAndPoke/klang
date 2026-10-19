# Performance Rules for Audio DSP

Reference for keeping the audio hot path fast on **both** JVM and Kotlin/JS.
Real-time audio means **per-block deadlines** (e.g. 128 frames at 48 kHz =
~2.67 ms per block). A single 5 ms GC pause or a few extra property loads per
sample turn into audible artifacts. The JS path is the binding constraint —
everything that's painful on JS is at most uncomfortable on JVM, but the
reverse is not true.

## The two rules

These cover ~95% of what we hit in practice. Apply both — rule 1 without
rule 2 is half a fix.

### Rule 1 — Stateful Ignitors are dedicated classes, not SAM lambdas

**Why:** in Kotlin/JS, a `var` declared in an outer scope and mutated inside a
captured lambda compiles to an `ObjectRef` — essentially `{ v: someValue }`.
Every read is a property lookup (`ic1eq.v`), every write is a property store —
*two* indirections per access, on every sample. With 16 voices × per-sample
IIR taps this adds up; pinkNoise was doing 7 ObjectRef reads + 7 writes per
sample before the cleanup.

A dedicated class with explicit `private var` fields avoids this entirely.
V8 turns each instance into a stable hidden-class shape with **single-load
field access**, no ObjectRef wrapper.

**Rule:** if a combinator has *any* mutable cross-block state, define a
**`private class XxxIgnitor : Ignitor`** with explicit fields and
`override fun generate(...)`. The factory function returns an instance.

```kotlin
// ✅ Class form — state in named fields, lifecycle visible
private class SineIgnitor(
    private val freq: Ignitor,
    private val analog: Ignitor,
) : Ignitor {
    private var phase: Double = 0.0
    private var drift: AnalogDrift? = null

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        // … use `phase` directly. No snapshotting needed —
        // hidden-class field access is fast, and there's no
        // writeback bug surface.
    }
}

fun sine(freq: Ignitor = FreqIgnitor, ...): Ignitor = SineIgnitor(freq, ...)

// ❌ SAM lambda with captured vars — every access through ObjectRef
fun sine(...): Ignitor {
    var phase = 0.0       // closure-captured → ObjectRef in JS
    return Ignitor { buffer, freqHz, ctx ->
        // each `phase` read/write is `phase.v` indirection
    }
}
```

**Stateless SAMs are fine**: `mul(constant)`, `range(from, to)`, `abs()`,
`whiteNoise()`, the arithmetic combinators in `Ignitor.kt`, etc. The footgun
is specifically **mutable** captures (`var phase`, `var lastSample`).

**Why no snapshotting?** A previous iteration of this rule recommended
copying fields into block-local vars at the top of `generate()` and writing
them back at the end ("snapshot-into-locals"). That gives a marginal extra
JS speedup but introduces a real bug surface: any early `return`, any
forgotten write-back, any `return@use` inside `ctx.scratchBuffers.use {…}`
silently drops the mutation. **Don't do it.** Trust V8's hidden-class
optimization for monomorphic class fields and keep the code obviously
correct. The big win is going from ObjectRef → class field, not from class
field → register.

One measured exception: `KarplusString.render` keeps the string's state in
locals and writes it back after the loop (9 to 16 percent faster on V8 there,
engine tidy-up step 11). It has one exit, and its write-backs are pinned by
`BlockFramingInvarianceSpec` (I2) and `BuiltInVoiceMatrixSpec`; its KDoc says
why. An exception needs its own measurement and its own guard.

### Rule 2 — No allocation inside `process()` / `generate()`

**Why:** the audio thread runs every ~2–6 ms. Every `DoubleArray(n) { … }`,
`mutableListOf()`, `Array(n)`, or boxed-Number push to the GC. JS GC pauses
can be tens of ms — long enough to drop a block.

**Rule:** allocate once at construction; resize only when the underlying
shape changes (e.g. when the unison voice count changes). Anything that's
the same size every block lives as a `private val` (fixed) or `private var`
(reused, resize-on-shape-change) field.

Concrete patterns:

- **Per-voice scratch** — use `ctx.scratchBuffers.use { … }` (already inline,
  pool-backed, no allocation).
- **Per-Ignitor scratch** — declare as a class field, allocated once at
  construction. Resize only when `length` grows beyond the current capacity
  (`if (buf.size < length) { buf = AudioBuffer(length) }`).
- **Per-shape-change buffers** — see super-saw's `phases` and `detunes`:
  reallocated only when the voice count changes, not per block.

```kotlin
// ✅ Persistent field, resized only on shape change
private var detunes: DoubleArray = DoubleArray(0)
override fun generate(...) {
    if (newV != v) {
        v = newV
        detunes = DoubleArray(v)        // shape changed
    }
    for (n in 0 until v) {
        detunes[n] = computeDetune(...) // reuse persistent storage
    }
}

// ❌ Per-block allocation
override fun generate(...) {
    val detunes = DoubleArray(v) { computeDetune(...) } // GC pressure
}
```

## Why JS in particular

| Construct                  | JS compile shape                    | Cost                        |
|----------------------------|-------------------------------------|-----------------------------|
| Captured mutable `var`     | `ObjectRef` wrapper `{ v: x }`      | property load + indirection |
| Class field (stable shape) | hidden-class slot on the object     | property load               |
| Local `var` inside loop    | JS register / function-frame slot   | direct                      |
| `inline fun` call          | inlined into caller (no call frame) | direct                      |
| `fun interface` w/ SAM     | JS function object (closure)        | one alloc per construction  |
| `Long`, `ULong`, `Char`    | boxed wrapper objects               | banned (see SKILL.md)       |

In V8, hidden-class slots optimize well *if* every instance has the same
field shape — which is why we declare all class fields with explicit types
and assign them exactly once in the constructor (or with a `= 0.0` default),
not lazily.

JVM is more forgiving: HotSpot escape-analyzes ObjectRef wrappers in many
cases. So a pattern that profiles cleanly on JVM may still be hot on JS.
**Profile both** before declaring a fix complete.

## Audit checklist

Before merging a new Ignitor / effect / filter:

1. `grep -n "return Ignitor {" path/to/file.kt` — every match should be
   either stateless or a tiny convenience wrapper. Anything with `var`
   declared above it in the same fun is a Rule 1 violation.
2. `grep -n "DoubleArray\|FloatArray\|IntArray\|Array(" path/to/file.kt` —
   every match inside `generate()` / `process()` should be a
   resize-on-shape-change pattern past what the node built. An Ignitor node
   builds its storage in its constructor (the voice build) and seeds it at the
   first block; a guarded "first-call" allocation is what engine tidy-up step 10
   removed.
3. Spec coverage — for class-form rewrites of an existing combinator,
   add or extend a parity test in `audio_be/src/commonTest/kotlin/...` so
   the new form is asserted equivalent to the old reference math (see
   `LowPassHighPassFiltersSpec` *"class form and Ignitor form produce
   equivalent steady-state output"* for the pattern).

## Verification

| Tool                                                          | What it covers                                                   |
|---------------------------------------------------------------|------------------------------------------------------------------|
| `./gradlew :audio_be:jvmTest`                                 | Correctness — equivalence tests, NaN safety, IIR stability       |
| `./console/run-dsp-benchmarks.sh`                             | RTF (render-time / audio-time) on JVM + JS for each effect       |
| `KLANG_BENCH_MODE=alloc ./gradlew :audio_benchmark:jvmRun`   | Bytes per block on JVM + V8 against budgets: "Measuring allocations" |
| `./console/full-check.sh`                                     | Every test, the corpus, the timing and the allocation check      |
| `audio_benchmark/src/commonMain/kotlin/EffectBenchmark.kt`    | Add a benchmark case here when introducing a new hot DSP unit    |
| Browser DevTools profiler on `:klang:jsBrowserDevelopmentRun` | The actual deployment target — check sample-recorded hot methods |
| `./gradlew :klang:jvmRun` listening pass                      | Final sanity check — perf changes can hide subtle math bugs      |

The benchmark script writes timestamped reports to `audio_benchmark/results/`.
Compare a new run against the closest-prior baseline (file-name format
`<date>_<time>_<kind>.md`) to catch regressions.

## Worked examples

These reflect the round-1 / round-2 cleanups; the patterns transfer.

### Worked example 1 — `Ignitor.svf` (filter combinator)

Before: SAM lambda capturing `var ic1eq`, `var ic2eq` (ObjectRef). Inner loop
read each twice and wrote each once per sample → 8 reads + 2 writes through
ObjectRef per sample. Mode (`LOWPASS` / `HIGHPASS` / …) was a 4-way `when`
inside the loop.

After (`audio_be/.../ignitor/IgnitorFilters.kt`):

- Class form (`SvfIgnitor`), integrator state in fields.
- Mode dispatch hoisted to a single outer `when (mode) { LOWPASS -> { for ... } HIGHPASS -> { for ... } ... }`.

Full discussion in the file's KDoc on `Ignitor.svf`.

### Worked example 2 — Pink noise

Before: 7 captured `var b0..b6` ObjectRefs inside the per-sample IIR
cascade — the worst single offender in the engine. 7 reads + 7 writes per
sample.

After (`audio_be/.../ignitor/Ignitors.kt::PinkNoiseIgnitor`):

- Class form with 7 `private var b0..b6` fields.
- Inner loop reads/writes the fields directly. V8 monomorphic-shape
  optimization keeps each access to a single slot load.

### Worked example 3 — the super-oscillators (supersaw / ramp / square / tri / sine)

Before: each `super-X` factory returned a SAM lambda that allocated a
**fresh `DoubleArray(v)`** every block to hold per-voice detune ratios — at
~344 blocks/sec × multiple super-* voices, real GC pressure.

After (`audio_be/.../ignitor/Ignitors.kt`, all five now one `UnisonStackIgnitor` with a `StackKind`, engine
tidy-up step 11):

- Per-voice state (phase / dt / gain / shape) lives in a persistent
  `Array<WaveVoiceState>`, built with the node at the voice count the build can
  read and reused across blocks and count changes (a voice back after a shrink
  is rewritten whole before it renders); only a count signal, or a count that
  reads the note's frequency, past that size grows it (engine tidy-up step 10). The drift lanes live beside it in one `DriftLanes`.
- Detune + shape are recomputed only when freq / voice count / spread change
  (NaN cache keys), not every block. The hot path is a **voice-major** loop that
  hoists each voice's fields into locals (register residency). Per voice, a
  `when (kind)` calls one small private loop per shape and path (trapezoid or
  sine, plain or phased), each call direct, so every loop stays monomorphic. Each
  loop runs its own drift prologue: with one shared prologue handing the ramp in
  as arguments, V8 left the phased loops un-inlined with untyped arguments, and
  they ran 20 to 25 percent slower (step 11, review round 1).

## Rules learned by measurement (2026-09)

The records behind each of these are in `audio/ref/memory-history.md` (the 2026-09-07 to 2026-09-16 entries).

- **Loop shape beats block-pass count.** A multi-voice or multi-partial oscillator renders voice-major (outer loop
  over voices or partials, inner tight loop over samples with the phase in a local), accumulating into the buffer.
  A sample-major loop that keeps its phase in an array was slower than twenty-one separate block passes.
- **On Kotlin/JS, hoist every per-block value into a LOCAL before the sample loop.** Reading a container's fields
  through an inline method inside the loop is free on the JVM and cost a drifting supersaw 16 percent on V8
  (`DriftLanes`: `ownLane(n)`, `sharedWalk()`, the weights as locals, one inline `driftStep`). `copyInto` allocates
  a typed-array view per call on JS: in a hot path use `copyRangeInto` (`utils/buffer_copy.kt`), a plain loop.
- **On V8, a double that seeds a loop-carried variable must pass through an arithmetic operation first** (2026-10,
  engine tidy-up step 11, review rounds 1 and 2). A double that comes from a call result, a field or an argument of
  a function V8 does not inline (a delay, a drift ramp's start) and seeds a variable the loop updates stays a
  tagged value, and every update allocates a heap number: one 16-byte box per sample. Computing it in the same
  function is not enough (a value from `lanes.startOf(n)` boxed so). The remedy is `* 1.0` once before the loop
  (exact for every value but a NaN's payload). Measured on the Karplus string: without it the superpluck with drift
  ran 424 to 633 scavenges per run and 40 to 76 percent slower (records: `docs/tasks-archive/2026-10/20261009-engine-tidy-up.md`, step 11
  (b)); the unison stacks' phased loops showed the same with arguments. No test can pin it, because the sound is
  bit-identical either way: the KDoc at the site is the guard. Check a new hot loop with `--trace-gc`.
  Applied by rule to every drift seed (the four unison stack loops, the sine, the partial bank, the wave, impulse and
  sample oscillators) and to the partial bank's phase, seeded from an argument (V8 allocation pass, 2026-10-08,
  `docs/tasks-archive/2026-10/20261009-engine-tidy-up.md`). Before it a five-partial bank with drift took about 10.5 KB per block (one heap
  number per sample per partial; 2.1 KB without drift when run alone, 10.5 KB under a mixed profile), 304 bytes
  after, on both bundles, pinned to one core and not. The stacks show why a loop shape needs a MIXED profile: run
  alone, a drifting stack boxed nothing per sample; with every oscillator case warmed in one process (the worklet's
  situation) some processes boxed one heap number per sample per voice (14.4 KB per block on `supersine-7-drift`
  and others). On the development bundle that is confirmed: 2 of 3 processes before and 0 of 3 after in review (4
  of 8 and 0 of 7 in the pass). On the production bundle it was seen in 2 of 3 processes in one sample and in 0 of
  3 in another, so it is not reproduced there; the seeds cost nothing and stay by rule.
  Engine follow-up 5 (2026-10-11) applied the rule to every field seed of an OSCILLATOR loop too: the phase of the
  wave loops (`WaveIgnitor`'s PWM loop, `renderHoisted`, `renderPhased`) and of the unison stacks' four loops, the
  sample's playhead, the Karplus string's filter state. The PWM loop's phase seed boxed per sample on the
  development bundle under a mixed profile (about 2 KB per block, 64 B after), where `voice.phase` compiles to a
  getter call that V8 did not inline into that `generate` in one traced process (a call result under this rule);
  production loads the field directly and never showed it, and the others boxed on neither bundle. The oscillator
  scope is a choice: the filter and effect loops were not swept (candidates: the SVF coefficient ramps in
  `IgnitorFilters.kt`, `DcBlocker.process`; no box measured).
- **On V8, a non-integral double handed to a function that is not inlined is a heap number per call** (2026-10-08,
  engine tidy-up step 12 (a)). The calling convention is tagged, so a double argument or a double result of a call
  V8 does not inline is boxed; an integral value in Smi range (a mix of 1.0, a floor of 0) travels as a Smi and is
  not. Measured for the resonator stage: its first shape passed its mix and floor to `configure` every block and took
  about 40 bytes per block of a steady body. Its remedy is a holder the caller owns and fills at control rate
  (`ResonatorConfig`, like the EQ's `DoubleArray`), handed over by reference; and a method that Kotlin's `inline`
  makes large can leave V8 without the budget to inline its small callees (`ResonatorBank.install` boxed the wet/dry
  law's doubles until its per-band and blend halves became methods of their own). The JVM boxes none of this.
  **A holder of double fields can still box; an array element access in optimized code never allocates** (engine follow-up 7,
  2026-10-11; a value read from it can still box further on, into a call or a field, hence the `* 1.0`):
  `DriftLanes` first held the drift ramp in two double fields written per voice, and in a single-case process V8
  stored them as fresh heap numbers inside the superpluck's `generate`, as many as the double returns they replaced
  (gone only under a
  mixed profile; a field initialized at 1.5 instead of 1.0 boxed the same; the fields ended as Double representation
  in `--trace-generalization`, like `AnalogDrift`'s, which do not box, so representation is ruled out; in that form V8
  inlined `advanceLane` into the superpluck's `generate`: measured, not explained). The holder is a
  `DoubleArray(2)` (a `Float64Array` on Kotlin/JS, whose elements are raw doubles), read through `inline` properties,
  and the box is gone in the processes measured (a rare tail of about 1 process in 30 on the superpluck's drift
  path, 300 to 460 B, not located, is recorded in item 7). Measure a new field holder with `--predictable` in a
  single-case process too. Item 29's field holders were checked so and do not box (item 31).
  **A per-sample helper past V8's bytecode limit for inlining (460 bytes) is never inlined**, however warm:
  `--trace-turbo-inlining` prints "Cannot consider ... for inlining (reason: 5)". Make it a Kotlin `inline` function,
  then check with the same flag that V8 still inlines the small helpers it calls (the caution above; here
  `gainReductionDb`, 70 bytes, stays inlined). Engine follow-up item 3, 2026-10-10: the house limiter's
  `Compressor.lookaheadStep`, 974 bytes, boxed its level argument per sample whenever anything sounded, and its gain
  result per sample from its first reduction on (the release settles a few ulps below 1.0, never exactly 1.0, so
  the result stayed non-integral until `MasterStage.reset()`): about 2 and 4 KB per block at the master. It is the
  likely cause, unproven, of the V8 pass's "a noise voice allocates" (noise plus saw is loud enough to engage the
  limiter; no noise box reproduces on either bundle). The realtime master stage runs in `PlaybackEngineDispatcher`
  and the offline one in `KlangAudioRenderer`, not in `PlaybackEngine`, so a probe that stops at `PlaybackEngine` or
  at one voice does not see it.
  The delay, reverb and phaser were located in the V8 allocation pass (2026-10-08). Run with ten times V8's inlining
  budgets (`--max-inlined-bytecode-size-cumulative=9200 --max-inlined-bytecode-size=4600`) the delay and the phaser
  allocate nothing and the reverb about 5 bytes per block (5.2 on the production bundle, 3.9 on the development one),
  so nearly every byte is a double crossing a call that V8 left out for lack of budget: the writer's `configure`
  arguments, the phaser stage's six arguments to `Phaser.process`, `KnobGlide.advance`'s result, and inside
  `configure` the setters, `KnobGlide.retarget` and Kotlin's `isFinite()` (a stdlib call on Kotlin/JS). The writers
  now hand a holder (`DelayConfig`, `ReverbConfig`, `PhaserConfig`) and the phaser stage hands `Phaser.process` a
  `PhaserBlock`. Measured and dropped: an inline finiteness compare in place of `isFinite()` (better pinned to one
  core, worse unpinned on the phaser and on all three stages together), a Unit-returning glide step (within noise),
  and the resonator's split remedy on the delay (worse). After the pass, at engine level, the production bundle read (pinned, medians) delay 82, reverb 22, phaser 144
  (192 with a floor of 0.5), all three 197 bytes per block (HEAD 277). **After engine follow-up 1 (2026-10-11) every box
  located in the stages' block processing is gone** (an off phaser's call into `Phaser.process`, about one heap number
  per block in single-case processes, is located, not removed) (`docs/tasks/in-progress/engine-follow-ups.md`, item 1): the doubles that crossed were
  `KnobGlide.advance`'s result (now Kotlin `inline`), the delay's and the reverb's tail step (`TailCeiling.observe`,
  `peakOf` and the units' window and laps getters; now `DelayLine.observeTail` and `Reverb.observeTail`, one call that
  takes no double, with the helpers `inline`), the delay loops' per-block arguments (`DelayLine.blockArgs`, a
  `DoubleArray` holder), the phaser's `alphaAt` (now `inline`) and two configure calls (the depth's `coerceIn` written
  out, the constant feedback no longer rewritten per block). In six shuffled mixed processes the delay and the reverb
  read exactly what the engine without them reads, all three together what the phaser alone reads (before its
  `configure` was fixed, below).
  **The per-block `configure` is a class of its own**: a writer re-applies the resolved knobs every block
  (`KatalystChain.applyParams`), and in some processes V8 compiles the stage's `configure` with nothing inlined but
  `isFinite` (a mixed profile, where most orbits take the off arm, leaves the active arm cold), so every setter,
  `KnobGlide.retarget` and the phaser's wet/dry law take their doubles across calls; in single-case processes the
  phaser's `WetDryMix.dryCoeff` and `wetCoeff` were left out of the writer's budget. The remedy is no call on the steady
  path: the house pattern of an unchanged config (the EQ's `unchanged`, the resonator's, the compressor's), now in the
  phaser stage too (`KatalystPhaserEffect.applied`, review round 1 of follow-up 1), after which every stage case read
  the engine without a stage in every mixed process measured but one pinned process (the delay, one heap number).
  The delay and the reverb skip an unchanged config too since engine follow-up 32 (2026-10-16;
  `KatalystDelayEffect.applied`, `KatalystReverbEffect.applied`): reproduced first in pinned mixed processes, their
  `configure` booked 64 and 48 B per block (`ensureRing` and the setters left out), and after it every stage case
  reads the engine without a stage in every pinned and unpinned mixed process measured. Two writes stay on the path,
  which is what keeps the skip exact: a coefficient glide that moves (the old path writes its target every block,
  and a reader between the configure and the block sees it) and the delay's latched retry of a refused grow. The
  OFF arm is never skipped.
  **A `Math.ceil` handed to a call that is not inlined boxes even when it is a whole number:** V8 cannot rule out -0,
  so it cannot travel as a small integer (the `toInt()` inside the delay's `framesFor(time)`, run every block until
  follow-up 1 kept the conversion off the steady path).
  Six cautions for this class of box:
  - **Measure pinned AND unpinned.** `taskset` to one core puts V8's concurrent compiler on the core of the measured
    code, which changes the tier-up history and so which calls are left out. A single bundle varies by up to five
    heap numbers per block between unpinned processes (and pinned ones vary too while the sibling threads are busy);
    pinned on a quiet machine a run repeats exactly. A worklet's compile jobs run on other threads, so the unpinned
    condition is at least as real. A fix counts only when it holds in both.
  - Which call V8 inlines depends on the caller's whole compilation unit, so a change elsewhere moves these boxes. A
    semantically inert change on the per-block path (a placebo) shifted single rows by up to two heap numbers per
    block, pinned, on either bundle. Judge a fix against that band, at engine level, not stage by stage in a probe.
  - Measure the production bundle (`:audio_be:compileTestProductionExecutableKotlinJs`): it reads fields directly
    where the development bundle calls accessors, and it boxes in different places.
  - Run long: right after a change the first few megabytes are tier-up, not steady state.
  - **Do not bracket a mixed-profile window with forced mark-compacts** (engine follow-up 10d, 2026-10-19). A
    `gc()` at the window's start likely disturbed the compiled code of a process that had warmed many cases (no
    deopt trace was taken): HEAD's fm voice read 84 B per block bracketed so and 0 B without, and the profiler then
    showed fractional bytes per block in the oscillators. For an exact count of one window use one scavenge
    (`gc({ type: 'minor' })`, `--expose-gc`) and the new space's growth across the window
    (`v8.getHeapSpaceStatistics()`, `new_space`) with a semi-space large enough that no collection falls inside it
    (`--min-semi-space-size=128 --max-semi-space-size=128` for 200,000 voice blocks, a `PerformanceObserver` on `gc`
    to prove none did): it reads 0 B on an allocation-free voice and whole heap numbers otherwise. The difference of
    two processes read about ±60 B per round on the same probe.
  - **Probe at a non-integral note, and subtract a plain voice** (engine follow-ups 10c and 10d, review round 1). A
    double that is a whole number in Smi range travels as a small integer and did not box (measured here on products
    and field loads; the `Math.ceil` exception above still holds), so a probe at 220 Hz hides
    every hand-off whose value is the note times a simple ratio (220 x 1.4 = 308.0 exactly): 10d's first pass read the
    fm's modulator frequency and 10c's pin as no site there, and both box at middle C. Play the allocation harness's
    middle C (261.6255653005986 Hz), keep the derived frequencies non-integral too (a ratio of 1.4 on a non-integral
    note), and subtract a plain classic voice at the same note, which carries item 30's root box (16 B).
  Measure with `--trace-gc-nvp` (it reports the bytes allocated since the last collection) and
  `--max-semi-space-size=1`, so a collection falls every megabyte and a residue of a few bytes per block is counted;
  at the default semi-space one run read 0 and the next 20.7 bytes per block for the same code. Locate with the
  sampling heap profiler (`HeapProfiler.startSampling` with `includeObjectsCollectedByMinorGC`), started and stopped
  around the measured window only: the warm-up runs in the interpreter, which boxes every double.
- **On V8, an exhaustive `when` whose result is a double boxes that result inside a sample loop** (2026-10-10,
  engine follow-up item 9). Kotlin/JS compiles it into a `switch` whose `default` arm calls
  `noWhenBranchMatchedException()` and leaves the result variable unassigned. The variable is a function-scoped
  `var`, so on that path it holds the previous sample's value: V8 cannot see that the call throws, keeps the variable
  live around the loop seeded with `undefined`, holds it tagged, and every result that is not a small integer becomes
  a heap number (16 bytes on node, 12 in Chrome with pointer compression, the count the same; a `±1.0` clamp travels
  as a Smi and does not). The expression form triggers it, an inlined one too (an inline `fun f(x) = when (...)`
  called per sample), and by the same JS shape (inferred, not measured) a `val y: Double` assigned in a statement
  `when`. Measured on the shaper (`DistortionCore`, production bundle, pinned and unpinned): one heap number per
  shaped sample at every oversampling factor, about 2 KB per block without oversampling, 8 KB at 4x, 33 KB at 16x
  (`tube`). Two remedies, both measured there:
  - **The statement form** removes the boxes alone: `var y = 0.0; when (x) { A -> y = ...; B -> y = ... }; return y`.
    The variable is initialized, so no path leaves it undefined (the `default` arm stays, harmless). Three lines,
    and the right fix where the loop is not worth splitting. It is a stated exception to `/code-style` §19, which asks
    for the expression form: the `when` stays exhaustive (no `else`), the compiler checks that every arm EXISTS but not
    that it assigns `y` (the initializer hides a missing assignment, so a spec that renders every arm is the guard), and a
    comment at the site names this rule (it also answers the redundant-initializer warning).
  - **The hoist** takes the `when` out of the loop, one loop per arm through an inline loop helper and a lambda
    (worked example 1's shape). It removes the boxes AND runs a smaller, switch-free loop: on the shaper 12 to 25
    percent faster again than the statement form (both reviewers of item 9), and about 15 to 50 percent faster than HEAD.
    The shaper keeps it for that speed; it costs a table per loop form.
  No test can pin the boxes: the KDoc at the site is the guard. Find candidates by grepping the production bundle
  for `noWhenBranchMatchedException` inside a `do { ... } while` loop. The envelope curve was the second site
  (engine follow-ups 10a and 10b, 2026-10-10): `adsrCurveShape`, inlined three times into every per-sample envelope
  loop (the chain `adsr` and so `classic()`'s envelope, the pitch envelope, the FM index envelope), boxed one heap
  number per sample while an envelope moved, on every curve and at every release length (about 2.1 KB per block per
  envelope, 6.2 KB with all three). The statement form in the shared helper removed them all and runs 2 to 21 percent
  faster than HEAD. **Measure the hoist's upper bound before writing it**: a copy of the bundle with every switch
  deleted from those loops by hand (the one arm the rows use) ran within about 2 percent of the statement form on
  the exponential (default) rows and 4 to 8 percent faster on a bare linear envelope (0.99 to 1.00 inside
  `classic()`), so the envelope keeps the three-line form; the hoist pays where the switch is a large share of a tight loop (the shaper).
  After it, that grep finds no other `when` yielding a double in a sample loop of `audio_be`'s production bundle.
- **On V8, `x ?: field` with a nullable map value and a double field boxes the field's value** (V8 allocation pass,
  `KatalystKnob.resolve`): the merge is a tagged value, so an absent knob with a non-integral default became a heap
  number. Two branches store the same values without it: about 180 of the about 1,310 bytes a new param map cost on
  the classic chain, on both bundles, pinned and unpinned. The rest was the map lookup itself (the next rule).
- **On Kotlin/JS every `HashMap`/`HashSet` operation with a non-null key allocates a 40-byte context on V8** (engine
  follow-up 2, 2026-10-11). The stdlib's generic `hashCode(dynamic)`, which `get`, `put` and `containsKey` call
  through `findKey`, ends in `else -> throw ...`; a `throw` in expression position compiles to a closure called on the
  spot, the closure captures the `typeof` result, so V8 creates a function context on EVERY call, whichever arm runs:
  40 bytes on node x64 (about 20 in Chrome with pointer compression, inferred), hit or miss, empty map or full,
  inlined or not. The profiler puts it in whichever function the `get` chain was inlined into (`KatalystKnob.resolve`
  here, whose own code allocates nothing). Measured: 27 lookups 1,080 B exactly in every process; a new orbit map on
  the classic chain 1,096 B (27 knobs, plus the reverb's documented lowpass box); a classic voice build 24 lookups
  whenever its `ignitorParams` map is not null, up to 76 with many slots set (957 to 3,062 B and 10 to 38 percent of
  the build time). Kotlin is 2.3.10; the JVM caches a string's hash and allocates nothing. **Remedy:** a map the
  engine reads by key on JS is a `KlangMap` (`common/infra/KlangMap.kt`, a native JS `Map` on JS, a `LinkedHashMap`
  on the JVM; not a Kotlin `Map` since step 5, so it has no `entries` to walk): its lookup allocates nothing (a new orbit map about 16 B; `C-remap` 2.03 to 2.16 us
  per block against 2.80 to 3.11 with the decoder reverted, about 0.9 us per new map). The worklet's decoder
  (`wireDecodeStringDoubleMap`), the warm-up and the sample-envelope merge make them; the allocation check's
  `C-remap` guards the decoder. A map with `Int` keys has no `KlangMap` (its keys are strings): the oversampled
  sub-pools of `ScratchBuffers.oversample` were a `mutableMapOf<Int, ScratchBuffers>` asked by every oversampled node
  and stage per block (three lookups per block on the bus distort at 2x, about 125 B), and are now two small arrays
  scanned by factor (engine follow-up 34, 2026-10-16). **Read a `KlangMap` by key, and walk it with its own walk:**
  it has no `entries`,
  `keys` or `values` (step 5), and iterating them on a map on JS allocates about 110 B per entry (the JS iterator's results
  and an entry object; 666 B per walk of 6 entries on node), where a `LinkedHashMap` walk is free once V8 inlines the
  whole loop. That inlining is not reliable either: a lookup written out as a scan over a `LinkedHashMap`'s entries
  was rejected, because its iterator escaped wherever V8 did not inline the loop (1,554 B on a one-entry map), and on
  the JVM it made an iterator per knob. `KlangMap.forEachEntry`, `copy` and `setAll` from a `KlangMap` walk through the
  JS `Map`'s own `forEach` (engine follow-up 2, step 2): a 6-entry copy 592 B, against 1,160 B through `entries`,
  1,385 B through `new Map(source)` (which goes through the iterator protocol, a `[key, value]` array per entry) and
  1,290 B for a `LinkedHashMap`'s `toMutableMap()`; the wire encoder walks a `KlangMap` with `forEachEntry`. Measured at
  event level, that walk is what made sprudel's param bags pay off (below). The steady block path makes no lookup
  at all (counted).
  **Sprudel's param bags (step 2)**: `ParamBag` (both slot maps of an event) and `classicSlotParams`' copy are a
  `KlangMap`. Per event, querying a cycle and converting with `toVoiceData` plus the wire encode, production bundle,
  mixed profile, 3 processes: three bus doors 17.1 to 12.4 KB and 7.8 to 4.3 us, many doors 45.3 to 31.7 KB and 24.5
  to 12.5 us, a patterned control 16.3 to 13.5 KB, six voice doors 19.0 to 17.6 KB, a plain note unchanged (no bag).
  With `copy` through `new Map(source)` and the walks through `entries` (the first form) the saving was about 40 percent as large
  (three bus doors 15.1 KB, many 40.1). The stdlib hashed 64 times per event with three bus doors and 143 with many,
  and those hashes were not the only cost: a Kotlin/JS `LinkedHashMap` copy and growth allocate more than a JS `Map`.
  The JVM: `KlangMap` is a wrapper there, +0 to +168 B per event depending on the shape, up to about 2.4 percent on
  small events (review round 1 read +2.0 and +2.4 percent), time within HEAD's spread. The slot copy keeps HEAD's
  presizing (`KlangMap(capacityHint)`, the JVM's initial table size); without it the worker's probe read its two
  voice-door shapes 6 to 8 percent slower in 3 runs each, and review round 1 found no such effect on six shapes, so
  the hint is kept as parity with HEAD, not for a measured gain. Comparing or printing a param map walks it (in
  step 2 through `entries`, `==` about 1.8 times HEAD's bytes; since step 5 through `forEachEntry`, not measured);
  nothing does it per event. **The per-note lookups (step 3):** the sound registry's two maps are `KlangMap`s and
  `engineFor` looks the playback id up once, so a synth note hashed once (the dispatcher's `engines` map, 40 B),
  where it hashed 8 times; since step 5 that map is a `KlangMap` too, and a synth note at the defaults hashes no more (an opt-in banded phase
  pool, `phasePool` above 0.5, still makes one stdlib lookup per unison note: `PhasePool.kt`). **On V8, `toLowerCase` allocates a fresh string even when nothing changes**: 23.9 B per
  call in a node loop from 1 to 8 characters (`"b"` to `"supersaw"`), 32 B from 9; in the engine's own path a
  2-character `"bd"` paid it too. A reviewer's probe read nothing for `"bd"` because that name missed its map, unlike
  its others (review round 2): measure in the real path. The registry lowercased the sound name at every level of its fork chain, 7 copies per
  synth note. Its lookups now lowercase only when the name has a character that could change (`lowerKey`: an index
  loop; any ASCII capital or any character above 127 takes `lowercase()`), once per call. The worker's probe (a synth
  note per block through the dispatcher, single-case): 13,257 B per block at HEAD, 12,806 now; a sample note 1,033 to
  737 (`"snare"`). The allocation check's `E-C-n1` reads 12,982 to 13,036 B (healthy rounds) against HEAD's code's
  13,435 to 13,476, under a line of 13,100: it catches the registry back on `HashMap` and the lowercasing back at
  every level, not `engineFor`'s second lookup (40 B; since step 5 a `KlangMap`
  lookup, 0 B) nor a lowercase on every call (about 72 B per synth note, 25 B
  under the line); those two are guarded by the comments at their sites. Since step 5 (the dispatcher's map a
  `KlangMap`, about 35 B less per note, all of it that map) it reads 12,988.5 to 13,050.7 B in 45 healthy rounds
  (full-check medians 13,004 to 13,023) under a line of 13,068. The margins are thin: a lowercase on every call is
  caught by its median (13,090 and 13,091), its lowest round 5 B over the line; the dispatcher's map back on a
  `HashMap` read medians 13,047 to 13,058 and goes red in about 1 run in 30, so the comment at that field guards it. Grep a new per-event path for `HashMap`, `mutableMapOf`, `mapOf`, `linkedMapOf`, `associate`,
  `toMap`, `getOrPut` and `in` on a set before it lands: all of them make or read a stdlib map. The stdlib site is
  worth an upstream report.
  **Measured, not removed:** in single-case processes two writers' `resolve` box one heap number each per new map
  (the body's and the vowel's `fill`, which V8 leaves out and which hands the unset index, NaN, to
  `ResonatorTables.at`); under a mixed profile they do not. The delay writer's, "fully inlined, not explained" here
  before, was its gate's `NaN` arm, removed by engine follow-up 41 (`C-remap` 32.0 to 16.8 B, `--predictable`).
- **On V8, a shared one-line forwarding helper is one megamorphic call site: make it Kotlin `inline`** (2026-10-10,
  engine follow-ups 8 and 12). V8 keeps its type feedback per call site in the bytecode, not per caller, so a
  small plain function that every caller goes through to make one call (`Ignitors.readParam` was
  `param.blockStartValue(freqHz, ctx)`, one line) puts every caller's receiver on ONE site: it goes megamorphic, the
  callee is never inlined, and its double result crosses a call, a heap number per call whenever it is not a small
  integer (the rule above). The `Double?` of `controlRateValueOrNull` was blamed first; it was not the cause: on JS
  `Double?` and `Double` are the same number. Measured: every knob read of the engine boxed so, 4 to 6 heap numbers
  per classic voice block (about 60 to 100 bytes), about 770 bytes per block on a row with a dozen releasing classic
  voices. **Remedy:** make the helper Kotlin `inline` (`@Suppress("NOTHING_TO_INLINE")`, a KDoc that says why), so
  each caller owns its own site, monomorphic in practice, and V8 inlines the small callee into the caller; keep the
  inlined body small (here a flag gate, `isBlockConstant`, then the primitive scalar, else the member). A plain
  function per site is not always enough inside a large caller: in a hand-edited bundle, per-site helpers that carried
  the whole fallback body stayed un-inlined in `AdsrIgnitor.generate` (its budget used up) and three of them still
  boxed. Measured on the production bundle (HEAD / inline / HEAD again, medians of
  3, unpinned): classic saw 77 / 21 / 82 bytes per block, unison stack 98 / 0 / 97, the dozen releasing voices
  1,846 / 1,062 / 1,844, bit for bit the same sound, at 0.96 to 1.01 of HEAD's time; pinned alike (the record:
  `docs/tasks/in-progress/engine-follow-ups.md`, items 8 and 12). No test can pin it: the KDoc at the helper is the guard.
  **How to check:** the sampling heap profiler puts the box in the helper or in its callee; `--trace-turbo-inlining`
  then shows the callee as a candidate the caller never inlines (`blockStartValue` from `AdsrIgnitor`'s `generate`,
  five times on HEAD), and after the change the small callees inlined into the caller instead (`isBlockConstant` and
  the scalar, five times each, no `blockStartValue` candidate left). Grep for other shared helpers whose body is one
  call returning a double before writing a new one. **Check the JVM with a megamorphic profile too, and return a primitive:**
  inlining the helper moves the call each site makes. With step 1 alone the body called the nullable
  `controlRateValueOrNull` at the site, and on the JVM a site C2 could not reduce to an inlined fresh box (three or
  more node classes, production ones among them) boxed a `Double` per read where HEAD's shared `blockStartValue`, a
  primitive return, settled to none (a scratch probe on the partial bank's count reads: 72 B per block, HEAD 0;
  `FirstBlockAllocationSpec` caught it, the single-profile harness did not). Step 2 (maintainer, 2026-10-10) made
  the scalar a primitive, `controlRateValue(freqHz): Double`, read only when the flag is true: the probe reads 0,
  and the constant-fold ladder's boxes under a vibrato went with it (72 B per vibrato voice block in some
  processes before, 0 in every round after; about 95 MB of `Double`s over the harness run, now none on the render
  path). A nullable scalar on a hot read is a box on the JVM wherever the call is not inlined; keep the nullable
  view (`controlRateValueOrNull`, an extension) for the cold readers only.
- **On V8, one node class is one compiled body with one inlining budget, shared by every instance: measure the
  whole voice, not the stage** (2026-10-11, engine follow-up 29). TurboFan inlines a method's callees in order of
  call frequency until a cumulative budget (920 bytes of bytecode by default) is spent. When two instances of one
  class take different branches (`classic()`'s lowpass with a cutoff envelope and its highpass without one, both
  `SvfIgnitor.generate`), each branch's callees run at half the frequency and are the first left out. So helpers
  that a one-filter probe sees inlined (`prepareModEnvelope`, 56 bytes; Kotlin's `coerceIn` and `isFinite`, stdlib
  calls on Kotlin/JS) stayed calls in the full voice, and their doubles crossed as heap numbers: about 8 per block on
  the lowpass and 4 on the highpass, the sweep's arguments included; alone, the lowpass with `env` and `analog`
  boxed only the sweep's three arguments (47 to 88 B on HEAD) and the highpass with `analog` nothing measurable.
  Remedy: move the branch's per-block setup into a method of its own that takes no doubles (the node's own fields
  hold the block's reads) and returns none (the results land in a holder), so it spends its own budget
  (`SvfIgnitor.prepareEnvelopeSweep`, larger than the 460-byte limit, so never inlined back); a callee past that
  limit takes its doubles from a holder (`SvfCoeffSweep`'s cutoffs and q); a per-block step returns nothing and the
  caller reads a field (`FilterHumanization.stepDrift`, then `driftMul`). A small setup method can still be inlined
  back where the voice is simpler, and then ITS callees compete for the caller's budget again: the static path's
  `computeStaticCoeffs` is a call in a two-filter voice but inlined in a one-filter one, where Kotlin's `coerceIn`
  was left out (about 2 heap numbers per block more than before, measured) until it was written out as two
  comparisons (`clampSvfRange`). The `isFinite` guard beside it, a 23-byte stdlib call, was the next one out (engine
  follow-up 31): a lowpass or a highpass alone with `analog` left it out (about one and two heap numbers per block),
  and freeing two enum lookups of `generate`'s budget moved that box to a lowpass with `analog` instead of removing
  it. It is `isFiniteInline` now. So a split is checked on both shapes.
  Two placements measured worse and were dropped: the static path in place (boxes in the two-filter voice) and
  both paths in one method (boxes in the one-filter voice, not explained). A box can also move instead of going: on
  HEAD V8 inlined `blockDriftMultiplier` into `generate` and left the `nextMultiplier()` it called out; with
  `nextMultiplier` folded into the lane's step, the two-filter voice left `blockDriftMultiplier` itself out, and its
  double result crossed instead, one level up (seen in the trace). Inlining an outer method does not inline what it
  calls, and a double return is a box wherever the call stays: hence the step that returns nothing. `C-full` went
  from about 190 to about 40 bytes per block. A probe that renders one stage alone does not reproduce this class:
  build the voice the songs build, then narrow it.
  **Engine follow-up 31 (2026-10-15) closed what item 29 left on the full voice**, each by a remedy of this file:
  `EnvelopeCore.prepare` is Kotlin `inline` and leaves its six doubles in the core's own fields for the call past
  the 460-byte limit (`prepareBlock`); `DistortionCore.shapeRun` (sixteen loops, never inlined) reads its drive from
  a field. Both are fields and not a `DoubleArray`, because an array is an allocation per voice build (a
  `Float64Array` per envelope read about 224 B per note on V8 and 64 on the JVM, `E-C-n1` over both lines), and these
  fields did not box (measured, `--predictable`). `classic()`'s tremolo folds a voice-constant depth into one constant
  at build (the chain
  `MinusIgnitor.controlRateValue` then `MaxIgnitor.controlRateValue` was left out of the voice's budget, its double
  result a heap number); and the SVF clamps test finiteness with `isFiniteInline`. In `--predictable` single-case
  processes `C-full`, a lowpass or highpass with `analog`, the tremolo alone and the fused distort now read what the
  classic saw reads (about 15 B per block, the method's floor). Left open on these voices then: the fm's per-block calls
  (items 10c and 10d, about 2 heap numbers on `C-env3`) and a classic envelope with a non-integral sustain (about one
  heap number, booked in `AdsrIgnitor.generate` as its root call, on HEAD too; likely item 30's class). **Engine
  follow-up 10d (2026-10-19) fixed the fm's knob reads**, two cases of the rules above: `prepareModEnvelope` (four
  stage knobs as double arguments, left out of `FmModIgnitor.generate` in a mixed profile) and `finiteOr` (its stdlib
  `isFinite()` left out of the same budget) are Kotlin `inline` now, `finiteOr` over `isFiniteInline`. At 220 Hz,
  where every other hand-off of the fm is a whole number, `C-env3` then reads 0 B on the tree in every condition
  (HEAD 16 to 48). At middle C, as the allocation check plays it, the tree reads 32 B on every single-fm row (HEAD up
  to 112): the root box (item 30) and **one heap number per fm per block, still open: the modulator's frequency**
  (`fmFreqVal * ratioVal`, non-integral at almost every note) handed to the modulator's `generate`; and one more per
  wrapper whose outer mod reads the note (item 10c, the pinned carrier frequency). Both are item 30's class, a tagged
  cell their remedy, which the maintainer declined for the root box (item 30, decision D): revisited with the FM round (maintainer, 2026-10-19; `docs/tasks/future/fm-revisit.md`).
  The sustain box may have gone with `finiteOr` (not shown: HEAD read it in some processes only).
  **Engine follow-up 34 (2026-10-16) closed the oversampled path** that item 31 left (`tube4x`, `E-distort`): besides
  the sub-pool map (the `HashMap` rule above), the bus distort handed its drive to a `DistortionCore.process` that V8
  did not inline into the chain (one heap number per block, two while the amount glides, the ramp's two ends).
  `process` and `processRamped` are Kotlin `inline` now and stage the drive in the core's fields, `EnvelopeCore`'s
  pattern. Every oversampled case reads the floor under `--predictable` (`E-distort` 155 to 16 B at 1,000,000
  blocks, `tube4x` 57 to 16, `E-parallel` 197 to 15, `E-bands` 232 to 16).
- **A knob that moves every block makes its whole per-block path hot: measure a group of sites, and in both modes**
  (2026-10-12, Katalyst knob expressions step 6, `docs/tasks-archive/2026-10/20261012-katalyst-knob-expressions.md`; the runs:
  `docs/benchmarks/2026-10-12_183400_knob-step6-v8.md`). A modulated Katalyst knob runs the chain's hook every block
  (the knob's evaluation, the writer's refill, the stage's `configure`, `KnobGlide.retarget`), and every small
  double-taking call on it competes for one inlining budget. Fixed: `KnobGlide.retarget` a plain method (now `inline`),
  the stdlib `isFinite()` calls of the knob, the glide and the gain and compressor writers on the hook (now
  `isFiniteInline`, one compare; the delay, reverb and resonator `configure` keep theirs, measured harmless), `Compressor.processGliding`'s six
  arguments and `PhaserCore.prepareBlock`'s two (now holders), and `KnobGlide`'s three double fields, which boxed
  where a `DoubleArray` does not (item 7's rule again; it also took about two heap numbers per block off the classic
  `E-stages` and `E-delay` under `--predictable`: 45.5 and 61.1 with the fields, 15.8 and 32.3 without; the check's
  method reads no difference). Lessons: the `isFinite()` calls are
  measured as a GROUP (all put back: one to five heap numbers per case), because which stdlib call V8 leaves out
  depends on the hook's whole budget; the allocation check's mode and `--predictable` can disagree, and a case can
  carry a tail of high rounds in either tree (`E-kat-all7` read a round of 277 against medians of 155 to 161), so a
  remedy is judged by a mutant in both modes and a line is set above the tail; and the double-field settings holders
  (`CompressorSettings`, `DuckSettings`) do not box: as `Float64Array`s in a hand-edited bundle (run XS) `E-kat-dyn`
  and `E-kat-mix` read what run T reads (98.7 and 196.3, `--predictable`). The residue on the moving delay and
  reverb paths was a `NaN` arm (item 41, the `Double.NaN` rule below). **Closed by engine follow-up 40 (2026-10-16): a stage that writes several knobs
  of one object every block hands them over in ONE call through a holder**, not through a setter per knob. The orbit
  compressor called `Compressor`'s five setters, which V8 did not inline, so each knob crossed as a heap number
  (the profiler booked 48 B per block in `KatalystCompressorEffect.configure`), and each setter recomputed the three
  time constants. Now it fills `Compressor.knobArgs` (a `DoubleArray`) and calls `applyKnobArgs`, which guards and
  coerces each knob as its setter does and computes the constants once: `E-kat-dyn` 97.1 to 45.3 B (the duck's
  floor), `E-kat-mix` 96.9 to 67.3 (`--predictable`), pinned about 200 ns per block faster (0.98). Collapsing the
  setters is exact only because nothing between them reads what an earlier one computed; check that first. The
  setters stay public for the other callers, and their getters now read private fields: a Kotlin/JS getter on a
  property with a private backing field compiles to a call (`retarget` calls three), which V8 inlines here
  (measured: the floor); a hot reader inside the class reads the fields.
- **A callee's own inlined size counts against its caller's budget: a helper that calls a small function at two
  sites can fit no caller** (engine follow-up 39, 2026-10-16). `--trace-turbo-inlining` prints it as "existing opt
  code's inlined bytecode size": V8 adds what a candidate's own optimized code inlined to the candidate's bytecode,
  against the caller's cumulative 920. `fbm` called `noise` at two sites (the one-octave early return and the loop),
  so it carried two inlined copies (690 bytes, 806 with its own 116) and fitted no caller that had spent more than
  about 114: the Katalyst knob's `sample` handed it the clock and the persistence as heap numbers and got the result
  back as one, three per noise and block. Two remedies, each needed in a different profile: `fbm` calls `noise` at
  ONE site (the single octave returns from the loop's first pass, bit for bit; it fits `sample` when only noises are
  hot, `E-kat-perlin` 113.7 to 21.8 B), and the noise arms are small methods of their own taking two Ints
  (`samplePerlin`, `sampleBerlin`), which V8 leaves out of `sample` when several modulator kinds are hot and then
  compiles with room for `fbm` (`E-kat-all7` 118.5 to 15.0, `E-kat-mix` 196.2 to 96.9). Each alone left one profile
  boxing. Where V8 places such a method follows the profile, so measure a fix of this kind on every case that
  reaches the site, with a mutant per half. Kotlin `inline` on `fbm` was worse (`noise` then crossed once per block).
- **On Kotlin/JS, `Double.NaN` is the global `NaN`, a tagged value: an `if` with a NaN arm boxes its double arm on
  V8** (engine follow-up 41, 2026-10-16). `x = if (c) value else Double.NaN` (and `SLOT_UNSET`, which is `Double.NaN`)
  compiles to `c ? value : NaN`; V8 keeps the global load as a tagged value, so the two arms merge into a tagged
  value (`Phi[kRepTagged]` after a `ChangeFloat64ToTagged` of the double arm in `--trace-turbo-graph`), and a
  non-integral `value` can become a heap number every block (it did on these paths; a plain-JS probe of the same
  graph allocated about nothing, so the graph alone does not prove a box: measure). Found on the moving delay and reverb paths: the delay writer's gate (`time` or `SLOT_UNSET`), the
  reverb writer's (`normalizeSize(size)` or `SLOT_UNSET`) and the `Reverb.lowpass` setter (`value` or `NaN`), one
  heap number per block each. **Remedy: one store per arm** (`if (c) { x = value } else { x = Double.NaN }`), the
  same values, no merge. With them gone the reverb writer's freed budget left the stdlib `coerceIn` in
  `normalizeSize` out of the writer's `tick` instead (its quotient crossed the call), so that clamp is written out
  too (`clampSvfRange`'s case above): a box can move, so measure again after each fix. `E-kat-gates` 54.5 to 16.3 B
  per block, `E-kat-mix` 65.0 to 33.0 (its two voices' floor), `C-remap` 32.0 to 16.8 (`--predictable`, 1,000,000
  blocks). **How it was found:** the sampling heap profiler books an inlined box in the OUTERMOST optimized
  function (here `KatalystChain.tickModulated`), so it names the compile, not the line; `--trace-turbo-graph
  --trace-turbo-filter=<function>` then shows every `ChangeFloat64ToTagged` in the graph after
  `V8.TFSimplifiedLowering`, and its use (a tagged `Phi`, a call argument) is the site. Grep a new per-block path's
  production bundle for `: NaN;` and `? NaN :` (a function's default-argument prologue, `x === VOID ? NaN : x`, merges
  an argument that is tagged anyway and costs nothing). The sweep of `audio_be` after the fix found no other.
- **On the JVM, many loops inlined into one method compile on one shared profile: give each kind its own method**
  (engine follow-up 35, 2026-10-19). `KatalystArithmeticEffect.process` held every operator's loops (Kotlin `inline`
  `applyLaw`: 4,323 bytes of bytecode, seventeen loops). C2 compiled it six times as the operators arrived, and the
  last compile ran the gliding `div`'s ramp loop about five times slower than a compile that had seen only that row:
  1.61 to 1.94 µs per block after the `times` and `pow` rows in the same JVM, 0.28 to 0.41 alone. In the suite order
  C1 alone ran it at 0.59 and C2 without unrolling (`-XX:LoopUnrollLimit=0`) at 0.63, so the slow code is C2's
  unrolled form of a loop compiled on a profile the other operators had filled first; measured, not explained further
  (no disassembly). **Remedy:** one private method per operator (`plusBlock` to `maxBlock`), each with a profile of
  its own: 0.29 and 0.52 in suite order, the other rows unchanged or faster, the output bit-identical. On V8 the
  methods take no double (the block's ramp travels in a `DoubleArray`, the rules above), and neither the allocation
  check nor the node timing moved. A row timed alone can hide this class: compare it with the suite order
  (`KLANG_BENCH_FILTER` on the one row, then on its group), and look at `-XX:+PrintCompilation` for a large method
  made not entrant again and again.
- **Fast math, `utils/fast_math.kt`**: `fastSin` (degree-11 polynomial on the folded half period, bound
  `FAST_SIN_MAX_ERROR` 1e-10), `fastExp2` (table plus polynomial with exact ends, `fastExp2(n) = 2^n` bit for bit,
  bound `FAST_EXP2_MAX_REL_ERROR` 1e-10) and `fastExp(x) = fastExp2(x * log2 e)` replace the library calls per
  sample in the oscillators, the modulators, the envelopes and the compressor. **Contract: wrap the phase first.**
  `fastSin` is exact only a quarter period past `[0, 2pi)` and diverges beyond, so every accumulator that can step
  a full period per sample wraps per sample through the hoisted `safeWrap = !(abs(inc) < TWO_PI)` (guards:
  `SuperSineOutOfRangePhaseSpec`, `ModulatorPhaseWrapSpec`). Still on the library: `accelerate` (one `pow` per
  block), note-on detune, the generic `pow`/`exp` ignitors, the phaser LFO and the phase-pool selection. No
  `fastLn`: measured under 1 percent of a song.
- **The analog drift steps once per block** and ramps across it; the drift is free now, and its time constants
  follow the block rate (so the pinned block size is part of its sound).
- **Measure a fold's ceiling by deleting the nodes before building it** (the Eq and shaper gain folds were won't
  implement: deleting every level `mul` and `Drive` bought under 4 percent, while oversampling was 63 percent of
  the guitar rig and the polyphase decimator then paid).

### Measuring the engine

- **`./gradlew runSongBenchmark --args=ledger`** renders the six instrument pieces of Der Schmetterling solo on the
  frozen text (`src/jvmMain/kotlin/FrozenPieces.kt`) and on the live text, and appends a row per piece to
  `docs/benchmarks/ledger.md`. Run it after every optimization round: within one piece and one machine only the
  engine moves between rows. The other suites are listed in `src/jvmMain/kotlin/SongBenchmarkMain.kt`.
- **`GraphCensus`** (`ignitor/GraphCensus.kt`, diagnostic, never on the render path) counts what a note asks of
  the engine (passes, traffic, held state). It does not model the build gate: every `classic()` stage counts as
  built, set or not.
- **`KLANG_BENCH_FILTER=guitar-rig`** runs only the `guitar-rig*` rows of `audio_benchmark`'s `IgnitorBenchmark`.
- A timing claim needs interleaved runs and a control row: `audio/ref/verification.md`.
- **`console/full-check.sh`** runs everything before a merge or a release: every test, the corpus check
  (`./gradlew runSongBenchmark --args=corpus`, against `docs/benchmarks/corpus-reference.md`), the timing benchmarks
  and the allocation check, each step in its own build lock, then one summary and exit 0 or 1. `--fresh` makes every
  test task run again.

### Measuring allocations

- **The allocation check** (`KLANG_BENCH_MODE=alloc ./gradlew :audio_benchmark:jvmRun`, after
  `./gradlew :audio_benchmark:compileProductionExecutableKotlinJs --rerun`, without which a bundle older than its
  sources is refused; the `alloc` step of `console/full-check.sh`)
  measures the bytes per block of 39 fixed cases on the JVM (in-process, `ThreadMXBean`) and on V8 (the production
  node bundle, `--trace-gc-nvp`, two processes, the difference), compares them with
  `audio_benchmark/alloc-budgets.txt`, appends two rows to `docs/benchmarks/alloc-scoreboard.md` and exits 1 on a
  breach. How it measures and the cases: `audio_benchmark/README.md`. The cases cover the Ignitor's hot nodes and
  every Katalyst stage family on an orbit: the classic chain's delay, reverb and phaser through its slots, and an
  authored chain for the arithmetic (constant and gliding), the bus distort, the compressor and limiter, the eq, the
  gain, the duck, `parallel`, `bands`, the body and the vowel (`docs/tasks-archive/2026-10/20261011-katalyst-stage-measurements.md`), and the
  knobs that move every block through the chain's own modulators, the `E-kat-*` cases (knob expressions step 6,
  `docs/tasks-archive/2026-10/20261012-katalyst-knob-expressions.md`). Not
  covered: an authored chain at the master position (`MasterBus`; every engine case runs its empty default). The JVM guards in `jvmTest` (`FirstBlockAllocationSpec`,
  `KatalystResonatorAllocationSpec`) stay where they are.
- **What it can see.** On V8 the method's noise (about 25 B per block) is larger than one heap number (16 B on
  node): a budget catches a heap number per sample or per knob read, not one new box per block. The JVM catches a
  single `Double` per block (24 B), or one every other block (its budgets are 6 B). A box usually shows on both,
  but it can be on one only: a V8 box comes from a megamorphic call site or a boxed expression result, a JVM box
  from a failed escape analysis, and the JVM can optimise away a box V8 keeps (review round 1 of the harness).
- **It names the case, not the box.** After an `OVER` line, locate the box by hand, on the case it names:
  1. **V8, which function allocates:** the sampling heap profiler, with the collected objects included (node's
     `--heap-prof` keeps only what is alive at exit, which misses every box). A preload file:

     ```js
     // heap-sample.js: node -r ./heap-sample.js klang-engine-audio_benchmark.js
     const s = new (require('inspector').Session)();
     s.connect();
     s.post('HeapProfiler.enable');
     s.post('HeapProfiler.startSampling', { samplingInterval: 128,
       includeObjectsCollectedByMinorGC: true, includeObjectsCollectedByMajorGC: true });
     process.on('exit', () => s.post('HeapProfiler.stopSampling', (e, { profile }) => {
       const bytes = new Map();
       const walk = (n) => {
         const f = n.callFrame, k = `${f.functionName} ${f.url.split('/').pop()}:${f.lineNumber + 1}`;
         bytes.set(k, (bytes.get(k) || 0) + n.selfSize);
         n.children.forEach(walk);
       };
       walk(profile.head);
       [...bytes].sort((a, b) => b[1] - a[1]).slice(0, 12).forEach(([k, v]) =>
         console.log((v / process.env.KLANG_ALLOC_COUNT).toFixed(1), 'B/block', k));
     }));
     ```

     Run it in `audio_benchmark/build/compileSync/js/main/productionExecutable/kotlin/` with
     `KLANG_ALLOC_CASE=<case> KLANG_ALLOC_WARM=20000 KLANG_ALLOC_COUNT=200000`. The names are the bundle's
     (`protoOf.u2i` is a member); the line leads to the Kotlin through the bundle's source map. The profiler slows the
     run and inflates the bytes: read it for where, not for how much. **Subtract the warm-up:** the preload samples
     the whole process, and the warm-up runs in the interpreter, which boxes every double, so a hot loop shows up as
     an allocating function that allocates nothing once compiled (item 34's first report named `shapeRun`,
     `interpolate44` and `decimate44` this way). Run the case once with `KLANG_ALLOC_COUNT=0`, keep the raw bytes per
     stack, and subtract them from the window's run; what is left is the window's. For both runs print `v` unscaled
     and every stack, not `slice(0, 12)` (with `KLANG_ALLOC_COUNT=0` the scaled value is Infinity). Give both runs the same node
     flags and `--predictable`, so they share one tier-up history; small differences between unpinned runs are
     noise.
  2. **V8, why it boxes:** `node --trace-turbo-inlining` (and `--trace-deopt`) on the same case shows whether the
     callee of the allocating function was inlined. A double that crosses a call that was not inlined becomes a heap
     number: the knob reads of item 8 and the limiter of item 3 were found this way.
  3. **JVM:** a flight recording of the check. `JAVA_TOOL_OPTIONS` reaches the JVM `jvmRun` starts; give it a
     directory, so each JVM (Gradle's client too) writes its own file:

     ```bash
     JAVA_TOOL_OPTIONS="-XX:StartFlightRecording=filename=/tmp/alloc-jfr/,settings=profile" \
       KLANG_BENCH_MODE=alloc ./gradlew :audio_benchmark:jvmRun
     for f in /tmp/alloc-jfr/*.jfr; do
       jfr print --events jdk.ObjectAllocationSample --stack-depth 8 "$f" | grep -A 12 'objectClass = java.lang.Double'
     done
     ```

     The check's own JVM is the file whose stacks are the engine's; read where the `java.lang.Double` (or another
     box) comes from. The warm-ups and voice builds are in the recording too. Tried on a planted box
     (2026-10-10): the stack named the planted line.

## Adjacent rules (linked, not duplicated here)

- **Boxed types are banned** — `Long`, `ULong`, `Byte`, `Short`, `UByte`,
  `UShort`, `Char` are wrapper objects on JS. Use `Int` for frame counts /
  indices and `Double` for time / audio samples. (See
  `.claude/skills/klangaudio-knowhow/SKILL.md` "Critical Rules".)
- **Numerical safety** — `SAFE_MIN`/`SAFE_MAX` clamps are independent of
  perf rules and should not be skipped for speed. See
  `audio/ref/numerical-safety.md`.
- **Cylinder cache locality** — cross-cylinder mix order is a separate (and
  currently un-actioned) optimization. See `audio_be/optimizations.md` for
  the analysis from Gemini-3-Pro and the rationale for deferring.
- **Accepted, and temporary: every bus-door event ships `katalystParams` as
  well as its voice fields** (Katalyst step 5a, 2026-09-18). A song touching
  `reverb` / `delay` / `compressor` / `duck` / `phaser` / `body` / `vowel`
  sends the matching `<stage>.<knob>` slots over the wire AND the fields.
  Since step 5b-1 (2026-09-19) the SLOTS are what every orbit reads and the
  fields are the temporary half. Step 5b-3 (2026-09-19) took the delay,
  reverb, compressor and duck fields off the wire, so for those four doors this
  row's cost is gone; phase 3 step 9 (2026-09-27) took the phaser fields and
  the `filters` list (body and vowel) off too, so every orbit door now ships its
  slots only.
  The rule's one home is the `katalystParam` door's KDoc in
  `sprudel/lang/lang_katalyst.kt`. Measured with
  `WorkletSerializationBenchmark` (`./gradlew :audio_benchmark:jsNodeProductionRun`,
  Node 24) on one voice.

  Re-measured 2026-09-18 after Katalyst step 5a-3, on the fixture as it stands
  (11 slots and the 11 matching bus voice fields, where the step-5a fixture had
  7 slots): encode 822 and 952 ns/op, `structuredClone` 11820 and 11905 ns/op,
  worklet decode 2073 and 2258 ns/op, two consecutive runs on one machine. Two
  runs is enough to show the spread (roughly 15 % on encode and decode, 1 % on
  the clone) and not enough to attribute the difference from the step-5a
  reading (609 / 8720 / 836) to the fixture rather than to the machine: that
  reading was taken on a different day, and the numbers before it (448 / 8097 /
  459, at 36b15ca0) came from a fixture with no slot map at all and are a
  record, not a baseline this can be reproduced against. What the three
  readings agree on is the shape: the clone dominates, and it grows with the
  payload. Step 5b takes the voice half off the wire and leaves the slots
  alone, which pays it back.
- **`SprudelVoiceData.clone()` deep-copies both param maps** (`ignitorParams`,
  `katalystParams`) since 2026-09-18: they are mutable and single-owner, so a
  door writes one key in place instead of allocating a map per slot, and the
  clone owns its own. `VoiceDataCopyBenchmark`
  (`./gradlew :audio_benchmark:jvmRun`) showed no change outside its own
  run-to-run noise, which on this harness is wider than the effect (`copy()`
  on a code path the change never touched read 123 to 257 ns/op across three
  runs).

## When to break the rules

Any rule can be broken with a comment saying *why* — e.g. `shimmer` in
`IgnitorEffects.kt` is currently WIP and intentionally allocates inside
`generate()`. That's fine **as long as it's marked**, so future maintainers
don't blindly follow the pattern. Don't break a rule silently.
