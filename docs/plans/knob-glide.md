# Knob glide: orbit settings change smoothly, never in one step

Decided 2026-09-19 with the maintainer (signal-flow plan section 7, the insert-style sends).
Status: the pilot on the orbit reverb is the first step; this file is where its lessons land.

## 1. Why

An orbit has one owner voice at a time, and two patterns on one orbit take turns owning it. Each
owner change can move every knob of the orbit's chain at once. Today most of them jump, which
clicks (a level) or zips (a coefficient). The rule: every orbit knob GLIDES to its new value over
`KNOB_GLIDE_SECONDS = 0.05` (a constant in `audio_bridge/constants/`, one day perhaps a user knob).

**What a glide is for (maintainer, 2026-09-19): a safety net against clicks and sudden unwanted
sounds, not a sound-proof one.** It does NOT preserve loudness: a linear blend of two different
sounds may dip by about 3 dB in the middle, and that is fine. No equal-power law, no loudness
compensation, no cleverness. Different-sounding voices that must not disturb each other belong on
different orbits; that is the user's call, and the glide does not try to make one orbit behave
like two.

**Measure before you glide (learned in the pilot, 2026-09-19).** A knob gets a glide only where
its JUMP is audible. The pilot measured the orbit reverb in a Python copy of `Reverb.process`
(low tones, energy above 3 kHz after the change as the artifact): a full-span DAMPING jump sits 66
to 70 dB below the tail, because the comb's one-pole state stays continuous and the change reaches
the output one comb length later, staggered over 16 combs; a SIZE jump sits 44 to 50 dB below the
tail, marginal, possibly audible on an exposed tail, and the glide takes 12 to 15 dB off it. So
size glides and damping does not. Every later knob is measured the same way first, and only the
ones whose jump is audible get the helper. Coefficients inside recursive filters with continuous
state are the likeliest to need nothing; LEVEL knobs (a gain that steps) are the likeliest to need
it.

## 2. The rule, per kind of knob

