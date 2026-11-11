# KLANG-00425: Audit fixes 1: no pattern crashes or silences the engine (the render's safety net, non-finite inputs, the duck)

> **Status (2026-11-10): PAUSED.** 10 of 13 fixed and landed; L6-01, L8-04, N2 and showing a failed playback in the UI wait for the maintainer (Decide).
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
| L2-04 | major | A gate that ends before the onset can hand the tree a NEGATIVE window length for one block | Confirmed, larger: a crash of all audio from a pattern: `note("c3*8").s("saw").pregain(0).legato(-0.001).adsr(release = 0)` throws `IllegalArgumentException` out of the render |
| L8-03 | major | An exception inside the render block ends the audio for good on both platforms, and nobody is told | Confirmed: no catch on either host; a throw from wire-legal song input now exists (L2-04) |
| L9-01 | major | One exception anywhere in a command or a block ends all audio for the session, on both hosts | Confirmed: same; the throw leaves `renderBlock` (measured), and the throwing voice stays in the active list |
| L1-01 | major | A NaN `pan` is the one typed wire number read raw: the voice writes NaN into its cylinder | Confirmed: `pan("NaN")`, `pan(Math.sqrt(-1))`, `pan(pow(-1)).pan(mul(0))` all put NaN on the wire; the whole output of every playback is exact zero while the voice lives |
| L6-02 | major | A NaN `pan` makes the voice's whole output NaN on both channels; `gain` is guarded twice, `pan` nowhere | Confirmed: same defect; its "0 divided by 0" route is wrong (`div` returns null for a zero divisor), the other routes stand |
| L1-02 | major | A non-finite `freqHz` on a sample voice makes every sample after the first NaN | Confirmed, larger: a NaN pitch on a sample voice measured as described; the Karplus family (`pluck`, `string`, `superpluck`, `ks`) does the same, so `note("NaN").s("pluck")` silences everything too |
| L6-01 | major | Nothing stops a non-finite sample between a voice and the house limiter, and the duck turns one into a bus that is NaN for good | Confirmed: measured end to end: no guard between a voice and the house limiter; the duck turns it into silence of everything |
| L7b-01 | critical | One NaN on the side chain makes the duck's gain NaN until the duck's life ends | Confirmed: one NaN on a duck's source silences every playback until the ducked cylinder's notes end; a swap onto a ducking chain carries it, onto a plain chain ends it; the fix is right |
| L6-07 | minor | A voice that is NaN from its first sample is never culled, and the channel's KDoc says the opposite | Confirmed, smaller: a voice NaN from frame 0 lives to its end frame (measured); NaN mid-gate or in the release is culled, so two cases of three are bounded, not one |
| L8-04 | minor | One engine's non-finite output silences every playback, and its end is a hard step | Confirmed, smaller: every playback is silenced (measured); the "hard step at its end" is wrong: the DC blocker restarts from zero state, the music re-enters with a 21 ms DC transient |
| L1-04 | minor | One NaN `startTime` in the queue can hide voices pushed after it until they are late, and they are dropped | Confirmed: measured as described; no frontend path makes a NaN start |
| L1-05 | minor | A non-finite `playbackStartTime` on a playback's first voice makes the epoch NaN: every voice is dropped | Confirmed: measured as described; internal only |
| N2 | new | Four finite extreme knob values put NaN, not a huge value, into a cylinder | found in the disproof pass, `disproof-safety.md` |

## The work

Per finding: read its report and its reproduction (`src/jvmTest/kotlin/audit/` in `audio_be`, `sprudel` or `klang`,
run with `KLANG_PROBE=audit`), fix it, and turn the reproduction's row into an ordinary spec that guards the fix
(mutation-checked). A finding that turns out to need a maintainer decision is not forced: it is listed under
"Decide" below with the options, and the rest of the ticket lands without it. How the fixes are made and reviewed:
`docs/audio-audit/2026-11-10/audit/FIX-BRIEF.md`.

