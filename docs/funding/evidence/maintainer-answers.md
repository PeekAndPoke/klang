# Maintainer answers to multiple-choice questions

Extracted from AskUserQuestion results in the local transcripts. `Q` is the agent's question
(agent text, kept verbatim, including its punctuation). `A` is the option the maintainer picked
or the free text they typed. `(Recommended)` marks the option the agent recommended. Timestamps UTC.

## 2026-08-19T08:04:49 | 33180650-9f15-4cc6-b6d4-ad10a42d7c21

- **Q:** Fold the serial stages into the eq? With the section model above (notch/highpass/lowpass keep their exact current params, only .band() carries dB), any filter chained after .eq() can fold into the same fused node automatically — one per-sample loop for the whole chain.
  - **A:** Full chain (Recommended)
- **Q:** Besides the eq, ship the two small extras? (1) Engine-internal: .mul()/.add() with block-constant operands skip the scratch-buffer render (today even .mul(2.0) renders a 128-sample constant buffer first) — every song benefits, nothing to migrate. (3) passes = N on the standalone lowpass/highpass for cascades outside an eq.
  - **A:** How would this exactly work and how can we apply this in general when there is a constant arithmatic step?

## 2026-08-19T08:07:15 | 33180650-9f15-4cc6-b6d4-ad10a42d7c21

- **Q:** With the constant-fold mechanism now clear (generalizes to all binary ops via controlRateValueOrNull, engine-internal, nothing to migrate) — ship the extras alongside the eq?
  - **A:** And what happens if we have multiple constant arithmatic steps like signal.mul(Osc.constant(1).minus(pMyConstantParam)) ... params should be treated as constant as well

## 2026-08-19T08:07:52 | 33180650-9f15-4cc6-b6d4-ad10a42d7c21

- **Q:** Ship the extras alongside the eq? (1) The constant-fold in all binary combinators, as just discussed. (3) passes = N on the standalone lowpass/highpass for cascades outside an eq.
  - **A:** Both (Recommended)

## 2026-08-19T10:11:34 | 33180650-9f15-4cc6-b6d4-ad10a42d7c21

- **Q:** Ready to run the D0 baseline benchmarks? I'll run `runSongBenchmark --args=ladders` then `--args=live` through the build lock — but the frontend auto-compile watcher must be OFF first (the lock cannot serialize against it, and a race corrupts the sprudel KSP cache).
  - **A:** Watcher is off — run

## 2026-08-19T11:05:20 | 33180650-9f15-4cc6-b6d4-ad10a42d7c21

- **Q:** D0's measurements say the fused EQ can win ~3–5% of song CPU, not the ~35–60% projected — the Fairphone fix lives in the bare signal (distort os4, unison depth, analog drift, superimpose), not the filter chain. How do we re-scope?
  - **A:** Hmm we anyway need the eq in all dsl layers. So why not continue with it and cash in the performance gains along the way?

## 2026-08-19T13:05:47 | 33180650-9f15-4cc6-b6d4-ad10a42d7c21

- **Q:** safeOut on Plus: partial application breaks bit-identity between Plus's own paths (1e15+1e15: bare scratch → 2e15, clamped fill → 1e15), so the real choice is all-or-nothing.
  - **A:** Keep Plus bare (Recommended)

## 2026-08-23T19:24:51 | 8b490939-1e17-4f63-8357-cd8afd4bd1ee

- **Q:** FrozenSongs.kt has 23 colon strings that stop parsing after C0, but its header says "DO NOT edit these to track song changes". How should C0 treat it?
  - **A:** Syntax-only migrations OK (Recommended)
- **Q:** compressor("thr:ratio:knee:att:rel") is a WIRE field parsed inside audio_be, so splitting it is a wire-format change (VoiceData shape, KSP codec, schema hash, worklet). How should C0 handle it?
  - **A:** Own sub-step inside C0

## 2026-08-23T22:45:30 | 8b490939-1e17-4f63-8357-cd8afd4bd1ee

