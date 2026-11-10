# Layer 9: the engine shell

**What I read** (in full unless noted)

- `audio_be/src/commonMain/kotlin/`: `PlaybackEngineDispatcher.kt`, `PlaybackEngine.kt`, `BackendClock.kt`,
  `AudioBackendContext.kt`, `AudioBackend.kt`, `OfflineBackend.kt`, `KlangAudioRenderer.kt`, `WarmupRunner.kt`,
  `WarmupVocabulary.kt`, `AudioAnalyzer.kt`, `VoiceWatch.kt`, `SampleStore.kt`, `TailRelease.kt`,
  `voices/VoiceScheduler.kt`, `voices/PlaybackCtx.kt`, `utils/retain_in_order.kt`, `warehouse/ResourceWarehouse.kt`.
- Skimmed, for the questions of this layer only: `cylinders/Cylinders.kt` and `cylinders/Cylinder.kt`
  (`tryDeactivate`, `isMixBufferSilent`, `releaseAll`), `master/MasterBus.kt` (`isRinging`, `isSettled`,
  `updateTailState`), `effects/TailCeiling.kt` (the KDoc and `hasTail`), `ChainHost.kt` (the cache bound),
  `katalyst/KatalystRegistry.kt`, `ignitor/IgnitorRegistry.kt` (`register`, the forks), `voices/Voice.kt`
  (`advance`, `renderStages`, `ignite`), `voices/VoiceFactory.kt` (`onsetFrame`, the top of `makeVoice`),
  `PlaybackGain.kt` (the default), `warehouse/SizedBuffers.kt`, `ReverbUnits.kt`, `CylinderUnits.kt` (names only).
- `audio_be/src/jvmMain/kotlin/JvmAudioBackend.kt`, `audio_be/src/jsMain/kotlin/JsAudioBackend.kt`,
  `WorkletContract.kt`, `_audio_context_wake.kt`.
- `audio_jsworklet/src/jsMain/kotlin/KlangAudioWorklet.kt`, `init.kt`.
- `audio_bridge`: `infra/KlangCommLink.kt`, `SampleRequest.kt`, `KlangTime.kt` (common, JS, JVM), the head of
  `IgnitorDslIdentity.kt` and `KatalystDslIdentity.kt`. `common/.../infra/KlangRingBuffer.kt`.
- The frontend's half, module `klang`: `KlangPatternScheduler.kt`, `PlaybackClock.kt`, `KlangPlayer.kt`,
  `SamplePreloader.kt`, `KlangOfflineRenderer.kt`, `InlineDslRegistrar.kt`, `AnnounceOnceRegistry.kt`,
  `ContinuousPlayback.kt`, `OneShotPlayback.kt`, `KlangRealtimeVoicePlayback.kt`, `KlangPlaybackContext.kt`,
  `index_common.kt`, `index_jvm.kt`, `index_js.kt`.
- `audio_fe`: `samples/Samples.kt`, `BrowserAudioDecoder.kt`, `utils/AssetLoader.kt`, `cache/InMemoryUrlCache.kt`,
  the head of `SampleIndexLoader.kt` (`provide`); the JVM decoders by `grep` only.
- The app, for reach only: `src/jsMain/kotlin/spine/KlangCodePlaybackCtrl.kt`, `Previews.kt`, `MidiRig.kt` (parts).
- Ref pages: `audio/CLAUDE.md`, `audio/MEMORY.md` (the sections the brief names), `audio/ref/architecture.md`,
  `audio/ref/sample-management.md`, `audio-constraints.md`, `docs/audio-backend-file-map.md`, KLANG-00420's leads,
  KLANG-00210 section 11, `hot-path-lint.txt`, `dead-knobs.txt`.
- Tests: the test NAMES of `PlaybackEngineDispatcherTest`, `...DiagnosticsTest`, `...OrderSpec`,
  `PlaybackEnginePhaseSpec`, `PlaybackEngineGainSpec`, `EngineStopReleaseSpec`, `OfflineBackendSpec`,
  `SchedulerStartupSpec`, `SampleStoreSpec`, `LongRunningTimelineSpec`; the bodies of two cases in
  `PlaybackEngineGainSpec` and all of `LongRunningTimelineSpec`.

**What I did not**

- `audio/ref/performance.md` (817 lines): not read. `stage-cost`, `aliasing`, `clicks`, `framing` tables: only the
  leads in KLANG-00420, not the tables.
- `HouseStage.kt`, `ChainSwap.kt`, the Katalyst stages, the Ignitor tree, `SoloTracker.kt`, `KlangMinHeap.kt` (only
  its member list), `SoundFont.kt`, `SampleCatalogue.kt`, the JVM decoders' bodies, `DiskUrlCache.kt`, `TempoMap.kt`,
  `KlangDiagnostics.kt`, the generated wire codec.
- The bodies of most specs, `KlangPlaybackSyncSpec`, `RealtimeVoiceSpec`, `WarmupVocabularySpec`,
  `SampleChunkRoundTripSpec`.
- I ran nothing. Every number below is from reading or from arithmetic done by hand; none is measured.

## The probes' leads in this layer

| Lead | Verdict | Cause, with the place |
|------|---------|-----------------------|
| Hot-path lint: `PlaybackEngineDispatcher.kt:308`, `:321`, `:357` (the diagnostics message built on the audio thread) | CONFIRMED, periodic | `emitDiagnostics` runs its body once per `diagnosticsEveryFrames` (`:300`): every 8 blocks at 48 kHz (1024 frames, 21.3 ms), every 7 at 44.1 kHz. Each time: one `ArrayList`, one `CylinderState` per rented cylinder of every rendering engine, one `Diagnostics`. In the worklet the same message is then encoded into a second object tree and cloned by `postMessage` (`KlangAudioWorklet.kt:210`, `WorkletContract.kt:46`). See L9-08. |
| Hot-path lint: `ResourceWarehouse.kt:95` (`WarehouseStats`) | CONFIRMED, per change | Rebuilt only when the summed version moved (`ResourceWarehouse.kt:88` to `:95`). It moves on every sample message, every rent or return, every dropped voice. See L9-11 for the sum. |
| Hot-path lint: `WarmupRunner.kt:305` (`BackendReady`) | BY DESIGN | Once per backend life, in the block that ends the warm-up. |
| Dead knobs: `warm`, `offset` | BY DESIGN, and not reachable by a raw `ignp` either | `WarmupVocabulary.kt:175` and `:179`: two `Param` leaves in the `math` graph, there so the warm-up runs the `ParamIgnitor` path. The graph is registered on the warm-up playback's own fork (`WarmupRunner.start`, `RegisterIgnitor` with `--WARMUP--`), which `cleanupHard` disposes. No song's voice can name a tree that reads them. The finder's "reachable by a raw `ignp` only" is too wide for these two. |
| Stage probe 3 and 4 (the delay and the reverb never reach exact zero: 1.44e-21, 9.1e-19): "what it costs downstream (`hasTail`, an idle engine)" | DISPROVED as an engine hold. The residue itself: NOT MINE | An engine is idle by `cylinders.anyActive()` and `masterBus.isRinging` (`PlaybackEngine.kt:232`, `:301`). A cylinder's activity is decided by `isMixBufferSilent` against `SILENCE_FLOOR` 1e-5 (`Cylinder.kt:706`) and then by `chain.hasTail()`, which is a `TailCeiling`: a bound kept from the unit's INPUT that decays by the feedback per window (`effects/TailCeiling.kt`, `hasTail`). Neither reads whether the output is exactly zero. A constant 1e-18 keeps no cylinder and no engine alive. |
| Stage probe 1 (a NaN latches the ducked bus) | NOT MINE | One observation for whoever owns it: `Cylinder.isMixBufferSilent` (`Cylinder.kt:710`) and the master's audibility test compare with `>` and `<`, so a NaN mix reads as silent. |
| Stage probe 7 (1e15 into the house limiter) | NOT MINE | `HouseStage`. |
| Aliasing 3 (sample playback off its own rate, linear interpolation) | NOT MINE for the interpolator; one part is mine | Which samples run off their own rate differs per host: the browser decodes every sample to 48000 Hz, the JVM keeps the file's rate. See L9-07. |
| Framing 3 ("no voice wrote before its onset", all offsets) | True at a timeline that starts at 0. Not true of the live hosts by one frame | See L9-03: the live timeline starts at Unix-epoch seconds, and `floor` in `VoiceFactory.onsetFrame` then lands half of the on-grid onsets one frame early. |

