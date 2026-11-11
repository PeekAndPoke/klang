# KLANG-00432: Audit fixes 8: the per-note stages (the filter envelope's grid, fastExp, the doors' soft cap, non-finite samples, the house stage)

> **Status (2026-11-11): DONE.** the filter envelope's knots, the one distort law, the cutoff clamp, the crushed count are built
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
| L4-02 | minor | The filter envelope is read at a block's two ends, and every sprudel filter envelope reaches it | Confirmed, smaller: Mechanism, a stale ledger line and SEVEN songs confirmed; the per-note variation is -40 to -63 dB re the peak in the songs' own shapes; the grid fix at N=128 is invariant but no more accurate |
| L4-04 | (read only) | `exp` and `stompbox` call `pow` per sample once the drive pushes the argument past 22 | not challenged; the auditor's reading in `layer-4-note-stages.md` |
| L4-05 | (read only) | The doors' soft cap is the alias source: a near-hard knee from 0.95 to 1.10 after every `Shape` | not challenged; the auditor's reading in `layer-4-note-stages.md` |
| L4-06 | minor | Shimmer stores a NaN in its grain ring and plays it again | Confirmed: one NaN in, two NaN out (frames 4800 and 13599, 0.183 s later) |
| L4-07 | minor | `coarse` turns one infinite sample into `factor` infinite samples | Confirmed: one infinity in, `factor` infinities out (4 and 4800) |
| L4-08 | minor | Five answers to one bad sample: the layer has no rule for a non-finite SAMPLE outside the arithmetic nodes | Confirmed: the rows I measured or read in the probe table match |
| L4-10 | (read only) | The cutoff clamp sits 1 Hz under Nyquist, where the filter is a resonator at Nyquist | not challenged; the auditor's reading in `layer-4-note-stages.md` |
| L4-11 | minor | The bare `drive` node emits infinity and NaN from finite input | Confirmed: `drive` 257 and up: NaN on every exact-zero input sample (silence), infinity elsewhere; 256 is finite |
| L4-01 | minor | The saturating SVF stops decaying once its state is large: more damping means a slower decay | Confirmed, smaller: the hold law matches the engine within 3 percent, but no ordinary signal reaches the slow region: it takes a transient 60 to 80 dB over full scale |
| N1 | new | The analog SVF oscillates for good near fs/3: q 200 and analog 5 at 16 kHz ring at full scale after the input stops | found in the disproof pass, `disproof-safety.md` |
| L8-01 | minor | A huge finite sample on the mix becomes up to seconds of full-scale DC: the DC blockers carry it and the 20:1 limiter cannot bring it under the clip | Confirmed, smaller: numbers confirmed (1e15 for one block: 26,783 rail samples, clean after 1.13 s); only extreme raw values put such a block on the mix |
| L8-02 | minor | A huge constant on the mix absorbs every playback's music: steps of 0.125 at 1e15, exact silence above 9e15, and the DC blocker hides the cause | Confirmed, smaller: 1e15 crushes every playback's music (error -15.4 dB), 1e17 gives exact zeros; same reach as L8-01 |
| L8-06 | (read only) | The 16-bit edge truncates toward zero and has no dither | not challenged; the auditor's reading in `layer-8-master-and-house.md` |
| L8-07 | (read only) | The house DC pole is a fixed coefficient, so its corner moves with the sample rate, and the comment beside it is wrong | not challenged; the auditor's reading in `layer-8-master-and-house.md` |

## The work

Per finding: read its report and its reproduction (`src/jvmTest/kotlin/audit/` in `audio_be`, `sprudel` or `klang`,
run with `KLANG_PROBE=audit`), fix it, and turn the reproduction's row into an ordinary spec that guards the fix
(mutation-checked). A finding that turns out to need a maintainer decision is not forced: it is listed under
"Decide" below with the options, and the rest of the ticket lands without it. How the fixes are made and reviewed:
`docs/audio-audit/2026-11-10/audit/FIX-BRIEF.md`.

## Decide

What landed is in "Notes" below. These need the maintainer; each with its options, its cost and a recommendation.
The reproductions of every finding listed here stay in `audio_be/src/jvmTest/kotlin/audit/`.

**L4-02, the filter envelope read at a block's two ends** (NOT landed). Options, measured by the disproof
(`disproof-sound.md`, L4-02 (b) and (c)) and read against the code:

- (a) Accept and record it: E3 closed as accepted, the pin `BlockFramingInvarianceSpec` "E3 KNOWN DEFECT" flipped to
  an "accepted" row, the stale ledger line of KLANG-00156 ("latent: no production door passes an env here") updated
  (another ticket, not edited here). Cost 0. The songs' own envelope shapes vary by -40 to -63 dB re the peak from
  note to note (-56 to -77 dB RMS); the deviation from the exact curve is part of what was tuned by ear.
- (b) Split the chord at the envelope's breakpoints inside a block (attack end, decay end, gate, release end): a short
  attack is no longer erased and no breakpoint is straightened. Up to 4 extra coefficient sets per note, nothing per
  sample; the six loops of `SvfIgnitor.generate` run per segment. Still framing-dependent between breakpoints.
