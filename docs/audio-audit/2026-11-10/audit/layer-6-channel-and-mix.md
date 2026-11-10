# Layer 6: the voice channel and the cylinders' mix

**What I read** (all paths under `audio_be/src/commonMain/kotlin/` unless a module is named)

- In full: `voices/voice_channel.kt`, `cylinders/Cylinders.kt`, `cylinders/Cylinder.kt`, `StereoBuffer.kt`,
  `AudioBackendContext.kt`, `PlaybackEngine.kt`, `PlaybackGain.kt`, `HouseStage.kt`, `voices/Voice.kt`,
  `voices/SoloRamp.kt`, `katalyst/KatalystContext.kt`, `katalyst/KatalystBusLadder.kt`, `katalyst/KatalystDuckEffect.kt`
  (from line 30), `effects/Ducking.kt`, `katalyst/KatalystChain.kt` (from line 60), `utils/numerical_safety.kt`,
  `warehouse/CylinderUnits.kt` (to line 102).
- In part: `voices/VoiceScheduler.kt` (`process`, `promoteScheduled`, `activateVoice`, `backgroundLevel`),
  `voices/VoiceFactory.kt` (`onsetFrame`, the gain and pan lines, `buildVoice`), `voices/SoloTracker.kt` (KDoc,
  `targetGain`, `isSoloed`), `PlaybackEngineDispatcher.kt` (`renderBlock` to `disposeDrainedEngines`),
  `effects/TailCeiling.kt` (KDoc, `observe`, `peakOf`), `ChainSwap.kt` (the `Idle` state and the top of `Fading`
  only), `effects/Compressor.kt` (the lookahead loop and `lookaheadStep` only), `filters/LowPassHighPassFilters.kt`
  (`DcBlocker` only), the `reset` and `hasTail` of `KatalystDelayEffect`, `KatalystReverbEffect`, `DelayLine`, `Reverb`.
- Docs: `audio/CLAUDE.md`, `docs/audio-backend-file-map.md`, `audio/ref/effects-mixing.md`, `audio/ref/katalyst.md`,
  `.claude/skills/review-loop/audio-constraints.md` in full; `audio/MEMORY.md` lines 11 to 106 and the Solo and
  hand-over entries; the leads in KLANG-00420; `stage-probe.txt` (the duck rows), `stage-cost.txt` (the duck and gain
  rows), `hot-path-lint.txt` (the rows of this layer).
- Specs, titles and the rows quoted below: `cylinders/CylindersOrderSpec.kt`, `CylindersCleanupTest.kt`,
  `CylinderCleanupTest.kt`, `CylinderKatalystParamsSpec.kt`, `voices/VoiceGainWireSpec.kt`, `VoiceHandOverSpec.kt`,
  `VoiceSchedulerSoloCutSpec.kt`, `effects/DuckingSpec.kt`.

**What I did not**

- `ChainSwap.kt` beyond the parts named, `ChainHost.kt`, `Crossfade.kt`, `KnobGlide.kt`, `TailRelease.kt`,
  `master/MasterBus.kt`, `KlangAudioRenderer.kt`, `Oversampler.kt` (so the rate buses' conversion quality is NOT
  audited here, only the routing around it), every Katalyst stage's inside, the slot writers.
- `audio/ref/architecture.md`, `numerical-safety.md`, `performance.md`: searched, not read through.
- `framing.txt`, `clicks.txt`, `aliasing.txt`, `dead-knobs.txt`: only through the leads in the ticket.
- No spec body was read end to end, and nothing was run (no Gradle, no probe).

## The probes' leads in this layer