## Findings

### L9-01 One exception anywhere in a command or a block ends all audio for the session, on both hosts

- **Severity**: major
- **Tag**: safety
- **Where**: `audio_jsworklet/src/jsMain/kotlin/KlangAudioWorklet.kt:132` to `:135` (no try in `onmessage`),
  `:151` to `:158` and `:178` (no try around `renderBlock`); `audio_be/src/jsMain/kotlin/JsAudioBackend.kt:116` to
  `:184` (no `onprocessorerror` on the node); `audio_be/src/jvmMain/kotlin/JvmAudioBackend.kt:80` to `:116`;
  `audio_be/src/commonMain/kotlin/PlaybackEngineDispatcher.kt:260` to `:266`;
  `audio_be/src/commonMain/kotlin/voices/VoiceScheduler.kt:453` to `:476`, `:639`;
  `audio_be/src/commonMain/kotlin/utils/retain_in_order.kt:22`.
- **What**: There is no containment between one playback and the host.
  1. JS, in `process()`: `contextFor(outputs).renderInto(outputs)` calls `dispatcher.renderBlock` bare. By the Web
     Audio rule, a `process()` that throws puts the processor into its error state for good: it outputs silence for
     the rest of its life and the node gets a `processorerror` event. `JsAudioBackend` sets no handler for it. The
     main thread sees nothing: the frontend's playbacks stay "started", `PlaybackClock` stops 0.5 s after the last
     report, the UI shows a playing song with no sound. Only the browser console has a line.
  2. JS, in `port.onmessage`: `WorkletContract.decodeCmd(message).also { cmd -> ctx.handle(cmd) }`. A throw here
     leaves the processor alive, and drops the rest of that command. For `ScheduleVoices` that is every voice of the
     batch after the one that threw (`VoiceScheduler.scheduleVoices` pushes, prefetches and prepares voice by voice,
     `:407` to `:415`, and promotes after the loop).
  3. JVM: `handle` and `renderBlock` are both inside the `while` of `run`. Any throw leaves the loop, the `finally`
     closes the line, `run` ends. The same command that costs the browser one batch costs the JVM the backend.
  4. The state after a throw in a voice: `active.retainInOrder { ... voice.render(ctx) ... }` is an in-place
     compaction. A throw at read index k leaves the list at full size with the slots between the write and the read
     index stale: voices that were `Done`, or second references to voices already moved down. The dispatcher's
     `finally` (`:262`) advances the clock "so an exception mid-block cannot leave now in the past", but no live host
     renders another block after a throw, so that care only serves a spec.
  `IgnitorRegistry.register`'s own KDoc states the hazard ("The worklet's onmessage dispatch has no try/catch") and
  guards its one call. Nothing guards `voiceFactory.makeVoice` (the tree build, run inside `process()` at promotion
  and inside `onmessage` at schedule time), `masterBus.prepare`, `SampleStore.addSample` (`copyInto` with an offset
  from the wire) or `voice.render`.
- **Reach**: any throw. I did not find a proven throw site from song input in this layer; the tree and chain builds
  belong to other layers. The engine rule "no exceptions in audio hot paths" is the only thing between a build bug
  and a dead session.
- **Settle it**: a spec with a `VoiceWatch` (or a test ignitor) that throws once in `render`: assert that the next
  `renderBlock` still renders the other playback and that `active` holds each voice once. Today the first assertion
  cannot be written for the hosts at all, because they do not call again. For the browser: throw once in `process()`
  by hand and watch `node.onprocessorerror`.
- **Confidence**: high for the mechanism, by reading; the reach depends on a throw existing.

### L9-02 A `ScheduleVoices` that arrives after its playback's `Cleanup` revives the playback: late ghost notes and an engine that never ends

- **Severity**: major
- **Tag**: bug
- **Where**: `klang/src/commonMain/kotlin/KlangPatternScheduler.kt:266` to `:294` (`stop`), `:330` to `:334`
  (`cleanUpBackend` launches the send), `:481` to `:492` (`tick`), `:540`, `:767`;
  `audio_be/src/commonMain/kotlin/PlaybackEngineDispatcher.kt:83` to `:121` (`engineFor`, `scheduledAgain`);
  `audio_be/src/commonMain/kotlin/voices/VoiceScheduler.kt:214` to `:229` (`cleanup` drops the context), `:491` to
  `:507` (`ensureEpoch`).
