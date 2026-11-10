# Disproof: realtime and lifecycle

The second reader of 32 findings from layers 1 to 9 (work on the audio thread, leaks, state that outlives its owner,
platform differences). Every number below comes from the engine, run by me; where a finding is judged by reading
only, its section says so.

**What I ran.** All with `KLANG_PROBE=audit`, `JAVA_HOME=/opt/dev/sdkman/candidates/java/21.0.12-amzn`, each call through
`console/with-build-lock.sh` and `test-digest run`:

1. `./gradlew :audio_be:jvmTest --tests io.peekandpoke.klang.audio_be.audit.AuditRealtimeRepro --rerun`, twice: the
   first run (14 tests) and a second after refinements (15 tests: the drain-end block classified on its own, the render
   path warmed before first-block timings, a region voice, the L5-07 diagnostics, the isolated onset). test-digest both
   times: `AuditRealtimeRepro RAN, 0 failed`, exit 0. The numbers quoted are from the second run; the first agrees within
   about 10 to 30 percent on every quoted row except the allocation-heavy first-activation rows (stated where used).
   Output: `.build-claude/audio_be/probes/audit-realtime.txt`.
2. `./gradlew :klang:jvmTest --tests io.peekandpoke.klang.audio_engine.audit.AuditRealtimeFrontendRepro --rerun`: the
   first attempt did not compile (my test event's property `sound` hid the interface's), fixed; then
   `AuditRealtimeFrontendRepro RAN, 2 tests, 0 failed`, exit 0. Output: `.build-claude/klang/probes/audit-realtime-frontend.txt`.

**Method.** The cost probe's clock (`nanoNow`) and per-thread byte counter (`allocatedBytesNow`; its own reading overhead
measured at 0 B and subtracted), medians over repeated runs, a discarded warm-up pass, a control row in each test
(ordinary blocks of the same run, the plain voice, the unshared tree, the configuration without the trigger). Block
times are whole `PlaybackEngineDispatcher.renderBlock` calls of the live dispatcher at 48 kHz, block 128, against the
deadline of 2666.7 us. One machine (16 processors, shared with other sessions), the JVM only: no Kotlin/JS number in this
report.

**What I only read.** L7a-05, L1-03, the announce half of L9-04, L9-06, L5-10, L9-07, L3-13, L8-08, L6-06, L7b-07,
the worklet's encode and structured clone (L1-06, L8-05, L9-08). The frontend half of L9-02 is reproduced with a latch
standing in for a slow query; the second door to it (N-R3) is read only.

| Finding | Auditor | Verdict | Now | One line |
|---------|---------|---------|-----|----------|
| L6-03 | major | CONFIRMED, SMALLER | minor | The deactivation clear costs 32 us (0.25 s delay) and 70 us (reverb), 1 to 3 % of a block; only long delays are expensive (L7b-06) |
| L7b-06 | major | CONFIRMED | major | 10 s delay: 571 us to clear, 953 us where the drain end and the deactivation share a block; a missed block from about a 32 s delay |
| L4-09 | major | CONFIRMED | major | Shimmer at wet 0: 774,520 B and 71 us per note; 16 in one block 1.6 ms median, 4.8 ms max |
| L5-03 | minor | CONFIRMED | minor | Pluck 26 KB, superpluck 167 KB, shimmer 775 KB per note, never pooled |
| L5-04 | minor | CONFIRMED | minor | The gated-off tail is 11 of the 12 us and 3.6 of the 5.8 KB of a default saw build |
| L2-06 | minor | CONFIRMED | minor | 16 default saws starting together: 513 us (19 %); one isolated note's onset block 60 us |
| L5-09 | minor | CONFIRMED, SMALLER | note | First-block allocation: 80 B for a region voice, 0 B for the others measured |
| L6-06 | minor | CONFIRMED, SMALLER | note | One small `Ducking` (and its `knobArgs` array) per life |
| L7b-07 | minor | CONFIRMED, SMALLER | note | Same as L6-06 |
| L1-06 | minor | CONFIRMED, SMALLER | note | 104 to 352 B and under 1.3 us per report on the JVM, 47 reports a second |
| L8-05 | minor | CONFIRMED, SMALLER | note | Same measurement as L1-06 |
| L9-08 | minor | CONFIRMED, SMALLER | note | Same measurement as L1-06 |
| L9-02 | major | CONFIRMED | major | Both halves reproduced: `[Cleanup, ScheduleVoices]` on the JVM dispatchers, the straggler starts 40 s after the stop, the engine never ends; the browser cannot reach it today |
| L2-02 | minor | CONFIRMED | minor | 64 frames apart: 0 of 64 victim frames in one block, 63 of 64 across a border |
| L2-03 | minor | CONFIRMED | minor | 49.9 % of block-aligned stamps one frame early at a Unix start; nothing audible follows |
| L9-03 | minor | CONFIRMED | minor | A playback's dyadic-grid notes are either all early or all exact, half the playbacks each |
| L2-07 | minor | CONFIRMED | minor | 60 s gate: full level 29 s after the stop, engine kept to 60.08 s; release 1e6 lives 11.6 days |
| L6-05 | minor | CONFIRMED | minor | 256 cylinders: 1 never cleaned; 509: 254 never cleaned; negative ids reachable |
| L7a-05 | minor | CONFIRMED | minor | By reading: a pad without twins never reports a tail |
| L5-07 | minor | CONFIRMED | minor | A shared body node freezes into a 128-frame loop at saturation; the auditor's example (the input read twice) does not |
| L5-06 | minor | CONFIRMED | minor | Under detune(-12) the voice is cut at -14.7 dB in mid-release |
| L5-02 | major | CONFIRMED | major | One name (`ignitor-0`) for two trees whose renders differ by 3 dB RMS and in waveform |
| L1-03 | minor | CONFIRMED | minor | No app path restarts a playback object; the library API does |
| L9-04 | minor | CONFIRMED | minor | Measured: the restart's voices land at gain 1.0 after 0.3 was asked |
| L9-05 | minor | CONFIRMED | minor | 100 synth notes: 100 `NotFound` commands, 100 backend store entries, 100 preloader entries |
| L9-06 | minor | CONFIRMED | minor | By reading: one PCM per requested note in the worklet |
| L5-10 | note | CONFIRMED | note | By reading |
| L9-07 | minor | CONFIRMED | minor | By reading: left channel at 48 kHz against the average at the file's rate |
| L3-13 | minor | CONFIRMED | minor | By reading, the stereo half of L9-07 |
| L8-08 | minor | CONFIRMED | minor | By reading: the master's latency is not carried; the JVM reports 0 |
| L9-10 | minor | CONFIRMED, LARGER | minor | `ReplaceVoices` is O(incoming x active) in the real shape; 9.2 ms and 13.6 MB at 1000 x 1000 on the JVM |
| L2-09 | note | CONFIRMED, LARGER | minor | Same measurement as L9-10 |

