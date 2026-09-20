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

**Decided 2026-09-18 with the maintainer, replacing the append rule of step 1:** `.katalyst(dsl)`
REPLACES the chain an event carries, exactly as `sound()` replaces the instrument and `master()`
the master chain. Chaining two doors was error-prone: with the append rule
`.katalyst(Katalyst(k => k.classic())).katalyst(Katalyst(k => k.classic()))` stacked fourteen
stages whose duplicates read the same slot names, so one `.reverb(0.3)` switched on reverb into
reverb and two compressors in series. The builder starts EMPTY, so `Katalyst(k => k.eq(...))` is
honestly "an EQ and nothing else", and `k.classic()` is the one explicit word for "the chain an
orbit has always run", appended at most once per builder (a second `classic()` in the same
builder is dropped). The default of an orbit that declares nothing stays classic. The composition
memo (`KatalystAppend`) and `KatalystDsl.plus` go with the rule.

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
    @WireName("body")       data class Body(val material: IgnitorDsl = Constant(SLOT_UNSET), val wet: IgnitorDsl = Constant(0.0), val floor: IgnitorDsl = Constant(0.0))   // material = an index, step 5a-2
    @WireName("vowel")      data class Vowel(val vowel: IgnitorDsl = Constant(SLOT_UNSET), val wet: IgnitorDsl = Constant(0.0), val floor: IgnitorDsl = Constant(0.0))
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
- **Enums nowhere, and no string slot either.** `material` and `vowel` were strings until step
  5a-2 (2026-09-18) made them numeric INDICES into `BodyMaterials.names` and `VowelBands.names`,
  0 = none, so both doors convert a NAME through one shared `indexOf` and the wire keeps carrying
  numbers. A typed (string) param kind was considered and not needed: the tables are closed lists.
  The stage variants are the sealed hierarchy (rule 7).