- **Q:** C1+C2 changes what filters sound like (default q 0.707 + unity-peak bandpass family). How should we run it?
  - **A:** Build now, I listen after (Recommended)

## 2026-08-24T07:45:02 | 8b490939-1e17-4f63-8357-cd8afd4bd1ee

- **Q:** accelerate() is secretly in OCTAVES (accelerate(1) = +1 octave over the note; engine: 2^(amount·progress), no /12). What should it be?
  - **A:** Convert to semitones (Recommended)
- **Q:** The phaser runs TWICE per note (per-voice strip pass + cylinder-bus pass, same knobs — pre-existing). With the new phaserFloor < 1 the dry gets floored twice (≈ floor²). Which path should own the phaser?
  - **A:** Hmm that is a good question: how do phaser normally work in a DAW? Are they per voice or per bus effect. Running it double is not desirable i asume.
- **Q:** C5 (`passes` on lpf/hpf): two cascaded Butterworth stages are −6 dB AT the cutoff and the −3 dB knee slides to ~0.64·fc — so passes=2 is darker, not just steeper. How should the cascade behave?
  - **A:** Stagger per-stage q (Recommended)
- **Q:** C5: lpf with q omitted is the one-pole path (6 dB/oct). Should `passes` work there too?
  - **A:** No q provided must fallback to the default 0.707 ... no "secret" swapping of an effect. We also need to rename "warmth" to express that it is a onepole lpf. How would you name it and what scale should the param have. There is also a mismatch between the scale of the param in the warmth in sprudel and in the ignitor dsl. Can we resolve this as well please?

## 2026-08-24T07:55:01 | 8b490939-1e17-4f63-8357-cd8afd4bd1ee

- **Q:** Phaser ownership: remove the Phaser stage from the default `modern` pipeline so the BUS phaser owns the knob (DAW-normal: one coherent sweep over the summed orbit)? StripPhaserRenderer stays available for explicit custom pipelines. Audible: phaser songs lose one of their two wet passes.
  - **A:** Yes, bus owns it (Recommended)
- **Q:** warmth rename: my proposal is ONE name on both doors — `onepole(freq)` — with freq in Hz (lowercase one word like lpf/lowpass; says exactly what it is; param matches the C6-decided `freq`). Sprudel migrates from the raw 0..1 coefficient to Hz (sites converted value-exactly at 48k; this also fixes the coefficient being sample-rate-dependent). The ignitor aliases `warmth`/`onePoleLowpass` collapse into it.
  - **A:** How would the conversion actually work? What does warmth(0.5) currently stand for?

## 2026-08-24T07:59:28 | 8b490939-1e17-4f63-8357-cd8afd4bd1ee

- **Q:** Given the conversion above (exact, per-site, table values), which name/scale for the one-pole lowpass on both doors?
  - **A:** sorry i cannot see the conversion proposal. Can you please show it explicitly

## 2026-08-24T08:02:28 | 8b490939-1e17-4f63-8357-cd8afd4bd1ee

- **Q:** With the conversion shown above (w is the raw filter coefficient today; fc = sr/pi * atan((1-w)/w); per-site exact values like warmth(0.8) -> onepole(3743)), which name and scale should the one-pole lowpass get on both doors?
  - **A:** onepole(freq) in Hz (Recommended)

## 2026-08-24T11:16:08 | 8b490939-1e17-4f63-8357-cd8afd4bd1ee

- **Q:** What should the coefficient in "distribution:X" mean? (All options keep the bare "distribution" default sounding as it does now.)
  - **A:** Fraction of the band (Recommended)

## 2026-08-25T06:55:00 | 34da42ea-3401-4de6-a694-34cff8a18389

- **Q:** Does the de-umlauting also cover the standalone "Motör" (the engine metaphor)?
  - **A:** Yes, all ö → o
- **Q:** What about historical records (DEV-DIARY.MD, docs/history/, docs/tasks-archive/)?
  - **A:** Leave history untouched

## 2026-08-25T17:44:57 | 8b490939-1e17-4f63-8357-cd8afd4bd1ee