## Buffer clears in the render block

### L6-03

**Verdict**: CONFIRMED, SMALLER.

**What I checked**: `cylinders/Cylinder.kt:639` to `:704` (`tryDeactivate`; `chain.reset()` at `:699`, after the
silence grace, the tail question and the check-in); `katalyst/KatalystChain.kt:371` (`reset`) against `:389` (`retire`,
units handed back dirty); `katalyst/KatalystDelayEffect.kt:757` (`reset` keeps the ring and calls `DelayLine.reset`);
`effects/DelayLine.kt:406` (`buffer.clear()`, the whole ring, both channels); `effects/Reverb.kt:263` (every line) and
`:278` (`resetPart`, used only for idle units). The claim about the warehouse holds: `warehouse/SizedBuffers.kt:230`
(`giveBack`) is O(1) and marks a buffer dirty, `:268` (`housekeep`) zeroes one class-0 ring per block, `KatalystChain.retire`
returns units dirty; the one synchronous clear left there is `rent`'s `take` (`:210`, `syncCleans`) when nothing clean
fits, bounded by twice the request. `CylinderCleanupTest` "an inaudibly-charged delay ring is cleared LITERALLY on
deactivation" pins the full clear.

**What I measured**: test "L6-03, L7b-06: the block in which a cylinder with a delay of 0.25 s or 10 s, or a reverb,
deactivates" (one cylinder, a 50 ms saw note every 2 to 24 s, wet 1, feedback 0, the deactivation blocks against the
same run's ordinary blocks):

| Owner's bus | Deactivation block, median | Ordinary active block | Share of the deadline |
|-------------|----------------------------|-----------------------|-----------------------|
| delay 0.25 s (class-0 ring, 24064 frames, 385 KB) | 32.4 us (n 25; run 1: 46.8) | 3.3 us | 1.2 % |
| reverb size 5 (27688 samples) | 70.4 us (n 10; run 1: 66.2) | 6.6 us | 2.6 % |
| delay 10 s (class-5 ring, 770048 frames, 12.3 MB) | 570.6 us (n 8, max 1025; run 1: 546.6) | 2.6 us | 21.4 % |

