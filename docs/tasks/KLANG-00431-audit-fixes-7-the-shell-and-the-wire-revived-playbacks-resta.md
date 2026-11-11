# KLANG-00431: Audit fixes 7: the shell and the wire (revived playbacks, restarts, leaks, sample decoding on both platforms, latency, replaceVoices)

> **Status (2026-11-11): PAUSED.** Landed; twelve decisions pending with the maintainer.
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
| L9-02 | major | A `ScheduleVoices` that arrives after its playback's `Cleanup` revives the playback: late ghost notes and an engine that never ends | Confirmed: Both halves reproduced: `[Cleanup, ScheduleVoices]` on the JVM dispatchers, the straggler starts 40 s after the stop, the engine never ends; the browser cannot reach it today |
| L9-04 | minor | A playback object started again after its stop: the gain can be lost and its inline instruments are never announced again | Confirmed: Measured: the restart's voices land at gain 1.0 after 0.3 was asked |
| L1-03 | minor | "Announce once" and "the fork dies with the engine" contradict each other when a playback is started again | Confirmed: No app path restarts a playback object; the library API does |
| L9-05 | minor | Every synth voice's sound and note is sent to the backend as a sample that was not found: a process-wide map that grows per edit, on both sides, missing from the debt list | Confirmed: 100 synth notes: 100 `NotFound` commands, 100 backend store entries, 100 preloader entries |
| L9-06 | minor | In the browser the backend keeps one full PCM copy per requested NOTE, as doubles, and nothing ever evicts | Confirmed: By reading: one PCM per requested note in the worklet |
| L8-08 | minor | The output latency the UI's clock gets: the JVM reports none, and the browser leaves out an authored master's delay | Confirmed: By reading: the master's latency is not carried; the JVM reports 0 |
| L9-09 | (read only) | A sample upload costs the audio thread one megabyte clone and one megabyte copy per chunk, and the whole PCM allocation at the first chunk; the comment reasons with half the size | not challenged; the auditor's reading in `layer-9-engine-shell.md` |
| L9-11 | (read only) | The "dropped" and "soundless" voice counts fall back to 0 at every stop; the KDoc calls the counters monotone; the stats snapshot's version is a sum that can stand still | not challenged; the auditor's reading in `layer-9-engine-shell.md` |
| L9-12 | (read only) | The warm-up runs every Ignitor node kind, and on the bus side only the six born-with stages | not challenged; the auditor's reading in `layer-9-engine-shell.md` |
| L9-13 | (read only) | `Samples`' two caches are plain maps written from several threads on the JVM | not challenged; the auditor's reading in `layer-9-engine-shell.md` |
| L9-16 | (read only) | The render headroom is timed with `Date.now()` in the worklet | not challenged; the auditor's reading in `layer-9-engine-shell.md` |
| L1-06 | note | The diagnostics message is built, encoded and posted on the audio thread about 47 times a second | Confirmed, smaller: 104 to 352 B and under 1.3 us per report on the JVM, 47 reports a second |
| L8-05 | note | The diagnostics message is built and posted on the audio thread fifty times a second, also on an idle backend | Confirmed, smaller: Same measurement as L1-06 |
| L9-08 | note | The diagnostics are built, encoded and cloned on the audio thread about 47 times a second, and they are the clock | Confirmed, smaller: Same measurement as L1-06 |
| L1-07 | (read only) | The schema hash does not cover an enum's entries, the slot-key vocabulary, the catalogue index spaces or the codec's own format | not challenged; the auditor's reading in `layer-1-wire.md` |
| L1-08 | (read only) | The round-trip spec never crosses a structured clone, several wire shapes have no row, and the receiving side has no test | not challenged; the auditor's reading in `layer-1-wire.md` |
| L1-10 | (read only) | A slot map does not arrive the same on JS and on the JVM for two kinds of key | not challenged; the auditor's reading in `layer-1-wire.md` |
| L1-11 | (read only) | Both rings drop a message when full and no sender reads the result | not challenged; the auditor's reading in `layer-1-wire.md` |
| N-R3 | new | A stop during the start's sample preload is swallowed: the scheduler then starts and sends its first cycle | found in the disproof pass, `disproof-realtime.md` |
| N-R4 | new | `updatePattern`'s sample prefetch does nothing once the look-ahead has run | found in the disproof pass, `disproof-realtime.md` |
| L8-09 | (read only) | `PlaybackGain.retarget` ignores a new ramp time when the target is unchanged | not challenged; the auditor's reading in `layer-8-master-and-house.md` |
| L8-11 | (read only) | The offline render starts 5 ms late and the WAV length counter is an Int | not challenged; the auditor's reading in `layer-8-master-and-house.md` |

