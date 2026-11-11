# KLANG-00428: Audit fixes 4: the bus (switch-on and swap clicks, long delay rings, compressor, reverb lowpass, vowel, phaser, duck order)

> **Status (2026-11-11): DONE.** the feed fade, the delay bound, the warm rings, the compressor snap and the reverb's gliding damping are built; the vowel table is deferred by the maintainer
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
4. **DONE (second pass, option (b)).** `reverb.lowpass` is the -3 dB cutoff of the combs' one-pole, the same corner
   at every rate; unset stays Freeverb's pole, bit for bit. See "Notes from the second pass". The question as it was:
   **L7b-03: what `reverb.lowpass` means.** The docs now say what the code does (a damping on a scale in Hz, 0 the
   darkest, not a cutoff), at every door: `Reverb.lowpass`, the bridge's `KatalystStageDsl.Reverb`, sprudel's
   `reverb(lowpass = ...)`, `audio/ref/effects-mixing.md`. Options: (a) keep the law; (b) make it a real cutoff in Hz
   (the comb one-pole's corner at the value; a sound change for every song that writes it, `StrangerThings` 8000 and
   `SoundOfTheSea` 12500 among them), with a listening pass; (c) rename it to a 0 to 1 damping (a door change).
   Recommendation: (b): authors and songs write it as a cutoff, and the word says cutoff.
5. **DONE (second pass, option (b)).** The classic follower smooths the gain reduction (the smooth decoupled peak
   detector of the design the credits already name). See "Notes from the second pass". The question as it was:
   **L7b-04: the compressor's detector law.** Landed: the uncontroversial part (S-02, a level under -100 dB reads as
   -100 dB) and the law written down in the `Compressor` KDoc. The law itself smooths the LEVEL in dB before the gain
   computer: a dead time of 211 frames (4.4 ms) after silence at the defaults, 188 (3.9 ms) for the limiter preset,
   and a release of 24.5 ms into silence against 128.9 ms into -30 dBFS. Options: (a) keep it, the character every
   compressed song has; (b) smooth the gain REDUCTION instead (the gain computer on the instantaneous level, then
   attack and release on the reduction): no dead time, a release that does not depend on what follows; every
   compressed song moves, a listening pass and a credit to the design it follows; (c) hold the envelope at or above
   the knee's start: no dead time from silence, the release asymmetry stays, and the follower then reads the
   gain-computer knobs. Recommendation: (b), by ear first.
6. **DONE (second pass, option (a)).** Every sidechain reads its source's mix before any duck writes; a copy only for a
   source that is itself ducked. See "Notes from the second pass". The question as it was:
   **L6-04, L7b-08: the order of chained ducks.** The duck pass walks the cylinders in rent order, so a duck that
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
- **4, `reverb.lowpass`:** "Yes make lowpass reverb a real cutoff." Done in the second pass.
- **5, the compressor's detector law:** "Yes to the compressor change." Done in the second pass: the gain reduction is smoothed, not the level.
- **6, the duck order:** "Ducking accepted." Read as: option (a) is accepted, every sidechain reads its source's mix
  before any duck writes. Done in the second pass.
- **7, the skill sentence:** "Update the skill please." Done (`.claude/skills/review-loop/audio-constraints.md`).
- 1 (the fade's length) and 2 (the delay ring's bound) are not answered yet.

The three "to do" items run as a second pass of this ticket, after KLANG-00429 has landed (one build, one corpus
comparison at a time).

### Decided (maintainer, 2026-11-11)

- **The switch-on fade:** "yes, fade in only when already sounding". A stage that joins a silent bus starts at once;
  the 50 ms fade applies only when the bus is sounding. Owed.
- **The delay ring bound:** "ok as recommended" (`MAX_DELAY_SECONDS` 60), and "the warehouse should be warmed up to
  have delay rings available for normal songs". The warm-up shelves 16 rings of the smallest class today (half a
  second); rings of the next classes are owed, the count to be agreed.
