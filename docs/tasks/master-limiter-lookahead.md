# Master limiter: lookahead — fixing the transient "knock"

> **Planned 2026-08-04, not started.** Fixes audit finding
> [F16](../audio-audit/FINDINGS.md#f16) — the user-reported "knock". Priority: **SHOULD**, and it is
> a *sound* fix, so it outranks the rest of the audit backlog ("sound first").
>
> **Five decisions are taken** (user, 2026-08-04) and the plan is built on them:
> 1. **Lookahead is exposed on the master limiter ONLY** — not on sprudel's `compressor()`, not
>    per-orbit, not per-voice. Reasoning in §3.
> 2. **When lookahead > 0 the gain is built by moving-minimum → smoothing → delay-by-the-sum**
>    (Luff/Signalsmith), replacing the one-pole attack. This supersedes the earlier "smoothstep ramp"
>    idea, which review showed to be under-specified — two reasonable readings gave opposite results.
>    Reasoning and measurements in §2.3; **credit obligation in §7b**.
> 3. **The final safety limiter stays where it is — on the summed mix in `MasterStage` — and
     > `MasterDsl.default` stays empty.** Reasoning in §4. This is the decision that makes the whole
>    change small and safe.
> 4. **Units are seconds**, uniform with the rest of the engine (§4b).
> 5. **Two knobs — `.lookahead()` and `.attack()`** — rather than deriving the split, because a
>    derived split would make `attack` a silent no-op on the master limiter (Phase 3).
>
> **Reviewed 2026-08-04** by a fresh coding reviewer and a fresh DSP reviewer (`/review-loop` round
> 1); both sets of findings are folded in. **A round 2 is due before implementation** — this plan
> changed substantially in response, and changed plans are unreviewed plans.

## 1. The defect, in one paragraph

`effects/Compressor.kt` is feed-forward with no lookahead and no delay line, so the detector sees a sample at the same
moment the signal does. `MasterStage.process` runs limiter → DC blocker → **hard clip at ±1.0**
(`MasterStage.kt:78-116`; the clip loop itself is `:92-115`). Measured against the real `Compressor` with
`MasterStage`'s own constants, a 55 Hz kick-like transient:

| Kick peak in | Peak out        | Hard-clipped for |
|--------------|-----------------|------------------|
| 0 dBFS       | −0.33 dBFS      | 0 ms             |
| +6 dBFS      | **+5.67 dBFS**  | **3.99 ms**      |
| +12 dBFS     | **+11.67 dBFS** | **5.22 ms**      |
| +18 dBFS     | **+17.67 dBFS** | **5.90 ms**      |

At t = 1 ms into a +18 dB transient the gain is still exactly **0.00 dB**. The ceiling is enforced by the clip, not the
limiter. Every loud transient is a 2–6 ms hard-clipped burst, and the window grows with the amount of limiting — which
is exactly the reported *"when it has to limit a lot"*.

Already ruled out by measurement, so nobody re-investigates: envelope ripple (≤ 1 dB pk-pk even at 40 Hz), cold-envelope
startup (a warm envelope still overshoots to +8.43 dBFS), and the
`ENV_COEFF_BLEND_DB` crackle fix (worth 0.14 dB).

## 2. Why this is not a one-parameter change

**Lookahead alone does not fix it.** Measured, +12 dB kick, current 1 ms one-pole attack:

| lookahead | delay only  | + running-max detector |
|-----------|-------------|------------------------|
| 0 (today) | +11.67 dBFS | —                      |
| 1.5 ms    | +11.67      | +10.16 — still clips   |
| 3.0 ms    | +4.86       | +1.57 — still clips    |
| 5.0 ms    | +3.64       | 0.00 — still clips     |

A one-pole with τ = 1 ms needs several τ to settle, so it cannot reach full reduction inside any sane lookahead window.
Three things must change together:

1. **A delay line** on the signal (the lookahead itself).
2. **A running-maximum detector** over the lookahead window, so the gain starts moving *before* the peak reaches the
   delayed signal. Delay alone is strictly worse — see the table.
3. **An attack that fits inside the window.**

With all three:

| lookahead | attack | +12 dB kick out   |
|-----------|--------|-------------------|
| 1.0 ms    | 0.2 ms | **−0.31 dBFS** ✓ |
| 1.5 ms    | 0.2 ms | **−0.35 dBFS** ✓ |
| 1.5 ms    | 0.5 ms | +0.40 ✗          |
| 1.5 ms    | 1.0 ms | +10.16 ✗         |

⚠️ **Everything in §2 up to here is the FIRST pass, kept only as the evidence that lookahead alone is insufficient.**
Its "smoothstep ramp" proposal and its numbers are **superseded by §2.1 and §2.3** — read those for the actual design.
`attackSeconds` does become the smoothing length, but as part of the min-hold construction, not as a standalone ramp.

`lookaheadSeconds = 0` keeps the existing one-pole path byte-for-byte, so per-orbit compressors and existing behaviour
are untouched.

### 2.1 What the prototype found — three corrections to the above

A working prototype of the proposed design was stressed before any code was written. It found three things, and they
change the numbers.

**(a) The ceiling is NOT guaranteed, and that is arithmetic, not a ramp bug.**

| kick in | out                    |
|---------|------------------------|
| +12 dB  | −0.37 dBFS ✓          |
| +18 dB  | −0.07 dBFS ✓          |
| +24 dB  | **+0.23 dBFS — clips** |

At ratio 20:1 the residual above threshold is `overshoot / ratio`. At +24 dB in that is 25/20 = 1.25 dB over a −1 dB
threshold = +0.25 dBFS predicted, +0.23 measured. **Our "brickwall limiter" is a 20:1 compressor**, so it will always
exceed the ceiling under enough drive. → **DECIDED — see §2.4**, which quantifies the bound and records the resolution
(accept it; the fix holds to ≈ +20 dB over threshold, the hard clip remains the last resort).

**(b) `attackSeconds ≤ lookahead` is the WRONG clamp — it must be strictly less.** At 1.5 ms lookahead:

| ramp                     | result                |
|--------------------------|-----------------------|
| 1.0 ms                   | −0.37 dBFS clean      |
| **1.5 ms (= lookahead)** | **+2.20 dBFS, clips** |
| 2.0 ms (> lookahead)     | +5.18 dBFS, clips     |

A ramp that finishes exactly as the peak arrives is already too late. Measured safe region was roughly
`ramp ≤ ⅔ × lookahead` — **superseded by §2.3**. With the min-hold construction the constraint becomes `M ≤ D + 1`, and
`attack == lookahead` is valid rather than fatal.

**(c) A 0.2 ms ramp is too aggressive — the gain becomes an audio-rate signal.** Max gain slew on a +12 dB kick: **0.2
ms ramp → 43 dB/ms**, 1.0 ms ramp → 19 dB/ms, both clean. Trading the knock for gain-modulation distortion would be a
poor bargain.

**Settled, so they need not be re-litigated:**

- *Dense transients do not cause a gain plateau.* At 8/16/32 hits per second the gain recovers to unity every time — the
  running-max window does not hold it down.
- ⚠️ *Smoothstep vs linear* — **my "identical" claim was wrong, and how it was wrong matters.** It held only for my
  restart policy (restart the ramp only when the target drops *below* the current ramp target) at ramp < window. The DSP
  reviewer, restarting on *every* new target, measured smoothstep at **+4.74 / +8.79 / +9.85 dBFS** for ramp 0.5 / 1.0 /
  1.5 ms where linear held −0.35 — because smoothstep has zero derivative at t=0, so a per-sample restart never
  advances. **Neither of us is wrong: the ramp design was under-specified enough that two reasonable readings give
  opposite answers.** That is the case for §2.3's construction, which has no such freedom.

### 2.3 The correct formulation — moving-minimum, then smoothing

The prototype's remaining hole (two peaks 0.2–0.5 ms apart clipped, because a larger target arriving mid-ramp restarted
the ramp) is a solved problem. The construction is from Geraint Luff's *"Designing a straightforward limiter"*
(Signalsmith Audio, 2022 — §8; **credit obligation in §7b**):

