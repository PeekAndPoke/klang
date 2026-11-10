# Layer 5: the Ignitor build

**What I read**

- In full: `audio_be/.../ignitor/IgnitorDslRuntime.kt`, `Ignitor.kt`, `_arithmetic_laws.kt`, `BuiltIgnitor.kt`,
  `IgniteContext.kt`, `MemoizingIgnitor.kt`, `ModApplyingIgnitor.kt`, `ModBlockingIgnitor.kt`, `CarrierFreqMod.kt`,
  `ParamIgnitor.kt`, `ConstantIgnitor.kt`, `FreqIgnitor.kt`, `NoteClockIgnitors.kt`, `PhaseOffset.kt`,
  `ScratchBuffers.kt`, `OversampleRegion.kt`, `IgnitorRegistry.kt`, `EqIgnitor.kt` (the build side),
  `audio_be/.../voices/VoiceFactory.kt`, `audio_bridge/.../IgnitorDslOptimizer.kt`, `IgnitorDslWalk.kt`,
  `IgnitorDslIdentity.kt`, `WireShared.kt`, `klang/.../AnnounceOnceRegistry.kt`, `InlineDslRegistrar.kt`.
- In part: `IgnitorDslClassic.kt` (the slot groups, `classic()`, `endsInClassic`), `IgnitorDsl.kt` (the header, `Param`,
  `Slots`, `Variants`, `OptimizerHint`, `Affine`, the filter doors, `fillFilterEnvelope`), `Ignitors.kt` (only
  `readParam`, the drift sizing helpers, `WaveIgnitor.generate`'s head, the sine's head), `IgnitorEnvelopes.kt`
  (`AdsrIgnitor`), `IgnitorEffects.kt` (`DriveIgnitor`, `phaseTwins`, `delayedBy`, the shimmer's constructor),
  `PitchModFactories.kt` (`vibratoModIgnitor`), `IgnitorFilters.kt` (the cutoff read of `SvfIgnitor`),
  `Oversampler.upsample`, `ResourceWarehouse.kt` (the scratch pool), `EqCore.configureSection`, `clampSvfCutoff`.
- Docs: `audio/CLAUDE.md`, `audio/MEMORY.md` (all but the History), `audio/ref/off-values.md` (all),
  `.claude/skills/review-loop/audio-constraints.md`, KLANG-00420 (all), KLANG-00204 (all), KLANG-00210 section 11.
  `audio/ref/voice-synthesis.md`: the processing order and the lifetime section only. `audio/ref/performance.md`: rule 2
  only. `audio/ref/data-model.md`: the `soundIndex` and slot rows only.
- Tests: `IgnitorDslOptimizerFuzzSpec` in full; `IgnitorDslOptimizerRenderSpec` (the helper, the row titles, about a
  third of the rows); the other specs by their row titles and by grep (`IgnitorGateSpec`, `MemoizingIgnitorSpec`,
  `OversampleRegionSpec`, `IgnitorTailSpec`, `InlineDslRegistrarTest`).
- Tables: `stage-cost` (text and json), `stage-cost-js` (it appeared while I read; five rows used), `hot-path-lint.txt`,
  `dead-knobs.txt`, `audio_benchmark/alloc-budgets.txt`.

**What I did not**

- The DSP of every node (`Ignitors.kt` beyond the parts named, `IgnitorFilters.kt`, `PhasePool.kt`, `DriftLanes.kt`,
  `KarplusString.kt`, the shimmer's loop): other layers.
- `GraphCensus.kt` beyond its class KDoc and its callers (it is benchmark-only; one note below).
- `IgnitorBuilders.kt`, `Ign.kt`, `EffectBuilders.kt` beyond the filter knobs; the KlangScript doors.
- The wire codec generator; `WireCodecSupport.kt` only at the identity table.
- Nothing was rendered or run. Every finding below is traced by reading; where a number matters I say where it comes
  from. `aliasing`, `clicks`, `framing`, `stage-probe` tables: only the leads in KLANG-00420, not the tables.

## The probes' leads in this layer

| Lead | Verdict | Cause |
|------|---------|-------|
| Lint `Ignitors.kt:923` (`AnalogDrift()` in `WaveIgnitor.generate`) | CONFIRMED, BY DESIGN | The lane is built with the node when `mayDrift(analog)` says so (`Ignitors.kt:2928`), which reads the knob at freq 0 (`sizingValueOrNull`, `:2922`). Only an `analog` written over `Freq` (0 at freq 0, above 0 at the note) reaches the fallback: one object per note at the first block. The KDoc at `:2913` to `:2921` says so. |
| Lint `Ignitors.kt:2950` (`DriftLanes(...)` in `startDriftLanes`) | CONFIRMED, BY DESIGN | The same guess for the stacks, same reach, same KDoc. |
| Lint `OversampleRegion.kt:158` (`IgniteContext(...)` in `generate`) | CONFIRMED | Every region of every voice makes its child context at its first rendered block, always, not on a wrong guess. Finding L5-09. |
| Lint `ScratchBuffers.kt:213` (`ScratchBuffers(...)` in `addOversample`) | CONFIRMED, BY DESIGN (documented) | The warehouse warms 2, 4, 8, 16 on the root pool only (`ResourceWarehouse.kt:145` to `:151`). A sub-pool of a sub-pool (a region nested in a region, a phase twin inside a region) and a sub-pool deeper than 4 leases grow in render, once per engine (`ResourceWarehouse.kt:197` to `:202` says so). Two comments beside it are stale: L5-12. |
| Lint `ScratchBuffers.kt:83`, `:125` (`acquire` growth) | CONFIRMED for region sub-pools | A sub-pool starts with 4 buffers. A region takes 2 (`OversampleRegion.kt:173`, `:179`) and its whole body renders on the same sub-pool, so a body that nests more than 2 scratch leases grows it in render, once per engine. Counted by `lateAllocations` of the SUB-pool, which `WarehouseStats` does not read (`ResourceWarehouse.kt:117` reads the root's). |
| Lint `MemoizingIgnitor.kt:130` (cache growth in `generate`) | DISPROVED for voices | The cache is reserved at build at the block size of the scope the entry lives in (`onShared`, `combineMods`, `tremoloGainInRange`), and a hit means the same scope. A root memo is never shared and the hand-over bypasses it (`VoiceFactory.kt:356`). Only a test rig's larger buffer grows it, as its comment says. |
| Dead knobs: `rate`, `octaves`, `persistence`, `bipolar`, `declick` | CONFIRMED, BY DESIGN | Placed as defaults of the noise doors and of `Adsr.declick` (`IgnitorDsl.kt:695` to `:728`, `:2060`); no sprudel door writes them; a raw `ignp` reaches every `Param` of that name in the tree. |
| Dead knobs: `divisor`, `eqhz`, `eqq`, `eqdb`, `eqlp`; `warm`, `offset` | BY DESIGN | Inline `Param`s of the demo sounds `sgpad` and `eqdemo` (`IgnitorDefaults.kt:196`, `:236` to `:241`) and of the warm-up vocabulary. |
| Stage cost: "`classic()` all off costs 0 to 0.2 ns" | CONFIRMED per block; the BUILD is not free | The gate removes every off stage from the render. The build of the off tail costs about 10 us per note (`build_us` 12.3 against 2.2 for the bare leaf). Finding L5-04. |
| Stage cost 2: vibrato adds 8.5 ns | CONFIRMED in part (mine: two copies) | Five passes over the block before the source: the LFO, the depth multiply, the `2^x` loop, the pitch memo's copy out (`combineMods` caches for a single reader too, `IgnitorDslRuntime.kt:643`), and `ModApplyingIgnitor`'s copy of the mod into `ratioArray` (`ModApplyingIgnitor.kt:39` to `:42`). The last two are pure copies; see the performance table. The rest is layer 4. |
| Stage cost 3: adsr costs 4.1 to 4.5 ns at 0/0/1/0 | NOT MINE, and not a gate case | At 0/0/1/0 the envelope still closes the note at the gate, so the build cannot fold it; only `adsr.on = 0` removes it (`switchedOff`, `IgnitorDslRuntime.kt:1171`). A sustain fast path is layer 4's. |
| Clicks 5: `adsr.on=0` fades the end, not the start | BY DESIGN on the build side | Off, the envelope is not built (`IgnitorDslRuntime.kt:1886` to `:1889`) and the voice appends only the teardown fade at its end (`VoiceFactory.kt:316`). Nothing in the build or the voice fades an onset. |
| Clicks 6 and 8: a kink at the last frame of a default-adsr note; the 3.3e-3 KDoc | NOT MINE, with one build fact | `endsInEnvelope` is true for `classic()`, so no teardown fade runs, and the lifetime is exactly gate plus release (`treeLifetime`), while the 1 ms de-click one-pole lags the release. With the exponential release (k = 3) the end slope is about 0.157 / release, so the gain left at the last frame is about 1 ms x 0.157 / 50 ms = 3.1e-3 of the sustain level. The KDoc's number is right by this estimate; whether a probe sees it depends on the signal's value at that frame. See "Decisions worth revisiting". |
| Framing 1 (filter envelope), aliasing, stage probe 1 to 13 | NOT MINE | |

## Findings

### L5-01 A fused filter loses its gate: an unset cutoff plays a 1 kHz filter

- **Severity**: major
- **Tag**: bug
- **Where**: `audio_bridge/src/commonMain/kotlin/IgnitorDslOptimizer.kt:342` to `:382` (`asFusibleSections`), `:246`;
  `audio_be/src/commonMain/kotlin/ignitor/IgnitorDslRuntime.kt:1754`, `:1788`, `:1826`, `:1838` (the gate rows),
  `:1855` to `:1868` (the `Eq` arm, no gate); `audio_be/.../ignitor/EqIgnitor.kt:131`;
  `audio_be/.../filters/LowPassHighPassFilters.kt:143`.
- **What**: the gate row "the four SVF filters: not built when the cutoff is unset" lives in the `Lowpass`, `Highpass`,
  `Bandpass` and `Notch` arms. The optimizer turns such a node into an `Eq` section whenever `analog` and `env` are
  literal zeros, `passes` is a constant and `humanize` is false, which is exactly what the plain doors build
  (`FilterKnobs` defaults, `fillFilterEnvelope` returns `Constant(0.0)` for an unnamed envelope). It does not look at
  the cutoff. The `Eq` arm has no gate. Input: `Ignitor.sine().lowpass(Ignitor.slot.lpf.freq)`, the example in the
  `Slots` KDoc (`IgnitorDsl.kt:244`), played by a note that writes no `lpf`. Steps: `register` optimizes; the tree is
  `Eq(Sine, [Lowpass(freq = Param("lpf.freq", NaN), q = 0.707)])`; at the build the `Param` leaf resolves to NaN (no
  finite override, the default is the sentinel); `EqIgnitor` reads NaN and calls `configureSection`;
  `clampSvfCutoff(NaN)` answers 1000.0. Result: a 1 kHz lowpass on every such note. Expected by `off-values.md` and
  by the unoptimized build: no filter, the bare sine. With `hpf.freq` it is a 1 kHz highpass (the bass is gone), with
  `bpf.freq` a 1 kHz bandpass. The same instrument with `.optimizer(0)` plays the bare sine, so the by-ear A/B hears a
  difference that is not rounding, and `OPTIMIZER_PARITY` is broken for this shape. A literal non-finite cutoff
  (`lowpass(Constant(NaN))`) takes the same path. `classic()` is not affected: its filters carry `env` and `analog`
  slots and never fuse.
- **Reach**: an authored instrument that places a filter slot with an unset default (`Ignitor.slot.lpf.freq`,
  `hpf.freq`, `bpf.freq`, `notch.freq`, or its own `Ignitor.param(name, NaN)`) on a plain `lowpass` / `highpass` /
  `bandpass` / `notch` door. No corpus song does (grep for `Ignitor.slot.lpf` in `builtinsongs` is empty).
- **Settle it**: one row in `IgnitorDslOptimizerRenderSpec`:
  `assertOptimizeIsInaudible(IgnitorDsl.Sine().lowpass(IgnitorDsl.Slots.lpf.freq))` with no params. It should be red
  today. The fuzz cannot find it: its filter cutoffs are finite constants or `Param("cut", 1000.0)`
  (`IgnitorDslOptimizerFuzzSpec.kt:147` to `:151`).
- **Confidence**: high (traced end to end, not rendered).

### L5-02 The inline instrument's name is structural, the build shares by identity: an edit that only changes the sharing is not heard

- **Severity**: major
- **Tag**: bug
- **Where**: `audio_bridge/src/commonMain/kotlin/IgnitorDslIdentity.kt:24`, `:36`;
  `klang/src/commonMain/kotlin/AnnounceOnceRegistry.kt:52`; `klang/src/commonMain/kotlin/InlineDslRegistrar.kt:50`;
  the build cache `IgnitorDslRuntime.kt:200` to `:215`; pinned as intended by
  `klang/src/jvmTest/kotlin/InlineDslRegistrarTest.kt:77`, `:120`.
- **What**: `uniqueId()` keys a process-wide map by the data classes' `equals`, so two trees that are equal node for
  node get one name, and the per-playback registrar announces a name once. The engine's meaning of a tree depends on
  which nodes are the SAME instance: `let p = Ignitor.pluck(); p.add(p.lowpass(800))` builds one string read twice,
  `Ignitor.pluck().add(Ignitor.pluck().lowpass(800))` builds two strings with their own noise bursts (the build cache
  keys by `===`; `@WireShared` exists to keep exactly this across the wire; `MEMORY.md`: "Equal but distinct nodes
  stay two"). Both trees are structurally equal (`Pluck` has no `uid`; only the three coloured noises carry one,
  `IgnitorDsl.kt:558`, `:675`, `:687`). Steps: the first spelling plays, gets `ignitor-N`, is registered on the
  playback's fork. The author edits to the second spelling while the playback runs. `uniqueId()` answers the same
  name, `announced.add(name)` is false, nothing is sent, the fork keeps the first tree. Result: the edit has no
  effect; the reverse edit likewise. Two instruments in one song that differ only in sharing also both play the first
  one seen. Audible wherever an instance has dice or drift: `pluck`, `superpluck`, the super oscillators' phases,
  `perlin`, `berlin`, `dust`, `crackle`, any oscillator at `analog` above 0 (two copies summed against one doubled;
  `off-values.md` measured the same pair of shapes at +4.6 to +7.0 dB). A new playback announces again and is right.
- **Reach**: live coding: switching between a `let` used twice and two calls of the same door, in a running playback.
- **Settle it**: a spec beside `InlineDslRegistrarTest`: register `Plus(p, p)` then `Plus(p1, p2)` with `p1 == p2`,
  `p1 !== p2`, and assert two announcements (red today), or render both through `KlangOfflineRenderer` on one
  playback and compare with a fresh playback.
- **Confidence**: high for the mechanism (each step read); the size of the audible difference is taken from the gate's
  measured twin case, not measured here.

### L5-03 Large per-note buffers are allocated at note-on and never pooled (shimmer ring, pluck rings)

- **Severity**: minor (major inside a 16x region, see What)
- **Tag**: realtime
- **Where**: `audio_be/src/commonMain/kotlin/ignitor/IgnitorEffects.kt:602` (ring of `ringFrames`),
  `IgnitorDslRuntime.kt:2084` to `:2087` (`SHIMMER_RING_FRAMES shl cache.regionStages`, no gate);
  `audio_be/.../ignitor/KarplusString.kt:27`.
- **What**: the voice build runs on the audio thread at note-on. A `shimmer` node allocates a ring of 96,000 doubles
  (768 KB) per voice, whatever `wet` is: the DSL arm is not gated, although the Kotlin overload
  `Ignitor.shimmer(wet: Double)` returns the input at `wet <= 0` (`IgnitorEffects.kt:781`), so the two doors
  disagree. Inside a region the ring is multiplied by the factor: 12.3 MB per note at 16x
  (`OversampleRegionSpec` pins that size as wanted). Each plucked string allocates 2,500 doubles (20 KB), a default
  `superpluck` 160 KB per note. None of it is returned to a pool; the Katalyst's rings of the same kind come from
  `ResourceWarehouse`. Measured: `build_us` 39.8 for the shimmer stage against 12.3 for the same tail without it,
  29.0 and 55.5 for `superpluck` at 8 and 16 voices against 2.8 for a saw (`stage-cost.txt`, JVM); under node 59.0
  against 10.8, and 60.2 and 116.1 against 7.3 (`stage-cost-js.txt`).
- **Reach**: any instrument with `shimmer` (none in the corpus) or `pluck` / `superpluck` (in the corpus); every note.
- **Settle it**: the allocation check (`audio_benchmark`) with a case that plays one `shimmer` note per block, JVM and
  V8; a JFR allocation profile of a pluck song.
- **Confidence**: high for what is allocated; the bytes on V8 are not measured, only the build time.

### L5-04 A gated-off stage still pays the whole build path: 5 to 10 us and several KB per note for a tail that builds one stage

- **Severity**: minor
- **Tag**: performance
- **Where**: `IgnitorDslRuntime.kt:599` to `:610` (every non-leaf goes through `getOrPut` and `buildRaw` before its
  gate is asked), `:1240` to `:1269` (six captured `var`s per `buildRaw`), `:753` to `:761` (a gate query BUILDS the
  leaf), `:1754` to `:2083` (the gate rows inside `buildRaw`).
- **What**: for `sound("saw")` the build visits 12 non-leaf nodes (the envelope, nine off amplitude stages, the
  pregain `Affine`, the saw) and four off pitch stages. Each of the nine off amplitude stages still: scans the cache,
  enters `buildRaw` (six heap cells for the captured locals; the code's own notes at `:1261` and `:1515` price one
  such local at about 190 B per voice on the JVM and 380 B on V8, which is 12 nodes times one cell), builds its
  gating leaf to ask it (`BuiltIgnitor` + `ParamIgnitor` + a boxed `Double`), returns a `BuiltIgnitor`, copies it
  (`raw.copy`), and files four list entries. About 25 leaves are built only to be asked (9 amplitude gates, 4 pitch
  gates, the envelope's switch and 3 curves, the unity test, and the knobs that are then built a second time).
  Measured: `classic() all off` builds in 12.3 us against 2.2 us for the bare leaf, the saw voice with everything
  off in 13.4 us against 2.8 us for the bare saw (`stage-cost.txt`, JVM, build plus first block); a classic saw note
  allocates 6,661 B on the JVM and 13,296 B on V8 (`alloc-budgets.txt`, `E-C-n1`, which includes the dispatcher's
  per-note work). Under node (`stage-cost-js.txt`, which appeared in the audit folder while I read; the median of 5
  processes) the same rows read 10.8 against 5.8 us and 13.8 against 7.3 us. So the off tail is about 10 of the
  17.6 us a default saw voice takes to build on the JVM and about 5 to 6.5 of its 23.1 us under node, and the build
  of that voice equals about 24 blocks of its own render on either platform (5.82 and 7.80 ns per sample).
- **Reach**: every note of every built-in and every sample.
- **Settle it**: move the gate test in front of the cache for the amplitude rows, as the pitch rows already do
  (`:471` to `:560`), returning the inner's `BuiltIgnitor` unchanged (the off envelope alone needs a copy for its
  tail), and resolve a leaf's value from the DSL leaf instead of building it; then rerun the stage cost table and
  `E-C-n1`. By reading it is behaviour-neutral: the answers of a gated node are its inner's for tail, gate flag,
  envelope flag, latency and oversamplers, and a leaf query draws nothing.
- **Confidence**: high for the path and the measured totals; the split of the 10 us between its parts is an estimate.

### L5-05 A pitch node bends a musical oscillator in a SOURCE's parameter position, and a shared one depends on who renders first

- **Severity**: minor
- **Tag**: consistency
- **Where**: `audio_be/.../ignitor/ModApplyingIgnitor.kt:45` to `:48`; `Ignitors.kt:166`, `:175` (and every source's
  `readParam` and `PhaseOffset.render`, `PhaseOffset.kt:54`); `IgnitorDslRuntime.kt:1296` (`noMod`), `:1318` to `:1324`
  (`pitchedSource`); `MemoizingIgnitor.kt:121` (the key has no `phaseMod`); `audio/ref/voice-synthesis.md:51`.
- **What**: a source's knobs are built with `noMod()`, so a musical oscillator in a knob is a bare source
  (`applyMod(source, null)`), not shielded (only an absolute-frequency one gets `ModBlockingIgnitor`). At render the
  wrapped source reads its knobs INSIDE its own `generate`, while `ModApplyingIgnitor` has `ctx.phaseMod` set, and
  the bare oscillator in the knob reads `ctx.phaseMod`. Input: `Ignitor.sine(phase = Ignitor.sine().mul(0.3))` under
  a vibrato (an authored one or sprudel's `vib` through `classic()`). Result: the phase modulator follows the vibrato.
  The same oscillator in a filter's knob (`x.lowpass(Ignitor.sine().range(...))`) is rendered outside every
  `ModApplyingIgnitor` and is not bent. The ref page says a classic stage "never bends a musical oscillator in a
  parameter position"; the archived plan says "of a non-source node". Second half: one `let m` used in a source's
  knob under a pitch node AND in a filter's knob is ONE cache entry (both built with mod null). The memo caches per
  block and its key does not hold `phaseMod`, so whichever reader renders first decides: `a + b` gives the filter a
  bent modulator, `b + a` gives the source an unbent one.
- **Reach**: phase modulation or PWM by a note-pitched oscillator under any pitch node; the order dependence needs
  the modulator shared with a non-source knob.
- **Settle it**: a spec rendering `sine(phase = m).vibrato(5, 1) + saw.lowpass(m.range(200, 2000))` and the same sum
  with its operands swapped: the two must be equal up to the order of one add (they are not, by this reading).
- **Confidence**: medium (read, not rendered).

### L5-06 A release written over `Freq` under a detune: the lifetime is read at the note, the envelope runs at the detuned pitch

- **Severity**: minor
- **Tag**: bug
- **Where**: `IgnitorDslRuntime.kt:1918` (`controlRateValueOrNull(cache.freqHz)`), `:1731` to `:1741` (the forked
  detune hands `endsInEnvelope` on); `audio_be/.../ignitor/IgnitorEnvelopes.kt:117`; `VoiceFactory.kt:302`, `:316`.
- **What**: the tail of an `Adsr` is its release evaluated ONCE at the note's frequency. The envelope itself reads
  its release every block at the frequency it is called with, which a `Detune` above it has multiplied. Input:
  `Ignitor.sine().adsr(release = Ignitor.freq().recip().mul(200)).detune(-12)` (the pitch-relative release is the
  build KDoc's own example, `:140`), at 440 Hz. Steps: the tail is 200 / 440 = 0.4545 s; the detune arm forks and
  passes `endsInEnvelope = true` on; the voice lives 0.4545 s past its gate and gets no teardown fade; the envelope
  runs at 220 Hz with a release of 0.909 s. Result: the voice is removed half way through its release, a hard step
  from mid level to zero on every note. Detuned UP it ends early instead (harmless). As one layer of a sum the root
  is not an envelope, so the teardown fade hides the step and the layer's tail is only cut short.
  `IgnitorTailSpec.kt:240` pins `endsInEnvelope` through a detune, with a constant release only.
- **Reach**: an authored instrument without `classic()` whose root is a detune over an envelope with a
  `Freq`-relative release. Narrow.
- **Settle it**: `IgnitorTailSpec`: build that tree at 440 Hz, render to the reported end, assert the last sample is
  silent.
- **Confidence**: high (traced), not rendered.

### L5-07 Inside a region a shared node repeats one block for ever once the region's frame counter saturates

- **Severity**: minor
- **Tag**: bug
- **Where**: `audio_be/.../ignitor/OversampleRegion.kt:166` to `:168`, `:189` to `:191`; `MemoizingIgnitor.kt:121` to
  `:124`.
- **What**: the child context's `voiceElapsedFrames` is the voice's times the factor, held at `Int.MAX_VALUE`. Its
  window is always `(0, length x factor)`. A caching memo keys on `(voiceElapsedFrames, offset, length, freq)`. Once
  the counter saturates, the key is the same in every block, so every shared node in the body (the body's own input
  read twice, by a `parallel` or a `blend` in the body, is one) serves the block it cached then, for the rest of the
  voice: a 2.67 ms loop of stale signal. The KDoc names the saturation for envelopes and clocks, not for the memo.
  When: `Int.MAX_VALUE / (48000 x factor)`, 46.6 minutes at 16x, 93 at 8x, 3.1 hours at 4x, 6.2 hours at 2x.
- **Reach**: one voice held that long inside a region (a live drone), with a shared node in the body.
- **Settle it**: `OversampleRegionSpec`: a context with `voiceElapsedFrames` near `Int.MAX_VALUE / 16`, a 16x region
  whose body is `y + y`, two blocks with different input; the second block must differ.
- **Confidence**: high (traced), not rendered.

### L5-08 A knob that reads a lifted LFO inside a region reads the upsampler's start-up at the first block, and pays a whole conversion per read

- **Severity**: minor
- **Tag**: sound
- **Where**: `IgnitorDslRuntime.kt:436` to `:450` (every source in a region becomes a `LiftedSourceIgnitor`, a knob's
  LFO included); `OversampleRegion.kt:205` to `:226`; `Ignitor.kt:105` to `:112` (`blockStartValue`).
- **What**: a stage inside a region reads a moving knob once per block as sample 0 of a full render. For a source in
  the knob that render is: the source's base block, then the IIR up conversion of the whole block, then one sample
  read. At the voice's first block the half-band all-pass chains start from zero state, so output sample 0 is a
  small fraction of the source's first sample. Input: `x.oversampleBy(4) { y -> y.lowpass(Ignitor.saw(2).range(200, 2000)) }`
  (a saw LFO starts at -1, so the cutoff should start at 200 Hz). Result: the first block reads about 0, which
  `range` maps to about 1100 Hz; from the second block on the read is right (late by the converter's 3 to 5
  samples). One block of a wrong cutoff at every note onset. A sine LFO starts at 0 and hides it; a saw, square,
  triangle, `perlin` or `berlin` LFO does not. Cost: a full up conversion per block per such knob to read one
  sample, and a source read twice in a body is converted twice (the lifted wrapper is made per reference and has no
  cache entry, each with its own `Oversampler`).
- **Reach**: an authored region whose body has a stage with an LFO in a per-block knob (cutoff, q, drive amount,
  coarse factor, crush bits).
- **Settle it**: `OversampleRegionSpec`: the cutoff a filter in a 4x region computes at block 0 for a saw LFO knob,
  against the same filter outside the region. A control-rate read of a lifted source could read the base-rate
  source directly.
- **Confidence**: medium (the path is traced; the size of sample 0 comes from the filter's structure, not from a run).

### L5-09 Per-note allocations in render at the first block (the lint's leads, with their reach)

- **Severity**: minor
- **Tag**: realtime
- **Where**: `OversampleRegion.kt:158` (one `IgniteContext` per region per voice, always);
  `Ignitors.kt:923`, `:2950` (a drift lane or container when `analog` reads `Freq`); `ScratchBuffers.kt:83`, `:213`
  (sub-pool growth, once per engine); `MemoizingIgnitor.kt:63` (not in render: one empty `AudioBuffer(0)` per memo
  at build, a typed array per built node on JS).
- **What**: `MEMORY.md` says "a node builds its storage with the voice and draws at its first block". The region's
  child context is the one object that is always made in `generate`: it needs the voice's context, which exists only
  after the build (`VoiceFactory.buildVoice` makes it right after). The drift fallbacks fire only when the build's
  freq-0 read of `analog` disagrees with the first block's. The sub-pool growth is per engine, and its counter is not
  in the stats feed (the root pool's is).
- **Reach**: every voice with a region (one object per region per note).
- **Settle it**: `FirstBlockAllocationSpec` with a region voice; decide the rule question the ticket already asks
  (is "once per note inside `generate`" allowed).
- **Confidence**: high.

### L5-10 Registered inline instruments are never dropped while a playback lives, and a noise instrument gets a new name at every evaluation

- **Severity**: note
- **Tag**: leak
- **Where**: `IgnitorRegistry.kt:72`, `:82`, `:105` to `:119` (`defs` and `optimizedDefs`, two trees per name, no
  bound); `IgnitorDslIdentity.kt:24` (the process-wide map, never cleared); `IgnitorDsl.kt:558`, `:675`, `:687`
  (`uid = nextNoiseUid()`).
- **What**: KLANG-00210 section 11 lists the process-wide name map as debt; it is still there. Two things beside it.
  The engine's fork keeps the authored and the optimized tree of every name for the playback's life, where the
  Katalyst side bounds its built chains (`MAX_CACHED_CHAINS`). And a white, brown or pink noise node takes a fresh
  `uid` at every construction, so an instrument that contains one is a NEW structural tree at every evaluation of
  the script, edited or not: a new name, a new map entry, a new announcement, a new `optimize()` on the audio
  thread, two more trees on the fork.
- **Reach**: long live-coding sessions; bounded by the page's life (the map) and the playback's (the fork).
- **Settle it**: count `globalIgnitorNames` and the fork's `names()` over 100 evaluations of one unchanged song with a
  `whitenoise` instrument.
- **Confidence**: high for the code; when the script is re-evaluated is the frontend's (not read).

### L5-11 The gate reads leaves only: the full table, both directions

- **Severity**: note
- **Tag**: consistency
- **Where**: `IgnitorDslRuntime.kt:739` to `:761`, and each row's line below.
- **What**: read from the code against `audio/ref/off-values.md`. "Leaf" is a `Param` or a `Constant`; a `Param`
  with a non-finite override reads its default.

| Stage | Line | Off when the knob is a leaf at | Matches the table | A non-leaf knob (expression, signal, `Variants`, a hint that survived) |
|-------|------|--------------------------------|-------------------|------|
| vibrato | 474 | a finite depth `<= 0` | yes | built; depth floored per sample |
| accelerate | 496 | `== 0` or non-finite | yes | built; runtime reads non-finite as 0 |
| pitch envelope | 509 | `== 0` or non-finite | yes | built |
| pitchModSemitones | 545 | `== 0` or non-finite | yes | built |
| fm | 560 | `== 0` or non-finite | yes | built |
| pitchMod | 533 | never | yes | built |
| `mul` (`Times`) | 1647 | exactly 1.0, signal survivor only | yes | built |
| `mul` (`Affine`, bare) | 1682 | exactly 1.0, signal survivor only | yes | built |
| lowpass, highpass, bandpass, notch | 1754, 1788, 1826, 1838 | non-finite | yes, UNTIL THE OPTIMIZER FUSES IT (L5-01) | built; a NaN cutoff is a 1 kHz filter |
| onepole | 1819 | `<= 0` or non-finite | yes | built |
| envelope | 1886 | `on == 0` (either sign) | yes | built |
| distort (fused) | 1961 | `<= 0` or non-finite | yes | built; runs at unity drive at or below 0 |
| drive | 1990 | `<= 0` or non-finite | yes | built; a NaN amount makes every sample NaN (`IgnitorEffects.kt:109` to `:122`: `NaN <= 0.0` is false, the gain is `10^NaN`) |
| shape | 1998 | never | yes | |
| crush | 2013 | `< 1` or non-finite | yes | built |
| coarse | 2026 | `<= 1` or non-finite | yes | built |
| tremolo | 2055 | depth `<= 0` or non-finite | yes | built with gain 1; the LFO renders and `gatesOutput` switches culling off |
| phaser | 2041 | never | yes | |
| `Eq` sections | 1855 | never (not in the table) | no row | a NaN cutoff is a 1 kHz section |
| shimmer | 2084 | never (not in the table) | no row | `wet` 0 still builds the 768 KB ring (L5-03) |

  The "non-leaf" column is the documented consequence of the leaf rule ("for an ungated stage with a NaN knob there
  is no second line of defence"). The drive row is the one where it is not benign:
  `x.drive(Ignitor.param("d", NaN).add(0))` or a `Variants` over an unset slot renders an all-NaN voice. It needs an
  authored non-finite default behind clamp-free arithmetic, so it stays a note. `Variants` and `OptimizerHint` are
  draw-free wrappers; unwrapping them in `buildTimeKnobValue` would close their half (see "Decisions").
- **Reach**: authored trees only; `classic()` places plain slots.
- **Settle it**: `IgnitorGateSpec` rows for a `Variants`-wrapped knob per stage.
- **Confidence**: high.

### L5-12 Dead code and stale statements in this layer

- **Severity**: note
- **Tag**: dead / doc
- **Where and what**:
  - `IgnitorDslClassic.kt:548` `endsInClassic()`: no production caller (its KDoc says the engine no longer asks);
    only `IgnitorRegistryTest` calls it.
  - `Ignitor.kt:1150` to `:1164` `withGain`, `octaveUp`, `octaveDown`: no caller in main code.
  - `ResourceWarehouse.kt:148` to `:149`: "a sub-pool's real depth is 1. Nothing more to pre-size" is false since
    regions; the companion's own KDoc at `:197` to `:202` says the opposite.
  - `ResourceWarehouse.kt:186` to `:190`: "32x is legal ... builds its sub-pool on first use": a region stops at 16x
    (`REGION_MAX_STAGES`, `IgnitorDslRuntime.kt:1537`); no door reaches 32x any more.
  - `IgnitorDslRuntime.kt:89` to `:97` and `IgnitorRegistry.kt:185`: "only one build-time consumer reads the
    sample rate, the humanize drift lane". The tremolo's edges read it too (`:1090`, through `cache.sampleRate`), and
    the region arms derive rates from it.
  - `audio/ref/voice-synthesis.md:51`: see L5-05.
  - `GraphCensus` (its KDoc): every gated amplitude stage "counts as built, set or not", so the song benchmark's
    `work` and `traffic` columns count nine stages per classic voice that do not exist at render.
- **Confidence**: high.

### L5-13 What the parity specs do not reach

- **Severity**: note
- **Tag**: test
- **What**: see "Test truth". The promise "every registered tree renders within `OPTIMIZER_PARITY`" is pinned for
  finite cutoffs, default slot values, 3 or 4 blocks, on the JVM for the fuzz.
- **Confidence**: high.

## Performance candidates

| Where | What | Rate | Measured | Expected gain (rough) | Risk to the sound |
|-------|------|------|----------|-----------------------|-------------------|
| `IgnitorDslRuntime.kt:599`, the gate rows in `buildRaw` | Ask the gate before the cache and `buildRaw`; a gated stage returns its inner's `BuiltIgnitor` (L5-04) | per note | off tail 12.3 against 2.2 us (JVM), 10.8 against 5.8 us (node); 6.6 KB (JVM) and 13.3 KB (V8) per classic note in all | most of the 10 us (JVM), of the 5 us (node); 2 to 3 KB on the JVM | none by reading (same answers, no draw moved) |
| `IgnitorDslRuntime.kt:753` | Read a leaf's build-time value from the DSL leaf (one shared resolver with the `Param` arm) instead of building `BuiltIgnitor` + `ParamIgnitor` + a boxed `Double` | per note, about 25 times per classic voice | not split out | about 50 small objects per note | none |
| `IgnitorDslRuntime.kt:1240` to `:1269` | Six captured locals are six heap cells per built node | per node per note | the code's own note: one cell is 190 B (JVM), 380 B (V8) per voice | about 1 KB (JVM), 2 KB (V8) per classic voice | none |
| `MemoizingIgnitor.kt:63` | One shared empty buffer instead of `AudioBuffer(0)` per memo | per built node per note | not measured | one typed array per node on JS | none |
| `ModApplyingIgnitor.kt:39` to `:42` | With no outer `phaseMod`, hand `modBuf` to the source as `phaseMod` (both are `DoubleArray`s) instead of copying it | per block per pitched source under a pitch node | part of vibrato's 8.5 ns | one pass of 128 | none (same values) |
| `IgnitorDslRuntime.kt:643` (`cachePerBlock`) | A pitch memo with one reader renders into its cache and copies out, and allocates that cache (128 doubles, a typed array on JS) at every note | per block and per note, per pitch node | part of vibrato's 8.5 ns, fm's 4.6, penv's 4.3 | one pass of 128 per block, 1 KB per note | none; the code chose the copy for simplicity (review round 2) |
| `IgnitorDslRuntime.kt:471` to `:592` | The ON path of a pitch stage is the dearest thing a pattern can add to a build (`MEMORY.md` lists it as never measured) | per note | build of the saw voice with the stage on, minus all off: vibrato +10.3 us (JVM), +24.4 us (node); fm +11.4, +24.7; penv +8.6, +17.8; accelerate +3.4, +7.2 (first block included) | not estimated; the vibrato builds a sine with its drift lane, three wrappers, a memo with its cache, and asks six leaf questions | none for the leaf questions and the cache |
| `IgnitorDslRuntime.kt:206` | The build cache is a linear scan per node: quadratic in the tree | per note | not measured | nothing at 12 nodes; about 45,000 compares for a 300 node instrument | none |
| `OversampleRegion.kt:205` | A lifted source is converted up once per reader, and fully for a one-sample knob read (L5-08) | per block | not measured | one conversion per extra reader or knob | a knob reading the base-rate source changes its timing by the converter's delay: a SOUND decision |
| `IgnitorDslRuntime.kt:1540` to `:1545` | A region with no step rewrites its body's DSL (`withRegionInput`) at every note-on | per note | not measured | the rewrite; could be done once at registration for a literal factor | none |
| `IgnitorEffects.kt:602`, `KarplusString.kt:27` | Pool the shimmer ring and the string rings (L5-03) | per note | shimmer 39.8 us, superpluck 29.0 and 55.5 us build | most of those | a reused ring must be cleared |
| `IgnitorDslRuntime.kt:1779`, `:1855` | Products that depend only on the registered tree, built per note: `butterworthQLadder`, `eqSectionSpec` per section, the tremolo's shape by name | per note | not measured | small | none |
| `InlineDslRegistrar.kt:50` | `uniqueId()` hashes the whole tree per event; the Katalyst value memoizes its name | per event, frontend thread | not measured | one deep hash per event | none (but see L5-02 before caching the name) |

Not a candidate: the optimizer on `classic()`. Its filters never fuse (slots in `env` and `analog`), and a one-section
`Eq` costs what the SVF node costs (4.0 against 3.9 ns), so nothing is lost there.

## Suspicions

- **Stack depth.** The build recurses four frames per node (`buildIgnitor`, `buildRaw`, `withMod`, `withModBuilt`),
  `buildRaw` with a large frame, at note-on on the audio thread with no handler around it (`VoiceScheduler` has no
  `try`). A chain of about a thousand nodes (a script loop that appends a stage) may overflow the stack; in the
  worklet a throw in `process` silences the processor for good. `optimize()` recurses too but is caught
  (`IgnitorRegistry.kt:109`); the frontend's structural `hashCode` and the codec recurse as well. Settle: a spec
  that builds `x.abs()` repeated 500, 2,000 and 10,000 times, on the JVM and under node.
- **A self-reference through `let`** cannot be built: the DSL is immutable data classes, so a node cannot contain
  itself. Not traced through the KlangScript interpreter.
- **Kotlin/JS equality of `-0.0`** in `phaseInput()` (`IgnitorDslRuntime.kt:1302`, `this == Constant(0.0)`): on the
  JVM `Constant(-0.0)` is not equal and builds a `PhaseOffset` that does nothing audible; I did not check the JS
  compiler's `equals`.
- **Long-run parity.** The fuzz renders 3 blocks, the render spec 4. A fused high-q section and its chained twin
  differ by rounding in their state; whether that stays within 1e-12 of the block after seconds is not pinned.
- **The dead branch under a block-constant that crosses zero between blocks** (a multiplier written over `Freq` under
  a moving detune): the upstream freezes for those blocks and resumes with stale state. Documented as an accepted
  residual for the vibrato's depth (`off-values.md`, edge 6); the same holds for every `Times` and `Affine`.

## Decisions worth revisiting

- **Fusing a single filter into an `Eq` is "on"** (`IgnitorDslOptimizer.kt:243` to `:246`). With L5-01 it removes a
  gate row for every plain-door filter. Either the `Eq` arm gates a section whose cutoff is an unset leaf, or the
  optimizer refuses a cutoff that is a `Param` (the reason it already gives for `env`, `analog` and `passes`: a slot
  can change per note and the decision is made once).
- **`buildTimeKnobValue` could look through `Variants` (the build's own pick) and `OptimizerHint`.** Both are
  draw-free and already dissolved by the build; today a knob behind either is "not a leaf", so the stage is built
  with an off or non-finite value (`off-values.md` records it for the vibrato only).
- **The structural instrument name** (L5-02). It was chosen so that an unchanged tree keeps its name; it now
  contradicts the identity rule the wire and the build keep. The noise `uid` is the earlier patch for the same
  conflict, for three node types only, and it costs a new name per evaluation (L5-10).
- **`endsInEnvelope` with a de-click.** A `classic()` voice ends exactly at gate plus release with the de-click
  filter still about 3e-3 above zero (teardown_fade's KDoc knows). Giving the lifetime a few de-click time constants
  when the root envelope has a positive leaf `declick` would let the envelope really end the voice. One line in
  `treeLifetime`; it lengthens every classic voice by about 5 ms.
- **First-block allocation.** The rule question in KLANG-00420 decides L5-09; the region's child context is the one
  case that could move to the build (the factory could hand the voice's context to the tree after `buildVoice`).

## Listening tasks

- A `detune` whose semitones are a signal is read once per block (`Ignitor.kt:1122` to `:1126`), so a detune LFO moves
  the pitch in steps of 128 frames, where `pitchModSemitones` moves per sample. Listen to
  `saw.detune(Ignitor.sine(6).mul(1))` against `saw.pitchModSemitones(Ignitor.sine(6).mul(1))` on a high note.
- A phase or duty modulator at the note's pitch under a vibrato follows the vibrato (L5-05). That may be what a
  player wants (the fm modulator was made to follow on purpose); the question is only whether a filter LFO should
  differ from it.
- L5-08: a saw or square LFO on a cutoff inside a region, short notes: is the first 2.7 ms audible as a tick.

## Test truth

- **Pinned well**: each gate row with a leaf knob, off and on, unset included (`IgnitorGateSpec`); the unity fold
  through `Affine`; the release tail and `endsInEnvelope` through gated stages (`IgnitorTailSpec`); the memo's key
  parts (`MemoizingIgnitorSpec`); regions' rates, latencies and the no-step substitution (`OversampleRegionSpec`); the
  optimizer's laws (idempotent, never grows, parameters survive, sharing survives) and its arithmetic folds over
  adversarial constants (fuzz, 1,500 trees).
- **Not pinned**:
  - optimizer plus gate on a filter with an unset cutoff (L5-01). No mutant is needed: the row is red on HEAD.
  - the fuzz's filter cutoffs are always finite; its `ignitorParams` are always null; it has no pitch node, no
    `Detune`, no `Variants`, no region with a slot factor; it runs on the JVM only (`jvmTest`).
  - identity against structure in the instrument's name: `InlineDslRegistrarTest:77` and `:120` pin the collapse
    of equal trees as wanted, with trees whose sharing does not differ.
  - `endsInEnvelope` through a detune over a `Freq`-relative release (`IgnitorTailSpec:240` uses a constant release).
  - a pitch node over a source with a musical oscillator in a knob (no spec found by grep for `phase = ` with a
    `Sine` at the note under a vibrato).
  - the region's saturated counter against a memo.
- **Mutations that would survive** (by reading, not run):
  - `OversampleRegion.kt:168`: drop the saturation and let the product wrap: no spec holds a context that far.
  - `IgnitorDslRuntime.kt:1855`: add a gate to the `Eq` arm (skip a section with a non-finite leaf cutoff): no spec
    builds such an `Eq`, so the fix for L5-01 needs its own row to be mutation-checked.
  - `IgnitorDslRuntime.kt:1918`: read the tail at freq 0 instead of `cache.freqHz`: only a spec with a
    `Freq`-relative release would see it; `IgnitorTailSpec` may have one (the KDoc names
    `Ignitor.freq().recip().mul(200)`), I did not find the row.
  - `ScratchBuffers.kt:213`: a sub-pool with 2 buffers instead of 4: `lateAllocations` of a sub-pool is not asserted.

## For the audit ruleset

- Worked: the brief's "check every stage against the table, both directions, as a full table read from the code".
  Writing the table found L5-01, because the `Eq` arm had no row to fill.
- Worked: `build_us` and `alloc-budgets.txt` beside the code. The code's own comments price single allocations, so a
  reader can add up a per-note cost without a run.
- Worked: asking "what is the key" for every cache and name. Two of the findings (L5-02, L5-07) are a key that is
  missing a part.
- Did not work: a read-only audit cannot settle findings that are one render away. A rule that lets an auditor write
  a throwaway spec in the scratch directory and run it under the build lock would have turned four "traced, not
  rendered" findings into measured ones.
- Missing in the brief: which platform's stack and heap limits count for pathological input, and whether the
  frontend's side of a key (here `klang/`) belongs to the layer that owns the build cache. I read it; another
  auditor might not.
- The scope was too large for one reader at full depth: `Ignitors.kt` and `IgnitorDsl.kt` are 6,400 lines that this
  layer touches only at their edges. A split "build and gate" against "optimizer, cache and names" would fit better.