| Lead | Verdict | Cause |
|---|---|---|
| Stage probe 1: one NaN on the sidechain latches the ducked bus for good | CONFIRMED | `effects/Ducking.kt:138-143`. `max(abs(NaN), x)` is NaN, `min(1.0, NaN)` is NaN, so `targetGain` is NaN; `NaN < currentGain` is false, so the release arm runs and stores `currentGain + coeff * (NaN - currentGain)`, NaN. From then on `finite < NaN` is false for ever. An infinity recovers because `min(1.0, Inf)` is 1. And yes, a NaN can reach a side chain in real playback: nothing guards the path (L6-01), and the channel itself can make one (L6-02). |
| Stage probe 3: the delay's constant 1.44e-21 | NOT MINE for the cause. DISPROVED as a lifecycle problem | The cylinder's two idle tests never read the ring: `Cylinder.isMixBufferSilent` compares against `SILENCE_FLOOR` (1e-5, `Cylinder.kt:706-717`), and `KatalystDelayEffect.hasTail` asks a `TailCeiling` fed from the INPUT peak (`KatalystDelayEffect.kt:411,422`, `TailCeiling.kt:158-179`), which decays geometrically whatever the ring holds. |
| Stage probe 4: the reverb's constant 9e-19 for ever, cost downstream | BY DESIGN for the cause (`audio-constraints.md`, `+ ANTI_DENORMAL`). DISPROVED downstream | Same two tests as above. A cylinder with a room deactivates on schedule; the residue is summed into the master only during the silent grace and is 13 orders under the floor every later test uses. |
| Stage probe 7: `pow(-1)` puts 1e15 on a silent bus | NOT MINE (the stage, the house limiter). One consequence here | Such a cylinder never reads as silent, so it never deactivates once a voice has woken it (`Cylinder.kt:648-651`). Whether `sustainsItself` covers `pow(-1)` decides if a stopped playback is ever released; I did not read `KatalystArithmeticEffect.soundsOnSilence`. |
| Hot-path lint: `KatalystDuckEffect.kt:254` | CONFIRMED, per duck LIFE, not per note | See L6-06. |
| Hot-path lint: `KatalystContext.kt:53` | BY DESIGN | One `StereoBuffer` per rate per context, kept (`KatalystContext.busAt` KDoc). |
| Hot-path lint: `PlaybackEngineDispatcher.kt:308, 321, 357` | NOT MINE | Periodic diagnostics. |
| Framing: "a voice joining a running cylinder" not covered | Read, by design | The owner is committed once per block (`Cylinder.commitOwner`), ledger D14; the grace is counted in visits, ledger D11. Both are stated in the code. See "Decisions worth revisiting". |

## Findings

### L6-01 Nothing stops a non-finite sample between a voice and the house limiter, and the duck turns one into a bus that is NaN for good

- **Severity**: major
- **Tag**: safety
- **Where**: `voices/voice_channel.kt:84-102` and `:130-137` (the mix loops); `StereoBuffer.kt:40-50` (`addFrom`);
  `cylinders/Cylinders.kt:146-162` (duck pass, mix); `effects/Ducking.kt:134-145` (`stepGain`), `:160-164`, `:204-211`;
  `cylinders/Cylinder.kt:706-717` (`isMixBufferSilent`); first guard: `effects/Compressor.kt:604-605` behind
  `HouseStage.kt:123-128`.