The clears alone (test "L6-03, L7b-06: DelayLine.reset and Reverb.reset alone, hot and after a cache eviction, and a
fresh 10 s ring", 41 rounds): class-0 ring 21 to 36 us; class-5 ring 505 to 555 us hot, 488 to 525 us after writing 64 MB
elsewhere (max 1.5 ms); `Reverb.reset` 23 us (29 us after an eviction). About 45 us per MB here. The finding's own
examples (a 0.5 s ring, a room) cost 1 to 3 % of a block; a 4 s delay (class 3, 3.1 MB) about 140 us by that rate.

**Reach**: as stated, but the cost is the ring's class: under 3 % of a block for every delay up to 0.5 s and any reverb.
The long delays are L7b-06.

**On the proposed fix**: a sliced clear must still be finished before the next life reads the ring: a re-activation
inside the slicing window has to complete it (O(ring) again, in an onset block) or start the new life on the cleared
part, the same caveat as `rent`'s sync clean. For class-0 rings and reverbs the full clear costs less than a slicer's
bookkeeping would save.

**Severity now**: minor (was major).

### L7b-06

**Verdict**: CONFIRMED.

**What I checked**: the three call sites of `DelayLine.reset`: `KatalystDelayEffect.kt:470` (leave with an already silent
window), `:539` (the drain's last block), `:757` (`reset` at deactivation); and the switch-off's scan at `:467`
(`tapWindowPeakAbs()`, O(delay), `DelayLine.kt:233` to `:262`).

**What I measured**: the bus test, variant "delay 10 s switched off by the next note (delay.time 0)", six cycles; every
cycle has three heavy blocks:

- the switch-off block (`Active.leave` with the tap-window scan, 2 x 480000 reads): 596 us (22.4 %; run 1: 550);
- the drain's last block, which is also the deactivation block: the countdown ends after the 10-visit grace has passed,
  `line.reset()` clears the ring, and the same block's `tryDeactivate` finds no tail and runs `chain.reset()`, which clears
  the same ring again: 952.5 us (35.7 %), max 1478 us (55 %); run 1: 825 us;
- without a switch-off, the deactivation alone: 571 us (21.4 %).

And the first activation, which allocates the ring in the onset block: 1573 to 1781 us (59 to 67 %) with 12.36 MB
allocated in run 2, 682 to 840 us in run 1 (N-R1). The finding's "8 to 12 MB" per clear is right (12.3 MB at 48 kHz).
No 10 s case missed the deadline on this machine. Scaled by the measured rates (39 us per MB in the double-clear block,
45 us per MB for one clear alone), the drain-end block reaches about 1.9 to 2.2 ms at a class-6 ring (delays from 16 to
32 s, 24.6 MB) and about 3.8 to 4.4 ms, a missed block, at class 7 (delays from 32 to 64 s).

**Reach**: any delay of several seconds: at its first activation, at every switch-off, drain end and deactivation.

**On the proposed fix**: slicing is the shape, with L6-03's caveat. Two cheaper steps first: the deactivation should not
clear a ring the drain end has just cleared (it halves the worst block, N-R2), and the switch-off's scan is the same
O(delay) class and needs the same treatment.

**Severity now**: major (unchanged): work sized by a number the author writes, 21 to 67 % of a block in four kinds of
block at 10 s on a fast desktop JVM, a missed block from about a 32 s delay.

## Per-note allocation and build cost

### L4-09

**Verdict**: CONFIRMED.

**What I checked**: `ignitor/IgnitorDslRuntime.kt:2084` to `:2087` (the DSL arm builds the shimmer whatever `wet` is,
ring `SHIMMER_RING_FRAMES shl cache.regionStages`); `ignitor/IgnitorEffects.kt:584`, `:601` and `:602` (the ring is a
field initializer); `:781` (the Kotlin overload returns its input at `wet <= 0`: the two doors disagree).

**What I measured**: test "L2-06, L4-09, L5-03, L5-04, L5-09: bytes and microseconds per voice build
(VoiceFactory.makeVoice) and per first block" (31 rounds of 40 builds, cases interleaved, after warm-up): a saw under
`Shimmer(wet = 0)` then `classic()`: 70.9 us (run 1: 74.3) and 774,520 B per build, against 12.2 us and 5,784 B for the
default saw voice. Test "L2-06: a burst of 16 simultaneous note starts, the onset block against the deadline (live
dispatcher)" (14 repetitions, fresh dispatcher each): 16 such voices in one block: 1587 us median (59.5 %), max 4850 us
(182 %, a missed block), 12.1 MB; run 1: 1483 us, max 3600 us.

**Reach**: any authored instrument with `shimmer`, wet 0 included; no built-in, no corpus song (the node is marked WIP).

**On the proposed fix**: gating the arm at a literal `wet <= 0` matches the Kotlin overload; a modulated wet still needs
the ring, which should then come from the warehouse's ring shelf (region-scaled), as the delay's does.

**Severity now**: major (unchanged).

### L5-03

**Verdict**: CONFIRMED.

**What I measured**: the build test: pluck 26,040 B (14.9 us), superpluck at its default 8 strings 166,816 B (30.6 us),
shimmer 774,520 B at any wet. The burst test: 16 superplucks 1017 us (38.1 %), max 2639 us (99 %), 2.6 MB; 16 plucks
732 us (27.5 %), 445 KB.

**Reach**: as stated; pluck and superpluck are built-ins in the corpus.

**On the proposed fix**: pooling the Karplus rings by length class, as the delay rings are, is the shape; the shimmer half
is L4-09.

**Severity now**: minor (unchanged); the shimmer half is major under L4-09.

### L5-04

**Verdict**: CONFIRMED (the share is larger than estimated).

**What I measured**: the build test: a bare saw (the same source registered without `classic()`) builds in 0.95 us and
1,384 B; the saw with every stage off (`adsr.on 0`) in 12.0 us and 5,024 B; the default saw voice in 12.2 us and 5,784 B
(run 1: 1.28, 13.4 and 14.3 us). The gated-off tail is about 11 us and 3.6 KB of a 12 us build, about 90 %. The auditor's
"about 10 of 17.6 us" used the cost table's `build_us`, which includes the first block.

**Reach**: every note of every built-in and every sample.

**On the proposed fix**: plausible as written; it must keep the draw order (a leaf query draws nothing), which this
measurement does not check.

**Severity now**: minor (unchanged).

### L2-06

**Verdict**: CONFIRMED.

**What I checked**: `voices/VoiceScheduler.kt` `process` calls `promoteScheduled`, which builds every voice due in the
block (`activateVoice`, `VoiceFactory.makeVoice`); `scheduleVoices` promotes at command time only what falls in the next
block, so a timeline note scheduled 1.5 s ahead is built inside the render call. Realtime notes are built at command time.

**What I measured**:

- warm builds: see L5-04; every classic stage on: 26.8 us and 19.5 KB.
- the burst test, onset block against the next blocks with the 16 sounding: default saw 513 us (19.2 %) against 24 us,
  128 KB; every classic stage on 1056 us (39.6 %); a 4x region voice 752 us (28.2 %); superpluck, pluck and shimmer above.
- test "L2-06: one note's onset block, isolated (a note a second) against dense (a note every second block)": one default
  saw note a second: onset block 59.6 us (2.2 %) against 3.2 us for the other blocks; a note every second block 51.8 us.
  In the bus test a cylinder's re-activation with a delay or a reverb (the note's build and the bus's new owner)
  costs 101 to 253 us. In place, one ordinary note costs about four times its warm build.

**Reach**: every timeline note.

**On the proposed fix**: in the worklet, and in the JVM backend's loop, "between blocks" is the same thread and the same
callback: building one block ahead moves a chord's cost to the block before, it does not spread it. Building when the
schedule arrives (1.5 s ahead, over the commands of a cycle) is the variant that removes the onset spike; it holds built
voices that long and must keep the rng's draw order.

**Severity now**: minor (unchanged). The rule question the lint raised still decides it; the per-note buffers of L4-09 and
L5-03 are what push a burst toward the deadline.

### L5-09

**Verdict**: CONFIRMED, SMALLER.

**What I measured**: the build test, first block of a fresh voice after 1500 warm renders per case: 0 B for the saw,
pluck, superpluck and shimmer voices; 80 B for a saw in a 4x region (the region's child `IgniteContext`,
`ignitor/OversampleRegion.kt:158`); 0 B at the second block. The drift-lane fallbacks and the sub-pool growth were not
exercised.

**Severity now**: note (was minor): 80 B per region voice.

### L6-06 and L7b-07

**Verdict**: CONFIRMED, SMALLER (by reading).

**What I checked**: `katalyst/KatalystDuckEffect.kt:254` (`Ducking(...)` on an ON without an instance), `:390` to
`:396` (`endLife` drops it), `effects/Ducking.kt:97` (`knobArgs = DoubleArray(KNOB_ARGS)`): one object of five fields and
one small array per life.

**On the proposed fix**: one instance per effect, reset at the start of a life, as the compressor does: right and cheap.

**Severity now**: note (was minor, both).

## Diagnostics on the audio thread

### L1-06, L8-05 and L9-08

**Verdict**: CONFIRMED, SMALLER.

**What I checked**: `PlaybackEngineDispatcher.emitDiagnostics` (960 frames counted in blocks: every eighth block at
48 kHz, every seventh at 44.1 kHz); `audio_jsworklet/.../KlangAudioWorklet.kt:207` to `:213` (every feedback through
`sendFeed`); `audio_be/src/jsMain/kotlin/WorkletContract.kt` (`encode_KlangCommLink_Feedback`, then `postMessage`); the
generated encoder (a fresh JS object for the 11-field `Diagnostics`, one array, one object per cylinder, a fresh 27-field
object for the warehouse even when the Kotlin snapshot is cached); `ResourceWarehouse.stats` (rebuilt only when a part's
version moves; every `Cmd.Sample.NotFound` moves it, L9-05).

**What I measured**: test "L1-06, L8-05, L9-08: bytes and time per diagnostics report on the JVM, idle and with 8
cylinders" (8000 blocks after 2000): 46.9 reports a second. Idle dispatcher: 104 B and 0.03 to 0.08 us per report. One
playback, 8 held voices on 8 cylinders: 352 B and 0.6 to 1.25 us per report. 4.8 and 16.1 KB per second. Blocks without a
report: 0 B.

**Not measured**: the worklet's encode and structured clone. By reading: 4 + N small JS objects and one serialization of
about 40 to 70 properties per report, on the audio thread, every eighth block.

**On L8-05's side remark**: with `Date.now()` (`KlangAudioWorklet.kt:53`, 1 ms grain) one block's headroom reading moves
in steps of 37.5 % of a block, 3.75 % after the 1/10 smoothing.

**Severity now**: note (was minor, all three).

## Lifecycle

### L9-02

**Verdict**: CONFIRMED (both halves reproduced).

**What I checked**: `PlaybackEngineDispatcher.engineFor` and `scheduledAgain` (a `Stopped` engine resumes; none, and one is
created), `VoiceScheduler.cleanup` (drops the context) and `ensureEpoch` (a new epoch at now); `KlangPatternScheduler.stop`
(cancels the fetcher, launches the `Cleanup` on `fetcherDispatcher`), `tick` and `requestNextCyclesAndAdvanceCursor`
(sends without looking at `running`); `klang/src/jvmMain/kotlin/index_jvm.kt:47` (`Dispatchers.Default` for both).

**What I measured**: backend, test "L9-02, L9-04: a ScheduleVoices after its playback's Cleanup, and a gain sent to a
stopped engine": a voice at 0, `Cleanup` at 1.0 s, then a `ScheduleVoices` for startTime 40 with the old
`playbackStartTime` 0: the engine goes from `Stopped` to `Playing`, the straggler starts at 41.000 s, and at 50 s and 90 s
the engine is `Playing` and rendered. With the engine already disposed (at 1.003 s) the straggler creates a fresh one:
started at 41.003 s, `Playing` at 55 s. Frontend, klang test "L9-02: on the JVM dispatchers a pattern query that is
running at stop() sends its cycle after the Cleanup": a pattern whose third query (the first of the fetch loop's tick)
blocks on a latch; `stop()` while it blocks; the `Cleanup` goes out on another thread; after the release the commands
sent after `stop()` are `[Cleanup, ScheduleVoices(1)]`.

**The race, judged**: real on the JVM; the window is one pattern query, once per stop. In the browser it cannot happen
through `tick`: one thread, and `stop()` runs while the fetcher is suspended. A second door, found on the way (N-R3): a
stop during the start's sample preload is swallowed and `begin()` sends the first cycle after the stop; on the JVM it
races the `Cleanup` the same way.

**Reach**: the JVM live player (`klangPlayer` on the JVM), at a stop during a query or during the start's preload. Not the
browser app today.

**On the proposed fix**: two frontend changes close both doors found: look at `running` right before each send of the
fetch path, and rethrow `CancellationException` in `preloadSamples`. A backend guard is cheap and robust: a straggler
carries the stopped playback's `playbackStartTime`, a restart a new one.

**Severity now**: major (unchanged), on the JVM host; the browser cannot reach it today.

### L2-02

**Verdict**: CONFIRMED.

**What I checked**: `voices/Voice.kt:327` and `:328` (`Pending` to `Done` on a cut), `voices/VoiceScheduler.kt:632` and
`:636` (the cut sweep before the cutter is built, then the removal).

**What I measured**: test "L2-02: two voices of one cut group 64 frames apart, inside one block and across a block
border" (attack 0, victim A on cylinder 1, cutter B on cylinder 2, A's cylinder read every block): both in block 8 (A at
1030, B at 1094): 0 of A's 64 frames before B's onset sounded, never more than one voice active. A at 1100 (block 8), B at
1164 (block 9): 63 of 64 sounded (the 64th is the sine's zero at phase 0), then A faded. Control 200 frames apart: 199 of
200.

**On the proposed fix**: a `Pending` victim whose onset lies before the cutter's should enter `Fading` from the cutter's
onset instead of `Done`; one whose onset is at or after the cutter's can stay `Done`.

**Severity now**: minor (unchanged).

### L2-03 and L9-03

**Verdict**: CONFIRMED; the consequence is inaudible.

**What I measured**: test "L2-03, L9-03: onset frames on a Unix-epoch timeline, a sweep, and what voiceElapsedFrames 1
does to the first block" (`BackendClock.secAt` and `VoiceFactory.onsetFrame`, the production functions), 200000
block-aligned cursors: start 0: 12280 one frame early (6.1 %); start 1000 s: 80474 (40.2 %); starts 1794268800.5,
1800000000.123 and 1794326400.25: 99732 each (49.9 %); 44.1 kHz with a Unix start: 99988 (50.0 %). Timeline notes on a
16th grid (0.125 s, 6000 frames) after a playback anchored at block 3: with a Unix start 20000 of 20000 one frame early,
with a start of 0 3640 of 20000. So for the timeline it is not "half of the notes" but "half of the playbacks, and then
every note of that playback on a dyadic grid": 0.125 s is exact in binary, so the anchor's rounding repeats in every note.

The consequence: the same voice placed on the border (elapsed 0) and one frame before it (elapsed 1), both rendered from
the border: with the default 10 ms attack, first sample 0 against -0.000328 (saw and pulse, -69.7 dB) and the largest
difference over 8 blocks 0.0039 to 0.0051 (-46 to -48 dB re the peak): the envelope one frame ahead of the oscillator, a
smooth difference, not a step. With attack 0 the two renders are identical: the oscillator keeps its own phase and the
sample playhead its own position (`SampleIgnitor`), so only clock-driven nodes see the frame. Every reader of
`voiceElapsedFrames` takes it as a position (`IgnitorEnvelopes.kt:138`, `IgnitorFilters.kt:354`, `PitchModFactories.kt:178`,
`:188`, `:297`, `:507`, `NoteClockIgnitors.kt:45`, `:68`); the two that compare it (`FilterHumanization.kt:93`,
`MemoizingIgnitor.kt:121`) start from sentinels. Nothing audible or contractual follows beyond the comment's guarantee and
a one-frame difference between a live and an offline render.

**On the proposed fix**: rounding to the nearest frame is safe within 0.02 frames (the grid at 1.8e9 s is 0.0114 frames at
48 kHz); counting the timeline from the backend's start instead of in Unix seconds removes the cause on both paths.

**Severity now**: minor (unchanged, both).

### L2-07

**Verdict**: CONFIRMED.

**What I measured**: test "L2-07: a stopped playback's long note keeps its engine, and a release of 1e6 s": one sine
voice, gate 60 s, `Cleanup` at 1.0 s: output peak 0.212 at 30 s, engine disposed at 60.077 s. A sine with `adsr.release`
1e6 and a 1 s gate: the voice ends 1,000,001 s after its onset (11.6 days); the cull cannot end it while the release stays
above the floor.

**Severity now**: minor (unchanged); the ordinary case is the decision `RealtimeVoiceSpec` pins.

### L6-05

**Verdict**: CONFIRMED.

**What I checked**: `cylinders/Cylinders.kt:166` and `:167`; sprudel's `cylinder(...)` takes any Int
(`sprudel/.../lang/lang_dynamics_cylinder.kt:32`, `asIntOrNull`), `VoiceFactory` reads it raw and the fold keeps the sign:
negative cylinders are reachable from a pattern.

**What I measured**: test "L6-05: with more than 255 rented cylinders, the ones past index 254 are never cleaned up":
defaults, every cylinder checked in once, 3000 silent blocks: ids 0 to 254: none active; ids 0 to 254 and -1: 1 still
active (rent index 255), `anyActive` true; ids -254 to 254: 254 still active (indices 255 and up).

**On the proposed fix**: wrap by `count`, read each block (it grows while cylinders are rented).

**Severity now**: minor (unchanged).

### L7a-05

**Verdict**: CONFIRMED (by reading; not rendered).

**What I checked**: `katalyst/KatalystParallelEffect.kt`: `hasTail()` asks every branch and `pads[i].hasTail()`;
`Pad.hasTail()` is `quietFrames < holdFrames` with `holdFrames` from the twins only, 0 without twins, and `countQuiet` runs
only when `holdFrames > 0`: a pad with a delay ring and no twins never reports. The cylinder's 10-visit grace does not save
the echo: the mix has been silent since the dry click left the pad, so the first visit after the delay's ceiling ends
deactivates and `reset` clears the pad.

**On the proposed fix**: right: hold for `max(twin tail, delayFrames)` quiet frames counted on the branch's output.

**Severity now**: minor (unchanged).

### L5-07

**Verdict**: CONFIRMED, with the example corrected.

**What I measured**: test "L5-07: inside a 16x region a shared node repeats one block once the region's frame counter
saturates": saturation at voice frame 134217727 (46.6 min at 16x, 93.2 min at 8x, 6.21 h at 2x). The auditor's example,
the region's input read twice (`y + y.lowpass(3000)`), does not freeze: through saturation and with a clock held still
the blocks keep changing, bit for bit as the control. The reason: `bindRegionInput` (`audio_bridge/.../IgnitorDsl.kt:2780`
and `:2781`) gives every read of the input its own `RegionInput` leaf, so it is not shared and has no memo. Any other node
shared in the body freezes: `z = y.lowpass(3000); z + z.highpass(100)` in a 16x region, through saturation, block-to-block
change 1.64, 1.34, 1.70, 2.05, 1.38, then 0.40, 0.010, 0.009, 0.002, 0.0004, 0.0003: a 128-frame loop once the
decimator settles. Control: the memo outside a region at a held clock repeats from the second block, as designed.

**Reach**: one voice held over 46.6 minutes in a 16x region (6.2 h at 2x) whose body shares a node: a held realtime note or
a very long timeline note. Rare.

**On the proposed fix**: a memo key that cannot saturate (a block serial in the context instead of the scaled frame count)
closes it for every node.

**Severity now**: minor (unchanged).

### L5-06

**Verdict**: CONFIRMED.

**What I measured**: test "L5-06: a Freq-relative release under a detune of -12": `sine.adsr(release = 200 / freq)` at
440 Hz, gate 0.1 s, rendered to the voice's end: plain, the voice lives 0.4545 s past the gate and its last block peaks at
0.0006 (-64 dB); under `detune(-12)` it lives 0.4545 s too and its last block peaks at 0.183 (-14.7 dB), cut there without
a teardown fade; under `detune(+12)` the release ends early and the last block is silent.

**On the proposed fix**: the spec named is right; the fix needs the tail at the detuned frequency: the detune arm can scale
a `Freq`-relative tail when its ratio is a literal, and should fall back to the teardown fade when it is not.

**Severity now**: minor (unchanged).

## Naming and announcing

### L5-02

**Verdict**: CONFIRMED.

**What I measured**: test "L5-02: two trees equal by structure and different by sharing get one name and make two
sounds": `p + p.lowpass(800)` with one `pluck` against `pluck() + pluck().lowpass(800)`: structurally equal, not the same
instance, `uniqueId()` `ignitor-0` for both. Built optimized (the optimizer keeps sharing by identity: its
reference-count pass and its identity-memo rewrite, `IgnitorDslOptimizer.kt`) and rendered 0.5 s at 220 Hz: RMS 0.0895 against 0.0631 (-3.0 dB), difference
0.0775 (-1.3 dB re the shared one); the shared tree rendered twice is identical. The announce-once gate therefore merges
two different instruments under one name.

**Does the pinning test document a decision?** `InlineDslRegistrarTest` ("structurally-equal but distinct DSL instances
collapse to the same name", "dedup is on the NAME") and `AnnounceOnceRegistry`'s KDoc ("two structurally equal trees have
one name by construction") document a naming decision taken in 2026-05 for independent instances. They do not consider
sharing, which became engine meaning with `@WireShared` (2026-10-10, `audio/MEMORY.md`: "Equal but distinct nodes stay
two"). The two decisions contradict each other for exactly this case and neither text decides it.

**Reach**: as stated, and also two instruments of one song that differ only in sharing (no edit needed). Audible when the
shared node draws dice or drifts.

**On the proposed fix**: the name must be a key over the graph (back-references for repeated instances), not over the
tree: a structural `equals` cannot see sharing.

**Severity now**: major (unchanged).

### L1-03 and L9-04

**Verdict**: CONFIRMED; the gain half of L9-04 measured.

**What I checked**: every caller that starts a cyclic playback: `src/jsMain/kotlin/spine/KlangCodePlaybackCtrl.kt:296` to
`:311` (a new playback per Play; stop drops it), `pages/SamplesLibraryPage.kt:153` and `jingles.kt:30` to `:32` (`playOnce`
per call), `src/jvmMain/kotlin/Main.kt:54` (one start), the MIDI rig's `RealtimePlaybackCtrl` (one durable realtime
playback; its `stop()` runs only from `KlangPlayer.shutdown()`, `KlangPlayer.kt:366`, at the player's end of life). No app
path restarts the same playback object; the library API (`ContinuousPlayback.start()` after `stop()`) does, and the scheduler's KDocs promise it.

**What I measured**: the L9-02 test's third part: play, `Cleanup`, `SetGain(p, 0.3)` at once (a restart's first command):
the stopped engine holds 0.3, is disposed at 0.504 s, and the restart's `ScheduleVoices` lands on a fresh engine at gain
target 1.0.

**Severity now**: minor (unchanged, both).

## Leaks

### L9-05

**Verdict**: CONFIRMED.

**What I checked**: `KlangPatternScheduler.kt:349` to `:351` and `:737` to `:743` (every queried voice becomes a
`SampleRequest`, no filter); `SamplePreloader.loadAndSend` (`NotFound`, the `sent` set); `Samples.get` (`resolveCache` keeps
the null); `SampleStore.addSample` (keeps a `NotFound` entry, `version++`). KLANG-00210 section 11 (archived) lists only the
`uniqueId()` maps.

**What I measured**: klang test "L9-05: every synth voice is preloaded as a sample, sent as NotFound, and kept by the
backend's store": a pattern of 100 distinct synth notes per cycle (`supersaw`) started on the JVM dispatchers: the start's
preload sent 100 `Cmd.Sample.NotFound`, one per note (the request carries the note); fed into a backend `SampleStore`, 100
entries kept; the preloader's `sent` set holds the same 100. An edit renaming the instrument (`updatePattern`) sent
nothing new in this rig: the edit's prefetch is a no-op once the look-ahead has run (N-R4); the new name's entries come
later, from the tick's look-ahead.

**Severity now**: minor (unchanged).

### L9-06

**Verdict**: CONFIRMED (by reading).

**What I checked**: `audio_be/src/jsMain/kotlin/JsAudioBackend.kt:355` to `:360` (each `Complete` chunked on its own);
`SampleStore` keyed by `SampleRequest`, note included, one `allocatePcm(totalSize)` per first chunk; the frontend's
`pcmCache` is keyed by the zone (`Sample`) but every request sends its own command; the JVM passes one array by reference.

**Severity now**: minor (unchanged).

### L5-10

**Verdict**: CONFIRMED (by reading).

**What I checked**: `IgnitorRegistry` has no removal; `WhiteNoise`, `BrownNoise` and `PinkNoise` take `nextNoiseUid()` at
construction; the app compiles the code at every Play and every update (`KlangCodePlaybackCtrl.runPlay`), so a noise
instrument is a new name per update.

**Severity now**: note (unchanged).

## Platform differences

### L9-07 and L3-13

**Verdict**: CONFIRMED (by reading).

**What I checked**: browser, `audio_fe/src/jsMain/kotlin/BrowserAudioDecoder.kt`: `new OfflineAudioContext(1, 1, 48000)`,
`decodeAudioData` (resamples to the context's 48 kHz and keeps the file's channels), `getChannelData(0)` (left only),
`MonoSamplePcm(sampleRate = audioBuffer.sampleRate)`; the fallback `AudioContext()` decodes at the device's rate. JVM:
`JvmWavDecoder.kt:134`, `JvmGenericAudioDecoder.kt:72`, `JvmMp3Decoder.kt:62` average every channel and keep the file's
rate. `audio/ref/sample-management.md:79` and `:137` state both.

**Severity now**: minor (unchanged, both).

### L8-08

**Verdict**: CONFIRMED (by reading).

**What I checked**: `JsAudioBackend.kt:155` to `:169` adds `HOUSE_LIMITER_LOOKAHEAD_SECONDS` only;
`PlaybackEngineDispatcher.latencyFrames` (the house plus the longest master chain) is not carried; `JvmAudioBackend` never
fills the three latency fields (0.0) over a 250 ms line buffer. Whether "nothing compensates an authored lookahead"
(`audio-constraints.md`) covers the UI's clock is not written down; reporting it is not audio compensation, so it is a
one-line maintainer decision.

**Severity now**: minor (unchanged).

## Command-time work

### L9-10 and L2-09

**Verdict**: CONFIRMED, LARGER.

**What I measured**: test "L9-10, L2-09: ReplaceVoices with 100, 1000 and 5000 voices against as many active ones", one
`Cmd.ReplaceVoices` handled between blocks, n incoming against n active timeline voices (median of 7, 3 at n 5000):

| Incoming | n 100 | n 1000 | n 5000 |
|----------|-------|--------|--------|
| at later starts (the frontend's real shape: only voices past now + 0.2 s) | 71 us, 1.5 KB | 9.2 ms (3.5 blocks), 13.6 MB | 243 ms, 381 MB |
| equal to the active ones (a resend, all deduplicated) | 28 us | 2.1 ms, 5.9 MB | 60 ms, 185 MB |
| at the same starts, the last field different (n x n full compares) | 120 us | 11.8 ms, 13.6 MB | 364 ms, 381 MB |

The work is O(incoming x active) in the real shape, where no start can match: `isDuplicate` fails on the start, but every
pair is visited. On the JVM every pair also allocates: `active.indices.firstOrNull { }` (`voices/VoiceScheduler.kt:294`)
iterates an `IntRange` through the generic `Iterable` path and boxes each index above 127 (13.6 MB at 1000 x 1000 is
16 B per pair; at n 100 the indices stay in the Integer cache, 1.5 KB). Kotlin/JS has no box, but the same visits.

**Reach**: every live edit and tempo change. A dense song (a few hundred incoming against one or two hundred active) costs
a few tenths of a millisecond on this JVM; 1000 x 1000 costs three and a half blocks.

**On the proposed fix**: an O(n) pre-check removes all of it in the real shape: when the earliest incoming start is after
the latest active timeline voice's start, nothing can be a duplicate. Otherwise index the active voices by start, and
iterate with a plain index loop.

**Severity now**: minor (L9-10 unchanged; L2-09 was note).

## New findings

### N-R1 The first activation of a long delay allocates and zero-fills its ring inside the onset block

- **Severity**: minor
- **Tag**: realtime
- **Where**: `audio_be/.../katalyst/KatalystDelayEffect.kt` `ensureRing` (around `:190` to `:230`);
  `warehouse/SizedBuffers.kt:142` (`rent` allocates on a miss); `SizedBuffers.allocateOrNull`.
- **What**: a cylinder's first `delay(time = 10)` (or a grow) rents a class-5 ring; with nothing on the shelf (a fresh
  backend, or a ring the shelf dropped over its budget) it allocates 12.3 MB in the block of the owner's first commit.
  Measured (bus test, "activation, unit rented fresh"): 1573 to 1781 us (59 to 67 %) with 12.36 MB allocated, 682 to
  840 us in run 1; a fresh `StereoBuffer(770048)` alone 236 to 400 us median, max 1.8 to 5.6 ms (a collection). The
  warehouse's KDoc ("A miss is cheap: the smallest class is ~0.4 MB") holds for class 0 only.
- **Reach**: the first long delay of a playback, and every grow.
- **Settle it**: the bus test's activation row; the fix's guard is the same row under a threshold.
- **Confidence**: high (measured).

### N-R2 The drain end of a long delay clears its ring twice in one block, after a switch-off that scans it

- **Severity**: minor (L7b-06's class; listed apart because the double clear has a cheap fix)
- **Tag**: realtime, performance
- **Where**: `KatalystDelayEffect.kt:467` (`tapWindowPeakAbs()` at the switch-off), `:539` (the drain's reset), then in the
  same block `Cylinder.kt:699`, `KatalystDelayEffect.kt:757`, `DelayLine.kt:406` (the same ring again).
- **What**: at 10 s: the switch-off block 550 to 596 us (2 x 480000 reads); the drain-end block 825 to 953 us, max 1478 us,
  because the countdown ends after the cylinder's 10-visit grace has run, so the same block's `tryDeactivate` resets the
  chain and clears the all-zero 12.3 MB ring a second time.
- **Reach**: a long delay switched off by a new owner (`delay.time` under the active minimum).
- **Settle it**: the bus test's two rows; a variant that skips the second clear when the ring was just reset should halve
  the second row.
- **Confidence**: high for the times (measured); the coincidence read from the code and seen in the timing.

### N-R3 A stop during the start's sample preload is swallowed: the scheduler then starts and sends its first cycle

- **Severity**: minor
- **Tag**: bug, lifecycle
- **Where**: `klang/src/commonMain/kotlin/KlangPatternScheduler.kt:363` to `:370` (`catch (e: Exception)` around
  `deferred.await()`), `runPlayback` (`begin()` follows the preload), `:423` to `:433` (the first `ScheduleVoices`).
- **What**: `stop()` cancels the fetcher while it awaits the preload's acknowledgements (up to 5 s at a first load).
  `CancellationException` is an `Exception`, so it is caught and printed, and `runPlayback` runs `begin()`: `begun` is set,
  the runtime metric is opened again, and the first cycle's `ScheduleVoices` is sent after the stop. On the JVM this send
  races the launched `Cleanup` (the L9-02 revival, the song's first cycle as the ghost, an engine that never ends). In the
  browser the dispatcher's order sends it before the `Cleanup`, which clears it, but `begun` and the "Sprudel runtime"
  metric stay as if started.
- **Reach**: Play, then Stop while the samples load.
- **Settle it**: a `KlangPatternSchedulerGainSpec`-style spec with a sample request that is never acknowledged, `stop()`
  during the preload, then the sent commands: a `ScheduleVoices` after `stop()`.
- **Confidence**: high for the path (read); the JVM race not run.

### N-R4 `updatePattern`'s sample prefetch does nothing once the look-ahead has run

- **Severity**: minor
- **Tag**: bug
- **Where**: `KlangPatternScheduler.updatePattern` (`lookAheadForSampleSounds(queryCursorCycles, sampleSoundLookAheadCycles)`),
  `lookAheadForSampleSounds` (`if (to <= sampleSoundLookAheadPointer) return`), `tick` (moves the pointer to the cursor
  plus nine cycles).
- **What**: the tick's look-ahead leaves the pointer at `queryCursorCycles + 9`; an edit asks for `[cursor, cursor + 8)`,
  whose end is under the pointer, so it returns at once. A sample new to the edit is then asked for by the backend when a
  voice is scheduled (`RequestSample`), racing that voice's onset, or by the tick's look-ahead eight cycles later. Seen in
  the klang test "L9-05: ...": after `updatePattern` with a renamed sound, no new request.
- **Reach**: every live edit that brings in a sample not loaded yet; its first notes are dropped as soundless when the load
  takes longer than the scheduling look-ahead.
- **Settle it**: a spec whose edit adds a sample sound, counting `Cmd.Sample` sends right after `updatePattern`.
- **Confidence**: high for the gate (read and observed); the soundless first note not run.

## For the audit ruleset

- Findings that named a cost without a number (L6-03, L7b-06, L9-10, the diagnostics) were the cheapest to settle and the
  ones whose severity moved: a realtime finding should carry a microsecond figure against the 2.67 ms block, measured or
  estimated from a stated rate.
- Warm microbenchmarks understate the block a note lands in: an isolated note's onset block is about four times its warm
  build (60 against 15 us), a bus re-activation ten to fifteen times. Realtime claims need a dispatcher-level block timing.
- Run the finding's own example: L5-07's mechanism holds, but its example does not reproduce, for a reason
  (`bindRegionInput`) only visible by running it.
- Say which platform a cost belongs to: the JVM's boxing in a generic stdlib call dominates L9-10's bytes there and does
  not exist on Kotlin/JS; the converse holds for the structured clone, which nothing here could measure.
- Cross-thread races are reproducible in a few lines with a latch in a fake pattern (L9-02); a lifecycle finding that
  spans the wire should name both a backend and a frontend reproduction.