- **The classic chain is the untouched voice, slot by slot (decided in step 1's review loop,
  2026-09-17).** The engine gates the send effects on `delay.time` and `reverb.size`
  (`KatalystDelayEffect` / `KatalystReverbEffect.configure`), not on wet, so `classic` carries what
  `VoiceFactory`'s untouched branch carries: delay wet, time and feedback 0.0 with `cap` at
  `DELAY_CAP`, reverb wet and size 0.0 with lowpass unset, phaser wet 0.0, body and vowel with
  their index AND their wet unset (since 5a-2: the material and the vowel are index slots, and an
  unset wet lets the engine's non-finite guard supply the constant), all five compressor knobs
  unset (the engine's gate is "any of the five set", `Voice.Compressor.fromParams`), duck orbit
  unset and depth 0.0; every other knob the shared constant. `KatalystClassicMatchesUntouchedVoiceSpec` builds a voice
  through the real factory and compares. A BARE stage keeps the touched constants (`Reverb()` has
  wet `REVERB_WET`), except phaser and duck, which stay off until `wet` respectively `orbit` is
  written, as their sprudel doors do today. Consequence: the `reverb(...)` door fills the companion
  slots at write time (the 2026-09-16 rule) and sounds familiar; a raw `katp("reverb.wet", x)`
  writes one slot and is silent on classic until `reverb.size` is written too. A raw
  `katp("compressor.ratio", 8)` switches the compressor on with the constant threshold through the
  engine's guard; since step 5a-3 the `compressor(...)` door fills its companions like every
  compound bus door, so the asymmetry this paragraph once recorded between the two doors is gone
  (`/dsl-design` §4 is the one home of the fill rule).
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
historical order, and `k.classic()` lands its block once per builder. Nothing reorders behind the
author's back.

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
| swap | `MasterBus` dual-chain crossfade, 60 ms, linear, the outgoing cut | per-cylinder dual-chain crossfade, same constant (`Crossfade.XFADE_SECONDS`), same block-quantized start, the outgoing chain's INPUT ramped (dry and sends) and its output added at full weight, then drained (see §9) |
| build | chains built at registration, bounded cache | chains built at registration per cylinder that references them, bounded cache; reverb units and rings rented from the shelves as today |
| reset | `Master.default()` says "back to unity" | there is no `default`: `Katalyst.classic()` IS the historical chain, and an event carrying no chain leaves the orbit's chain as it is (the master's "no change" rule), so going back is `.katalyst(Katalyst.classic())`; since 2026-09-18 the door replaces like `master`, so the last `.katalyst` on a pattern is the chain |

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
  coefficient change on a bus is a click. **Closed 2026-09-18 by step 4, at the SURFACE rather than
  in the core:** `KatalystEqEffect` holds two pre-built `EqCore` banks and installs a changed curve
  through `KatalystFilterSwap`, the 12 ms declick crossfade body and vowel already use, so a knob
  moved by `.katp` costs one bank swap per changed block and never a click for changes at least
  12 ms apart; two changes inside one fade drop the oldest bank, the faint tick
  `KatalystFilterSwap` documents for body and vowel alike (SUPERSEDED by Katalyst 5c-6: 50 ms,
  crossfade from what sounds now, at most 10 banks, a change at a full pool parked). `EqCore` keeps its
  snap-only contract and needs no coefficient ramp for this host; the unified-eq plan's D4 note is
  therefore closed for the orbit and still open for a future master eq that wants per-sample
  interpolation instead of a bank swap.
- Body and vowel: unchanged in this work; their consolidation is the resonator-bank item in the
  settled section (not a rebase on `EqCore`, superseded 2026-09-19).
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
  - Body and vowel: `configure(null)` iff the material respectively vowel INDEX names nothing
    (non-finite, below 0, 0 = `none`, or past the end of the catalogue), whatever `wet` says;
    otherwise the `FilterDef` with `mix = wet` and a non-finite floor taking `BODY_FLOOR` /
    `VOWEL_FLOOR`. An unknown NAME is index 0 at the door, so it resolves to null as `toVoiceData`
    does. The index-to-bands lookup happens in `resolve`, never in `apply` (step 5a-2).
  - Doors (phase 1 step 5, as of step 5a-3): every compound bus door fills per param at the door;
    `/dsl-design` §4 is the one home of the rule and of the two closed lists (which door is named
    how), deliberately not copied here. (Before 5a-3: only the sends filled, `compressor(...)`
    wrote only the named slots.)
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
  - **Body and vowel on a declared chain resolve through `audio_bridge`** (step 3c,
    done 2026-09-18): `BodyMaterials.modesFor` and `VowelBands.bandsFor`, pure data moved out of
    `sprudel` byte for byte, the sprudel-side symbols removed; `SprudelVoiceData.toVoiceData` and the
    body editor tool read the same objects, and `KatalystSlots` maps a stage's name through them at
    chain build. Unknown or null name = stage off on both paths. A declared `body("wood", wet 0.5)`
    renders bit-identically to the voice door; the one formal difference is the floor (null on the
    voice, the constant written out on the chain, the same number).


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
  the start because the effects already own the drain). Two blend shapes follow from that, decided
  2026-09-18: the orbit ramps the OUTGOING chain's input down and adds its output at full weight, so
  the seam into the drain is continuous by construction and the tail is never scaled; the master
  keeps its output blend because it cuts. The master could adopt the orbit's shape together with a
  drain later, as a by-ear sound item of its own, not here. A self-oscillating delay (`|feedback|`
  at or above 1) never drains, so it pins the orbit's next swap the way it already pins a live
  orbit; the existing open question in `KatalystDelayEffect`'s KDoc covers both. One outgoing slot:
  a swap queued during a drain waits for the full ring-out (bounded by the closed-form ceiling, seconds
  for a room); the master's bounded tail hold is the escape if live editing on a wet orbit needs it,
  open item 2026-09-18. The duck envelope is orbit state that survives a swap (decided 2026-09-18 in
  step 3b's review); its effect is crossfaded per sample in both directions, the envelope is taken
  over only when the arriving duck will be configured, and a late takeover happens only in the swap
  block (a claim after the ramp has advanced keeps ramping out; the fresh duck's own attack follows).
  Two pre-existing corners recorded, not fixed: a takeover onto a duck whose sidechain orbit is not
  yet rented freezes the reduction until that orbit sounds; the two-block owner liveness can answer
  "ducks" for an owner that died on the swap block, and the successor's reset then steps, as any
  owner change on a classic duck does today. A continuous mid-ramp handover would need a second ramp
  the crossfade cannot express; the maintainer's call whether that corner deserves it; step 3c = the
  body and vowel name tables move to `audio_bridge` so a declared chain can carry them; step 5a (done
  2026-09-18) = the orbit param state (`katp`, `VoiceData.katalystParams`) and the bus doors writing it
  as aliases, so a declared chain's `Param` slots resolve from what the pattern wrote; step 4 = `eq` and `gain`; step 5b =
  insert-style sends and the voice fields leaving the wire. Reordered 2026-09-17 after step 3a's review:
  a declared chain reads slots only, so `stack(guitars).reverb(wet = 0.15).katalyst(Katalyst(k =>
  k.classic().eq(...)))` would lose its room until the doors write `katp`; 5a therefore precedes 4.
  Step 5a's scope, decided 2026-09-18: the orbit param state IS the owner voice's `katalystParams`
  map read through the lease (no cylinder copy; it dies with the voice), re-resolved by the
  slot-driven writers only when the map instance changes; `.katp` and the bus doors write it (the
  doors with their fill rule, `compressor` without (superseded by step 5a-3, where every compound
  door fills), `body`/`vowel` only `wet` and `floor` since a
  material is a name, not a number). Voice-driven is ONLY the cylinder's born-with chain (no
  declaration); every chain that arrives by name is slot-driven, classic content included (decided
  2026-09-18 in step 5a's review: `Katalyst(k => k.classic())` was content-equal to the built-in
  classic and resolved to the voice-driven chain, leaving `katp` inert on the documented line). A
  declared chain owned its material until step 5a-2 made the material an index slot (below), so
  `.body(material = ...)` on its voices reaches it through `katp` like every other knob. The owner's map is aged with
  the lease's two-block liveness, so a lapsed owner's values never configure an arriving chain.
  Adding `.katalyst(Katalyst(k => k.classic()))` to a running pattern therefore installs a chain:
  immediate when the orbit is idle, a crossfade from the born-with chain when it sounds, and that
  crossfade is not bit-transparent (the arriving chain's ring and network warm from empty, a dip in
  the wet inside the 60 ms window while the old tail drains at full weight), the same as every swap;
  a repeated request is the raw-name no-op.
  Classic keeps the owner-voice writers until 5b removes the voice fields.
  **5b-2 decided with the maintainer (2026-09-19), see the signal-flow plan section 7:** inserts at
  today's position, one owner `wet` per orbit, every room setting glides over 50 ms
  (`docs/plans/knob-glide.md`), the delay's time crossfades between taps; the glide is piloted on
  the orbit reverb first.
  **Listened 2026-09-19 by the maintainer, on a fresh JS build at `b27dd5ad`** (levels on the
  wire, `pregain`, the unity fader, 5b-1, the delay and reverb state machines, the reverb's size
  glide): "things check out".
  **Listened 2026-09-19 by the maintainer on 5b-2, the insert-style sends** (rooms hear the body
  and the delay's echoes; the mixed-wet orbits of Der Schmetterling, Synthris and Pipers' Last
  Rave, where the room drains while an owner without a `wet` holds the orbit): "the listening
  checks out". The drain on a mixed-wet orbit is therefore kept: it is the turn-taking the
  signal-flow plan section 7 accepts, softened by the `wet` glide.
  **5b-3 as built (2026-09-19; one review round, clean):** the delay, reverb, compressor and duck
  fields (15 on `VoiceData`, the matching groups on `SprudelVoiceData`) are gone; `VoiceFactory`
  no longer builds `Voice.Delay`, `Voice.Reverb`, a voice compressor or ducking, nor hands body
  and vowel to the voice. The phaser fields stay for the custom pipeline's stage until phase 3.
  Identity: 15 built-in songs and 3 frozen pieces over 256 cycles bit-identical in raw doubles
  at `15b19243` and on the tree, 26 door-form rows identical; the voice-data golden regenerated,
  every changed line the old one with exactly the removed keys stripped, and every removed value
  equal to its slot. One deliberate change, judged correct by both reviewers: sprudel's
  accessors and mappers of these doors read the slots now (they read the fields), so a raw
  `katp` write is visible to `reverb.wet` and friends, the duck's mappers scale the companions
  the duck fill wrote (`duck(1, 0.8).duck(attack = mul(4))` is 4 times `DUCK_ATTACK_SECONDS`, it
  was a no-op), and a bare `reverb()` with a null value leaves the slot readable. No built-in
  song, frozen piece or tutorial uses an accessor or mapper of these doors, nor `duck` at all.
  Left for phase 3: body and vowel `FilterDef`s still ride `filters` with no backend reader, and
  `Voice.Compressor`/`Voice.Ducking` live under `Voice` though only the chain uses them.
  **Step 5b is run in three parts (2026-09-19):** 5b-1, the born-with chain becomes slot-driven
  and the voice-driven writers retire; 5b-2, the sends become inserts (the per-voice send amounts
  in `SendRenderer` go), with a listening checkpoint; 5b-3, the bus fields leave the wire and
  `SprudelVoiceData`. **5b-1 as built (2026-09-19; two review rounds):** there is ONE way a bus knob
  reaches a stage, the owner's `katalystParams`; the `voiceDriven` flag, the `owners` array and
  `applyOwner` are deleted. The rule above, "voice-driven is ONLY the born-with chain", and its
  consequence that `Katalyst(k => k.classic())` had to install a second chain, retire with their
  reason: a chain whose stages equal classic's resolves to the born-with chain, a bit-exact no-op
  instead of a crossfade. Evidence: 18 songs and frozen pieces (every built-in song, the
  maintainer's working copy of Der Schmetterling included) render bit-identically at HEAD
  `52b89756` and on the tree over 256 cycles with the wall-clock seeds pinned; 27 door-form rows
  and 23 value-edge rows rendered the same way. Accepted differences, for the 5b listening
  checkpoint: (1) `katp` now reaches every stage of an undeclared orbit, which is the point;
  (2) RETIRED in review round 1: the first cut gated the two send stages on `wet > 0`, which
  let the voice holding the orbit's lease silence the room for every other voice on it
  (`stack(pad.reverb(0), lead.reverb(0.4))` lost the lead's room, and swapping the arms changed
  the song, because the lease is first-rendered-wins), against the door's own promise that a dry
  voice on a wet orbit stays dry. The coordinator had accepted it as a note; the audio reviewer
  was right that it was not. `VoiceFactory` ran the stage on a TOUCHED field (non-null, a written
  0 included) and let `time`/`size` decide; the slot twin of touched is `KatalystKnob.written`
  ("the owner's map carries the key with a finite value"), and `sendStageRuns` is
  `written || (finite && > 0)`. The second half keeps the 2026-09-17 decision for authored
  constants: a declared `k.reverb(r => r.wet(0.0).size(6))` that no pattern touches still rents
  nothing. Making `wet` mean the amount is 5b-2's, with the listening checkpoint. After the fix
  76 of 80 rendered rows are identical at HEAD and on the tree; the four that differ are (1) and
  the three non-finite rows of (3).
  (3) a non-finite `delay.time` or `reverb.size` is the slot vocabulary's OFF where the field
  path substituted the shared constant (the master still does), and a non-finite `duck.attack`
  now takes its constant where the field path handed the duck a NaN.
  (4) A sixth accepted difference, and the one that is NOT an improvement (round 2): a non-finite
  `wet` (`reverb("NaN")`, `delay("NaN")`, a hand-written `katp("reverb.wet", NaN)`) switched the
  stage ON at the shared constants on the field path, because the field was non-null and therefore
  touched and `VoiceFactory`'s `orDefault` swallowed the NaN; now `KatalystKnob.written` is false
  for a non-finite value, so the stage is OFF for the whole orbit and another voice sending into
  it loses its room. Accepted because it is the slot vocabulary being consistent with itself
  ("non-finite is unset" on every knob, which is what lets a cleared slot read as untouched at
  all) and because no ordinary spelling produces it: a mapper over an unset field yields null, a
  rest calls no setter, the doors fill finite constants, and KlangScript division by zero throws.
  Guard: `KatalystSlotResolverSpec`. Also BETTER than the field path, worth claiming so nobody
  re-derives them as regressions: a non-finite `phaser.wet` (the setter used to drop it and keep
  the previous owner's depth), a non-finite `phaser.floor` (it used to reach the dry coefficient
  raw and turn the additive law into the crossfade law, `cos(depth*pi/2)^2`), and a non-finite
  compressor knob from a SECOND owner (the first owner's value used to stay).
  Decided 2026-09-19 for (3):
  leave it. Substituting in the writers would break the resolver contract above ("non-finite
  time is off") and two guards that author `SLOT_UNSET` as an off state. Option kept on file:
  read a non-finite OVERRIDE from the map as absent, so the knob falls back to its AUTHORED
  default, the rule the `oscParams` leaf got the same day; it buys one rule across both bags and
  no parity with HEAD or the master on this knob. Also on file: `Reverb.normalizeSize(+Infinity)`
  is 1.0, the largest room, not off; left alone. Readers of the bus FIELDS that remain after
  5b-1: the send amounts in `SendRenderer` (5b-2), `VoiceFactory`'s construction of the
  `Voice.*` objects, and the per-voice phaser of a CUSTOM pipeline that declares
  `StageDsl.Phaser` (`FilterPipelineBuilder`), so `phaser` cannot leave the wire in 5b-3 without
  deciding what that stage reads. **Decided 2026-09-19 with the maintainer:** it is not decided
  per stage. Phase 3 of the signal-flow plan retires the whole Pipeline DSL in one go; its stages
  move where they belong (filters, crush, coarse, distort, tremolo and the VCA to the instrument's
  `.classic()`; the phaser is a bus effect and is already the Katalyst's). So 5b-3 leaves the
  phaser fields on the wire for the custom pipeline's stage, and they leave with the pipeline in
  phase 3. **For the 5b-2 plan (event-stream survey over 256 cycles, sample
  voices included, 2026-09-19):** NO built-in song and no frozen piece carries two distinct send
  amounts on one orbit in one cycle, so the per-voice send becoming the owner's insert amount
  changes no song in the repo. 5 of 953 playable doc examples do, four of them the "as much X as
  Y" idiom (`reverb(wet = delay.wet, ...)` with a patterned source) and one a wet of 0 next to a
  positive one (`s("hh*4").delay(0.3, 0.25).delay(wet = mul("1 0 1 0"))`, "delay on every other
  hat only"); 5b-2 has to decide what those spellings mean once the send is the orbit's insert.
  A raw wire producer can no longer hand an orbit private body
  or formant bands: a body is an index into the shared catalogue (only the warmup did).
  As built (2026-09-18): a slot-driven writer is a class with `resolve(params)` and `apply()`
  (`KatalystSlotWriters.kt`), one per stage kind, over `KatalystKnob`s that hold a slot NAME and the
  authored number. `apply` runs every block and writes numbers already in hand; `resolve` runs only
  when the map instance changes and is the one place a lookup happens and a composite (`FilterDef`,
  `Voice.Compressor`, `Voice.Ducking`) is allocated. The identity gate lives in `KatalystChain`, not
  in the cylinder, because a crossfade has TWO chains at different resolve states (a chain faded in
  from the pending poll resolved from no owner). `reset()` and `retire()` drop the reference, so an
  idle chain pins no voice's map. `duckDeclared` became the duck writer's live flag: `.katp` can name
  the sidechain orbit after the chain was built, and `ducksWith` has to see that. The `.katp` rest
  rule is the 2026-09-16 one for free (the string-lift helper). Round 1 of the coordinator's review
  moved the sprudel storage: `oscParams` and `katalystParams` are mutable single-owner maps now, a
  door writes one KEY in place, `clone()` deep-copies them and `toVoiceData` hands the wire a copy
  (see `sprudel/MEMORY.md`). Round 1 also found the duck: `ducksWith` is asked BEFORE the arriving
  chain's writers may run, so a duck named through `.katp("duck.orbit", n)` read as "no duck" at the
  handover and the swap ramped the reduction out and then dropped a fresh one on the orbit a block
  later. `KatalystChain.resolveParams` is the resolve half without the write, and both swap paths
  (`beginFade`, the late-duck correction in `updateFromVoice`) call it first; the cylinder keeps the
  owner's map in `ownerParams` for exactly that, dropped wherever the lease is. Guards:
  `CylinderChainCrossfadeSpec`, three slot-duck rows. The acceptance measured:
  `note("c3 e3 g3").sound("supersaw").reverb(wet = 0.5, size = 6).orbit(1).katalyst(Katalyst(k =>
  k.classic()))` rendered byte-identically to the same pattern WITHOUT the reverb door before this
  step, and differs after it (last-cycle rms 0.1333 against 0.1301, tail 0.0085 against 0.0080).
  Guards: `CylinderKatalystParamsSpec`, `LangKatalystParamSpec`. Round 2 removed the `.gain(1.0)`
  that acceptance needed: see the voice-driven rule below. Its parity fell out: step 3a's
  `audible-classic` (a declared classic under voices carrying `reverb(wet 0.5, size 6)`) still
  hashes identically to `audible-none` (no declaration at all), `6c8540cc…`, so the slot path and
  the voice path agree on the reverb sample for sample.
- **Step 4 as built (2026-09-18): `eq` and `gain` are real DSP on the orbit bus.**
  `KatalystEqEffect` runs the declared section list through two `EqCore` instances, left and right,
  with the section types coming from `eqSectionSpec`, the variant-to-Int `when` hoisted out of
  `IgnitorDslRuntime`'s Eq arm into `audio_be/filters/EqSectionSpec.kt` so the per-voice and the
  orbit adapter cannot drift. Smoothing is the SURFACE's, as `EqCore`'s contract demands, and it is
  the house precedent: two pre-built banks are ping-ponged and a changed curve is installed through
  `KatalystFilterSwap`'s 12 ms crossfade, so a `.katp` on an EQ knob costs one bank swap per changed
  block (a `reset` plus one coefficient computation per section per channel, and 12 ms of two banks
  running), bounded by the once-per-block param read and allocating nothing after the swap's
  scratch has grown once. Click-free holds for changes at least 12 ms apart; two inside one fade
  drop the oldest bank, the faint tick body and vowel have always had (SUPERSEDED by Katalyst
  5c-6: the EQ now installs into any of `MAX_BANKS + 2` pre-built banks the swap no longer holds,
  so an audible bank is never zeroed). Two banks were enough
  because a section list's SIZE is structure; body and vowel build a fresh bank per change only
  because a material decides how many bands it has. The flush lives in exactly one place, the
  install, so it has one failing row; `reset()` only clears the swap, which makes a parked bank
  unreachable. `KatalystGainEffect` multiplies the mix and ramped per sample across ONE block on a
  change (SUPERSEDED by Katalyst 5c-8: a `KnobGlide` over `KNOB_GLIDE_SECONDS`, per sample from
  the end, from where the fader stands), which is the longest ramp that always finishes before the next possible param read; the
  master snaps the same knob (its factor is fixed at chain build), so this is the improvement, not
  parity. Unity is bit-transparent on both stages, and a 0 dB bell stays bit-transparent through
  the core's explicit branch. Both stages are slot-driven on every chain, `voiceDriven` or not,
  because neither ever had a voice field; `KatalystPassThroughStage` went with the step (the
  scaffolding rule). Position semantics stay "where written", and the `Eq` wire KDoc now says what
  that means while the sends are per voice: an `eq` before `reverb` shapes the dry mix and not the
  reverb return, after it shapes both. Parity measured: one voice with a `band(300, 0.8, 6)` on the
  VOICE and the same band on its ORBIT render the orbit within 1e-12 (not bit-identical, because
  the voice path filters before gain and pan and the bus path after), two voices likewise, and at
  stage level the orbit stage is BIT-identical to `EqIgnitor` for all six section kinds. The frozen
  July song is unchanged (`8d79b9fc…`).
  Acceptance measured on the frozen song (32 cycles, 34.5 rpm, `specdist.py` 16-cycle windows):
  with `k.classic().eq(band 300 Hz, q 0.8, +2 dB)` on the guitar orbit and `k.classic().eq(band
  160 Hz, q 1.0, -3 dB)` on the drum orbit, the 320-to-160 gap closes from 1.8 dB to 0.1 dB and the
  hump drops (127 by 3.4, 160 by 2.0, 202 by 1.5 dB rel pink); absolute, EQ minus declared: 254 +1.7,
  320 +1.9, 403 +1.6, 127 -1.1, mid +0.9, presence +0.0. The q 0.8 bell is wide (+0.5 dB still at
  806 Hz); a q of 1.2 to 1.5 lifts 300 Hz with less skirt, the ear decides. A confound the measurement
  exposed: DECLARING a chain on the guitars drops their `.body(material = "wood")` (a declared chain
  owns its material), which alone is +1.5 dB at 254 and 508 and +1.6 at 2.5 to 5 kHz. Decided
  2026-09-18 for 5b: there is no string slot; a material is chain-declared (`k.classic().body("wood",
  b => b.wet(0.3)).eq(...)`, the null-material body stage of classic stays off and the declared one
  runs), and at 5b the pattern-side `material`/`vowel` arguments retire with the voice fields while
  `wet` and `floor` stay `katp` aliases. **Superseded the same day by step 5a-2 (below):** the
  material and the vowel are numeric INDEX slots into the shared tables, so the pattern doors keep
  working on a declared chain and the song text does not move.
- **Step 5a-2, decided 2026-09-18 with the maintainer: the door replaces, the names are index slots.**
  Two cleanups before 5b, one review loop and one commit, byte-identical for undeclared songs:
  (1) `.katalyst(dsl)` replaces (see §0), `KatalystAppend`, `KatalystDsl.plus` and the "A plus B"
  KDoc go, `k.classic()` appends its block once per builder; (2) `body.material` and `vowel.vowel`
  become `IgnitorDsl` slots holding an INDEX into `BodyMaterials.names` and a flattened
  register-by-vowel catalogue in `VowelBands`, index 0 = `none`, unset or out of range = the stage
  is off, the same shape `duck.orbit` already has. Classic carries both as unset `Param` slots; the
  pattern doors `.body("wood", ...)` and `.vowel("bass:a", ...)` write the index plus their
  companions; the chain builders `k.body("wood", ...)` write a `Constant`; name-to-index and
  index-to-bands live in one place next to the tables in `audio_bridge`, so both doors and the
  resolver agree. A typed (string) param kind was considered and not needed: the tables are closed
  lists, and a number is what the wire already carries. Off stages cost nothing new: `configure(null)`
  clears the filter swap, whose `process` returns on its first line, the path the born-with chain
  runs today. With this the 5b material wall is gone and the frozen July song keeps its body on a
  declared chain.
  **Round 1 of 5a-2 (2026-09-18):** both reviewers found that a material-only `body("wood")`
  reached a declared classic as mix 0.0 (classic's `body.wet` default was a SET 0.0, never the
  unset the resolver substitutes `BODY_WET` for), bit-identically dry where the born-with path
  plays it at `BODY_WET`. Decided with the maintainer, and made a rule (CLAUDE.md rules register,
  `/dsl-design` §4, checklist 11): **a compound door fills per param, at the door, everywhere.**
  A call that names a stage writes every companion it left out and the event has not set, from
  the constant in `audio_bridge/constants/`; an explicit value is never overwritten (the first
  wording of this sentence said "the gate is never invented", which rounds 1 to 3 of 5a-3 showed
  to be wrong for the sends; `/dsl-design` §4 holds the corrected text and is its one home). The
  reverb and delay doors were the blueprint; `body`, `vowel` and `compressor`
  follow in **step 5a-3** (the compressor's recorded "no fill" asymmetry ends, byte-identical
  because the engine applied the same constants to an unset field), which also gives the two
  param bags a class of their own, `ParamBag` in sprudel, with the rule as one method:
  `setOrDefault(name, value, default)` writes the value when given, else the default only when the
  name is absent; the gate of a stage is always written with `set`, never `setOrDefault`; the
  per-door fill functions go. 5a-2 itself makes classic's `body.wet` and `vowel.wet` unset `Param`
  slots so a raw `katp("body.material", n)` resolves through the engine's non-finite guard to the
  same constant, and adds the missing no-wet guards. The voice-side compound doors (`adsr`, `lpf`, FM and pitch
  envelopes) adopt the rule in phase 3 of the signal-flow plan, where their literal engine defaults
  (`AdsrDef`, the 0.707 q, the vibrato rate) move into `audio_bridge/constants/`.
  Open item recorded, not fixed: `configure(null)` on a running body or vowel stage is a hard cut
  (`KatalystFilterSwap.clear`) while every material change crossfades; pre-existing timing (the
  same moment an owner handover already hits on the voice path). Shape when someone is in the file:
  a `KatalystFilterSwap.fadeOut()` that keeps the current pair as old with no new pair and ramps
  to dry over the same 12 ms, called from `configure(null)`, with `reset()` kept for the lifecycle
  paths where the signal is already at weight zero. CLOSED by Katalyst 5c-6 (2026-09-19), in
  another spelling: `clear()` itself fades to dry over `KNOB_GLIDE_SECONDS` and enters Off only on
  landing, `reset()` stays the synchronous hard cut for deactivation and retire.
- **5a-2 and 5a-3 as built (2026-09-18).** 5a-2: round 1 blind (two reviewers) found the same MAJOR
  (classic's `body.wet` a SET 0.0); round 2 on the high tier clean. 5a-3: round 1 on the high tier
  found one MAJOR, the coordinator's rule TEXT (it named `wet` as the gate of the sends, which the
  blueprint `reverb(size = 4)` contradicts; fixed in the register and `/dsl-design` §4, with
  checklist 12), and a MINOR batch: the fills had handed `setOrDefault` the voice field, so a
  `katp` between two calls of the same door was overwritten (now every setter writes its own slot
  with `set` and every companion fill passes null, 22 sites); the phaser joined the rule (five
  slots and five fields, the constants `VoiceFactory` substitutes); a duck clear could not leave a
  stale slot once each duck setter wrote its own slot, and the `SLOT_UNSET` arm first added for it
  was removed again as unreachable (a non-numeric token never reaches the setters); a cleared-slot
  row in `ParamBagSpec`; the "slots apply in order" sentence on the body, vowel, compressor and
  phaser doors. Decisions: `setOrDefault(name, value, default)` keeps its value parameter although
  no production caller passes a non-null value today (the phase 3 voice doors will; checklist 12
  is written in its terms). Behaviour changes recorded for 5a-3, none reaching a shipped or frozen
  song: (1) a `katp` on a companion slot survives a later call of the same door, in both
  directions; (2) `FilterDef.Body`/`Formant` carry an explicit floor and the phaser its five fields
  on the wire where they carried null, the same numbers the engine substituted; (3) a mapper or a
  `merge` on a companion reads the filled field where it read null (`body("wood").body(wet =
  mul(2))` is 1.0, was 0.5), the shape the reverb door already had. The five
  `fillCompressorDefaults()` calls per full compressor call stay: a trailing fill would need its
  own reinterpret step, cloning every event and filling a rest in a control pattern. Both frozen
  songs byte-identical after every edit (`8d79b9fc…`, Seltsamere Dinge `017da26d…`).
- **5a-3, round 3 (2026-09-18, strongest tier).** One MAJOR, inherited from step 5a and found by a
  door-by-door table against the rule's two closed lists: the duck filled its companions on ANY
  knob, so on a custom chain `duck(attack = 0.3)` wrote `duck.depth = 0.0` over a chain-authored
  0.8 and the ducking stopped. Fixed: the duck fills only when THIS call named an orbit; a bare
  `duck()` writes nothing, slot included; render rows prove a tail-only call leaves a chain-authored
  depth alone (0 counts) against a no-duck control (14572 counts). The rule's text, which had
  escaped three times because it was copied to a dozen sites, now lives only in `/dsl-design` §4;
  every other site keeps its own facts and points there. Evidence audit closed: the phaser fill,
  which no frozen song exercises, is backed by IrishLamentTechno rendered at HEAD and on the final
  tree (`a0c30f33…`, identical); both frozen songs re-rendered on the final tree. A lesson for
  render rows: the offline renderer of `:jvmTest` has no sample bank, so `s("bd*4")` is silent
  there and a row that asserts "0 counts" on a sample source passes on silence; use a synth source.
- **Fixed as its own step on 2026-09-18 (see the commit after 5a-3):** found in 5a-3's round 3, a NaN
  body or vowel mix on the BORN-WITH path (`body("wood", wet = "NaN")`; `toVoiceData` guards null,
  not non-finite) makes `KatalystBodyEffect.configure`'s `body.mix != curMix` true forever (NaN is
  self-unequal), so every block allocates two filter banks on the audio thread and restarts the
  12 ms crossfade, which never completes. The declared path is safe (`KatalystSlots.bodyDef`
  substitutes the constant). Smallest guard: substitute at the entry of `configure` and its formant
  twin (`if (mix.isFinite()) mix else BODY_WET`, the same for the floor), the rule `bodyDef`
  already applies one layer up, so both paths agree by construction. Violates the stone rule on
  hot-path allocation for a user-reachable input, hence before 5b.
- **DONE in Katalyst 5c-7 (2026-09-19; was open, pre-existing, recorded 2026-09-18, performance, not correctness):** `writeCompressor`
  (`KatalystChainBuilder.kt`) writes all five `Compressor` setters every block on a running orbit,
  and each setter calls `updateCoefficients()` with three `exp()`, about fifteen per block for
  numbers that did not move; the setter's own KDoc says it is not meant per block. No allocation.
  Was: the guard should be a stored-five comparison in `writeCompressor`. As built in 5c-7:
  `writeCompressor` is deleted; `KatalystCompressorEffect.configure(settings)` writes the five
  setters only when the writer hands it a NEW settings object (a reference compare, `settings !==
  applied`), which is safe because the writer builds a fresh, already-substituted `Voice.Compressor`
  only when the owner's map changes. A running orbit costs one compare per block.
- **RESOLVED by Katalyst 5b-3 (the duck fields are gone; was open, pre-existing, LIVE since 5b-1):** a `merge` whose control carries `duck(1)` takes the control's
  filled slots (`duck.depth` 0.0) but not its null fields, so after the merge the voice path and
  the declared path disagree on the depth. No song merges a duck; step 5b removes the fields and
  the disagreement with them. Optional alongside: fill the duck's voice fields on an orbit-named
  call, byte-identical by the phaser's argument, which would make every filled door readable.
- **DONE in Katalyst 5c-5 (2026-09-19; was open, found in the 5c-1 review): the delay's tail ceiling could
  under-report a real tail** after a self-oscillating drain or after a feedback reduced to (near)
  zero, live or on return; worst measured an echo at about -3 dBFS dropped at a 0.7 to 0.0
  handover with a silent new owner. Details and the repair shapes in `audio/MEMORY.md`. Any fix
  moves the block on which an orbit resets, so it belongs with a listening checkpoint (5c).
  Since 5b-2 it is also reachable on the CHAIN-SWAP ring-out, where the leaving chain retires on
  the first block its ceiling says silent, without the ten silent blocks the orbit path waits
  for (a feedback glide still falling to near zero after the fade can end it one echo early).
  The repair the 5b-2 review recommends: `TailCeiling` tracks the largest |feedback| seen in the
  running window, as it tracks the input peak, and uses it for both terms; O(1), closes the class
  on every path, unchanged for a steady feedback.
  **As built in 5c-5:** exactly that repair, plus, for a return from a self-oscillating drain
  (the frozen window's own feedback is above 1 there, so the window maximum alone does not close
  it), the drain hands its feedback to the ceiling and, only above 1, the delay re-measures what
  its tap can still reach (one O(delay) scan on that return only). Measured before and after at
  effect level: a 0.7 to 0.0 handover dropped repeats at -9 dBFS in about 30 % of phases, now
  nothing; the chain-swap ring-out the same; a self-oscillating drain and a tame return dropped
  -39 to -87 dBFS, now -110 dBFS or quieter. A reset or retire only ever moves LATER: at most
  about two windows after a fall; after a self-oscillating drain the orbit holds the ring's real
  tail (minutes at a returning feedback of 0.99, an idle network). 18 of 18 songs bit-identical.
  In review, the audio reviewer's independent fuzz (600 runs, time held fixed) found the old
  ceiling wrong in 322 and the new one in none.
- **OPEN, pre-existing, found in the 5c-5 review (2026-09-19): a LENGTHENED tap can re-reach
  content the ceiling has already written off.** At a low feedback the ceiling rightly says "no
  tail" once the short tap has passed a burst, but a new owner with a longer `delay.time` reads
  the same ring further back: a delay at 0.03 s and feedback 0 takes a burst at -1 dBFS, two
  windows later a quiet owner arrives at 0.18 s, and the orbit resets over a ring that would
  still emit -1 dBFS. Live and on return; the old and the new ceiling agree. Arguably right to
  cut (at feedback 0 it is a stale re-emission), but a reset in the middle of it is a cut. Decide
  with the other 5c switch edges; a candidate is to re-measure on a time lengthening, as the
  self-oscillating return already does.
- **Candidate, pre-existing (5c-5 review): a LIVE self-oscillating delay that falls to a tame
  feedback decays from `CEILING_MAX` (1e6)**, about twice the ring's real tail at 0.99, because
  the ceiling saturates at 1e6 while softCap bounds every cell at the `cap`. Saturating the
  delay's ceiling at the cap would align it with the drain path. Idle hold only, never audio.
- **DECIDED 2026-09-19 with the maintainer: how every orbit stage switches and changes (step 5c).**
  The principle: **always glide from the CURRENT state of the effect to the target state; only the
  very first initialisation is instant.** It is the knob-glide rule (`docs/plans/knob-glide.md`)
  applied to switching:
  - OFF is "glide the stage's mix to 0 over 50 ms, then release its resources"; ON is "install at
    mix 0 and glide up". An owner that returns mid-fade simply retargets, and the fade turns
    around where it stands. Switching on fades in.
  - A DIFFERENT material arriving mid-fade (or an off arriving mid-crossfade): crossfade from what
    is sounding NOW (the old bank at its current, frozen level) to the new one at its target
    level. One ramp, two banks, no tick, no delay. Today's "drop the oldest, may tick faintly"
    retires.
  - The compressor switches by gliding its gain reduction to 0 dB over the same 50 ms, not by
    letting its own release run out. **As built in 5c-7 (2026-09-19):** a linear blend with dry,
    `out = dry + w * (compressed - dry)`, the instance running on the live signal until `w` lands
    on exactly 0 (a dB ramp of the reduction was offered as a contained change and not needed for
    a safety net); ON fades in the same way (its own attack clamping a live signal measured 8 to
    26 dB of low-frequency swell over the floor at attacks of 5 ms or less); threshold, the
    INVERSE ratio (the curve is linear in `1/ratio - 1`; review round 1) and knee glide PER SAMPLE (a memoryless gain computer's knobs behave like LEVEL knobs: a per-block
    glide modelled a -31 to -69 dB zipper), attack and release do not (their jumps measured at the
    floor). Off at 3 to 12 dB of reduction: -10..-39 dB before, -78..-86 after (floor -81..-83);
    knob jumps to the floor. 18 of 18 songs bit-identical (no song switches or changes a
    compressor while its orbit sounds). Listening checkpoint: WAVs in the session scratchpad
    `5c7/listen/`.
  - **The fader and the orbit's liveness, as built in 5c-8 (2026-09-19):** an orbit never
    deactivates while its `VoiceLease` is held, so a fader patterned through exactly 0 no longer
    resets a playing orbit (the click measured -17.7 dB, now -88); the fader glides over
    `KNOB_GLIDE_SECONDS` from where it stands (was one block), every edge to the floor, a
    polarity flip and 0.01 to 4 included. 15 of 18 songs bit-identical; Synthsturm and Synthkura
    differ at -140 dB (the reverb's anti-denormal residue is no longer zeroed by the resets that
    are gone). **The Synthsale Pipers' Last Rave differs audibly 163 to 234 s:** orbit 6's
    silent supersaw runs a phaser at 1/13 Hz whose sweep HEAD restarted at each of about 167
    resets (a cleanup-schedule-dependent offset the Phaser's own KDoc rejects) and which now runs
    on from the section's downbeat; stripping the phaser from both sides leaves 3e-16. Listening
    checkpoint pending: `5c8/listen/last-rave-155-240-{head,tree}.wav`.
  - Known and accepted (5c-8 review): a fader edge takes effect when the lease changes hands, not
    when the pattern changes, and then glides 50 ms, so a patterned fader is not a rhythmic gate.
    No song does it; a gate would be its own feature.
  - **The phaser and the duck, as built in 5c-9 (2026-09-20):** the phaser needs no fade of its
    own, because OFF is exactly `dryC = 1, wetC = 0`, so the per-sample glide of the C4 law's two
    coefficients IS the crossfade (verified in review against an exact crossfade: one ulp), and
    the cascade is dropped only on the block it lands; `centre` and `sweep` glide per block with
    alpha continuous across the seam; `rate` does not glide. The duck rides a weight to exactly
    0 dB (`gain = 1 + w * (g - 1)`, no dry copy: ducking is one multiply) and lets go of the
    envelope and the source orbit only then; `depth` glides per sample, `attack` does not. Neither
    got state classes (the complexity rule, the 5c-8 precedent). Handover clicks were the loudest
    thing in both stages (the duck's -6.0 dB HF and a thump LOUDER than the signal's low-frequency
    RMS) and are now at their floors. 17 of 18 songs bit-identical; **The Synthsale Pipers' Last
    Rave differs 66 to 246 s, worst -26.3 dB re local peak**: orbit 5 hosts two phasers (the pad's
    at centre 1400 and the bassline's at 3500), so the orbit's notch used to JUMP between them at
    every handover and now travels in 50 ms; level and timing are unchanged (per-0.5 s RMS equal
    to 0.00 dB, cross-correlation lag 0), 12 bursts in 35 s, and stripping the three `.phaser(...)`
    calls makes both sides bit-identical. Listening checkpoint: `5c9/listen/`.
  - **The resonator morph, as built in 5c-10 (2026-09-20):** a MATERIAL change on body or vowel can
    travel instead of crossfading. The decided rules are built exactly: pairing by position, log
    space for frequency and Q, linear gain per sample, a band on one side only keeping its
    frequency and fading in place, a preallocated capacity (`MORPH_CAPACITY = 8`, pinned against
    both catalogues), a cold arrival masked by its fade-in, an exact landing and a snap on the
    first morph. It covers a material change only: `wet` and `floor` changes, the on and off edges
    and a fading-out pair keep 5c-6's output crossfade. The `v1` question is CLOSED: the fold
    stays, because `v1` buys 29 dB on an artifact 121 dB down, needs a second band kind in the hot
    loop, and would change the arithmetic of every settled vowel. The 5c-3 note "a Q glide needs a
    setter on the same ramp path" is done (`BaseSvf.retune`), and the bank's coefficient ramp is a
    block long, not the shared 32 samples. With the morph off the whole corpus is bit-identical to
    `ad9f0d64` (17 of 17, measured twice, the second time after the shared SVF clamps were
    extracted); with it on, Seltsamere Dinge and frozen Stranger Things differ at their vowel
    changes (worst -21.7 and -27.5 dB re peak) and nothing else does.
  - **LISTENED 2026-09-20, the maintainer, on 5c-7 to 5c-10.** 5c-7 (the compressor) and 5c-8 (the
    fader, the orbit's liveness and the Last Rave phaser section): "fine ... the clicks are
    successfully eliminated". 5c-9: the phaser's HIGH click is gone, but **a low "blub" remains on
    every phaser transition** (`phaser.wet` and the other settings), audible because that listening
    material is nearly silent. It measured at the floor of the metric we used, which is that
    metric's blind spot; recorded with the transition-time question in
    `future/transition-times.md` section 4, because it may be an argument for a different time on
    that stage rather than a defect in the law. 5c-10: **the morph is REJECTED, for body and for
    vowel both.** It makes an "8-bit laser shot" on `body-woodglass` and `body-woodglass-fast` and
    the same on the vowel; the maintainer's diagnosis, which the measurements agree with: "moving
    the filter resonances and q creates an audible filter-sweep". It is not audible on
    `song-stranger-synths`, where the vowel changes are slow and the mix is dense, which is why the
    synthetic cases were the ones that decided it. **The output crossfade stays, on both stages.**
    The metric could not have caught this: a travelling resonance is not a discontinuity, so it
    reads clean while the ear hears a sweep. That blind spot is recorded in
    `future/transition-times.md` section 1.
  - **DECIDED 2026-09-20 with the maintainer, after that listening: the swap's shape and its time.**
    The 10-bank pool with its parking overflow is replaced by the maintainer's model: **two banks
    and ONE parking slot.** Crossfade the sounding bank into the new one; a change arriving mid-fade
    is parked; a further change REPLACES what is parked (latest wins); when the fade lands, a parked
    change starts its own crossfade. No pool, no cap, no drop rule, and the EQ's 12-bank pool goes
    with them. It is not "unbounded banks": the mechanism can never need more than two, which is why
    it is the raw shape rather than a clamp. It rate-limits material changes to one per fade, and
    the maintainer accepts the consequence: a rate faster than that "is chaotic in any case".
    **The swap's fade becomes 20 ms, its own constant**, separate from `KNOB_GLIDE_SECONDS`, which
    stays 50 ms for level and dynamics glides (the compressor's release gets WORSE if shortened: it
    already measures -62 to -65 dB releasing 25 dB in 50 ms). Evidence for 20: 12 ms was the swap's
    value before 5c-6 and the click investigation measured a single material change there at worst
    -66 dB against a -78 to -93 floor, so the clicks of that era were the hard off/on and the
    drop-the-oldest restart, not the 12 ms fade. 50 ms was adopted to keep ONE constant, not because
    12 clicked. At 20 ms the rate limit is 50 changes a second, so 64ths at 174 BPM (21 ms apart)
    still land on time. To measure before it is believed: 20 ms on both the HF and the sub-60 Hz
    metric, across wood (whose lowest mode is about 100 Hz, one period of which is 10 ms) and the
    other materials, vowels, and note frequencies from a 55 Hz bass upward, single changes and runs;
    and the cold start, which a shorter fade exposes rather than hides.
  - **CLOSED by that listening (was open, the maintainer's ear, 5c-10): morph or crossfade, per
    effect.** Both are live behind
    `KatalystBodyEffect.MORPH` and `KatalystFormantEffect.MORPH`; the loser is DELETED with the
    choice. Evidence. VOWEL, for the morph: the formant really travels (F1 750 to 234 Hz in
    fifteen continuous steps against the crossfade's single jump), 12 to 19 dB fewer artifacts on
    the large F1/F2 moves and never worse than a wash, the low-end penalty 2 to 6 dB at -55 dB or
    quieter, Q does not move inside a register so none of the new Q-glide risk is engaged, and the
    one-pole smear cannot bite above 50 ms, which no pattern in the repertoire crosses. BODY,
    against the morph: pairing by position sends wood's 100 Hz mode to glass's 700 Hz, a 2.8-octave
    chirp in 50 ms that costs 11 dB more below 60 Hz at an audible -31 dB re signal and up to 3.3 dB
    of level over both endpoints, while the click metric is a wash; the one case where the morph
    clearly wins (a band-count change, where nothing travels) is unreachable from any song text.
    Cost: the morph is 2 to 7 % more expensive, not cheaper.
  - **OPEN for the maintainer, MEASURED in the 5c-9 review (2026-09-20): a `duck.orbit` switch onto
    an already-sounding source clicks.** Moving the sidechain to a louder orbit steps the reduction
    by -40.8 to -19.0 dB HF against a floor of -52.8, and the already-sounding sub-case (0.2 to
    0.9) is no better than the silent one, so the old argument ("it steps by exactly as much as
    that orbit's own onset would") bounds the magnitude but not the audibility: an orbit-to-orbit
    switch brings no new sound into the mix to mask the drop. The other direction is at the floor.
    Unchanged by 5c-9 and the same on HEAD. What it needs is a decision about what a sidechain
    switch MEANS: (a) leave it (the ducker's instantaneous attack is its character, "the Motor
    stays raw"); (b) blend the two sidechain sources over the glide (one extra envelope read per
    sample while switching); (c) glide the reduction to 0 dB, switch, and glide back (an audible
    dip, but no new machinery). Recommendation: (b) if it is to be fixed, because it keeps the
    duck working through the switch.
  - Also unchanged from HEAD, stated so nobody reads it as new (5c-9): a duck handover does not
    carry the arriving chain's `duck.depth`, so two chains with different depths on one orbit step
    the reduction by the difference.
  - The EQ gets no off door: it exists only on a declared chain, and a chain swap already
    crossfades.
  - The maintainer remembers audible clicks on material changes with today's 12 ms crossfade.
    Suspects, unmeasured: the new bank's cold start rings up for longer than 12 ms at the body's
    low modes; a second change mid-crossfade drops the oldest bank; a linear blend of two
    uncorrelated materials dips about 3 dB. First task of the filter swap's conversion: a render
    that alternates materials, measured at 12 ms and at 50 ms. **MEASURED 2026-09-19 (HEAD
    `51bec86b`)**, metric: peak 0.7 ms RMS above 8 kHz relative to the signal, on sources
    band-limited to 3 kHz (steady state -78 to -93 dB, a hard switch -8 to -46 dB):
    - A single material change at today's 12 ms fade does NOT click: 30 body pairs and 7 vowel
      pairs, saw and pluck, 44.1 and 48 kHz, worst -66 dB; a whole-engine render of
      `body("<wood glass>")` reads the same as no change.
    - The cold start is not a click but a SWELL: on bell the new ring arrives up to 190 ms late,
      up to 4 dB under the quieter material. 50 ms halves it; a warm start removes it and is
      won't-implement (it buys nothing against clicks).
    - The 3 dB linear-blend dip did not appear (both banks share a dry path).
    - The clicks the maintainer heard are elsewhere: (1) the hard OFF (`reset()`) and the instant
      ON, -12 to -30 dB, reproduced in the engine with `body("<wood none>")` and with two patterns
      sharing one orbit where only one has a body (a click at EVERY owner handover); a fade to or
      from dry takes them to -70 to -92 dB. (2) A second change inside a running fade (today's
      drop-the-oldest), -11 to -34 dB, a hard-switch-class click, and a 50 ms fade makes it WORSE;
      crossfading from what sounds now takes it to -64 to -95 dB.
    - So for 5c: fade OFF and ON through the swap (to and from a dry partner, no new machinery);
      crossfade from what sounds now on a restart, MANDATORY together with a 50 ms fade (bound the
      number of outgoing banks, for example drop one whose weight is under a few percent: not yet
      measured); 50 ms is optional for clicks and keeps one constant; no warm start.
    - WAVs for listening (today's clicks and the fixes): the session scratchpad, `clicks/`.
  - **DECIDED 2026-09-19 with the maintainer (was open before the swap's second commit, found in the 5c-4 review,
    2026-09-19): rapid changes and "crossfade from what sounds now".** Taken literally, every
    restart freezes the outgoing banks and ramps them out together. A `.katp` that changes the EQ
    (or a pattern that changes a material) every block piles up outgoing banks: at 50 ms and 128
    frames a frozen weight falls about 6 % per block, so dozens of banks would be live at once, and
    the EQ's two-bank reuse would zero a bank that is still audible. Two options: (a) the master's
    precedent, "at most one queued swap": a change that arrives mid-fade is parked, the latest
    wins, and it is applied when the fade ends; two banks stay enough and nothing piles up; the
    price is up to one fade of lag (12 or 50 ms) on a rapid change. (b) N banks with a hard cap on
    outgoing pairs and a drop rule; the measured restart click (-11 to -34 dB) says the drop rule
    needs a listening test. Recommendation: (a), the smaller and already proven answer.
    **Decision: (b), a cap of banks, "for now", 10 suggested by the maintainer.** Sizing: a 50 ms
    fade is 17 to 19 blocks of 128 frames, so a change on EVERY block keeps at most about 19 banks
    sounding; 10 engages only under changes faster than about one per 5 ms and roughly halves the
    worst case. The banks are preallocated with the stage (no allocation in the hot path). The drop
    rule is decided by measurement in the step, not by argument: first drop the QUIETEST outgoing
    bank (not the oldest, which is today's measured -11 to -34 dB click); if that measures in the
    click class, a change arriving at a full pool is parked until a bank frees, latest wins (the
    master's precedent as the overflow rule, so nothing sounding is ever cut).
    **Measured in Katalyst 5c-6 (2026-09-19): quietest-drop -35 to -53 dB under a change every
    block (inside the hard-switch class), so the overflow rule is PARK: -76 to -96 dB.** Each
    outgoing bank keeps its OWN ramp (a shared ramp restarted on every change never lands, and the
    pool would fill on any stream faster than one change per fade). At 48 kHz a 50 ms fade is 2400
    frames, so a change every OTHER block already reaches the cap. Cost while fades run: +15 % for
    a body change every 125 ms, +25 % for a vowel change every 62.5 ms; nothing when settled.
    **Listened 2026-09-19 by the maintainer on 5c-6:** "Vowels change on each 16th and no clicks
    to be heard. Synthkura also sounds right." Fade law kept as built (each outgoing bank ramps
    from its frozen weight to 0 over the full fade). Considered and NOT taken (maintainer: "the
    current implementation is fine"): a constant-slope law (every outgoing weight falls at 1/L per
    sample, so a quiet bank lands early; about 8 banks instead of 20 under a change every block,
    never reaching the cap, about a third of the cost, the same sound for single changes). On file
    if rapid changes ever feel laggy or cost too much. Also accepted: A, B, then A again inside
    one fade builds a fresh A (continuous, a small swell, no turn-around by config search).
    Also for the second commit, from the same review: the hosts must tell INTENT from SOUND (an
    `active` that flips synchronously in `set` and `clear`, separate from "a pair is still
    sounding"); `clear` during a fade-out is idempotent and `set` during it retargets; and the
    cylinder's deactivation and `retire()` keep a synchronous HARD cut, or an orbit deactivates
    mid-fade-out and its next life resumes a stale fade on new material.
  - **One common resonator bank for body, vowel, and later a user surface where formants are
    specified one by one.** Body (`BodyFilter`) and vowel (`FormantFilter`) are already the same
    thing under the hood (a parallel bank of `SvfBPF` bandpasses in a `ParallelMixFilter`); they
    differ in the band count and the vowel's legacy gain fold. They merge first, as an identity
    step with the sound unchanged, so the glide and the morph are written once.
  - **Morph rules of the bank:** bands pair BY POSITION (band n to band n; for vowels formant n to
    formant n; for a user surface the order the user lists them), never by nearest frequency; a
    band that exists on one side only keeps its frequency and fades its gain from or to 0 in
    place; frequency and Q glide in log space, the gain linearly; the bank has a fixed capacity
    preallocated with the effect (today's tables need at most 8; a user surface states its own
    maximum). A band at gain 0 may be skipped; it then starts cold, which its own fade-in masks.
    Verified 2026-09-19 (step 5c-3): `SvfBPF` retunes its FREQUENCY while playing (`BaseSvf.setCutoff`
    ramps the coefficients over 32 samples and never touches the integrator state); its Q is a
    constructor value with no setter, so a Q glide needs a setter on the same ramp path, and since
    the vowel's band gain contains the clamped Q, the bank must move that gain with it. Largest
    band count today: 8 (body), 5 (vowel); nothing is sized by it.
    From the 5c-3 audio review, for the morph step: (1) a band gain moved once per block is a gain
    step every 128 frames (a 375 Hz zipper); the bank's sum loop needs a per-sample gain ramp on
    the coefficients' schedule, the LEVEL half of the glide helper. (2) The Q coupling can be
    REMOVED instead of followed: the vowel band's output `(dB * clampedQ * TAME) * (k * v1)` with
    `k = 1 / clampedQ` is algebraically `dB * TAME * v1`, the un-normalised bandpass tap; if vowel
    bands take `v1` the vowel gain stops depending on Q, a Q glide needs no companion gain move,
    and no mid-ramp k/a mismatch exists (ulp-level differences, fine in the step that changes the
    sound anyway). Decide this before writing a Q setter. (3) `setCutoff`'s 32-sample coefficient
    ramp retuned every 128 frames is a ramp-then-hold staircase repeating at 375 Hz; a ramp as long
    as the block would be continuous. `FILTER_SMOOTH_SAMPLES` is shared with the `lpf` envelope, so
    it is not simply changed; measure before choosing. (4) Mid-ramp stability at Q 80 to 140 is not
    a risk: checked analytically and over 20,000 random coefficient pairs, the pole radius never
    exceeded the larger endpoint's. First measurements of the morph step: a steady tone through a
    Q 140 band gliding its frequency per block (jump detector, 375 Hz sidebands, 32- against
    128-sample ramp); a Q glide with the gain ramped per sample against the `v1` form; a band
    fading in from gain 0 from a cold start.
  - Morph or output crossfade is chosen per effect BY EAR at the 5c checkpoint: the proposal is
    morph for vowels (a vowel change sweeps like a mouth), and for body whichever sounds better.
  - Order inside 5c for body and vowel: merge into one bank (identity), the click measurement, the
    state machine of the swap (identity, today's lifecycle), then the glides, fades and morph as
    the sound change.
    The merge is DONE 2026-09-19 (step 5c-3): one `ResonatorBank` of `SvfBPF` bands with linear
    gains, the two gain rules at the factories; the two orbit hosts stay two classes (the wire
    types share no supertype, `KatalystChain` finds each stage by type, the specs use typed seams;
    one class would cost a generic band type and a kind marker to save about 40 lines).
- **Decided 2026-09-18 with the maintainer, step 5c (after 5b): switching any stage on or off
  always crossfades, body and vowel included.** Off is a pass-through (the hosts process in place,
  so off costs one comparison), but the EDGE between on and off is never a hard cut: a stage whose
  off value is dry (wet 0, depth 0, amount 0, gain unity) switches continuously by construction; every
  other stage (body, vowel, eq, compressor, phaser cascade) goes through a crossfade of its own
  (for the resonators the swap's own fade, built in 5c-6 as `clear()`; the eq changes through the same swap; a gain-reduction ramp for the
  compressor), and the sends keep their drain. Latency-bearing stages never bypass mid-signal.
  With it, the stage lifecycle is written as a small state machine per effect, not as flags: a
  private sealed hierarchy (`Off`, `Active`, `Draining`, `FadingOut`, ...) as preallocated inner-class
  instances (no `when`, no `object`s: a state reaches the effect's resources) and a state that carries data preallocated once per
  effect so a transition on the audio thread allocates nothing. The delay and reverb enum
  lifecycles convert; the cylinder's swap bookkeeping (`outgoing`, `draining`, `duckingOut`,
  `duckFadingIn`, `pendingKey`) becomes one `SwapState`. Same rule for the voice strips in phase 3.
- **Future optimisation, noted 2026-09-18, not now:** an OFF ignitor effect node (`CoarseIgnitor`,
  `CrushIgnitor`, the distortion) still lets its upstream generate into a scratch buffer and then
  copies it into its output, so N off nodes in a row copy N times. Reading the gate knob BEFORE
  generating upstream and, when off, letting upstream generate straight into the node's own output
  buffer makes a chain of off nodes copy nothing; the gate knob is a per-block param read that does
  not depend on the upstream signal. Belongs with phase 3's node-level gate.
  **As built (2026-09-18).** `KatalystAppend`, its memo and `KatalystDsl.plus` are gone: a door
  allocates one `KatalystValue.Dsl` when it is written and stamps that instance onto every event, so
  replacing is cheaper per event than appending was and needs no memo at all. `KatalystBuilder
  .classic()` drops its block when `node.stages` already contains `KatalystDsl.classic.stages` as a
  contiguous run (content equality, so a hand-built classic counts); an explicit stage written twice
  still stacks. The catalogues grew the conversion: `BodyMaterials.indexOf`/`modesAt` and
  `VowelBands.indexOf`/`bandsAt`, with `VowelBands.names` a generated flat cross product of the five
  register spellings and the fifteen vowel spellings (76 entries, `none` at 0), and `modesFor` /
  `bandsFor` now go THROUGH the index, so the name path and the slot path cannot answer differently.
  Both tables build their band lists once and hand out the table's own instance, which turns the
  per-note `listOf(...)` on the voice path into a lookup and lets `KatalystBodyEffect.configure`
  reject an unchanged bank by identity. The two writers take a `KatalystKnob` for the index and do
  the lookup in `resolve`, never in `apply`. Acceptance measured: the frozen July song is unchanged
  (`8d79b9fc…`), and a supersaw chord carrying `body(material = "wood", wet = 0.3)` renders BIT
  identically (max difference 0 of 16-bit counts) with and without
  `.katalyst(Katalyst(k => k.classic()))` on its orbit, where the same song without the body differs
  by 3374 counts. Guards: `CatalogueIndexSpec` (bridge), `LangKatalystSpec` (the door replaces, one
  instance per door), `LangKatalystParamSpec` (the doors write the index), the door parity spec
  (`classic()` once, the name and the slot on both doors), `KatalystSlotResolverSpec` (the index
  slot re-resolves only on a map instance change, the two-probe coercion),
  `KatalystClassicMatchesUntouchedVoiceSpec` (born-with against declared, stage level) and
  `KatalystDoorFillRenderSpec` (the render; named `KatalystDeclaredBodyParitySpec` until 5b-1
  retired its declared-against-undeclared rows, which had become one chain compared with itself).
  **Round 1 of the review found one MAJOR, both reviewers independently.** A material-only
  `body("wood")` reached a declared classic chain at mix 0.0, bit-identically dry, while the
  born-with path played it at `BODY_WET`: classic declared `body.wet` as `Param(default = 0.0)`, and
  0.0 is a SET value, so `KatalystSlots.bodyDef` never saw "unset" and never substituted the
  constant, although it already does exactly that for a non-finite mix. Fixed by moving `body.wet`
  and `vowel.wet` to the UNSET family, which is the compressor's model (an unset knob takes its
  constant AT THE ENGINE, the door fills nothing). Safe for these two stages and nowhere else in
  that family, because their gate is the NAME: `bodyDef` returns null whenever the bands are null,
  so no amount can switch on an orbit that named no material. A door-side fill was NOT added here;
  that is a separate step. New guards: the material-only and vowel-only rows in
  `KatalystClassicMatchesUntouchedVoiceSpec`, a second render row in
  `KatalystDoorFillRenderSpec` (then `KatalystDeclaredBodyParitySpec`) for the `body(material = ...)` spelling, and a POSITIVE control
  there (a declared chain with no body stage must differ from the undeclared render by more than
  100 counts, measured 3374), which is the row that catches a declaration that never installs at
  all: with `Cylinders.requestChain` stubbed out, both parity rows pass at a difference of 0 and
  only the control goes red. `CatalogueIndexSpec` gained two ANCHORED rows (index 1 is wood at
  100 Hz, index 1 of the vowel catalogue is `bass:a` at 600 Hz) because the round-trip rows route
  both sides through the same functions and cannot see a uniform off-by-one; a shifted
  `modesByIndex` leaves the round trip green and kills the anchored row.
- **Phase 0, the mirror.** Wire model, identity, registry, registrar, doors on both surfaces,
  builder shells for the seven existing effects, `KatalystDsl.classic`, the per-cylinder swap,
  tests 1 to 3, 6, 7. No new sound is reachable yet; the engine is byte-identical.
- **Phase 1, the song's need.** `eq` and `gain` stages (step 4, done 2026-09-18: knobs are slots,
  not just literals, since 5a landed first), tests 4 and 5. Test 5's sends rule went with §D2.
  Acceptance: Der Schmetterling with the two EQs from the header, re-measured with the
  same tools: the 320 Hz band within 2 dB of the 160 Hz band, the guitars' 160 to 400 Hz within
  2 dB of their own 500 Hz to 1.6 kHz, no other region moved by more than 1 dB. Ears decide the
  numbers; the table referees them.
  **Measured 2026-09-18** (32 cycles at 34.5 rpm, `specdist.py <wav> 34.5 16`, and an absolute
  band table beside it): the shape's 320-to-160 gap closes from 1.8 dB to 0.1 dB (320 stays at
  -0.7, 160 goes +1.2 to -0.8) and the hump drops (127 +3.8 to +0.4, 202 +0.6 to -0.9), because
  the anchor band itself rose. In ABSOLUTE terms, against the same song with a declared classic
  chain and no EQ, the guitar bell adds +1.9 at 320, +1.7 at 254, +1.6 at 403, +1.1 at 508,
  +1.0 at 202 and +0.5 to +0.7 at 640 to 806 (a q of 0.8 bell is wide), the drum cut takes -1.1
  at 127 and -0.1 at 160, and nothing above 1.6 kHz moves by more than 0.1 dB, so mid +0.9,
  highmid +0.1, presence +0.0. Ears next. One confound the maintainer should know: DECLARING a
  chain on the guitar orbits already changes the song, because `.body(material = "wood")` did
  not survive a declaration before step 5a-2's index slot (+1.5 at 254 and 508, +1.6 at
  2.5 to 5 kHz, +0.84 dB rms), which is why the EQ's own effect is measured against the declared
  chain and not against the undeclared song.
- **Phase 2, the performance.** `Katalyst.param`, `.katp`, `VoiceData.katalystParams` (step 5a,
  done), tests 9, 10. The `EqCore` coefficient ramp is NOT part of it any more: step 4 built the
  smoothing at the surface as a bank crossfade (§7).
- **Phase 3, thickness.** `mics`, test 8.
- **Phase 4, consolidation.** SUPERSEDED 2026-09-19: body and vowel do NOT move onto `EqCore`.
  `EqCore` is snap-only by contract (a per-block coefficient change on it clicks), and the
  decision of 2026-09-19 ("how every orbit stage switches and changes") wants a per-block formant
  MORPH for vowels. Body and vowel already run on `SvfBPF`, the state-variable bandpass that
  tolerates coefficient changes while it plays; they merge into ONE resonator bank on it instead
  (identity first, unchanged sound), which is also the core of a later user surface for
  individual formants.

### 10. Parked decisions

- **§D1 DECIDED 2026-09-17:** `Katalyst.param(...)` on the object, `.katp(...)` on the pattern, as `oscp`.
  Noted for later: `Osc` / `oscp` are misnomers for the Ignitor concept and get renamed in their own item
  (the known debt in `/dsl-design` §5).
- **§D2 DISSOLVED 2026-09-17** by the signal-flow plan §7: no per-voice send amounts, so no send
  buffers; `reverb` and `delay` are insert-style stages at their list position, the master's model.
- **§D3 DECIDED 2026-09-17, DONE 2026-09-18 (step 3b):** the crossfade is extracted into
  `audio_be/Crossfade.kt`, one plain class both hosts own an instance of: the ramp, its length
  (`Crossfade.XFADE_SECONDS`, the one name now, `MasterBus.MASTER_XFADE_SECONDS` is gone), the
  block-quantized start and the per-sample loops. Measured: a master swap rendered before and after
  the extraction is byte-identical (sha256 `af5bdcff…`), and the frozen July song is unchanged
  (`8d79b9fc…`). No master-swap benchmark exists under `audio_benchmark/` and none was written.
  Two things did NOT move into the helper, each with a reason:
  - **The retarget queue.** The master queues a registered master's name; a cylinder's queue also
    holds a name whose `RegisterKatalyst` has not arrived and is polled on every block, and each
    host's eviction consults its own. Sharing it would buy indirection, not sharing.
  - **The blend law.** The master blends the two chains' OUTPUTS; a cylinder ramps the outgoing
    chain's INPUT down instead and adds its output at full weight. The orbit DRAINS its outgoing
    chain (see §9, step 3b), and a chain whose output has just been ramped to zero and is then
    re-added at full weight steps by the whole level of its tail: measured at ~0.045 rms on a
    sustained chord with a wet room, which is the click the fade exists to prevent. Ramping the
    input reaches zero input exactly where the drain takes over, so the seam is continuous by
    construction and the tail is never scaled, while the dry path still crossfades linearly through
    both chains' LINEAR inserts. The ramp is on the whole input, the SENDS included (the leaving
    chain gets ramped copies of the delay and reverb sends): without that, both chains' rooms are
    charged at full level for the length of the fade, and the leaving chain's drain rings out
    material from after the swap. Measured on a constant probe with a room and an echo on both
    chains, the fade's peak sits 17 % above the louder endpoint with the sends ramped and 32 %
    above it without. The master keeps the output blend, where the outgoing chain IS cut and a
    ramped output is the right shape. Guard: `CylinderChainCrossfadeSpec`, "the handover from the
    fade to the drain does not step".
- **§D4 DECIDED 2026-09-17:** the chain is the instrument. The bus doors become `katp` aliases on
  the orbit's chain; a stage absent from a declared chain makes the matching door a no-op.
- **§D5 DECIDED 2026-09-17:** the orbit `gain` stage stays, the group fader after the inserts.
  Gain and postgain were both pre-bus faders at one point (`SendRenderer`); postgain retires in
  the signal-flow plan §6, and `gain` means the tone-neutral level on every surface.
  **Built 2026-09-19** (signal-flow phase 2): `postgain` is retired, and `classic` ends in a unity
  `gain.gain` slot, last in the serial list before the duck. Bit-identical by construction (the
  fader returns early at exactly 1.0), verified against HEAD `df93f9f1` on three minimal
  multi-orbit rows. Since then `classic` declares no `eq` and DOES declare a `gain`, so every
  chain, the born-with one included, reads `katalystParams` for that one stage; the one home of
  that sentence is the `katp` door's KDoc. `KatalystClassicGainStageSpec` is a contract in the
  sense of the signal-flow plan §12 (its oracle, the chain without the fader, never expires).

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