## Decide

Filled by the fixer (2026-11-10). The rest of the ticket landed without these.

- **L6-01, a guard between a voice and the house limiter.** What it needed most is done: the measured pattern routes
  (a NaN `pan`, a NaN or 0/0 pitch) are closed at their readers and the duck no longer latches, so one NaN costs the
  output while its voice sounds and no longer for good. What is left reaches the mix only from N2's raw extreme values.
  Options: (a) nothing more; (b) a NaN test in `measureVoicePeak`, ending a voice whose block is NaN: NOT free (a
  per-sample compare) and not whole (the peak is measured only until a voice is heard and in its release, so a heard
  voice that turns NaN in its gate still mixes); (c) `finiteOrZero` per sample in the mix loop: one compare per sample
  on every voice, on a raw Motor. Recommendation: (a), and revisit with N2.
- **L8-04, one engine's non-finite output silences every playback.** Same sources as L6-01. Options: (a) nothing more;
  (b) every engine renders into its own bus and a non-finite block fails it like a throw (the net): a copy and a scan
  per engine per block, and the zero-copy path goes; (c) the house stage counts the non-finite samples its limiter
  already sterilises and the diagnostics say "poison seen" (cheap, but it cannot name the playback). Recommendation:
  (a), or (c) if the UI wants to say why the sound dropped.
- **N2, four finite extreme knob values end in NaN** (the Karplus `feedback`, the perlin `rate`, `reverb.wet` at
  plus or minus 1e308, `drive` from 257, which is L4-11's). Each is a raw value the Motor allows. A clamp at the read
  is a safety clamp on an audio parameter (the stone rule: ask first); a `safeOut` at each node's state write costs
  per sample (the `DelayLine` precedent: +30 percent for a per-sample `isFinite`). Options: (a) document that these
  values end in NaN (`audio/ref/numerical-safety.md` names the Karplus divergence, not the NaN); (b) coerce the four
  knobs at their per-block read to plus or minus `SAFE_MAX` (a clamp, the maintainer's call); (c) per-sample
  `safeOut` at the four writes. Recommendation: (a) now, (b) if the maintainer accepts a `SAFE_MAX` bound as "not a
  clamp" for these four.
- **Showing a failed playback in the UI.** The engine and the wire report it (`Feedback.PlaybackFailed`), the player
  writes it to the log. Showing it in the editor (stop the playback, show the reason as an error line through the
  controller's `errors`) needs a new `KlangPlaybackSignal`, forwarding in `KlangPlayer` and the controller's handling:
  more than a few lines in the app, which another session is working in. Recommendation: do it with KLANG-00407 (the
  frontend's logger and its console tab).

### Decided (maintainer, 2026-11-11)

"All as you suggest. We make the failures visible but keep the motor raw."

- **L6-01:** (a), nothing more between a voice and the house limiter.
- **L8-04:** (c), the house stage counts the non-finite samples its limiter already sterilises and the diagnostics
  carry the count ("poison seen"). Owed; it waits for KLANG-00431, which is working in the diagnostics.
- **N2:** (a), no clamp and no per-sample guard. `audio/ref/numerical-safety.md` names the four values that end in
  NaN. Owed, with the docs of KLANG-00433.
- **A failed playback in the UI:** with KLANG-00407. The "poison seen" count is shown there too.

## Acceptance

- Every row above is fixed with a guarding spec, or listed under "Decide" with its reason.
- The suites are green, the corpus render is identical except where a fix changes the sound on purpose, and each such
  change is named in this ticket with the songs it moves.
- The engine health page shows the fixed items green after a new probe run.

## History

- 2026-11-10: created.
- 2026-11-10: IN PROGRESS. Started.
- 2026-11-10: PAUSED. 10 of 13 fixed and landed; L6-01, L8-04, N2 and showing a failed playback in the UI wait for the maintainer (Decide).