## The work

Per finding: read its report and its reproduction (`src/jvmTest/kotlin/audit/` in `audio_be`, `sprudel` or `klang`,
run with `KLANG_PROBE=audit`), fix it, and turn the reproduction's row into an ordinary spec that guards the fix
(mutation-checked). A finding that turns out to need a maintainer decision is not forced: it is listed under
"Decide" below with the options, and the rest of the ticket lands without it. How the fixes are made and reviewed:
`docs/audio-audit/2026-11-10/audit/FIX-BRIEF.md`.

## Decide

Each item names the finding, the options and a recommendation. Nothing here is half done in the code: what landed is
in "Notes".

### D1. The wire has no notion of a start (L9-02's backend half, the residue of L9-04)

The backend still cannot tell a straggler from a restart: a `ScheduleVoices`, `RegisterIgnitor`, `RegisterKatalyst` or
`StartRealtimeVoice` after a `Cleanup` resumes the stopped engine or creates a fresh one, and nothing ever stops it
(the audit repro "L9-02, L9-04" in `AuditRealtimeRepro` still shows it, kept on purpose). The frontend no longer sends
one (Notes), so no path in the tree reaches it. One residue of L9-04 stays: a block rendered exactly between a start's
second `SetGain` and its first `ScheduleVoices` can still dispose an idle stopped engine (on the JVM the two sends are
microseconds apart; in the browser they are two posted messages).

- (a) A start number on the wire: `Cleanup` and every command of a start carry it; the dispatcher drops a command of
  a start older than the stop it follows, and keeps the gain with the start, not the engine. A wire change (the
  schema hash moves). Closes both doors for any frontend.
- (b) A backend guard without a wire change: a stopped engine remembers its start's `playbackStartTime` and ignores a
  `ScheduleVoices` that carries it. Only while the engine is not disposed yet, and a restart on a frozen clock (the
  specs) looks like a straggler: partial.
- (c) `SetGain` on a `Stopped` engine resumes it, as a schedule does. The frontend gate now guarantees that a
  `SetGain` after a `Cleanup` is a restart's, so this closes the residue, but it reverses KLANG-00406 step 5's "a
  fade sent to a song that just stopped must not revive it" (`PlaybackEngineGainSpec` pins it).
- (d) Leave it to the frontend (today).

Recommendation: (d) now; (a) when a second frontend (the MIDI rig, a sequencer) starts cyclic playbacks of its own.

### D2. One PCM per requested note in the worklet, never evicted (L9-06)

The store is keyed by the request, note included; the browser rebuilds every request's PCM as its own `DoubleArray`
(a soundfont over 40 notes in 10 zones holds 40 copies). Every fix changes the wire or decides an allocation policy.

- (a) A `Cmd.Sample.Alias(req, of)`: the frontend sends a zone's PCM once and points the other notes' requests at it
  (it knows the zone: `Samples.pcmCache` is keyed by the `Sample`). Wire change.
- (b) Key the store by the resolved sample (a zone id in the request). Wire change, and the backend's request path
  changes with it.
- (c) PCM as 32-bit floats on the wire and in the store: half the memory; moves every sample song by rounding.
- (d) Evict: a sample no live engine has used for a while leaves the store (and the frontend's `sent` set, so it is
  asked for again). An allocation policy.

Recommendation: (a), then (d). The concurrent double decode the auditor names beside it is fixed (L9-13, Notes).

### D3. An authored master's lookahead in the UI's clock (L8-08, the browser half)

The browser adds the house limiter's 5 ms to its output latency, not a `master(...)` chain's authored lookahead (up
to 50 ms) or an oversampling region's delay; the dispatcher knows the true figure (`latencyFrames`). No wire change
is needed: the worklet's dispatcher can fill `outputLatencyMs` with its own `latencyFrames` in ms and `JsAudioBackend`
add the context's figures to it instead of the constant. Reporting is not compensation (`audio-constraints.md` keeps
"nothing compensates an authored lookahead" for the audio), but the highlights of a song with such a master move by
up to 50 ms, which an author sees. The JVM half (no latency at all) is fixed.