| kind | examples | how it glides | cost |
|---|---|---|---|
| LEVEL | reverb and delay `wet`, body, vowel and phaser mix, the fader, the crossfade between two delay taps | linear ramp PER SAMPLE: start value and step computed once per block, one add per sample in the effect's own loop, on locals | one add per sample |
| COEFFICIENT | reverb size (damping measured and NOT needed, pilot log entry 4), delay feedback, compressor settings, filter frequencies: each measured first | linear glide PER BLOCK: the value used for a block moves one step toward the target | nothing per sample; the coefficient math stays at block rate |
| exception: delay TIME | | never glides (a glide bends the echoes' pitch); a change crossfades from the old tap to the new one, which is a LEVEL ramp | as LEVEL |

Why the split: a gain that moves in per-block steps (about 19 steps over 50 ms at 128 frames)
zips audibly on a loud signal; recomputing a coefficient per sample would put `exp` or `tan` in the
hot path. Small per-block steps of a recursive filter's coefficient are ordinary control rate and
do not click.

## 3. The helper

One small plain class, one instance per knob, allocated with the effect (no framework, the sibling
of `Crossfade`):
- As built in the pilot it serves COEFFICIENT knobs only; the LEVEL half (a per-block start and
  step for a per-sample ramp) arrives with its first real knob in 5b-2 (pilot log entry 10).
- LINEAR with an EXACT landing: after the glide it holds the target exactly and does no work, so a
  settled knob is bit-transparent and costs nothing.
- A new target mid-glide restarts from the CURRENT value, so owner flip-flops chain smoothly.
- A non-finite target is IGNORED, the standing target stays (the owner substitutes its own
  meaning first), so nothing non-finite is ever stored (the review ledger's cached-config rule):
  a glide can never get stuck on a NaN.
- The FIRST value after construction or reset SNAPS (no glide from a meaningless default), like
  `KatalystGainEffect`'s fresh state. Without that, every song's reverb would open with a 50 ms
  glide from nothing, and every render would change.

## 4. Rollout

1. Pilot: the orbit reverb's size, a coefficient knob (damping was measured and dropped, log
   entry 4). Its `wet` does not exist as an
   orbit knob until 5b-2 makes the reverb an insert; the LEVEL half of the helper is exercised
   there. A sound change for any song whose reverb settings change while it plays; a song with
   constant settings stays bit-identical.
2. 5b-2: the reverb's and the delay's `wet` (LEVEL), the delay's feedback (COEFFICIENT), the tap
   crossfade on a time change.
3. 5c: every other orbit knob, together with the switch-off fades.

## 5. Pilot log: problems met, and how they were solved

Pilot: the orbit reverb's size (damping measured and dropped, entry 4), 2026-09-19. Result: no song
changes (18 of 18 built-in songs and frozen pieces bit-identical in raw doubles against
`279d5fc4`; no song moves an orbit reverb while it plays).

1. **The owner re-applies its settings EVERY block** (`Cylinder.updateFromVoice` calls
   `configure` on every block the lease is held), not once per change. A glide that restarts on
   every call never lands. Decision: a retarget to the unchanged target is a no-op. Next effects:
   never do anything with a side effect on an unchanged value, and write rows with a configure
   per block, like production.
2. **Snap or glide is decided by the LIFECYCLE.** The helper snaps until its first `advance`; the
   stage forgets the glide at one place only: the transition into or out of the state in which
   its memory is EMPTY, whichever keeps the ON arm uniform; the two are equivalent when nothing
   reads or advances the glide there (reset, release, retire and a finished drain all land in
   Off; since Katalyst 5c-2 the reverb forgets it on ENTERING Off, next to the tail ceiling).
   Next effects: find that state and forget the glide at its door; one row deletes that line and
   goes red.
3. **The first value snaps until a BLOCK has consumed it**, not only on the first call: a chain
   arriving through `beginFade` can be configured twice before its first `process`.
4. **Glide only where a jump is AUDIBLE, and measure before you build.** The pilot first glided the
   damping too. On an axis linear in the comb LPF coefficient, the unset value had to be a point
   on that axis, so it needed a round trip to Hz while moving and the authored value on landing:
   the most intricate part of the step. Review round 1 measured it in a copy of `Reverb.process`
   (low tones, energy above 3 kHz as the artifact): a full-span damping jump already sits 66 to 70
   dB below the tail, because the comb's one-pole state stays continuous and the change reaches
   the output one comb length later, staggered over the combs. The glide bought about 15 dB on an
   artifact nobody hears, and was removed. A size jump sits 44 to 50 dB below the tail and the
   glide takes 12 to 15 dB off it (10*log10(17)), so only size glides. Size glides on the
   normalized 0..1 axis, which is affine to the comb feedback. Next effects: measure the jump's
   artifact against the signal before adding a glide. A recursive filter's coefficient whose state
   stays continuous may need nothing; a level (wet, mix, fader) almost always needs one.
5. **Keep the old path literally.** `configure` writes the configured value unconditionally,
   exactly as before the glide; while a glide moves, the per-block write (`advanceGlide`, before
   the block processes) overwrites it. That makes "settled equals HEAD" true by construction, not
   by a conditional. The price: between a configure and the next block the DSP holds the target,
   so every reader in that window must read the glide instead. For the reverb there is exactly
   one, the drain countdown, found by a caller search of every read of the unit's size. Next
   effects: list every reader of the knob between the door and the per-block write before relying
   on this; a test that reads the DSP right after configure sees the target and must check the
   value a block runs at instead.
6. **A drain countdown computed from a coefficient that is still moving ends too early.** Draining
   keeps gliding toward the last Active settings (so "a drain is Active on silent input" stays
   true), and the countdown uses the larger of the value in force and the target. The DELAY's
   drain countdown reads its feedback the same way: when delay feedback glides, pass the larger.
   For a FALLING glide `max(in force, target)` over-holds: size 1.0 falling to 0.0, switched off
   mid-glide, counts down at feedback 0.98 (about 21 s) where the room decays like 0.7 (about
   1.3 s). Accepted for simplicity; a tighter bound needs its own proof, and it holds an idle
   network, never audio. Guard BOTH directions: with a rising-only row the mutant "countdown
   from the target" passed every test.
7. **The tail ceiling under a FALLING coefficient** under-states a window by at most the ratio of
   the two feedbacks for about two windows. For the reverb (feedback floor 0.7) that is -97 dBFS
   or quieter. The delay's feedback can reach 0.0, the case `audio/MEMORY.md` recorded as
   audible. CLOSED by Katalyst 5c-5 (2026-09-19): the ceiling uses the largest |feedback| any
   block of its running window ran at (the ramp's start included), for the reverb's size as well
   as the delay's feedback, so a falling coefficient holds about two windows longer instead of
   under-stating.
8. **What an identity fixture can claim for a coefficient on a recursive network:** bit-identical
   before the change, different from the first RE-EMITTED block on (one shortest comb later), the
   difference decaying with the tail. For the delay, expect it one delay time after the change.
   A LEVEL knob has no memory: its difference starts in the change block and ends with the glide.
9. **The sound oracle that works:** identity against the bare DSP configured BY HAND with the
    linear per-block values, plus an engagement control, the same bare DSP jumping. Build controls
    from the bare DSP, never from the stage under test (a first control was overwritten by the
    stage's own glide write). A patterned knob rendering bit-identically to HEAD is the cheapest
    proof that a knob takes the old path.
10. **LEVEL half: designed in the pilot and removed unused** (nothing read it, and its shape had
    not met a real loop; the Draining path processes fewer frames than a block). 5b-2 adds it with
    its first real knob. The recipe: after one `advance()` per block, run `end - step * (n - 1 - i)`
    per sample on locals, written from the END so the last sample lands exactly on the target (the
    gain stage's start-based `from + step * (i + 1)` misses by a rounding); fast path when the
    step is 0. Decide what a block shorter than `blockFrames` does before copying it.
11. **Tooling:** Gradle may judge a fixture task UP-TO-DATE after a comment-only production edit
    and write nothing; use `--rerun` and check that the output files exist before comparing.
12. **Keep the helper minimal and delete what no row guards.** Round 1 removed from `KnobGlide`: a
    `next == value` shortcut (it fired only on two different configures in one block before an
    advance; without it the glide runs value to value, which is exact), an unreachable
    `remaining = 0`, a `reset` that zeroed fields the snap overwrites anyway, a `seconds`
    constructor parameter no caller passed (with a false claim about `round(+Inf)`: it saturates
    to `Int.MAX_VALUE` on both targets), and the LEVEL half. The helper is about 120 lines with
    its KDoc; its only state is value, target, start, remaining and the snap flag.

**Katalyst 5b-2 (2026-09-19), the first LEVEL knobs and the delay.** The LEVEL half landed with
the owner's `wet` of the delay and the reverb (`KnobGlide.advanceScaled`: from the end, exact
landing, a multiply-only fast path when settled, a short block spreads its share over the frames
it has). Delay TIME: measured a hard-cut-class click (-20 to -29 dB), so a change crossfades
between the old and the new tap over the glide time (a second read position, the blended tap also
fed back; a change arriving mid-crossfade is parked, the latest wins); after: at the steady floor.
Delay FEEDBACK: measured -28 to -35 dB; a per-block glide alone left -47 to -55 dB, because
feedback is a gain on the recirculating audio with no continuous state, so the steps are written
into the ring and come back every period; a per-SAMPLE ramp inside the delay's loop (one add,
exactly 0 when settled) brought it to the floor (-90 dB, or its own floor). Lesson for the next
effects: a coefficient that multiplies the signal directly behaves like a LEVEL knob and needs the
per-sample ramp; only a coefficient inside a filter with continuous state is safe per block.

**Katalyst 5c-9 (2026-09-20), the phaser and the duck.** Phaser `wet` and `floor` feed the C4 law's
two MEMORYLESS coefficients, so both are LEVEL knobs and the output is LINEAR in the pair: ramping
both per sample IS a crossfade from the old settings to the new ones, which carries the knobs AND
the ON and OFF edges in one mechanism, because OFF is exactly `dryC = 1, wetC = 0` (verified in
review against an exact crossfade: one ulp). Jumps -49.8 to -14.8 dB, landing -60.3 to -49.6.
Phaser `centre` and `sweep`: the allpass multiplies its INPUT by alpha, so a breakpoint step is a
level step; they glide per BLOCK, with alpha computed at the block start from the breakpoint IN
FORCE (`PhaserCore.prepareBlock(frames, centerTo, sweepTo)`), which is bit-identical when nothing
moves and leaves the alpha sequence continuous at the seam (measured: seam step equals the
in-block step, ratio 1.0000). **They are the first knobs that do not reach their own floor** (6 to
9 dB over), and that residue is MODULATION, not a staircase: an IDEAL per-sample breakpoint glide
measures within 1.1 dB of the per-block one on every row. Axis question closed by measurement: a
log-Hz axis costs 12 dB above 8 kHz and buys 7 to 18 dB below 60 Hz, tan-warped is the mirror, so
no axis dominates and linear Hz stands. Phaser `rate` does not glide (it scales a phase increment,
the phase carries on). Duck `depth` glides per sample (the target gain is linear in depth); duck
`attack` does not (a time constant of a continuous envelope). **Read a row against its own floor:**
the phaser's steady floor is -50 to -61 dB and the duck's -49 to -53, not the compressor's -81.

**A glide is also a one-pole on a patterned knob (2026-09-20).** A retarget mid-glide restarts from
the current value over the full time, so a knob repatterned every block converges like a one-pole
with tau about 49 ms: -3 dB at 3.2 Hz, -10 dB at 10 Hz. A fast-wobbled `phaser.wet` or
`duck.depth` therefore loses modulation depth. It is the price of "always glide", stated here once.

**Katalyst 5c-8 (2026-09-19), the fader.** The orbit `gain.gain` is on the LEVEL law: a
`KnobGlide` over `KNOB_GLIDE_SECONDS`, per sample from the end. Its one-block ramp measured -58 to
-79 dB on jumps across 0.01 to 4 and through 0; the glide -82 to -100 dB.

**Katalyst 5c-7 (2026-09-19), the compressor.** Threshold, ratio and knee jumps measured -9 to -52
dB (a hard-switch class for threshold); a per-block glide left a zipper at -31 to -69 dB, because
the gain computer is MEMORYLESS: its output gain follows the knob within the sample, so a knob
step is a level step. They glide per sample inside `Compressor.processGliding` (written from the
end, exact landing), to the floor. **Glide on the axis the law is linear in:** the curve is linear
in threshold (dB), knee, and the SLOPE `1/ratio - 1`, not in the ratio; the first cut glided the
ratio itself, measured on a 2 to 8 sweep only, and a 20 to 1 or 100 to 1 swing still clicked at
-41 to -56 dB (review round 1); gliding `1/ratio` puts every swing at its floor. A rising threshold
swing of 40 dB still reads -62 to -65 dB (25 dB of release in 50 ms), accepted as a safety net. Attack and release are time constants of the smoothed envelope
and measured at the floor: no glide. Lesson: a knob feeding a memoryless gain law is a LEVEL knob.

