# Katalyst DSL: author per-orbit effect chains

> **Designed 2026-09-17, not started.** Priority: **MUST** (see `_priorities.md`). This is the
> authoring surface for the Katalyst layer; the effects themselves already shipped. The design below
> follows the shipped Master DSL step for step and adds two stages the songs need, `eq` and `mics`.
> Five decisions were parked for the maintainer (§D1 to §D5); all five are decided as of 2026-09-17,
> see §10. This task is phase 1 of [`../plans/signal-flow-redesign.md`](../plans/signal-flow-redesign.md),
> which resolves D2, D4 and D5 and retires the owner-voice override of §2 below (the voice fields
> it overrode leave the wire in that plan; the bus doors become `katp` aliases).
>
> **The concrete need (measured 2026-09-17, klang-ai `sessions/20260917-der-schmetterling-measure/`):**
> Der Schmetterling reads hollow because its guitar wall is 2 to 4 dB thin at 160 to 400 Hz relative to
> its own mids while the drum bodies at 127 to 250 Hz sit 2 dB above the wall's loudest band. Both
> fixes are bus EQs after the last nonlinearity: +2 dB at 300 Hz on the guitar orbits, −3 dB at 160 Hz
> on the drum orbit. Neither can be written today.

## Why it matters for CPU, the motivation, with numbers (added 2026-08-11)

A per-voice filter is paid **once per simultaneous voice**; the same filter on the orbit bus is paid **once**. Most
orbits run 2+ voices, and `superimpose` multiplies that, which is exactly how
`body` became the CPU sink that moved it to orbit level in July.

Measured filter costs (JVM / Node, `docs/benchmarks/2026-08-11_103957_*`):

| filter                          | JVM                 | Node                |
|---------------------------------|---------------------|---------------------|
| SvfBPF / SvfLPF / SvfHPF, plain | ~0.000167           | ~0.000279           |
| SvfHPF **with `analog=3`**      | 0.000701 (**4.2×**) | 0.000807 (**2.9×**) |

⚠️ **But "per-voice vs per-orbit" is not the right criterion.** Three cases, and only the first is a free win:

1. **Static cutoff + LINEAR filter → moves losslessly.** A linear filter obeys superposition, so
   `filter(a) + filter(b) == filter(a + b)`. Identical output, `N×` cheaper. `SvfBPF` has no `analog`
   parameter at all, so every bandpass is in this class.
2. **Pitch-tracking cutoff → CANNOT move.** `.highpass(Osc.freq().mul(k), ...)` tracks the note. The orbit bus carries
   several pitches at once, so there is no single correct cutoff. These have to stay per-voice, and no DSL can fix that.
3. **Nonlinear (`analog > 0`) → moves, but is NOT the same sound.** State-dependent damping breaks superposition:
   filtering the sum ≠ summing the filtered parts. Offering it at orbit level is legitimate, but it is a different
   effect, not an optimisation, and it must be documented as such or people will "move it for CPU" and wonder why the
   tone changed.