Recommendation: do it; one line in `audio-constraints.md` that the UI's clock reports the authored lookahead.

### D4. The diagnostics report on the audio thread (L1-06, L8-05, L9-08)

Measured by the disproof pass: 104 B and under 0.1 us per report idle, 352 B and up to 1.25 us with 8 cylinders, 47
reports a second; the worklet's encode and structured clone are not measured. Accepted and documented at
`PlaybackEngineDispatcher.emitDiagnostics` (the rule it departs from, and why). The option: send the clock (one
number) at 50 Hz and the gauges (cylinders, warehouse) only when they changed or at 4 Hz; the frontend keeps the last
gauges. Recommendation: leave it until a phone profile of an idle worklet shows its garbage matters.

### D5. Sample uploads on the audio thread (L9-09)

A chunk is 1 MB (the comment said 512 KB; corrected), cloned and copied once more on the audio thread, about 100 MB/s;
the first chunk allocates the whole PCM there.

- (a) Hand each sample over as one transferred `ArrayBuffer` (`postMessage` with a transfer list): no clone, no chunk
  copy, the allocation on the main thread. Changes the upload path and `SampleStore`'s `Partial` state.
- (b) Chunks of 64 * 1024 frames, as the comment meant: half the hiccup per chunk, half the upload rate.
- (c) Leave it (today, the comment true).

Recommendation: (a).

### D6. The dropped and soundless counts across a stop (L9-11)

They are the engines' and fall when a playback stops (its context goes at `cleanup`, its engine at disposal); the
docs now say so (`WarehouseStats`). The stale snapshot the falling counts could cause is fixed (Notes).

- (a) Monotone since the backend's start: the dispatcher adds an engine's counts to its own when the context or the
  engine goes (the warm-up's left out). A stutter stays visible after the stop.
- (b) Keep "the playbacks rendering now" (today).

Recommendation: (a) for the dropped count, which the scheduler's KDoc calls "the only visible trace of a frontend
stall".

### D7. What the warm-up does not run (L9-12)

The master bus and any `master(...)` chain, the Katalyst stages outside the born-with chain, the chain swap, the duck,
`PlaybackGain` and `TailRelease`, `ReplaceVoices`, and the wire decoder. Whether their first use costs a cold block on
a phone is a measurement (first run against second run of a song whose first block has a master chain, the Fairphone
the warm-up was built on). Recommendation: measure first; extend the warm-up only for what the measurement shows.
The stale "~21 ms" in `JvmAudioBackend` now says 48 to 219 ms.

### D8. The rest of the schema hash (L1-07)

An enum's entries are in the hash now (Notes). Still outside it: the slot-key vocabulary (`"lpf.freq"`), the
catalogue index spaces (`DistortionShapes`, `LfoShapes`, `AdsrCurves`, `BodyMaterials`, `VowelBands`), and the codec's
own format (`#t`, a set as an array, an enum as an ordinal). They are values, not types, so the processor cannot read
them without a list kept by hand or more generated code.

- (a) A codec format constant in the processor, folded into the signature, raised by hand when the generator's
  encoding changes.
- (b) The catalogue names and the slot keys folded in through a hand-kept list.
- (c) Leave it: production loads the worklet with the bundle's hash in its URL.

Recommendation: (a) only (cheap, no new machinery); (b) is the complexity the stone rule warns about.

### D9. Slot-map keys on JS (L1-10)

`"__proto__"` is lost on JS and kept on the JVM; index-like keys (`"0"`, `"12"`) arrive first and ascending on JS. No
door produces such keys and no reader depends on a slot map's order (the auditor's search, not repeated). Options:
encode into `Object.create(null)` (closes `"__proto__"`), or a JS `Map` on the wire (closes both, a codec change).
Recommendation: leave it; take `Object.create(null)` when the codec is next touched.

### D10. Full rings drop a message, and no sender looks (L1-11)

Only the upload ring can fill in the browser; on the JVM the feedback ring fills in about 175 s of a stalled frontend
reader. Options: count the refused sends on each side and carry the backend's in `Diagnostics` (a wire change); log a
refused send on the frontend (`KlangPlayer.sendControl`); a larger ring. Recommendation: the count, with D4's gauges.

