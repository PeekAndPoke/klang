# A chain request while the old chain drains: wait, or not?

Status: **decided 2026-10-17, done, confirmed by ear 2026-10-17** (maintainer: "The switch between two katalyst settings is now smooth and fast enough") (the option "cut the drain", plus the tail detection fixed; see
"Decision" and "What was done" at the end). Was: open question for the maintainer's ear (phase 3 step 12, decision
(g), 2026-09-27: "kept as is for step 12, recorded as an open question for the maintainer's ear later"). Opened as a file 2026-09-28 when the step 12
plan was archived (`docs/tasks-archive/2026-09/20260928-phase3-step12-master-as-katalyst.md`, section 0 row (g) and
section 8 (g)).

Pairs: `tmp/listening/chain-swap-drain/` (local, not in git; start at `tmp/listening/00-index.md`).

## What it is

Since step 12 the orbit and the master swap their effect chain by one law, `ChainSwap`
(`audio_be/src/commonMain/kotlin/ChainSwap.kt`): the leaving chain's input ramps down over 60 ms while the arriving
chain ramps up, then the leaving chain DRAINS (rings out on silence) until it has no tail. A new request that
arrives during the fade or the drain is REFUSED by the swap and parked by the host (`Cylinder`, `MasterBus`),
latest wins; it lands when the swap is idle again.

## Why it is open

A drain can be long: a size-10 room rings for about 12.5 s. Since decision (i) the drain is capped at
`ChainSwap.MAX_DRAIN_SECONDS` (20 s) plus the exponential release (about 4.5 s), so the worst-case wait for a
parked request is about 24.5 s. In live coding that is latency: a second edit of an orbit's chain, or of the
master, inside a long ring-out is not heard until the ring-out ends. Nobody has listened for it.

## The options (from the plan)