- **What**: Traced from one NaN sample of one voice on cylinder 1 (the kick), with cylinder 2 (the bass) ducking
  from it on the classic chain:
  1. `mixIntoCylinder` adds `signal * gainL` into the cylinder mix. No guard. The mix holds a NaN at frame i.
  2. The classic chain with its stages off leaves the dry mix as it is (delay and reverb are inserts that keep the dry;
     their own state is guarded, the dry path is not).
  3. `Cylinders.processAndMix` step 2 hands cylinder 1's mix to cylinder 2's duck. `stepGain` stores NaN in
     `currentGain` (mechanism in the leads table). Every later sample of cylinder 2 is multiplied by NaN, in both
     channels, every block. The gliding path does the same (`1.0 + w * (NaN - 1.0)`), also at weight 0.
  4. Step 3 adds cylinder 2's mix into the fusion mix (`addFrom`, whose KDoc says it passes non-finite values). The
     playback's bus, and through it the shared mix of ALL playbacks, is NaN on every sample.
  5. The house DC blocker passes the NaN (its state is flushed, so it does not latch). The house limiter's ring stores
     `finiteOrZero`. The whole backend's output is exact silence.
  6. It ends only when the duck's life ends: `Ducking.reset` or a new instance. That needs cylinder 2 to deactivate
     (`chain.reset`), which needs no voice on it for two blocks plus the silent grace, or the owner to switch the duck
     off for the 50 ms weight fade (`KatalystDuckEffect.endLife`). A bass line that keeps playing never lets go, so the
     silence lasts until the pattern stops. `isMixBufferSilent` reads a NaN mix as silent (both compares are false),
     which is what lets the cylinder deactivate and heal once its notes end.
  Without a duck the same NaN costs one or two zero samples on the whole output and then recovers.
  Where a NaN can come from, inside this layer: a non-finite `pan` (L6-02), and `Inf * 0`: an infinite voice sample
  times a gain that is exactly 0 (pan 0 makes `gainR` exactly 0; `gain(0)`; a solo background at multiplier 0), so a
  muted or hard-left voice turns an infinity, which the duck survives, into a NaN, which it does not. The stage probe
  says the trees produce non-finite output from poison input only, so the tree side is not shown reachable by me.
- **Reach**: any `duck` (`duck.cylinder` with a depth above 0) whose source cylinder ever carries one NaN sample.
  With L6-02: `s("bd").pan("0").pan(div(0))` on the source cylinder.
- **Settle it**: a spec on `Cylinders`: cylinder 3 ducks from 1, fill cylinder 1's mix with one NaN for one block,
  then finite input for 100 blocks with a voice checking in on 3; assert cylinder 3's mix is finite again. Red today.
  The cheapest fix candidates for the maintainer to weigh: `finiteOrZero` on the sidechain level in `stepGain` (one
  compare per sample, 5.2 ns stage today), or flush `currentGain`.
- **Confidence**: high for the mechanism and the path (read end to end, and the stage probe measured the latch);
  medium for the reach of a NaN source other than L6-02.

### L6-02 A NaN `pan` makes the voice's whole output NaN on both channels; `gain` is guarded twice, `pan` nowhere

- **Severity**: major
- **Tag**: bug
- **Where**: `voices/VoiceFactory.kt:363` (`pan = data.pan ?: 0.5`) against `:136` (`gain ... takeIf { it.isFinite() }`,
  with the comment "A non-finite gain reads as UNSET, like every other wire number"); `voices/voice_channel.kt:48-49`,
  `:86-87`, `:127-128`; `sprudel/src/commonMain/kotlin/lang/lang_dynamics_level.kt:258` (no guard) against
  `sprudel/.../SprudelVoiceData.kt:868-869` (gain and velocity guarded); `sprudel/.../SprudelVoiceValue.kt:52-56`
  (`Num(a / b)`, unguarded).
- **What**: `voice.pan.coerceIn(0.0, 1.0)` returns NaN for NaN (both compares in the stdlib body are false).
  `panAngle` is NaN, `cos` and `sin` are NaN, `gainL` and `gainR` are NaN, and every sample the voice writes into its
  cylinder is NaN, for its gate and its release. The cull does not see it: `measureVoicePeak` reads the buffer before
  the pan, so the voice measures as a healthy one and lives to its end frame. Result by L6-01: the whole output is
  silent for the voice's life, and any cylinder ducking from this one stays NaN after it. An infinite pan is fine
  (coerced to 0 or 1). The code states the rule it breaks: every wire number reads a non-finite value as unset.
