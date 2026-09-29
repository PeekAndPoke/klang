# Master limiter: the mix surges back after deep limiting

> **CLOSED 2026-09-29: measured, not audible.** The maintainer listened to the pair around the worst event
> (`tmp/listening/01-limiter-snare-*`, the song as written against 3.3 dB less drive): "there is nothing after the
> snare hit, that feels as if it surges in volume"; no ducking heard in the lower-drive version, and the snare stays
> prominent. So the house limiter's dips under each snare (measured below) are masked by the snare itself, and
> neither the depth-aware release nor an upstream change is needed. Re-open only on a new report by ear.

Status (before closing): **open, sound; re-measured 2026-09-29 (it still shows, on every snare hit), next: by ear.** Carried 2026-09-27 out of the master limiter lookahead task (archived as
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

## Re-measured 2026-09-29: it still shows, on every snare hit

Commit `12f889cb`, the committed Der Schmetterling (its master is `master(Katalyst(k => k.reverb(0.2, 7, 3500).gain(3.5)))`,
no authored limiter, so the house limiter does all the limiting), JVM offline render, 258 cycles at 32.5 rpm
(478 s), clock seed pinned. The method above: each drive against one 12.04 dB lower; the reference renders are clean
(the 0.875 reference peaks at -2.78 dBFS, below the knee) and deterministic (two references against each other give
0.00 dB in every frame). Event: a local minimum below -4 dB with 250 ms of suppression either side; the reopen is the
highest gain within the next 250 ms minus that minimum.

| drive | median gain | frames above 3 dB of reduction | closures deeper than 4 dB | reopen at least 3 dB | deepest |
|---|---|---|---|---|---|
| 3.5, as written | +0.00 dB | 1.92 % | 405 | 405 | -9.34 dB |
| 2.2, the August reference | +0.00 dB | 0.61 % | 80 | 80 | -5.76 dB |
| 3.5 minus 3.3 dB | +0.00 dB | 0.92 % | 186 | 186 | -6.42 dB |

- **Every event is a snare hit**: 396 on the backbeat, 9 in the snare fills; almost none while the snare is muted
  (cycles 128 to 160). The worst event: -9.7 dB, back to -3 dB in 12 ms and -0.5 dB in 38 ms, so the whole mix ducks
  by about 9 dB for 20 to 40 ms under each snare crack. Reopen within 250 ms at 3.5: median +6.7 dB, max +9.3 dB
  (target: under 3 dB).
- Against 2026-08-18 (14 events): similar dives, larger recovery, and a different character. The August events were
  rare coinciding onsets; these come from the snare (the metal snare of 2026-09-28) reaching about +8 dBFS before the
  limiter on every hit.
- Cost baseline for any fix: as written, peak -0.49 dBFS, mean -17.71 dBFS RMS; 3.3 dB less drive costs 3.0 dB of mean
  level at practically the same peak. The dual release's own share could not be computed without an engine change.
- **Judgement before any fix: listen first.** If the snare's crack masks the fast reopen, nothing needs fixing; if it
  does not, the likely fix is upstream (the snare's level, "check upstream before animating a component"). The
  depth-aware release below would stretch every snare into a 100 to 200 ms duck of the whole mix, which could pump
  more, not less. The listening pair: `tmp/listening/01-limiter-snare-*` (prepared 2026-09-29, not in git).

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