1. compute the per-sample **required gain** `g_req[n]` from the undelayed signal through the existing knee curve;
2. take a **moving MINIMUM** of `g_req` over a window of `L` samples — this is what makes the gain dip *before* the
   peak, and it handles any number of peaks in the window with no special case;
3. **release stage**: `r[j] = min(held[j], releaseUp(r[j-1]))` — instant down, one-pole up;
4. **smooth** with two cascaded box filters (C¹ by construction);
5. **delay the signal by `D`.**

⚠️ **The release goes BEFORE the smoother, not after it.** The obvious arrangement —
`min(smoothed, onePoleGain)` — is C⁰ at the crossover: the box path leaves its minimum with slope 0, accelerates to ~
2Δ/B, then crosses the release path (~0.12 dB/ms), and the gain slope drops ~40× **in one sample**. That is structurally
the same corner we reject a single box for, so it would spend the two-box cascade on C¹ and then break C¹ one stage
later. Putting release before the box keeps the whole trajectory C¹, and safety still holds: `r ≤ held` pointwise, so
`box(r) ≤ box(held) ≤ g_req`. It also removes the ambiguity about *what* the one-pole operates on — there is one `min`
per sample against one state variable.

#### ⚠️ The budget arithmetic — hold and smoothing OVERLAP, they do not add

An earlier revision of this plan said `lookahead = movingMin(A) + smoothing(B)`. **That is wrong, and it clips.** The
correct relation, derived rather than tuned:

> We need `smoothed[n] ≤ g_req[n+D]` for the sample emerging from the ring. An average is **≥** its
> minimum, so *every* term entering it must already be `≤ g_req[n+D]`. With
> `held[n+k] = min(g_req[n+k … n+k+L-1])`, that requires `n+D ∈ [n+k, n+k+L-1]` for all
> `k ∈ [0, M-1]`, giving:
>
> ```
>     L ≥ D + 1        and        M ≤ D + 1
> ```
>
> **State the smoothing bound in TAPS, not milliseconds**, or ceil/odd rounding drifts by a sample.
> Two cascaded boxes of `b1`, `b2` taps have convolution support `b1 + b2 − 1` and group delay
> `(b1 + b2 − 2)/2`, so the exact implementable condition is:
>
> ```
>     b1 + b2 − 2 ≤ D
> ```
>
> `A` is **not an independent quantity** — delete it from the spec. What earlier drafts called `A` is
> only the flat-bottom margin `D + 1 − M`.

**The min-hold window must span the WHOLE delay, not the leftover after smoothing.** They overlap.

Measured, house settings, +12 dB kick:

| construction                                  | peak out               |
|-----------------------------------------------|------------------------|
| `L = D − M + 1` (the wrong "A + B" partition) | **+0.14 dBFS — clips** |
| `L = D + 1` (correct)                         | **−0.37 dBFS clean**   |

And the correctness now shows up as **invariance**, which is the property a correct construction must have and the wrong
one cannot fake — same +12 dB kick, 5 ms window, varying the smoothing:

| smoothing `M` | wrong partition | **correct** |
|---------------|-----------------|-------------|
| 20% of window | −0.27           | **−0.37**   |
| 50%           | +0.14 ✗        | **−0.37**   |
| 80%           | +1.10 ✗        | **−0.37**   |
| 100%          | +2.74 ✗        | **−0.37**   |

**This invariance is the headline test** (Phase 4 item 3): with a correct construction the ceiling result must not
depend on the smoothing length at all. It is the single assertion that catches this entire class of error, and it would
have caught the bug above before a line was written.

#### What this means for the two knobs

Simpler than the partition model, and the constraint relaxes:

- **`lookahead` = `D`** — the delay, the whole added latency, and the min-hold window (`L = D + 1`).
- **`attack` = `M`** — the smoothing length, constrained only by `M ≤ D + 1`.

So `attack == lookahead` is **valid and is the maximum-smoothing setting**, not an error. This **supersedes §2.1 (b)'s "
clamp must be strictly less than"** — that was an artefact of the ramp construction. Clamp `M` to `≤ D + 1`, floor at ≥
2 samples.

Because peak is invariant to `M` but LF cleanliness tracks it directly (§2.5), **more smoothing is strictly better for
the same latency** — which makes the default choice easy.

⚠️ **And the "flinch" is a knob.** The flat bottom of the gain dip is `D + 1 − M` — the time the gain sits at *full
depth before the transient arrives*. At `M = D/2` that is 2.5 ms of the mix fully ducked ahead of the kick, which is the
audible inhale people describe as "the mix flinches". At
`M ≈ D` the flat bottom collapses to ~1 sample and the pre-duck becomes a smooth ramp reaching full depth exactly as the
transient lands. A 50/50 split sets this knob to its **worst** value; its only benefit was off-by-one margin, and a few
taps is margin enough.

Note on provenance: the article ships C++ snippets whose licence is not stated. **Implement from the described method,
not by transcribing the code.** Credit is due either way; see §7b.

### 2.4 What still does NOT hold, even with the correct formulation

**The ceiling is bounded by `ratio`, not by the lookahead design.** At 20:1 the residual above threshold is
`overshoot / ratio` — a +24 dB input still exits ≈ +0.23 dBFS regardless of how good the anticipation is. This is
arithmetic, and no amount of lookahead fixes it.

Quantified against the real `calculateGainReduction` with a *perfect* detector (infinite lookahead, no time constants),
the best achievable output is:

| input over threshold | best achievable out |
|----------------------|---------------------|
| +6 dB                | −0.70 dBFS          |
| +12 dB               | −0.40 dBFS          |
| +18 dB               | −0.10 dBFS          |
| **+20 dB**           | **0.00 dBFS**       |
| +30 dB               | +0.50 dBFS — clips  |

**So the whole fix holds only up to ≈ +20 dB over threshold.** Past that the knock returns unchanged, however good the
anticipation is. With N playbacks summing into one `MasterStage` and a deliberately raw engine, +20 dB over is
reachable.

→ **DECIDED (user, 2026-08-04): accept the residual.** The hard clip stays as a genuine last resort, and 20:1 stays the
house ratio. +20 dB over threshold is a lot of drive, and the engine is deliberately raw — if you push that hard, you
get the rail. **The bound must go in the KDoc and in
`LimiterLookaheadSpec`**, so the limit is stated rather than discovered. Reversible later: the alternative was to raise
`LIMITER_RATIO` toward ∞ **for the safety limiter only** (the authored `MasterFx.limiter()`
keeps 20:1 as its musical character). Note `ratio` stays user-facing and unclamped — raw Motör — so
"∞:1" would mean the house default is ∞, not that the user may not choose 20. **The bound belongs in the KDoc and in
`LimiterLookaheadSpec`.** (The rejected alternative would also have created a *third* `MasterDefaultsSyncSpec`
asymmetry — another reason it is not worth it.)

### 2.5 Defaults

