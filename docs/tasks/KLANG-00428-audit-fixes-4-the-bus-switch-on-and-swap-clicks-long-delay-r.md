# KLANG-00428: Audit fixes 4: the bus (switch-on and swap clicks, long delay rings, compressor, reverb lowpass, vowel, phaser, duck order)

> **Status (2026-11-10): PAUSED.** 22 of 29 findings fixed, pinned or accepted with a reason. Waiting for the maintainer: seven decisions (see Decide).
>
> **Part of:** KLANG-00353 (The Motor: correct at the edges)

## Why this exists

The engine audit (KLANG-00420) found these. Every row is traced in `docs/audio-audit/2026-11-10/audit/` (the layer
report named by the id's layer, and the `disproof-*.md` that measured it); the index is `SUMMARY.md` there. The
maintainer's decision (2026-11-10): "create tickets and fix all of the findings. Sound changes are fine, for example
for the pluck."

## The findings

| Id | Severity now | Finding | Verdict and measurement |
|---|---|---|---|
| L7a-01 | major | A delay or a reverb switched on while the bus sounds feeds the unit a step | Confirmed: Delay switched on under a held tone: kink 120.6 at 250 ms, burst -22.8 dB |
| L7a-02 | major | A chain swap feeds the arriving chain's room and delay at full level from the first block | Confirmed: A swap into a delay reads exactly like switching it on; the room's first return is at 25.3 ms, inside the 60 ms ramp |
| L7a-03 | minor | A leaving chain that sounds on silence stays at full level for 20 s after the edit that removed it | Confirmed: `pow(-1)` and `plus(0.2)` hold full level 20.36 s after the edit, above 1e-6 until 24.56 s |
| L7a-04 | (read only) | With an authored lookahead the three gain-computer knobs do not glide, and the docs do not say so where the law is stated | not challenged; the auditor's reading in `layer-7a-katalyst-chain.md` |
| L7a-05 | minor | A `parallel` pad ring is not part of the tail question | Confirmed: By reading: a pad without twins never reports a tail |
| L7a-06 | (read only) | The tail scans run on every switch-off, not only in a swap's drain | not challenged; the auditor's reading in `layer-7a-katalyst-chain.md` |
| L7a-07 | (read only) | The delay ring has no size bound, and its allocation and its cleaning run in `configure` | not challenged; the auditor's reading in `layer-7a-katalyst-chain.md` |
| L7a-09 | (read only) | A switched-off lookahead compressor runs its whole detector | not challenged; the auditor's reading in `layer-7a-katalyst-chain.md` |
| L7a-10 | (read only) | The noise seeds follow the builder's line order, which no row pins | not challenged; the auditor's reading in `layer-7a-katalyst-chain.md` |
| L7b-02 | minor | The vowel tables use formant bandwidths (in Hz) as q, so every formant is about 10 to 35 Hz wide | Confirmed, smaller: Widths 10 to 22 Hz measured; the swing is 17.8 dB at wet 1 floor 0, 2.1 dB at the defaults |
| L7b-03 | minor | `reverb.lowpass` is documented as a cutoff in Hz, and it is a linear damping amount | Confirmed: 4 kHz T60: 0.79 s at lowpass 0, 0.81 at 1000, 1.22 at 20000 |
| L7b-04 | minor | The compressor smooths the LEVEL in dB before the gain computer: a dead time on the attack and a release that both hang on the -100 dB floor | Confirmed: Dead time 211 and 188 frames; release 24.5 ms to silence, 128.9 ms to -30 dBFS |
| L7b-05 | minor | The phaser's breakpoint is clamped to 18 kHz whatever the sample rate: below 36 kHz the all-pass can go unstable | Confirmed: At a 32 kHz engine: peak 8.8e307 and 49 non-finite samples; rates under 36 kHz are reachable offline |
| L7b-06 | major | `DelayLine.reset` zeroes the whole ring in one block, whatever its size | Confirmed: 10 s delay: 571 us to clear, 953 us where the drain end and the deactivation share a block; a missed block from about a 32 s delay |
| L7b-07 | note | A duck's life builds its `Ducking` on the audio thread | Confirmed, smaller: Same as L6-06 |
| L7b-08 | (read only) | A chain of ducks depends on the order the cylinders were first used | not challenged; the auditor's reading in `layer-7b-bus-stages.md` |
| L7b-09 | (read only) | `phaser(sweep = 0)` sweeps 1000 Hz, and a center at or below 0 becomes 1000 Hz | not challenged; the auditor's reading in `layer-7b-bus-stages.md` |
| L7b-11 | (read only) | The body bank and the other inserts keep computing on exact silence while a later stage's tail holds the cylinder | not challenged; the auditor's reading in `layer-7b-bus-stages.md` |
| L7b-12 | (read only) | The delay's ring passes through the subnormal range before it reaches zero | not challenged; the auditor's reading in `layer-7b-bus-stages.md` |
| L6-03 | minor | A cylinder's deactivation zeroes its delay ring and its reverb network inside the render block | Confirmed, smaller: The deactivation clear costs 32 us (0.25 s delay) and 70 us (reverb), 1 to 3 % of a block; only long delays are expensive (L7b-06) |
| L6-04 | (read only) | The duck's sidechain is another cylinder's mix before OR after that cylinder's own duck, by rent order | not challenged; the auditor's reading in `layer-6-channel-and-mix.md` |
| L6-05 | minor | With more than `maxCylinders` rented cylinders, the ones past index 254 are never visited by the cleanup | Confirmed: 256 cylinders: 1 never cleaned; 509: 254 never cleaned; negative ids reachable |
| L6-06 | note | The duck builds a new `Ducking` on the audio thread at the start of every life | Confirmed, smaller: One small `Ducking` (and its `knobArgs` array) per life |
| L6-09 | (read only) | Hard right is not exact: `cos(PI / 2)` leaves 6.1e-17 of the voice in the left channel | not challenged; the auditor's reading in `layer-6-channel-and-mix.md` |
| L6-10 | (read only) | The solo ramp's last sample is `from + (to - from)`, not `to`, and the two mix paths round differently | not challenged; the auditor's reading in `layer-6-channel-and-mix.md` |
| N-R1 | new | The first activation of a long delay allocates and zero-fills its ring inside the onset block | found in the disproof pass, `disproof-realtime.md` |
| N-R2 | new | The drain end of a long delay clears its ring twice in one block, after a switch-off that scans it | found in the disproof pass, `disproof-realtime.md` |
| S-02 | new | The compressor's detector reads a very quiet input as quieter than silence | found in the disproof pass, `disproof-sound.md` |
| S-03 | new | The vowel table names no source | found in the disproof pass, `disproof-sound.md` |

## The work

Per finding: read its report and its reproduction (`src/jvmTest/kotlin/audit/` in `audio_be`, `sprudel` or `klang`,
run with `KLANG_PROBE=audit`), fix it, and turn the reproduction's row into an ordinary spec that guards the fix
(mutation-checked). A finding that turns out to need a maintainer decision is not forced: it is listed under
"Decide" below with the options, and the rest of the ticket lands without it. How the fixes are made and reviewed:
`docs/audio-audit/2026-11-10/audit/FIX-BRIEF.md`.

## Decide

What this ticket did not force, each with the options and a recommendation. The reproduction of each stays in
`audio_be/src/jvmTest/kotlin/audit/` (run with `KLANG_PROBE=audit`).

1. **L7a-01, L7a-02: how long a switch-on's feed fade is.** Landed: the wet's own glide, 50 ms (one block for a
   modulated wet), the mirror of the switch-off's fade and of a return out of Draining. The price the auditor named:
   a note that switches the delay on over a bus that has run (an earlier note's tail, another pattern on the cylinder)
   feeds its own first 50 ms at a rising level, so its first echo loses some attack. Options: (a) keep 50 ms;
   (b) one block (2.7 ms): the disproof's control row reads -60.5 dB for a one-block ramp against -22.8 dB for the
   step, so it removes most of the click and keeps the attack; (c) fade only when the mix at the switch-on is not
   silent. Recommendation: (a), for one law on every edge of these two stages, unless the listening pair (a held pad
   and a snare with `delay` on one cylinder) says the snare's echo is too soft; then (b).
2. **L7a-07 and N-R1: a resource bound for the delay ring, and its first allocation in the onset block.** The ring
   ladder is open (`SizedBuffers` rule 1), so `katp("delay.time", 250)` (milliseconds meant) asks for 192 MB on the
   audio thread, and a first 10 s delay allocates 12.3 MB in its onset block (1.6 to 1.8 ms of a 2.67 ms block
   measured). In the worklet there is no other thread to allocate on. Options: (a) a named bound,
   `MAX_DELAY_SECONDS` (30 or 60 s), stated as a resource bound like `Compressor.MAX_LOOKAHEAD_SECONDS`, the time
   clamped to what the bound's ring holds; (b) rent the ring when the chain is built or a voice is scheduled (still
   the worklet's thread, but outside the render callback); (c) leave it open. Recommendation: (a) at 60 s, and
   accept the first allocation as a per-change cost below it; (b) only if a long delay is a real use.
3. **L7b-02 and S-03: the vowel table's third column, and its source.** The column is a formant BANDWIDTH in Hz in
   the published table it matches, and the code reads it as a q on purpose (`VowelBands.Band` KDoc: "legacy
   Q-peak convention"), so every formant is 10 to 22 Hz wide (measured). Options: (a) keep it, the sound the songs
   have; (b) q = freq / bandwidth, with `vowelGain`'s fold and `VOWEL_TAME` tuned again by ear (every `vowel(...)`
   moves; at the defaults the swing over a melody is 2.1 dB today, 17.8 dB at wet 1 floor 0); (c) a wider fixed q.
   The source: both auditors recall the Csound manual's formant table, from memory and without the network; nothing
   in the repo names it, so no credit was written. Recommendation: confirm the source, then credit it in `CREDITS.MD`
   and `src/jsMain/kotlin/pages/CreditsPage.kt` and name it in the `VowelBands` KDoc; then (b) with a listening pass.
4. **L7b-03: what `reverb.lowpass` means.** The docs now say what the code does (a damping on a scale in Hz, 0 the
   darkest, not a cutoff), at every door: `Reverb.lowpass`, the bridge's `KatalystStageDsl.Reverb`, sprudel's
   `reverb(lowpass = ...)`, `audio/ref/effects-mixing.md`. Options: (a) keep the law; (b) make it a real cutoff in Hz
   (the comb one-pole's corner at the value; a sound change for every song that writes it, `StrangerThings` 8000 and
   `SoundOfTheSea` 12500 among them), with a listening pass; (c) rename it to a 0 to 1 damping (a door change).
   Recommendation: (b): authors and songs write it as a cutoff, and the word says cutoff.
5. **L7b-04: the compressor's detector law.** Landed: the uncontroversial part (S-02, a level under -100 dB reads as
   -100 dB) and the law written down in the `Compressor` KDoc. The law itself smooths the LEVEL in dB before the gain
   computer: a dead time of 211 frames (4.4 ms) after silence at the defaults, 188 (3.9 ms) for the limiter preset,
   and a release of 24.5 ms into silence against 128.9 ms into -30 dBFS. Options: (a) keep it, the character every
   compressed song has; (b) smooth the gain REDUCTION instead (the gain computer on the instantaneous level, then
   attack and release on the reduction): no dead time, a release that does not depend on what follows; every
   compressed song moves, a listening pass and a credit to the design it follows; (c) hold the envelope at or above
   the knee's start: no dead time from silence, the release asymmetry stays, and the follower then reads the
   gain-computer knobs. Recommendation: (b), by ear first.
6. **L6-04, L7b-08: the order of chained ducks.** The duck pass walks the cylinders in rent order, so a duck that
   reads a cylinder which ducks itself reads it ducked or not depending on which pattern sounded first. Options:
   (a) every sidechain reads its source's mix BEFORE any duck writes (a copy of each named source's block, one block
   buffer per cylinder that is someone's sidechain); (b) a dependency order, sources first, cycles broken by id
   (DAW-like: the source's own duck is heard); (c) id order (deterministic, still not the author's). Recommendation:
   (a): it makes the order irrelevant and mutual ducks symmetric, for one copy per source per block.
7. **L7a-06: a skill says what the code does not do.** `.claude/skills/review-loop/audio-constraints.md` says
   `Reverb.combPeakAbs()` and `DelayLine.tapWindowPeakAbs()` run only in a swap's drain; every switch-off runs them
   too (the delay's since this ticket only up to a 1 s tap window, `KatalystDelayEffect.MAX_SCANNED_TAP_SECONDS`; the
   reverb's about 28k cells per switch-off). Fixers do not edit skills: the sentence needs its owner's correction.

### The maintainer's answers (2026-11-10)

- **3, the vowel table:** "Yes the vowels do not really sound well today... They need tweaking later." Not done now;
  it stays open for a later pass by ear.
- **4, `reverb.lowpass`:** "Yes make lowpass reverb a real cutoff." To do.
- **5, the compressor's detector law:** "Yes to the compressor change." To do: smooth the gain reduction, not the level.
- **6, the duck order:** "Ducking accepted." Read as: option (a) is accepted, every sidechain reads its source's mix
  before any duck writes. To do.
- **7, the skill sentence:** "Update the skill please." Done (`.claude/skills/review-loop/audio-constraints.md`).
- 1 (the fade's length) and 2 (the delay ring's bound) are not answered yet.

The three "to do" items run as a second pass of this ticket, after KLANG-00429 has landed (one build, one corpus
comparison at a time).

## Notes from the work (2026-11-10)

**Per finding.**

| Id | Outcome | What, where | Guard |
|---|---|---|---|
| L7a-01 | fixed, sound change (no corpus song) | `KatalystDelayEffect`, `KatalystReverbEffect`: a `fresh` flag (the compressor's model): out of Off the wet snaps only while no block has run since the build or reset; otherwise it glides up from 0 over its span, the mirror of every switch-off's fade | `KatalystSwitchOnFadeSpec` |
| L7a-02 | fixed, sound change (no corpus song) | `KatalystEffect.joinSoundingBus` (default nothing; delay and reverb clear `fresh`, `parallel` and a region pass it on), called by `Cylinder.beginFade` and `MasterBus.landChain` after the arriving chain's reset | `KatalystSwitchOnFadeSpec`; `MasterOutputChainSpec`'s two recovery rows now expect the fade (they pinned the snap) |
| L7a-03 | fixed, sound change (no corpus song) | `ChainSwap.Fading`: a leaving chain that `soundsOnSilence()` (new on `KatalystChain`, `KatalystParallelEffect`, `KatalystRegionEffect`; the arithmetic stages only, a delay at feedback >= 1 keeps decision (i)) goes into the 150 ms cut | `ChainSwapCapSpec` |
| L7a-04 | accepted, documented | the lookahead exception written into `audio/ref/katalyst.md`'s law table and `audio/ref/knob-glide.md` | none (a doc) |
| L7a-05 | fixed | `KatalystParallelEffect.Pad`: holds for the twins' tail plus the ring's length, counted on the branch's output | `KatalystParallelEffectSpec` |
| L7a-06 | partly fixed, rest under Decide 7 | the delay's switch-off scans at most a 1 s tap window (`MAX_SCANNED_TAP_SECONDS`; longer counts down from `TailCeiling.bound`); the reverb's comb scan stays | `KatalystDelayEffectSpec` |
| L7a-07 | Decide 2 | | |
| L7a-09 | accepted | a switched-off lookahead compressor runs its detector: rare (a lookahead stage with unset knobs), a cost only, the performance work comes after sound (`docs/tasks/_priorities.md`) | none |
| L7a-10 | pinned | the phaser's wet is noise ordinal 0 and its rate 1 | `KatalystKnobModulatorSpec` |
| L7b-02, S-03 | Decide 3 | | |
| L7b-03 | docs fixed, mapping under Decide 4 | `Reverb.lowpass` KDoc, the bridge's `Reverb` KDoc, sprudel's `reverb(lowpass)` text, `audio/ref/effects-mixing.md`: a damping on a scale in Hz, not a cutoff | none (a doc) |
| L7b-04 | documented, law under Decide 5 | the `Compressor` class KDoc states the dead time and the release's dependence | none (a doc) |
| S-02 | fixed, sound change (3 renders) | `Compressor.followEnvelope`: a level at or under 1e-5 (-100 dB) reads as -100 dB (`SILENCE_DB_LIN`); the lookahead path is unchanged | `CompressorSpec` |
| L7b-05 | fixed | `PhaserCore.maxModFreq`: 18 kHz, or 0.45 of the rate under 40 kHz; moved by `bindSampleRate` too; identical at 44.1 and 48 kHz | `PhaserCoreLawSpec` (kernel at 32, 24, 22.05 kHz; the voice node at 32 kHz; the oracle's clamp follows) |
| L7b-06, L6-03, N-R2 | fixed (the delay); the reverb's 23 us reset accepted | `DelayLine.reset` is O(1): `validFrames` counts what was written since, an older cell reads as zero and the cells the tap reaches are zeroed one block at a time (`zeroStale`); `adoptHistory` and `tapWindowPeakAbs` read the same way. Renders bit for bit as a zeroed ring | `DelayLineSpec`; `CylinderCleanupTest` and `KatalystDelayEffectSpec` read the ring through the line now (they pinned a literal whole-ring clear) |
| N-R1 | Decide 2 | | |
| L7b-07, L6-06 | fixed | `KatalystDuckEffect.spare`: one `Ducking` per stage, put at rest by `Ducking.startLife`; `takeOver` swaps the instances | `KatalystDuckEffectSpec` |
| L7b-08, L6-04 | Decide 6 | | |
| L7b-09 | fixed, door now as documented | `KatalystPhaserEffect.configure`: only a non-finite center or sweep is unset; 0 and negatives are raw | `KatalystSlotResolverSpec` (its row pinned the `> 0` fallback) |
| L7b-11 | accepted | the body bank runs on exact silence under a tail: a cost (58 ns per sample) for the performance work | none |
| L7b-12 | fixed, sound change (2 renders, under -300 dB) | `DelayLine`'s ring store takes `flushState` instead of `nanGuard` | `DelayLineSpec` |
| L6-05 | fixed | `Cylinders.processAndMix`: the cleanup index wraps at `max(count, maxCylinders)`; up to 255 cylinders the visits are what they were | `CylindersCleanupTest` |
| L6-09, L6-10 | accepted, KDoc | `voice_channel.kt` says both | none |

**The corpus, my own before against after** (no song text changed between the runs). Four renders moved, peaks
unchanged, each attributed by corpus runs with fixes taken out (without the switch-on glide, without S-02 and the
flush, without S-02 alone):

| Render | Moved by |
|---|---|
| Seltsamere Dinge (live) and frozen `strangerThings_2026_11_03` (one render) | S-02 |
| frozen piece `derSchmetterling_2026_09_16` | S-02 |
| Synthsturm | S-02 and L7b-12 |
| The Synthsale Piper's Last Rave | L7b-12 |

S-02 moves only what a compressor does after a passage under -100 dBFS; L7b-12 only ring cells under 1e-15. The
switch-on glide (L7a-01, L7a-02), the swap cut, the O(1) reset, the pad, the phaser and the cleanup moved nothing.

**Probes, before (the audit's tables) and after:**

| Row | Before | After |
|---|---|---|
| clicks: delay switch ON (wet .25) | kink 114.85, burst -27.5 dB | kink 1.50, burst -95.6 dB |
| clicks: reverb switch ON | kink 32.77, -54.5 dB | kink 1.04, -119.9 dB |
| clicks: swap into a room | kink 63.14, -49.1 dB | kink 1.05, -114.4 dB |
| clicks: swap into a delay (new row; before from `disproof-sound.md`) | kink 120.6, -22.8 dB | kink 1.99, -89.5 dB |
| framing: bus delay, reverb, phaser | identical, rounding, audible (block grid, by design) | the same |
| realtime: 10 s delay, deactivation block | 571 us | 77 us |
| realtime: 10 s delay, switch-off block | 596 us | 12 us |
| realtime: 10 s delay, drain end and deactivation in one block | 953 us | 83 us |
| realtime: `DelayLine.reset` alone, 12.3 MB ring | 505 to 555 us | 0.1 us |
| realtime: first activation of a 10 s delay (N-R1, open) | 1573 to 1781 us | 1879 us |

The realtime rows are `AuditRealtimeRepro` (JVM, 48 kHz); its two L6-03/L7b-06 tests stay, because they also carry
N-R1's row and the reverb's reset. The reproductions of L7a-01/L7a-02, L7b-05, L7a-03 and L6-05 are deleted; the
click table gains the row "swap into a delay".

**Mutation check** (`console/tools/mutate`, one campaign): 25 mutants, 25 killed for the expected reason, each by the
guard named above. One survived the first run (a grow after a reset, compared with a line holding the same stale
cells); the row now compares with a new line and kills it. **Suites** (test-digest, all green): `:audio_be:jvmTest`
3113, `:audio_be:jsBrowserTest` 2931, `:audio_bridge:jvmTest` 153, `:sprudel:jvmTest` 3523, `:klang:jvmTest` 82,
root `:jvmTest` 163.

## Acceptance

- Every row above is fixed with a guarding spec, or listed under "Decide" with its reason.
- The suites are green, the corpus render is identical except where a fix changes the sound on purpose, and each such
  change is named in this ticket with the songs it moves.
- The engine health page shows the fixed items green after a new probe run.

## History

- 2026-11-10: created.
- 2026-11-10: IN PROGRESS. Started.
- 2026-11-10: PAUSED. 22 of 29 findings fixed, pinned or accepted with a reason. Waiting for the maintainer: seven decisions (see Decide).