- **Reach**: a pan pattern whose arithmetic yields NaN (0 divided by 0 in `SprudelVoiceValue.div`), or any front end
  that sends `VoiceData.pan = NaN` (the factory's own gain spec shows NaN crosses the wire).
- **Settle it**: copy `VoiceGainWireSpec` "a NaN gain from the wire renders exactly what an unset gain renders" for
  pan: expected the centre render, actual a NaN mix.
- **Confidence**: high for the engine side; medium for the exact sprudel expression (I read `div` and the pan setter,
  I did not run a pattern).

### L6-03 A cylinder's deactivation zeroes its delay ring and its reverb network inside the render block

- **Severity**: major
- **Tag**: realtime
- **Where**: `cylinders/Cylinder.kt:699` (`chain.reset()` in `tryDeactivate`); `katalyst/KatalystChain.kt:371-378`;
  `katalyst/KatalystDelayEffect.kt:757-767` and `effects/DelayLine.kt:406-412` (`buffer.clear()`);
  `katalyst/KatalystReverbEffect.kt:679-689` and `effects/Reverb.kt:263-269` (every line filled).
- **What**: `tryDeactivate` runs at the end of `processAndMix`, on the audio thread. For a cylinder that used a delay
  and a room, `chain.reset()` fills the whole ring (both channels, the ring's full capacity, which follows the delay
  time the pattern asked for) and every comb and allpass line of the reverb (about 28k samples by
  `audio-constraints.md`) in that one block. The project measured this class and moved it off the audio thread for
  every OTHER door: `KatalystChain.retire` hands units back dirty ("sixteen warmup cylinders retired in one block were
  ~19 MB of stores"), `SizedBuffers` clears in slices per block ("clearing there is O(ring) at the worst moment"),
  `Reverb.resetPart` exists for slicing. Deactivation still does the full clear, and
  `CylinderCleanupTest` "an inaudibly-charged delay ring is cleared LITERALLY on deactivation" pins that it does. At
  48 kHz a 0.5 s ring is 2 x 24000 doubles (384 KB of stores), a 4 s delay about 3 MB, in a block whose budget is
  2.67 ms. It happens once per idle gap of the cylinder: a sparse pattern (a hit every few seconds with an echo) pays
  it every gap. The work is unbounded by anything but the user's delay time.
- **Reach**: any cylinder with `delay(...)` or `room(...)` whose notes leave gaps longer than its tail plus the grace.
- **Settle it**: time `Cylinder.tryDeactivate` on a cylinder holding a class 0 ring and a network, and on one with
  `delay.time` 4; on Kotlin/JS it is the number that matters. A mutant that skips the ring clear shows which specs
  need the literal zero (the residue is under 1e-5 by the test that allows deactivation, but a lengthened tap can
  reach older cells, KLANG-00246).
- **Confidence**: high that the clear runs there; medium on its cost (not measured).

### L6-04 The duck's sidechain is another cylinder's mix before OR after that cylinder's own duck, by rent order

- **Severity**: minor
- **Tag**: consistency
- **Where**: `cylinders/Cylinders.kt:146-151`.
- **What**: Step 2 walks `rented` in rent order and ducks each cylinder in place, reading
  `sidechainCylinder.mixBuffer`. Take kick on 1, bass on 2 ducking from 1, pad on 3 ducking from 2. Rented 1, 2, 3:
  the bass is ducked at i = 1, the pad at i = 2 reads the DUCKED bass. Rented 3, 2, 1 (the pad's first note came
  first): the pad at i = 0 reads the UNDUCKED bass. Two cylinders ducking from each other are asymmetric the same way.
  Rent order is the order of each cylinder's first voice or `katalyst(...)` request, so the same song ducks differently
  depending on which pattern sounded first, for the engine's whole life. The sidechain is always this block's signal
  (step 1 finishes every chain first), and it is post-chain and post-fader of the source, which the KDoc states.
- **Reach**: chained or mutual `duck` across three cylinders or two.
- **Settle it**: `CylindersOrderSpec`-style row: the same three mixes, rented in two orders, compare cylinder 3's last
  sample. To make it one law, read every sidechain level before any duck writes (a second pass or a per-cylinder
  pre-duck peak buffer); which of the two readings is wanted is a maintainer decision.
- **Confidence**: high.

### L6-05 With more than `maxCylinders` rented cylinders, the ones past index 254 are never visited by the cleanup

- **Severity**: minor
- **Tag**: leak
- **Where**: `cylinders/Cylinders.kt:57` (`byId` has `2 * maxCylinders - 1` slots), `:165-168`, `:221-223`.
- **What**: A folded id keeps its sign, so the ids run from -254 to 254: up to 509 cylinders. The cleanup visits
  `rented[cleanupIndex % count]` and advances `cleanupIndex = (cleanupIndex + 1) % maxCylinders`, so `cleanupIndex`
  never exceeds 254. With `count` 256 or more, index 255 and up are never visited: once a voice has woken such a
  cylinder it stays active for ever, its chain runs every block, `Cylinders.anyActive()` stays true, and
  `PlaybackEngine.isIdle()` never turns true after a stop, so the stopped engine is never disposed and its rented
  units never return. With `count` at or under 255 but not dividing it, one cylinder is visited twice in a row at each
  wrap (harmless: one visit of grace).
- **Reach**: a pattern that uses more than 255 distinct cylinder numbers, which needs negative ones
  (`cylinder(...)` takes any Int). Unusual.
- **Settle it**: `Cylinders()` at its defaults, check in ids -1 and 0 to 254 with silent mixes, run `processAndMix`
  3000 times past the last check-in, assert none is active. `CylindersCleanupTest` "round-robin wraps around after
  maxCylinders" uses 2 and 2 and cannot see it. Fix candidate: wrap by `count`.
- **Confidence**: high (arithmetic only).

### L6-06 The duck builds a new `Ducking` on the audio thread at the start of every life

- **Severity**: minor
- **Tag**: realtime
- **Where**: `katalyst/KatalystDuckEffect.kt:253-259`, reached from `Cylinder.commitOwner` (`Cylinder.kt:387`).
- **What**: A life ends in `endLife` (the weight landed on 0, or `reset` at deactivation) and drops the instance; the
  next ON allocates a new one (an object and a `DoubleArray(2)`). Per life means: each reactivation of a ducked
  cylinder after an idle gap, and each time ownership alternates between a voice with a duck and one without for
  longer than the 50 ms fade. Not per block and not per note in the common case.
- **Reach**: `duck` on a cylinder with gaps, or shared with voices that carry no duck slots.
- **Settle it**: keep one instance per effect and `reset()` it at the start of a life; the identity spec then counts
  one `Ducking` over many lives.
- **Confidence**: high.

### L6-07 A voice that is NaN from its first sample is never culled, and the channel's KDoc says the opposite

- **Severity**: minor
- **Tag**: doc
- **Where**: `voices/voice_channel.kt:163-169` (the comment), `voices/Voice.kt:433` and `:475-486`.
- **What**: The comment says a block of NaN measures as silence, "which is how a voice that blew up in its release
  stops rendering and stops feeding the bus". That holds only for a voice already `heard`. A voice whose output is NaN
  from the start never sets `heard` (`peak` is 0, `silent` is true), and `if (heard && isReleasing)` never counts, so
  it feeds NaN into its cylinder until its end frame. A heard voice that turns NaN during its gate is not measured at
  all until the release. So the cull bounds a NaN voice by the cull window only in one of three cases.
- **Reach**: any voice with a broken tree; with L6-01 it sets the length of the dropout.
- **Settle it**: `VoiceLifecycleSpec` row: an ignitor that writes NaN from frame 0, a 50 ms gate, a 5 s release;
  expected by the comment: culled 50 ms into the release; actual: alive to the end frame.
- **Confidence**: high.

### L6-08 Three ref pages and two KDocs describe a cylinder manager that no longer exists

- **Severity**: minor
- **Tag**: doc
- **Where**: `audio/ref/effects-mixing.md:33` ("up to 16 effect buses (cylinder IDs 0-15)") and `:38-45`
  (`ducking.applySidechain(...)`, "mix ... (with cylinder gain)", no owner commit); `audio/ref/architecture.md:74` and
  `:160` ("16 cylinders"); `docs/audio-backend-file-map.md` (`Cylinders.kt`: "`Map<cylinderId, Cylinder>`");
  `audio/CLAUDE.md` (`mixIntoCylinder` "(gain, pan, sends, peak)"); `audio/ref/katalyst.md:96` and
  `Cylinder.kt:231` ("a visit is `tryDeactivate`, once per block").
- **What**: The code has `MAX_CYLINDERS = 255`, signed ids, an array and a rent-ordered list, no gain in the mix step
  (the fader is the chain's `gain` stage), no sends, and one visit per cylinder every N blocks where N is the number
  of RENTED cylinders, so "10 blocks" of grace is 10 x N blocks. The class comment at `Cylinder.kt:45-53` has it right.
- **Reach**: readers.
- **Settle it**: read side by side.
- **Confidence**: high.

### L6-09 Hard right is not exact: `cos(PI / 2)` leaves 6.1e-17 of the voice in the left channel

- **Severity**: note
- **Tag**: sound
- **Where**: `voices/voice_channel.kt:49`, `:86`.
- **What**: The law is constant power: `L = cos(pan * PI/2)`, `R = sin(pan * PI/2)`; centre is 0.7071 on both sides
  (-3.01 dB), pan 0 is exactly (1, 0), pan 1 is (6.12e-17, 1). Out-of-range pan is clamped to the ends, the one clamp
  in the channel (the Motor's raw rule would pass it; a pan of 1.5 would give a negative left gain). The source is
  always mono: one voice buffer, so there is no stereo source case. 324 dB down is inaudible; it matters only to a
  spec that asserts exact silence on one side, and to anyone who expects a hard-right voice to leave the left bus's
  filters at rest.
- **Reach**: `pan(1)`.
- **Settle it**: `cos(1.0 * (PI / 2.0))` printed.
- **Confidence**: high.

### L6-10 The solo ramp's last sample is `from + (to - from)`, not `to`, and the two mix paths round differently

- **Severity**: note
- **Tag**: consistency
- **Where**: `voices/voice_channel.kt:84-87` (constant: `signal * (cos * (gain * multiplier))`) against `:127-136`
  (ramped: `signal * ((cos * gain) * multiplier)`, multiplier `from + delta * (idx + 1) / blockSamples`).
- **What**: No zipper and no double application: `gain` (velocity folded in before the wire), the multiplier and the
  pan law multiply once, here; the ramp is linear over the whole block, read at the voice's own frames, and the next
  block starts where it ended. Two ulp-sized seams remain: `from + (to - from)` can differ from `to` in the last bit,
  and the two paths associate the product differently. `PlaybackGain` and `Ducking.processStereoGliding` write their
  ramps from the block's end to land bit for bit; the channel does not. Inaudible (1e-16 relative).
- **Reach**: every solo transition.
- **Settle it**: none needed; listed so a later bit-exact spec is not surprised.
- **Confidence**: high.

## Performance candidates

| Where | What | Per | Measured cost | Expected gain | Risk to the sound |
|---|---|---|---|---|---|
| `Cylinder.kt:699` | The full ring and network clear at deactivation (L6-03): hand the units to the sliced housekeeping, or skip the clear when the ceiling says the content is under the floor | cylinder idle gap | not measured; 384 KB to several MB of stores in one block | removes the largest single-block spike of this layer | a ring not literally zero at reuse (KLANG-00246's lengthened tap can reach it) |
| `Cylinders.kt:165-168` | Round-robin of ONE visit per block over all RENTED cylinders, inactive ones included: a silent cylinder's chain runs 10 x N blocks before its first tail question (16 rented: 0.43 s; 64: 1.7 s) each time it falls silent. A visit costs at most 256 compares, far less than one chain block | block | chain cost per silent cylinder per sample: reverb 35 ns, body 58 ns (`stage-cost.txt`) | with many sparse cylinders, most of the idle chain time | changes WHEN a teardown lands (ledger D11: the phaser's restart); a decision, see below |
| `voice_channel.kt:48-49, 86-87, 127-128` | `cos` and `sin` of a pan that is a `val` for the voice's life, computed per voice per block | block per voice | not in `stage-cost.txt` | two trig calls per voice per block; small | none (same numbers if computed once with the same expression) |
| `voice_channel.kt:23, 113, 150`; `Cylinder.kt:321, 403, 416, 420, 639` | Doubles crossing calls on Kotlin/JS: `mixIntoCylinder` and `measureVoicePeak` RETURN a non-integral double per measured block; `mixRamped` takes `panAngle`; see the first suspicion for the frame arguments | block per voice | not measured | 0 to 2 heap numbers per voice per block where V8 does not inline | none |
| `PlaybackEngine.kt:205-222` | The own-bus path clears and re-adds one block per engine while a master or a gain is in play | block | trivial | none worth taking | none |

## Suspicions

- **The absolute frame leaves V8's Smi range after about 6.2 hours of backend uptime at 48 kHz** (2^30 frames; 12.4 h
  without pointer compression). `audio/ref/performance.md:265-267` says an integral double in Smi range crosses a call
  as a small integer and a non-integral or larger one as a heap number. `blockStart` and `cursorFrame` cross many
  calls per voice per block in this layer (`Cylinders.offer` / `checkIn`, `Cylinder.offer` / `checkIn`, `isNewer` with
  two start frames, `claimsCylinder`, `voiceCheckedIn`, `tryDeactivate`, `processAndMix`, `renderInto`). If V8 does not
  inline them, a long session starts allocating several heap numbers per voice per block. Settle: the allocation probe
  on the production bundle with the clock started at 2^31 frames.
- **A ringing insert under a full duck or a closed fader is reset while it still rings.** The silence test reads the
  mix after the fader and after the duck (`Cylinder.kt:648`). A body, a phaser or an eq bell holds no declared tail,
  so a cylinder ducked to 0 for the whole grace with no voice checked in deactivates and its chain is reset. Nothing
  is heard at that moment; the tail is missing when the duck lets go. Needs a source held above 0.5 for 10 x N blocks.
  Settle: a row with a q 200 bell, depth 1, a held source.
- **`mixRamped`'s `(idx + 1)` is probably unpinned.** `VoiceHandOverSpec` compares a handing voice with a converting
  one (both shift together) and `VoiceSchedulerSoloCutSpec:1088` bounds the per-sample step by slope plus 1/128 of the
  amplitude, which a ramp ending one sample short (`idx` for `idx + 1`, a seam step of delta/128) also meets. Settle:
  run that mutant.
- **A solo amount outside 0 to 1 from a front end other than sprudel** gives a background multiplier of `1 - amount`:
  2 inverts the background at full level. MEMORY says a NaN or infinite amount ends the solo; I did not read
  `SoloTracker.record` to confirm the finite upper side. Raw by the Motor's rule if so.
- **A non-finite `releaseSec` in `VoiceFactory.buildVoice`** would give `endFrame` NaN: `blockStart >= NaN` is never
  true, `length` is 0, and the voice checks in on its cylinder for ever (the cylinder stays `Held`). `treeLifetime` is
  said to floor at 0; I did not read it. Not mine, named for the voice auditor.

## Decisions worth revisiting

- **The newest `Sounding` voice owns the bus, on a default cylinder everything shares** (accepted: "cylinder
  last-writer-wins"). Traced with a 4 s pad with `room` and hats without, both on cylinder 0: each hat is the newest
  voice while its gate is open, `commitOwner` applies its map, the reverb's feed glides to 0 over 50 ms and the room
  drains; when the hat's gate closes the pad owns again and the feed glides up from 0. The pad's reverb send is
  amplitude-modulated at the hat rate. Nothing clicks, and the rule is documented, but a beginner's
  `stack(drums, pad.room(.5))` meets it first. An alternative worth one listening session: a slot the newest owner did
  NOT write keeps the previous owner's value instead of falling to the authored default.
- **The cleanup's round-robin (ledger D11).** One visit per block was chosen when the tail question was a scan. It is
  two compares now (`TailCeiling`), and the silence test exits at the first loud sample. Visiting every active
  cylinder every block would make the grace a time (27 ms) instead of `10 x rented x block`, remove L6-05, and stop
  idle chains sooner. It moves every teardown instant, so it is a sound decision.
- **`pan` is clamped to 0 to 1 while `gain` is raw.** Either is defensible; the pair is inconsistent with "the Motor
  stays raw", and the clamp hides NaN from nobody (L6-02).

## Listening tasks

- A voice entering a solo rises from the background level to 1.0 over ONE block (2.7 ms at 48 kHz, 1.3 ms at 96 kHz),
  and a falling one takes two blocks. Listen for a tick on a low sine when `solo()` is added to its own pattern.
- The constant-power law at centre: two voices at pan 0.5 sum to +3 dB over one hard-panned voice of the same gain.
  Is that the loudness the maintainer wants for the default pan?
- The owner flicker described above: a pad with `room` or `delay` sharing cylinder 0 with a busy drum pattern.
- A cylinder ducking from itself (`duck.cylinder` = its own number): the instantaneous attack makes the gain follow
  the waveform, so it is a distortion, not a pump (stage probe row 118). Wanted, or worth a line in the door's KDoc?
- Chained ducks in both rent orders (L6-04): which reading sounds right.

## Test truth

- **Pinned**: the pan law and the order of the products at ONE pan (0.35), by raw bits (`VoiceGainWireSpec:193`);
  `gain` from the wire, non-finite included; the summing order by rent (`CylindersOrderSpec:35`); the duck's lookup by
  raw id; the newest owner and its handover (`CylinderKatalystParamsSpec:244, 257`); deactivation, the grace, the
  literal clear, the duck in the lifecycle set (`CylinderCleanupTest`); the rate buses (`VoiceHandOverSpec`).
- **Not pinned**: pan at 0, 0.5 and 1, out of range, and non-finite. Mutants that should survive: delete
  `coerceIn(0.0, 1.0)`; replace it by `coerceIn(-1.0, 1.0)`.
- **Not pinned**: a non-finite sample anywhere on the path voice, cylinder mix, sidechain, fusion mix. `DuckingSpec`
  feeds NaN to the KNOBS only; no row feeds a NaN sidechain or a NaN mix (the stage probe is the only reader).
- **Not pinned**: the duck pass's order (L6-04); the cleanup with a count that does not divide `maxCylinders` or
  exceeds it (L6-05: the wrap test uses 2 and 2); a NaN mix reading as silent in `isMixBufferSilent` (a mutant that
  treats NaN as loud would pin a poisoned cylinder for ever and stay green).
- **Not pinned, probably**: the ramp's `(idx + 1)` (see Suspicions).
- **Pinned on purpose but costly**: "cleared LITERALLY on deactivation" fixes the behaviour L6-03 questions.

## For the audit ruleset

- The question "does anything stop a non-finite value between A and B" worked best asked per EDGE of the signal flow
  (voice to cylinder, cylinder to sidechain, cylinder to fusion, engine to house), not per stage: the stages are
  guarded, the edges between them are where nothing is.
- Tracing one concrete song shape through one block (two voices, two settings, one cylinder; three cylinders, two
  ducks) found the order and ownership effects that reading the KDocs alone presents as settled.
- The brief pointed at `PlaybackGain.kt` for solo; solo lives in `VoiceScheduler.process`, `SoloTracker` and
  `SoloRamp`. A file list per layer generated from the code would save the first hour.
- The KDocs here are long and mostly true, which makes the few stale sentences hard to see. A rule that a KDoc states
  the law and leaves the review history to the ticket would make the next audit faster.
- `stage-cost.txt` has no row for the channel or for `Cylinders.processAndMix` with idle cylinders, and no JS column;
  every performance line of this layer is therefore unmeasured. A cost row for "N rented, silent cylinders" and one
  for "deactivation with a ring" would turn two findings into numbers.
- Layers overlap at `ChainSwap`, the stages' `reset` and the house stage; the brief should say which auditor owns a
  shared file so that it is read once and not zero times.
