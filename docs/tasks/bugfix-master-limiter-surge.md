# Master limiter: the mix surges back after deep limiting

Status: **open, sound.** Carried 2026-09-27 out of the master limiter lookahead task (archived as
`docs/tasks-archive/2026-09/20260927-master-limiter-lookahead.md`, section "REOPENED 2026-08-18"; the
full measurements and the limiter's design live there). Reported by ear by the maintainer, measured
2026-08-18, not fixed.

## The symptom

"Sudden boosts in volume of the entire mix" (maintainer, Der Schmetterling). The dual
program-dependent release (`audio_be/src/commonMain/kotlin/effects/Compressor.kt`, the release stage
from `:198`) recovers through its fast branch. At shallow reduction that is inaudible and buys about
1 dB of loudness at an identical peak, which is why it shipped. At deep reduction the same fast
recovery is a broadband surge.

Measured on Der Schmetterling v30, 256 cycles, 2026-08-18: transparent almost everywhere (median gain
+0.02 dB, 2.5 % of 10 ms frames above 3 dB of reduction), but 14 close-then-reopen events in which the
gain dives to -4 to -11.5 dB and recovers +5 to +8 dB within 250 ms. By ear, backing the drive off by
3.3 dB made it "almost vanish", so the threshold sits around closures of about 4 dB.

Exonerated the same day (do not re-suspect): the per-orbit glue compressor, double summing,
stateful-effect resets, render nondeterminism.

## Re-measure first

The numbers are from 2026-08-18. Since then the gain staging changed (`gain` is the one level word,
2026-09-19, `docs/plans/signal-flow-redesign.md` section 6) and the song has moved on. The method
drove the limiter with `MasterFx.gain`, which is retired; use the `gain` stage of the Katalyst at the output instead
(`master(Katalyst(k => k.gain(2.2).limiter()))`, since phase 3 step 12, 2026-09-28):

- Render the song twice, identical except the master drive (2.2 against 0.55, 12.04 dB less, so the
  limiter idles in the reference). The per-frame envelope ratio of the two renders minus the drive
  delta IS the limiter's gain trajectory. Per-note seed noise is about 0.3 dB.
- The number to move: reopen dB within 250 ms after closures deeper than 4 dB, target under 3 dB.
- The cost number: mean level at identical peak (the dual release's reason to exist).

If the surge no longer shows at a realistic drive, record the measurement and close this.

## The fix direction

Make the fast branch **depth-aware**: blend its recovery toward the slow constant as the reduction
depth passes about 3 to 4 dB, so a -10 dB closure recovers over about 100 to 200 ms instead of tens of
milliseconds, while shallow reduction keeps the loudness win.

Rejected, measured, do not retry:

- a **dB-domain release**: crawls in the last dB just as badly (205 ms against 187 to recover within
  0.5 dB);
- **simply shortening the release**: reduces the pump but gives back most of the lookahead's LF gain
  (-41.1 to -37.4 dB at 50 ms).

The fix at the source is `docs/tasks/voice-takeover.md` (deep closures come from coinciding onsets
over loud overlapping tails), which is blocked on a maintainer design decision since 2026-09-08.

## Tests

`LimiterLookaheadSpec` ("the bed recovers between kicks") guards the dual release; keep it green. Add
the three measurements the lookahead work asked for, because peak dBFS alone hid four findings there:

1. Non-fundamental energy on a sustained 55 Hz sine at +6, +12 and +18 dB over ceiling.
2. The gain trajectory as a signal: max |delta gain| per sample and its second difference.
3. A ramp-length invariance sweep: the same ceiling test at three smoothing lengths in a fixed window
   gives the same peak.

Plus a guard for the fix itself: a deep closure recovers no faster than the depth-aware law allows,
mutation-checked (mandatory tier, `audio_be`). A by-ear check on Der Schmetterling closes it.
