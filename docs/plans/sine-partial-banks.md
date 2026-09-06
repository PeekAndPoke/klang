# Sine partial banks: `harmonics`, `octaves` and `fundamental` on `Osc.sine()`

**Status: design settled 2026-09-06, not built.** Three open points in section 7 need a yes before
the build starts (band-limiting is the one that touches a stone rule). Earlier draft of the same
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

| knob                          | adds                                       | gain of an added partial at multiple `m` |
|-------------------------------|--------------------------------------------|-------------------------------------------|
| `harmonics(count, rolloff=1)` | `count` partials at `2f, 3f, 4f ...`       | `m ^ -rolloff`                            |
| `octaves(count, rolloff=1)`   | `count` partials at `2f, 4f, 8f ...`       | `m ^ -rolloff`                            |
| `fundamental(gain=1)`         | nothing; scales the sine's own partial     | `gain`                                    |

- `count = 0` is off and the default. `harmonics(1, 0.5)` adds one partial at 2f with gain 0.707.
- `rolloff = 1` is the sawtooth law (a full series is a band-limited saw), `2` is triangle-soft,
  `0` is flat and buzzy. `rolloff` is the balance between the sine and its overtones, the one knob
  to tune by ear, and it can be an `Osc.param`.
- `fundamental` is a gain, not a switch. `0` drops the sine and leaves the overtones alone on their
  own fader; `0.5` halves it. Decided over a boolean because the fader is what was wanted, `0` is the
  switch for free, levels are 0 to 1 doubles everywhere in the house, and no boolean sits on the wire.
  It is a knob of its own, stated once, rather than a third parameter on each bank, so two banks
  never have to pick a winner.
- Both banks set at once are **summed, no deduplication**: `harmonics(7)` plus `octaves(3)`
  doubles 2f, 4f and 8f, exactly as two hand-written sines would. No special rule.
- With `fundamental = 1` and both counts `0` the node IS today's sine, bit-identical (section 5).
- `Osc.sine(x => x.fundamental(0))` with no bank is silence. Coerced, never an error.

**The door's `freq` decides where the series starts and what it is.** Partials are multiples of
the sine, not of the note:

```javascript
Osc.sine(x => x.harmonics(7))                          // sub plus 2f .. 8f: the current bass stack
Osc.sine(x => x.harmonics(7).fundamental(0))           // overtones only, 2f .. 8f
Osc.sine(Osc.freq().mul(2), x => x.octaves(5)).mul(1/2) // the original grind stack, 2f .. 64f, original levels
Osc.sine(Osc.freq().mul(2), x => x.harmonics(3))       // 2f, 4f, 6f, 8f: the EVEN series (the tube spectrum)
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

Three methods on `OscSineBuilder` in `klangscript-libs/.../IgnitorBuilders.kt`, next to `analog`:

```kotlin
fun OscSineBuilder.harmonics(count: IgnitorDslLike, rolloff: IgnitorDslLike = 1.0): OscSineBuilder
fun OscSineBuilder.octaves(count: IgnitorDslLike, rolloff: IgnitorDslLike = 1.0): OscSineBuilder
fun OscSineBuilder.fundamental(gain: IgnitorDslLike): OscSineBuilder
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

`IgnitorDsl.Sine` in `audio_bridge/src/commonMain/kotlin/IgnitorDsl.kt` grows five fields, all
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
) : IgnitorDsl { collectParams over all seven }
```

No new node kind, no enum, no sealed addition: the distinction is numeric, so fields are the
right shape (`/dsl-design` §7). Existing wire payloads without the fields decode to the defaults
(the codec fills absent fields from the constructor defaults; `IgnitorDslWireCodecSpec` pins it).

Registration points a grown leaf must hit (all found by grepping `IgnitorDsl.Sine`):

- `audio_bridge/.../IgnitorDslWalk.kt`: the children list and the `copy(...)` rebuild, both branches.
- `audio_bridge/src/jsTest/kotlin/IgnitorDslWireCodecSpec.kt`: the `Sine` case with every field non-default, plus a case that decodes a field-less `sine` payload to the defaults.
- The codec itself is generated by `audio-wire-codec-ksp` over the sealed hierarchy; nothing by hand.
- `audio_be/.../WarmupVocabulary.kt`: add a `Sine(harmonics = Constant(7.0), octaves = Constant(2.0))` so the JIT warm-up covers the bank's hot loop.
- `audio_be/.../ignitor/IgnitorDslRuntime.kt`: the `Sine` dispatch (section 5). No engine-side constants are duplicated, so `SuperOscDefaultsSyncSpec` needs no new case.

## 5. Engine

### 5.1 Dispatch: the plain sine stays the plain sine

```kotlin
is IgnitorDsl.Sine ->
    if (isPlainSine()) pitchedSource(freq, Ignitors.sine(freq.noMod(), analog.noMod()))
    else pitchedSource(freq, Ignitors.sinePartials(freq.noMod(), analog.noMod(), fundamental.noMod(),
        harmonics.noMod(), harmonicsRolloff.noMod(), octaves.noMod(), octavesRolloff.noMod(), rng = cache.random))