- **What**: The wire cannot tell a straggler from a restart.
  The backend half, deterministic: send `Cleanup(p)`, then `ScheduleVoices(p, [v])` where `v.startTime` is 40.0 (a
  cycle 40 s into the song) and `v.playbackStartTime` is the old start. `engineFor(p)` finds the engine `Stopped`,
  `scheduledAgain` calls `resume()` and takes it out of `ending` (or, if the engine was already disposed, a fresh
  `Playing` engine is created). `cleanup` had set `playback = null`, so `ensureEpoch` makes a new context:
  `latency = max(0, now - playbackStartTime)`, `epoch = playbackStartTime + latency = now`. `v` is then due at
  `now + 40 s`. Result: the notes of that cycle sound 40 s after the stop, and the engine is `Playing` with nobody
  left to send its `Cleanup`: it renders every block for the life of the backend and its entry stays in `engines`
  and `rendering`.
  The frontend half, a race on the JVM: `stop()` flips `running`, cancels the fetcher job, and LAUNCHES the
  `Cleanup` on `fetcherDispatcher` (`Dispatchers.Default`, `index_jvm.kt`). Cancelling does not interrupt a `tick`
  that is in the middle of a pattern query on the fetcher's thread. `tick` checks `running` only in
  `emitCompletedCycles`; `catchUp` (`:540`) and `requestNextCyclesAndAdvanceCursor` (`:767`) send without a check.
  A query that started before the stop and ends after the launched `Cleanup` went out sends its cycle behind it.
  In the browser the same code is safe: one thread, the fetcher is always parked in `pause()` when `stop()` runs.
  The same door is open to every command that goes through `engineFor`: `RegisterIgnitor`, `RegisterKatalyst` and
  `StartRealtimeVoice` after a `Cleanup` revive or create an engine for good too (`:150`, `:166`, `:169`). `SetGain`
  has its own gate and KDoc for exactly this; the others have none.
- **Reach**: the JVM live player (`klangPlayer` on the JVM, `src/jvmMain/kotlin/Main.kt`), a stop while a cycle is
  being fetched. The window is the length of one pattern query, once per fetched cycle. Internal in the browser.
- **Settle it**: the backend half with `OfflineBackendSpec`'s rig: `Cleanup`, then a `ScheduleVoices` with
  `startTime = 40.0`; assert nothing starts (`VoiceWatch.started`) and the engine count returns to 0. It will show
  one start at `now + 40` and one engine. The race: a `KlangPatternScheduler` spec with a pattern whose
  `queryEvents` blocks on a latch, `stop()` while it blocks, release the latch, read the order of the sent commands.
- **Confidence**: high for the backend half (read end to end), medium for the race (not run).

### L9-03 The live timeline is counted in Unix-epoch seconds: half of the on-grid onsets land one frame early, a start's first voice among them

- **Severity**: minor
- **Tag**: bug, consistency
- **Where**: `audio_bridge/src/jsMain/kotlin/KlangTime.kt:51` (`baseTimeMs = Date.now()`), the JVM twin
  (`System.currentTimeMillis()`); `audio_jsworklet/.../KlangAudioWorklet.kt:126` (`setBackendStartTime`),
  `audio_be/src/jvmMain/kotlin/JvmAudioBackend.kt:36`; `audio_be/src/commonMain/kotlin/BackendClock.kt:54`
  (`secAt`); `voices/VoiceFactory.kt:51` to `:56` (`floor`); `voices/VoiceScheduler.kt:588` to `:598` (the contract
  and its check in seconds); `voices/Voice.kt:513`.
- **What**: `startTimeSec` is about 1.79e9 on both live hosts. A double there has a step of 2^-22 s = 2.38e-7 s,
  which is 0.0114 frames at 48 kHz. Every absolute second on the timeline is rounded to that grid, and
  `onsetFrame` takes `floor((startSec - startTimeSec) * sampleRate)`. `floor` turns a rounding of -0.005 frames
  into a whole frame.
  A case by hand: `startTimeSec = 1794268800.5`, the cursor at frame 384 (the fourth block), 48 kHz. The true now
  is S + 0.008 s. In grid steps 0.008 s is 33554.432, so `secAt(384)` rounds DOWN to S + 33554 steps. A playback's
  first voice (`startTime` 0) gets `epoch = nowSec` exactly (`ensureEpoch`), passes the late check
  (`absoluteStartSec >= nowSec`, equal), and `onsetFrame` computes `33554 * 2^-22 * 48000 = 383.995`, floored to
  383. Its onset is one frame BEHIND the block it is promoted for. `Voice.ignite` then gives the tree
  `voiceElapsedFrames = 384 + 0 - 383 = 1` in its first block.
  That is the state the comment at `:588` says cannot exist ("a voice's first generate() has voiceElapsedFrames ==
  0; the scheduler guarantees it here"). The guarantee is checked in seconds and broken in frames. It happens
  whenever the block's time rounds down, which is about every second block boundary (the fraction of
  `k * 11184.8107` under 0.5 at 48 kHz, of `k * 12174.0497` at 44.1 kHz), and the same way for every later voice
  whose start falls on a whole frame, and for realtime notes (`startRealtimeVoice` stamps `secAt(cursor)`).
  What is heard: nothing I can name. Every reader of `voiceElapsedFrames` takes it as a position (the envelope
  starts at position 1 of its attack, the note clocks one frame in), none tests for 0. What is wrong: the stated
  contract, sample-exact spacing between two voices (n or n minus 1 frames), and the equality of a live playback
  with the offline render, whose timeline starts at 0 and is exact.
- **Reach**: every live playback, browser and JVM.
- **Settle it**: `SchedulerStartupSpec` "B1: a playback's first note ... with its attack at zero", once more with
  `setBackendStartTime(1794268800.5)` and three blocks rendered first; assert the voice's `startFrame` is 384. It
  will be 383. No spec today runs a timeline above 1000 s (`OfflineBackendSpec:98`); every other rig sets 0.0.
- **Confidence**: high (arithmetic by hand, the code read end to end; not executed).

### L9-04 A playback object started again after its stop: the gain can be lost and its inline instruments are never announced again