- (c) A grid anchored to the note (knots every 128 frames plus the breakpoints, the running coefficients kept across
  blocks): bit-identical at every onset, but only at `analog` 0 with a block-constant cutoff and q (the drift steps
  per block by design), and NOT more accurate than today (-18.4 dB re the peak against exact, today -15.9). 20 percent
  fewer coefficient sets, a loop split at almost every block, a re-aim whenever the cutoff, q, drift or a realtime
  gate moves. Moves each note of seven songs by about -44 dB RMS. Most of those voices run `analog > 0`
  (`IrishLamentTechno`, the `StrangerThings` melody, `Tetris`, `TetrisRemix`), where (c) buys no invariance.
- (d) A finer grid (N 64 or 32) for accuracy: 1.6 to 3.1 times today's coefficient work, plus (c)'s machinery.
- (e) The exact per-sample cutoff: about +15 to +40 ns per sample against 3.9 ns for the whole filter.

Why not landed: (b) to (d) are a rewrite of the SVF node's six loops into segment loops with state across blocks,
in the one `generate` body whose V8 inlining budget its KDoc guards (engine follow-ups 29 and 31); the complexity rule
says stop and ask. Recommendation: (a), and (b) only if the bite of a sub-3 ms filter attack is wanted by ear (the
listening task of `layer-4-note-stages.md`). Whoever builds (b) to (d): the cost table has no filter-envelope row yet
(`_stage_cost_cases.kt`: `lpf=1000 + adsr` is the amp envelope); add one (`lpf.env` with a sub-block attack) and run
it on HEAD and the tree before landing.

**L4-08, one rule for a non-finite SAMPLE at a stage that is not arithmetic.** Today one infinity leaves `Times` and
the tremolo as 1e15, `CrushCore` as 1.0, the clipping distort shapes as 1, the folding ones as 0, the shimmer ring as
2, the one-pole as 0, the SVF and the DC blocker as itself for one or two samples, `Drive` and `Adsr` raw (an
infinity at a gain of exactly 0 becomes NaN). Options:

- (a) What landed (free): a stage that STORES a sample for later stores a non-finite one as 0 (the coarse hold, the
  shimmer ring); every other stage stays as it is, documented in `audio/ref/numerical-safety.md`, and the house stage
  counts what reaches it ("poison seen"). Cost 0 per sample.
- (b) Filters write their output AFTER the state's flush (the one-pole's order) for the SVF and the DC blocker: one
  bad sample leaves as 0, not as itself. Cost about 0, but `flushState` also zeroes values under 1e-15, so every
  filter tail's last bits change (sub -300 dB) and the corpus moves on every song.
- (c) `finiteOrZero` at every stage's output: an abs and a compare per sample per stage (about 0.3 to 1 ns each);
  against the decision of 2026-11-11 (no per-sample finite guard in the inner math).
- (d) One guard at the voice's exit: decided against with L6-01 (a).

Recommendation: (a), the table above into the ref page once the maintainer agrees it is the rule.