|                                    | Value                                                                                    | Why                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
|------------------------------------|------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **`LIMITER_LOOKAHEAD_SECONDS`**    | **0.005 (5 ms)** — *defensive default: sound good out of the box, let the user optimise* | With the §2.3 construction the smoothing spans the whole window, so LF quality tracks window length directly. Measured on 55 Hz at +12 dB over: 1.5 ms → −42.6 dB THD-ish, 3 ms → −48.0, **5 ms → −57.4**, with peak essentially unchanged (−0.40 → −0.37). On a path whose hardware latency is already 10–30 ms, 5 ms costs nothing musically and buys ~15 dB of LF cleanliness. The complaint is a **55 Hz** knock — one cycle is 18 ms, so 1.5 ms is only 8% of a cycle. |
| **house smoothing `M` (`attack`)** | **0.005 (5 ms) = the full window**                                                       | §2.3: peak is *invariant* to `M`, while LF cleanliness tracks it directly — so at fixed latency more smoothing is strictly better. The partition model's "half and half" was an artefact of the wrong arithmetic. ⚠️ **Confirm by ear in Phase 4** — maximum smoothing is optimal for LF, but may soften transients more than we want.                                                                                                                                      |

**Why 5 ms and not 2 ms (user, 2026-08-04): a defensive default.** Measured LF quality on 55 Hz at +12 dB over the
ceiling: 1.5 ms → −42.6 dB THD-ish, 3 ms → −48.0, **5 ms → −57.4**, with peak essentially unchanged. The complaint is a
55 Hz knock — one cycle is 18 ms, so 1.5 ms is 8% of a cycle and 5 ms is 28%. On a path whose hardware latency is
already 10–30 ms, 5 ms costs nothing musically and buys ~15 dB. Default to the good-sounding end; the knobs are there
for anyone who wants the latency back.

⚠️ **Consequence: `LIMITER_ATTACK_SECONDS` now diverges from the authored default too.** The house limiter needs
`B = 2.5 ms` to deliver the 5 ms window's benefit, but the *authored* `MasterFx.limiter()`
has `lookahead = 0`, so its `attackSeconds` is still the **one-pole attack** and must stay at **0.001** — raising it to
2.5 ms would make every authored limiter grab transients far more slowly. So `MasterDefaultsSyncSpec` now documents a
deliberate asymmetry on **two** constants (`lookaheadSeconds` and `attackSeconds`), not one. Same reasoning as §Phase 3:
the house limiter is global and post-sum; the authored one is per-playback.

Memory footprint at 5 ms / 48 kHz: 240 frames × 2 ch × 8 B ≈ **3.8 KB**. Negligible.

Both are **by-ear gates, not settled numbers** — Phase 4 tunes them.

## 3. Scope decision: master only

The initial request was to reflect `lookaheadSeconds` across sprudel's `compressor()` compound string, the param tool,
and a new DSL function. **That is not being done, deliberately.**

A lookahead limiter delays the signal it protects. On the master that is harmless — everything shifts together. On a
**per-orbit** compressor (which is where sprudel's `compressor()` actually applies —
`Cylinder.kt:58`, one `KatalystCompressorEffect` per orbit) it would shift *that orbit* late against every other orbit.
`s("bd*4").compressor("…:1.5")` would put the kick 1.5 ms behind the snare. That is a silent timing bug that reads as
"my drums feel loose", and it is the kind of thing that is very hard to diagnose after the fact.