### D11. The offline render's head (L8-11)

Every WAV and corpus render begins with the house limiter's 240 frames of silence (more under a latent master), and
renders with different master chains are offset against each other. Trimming `latencyFrames` at the head moves every
corpus hash and every WAV once. The WAV length `Int` is no defect: it wraps past 2 GiB, and `putInt` then writes the
same 32 bits as the unsigned size the header field is, up to the format's own end at 4 GiB (documented at
`WavFileWriter.dataBytes`). Recommendation: trim the head, at the coordinator's corpus re-accept.

### D12. The process-wide sample maps and the debt list (L9-05)

The synth voices' share of the growth is gone (Notes). What remains grows per new sample request and never shrinks:
`SampleStore.samples` (the PCM, and the `NotFound` entries of samples that do not exist), `SamplePreloader.sent`,
`Samples`' two caches, `InMemoryUrlCache`. The root `CLAUDE.md` rule names KLANG-00210 section 11 as the debt list,
which is archived and lists only the `uniqueId()` maps. Options: list these there (or in a new ticket), with D2 (d) as
their eviction. Recommendation: a new ticket for the sample memory (D2 and this), and the `CLAUDE.md` pointer moved to
it by the maintainer.

### Decided (maintainer, 2026-11-11)

- **D1:** "ok leave it" (the frontend's gate; a start number on the wire when a second frontend starts playbacks).
- **D2:** "yes to own ticket": KLANG-00440.
- **D3:** "ok please go for it. change to the skill approved": the UI's clock reports an authored lookahead; one line
  in `audio-constraints.md`. Owed.
- **D4:** "yes keep it like this".
- **D5:** "ok if this is possible do it": one transferred buffer per sample. Owed.
- **D6:** "yes count since engine start". Owed.
- **D7:** "ok, parked for now" (a phone measurement first; in KLANG-00437).
- **D8:** "as recommended": a codec format constant in the signature. Owed.
- **D9:** "ok leave it".
- **D10:** "as recommended": the refused sends are counted and carried with the diagnostics. Owed.
- **D11:** "as recommended": the offline render's head is trimmed, at the corpus re-accept. Owed.
- **D12:** "yes new ticket. Point CLAUDE.md to the new ticket": KLANG-00440; the pointer is moved.

## Notes

What landed, per finding (2026-11-11, the fix worker). No change moves the sound of a song (the corpus, below).

| Id | Outcome | The change | Guard |
|---|---|---|---|
| L9-02 | FIXED (frontend), backend half under D1 | `KlangPatternScheduler`: the stop sends its `Cleanup` itself, under the lock every send takes (it was launched on the fetcher); every voice the fetch path sends goes through `sendWhileStarted` (only while started, and only for the start the loop belongs to: a generation per start); the inline DSLs are announced only while started | `KlangPatternSchedulerLifecycleSpec`: a query running at `stop()` sends nothing after the `Cleanup`; `stop()` has sent its `Cleanup` when it returns; an edit after the stop announces nothing |
| N-R3 | FIXED | The preload's wait rethrows the stop's cancellation (told apart by the coroutine's own state, as the timeout is a `CancellationException` too); `begin` sets the origin, opens the metric and sends only while its start is current | same spec: a stop during the preload sends nothing after it, no `PlaybackStarted` |
| L9-04 | FIXED | A start sends its gain once more right before its first voices; the inline DSLs: see L1-03 | same spec: over an `OfflineBackend`, a restart whose stopped engine is disposed during the preload plays at the gain kept (it played at 1) |
| L1-03 | FIXED | `InlineDslRegistrar.forgetAnnounced` at every start (the backend's forks die with the engine); KDoc corrected | same spec: a restart announces its inline instrument again, after the `Cleanup` |
| L9-05 | FIXED | Sample requests only for the voices the engine plays as samples: not an unset sound, not a built-in (`builtInSoundNames`, new in `audio_be`), not an announced inline instrument; the engine's own rule from the frontend's side. The remaining sample maps: D12 | same spec: only `bd` of a supersaw, a `SuperSaw`, an unset sound, an inline instrument and `bd` |
| N-R4 | FIXED | `updatePattern` prefetches the new pattern's samples over the whole look-ahead, past the look-ahead's pointer; not while stopped or driven | same spec: an edit asks for its new sample at once |
| L9-06 | DECIDE (D2) | The concurrent double decode beside it is fixed with L9-13 | |
| L8-08 | FIXED (JVM), browser half under D3 | `PlaybackEngineDispatcher.outputLatencyMs`, carried in every report; the JVM host sets its line's buffer plus the house lookahead | `PlaybackEngineDispatcherDiagnosticsTest`, `JvmAudioBackendLatencySpec` |
| L9-09 | comment FIXED, the rest D5 | The drip's comment says 1 MB a chunk, about 100 MB/s | |
| L9-11 | stale snapshot FIXED, counts D6 | `ResourceWarehouse.stats` compares the dropped and denied counts on their own, not in the version sum; the `WarehouseStats` KDoc says which counts fall | `WarehouseStatsSpec`: a fall of the dropped count while a part rises by as much re-snapshots |
| L9-12 | DECIDE (D7) | The stale "~21 ms" comment in `JvmAudioBackend` corrected | |
| L9-13 | FIXED | `Samples`' two caches hold one `CompletableDeferred` per key under a lock: one load per key, concurrent callers await it; a null or a throw is not kept (as before) | `SamplesConcurrencySpec` (common: JVM and browser): 64 concurrent requests for 8 files, each downloaded and decoded once |
| L9-16 | FIXED | The worklet times its blocks with `performance.now()` where its scope has it, else `Date.now()` (`renderTimerMs`) | `RenderTimerSpec` (browser) |
| L1-06, L8-05, L9-08 | ACCEPTED (D4) | Documented at `emitDiagnostics` with the measurement, as a deliberate departure | |
| L1-07 | enum entries FIXED, the rest D8 | The processor folds an enum's entries into its signature; the pinned hash moved from -985611466 to 212461444 | `WireCodecRoundTripSpec`'s hash pin |
| L1-08 | FIXED | Every row of `WireCodecRoundTripSpec` crosses a structured clone; rows for an empty slot map, a non-null tag set, `ReplaceVoices` with no cutoff, `Feedback.SampleReceived` | the same spec |
| L1-10 | DECIDE (D9) | | |
| L1-11 | DECIDE (D10) | | |
| L8-09 | FIXED | `PlaybackGain.retarget`: the same target asked again while a ramp runs takes the new time from where the gain is | `PlaybackGainSpec` |
| L8-11 | head D11; the WAV size ACCEPTED (no defect, documented) | | |

The audit repro `AuditRealtimeFrontendRepro` (klang) is deleted: both its rows (L9-02's frontend half, L9-05) are
guarded by `KlangPatternSchedulerLifecycleSpec`. `AuditRealtimeRepro`'s rows "L9-02, L9-04" (the backend half, D1) and
"L1-06, L8-05, L9-08" (D4) stay.

Mutation-checked (`console/tools/mutate`, three campaigns, every mutant KILLED for the expected reason): L8-09
same-target, L9-11 version sum, L8-08 report carries / JVM house lookahead, L9-02 fetch gate / launched cleanup /
announce gate, N-R3 all three guards taken out together (each guard alone is covered by the other two: removing one
survives, by design), L9-04 the second `SetGain`, L1-03 the forget, L9-05 the filter, N-R4 the pointer-gated prefetch,
L9-13 one load per key, L1-07 the enum fold, L1-08 a `Set` passed through un-encoded (dies at the clone), L9-16 the
timer. Suites green: `:audio_be:jvmTest` 3141, `:audio_be:jsBrowserTest` 2961, `:audio_bridge:jvmTest` 160,
`:audio_bridge:jsBrowserTest` 299, `:audio_fe:jvmTest` 28, `:audio_fe:jsBrowserTest` 30, `:klang:jvmTest` 89, root
`:jvmTest` 172. Corpus before against after: all 18 sound hashes identical, no song edited between the runs.

## Acceptance

- Every row above is fixed with a guarding spec, or listed under "Decide" with its reason.
- The suites are green, the corpus render is identical except where a fix changes the sound on purpose, and each such
  change is named in this ticket with the songs it moves.
- The engine health page shows the fixed items green after a new probe run.

## History

- 2026-11-10: created.
- 2026-11-11: IN PROGRESS. Fix worker started.
- 2026-11-11: PAUSED. Landed; twelve decisions pending with the maintainer.
