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

**Stateless SAMs are fine** — `mul(constant)`, `bipolar()`, `abs()`,
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
   every match inside `generate()` / `process()` should be either a
   resize-on-shape-change pattern or a guarded "first-call" allocation.
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

### Worked example 3 — Super-saw / super-sine / ...

Before: each `super-X` factory returned a SAM lambda that allocated a
**fresh `DoubleArray(v)`** every block to hold per-voice detune ratios — at
~344 blocks/sec × multiple super-* voices, real GC pressure.

After (`audio_be/.../ignitor/Ignitors.kt::SuperSawIgnitor` and siblings):

- `private var detunes: DoubleArray` field, reallocated **only** when
  voice-count changes (the same condition that resizes `phases`).
- Per-block: `for (n in 0 until v) detunes[n] = ...` writes into the
  persistent buffer.

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

## When to break the rules

Any rule can be broken with a comment saying *why* — e.g. `shimmer` in
`IgnitorEffects.kt` is currently WIP and intentionally allocates inside
`generate()`. That's fine **as long as it's marked**, so future maintainers
don't blindly follow the pattern. Don't break a rule silently.