- **Q:** DerSchmetterling.kt has 4 named-arg sites the rename touches, and it's your uncommitted by-ear work. How should I handle it?
  - **A:** I rename it, own commit (Recommended)
- **Q:** The task doc flags a second vocabulary collision to decide in the same pass: sprudel ships band(mask)/eq(equality) while the ignitor DSL ships .band(EQ band)/.eq(open equalizer). Nothing breaks (dispatch is by receiver type), but the docs registry merges symbols by name.
  - **A:** Let's rename eq() and the ignitor to equalizer() then

## 2026-08-25T17:46:12 | 8b490939-1e17-4f63-8357-cd8afd4bd1ee

- **Q:** The other half of that collision is band: sprudel has band(mask) (bitwise AND), the ignitor has .band(freq, q, db) which builds an EqSection.Bell. Which one moves?
  - **A:** Leave band alone

## 2026-08-25T17:48:43 | 8b490939-1e17-4f63-8357-cd8afd4bd1ee

- **Q:** Shift names for blshift / brshift?
  - **A:** bitShl / bitShr

## 2026-08-28T20:50:23 | 127f0cb2-2ec9-4a8a-85dc-21e128d7ba5f

- **Q:** ConfigurePlayback/autoCleanup turns out to be unnecessary for v0 (no idle reaper exists; cleanup is only ever sent by a playback's own stop). Defer it, or build it now anyway?
  - **A:** Defer it (Recommended)

## 2026-08-30T15:45:20 | e42d2d00-f5be-40f6-b117-6b550b0042ba

- **Q:** W1: how should the coarse sample-and-hold bootstrap be fixed (both doors)?
  - **A:** counter = 1.0 bootstrap (Recommended)

## 2026-08-30T15:46:29 | e42d2d00-f5be-40f6-b117-6b550b0042ba

- **Q:** W2: fix the tremolo LFO with the delay round's D1 shape (advance the phase by ctx.length unconditionally in the bypass arm) plus the house wrapPhase on both doors?
  - **A:** Yes, both parts (Recommended)

## 2026-08-30T15:47:11 | e42d2d00-f5be-40f6-b117-6b550b0042ba

- **Q:** W3: narrow the coarse bypass guard to `amount <= 0` (plus a non-finite arm) so the S&H clock and held value stay contiguous through the (0,1] range, instead of inventing a bypass-time clock rate?
  - **A:** Yes, narrow the guard (Recommended)

## 2026-08-30T15:48:58 | e42d2d00-f5be-40f6-b117-6b550b0042ba

- **Q:** W5: what happens to the legacy DistortIgnitor?
  - **A:** Delete it

## 2026-08-30T15:49:37 | e42d2d00-f5be-40f6-b117-6b550b0042ba

- **Q:** W10: the six inert sprudel tremolo controls (skew, phase, shape + aliases) — implement or retire?
  - **A:** Implement all three (Recommended)

## 2026-08-30T15:50:35 | e42d2d00-f5be-40f6-b117-6b550b0042ba

- **Q:** W7: how raw do we stay on NaN/Inf egress?
  - **A:** Record only, master heal later (Recommended)

## 2026-08-30T15:52:01 | e42d2d00-f5be-40f6-b117-6b550b0042ba

- **Q:** W13: what happens with the phaseMod family's musical/absolute question?
  - **A:** Own round later (Recommended)

## 2026-08-30T17:25:41 | e42d2d00-f5be-40f6-b117-6b550b0042ba

- **Q:** Sakura's .coarse(3) hold grid rotates by one sample for the whole note under the W1 fix — how do you want to sign it off?
  - **A:** Fine, commit as is

## 2026-08-30T18:17:17 | e42d2d00-f5be-40f6-b117-6b550b0042ba

- **Q:** F1: what unit is `tremolophase` in? The KDoc says radians, the factory and the shipped test rows say turns.
  - **A:** Turns, fix the KDoc (Recommended)

## 2026-08-30T18:18:04 | e42d2d00-f5be-40f6-b117-6b550b0042ba

- **Q:** F2: where is `tremoloskew`'s neutral point? The KDoc says 0.5, the factory defaults it to 0.0 — as written, every existing tremolo would come out maximally skewed.
  - **A:** 0.0 = symmetric, range -1..+1 (Recommended)

## 2026-08-30T18:20:19 | e42d2d00-f5be-40f6-b117-6b550b0042ba

- **Q:** F3: which LFO shapes does `tremoloshape` actually get? The KDoc names six; the waveform editor it points at offers a different five (no rampup/rampdown, plus an undocumented `noise`).
  - **A:** Let us not introduce new shapes like "rampup" or "rampdown" now. Let us use the shapes the user already know from other areas.

## 2026-08-30T18:37:16 | e42d2d00-f5be-40f6-b117-6b550b0042ba

- **Q:** F5: does the ignitor door get skew/phase/shape too, or is this round the sprudel/strip half only?
  - **A:** Strip half only, file the parity (Recommended)

## 2026-08-30T20:06:02 | e42d2d00-f5be-40f6-b117-6b550b0042ba

- **Q:** Both reviewers caught this: skew is inverted on `sawtooth`. The warp stretches the waveform's FIRST half, which is the loud half for sine/triangle/square/ramp but the QUIET half for sawtooth. Measured over one cycle at skew +0.6: sine 0.691 mean, ramp 0.650, square 0.800 — but sawtooth 0.351.
  - **A:** Flip the duty for sawtooth (Recommended)

## 2026-08-30T21:03:17 | e42d2d00-f5be-40f6-b117-6b550b0042ba

- **Q:** What should `coarse(Infinity)` do on the strip? Today it freezes the voice to DC for the whole note; the ignitor door passes the signal through untouched.
  - **A:** Match the ignitor: pass through (Recommended)

## 2026-08-31T06:32:16 | e42d2d00-f5be-40f6-b117-6b550b0042ba

- **Q:** M1: should a song's FIRST master application still crossfade in over 60 ms? Today DerSchmetterling's opening downbeat is 8.3 dB down and swells for 60 ms, because the fade ramps up from unity.
  - **A:** Apply instantly when there is nothing to fade from (Recommended)

## 2026-08-31T06:34:43 | e42d2d00-f5be-40f6-b117-6b550b0042ba

- **Q:** M2: where does the one master-side heal go? One non-finite sample latches the master DC blockers and silences the whole backend until a page reload.
  - **A:** What would be the most general solution. The fix in the master seems to be a patch not a full solution

## 2026-08-31T06:40:17 | e42d2d00-f5be-40f6-b117-6b550b0042ba

- **Q:** How wide should the general fix go? The core move is widening `flushDenormal` so no IIR carry can hold a non-finite value.
  - **A:** Widen the helper, then close the exceptions (Recommended)

## 2026-08-31T06:40:55 | e42d2d00-f5be-40f6-b117-6b550b0042ba

- **Q:** The helper will no longer only flush denormals — it will flush anything the carry must not hold. `flushDenormal` becomes a name that under-describes it. Rename?
  - **A:** Rename to `flushState()` (Recommended)

## 2026-08-31T13:11:32 | e42d2d00-f5be-40f6-b117-6b550b0042ba

- **Q:** How much net do we build under `strip/pitch` before phaseMod part 2? Confirmed open there: F11 (FM modulator quantity unguarded — 1000x too slow passes), F12 (five "disabled" tests that compare a value against its own default, so no mutation can falsify them), and F15(a)'s accelerate item.
  - **A:** Fix F11 + F12, skip the rest (Recommended)

## 2026-08-31T13:51:12 | e42d2d00-f5be-40f6-b117-6b550b0042ba

- **Q:** Fixing this changes `DialogueWithTheStars`: its 180 Hz soundbox thump stops being pitch-enveloped by 0.4 semitones. The code comment says "fixed-pitch", so the fix matches the stated intent — but it is still a by-ear change to a shipped song, and you have not heard it either way.
  - **A:** Fix it — absolute freq means absolute (Recommended)

## 2026-08-31T19:08:59 | 127f0cb2-2ec9-4a8a-85dc-21e128d7ba5f

- **Q:** May I run Gradle now to green-and-mutation-check the new toChunks spec?
  - **A:** Go ahead, watcher is down

## 2026-09-04T14:35:22 | e42d2d00-f5be-40f6-b117-6b550b0042ba

- **Q:** Finish the warmup vocabulary now, or defer it to a v2 task?
  - **A:** Finish it now (Recommended)

## 2026-09-05T20:38:06 | 743ece1a-3a76-4242-8a86-2acbb786bc6e

- **Q:** How wide should the new skill be?
  - **A:** DSL design principles (Recommended)
- **Q:** What should the skill be called? (becomes the /slash-command)
  - **A:** dsl-design

## 2026-09-07T15:58:00 | f6fba18e-29b1-4ad5-b2b0-6a2b68fbc36d

- **Q:** An IDE code-cleanup swept sprudel/src/commonMain/kotlin/lang/ at 17:43 while the workers were running. It rewrapped long parameter lists across ~15 files, removed 2 unused imports, and renamed 8 KDoc tags from `@param mask` to `@param args` (fixing a real name mismatch — the parameter is `vararg args`). All behaviour-neutral, all verified token-safe. How should I handle it?
  - **A:** Fold into the split commits
- **Q:** Was that sweep something you ran deliberately, or an automatic IDE action? It affects whether I should expect it to recur while the last group lands.
  - **A:** I ran it deliberately

## 2026-09-08T10:09:18 | e42d2d00-f5be-40f6-b117-6b550b0042ba

- **Q:** How should the scope be carried: as structured metadata through the KSP pipeline, or as prose in the doc block?
  - **A:** Structured @scope tag (Recommended)

## 2026-09-08T11:01:42 | e42d2d00-f5be-40f6-b117-6b550b0042ba

- **Q:** "Scope" now denotes two concepts: tutorial effort (Quick/Standard/DeepDive) and audio reach (voice/orbit/master). How should we resolve the collision?
  - **A:** Rename the tutorial one (Recommended)

## 2026-09-10T09:54:29 | 9f5d38a5-8756-44bb-b7a9-570f9429c4c9

- **Q:** Do the two-argument `Math.min(a, b)` / `Math.max(a, b)` free functions flip too, or keep selection semantics?
  - **A:** Keep Math.* as selection (Recommended)

## 2026-09-16T13:17:12 | 351cbd90-a102-4860-8794-cbff5b7be475

- **Q:** Which word names the reverb concept on every surface?
  - **A:** reverb (Recommended)
- **Q:** What happens to `fade`, which is `size / 10` with an override?
  - **A:** Remove it (Recommended)
- **Q:** What happens to `damp` (0..1, master only)?
  - **A:** Remove it (Recommended)
- **Q:** How far should the size ceiling go?
  - **A:** Keep it at 10

## 2026-09-16T14:43:01 | 351cbd90-a102-4860-8794-cbff5b7be475

- **Q:** LangRetiredDoorsSpec goes. Its siblings also only assert that a removed name stays gone. Which of them should go too?
  - **A:** All name-guard tests (Recommended)
- **Q:** Should this become a standing rule in the CLAUDE.md register, so future removals don't add such guards again?
  - **A:** No new rule necessary, if at all remove any rule that led you to write this spec

## 2026-09-16T15:00:44 | 351cbd90-a102-4860-8794-cbff5b7be475

- **Q:** Which default should the shared constants carry, on every surface?
  - **A:** Musical: unset means a usable sound
- **Q:** The reverb has the same split (master size 5 / wet 0.25 vs orbit 0). Apply the shared constants to the reverb in the same change?
  - **A:** Yes, delay and reverb (Recommended)

## 2026-09-19T15:11:58 | 2b9d5146-bb91-421f-8521-3abde0cee4d4

- **Q:** 5b-3 removes the phaser fields from the wire. A user's custom pipeline (`Pipeline...phaser()`, i.e. `StageDsl.Phaser`) runs a PER-VOICE phaser that reads exactly those fields. What should that stage become?
  - **A:** OK i am not fully sure what the background of this question is. The plan says that we will fully retire the PipelineDsl. This means, there is no need to remove things individually. I would say we port all the effects that currently live on the PipelineDsl to the KatalystDsl (in case they should be there) and then clean up the pipeline code fully in one go.
- **Q:** 5c's 'crossfade from what sounds now' needs a rule for RAPID changes, e.g. a `.katp` or material pattern that changes every block. Otherwise dozens of outgoing banks would be live at once. Which rule?
  - **A:** Yes we will go for cap of banks for now. What would be a useful cap, maybe 10?

## 2026-09-23T11:01:05 | 2b9d5146-bb91-421f-8521-3abde0cee4d4

- **Q:** lowpass: which parameters stay on the door, and which move onto the builder?
  - **A:** I do not fully understand the first proposal. I would not create four functions for attack, decay, sustain, release. I would create one .adsr() the same patterns as the adsr on the ignitor chain

## 2026-09-23T11:02:14 | 2b9d5146-bb91-421f-8521-3abde0cee4d4

- **Q:** With the envelope as one adsr() on the builder: does q stay on the lowpass door?
  - **A:** Yes q on the door, f.e.: lowpass(200, 1.2, x => x.adsr().env())

## 2026-09-23T11:02:38 | 2b9d5146-bb91-421f-8521-3abde0cee4d4

- **Q:** highpass has the same parameters as lowpass. Same split?
  - **A:** Same, and bandpass/notch too

## 2026-09-23T11:04:14 | 2b9d5146-bb91-421f-8521-3abde0cee4d4

- **Q:** drive(amount, driveType = "linear"): driveType has only one value today. What happens to it?
  - **A:** I would keep it at the door, but rename the param from "driveType" to "shape" if this makes sense. Or actually, do we need this param at all? What would it do and which other options would there be?

## 2026-09-23T11:04:49 | 2b9d5146-bb91-421f-8521-3abde0cee4d4

- **Q:** Given that driveType is never read and every colour it promised belongs to shape(): what happens to it?
  - **A:** Remove it everywhere (Recommended)

## 2026-09-23T11:05:58 | 2b9d5146-bb91-421f-8521-3abde0cee4d4

- **Q:** shape(shape = "soft", oversample = 0): where does each go?
  - **A:** I would keep it on the door, then we do not need to introduce a builder and we will remove the oversample knob with the future task anyway.

## 2026-09-23T11:06:13 | 2b9d5146-bb91-421f-8521-3abde0cee4d4

- **Q:** distort(amount, shape = "soft", oversample = 0): keep it flat like shape()?
  - **A:** Flat, like shape (Recommended)

## 2026-09-23T11:07:07 | 2b9d5146-bb91-421f-8521-3abde0cee4d4

- **Q:** pitchEnvelope: which parameters stay on the door?
  - **A:** Please give me an example how this would look like in the proposed shape

## 2026-09-23T11:08:49 | 2b9d5146-bb91-421f-8521-3abde0cee4d4

- **Q:** pitchEnvelope: which shape, knowing that releaseSec and curve are read and discarded on both surfaces?
  - **A:** I think it should have a builder with .adsr() and .adsrCurves(), do you agree?

## 2026-09-23T11:09:48 | 2b9d5146-bb91-421f-8521-3abde0cee4d4

- **Q:** pitchEnvelope(semitones, configure) with adsr() and adsrCurves() on its builder. Do the four filter builders get adsrCurves() too?
  - **A:** Yes, filters too (Recommended)

## 2026-09-23T11:11:22 | 2b9d5146-bb91-421f-8521-3abde0cee4d4

- **Q:** tremolo gains shape, skew and phase in 3b (they exist on the strip, not on the node). Where do they go?
  - **A:** rate, depth on door (Recommended)

## 2026-09-23T11:12:05 | 2b9d5146-bb91-421f-8521-3abde0cee4d4

- **Q:** The dry floor: one name across the three DSLs?
  - **A:** floor everywhere (Recommended)
- **Q:** shimmer(feedback = 0.5, tone = 4000, pitches, configure) with wet and dryFloor on the builder: keep this split?
  - **A:** Keep the split (Recommended)

## 2026-09-23T11:13:11 | 2b9d5146-bb91-421f-8521-3abde0cee4d4

- **Q:** fm(modulator, ratio, depth): the node's index envelope and freq exist but the script door never exposed them. Shape?
  - **A:** adsr + freq on builder (Recommended)

## 2026-09-23T11:14:08 | 2b9d5146-bb91-421f-8521-3abde0cee4d4

- **Q:** phaser must be one shape on the Ignitor and the Katalyst. Which principle, knowing it then applies to reverb, delay and the other bus effects too?
  - **A:** Musical inputs on the door (Recommended)

## 2026-09-23T11:15:19 | 2b9d5146-bb91-421f-8521-3abde0cee4d4

- **Q:** eq(configure) is already one shape on the Ignitor and the Katalyst, but eq() without a lambda silently does nothing. Make the lambda required?
  - **A:** Keep it optional

## 2026-09-23T11:16:10 | 2b9d5146-bb91-421f-8521-3abde0cee4d4

- **Q:** reverb on the Katalyst and the Master (today builder-only: wet, size, lowpass). Shape?
  - **A:** [User dismissed — do not proceed, wait for next instruction]

## 2026-09-23T11:18:04 | 2b9d5146-bb91-421f-8521-3abde0cee4d4

- **Q:** One rule for every wet effect: wet on the door in sprudel's position, floor on the builder. Confirm these shapes?
  - **A:** Yes wet on the door, param position 1 ... floor on the builder

## 2026-09-23T11:18:40 | 2b9d5146-bb91-421f-8521-3abde0cee4d4

- **Q:** "wet at param position 1": the very FIRST parameter everywhere, or index 1 as in sprudel (after the effect's identity input)?
  - **A:** Very first param. Sprudel need to match the order of params.

## 2026-09-23T11:20:34 | 2b9d5146-bb91-421f-8521-3abde0cee4d4

- **Q:** delay (Katalyst and Master): where does cap go?
  - **A:** cap on the builder (Recommended)

## 2026-09-23T11:21:43 | 2b9d5146-bb91-421f-8521-3abde0cee4d4

- **Q:** The limiter says thresholdDb and kneeDb where the compressor and sprudel say threshold and knee. One word?
  - **A:** threshold, knee everywhere (Recommended)
- **Q:** compressor (Katalyst) and limiter (Master): door shape?
  - **A:** All flat, like sprudel

## 2026-09-23T11:22:35 | 2b9d5146-bb91-421f-8521-3abde0cee4d4

- **Q:** body and vowel (Katalyst; today body(material, configure) { material, wet, floor }). The wet rule gives this. Confirm?
  - **A:** Yes, as shown (Recommended)

## 2026-09-23T11:22:45 | 2b9d5146-bb91-421f-8521-3abde0cee4d4

- **Q:** duck (Katalyst; today duck(configure) { orbit, depth, attack }; sprudel duck(orbit, depth, attack)). Shape?
  - **A:** Flat, like the compressor (Recommended)

## 2026-09-24T13:43:32 | 2b9d5146-bb91-421f-8521-3abde0cee4d4

- **Q:** floor vs dryFloor: the Ignitor phaser KDoc says 'Named dryFloor, not floor: floor() is already the arithmetic round-down on patterns, and one word must not mean two things.' Yesterday you chose floor everywhere without that reason. Which stands?
  - **A:** As long as the floor() is only on effect builders we can live with duplication in naming. So yesterdays descission stands
- **Q:** fm freq: on 2026-08-30 you decided it is deliberately NOT exposed ('a hidden internal of the pitch machinery; the default IS the semantics; absolute authoring stays a power-user construction'). Yesterday's walk put freq(hz) on the fm builder without that context. Which stands?
  - **A:** Keep it hidden (Aug 30)