- **Severity**: minor (the app mints a new playback for every Play; the object's own KDocs promise the restart)
- **Tag**: bug
- **Where**: `klang/.../KlangPatternScheduler.kt:239` to `:264` (`start` sends `SetGain` at once), `:373` to `:397`
  (the voices follow after the ready wait, the preload and the first query), `:330` (the stop's `Cleanup` is
  launched, not sent); `audio_be/.../PlaybackEngineDispatcher.kt:184` to `:201` (`setGain` on a `Stopped` engine
  does not resume it), `:367` to `:388` (the sweep); `klang/.../ContinuousPlayback.kt:31`, `OneShotPlayback.kt:38`
  (one registrar per object, never reset), `AnnounceOnceRegistry.kt` (`announced`).
- **What**: two defects on the same path, `stop()` then `start()` on one `KlangCyclicPlayback`.
  1. The gain. `start()` sends `SetGain(p, g, 0)` synchronously. The engine of `p` is still `Stopped` and ringing
     out, so the dispatcher sets the gain on it and leaves it in `ending`. The first `ScheduleVoices` follows only
     after `backendReady.await()`, `preloadSamples` and the first query. If the old engine goes idle in between,
     `disposeDrainedEngines` disposes it, and the schedule creates a fresh engine whose `PlaybackGain` is 1. The
     frontend holds `g`, the backend plays at 1: a playback asked for at 0.3 comes back at full level. When
     `stop()` and `start()` are called back to back, the order on the wire is even `SetGain`, `Cleanup`,
     `ScheduleVoices`, because the `Cleanup` is launched; a playback that had no voice sounding at that moment is
     idle in the next block and loses its gain every time.
  2. The inline DSLs. The backend's registry forks die with the engine (`PlaybackEngine.create`, by design). The
     frontend's `InlineDslRegistrar` lives as long as the playback OBJECT and its `announced` set is never cleared,
     though its KDoc says "Both sides free it when the playback stops". After a stop whose engine was disposed, the
     restart sends no `RegisterIgnitor` and no `RegisterKatalyst`: `announced.add(name)` is false. The fresh engine
     knows none of the names, every voice of an inline instrument is taken for a sample
     (`VoiceFactory.makeVoice`: `isOsci` false), asked for, not found, and counted soundless; a `master(...)` or
     `katalyst(...)` chain never lands. Whether it happens depends on whether the old engine was already disposed:
     a restart inside the tail works, one after it is silent.
- **Reach**: `KlangCyclicPlayback.start()` after `stop()`. Not from the app today (`KlangCodePlaybackCtrl` drops the
  playback at stop and calls `player.play` again). `KlangPatternScheduler` itself documents the restart in three
  places (`gain`: "kept while stopped, and sent at the next start"; `PlaybackAnchored`: "a resume after a stop
  included"; the runtime metric).
- **Settle it**: (1) `PlaybackEngineGainSpec`: play, `Cleanup`, `SetGain(song, 0.0, 0.0)`, render until
  `engine("song")` is null, play again, assert the first block is silent. It will not be. (2) a
  `KlangPlaybackSyncSpec` case: a pattern with an inline ignitor, start, stop, render until the engine is gone,
  start, count `VoiceWatch.soundless`.
- **Confidence**: high for both mechanisms, by reading.

### L9-05 Every synth voice's sound and note is sent to the backend as a sample that was not found: a process-wide map that grows per edit, on both sides, missing from the debt list

- **Severity**: minor
- **Tag**: leak
- **Where**: `klang/.../KlangPatternScheduler.kt:349` to `:351` and `:737` to `:743` (no filter, although the comment
  says "non-oscillator sounds"); `klang/.../SamplePreloader.kt:39`, `:181` to `:208`;
  `audio_fe/.../samples/Samples.kt:161`, `:171`; `audio_be/.../SampleStore.kt:72`, `:114` to `:117`;
  `audio_bridge/.../VoiceData.kt:166`.
- **What**: `preloadSamples` and `lookAheadForSampleSounds` map EVERY queried voice to
  `SampleRequest(bank, sound, soundIndex, note)`, the synth voices and the control events included. For
  `note("c3 e3 g3").sound("supersaw")` the preloader asks `Samples.get` three times, gets null, and sends three
  `Cmd.Sample.NotFound`. Each one: a message to the audio thread, `samples[req] = NotFound(req)` in the backend's
  one `SampleStore` (never removed, for the backend's life), `version++` (so the next diagnostics rebuilds the
  `WarehouseStats`), an entry in the frontend's `sent` set and a null entry in `Samples.resolveCache`.
  An inline instrument is named by its content (`ignitor-N`, `IgnitorDslIdentity.kt:36`), so every live edit of it
  mints a new sound name and with it one new entry per distinct note in all four maps. This is the class the rule
  names ("process-wide maps that grow per edit are debt, tracked in KLANG-00210 section 11"). Section 11 lists only
  the `uniqueId()` identity maps. The full list as I found it, all process-wide and never freed:
  `globalIgnitorNames` and `globalKatalystNames` (listed), `SampleStore.samples` (the PCM and these `NotFound`
  entries), `SamplePreloader.sent`, `Samples.resolveCache`, `Samples.pcmCache`, `InMemoryUrlCache.cache`.
  `KlangOfflineRenderer.loadSamples` does filter (`:205`, `!backend.ignitorRegistry.contains`), so the offline
  path does not do this; the live path cannot use that filter as it stands, because the frontend does not know the
  backend's registry.
- **Reach**: every live playback with a synth voice; the growth per edit with any inline `Ignitor(...)`.
- **Settle it**: a `KlangPlaybackSyncSpec` case with one synth note and a counting `sendControl`: assert no
  `Cmd.Sample` is sent. Or read "samples" on the warehouse stats page after playing a song with no sample: the
  count stays 0 (`residentCount` counts PCM only), which is why the page does not show this map.
- **Confidence**: high.

### L9-06 In the browser the backend keeps one full PCM copy per requested NOTE, as doubles, and nothing ever evicts

- **Severity**: minor
- **Tag**: leak, performance
- **Where**: `audio_be/.../SampleStore.kt:72`, `:137` to `:186`; `audio_be/src/jsMain/kotlin/JsAudioBackend.kt:355`
  to `:360`; `audio_fe/.../samples/SampleIndexLoader.kt:116` to `:130` (several notes pick one zone);
  `audio_fe/.../samples/Samples.kt:165`, `:182`; `audio_fe/src/jsMain/kotlin/BrowserAudioDecoder.kt:52` to `:56`.
- **What**: the store is keyed by `SampleRequest`, which carries the note. The frontend knows that C4 and D4 pick
  the same zone (`zoneSampleCache`, `pcmCache` by `Sample` identity), but each request is its own
  `Cmd.Sample.Complete`, chunked and rebuilt in the worklet as its own `DoubleArray`. A soundfont piano played over
  40 notes that fall into 10 zones holds 40 PCM arrays in the worklet. On the JVM the 40 entries share one array
  (the command passes by reference), so the JVM does not show it; `residentBytes` counts 40 there too.
  A frame costs 8 bytes in the worklet (the engine's canonical `DoubleArray`), 8 more in the frontend's `pcmCache`,
  plus the encoded bytes in the URL cache. None of the three is bounded or evicted. Two concurrent requests for one
  `Sample` (a song's start launches them all at once, `SamplePreloader.ensureLoadedDeferred`) both miss
  `pcmCache.getOrPut`, whose loader suspends, and decode twice.
- **Reach**: any pitched sample instrument over many notes; any long sample; a long session over many songs.
- **Settle it**: `SampleStoreSpec`-style count in the browser: play one soundfont over two octaves and read
  `sampleBytes` on the warehouse stats against the bytes of its zones.
- **Confidence**: high for the mechanism; the sizes are not measured.

### L9-07 The browser and the JVM decode a sample differently, so the offline render of a sample song is not the live sound

- **Severity**: minor
- **Tag**: consistency, sound
- **Where**: `audio_fe/src/jsMain/kotlin/BrowserAudioDecoder.kt:32` (`new OfflineAudioContext(1, 1, 48000)`), `:47`
  (`getChannelData(0)`); `audio_fe/src/jvmMain/kotlin/decoders/JvmWavDecoder.kt:134`,
  `JvmGenericAudioDecoder.kt:72`, `JvmMp3Decoder.kt:62` (the channels averaged, the file's rate kept);
  `audio/ref/sample-management.md:79` against `:137`.
- **What**: two differences, both before the engine.
  1. Stereo. The browser returns the LEFT channel only (`decodeAudioData` keeps the file's channels whatever the
     context's count; channel 0 is taken). The JVM averages all channels. A stereo sample with different sides is a
     different signal and level on the two hosts. The ref page says both things: "Returns first channel as mono PCM"
     (line 79) and "Stereo samples are downmixed to mono during decoding (L+R averaged)" (line 137).
  2. Rate. `decodeAudioData` resamples to its context's rate, fixed here at 48000 whatever the engine runs at. So
     in the browser every sample arrives at 48 kHz, converted by the browser's own resampler, and at an engine rate
     of 48 kHz a sample at its own pitch steps by exactly 1. On the JVM a 44.1 kHz file stays at 44.1 kHz and the
     playhead steps by 0.91875 through the engine's linear interpolation at every pitch. The aliasing meter's lead
     3 (linear interpolation off the sample's own rate: -30 dB at 7 kHz) therefore applies to every 44.1 kHz sample
     in the corpus render and to none of them at their own pitch in the browser. And when the browser's engine runs
     at 44.1 kHz (`resolveBestSampleRate` takes what the device gives), every sample is first raised to 48 kHz and
     then stepped down by linear interpolation with no filter.
- **Reach**: every sample voice; the corpus check and `RenderWavCommand` against live playback.
- **Settle it**: decode one stereo 44.1 kHz file on both hosts and compare length, rate and the first samples; or
  render the same one-sample pattern offline and in the browser and compare spectra above 5 kHz.
- **Confidence**: high for the code on both sides; the browser's behaviour is from the Web Audio specification,
  not from a run.

### L9-08 The diagnostics are built, encoded and cloned on the audio thread about 47 times a second, and they are the clock

- **Severity**: minor
- **Tag**: realtime (periodic)
- **Where**: `audio_be/.../PlaybackEngineDispatcher.kt:292` to `:361`; `audio_jsworklet/.../KlangAudioWorklet.kt:207`
  to `:213`; `audio_be/src/jsMain/kotlin/WorkletContract.kt:46` to `:48`; `ResourceWarehouse.kt:88` to `:123`.
- **What**: the hot-path lint's lead, with its size. Per emission, on the audio thread: an `ArrayList` that grows as
  it fills, one `CylinderState` per rented cylinder of every engine that renders (a song with 8 cylinders: 8; two
  songs ringing: 16), one `Diagnostics` of 13 fields, sometimes a `WarehouseStats` of 27 fields. In the worklet the
  ring hands it to `sendFeed`, which builds the wire object tree again and `postMessage` clones that. So about 47
  times a second the audio thread makes two object graphs of some 40 to 70 properties and one structured clone.
  It is not per block and each one is small; the cost is steady garbage in the worklet's heap, on the thread that
  must not pause.
  It cannot simply be sent less often, because since KLANG-00399 the report IS the frontend's clock
  (`backendNowMs`). But only that one number needs 50 Hz. The cylinder list and the warehouse change with a rent, a
  return or a deactivation, and the frontend only draws them.
- **Reach**: every running backend, playing or idle.
- **Settle it**: count allocations per second of an idle worklet in the browser's memory profile; or the JVM
  allocation counter around 1000 blocks of an idle dispatcher (expect about 125 emissions).
- **Confidence**: high for what is built; the cost is not measured.

### L9-09 A sample upload costs the audio thread one megabyte clone and one megabyte copy per chunk, and the whole PCM allocation at the first chunk; the comment reasons with half the size

- **Severity**: minor
- **Tag**: realtime (per sample chunk), doc
- **Where**: `audio_be/src/jsMain/kotlin/JsAudioBackend.kt:187` to `:201` (the comment: "One chunk is ~512 KB ...
  64 * 1024 frames ... ~51 MB/s"), `:357` (`cmd.toChunks(128 * 1024)`); `audio_bridge/.../KlangCommLink.kt:157` to
  `:176`; `audio_be/.../SampleStore.kt:141` to `:172`, `:194`.
- **What**: `toChunks(128 * 1024)` makes chunks of 131072 doubles, 1 MB each, one every 10 ms: about 100 MB/s, twice
  what the comment that argues for the drip says. Each chunk is deserialised by the browser on the audio thread
  before `onmessage` runs, then copied once more by `copyInto`. The first chunk of a sample allocates
  `DoubleArray(totalSize)` on the audio thread (the KDoc at `SampleStore.kt:23` says so): for a one-minute sample
  that is 23 MB to allocate and zero inside one 2.67 ms block, while a song may be playing (the look-ahead loads
  samples 8 cycles ahead, during playback).
  `toChunks` on the main thread also copies the whole PCM a second time before the first chunk leaves.
- **Reach**: every sample loaded in the browser; the allocation spike with long samples loaded while something
  plays.
- **Settle it**: load a 60 s sample during playback with the engine health page open and read the render headroom
  at the first chunk.
- **Confidence**: high for the sizes (arithmetic) and the thread; the spike is not measured.

### L9-10 Work on the audio thread at command time has no bound: the optimizer, chain builds, the replace dedup, and any number of voices

- **Severity**: minor
- **Tag**: realtime (per command, per edit)
- **Where**: `audio_be/.../ignitor/IgnitorRegistry.kt:105` to `:117` (`dsl.optimize()` inside `register`);
  `voices/VoiceScheduler.kt:290` to `:304` (`dedupAgainstActive`: a `BooleanArray`, a `filter`, a lambda per
  incoming voice, incoming times active), `:405` to `:418` (per voice: a heap push, `asSampleRequest()` and a
  `HashMap` probe keyed by a data class for every sound that is not a registered instrument, `masterBus.prepare`),
  `:543` to `:607` (every voice due in the block is built in that block); `SampleStore.kt:72`, `:90`.
- **What**: in the worklet a command is handled on the audio thread, between two blocks. Nothing there is sized.
  1. `RegisterIgnitor` runs the graph optimizer over the whole tree, once per edit.
  2. `ReplaceVoices` (every edit, every tempo change) allocates and compares every resent voice with every active
     timeline voice by full `VoiceData` equality.
  3. A flood: `ScheduleVoices` with 10000 voices is first cloned and decoded on the audio thread, then pushed one
     by one; the ones due in the next block are all built at once (`makeVoice`, the tree build), and then all
     render. There is no polyphony cap and no shedding: `active` is an unbounded list, and the only bounded counts
     are the unison voices and the cylinders. At the JVM's measured 2.24 ns per sample for a plain sine, 9000
     plain voices fill a 2.67 ms block; a pattern that asks for them (`s("hh*2048")`, stacked `fast`) starves the
     thread until they end, and the browser can drop the worklet for it.
  4. `SampleStore.samples` is a `mutableMapOf` keyed by a data class of three strings and an int. The dispatcher's
     own comment (`PlaybackEngineDispatcher.kt:35` to `:38`) records that such a lookup costs about 40 bytes on V8;
     every sample voice pays it at schedule (`requestIfMissing`) and at promotion (`getComplete`), plus the
     `SampleRequest` it allocates for the key.
- **Reach**: every live edit (1, 2); dense patterns (3); every sample voice (4).
- **Settle it**: the allocation counter of `audio_benchmark` around `handle(ReplaceVoices)` with 500 voices against
  50 active; a timed `handle(ScheduleVoices)` of 10000 voices under node.
- **Confidence**: medium (the code is as described; no cost measured).

### L9-11 The "dropped" and "soundless" voice counts fall back to 0 at every stop; the KDoc calls the counters monotone; the stats snapshot's version is a sum that can stand still

- **Severity**: minor
- **Tag**: consistency, doc
- **Where**: `audio_be/.../voices/VoiceScheduler.kt:200` to `:203`, `:227`; `voices/PlaybackCtx.kt` (the two
  counters live in the context); `PlaybackEngineDispatcher.kt:310` to `:318`, `:347` to `:349`;
  `warehouse/ResourceWarehouse.kt:88` to `:95`; `audio_bridge/.../KlangCommLink.kt:296`, `:330`;
  `src/jsMain/kotlin/comp/PlayerWarehouseStats.kt:51`, `:155`.
- **What**: `droppedVoiceCount()` is `playback?.droppedVoices ?: 0`. `cleanup()` sets `playback = null`, so the
  moment a playback stops, its late and soundless voices leave the sum, and when its engine is disposed they are
  gone. The UI's figure "dropped" is therefore "dropped by the playbacks that are playing right now". A user who
  hears a stutter, stops and looks at the page sees 0. `VoiceScheduler`'s KDoc calls this count "the only visible
  trace of a frontend stall", and `WarehouseStats`' KDoc says "Counters are monotone since backend start" while its
  own field comment says "Across all live engines".
  Because the two can fall, `stats()`' change test is unsound: the version is the SUM of the parts' versions plus
  `droppedVoices + deniedRents`. A playback with 3 dropped voices that stops (minus 3) in the same 21 ms in which
  three sample messages arrive (plus 3) leaves the sum where it was, and the cached snapshot, with the old sample
  count and the old dropped count, is served until the next change.
- **Reach**: the engine health and warehouse pages.
- **Settle it**: `PlaybackEngineDispatcherDiagnosticsTest`: schedule one late voice, read `droppedVoices` 1, send
  `Cleanup`, render one report, read it again. It reads 0.
- **Confidence**: high.

### L9-12 The warm-up runs every Ignitor node kind, and on the bus side only the six born-with stages

- **Severity**: minor
- **Tag**: performance, test
- **Where**: `audio_be/.../WarmupRunner.kt:160` to `:255`; `WarmupVocabulary.kt`; `audio_be/src/jvmTest/kotlin/
  WarmupVocabularySpec.kt` (guards `IgnitorDsl` kinds only).
- **What**: what the warm-up does: 16 voices, one per block and cylinder, through delay, reverb and one of phaser,
  compressor, body, vowel; twelve sounds, six of them the vocabulary graphs. It runs 18 blocks, then waits at most
  64 more for the shelves to be zeroed: 48 ms to 219 ms at 48 kHz before `BackendReady` (the comment in
  `JvmAudioBackend.kt:39` still says about 21 ms). It cannot be heard: both hosts zero the output while
  `isWarming`, and the block that ends it is zeroed too. What it leaves behind: the 256-frame `--warmup--` sample
  in the store (said so), the stocked shelves (its purpose), the scratch high-water mark, a `PlaybackAnchored` for
  `--WARMUP--` and a few diagnostics that count its voices, the headroom average of its heavy blocks (gone after
  about 30 blocks). The house stage is reset.
  What it never runs, so its first use is cold on the audio thread in a live block: `MasterBus.process` and any
  `master(...)` chain; every Katalyst stage outside the born-with chain (arithmetic, parallel, regions, distort,
  shimmer, eq, a user chain's builder); the chain swap and its crossfade; the duck; `PlaybackGain` and
  `TailRelease` (the engine's own-bus path, `PlaybackEngine.kt:205` to `:226`); the solo ramp, cut groups,
  `ReplaceVoices` and its dedup; and the wire decoder, because the warm-up calls `dispatcher.handle` with Kotlin
  objects and never goes through `WorkletContract.decodeCmd`.
  Each warmed sound renders 3 to 18 blocks (the voice on cylinder 15 starts in block 15 of 18). Whether that is
  enough for V8 to compile the loops at its top tier is a measurement, see the suspicions.
- **Reach**: the first `master(...)`, the first custom `katalyst(...)`, the first live edit of a session, on a
  phone.
- **Settle it**: the Fairphone measurement the warm-up was built with, on a song whose first block has a master
  chain, first run against second run.
- **Confidence**: high for the coverage (read), low for the cost.

### L9-13 `Samples`' two caches are plain maps written from several threads on the JVM

- **Severity**: minor
- **Tag**: bug (JVM, frontend)
- **Where**: `audio_fe/.../samples/Samples.kt:161` to `:185`; `klang/.../SamplePreloader.kt:82` to `:84`, `:154`;
  `klang/src/jvmMain/kotlin/index_jvm.kt` (`fetcherDispatcher = Dispatchers.Default`).
- **What**: `ensureLoadedDeferred` starts one `scope.async(dispatcher)` per new request, and on the JVM the
  dispatcher is the default pool, so a song's start runs `Samples.get` on several threads at once. `resolveCache`
  and `pcmCache` are `mutableMapOf` (a `LinkedHashMap`) with a suspending loader inside `getOrPut`: concurrent
  `put`s on an unsynchronised map. The preloader's lock guards its own three maps and not these. The class KDoc
  says "safe to call from scheduler thread to prefetch". Usual outcome: a lost entry and a second download or
  decode; possible outcome: a corrupted map. The offline renderer is not affected (it loads in one loop).
- **Reach**: the JVM live player, a song with several samples.
- **Settle it**: a JVM spec that calls `Samples.get` for 64 requests from 8 threads with a loader that yields, and
  asserts the cache size and one load per sample.
- **Confidence**: medium (read, not run).

### L9-14 Dead and half-wired things in the shell

- **Severity**: note
- **Tag**: dead
- **Where and what**:
  - `KlangAudioWorklet.kt:94`, `:162`: `isPlaying` is created true and never written. The worklet has no tear-down
    of its own: no message stops it, `process` always returns true; the end is the main thread disconnecting the
    node and closing the context (`JsAudioBackend.kt:254` to `:281`).
  - `KlangAudioWorklet.kt:165` with `audio_bridge/src/jsMain/kotlin/KlangTime.kt:55`: the worklet updates its
    `KlangTime` every block, and nobody reads that clock after the one call at start-up (`:126`). The clock is also
    off by a block: the setter takes "the first non-zero frame" as its start, which is frame 128, so it stands
    still from block 0 to block 1. Harmless only because it is unread.
  - `PlaybackEngineDispatcher.kt:109`, `:191`, `:220`: the `Phase.Disposed` arms cannot be reached. Both paths
    that dispose (`:240` to `:245`, `:378` to `:386`) take the engine out of `engines` first.
  - `AudioBackendContext.kt`'s KDoc: "`performanceTimeMs` is transitional - once diagnostics emission moves up to
    the dispatcher (D5)". D5 is done; the note is stale.
- **Confidence**: high.

### L9-15 The ref pages and KDocs of this layer that say what the code does not

- **Severity**: note
- **Tag**: doc
- **Where and what**:
  - `audio/ref/architecture.md`, "Platform Backends": `KlangTime.internalMsNow()` is given as
    `System.nanoTime() / 1_000_000L` and `performance.now()`. Both actuals add the wall clock at creation
    (`KlangTime.kt` JVM and JS). The common KDoc says "Not epoch-based ... relative to start, not epoch"
    (`audio_bridge/src/commonMain/kotlin/KlangTime.kt:10`, `:15`). L9-03 hangs on this.
  - `audio/ref/architecture.md`, the `Cmd` table: `Cleanup` is "stop all voices, reset state"; the code lets
    playing voices ring out. `StartRealtimeVoice`, `StopRealtimeVoice` and `SetGain` are missing.
  - `audio/ref/sample-management.md` describes an API that is not there: `Samples.Index.addBank`,
    `AudioDecoder.decode(bytes, mimeType)` (it is `decodeMonoFloatPcm(audioBytes)`), `UrlCache.get/put` (it is
    `has/get/getOrPut`), `AssetLoader.load` (it is `download`), `MonoSamplePcm.pcm: FloatArray` (it is a
    `DoubleArray`; `audio/CLAUDE.md`'s module map says FloatArray too), `decoders/AudioDecoder.kt` (it is
    `samples/AudioDecoder.kt`), and step 7 "`VoiceScheduler` receives it, stores PCM, activates the pending sample
    voice": there is no pending sample voice, a voice whose sample is not complete at its start is dropped whole
    and counted soundless (`VoiceScheduler.kt:648` to `:652`).
  - `docs/audio-backend-file-map.md`: `VoiceScheduler` is listed with "diagnostics emit"; the dispatcher emits.
  - `audio/MEMORY.md` "One clock": "one report per 20 ms of audio". The counter is reset to 0 at each emission
    (`PlaybackEngineDispatcher.kt:304`), so a report comes every 1024 frames at 48 kHz, 21.3 ms.
  - `JvmAudioBackend.kt:39`: "costs ~21 ms of silence at start"; it is 48 ms at least (L9-12).
  - `JsAudioBackend.kt:189`: the chunk size (L9-09).
  - `InlineDslRegistrar.kt:33`: "Both sides free it when the playback stops" (L9-04).
- **Confidence**: high.

### L9-16 The render headroom is timed with `Date.now()` in the worklet

- **Severity**: note
- **Tag**: consistency
- **Where**: `audio_jsworklet/.../KlangAudioWorklet.kt:53`; `PlaybackEngineDispatcher.kt:256`, `:293` to `:296`.
- **What**: `Date.now()` has a step of 1 ms and is the wall clock. A block is 2.67 ms, so one block's duration
  reads 0, 1, 2 or 3 ms, and the headroom is an average over ten such readings: right in the mean, coarse per
  report. A wall-clock step (a time sync) puts one outlier of any size into the average. The "Motor heat" graph and
  any decision taken from it rest on this.
- **Confidence**: high for the code; `performance.now()` is not available in every worklet scope, which may be the
  reason.

## Performance candidates

| Where | What | Per | Measured cost | Expected gain (rough) | Risk to the sound |
|-------|------|-----|---------------|-----------------------|-------------------|
| `PlaybackEngineDispatcher.kt:292` to `:361`, `KlangAudioWorklet.kt:207` | Send the timeline (one number) at 50 Hz and the cylinder list plus warehouse stats only when they changed, or at 4 Hz | periodic, 47/s | not in `stage-cost` | most of the worklet's steady garbage when idle | none |
| `JsAudioBackend.kt:355`, `SampleStore.kt:137` | Hand a sample over as ONE transferred `ArrayBuffer` (`postMessage` with a transfer list): no clone, no chunk copy, no allocation on the audio thread | per sample | not measured | removes 2 MB of copying per chunk and the whole-PCM allocation from the audio thread | none |
| `SampleStore.kt:72`, `VoiceScheduler.kt:537` | Key the store by the resolved sample, not by the request with its note; a `KlangMap` on a string key for the per-voice lookup | per sample voice, per note | 40 bytes per lookup on V8 by the dispatcher's own comment | memory of pitched instruments by the notes-per-zone factor; two allocations less per sample voice | none |
| `KlangPatternScheduler.kt:349`, `:737` | Do not send `NotFound` for names the playback registered as ignitors, nor for built-ins | per start, per edit | not measured | fewer audio-thread messages at every start; ends L9-05's growth | none |
| `IgnitorRegistry.kt:105` | Optimize the tree on the frontend and send the optimized tree | per edit | not measured | the optimizer leaves the audio thread | a decision: the two trees must stay the same thing on both hosts |
| `VoiceScheduler.kt:290` | The replace dedup without `filter`, `firstOrNull` and the `BooleanArray` per call | per edit | not measured | small | none |
| `BrowserAudioDecoder.kt:28`, `:55` | `audioBytes.toTypedArray()` makes a boxed JS array of every byte before the `Int8Array`; the PCM is widened to doubles on the main thread | per decoded sample | not measured | main-thread stall at load, which is the scheduler's thread | none |

## Suspicions

- **A NaN mix reads as silent.** `Cylinder.isMixBufferSilent` (`Cylinder.kt:706`) and the master's audibility test
  use `>` and `<`. A cylinder whose mix is NaN counts its silence grace. Whether that lets a NaN-latched cylinder
  deactivate and reset (a recovery by accident) or hides it from the engine's end-of-life logic, I did not trace.
  Settle: the stage probe's duck case, then 20 blocks without a voice, read `cylinder.isActive`.
- **`MasterBus.sustainsItself()` asks the chain's configuration, not whether it sounds** (`MasterBus.kt:237`), while
  `PlaybackEngine.isIdle`'s KDoc says "while it still sounds". A stopped engine whose master holds a silent delay at
  feedback 1 beside a finite cylinder tail longer than 20 s would be released with it. I did not read the delay
  stage's `sustainsItself` states to the end. Settle: `EngineStopReleaseSpec` with that pair.
- **The warm-up's length against V8's tiers.** A warmed sound renders 3 to 18 blocks. Whether the loops reach the
  optimizing compiler in that time is not knowable by reading. Settle: `--trace-opt` under node on the production
  bundle, or first note against second note on the phone.
- **Background tabs.** The sample drip is a 10 ms `setTimeout` chain (`JsAudioBackend.kt:233`) and the feedback
  reader a 16 ms `delay` (`KlangPlayer.kt:209`); a throttled tab runs both once a second. The clock's cap (0.5 s)
  and the look-ahead (1.5 s) cover the reader. A sample upload slows to 1 MB per second. Not tried.
- **The frontend clock stamps a report when it is HANDLED, not when it arrived** (`PlaybackClock.onReport`, after
  the 16 ms poll), so it runs up to 16 ms behind and its steps are uneven. Voices carry times from the anchored
  epoch, so I found no path by which this reaches the sound; it reaches the UI's highlight times.
- **A stale `PlaybackAnchored`** of an earlier start can arrive after a restart of the same object and set
  `epochSec` back until the new one arrives (`KlangPatternScheduler.kt:305` to `:308`). A few milliseconds, same
  reach as L9-04.
- **The JVM reports no output latency** (`outputLatencyMs` 0.0) while its line buffers 250 ms
  (`JvmAudioBackend.kt:68`); realtime notes and anything timed to the sound are that far off on the JVM.
- **The clone of a 10000-voice message** happens on the audio thread before `onmessage`; its cost may dwarf the
  decode figure in `WorkletContract`'s KDoc. Not measured.

## Decisions worth revisiting

- **A stop does not close the gate of the notes that are sounding** (`VoiceScheduler.cleanup`, "timeline voices
  keep their natural ring-out"). A pad with a 60 s gate that started two seconds ago plays 58 s more after Stop,
  and its engine lives that long; the 20 s hold and the release apply to endless bus tails only, never to voices.
  "A stopped playback is never hard-cut" reads as a rule about tails; for a held note the musical expectation is a
  note-off at the stop (its release, not a cut), which is what `cleanup` already does for held realtime voices.
- **The timeline's zero.** Starting the backend's timeline at 0 instead of at the wall clock would make every
  second on it exact to about 1e-12 frames at any uptime a session reaches, make the live path equal to the offline
  one, and end L9-03 without touching `floor`. Since KLANG-00399 the frontend's clock runs on the reports; the wall
  clock is only its guess before the first report, and no playback can start before the first report.
- **A voice whose sample is a moment late is dropped whole.** For a hit that is right; for a long sample (a loop of
  several bars) it means silence for its whole length because of a few milliseconds. The store has the `Partial`
  state and the voice has `Pending`; a voice that waits for its sample and enters late is the "no late voices"
  question again, so this is a decision, not a fix.
- **The wire has no notion of a start.** A playback's life on the backend is inferred from which commands arrive in
  which order (L9-02, L9-04, the `SetGain` gate and its lock). One number on `Cleanup` and on every later command
  of the same start would let the dispatcher drop a straggler and keep the gain with the id.
- **PCM as doubles in the store**: 8 bytes per frame for 16-bit material, twice over in the browser.

## Listening tasks

- Stop a song in the middle of a long held note (a pad over several cycles) and judge whether the ring-out to the
  gate's end is what a Stop should sound like, against a release at the stop.
- One stereo sample with clearly different sides, played in the browser and rendered offline: is the difference in
  level and colour acceptable for a render that is taken as the reference.
- A bright 44.1 kHz sample (a hi-hat) at its own pitch, browser against offline render, for the top octave.

## Test truth

- **Pinned well**: the `Phase` machine. `PlaybackEnginePhaseSpec` and `EngineStopReleaseSpec` walk every cell of the
  class KDoc's table that can be reached, the hold, the release, the detach, both disposal orders, a second stop,
  `cleanupHard`. `PlaybackEngineGainSpec` pins the gain against a stopped, a releasing and a missing engine.
  `SchedulerStartupSpec` pins B1 and B2 at a timeline of 0. `OfflineBackendSpec` pins the anchor and both counters.
- **Not pinned**:
  - a timeline at the live magnitude. Every rig sets `setBackendStartTime(0.0)`, two use 1000.0. Nothing would
    notice L9-03, and `LongRunningTimelineSpec`'s "hundred years" case asserts only that some block has audio.
  - any command after `Cleanup` other than `ReplaceVoices` and `SetGain`: a late `ScheduleVoices` is pinned as "a
    resume", which is L9-02 seen from the other side; `RegisterIgnitor` and `StartRealtimeVoice` on a stopped
    engine are not pinned at all.
  - a gain on a stopped engine that is disposed before the start's voices (L9-04).
  - an exception in a render or a command (L9-01).
  - the counters across a stop (L9-11).
  - the worklet: no test (KLANG-00308 section 2 waits on the maintainer). Start-up order, the zeroed warm-up
    blocks, mono output, the feedback pump are covered by nothing.
  - the frontend preloader against synth voices (L9-05); a restart of a playback object.
- **Mutations I expect to survive** (not run):
  - `PlaybackEngineDispatcher.kt:296`: any change of the headroom average's weights; the only assertion is
    "headroom is a valid ratio (<= 1.0)".
  - `VoiceScheduler.kt:227`: remove `playback = null` from `cleanup`. The counters would then survive a stop; I
    expect the epoch specs to catch it ("once per start"), so this one may die. Worth one `mutate` run.
  - `KlangAudioWorklet.kt:180` to `:187`: drop the zeroing of the output while warming. No spec renders the worklet.
  - `WarmupRunner.kt:197` to `:214`: take the delay and reverb slots off the warm-up voices. If
    `WarmupVocabularySpec` only checks the Ignitor kinds, the warm-up would stock no ring and no network and stay
    green; `CylinderShelfSpec` may catch it.
  - `ResourceWarehouse.kt:88`: replace the version sum by any other combination; no spec makes two parts move in
    opposite directions.

## For the audit ruleset

- What worked: the brief's questions for this layer were concrete enough to be answered one by one from the code,
  and "trace it to an input and an output" kept the report short. Reading the frontend's half was where four of the
  findings came from; a layer cut at the module border would have missed them.
- What did not: this layer has nothing in `stage-cost`, `clicks`, `aliasing` or `framing`, so five of the seven
  probe tables were dead weight to read first. Tell an auditor which tables touch the layer.
- A calculator is not on the list of what may be run. L9-03 is floating-point arithmetic that I did by hand; one
  allowed `python3 -I -c` for arithmetic that touches no file would make such a finding cheaper and safer.
- The scope needs a host rule: several questions here have different answers in the browser, on the JVM and
  offline. A fixed column "browser / JVM / offline" per finding would force the auditor to say which.
- Reach was the hardest thing to state honestly. Two findings are real in the API and not reachable from the app
  today; the severity scale has no word for that. A "latent" level between minor and note would help.
- Doc drift deserves its own pass with a checklist of the ref pages, not a share of each layer's time:
  `sample-management.md` describes an API that no longer exists, and that is cheaper to find by diffing names than
  by reading.