**L4-01, the saturating SVF holds a huge state.** Options: (a) accept (documented in the polynomial's KDoc now): no
ordinary signal reaches it, it takes a transient 60 to 80 dB over full scale; (b) evaluate the polynomial on a bounded
argument (two compares per sample in the saturated loops, about 17 ns per sample today, a sound change only above
the bound): the node's own curve, not a parameter clamp, but a new number to choose. Recommendation: (a).

**L4-05, the doors' soft cap knee (0.95, flat from 1.10).** A sound decision (D2): (a) keep; (b) a wider knee (0.5),
which moves the three songs that render through the doors' `Shape`; (c) the cap only where the DC blocker's
overshoot can occur; (d) ask whether those three songs still need the old door law at all, and if not give the doors
the fused law (2.6 times cheaper, 45 to 100 dB less alias on smooth shapes). Recommendation: (d), by ear.

**L4-10, the cutoff clamp at `fs/2 - 1`.** An existing clamp's value: (a) keep; (b) `0.49 * fs`: the Nyquist ring
of 0.225 s (Butterworth) to 64 s (q 200) becomes 22 samples to 0.13 s, under 0.001 dB at 20 kHz. A sound change at
the bit level for every sweep that reaches the clamp (not measured on the corpus here). Recommendation: (b).

**L4-11, `drive` from 257.** Decided with N2 (2026-11-11): documented, not clamped (`numerical-safety.md`, "Four
extreme raw values"). The reproduction row stays.

**L8-01 and L8-02, a huge FINITE value on the mix.** Not counted by "poison seen" (it is finite). Options:

- (a) Accept (raw values only reach it).
- (b) The house DC blockers flush their STATE above a bound (the same compare `flushState` makes, another constant;
  free per sample): at `SAFE_MAX` it bounds the rail after one bad block to about 0.6 s (today up to 14.7 s at
  1.8e308); at a low bound such as 1e3 (+60 dBFS) one bad block costs one block plus the limiter's 100 ms release.
  Changes nothing a musical mix reaches. L8-02's crushed music is NOT fixable at the house stage: the sum
  `1e15 + m` has already rounded the music to steps of 0.125 in the mix.
- (c) Make it visible for free: once per block the house stage reads the limiter's gain reduction and counts a block
  where it sits past -200 dB (a level no music reaches), beside "poison seen".

Recommendation: (b) at 1e3 for L8-01 and (c) for both.

**L8-06, the 16-bit edge truncates and has no dither.** Kept bit for bit on purpose (`_pcm16_edge.kt`). Options: (a)
keep; (b) round to nearest (half the peak error, no error term that follows the sign); (c) TPDF dither from a seeded
generator (a fading tail turns into noise, not a cut). Neither moves the corpus (it hashes the doubles); both change
every WAV render's lowest bit and `Pcm16EdgeSpec`. Recommendation: (b), (c) as an export option.

### Decided (maintainer, 2026-11-11)

- **L4-02:** "No need to pay respect to the songs, they need retuning anyway. I am interested in the correct yet
  performant solution." Not accepted as it is: a design with measurements first (accuracy against the exact curve,
  cost on JVM and JS), then the build. Built in decisions pass C.
- **L4-08:** "as recommended": a stage that stores a sample stores a non-finite one as 0, everything else stays raw,
  the house stage counts. It is the rule; the table goes into `audio/ref/numerical-safety.md`. Owed (the text).
- **L4-01:** "accepted". The maintainer asked whether warehousing the state's memory would win anything: no, the
  state is two numbers inside the filter node, there is no buffer to hold.
- **L4-05:** "no need to pay attention to the builtin songs. Let us use the new implementation and drop the old one":
  the doors take the fused law, the old door law goes. Built in decisions pass C.
- **L4-10:** "as recommended": the cutoff clamp at 0.49 of the sample rate. Owed.
- **L8-01, L8-02:** "as recommended" and "ok": the house DC blockers flush their state above 1e3, and the blocks where
  the limiter's gain reduction sits past -200 dB are counted beside "poison seen". Owed.
- **L8-06:** "as recommended": round to nearest; dither as an export option later. Owed.

## Notes

What landed (fix worker, 2026-11-11), each with its guarding spec, mutation-checked (10 mutants, all KILLED for the
expected reason):

- **N1, FIXED, no ordinary sound change.** The saturated lowpass and highpass floor their effective damping at 0
  (`IgnitorFilters.kt`; the polynomial's KDoc corrected). Bit-identical wherever the damping is positive, which is
  every setting with q times analog under about 590. The fs/3 ring (0.795 RMS at 16 kHz, q 200, analog 5, ten seconds
  on) is gone: under 1e-9 one second after the input. Guard: `SvfNodeLawSpec`, "analog SVF near fs/3".
- **L4-06, FIXED.** The shimmer stores a NaN in its grain ring as 0 (the second compare reads `!(write >= -2.0)`, no
  third compare on the common path): one NaN in, one NaN out (was two, 0.183 s apart). Guard: `ShimmerSchedulerSpec`.
- **L4-07, FIXED.** The coarse hold takes `finiteOrZero`: an infinity is held as 0, not for `factor` frames. Guard:
  `CoarseLawSpec`. `audio/ref/off-values.md`'s coarse row follows.
- **L4-04, FIXED, bit-identical.** `expClip` and `stompBox` return the rail past an argument of 38 (`EXP_SATURATED_ARG`),
  where `1 - e^-x` is exactly 1.0. Cost at amount 3 (median of two interleaved HEAD and tree runs, normalised by the
  median row): Ignitor `exp` 38.97 to 7.03 ns, `stompbox` 38.83 to 7.29; Katalyst `exp` 77.85 to 13.66, `stompbox`
  77.24 to 14.43. Amount 0.5: 1.02 of HEAD (noise). Guard: `ShapingFuncsBoundsSpec`, bit for bit against the formula.
- **L8-07, FIXED.** The house DC pole is `1 - 48 / fs` (`houseDcBlockCoeff`): exactly 0.999 at 48 kHz, a 7.64 Hz
  corner at every rate (was 7.0 Hz at 44.1 kHz, 15.3 Hz at 96 kHz); the comment beside it said otherwise. A sound
  change below 20 Hz at rates other than 48 kHz only (the corpus renders at 48 kHz). Guard: `HouseStageSpec`.
- **L4-08, the free part.** A stage that stores a sample for later stores a non-finite one as 0 (L4-06, L4-07); the
  rule for the rest is under Decide.
- **L4-11 (with N2), DOCUMENTED.** `audio/ref/numerical-safety.md`, "Four extreme raw values that end in NaN".
- **"Poison seen" (KLANG-00425 L8-04 (c)), BUILT.** The house limiter's ring write tests each sample as before
  (written out from `finiteOrZero`); on its rare branch it counts the non-finite ones (`Compressor.nonFiniteSamples`,
  an Int), `HouseStage` moves that into its monotone Double total once per block, and every diagnostics report
  carries it (`Diagnostics.nonFiniteSamples`, Double). One bad sample on the mix reads as 2 per channel (the DC
  blocker passes it twice). Cost: the lookahead limiter row 1.01 of HEAD (noise). No allocation. The wire schema
  hash moved (pin in `WireCodecRoundTripSpec`). Guards: `HouseStageSpec` (exact counts), and
  `PlaybackEngineDispatcherDiagnosticsTest` (a sample with one NaN through the dispatcher). Showing it is KLANG-00407.

Probes, HEAD (a throwaway worktree) against the tree:

- Stage probe: exactly four of 1411 lines moved, the intended ones. `coarse factor=4 +inf`: 4 non-finite out, now 0;
  `coarse factor=4800 +inf`: 4800, now 0; `shimmer default nan` and `shimmer wet=1 feedback=0.95 nan`: 2 non-finite
  out and finite again 0.1833 s after the poison, now 1 and 0.0. No filter row moved.
- Cost probe: two interleaved pairs, normalised by the median row (machine load moved every row by up to 8 percent
  between runs). The analog lowpass and highpass rows 0.99 of HEAD, the lookahead limiter 1.01, shimmer 0.97 to 0.99,
  coarse under 2 ns (noise), `exp` and `stompbox` as above.
- Audit repro (tree): the L8-01, L8-02, L4-01, L4-08 and L4-11 rows read exactly the disproof's numbers (the house
  stage is unchanged at 48 kHz).

Suites (test-digest, each exit 0): `:audio_be:jvmTest` 3146 tests, `:audio_be:jsBrowserTest` 2968,
`:audio_bridge:jvmTest` 160, `:audio_bridge:jsBrowserTest` 299, `:klang:jvmTest` 90, root `:jvmTest` 173; 0 failed.
Corpus: run before the first edit and after the last, all 18 rows byte-identical (sound hash and peak); no fix
changes the sound of a corpus song (none has q times analog near 590, none carries a non-finite sample, the corpus
renders at 48 kHz). The check's RED rows are the same before and after (its reference predates other sessions' song
edits). No engagement control was run for the corpus; the ten mutants are the engagement of the specs.

