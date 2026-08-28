# Ignitor envelope ownership — the double-ADSR problem

> **Status (2026-08-27): Phases 0+1 SHIPPED, Phase 3 planned.** Found by the maintainer by ear
> ("the release tail is cut off, causing these ugly cracks"), then reproduced and measured with
> offline probe renders. The design was settled in a review round on 2026-08-27: build from
> **[THE PLAN](#-the-plan-agreed-2026-08-27)** below, which supersedes the earlier direction
> sections. Everything from "Design conversation" onward is kept as the reasoning record so it is
> not re-derived; where it conflicts with THE PLAN, THE PLAN wins.
>
> **Phases 0 and 1 (voice LIFETIME) are DONE and committed**, 2026-08-27: `controlRateValueOrNull`
> lost its render context, and the build now reports the release tail in `BuiltIgnitor`, so `oscp`
> overrides and release expressions reach lifetime. **Phase 3 (the VCA on/off model) is decided and
> not started** — explicit `.adsrOff()` / `Vca(on = false)`, nothing inferred.

## The short version

When an ignitor declares its own ADSR, the voice **also** applies one (the sprudel-level ADSR, or
`AdsrDef.defaultSynth` when the song declares none). The two multiply. Measured consequence: the
authored envelope comes out with **exactly twice the dB slope**, and — in one specific case — the
tail is cut off entirely.

Maintainer's position (2026-08-27): *"the Ignitor's total length should be the length of the note
until its release gate is reached. Otherwise we get two overlapping ADSR, one in the ignitor, one
in sprudel, which just makes every curve twice as steep. We need to be able to not put any ADSR in
sprudel at all, but the ignitor should fully complete its envelopes."*

## ✅ THE PLAN (agreed 2026-08-27)

Supersedes the "CURRENT DIRECTION", "BEST OPTION" and "Simplification" sections further down. Those
are kept for their reasoning; where they conflict with this section, this section wins.

**Scope of this work: voice LIFETIME only.** Whether the sprudel / pipeline VCA envelope applies at
all stays explicit and user-controlled, and is a separate pass (see "Still open" below).

### The design in one paragraph

The build already runs once per note-on, and it already resolves every `oscp` override into its leaf
(`IgnitorDslRuntime.kt:101` builds `ParamIgnitor(name, oscParams?.get(name) ?: default)`). So the
release tail is not *queried* off the DSL tree, it is **carried out of the build** as metadata beside
the runtime graph:

```kotlin
data class BuiltIgnitor(
    val ignitor: Ignitor,
    /** Longest release tail on the signal spine, in seconds.
     *  null = nothing tail-bearing found, OR the release time is itself modulated. */
    val releaseTailSec: Double? = null,
)
```

Voice lifetime stays `gate end + max(voice release, ignitor tail)`, with a tail that is now correct
under `oscp` overrides and under release *expressions*. `maxReleaseSec()` is deleted, not fixed.

### Phase 0 (own commit): `controlRateValueOrNull` loses its context parameter

```kotlin
fun controlRateValueOrNull(freqHz: Double): Double? = null
```

**Why:** the build needs to ask a node its value, and there is no `IgniteContext` at note-on. Rather
than make the parameter nullable (which weakens the contract for every implementor forever, to serve
one caller), remove it, because nothing uses it.

**Evidence, checked repo-wide:** 34 overrides exist. Every one either ignores `ctx` or forwards it
verbatim to another `controlRateValueOrNull`. **Not one dereferences it.**

| where | count | what it does with `ctx` |
|---|---|---|
| `ConstantIgnitor:30`, `ParamIgnitor:30`, `FreqIgnitor:14` | 3 | ignores it |
| `Ignitor.kt` pointwise combinators | 28 | forwards to children |
| `MemoizingIgnitor:46` | 1 | forwards to `inner` |
| `OpaqueIgnitor:36`, `PhasePoolSpec:298` (tests) | 2 | forwards / ignores |

The only thing on that path that genuinely needs a context is `blockStartValue` (`Ignitor.kt:82-84`),
and it needs it for the **scratch-render fallback**, not for the query. It keeps its `ctx` and simply
stops passing it on.

Mechanical scope: 34 signatures, ~80 forwarding calls inside `Ignitor.kt`, one real call site
(`Ignitors.kt:174`, the pulse `duty` path), ~28 spec call sites that can drop their `ctx()` fixture.
All compiler-caught: removing a parameter cannot silently change behaviour.

**The one capability given up:** the signature could in principle carry a node whose block-constant
value is derived from `ctx.voiceElapsedFrames`. Nothing does. The per-block variation the KDoc
anticipates (`FreqIgnitor` under detune) arrives through `freqHz`, not through `ctx`. If such a node
ever appears, re-adding a **required** parameter breaks the build at every implementor and forces a
decision at each one, which is the safe direction of change.

### Phase 1: `BuiltIgnitor`

1. **`IgnitorBuildCache` gains `freqHz`**, next to `soundIndex` / `orbit` / `random` (the established
   pattern for values that must reach every arm without threading a parameter). `toExciter` takes it;
   `IgnitorRegistry.createExciter` already receives it (`:108`). Base note frequency, not the
   modulated one, which is what a `Osc.freq()`-relative release should read.

2. **`buildIgnitor` / `buildRaw` return `BuiltIgnitor`**, and the cache stores `BuiltIgnitor` instead
   of `Ignitor` (`incConsumers()` moves to `entry.ignitor`).

3. **Absorb / discard lives in the two local helpers, not in 60 arms:**

   ```kotlin
   var spineTail: Double? = null
   fun IgnitorDsl.withMod(mod: Ignitor? = accumulatedMod): Ignitor {
       val built = buildIgnitor(oscParams, cache, mod)
       spineTail = maxOrNull(spineTail, built.releaseTailSec)   // signal path: absorb
       return built.ignitor
   }
   fun IgnitorDsl.noMod(): Ignitor = buildIgnitor(oscParams, cache).ignitor   // parameter: discard
   ```

   Every arm of the `when` keeps its current text. The rule lives in one place, and a new node type
   cannot forget it.

4. **Two arms change.** `Adsr` adds its own release:
   `releaseSec.noMod()` then `releaseSec-as-built.controlRateValueOrNull(freqHz)`, folded into
   `spineTail`. `Fm` keeps absorbing the modulator's tail, because today's `maxReleaseSec` does
   (`maxOf(carrier, modulator)`) and dropping it would silently shorten voices.

5. **`createExciter` returns `BuiltIgnitor?`**; seven spec call sites add `.ignitor`.

6. **`VoiceFactory` ordering flip:** build the exciter (`:272`) *before* computing `effectiveAdsr`
   (`:260-268`), which today runs first.

7. **Delete** `IgnitorDsl.maxReleaseSec()` (`:2043-2105`) with `IgnitorDslSpec.kt:135-147`,
   `IgnitorDslOptimizerSpec.kt:283-296` and `EqIgnitorSpec.kt:725-730`. Verify (do not assume) whether
   `IgnitorRegistry.get()` still needs to be the authored tree; its stated reason (`:32`, `:78`, and
   the guards at `IgnitorRegistryTest.kt:30`/`:67`) is `VoiceFactory`'s `maxReleaseSec` call, which is
   going away.

**New specs:**

- an `oscp` release override extends voice lifetime (Measurement 2, as a regression guard);
- an expression release (`pRel.mul(2)`) resolves exactly rather than falling back;
- a modulated release time yields `null`;
- **a subtree shared between a parameter position and the signal spine still counts** (see below);
- `Variants` reports the tail of the child it actually built, not the max across all of them.

### Why the cache-sharing spec matters

The build cache keys on `(dsl identity, mod identity)` only (`IgnitorBuildCache.getOrPut`, `:75-86`).
If the tail were accumulated into a shared counter gated on spine-ness, this would silently truncate:

```kotlin
val env = Osc.sine().adsr(0.01, 0.1, 1.0, Osc.param("rel", 0.8))
Osc.sine().lowpass(freq = Osc.constant(800).mul(env)).mul(env)
```

`Times` builds `left` first, reaches `env` inside the cutoff (a parameter position, so no
contribution), and caches it. The later spine reference hits the cache and never contributes.
Lifetime comes out 0.8 s short: the original bug in a new costume.

Carrying the tail **inside the cached value** closes this by construction: the off-spine build still
computes and stores the tail, the parent just discards it, and the later on-spine reference gets it
back with the cache hit. This is the reason the metadata rides on the returned structure rather than
on an accumulator, and the spec is the guard.

### Decided against (do not re-derive)

- **Inferring that "the ignitor owns amplitude."** Maintainer, 2026-08-27: *"I do not want cases where
  it is not obvious to the user, why the other (sprudel) envelope is used and why not. If there is
  noise without envelope then it is what it is. Engine stays raw, and when the user wants to express
  this they can."* So the six-row table above collapses: nothing is inferred, the ignitor tail feeds
  **lifetime only**, and the VCA on/off stays whatever the user wrote. This also removes the hazard
  that `Osc.sine().adsr(...).plus(Osc.whitenoise())` would defer the voice envelope to an envelope
  that covers only one summand, leaving the noise branch on a bare gate.
- **An `onSpine` flag threaded through the build.** Unnecessary once the tail is a return value:
  `withMod` / `noMod` is already the correct *local* rule at each parent. (It fails as a *propagated*
  flag, because it resets inside parameter subtrees, which is why the return-value shape matters.)
  Accepted rough edge: seven arms treat a parameter operand as signal for pitch-mod purposes
  (`Pow.exp`, `Div.right`, `Mod.right`, `Clamp.lo/hi`, `Range.lo/hi`, `Lerp.t`, `Select.cond`), so
  their tails are absorbed. That over-counts, which only over-allocates lifetime. Harmless.
- **The `0.3` fallback, and any "was it a guess" flag.** `null` means unknown, and unknown contributes
  nothing, so the voice's own release governs. Shorter than today's 0.3 for a modulated release, and
  deliberately so: `0.3` is a wrong number that looks like an answer, `null` is no claim. `Double?`
  carries the whole thing, so no second field.
- **A nullable or dummy `IgniteContext` for the build-time read.** A dummy with `voiceElapsedFrames`
  set to gate end would change nothing: `controlRateValueOrNull` is *structural*, not temporal, and
  never reads time. Zero overrides exist in `IgnitorEnvelopes.kt`, `IgnitorFilters.kt` or
  `Ignitors.kt`, so every oscillator, envelope, noise source and filter inherits the `null` default
  regardless of what a context says. Going further (calling `generate` against a synthetic context)
  cannot work either: state does not teleport. A phase accumulator handed
  `voiceElapsedFrames = 88200` with fresh internal phase still produces frame 0's phase, and even
  `AdsrIgnitor` would lie, because its release branch reads `releaseStartLevel`, which is only correct
  if it actually rendered the attack and decay.
- **A separate DSL-side spine walk.** Considered seriously (it keeps the exhaustive `when` with no
  `else`, i.e. the compile-time gate) and rejected in favour of the build, because putting the
  absorb/discard inside the two helpers recovers most of that gate at a fraction of the diff.

### Verified facts worth keeping

Checked while designing this; useful if the tail question is ever escalated to runtime.

- **`IgniteContext.voiceEndFrame` has zero readers.** It is written and never read.
- **`IgniteContext.releaseFrames` is read only by `releaseProgress` (`:80-85`), which itself has zero
  readers.** `AdsrIgnitor` computes its own local `releaseFrames` from its own node
  (`IgnitorEnvelopes.kt:90`); the strip envelopes read `Voice.Envelope.releaseFrames`.
  **Consequence: the ignitor graph never sees the predicted lifetime**, so a late-resolved or wrong
  prediction cannot corrupt anything it renders.
- **`Voice.endFrame` has exactly two consumers:** the lifecycle check at `Voice.kt:103` and the
  last-block clip at `:107`. `VoiceScheduler.kt:298` only acts on the returned Boolean. One further
  dependency exists outside teardown: `AccelerateRenderer(accelerate, startFrame, endFrame)`
  (`PitchPipelineBuilder.kt:39-40`), active only when sprudel `.accel()` is non-zero.
- Together those make **gate-end resolution feasible** as a later escalation for the one case a
  build-time read cannot answer (a release time that is itself modulated): make `endFrame` mutable and
  re-ask the release node during release, so teardown and `AdsrIgnitor` ask the same node the same
  way and cannot diverge. Sharp edges if it is ever picked up: a second reader of a stateful subtree
  must register via `MemoizingIgnitor.incConsumers()` or the inner generates twice (`:63`), and the
  voice's read happens before the ignite stage within the same block.

### Housekeeping to land at the END of this workstream

- **`Ignitor.octaveUp()` / `Ignitor.octaveDown()` (`Ignitor.kt:1324`, `:1327`) have zero callers and
  therefore zero tests.** Noticed by the maintainer during the Phase 0 review, 2026-08-27. They are
  thin wrappers over `detune(±12.0)`; the klangscript `octaveUp`/`octaveDown` at
  `KlangScriptOscExtensions.kt:386`/`:391` are a DIFFERENT pair that build `IgnitorDsl.Detune`, so
  they do not cover these. Add specs (do not delete them) before the workstream closes.

### ✅ PHASE 3 (decided 2026-08-27): explicit only, nothing inferred

Phases 0 and 1 shipped voice LIFETIME. Phase 3 is the other half: stopping the two envelopes from
compounding. **Decision: the VCA is switched off explicitly, never inferred.**

Maintainer, 2026-08-27, after re-opening and closing the question: *"engine stays raw is correct
then. So we go for explicit `.adsrOff()` -> `Vca(on = false)`."*

**What was reconsidered and rejected again.** The tempting rule is row 1 of the old table: *ignitor
has an envelope + `on == null` -> VCA off*, so the common case needs no ceremony. It was re-proposed
and dropped, because the engine has no signal that can answer "does the ignitor own amplitude", and
the one it does have cannot be repurposed:

- **`releaseTailSec != null` is not that signal.** It reports non-null for an FM modulator's envelope
  (deliberately, see the `Fm` arm and `IgnitorTailSpec`), for `Osc.sine().adsr(...).plus(noise)` where
  only one summand is enveloped, and for the seven parameter slots that are absorbed as signal
  (`Pow.exp`, `Div`/`Mod.right`, `Clamp`/`Range` bounds, `Lerp.t`, `Select.cond`).
- **The error direction inverts.** `releaseTailSec` is tuned to err LARGE on purpose, because
  over-counting only keeps a silent voice alive slightly too long. Wire the same number to an on/off
  switch and every over-count becomes a bare gate, which has 1 ms of declick against a 24 ms period
  at E1. That is an audible click regression, strictly worse than the compounding it would replace.
- A correct signal would need its own conservative test (an `Adsr` on the unbranched chain from the
  root, `else -> false` so it fails safe). Buildable, ~20 lines, and deliberately not built: it is
  inference, and inference is what the maintainer ruled out twice.

So: an ignitor with its own envelope still compounds until someone says otherwise. That is the raw
behaviour, and the author has one obvious lever.

#### Resolution order

One resolved value, three layers: **sprudel per-voice, then the pipeline's `Vca` stage, then `true`.**

| resolved `on` | what the VCA stage does |
|---|---|
| `true` (or nothing set anywhere) | renders the ADSR as today; an ignitor envelope compounds |
| `false` | renders a unity gate through the declick smoother; the ignitor owns amplitude |

Voice lifetime is unaffected either way: Phase 1 already covers the ignitor's tail.

#### Work items

**A. `AdsrDef.Std` gains `on: Boolean? = null`.** `mergeWith`: `on = on ?: other.on`. `resolve`:
`on = on ?: d.on ?: true`, and `AdsrDef.Resolved` gains a non-null `on: Boolean`.

> **It must be `Boolean? = null`, not `= true`.** A non-null default makes the inherit shape
> (`AdsrDef.empty`) carry an explicit `true`, so `on ?: other.on` could never reach a pipeline-level
> `Vca(on = false)` and the whole layer would be dead. There is a spec for exactly this below.

Wire: `kotlin.Boolean` is already in the codec's scalar set (`WireCodecProcessor.kt:66`) and is
pass-through, so this is a KSP regen and no codec work. It happens to be the first Boolean on the
wire, which is why none appears in the generated file today.

**B. Both doors get `.adsrOff()` / `.adsrOn()`** (see the dual-surface rule: every surface function
lands on the script stdlib AND the Kotlin extensions). Sprudel side: `SvdAdsr.on`, `mergeSvdAdsr`,
and the FOUR-door surface (pattern / string / standalone factory / chained mapper). They live in
`lang/addons/lang_dynamics_addons.kt`, not `lang_dynamics.kt`, and carry `addon` in `@tags`: neither
exists in Strudel, and `sprudel/ref/dsl-conventions.md` puts non-Strudel functions in `lang/addons/`.

**NOT** the mini-notation attribute map (`MnPatternToSprudelPattern.kt`) — an earlier draft of this
plan listed it. `loop`, the existing Boolean control, is not in that map either, so the precedent is
that `{adsr=…}` sets the numbers while the switch stays a method call.

**C. `StageDsl.Vca` gains `on: Boolean = true`**, alongside `expK` and `declickSeconds`. Non-null
here on purpose: `Vca` IS the fallback layer, so it has no "unset" state to express. This is a soft
default, not a structural switch: the structural switch already exists, since `PipelineDsl` is an
ordered list and a pipeline may simply omit the stage.

> **The built-in engines keep `on = true`.** Flipping `modern` / `pedal` would change how every
> existing song sounds. `Vca(on = false)` is there for engines built around self-enveloping ignitors.

**D. `on = false` renders a GATE, not a bypass, AND fades at teardown.**

> **⚠️ SUPERSEDED IN PART — read the callout below before the prose that follows it.** The original
> prose prescribed "feed a constant 1.0 through the same smoother". The shipped `renderGate` has NO
> smoother, deliberately: inside the fade window the target ramps 1.0 -> 0, so a one-pole would lag
> and leave a non-zero final sample, destroying the exact-zero endpoint. Restoring the smoother
> would bring the guitar click back. The prose is kept for the reasoning that led here, not as an
> instruction. Likewise "ending exactly at the voice's end frame" is wrong: it ends on
> `floor(endFrame) - 1`, the last frame `Voice.render` actually produces, and getting that wrong was
> the first bug the review found.

> **⚠️ FOUND BY EAR AFTER SHIPPING, 2026-08-27 — the fade is the load-bearing half.** The first
> implementation kept only the de-click smoother and clicked on every guitar note. The smoother was
> never the protection: it smooths the GAIN, and a constant gain has nothing to smooth. What
> actually guaranteed silence at teardown was that **the VCA sits LAST in the strip**, so with a
> curve it drove the fully amplified signal to zero before `Voice.render` dropped the voice.
>
> An ignitor's own envelope cannot replace that, because it sits **before** the instrument's amp.
> Measured on Der Schmetterling's guitar topology (`adsr → distort("tube") → highpass`):
>
> | topology | peak | last sample before teardown |
> |---|---|---|
> | envelope only | 0.995 | 0.00002 |
> | **envelope → amp** (the guitar) | 1.270 | **0.01510** |
> | amp → envelope (what the VCA did) | 1.267 | 0.00002 |
>
> The amp lifts the near-zero tail by ~20 dB and teardown steps it to zero in one sample. Fix:
> `VCA_OFF_TEARDOWN_FADE_SECONDS` (0.004), a linear ramp to zero ending exactly on `Voice.endFrame`.
> On the real guitar that halved max second-difference (0.0110 → 0.0057) and cut corners above
> 0.005 by 65% (77 → 27), with peak and RMS unchanged. Guard: `VcaOffTeardownSpec`, which is red
> without the fade and red at 0.5/1/2 ms. **This is a fade guard, not an envelope** — resist growing
> it, that invites the second ADSR back in through the safety door.

 `EnvelopeRenderer.kt:100-143` does three things per
sample: evaluate the curve, run the one-pole declick smoother, multiply into the buffer. Feed a
constant 1.0 through the same smoother and skip only the curve evaluation. That keeps nearly all the
CPU saving (the branch table is the expensive part) and all of the safety, because the declick is the
only thing rounding the note-off corner, and an envelope-less voice is exactly where a discontinuity
is guaranteed.

> **Do NOT touch `EnvelopeCalc.kt`.** An earlier draft of this plan said to mirror the change there.
> That is wrong: `calculateControlRateEnvelope` serves `FilterModRenderer` and `FmRenderer`, which
> pass their OWN `Voice.Envelope` instances. Switching the amp envelope off must not switch off the
> filter envelope. `EnvelopeRenderer` is the only VCA site.

**E. Specs.**
- merge reaches the pipeline: an all-null voice ADSR plus `Vca(on = false)` resolves to off. This is
  the guard for the `Boolean? = null` requirement above; make it red under `on: Boolean = true`.
- render: `on = false` gives unity gain (not zero, not a bypass) and still rounds the note-off corner.
- `.adsrOff()` keeps its numbers and `.adsrOn()` restores them. This is the property that chose a
  flag over an `AdsrDef.None` variant: in a live-coding language you flip it back for an A/B.

#### ⚠️ Expect a re-tune

Every ignitor with an internal ADSR was authored against the squared curve. Switching the VCA off
makes those envelopes longer and louder in their tails than they were tuned to be. Der Schmetterling's
guitar (`decay 1.5, sustain 0.05, release 0.013`) currently compensates for the bug and will not sound
the same afterwards. Land with a version note so the change is attributable.

### Still open (nothing blocking)

- **`AdsrDef`'s sealed hierarchy has exactly one implementor and always has.** Checked across all
  eleven revisions of the file: it was a plain `data class` until `77b3fdf0`, became
  `sealed interface { data class Std }` at `9c540a52`, and gained `@WireName("std")` at `b7b31bbf`.
  No second variant was ever written and removed. Cost: every ADSR on the wire carries a `"#t": "std"`
  discriminator with one possible value, plus dispatch on both sides. Kept anyway, because it is the
  wire convention (sealed `@WireName` hierarchies over enums) and because the AD / one-shot envelope
  that ignores the gate is a real candidate that `Std` values cannot express. Do not flatten it as a
  side effect of envelope work; if the tax is ever worth removing, that is its own decision.


## Measurement 1 — the curves compound (2× dB slope)

Two renders, identical envelope numbers `adsr(0.005, 0.001, 1.0, 0.300)`, differing only in where
the envelope is declared. Level after note-off, relative to the level at note-off:

| t after note-off | A: envelope in the **ignitor** | B: envelope in **sprudel** | A − B |
|---|---|---|---|
| 50 ms | −6.49 dB | −2.26 dB | −4.23 |
| 100 ms | −16.10 dB | −7.06 dB | −9.03 |
| 150 ms | −26.48 dB | −12.24 dB | −14.23 |
| 200 ms | −39.76 dB | −19.52 dB | −20.24 |
| 250 ms | −56.48 dB | −27.54 dB | −28.94 |

A ≈ **2 × B in dB** at every point — the signature of the amplitude curve being applied twice
(amplitude squared = dB doubled). Case A reaches −60 dB in 261 ms; case B in 304 ms.

Probes: `test-doubleenv-{ignitor,sprudel}.sprudel` (klang-ai session dir).

## Measurement 2 — `oscp` release overrides are invisible to voice lifetime

`IgnitorDsl.maxReleaseSec()` (`audio_bridge/IgnitorDsl.kt:1996`) extends voice lifetime to cover an
ignitor envelope, but its KDoc states the limit plainly: it reads **`Constant` values and `Param`
defaults only**. `VoiceFactory` consults `data.oscParams` for exactly one key — `"analog"`
(`:103`, `:366`). So a per-note override never reaches the lifetime calculation.

Three renders, all requesting a 1.5 s release:

| how the 1.5 s release is delivered | audible tail | level at 1.0 s |
|---|---|---|
| as the `Osc.param` **default** | 3.91 s ✅ | −30.7 dB |
| via **`.oscp("release", 1.5)`** | **0.56 s** ❌ | silent (−180 dB) |
| oscp override **+ a voice-level `.adsr(…, 1.5)`** | 3.90 s ✅ | −32.6 dB |

Any release that is not a literal or a bare `Param` (e.g. `pRelease.mul(2)`, a `Select`) hits the
hardcoded `else -> 0.3` fallback in the same function, so anything longer than 0.3 s is cut too.

Probes: `test-release-{default,oscp,voiceadsr}.sprudel`.

**Why it hid for ~50 song iterations:** the voice's own envelope fades the signal over its release
(default 0.05 s) rather than cutting it to zero, so the symptom reads as "too short / clipped /
cracky" rather than as an obvious truncation. The `0.3` fallback is silent — no warning, no log.

## Measurement 3 — related but separate: short release on a low note IS a click

Not an engine bug, a physics trap worth documenting alongside. A release shorter than a couple of
periods of the fundamental truncates the waveform mid-swing. At the song's `release = 0.013`:

| release | broadband splatter at note-off (click energy / note energy) |
|---|---|
| 13 ms (song value) | −69.7 dB |
| 60 ms | −85.1 dB |

15 dB more click energy, on a note (E1, 41 Hz = 24 ms period) whose release is shorter than **one
cycle**. Two independent blind reviewers flagged "clicking/knocking on the low rhythm-guitar
attacks" in the same round. **Rule of thumb: minimum release should scale with the note's period
(≈2–3 periods), not be a fixed millisecond count** — the same "express it relative to the note"
idea as the harmonic-relative string filter.

## Current behaviour, precisely

`VoiceFactory.kt:260-268` (osci branch):

```kotlin
val resolvedAdsr = data.adsr.resolve(AdsrDef.defaultSynth)   // default: a=0.01 d=0.1 s=1.0 r=0.05
val ignitorMaxRelease = ignitorDsl?.maxReleaseSec() ?: 0.0   // static estimate only
val effectiveAdsr = if (ignitorMaxRelease > resolvedAdsr.release)
    resolvedAdsr.copy(release = ignitorMaxRelease) else resolvedAdsr
```

and `:537`: `endFrame = gateEndFrame + resolvedAdsr.release * sampleRate`.

So the voice always has an amp envelope; the ignitor's *static* max release is copied into the
voice envelope's release (that is why case A above has the right duration but the squared shape),
and voice lifetime follows from it.

## Open design questions

> **ALL ANSWERED** by [THE PLAN](#-the-plan-agreed-2026-08-27) (2026-08-27). In short: (1) it does
> not, and must not infer it; (2) not applicable, the voice envelope is not switched off
> automatically; (3) static, with gate-end resolution recorded as a feasible escalation;
> (4) yes, always, because the user wrote it; (5) the silent `0.3` is deleted rather than made
> loud, since `null` makes no claim at all. Kept because the arguments still constrain the
> follow-up pass on VCA on/off.

### 1. How does the engine know the ignitor has an **amp** envelope?

The crux. `maxReleaseSec() > 0` detects *any* `Adsr` node — but in Der Schmetterling `.adsr()` is
also used for **modulation**: `Osc.freq().mul(pStrFloor.plus(pStrSweep.adsr(...)))` is a filter
sweep, not an amplitude envelope. If "has an Adsr node" switched the voice envelope off, an ignitor
with only a *filter* envelope would get a raw unity gate → hard on/off → worse clicks than today.

Candidate answers:
- **Structural rule** — an `Adsr` whose output multiplies the signal path is an amp envelope; one
  that feeds a parameter subtree (cutoff, gain argument) is modulation. Statically determinable,
  but subtle and easy to get wrong (`.mul(env)` is also amplitude).
- **Explicit marker** — the ignitor declares it: `Osc.amp(...)` / `.ampEnvelope(...)`. Unambiguous,
  self-documenting, gives the lifetime calculation an exact node to read. Costs new vocabulary and
  a migration.
- **Sprudel opt-out only** — `.adsr("none")` / `.gate()`, leaving detection out of it entirely.
  Smallest change, zero risk to existing songs, but does not deliver "no ADSR in sprudel at all".

### 2. What replaces the voice envelope when it is switched off?

Not "nothing" — a bare gate clicks at both ends. The voice still needs a **1–2 ms declick ramp at
teardown** as unconditional insurance, precisely because the ignitor's envelope may not have
reached zero (short release, sustain > 0, modulated release, voice stealing).

### 3. Static prediction vs. runtime "am I finished?"

The truncation bug exists *because* lifetime is predicted statically from the DSL tree. An
alternative: let the ignitor's envelope report completion per block, and end the voice when it
says so, with the static estimate kept only as a safety cap. That is exact under any modulation,
and it also lets voices die **earlier** than predicted (a CPU win that partly pays for the change).
Cost: a per-block query and a clear definition of "finished" for trees with several envelopes.

A cheaper intermediate step, if a full runtime query is too much: a `maxReleaseSec(oscParams)`
overload so per-note overrides at least reach the static estimate — `oscp` values are known at
build time (`IgnitorDslRuntime.kt:101` resolves them into `ParamIgnitor`).

### 4. Should an explicit sprudel `.adsr()` still apply on top?

Suggested split, which satisfies the maintainer's requirement without removing a legitimate
technique: **the DEFAULT voice envelope is what must not compound** — replace it with a unity gate
plus declick when the ignitor owns amplitude. An **explicitly written** sprudel `.adsr()` keeps
applying, because the author asked for it (a pad ignitor with its own swell, shortened per note, is
a real use case). Layering then becomes a choice rather than an ambush.

### 5. Make the silent fallback loud

The hardcoded `else -> 0.3` guess is what let this hide. Whatever the fix, a non-statically-
resolvable release should produce a warning (intellisense diagnostic or console) rather than a
plausible wrong number — the same failure mode as `^`-as-XOR in `klangscript-caret-as-power.md`.

## Design conversation, 2026-08-27 — where it landed

Not decided, but the reasoning converged. Recorded so it is not re-derived.

### Three states, expressed as a flag rather than a sealed variant

`AdsrDef` needs to express: *inherit everything*, *these values*, *no envelope*. The maintainer's
proposal — a nullable `on` flag on `Std` rather than an `AdsrDef.None` variant — is the better fit:

- **A flag is toggleable; a variant is destructive.** `.adsr(0.005, 1.0, 1.0, 0.05).adsrOff()` keeps
  the values while disabling the stage, so it can be flipped back for comparison. `None` throws the
  numbers away. In a live-coding language that is the deciding property.
- Merge semantics stay uniform (`on ?: other.on`); no variant-precedence rules to invent.
- Cost: `.adsr(a = 0.5, on = false)` is representable nonsense. Mild — the values lie dormant, the
  same way `decay` already does when `sustain = 1.0`.

**It must be `on: Boolean? = null`, not `= true`.** A non-null default makes the inherit shape carry
an explicit `true`, so `on ?: other.on` could never reach a pipeline-level `Vca(on = false)` — which
would defeat configurable defaults entirely.

Naming for the inherit shape: **`AdsrDef.inherit`** (`unset` acceptable). Avoid `default` — once the
defaults are configurable it becomes ambiguous ("the default values" vs "whatever default is in
scope"), and `inherit` reads correctly with partials: `Std(attack = 0.005)` is *inherit everything
except attack*.

### `Vca(on = false)` is a soft default, not a structural switch

`PipelineDsl` is an ordered **list** of stages and its KDoc says users "may omit stages", so the
structural switch already exists: leave `Vca` out of the list and there is no VCA stage. Therefore
`Vca(on = false)` should mean *voices in this pipeline default to no amp envelope*, overridable
per-voice with `.adsrOn()` — the same status as `expK` / `declickSeconds`, which are also stage
defaults. Two distinct mechanisms, both already available.

### "Off" must render a gate with declick, NOT a bypass

`EnvelopeRenderer.kt:100-143` does three things per sample: evaluate the curve, run the one-pole
declick smoother, multiply into the buffer. Gain and velocity are applied elsewhere, so skipping it
is clean in that respect — **but the declick lives inside that loop**, and it is the only thing
rounding the note-off corner (`AdsrCurveMath.kt:51-59` documents that this corner "radiates a
broadband click, most audible on low notes"). Bypassing the stage deletes the protection exactly
where an envelope-less voice is guaranteed to have a discontinuity.

So `on = false` should feed a constant 1.0 through the same smoother and skip the curve evaluation:
nearly all of the CPU saving (the branch table is the expensive part), all of the safety.

### ✅ CURRENT DIRECTION (maintainer, 2026-08-27): `getVcaAdsr()` — a spine-only structural rule

> **SUPERSEDED by [THE PLAN](#-the-plan-agreed-2026-08-27) (2026-08-27).** The spine idea survives
> and drives the tail; the amp-vs-modulation *detection* and the six-row deferral table do not.
> Nothing is inferred any more: the ignitor tail feeds lifetime only, and the VCA on/off stays
> whatever the user wrote. Kept for the case analysis, which is still the clearest statement of
> what the alternatives were.

Supersedes the observation proposal below, which was rejected as too complicated and constant-heavy.
The rule: **an `Adsr` reached by walking only signal-carrying edges is the VCA (amplitude) envelope;
an `Adsr` inside a parameter subtree is modulation.**

```javascript
Osc.sine().adsr(...)                    // VCA adsr — the envelope multiplies the signal
Osc.sine().lowpass(cutoff = x.adsr(...)) // NOT a VCA adsr — it modulates a cutoff
```

Why this beats both alternatives:

- **The distinction becomes definitional, not heuristic.** On the signal path, an `Adsr` multiplies
  the signal — that *is* amplitude, by construction. No inference, no amp-vs-mod guessing.
- **Zero new constants** (the objection that killed the observation design).
- **It resolves the case that broke observation**: when the ignitor has no VCA adsr, that is known
  *statically*, so the note simply ends at gate end + fade guard. No follower, no decay test, no cap,
  no absurd ringing.
- Everything stays deterministic and cheap: one small tree walk at voice build, no per-sample work.

The full case table. The voice-side axis has **three** states (the point of the `on: Boolean?` +
`inherit` model), so there are six cases, not four:

| voice-side ADSR (sprudel → pipeline) | ignitor VCA adsr | what shapes amplitude | lifetime |
|---|---|---|---|
| **not defined** (`inherit`) | **yes** | ignitor only — the voice defers | gate end + **ignitor's** release |
| **not defined** (`inherit`) | none | voice envelope, from the pipeline defaults | gate end + pipeline default release |
| **defined** (explicit values) | **yes** | both — compounds, the author asked for it | gate end + max(voice, ignitor) release |
| **defined** (explicit values) | none | voice envelope as written | gate end + voice release |
| **off** (`on = false`) | **yes** | ignitor only | gate end + **ignitor's** release |
| **off** (`on = false`) | none | nothing — a bare gate | gate end + fade guard |

Read the voice-side column as one resolved value: sprudel wins over pipeline, pipeline over the hard
fallback. "Not defined" means every field is null all the way down, i.e. `AdsrDef.inherit`.

**The key line is row 1** — *not defined* + *ignitor has a VCA adsr* → **the voice defers entirely**.
An ignitor that declares a VCA adsr has effectively declared "I own amplitude", so the default
envelope must step aside rather than compound. That makes the common case need no ceremony at all:
build an instrument with its own envelope, play it, no compounding, nothing to remember.

Note that rows 1 and 5 are **behaviourally identical**. That is a good property, not a redundancy: it
means the sensible thing happens by default, and `.adsrOff()` only changes behaviour in row 6 — where
it means "not even the pipeline default; give me a bare gate."

Row 3 is the deliberate-layering escape hatch: an explicitly written sprudel `.adsr()` still applies
on top of an ignitor envelope, because the author wrote it on purpose (a pad ignitor with its own
swell, shortened per note). Compounding becomes a choice instead of an ambush.

**⚠️ Row 6 has a gap: "no VCA adsr" ≠ "no tail".** Physical-model generators decay on their own
without any `Adsr` node — `IgnitorDsl.Pluck` (`:643`) and `SuperPluck` carry their own `decay`
parameter, and `maxReleaseSec` returns `0.0` for both today. So `.adsrOff()` on a pluck would cut the
string at note-off, and even under the current code a pluck's natural decay is truncated by the
voice's release. The fix is a small generalisation, and it adds no constants because the number
already exists in the node: make the walk return **the voice's tail length**, from either a spine
`Adsr` *or* a self-decaying generator's own decay parameter. Worth naming the function accordingly
(`getVoiceTail()` / `getAmpEnvelope()`) rather than binding it to "adsr".

### Why there are two implementations at all — and which half is the accident

Asked by the maintainer, 2026-08-27. The answer splits, and only one half is drift.

**Two WALKS are justified.** The DSL tree and the runtime graph are different kinds of object:

| | `IgnitorDsl` tree | runtime `Ignitor` graph |
|---|---|---|
| purpose | describe a patch | produce samples |
| shape | sealed hierarchy, `childNodes()`, exhaustive `when`, no `else` | objects with `generate(buffer, freqHz, ctx)` |
| inspectable | **yes, by design** | **no — no child enumeration exists** |

`Ignitor` (`audio_be/ignitor/Ignitor.kt:36`) exposes `generate`, `controlRateValueOrNull`,
`isBlockConstant` — nothing structural. So the built graph *cannot* be asked "how long is your tail"
without adding a new capability to every component. Analysis therefore belongs on the DSL side, and
"just query the built graph instead" does not work.

**Two RESOLUTION RULES are not justified.** *What number does this node yield for this note?* is one
question, answered twice and differently (`IgnitorDslRuntime.kt:101` vs `IgnitorDsl.kt:2047`). That
is drift, not architecture — and the runtime side already has the concept named and generalised:
`controlRateValueOrNull(freqHz, ctx)` returns the block-constant scalar or `null` if it varies,
folding through `Constant` / `Param` / `Freq` and pure pointwise combinators. That is exactly the
maintainer's rule ("ask the node its value; if it is neither constant nor param, ignore it") — already
written, on the wrong side of the wire for static use.

**Scope check: `maxReleaseSec()` has exactly ONE production caller** — `VoiceFactory.kt:264`.
Everything else is the definition plus two spec files. One call site, one function, one new helper.

### ✅ BEST OPTION (maintainer, 2026-08-27): don't query at all — accumulate during the build

> **REFINED by [THE PLAN](#-the-plan-agreed-2026-08-27) (2026-08-27).** The route is confirmed, but
> not as an accumulator: the tail is a **return value** carried in a `BuiltIgnitor` structure, which
> is what closes the build-cache sharing hole an accumulator would have. Contribution happens in the
> `withMod` / `noMod` helpers, not at the component factory. Read THE PLAN for the shape.

The maintainer's push — *"the query has to be done on the built graph + params context, not on the DSL
definition"* — is right, and the code offers something better than a query.

**The build already distinguishes signal path from parameter position.** Every node's build arm reads:

```kotlin
is IgnitorDsl.Adsr    -> inner.withMod().adsr(attackSec.noMod(), …, releaseSec.noMod(), …)
is IgnitorDsl.Lowpass -> inner.withMod().lowpass(freq.noMod(), q.noMod(), analog = analog.noMod())
```

`withMod()` = signal path (pitch mod propagates along it); `noMod()` = parameter
(`IgnitorDslRuntime.kt:196-197`). That is exactly the spine-vs-parameter classification this design
needs, already written into every node for an unrelated but structurally identical reason. It does not
have to be invented or maintained.

So the tail need not be *queried* — it can be **accumulated as the graph is built**. Thread one
accumulator through `buildIgnitor(oscParams, cache, accumulatedMod)` (which already threads context);
each tail-bearing component contributes its already-resolved value at construction:

- **the runtime/analysis disagreement cannot exist by construction** — values are resolved at the point
  of contribution, so the whole `oscp` question disappears instead of being fixed;
- the signal-path distinction is free (`withMod` / `noMod`);
- **zero extra traversal** — it rides the build that already runs per voice;
- no child enumeration to add to `Ignitor`, no per-class query overrides, no second exhaustive `when`;
- contribute at the **component factory** (`.adsr(...)`, `.pluck(...)`), not in the DSL arm, so any
  Adsr built by any path contributes automatically and a new DSL node reusing the component cannot
  forget.

**DECIDED (maintainer, 2026-08-27): take this route.** `maxReleaseSec()` is deleted, not fixed.
Rationale: ADSR is *the* thing that shapes note duration and release tail, and no other effect does
the same job. If a second one ever arrives, both can implement a `CarriesReleaseTail`-style interface
then — a fool-me-twice situation, so nothing speculative is built today.

**Two free accuracy wins from building instead of analysing:**

- **`Variants` builds only the selected child** (`cache.soundIndex.mod(children.size)`,
  `IgnitorDslRuntime.kt:116-118`), where `maxReleaseSec` takes the max across *all* children. The
  accumulator therefore reports the tail of the variant this voice actually uses — variant instruments
  stop over-allocating lifetime.
- **Diamond dedup is harmless**: a shared subtree builds once and contributes once, and max is
  idempotent, so contribution needs no de-duplication of its own.

**Cleanup this removes:** `IgnitorDsl.kt:2043-2105` (~60 lines of `when` arms over all 78 node types),
plus `IgnitorDslSpec.kt:135-147` and `IgnitorDslOptimizerSpec.kt:283-296`. That last test —
*"maxReleaseSec is unchanged by optimization"* — exists **only** because one question is answered on
two structures that can drift; under the accumulator it needs no equivalent, because the drift becomes
unrepresentable.

**⚠️ `Pluck` / `SuperPluck` carry a tail today — FOLLOWUP, own task:
[`pluck-release-tail.md`](pluck-release-tail.md).** Decided 2026-08-27 after checking what `decay`
actually is; summary below, full treatment in that file.

`decayDefault = ConstantIgnitor(0.996)` (`Ignitors.kt:59`) is **not a duration**. It is applied as
`delayLine[writePos] = filtered * decayVal` — once per delay-line pass, i.e. **once per period of the
note**. So the ring time is frequency-dependent:

  t60 = ln(0.001) / (f · ln(decay))   ≈ **1723 / f** seconds at the default 0.996

| note | frequency | ring to −60 dB |
|---|---|---|
| E2 | 82 Hz | **21.0 s** |
| A4 | 440 Hz | 3.9 s |
| C6 | 1046 Hz | 1.6 s |

A `tailSeconds()` interface would therefore need, for pluck alone: a coefficient→duration conversion,
a threshold policy (T60? T80? relative to what level?), the note frequency threaded into the query,
and a **cap policy** — 21-second voices at E2 would wreck polyphony. Four decisions, not an interface.
Hence: followup.

**Reframe:** today's voice envelope is not merely masking the pluck gap, it is **load-bearing** — it is
the only thing preventing 21-second pluck voices. Which means `.adsrOff()` on a pluck cutting at
note-off is not obviously wrong: the alternative is effectively unbounded ring, so the palm-mute
reading may be the correct default rather than a compromise. Decide it deliberately when the followup
is picked up; note that `.adsrOff()` on a pluck is the first thing someone reaching for physical
modelling will try.

**The one real cost — losing a compile-time gate.** `maxReleaseSec` is a single exhaustive `when` with
no `else` **on purpose**: adding a node type breaks the file and forces a decision. Accumulation has no
such gate — a *new component type that has a tail* which forgets to contribute silently reports 0.0 and
truncates, the same silent-failure family as this entire bug. Mitigation: the tail-bearing set is tiny
(`Adsr`, `Pluck`, `SuperPluck` today), so a spec that renders each and asserts the voice outlives its
tail catches a forgotten contribution immediately. Note the codebase already lives with this pattern —
`controlRateValueOrNull` defaults to `null`, `isBlockConstant` to `false`, with a KDoc warning that a
wrong override misbehaves "with no spec failing loudly".

**Ordering change required:** `VoiceFactory` currently calls `maxReleaseSec()` at `:264` *before*
`createExciter` at `:272`. Flip it — build the exciter, read the accumulated tail, then compute
`effectiveAdsr` (`:265-269`). About ten lines in one function; `voiceEndFrame` has no other readers.

If this route is taken, the DSL-side `constValueOrNull` helper below becomes unnecessary, and
`maxReleaseSec()` can be deleted along with its single call site.

### Fallback if the build-accumulator route is not taken: put resolution on the node

> **NOT TAKEN.** The build route was taken, and `controlRateValueOrNull` (with its context
> parameter removed, Phase 0) is the single resolution rule, so no DSL-side `constValue` twin is
> written. Kept because the diagnosis in the first paragraph is exactly right and is the reason the
> chosen route works.

The structural statement of the bug: **exactly two places turn a `Param` node into a number, and they
disagree.**

```kotlin
// audio_be — IgnitorDslRuntime.kt:101   (the renderer: consults the override)
is IgnitorDsl.Param -> return ParamIgnitor(name, oscParams?.get(name) ?: default)

// audio_bridge — IgnitorDsl.kt:2047     (the analysis: does not)
is IgnitorDsl.Param -> releaseSec.default
```

It is not that one site forgot a lookup; it is that one question — *what is this node's value for this
note?* — is answered in two places by two rules. Move the answer onto the node and pass the context in,
and they agree by construction. The context type is already uniform: `Map<String, Double>?` at
`VoiceData.oscParams:34` and `buildIgnitor(oscParams:97)`.

```kotlin
// audio_bridge, beside the node definitions — the one place that knows how a node yields a number
fun IgnitorDsl.constValue(params: Map<String, Double>?): Double? = when (this) {
    is IgnitorDsl.Constant -> value
    is IgnitorDsl.Param    -> params?.get(name) ?: default
    else                   -> null          // not statically knowable
}
```

Both existing sites then delegate instead of reimplementing. Beyond fixing the truncation this
(a) **names the `0.3`** — today an invisible magic number in an inline `else` arm, afterwards a named
constant and the obvious place to hang a diagnostic; (b) gives constant folding a single growth point
(`IgnitorDslOptimizer` already folds some constants, `:273`), so `pRelease.mul(2)` could resolve later
with no caller changes; (c) supplies the leaf resolver for any spine walk.

Module placement is forced and convenient: it must live in `audio_bridge` (audio_be depends on it, not
the reverse), which is where `IgnitorDsl` and `maxReleaseSec` already are.

**Walk substrate already exists:** `IgnitorDslWalk.kt` provides `childNodes()` / `withChildNodes()`,
exhaustive over all 78 node types with **no `else` arm on purpose** (so a new node type breaks the file
rather than silently skipping). Note it returns *all* children including parameter positions
(`Lowpass -> listOf(inner, freq, q, analog)`), so a spine-only walk wants a sibling
`signalChildNodes()` there, under the same discipline.

**Simplification (maintainer, 2026-08-27): keep `maxReleaseSec()` as it is.** Do not build a new
walk. *(NOT TAKEN: `maxReleaseSec()` is deleted. See [THE PLAN](#-the-plan-agreed-2026-08-27).
The two watch-outs listed below still hold and are handled there.)*

When `.adsrOff()` is in effect, use its value as the voice's tail: lifetime = gate end +
`maxReleaseSec()`; the VCA stage passes the signal through unshaped.

Two things to watch with that shortcut:

- **It returns `0.0` when the tree has no `Adsr` — including `Pluck` / `SuperPluck`**, which decay on
  their own via their `decay` parameter (`IgnitorDsl.kt:643`). So `.adsrOff()` on a pluck yields
  lifetime = gate end + 0, cutting the string at note-off. The fade guard is therefore not optional
  in row 6, and `Pluck.decay` eventually wants counting alongside the `Adsr` releases.
- **Estimate errors are asymmetric.** Over-estimating the tail is harmless (a voice lives slightly too
  long; under `.adsrOff()` it is usually silent anyway). Under-estimating truncates audibly.
  Everything imprecise about the current walk errs *large* — it descends into parameter positions on
  `Clamp` / `Range` / `Lerp` / `Select`, over-counting. Exactly two things err *small*: an `oscp`
  override larger than the `Param` default, and an expression whose real value exceeds the hardcoded
  `0.3` fallback. Those two are the only ones that can cut a tail.

**Details — maintainer decisions, 2026-08-27:**

1. **`oscp` resolution — REOPENED 2026-08-27.** The objection that closed it was *"then we would have
   to hardcode the name `release` — what if an ignitor names it differently?"*, which came from a
   sloppy shorthand in the discussion (`oscParams["release"]`). **The name is never hardcoded; it is
   read off the node.** `Adsr.releaseSec` holds an `IgnitorDsl` that is a `Constant`, a
   `Param(name, default)`, or an expression:

   ```kotlin
   val release = when (val r = adsrNode.releaseSec) {
       is Constant -> r.value
       is Param    -> oscParams?.get(r.name) ?: r.default   // r.name — whatever the author called it
       else        -> /* not statically resolvable */
   }
   ```

   An instrument naming it `"tail"` or `"damping"` works identically. `maxReleaseSec` already reads
   `releaseSec.default` off this same node, so this is one added lookup and no new vocabulary.
   Recommendation: do it — it is the difference between `oscp` release overrides working and silently
   truncating.

   If it is nevertheless left out, the accepted consequence to document and warn about:
   *the VCA envelope's release must be a literal or a `Param` default; `oscp` may shape
   attack/decay/sustain but not release.* Only **release** feeds lifetime, so this is one field, not
   four. The failure it accepts: `pRelease = Osc.param("release", 0.013)` plus
   `.oscp("release", 0.4)` renders a 400 ms tail through `ParamIgnitor` while lifetime is computed
   from 0.013 → the tail is cut 13 ms after gate end. This is the original truncation bug in a
   smaller costume, and it becomes *more* audible under the new model, because with the voice
   envelope deferring there is no 50 ms default fade left to disguise it. If it is ever revisited,
   the fix is one lookup at voice-build time (`oscParams["release"] ?: default`) on one field of one
   node — no traversal. An unheeded `oscp` override is exactly the silent-wrong-number class that
   `klangscript-caret-as-power.md` exists to eliminate, so a diagnostic is the cheap middle ground.
2. **Several `Adsr` nodes on the spine — REVISED 2026-08-27: find them all, take the max.**
   Supersedes the earlier "outermost / last in the chain wins", which breaks the `Plus` case: an
   outermost envelope with release 0.1 would truncate a branch envelope with release 0.5.
   `Variants` → max across children (as today).

   ⚠️ **Rank by RELEASE, not by total length (attack + release).** Attack happens *inside* the gate:
   an ADSR starts releasing at note-off wherever it was in its attack, so the envelope reaches zero
   at `gate_end + release` regardless of attack. Ranking by total mis-picks:

   | | attack | release | tail needed after gate | attack+release |
   |---|---|---|---|---|
   | Env A | 2.0 | 0.1 | **0.1 s** | 2.1 |
   | Env B | 0.001 | 0.5 | **0.5 s** | 0.501 |

   Total picks A (needs 0.1 s) and truncates B (needs 0.5 s). Including attack can only over-allocate
   (harmless CPU) or mis-rank (a real bug) — never help. **The one exception, if it is ever added:** a
   one-shot / trigger envelope that completes regardless of note-off (AD envelope, drum-synth
   behaviour) *would* need `attack + decay` in the metric, because it ignores the gate. Klang has no
   such mode today; design the function with room for it.

   **Consequence — the function need not return an envelope at all.** Two values answer everything:
   - `hasVcaEnvelope: Boolean` — any volume-shaping envelope on the spine → drives the row-1 deferral;
   - `maxTailSec: Double` — max release across volume-shaping envelopes, **plus** self-decaying
     generators (`Pluck.decay`) → drives lifetime.

   So `vcaTail()` / `ampEnvelopeInfo()` is a more honest name than `getLongestVcaAdsr()`: there is no
   single "longest ADSR", there is an ownership question and a duration question, and only the second
   is a max.
3. **Lifetime is gate end + release — CONFIRMED.** Not attack+decay+sustain+release: the gate decides
   when release starts; a note shorter than attack+decay simply releases from a lower level.
4. **Scope of the walk — DECIDED: no full tree walk.** Follow only the signal spine from the root
   inward; never descend into parameter subtrees. Note the existing `maxReleaseSec` is *already*
   spine-only for filters (`is Lowpass -> inner`, ignoring `cutoffHz`/`q`), which is why the song's
   string-sweep envelope is already excluded from lifetime today. The nodes that currently leak into
   parameter positions and must be tightened: `Clamp -> maxOf(inner, lo, hi)`,
   `Range -> maxOf(inner, lo, hi)`, `Lerp -> maxOf(left, right, t)`,
   `Select -> maxOf(cond, whenTrue, whenFalse)`.

**Known residual gap:** an amplitude envelope written in a *parameter* position (e.g.
`.gain(pGain.adsr(...))`) is missed by a spine walk. Unusual spelling; the natural one is `.adsr()`
on the signal. Document the rule rather than build machinery for it.

### REJECTED — drop static lifetime prediction entirely, observe instead

> Rejected 2026-08-27 (too complicated, introduces new constants: no-decay window, decay margin,
> absolute cap). Kept because the *reasons* it was considered still constrain the design — in
> particular the "nothing owns the tail" hole, which the `getVcaAdsr()` rule closes statically.

Two checks establish that nothing depends on the prediction:

1. The ignitor's `Adsr` node uses `ctx.gateEndFrame` — *when the note releases*, known exactly — and
   computes its release from its **own** `releaseSecVal` (`IgnitorEnvelopes.kt:86-91`). It is
   self-contained; it never asks when the voice ends.
2. **`voiceEndFrame` has no consumers** outside `IgniteContext.kt` and `VoiceFactory.kt`.

So the prediction exists solely to answer *when may this voice be torn down?* — and that is better
answered by measuring the voice's own output after gate end (peak follower + threshold) than by
analysing the DSL tree. Doing so removes every failure recorded above at once: no amp-vs-modulation
distinction needed, `oscp` and expression and LFO releases all just work, the silent `0.3` fallback
disappears, and voices can die **earlier** than a static estimate would allow (a CPU win that partly
funds honouring long releases). This is also how hardware and software synths decide "voice done".

Two details that matter: (a) do not test an instantaneous sample — E1 crosses zero every 12 ms, so a
naive check kills voices mid-cycle; use a peak-hold/follower with a window longer than the lowest
musical period (≥50 ms covers 20 Hz). (b) Threshold relative to the voice's own peak (≈ −80 dB), not
absolute, so quiet voices are not cut early.

### The safety layer is a *fade guard* plus a *cap*, not a default envelope

The catastrophic case is already covered — the house limiter (post-sum, 5 ms lookahead) caps peaks
and the distortion stages carry DC blockers, so `.adsrOff()` cannot push a damaging signal through.
The real residual risk is clicks. Resist the framing "the VCA needs a security envelope": that
invites a second ADSR back in through the safety door. Two separate mechanisms instead:

| | ADSR | fade guard |
|---|---|---|
| purpose | musical shaping | continuity at boundaries |
| controlled by | the author, per note | the engine, always on |
| duration | ms to seconds | ~1–2 periods of the note |
| audible as shaping | yes | no |

The declick smoother already *is* the fade guard — it is simply fixed at `ENV_DECLICK_SECONDS =
0.001`, which cannot round a corner inside a 24 ms cycle (E1). Scaling it with the note's period is
the same insight as scaling minimum release with the period.

Then the one thing observation cannot handle on its own: a voice that never falls silent
(`.adsrOff()` on a plain sine, a self-oscillating filter). That needs a **maximum tail after gate
end, then a forced fade** — configurable on the `Vca` stage. A cap never shapes anything; it only
ends things that refuse to end.

### Attack curve — the finding that explains "soft onsets"

`AdsrCurve.Default = Exponential` applies to **all three stages**, with `ADSR_EXP_K = 3.0`. For decay
and release that is right (fast drop, long tail). For **attack it is convex — slow start**:

| progress through attack | level | compounded (today's double envelope) |
|---|---|---|
| 25 % | 0.059 (−24.7 dB) | 0.003 (−49.3 dB) |
| 50 % | 0.182 (−14.8 dB) | 0.033 (−29.6 dB) |
| 75 % | 0.445 (−7.0 dB) | 0.198 (−14.1 dB) |

Half-way through the attack the voice is 15 dB down; with compounding, 30 dB. The onset starts late
and arrives as a ramp — which is "onsets sound soft / weak / hollow", the most persistent complaint
across ~50 review rounds, and why attack-time knob turning never did what the author expected.
Suggested: default `attackCurve` to a **concave** shape (`InvSquare`, `p(2−p)`) while decay and
release stay `Exponential` — i.e. `AdsrCurve.Default` becomes per-stage rather than one value.

Note also that `decay` is a **no-op while `sustain = 1.0`** (the stage ramps peak→sustain, which are
equal); it only surfaces when sustain alone is overridden through a partial merge.

## ⚠️ Migration warning — this changes how existing songs sound

Every ignitor with an internal ADSR has been authored **against the squared curve**. Removing the
compounding makes those envelopes *longer and louder* in their tails than the author tuned them to
be. Der Schmetterling's guitar (`decay 1.5, sustain 0.05, release 0.013`) will not sound the same
afterwards, and its settings currently compensate for the bug. Budget a re-tune, and consider
landing the fix together with a version note so the change is attributable.

## Files

- Probes (klang-ai, `sessions/20260810-gemini-review/`): `test-release-default`,
  `test-release-oscp`, `test-release-voiceadsr`, `test-release-lownote`,
  `test-release-lownote-long`, `test-doubleenv-ignitor`, `test-doubleenv-sprudel` (+ `.wav`).
- Code: `audio_be/voices/VoiceFactory.kt:260-268`, `:537`;
  `audio_bridge/IgnitorDsl.kt:1996` (`maxReleaseSec`); `audio_bridge/AdsrDef.kt:93`
  (`defaultSynth`); `audio_be/voices/strip/EnvelopeCalc.kt`; `audio_be/ignitor/IgniteContext.kt`
  (`gateEndFrame` is voice-RELATIVE `Int`).