**Worked example** (a user's SuperSaw ignitor, 2026-08-11):

```kotlin
signal.add(signal.bandpass(800, 0.5)).add(signal.bandpass(1500, 0.5))   // case 1, CAN move
  ...
  .highpass(freq = Osc.freq().mul(pHpTrack), q = pHpQ, analog = pAnalog)                     // cases 2+3, CANNOT
```

The two bandpasses are static and linear, so at 2 voices/orbit moving them halves their cost for bit-identical output.
The highpass both tracks pitch *and* uses `analog`, so it is stuck per-voice on two independent grounds, and it is the
expensive one (4.2× a plain filter).

**Design consequence for this DSL:** the authoring surface should make the distinction visible, not leave it as
folklore. A user reaching for the orbit chain to save CPU needs to know which of their filters can follow them there.

## Context, what exists

The **Katalyst** layer is the per-orbit (Cylinder-level) effect chain, the bus counterpart to the
per-voice **Ignitor** (exciter) and the per-voice **Pipeline** (signal path). One word per concept.

The effects are already built and committed under `audio_be/src/commonMain/kotlin/cylinders/katalyst/`:
`KatalystEffect` (base) + **Body / Formant(vowel) / Delay / Reverb / Phaser / Compressor / Ducking**,
plus `KatalystContext` and `VoiceLease`. Body & vowel moved here from the per-voice filter chain in the
2026-07-03 orbit-Katalyst work (archived `../tasks-archive/2026-07/20260703-body-vowel-orbit-katalyst.md`).

**But the chain is hardcoded.** `Cylinder.kt:68`:

```kotlin
val pipeline: List<KatalystEffect> = listOf(body, vowel, delay, reverb, phaser, compressor)
```

There is no way to author, reorder, or parameterise a per-orbit chain from KlangScript.

## Goal

A KlangScript-facing DSL to declare a per-orbit effect chain, which effects, in what order, with what
params, mirroring how `PipelineDsl` declares the per-voice pipeline and `IgnitorDsl` the exciter. This
is the materialised **Phase 4 ("Katalyzer")** of the archived engine-dsl design record, the effects
landed; the authoring surface didn't.

## Settled design decisions (2026-08-24, with the maintainer)

Decided during the Der Schmetterling guitar-amp work; the empirical driver is in the last bullet.

- **Cab + mix-shaping EQ belong in Katalyst.** Both are case 1 (static + linear): they move to the
  orbit losslessly and get N× cheaper. Pitch-tracking filters stay per-voice (case 2, unfixable).
- **The power amp (any saturating stage) stays on the VOICE by default.** Shared clipping on a bus is
  cross-voice intermodulation, chords eat each other (and orbits do share: two rhythm guitars ride one
  orbit today). An orbit-level drive stage may be offered later as an explicitly *different* effect,
  placed before reverb, not as an optimization.
- **Doctrine, in studio terms: voice = string + amp head (per-note, nonlinear); Katalyst = cab + mics
  + outboard (shared, mostly linear).**
- **Params: `Kat.param("name", default, doc)` in the builder, `.katp("name", v)` from sprudel.**
  Semantics deliberately differ from `oscp`: NO per-note snapshot, orbit-scoped continuous state,
  read per block, last-writer-wins per orbit (the existing `reverb(wet)`/`compressor` rule). Values may
  be patterns → control-rate automation of the bus.
- **The EQ stage reuses the fused `EqCore` / `IgnitorDsl.Eq` sections**, same section vocabulary
  (lowpass/highpass/bell/tap/notch), same per-block param reads, hosted on the orbit.
- **Multi-mic taps (added 2026-08-24): the chain cannot be a pure serial list.** The maintainer wants
  to place multiple "microphones" on one cab, N parallel taps off the bus, each with its own short
  delay (sub-ms..few ms), EQ color, pan, and gain, summed back. This is how thickness works without
  doubling the performance: a static comb (fixed mic placement) reads as a cab/room *signature*; a
  drifting comb (two detuned copies of the performance) reads as a phaser. Measured on the song: the
  doubled guitars bought <±1.2 dB of spectrum and an audible slow phaser; removing them and getting
  width from placement improved everything. Multi-mic is still case 1, N linear paths paid once per
  orbit, and it needs a split/process/sum construct in the builder, not just a stage list.

- **Why the EQ must live here (measured 2026-08-22):** a mix-shaping EQ placed before the voice's
  power amp is compressed away, a +10 dB tap boost came out as +2.7 dB after `distort(0.30,"gentle")`,
  because saturation is a level equalizer. Color EQ stays pre-amp on the voice (useful range gain 1–3);
  anything meant to change the MIX must sit after the last nonlinearity, i.e. on the orbit bus. That is
  the concrete reason this DSL is a MUST for the guitar work, not a nice-to-have.

## The design

### 0. Shape in one paragraph

A Katalyst chain is data, exactly like a master chain: `KatalystDsl(stages: List<KatalystStageDsl>)`,
a sealed `@WireName` stage hierarchy, content-addressed registration over the wire, a reference that
rides the voice stream, a per-cylinder dual-chain crossfade on swap. The chain is written with the
same door shape as `Master(m => ...)`:

```
let guitarBus = Katalyst(k => k
  .eq(e => e.band(freq = 300, q = 0.8, db = 2.0))     // the mix EQ, after the amp
  .reverb(r => r.wet(0.15).size(3))
  .compressor(c => c.threshold(-21).ratio(3).knee(6).attack(0.005).release(0.12))
)

stack(guitar1, guitar2, guitar3).katalyst(guitarBus)  // three orbits, one chain
drums.orbit(5).katalyst(Katalyst(k => k.eq(e => e.band(freq = 160, q = 1.0, db = -3.0)).reverb(r => r.wet(0.25).size(4))))
```

A song without `katalyst(...)` runs `KatalystDsl.classic`, which is today's hardcoded chain
(body, vowel, delay, reverb, phaser, compressor) driven by the voice fields, byte-identical to the
engine before this work. That is the same "default MUST stay empty" contract `MasterDsl.default`
carries, transposed: here the baseline is not empty but the chain the engine has always run.

### 1. Wire model (`audio_bridge/KatalystDsl.kt`)

```kotlin
@WireFormat
data class KatalystDsl(val stages: List<KatalystStageDsl>) {
    companion object {
        /** Today's fixed chain, knobs as slots named `<stage>.<knob>`. MUST stay equal to what Cylinder ran before this DSL (guarded, §7). */
        val classic: KatalystDsl = KatalystDsl(listOf(Body(), Vowel(), Delay(), Reverb(), Phaser(), Compressor(), Duck()))  // Duck last, runs outside the list
        fun of(vararg stages: KatalystStageDsl): KatalystDsl = KatalystDsl(stages.toList())
    }
}

@WireFormat
sealed interface KatalystStageDsl {
    @WireName("body")       data class Body(val material: String? = null, val wet: IgnitorDsl = Constant(0.0), val floor: IgnitorDsl = Constant(0.0))
    @WireName("vowel")      data class Vowel(val vowel: String? = null, val wet: IgnitorDsl = Constant(0.0), val floor: IgnitorDsl = Constant(0.0))
    @WireName("delay")      data class Delay(val wet: IgnitorDsl = Constant(DELAY_WET), val time: IgnitorDsl = Constant(DELAY_TIME_SECONDS), val feedback: IgnitorDsl = Constant(DELAY_FEEDBACK), val cap: IgnitorDsl = Constant(DELAY_CAP))
    @WireName("reverb")     data class Reverb(val wet: IgnitorDsl = Constant(REVERB_WET), val size: IgnitorDsl = Constant(REVERB_SIZE), val lowpass: IgnitorDsl? = null)
    @WireName("phaser")     data class Phaser(val rate: IgnitorDsl, val wet: IgnitorDsl, val center: IgnitorDsl, val sweep: IgnitorDsl, val floor: IgnitorDsl)
    @WireName("compressor") data class Compressor(val threshold: IgnitorDsl, val ratio: IgnitorDsl, val knee: IgnitorDsl, val attack: IgnitorDsl, val release: IgnitorDsl)
    @WireName("duck")       data class Duck(val orbit: Int, val depth: IgnitorDsl, val attack: IgnitorDsl)
    @WireName("eq")         data class Eq(val sections: List<IgnitorDsl.EqSection> = emptyList())
    @WireName("gain")       data class Gain(val gain: IgnitorDsl = Constant(1.0))
    @WireName("mics")       data class Mics(val mics: List<Mic>)          // §4
}
```

Rules that fix the shape:

- **Knob type is `IgnitorDsl`, restricted to block-constant nodes.** The settled decision reuses
  `IgnitorDsl.EqSection` verbatim, and its knobs are `IgnitorDsl` values (`Constant`, `Param`), so
  every other stage takes the same type: one value vocabulary, one `Katalyst.param` slot type
  (§3), and the `KatalystDslLike` conversion (number or param) is the existing `IgnitorDslLike`.
  Anything else a user manages to pass (an oscillator as a knob) is COERCED, never rejected: the
  runtime reads `controlRateValueOrNull` and falls back to the knob's default when that is null.
  No engine sees a signal-rate knob on the bus; a moving knob is a `.katp` pattern (§3).
- **Defaults are the wire defaults, from `audio_bridge/constants/`** (`SendEffectDefaults.kt` and
  friends), the same constants the sprudel doors and the master stages read. Never a literal per
  stage (parity rule 4, maintainer 2026-09-16).
- **Every knob keeps the name and scale of its sprudel door**: `reverb(wet, size, lowpass)`,
  `delay(wet, time, feedback, cap)`, `compressor(threshold, ratio, knee, attack, release)`,
  `duck(orbit, depth, attack)`, `phaser(rate, wet, center, sweep, floor)`, `body(material, wet,
  floor)`, `vowel(vowel, wet, floor)`. The one recorded asymmetry to reconcile in the same
  deliverable: the master limiter builder says `thresholdDb`/`kneeDb`/`attack(seconds)`; the
  Katalyst compressor follows sprudel (`threshold`, `knee`, `attack`), and the follow-up file gets
  a row saying the master limiter is the odd one out.
- **`Duck` is declared in the list but runs outside it**, as today (`Cylinders.processAndMix` step 2
  needs every orbit processed first). Its position in the list is documented as ignored, and when a
  chain declares two, the last one wins (decided 2026-09-17; the chain builder says so in its KDoc).
- **Enums nowhere.** `material` and `vowel` stay strings, as on the voice fields today; the stage
  variants are the sealed hierarchy (rule 7).
- **The classic chain is the untouched voice, slot by slot (decided in step 1's review loop,
  2026-09-17).** The engine gates the send effects on `delay.time` and `reverb.size`
  (`KatalystDelayEffect` / `KatalystReverbEffect.configure`), not on wet, so `classic` carries what
  `VoiceFactory`'s untouched branch carries: delay wet, time and feedback 0.0 with `cap` at
  `DELAY_CAP`, reverb wet and size 0.0 with lowpass unset, phaser wet 0.0, body and vowel wet 0.0
  with material and vowel null, all five compressor knobs unset (the engine's gate is "any of the
  five set", `Voice.Compressor.fromParams`, and the `compressor(...)` door does not fill threshold on
  a tail-only call, so `compressor(ratio = 8)` must still switch the stage on with the constant
  threshold), duck orbit unset and depth 0.0; every other knob the shared constant. `KatalystClassicMatchesUntouchedVoiceSpec` builds a voice
  through the real factory and compares. A BARE stage keeps the touched constants (`Reverb()` has
  wet `REVERB_WET`), except phaser and duck, which stay off until `wet` respectively `orbit` is
  written, as their sprudel doors do today. Consequence: the `reverb(...)` door fills the companion
  slots at write time (the 2026-09-16 rule) and sounds familiar; a raw `katp("reverb.wet", x)`
  writes one slot and is silent on classic until `reverb.size` is written too. The compressor is
  the second asymmetry, the other way round: a raw `katp("compressor.ratio", 8)` switches the stage
  on with the constant threshold, as the voice door does today.
- **`reverb.lowpass` is a slot whose unset value is the wire's non-finite sentinel** (`/dsl-design` §4:
  a non-finite value reads as unset), meaning the engine's fixed damping, exactly as a null `reverbLowpass`
  on the voice does today. Decided 2026-09-17 after step 1 shipped it without a slot; step 1's fix
  round adds it, so `katp("reverb.lowpass", hz)` has somewhere to land in phase 1 step 5.