- **A compressor knob move that relaxes it:** "as recommended, no ear test needed": snap the knob, the follower
  releases. Owed.
- **The listening pass** of the eleven moved renders: the maintainer listens.

### Decided, second round (maintainer, 2026-11-11)

- **Warm rings:** "we should increase the idle budget and have big rings ready. Multiple songs now use a delay of 9 up
  to 10 on the master." The songs in the repo carry a master REVERB of size 9 to 10 (its network is pooled and warm
  already, whatever the size); their delays are 0.05 to 0.6. Asked back which was meant; the ring counts wait for the
  answer.
- **The listening pass:** nothing owed; the maintainer revisits the songs.

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

## Notes from the second pass (2026-11-10)

The maintainer's answers 4, 5 and 6, landed one after the other with a corpus run after each, so every moved render is
attributed to one item. Sound changes on purpose, all three.

**A. `reverb.lowpass` is a real cutoff (Decide 4, L7b-03).** `Reverb.cutoffDamping`: the value is the -3 dB corner of
the one-pole in every comb's feedback path, the root of `p^2 - 2 (2 - cos w) p + 1 = 0` (written with `2 sin^2(w / 2)`
so a low corner keeps its digits), computed at the rate the room runs at, so the same Hz is the same corner at 44.1 and
48 kHz and inside a region. Two bounds, neither a taste: a one-pole has no corner above the Nyquist frequency, so a
cutoff above it is the Nyquist frequency (inside a region, the base rate's); a cutoff under 5 Hz is 5 Hz
(`Reverb.LOWPASS_FLOOR_HZ`, the engine's lowest filter cutoff, `clampSvfCutoff`'s), which keeps the pole under 1.
Unset keeps Freeverb's fixed pole 0.2 (a corner of 16.9 kHz at 48 kHz, 15.5 kHz at 44.1 kHz), bit for bit, so a song
that never sets it does not move. A dark cutoff lengthens every lap of the loop by the one-pole's lag `p / (1 - p)`;
without counting it, the darkest cutoff at size 10 ended the drain countdown with a comb cell at 1.65e-5 and the tail
ceiling let go at 1.05e-5, both over `SILENCE_FLOOR`. `Reverb.dampingLag` (0 unset) now lengthens the drain's
revolution and the ceiling's window (and its laps). No highpass or other reverb door in Hz exists; nothing else had
the defect. No wire change, no schema hash move.

T60 at size 5, 48 kHz, before and after (the audit's STFT method; "computed" where the band falls 30 dB inside about
two of its 85 ms frames, from the loop gain `feedback * |H(f)|` per mean comb lap plus the lag, a model that reads
within about 10 % of the measured cells):

| lowpass | 1 kHz before | 1 kHz after | 4 kHz before | 4 kHz after |
|---|---|---|---|---|
| unset (default) | 1.34 | 1.34 (identical) | 1.07 | 1.07 (identical) |
| 0 | 1.28 | 0.08 computed (the 5 Hz floor) | 0.79 | 0.06 computed |
| 1000 | 1.29 | 0.51 | 0.81 | 0.14 computed |
| 4000 | 1.31 | 1.16 | 0.87 | 0.48 |
| 20000 | 1.36 | 1.34 | 1.22 | 1.09 |

Guards: `ReverbLowpassLawSpec` (the corner is -3 dB at 44.1, 48 and in a 2x region; the Nyquist hold; the 5 Hz floor;
1000 dark and 20000 bright, 1000 no longer 0; the drain countdown at the darkest cutoff), `ClosedFormTailSpec` (the
ceiling at the darkest cutoff and size 10), `ReverbNetworkLawSpec` (its oracle pinned the old law `0.4 (1 - lowpass /
nyquist)`; it now writes the cutoff law, and gained a row inside a 2x region; its render is 8192 frames, so the 96 kHz
row's tail is there). Docs: `Reverb.lowpass`, `DEFAULT_DAMP`, `cutoffDamping`, `dampingLag`, the drain's and
`TailCeiling`'s proofs, the bridge's `KatalystStageDsl.Reverb`, sprudel's `reverb(lowpass)` (both texts),
`audio/ref/effects-mixing.md`, `audio/ref/katalyst.md`.

**B. The compressor smooths the gain reduction (Decide 5, L7b-04).** The classic path's curve reads each sample's
level (S-02's floor kept: under -100 dB reads as -100 dB), and the smooth decoupled peak detector of Giannoulis,
Massberg and Reiss (JAES 2012, already in `CREDITS.MD`) runs on its output: a held reduction that takes more at once
and releases at the release time constant, followed by a one-pole at the attack time constant. Not the level
follower's attack-release blend carried over: on the reduction, its +/-2 dB window around the target would have run
the last 2 dB of every release at about half the attack's speed. The lookahead topology, every door, knee, ratio, the
limiter preset and the house limiter are unchanged. Measured (48 kHz, the audit's reproduction, before and after):

| | before | after |
|---|---|---|
| defaults, 0 dBFS step after 0.2 s of silence: frames at gain exactly 1.0 | 211 (4.40 ms) | 0 |
| defaults: frames to -1 dB of gain | 239 | 9 |
| limiter preset: frames at gain exactly 1.0 | 188 (3.92 ms) | 0 |
| defaults, release from 15 dB: back within 1 dB, into silence | 21.1 ms | 273.6 ms |
| defaults, release from 15 dB: back within 1 dB, into -30 dBFS | 100.3 ms | 273.6 ms (the same) |
| limiter preset, release from 0.95 dB: within 0.1 dB, into silence / into -30 dBFS | 1.4 / 4.6 ms | 226.1 / 226.1 ms |
| defaults, steady gain on DC at -30, -20, -10, 0, +6 dBFS | 0, -0.5625, -7.5, -15, -19.5 dB | the same, to 4 decimals |
| limiter preset, steady gain on DC at 0, +6 dBFS | -0.95, -6.65 dB | the same |
| defaults, steady gain on a 1 kHz sine at -20, -10, 0 dBFS peak | -0.198, -6.558, -14.032 dB | -0.562, -7.492, -14.990 dB |
| limiter preset, steady gain on a 1 kHz 0 dBFS sine | -0.348 dB | -0.948 dB |
| defaults, 0 dBFS 1 kHz burst after silence: peak in the first 1 ms | 0.0 dBFS | -0.9 dBFS |
| defaults, the same burst: samples over the settled peak + 1 dB | 539 | 194 |
| limiter preset, the same burst: peak in the first 1 ms | 0.0 dBFS | -0.1 dBFS |

The static curve is unchanged (DC). The steady reduction on a sine is NOT unchanged: the level follower sagged
between the peaks (its release pulled toward the zero crossings' -100 dB), the reduction follower holds the peak's
reduction, so a sine now gets what the curve gives its peak. The release now means its time constant on the
reduction (the textbook meaning), so in effect it is much longer than the level follower's was into silence.

Guards (`CompressorSpec`): an onset after silence is reduced from its first frame, more each frame, and within 0.1 dB
after six attack time constants, at the defaults and the limiter preset; the release into silence and into -30 dBFS,
bit for bit alike; the steady curve on DC. Changed: the S-02 row runs at a threshold of -110 dB (under the knee both a
-120 dBFS tail and silence now ask for no reduction, so only a threshold under -100 dB shows the floor); "Compressor
reset clears state" compares bit for bit (its 0.01 tolerance could not see a held reduction that survived a reset).
`KatalystCompressorEffectSpec`: three rows pinned how the knob move lands on a memoryless curve and now read the new
truth, each with a note: the relaxing swell's 10 to 90 percent is 2.65 release time constants (1.36 before; the
knob's one-pole and the follower's release in a row, rendered 1200 blocks to settle), the gliding move is read three
attack time constants after the glide (the attack trails a 17 dB glide by about 1.7 dB), the limiter swap from 10 ms
after it. Docs: the `Compressor` class KDoc (the dead-time paragraph rewritten), `smoothReduction`, `levelDb`,
`processGliding`, `KatalystCompressorEffect`, `CompressorKnobMove`, `audio/ref/knob-glide.md`,
`audio/ref/effects-mixing.md` (also: attack and release are in seconds, not ms).

**C. Every sidechain reads its source before any duck writes (Decide 6, L6-04, L7b-08).** `Cylinders.processAndMix`
step 2: before a single duck runs, the mix of every source that is ITSELF ducked this block is copied into its
`Cylinder.preDuckMix` (a block buffer the cylinder owns, built and shelved with it, zeroed by its new life); each duck
then reads that copy, or the live mix of a source nobody ducks. The shortcut is exact: the duck pass writes only
through `Cylinder.processDuck`, which writes only its own cylinder's mix (`KatalystContext.mixBuffer`; the swap's blend
uses its own scratch), so a cylinder whose `processDuck` does not run this block (inactive, no duck, or a sidechain not
rented: `Cylinders.ducksThisBlock`, the pass's own three tests) leaves the pass with its mix bit for bit as it came. No
allocation in the block; one block copy per ducked source. A cylinder that ducks itself read itself sample by sample
before its own write already; it now reads the copy, the same numbers. Guards: `CylindersDuckOrderSpec` (two
cylinders that duck each other, in both rent orders, bit for bit; kick, bass ducked by the kick, pad ducked by the
bass, in three rent orders, the pad bit for bit the pad over an unducked bass).

**The corpus, my own runs** (no song text changed between them, HEAD the same, the song files untouched):

| Render | Moved by | Peak before, after |
|---|---|---|
| Kokon (`reverb(0.10, 9.5, 6500)`) | A | 0.7458, 0.7447 |
| Die Kirschblüte (4500) | A | 0.7480, 0.7468 |
| Der Schmetterling (6500) | A | 0.7526, 0.7525 |
| Gestrandet (`lowpass = 12500`) | A | 0.0695, 0.0694 |
| Seltsamere Dinge and frozen `strangerThings_2026_11_03` (8000; one render) | A, then B | 0.7510, 0.7510, then 0.7456 |
| Stein um Stein (5500) | A | 0.7605, 0.7606 |
| A Synth Worth Lying For (4500) | A | 0.7555, 0.7555 |
| frozen `derSchmetterling_2026_11_03` | A | 0.7446, 0.7446 |
| frozen piece `derSchmetterling_2026_09_16` (3500) | A, then B | 0.9143, 0.9143, then 0.8994 |
| Synthsturm | B | 0.8540, 0.8272 |

B's renders, peak and RMS without and with B (the whole 256-cycle render, a temporary harness):

| Render | Peak | RMS |
|---|---|---|
| Seltsamere Dinge (and its frozen twin) | -0.06 dB | -16.73 to -17.13 dBFS, -0.39 dB |
| Synthsturm | -0.28 dB | -17.52 to -18.56 dBFS, -1.04 dB |
| frozen piece `derSchmetterling_2026_09_16` | -0.14 dB | -17.90 to -18.39 dBFS, -0.50 dB |

The other compressed songs did not move: their compressors have a lookahead (the master limiters; that path is
unchanged) or never reach the knee. C moved nothing (no corpus song ducks).

**Left for the maintainer from the second pass** (none forced):

- **The compressor's knob move and the new follower.** A relaxing knob move still releases its knobs at the release
  time (`CompressorKnobMove`, by ear 2026-10-17), and now the follower releases the reduction after it: the swell is
  two one-poles in a row, 10 to 90 percent in about 2.65 release time constants (1.36 before). Options: (a) keep;
  (b) on the classic path a relaxing move snaps its knobs and the follower's release carries the swell, exactly "as
  when the input falls", the 50 ms glide kept for adding moves and as the floor under a release shorter than it;
  (c) keep the move and halve its time. Recommendation: (b), by ear on the listening pair of 2026-10-17.
- **The songs' compressor and reverb numbers were tuned by ear under the old laws.** A release now means its time
  constant on the reduction (a 15 dB reduction is back within 1 dB after 274 ms at release 0.1, against 21 to 100 ms);
  a `reverb` lowpass of 4500 is now a dark room. A listening pass over the eleven moved renders, and new values where
  a song wants its old sound (Kokon, Die Kirschblüte, Der Schmetterling, Gestrandet, Seltsamere Dinge, Stein um
  Stein, A Synth Worth Lying For, Synthsturm, the frozen pieces stay as frozen).
- **The unset damping stays a pole, not a number in Hz** (16.9 kHz at 48 kHz, 15.5 kHz at 44.1 kHz), so no song
  moves. Options: (a) keep; (b) one cutoff in Hz at every rate (16.9 kHz: nothing moves at 48 kHz beyond rounding,
  every 44.1 kHz render slightly). Recommendation: (a).
- **The cutoff's floor is the engine's filter floor, 5 Hz**, with the tail accounting stretched by the lag. A room
  under about 70 Hz holds its cylinder for up to about twice as long (the lag at 5 Hz is about one longest comb).
  Options: (a) keep; (b) a higher floor where the lag is negligible (about 72 Hz, the pole whose memory of one lap is
  `SILENCE_FLOOR`), with the accounting left as Freeverb had it. Recommendation: (a), raw.
- **A modulated `reverb.lowpass` steps the pole once per block.** It did before too, but the new law moves the pole
  much further per Hz (1000 to 16000 Hz is a pole of 0.88 to 0.21 at 48 kHz, was 0.38 to 0.13). Not measured for clicks; the
  glide table says "measured inaudible" for the old law only.

**Mutation check** (`console/tools/mutate`, one campaign of 14, two rerun): 14 of 14 killed for the expected reason.
A1 the old linear law (`ReverbNetworkLawSpec`), A2 no Nyquist hold, A3 no 5 Hz floor, A4 the drain without the lag
(`ReverbLowpassLawSpec`), A5 the ceiling's window without the lag (`ClosedFormTailSpec`), A6 a region's corner at the
base rate (`ReverbNetworkLawSpec`, the 96 kHz row), A7 the lag for an unset room too (survived the first run: the
drain-arithmetic row never ran a block; `ReverbLowpassLawSpec` now runs blocks first and kills it); B1 the level
follower back, B2 a held stage that does not take more at once (a dead time again: the first frame's reduction is
under the skip threshold), B3 S-02's floor gone, B4 a reset that keeps the held reduction (`CompressorSpec`; the reset
row compares bit for bit now); C1 no pre-duck copy, C2 the shortcut taken the wrong way (no source counted as ducked),
C3 the copy flag never cleared, a stale copy read the next block (`CylindersDuckOrderSpec`).

**Cost** (`KLANG_PROBE=cost`, JVM, ns per sample net of the empty chain, before and after; under 10 percent is noise):

| Row | Before | After |
|---|---|---|
| reverb default (wet .25 size 5) | 35.01 | 36.43 |
| reverb wet 1 size 10 | 34.99 | 36.36 |
| reverb wet 1 size 10 lowpass 20000 (one `sin`, one `sqrt` per block) | 34.30 | 36.93 |
| compressor default | 23.57 | 17.52 (no blend's smoothstep per sample) |
| compressor thr -60 ratio 100 knee 0 | 24.95 | 21.56 |
| limiter default | 23.92 | 20.64 |
| compressor lookahead 0.05 (path unchanged) | 37.32 | 38.41 |
| limiter lookahead 0.005 (path unchanged) | 31.94 | 33.05 |
| duck self-trigger, duck depth 1 | 5.39, 5.34 | 5.22, 5.34 |

No row allocates per block (net_B 0 before and after). The duck rows run the stage with its own sidechain buffer, not
`Cylinders`, so they cannot show the pre-duck copy: one block copy (256 doubles) per ducked source per block.

**Suites** (test-digest, all green, after the last change): `:audio_be:jvmTest` 3128, `:audio_be:jsBrowserTest` 2950,
`:audio_bridge:jvmTest` 160, `:sprudel:jvmTest` 3523, `:klang:jvmTest` 83, root `:jvmTest` 163. `docs-lint`: 0
problems. The reproductions of L7b-03 and L7b-04 are deleted from `AuditSoundStagesRepro` (their numbers are above);
L6-04 and L7b-08 had none.

## Decisions pass B1 (2026-11-11)

What the fix worker of pass B1 built of the decisions of 2026-11-11 (the vowel table stays open).

**The switch-on fade only over a sounding bus, BUILT.** A delay or a reverb switched on out of Off snaps its wet while
fresh (as before) or while its host judges the bus SILENT (`KatalystEffect.busSilent`, new, told by the host when the
judgement changes or another chain enters service; `parallel` and a region pass it on); over a sounding bus it glides
up from 0 as before. A cylinder judges with the tests it already makes (`Cylinder.judgeBus`, once per block before the
owner's writers): it SOUNDS when its last silence visit (`tryDeactivate`, the `SILENCE_FLOOR` scan it ran anyway) found
its mix sounding, or when a voice checked in during the block before (the onset block's own note does not count, so a
note that switches a delay on over silence keeps its first echo's attack). No sample is read for it. A visit comes
once per rented cylinder in turn, so its answer can be a few blocks old: a stale "sounding" fades in (the safe side),
and a voice that started since the visit counts through the check-in. A swap's arriving chain is told the cylinder's
current judgement. The output (`MasterBus`) is unchanged: its chain reads authored defaults only, so a stage there
switches on at its first block or under a modulated knob (a one-block ramp); a judgement there would have had no
reachable case to guard (built first, then taken out again). Guards: `KatalystSwitchOnFadeSpec`, through a real
cylinder with one visit per block: after ten silent blocks a delay and a reverb snap to their wet (0.5); with the
tone's voice in the block before, with a tail and no voice (the visit found sound), and with a voice that started after
the last silent visit, they glide (0.5 / 17 after one block). Mutants (KILLED for the expected reason): the bus always
judged sounding; the delay, and the reverb, ignoring the judgement; the visit never reporting sound; the voice
check-in not rolled into the next block. Probes: the click table's switch-on rows run a chain with no host and a
held tone, so they keep the fade: delay switch ON kink 1.50, burst -95.6 dB; reverb switch ON 1.04, -119.9 dB; swap
into a room 1.05, -114.4 dB; swap into a delay 1.99, -89.5 dB; all identical to before this pass. No corpus song moved
by it (a run with the judgement taken out: identical hashes).

**The delay ring bound, BUILT (option (a), 60 s).** `KatalystDelayEffect.MAX_DELAY_SECONDS` = 60: a time above it
asks for the ring a 60 s time rents (`framesNeeded`), never more; the tap of a longer time is then held to what that
ring holds by `DelayLine`'s existing clamp (the one a refused grow relies on), so the echo comes at the class's length,
about 64 s at 48 kHz (49 MB). No new clamp on the parameter: the time stays what was written, the ring is bounded.
Said where the doors name the time: `KatalystDsl` (`@param time`), the script `delay` door and its slot
(`klangscript-libs`), sprudel's `delay(time)` (both texts), `audio/ref/effects-mixing.md`. Guard: `LazyRingSpec`, "a
time past MAX_DELAY_SECONDS asks for the bounded ring" (it pinned the open ladder's overflow path, 60000 s asking for
`Int.MAX_VALUE`; moved), 60.5, 250, 60000 and 1e300 s ask for 128 base rings, 60 s too, 30 s for 64. Mutant: the
bound taken out, KILLED.

**Warm rings, BUILT.** The warm-up stocks four rings of the 1 s class and two of the 2 s class beside its sixteen of
0.5 s (`WarmupRunner.WARM_RINGS_ONE_SECOND`, `WARM_RINGS_TWO_SECONDS`; `ResourceWarehouse.stockDelayRings`, a rent and
a give-back, after the warm-up engine's disposal so its own cylinders never took one): 12.3 MB idle at 48 kHz in all,
inside `SHELF_BUDGET_BYTES` (32 MB, unchanged; at 192 kHz the budget drops the largest first), zeroed by the same
housekeeping before `BackendReady` (32 slices instead of 16; the cap on the wait is 64). `SizedBuffers` rule 2 names
the exception. Guard: `CylinderShelfSpec`, after the warm-up the shelf holds 22 clean rings inside the budget, and a
song's first 0.8 s and 1.5 s delays rent the 1 s and 2 s classes without an allocation or a clean on the spot; the
first row's shelf count moved with it. Mutant: the 2 s stock taken out, KILLED.

**A relaxing compressor move snaps, BUILT (classic path; the lookahead path never used the move).** A move whose net
effect at 0 dBFS is less reduction now snaps its knobs, and the reduction follower carries the swell at the release
time constant, so the release is paid once; under a release shorter than the 50 ms glide it glides (the floor of the
second-pass note). `CompressorKnobMove`'s release law (`RELEASING`, the fraction, `LANDING`) is gone. On DC (threshold
-40 to 0, ratio 8, release 0.2 s) the swell's 10 to 90 percent is ln(9) release time constants, 0.44 s (2.65, 0.53 s,
before). Guards, `KatalystCompressorEffectSpec`: its knob-law oracle writes the snap law; the swell row (threshold in
force snapped in the change's block, ln(9) time constants, no step over 0.0041 dB per sample); the net-effect row; the
modulated-knob row (a relaxing move landed one block after the change, an adding one still travelling); release 0 and
5 ms still take the glide. Mutants: the snap never taken, the glide floor taken out; both KILLED. Click probe, the
relaxing rows (bursts unchanged at -110 dB, kink 1.00): threshold -40 to -20 rise 249.6 to 126.7 ms, settle 306.0 to
149.6 ms; ratio 20 to 4 rise 232.9 to 45.8 ms, settle 251.1 to 55.1 ms; every other compressor row identical. No
corpus song moved by it (a run with the snap taken out: identical hashes; the corpus' compressors are lookahead ones
or do not move their knobs).

**Cost** (`KLANG_PROBE=cost`, JVM, net ns per sample, before and after this pass; the median row 0.99): delay 25.91 to
24.88, reverb default 36.92 to 35.25, compressor default 17.04 to 17.05, lookahead compressor 38.39 to 37.69, limiter
20.61 to 20.78; no row over 10 percent, no row allocates per block.

## Acceptance

- Every row above is fixed with a guarding spec, or listed under "Decide" with its reason.
- The suites are green, the corpus render is identical except where a fix changes the sound on purpose, and each such
  change is named in this ticket with the songs it moves.
- The engine health page shows the fixed items green after a new probe run.

## History

- 2026-11-10: created.
- 2026-11-10: IN PROGRESS. Started.
- 2026-11-10: PAUSED. 22 of 29 findings fixed, pinned or accepted with a reason. Waiting for the maintainer: seven decisions (see Decide).
- 2026-11-10: IN PROGRESS. Second pass: the maintainer's answers 4, 5, 6.
- 2026-11-10: PAUSED. Second pass landed (answers 4, 5, 6). Open: the fade's length, the delay ring's bound, the vowel table, and the second pass's five notes.
- 2026-11-11: DONE. the feed fade, the delay bound, the warm rings, the compressor snap and the reverb's gliding damping are built; the vowel table is deferred by the maintainer
