# KLANG-00429: Audit fixes 5: the Ignitor build (the fused filter's gate, instrument names, per-note buffers, the off stages' build cost)

> **Status (2026-11-11): DONE.** the shimmer's ring is sized and the per-note rings are rented from the warehouse; the gate through Variants is in KLANG-00437
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
| L5-01 | major | A fused filter loses its gate: an unset cutoff plays a 1 kHz filter | Confirmed: An unset `hpf.freq` slot plays a 1 kHz highpass when optimized: -26.3 dB at 220 Hz, through the registry too |
| L5-02 | major | The inline instrument's name is structural, the build shares by identity: an edit that only changes the sharing is not heard | Confirmed: One name (`ignitor-0`) for two trees whose renders differ by 3 dB RMS and in waveform |
| L5-03 | minor | Large per-note buffers are allocated at note-on and never pooled (shimmer ring, pluck rings) | Confirmed: Pluck 26 KB, superpluck 167 KB, shimmer 775 KB per note, never pooled |
| L4-09 | major | Shimmer allocates its 96000-sample ring at every voice build, on the audio thread | Confirmed: Shimmer at wet 0: 774,520 B and 71 us per note; 16 in one block 1.6 ms median, 4.8 ms max |
| L5-04 | minor | A gated-off stage still pays the whole build path: 5 to 10 us and several KB per note for a tail that builds one stage | Confirmed: The gated-off tail is 11 of the 12 us and 3.6 of the 5.8 KB of a default saw build |
| L5-05 | (read only) | A pitch node bends a musical oscillator in a SOURCE's parameter position, and a shared one depends on who renders first | not challenged; the auditor's reading in `layer-5-ignitor-build.md` |
| L5-07 | minor | Inside a region a shared node repeats one block for ever once the region's frame counter saturates | Confirmed: A shared body node freezes into a 128-frame loop at saturation; the auditor's example (the input read twice) does not |
| L5-08 | (read only) | A knob that reads a lifted LFO inside a region reads the upsampler's start-up at the first block, and pays a whole conversion per read | not challenged; the auditor's reading in `layer-5-ignitor-build.md` |
| L5-09 | note | Per-note allocations in render at the first block (the lint's leads, with their reach) | Confirmed, smaller: First-block allocation: 80 B for a region voice, 0 B for the others measured |
| L5-10 | note | Registered inline instruments are never dropped while a playback lives, and a noise instrument gets a new name at every evaluation | Confirmed: By reading |
| L5-11 | (read only) | The gate reads leaves only: the full table, both directions | not challenged; the auditor's reading in `layer-5-ignitor-build.md` |
| L5-13 | (read only) | What the parity specs do not reach | not challenged; the auditor's reading in `layer-5-ignitor-build.md` |

## The work

Per finding: read its report and its reproduction (`src/jvmTest/kotlin/audit/` in `audio_be`, `sprudel` or `klang`,
run with `KLANG_PROBE=audit`), fix it, and turn the reproduction's row into an ordinary spec that guards the fix
(mutation-checked). A finding that turns out to need a maintainer decision is not forced: it is listed under
"Decide" below with the options, and the rest of the ticket lands without it. How the fixes are made and reviewed:
`docs/audio-audit/2026-11-10/audit/FIX-BRIEF.md`.

## Decide

1. **L4-09 (the rest) and L5-03: the per-note rings.** Gated, a shimmer at a wet of 0 or less builds no ring now; a
   built shimmer (a wet above 0, or a modulated one) still allocates its 96,000-sample ring at every note, a pluck its
   2,500-sample line, a superpluck one per string (167 KB at 8). Pooling them needs more than a plain mechanism: no
   Ignitor tree gives anything back when its voice ends today, so a pool needs a return path from the voice's removal
   to the nodes that rented (a new lifecycle hook), a mono ring shelf with a byte bound, and deferred clearing (the
   warehouse's delay rings have all three, for one owner per cylinder). Options:
   - (a) A per-voice rental: the build rents from a mono shelf in `ResourceWarehouse` (class-sized, idle bytes
     bounded like `SHELF_BUDGET_BYTES`), the voice keeps the list of what its tree rented and the scheduler gives it
     back when it removes the voice (and a playback's disposal for the voices it drops); the shelf clears returned
     rings in `housekeep`. Owner: the warehouse; bound: its idle budget.
   - (b) Size the rings to what they read, at the build: the shimmer's grains reach back the highest pitch's rate times
     the grain length (0.3 s at the default `[0, 7, 12]`, 14,400 frames at 48 kHz, a sixth of today's ring), and a
     Karplus line needs the note's period with headroom for its pitch modulation. Smaller allocations, not fewer; the
     shimmer's read positions round differently in the last bits, a pitch drop past the sized headroom needs a grow or
     a clamp (a sound question).
   - (c) Build at schedule time (KLANG-00427's L2-06 option (a)): the allocation leaves the onset block, not the thread.
   Recommendation: (b) for the shimmer (one size computed from its pitch list), and decide (a) together with L2-06,
   with the numbers below: a burst of 16 superplucks still costs 520 us in its onset block, about 2.6 MB of rings.

2. **L5-05: a source's phase modulator under a pitch node, and the order it renders in.** The doc now says what the
   code does (`audio/ref/voice-synthesis.md`): a musical oscillator in a SOURCE's knob follows a vibrato above the
   source. The second half stays: one modulator shared by a source's knob and a filter's knob is one memo whose key
   does not hold the pitch mod, so whichever reader renders first decides for both. Options: (a) accept and document
   (done); (b) build a source's knobs under the source's mod as a cache key part (not applied), the house rule of the
   pitch nodes: the shared modulator forks into a bent and an unbent instance, each rendered once per block, order
   free (a modulator with dice decorrelates); (c) shield a source's knobs (`ModBlockingIgnitor`): the phase modulator
   no longer follows the vibrato, a sound change for PWM and phase modulation under `vib`, and against fm's rule D1.
   Recommendation: (b), because it is the existing rule; it is a question of what a phase modulator means, so the
   maintainer's.

3. **L5-08: a knob reading a lifted LFO inside a region.** At a voice's first block the read is the up-converter's
   start-up (a saw LFO's cutoff reads about the middle of its range for one block), and every block pays a whole up
   conversion to read one sample. Options: (a) a block-constant read of a lifted source reads the base-rate source
   directly: no start-up, no conversion, the knob earlier by the converter's 3 to 5 samples (inside one block,
   invisible to a per-block read); (b) prime the converter's state with the first sample (the start-up only);
   (c) accept. Recommendation: (a). A sound change (one block per note, for authored regions with an LFO in a knob),
   hence here.

4. **L5-10: the growth that is left.** A noise instrument no longer gets a new name at every evaluation, so an
   unchanged song stops growing. Every distinct EDIT still adds a name to the process-wide map (`globalIgnitorNames`,
   KLANG-00210 section 11's debt; `KlangSnapshotMap` copies itself at every new name) and two trees to the playback's
   fork. Options: (a) accept: per edit, a few KB, bounded by the page's and the playback's life; (b) bound the fork
   like the Katalyst's `MAX_CACHED_CHAINS`, which needs the frontend's announce-once set to forget the same names, or a
   reused name is never announced again and its voices are silent: a protocol change; (c) drop a name's trees when no
   scheduled voice has used it for some cycles. Recommendation: (a) until a live session shows it.

5. **L5-11: knobs the leaf rule does not read.** Pinned as it stands (`IgnitorGateSpec`): a knob behind `Variants`
   or an `OptimizerHint` is not a leaf, so its stage is built at the off value too. Both are draw-free and dissolved by
   the build; `buildTimeKnobValue` could look through them (the build's own pick). That changes what authored trees
   with such knobs build (a fold for most rows, a NaN closed for `drive`), and `x.drive(Ignitor.param("d", NaN).add(0))`
   (clamp-free arithmetic over an unset default) still renders an all-NaN voice: the numerical-safety contract would
   read a non-finite amount as unset at `DriveIgnitor`'s read. Recommendation: both, as one small change, after a yes.

### Decided (maintainer, 2026-11-11)

- **L5-03:** "yes size it to what it needs": option (b) for the shimmer's ring. Owed. The rule answer of the same day
  ("allocation per note: if we are able to avoid it or reduce it through the warehouse we should do it") asks for
  option (a), the per-voice rental, as well; it is planned with the maintainer before it is built.
- **L5-05:** "leave, that is fine" (accepted, documented).
- **L5-08:** "leave" (accepted).
- **L5-10, L5-11:** the maintainer asked for an explanation; open.

### Decided, second round (maintainer, 2026-11-11)

- **L5-10:** "that is ok". The built trees go with the playback's engine; only the names stay until the page reloads.
- **L5-11:** both parts, "as long as the added complexity is worth it". Owed; a part that turns out heavier than a
  small change goes to the backlog (KLANG-00437) instead.
- The optimizations backlog the maintainer asked for is KLANG-00437.

## Notes from the work (2026-11-10)

**Per finding.**

| Id | Outcome | What, where | Guard |
|---|---|---|---|
| L5-01 | fixed, sound change (no corpus song) | `IgnitorDslOptimizer.asFusibleSections`: a filter whose cutoff may be unset (`mayBeUnset`: a `Param` with a non-finite default, a non-finite `Constant`) is not fused, so the build's gate row still decides; a finite-default `Param` fuses as before | `IgnitorDslOptimizerRenderSpec` ("audit L5-01" rows: the four doors, passes 2, an authored NaN default, a literal NaN, an unset filter over a fused one; slot written and not; and a finite cutoff still fuses) |
| L5-02 | fixed (no corpus song) | `IgnitorDslIdentity.kt`: the name's key is `IgnitorDslNameKey`, the tree plus its sharing (a depth-first walk numbering non-leaf visits; a repeated instance adds its visit and its first visit). The registrar and its KDoc follow (`AnnounceOnceRegistry`) | `IgnitorDslIdentitySpec` (audio_bridge, common), `InlineDslRegistrarTest` ("audit L5-02": two announcements) |
| L5-03 | Decide 1 | | |
| L4-09 | fixed for a wet of 0 or less (the gate's promise), the rest Decide 1 | a gate row `shimmer`: unset or a wet `<= 0` (`gatedOffInner`); a fold, so no sound change; `audio/ref/off-values.md` has the row | `IgnitorGateSpec` ("shimmer: ...") |
| L5-04 | fixed, no sound change | the amplitude gate rows are asked in front of the build cache (`gatedOffInner`, the envelope's switch beside it in `buildIgnitor`) and their branches left `buildRaw`; the query reads the DSL leaf through one resolver (`Param.resolvedValue`, which the `Param` arm uses too) and builds and boxes nothing; the now dead `cache` parameter left the leaf helpers; a non-caching memo shares one empty buffer (`MemoizingIgnitor`) | `GatedStageBuildCostSpec` (JVM: the off tail of `classic()` under 400 B per build, measured 112 B); behaviour by the whole gate and tail suites |
| L5-05 | doc fixed, the order question Decide 2 | `audio/ref/voice-synthesis.md` says a source's knob follows the pitch mod | none (a doc) |
| L5-07 | fixed (no corpus song) | `MemoizingIgnitor`'s key holds the root context's frame count, which moves per block inside a region after the region's own count saturates | `OversampleRegionSpec` ("audit L5-07") |
| L5-08 | Decide 3 | | |
| L5-09 | accepted | 80 B once per region voice (the child context, made at the first block because the voice's context exists only after the build; `OversampleRegionIgnitor`'s KDoc says so); moving it needs a build-to-voice hook, more than it saves; the rule question stays KLANG-00420's | none |
| L5-10 | naming fixed, growth Decide 4 | the name's key sets every noise `uid` to 0 (sharing tells two noises from one read twice), so a re-evaluated script keeps its noise instrument's name | `IgnitorDslIdentitySpec` ("audit L5-10" rows) |
| L5-11 | pinned, the extension Decide 5 | the table gets the `shimmer` and `Eq` rows and the optimizer note (`audio/ref/off-values.md`) | `IgnitorGateSpec` ("a knob behind Variants or an optimizer hint is not a leaf") |
| L5-13 | parity extended | the optimizer against the unoptimized tree on every gate row's plain door (unset, off, on) and on `classic()` (nothing written, every stage on); the fuzz still has finite cutoffs and no bag (JVM only) | `IgnitorDslOptimizerRenderSpec` ("audit L5-13" rows) |

**One build fact moved by L5-04, behaviour-neutral by reading and by the suites:** a gated stage no longer takes a
cache entry, so a SECOND reference to it shares its inner's entry directly. The memo's freq key is then resolved from
the inner (`usesMusicalFreq(inner)`), where it was resolved from the gated node, whose off knobs could read `Freq`: the
answer is the stage that exists. A gated stage over a bare LEAF (`Constant(1).lowpass(unset)`) returns the leaf
unwrapped, where it was wrapped in a one-reader memo (pure delegation; same samples).

**The corpus, my own before against after** (no song text changed between the runs; the code hashes match too): every
render identical, 18 of 18 rows. None of the fixes reaches a corpus song: no song places a plain filter door on an
unset slot, uses a shimmer, two inline instruments that differ only in sharing, or a region voice held 46 minutes.

**Build cost and the burst** (`AuditRealtimeRepro`, JVM, 48 kHz, through `VoiceFactory.makeVoice`; before is this
tree before the first edit):

| Case | Build before | Build after | Burst of 16, onset block before | after |
|---|---|---|---|---|
| bare saw (no `classic()`) | 0.77 us, 1,384 B | 0.81 us, 1,376 B | | |
| saw, every stage off | 10.59 us, 5,024 B | 1.90 us, 1,624 B | 292.8 us | 118.1 us |
| saw, default voice | 10.94 us, 5,856 B | 3.52 us, 2,448 B | 319.5 us (max 424) | 153.5 us (max 259) |
| saw, every classic stage on | 21.72 us, 19,536 B | 21.41 us, 16,600 B | 634.0 us | 585.9 us |
| pluck | 13.22 us, 26,168 B | 5.79 us, 22,760 B | 366.4 us | 230.6 us |
| superpluck (8 strings) | 24.75 us, 166,992 B | 12.40 us, 163,584 B | 673.4 us (max 870) | 493.1 us (max 556) |
| shimmer at wet 0 | 76.08 us, 774,592 B | 2.66 us, 2,264 B | 1045.9 us (max 2470), 12.1 MB | 146.0 us (max 386), 73 KB |
| saw in a 4x region | 15.44 us, 8,656 B | 7.44 us, 5,000 B | 478.6 us | 308.5 us |

One isolated default saw note's onset block: 18.6 us before, 9.4 us after. The stage cost probe's `build_us`
(build plus first block): `classic()` all off 12.0 to 2.8, the saw voice with everything off 13.3 to 4.0, with its
default adsr 16.7 to 8.7, with vibrato 25.5 to 13.5, the sample instrument 19.0 to 12.0, a lowpass 14.7 to 5.9; the
`net_ns` rows unchanged within noise; the shimmer rows (built ON, wet 0.5 and 1) unchanged, 35 us, Decide 1.

**Mutation check** (`console/tools/mutate`): 13 mutants, 13 killed for the expected reason. L5-01: the lowpass arm
fuses an unset cutoff, `mayBeUnset` false for a `Param` (both by the "audit L5-01" row), `mayBeUnset` true for every
`Param` (by the "still fuses" row). L4-09: the shimmer row removed, its bound `< 0` (the gate spec's shimmer row).
L5-04: the gate behind the cache with a copy, the query building its leaf (both `GatedStageBuildCostSpec`), the off
envelope's tail dropped (`IgnitorGateSpec`'s tail row). L5-11: `Variants` read as a leaf (the pin row). L5-07: the root
count out of the key (`OversampleRegionSpec`). L5-02 and L5-10: no sharing in the key, the uid kept, leaves counted
(`IgnitorDslIdentitySpec`). Not mutation-checked: the shared empty memo buffer (equivalent by construction: a size-0
buffer is never written; a cost on JS only).

**Suites** (test-digest, all green): `:audio_be:jvmTest` 3118, `:audio_be:jsBrowserTest` 2938, `:audio_bridge:jvmTest`
160, `:audio_bridge:jsBrowserTest` 299, `:sprudel:jvmTest` 3523, `:klang:jvmTest` 83, root `:jvmTest` 163. No wire type
changed (the noise `uid` stays on the wire; only the name's key drops it), so the schema hash pin is untouched.

**Reproductions deleted:** L5-01 (`AuditSoundStagesRepro`), L5-02 and L5-07 (`AuditRealtimeRepro`). The build and
burst test stays: it carries L2-06 (KLANG-00427) and the open L5-03.


## Decisions pass (2026-11-11)

**L5-03 built for the shimmer (option (b)).** `shimmerRingFrames` (`IgnitorEffects.kt`) sizes the grain ring at the
build from the pitch list and the build's rate (the region's inside one): a grain at the rate `r` reads back
`max(r, 1)` grain lengths (150 ms), so the ring holds the largest of these plus two frames, 14,402 frames at the default
`[0, 7, 12]` at 48 kHz (115 KB) where it held 96,000 (768 KB). The pitch list is fixed per node and no grain reads a
run-time pitch, so the build knows the worst case and nothing is clamped; a list whose reach is at or past today's ring
(a pitch above about +44.8 semitones) keeps today's ring, so the sizing never grows one. `GraphCensus` sizes it the same
way (at 48 kHz). The Karplus lines are not touched. Sound: the read positions round differently in their last bits (the
ring's length enters the modulo of a grain's start); no corpus song has a shimmer.

Guards: `ShimmerRingSizeSpec` (the size law at eleven pitch lists, a 2x region, a non-finite and a huge pitch; a sized
ring renders the full ring's output within 1e-7 for four pitch lists with feedback 0 and 0.9; the control: a ring 300
frames short differs by more than 1e-3), `ShimmerBuildBytesSpec` (JVM: 117,304 B per build of a saw under a shimmer, the
bound 250 KB; 774,520 B before). Mutants KILLED: the reach without its floor of 1, the headroom taken away, the build
site passing today's ring. Cost probe: the shimmer rows' `build_us` 49.8 to 12.8 and 42.4 to 13.3; `net_ns` unchanged
within the run's drift.

**L5-11 (ii) built: a non-finite `drive` amount reads as unset at `DriveIgnitor`'s read** (unset is off for the drive,
its gate row): `!(amt > 0.0) || amt - amt != 0.0` passes the input through, so `x.drive(Ignitor.param("d", NaN).add(0))`
no longer renders all NaN and an infinite amount no longer an infinite gain. A one-line read guard, not a clamp: every
finite amount drives as before. Guard: `DriveAmountNonFiniteSpec` (NaN, +-Infinity pass the input bit for bit; 0.5 drives
by `DistortionCore.drive`). Mutants: the original read `amt <= 0.0` KILLED (the NaN row), the infinity half taken out
KILLED; a first mutant that kept `amt - amt != 0.0` was equivalent (that half alone catches a NaN too) and SURVIVED, as it
must. `audio/ref/numerical-safety.md` and the `drive` row of `audio/ref/off-values.md` say it. No song moves.

**L5-11 (i) not built.** Looking through `Variants` and `OptimizerHint` needs the sound index in all six gate queries
(`gatedOff`, `gatedOffWhenUnset`, `gatedOffInner`, `gatedOffWhenFinite`, `gatedOffAtUnity`, `switchedOff`) and their
call sites, and the optimizer's `mayBeUnset` (`audio_bridge`) must look through both too, or an unset cutoff behind a
`Variants` fuses where the plain build gates it, which breaks the optimizer's parity. Two modules and a parity
contract for trees no corpus song has: more than the small local change the maintainer allowed. On KLANG-00437's list.
`IgnitorGateSpec`'s pin row stays.

## The per-note rings pooled (2026-11-11, L5-03 option (a), maintainer: "go ahead")

**Built.** The plan the maintainer approved, item by item:

1. **A mono shelf** in `ResourceWarehouse`: `NoteRings` (`warehouse/NoteRings.kt`), classes of powers of two from 4096
   frames (32 KB, the class of a Karplus line's 2,500 frames) to 131,072 (1 MB, the class of the shimmer's largest ring
   at the base rate, 96,000); a request past the top class (a shimmer in a region of 2x or more whose ring passes 1 MB)
   is allocated at its own size and never shelved. Its idle bound, `NoteRings.BUDGET_BYTES` 4 MB, is carved out of
   `SHELF_BUDGET_BYTES` (the delay rings' default bound is now 28 MB, the two 32 MB together); a return that would pass
   it is dropped.
2. **Rental per voice.** The build makes the voice's `NoteRingLease` on the first rent (`IgnitorBuildCache.noteRingLease`;
   a voice that rents nothing makes none) and hands it out in `BuiltIgnitor.noteRings` (set on the root only); the
   factory gives it to the `Voice`. The pluck rents its line, the superpluck one per string, also a string grown at
   render past the build's count (`growStrings`, through the same lease), and the shimmer its grain ring
   (`ringOf`; without a shelf, a build outside a voice, each allocates its own as before). The voice gives it all
   back in the step that takes it out of the scheduler's list: the render loop's end of a voice, `removeDoneVoices`
   (a cut, a kill, a stop that drops a pending voice, a hard cleanup), and `PlaybackEngine.dispose` through the new
   `VoiceScheduler.disposeVoices` (a released engine is disposed with its voices still listed). The delay rings'
   return path is the engine's disposal; this one is the voice's removal, the only point after which nothing renders
   the tree. Once: the lease forgets what it gave back, and the shelf refuses a ring it holds already
   (`doubleReturns`).
3. **Deferred clearing.** A returned ring is dirty; `ResourceWarehouse.housekeep` zeroes `NoteRings.HOUSEKEEP_FRAMES`
   (16,384, four lines) per block. A rent takes only a clean ring, nothing is cleared on the note-on path, and a dirty
   ring is never handed out, not even cleaned on the spot (unlike the delay rings' sync clean). The string would only
   need its own 2,500 frames zero, but clearing them at the rent would still be on the note-on path; the default
   holds.
4. **Fallback.** A class with no clean ring allocates a fresh one, as today, counted in `NoteRings.allocations`. Denied
   rents mean a refused unit (a latched allocation failure) elsewhere, so the shelf has its own counts, six on the wire:
   `WarehouseStats.noteRingIdleBytes`, `noteRingIdleCount`, `noteRingDirtyCount`, `noteRingAllocations`, `noteRingHits`,
   `noteRingDropped`. A clean wire change: `WIRE_SCHEMA_HASH` moved once, -1036617280 to 1286855684
   (`WireCodecRoundTripSpec`). The warehouse panel (`src/jsMain/kotlin/comp/PlayerWarehouseStats.kt`) does not show
   them yet (an app file, not this pass's).
5. **Own length.** A rented ring is longer than asked. The Karplus string runs on `KarplusString.length` (2,500, a
   field: its wrap is read at run time as before) for the wrap, the read's clamp and the excitation's write head,
   never `delayLine.size`; the shimmer on its `ringSize` (its bypass clears `0 until ringSize`). Every `.size` of the
   Karplus code was read: the line's (render) is now `length`; the others are the string array's and the delay
   caches', which count strings. The ring's length does not depend on the pitch in today's code (the line is 2,500
   for every note, the lowest about 19 Hz at 48 kHz); sizing it by pitch would be option (b), a sound question for a
   pitch bent past the sized line, and is not done.
6. **Warm stock.** The corpus, measured per song (the after-render below, a temporary count in the throwaway
   worktree): the most lines a song holds at once is 8 (The Synthsale Piper's Farewell, 3,586 rents); Die Kirschblüte
   and Greensleeves Chant 6 (4,568 and 2,010 rents), Drunken Synthlor 4 (2,172); the other 14 renders none. With an
   empty shelf each of the four allocated only its peak, every later note rented. The warmup stocks 16 lines
   (`WarmupRunner.WARM_NOTE_RINGS`, twice the peak, fresh so clean at once; its own pluck and superpluck give 4 back and
   the stock tops the class up): 512 KB idle after the warmup.

**The shimmer** fits the same mechanism without contortion (one ring per node, its own length already a field), so it
is in: its default ring of 14,402 frames rents the 16,384 class.

**Numbers.**

- Bytes per build with a warm shelf (`NoteRingBuildBytesSpec`, `IgnitorRegistry.createExciter`, the rings given back
  and zeroed after each build): pluck 2,720 B, superpluck (8 strings) 3,544 B; through `VoiceFactory` in the closing
  run, 22,760 and 163,584 B. What is left is the tree around the strings (a default saw builds in 2,456 B).
- `AuditRealtimeRepro`'s L5-03 row (`KLANG_PROBE=audit`, now with a note-ring shelf whose rings are given back and
  zeroed after each round, outside the timing: 320 allocated in the first round, 33,844 rented): pluck 22,760 to
  2,864 B per build (5.75 to 5.89 us), superpluck 163,584 to 3,688 B (12.93 to 7.16 us); the default saw reads 2,472 B
  in the same run, so a pluck's build now costs what any voice's tree costs. Its first and second blocks still 0 B.
- **Seen, a trade-off of the power-of-two classes:** a line rents 4096 frames for its 2,500, 32 KB instead of 20 KB. On
  a COLD shelf (`AuditRealtimeRepro`'s burst, a fresh dispatcher without a warmup per repetition) 16 superplucks in one
  block allocate 4,196 KB where they allocated 2,596 KB, and the onset block took 692.6 us (median, max 997.5) against
  567.8 (max 1,301.4) in the closing run; 16 plucks 597 against 396 KB. A live backend starts with 16 lines warm, and
  every line once allocated is rented again. A class of exactly 2,500 frames (the line's own length as a class of its
  own) would drop the slack; not built, the plan named powers of two.
- Cost probe, JVM, HEAD and the tree interleaved, four pairs (`StageCostRunner`, ratio to the plain sine, which moved
  2.27 to 2.45 ns between runs): pluck 2.25 to 2.35 HEAD, 2.28 to 2.34 tree; superpluck 8 strings 17.9 to 18.4 HEAD,
  18.7 to 19.4 tree; 16 strings 36.3 to 37.4 HEAD, 37.0 to 38.8 tree; 1 string 2.25 to 2.32 HEAD, 2.34 to 2.43 tree.
  Under the probe's 10 percent noise rule, but the superpluck rows sat about 4 percent higher in every pair; a
  variant that wraps by the array's size and clamps by the field read the same, and so did moving the string growth
  out of `generate`, so the cause is not found (both variants measured; the field version kept). Allocation per block
  0 B on every row, as before. Kotlin/JS (production test bundle, three node processes each, interleaved): pluck 2.01
  HEAD, 2.06 tree (medians); superpluck 8 17.01 and 17.26, 16 34.27 and 34.34, 1 2.17 and 2.22; V8's new-space growth
  per block 0 B on the pluck rows (the shimmer's 16 B as before).
- The corpus (`runSongBenchmark --args=corpus`, 48 kHz, samples from the cache): every one of the 18 renders bit
  identical, HEAD (a throwaway worktree) against HEAD with this pass applied, by each render's hash. The four pluck
  songs rented and returned their lines 2,000 to 4,600 times each in that render, so the shelf's rings carried their
  sound. No song moves. The hash lines compared are the corpus check's own, against the 2026-11-06 reference both
  sides.

**Guards.** `NoteRingsSpec` (the classes; the fallback allocates and counts; a dirty ring is never rented, before and
during its zeroing, and comes back all zero; housekeeping's slice; the byte bound; double returns refused while dirty,
half zeroed and clean; a ring of no class not shelved; the stock; a lease gives each ring back once), `NoteRingReturnSpec`
(through the real dispatcher: the end in the render, a cut before sounding and after a fade, a stop that drops a pending
voice, a hard cleanup, an engine disposed with its voices listed; every ring back once, the next note rents them),
`NoteRingIdentitySpec` (a pluck, a low pluck bent past its line, a superpluck of eight, one grown at render, a shimmer:
rented against own ring bit for bit, and again after the rings came back and were zeroed), `NoteRingBuildBytesSpec`
(JVM, under 8,000 and 12,000 B), rows in `WarehouseStatsSpec` and `ResourceWarehouseSpec`.

**Mutation check** (`console/tools/mutate`, one campaign with KLANG-00420's click fix, 16 mutants): KILLED for the
expected reason: a rent that takes a dirty ring, no byte bound, no double-return guard, housekeeping that does not
zero, a lease that does not forget, the string wrapping by the array's size (the bent pluck), the shimmer's grain start
by the array's size, grown strings allocating their own lines, no return at the render's end, none at a removal, none at
the disposal; and the click fix's five (below in KLANG-00420). One SURVIVED in the first campaign: `Reverb.process`
ignoring the held pole stayed green, because the hand oracle held its poles on a bare `Reverb` through the same
`holdDamping`. The oracle now runs the bare network on the CUTOFF whose pole the glide holds (the pole law inverted by
hand), and the mutant is KILLED (second campaign, with the spec's other two mutants again).

## Acceptance

- Every row above is fixed with a guarding spec, or listed under "Decide" with its reason.
- The suites are green, the corpus render is identical except where a fix changes the sound on purpose, and each such
  change is named in this ticket with the songs it moves.
- The engine health page shows the fixed items green after a new probe run.

## History

- 2026-11-10: created.
- 2026-11-10: IN PROGRESS. Started.
- 2026-11-10: PAUSED. Findings landed. Waiting for the maintainer: five decisions (see Decide).
- 2026-11-11: IN PROGRESS. Decisions pass: building what the maintainer decided on 2026-11-11.
- 2026-11-11: PAUSED. The decisions pass of 2026-11-11 landed; what is left is named in the ticket.
- 2026-11-11: the per-note rings pooled (L5-03 option (a), planned with the maintainer and built).
- 2026-11-11: DONE. the shimmer's ring is sized and the per-note rings are rented from the warehouse; the gate through Variants is in KLANG-00437
