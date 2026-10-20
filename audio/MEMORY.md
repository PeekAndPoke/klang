# Klang Audio: memory

What is TRUE NOW in the audio engine (`audio_bridge`, `audio_be`, `audio_fe`, `audio_jsworklet`), each point
once, with a pointer to its home. Keep it that way: when a change lands, update the section it touches IN PLACE
(replace, do not append), add ONE line to History (date, a few words, the link to the task record), and put the
narrative, the measurements and the review story in the task record, which gets archived. A rule that needs more
than two lines lives in an `audio/ref/*.md` topic file or a class KDoc, and this file points there. The full dated
record up to 2026-09-29 is `audio/ref/memory-history.md` (read it only for the history of a decision). Restructured
2026-09-29; every statement below was checked against the code that day.

## The signal flow and its owners

- **Per playback.** `PlaybackEngineDispatcher` routes every `Cmd` by `playbackId` to its own `PlaybackEngine`
  (scheduler, orbits, registry forks, `MasterBus`); the engines' outputs sum into the house stage. A
  `VoiceScheduler` serves its engine's one playback: one `PlaybackCtx`, made by the first voice, dropped by
  `cleanup`, nothing filtered by id. File map:
  `docs/audio-backend-file-map.md`; data flow and isolation: `audio/ref/architecture.md`.
- **Instrument = the voice's Ignitor tree** (phase 3, done 2026-09-28; its pitch modulations too since pitch pipeline
  steps 1 to 4, the last, `fm`, 2026-10-10; step 5 removed the empty shell in front of it). `Voice` runs Ignite, (teardown fade), Send; the stages and `BlockContext` live in `voices/`. Every built-in sound is `IgnitorRegistry.builtInVoice(source)` = `source.pregain().classic()`;
  every sample voice is the same shape over `IgnitorDsl.Sample` (`IgnitorRegistry.SAMPLE_INSTRUMENT`, never
  registered under a name). An authored instrument gets the voice doors by ending in `.classic()` as its LAST call
  (`IgnitorDsl.endsInClassic()`); a tree without it plays bare: no doors, no default envelope.
- **`classic()`** (`audio_bridge/.../IgnitorDslClassic.kt`): fm, pitch envelope, accelerate, vibrato (on the source, the
  pitch stages' place, FM innermost), onepole, crush, coarse, distort, highpass, bandpass, notch, lowpass, tremolo, adsr. Its knobs are `<door>.<param>` slots the pattern fills through
  `VoiceData.ignitorParams`, the param part the engine door's word (`adsr.attack`, `crush.bits`,
  `coarse.factor`); a stage at its off value is not built. Detail: `audio/ref/voice-synthesis.md`.
- **One word per knob, node to wire** (Q21, 2026-10-09): every envelope says `attack`, `decay`, `sustain`,
  `release` (the unit in the KDoc, not the name) and `declick`; the pluck's loop gain is `feedback`, brown noise's
  white leak is `leak`. The frame-domain core keeps `sustainLevel` (`EnvelopeCore.prepare`)
  and the constants keep their `*_SEC` names. Old names: `docs/retired-names.md`.
- **The pitch doors are in the tree** (KLANG-00245 (pitch pipeline into the tree), steps 1 to 4): sprudel's
  pitch envelope, accelerate, vibrato and fm are `classic()`'s `PitchEnvelope`, `Accelerate`, `Vibrato` and `Fm`
  stages, filled by the `penv.*` / `penvCurves.*` (step 1), `vibrato.*` (step 2), flat `accelerate` (step 3) and
  `fm.*` (step 4) slots; nothing runs in front of the tree (step 5): the root's `IgniteContext.phaseMod` is null, and
  `ModApplyingIgnitor` is the one source of a non-null `phaseMod` (`ModBlockingIgnitor` only clears and restores it). FM is innermost, so the other
  classic pitch stages move its modulator with the carrier (step 3b's rule), and an `fm` in the instrument sits inside
  its carrier and follows it (`s("sgbell").fm(...)`). The classic FM is the node's law, NOT the strip's sound (decision
  D3): the depth envelope per sample (ledger E11 closed), an envelope-free door keeps its depth through the release
  tail (the strip collapsed it at the gate), the rounding order (at most 7.6e-15 at the output before the gate), the
  modulator sine's rng draw (a pitched voice that also draws while it renders, a supersaw, a pluck, `analog > 0`,
  takes other random values; a noise-only voice is untouched), the modulator following `vib` / `penv` / `accelerate`. Over a forking detune one
  modulator serves both pitches, block-size dependent (ledger E8, the author rule below); no built-in has that shape
  since `sgpad` reads its second pitch as `Freq * 0.1.semitones()`, not under a `detune` (Q31, 2026-10-18;
  `SgpadFmSpec`).
- **Accelerate glides over the GATE and holds** (decision D2, with its hold, 2026-10-09): `2^(semitones / 12 *
  progress)` from the onset to the gate close (`IgniteContext.voiceDurationFrames`, which a note-off never moves),
  then the target through the release, for both doors; a gate of 0 frames holds the target from the first frame
  (Q27). The strip glided over the scheduled end (release tail
  included), so every sprudel `accelerate` under a release tail changed in step 3 (in the corpus only Kokon's
  `strike`); the Ignitor node rose on past the gate before the hold (an authored `accelerate` under a release tail,
  after the gate, no corpus song).