### 2. Where a chain's values come from: the chain, then the owner voice

> **Superseded 2026-09-17 by the signal-flow plan §7 (D4):** the voice fields this rule overrode leave
> the wire and the bus doors become `katp` aliases, so a chain's values come from its slots only.
> Kept for the record; step 2 still runs the owner-voice writers (byte identity), step 3's resolver
> follows §7's contract, step 5 removes the fields.

Two sources exist for the same knob: the chain says `reverb(r => r.size(3))`, the pattern says
`.reverb(size = 5)`. The rule, and it is one rule for every built-in stage:

> **The chain is the instrument; a voice field, when set, is the performance.** A stage's knob
> takes the chain's value unless the orbit's OWNER voice carries the matching field non-null, in
> which case the voice value wins for as long as that voice owns the lease.

This keeps every existing sprudel door working unchanged on top of a declared chain, keeps the
`VoiceLease` first-writer rule as the one ownership rule (the settled text's "last-writer-wins" is
the pre-lease wording; the lease replaced it and this design follows the lease), and gives a
declared chain the power to fix the topology and the defaults for a whole orbit. A stage ABSENT
from the chain does not run even if a voice asks for it: `Katalyst(k => k.eq(...))` is a bus with
an EQ and nothing else, and a `.reverb(wet = 0.3)` on one of its voices sends into nothing. That is
what "the chain is the instrument" means, and the editor shows it: the stage is not there.

