# What makes a record sound expensive, and what Klang needs to play it

Status: **research, 2026-10-08. Parked until the engine tidy-up is done** (maintainer, 2026-10-08: "let us wait until
the current engine work / cleanup is done. I think it is a good point in time to think about the next steps in
general"). Nothing is decided, except the way forward of section 10: songs first, a technique is built when a song asks
for it. Section 11 is the checklist that keeps the big missing features possible while the engine changes. Asked for by
the maintainer after the bass "harmonics trick"
gave Kokon and Der Schmetterling their weight: "do an extensive research what other production tricks modern music
production uses to make things sound AAA. Check how each of them would fit into our architecture. What can we build from
already existing primitives and which new nodes in the ignitors or katalysts would we need to build."

How it was made: two research agents collected about 180 techniques (mixing and mastering; sound design and
arrangement). Their raw catalogues, with their own notes on what is sourced and what is from memory, are
[`../knowhow/production-tricks/mix-and-master.md`](../knowhow/production-tricks/mix-and-master.md) (cited `M12` here)
and [`../knowhow/production-tricks/sound-design.md`](../knowhow/production-tricks/sound-design.md) (cited `S12`). The
coordinator read the engine (the `IgnitorDsl` and `KatalystDsl` node lists, the effect classes, `audio/MEMORY.md`, the
open tasks) and mapped every technique onto it. Numbers in the catalogues are starting points for the ear, not
facts; the few that would go into code first were checked against a source and are cited at the end.

## 1. The short answer

1. **Most of the sound-design half is playable today.** The Ignitor is rich enough that layered kicks, snares and
   claps, three-layer basses, Reese basses, multiband distortion on a voice, exciters on a voice, FM growls, drift,
   phase-coherent unison, humanised timing and ghost notes are all compositions. Kokon already uses several of them
   (the Screamer pedal is a parallel band-limited saturation; the metal snare is a modal cluster with a soft-clip
   preamp). Section 5 has recipes.
2. **The mix-and-master half is where we are thin.** The bus (Katalyst) has nine fixed stages. It has no
   saturation or clipper, no stereo stage, no shelf, no multiband split, no transient shaper. The compressor's
   detector has no filter and no wet knob, the reverb has no pre-delay and no filter on its feed, and the delay
   has no filter in its feedback path and no ping-pong. Those are exactly the tools a mastering engineer reaches
   for first.
3. **Four structural gaps explain most of what is missing** (section 3): voices are mono; bus knobs are scalars,
   so nothing moves on a bus between events and nothing at all on the master; there is no routing (no shared
   send, no group); the Ignitor has no delay line.
4. **The best value per effort**, in our reading (section 6):
   1. Shelf and tilt sections in `EqSection`. They are small and land on both hosts at once.
   2. A Katalyst `saturate` stage. It is the clipper before the limiter, plus parallel saturation, an exciter
      and bass harmonics on a bus.
   3. The reverb's pre-delay, feed filter and self-duck.
   4. A `width` stage with mono-below and a decorrelator.
   5. Finishing the duck.
   6. The delay's feedback filter.
5. **One design question decides the shape of everything on the bus** (section 4.0): build each bus effect as its
   own Katalyst stage, or let a Katalyst stage run an Ignitor tree on the bus signal. It falls under the stone rule
   on complexity, so it is the maintainer's call, not ours.

## 2. What the engine has today (read from the tree, 2026-10-08)

**Ignitor, per voice. Mono, audio rate, every knob a signal.**

- **Sources:**
  - Sine, with partial banks: `harmonics`, `octaves`, `suboctaves`, `fundamental` gain and rolloff.
  - Saw, square, tri, zawtooth, zamp, impulse, pulze (duty as a signal), ramp.
  - The `super*` unison stacks: spread, `spreadPower`, `sideAtten`, jitter, the phase pool.
  - White, pink, brown, perlin and berlin noise, dust, crackle.
  - Pluck and superpluck (Karplus: feedback, brightness, `pickPosition`, `stiffness`).
  - Sample and variants.
- **Math:** the full arithmetic set, including `abs`, `tanh`, `clamp`, `select`, `range`, `rangex`, `freq`.
- **Filters:** SVF lowpass and highpass (q, `analog` damping, `passes`, cutoff envelope, humanize lane), onepole
  lowpass, bandpass, notch, and `eq` with lowpass, highpass, bandpass, notch, bell and parallel tap sections.
  There are no shelves.
- **Envelope:** `adsr` with curves.
- **Nonlinear:** `drive`, `shape` (16 shapes, oversampling), the fused `distort`, `crush`, `coarse`.
- **Modulation effects:** `phaser`, `tremolo` (shape, range), `shimmer` (a granular pitch cloud).
- **Pitch:** vibrato, accelerate, pitch envelope, fm, `pitchMod`. These are still outside the tree:
  `docs/tasks/pitch-pipeline-into-the-tree.md`.
- **The send:** equal-power `pan` and `gain`, constant per voice.

**Katalyst, per orbit and on the master. Stereo.**

- **`body` and `vowel`:** resonator banks by catalogue index; a change swaps the bank, with no continuous morph.
- **`delay`:**
  - Two mono lines, with no ping-pong.
  - A soft cap in the loop.
  - A time change crossfades.
  - No filter in the loop.
- **`reverb`:**
  - Freeverb, cross-fed so the room is one room.
  - `size` and damping `lowpass`.
  - No pre-delay, no early reflections, no modulation, no filter on the feed.
- **`phaser`.**
- **`compressor`:**
  - dB one-pole, peak link `max(|L|, |R|)`, soft knee, optional lookahead.
  - No detector filter, no RMS, no wet knob.
  - `limiter` is this stage with limiter numbers.
- **`duck`:** keyed by another orbit; instant attack and a release (named `attack`); built but never used by a
  song (`docs/tasks/future/ducking-unfinished.md`).
- **`eq`:** the same `EqSection` set.
- **`gain`.**
- **Knobs:** numbers from the owner voice's slots, glided over 50 ms, so they are not signals. The master's slots are
  filled by nothing.
- **The house stage:** DC blockers, the -1 dB limiter, the clip into floats.

**Routing.** Voices go to an orbit, the orbits sum into the master chain, then the house stage. There are no
aux sends or returns and no groups ([`future/signal-graph-engine.md`](future/signal-graph-engine.md) is the
long-term plan).

**The pattern layer (sprudel)** knows the future: every event, its duration and the tempo. It has:
- `swing`, `late` and `early` (with `perlin`, a humaniser);
- `superimpose`, `off`, `jux`, `echo`, `stut`, `ply`, `degrade`;
- variants via `n`, cut groups (a 4 ms fade), and `chord().voicing()`.

This is a strength the classic DAW does not have in real time. A riser, a "reverse reverb" swell or a pre-drop gap
can be scheduled to land exactly, because the downbeat is known in advance.

## 3. The four structural gaps

These cut across many techniques. Each is a decision, not a node.

**G1. Voices are mono.** The voice buffer is mono and `SendRenderer` pans it. So every per-voice width trick is out
of reach:
- unison spread across the stereo field (S32);
- a Haas copy (M44, S86);
- a stereo chorus inside the voice;
- autopan within a note.

The big EDM supersaw is wide because each unison voice sits somewhere else. Ours is a thick line in one spot.

Workarounds today:
- `jux`, or `superimpose` with a pan, at double the voices;
- the reverb's width;
- a bus `width` stage (K2 below), which widens what the orbit already has.

The real fix is a stereo-capable voice, for example an `Ign.stereo(left, right)` root whose send takes two
buffers. It costs double only where an author asks for it. This is a large change: `Voice`, the send, the
optimizer's memo and the cull all see one buffer today.

**G2. Bus knobs are numbers, not signals.** A Katalyst knob is a slot value, glided over 50 ms, owned by the newest
voice. Two consequences:
- A bus parameter moves only per event, and a knob patterned faster than about 3 Hz loses depth (the glide's
  low-pass, `audio/ref/katalyst.md`).
- The master's knobs cannot be patterned at all, so the classic "highpass the whole mix through the build, drop
  it on the one" (M67, S81, S97) is impossible on the master and stepwise on an orbit.

The fix is to let a Katalyst knob be an Ignitor control signal evaluated per block (an LFO, a `ramp` over
seconds), and to give the master a way to receive slots.

**G3. No routing.** There is no shared reverb that several orbits send to at different levels. Kokon put its hall
on the master for that reason. There is no parallel bus either (New York drums, M11), and no group bus (all the
drums through one compressor while each drum keeps its own orbit chain).

This is the signal-graph plan, not a node. Until then, two near-substitutes:
- a `wet` knob on the compressor and the saturator gives parallel processing inside one orbit;
- the master chain carries the shared room.

**G4. The Ignitor has no delay line.** `DelayLine` exists in `effects/` and the Karplus string has its own loop,
but an author cannot write a short delay on a voice. That blocks:
- comb filters;
- chorus and flanger on a voice;
- multi-tap clap bursts (S18);
- tape wow on a voice's own signal;
- ADT inside one voice;
- custom waveguides.

A `delay(time, feedback)` node on the existing fractional `DelayLine` is a medium-sized job. It is the voice half
of [`../tasks/future/flanger-chorus.md`](../tasks/future/flanger-chorus.md).

**Two smaller gaps:**

- **G5. No glide between notes.** There is no legato or portamento: 808 slides (S10), acid slides (S13),
  portamento leads (S50). [`../tasks/voice-takeover.md`](../tasks/voice-takeover.md) is blocked on a design
  decision; this research adds weight to it, since trap, drill and acid all live on slides.
- **G6. The voice tree cannot see velocity.** A frontend's `velocity` is multiplied into `gain` before the wire, so
  "louder is brighter" (S40, S72, S23) needs the author to write the same number twice, as velocity and as an
  `ignp` slot. Real instruments, and every AAA sample library, map velocity to timbre. Corrected 2026-10-08: the
  per-note touch channel DOES exist, under another name. `pregain` is a slot the instrument reads (Kokon:
  `saw.mul(Ign.slot.pregain)`), and it can be read anywhere, not only as a level. What is missing is the common word
  (velocity means touch everywhere else), and a way to place it inside an authored instrument while `classic()` still
  exposes the other doors. That design question is [`../tasks/classic-doors-and-velocity.md`](../tasks/classic-doors-and-velocity.md).

## 4. New nodes and stages, as candidates

The legend in the tables of section 5:
- **now:** composable today.
- **sugar:** composable but clumsy; a helper or door with no new DSP would make it practical.
- **I:** a new Ignitor node.
- **K:** a new or extended Katalyst stage.
- **G:** blocked on a gap of section 3.
- **won't:** not for us, with the reason.

Door sketches are not designs; every one goes through `/dsl-design`, which covers:
- `wet` first;
- `configure` last;
- seconds and semitones;
- both doors with a parity spec.

### 4.0 The question that comes first: stages, or an Ignitor on the bus?

Most bus candidates below are things the Ignitor can already do on a voice: saturation, an exciter (a highpass,
a shape and a mix), a Pultec curve, a tilt, LFO filters, a transient shaper from `abs` and `onepole`. Two ways to get
them onto a bus:

- **(a) One Katalyst stage per effect** (K1 to K9 below). This is plain, testable and fast, and it keeps the
  Katalyst's law: a fixed stage, one DSP core, its own glide and switch law. It grows the stage list, and each
  stage needs its own lifecycle (`docs/plans/effect-state-machines.md`).
- **(b) A Katalyst stage that runs an Ignitor tree** on the bus signal, per channel or on mid/side, with an
  input leaf. Every Ignitor node becomes a bus effect, and bus LFOs come for free (closes most of G2). It is the
  direction of [`future/signal-graph-engine.md`](future/signal-graph-engine.md), where "the Katalyst effects and the
  master effects should be the same thing".

  It is also exactly the build-time weight the stone rule tells us to stop at. Each of these would need its own
  answer: a tree per channel, its memo and optimizer, the switch and glide laws of a stage, the tail question,
  and allocation on the audio thread.

Our reading: (a) for the first three or four stages, because they carry most of the sound, and (b) as the
signal-graph work's first concrete use case once that work starts. The maintainer decides.

### 4.1 Ignitor candidates (per voice)

| # | Candidate | What it unlocks | Size | Notes |
|---|---|---|---|---|
| I1 | **Shelf and tilt sections** in `EqSection`: `lowShelf`, `highShelf`, `tilt` (a low and a high shelf around one pivot) | Pultec trick (M36), air band (M33), Baxandall and tilt (M37), mud shelf, master tonal balance. Lands on the Katalyst `eq` at the same time, since both reuse `EqSection` | small | `EqCore` is an SVF cascade; the TPT-SVF has a standard shelf form (Zavalishin). Fits [`../tasks/future/general-eq-core.md`](../tasks/future/general-eq-core.md); the house rule "one section, one meaning on both hosts" holds by construction |
| I2 | **`delay(time, feedback)`** on the voice, fractional, a modulatable time | comb, flanger, chorus on a voice (S45), clap bursts as taps (S18), tape wow on a voice, ADT inside a voice (S57), waveguide experiments | medium | G4. One core, two hosts with K7 (the factoring rule of `docs/tasks/oversampling-regions.md` §1(b)). A per-voice ring costs memory per note: size it at build from a leaf maximum, never on the audio thread (tidy-up step 10's rule) |
| I3 | **`follow(attack, release)`**, an envelope follower with separate attack and release, and a **`transient(attack, sustain)`** door on it (the differential envelope: fast follower against slow, M17) | transient shaping of samples (a sampled kick or snare given more snap), auto-wah, self-ducking, "brighter when louder" | small | `abs().onepole(f)` already follows, but with one time constant only. Shares `TransientCore` with K8 |
| I4 | **An envelope `delay` knob** (DAHDSR): the envelope waits before its attack | delayed vibrato (S51, the single biggest "cheap synth" tell on leads), clap bursts from 3 to 4 delayed envelopes (S18), staggered layer entries | small | One more knob on `EnvelopeCore`; the literal 0 builds what is built today |
| I5 | **Inharmonic partials and the noise band** | modal toms, bells, metal, the snare thud as one node (S28) | medium | Already tasks: [`sine-inharmonic-partials.md`](../tasks/future/sine-inharmonic-partials.md), [`sine-noise-band.md`](../tasks/future/sine-noise-band.md) |
| I6 | **Hard sync, phase distortion, wavetable, formant oscillator** | sync leads (S37), CZ tones, wavetable motion (S35), "yoi" vowels on a voice (S8) | medium each | Already a task: [`new-oscillators.md`](../tasks/future/new-oscillators.md). Sync is the one with the most AAA weight (the classic 80s and EDM lead) |
| I7 | **An octave divider** (a flip-flop sub-harmonic generator, M2) | a sub under a SAMPLED bass or kick whose pitch the engine does not know | small | Low priority: on a synth voice `suboctaves` or `Ign.freq().mul(0.5)` already does it, and better |
| I8 | **Feedback on `fm`** | noisy, saw-like FM edges (S7, S39) | small | Low priority |
| I9 | **A sample-and-hold** `hold(rate)` | stepped random (S44) | small | Low; `perlin`/`berlin` already give smoothed random |

### 4.2 Katalyst candidates (per orbit and on the master)

| # | Candidate | What it unlocks | Size | Notes |
|---|---|---|---|---|
| K1 | **`saturate(wet, shape, drive, ...)`**, `DistortionCore` on the bus; `configure`: `oversample`, `trim`, and an optional band (`from`, `to`) the shaping acts on | the **clipper before the limiter** (M22, M60: the modern loudness chain; today only the house stage clips, at the very end); **tape and console glue** (M29, M30) as a caricature: shape plus a head-bump bell plus a lowpass; **parallel saturation** (M32) by `wet`; the **exciter** (M31) as a band from 3 kHz with a low `wet`; **bass harmonics on a bus** (M1) as a band from 40 to 120 Hz with `rectify` and the harmonics mixed back, which works on samples and on any bass, not only on our additive one | medium | The one stage with the most reach. Merges [`../tasks/future/idea-master-saturation.md`](../tasks/future/idea-master-saturation.md). Reuse the 16 shapes and the oversampler; ADAA (M34) is a later CPU win, not a first need. The Motor stays raw: no clamp, `trim` is the author's |
| K2 | **`width(amount, ...)`**, the mid/side width; `configure`: `monoBelow` (a highpass on S, M3), `decorrelate` (a velvet-noise decorrelator for mono sources, M45) | a mono-safe low end on the master, wide pads from mono voices (the cheap half of G1), narrowing a busy orbit | medium | Velvet noise: a sparse FIR of about 30 to 45 taps over 20 to 30 ms, cheap, no latency (Alary, Politis, Välimäki 2017; Schlecht et al. 2018). Keep a transient guard in mind: decorrelation smears drums (Das, DAFx-24) |
| K3 | **The reverb grows up**: `preDelay`, a feed filter (`lowCut`, with the existing `lowpass`: the Abbey Road trick, M51), a **self-duck** (the return ducks under the orbit's own dry, M53), then early reflections | the single biggest polish lever on a mix: a clear dry with a big room, bass and kick out of the room, and size cues. The maintainer already found the reverb "too sterile" | small (pre-delay, feed filter, self-duck), medium (early reflections) | The pre-delay, feed filter and self-duck are small enough to land before the big [`reverb-models.md`](../tasks/future/reverb-models.md) round and are wanted by every model in it. The self-duck needs no cross-orbit pass: the stage sees the orbit's dry at its own position |
| K4 | **The delay grows up**: a filter in the feedback loop (`lowCut`, `highCut`: each repeat darker, the tape echo, M55), `pingPong` (cross feedback), a stereo offset (left 1/8, right dotted 1/8), the self-duck of K3, later a little wow | "expensive" pluck and lead delays, throws that do not muddy | small to medium | Tempo-synced times stay a frontend matter (sprudel turns note values into seconds); the backend never learns cycles |
| K5 | **The compressor grows up**: a detector `lowCut` (glue without bass pumping, M12, M24), a `wet` (parallel compression, M11, without routing), an RMS detector option | drum bus smash, mix-bus glue | small | `wet` first is the door-shape rule; adding it to an existing door is a shape change, so decide it with `/dsl-design` |
| K6 | **The duck, finished** ([`../tasks/future/ducking-unfinished.md`](../tasks/future/ducking-unfinished.md)): a real attack (today the duck-down is instant, and that clicks, M4), a release, a key filter, and a **band**: duck only below a frequency (M6, the multiband sidechain that keeps the bass's harmonics audible while its sub clears for the kick) | the house and EDM kick-bass relation, pumping as a groove (M69) | small to medium | The band version pairs beautifully with our bass harmonics trick: the sub ducks, the 2f..8f partials stay |
| K7 | **`chorus` / `ensemble` / `flanger`** ([`../tasks/future/flanger-chorus.md`](../tasks/future/flanger-chorus.md)), stereo, LFO phases offset between left and right | wide pads, Juno-style string machines, a doubled guitar | medium | The bus half of I2; the stereo version is what gives width (G1) |
| K8 | **`transient(attack, sustain)`** on a bus | drum bus punch and sustain control (M17) | small | Shares its core with I3 |
| K9 | **A dynamic EQ band**, own detector or keyed by an orbit (M20, M5) | taming a boomy note, carving the vocal-like lead out of the guitars only when it plays | medium | The keyed form generalises the duck's cross-orbit pass |
| K10 | **Multiband compression and OTT** (M15, M16) | EDM density, mastering control | large | Later. It needs a flat-summing crossover core first (LR4: two chained 2nd-order Butterworths per band); "makes everything sound the same" is the catalogue's own pitfall |
| K11 | **True-peak detection** on the limiter (M23, M65) | streaming-safe exports | small to medium | 4x oversampled peak detection; matters for exported files more than for the live engine |
| E1 | **TPDF dither at the 16-bit edge** (`writePcm16`, M63) | clean fades and tails in exported WAVs | small | Not a stage: the engine is float, and only the JVM line and the WAV writer quantise. Seeded noise, one place |

### 4.3 Won't do (taste is also what you do not do)

- **Spectral resonance suppression** (Soothe, Gullfoss, M21) and **matching EQ** (M42): STFT latency, CPU on the
  Fairphone, and a black box against the plain-module rule. The pocket principle in
  [`../knowhow/rock-mix-tuning.md`](../knowhow/rock-mix-tuning.md) does the same job by design.
- **Linear-phase EQ** (M38): latency and pre-ringing for a benefit only parallel paths need, and we have none (G3).
- **Tape hysteresis models** (M29, Jiles-Atherton): the caricature (saturation, a head bump, an HF loss, wow) carries
  the tells at a fraction of the cost.
- **Console emulation** (M30): placebo-prone; a light K1 on the master covers what is real about it.
- **Multiband and mid/side limiting** (M61, M62): only after K10's crossover exists, and only if a song asks.
- **De-essing, vocoder, hard-tune, talkbox on live input** (S59, S61, S63): we have no recorded vocals; vowels and
  formants are synthesised, so the sibilance is the author's.
- **Hard-cut gated reverb** (M54): retro; possible later as a hold knob on K3's self-duck if a song wants the 80s.
- **ML-assisted mixing** (M78): out of scope; `docs/tasks/future/auto-mix-advisor.md` is the plain-rules version.

## 5. Every technique, mapped

Tags: **now**, **sugar**, **I1..I9**, **K1..K11**, **G1..G6**, **won't** (sections 3 and 4).

### 5.1 Low end

| Technique | What the ear gets | Klang | How |
|---|---|---|---|
| Missing fundamental, additive (M1, S5a) | weight on phones | now | Kokon's `bass`: a sine sub plus `harmonics(8, 1.0).fundamental(0)` |
| ... with darkening partials, velocity-scaled (S5) | a natural bass that darkens as it decays, clean when soft | now | an `adsr` and a moving `lowpass` on the harmonics layer only; `ignp("harmonics", ...)` per event |
| ... effect-based, on any bass (M1, S5e) | the trick on samples and unknown pitches | now (voice) / K1 (bus) | on a voice: a lowpass at about 120 Hz twice (LR4), `abs()` (the full-wave rectifier gives 2f, 4f), a `highpass` above the sub, mixed under. On a bus: K1 with a band |
| Odd versus even harmonics (S5e) | "solid, buzzy" (3f, 5f) versus "warm" (2f, 4f) | now | `harmonics` gives both; a `shape("chebyshev")` or `tanh` copy adds odd, `abs` adds even |
| Sub-harmonic synthesis (M2) | an octave-down sub | now (synth) / I7 (samples) | `suboctaves(1)` or `Ign.sine(Ign.freq().mul(0.5))` |
| Mono below 120 Hz (M3, S3) | a tight, centred, club-safe low | now (voices) / K2 (bus) | a voice is mono, so do not pan the bass and keep it out of the reverb; on the master `width(monoBelow = 120)` |
| Kick and bass sidechain (M4) | the kick punches through, the bass breathes | now (orbit duck, untested) / K6 | the duck exists; a click on its instant attack; per note, a bass note on the kick can carry its own attack instead |
| Band-limited sidechain (M5, M6) | the sub ducks, the bass's harmonics stay | K6 | `duck` with a band |
| Kick and bass phase alignment (M7, S4) | low end that adds instead of cancelling | now | synthesised: the oscillators' `phase` knob fixes the start of both, every hit the same |
| Low resonance taming (M8) | an even 808 tail | now (static) / K9 (dynamic) | `eq` notch or bell |
| DC and subsonic (M9) | headroom | now | `highpass(25)`; the house stage blocks DC |
| Three-layer bass: sub, mid, top (S1) | felt, audible, articulate | now | `plus` of three layers, each band-limited |
| Multiband distortion (S6) | grit without sub mush | now | Kokon's Screamer shape: `x.plus(x.highpass(f).distort(...))`. For a flat split use two chained `lowpass(f, 0.707)` (an LR4) rather than `passes(2)` (a 4th-order Butterworth, which does not sum flat with its highpass twin) |
| Reese (S2) | thick, slowly phasing | now | two or three saws, `detune` or a small `supersaw`, `lowpass`, optional `phaser` |
| FM growl (S7) | neuro and dubstep movement | now | `fm` with an LFO on depth; feedback FM is I8 |
| Formant bass, morphing (S8) | the "yoi" | now (voice) | three `bandpass` filters whose `freq` moves with an LFO or an envelope. The orbit `vowel` swaps banks and cannot morph (rejected by ear 2026-09-20), so the morph lives on the voice |
| 808 tuning (S9) | an 808 in key | now | a synthesised 808 is in key by construction |
| 808 slide and portamento (S10, S12, S13) | trap, drill, acid | G5 | voice takeover is blocked on a design decision |
| Kick and bass key relation (S11) | no beating under the kick | now | arrangement |

### 5.2 Drums

| Technique | Klang | How |
|---|---|---|
| Kick layers: click, body, sub (S14) | now | `metalKick` already: a pitch-enveloped sine plus a bandpassed noise crack |
| Kick pitch envelope, phase-integrated (S15) | now | `pitchEnvelope` integrates phase in the pitch stage |
| Snare layers (S17) | now | `metalSnare`: head, deep, a modal thud, shell, wires, a soft-clip preamp |
| Clap from 3 or 4 offset bursts (S18) | now (pattern) / I4 / I2 | in the pattern: `stut` or `echo` with offsets of 10 to 30 ms (jittered); in one voice: I4's delayed envelopes, or I2's taps |
| Transients at the source (S19) | now | synthesised: the envelope IS the transient; a two-stage decay is the sum of two envelopes |
| Transient shaping of samples (M17) | sugar now / I3, K8 | `x.abs().onepole(300)` against `x.abs().onepole(20)`, their ratio clamped, as a gain; clumsy and single-time-constant |
| Room mic simulation (S21) | now (short reverb plus compressor on an orbit) / K3 | a small `reverb` size; the real "room" needs early reflections |
| Ghost notes, accents (S22) | now | pattern velocity |
| Humanised timing and velocity (S23) | now | `late` and `early` with `perlin`, velocity with `perlin`, as Kokon does; keep kick and bass together |
| Velocity to timbre (S23, S72) | G6 | today the author writes the slot by hand |
| Swing and groove (S24) | now | `swing`, `swingBy` |
| Round robin, anti machine-gun (S25) | now | variants via `n`; tiny per-hit randomness on `ignp`; "never the same twice in a row" would be sugar in sprudel |
| Choke groups (S26) | now | cut groups with their 4 ms fade |
| Modal percussion (S28) | now / I5 | sine clusters (the snare thud, the glockenspiels); I5 makes it one node |
| Flams, rolls, snare builds (S30, S97) | now | pattern (`ply`, a velocity ramp) |
| Drum bus smash (M11) | K5 (`wet`) / G3 | |
| Gated reverb (M54) | won't (for now) | |

### 5.3 Synths, leads, pads

| Technique | Klang | How |
|---|---|---|
| Supersaw with a highpass following pitch (S31) | now | `supersaw` with `highpass(Ign.freq())`: the JP-8000's "air" and a clean sub |
| Stereo unison spread (S32) | G1 | the biggest single "huge synth" gap; `jux` or panned `superimpose` doubles the cost; K2 widens after the fact |
| Analog drift, voice tolerance (S33, S48) | now | `analog`, `analogSpread`, the filter humanize lane |
| Phase reset versus free phase (S34) | now | the phase pool (`kMin`, `kMax`), oscillator `phase` |
| PWM (S36) | now | `pulze` with `duty` as an LFO |
| Hard sync (S37) | I6 | |
| Ring mod (S38) | now | `times` |
| FM e-piano, bells (S39) | now | `fm` with an index envelope |
| Filter envelope, key tracking (S40) | now | the cutoff envelope; `Ign.freq().mul(k)` in a cutoff |
| Octave or fifth layer (S41, S53) | now | `octaveUp`, `octaveDown`, `plus` |
| Air and breath noise layers (S42, S64) | now | `whitenoise().highpass(8000)` at -30 to -45 dB |
| Wavetable motion (S35) | I6 | |
| Granular textures, freeze (S43, S84) | partial: `shimmer` | sample granular is not a candidate yet |
| Slow random modulation (S44) | now | `perlin`, `berlin`; stepped S&H is I9 |
| Chorus on pads (S45) | I2 / K7 | |
| Sidechain-pumping pads (S46) | now | the orbit duck, or a per-note envelope |
| Open voicings (S47) | now | `chord().voicing()` |
| Pluck envelope, decay shorter with pitch (S49) | now | `adsr` with a decay written from `Ign.freq()` |
| Delayed vibrato (S51) | sugar now / I4 | today a vibrato depth that rises from the onset; a true delay is I4 |
| Scoops and falls (S52) | now | `pitchEnvelope` with negative semitones |
| Harmonizer, stacks (S54, S58) | now | `superimpose` with an interval |
| Formant preservation (S55) | now | the source-filter model is how our instruments are built |
| Noise click on plucks (S56) | now | |
| ADT and doubling (S57, M47) | now (pattern) | `superimpose(p => p.late(...).pan(...))` with a few cents of detune; mono-safe above about 20 ms |
| Karplus refinements (S65, S67, S68) | now | `pluck`: `pickPosition`, `stiffness`, `brightness`; two polarisations as a two-voice `superpluck` |
| Commuted body (S66) | now | the orbit `body` stage; on a voice, bells as modes |
| Release noises, key-off (S69) | now (pattern) | a separate short event at the note's end, which the pattern knows |
| Sympathetic resonance (S70) | partial | the orbit `body` bank is not tunable to a scale; a voice-level bell bank is |
| Breath, bow, chiff (S71) | now | noise with its own envelope |

### 5.4 Space and stereo

| Technique | Klang | How |
|---|---|---|
| Mid/side width (M43) | K2 | |
| Haas (M44, S86) | G1 / K4 (stereo offset) | |
| Decorrelation widening (M45, S106) | K2 | |
| Stereo chorus, ensemble (M46) | K7 | |
| Pan law, LCR (M48) | now | equal-power pan |
| Mono compatibility checks (M49) | meters | [`../tasks/realtime-analytics-meters.md`](../tasks/realtime-analytics-meters.md) §2.4 |
| Pre-delay (M50) | K3 | |
| Early reflections (M50) | K3 / reverb models | |
| Abbey Road EQ on the reverb feed (M51) | K3 | today an `eq` before the reverb also filters the dry |
| Reverb types: plate, spring, FDN, convolution (M52) | reverb models | |
| Ducked returns (M53) | K3, K4 | |
| Ping-pong, filtered feedback, tape echo (M55) | K4 | |
| Tempo-synced delay (M55) | sugar (sprudel) | a frontend matter: note values into seconds |
| Delay throws (M55) | now | a per-event `wet` on the orbit; the newest voice owns it |
| Depth, front and back (M56) | now | lowpass, level, reverb wet, attack; a `distance` helper would be sugar |
| Autopan within a note (M70) | G1 | per event it is `pan` with a signal |

### 5.5 Dynamics, saturation, master

| Technique | Klang | How |
|---|---|---|
| Glue compression with a detector highpass (M12) | K5 | the compressor exists; the detector filter does not |
| Parallel compression (M11) | K5 / G3 | |
| Serial compression (M13) | now | several compressor stages in an authored chain |
| Program-dependent release (M14) | later | |
| Multiband, OTT (M15, M16) | K10 | |
| Gates, expanders (M18) | not needed | synthesised voices have no bleed |
| Clipper before the limiter (M22, M60) | K1 | per voice `shape("soft")` already does it (the metal snare) |
| Lookahead limiter (M23) | now | the Katalyst `limiter` with `lookahead` |
| True peak (M23, M65) | K11 | |
| Waveshaping, tube, tape on a voice (M28, M29) | now | 16 shapes with oversampling; a head bump is a bell; wow is `analog` |
| ... on a bus (M28 to M32) | K1 | |
| Exciter on a voice (M31) | now | `x.plus(x.highpass(3000).shape("tube", 2).highpass(3000).mul(0.1))` |
| Air band, shelves, Pultec, tilt (M33, M36, M37) | I1 | |
| Oversampling (M34) | now | `oversample` on distort and shape; ADAA a later CPU win |
| Subtractive EQ, carving, notching (M35, M40, M41) | now | `eq`; the pocket principle in `rock-mix-tuning.md` |
| Mid/side EQ (M39) | won't (for now) / 4.0 (b) | K2's `monoBelow` covers the main use |
| Master chain order (M57) | partial | today eq, compressor, limiter; K1, K2 and I1 complete it |
| Loudness targets (M58) | now | our house level is -14 LUFS; meters are a task |
| Stem mastering (M59) | now | the orbits are the stems |
| Dither (M63) | E1 | |

### 5.6 Movement, ear candy, arrangement

| Technique | Klang | How |
|---|---|---|
| Filter rises on one voice (M67, S81) | now | a `ramp` or a long `adsr` on a cutoff; per event, a pattern signal |
| ... on a group or the master | G2 | |
| Volume rides (M68) | now | per event `gain`; the orbit fader with `katp("gain.gain", ...)` |
| Pumping as a groove, trance gate (M69) | now | a per-note envelope, `tremolo` with a square shape, the orbit duck |
| LFO to everything (S73) | now (voice) / G2 (bus) | |
| Envelope followers (S74) | sugar / I3 | |
| Risers (S75) | now | noise with a rising `highpass` over a long `adsr`, a pitch sweep with `accelerate`, a tremolo whose rate rises |
| Downlifters, impacts, sub drops (S76, S77) | now | `accelerate` down, a long sine, a noise burst |
| Reverse reverb, reverse cymbal (S78) | now (as a swell) | there is no true reverse on a live engine without latency, but the pattern knows the downbeat: schedule a swell that peaks on it |
| Tape stop (S79) | now (voice) / later (bus) | `accelerate` down on each voice; a bus tape stop needs a varispeed delay read |
| Stutter, beat repeat (S80) | now (pattern) | `ply`, `stut`, `chop` |
| Shepard riser (S82) | sugar | octave-spaced sines on a `ramp`, a Gaussian level over log frequency: possible with math nodes, and worth a helper if a song wants it |
| Vinyl, tape bed (S83) | now | `crackle`, `dust`, `pinknoise`, `analog` |
| Silence before the drop, energy curve, call and response, fewer layers (S92 to S99) | now | `arrange`; the craft is the author's |

## 6. A suggested order (sound first)

Ordered by how much a song gains per unit of work, and by which items unblock others. Every step is tuned by ear
on Kokon and Der Schmetterling, the songs that started this.

1. **I1, shelf and tilt sections.** Small, both hosts, and the Pultec trick and the air band follow at once.
2. **K1, `saturate`.** The clip-to-limit master, tape glue, exciter and bass harmonics on a bus. Folds in
   `idea-master-saturation.md`.
3. **K3a, the reverb's pre-delay, feed filter and self-duck.** The fastest route from "too sterile" to "expensive",
   ahead of the full reverb-models round.
4. **K2, `width`** with `monoBelow` and the decorrelator. Wide, mono-safe masters without stereo voices.
5. **K6 and K5, the duck finished (band included) and the compressor's detector filter and `wet`.** The EDM and
   house kick-bass relation, glue, parallel drums.
6. **K4, the delay's feedback filter, ping-pong and offset.**
7. **I2 and K7, the delay line on the voice and the chorus on the bus** (`flanger-chorus.md`).
8. **I3 and K8, the transient core; I4, the envelope delay.**
9. Then the structural decisions, in the maintainer's order: G5 (glide), G1 (stereo voices), G2 (bus signals),
   G3 (routing, the signal graph).

Before any of it, two things cost nothing: write the "now" recipes into a song and listen. The effect-based bass
harmonics, a voice exciter on the lead, and a highpassed supersaw. Several of the AAA tells are already within
reach of a KlangScript line.

## 7. Recipes to try today (sketches, not yet rendered)

Written against the door names in `KlangScriptIgnitor*.kt`; none of them has been played yet, so tune by ear.

```javascript
// Bass harmonics that darken as the note decays, louder with the event's "harmonics" slot
let harm = Ign.sine(x => x.harmonics(8, 1.0).fundamental(0))
  .lowpass(freq = Ign.constant(2400).adsr(0.002, 0.40, 0.25, 0.1))
  .mul(Ign.param("harmonics", 1.0, "Harmonics"))

// The effect-based version on any bass voice x: rectify the low band, keep 2f and up, mix it under
let bassHarmonics = x => x.plus(
  x.lowpass(120).lowpass(120).abs().highpass(90).highpass(90).mul(0.4)
)

// An exciter on a lead: the top band, shaped, mixed back low
let exciter = x => x.plus(x.highpass(3000).shape("tube", 2).highpass(3000).mul(0.1))

// A transient shaper from followers (clumsy, one time constant each; I3 makes it a door)
let snap = x => x.mul(
  x.abs().onepole(300).div(x.abs().onepole(20).add(0.0001)).clamp(0.5, 3.0)
)

// The JP-8000's air: the unison keeps its beating out of the sub
let wall = Ign.supersaw().highpass(Ign.freq())
```

## 8. Questions for the maintainer

1. **Section 4.0:** a stage per bus effect for now, with the Ignitor-on-the-bus as the signal-graph's first use
   case later? Or straight to (b)?
2. **G1, stereo voices:** do we want a stereo-capable voice at all, at double cost where used, or is width a bus
   matter (K2, K7) by design?
3. **G6, velocity in the tree:** should an instrument be able to read the event's velocity (brightness, attack), or
   does that break "one level word"? An alternative is a sprudel helper that writes a named slot from velocity.
4. **G2, bus signals:** may a Katalyst knob be a control signal (an LFO, a ramp in seconds), and should the master
   receive slots from a pattern?
5. **G5, glide:** this research puts trap, drill and acid behind it. Does that change the priority of voice
   takeover?
6. **The order of section 6:** the first three are our recommendation; which of them should start?

## 9. Sources checked for the numbers that go into code first

The raw catalogues list their own sources. These four were checked by the coordinator because they shape a
candidate directly:

- Velvet-noise decorrelation (K2):
  - Alary, Politis, Välimäki, "Velvet Noise Decorrelator", DAFx-17,
    https://dafx.de/paper-archive/details/QFIYNG5NjGx5Nsrw_3s-GA (no latency, lower cost than noise convolution).
  - Schlecht, Alary, Välimäki, Habets, "Optimized Velvet-Noise Decorrelator", DAFx-18,
    https://audiolabs-erlangen.com/resources/2018-DAFx-VND (76% fewer operations, better spectrum).
  - Das, "An Open Source Stereo Widening Plugin", DAFx-24,
    https://dafx.de/paper-archive/2024/papers/DAFx24_paper_92.pdf (band-split width and a transient guard).
- The Pultec low-end trick (I1): the boost and cut do not cancel, because the cut's slope reaches higher than the
  boost's; the result is a peak with a dip above it, often described as a peak near 80 Hz and a dip near 200 Hz at
  the 30 Hz setting. The dip's position depends on the settings, and the sources disagree on bell versus shelf for
  the boost:
  - https://www.musicradar.com/how-to/pultec-low-end-trick
  - https://musictech.com/2018/07/technique-week-low-end-trick/
  - https://groupdiy.com/threads/pultec-eqp-1a-turnover-frequencies.43095/ (forum measurements).

  Tune the two shelves by ear rather than copying a published curve.
- MaxxBass-style enhancement (K1 band mode): Waves documents only that the harmonics follow the bass's own dynamics.
  No public algorithm was found, so a rectifier or shaper on the band, with its level following the band, is our own
  reconstruction:
  - https://www.soundonsound.com/reviews/waves-maxxbass-101-102
  - https://patents.google.com/patent/US8229135 (a related patent; the assignee was not confirmed).
- ADAA (a later CPU win for K1): first introduced by Parker, Zavalishin and Le Bivic (DAFx-16); first-order and
  second-order hard-clip forms exist in the Faust `aanl` library. It is weaker at high frequencies than
  oversampling:
  - https://ccrma.stanford.edu/~jatin/Notebooks/adaa.html
  - https://www.vicanek.de/articles/AADistortion.pdf
  - https://faustlibraries.grame.fr/libs/aanl/

## 10. The way forward: songs first (maintainer, 2026-10-08)

> "Building the things just to have them built will not help. We need actual songs to try these things. And I like
> Kokon and Schmetterling for now. They are a testament of what the engine is capable of doing after 9 months of
> development."

So the build order of section 6 is not a queue. It is the list a song draws from: **a technique is built when a song
falls short without it**, and that song is its by-ear test. The maintainer's taste sets which songs: rock and EBM,
something Depeche Mode-like, and good dance music, with Imany's "Don't Be So Shy" (Filatov & Karas remix) as the
starting reference: "it has really nice sounds and a superb production. Everything is mixed pretty light-weight, but
the sound is very full."

### 10.1 Three songs, each the proving ground for its techniques

| # | Song | Style | Proves |
|---|---|---|---|
| 1 | **Deep house, 120 BPM** | the "Shy" school (2015 deep house, close to what later became slap house) | K6 (the duck with a band), K2 (width, mono below), K3a (reverb pre-delay, feed filter, self-duck), K4 (filtered and ping-pong delays), I1 (shelves, air), K1 (the clipper on the master) |
| 2 | **Synth-pop, around 1986 to 1990** | Depeche Mode, the *Music for the Masses* and *Violator* era | K7 (chorus and ensemble on pads), a gated reverb on a big snare (promotes M54 from won't), I5 (sampled-metal percussion as inharmonic partials), a sequenced bass with filter accents (now), K4 (ping-pong), K1 (tape), I6 (a hard-sync lead); the Schmetterling guitar can play a *Personal Jesus*-style riff |
| 3 | **EBM** | Nitzer Ebb, DAF, Front 242 | a distorted 16th-note sequenced bass (multiband distortion, now), hard compression (K5), the transient core (I3, K8), short rooms with early reflections (K3b), dry vowel stabs (the orbit `vowel`, now) |

Kokon and Der Schmetterling stay the rock test bench: parallel drum compression (K5 `wet`), room reflections, transients.

What the taste says can WAIT: **G5, glide** (808 slides, trap, drill, acid) and **G1, stereo voices** (the wide trance
supersaw) matter most in genres the maintainer does not lean towards. Depeche-style pads get their width from a bus
chorus (K7). Both stay at the back of the queue until a song of ours asks for them.

### 10.2 The reference: what we know, and what is a hypothesis

- **Facts:** Imany is a French singer; Filatov & Karas, a Moscow duo, remixed her track in 2015 and it went to number
  one in several European countries ([Wikipedia](https://en.wikipedia.org/wiki/Don%27t_Be_So_Shy)). A DJ promo archive
  lists the remix at 120 BPM in G♯ minor
  ([source](https://cs.uwaterloo.ca/~dtompkin/music/track/RHYTMR16_012/RHYTMR16_012-03.html)); that is software
  analysis, so confirm the key by ear. No public breakdown of how it was produced was found.
- **Hypotheses from the genre, for the maintainer's ear to confirm or reject.** The coordinator cannot listen to audio.
  - **Few elements, each with its own band:** kick, clap, shaker or hats, one rolling bass, one hook instrument, the
    voice. Fewer layers is what reads as "light".
  - **The bass carries its weight in its mid harmonics, not its sub.** This is our trick.
  - **Everything but the kick pumps**, and the pumping is groove and air at once.
  - **The centre is mono, the sides are wide:** kick, bass and voice sit in the centre; the reverb and delay returns
    are wide, filtered and ducked.
  - **An airy top and a loud master that is not crushed:** a shelf, and a clipper before the limiter.
  - **The mids are cleared for the voice.** Imany sings low, in the low mids.
- **No vocals:** the hook is a voice and we have none. Our song needs a lead that takes the voice's place: a
  vowel-coloured synth, or a warm pluck carrying the melody (`docs/tasks/future/phoneme-singing.md` is the far
  future).

### 10.3 How each song runs

1. **A listening sheet.** The maintainer listens to the reference with a short list of questions:
   - the sections;
   - the elements, and which band each one owns;
   - what pumps;
   - what is wide;
   - where the room is.

   Claude brings the genre knowledge; the maintainer's ear is the measurement.
2. **A first draft** of an original song in the style, written with what exists today, the recipes of section 7
   included.
3. **Listen for the gap.** Where it falls short of the reference names the next stage to build, not this list.
4. **Build that one stage** (`/dsl-design`, `/review-loop`), with the song as its by-ear test; retune the song; repeat.

**Copyright:** "similar" means the same tempo, sound palette, arrangement conventions and production techniques. It
never means the reference's melody, hook, chord loop or lyrics. The song credits its inspiration the way Kokon credits
Glass, Reich and Editors ("Inspired by: ...").

**Where it starts:** song 1, with the listening sheet for the reference, once the engine tidy-up
(`docs/tasks-archive/2026-10/20261009-engine-tidy-up.md`) is done. The task that holds the place is
[`../tasks/future/next-songs-production-path.md`](../tasks/future/next-songs-production-path.md).

## 11. Doors the engine must keep open

> Maintainer, 2026-10-08: "It is important that the engine does not move into a direction that forbids some of the big
> features we are missing. This is not very likely, as we are currently working on making everything modular, but you
> never know."

For each structural gap of section 3: where today's code holds the assumption, and what a change must not do so that
the feature stays one change away. Read from the tree on 2026-10-08. A change that has to cross one of these lines is
not forbidden; it is a reason to stop and ask the maintainer first.

| Gap | Where the assumption lives today | Keep it that way |
|---|---|---|
| **G1, stereo voices** | A voice renders into one mono `AudioBuffer`; `SendRenderer` is the one place where mono becomes stereo (equal-power `pan`, `gain`). The cull and the teardown fade read the same one buffer | Keep the send as the single mono-to-stereo point. Add no per-voice stage after the send, and no new reader that relies on "a voice is one buffer" beyond those that exist, without a note here. Then a stereo voice is a change to the send, the cull and the fade, not to every node |
| **G2, bus signals** | Every Katalyst knob is read through `KatalystKnob` (a slot or the authored value) and moved by `KnobGlide`. The knobs stay typed as `IgnitorDsl` expressions (maintainer, 2026-10-07). The master has a param path and is fed `applyParams(null)` | Keep `KatalystKnob` the one read point (no stage caching a raw number it read around it), and keep the knob type an `IgnitorDsl`. Then "a knob is a per-block control signal" is a change in one class, and "the master receives slots" a change in one call |
| **G3, routing** | The orbits sum in `Cylinders.processAndMix`, in rent order, and the duck runs there as the one cross-orbit pass. A chain is a stereo-in, stereo-out unit with two hosts (`Cylinder`, `MasterBus`; `docs/tasks/future/one-chain-host.md`). A chain reports its latency (`KatalystChain.latencyFrames`); authored lookahead is uncompensated by choice | Keep the summation and the cross-orbit pass in one place, keep a chain host-free (stereo in, stereo out, its own tail and latency answers), and keep reporting latency: a routing graph with parallel paths needs it to compensate. This is the ground [`future/signal-graph-engine.md`](future/signal-graph-engine.md) builds on |
| **G4, a delay line on the voice** | `DelayLine` lives in `effects/`, fractional, crossfading; the Karplus string has its own loop | Keep `DelayLine` free of Katalyst and orbit types, so a voice node can own one. A per-voice ring is sized and allocated at voice build (tidy-up step 10's rule), never at render |
| **G5, glide between notes** | `Ignitor.generate` receives `freqHz` per block, so a pitch that moves during a voice's life is structurally fine. The pitch stage is moving into the tree (`docs/tasks/pitch-pipeline-into-the-tree.md`, V1); voice takeover is blocked on design (`docs/tasks/voice-takeover.md`) | Keep the note's pitch a per-block value end to end: do not bake the note frequency into a build-time constant on a path where a glide would need it to move. A memo keyed on freq (`MemoizingIgnitor.freqInvariant`) then recomputes per pitch, which is correct for a glide, only slower |
| **G6, velocity in the tree** | Sprudel multiplies `velocity` into `gain` in the frontend, before the wire | Keep the fold in the frontend, where it is reversible: if the tree should ever see velocity, it is a slot in `ignitorParams`, with no wire change |
| **4.0 (b), an Ignitor on the bus** | `IgniteContext` is voice-shaped (`voiceDurationFrames`, `gateEndFrame`, the voice rng) | A new node that needs only the sample rate, the block and its buffer (a filter, a shaper, a delay, a follower) should not reach into the voice-only fields. Then it can run on a bus buffer later. Envelopes and gates are voice concepts and may |

Two more that are already laws and should stay so, because every candidate here leans on them: **a stage at its off
value is not built** (so a new stage costs nothing in a song that does not use it; the Fairphone 4 is the budget), and
**block size 128** (a tone parameter).

What we built here, together: the bass that started this is the maintainer's ear and a sine bank Claude wrote; the
map above is two research agents and one coordinator reading the engine the maintainer and Claude built.