**Consequence to accept knowingly:** this breaks the standing parameter-parity rule ([
`feedback_parameter_parity`](master-dsl-followups.md#1-parameter-parity-audit--the-principle-applied-everywhere))
— `lookahead` will exist on the master limiter and nowhere else. That is a justified exception, **and it must be
documented as one** in the parity audit, or the next person will "fix" it.

Therefore **untouched**: `sprudel/lang/lang_dynamics.kt` (all 8 `compressor`/`comp` forms),
`Compressor.parseSettings`'s 5-slot and 2-slot arms, `SprudelCompressorEditorTool`, and the
`@param-sub` docs. Nothing in sprudel changes.

## 4. Where the final limiter lives — RESOLVED

**The final safety limiter stays on the summed mix in `MasterStage`. `MasterDsl.default` stays empty.** This was
reconsidered and settled 2026-08-04; the reasoning is worth keeping because the first instinct (move it into
`Master.default`) is wrong for three separate reasons.

### Why the sum is the right place

`MasterStage` is **exactly one instance**, applied once to the summed mix of every playback
(`PlaybackEngineDispatcher.kt:34` holds it, `:133` calls it after the engine loop at `:129-131`).
`MasterBus` — the authored `master(…)` chain — is **one per `PlaybackEngine`**
(`PlaybackEngine.kt:24,129-133`). So a limiter in `MasterDsl.default` would be N limiters on N sub-mixes with **nothing
protecting their sum**: N playbacks each limited to −1 dB sum to as much as N × 0.89, and two playbacks authoring
different lookaheads would drift against each other.

### Why the lookahead delay is free here, and only here

This is the point that resolves the original "I want to remove it because it adds a time-shift"
concern. `MasterStage` is the last stage before interleave — everything after it is the DAC. A delay applied there
shifts **the entire output uniformly**, so nothing can desync against anything, because there is nothing left to desync
from. The same delay applied per-orbit (§3) or per-playback would desync. **The final sum is the one place a lookahead
delay costs nothing musically.**

The only real cost is absolute output latency: +5 ms on a path whose hardware latency is already 10–30 ms. See Phase 5
for what still has to account for it.

### Why keeping `MasterDsl.default` empty matters

`PlaybackEngine.renderInto` skips the master bus entirely when the bus is inert:

```kotlin
// PlaybackEngine.kt:62
if (!masterBus.isActive) {
    // Fast path — byte-identical to the pre-MasterDsl engine.
    cylinders.processAndMix(target); return
}
```

`isActive` is `current.isActive || previous != null`, and `MasterBus` seeds `current` from
`MasterDsl.default` at construction (`MasterBus.kt:89-96` — note this is a **backend-side seed at engine creation**;
sprudel never sends the default). A non-empty default would make `isActive` true for every engine forever, permanently
killing that fast path: every playback would allocate its own
`StereoBuffer`, run a full `MasterChain`, and add a per-sample summing loop. Keeping it empty preserves both the fast
path and the byte-identical guarantee.

The existing KDoc at `MasterDsl.kt:27-31` — "**This MUST stay empty**" — therefore **stands unchanged**. Its stated
reasoning ("seeding a limiter here would put two limiters in series") is still exactly right.

### The resulting two-limiter model

|                            | Where                | Scope                  | On by default | Lookahead                       |
|----------------------------|----------------------|------------------------|---------------|---------------------------------|
| **Final safety limiter**   | `MasterStage`        | the summed mix, global | **always**    | **yes** — this is the knock fix |
| **Musical master limiter** | `MasterFx.limiter()` | per playback, authored | no            | yes, for parity                 |

An authored master limiter sits *upstream* of the final one, so the two compose without desync. Two limiters in series
is the normal mastering arrangement (musical, then safety) and is fine as long as the musical one is doing the work.

### The DC blocker — the sub-question raised alongside this

Today there are **six DC-blocker objects at five sites** (`MasterStage` holds a stereo pair); the master pair is the
only unconditional one:

| Site                                | Signal                             | Coefficient   | Conditional                |
|-------------------------------------|------------------------------------|---------------|----------------------------|
| `MasterStage.kt:61-62`              | master out, post-limiter, pre-clip | 0.999 (~7 Hz) | **unconditional**          |
| `DistortionRenderer.kt:47`          | voice-strip distortion             | 0.995         | only when `amount > 0`     |
| `IgnitorEffects.kt:73` (`distort`)  | per-voice                          | 0.995         | only on the driven path    |
| `IgnitorEffects.kt:211` (`clip`)    | per-voice                          | 0.995         | whenever `.clip()` is used |
| `IgnitorEffects.kt:728` (`dcBlock`) | per-voice, explicit                | user          | whenever used              |

**Recommendation: keep the master pair unconditional in `MasterStage`, and move it BEFORE the limiter.** DC removal is a
correctness measure rather than a musical choice, it costs two one-poles, and it adds **zero latency**. Putting it ahead
of the limiter is also more correct — DC eats headroom asymmetrically and inflates what the detector sees.

⚠️ **My earlier justification for the reorder was inverted.** With DC *before* the limiter the blockers receive the
**raw, unlimited** mix — so `MasterStage.kt:58`'s claim that they get ±1-bounded input becomes *more* false, not true.
What becomes true is that the **clip** finally receives limiter output. The replacement comment must say that, or it
will be written wrong again.

**The real reason to reorder is that the DC blocker is not level-safe.** It is a 7.6 Hz high-pass whose pole transient
overshoots on onsets: measured, a limited (−1 dBFS) 55 Hz sine switched on at t=0 comes **out at −0.47 dBFS — a +0.53 dB
gain** — decaying over ~21 ms. With the fix leaving only
~0.35 dB of margin, that is enough to re-engage the clip on already-limited material. End-to-end on the +12 dB kick with
the fix in place:

| order                       | peak out       |
|-----------------------------|----------------|
| limiter → DC (today)        | −0.21 dBFS     |
| **DC → limiter (proposed)** | **−0.36 dBFS** |

Checked the other direction too: DC-first does *not* hand the limiter a peak it cannot catch — a +8 dBFS burst through
the blocker exits at +8 dBFS with a −0.83 dB undershoot tail, well inside reach. **So the reorder is a required part of
the fix, not a tidy-up.**

## 4b. Where the constant lives

**Surveyed: there is no unified constants file in `audio_be`, and mostly that is correct.** Five distinct patterns are
in use, and the split is principled rather than accidental:

| Pattern                                                              | Files                                                                                                                                 | What lives there                        |
|----------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------|
| **Dedicated subsystem tuning file**, file-level `internal const val` | `filters/FilterHumanizationCoeffs.kt` (5), `ignitor/AnalogDriftCoeffs.kt` (9), `ignitor/OscillatorTuning.kt` (36)                     | by-ear tunables for one subsystem       |
| **Math file + its constants**                                        | `AdsrCurveMath.kt` (2), `DspUtil.kt` (2)                                                                                              | shared curve/DSP math                   |
| **Public companion on the owning class**                             | `MasterStage` (`LIMITER_*`), `Reverb.AUTHORED_ROOM_SIZE_SCALE`, `PhaserCore.DEFAULT_STAGES`/`MAX_FEEDBACK`, `Cylinders.MAX_CYLINDERS` | values other modules or specs must read |
| **Private companion**                                                | `Compressor`, `Reverb` internals, `DelayLine`, `Ducking`                                                                              | implementation details, not tunables    |
| **Registry defaults**                                                | `PipelineRegistry.DEFAULT_PIPELINE`, `IgnitorRegistry.DEFAULT_SOUND`                                                                  | name resolution                         |

**Decision: `LIMITER_LOOKAHEAD_*` goes in `MasterStage`'s companion** (`MasterStage.kt:27-46`), beside the five existing
`LIMITER_*` constants. That companion's KDoc already declares itself "the house limiter character… also the defaults of
the opt-in `MasterStageDsl.Limiter` stage — kept in sync by `MasterDefaultsSyncSpec`", and that spec really does assert
all five against the wire defaults. Adding to them follows a precedent that already works — but note this is **not
purely additive**: `LIMITER_ATTACK_SECONDS` also changes meaning (§Phase 3).

**Do NOT unify the tuning files.** Subsystem grouping is the useful axis — you tune analog drift by opening
`AnalogDriftCoeffs.kt`. A single 60-constant file would be worse.

**But there IS a real constants problem, and it is [F1](../audio-audit/FINDINGS.md#f1), not fragmentation.** Five engine
constants are duplicated as bare literals in
`audio_bridge/PipelineDsl.kt:98,99,100,106,107`, each with a comment naming its twin, and **nothing guards them** —
three of the five are read by no production code at all, so tuning the documented constant does nothing. The master pair
is the *only* one guarded, by `MasterDefaultsSyncSpec`.

→ **The correct fix is to extend the `*DefaultsSyncSpec` family to cover `PipelineDsl`**, exactly as the master already
does. That closes F1 and F2 and costs one spec. Tracked in the audit list; not part of this change, but this is the plan
that identified the shape of the fix.

### Units — RESOLVED: seconds

**Decision (user, 2026-08-04): stay uniform, use seconds.** `lookaheadSeconds`, and
`LIMITER_LOOKAHEAD_SECONDS = 0.005`.

The engine is uniformly seconds — `LIMITER_ATTACK_SECONDS`, `LIMITER_RELEASE_SECONDS`,
`ENV_DECLICK_SECONDS`, `MIN_DELAY_SECONDS`, `ANALOG_FAST_TAU_SEC`, `ANALOG_SLOW_TAU_SEC` — and there is no
millisecond-valued constant anywhere in `audio_be`. `lookaheadMs` would have been the only one, sitting directly beside
`attackSeconds` and `releaseSeconds` in the same data class. Consistency wins over the (real) mastering-tool convention
of quoting lookahead in ms; the docs can say "2 ms" while the API says `0.005`.

## 5. Implementation phases

Each phase ends green and committable. ⚠️ **Shipped-song sound changes at Phase 2, not Phase 4** —
`MasterStage` is on the summed mix, so every song is affected the moment its limiter changes. Only Phases 0 and 1 are
sound-neutral. Phase 4 is the by-ear *gate*, not the first point of change.

### Phase 0 — capture the reference vector FIRST (sound-neutral)

**Before touching `Compressor`.**

1. **Capture the byte-identical reference.** Render fixed inputs through the *current* `Compressor`
   and check the output in. ⚠️ **`audio_be` has no test resources directory** — its source sets are
   `commonMain / commonTest / jsMain / jvmMain / wasmJsMain`, and common tests cannot read files (the repo's only test
   resources are under `sprudel/src/jvmTest/resources`). So the vector goes in as a **generated Kotlin source constant
   in `commonTest`**, not a resource file. Decide the shape here; do not discover it on day one. Capture **two**
   configurations: the house limiter settings *and* an orbit-shaped one (`Compressor.parseSettings` defaults: 4:1 / 6
   dB / 3 ms / 100 ms), because the path this guard actually protects is the **per-orbit** compressor.
2. **Write `LimiterLookaheadSpec` and watch it fail.**
   ⚠️ It must compile **today**, so write it against the *current* API — no `lookaheadSeconds`
   argument, which does not exist until Phase 1. Assert the real invariant ("no sample exceeds 1.0 linear" on the
   `Compressor` output, drive ≤ +18 dB over threshold per §2.4) and watch it go red for the right reason. Phase 1 adds
   the parameter and turns it green.

### Phase 1 — DSP: lookahead in `Compressor` (no behaviour change at default)

`effects/Compressor.kt`:

- New param `lookaheadSeconds: Double = 0.0`. **`0.0` keeps the current code path exactly** — same one-pole, no delay
  line, no allocation. Per-orbit compressors keep using it.
- When `> 0`: implement §2.3 — **`D = lookaheadFrames`; min-hold window `L = D + 1`; smoothing
  `M ≤ D + 1` via two cascaded boxes of `M/2`; signal delayed by `D`.** The hold and the smoothing **overlap** — do NOT
  partition the window between them (§2.3 shows the partition version clips).
- **The `attack ≤ lookahead` clamp must live in the ramp/smoothing computation, guarded on
  `lookaheadFrames > 0` — NOT in the `attackSeconds` setter.** `attackSeconds` is a live `var`
  (`Compressor.kt:74-79`) that `Cylinder.kt:196` writes on every settings change; clamping in the setter would clamp
  every per-orbit compressor against `lookahead = 0` and silently destroy all orbit compression.
- **The moving-min/smoothing ring must be primitive-backed** — `DoubleArray` + `IntArray` with plain
  `Int` head/tail. `ArrayDeque<Double>` boxes on Kotlin/JS (house rule: no boxed types in audio paths).
- **`reset()` must clear the rings**, not just `envelopeDb`. Three reset paths reach it:
  `MasterStage.reset()` (`:68-72`, already delegates — no `MasterStage` change needed),
  `KlangAudioRenderer.resetPostChain()` (`:44-48`), and `MasterChain.reset()` → `limiters[i].reset()`
  (`master/MasterChain.kt:92-94`), which `MasterBus.beginFade` (`:245-247`) calls **on the audio thread** for every
  re-adopted chain.
- **Release behaviour must be specified, not left to the implementer — the two readings differ by 28 dB.** Measured on a
  sustained 55 Hz sine 12 dB over the ceiling, non-fundamental energy relative to the fundamental:

  | reading | THD-ish |
    |---|---|
  | lookahead governs the **downward** direction only; one-pole 100 ms release upward | **−42.5 dB** ✓ |
  | lookahead governs every target change (release at window speed) | **−14.2 dB** ✗ |
  | today (no lookahead → hard clip) | −32.4 dB |

  The wrong reading is **17 dB worse than the hard clip it replaces**, on exactly the material that generated the
  complaint. **Specify: the lookahead path controls downward only; upward is the existing one-pole release; combine with
  `min()`.**
- **Use TWO cascaded box filters of `B/2`** rather than one of `B`. That is C¹ by construction, so the
  `ENV_COEFF_BLEND_DB` C¹ blend (which exists to kill the 2026-04-30 crackle, `Compressor.kt:232-236`)
  is not needed on this path — and cannot be reused anyway, since it interpolates two one-pole *coefficients* and a box
  has none. A single box leaves a C⁰ corner at every completion: measured slope jump of 1.235 dB/sample in one sample,
  structurally the same defect the blend was added for.
- **The mono `process(buffer, offset, length)` overload** (`:123`) must behave consistently — it has no production
  caller today, but `CompressorSmoothnessSpec.kt:55,74` drives it with limiter constants, so the same configured object
  would otherwise behave differently through two doors.
- ⚠️ **The min-hold window must be `D + 1` samples, not `D`.** With window == delay, the peak's own sample has already
  left the window when it emerges from the ring. Measured with two spikes and a fast release: `W = D` → **+0.89 dBFS (
  clips)**, `W = D + 1` → −0.35 dBFS. Harmless today only because release is slow — it becomes a real leak the moment
  anyone speeds release up.
- ⚠️ **Treat a sub-minimum `D` as zero.** Two cascaded boxes need `b1, b2 ≥ 1`, so `M ≥ 2` and therefore `D ≥ 2`; below
  that the smoother degenerates to a step. **Take the zero-lookahead path for `D < MIN_LOOKAHEAD_FRAMES = 8`** (≈0.17 ms
  at 48 kHz) — a documented number, not a vibe.
- ⚠️ **`lookaheadSeconds` must be a constructor `val`**, unlike every other `Compressor` param (all
  `var` for live change). State it, or someone will make it a `var` and resize the ring inside
  `process()` — on the audio thread.
- ⚠️ **`attackSeconds` is a public `var` whose setter calls `updateCoefficients()`
  (`Compressor.kt:74-79`) — and on the lookahead path it now sizes the smoothing boxes.** The rule:
  **allocate all rings once for `lookaheadFrames` at construction; the setter recomputes only the integer tap split,
  clamped to `b1 + b2 − 2 ≤ D`.** Never resize on the audio thread. Without this rule a future live-param path silently
  desynchronises the smoothing from the delay and voids the §2.3 alignment guarantee, with no test to catch it.
- **Document that `attackSeconds` does double duty on the lookahead path** — it sets the smoothing taps *and* still
  drives `attackCoeff` via `updateCoefficients()`. Harmless (the `min()` never raises the gain) but a future "attack
  tuning" change would otherwise move two unrelated things.
- ⚠️ **Stereo: one ring per channel, one shared write index, one shared detector reading the UNDELAYED input.** The
  natural refactor — "delay first, then run the existing `process()`" — nullifies the lookahead completely **and still
  passes a naive peak test on slow material**. This is the cheapest possible bug to introduce here.
- ⚠️ **Put the `lookahead > 0` branch OUTSIDE the per-sample loop** (two loop bodies in `process()`), never inside
  `envelopeStep`. That function is `inline`d into both overloads specifically to keep each loop specialised
  (`Compressor.kt:132-137`); a per-sample branch risks deoptimising the zero-lookahead path that the Phase 0
  byte-identical test exists to protect.
- ⚠️ **NaN guard at the detector input and on ring write**, with the house `// NaN-guard` comment.
  `max(NaN, x)` propagates NaN in Kotlin, poisoning the monotonic deque's ordering (all comparisons false) and sitting
  in the ring for `D` samples. Note `MasterStage.kt:96-110` maps NaN to
  `Short.MIN_VALUE` — full-scale negative, i.e. a loud click — because NaN fails both `>= -1.0 && <= 1.0`
  and `> 1.0`.
- Denormals: the ring stores *signal*, not IIR state — no accumulation, so no `flushDenormal`. Adding it would be pure
  cost. Keep the gain accumulator additive in dB. Nothing here touches `Reverb`'s
  `ANTI_DENORMAL` exception.
- Hot-path rules: no allocation in `process()`, rings sized once at construction. The monotonic deque is amortised O (1)
  for *any* input (each sample pushed once, popped once) — do not over-engineer it. Benchmark before/after with
  `console/run-dsp-benchmarks.sh`.

### Phase 2 — `MasterStage`: wire the lookahead in, fix the ordering

⚠️ **This is where shipped-song sound changes** — every song, because `MasterStage` is on the summed mix. The claim "no
phase changes shipped-song sound until Phase 4" was wrong; only Phase 0/1 are sound-neutral.

- `MasterStage` gains `LIMITER_LOOKAHEAD_SECONDS = 0.005` (§4b) and passes it to its `Compressor`. The house smoothing
  follows §2.5. Note `LIMITER_ATTACK_SECONDS` now means the **one-pole attack for the `lookahead = 0` path** and the
  **smoothing length** when lookahead > 0 — Phase 3 records why the house and authored values deliberately differ.
- Reorder to **DC blockers → limiter → clip**, and rewrite the now-stale comment at
  `MasterStage.kt:57-60`.
- ⚠️ **There are TWO `MasterStage` instances**, not one: `PlaybackEngineDispatcher.kt:34` (realtime)
  and **`KlangAudioRenderer.kt:27`** (offline renderer + `runSongBenchmark` + `KlangBenchmark`). Both are affected. The
  offline one is never warmed up, so its ring is cold at frame 0.
- ⚠️ **`MasterStageSpec.kt:31-41` will go RED.** It writes an impulse at `mix.left[0]` with
  `blockFrames = 64` and asserts `out[0] != 0`; a 2 ms delay is 88 frames, so the impulse never reaches that block. The
  spec must be updated in this phase or the tree is left red.
- `MasterDsl.default` and the `PlaybackEngine` fast path are **untouched**.

### Phase 3 — Wire + DSL surface (parameter parity, master side)

⚠️ **`MasterStageDsl.Limiter.lookaheadSeconds` defaults to `0.0`, NOT to the house 0.005.**
The authored master limiter is **per playback** (`MasterBus`, one per `PlaybackEngine`), and multiple simultaneous
playbacks are first-class (`index_common.kt:28` `play()` / `:50` `playOnce()`, and
`KlangPlayer.kt:65` holds a list). A non-zero default would delay any song authoring a master limiter against every
other playback — `DerSchmetterling.kt:76-77` chains **two** limiters, so 2×. That is precisely the desync §3 rejected
for orbits, one level up and shipped by default. **Parity by availability, not by default.**

| Layer              | File                                                                                                  | Change                                                                                                                                                                                                         |
|--------------------|-------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Wire model         | `audio_bridge/MasterDsl.kt:79-86`                                                                     | `Limiter` gains `lookaheadSeconds: Double = 0.0`; schema hash auto-shifts (`WireCodecProcessor.kt:101`)                                                                                                        |
| Builder            | `MasterChain.kt:191-198`                                                                              | pass through **via `finite(stage.lookaheadSeconds, 0.0)`** and bound it — see below                                                                                                                            |
| Visibility         | `MasterChain.kt:48`                                                                                   | `private val limiters` → **`internal val`**, matching `reverbs`/`delays` at `:46-47`, which are internal *specifically* so specs can assert what reached the DSP. Without this the wire→DSP hop is untestable. |
| KlangScript        | `KlangScriptMasterFxExtensions.kt:25-54`                                                              | add `fun lookahead(self, seconds)` beside the existing 5                                                                                                                                                       |
| House constants    | `MasterStage.kt:27-46`                                                                                | add `LIMITER_LOOKAHEAD_SECONDS = 0.005`; `LIMITER_ATTACK_SECONDS` changes meaning — see below                                                                                                                  |
| Authored constants | `MasterStage.kt:27-46`                                                                                | **add `AUTHORED_LIMITER_LOOKAHEAD_SECONDS = 0.0` and `AUTHORED_LIMITER_ATTACK_SECONDS = 0.001`** — see below                                                                                                   |
| Sync spec          | `MasterDefaultsSyncSpec.kt:34-38`                                                                     | rewrite per below                                                                                                                                                                                              |
| Wire round-trip    | `audio_bridge/src/jsTest/.../WireCodecRoundTripSpec.kt:61-64`                                         | sets every `Limiter` field explicitly by design; add a **non-default** `lookaheadSeconds` or a codec defect round-trips vacuously                                                                              |
| Docs               | `audio/ref/effects-mixing.md:9-25`, `.claude/skills/klang-music-writing/ref/sprudel-reference.md:189` | render-loop order + master-FX authoring surface                                                                                                                                                                |
| Parity exception   | `docs/tasks/master-dsl-followups.md` §1                                                               | record that `lookahead` is master-only **on purpose** (§3), or the next person "fixes" it                                                                                                                      |

#### The house-vs-authored asymmetry must be DATA, not prose

Two of the five limiter defaults now legitimately differ between the house limiter (global, post-sum, lookahead on) and
the authored one (per-playback, lookahead off). `MasterDefaultsSyncSpec.kt:37`
currently asserts `Limiter().attackSeconds shouldBe MasterStage.LIMITER_ATTACK_SECONDS`, which would simply break — and
"document the asymmetry" is prose, while `shouldNotBe` is exactly the toothless guard
class [F1](../audio-audit/FINDINGS.md#f1) exists to eliminate.

**Give the authored side its own named constants**, so every number has exactly one home and the asymmetry is
assertable:

```kotlin
// MasterStage companion
const val LIMITER_LOOKAHEAD_SECONDS = 0.005          // house: global, post-sum
const val LIMITER_ATTACK_SECONDS = 0.005          // house: smoothing length (= window, §2.5)
const val AUTHORED_LIMITER_LOOKAHEAD_SECONDS = 0.0   // authored: per-playback, no added latency
const val AUTHORED_LIMITER_ATTACK_SECONDS = 0.001 // authored: one-pole attack, unchanged
```

`MasterDefaultsSyncSpec` then asserts the authored constants against `MasterStageDsl.Limiter()` (the three shared params
keep their existing equality assertions), **plus a relation assertion** that encodes the intent — e.g.
`LIMITER_ATTACK_SECONDS shouldBe LIMITER_LOOKAHEAD_SECONDS` for the maximum-smoothing house default. The asymmetry
becomes a fact the suite checks, not a comment.

#### Three KDoc surfaces become false and must change in this phase

- `MasterStage.kt:24-25` — "Also the defaults of the opt-in `MasterStageDsl.Limiter` stage — kept in sync by
  `MasterDefaultsSyncSpec`". No longer true for two of five.
- `MasterDsl.kt:69-71` — "Defaults mirror that safety limiter's constants".
- `MasterStage.kt:42` — "1 ms allows transients to retain punch before clamping" describes a one-pole attack that no
  longer exists on that instance.

#### Rejected: a separate `smoothingSeconds` param

It would keep `attackSeconds` meaning exactly one thing and leave the sync spec's premise intact — genuinely tidier.
**Rejected because it makes `attackSeconds` inert on the master limiter whenever lookahead > 0**, which is the F1
silent-no-op defect this audit exists to remove, and the user's explicit choice was two knobs that both do something.

The honest framing, which the KDoc must use: **`attack` always means "how fast the gain closes".**
That is one concept with two mechanisms — a one-pole time constant when `lookahead = 0`, a smoothing length when
`lookahead > 0` — not two meanings sharing a name.

**Two guards `MasterChain.buildLimiter` must have** — these are not tone clamps, they are the same protection every
sibling param already gets (`finite()` at `:170-171`, `MAX_DELAY_SECONDS` at `:108`):

- `finite(...)` — `lookaheadSeconds` is the **first stage param that sizes an array**. `-1` →
  `NegativeArraySizeException` **on the audio thread**, killing the worklet; `Infinity` →
  `Int.MAX_VALUE` → OOM. `MasterChainSpec.kt:65-74` exists because a NaN delay time once did exactly this.
- **`MAX_LOOKAHEAD_SECONDS = 0.05`** (50 ms → ~77 KB stereo) — `MasterBus.register` runs on the audio thread
  (`MasterBus.kt:44-48`); `.lookahead(10.0)` would allocate ~7.7 MB there. A resource bound, not a tone clamp — same
  role as `MAX_DELAY_SECONDS = 10.0` (`MasterChain.kt:108`), and it lives beside it.

### The authoring surface — two knobs, decided (user, 2026-08-04)

**Option (a): keep both `lookahead` and `attack`.** Rejected alternative: deriving `B = lookahead/2`
and dropping `attack` — that would make `attack` a **silent no-op** on the master limiter, which is exactly
the [F1](../audio-audit/FINDINGS.md#f1) defect class this audit exists to remove. Every knob on the surface must do
something.

The contract, which the KDoc must state plainly:

```
lookahead = D  = the delay, and the min-hold window (L = D + 1)
attack    = M  = the smoothing length,  M <= D + 1     // they OVERLAP, see §2.3
```

- **`lookahead`** — the whole added latency.
- **`attack`** — the smoothing length. `attack == lookahead` is valid and gives maximum smoothing. **Exact bound, in
  taps: `b1 + b2 − 2 ≤ D`, with `b1, b2 ≥ 1` (so `M ≥ 2`).** Do NOT cite §2.1 (b)'s
  "strictly less than" or its `⅔ × lookahead` — both were measured on the superseded ramp design, and citing them makes
  the next person re-derive the wrong constraint.

Naming: **`.lookahead(...)`, lowercase** — the siblings are `thresholdDb`, `ratio`, `kneeDb`,
`attack`, `release`, all lowercase single words, and "lookahead" is one word in audio like
"sidechain".

```kotlin
master(Master.of(MasterFx.limiter()))                            // house defaults: 5 ms window, 5 ms smoothing
master(Master.of(MasterFx.limiter().lookahead(0.008).attack(0.008)))  // smoother, 8 ms latency
```

⚠️ **The documentation burden this option carries — do not skip it.** Peak performance is *invariant*
to `attack` (§2.3), but LF cleanliness tracks it directly (§2.5). So `.lookahead(0.005)` alone buys latency and only
part of the benefit. The KDoc must say **widen both**, and since more smoothing is strictly better at fixed latency, the
guidance is simply **`attack = lookahead`** unless you want a snappier character.

A `@sample` showing both knobs moved together is worth more here than prose.

### Phase 4 — Retune + by-ear ⚠️ the real risk is trading a knock for a PUMP

Changing the attack model changes how every shipped song's master sounds. Render the song fixtures before/after
(`console/record.sh`, `./gradlew runSongBenchmark` for CPU), listen, and tune both constants by ear. **This is the
gate — no "done" without it.**

⚠️ **The by-ear risk is not the one the peak numbers describe.** Today the limiter applies ~0 dB during a transient and
the clip does the work — the mix *crunches*. After the fix it applies the **full 12 dB to the entire summed mix** with a
100 ms release, four times a bar. That is a textbook recipe for pumping. **Measure gain-reduction depth and duration on
real song fixtures, not just peak dBFS**, and expect to need a dual (fast + slow) release before it sounds *better*
rather than merely *cleaner*.

⚠️ **The character change is GLOBAL, not confined to the pathological kick.** The old one-pole with τ = 1 ms barely
moved for a sub-millisecond peak — that is *why* transients punched through. Min-hold + box is a **true sample-peak
detector**: a single sample 3 dB over threshold now ducks the entire summed mix by 3 dB, held ~`D`, released over 100
ms. So the gain becomes a busy signal at transient rate on *all* material. Perceived punch drops, density rises. Two
consequences:

- **Add a +1 to +3 dB overshoot case to the measurement set.** Every number in this plan is at +6/+12/+18/+24 dB over —
  but shipped songs live at +1 to +3, and that is where the by-ear verdict will actually be decided.
- Artefacts that were previously ignored now steer the master — notably the DC blocker's own +0.53 dB onset overshoot
  (§4).

⚠️ **OPEN: re-measure the window-length justification with the release in the loop.** The −42.6 / −48.0 / −57.4 dB sweep
behind the 5 ms choice was measured on the lookahead path alone. With a 100 ms release, `min()` sits on the *release*
path for roughly half of every 55 Hz cycle, so much of that "THD-ish" number may be the release's, not the smoothing's.
**I attempted this and my crude Goertzel metric returned the numerical noise floor — it is not resolved.** Phase 4 needs
a proper windowed FFT over an integer number of cycles. If the 15 dB shrinks, a shorter window is defensible and the
latency gets cheaper. **Treat 5 ms as provisional until this is measured.**

**Three measurements to add to the harness** — peak dBFS alone hid four of this round's findings, because every failing
variant has a defensible peak number at *some* setting:

1. **Non-fundamental energy on a sustained 55 Hz sine** at +6/+12/+18 dB over ceiling — catches the release ambiguity
   (−42.5 vs −14.2 dB) and the window-length question. Neither moves the peak.
2. **The gain trajectory as a signal** — max |Δgain| per sample and its second difference. The C⁰ corner and the
   snap-and-hold artifact are instantly visible here and invisible in the peak.
3. **Ramp-length invariance sweep** — same ceiling test at three smoothing lengths in a fixed window; assert **all three
   give the same peak**. With a correct construction they must. This single assertion is the direct guard against the
   whole class of shape bugs.

### Phase 5 — Latency reporting

There is currently **no delay-compensation concept anywhere in the DSP path** (confirmed — the only
"latency compensation" in the tree is FE↔BE *clock* sync in `KlangPlaybackController.kt:107`, a different thing). A
lookahead limiter is the first deliberate signal-path latency. Decide whether it must be:

- reported to the FE for scheduling/visual alignment (probably yes — it shifts audio vs. the playhead), and
- accounted for in `KlangOfflineRenderer.render()`, which computes `totalFrames` from musical duration + a fixed
  `tailSec` with no latency term (`KlangOfflineRenderer.kt:166-179`). At 5 ms this is ~220 samples — inaudible, but an
  offline render is no longer sample-identical to the input timing. **Correction: there are no audio golden files.** The
  only golden in the tree is
  `sprudel/src/jvmTest/resources/golden/voicedata_golden.txt` (serialized VoiceData text, never touches the DSP), and
  the offline tests are threshold assertions (`KlangOfflineRendererMasterTest.kt:82-88` compares peak ratios) that a
  sub-ms delay will not disturb. The real exposure is narrower: `KlangAudioRenderer` (`:27`) is never warmed up, so an
  offline WAV now begins with ~88 zero samples and its final 2 ms sits unflushed in the ring. Harmless at the default
  `tailSec = 2.0`, but `KlangOfflineRendererMasterTest.kt:69` renders with
  `tailSec = 0.0`.

## 6. Tests — all mutation-checked per `/review-loop`

⚠️ Two of the originally-drafted assertions were themselves defective. Corrected:

- **`LimiterLookaheadSpec` (new, written in Phase 0 and watched failing).**
    - ❌ *Was:* "output ≤ −1 dBFS". Wrong — at ratio 20:1 the measured clean results are −0.31/−0.37 dBFS, i.e.
      **louder** than the −1 dB threshold. That assertion fails against a perfect implementation.
    - ✅ *Is:* **no sample exceeds 1.0 linear**, plus a peak-value assertion with tolerance.
    - ⚠️ **Measure on the `StereoBuffer` after `limiter.process` and BEFORE the clip — i.e. against
      `Compressor` directly.** `MasterStage.process` emits a `ShortArray` through a hard clip (`:96-110`), so "no sample
      above 1.0" measured there is **trivially true today, bug and all** — a textbook toothless guard of exactly the
      kind the audit is cataloguing.
- **`lookaheadSeconds = 0` is byte-identical** — against the **Phase 0 reference vector**, not against a
  freshly-constructed `Compressor()` (which would be a tautology). This is the guard protecting every shipped song's
  per-orbit compression.
- **Attack clamp is behavioural, not a getter check** — feed the same transient at `attackSeconds`
  0.2 ms and 5 ms with lookahead fixed; assert identical peak output. A getter-equality test would pass against a no-op,
  since the clamp deliberately does not write back to `attackSeconds` (§Phase 1).
- **`reset()` clears the rings** — otherwise the first block after warmup replays stale audio. Cover all three reset
  paths (Phase 1).
- **Two peaks inside one window** — the case that defeated the naive ramp (§2.3). Regression guard for the
  moving-minimum formulation.
- **`MasterChainSpec`** — assert `MasterStageDsl.Limiter(lookaheadSeconds = X)` actually reaches the
  `Compressor`. Requires the `internal val limiters` change (Phase 3); without it this hop is untestable and the parity
  claim is unverified.
- **`finite()` / bound guards** — negative, NaN, `Infinity`, and huge `lookaheadSeconds` must not throw or allocate
  unboundedly. `MasterChainSpec.kt:65-74` is the existing model.
- **DC-before-limiter ordering** — §4 calls the reorder "a required part of the fix", and every other required behaviour
  here has a guard. Assert the order observably: a signal with DC offset must not cost the limiter headroom (feed an
  offset sine, assert the peak matches the offset-free case).
- **`MasterDefaultsSyncSpec`** — extend, and encode the *deliberate* house-vs-authored asymmetry.
- **Latency + coverage assertion** — a delta at sample `n` must emerge at exactly `n + D`, **and the gain minimum must
  coincide with it: `argmin(gain) == D`.** The amplitude half alone does not catch the `W = D + 1` error: with a slow
  release the envelope path still catches the tail and produces a defensible peak while the gain minimum sits in the
  wrong place.
- **Ramp-length invariance** — see Phase 4 item 3. The direct guard for the shape-bug class.
- **Sustained above-ceiling signal** — nothing currently exercises the release path with lookahead.
- **Offline flush** — `KlangOfflineRenderer.render()` stops at `totalFrames`, leaving the last `D`
  samples in the ring.
- **Crossfade** — see §6b.

**Dropped as specified: "no allocation in the render callback".** No allocation-counting harness exists in the tree
(`audio_benchmark/` measures wall time only). **Decision: rely on the
`console/run-dsp-benchmarks.sh` gate Phase 1 already names, and do NOT write an allocation spec.** A regression shows up
there as a throughput cliff. Building a `ThreadMXBean` harness is a separate piece of work and should not be smuggled in
as a bullet — an untestable bullet is how toothless guards get written.

## 6b. ⚠️ The crossfade will comb-filter — unresolved

`MasterBus.process` (`master/MasterBus.kt:269-277`) runs the outgoing and incoming chains **in parallel on the same
input** and blends them linearly for 60 ms. If the two chains have different lookahead (including one having none), the
blend sums a signal with a delayed copy of itself → a **comb filter**, first notch near 100 Hz for a 5 ms difference —
bass, not presence, sweeping in over the fade. Every live-coding edit that adds, removes or retunes a master limiter
hits this.

Compounding: `beginFade` resets the incoming chain (`:245-247`), so its delay ring starts empty and the incoming side
contributes **silence for its first 2 ms** of the fade.

The linear (not equal-power) blend was a deliberate choice because both chains process the same input and their outputs
are correlated — but that reasoning assumed they were **time-aligned**, which a per-chain lookahead breaks.

**DECIDED (2026-08-04): accept and document. The exposure is far narrower than it first looks**, for two structural
reasons:

1. **The house limiter never crossfades.** `MasterStage` runs at `PlaybackEngineDispatcher.kt:133`, *after* summing and
   entirely outside `MasterBus`. Its 5 ms is a constant latency on the whole output and can never differ across a swap.
2. **The authored limiter defaults to `lookaheadSeconds = 0.0`** (Phase 3), so a default authored master chain has
   **zero** latency and blends exactly as it does today.

The comb is therefore reachable only when someone **explicitly** sets `.lookahead(...)` on an authored master limiter
**and then live-edits the master**. Opt-in, and audible as a 60 ms phasey sweep rather than a defect.

**Record it in the `.lookahead()` KDoc** so it is a known consequence, not a mystery. Contained fix if it ever bites:
pre-delay the shorter chain by the latency difference for the duration of the fade —
`MasterChain` would need to expose its latency, which it must do for Phase 5 anyway.

Still worth a guard: `beginFade` resets the incoming chain (`:245-247`), so its ring starts empty and the incoming side
contributes **silence for its first `D` samples**. At 5 ms that is a 60 ms fade starting from a 5 ms hole — cheap to
test, and a spec belongs in §6.

## 7. Doc debts found while mapping (pre-existing, unrelated to this change)

Both are wrong today and would mislead anyone working here:

- `audio/ref/effects-mixing.md:79-89` is wrong three ways in six lines: it states attack/release are in **ms** (they are
  **seconds**, `Compressor.kt:41-42`), calls the detector **RMS-based** (it is peak —
  `max(abs(left[i]), abs(right[i]))`, `Compressor.kt:114`), and says compression is applied
  "per-cylinder **or per-voice**" (it is never per-voice — `Cylinder.kt:58`).
- `audio/ref/data-model.md:41` types `compressor` as `Double?` "per-voice compression amount"; it is a `String?` packed
  5-field config, carried per-voice but **applied per-orbit**.

## 7b. Credits — required before this ships

**Yes, this needs an attribution.** The design in §2.3 is taken from a specific, identifiable source:

- **Geraint Luff — *"Designing a straightforward limiter"*, Signalsmith Audio (2022).** The moving-minimum → smoothing →
  delay-by-the-sum construction, and the insight that the smoothing duration must be *part of* the lookahead budget
  rather than fit inside it, come from this article. Our prototype independently hit the failure that article predicts
  (a ramp equal to the window is already too late), which is what sent us to it.

⚠️ **Implement from the described method, not by transcribing the article's C++.** The snippets carry no stated licence.
The underlying technique is standard DSP; the exposition is what we are crediting.

Both credit surfaces must be updated **together** — they mirror each other section-for-section:

| Surface | File                                         | Section                            |
|---------|----------------------------------------------|------------------------------------|
| Repo    | `CREDITS.MD`                                 | "DSP Algorithms & Techniques"      |
| In-app  | `src/jsMain/kotlin/pages/CreditsPage.kt:326` | `H2 "DSP Algorithms & Techniques"` |

While there: the existing `Compressor` (dB-domain feed-forward detector, soft-knee parabolic curve)
is textbook and currently uncredited in either surface. A single line covering the compressor/limiter topology would
close that gap at the same time.

Nothing else in this change needs attribution — the sliding-window maximum/minimum is classic CS, and the delay ring is
our own.

## 8. References

- **[Geraint Luff — Designing a straightforward limiter](https://signalsmith-audio.co.uk/writing/2022/limiter/)**
  (Signalsmith Audio, 2022) — **the source of the §2.3 design**; see §7b for the credit obligation
- [Tonalux — Lookahead limiting, delay compensation, peak anticipation](https://tonalux.org/blog/lookahead-limiting-delay-compensation-peak-anticipation)
- [Mastering The Mix — Advanced limiting techniques](https://www.masteringthemix.com/blogs/learn/advanced-limiting-techniques)
- [Sage Audio — How to limit your master](https://www.sageaudio.com/articles/how-to-limit-your-master)

The rule that pins our defect: *"if the lookahead is shorter than the attack time, the limiter will essentially be
guessing at the loud sounds instead of catching them."* Ours is zero lookahead with a 1 ms attack — always guessing.

Not in scope, worth knowing: **true-peak** (inter-sample) limiting is the actual streaming standard (−1 dBTP) and needs
oversampled detection. We do sample-peak only. Deferring is defensible — the output path ends in a `ShortArray` with no
reconstruction, and today's hard clip is a far worse inter-sample-peak generator than anything the fix produces. Two
things to record so they are not re-derived: (a) the fix *reduces* ISP relative to today; (b) the remaining margin after
the fix is only ~0.35 dB, which is **below** typical ISP overshoot on limited material (0.5–1.5 dB) — so if true peak
ever becomes a requirement, the answer is to **lower the ceiling, not change the algorithm**.

## 9. Links

- Finding: [`../audio-audit/FINDINGS.md`](../audio-audit/FINDINGS.md) §F16
- Precedent for the whole DSL-surface playbook:
  [`../tasks-archive/2026-08/20260803-master-dsl.md`](../tasks-archive/2026-08/20260803-master-dsl.md)
- Parity rule + the exception this creates: [`master-dsl-followups.md`](master-dsl-followups.md) §1
- Method: [`.claude/skills/review-loop`](../../.claude/skills/review-loop/SKILL.md)
