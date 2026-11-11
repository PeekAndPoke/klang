# KLANG-00432: Audit fixes 8: the per-note stages (the filter envelope's grid, fastExp, the doors' soft cap, non-finite samples, the house stage)

> **Status (2026-11-11): PAUSED.** Landed; eight decisions pending with the maintainer.
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
  cost on JVM and JS), then the build. Owed.
- **L4-08:** "as recommended": a stage that stores a sample stores a non-finite one as 0, everything else stays raw,
  the house stage counts. It is the rule; the table goes into `audio/ref/numerical-safety.md`. Owed (the text).
- **L4-01:** "accepted". The maintainer asked whether warehousing the state's memory would win anything: no, the
  state is two numbers inside the filter node, there is no buffer to hold.
- **L4-05:** "no need to pay attention to the builtin songs. Let us use the new implementation and drop the old one":
  the doors take the fused law, the old door law goes. Owed.
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

## Acceptance

- Every row above is fixed with a guarding spec, or listed under "Decide" with its reason.
- The suites are green, the corpus render is identical except where a fix changes the sound on purpose, and each such
  change is named in this ticket with the songs it moves.
- The engine health page shows the fixed items green after a new probe run.

## History

- 2026-11-10: created.
- 2026-11-11: IN PROGRESS. Fix worker started.
- 2026-11-11: PAUSED. Landed; eight decisions pending with the maintainer.