- **Voice lifetime** = gate end plus the tree's own release tail (`VoiceFactory.treeLifetime`, floored at 0;
  `VOICE_ADSR_RELEASE_SEC` when the tree has no static answer). `TeardownFadeRenderer` runs unless the root is a
  built amplitude envelope with a static release (`BuiltIgnitor.endsInEnvelope`). A silent release is culled: the
  voice ends at once (no zombie since lifecycle step 5). All three: `audio/ref/voice-synthesis.md`.
- **Voice lifecycle** = the state machine `Voice.State` (Pending, Sounding, Releasing, Fading, Done; Fading and
  Done terminal), advanced per block in `Voice.render`, which dispatches on it with an exhaustive `when` (the `Voice`
  KDoc). A sealed type: the states without data are `data object`s, `Releasing` (the cull's silence count) and
  `Fading` (the cut's fade window) are classes, one instance each created with the voice; a transition is
  `state = x.enter(...)`, so none allocates (the rule of `audio/ref/effect-state-machines.md` §1). The one list of
  the transitions is the table in the `Voice` class KDoc. A culled voice
  is Done at its cull block (`Voice.culled`, a latch for the scheduler's count). Onset, gate end and end live in ONE place,
  `VoiceLimits` (the voice owns and writes it, `releaseGate` included; the stages read it via `BlockContext.limits`;
  the ignite stage derives `IgniteContext.gateEndFrame` from it per block). Events from outside are `Voice` methods
  that decide by the state: the note-off (`releaseGate`, applies to Pending and Sounding) and the hard kill
  (`kill`, Done from any state), and the cut (`cutOff`: a sounding voice turns Fading, a 4 ms linear ramp to exact
  zero from the cutting voice's onset, before its send, `CUT_FADE_SECONDS`, its window held by `Fading`; a silent
  one is Done at once). The
  scheduler removes only Done voices, always keeping the list's order, by one allocation-free compaction pass,
  `retainInOrder` (`removeDoneVoices` between blocks, and the render loop).
  Plan and steps: KLANG-00279 (voice lifecycle state machine) (steps 1 to 5b done).
- **Channel**: `gain` is the one level word (the fader, applied once with `pan` in `SendRenderer`); a frontend's
  `velocity` is multiplied into `gain` before the wire. The orbit is the routing.
- **Bus**: each orbit (`Cylinder`) runs a `KatalystChain`, born with `KatalystDsl.classic` (body, vowel, delay,
  reverb, phaser, compressor, gain; duck in a cross-orbit pass), knobs from the owner's
  `VoiceData.katalystParams`: the orbit's bus settings are owned by the newest `Sounding` voice; a voice gives the
  orbit up when its gate closes or it is cut (lifecycle step 5: the block's newest offer, committed once per block
  by `Cylinders.processAndMix`; an ownerless orbit keeps its last settings). Laws: `audio/ref/katalyst.md`; the classes: `audio/ref/effects-mixing.md`.
- **Reverb**: one room for both ears: each side's combs are fed `(L + R) / 2` (`Reverb.CROSS_FEED` 0.5, by ear
  2026-09-30); an input with equal sides feeds what it did before, bit for bit. A Freeverb tail only: no pre-delay,
  no early reflections (KLANG-00256 (reverb models)). Two tail floors on purpose (maintainer,
  2026-10-17): ordinary playback holds a room's ceiling to `SILENCE_FLOOR` on the cells (unchanged, the corpus pins
  it); only while its chain drains after a swap (`KatalystReverbEffect.swapDraining`, set by the drain's
  `measureTail`, cleared by reset and retire) does it answer against `Reverb.TAIL_FLOOR`, the comb level whose output
  is about -90 dBFS, a MEASURED ratio, not a bound (the diffusers can add up to about 17 dB on a narrow band).
- **Master**: the same Katalyst chain at the output (`MasterBus`, `master(Katalyst(k => ...))`, off with
  `master(Katalyst())`). The Master DSL is retired.
- **House stage**: `MasterStage`: DC blockers, the limiter (-1 dB, 20:1, 5 ms lookahead, always on, not
  authorable; `MasterLimiterDefaults.kt`, `MasterStage.HOUSE_LIMITER_*`), then the clip into the engine's output:
  a floating-point `StereoBuffer`, no quantisation (in `[-1, 1]` a sample passes, above 1 is 1.0, else -1.0). The
  worklet copies it straight into Web Audio; only the JVM line and the WAV writer go to 16 bits, at their edge
  (`writePcm16` in `_pcm16_edge.kt`, the one home). The lookahead delays the whole output uniformly.
- **The wire** (`VoiceData`, `KlangCommLink`): pitch, gain, pan, routing, lifetime, and two slot maps; only seconds
  cross it, never cycles. `VoiceData.soundIndex` is the one variant channel (a sample bank's variant and
  `IgnitorDsl.Variants`, which picks `children[soundIndex.mod(size)]`; an empty one is silence everywhere, so as a
  Katalyst bus knob it reads 0.0, not the knob's default). Fields: `audio/ref/data-model.md`. On the worklet the
  decoder hands both slot maps over as a `KlangMap` (`common/infra`, a native JS `Map` behind it), whose lookup
  allocates nothing (engine follow-up 2; the guardrail below).
- **A shared node stays one node across the wire** (`@WireShared` on `IgnitorDsl`, 2026-10-10): the generated codec
  encodes and decodes each instance once per message, and `postMessage`'s structured clone keeps the sharing, so a
  `let` used twice is built once in the browser as on the JVM (the build cache keys by identity). Before, the browser
  built it twice (Kokon's and Der Schmetterling's Screamer pedal: two strings). Equal but distinct nodes stay two.

## Laws and constants in force

- **Block size 128, pinned everywhere** (`AudioBackendContext.RENDER_QUANTUM_FRAMES`, whose KDoc lists what
  depends on it): it is a tone parameter; never change it to speed a render.
- **The envelope law** is `EnvelopeCore` (its KDoc). Voice defaults `VOICE_ADSR_*` (0.01, 0.1, 1.0, 0.05); curves
  are index knobs with the reader's default, both Exponential; `ADSR_EXP_K` 3.0; de-click `ENV_DECLICK_SECONDS`
  1 ms on `classic()`. Knobs and `Adsr.on`: `audio/ref/voice-synthesis.md`.
- **The gate**: a stage whose gating knob is a leaf at its off value (or unset, except where the table says
  otherwise) is not built; since 2026-10-07 the pitch arms too, five now (vibrato, accelerate, pitch envelope, fm, and
  `pitchModSemitones` at a literal mod since 7a). The
  rule's text is the `gatedOff` KDoc in `IgnitorDslRuntime.kt`; the values are `audio/ref/off-values.md`, their one home.
- **The swap law** `ChainSwap` (both hosts): fade the leaving chain's input over 0.06 s, drain it at full weight,
  at most `MAX_DRAIN_SECONDS` 20 s; a request that waits behind the drain or the release CUTS it (a raised cosine over
  `CUT_SECONDS` 0.15 s, maintainer 2026-10-17), so an edit lands in about 0.2 s. **The release law** `TailRelease`: 60 dB per 3 s from exactly 1, retired
  under -90 dB. **A stopped playback is never hard-cut**; only an endless tail triggers the release, 20 s after the
  last note (`PlaybackEngine.isIdle` KDoc). All in `audio/ref/katalyst.md`. **An engine's end of life** is one
  `PlaybackEngine.Phase` (Playing, Stopped, Releasing, Released, Disposed; the table in its class KDoc); the
  dispatcher keeps only the render order and the disposal order, both exact.
- **Katalyst knobs** (2026-10-12): a stage knob is a `KatalystKnobDsl`: a number, a `Kat.param`, a modulator
  (`Kat.sine`, `saw`, `ramp`, `tri`, `square`, `perlin`, `berlin`), or the Ignitor's arithmetic and `range` over any of
  these, never an Ignitor node; the `eq` sections take a number or a `Kat.param` only. A knob over slots is evaluated when the owner's map changes; a knob that holds a modulator is
  ticked once per processed block at the top of `KatalystChain.process`. `audio/ref/katalyst.md`.
- **Knob glide** `KNOB_GLIDE_SECONDS` 0.05 in whole blocks (17 at 44.1 kHz, 19 at 48 kHz), one block for a knob that
  holds a modulator; **bank crossfade** `BANK_CROSSFADE_SECONDS` 0.05 (by ear 2026-10-17; 20 ms before), counted in
  samples, with two banks and one parking slot, so a material changes at most every 18 blocks at 44.1 kHz (19 at
  48 kHz): 16ths pass, 32nds park above about 143 BPM. A compressor move that RELAXES on balance (its net reduction at a fixed
  0 dBFS is less; the three knobs on one fraction) releases at the compressor's own release time,
  never faster than the 50 ms glide (`CompressorKnobMove`, by ear 2026-10-17). The bank
  crossfade and the compressor's switch fade run one linear law, `utils/linear_crossfade.kt` (the chain swap's
  `Crossfade` and the duck's glide are other laws). Every delay and reverb switch-off fades the feed over the wet
  glide and drains from the block it lands in, no further off-config needed (follow-up 42). Which knob glides how:
  `audio/ref/katalyst.md`.
- **Body and vowel** are one stage class with two kinds (`KatalystResonatorEffect`, `ResonatorKind`); a chain runs
  both stages as two instances. One table per catalogue index (`ResonatorTables`, equal rows share an instance, so a
  switch among aliases installs nothing); a TABLE change installs into the pooled pair nobody hears (two pairs, built
  at the stage's first install), from zero state: no allocation per change, and a sounding bank never retunes. A
  `wet` or `floor` change glides the blend of the sounding banks in place, no install (knob expressions step 5c).
  `audio/ref/katalyst.md`.
- **Authored lookahead** (Katalyst `compressor` / `limiter`): build-time, at most
  `Compressor.MAX_LOOKAHEAD_SECONDS` 0.05, uncompensated by the author's choice; the authored limiter defaults to
  lookahead 0 and a 1 ms attack (`AUTHORED_LIMITER_*`).
- **Analog drift**: the pitch drift is 1 cent per unit `analog` (`ANALOG_FAST_PEAK_CENTS` 0.2 plus
  `ANALOG_SLOW_PEAK_CENTS` 0.8); the filter cutoff drifts twice as far, `FILTER_DRIFT_RELATIVE_TO_OSC` 2.0 (by ear,
  2026-09-29); the voice-to-voice cutoff tolerance is `FILTER_CUTOFF_OFFSET_PER_ANALOG` (0.0002), the saturating
  SVF branch's drive `FILTER_DRIVE_PER_ANALOG` (0.25) (`audio_bridge/constants/FilterHumanizationDefaults.kt`).
  Every lane steps once per block and ramps across it.
- **`DriftLanes`** gives every multi-voice oscillator its drift; `analogSpread` 0 is one shared walk, 1 (default)
  a lane per voice, and both endpoints are exact.
- **Two pitch laws, two words** (decision D8, pitch pipeline 7a): `pitchModSemitones(mod)` is `2^(mod / 12)`, the law
  of `vibrato`, `accelerate` and `pitchEnvelope` as a primitive (`fastExp2`, `safeOut`); `pitchMod(mod)` is the linear
  `1 + mod`, FM's law, raw. The `vibrato` node is composed from it since 7b (the tremolo's pattern):
  `pitchModSemitones(sine(rate, analog = 0) * max(semitones, 0))`, the depth per sample; not bit-identical (one
  rounding, and the sine's drift-seed draw shifts the voice's later dice); `audio/ref/voice-synthesis.md`, its edge
  rules in the `vibrato` row of `audio/ref/off-values.md`. Since 7c the LFO takes `range(from, to)` before the depth
  and the sine's own `phase`; literals at the defaults `(-1, 1)` and 0 build neither (`classic()`'s slots included),
  so the default keeps 7b's bits. `pitchModSemitones` is gated at a literal 0 or non-finite mod, `pitchMod` is not
  (`audio/ref/off-values.md`); nesting multiplies, the outer ratio the left factor. The table:
  `audio/ref/voice-synthesis.md`, "The pitch nodes and their two laws".
- **Oscillator phase** (`PhaseOffset`, KLANG-00272 (oscillator phase knob)): every periodic oscillator has a `phase`
  input in cycles, wrapped to `KLANG-00300 (katalyst arithmetic)
- 2026-10-10 Engine follow-up 6: `WaveIgnitor` hands its `dt` to its loop methods (past V8's inlining limit) in its
  `WaveVoiceState.dt` holder, no longer as an argument; it boxed one heap number per block per wave oscillator on V8
  (about 16 B, now about 0), bit for bit, corpus identical; four V8 alloc budgets re-set with it:
  KLANG-00290 (engine follow ups) item 6
- 2026-10-10 The bus `distort` oversamples at 2x by default (maintainer; the voice's stays at 0): step 5 of
  KLANG-00298 (iir oversampler)
- 2026-10-10 The oversampler is a polyphase IIR half-band (flat to 20 kHz, about 100 dB of rejection, 3.07 samples at
  2x), and every dry path beside an oversampled one runs through its phase twin: KLANG-00298 (iir oversampler)
- 2026-10-10 Engine follow-ups 8 and 12: `Ignitors.readParam` is Kotlin `inline` (one shared helper was one
  megamorphic site that boxed every knob read on V8, 60 to 100 B per classic voice block), and the scalar is the
  primitive `Ignitor.controlRateValue`, read only when `isBlockConstant` (the nullable view is the extension
  `controlRateValueOrNull`; the "true flag, null value" breach path is dropped, maintainer); bit for bit, corpus
  identical: KLANG-00290 (engine follow ups) items 8 and 12
- 2026-10-10 Engine follow-ups 10a and 10b: `adsrCurveShape` is a statement `when`; its expression form boxed
  every sample of a moving envelope on V8 (the chain `adsr`, `classic()`'s envelope, the pitch and FM envelopes,
  about 2.1 KB per block each, now about 0), bit for bit, corpus identical: KLANG-00290 (engine follow ups) items
  10a and 10b
- 2026-10-10 Engine follow-up 3: no noise box reproduces; the house limiter's per-sample `lookaheadStep`, never
  inlined by V8, boxed about 2 KB per block whenever anything sounded and 4 KB from its first reduction on (the
  likely cause of the old figure, unproven), now about 0; it is `inline`, bit for bit, corpus identical:
  KLANG-00290 (engine follow ups) item 3
- 2026-10-10 Engine follow-up 9: `DistortionCore` dispatches its shape once per block to one loop per shape; the
  per-sample `when` boxed every shaped sample on V8 (2 to 33 KB per block, now about 0), bit for bit, corpus
  identical: KLANG-00290 (engine follow ups) item 9
- 2026-10-10 The sine's explicit partials, `partial(ratio, gain, phase)` (a fourth bank, wire `Sine.partials`, cap
  256, both doors), corpus identical: KLANG-00296 (sine inharmonic partials)
- 2026-10-10 Pitch pipeline 7c: the vibrato's `range(from, to)` and `phase` (node fields, both doors, `classic()`
  slots `vibrato.rangeFrom|rangeTo|phase`); the default builds neither, corpus 18 of 18 identical:
  KLANG-00245 (pitch pipeline into the tree) section 7c
- 2026-10-10 Pitch pipeline 7b: the `vibrato` node's runtime is a composition, `VibratoModIgnitor` gone; seven corpus
  songs move, by the sine's rng draw (all seven), one rounding (five) and the per-sample depth (Die Kirschblüte):
  KLANG-00245 (pitch pipeline into the tree) section 7b
- 2026-10-10 Pitch pipeline 7a: `pitchModSemitones(mod)`, the exponential pitch primitive (`2^(mod / 12)`), a node on
  both doors and the wire beside the linear `pitchMod` (D8); the corpus identical:
  KLANG-00245 (pitch pipeline into the tree) section 7a
- 2026-10-10 Pitch pipeline step 5: the strip's shell goes (`PitchPipelineBuilder`, `BlockContext.freqModBuffer`,
  `Voice.RenderContext.freqModBuffer`, `IgniteRenderer`'s bridge) and `voices/strip/` dissolves into `voices/` (D7);
  a pure removal, the corpus identical: KLANG-00245 (pitch pipeline into the tree) step 5
- 2026-10-10 Pitch pipeline step 4: sprudel's `fm(depth, ratio, attack, decay, sustain, release)` is `classic()`'s
  innermost FM stage (`fm.*` slots), its five wire fields, `FmRenderer`, `Voice.Fm`, `Voice.Envelope` and
  `EnvelopeCalc` gone; the node's law (per-sample envelope, E11 closed; full depth through the tail without an envelope;
  the modulator follows the other pitch doors; the rng shift); the corpus identical:
  KLANG-00245 (pitch pipeline into the tree) step 4
- 2026-10-10 `serial` (was `through`), `parallel` on the Ignitor and the Katalyst (branches summed and aligned by
  latency), `bands` (Linkwitz-Riley, flat untouched), `blend` (linear dry/wet), and a shared node kept one node
  across the wire (`@WireShared`): KLANG-00297 (parallel serial bands) steps 1 to 5
- 2026-10-10 v0.6.1: pitch pipeline steps 1 to 3b, the Katalyst `distort` stage (merged from `katalyst-distort`), a
  soloed voice protected to its end, the warehouse panel's reverb counters, `analog(character)` on every door and
  `variants` with plain numbers; the corpus identical except Kokon's two landing strikes (accelerate, at most 2.5
  cents): the entries below and `DEV-DIARY.MD`
- 2026-10-09 A `distort` stage on the Katalyst (bus and master), the voice's law at the house DC pole, the oversampler's
  latency held in every state: KLANG-00292 (katalyst distort stage)
- 2026-10-09 Superseded by follow-up 43 (2026-10-16): a soloed voice is protected for its whole life (`ActiveVoice.soloed`, Q14 and Q28); the window serves between events:
  KLANG-00294 (solo protects whole voice)
- 2026-10-09 Pitch pipeline step 3b: a pitch node means what it wraps; above an `fm` it moves the whole operator
  (`CarrierFreqMod`, the modulator reads the outer mod at the carrier's frequency, once per block). A sound change for
  authored fm trees under a pitch node and for sprudel's pitch doors over an fm instrument (the strip moved the modulator too),
  and for two shared-modulator shapes that rendered one modulator at two pitches; the corpus identical; one author rule
  (an fm above a forking detune): KLANG-00245 (pitch pipeline into the tree) step 3b
- 2026-10-09 Pitch pipeline step 3: sprudel's `accelerate` is `classic()`'s accelerate stage (the flat `accelerate`
  slot), its wire field and the strip's `AccelerateRenderer` gone; the node holds its target from the gate on (D2):
  a sound change for an `accelerate` under a release tail on both doors (corpus: Kokon's `strike` only, a listening
  pair) and for a zero gate (`legato(0)`: the target from the first frame, Q27), every other corpus row and every
  non-accelerate matrix row identical, apart from the shapes the step record names (D1, D6, a non-finite amount now
  the bare voice where the strip froze the oscillator, the clamp from about 598 semitones, one temporary regrouping
  with sprudel `fm`): KLANG-00245 (pitch pipeline into the tree) step 3
- 2026-10-09 Pitch pipeline step 2: sprudel's `vib` is `classic()`'s vibrato stage (`vibrato.*` slots), its two wire
  fields and the strip's `VibratoRenderer` gone, bit-identical on the corpus and the door matrix apart from the
  shapes the step record names (D1, D6, regroupings up to about 7.3e-13, the raw edges: a depth past about 598
  semitones, non-finite rates, a +Infinity depth): KLANG-00245 (pitch pipeline into the tree) step 2
- 2026-10-09 Pitch pipeline step 1: sprudel's `penv` is `classic()`'s pitch envelope stage (`penv.*`, `penvCurves.*`
  slots), its eight wire fields and the strip's `PitchEnvelopeRenderer` gone, bit-identical on the door matrix and
  the corpus, apart from the accepted shapes (an fm modulator under `penv` until step 3b, three pitch factors
  regrouped, bare instruments, D6): KLANG-00245 (pitch pipeline into the tree) step 1
- 2026-10-09 v0.6.0: the engine tidy-up is done (steps 1 to 13: the twins written once, `FilterDef` retired, a plain
  number at every constant door; the V8 allocation pass), bit-identical on the corpus; the open items:
  KLANG-00290 (engine follow ups); the record: KLANG-00286 (engine tidy up)
- 2026-10-09 The `classic()` slot renames: the envelope words and `declick`, `crush.bits`, `coarse.factor`, the
  pluck's `feedback`, brown noise's `leak`, on every door, node, wire field and runtime factory, bit-identical:
  KLANG-00289 (classic slot names check)
- 2026-10-08 One fade law for the bank swap and the compressor (`utils/linear_crossfade.kt`); body and vowel are one
  stage class with two kinds on a pooled bank, no allocation per change; the band rows live with their catalogues
  (`BodyMaterials.Mode`, `VowelBands.Band`) and `FilterDef` is retired: KLANG-00286 (engine tidy up) step 12
- 2026-10-08 First-block and voice-count allocations moved to the build (drift lanes, phaser, memo, partial banks,
  stacks, strings, the phase pool's parse and key): KLANG-00286 (engine tidy up) step 10
- 2026-10-08 An engine's end of life is one `PlaybackEngine.Phase`; the dispatcher's `draining` set and `detached`
  list are gone; `renderInto` is one path: KLANG-00286 (engine tidy up) step 9
- 2026-10-08 One playback per scheduler: one `PlaybackCtx`, no `playbackId` filters or parameters
  (`startRealtimeVoice` keeps its id, the context may be made from it): KLANG-00286 (engine tidy up) step 8
- 2026-10-08 Per-block walks are index loops (`Voice` stages, `Cylinders` in rent order with an id array, the
  scheduler, the dispatcher); the diagnostics closure is gone; the solo ramp is `SoloRamp` on an inlined
  `easeInOutCubic`, `ValueRamp` deleted: KLANG-00286 (engine tidy up) step 7
- 2026-10-08 The audio helpers live in `utils/` (`DspUtil.kt` split by content; `finiteOrZero`, `fadeToZero`,
  `timeConstantCoeff`, `wrapPhaseFastOrSafe`, `rampStep`, and `copyRangeInto` for every per-block copy, no `copyInto` view
  on JS); the stereo add is the member `StereoBuffer.addFrom`: KLANG-00286 (engine tidy up) step 6
- 2026-10-08 One silence floor, `SILENCE_FLOOR` (1e-5; the master's 1e-4 stays, D9); `BusEffectDefaults` holds
  delay and reverb; `TEARDOWN_FADE_SECONDS`, `stageAskedFor`, `KatalystChain.writers`: KLANG-00286 (engine tidy up) step 5
- 2026-10-07 The `KatalystSlots` composites live in their writers, one NaN rule per knob (body, vowel, reverb
  lowpass: the stage's); `CompressorSettings` / `DuckSettings` left `Voice`: KLANG-00286 (engine tidy up) step 4
- 2026-10-07 No `Random` default anywhere in the engine; an orbit knob's build draws from a fixed seed:
  KLANG-00286 (engine tidy up) step 3
- 2026-10-07 The oversampler is two halves, `upsample` and `decimate`, with the caller's shaping loop between
  them inline (no closure per block, no `copyInto` view on JS): KLANG-00286 (engine tidy up) step 2
- 2026-10-07 The gate covers the four pitch arms, a fold (a non-finite vibrato depth stays built, its default);
  a gated pitch arm's inner shares with the same node elsewhere: KLANG-00245 (pitch pipeline into the tree) step 0
- 2026-10-07 Solo is engine state per source: the rest fillers are control-only events, `SoloTracker` records from any
  event before the control drop and the late guard, the others play at `1 - amount`; `ActiveVoice.soloAmount` gone,
  audit B4.2 closed: KLANG-00287 (bugfix solo rests and amount)
- 2026-10-08 The unison cap is 256 (maintainer; it was 64): KLANG-00286 (engine tidy up) (Decided)
- 2026-10-07 An empty `Ignitor.variants()` is silence, no longer a `require` at note-on; the shimmer survives an empty,
  a huge or a non-finite pitch (no index error, no hang); a unison count is capped at `UNISON_MAX_VOICES` (64):
  KLANG-00286 (engine tidy up) ("First, a bug")
- 2026-10-07 The voice's states are a sealed type; the fade window lives in `Fading`, the silence count in
  `Releasing` (lifecycle step 5b, no sound change by design; the 18-song corpus bit-identical to step 5,
  coordinator, 2026-10-07): KLANG-00279 (voice lifecycle state machine)
- 2026-10-07 The newest Sounding voice owns its orbit's bus settings, gives them up at its gate or cut; the zombie
  is retired and every removal keeps the list's order (lifecycle step 5, maintainer, a sound change):
  KLANG-00279 (voice lifecycle state machine)
- 2026-10-07 A cut fades its victim over 4 ms (`Fading`) instead of removing it (lifecycle step 4; no shipped song
  uses cut): KLANG-00279 (voice lifecycle state machine)
- 2026-10-07 Note-off and hard kill are events on the voice; the scheduler removes only Done voices (lifecycle
  step 3, no sound change by design): KLANG-00279 (voice lifecycle state machine)
- 2026-10-07 One home for a voice's time limits, `VoiceLimits` (lifecycle step 2, no sound change by design):
  KLANG-00279 (voice lifecycle state machine)
- 2026-10-07 The voice's lifecycle is a state machine inside `Voice` (step 1, read-only, no sound change by design):
  KLANG-00279 (voice lifecycle state machine)
- 2026-10-07 The engine's output is floating point: the master clips into a `StereoBuffer`, the browser hears the
  floats, the 16-bit step lives only at the JVM/WAV edge (KLANG-00280 (float output))
- 2026-10-07 One pitch mod over several pitched sources at one pitch advances once per block; Sakura's `shaku` and Irish Lament's
  `blockfloete` vibrato rates written as heard (KLANG-00278 (shared modulator memo rate), B-1)
- 2026-10-07 A shared modulator that reads no `Freq` runs once per block at any pitch; a non-finite pitch amount reads
  as unset; silence floors on the Eq parity specs; the wasm stub deleted (KLANG-00278 (shared modulator memo rate),
  KLANG-00277 (bugfix ignitor non finite pitch amount), KLANG-00308 (audit audio backend leftovers))
- 2026-10-07 The saw's node is `Saw` end to end: the node `IgnitorDsl.Saw` (wire name `saw`), the factory
  `Ignitors.saw`; the sound names `sawtooth` / `saw` and the LFO shape `sawtooth` stay
  (KLANG-00273 (oscillator names across dsls), decision 3)
- 2026-10-06 The triangle oscillator is `tri` end to end: the node `IgnitorDsl.Tri` (wire name `tri`), the factory
  `Ignitors.tri`; the sound name and the LFO shape `triangle` stay
  (KLANG-00273 (oscillator names across dsls))
- 2026-10-06 A `phase` input on every periodic oscillator and a `range` on the tremolo's swing, both defaults bit-identical
  (KLANG-00272 (oscillator phase knob))
- 2026-10-05 The Ignitor's `rangex(from, to)`, the exponential twin of `range`, composed of `Exp`, `Range`, `Log` and
  `Max` (no node of its own), floor `RANGEX_FLOOR` (KLANG-00271 (sprudel signals range cleanup) decision 16)
- 2026-10-05 One `range`: the Ignitor's `unipolar()` / `bipolar()` go with their wire nodes; `range(0, 1)` and
  `mul(2).minus(1)` spell them (KLANG-00271 (sprudel signals range cleanup))
- 2026-10-04 The Ignitor/Katalyst naming: the wire field is `ignitorParams`, `SoundValue.Dsl` holds an inline tree,
  Katalyst params are their own type, `KatalystDsl.Slots` builds `classic` (KLANG-00269 (ignitor katalyst naming))
- 2026-09-30 The reverb is one room for both ears (`CROSS_FEED` 0.5): record (KLANG-00254)
- 2026-09-29 The tremolo is composed from the oscillators (its own LFO removed, `skew`/`phase` gone, 16 ms edges, every depth floored at 0): task (KLANG-00265)
- 2026-09-29 Filter drift is twice the pitch drift, by ear: record (KLANG-00248)
- 2026-09-28 The master is a Katalyst at the output; the Master DSL retires (phase 3 step 12): record (KLANG-00238)
- 2026-09-27 The voice strip and the Pipeline DSL retire; every voice is its tree (step 9): [entry](ref/memory-history.md#the-voice-strip-and-the-pipeline-dsl-retire-every-voice-is-its-tree-phase-3-step-9-2026-09-27)
- 2026-09-26 Authored instruments ending in `classic()` leave the strip (step 10 c1; superseded by step 9): [entry](ref/memory-history.md#authored-instruments-ending-in-classic-leave-the-strip-phase-3-step-10-commit-1-2026-09-26)
- 2026-09-26 The sample instrument (step 7): [entry](ref/memory-history.md#the-sample-instrument-the-strip-off-for-samples-phase-3-step-7-2026-09-26)
- 2026-09-26 The built-ins on `classic()` (step 6; the strip parts superseded): [entry](ref/memory-history.md#the-built-ins-on-classic-the-strip-off-for-them-phase-3-step-6-2026-09-26)
- 2026-09-25 One envelope law, `EnvelopeCore` (D3): [entry](ref/memory-history.md#one-envelope-law-envelopecore-for-every-envelope-phase-3-d3-commit-a1-2026-09-25)
- 2026-09-25 One crush law, one distort loop (step 4): [entry](ref/memory-history.md#one-crush-law-one-distort-loop-d1-and-d2-landed-phase-3-step-4-2026-09-25)
- 2026-09-25 `classic()` as a slotted tail, the filter envelope's slot-layer fill (step 5): record (KLANG-00237)
- 2026-09-25 One adsr shape, curve index knobs, an on switch, one exp bend (step 3c): record (KLANG-00237)
- 2026-09-25 One tremolo law, index shapes, a build-time factor (step 3b): record (KLANG-00237)
- 2026-09-20 Two banks, one parking slot, 20 ms: the filter swap after the morph (Katalyst 5c-11): [entry](ref/memory-history.md#two-banks-one-parking-slot-20-ms-the-filter-swap-after-the-morph-was-rejected-2026-09-20)
- 2026-09-20 The filter nodes grew a cutoff envelope and a per-voice lane (step 3a): record (KLANG-00237)
- 2026-09-20 The gate: a stage at its off value is not built (step 2): record (KLANG-00237)
- 2026-09-20 A material change morphs the resonator bank (5c-10; superseded, rejected by ear): record (KLANG-00236)
- 2026-09-20 The phaser and the duck switch and change without clicking (5c-9): [entry](ref/memory-history.md#the-phaser-and-the-duck-switch-and-change-without-clicking-2026-09-20)
- 2026-09-19 An orbit never deactivates while a voice plays on it; the fader glides (5c-8): [entry](ref/memory-history.md#an-orbit-never-deactivates-while-a-voice-plays-on-it-the-fader-glides-2026-09-19)
- 2026-09-19 The orbit compressor switches and changes by gliding (5c-7): record (KLANG-00236)
- 2026-09-19 Body, vowel and the orbit EQ fade from what sounds now (5c-6; its 10-bank pool superseded by 5c-11): record (KLANG-00236)
- 2026-09-19 The tail ceiling never under-reports a delay tail (5c-5): [entry](ref/memory-history.md#the-tail-ceiling-never-under-reports-a-delay-tail-2026-09-19)
- 2026-09-19 The orbit-bus fields left the wire (5b-3): [entry](ref/memory-history.md#the-orbit-bus-fields-left-the-wire-2026-09-19)
- 2026-09-19 The orbit delay and reverb are insert-style stages (5b-2): [entry](ref/memory-history.md#the-orbit-delay-and-reverb-are-insert-style-stages-2026-09-19)
- 2026-09-19 The filter swap as a state machine (5c-4; superseded by 5c-6 and 5c-11): [entry](ref/memory-history.md#the-filter-swap-is-a-state-machine-off-engaged-crossfading-2026-09-19)
- 2026-09-19 Body and vowel run on one resonator bank (5c-3): [entry](ref/memory-history.md#body-and-vowel-run-on-one-resonator-bank-2026-09-19)
- 2026-09-19 The orbit reverb is a state machine (5c-2): [entry](ref/memory-history.md#the-orbit-reverb-is-a-state-machine-too-2026-09-19)
- 2026-09-19 Orbit knobs glide: the pilot on the reverb size: [entry](ref/memory-history.md#orbit-knobs-glide-the-pilot-on-the-orbit-reverb-2026-09-19)
- 2026-09-19 An effect lifecycle as a state machine, the delay as template (5c-1): [entry](ref/memory-history.md#an-effect-lifecycle-as-a-state-machine-the-delay-is-the-template-2026-09-19)
- 2026-09-19 The born-with chain is slot-driven (5b-1): [entry](ref/memory-history.md#the-born-with-chain-is-slot-driven-one-way-a-bus-knob-reaches-a-stage-2026-09-19)
- 2026-09-19 The pregain slot, the `Param` leaf guard, the orbit's group fader: [entry](ref/memory-history.md#the-pregain-slot-the-leaf-guard-and-the-orbits-group-fader-2026-09-19)
- 2026-09-19 The wire carries one level word: [entry](ref/memory-history.md#the-wire-carries-one-level-word-2026-09-19)
- 2026-09-16 The ledger, a per-instrument benchmark harness: [entry](ref/memory-history.md#the-ledger-a-per-instrument-harness-for-every-optimization-round-2026-09-16)
- 2026-09-15 The optimizer's promise is a margin: record (KLANG-00202)
- 2026-09-15 div, minus and neg fold; a zero divisor is zero: [entry](ref/memory-history.md#div-minus-and-neg-fold-a-zero-divisor-is-zero-2026-09-15)
- 2026-09-15 The oversampler's decimator is polyphase: [entry](ref/memory-history.md#the-oversamplers-decimator-is-polyphase-and-indexed-2026-09-15)
- 2026-09-15 Analog drift steps per block and ramps across it: [entry](ref/memory-history.md#analog-drift-steps-per-block-and-ramps-across-it-2026-09-15)
- 2026-09-15 Polynomial e^x in the envelopes and the compressor, no fastLn: [entry](ref/memory-history.md#polynomial-ex-in-the-envelopes-and-the-compressor-no-fastln-2026-09-15)
- 2026-09-15 Table-and-polynomial 2^x in the pitch paths: [entry](ref/memory-history.md#table-and-polynomial-2x-in-the-pitch-paths-2026-09-15)
- 2026-09-15 Polynomial sine in the oscillators: [entry](ref/memory-history.md#polynomial-sine-in-the-oscillators-2026-09-15)
- 2026-09-15 Silence culling: record (KLANG-00199)
- 2026-09-10 One drift-lane container, `DriftLanes`: record (KLANG-00198)
- 2026-09-07 Loop shape beats block-pass count: [entry](ref/memory-history.md#loop-shape-beats-block-pass-count-2026-09-07-sine-partial-banks)
- 2026-07-04 Body and vowel resonators on the orbit, live-update fixes: [entry](ref/memory-history.md#body--vowel-resonators--live-update-fixes-2026-07-04)
- 2026-07-04 Ignitor ADSR knobs, declick and expK as slots (expK superseded by 3c): [entry](ref/memory-history.md#ignitor-adsr-knobs--declick--expk-as-slots-2026-07-04)
- 2026-07-03 Song-level CPU benchmark and the Der Schmetterling deep-dive: [entry](ref/memory-history.md#song-level-cpu-benchmark--der-schmetterling-deep-dive-2026-07-03)
- 2026-06-08 The VCA gain de-click smoother (superseded: the strip VCA retired): [entry](ref/memory-history.md#vca-gain-de-click-smoother-2026-06-08)
- 2026-06-05 The oscillator engine unified: record (KLANG-00100)
- 2026-05-28 The filter saturation dead end (its "purely linear" superseded, see the SVF guardrail): [entry](ref/memory-history.md#filter-saturation-dead-end--linear-svf-is-the-right-choice-2026-05-28)
- 2026-05-25 The sprudel-side scale and variant split (superseded here, a sprudel matter): [entry](ref/memory-history.md#sprudel-side-scale--variant-split-2026-05-25)
- 2026-05-22 Ignitor variants dispatch on `soundIndex`: record (KLANG-00097)
- 2026-05-21 The distortion shape catalogue extension: [entry](ref/memory-history.md#distortion-shape-catalog-extension-2026-05-21)
- 2026-04-27 The numerical safety contract and the distort DC lock (the `IgniteRenderer` clip superseded): [entry](ref/memory-history.md#numerical-safety-contract--distort-dc-lock-fix-2026-04-27)
- 2026-03-26 Ignitor param slots, "everything is a signal" (superseded as written): [entry](ref/memory-history.md#ignitor-param-slots--everything-is-a-signal-2026-03-26)
- 2026-03-23 The BlockRenderer pipeline (superseded: the strip retired): [entry](ref/memory-history.md#blockrenderer-pipeline-architecture-2026-03-23)
- 2026-03-19 The Ignitor composable architecture (superseded as written): [entry](ref/memory-history.md#ignitor-composable-architecture-2026-03-19)
- Undated sections of the old file, folded into the sections above: [Current Status](ref/memory-history.md#current-status), [Architecture Decisions](ref/memory-history.md#architecture-decisions), [Lessons Learned](ref/memory-history.md#lessons-learned)