The wire schema hash moved from 212461444 to 358149839 (`Diagnostics.nonFiniteSamples`), pinned in
`WireCodecRoundTripSpec`.

The audit reproductions of L4-06, L4-07 and N1 left `AuditSafetyRepro` (ordinary specs guard them now); L4-01,
L4-02, L4-08, L4-11, L8-01 and L8-02 stay.

## Decisions pass B1 (2026-11-11)

What the fix worker of pass B1 built of the decisions of 2026-11-11 (L4-02, L4-05 and L8-02's count belong to other
passes).

- **L4-10, BUILT, a sound change.** `clampSvfCutoff`'s top is `SVF_CUTOFF_MAX_SHARE * fs`, 0.49 (it was `fs / 2 - 1`):
  the one home, read by the SVF node, the one-poles (through `bilinearK`), the EQ sections and the resonator bank.
  Every text that named it moved: the fourteen `@param cutoffHz` lines of `IgnitorFilters.kt` ("[5, 0.49 * fs]"),
  `BandsBuilders.kt` in `audio_bridge`, the stage probe's labels ("freq=23999 (over the 0.49 fs clamp)": the same
  input, so the rows compare with older runs), the comments of `EqCoreSpec` and `IgnitorFilterEnvSemitoneSpec`.
  Guards: `SvfNodeLawSpec`, "a cutoff at the top of the range is 0.49 of the rate": a unit step into a q 200 highpass
  asked for 23999 Hz, 0.49 fs and 1e9 at 48 kHz rings and decays (half a second later under a tenth of its early
  peak; at `fs / 2 - 1` the ring hardly decays and starts tiny, 6.5e-5); `ResonatorBankSpec`, the clamp's own band is
  0.49 fs, bit for bit for every cutoff above it. `IgnitorFilterEnvSemitoneSpec`'s clamp row pinned "near silence" for
  a highpass at the clamp; at 0.49 fs it passes the top 2 percent of the band, so the row now pins the semitone law
  through the clamp bit for bit (a static highpass asked for 1e9 Hz) and less than half the unclamped filter's peak.
  Mutant: the clamp back at `fs / 2 - 1`, KILLED. It moves four corpus renders (below).
- **L8-01, BUILT, no musical change.** The house DC blockers drop their feedback state above 1e3
  (`HouseStage.HOUSE_DC_STATE_BOUND`, through `DcBlocker(stateBound = ...)` and `flushStateWithin`, the compare
  `flushState` makes with another bound; every other `DcBlocker` keeps `Double.MAX_VALUE`, bit for bit). One block of
  1e15 on a 0.3 sine at 44.1 kHz: 2 samples on the rail, back within 0.003 of the clean run 0.213 s after the bad
  block began (the audit at 48 kHz: 26,783 rail samples, 1.13 s). Guard: `HouseStageSpec`, "a huge FINITE block on the
  mix does not linger as DC" (under 50 rail samples, back within 0.4 s). Mutant: the bound at `Double.MAX_VALUE`,
  KILLED. The L8-01 reproduction is deleted from `AuditSafetyRepro`; L8-02's stays. Nothing new on the wire.