```

`isPlainSine()` is a structural check on the DSL node: `fundamental == Constant(1.0)`,
`harmonics == Constant(0.0)`, `octaves == Constant(0.0)`. Only literal defaults qualify; a `Param`
or graph on any of the three goes through the bank, because its value can change per block. Every
sine in every existing song therefore builds exactly the `SineIgnitor` it builds today; this is
pinned by a bit-identity spec.

`pitchedSource` gives the bank the accumulated pitch mod (vibrato, `pitchEnvelope`, `detune`)
through `ctx.phaseMod`, as the sines get it today, so the song's `.pitchEnvelope(12, 0.001, 0.02)`
keeps working unchanged.

### 5.2 `PartialBankIgnitor` (phase 1, the deliverable)

One new ignitor in `audio_be/src/commonMain/kotlin/ignitor/Ignitors.kt`. Per block:

1. `resolveFreq` the base frequency; `readParam` the five knobs (block-start values, the `voices`
   pattern in `DetunedStackIgnitor`). Coerce: counts to `0..64` each (the super oscillators' engine
   cap), `fundamental` and both rolloffs as read.
2. `n = 1 + harmonics + octaves`. Grow the per-partial arrays (`phase`, `gain`, `dt`) only when `n`
   exceeds the allocated size; never allocate per block (hot-path rule). New partials start at
   phase 0.
3. Fill the multiples table: partial 0 at `1` with `gain = fundamental`; then `2 .. harmonics+1`
   with `m ^ -harmonicsRolloff`; then `2^k, k = 1 .. octaves` with `m ^ -octavesRolloff`. Gains via
   `exp(-rolloff * ln(m))`: `n` transcendental calls per block, not per sample.
   `dt[k] = m * freq / sampleRate`. Partials whose frequency reaches Nyquist get `gain = 0`
   (decision 1).
4. Drift: a `PolyAnalogDrift(analog, n, sampleRate, rng)` created on first use and re-created on
   growth, per-partial multiplier lanes like the super-oscillator voices. `analog = 0` skips it.

Per sample, one loop over partials, one `sin()` each, summed into the buffer:

```
out = 0
for k in 0 until n:
    out += gain[k] * sin(phase[k] * TWO_PI)
    inc = dt[k] * (pm?.get(i) ?: 1.0) * drift[k]
    phase[k] = (phase[k] + inc).wrap(1.0)
buffer[i] = out
```

Four loop variants as in `SineIgnitor` (phaseMod on/off times drift on/off) so the common
`analog = 0`, no-mod case pays nothing for the branches. Phase in cycles, `wrapPhase` /
`smallNumFastMod` as the sine stack does.

Cost versus the tree, seven added partials: one block pass instead of twenty-one, eight `sin()`
per sample either way. That alone is the bulk of the win.

### 5.3 Phase-locked fast path (phase 2, gated on the benchmark)

When `analog = 0` and `phaseMod == null`, every partial's phase is an exact multiple of one base
phase, so the bank needs **two** transcendental calls per sample regardless of `n`:

- harmonics: `sin((m+1)θ) = 2 cos(θ) sin(mθ) - sin((m-1)θ)` (Chebyshev recurrence, restarted from
  fresh `sin(θ)`, `cos(θ)` every sample, so no accumulated error);
- octaves: `sin(2θ) = 2 sin(θ) cos(θ)`, `cos(2θ) = 2 cos²(θ) - 1`, applied `k` times.

Bit-identity with 5.2 is NOT expected (floating-point order differs); its parity spec compares within
`1e-9`. Build it only if the phase-1 benchmark shows the bank still matters in the song profile
(`audio/MEMORY.md`, song-level benchmark). Otherwise it stays a note here.

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
- `rolloff = 0` gives equal-amplitude partials; `rolloff = 2` gives `1/m²`. Both banks set: sum,
  the doubled partial doubled.
- A `Param`-driven `harmonics` changes the partial count at the next block; shrinking then growing
  does not reallocate (growth-only rule).
- Band limit: a partial at or above Nyquist contributes exactly zero (decision 1).
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
Decide phase 2 (5.3) on that number.

## 7. Decisions

**Decided 2026-09-06 (maintainer, in discussion):**

- Knobs on the sine builder, not separate doors. The sine is always partial 1.
- Counts are partials *added above* the sine; 0 is off and the default.
- `fundamental` is a gain with default 1, its own knob, not a boolean and not a bank parameter.
- Both banks sum without deduplication.
- Sine partials only; no `wave` knob.

**Still open, need a yes:**

1. **Band-limit at Nyquist** (a partial at or above `sampleRate / 2` gets gain 0). A behaviour on an
   audio path, so the Motor-stays-raw rule says ask. Recommended: yes. A sine above Nyquist is not a
   raw sound, it is an alias at an unrelated pitch, and every additive engine band-limits.
2. **No normalisation.** The bank is the raw sum; `harmonics(16)` is louder than `harmonics(8)`,
   and the user scales with `.mul()` as everywhere. Recommended: raw, matching the hand-rolled sound.
3. **Per-partial analog drift** (each partial its own `PolyAnalogDrift` lane), matching the
   hand-rolled stack where each `Osc.sine` drifted on its own. Recommended: yes; it keeps a harmonic
   bank on a saw from sounding like a static EQ boost.

**Settled by the design, recorded so nobody reopens them:**

- Knobs are `Constant` defaults on the data class, not `Slots`. `Slots` exist for pattern-level
  overrides and nothing on the pattern side asks for these yet. Note for later: `Slots.octaves`
  already exists as perlin noise's octave count, so a sine slot could not take that name.
- Sprudel addon shortcuts out of scope; `sound("sine")` with the knobs baked into the ignitor is
  the sprudel path.
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
- **Plucks and samples:** a sine with `harmonics(6, 2)` under a thin pluck for body; sample voices
  with a known note are the case where the pitch-aware bank beats the signal-derived wrapper (no
  intermodulation).

## What we built

Designed together on 2026-09-06 from a hand-rolled stack in the bass and the question "could we
make an operator out of this". Went from two doors to three knobs on the sine, and from a boolean
to a gain, in one afternoon of questions.