`wet` on the send stages (delay, reverb) keeps its per-voice meaning: it is the send amount each
voice writes into the send buffer, and the chain's `wet` is the amount for voices that set none.
That is the group idiom the songs already use (`stack(g1, g2, g3).reverb(wet = 0.15)`), as a bus
property instead of a field stamped on every voice.

### 3. Params: `Katalyst.param` and `.katp`

Settled: `Kat.param("name", default, doc)` in the builder, `.katp("name", v)` from sprudel, orbit
scoped, read per block, patterns allowed. Two refinements:

- **§D1, naming.** The settled shorthand `Kat` is a second word for the same concept next to
  `Katalyst(...)`; rule 5 says one word, no aliases. Recommendation: `Katalyst.param(...)` on the
  object, `.katp(...)` on the pattern (the `oscp` precedent keeps its short form because it is a
  method, not an object).
- **The slot type is `IgnitorDsl.Param`**, resolved by a different resolver: a voice resolves
  `Param` from `oscParams` per note (snapshot); a cylinder resolves it from its own param state per
  block, fed by the owner voice's `katalystParams` map (`VoiceData.katalystParams: Map<String,
  Double>?`, the `oscParams` shape). Two namespaces, one node type: `.oscp("decay", x)` never reaches
  a Katalyst stage and `.katp` never reaches an ignitor.
- **Param values are runtime state, not chain identity.** Automating `.katp("room", "<0.1 0.4>")`
  changes nothing about the registered chain; only an edit of the DSL text mints a new
  `uniqueId()` and a crossfade (§6).

### 4. The `mics` stage: parallel taps inside one serial stage

The chain stays a serial list. The parallel construct the settled decision asks for is ONE stage
whose inside is parallel, the way `Eq` is one stage whose inside is a section list:

```
.mics(m => m
  .mic(delay = 0.0,  gain = 0.7, pan = 0.35)                                   // close, on axis
  .mic(delay = 1.3,  gain = 0.5, pan = 0.65, configure = e => e.band(freq = 3000, q = 1.0, db = -3.0))  // off axis
  .mic(delay = 4.0,  gain = 0.3, pan = 0.5,  configure = e => e.lowpass(4000))  // room mic
)
```

```kotlin
@WireName("mic")
data class Mic(
    val delay: IgnitorDsl = Constant(0.0),      // milliseconds, sub-ms to a few ms
    val gain: IgnitorDsl = Constant(1.0),
    val pan: IgnitorDsl = Constant(0.5),
    val eq: List<IgnitorDsl.EqSection> = emptyList(),
)
```

Semantics: every mic reads the stage INPUT (never the running sum), delays it by a short ring,
runs its EQ sections, pans and scales, and the mics are summed into the stage output, replacing
the input. A static set of delays is a fixed comb, which is what a cab with two mics is; there is
deliberately no modulation knob on a mic (a drifting comb is a phaser, and the chain has one).
Delay is in milliseconds because that is how mic placement is spoken about; the ring is rented
from the same shelf as the delay stage's, class-sized for a few milliseconds, never a 10 s line.
Cost: N linear paths paid once per orbit, which is the case 1 win the motivation section counts.

### 5. Where a stage sits: sends, the EQ, and the compressor

The send buffers are written by the voices, before the bus. An `eq` placed BEFORE `reverb` in the
list therefore shapes the dry mix only, and the room hears the raw voices; placed AFTER, it shapes
dry and wet together. Both are legitimate mixes, and the list position expresses them, with one
rule that makes the "cab before room" doctrine hold physically:

> **§D2.** Linear stages that precede the first send-return stage (`delay`, `reverb`) in the list
> also process the send buffers, so the room hears what the cab put out. Stages after a return
> process the sum only. Recommendation: adopt it. Cost is two more buffers per preceding linear
> stage (eq, mics, gain), paid once per orbit; the alternative, an explicit `sends = true` knob on
> each stage, is one more thing to explain for a distinction nobody wants to get wrong.

The default position for a mix-shaping EQ follows the master decision (reverb, then eq, then the
dynamics): after `reverb`, before `compressor`, so the detector sees the corrected spectrum and a
low cut turns into headroom. The builder appends in written order; `Katalyst.classic()` is the
historical order. Nothing reorders behind the author's back.

### 6. Application path, mirrored from the master

| step | master (shipped) | Katalyst (this design) |
|---|---|---|
| sprudel carrier | `SprudelVoiceData.master: MasterValue?` | `SprudelVoiceData.katalyst: KatalystValue?` (flat field, merge last-writer-wins) |
| doors | `master(dsl)`, `.master(dsl)`, `String.master`, `PatternMapperFn.master` | `katalyst(dsl)`, `.katalyst(dsl)`, `String.katalyst`, `PatternMapperFn.katalyst`, all four with the form-(d) test |
| the orbit | one per engine | the voice's `orbit` field; the carrier form is `katalyst(dsl).orbit(n)` |
| wire | `VoiceData.master: String?` + `Cmd.RegisterMaster` | `VoiceData.katalyst: String?` + `VoiceData.katalystParams` + `Cmd.RegisterKatalyst` |
| announce | `InlineDslRegistrar.masters` | `InlineDslRegistrar.katalysts` (the one place, both live and offline) |
| identity | `MasterDsl.uniqueId()` | `KatalystDsl.uniqueId()` (`KatalystDslIdentity.kt`) |
| registry | `MasterRegistry`, per-playback fork | `KatalystRegistry`, per-playback fork |
| consume | scheduler at promotion, `masterBus.requestSwap` | scheduler at promotion, `cylinders.requestSwap(orbit, id)`, applied whether or not the event sounds, before the late-sound guards |
| swap | `MasterBus` dual-chain crossfade, 60 ms, linear | per-cylinder dual-chain crossfade, same constant, same linear law, same block-quantized start |
| build | chains built at registration, bounded cache | chains built at registration per cylinder that references them, bounded cache; reverb units and rings rented from the shelves as today |
| reset | `Master.default()` says "back to unity" | there is no `default`: `Katalyst.classic()` IS the historical chain, and an event carrying no chain leaves the orbit's chain as it is (the master's "no change" rule), so going back is `.katalyst(Katalyst.classic())` on a pattern that composes nothing else |

Two voices on one orbit carrying different chains swap it back and forth with a crossfade each
time, the same author error the master has, and the same cure: one chain per orbit. Voices with the
same chain on the same orbit cost nothing (content-addressed, idempotent).

**§D3, the crossfade helper.** `MasterBus` and the cylinder would carry the same dual-chain
crossfade. Recommendation: extract the crossfade (two chains, one linear blend, a block-quantized
start, tail pre-check) into one class both hosts own an instance of, in the deliverable that adds
the second host. If that extraction grows clever, copy the fifty lines instead; the stone rule on
complexity outranks the duplication.

### 7. Backend: thin shells, one builder, the EQ's one precondition

- `KatalystChain` = an ordered list of stage instances built from a `KatalystDsl` by
  `KatalystChainBuilder` (one exhaustive `when` over `KatalystStageDsl`). The seven existing
  `Katalyst*Effect` classes become the stage instances for their variants unchanged; new:
  `KatalystEqEffect` (two `EqCore` instances, left and right, the section list mapped by the same
  exhaustive variant-to-Int `when` `EqIgnitor` uses), `KatalystGainEffect`, `KatalystMicsEffect`.
  `Cylinder.pipeline` becomes the current chain's list; `applyBusEffects` becomes "resolve every
  knob of every stage per block: chain value, owner-voice field if set, param state".
- **The EQ's precondition, stated in `EqCore`'s KDoc:** the core is snap-only, and a per-block
  coefficient change on a bus is a click. Phase 1 (§9) ships the EQ with literal knobs only, so
  coefficients change only on a chain swap, which the crossfade covers. Phase 2, which lets
  `.katp` drive an EQ knob per block, adds the coefficient ramp to `EqCore` first (linear
  interpolation of the five coefficients across the block, the `BaseSvf` precedent), and the
  unified-eq plan's D4 note is closed by that commit.
- Body and vowel: unchanged in this work; their rebase on `EqCore` stays the separate item in
  the settled section.
- Ducking: the `Duck` stage configures `KatalystDuckEffect` exactly as the voice field does
  today; `Cylinders` keeps running it after every orbit is processed.
- Nothing per sample allocates; every stage instance and buffer is created at chain build, which
  happens at registration (on the audio thread, as the master's does, the same bounded exception).
- **Step 2's resolver contract, per stage (written down by the step 1 review loop, 2026-09-17;
  byte identity with today's `applyBusEffects` depends on every line):**
  - Delay: `configure(time, feedback, cap)` from the slots; non-finite time is off, non-finite
    feedback and cap take their constants. On a declared chain `delay.wet` is the stage's ON
    SWITCH (finite and above 0.0) until step 5b makes it the insert amount; the per-VOICE send
    amount (`SendRenderer` reads `voice.delay.amount`) still decides how much each voice sends.
    Decided 2026-09-17 in step 3a's batch, so `Katalyst(k => k.reverb(r => r.wet(0.0).size(6)))`
    rents nothing, consistent with phaser (depth) and duck (orbit).
  - Reverb: `configure(size = Reverb.normalizeSize(slot), lowpass = slot if finite else null)`;
    `reverb.wet` the same on switch, same caveat.
  - Phaser: `depth = wet`; only when the stored depth is at or above `Phaser.MIN_ACTIVE_DEPTH`
    write rate, center (`PHASER_CENTER_HZ` when not above 0), sweep likewise, floor, feedback 0.5.
    Never write the kernel params when gated off: the sweep clock must keep running.
  - Compressor: on iff ANY of the five slots is finite; each non-finite one takes its
    `COMPRESSOR_*` constant (`Voice.Compressor.fromParams` verbatim); reuse the instance so the
    envelope follower survives.
  - Duck: on iff `orbit.isFinite() && depth > 0.0`; `cylinderId = orbit.toInt()` after the door's
    integer coercion; non-finite attack takes `DUCK_ATTACK_SECONDS`; the last Duck stage wins; run
    after all orbits as today.
  - Body and vowel: `configure(null)` iff material respectively vowel is null, whatever `wet` says;
    otherwise the `FilterDef` with `mix = wet` and a non-finite floor taking `BODY_FLOOR` /
    `VOWEL_FLOOR`; an unknown material name resolves to null as `toVoiceData` does.
  - Doors (phase 1 step 5): `reverb(...)` and `delay(...)` write ALL companion slots on any call
    (today's fill rule); `compressor(...)` writes only the named slots; tail-only `body`, `vowel`
    and `duck` calls never write the name or the orbit.
  - Chain lookup: `KatalystRegistry.find` lowercases and allocates; resolve it on owner change or
    registration only, never per block.
  - Coercion of a knob that is neither `Constant` nor `Param` (decided 2026-09-17, step 3a, refined
    in its delta review): the resolver asks `controlRateValueOrNull` at TWO different finite
    frequencies and accepts the value only when both answers are finite and equal; anything else
    takes the knob's shared wire constant. A bus has no note, so `Osc.freq()` and every
    frequency-dependent subtree fall back (`Times` folds NaN to 0.0 through `safeOut`, which is why
    a single non-finite probe was not enough); an opaque node on a compressor slot switches the
    stage on with the constants. Never a throw.
  - A pending chain on an idle cylinder (decided 2026-09-17): `Cylinders.processAndMix` polls the
    pending name of INACTIVE cylinders once per block (one null check), so a registration that
    arrives after the request lands at the next block, the master's "late state must still take
    effect" rule; an active cylinder installs at its next deactivation until step 3b.
  - **Body and vowel on a declared chain need the name tables** (`SprudelBodyMaterials.modesFor`,
    the vowel bands), which live in `sprudel`, a module `audio_be` must not depend on. Step 3c
    (2026-09-17): move the two tables, pure data, into `audio_bridge`; sprudel and the editor tool
    import them from there; the resolver maps the name through them; until then a declared
    `body`/`vowel` stays off and `KatalystSlotResolverSpec` pins that on purpose. The classic chain
    is unaffected, its voices arrive with the `FilterDef` resolved.


### 8. Tests (mandatory tier, every one mutation-checked)

1. **Default parity, the byte-identity guard.** The frozen July song (`FrozenSongs`) renders
   byte-identical with `KatalystDsl.classic` driving every cylinder versus the pre-DSL engine, and
   `KatalystDefaultsSyncSpec` pins the default list to the historical order and the wire constants.
2. **Door parity.** `KatalystDoorParitySpec`: the script form and the Kotlin form build equal
   nodes, every stage, every knob (the `KlangScriptFilterDoorParitySpec` pattern).
3. **Wire round trip.** Every variant through the KSP codec, in `WireCodecRoundTripSpec`.
4. **Linear-move parity.** One voice with `.eq(e => e.band(300, 0.8, 2))` on the voice versus the
   same band on its orbit: equal output within float tolerance (superposition, case 1). Then two
   voices, which proves the orbit EQ is the sum's EQ.
5. **Sends rule.** An `eq` before `reverb` shapes the reverb input; after, it does not (assert on
   the reverb send buffer spectrum, §D2).
6. **Swap without click.** Chain A to chain B: the largest sample-to-sample step during the fade
   is below the threshold the master's `MasterBusTest` uses; a mid-fade re-swap follows the same
   queue policy as the master.
7. **Owner-voice override.** A chain `reverb(size 3)` with an owner voice carrying `size 5` renders
   the 5; a non-owner voice carrying 5 does not; the lease change hands the override over.
8. **Mics comb.** Two mics at 0 and 1 ms on a white-noise bus produce the comb's first notch at
   500 Hz; a third mic's EQ section is applied to that mic's path only.
9. **`.katp` automation.** A param slot driven by a pattern changes the stage's value at the block
   boundary and never rebuilds the chain (the registry's registration count stays at one).
10. **Coercion.** An oscillator handed as a knob renders the knob's default; nothing throws.

### 9. Phasing, one review loop and one commit each

- **Sequencing as run (2026-09-17):** step 1 = the wire model and plumbing (commit 9fbc9e2b); step 2 =
  the cylinder builds its chain from `KatalystDsl.classic` (d170bf12); step 3a = declared chains looked
  up, resolved from their slots (a `Param` resolves to its default until `katp` lands), installed when
  the cylinder is idle, pending otherwise; step 3b = the crossfade so a swap is immediate, with the
  outgoing chain's tail DRAINED through the send stages' existing off-config rather than cut (decided
  2026-09-17: the master accepted the cut for its v1 and noted the extension; the orbit does it from
  the start because the effects already own the drain); step 3c = the
  body and vowel name tables move to `audio_bridge` so a declared chain can carry them; step 5a = the
  orbit param state (`katp`, `VoiceData.katalystParams`) and the bus doors writing it as aliases, so a
  declared chain's `Param` slots resolve from what the pattern wrote; step 4 = `eq` and `gain`; step 5b =
  insert-style sends and the voice fields leaving the wire. Reordered 2026-09-17 after step 3a's review:
  a declared chain reads slots only, so `stack(guitars).reverb(wet = 0.15).katalyst(Katalyst(k =>
  k.classic().eq(...)))` would lose its room until the doors write `katp`; 5a therefore precedes 4.
  Classic keeps the owner-voice writers until 5b removes the voice fields.
- **Phase 0, the mirror.** Wire model, identity, registry, registrar, doors on both surfaces,
  builder shells for the seven existing effects, `KatalystDsl.classic`, the per-cylinder swap,
  tests 1 to 3, 6, 7. No new sound is reachable yet; the engine is byte-identical.
- **Phase 1, the song's need.** `eq` and `gain` stages with literal knobs, the sends rule (§D2),
  tests 4, 5. Acceptance: Der Schmetterling with the two EQs from the header, re-measured with the
  same tools: the 320 Hz band within 2 dB of the 160 Hz band, the guitars' 160 to 400 Hz within
  2 dB of their own 500 Hz to 1.6 kHz, no other region moved by more than 1 dB. Ears decide the
  numbers; the table referees them.
- **Phase 2, the performance.** `Katalyst.param`, `.katp`, `VoiceData.katalystParams`, the
  `EqCore` coefficient ramp, tests 9, 10.
- **Phase 3, thickness.** `mics`, test 8.
- **Phase 4, consolidation.** Body and vowel on `EqCore` (the settled item), unchanged sound,
  `FormantFilter` output as the oracle.

### 10. Parked decisions

- **§D1 DECIDED 2026-09-17:** `Katalyst.param(...)` on the object, `.katp(...)` on the pattern, as `oscp`.
  Noted for later: `Osc` / `oscp` are misnomers for the Ignitor concept and get renamed in their own item
  (the known debt in `/dsl-design` §5).
- **§D2 DISSOLVED 2026-09-17** by the signal-flow plan §7: no per-voice send amounts, so no send
  buffers; `reverb` and `delay` are insert-style stages at their list position, the master's model.
- **§D3 DECIDED 2026-09-17:** extract the crossfade helper if it costs no runtime, or as good as none;
  measure the master swap before and after in the same deliverable.
- **§D4 DECIDED 2026-09-17:** the chain is the instrument. The bus doors become `katp` aliases on
  the orbit's chain; a stage absent from a declared chain makes the matching door a no-op.
- **§D5 DECIDED 2026-09-17:** the orbit `gain` stage stays, the group fader after the inserts.
  Gain and postgain were both pre-bus faders at one point (`SendRenderer`); postgain retires in
  the signal-flow plan §6, and `gain` means the tone-neutral level on every surface.

## Links

- Effects + hardcoded order: `audio_be/.../cylinders/katalyst/`, `Cylinder.kt` (`pipeline`, `applyBusEffects`, the `VoiceLease`).
- The measurement that makes the EQ concrete: klang-ai `sessions/20260917-der-schmetterling-measure/README.md`.
- The master DSL, the pattern this design mirrors: `audio_bridge/MasterDsl.kt`, `klangscript-libs/.../stdlib/MasterBuilders.kt`,
  `KlangScriptMaster.kt`, `sprudel/.../lang/lang_master.kt`, `klang/.../InlineDslRegistrar.kt`, `audio_be/.../master/MasterBus.kt`.
- The EQ core and its snap-only contract: `audio_be/.../filters/EqCore.kt`, `docs/plans/unified-eq.md` (D4 ramp note).
- Prior design (Phase 4 Katalyzer): archived `../tasks-archive/2026-06/20260630-engine-dsl-design-record.md`.
- Counterpart DSL work: `engine-tuning-profile.md` (Pipeline DSL finish).
- Master stage: `../tasks-archive/2026-09/20260904-per-playback-engine.md` §H / D6, **and the now-SHIPPED Master
  DSL ([archived](../tasks-archive/2026-08/20260803-master-dsl.md)) is the pattern to follow**: it
  sets both the application path (in-pattern, registration + id-on-voice)
  AND the reuse rule (thin shells over the shared `audio_be/effects/` DSP classes; wire stages as
  `sealed @WireName` variants). Katalyst DSL and Master DSL share one effect vocabulary, different hosts (orbit bus /
  master bus).
- Memory: `project_katalyzers`, `project_engine_naming`, `pipeline_stage_design`, `osc_ignitor_misnamed`.