- **L8-06, BUILT.** `pcm16` rounds to nearest, ties away from zero, no dither (`_pcm16_edge.kt`): every WAV render's
  and the JVM line's lowest bit moves, the corpus (doubles) does not. Guards: `Pcm16EdgeSpec` rewritten (it pinned the
  truncation bit for bit): a literal table (5e-5 is 2, 0.5 is 16384, `1.0.nextDown()` 32767), a sweep against the rule
  written out, the error at most half a step and not following the sign; `WavFileWriterSpec` in `klang` (its two
  bytes of plus and minus 0.5 pinned the truncation; moved). Mutants: the positive side truncating again, KILLED; the
  negative side rounding half up, KILLED (the table's first negative row, -1e-4: -2 instead of -3).
- **L4-08, BUILT (text).** `audio/ref/numerical-safety.md`, "A non-finite sample: the rule": the decided rule and the
  table of what one NaN and one infinity become at every stage (read from the code and the audit's probe rows). The
  shared L4-08 and L4-11 reproduction stays (L4-11's half is open).

**The corpus**, my run before the first edit against my run after the last (no song text changed between them, the
code hashes identical): four renders moved, all by L4-10 alone (a run with the clamp taken back gives the before hash
for all eighteen; a run with the switch-on judgement or the compressor snap taken out moves the same four). Peak and
RMS of the whole 256-cycle render, before and after (a temporary print in the corpus check, removed byte for byte):

| Render | Peak dBFS | RMS dBFS |
|---|---|---|
| Gestrandet | -22.4982, -22.4984 | -43.5739, -43.5738 |
| Seltsamere Dinge and the frozen `strangerThings_2026_11_03` (one render) | -2.5507, -2.5499 | -17.1344, -17.1370 |
| Remix: Echo um Echo | -6.1369, -6.1358 | -25.1528, -25.1528 |
| The Synthsale Piper's Last Rave | -0.9602, -0.9602 | -15.6083, -15.6083 |

By reasoning, not traced per song: the clamp is the only change in that run, so these are the songs whose filters
reach above 0.49 fs (23.52 kHz) at some point; the levels move by under 0.003 dB.

## Decisions pass B2 (2026-11-11)

- **L8-02, option (c), BUILT, no sound change, with a reach to report.** Once per block the house stage reads the
  gain its limiter ended the block at (`Compressor.lookaheadGain`, the two boxes' output) and counts a block under
  1e-10, -200 dB (`HouseStage.HOUSE_CRUSHED_GAIN`, `HouseStage.crushedBlocks`, a monotone Double that `reset` leaves).
  One division and one compare per block, no allocation. Every diagnostics report carries it beside `nonFiniteSamples`
  (`Diagnostics.crushedBlocks`, in the one wire change of this pass, with its round-trip row; the hash moved from
  358149839 to -1036617280 for this and KLANG-00431's changes together). Measured at 44.1 kHz, 64-frame blocks, a 0.3
  sine under it: one block of 1e15 counts 1; 1e15 flipping its sign every block from block 100 of 400 counts 297; a
  mix at +60 dBFS counts 0, a NaN 0. **The reach:** a huge CONSTANT (the audit's L8-02 case, 1e15 from 0.5 s on)
  counts 0: since L8-01's state bound (pass B1) the house DC blockers take a constant out within a sample, so the
  limiter hears one sample of it and its gain never ends a block that low, while the sum has rounded the music under
  it away all the same. So option (c) as decided makes a huge MOVING value visible and not a huge constant. The
  options, for the maintainer: (a) keep it so (the constant's crushed music stays invisible); (b) one more compare per
  block on the mix's first sample BEFORE the DC blockers (say `abs(x) > 1e10`), counted into the same field: catches a
  constant from its first block, free in the same sense. Recommendation: (b), if the count is to cover L8-02's own case.
  Guards: `HouseStageSpec`, "crushed: ..." (the measured counts, the constant pinned at 0 as the reach), and
  `PlaybackEngineDispatcherDiagnosticsTest` (a sample voice at 1e15 through the dispatcher reports crushed blocks, a
  voice at 1e3 none, monotone). The L8-02 reproduction in `AuditSafetyRepro` stays: the constant's case is open.
  **Decided and built 2026-11-11** (maintainer: "count in the DC blocker"): the house stage also counts a block at
  whose end the mix stands above 1e12 (`HouseStage.HOUSE_CRUSHED_INPUT`) ahead of the DC blockers, read from the
  blockers' own input state (`DcBlocker.lastInputAbove`), so nothing is added per sample. A standing constant of 1e15
  now counts every block (300 of 300 measured), one block of 1e15 counts 2, 1e11 counts 0, either channel counts.
  Five mutants killed (the mix's side off, the level at 1e10, each channel off).
  Mutants: the count taken out, the threshold at 1e-20, the dispatcher not carrying it: each KILLED (`HouseStageSpec`: `expected:<1.0> but was:<0.0>`; the dispatcher row: `0.0 should be > 0.0`).

## Decisions pass C (2026-11-11)

What the fix worker of pass C built of the decisions of 2026-11-11 (L4-02 and L4-05; the aliasing verdict is in
KLANG-00420's pass C).

### L4-02, the filter envelope: BUILT, a sound change

**Measured first** (a gated scratch measurement, not kept: the engine's lowpass emulated from its own primitives, as the
disproof's L4-02 (c) did, against the EXACT per-sample cutoff; a 48 kHz saw, nine shapes: attacks 0, 1, 2, 5 and 10 ms,
depths -36 to 60 semitones, q 0.7 to 8, the IrishLamentTechno bass and Tetris sub shapes among them). Columns: the
largest difference re the exact render's peak, and the largest cutoff error in cents (from the stepped `g`):

| Law | 1 ms, 48 st | 5 ms, 48 st | defaults, 24 st | pluck (a 0), 60 st | Irish bass | Tetris sub | hpf, 2 ms, -36 st |
|---|---|---|---|---|---|---|---|
| today (block ends, worst of 32 onsets) | -4.7 dB, 2872 c | -25.0, 1809 | -15.9, 381 | -23.4, 231 | -37.0, 446 | -25.4, 1070 | -17.1, 2352 |
| grid 32 + the stage breakpoints | -17.2, 1095 | -51.0, 204 | -42.8, 27 | -42.8, 19 | -63.4, 37 | -51.2, 121 | -40.9, 129 |
| grid 16 + the stage breakpoints | -25.1, 1095 | -64.0, 90 | -51.5, 7 | -52.7, 5 | -76.5, 14 | -64.5, 34 | -54.7, 68 |
| knots by travel, 1 st, cap 128 | -57.6, 2.5 | -57.6, 2.6 | -61.5, 5.2 | -53.0, 2.9 | -63.4, 5.1 | -65.0, 3.1 | -46.3, 12.5 |
| **built: knots by travel, 0.5 st, cap 128** | -68.8, 1.0 | -68.9, 1.0 | -69.6, 1.2 | -68.0, 0.6 | -65.1, 2.2 | -77.2, 0.7 | -67.9, 1.5 |

A uniform grid of 16 or 32 is not correct for a fast deep sweep: a 1 ms attack over 48 semitones moves most of its
travel in its last few frames, and a straight line through 16 frames of it misses by 11 semitones. So the knots follow
the envelope instead. The criterion: the cutoff never more than 5 cents from the exact one (the pitch JND of a pure tone
in the mid range is about 5 cents, and a cutoff is heard far less precisely than a pitch); 0.5 semitone per segment
meets it with a margin (2.2 cents worst), 1 semitone does not (12.5). The output difference is reported, not judged: at
q 8 a fraction of a cent reads -55 to -60 dB at the largest sample.

**The design** (`SvfIgnitor.renderEnvelope` and `aimFrom`, `IgnitorFilters.kt`): the cutoff is exact at knots that belong
to the NOTE, the coefficients step linearly between two knots (the same adds per sample as before; the six loops moved
into one method, `runSegment`, called per segment). From a knot the next is at most 128 frames on
(`FILTER_ENV_KNOT_MAX_FRAMES`), never past the envelope's next stage frame (`EnvelopeCore.nextStageFrame`, new: the
attack's end, the decay's end, the gate, the release's end; within a stage the level is monotone), and close enough that
the cutoff moves at most 0.5 semitone (`FILTER_ENV_KNOT_SEMITONES`): the first guess is the last segment's length, twice
it after a move of at most half the step, halved while too far (whole numbers only; a `Double.toInt()` there boxed on V8,
below). The running coefficients carry across block borders, so a note renders the same samples, bit for bit, at every
onset inside a block (analog 0, knobs constant). The knobs read per block (the cutoff and q, an `analog` drift) enter at
the next knot's aim (at most two knots late, gliding); a gate learned while the note sounds re-aims at once. A knot whose
cutoff and q are unchanged (a sustain, a finished release) computes no coefficients. `SvfCoeffSweep` (its only host was
this node) and `SvfCoeffSweepSpec` are deleted; its cutoff law moved to `filters/FilterEnvCutoff.kt`
(`filterEnvCutoff`, `filterEnvCutoffAtLevel`). No new clamp; the level clamp to [0, 1] is the one that was there.

**Coefficient sets per note** (a `tan` and a divide each): 118 to 336 over the nine shapes, against 114 to 211 for the
block law (more only on the deep fast sweeps: Tetris 336 against 207, the pluck 172 against 114); plus about one envelope
level read per knot.

**Cost** (the cost probe, `KLANG_PROBE=cost`, three new rows `lowpass env`: default times held, a 1 s decay of 48
semitones moving through the whole render, the same with `analog` 1). Two sets of interleaved runs, HEAD (a throwaway worktree with the new rows copied in) against the tree, after the
last code change: JVM two pairs (HEAD, tree, HEAD, tree), JS five node processes each, interleaved, the production test
bundle, ratios to the plain sine. Tree over HEAD, the median of the runs:

| Row | JVM | JS |
|---|---|---|
| `lowpass env` default times (held) | 0.987 | 1.036 |
| `lowpass env` 1 s decay of 48 semitones (moving) | 0.955 | 0.986 |
| the same with `analog` 1 (moving) | 0.974 | 1.003 |
| the static lowpass and highpass rows (`freq=1000`, `q=200`, `passes=16`) | 1.03 to 1.05 | 1.02 to 1.04 |
| the median of every row over 2 ns (JVM) or over half a sine (JS) | 0.987 | 1.012 |

The envelope rows cost what they cost before, within the run-to-run noise (the register: under 10 percent between two
runs is noise); the static SVF rows read 2 to 5 percent dearer in both runs and on both platforms, consistent in sign
though inside the noise band (the static path now writes its latched coefficients into the node's fields and calls
`runSegment`). The first measurement, before the last two changes of the search, read the same picture (envelope rows
0.96 to 0.98 JVM, 0.96 to 1.02 JS).

**V8 allocation** (`KLANG_PROBE_JS_ALLOC=1`, three processes): 0 bytes per block on every filter row, HEAD and tree.
On the way: the first version's knot search shortened a segment by `(length * ratio).toInt()`, which on Kotlin/JS is a
call (`numberToInt`) V8 left out of the method's budget, so its double crossed as a heap number: 3.1 bytes per block on
the held row (about 1.2 KB per note of a 10 ms attack); whole-number doubling and halving took it to 0
(`audio/ref/performance.md`, the new bullet).

**Guards:** `FilterEnvKnotsSpec` (new): the node against an ORACLE written in the test (the TPT lowpass or highpass with
the cutoff recomputed at every sample from the textbook coefficients and the envelope law written out), four shapes
with bounds from the measurement (-64.5 / -54.3 / -68.0 / -68.2 dB re the peak as built, bounds -60 / -50 / -60 / -60,
and RMS bounds); note-anchoring (offsets 1 to 127 against 0, bit for bit); a gate learned three blocks early and in its
own block renders, from the gate on, what the known gate renders (bit for bit as measured, bound 1e-3 re the peak);
`nextStageFrame` against a scan of the law; the cutoff law (moved from the deleted spec). Specs that pinned the old law,
changed: `EnvelopeLawSpec`'s filter host row and `SvfNodeLawSpec`'s six-loop row now compare with the exact per-sample
cutoff (tolerances 5e-3 and 2e-3 on a unit sine, measured 3.0e-3 and 9.5e-4); `BlockFramingInvarianceSpec`'s
"E3 KNOWN DEFECT" pin is flipped to "E3 FIXED" (block lengths 128, 64 and a ragged sequence bit-identical to one-sample
blocks); the E3 ledger line of KLANG-00156 says FIXED.

### L4-05, the doors take the fused law: BUILT, a sound change

The `distort` doors (Kotlin `IgnitorDsl.distort`, both overloads, and the script door through it) build
`IgnitorDsl.Distort`, the fused node `classic()` runs, instead of `Shape(Drive(...))`. The `Shape` node lost its soft cap
(`ShapeIgnitor` copies the core's output; `shape()` is the fused law at unity drive, and `drive().shape()` renders the
fused node bit for bit). The backend `Ignitor.distort(amount: Ignitor, shape)` is `fusedDistort`. Removed: the cap on
`Shape` and the doors' law; `ShapingFuncs.softCap` stays (the delay's feedback path uses it). Two behaviours follow from
the fused node: a door's leaf amount at or below 0 is now a true bypass (the old chain kept the shaper at unity drive),
and a NaN amount read per block drives with NaN (the core writes 0 for that block) where `Drive` read it as unset.

Level and spectrum, old law (the chain plus `softCap`) against the new, a 110 Hz saw at 0.8, peak and RMS after 0.1 s:

| Shape | amount 0.1 | 0.3 | 0.5 | 1.0 | 2.0 |
|---|---|---|---|---|---|
| soft | peak 0 to +0.81 dBFS, RMS +0.06 dB | +2.77, +0.51 | +3.33, +0.64 | +3.50, +0.88 | +3.52, +1.07 |
| tube | -2.33 to -2.33, +0.00 | +0.25, +0.01 | +0.71, +0.05 | +0.37, +0.01 | +0.21, +0.01 |
| hard | +2.63, +0.50 | +3.25, +0.68 | +3.43, +0.67 | +3.51, +0.94 | +3.52, +1.08 |
| gentle | +3.07, +0.56 | +5.21, +1.22 | +6.75, +1.99 | +8.71, +4.20 | +9.48, +5.48 |
| fold | +1.46, +0.17 | +3.11, +0.65 | +2.23, +0.00 | +0.08, +0.02 | +0.09, +0.02 |

(the old peak sat at 0 dBFS, the cap's ceiling; the saw's flyback makes the DC blocker overshoot the shaper's rail, to
about +3.5 dBFS on a railed shape; `gentle` doubles and was the cap's biggest bend). On a sine at 0.5 (the aliasing
table, 1244.5 Hz): the fundamental moves +0.02 dB (soft, amount 0.5), +0.11 (hard), +1.78 (gentle); the harmonics stay
within 0.1 dB except `gentle` (-11.1 to -15.4 dB re the fundamental); the energy off the harmonics falls from -92.3 to
-138.3 dB (soft 0.5), -67.7 to -167.1 (fold 0.5), -50.6 to -53.2 (gentle 0.5), and does not move where the shape's own
corner dominates (hard, and every shape at amount 2). The doors' cost: one node instead of two (the drive pass and the
cap pass gone; the census counts 2 passes for the door, 3 for `drive().shape()`).

**Guards:** `DoorDistortionLawSpec` rewritten for the cap-less law (both spellings against the oracle, the output past 1
where the cap acted); `IgnitorsTest` ("shape through a voice is not capped", measured 1.205); `GraphCensusSpec` (the door
is one stage); `WaveshaperKnobsSpec` (the chain is the fused node bit for bit); the tree pins in `IgnitorDslSpec`,
`IgnitorDslOptimizerSpec`, `StdLibIgnitorTest`, `KlangScriptEffectDoorParitySpec`, `KlangScriptWaveshaperDoorParitySpec`
follow the node; `OversamplerDecimatorParitySpec`'s oracle lost the cap; `SharedScratchSpec`'s deep chain spells
`drive().shape()` to stay 24 nodes deep.

### Mutants

`console/tools/mutate`, one campaign under the lock, every file restored byte-identical and the final run green; 15
mutants, 15 KILLED for the expected reason (one, the sweep fraction, first read KILLED-OTHER because the sweep's top
fired before its per-pitch check; the check was moved first and the mutant rerun: KILLED):

| Mutant | Spec | Message |
|---|---|---|
| the knot step 0.5 to 4 semitones | `FilterEnvKnotsSpec` | largest difference re the oracle's peak |
| no re-aim on a gate learned late | `FilterEnvKnotsSpec` | gate learned at frame 14336: after the gate |
| an exact knot at every block start | `FilterEnvKnotsSpec` | onset at offset 1 differs from offset 0 |
| the same | `BlockFramingInvarianceSpec` | E3, blocks ... |
| the linear lowpass loop drops `a2`'s step | `SvfNodeLawSpec` | lowpass: the worst sample |
| the saturated highpass loop drops `g`'s step | `SvfNodeLawSpec` | saturated highpass: the worst sample |
| the level clamp at 2 instead of 1 | `EnvelopeLawSpec` | sustain 1.5, Linear |
| `nextStageFrame` without the gate | `FilterEnvKnotsSpec` | pos 145 |
| the soft cap back on the `Shape` node | `DoorDistortionLawSpec` | no cap / drive(...).shape(...) |
| the same | `IgnitorsTest` | should be > 1.1 |
| the door wraps a `Drive` inside the node | `GraphCensusSpec` | expected:<2> but was:<3> |
| the door drops its shape | `IgnitorDslSpec` | 10.0 |
| the healthy threshold 30 dB lower | `AliasingMeterSpec` | rising alias: the limit |
| the 5 kHz band counting every bin | `AliasingMeterSpec` | rising alias |
| the sweep fraction 0.5 | `AliasingMeterSpec` | fs / f = |

### The corpus

Three renders of the 18 rows (`runSongBenchmark --args=corpus`, through the lock, an RMS line per song added for these
runs only, `CorpusCheck.kt` restored byte for byte and identical to HEAD): HEAD (a throwaway worktree), HEAD with the
L4-05 files only, and the tree. No song text changed between them. The corpus check's own verdict is red on all three
(its reference predates other sessions' song edits), as in pass B1. 13 renders moved; Final Synthasy VII Prelude, Die
Kirschblüte, Greensleeves Chant, The Synthsale Piper's Farewell and Smalltown Synth did not.

| Render | Moved by | Peak dBFS, HEAD to tree | RMS dB, HEAD to tree |
|---|---|---|---|
| Kokon | L4-05 | -2.5604, -2.5478 | -18.2113, -18.1081 (+0.103) |
| Der Schmetterling | L4-05 | -2.4598, -2.4602 | -15.5682, -15.4801 (+0.088) |
| Synthsturm | L4-05 | -1.6377, -1.6425 | -18.5634, -18.4803 (+0.083) |
| frozen derSchmetterling_2026_11_03 | L4-05 | -2.5562, -2.5559 | -16.9107, -16.8590 (+0.052) |
| frozen piece derSchmetterling_2026_09_16 | L4-05 | -0.9221, -0.9217 | -18.3931, -18.3660 (+0.027) |
| A Synth Worth Lying For | L4-05 and L4-02 | -2.4357, -2.4349 | -13.9785, -13.9770 (+0.002) |
| Drunken Synthlor | L4-02 | -1.1043, -1.1042 | -18.9264, -18.9261 (+0.0003) |
| Gestrandet | L4-02 | -22.4984, -22.4987 | -43.5738, -43.5738 (0.0000) |
| Stein um Stein | L4-02 | -2.3749, -2.3750 | -16.1293, -16.1292 (+0.0001) |
| Seltsamere Dinge and the frozen `strangerThings_2026_11_03` (one render) | L4-02 | -2.5499, -2.5499 | -17.1369, -17.1369 (0.0000) |
| Remix: Echo um Echo | L4-02 | -6.1358, -6.1357 | -25.1528, -25.1528 (0.0000) |
| The Synthsale Piper's Last Rave | L4-02 | -0.9602, -0.9602 | -15.6083, -15.6084 (-0.0001) |

Attributed by the middle render: a row that moved between HEAD and HEAD plus L4-05 is L4-05's, a row that moved between
that and the tree is L4-02's. L4-05 moves the songs that play the `distort` door (the guitars of Der Schmetterling,
Synthsturm, Kokon), up to +0.10 dB RMS; L4-02 moves every song with a filter envelope, by under 0.001 dB RMS on the
whole render (its change is in the attacks).

### Not built, not verified

- Not listened to: neither change was judged by ear.
- The knots were measured on the linear lowpass and highpass emulation and through the node; the bandpass, notch and
  the saturated loops share the same knots and steps (`SvfNodeLawSpec`'s six-loop row) but were not measured against
  the exact cutoff in dB.
- A filter envelope inside an oversampling region counts its 128-frame cap in the region's frames (a shorter time); not
  measured.

## Acceptance

- Every row above is fixed with a guarding spec, or listed under "Decide" with its reason.
- The suites are green, the corpus render is identical except where a fix changes the sound on purpose, and each such
  change is named in this ticket with the songs it moves.
- The engine health page shows the fixed items green after a new probe run.

## History

- 2026-11-10: created.
- 2026-11-11: IN PROGRESS. Fix worker started.
- 2026-11-11: PAUSED. Landed; eight decisions pending with the maintainer.
- 2026-11-11: decisions pass C: L4-02 (the filter envelope's knots) and L4-05 (the doors on the fused law) built; see the section.
- 2026-11-11: DONE. the filter envelope's knots, the one distort law, the cutoff clamp, the crushed count are built