- **Keep** (today's behaviour): the edit waits, the ring-out is never cut.
- **Cut the drain** when a new request arrives: the edit lands at once; the old tail needs its own fade or it
  clicks.
- **Allow more than one leaving chain** (a list of draining chains): the edit lands at once and every tail rings
  out; more state, and the "at most two chains at once" bound goes.

## How to listen

A song with a big room (or a long delay) on one orbit, then two chain edits a few seconds apart; the same at the
master (`master(Katalyst(k => k.reverb(0.3, 10)))`, then two quick changes). Does the second edit feel late?

## Decision (maintainer, 2026-10-17)

After listening to `tmp/listening/chain-swap-drain/second-edit.md`: **cut the drain when a new request arrives**,
and fix the tail detection. In the maintainer's words: "the change should happen more or less immediate ... it is
really odd having to wait for that long. It feels as if it does not work."

## What was done (2026-10-17)

- **The cut.** `ChainSwap` has a fifth state, `Cutting`, and one new event, `requestWaiting`, which both hosts
  (`Cylinder`, `MasterBus`) raise when they park a request they could land (on the request, and on the poll once a
  parked name has been registered). A drain is cut from weight 1, a release from its current gain
  (`TailRelease.nextGain`): the leaving chain keeps running on silence and its OUTPUT is faded to 0 by a raised
  cosine over `ChainSwap.CUT_SECONDS` (0.15 s, no corner at either end, largest per-sample step about 2.2e-4 at
  48 kHz); the swap is then Idle and the parked request lands on the next poll as a normal 60 ms fade. A request
  during the fade is remembered by `Fading`, and where the fade ends a leaving chain with a tail goes straight into
  the cut. At most two chains run at once, latest-wins parking is unchanged, and with no request waiting the drain
  rings out in full. A request now waits about 0.15 to 0.22 s instead of up to about 24.5 s.
- **The tail detection.** The size-10 room's drain ran to the cap because the reverb's `TailCeiling` (the drain
  keeps the leaving chain Active on silent input, so the reverb answers from it) was (1) a bound built for a fed
  network, which on a long silent ring-out stays up to its slack above what the combs really hold (measured about
  26 dB on a musical charge), and (2) held to `SILENCE_FLOOR` (1e-5) on the comb CELLS, whose output is the comb sum
  through `FIXED_GAIN`, so about -120 dBFS at the output. Measured on a size-10 charge: output under -90 dBFS at
  13.0 s, ceiling under its floor only at 24.4 s. The fix in the existing mechanism: a draining chain measures its
  tails (`KatalystChain.measureTails`, on entry and every `ChainSwap.TAIL_MEASURE_SECONDS`, 0.5 s; the reverb
  tightens its ceiling to one comb scan over the feedback, `TailCeiling.tighten`, which never raises), and while it
  drains the reverb answers against an output-referred floor (`Reverb.TAIL_FLOOR`, the cell level whose output is
  about -90 dBFS, the floor `TailRelease` retires at; a measured ratio, not a bound). Review round 1 added an output gate: the drain
  retires only once the leaving chain's output block is also under `SILENCE_FLOOR`, so a gain, a compressor's makeup
  or a distort after the room cannot raise the retire's step (before: size 3 then +30 dB retired at -69 dBFS). A
  size-10 room now drains in about 14 to 18 s depending on its charge (measured: 13.9 s on a musical charge at wet
  0.3 on the stage alone; 17.2 s in `ChainSwapCapSpec`'s pure-tone rig; the reviewer 17.35 s at wet 1, level 0.5),
  with the chain's output at the end under -100 dBFS (the output gate; measured -106 in `ChainSwapCapSpec`; the
  stage alone, without the gate, ends at about -93 to -97 dBFS). The rebuilt ceiling sits up to about 10 dB above the
  measurement, so this errs long. A feedback-1 delay still never ends: the cap and the release stay for it. The
  reverb's `ANTI_DENORMAL` residue (about 5e-17 in a cell) is far under the floor.
- **The floor is the drain's only** (maintainer, 2026-10-17, option 1 after the corpus measurement). Applied to every
  Active room, it moved 13 corpus songs: 12 under -90 dBFS, Seltsamere Dinge -55 dBFS for 61 ms under a -1 dBFS
  passage at 325.5 s (a sub-floor state difference amplified at a loud hit); attributed by a probe to the Active-state
  floor alone. Now two floors on purpose: ordinary playback keeps `SILENCE_FLOOR` and is bit-identical to before; only
  a reverb whose chain drains after a swap (`KatalystReverbEffect.swapDraining`, switched on by the drain's
  `measureTail`, off again on reset and retire, so a chain re-used from the cache starts ordinary) answers against
  `Reverb.TAIL_FLOOR`.
- **Tests.** `ChainSwapCutSpec` (new), rows in `ChainSwapSpec`, `ChainSwapCapSpec`, `ChainSwapStateIdentitySpec`,
  `MasterBusTest`, `TailCeilingSpec`; `ReverbTailFloorSpec` (new) pins what the drain's floor means at the output
  with a literal window (-101 to -88 dBFS on broadband charges; a floor at -80 or -100 goes red), that an ordinary
  room still holds its tail to `SILENCE_FLOOR`, and that a stage re-used after a drain (through retire or reset) is
  back on the ordinary floor. Every new row mutation-checked.
- **Known untested margins, left as they are.** The `/ fb` in `tighten(combPeakAbs() / fb)` and the size-glide guard
  in the reverb's `Active.measureTail` are conservative margins no row catches (their mutants survive). The reverb's own
  switch-off countdown (its Draining state) still counts to `SILENCE_FLOOR` even inside a swap drain (errs long). The
  swap-block scan spike: several orbits swapped in one block each run `combPeakAbs` on their drain's first block
  (about 25 us each on the JVM at 48 kHz); not mitigated.
- **The render.** `tmp/listening/chain-swap-drain/second-edit-after.wav` (the same script, 18 cycles): the sound turns
  dark at about 6.22 s, 0.22 s after C's request at 6 s (before: about 28.6 s, a wait of 22.6 s).
