# Sine partial banks: `harmonics`, `octaves` and `fundamental` on `Osc.sine()`

**Status: SHIPPED 2026-09-07, commit `6d4056f9` (C1 to C4, two review rounds, 14 mutation
checks red). Archive record with the closing notes:
`docs/tasks-archive/2026-09/20260907-sine-partial-banks.md`.** Kept in `docs/plans/` rather
than archived, the `filter-unification.md` precedent:
eight files cite this path (KDocs on the wire node, the engine, the builders, the spec, both
module memories). The three open points of section 7 were decided on 2026-09-07: band-limit at
Nyquist (maintainer's explicit yes), raw sum, `analogSpread` default 1. Still open for the
maintainer: the by-ear checks of section 8 and the gated 5.3 fast path. Earlier draft of the same
day proposed two separate doors (`Osc.harmonics()`, `Osc.octaves()`); superseded by the knob form
below, decided in discussion with the maintainer.

## 1. Why this exists

Der Schmetterling's bass hand-rolls additive stacks
(`src/commonMain/kotlin/builtinsongs/DerSchmetterling.kt`). First the octave stack on the grind
saw (sines at 2f, 4f ... 64f, gain one over the multiple), now a harmonic series next to the sub:

```javascript
let sub = Osc.sine().mul(pSub)

let harmonics = Osc.sine(freq = Osc.freq().mul(2)).mul(1/2)
  .add(Osc.sine(freq = Osc.freq().mul(3)).mul(1/3))
  ...
  .add(Osc.sine(freq = Osc.freq().mul(8)).mul(1/8))

return sub.plus(grind).plus(harmonics)
```

The comment next to the grind names the reason: the ear reconstructs a 41 Hz fundamental from
its harmonics on a speaker that cannot play 41 Hz. This is the missing-fundamental trick that
Waves MaxxBass, SRS TruBass and every small Bluetooth speaker's DSP rely on, done here at the
source, where the pitch is known. Harmonics 2 to 8 of the low E span 82 to 328 Hz, exactly the
window those enhancers aim at.

Two things are wrong with the hand-rolled form:

- **Cost.** Seven `Sine` nodes, seven `Times`, seven `Plus`: twenty-one block passes and seven
  `sin()` calls per sample, per voice, plus the node dispatches. A native bank is one block pass.
- **Rigidity.** Count and rolloff are baked into the tree. A native bank reads them as signals
  once per block, like `voices` on the super oscillators, so brightness can be an envelope, an
  LFO or an `Osc.param` ("everything is a signal").

## 2. The model

The partials are **sines**, so the sine is their home. A sine is the one waveform with exactly
one partial: the spectrum written down is the spectrum produced, and the top of the spectrum is
bounded by the highest partial asked for. No other oscillator gets these knobs; the builder type
split is the documentation (`/dsl-design` §2). A `wave` knob was considered and dropped; the
exotic cases (triangle or supersine partials) are a four-line KlangScript `for` loop, which also
makes the stacking explicit.

Every partial is a multiple of **this sine's** frequency. The sine itself is partial 1.

| knob                            | adds                                       | gain of an added partial at multiple `m`  |
|---------------------------------|--------------------------------------------|--------------------------------------------|
| `harmonics(count, rolloff=1)`   | `count` partials at `2f, 3f, 4f ...`       | `m ^ -rolloff`                             |
| `octaves(count, rolloff=1)`     | `count` partials at `2f, 4f, 8f ...`       | `m ^ -rolloff`                             |
| `suboctaves(count, rolloff=1)`  | `count` partials at `f/2, f/4, f/8 ...`    | `m ^ -rolloff` for the partial at `f/m`    |
| `fundamental(gain=1)`           | nothing; scales the sine's own partial     | `gain`                                     |
| `analogSpread(amount=1)`        | nothing; how much the partials drift apart | see below                                  |

One gain law for all three banks: `m ^ -rolloff`, where `m` is the multiple measured away from the
fundamental (the partial at `m * f` above it, the partial at `f / m` below it). `suboctaves(1)` is a
sub at f/2 with gain 1/2; `suboctaves(1, 0)` is the classic equal-level sub oscillator.

- `count = 0` is off and the default. `harmonics(1, 0.5)` adds one partial at 2f with gain 0.707.
- `rolloff = 1` is the sawtooth law (a full series is a band-limited saw), `2` is triangle-soft,
  `0` is flat and buzzy. `rolloff` is the balance between the sine and its overtones, the one knob
  to tune by ear, and it can be an `Osc.param`.
- `fundamental` is a gain, not a switch. `0` drops the sine and leaves the overtones alone on their
  own fader; `0.5` halves it. Decided over a boolean because the fader is what was wanted, `0` is the
  switch for free, levels are 0 to 1 doubles everywhere in the house, and no boolean sits on the wire.
  It is a knob of its own, stated once, rather than a third parameter on each bank, so two banks
  never have to pick a winner.
- `analogSpread` is a blend, not a switch, the same move as `fundamental`. `0`: one shared drift
  lane, every partial follows the same wobble, the spectrum stays exactly harmonic, the bank is one
  slightly unstable physical oscillator. `1`: one independent lane per partial, the beating and
  inharmonicity of the hand-rolled stack. Between: each partial's multiplier mixes the shared lane
  and its own, so the knob is "how much the partials drift against each other", audible as more or
  less beating. The depth stays the sine's `analog`, in cents on every partial; `analog = 0` makes
  the knob moot. Named with the `analog` prefix because the supersaw's `spread` is static unison
  detune, a different thing.
- Several banks set at once are **summed, no deduplication**: `harmonics(7)` plus `octaves(3)`
  doubles 2f, 4f and 8f, exactly as two hand-written sines would. No special rule.
- With `fundamental = 1` and all counts `0` the node IS today's sine, bit-identical (section 5).
- `Osc.sine(x => x.fundamental(0))` with no bank is silence. Coerced, never an error.
- **A sub-octave moves the perceived pitch.** The ear takes f/2 as the fundamental as soon as it is
  there at a comparable level, because f is its second harmonic; that is the missing-fundamental
  effect run backwards, and it is the point of a sub oscillator (weight) but it must be in the
  KDoc. Downward partials never alias, but `suboctaves(2)` on the low E is 10 Hz: real infrasound
  at full gain, headroom and woofer travel for nothing audible. The Motor stays raw (no low cut);
  the user's highpass exists for that.
- **Sub-harmonics (f/2, f/3, f/4 ...) are WON'T IMPLEMENT.** The undertone series is a downward
  minor chord (f/3 is an octave and a fifth below), so it changes the harmony, not the timbre; no
  synth, pedal or enhancer offers it beyond the sub-octave (dbx 120XP, Lowender, Logic SubBass and
  the Octaver family all go down in octaves; the Hammond 5 1/3' drawbar is 3f/2, not f/3). A bank
  that rewrites the chord is a family of models (`/dsl-design` §9). `Osc.sine(Osc.freq().div(3))`
  is the honest spelling for the one song that wants a twelfth below.

**The door's `freq` decides where the series starts and what it is.** Partials are multiples of
the sine, not of the note:

```javascript
Osc.sine(x => x.harmonics(7))                          // sub plus 2f .. 8f: the current bass stack
Osc.sine(x => x.harmonics(7).fundamental(0))           // overtones only, 2f .. 8f
Osc.sine(Osc.freq().mul(2), x => x.octaves(5)).mul(1/2) // the original grind stack, 2f .. 64f, original levels
Osc.sine(Osc.freq().mul(2), x => x.harmonics(3))       // 2f, 4f, 6f, 8f: the EVEN series (the tube spectrum)
Osc.sine(x => x.suboctaves(1, 0))                      // the classic sub oscillator: f and f/2 at equal level
Osc.sine(x => x.harmonics(7).analog(3).analogSpread(0)) // drifts as ONE oscillator, spectrum stays harmonic
Osc.sine(x => x.harmonics(12, Osc.param("rolloff", 1))) // brightness from the pattern
Osc.sine(x => x.harmonics(8, Osc.sine(0.2).range(0.7, 2))) // breathing brightness, control rate
```

The even-series line goes into the door's KDoc verbatim; it is the one surprise a reader can hit.

**Musically.** `harmonics` on a bass is fundamental reconstruction (82 to 328 Hz on the low E).
`octaves` climbs an octave per partial, six of them reach 2.6 kHz; it is a brightness and grind
device. Adding a harmonic series to a saw is nearly redundant (the saw already carries every
partial at one over n; it doubles each and adds the beating of per-partial drift). The knobs earn
their keep on sine, pluck and sample-based voices, and as a standalone additive instrument.

## 3. Surface (both doors, identical)

Five methods on `OscSineBuilder` in `klangscript-libs/.../IgnitorBuilders.kt`, next to `analog`:

```kotlin
fun OscSineBuilder.harmonics(count: IgnitorDslLike, rolloff: IgnitorDslLike = 1.0): OscSineBuilder
fun OscSineBuilder.octaves(count: IgnitorDslLike, rolloff: IgnitorDslLike = 1.0): OscSineBuilder
fun OscSineBuilder.suboctaves(count: IgnitorDslLike, rolloff: IgnitorDslLike = 1.0): OscSineBuilder
fun OscSineBuilder.fundamental(gain: IgnitorDslLike): OscSineBuilder
fun OscSineBuilder.analogSpread(amount: IgnitorDslLike): OscSineBuilder
```

`copy`-based, immutable (`/dsl-design` §1). The script door is the same function through KSP;
`rolloff` is a safe literal default, so a named call that skips it works (`/dsl-design` §3
guardrail). `klangscript-libs` IS the Kotlin door for the builders; the raw Kotlin form is the
data class:

```kotlin
IgnitorDsl.Sine(harmonics = IgnitorDsl.Constant(7.0))
OscSineBuilder(IgnitorDsl.Sine()).harmonics(7.0, 0.5).fundamental(0.0).node
```

Door-parity spec in the pattern of `KlangScriptSuperSineSpec`: script == Kotlin for every knob,
`freq` first positional, a knob accepting an `Osc` graph.

Nothing changes for sprudel: `sound("sine")` reaches the node; the knobs are not `Slots`
(section 7). Sprudel addon shortcuts are out of scope.

## 4. Wire

`IgnitorDsl.Sine` in `audio_bridge/src/commonMain/kotlin/IgnitorDsl.kt` grows eight fields, all
with constant defaults that mean "plain sine":

```kotlin
@WireName("sine")
data class Sine(
    val freq: IgnitorDsl = Freq,
    val analog: IgnitorDsl = Slots.analog,
    val fundamental: IgnitorDsl = Constant(1.0),
    val harmonics: IgnitorDsl = Constant(0.0),
    val harmonicsRolloff: IgnitorDsl = Constant(1.0),
    val octaves: IgnitorDsl = Constant(0.0),
    val octavesRolloff: IgnitorDsl = Constant(1.0),
    val suboctaves: IgnitorDsl = Constant(0.0),
    val suboctavesRolloff: IgnitorDsl = Constant(1.0),
    val analogSpread: IgnitorDsl = Constant(1.0),
) : IgnitorDsl { collectParams over all ten }
```

No new node kind, no enum, no sealed addition: the distinction is numeric, so fields are the
right shape (`/dsl-design` §7). The generated codec passes every field through as written; the
main bundle and the worklet bundle ship together, so a payload always carries the fields its
decoder expects (a stale cached worklet meeting a fresh main bundle is the one mismatch, the same
as for every wire field ever added).

Registration points a grown leaf must hit (all found by grepping `IgnitorDsl.Sine`):

- `audio_bridge/.../IgnitorDslWalk.kt`: the children list and the `copy(...)` rebuild, both branches.
- `audio_bridge/src/jsTest/kotlin/IgnitorDslWireCodecSpec.kt`: a `Sine` case with every field non-default.
- The codec itself is generated by `audio-wire-codec-ksp` over the sealed hierarchy; nothing by hand.
- `audio_be/.../WarmupVocabulary.kt`: add a `Sine(harmonics = Constant(7.0), octaves = Constant(2.0))` so the JIT warm-up covers the bank's hot loop.
- `audio_be/.../ignitor/IgnitorDslRuntime.kt`: the `Sine` dispatch (section 5). No engine-side constants are duplicated, so `SuperOscDefaultsSyncSpec` needs no new case.

## 5. Engine

### 5.1 Dispatch: the plain sine stays the plain sine

```kotlin
is IgnitorDsl.Sine ->
    if (isPlainSine()) pitchedSource(freq, Ignitors.sine(freq.noMod(), analog.noMod()))
    else pitchedSource(freq, Ignitors.sinePartials(freq.noMod(), analog.noMod(), fundamental.noMod(),
        harmonics.noMod(), harmonicsRolloff.noMod(), octaves.noMod(), octavesRolloff.noMod(),
        suboctaves.noMod(), suboctavesRolloff.noMod(), analogSpread.noMod(), rng = cache.random))
```

`isPlainSine()` is a structural check on the DSL node: `fundamental == Constant(1.0)` and all
three counts `== Constant(0.0)`. `analogSpread` does not take part: with one partial it has nothing
to spread. Only literal defaults qualify; a `Param`
or graph on any of the three goes through the bank, because its value can change per block. Every
sine in every existing song therefore builds exactly the `SineIgnitor` it builds today; this is
pinned by a bit-identity spec.

`pitchedSource` gives the bank the accumulated pitch mod (vibrato, `pitchEnvelope`, `detune`)
through `ctx.phaseMod`, as the sines get it today, so the song's `.pitchEnvelope(12, 0.001, 0.02)`
keeps working unchanged.

### 5.2 `PartialBankIgnitor` (phase 1, the deliverable; as built)

One new ignitor in `audio_be/src/commonMain/kotlin/ignitor/Ignitors.kt`. Per block:

1. `resolveFreq` the base frequency; `readParam` the eight knobs (block-start values, the `voices`
   pattern in `DetunedStackIgnitor`). Coerce: counts to `0..64` each (NaN reads as 0),
   `analogSpread` to `0..1`, `fundamental` and the rolloffs as read.
2. **Each bank owns its state** (round-1 review, MAJOR): the fundamental is a scalar phase, and
   `harmonics`, `octaves`, `suboctaves` are three `Bank` objects with growth-only `phase`, `gain`,
   `inc` arrays and their own drift lanes. A count change in one bank never re-indexes another's
   partials; surviving partials keep their phase; a partial that (re)activates starts at phase 0,
   a zero crossing, so adds are step-free. Allocation happens only on growth.
3. Fill each bank's table: harmonics at `(2 + i) * f`, octaves at `2^(i+1) * f`, sub-octaves at
   `f / 2^(i+1)`, gain `m ^ -rolloff` via `pow` (0 when it overflows; NaN-guarded), `n`
   transcendental calls per block, not per sample. A partial at or above Nyquist, judged at block
   start on the base pitch, gets gain 0 (decision 1); no lower limit.
4. Drift: `analog` is latched at the first block like the plain sine (a NaN read latches as
   inactive). One shared `AnalogDrift` lane, one lane for the fundamental, one per bank partial,
   created lazily in that order from `ctx.random` so a seeded voice renders reproducibly. Per
   partial the multiplier is `1 + sqrt(1 - s) * (shared - 1) + sqrt(s) * (own - 1)` with
   `s = analogSpread`: constant-power weights, so two independent walks sum to the same depth as
   one and the bank drifts by `analog` at every setting (round-1 review; linear weights would have
   made `s = 0.5` drift at 0.71 x `analog`). At `s = 1` exactly the shared lane is not advanced (the
   hand-rolled sound, every `Osc.sine` in the stack owning its drift); at `s = 0` exactly only the
   shared lane is advanced, one walk for the whole bank. The shared walk is sampled into a
   growth-only scratch array once per block so every partial's loop reads the same sequence.
   `analog = 0` skips all of it. Since 2026-09-10 this machinery is not the bank's own: it lives in
   `DriftLanes`, and the super oscillators carry the same `analogSpread` knob over their voices
   (`docs/tasks-archive/2026-09/20260910-drift-lanes-analog-spread.md`). The draw order moved with
   it: one int for the shared lane's SEED comes off the voice rng at the first block, whatever the
   spread, then the own lanes in index order. The shared lane is still built lazily, at the first
   block below spread 1, but from that seed, so it costs no voice draw and WHEN it appears cannot
   shift what anything else in the voice draws.

Rendering is **partial-major**: one tight loop per partial with the phase in a local (radian
phase, `sin(phase)`, `wrapPhase(TWO_PI)`, the plain sine's own loop), the first audible partial
writes the buffer and the rest add to it; a fully silent bank writes zeros. Silent partials
(Nyquist, `fundamental(0)`) are skipped, phase frozen.

**Measured (C4, JVM, 2026-09-07, `docs/benchmarks/2026-09-07_sine-partial-banks_jvm.md`):** the loop
SHAPE decided the result, not the block-pass count. A first, sample-major version (one loop over
samples, an inner loop over partials reading and writing phase arrays) rendered `harmonics(7)` at
41 µs per block against 24 µs for the hand-rolled tree: slower than what it replaced. Rewritten
partial-major it is 22 µs against the tree's 24 µs, and `octaves(6)` is 17 µs (all three about
double the true cost: the harness rendered every voice twice per block, fixed the same day; the
ratios hold). `sin()` dominates
either way, so the native bank is only a little cheaper than the tree per partial; its value is the
knobs as signals, the growth-only arrays, and one node instead of three per partial. The real
speed-up is 5.3.

### 5.3 Phase-locked fast path (phase 2, gated on the benchmark)

When `analog = 0` and `phaseMod == null`, every partial's phase is an exact multiple of one base
phase, so the bank needs **two** transcendental calls per sample regardless of `n`:

- harmonics: `sin((m+1)θ) = 2 cos(θ) sin(mθ) - sin((m-1)θ)` (Chebyshev recurrence, restarted from
  fresh `sin(θ)`, `cos(θ)` every sample, so no accumulated error);
- octaves: `sin(2θ) = 2 sin(θ) cos(θ)`, `cos(2θ) = 2 cos²(θ) - 1`, applied `k` times.

Bit-identity with 5.2 is NOT expected (floating-point order differs); its parity spec compares within
`1e-9`. With the C4 numbers (eight `sin()` per sample cost about 22 µs per block, two would cost about
6) this is the path that would make a bank materially cheaper than a plain sine stack. Still gated:
build it when a song profile shows sine banks in the top rows, not before.

## 6. Work chunks, in order

Each chunk is reviewed (`/review-loop`), mutation-checked at the mandatory tier (engine, wire,
KSP), and committed on the working branch when clean.

**C1 Wire and engine.** The five `Sine` fields, walk, codec spec, warm-up vocabulary,
`PartialBankIgnitor`, the dispatch split. Specs in `audio_be/src/commonTest/kotlin/ignitor/`:

- **Plain-sine identity:** `Sine()` with defaults builds the same class as before and renders
  bit-identically to the pre-change `SineIgnitor` (pin the class with a `shouldBeInstanceOf`, the
  output against `Ignitors.sine()`).
- `Sine(harmonics = 2)` equals the sum of three `sine()` ignitors at `f`, `2f`, `3f` scaled
  `1, 1/2, 1/3`, within `1e-12` (same phase order, so this is tight).
- **The golden test:** `Sine(harmonics = 7)` built through `IgnitorDslRuntime` equals the current
  Der Schmetterling `sub.plus(harmonics)` tree (sub at gain 1) built the same way, `analog = 0`,
  within `1e-12`. The proof that the song migration does not change the sound.
- `fundamental = 0` renders the overtones only: equals the golden tree minus `Ignitors.sine()`.
- `octaves = 5` at `freq = 2f` scaled by `1/2` equals the original six-sine grind stack.
- `Sine(suboctaves = 1)` equals `Ignitors.sine()` at `f` plus `Ignitors.sine()` at `f/2` scaled
  `1/2`, within `1e-12`; `suboctaves = 1, suboctavesRolloff = 0` gives the sub at gain 1.
- `rolloff = 0` gives equal-amplitude partials; `rolloff = 2` gives `1/m²`. Several banks set:
  sum, the doubled partial doubled.
- A `Param`-driven `harmonics` changes the partial count at the next block; shrinking then growing
  does not reallocate (growth-only rule).
- Band limit: a partial at or above Nyquist contributes exactly zero (decision 1).
- Drift lanes: with `analog > 0` and `analogSpread = 0`, the ratio between any two partials'
  instantaneous frequencies stays exactly their multiple ratio (zero-crossing counts over a long
  render); with `analogSpread = 1` and a seeded RNG, partial k equals a standalone `sine()` at
  `k * f` driven by the same seed sequence; a mid value lands between the two (a mutation that
  drops the shared term must fail this).
- Frequency of each partial by zero-crossing count, the `IgnitorsTest` sine pattern.
- `pitchEnvelope` over the bank moves every partial (a `phaseMod` case).

**C2 Both doors.** The three builder methods, door-parity spec (`KlangScriptSineSpec`, pattern of
`KlangScriptSuperSineSpec`), `StdLibOscTest` cases, the KDoc on `Osc.sine` listing the new knobs
and carrying the even-series line.

**C3 Song and docs.** Der Schmetterling: the seven-sine stack becomes
`Osc.sine(x => x.harmonics(7).fundamental(0)).mul(pHarm)` next to the untouched `sub` (keeps the
independent faders the file has now), verified by the golden test and by ear. Docs: the oscillator
table in `.claude/skills/klang-music-writing/ref/ignitor-reference.md`; the stdlib docs page picks
the KDoc up through `generatedStdlibDocs`. `sprudel/MEMORY.md` and `audio/MEMORY.md` status lines.

**C4 Benchmark.** `IgnitorBenchmark` cases `sine-harmonics7`, `sine-octaves6`, and
`sine-harmonics7-tree` (the hand-rolled equivalent) so the win is a number in `docs/benchmarks/`.
Done 2026-09-07; numbers in 5.2, the lesson (loop shape) in `audio/MEMORY.md`. Phase 2 stays gated.

## 7. Decisions

**Decided 2026-09-06 (maintainer, in discussion):**

- Knobs on the sine builder, not separate doors. The sine is always partial 1.
- Counts are partials *added above* the sine; 0 is off and the default.
- `fundamental` is a gain with default 1, its own knob, not a boolean and not a bank parameter.
- Banks sum without deduplication.
- Sine partials only; no `wave` knob.
- Downward: `suboctaves` in, with the mirrored gain law; sub-harmonics (f/3 and beyond) are
  won't-implement (section 2 has the reasoning).
- Drift lanes are configurable through `analogSpread`, a 0 to 1 blend from one shared lane to
  one lane per partial (2026-09-07). The `analog` depth stays one value for the whole sine.

**Still open, need a yes:**

1. **Band-limit at Nyquist** (a partial at or above `sampleRate / 2` gets gain 0). A behaviour on an
   audio path, so the Motor-stays-raw rule says ask. Recommended: yes. A sine above Nyquist is not a
   raw sound, it is an alias at an unrelated pitch, and every additive engine band-limits.
2. **No normalisation.** The bank is the raw sum; `harmonics(16)` is louder than `harmonics(8)`,
   and the user scales with `.mul()` as everywhere. Recommended: raw, matching the hand-rolled sound.
3. **Default of `analogSpread`.** Recommended: 1, independent lanes, the sound already tuned in the
   song and the reason a harmonic bank on a saw does not sound like a static EQ boost. 0 would make
   "a sine with harmonics" behave like one physical oscillator by default. Either is defensible;
   pick by ear on the bass with `analog` turned up.

**Settled by the design, recorded so nobody reopens them:**

- Knobs are `Constant` defaults on the data class, not `Slots`. `Slots` exist for pattern-level
  overrides and nothing on the pattern side asks for these yet. Note for later: `Slots.octaves`
  already exists as perlin noise's octave count, so a sine slot could not take that name.
- Sprudel addon shortcuts out of scope; `sound("sine")` with the knobs baked into the ignitor is
  the sprudel path.
- RNG draw order (round-1 and round-2 review, accepted): the bank draws from the voice RNG only
  when `analog > 0` (shared lane, fundamental lane, then partial lanes), while the plain sine always
  builds one `AnalogDrift` (three draws) even at `analog = 0`. A random consumer later in the same
  voice graph (a supersaw next to a migrated stack) therefore sees a shifted sequence compared to
  the hand-rolled tree. Not a reproducibility bug: the render is still a pure function of (DSL,
  seed); it is a different function than the tree's. Do not chase it.
- Count changes (round-2 review, accepted as documented behaviour): adds are step-free (a
  (re)activated partial starts at phase 0), removals step by up to the partial's gain. Automate
  `rolloff`, not `count`, for smooth motion; a zero-crossing hold on removal is the engine fix if
  automation-grade counts are ever wanted.
- Phase 2 fast path only on benchmark evidence (5.3).
- The signal-derived wrapper is a separate task. The MaxxBass-style operator that works on any
  signal without knowing its pitch (`self.add(self.lowpass(f).distort("tube").bandpass(...))`,
  expressible today as a tree, the runtime memoises the shared input) needs a by-ear session for
  its band edge and shape before it is a door. It gets its own `docs/tasks/` file, not this plan.

## 8. Candidate uses and by-ear checks after C3

- **Bass, Der Schmetterling:** the migrated harmonic stack (unchanged sound), then tune `rolloff`
  as a param against `pSub` and `pGrind`; try `octaves(5)` on a 2f sine against the grind saw.
- **Guitars, Der Schmetterling:** no bank. The guitar is a 25-voice supersaw through two
  distortion stages and already carries a complete harmonic series. The trick it CAN use is the same
  missing-fundamental effect from the other side: raise `hptrack` from 1 toward 2 and let the
  saw's own harmonics carry the pitch. That clears the band under the guitars for the bass without
  thinning them. By-ear check, no operator needed.
- **Sub-octaves:** not for this bass (the sub already sits at 41 Hz; f/2 would be 20 Hz). The
  candidate is the lead, a sine an octave under the supersquare with `fundamental(0).suboctaves(1, 0)`
  for weight without a second voice; by ear, against the `hpf(2000)` already on it.
- **Plucks and samples:** a sine with `harmonics(6, 2)` under a thin pluck for body; sample voices
  with a known note are the case where the pitch-aware bank beats the signal-derived wrapper (no
  intermodulation).

## What we built

Designed together on 2026-09-06 from a hand-rolled stack in the bass and the question "could we
make an operator out of this". Went from two doors to five knobs on the sine, from a boolean to a
gain, drew the line at sub-harmonics, and made the drift lanes a blend, over two days of questions.
