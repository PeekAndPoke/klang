# In-browser Benchmark page (run the CPU benchmarks from the web UI)

Status: **future / dev tool — not user-facing for launch.** Created 2026-08-19 (scoped during the unified-EQ
workstream, but independent of it). A "Benchmarks" entry under the "..." sidebar menu (next to Samples Library
and Credits) where the current benchmark suites can be run individually or all at once, with progress and
result tables in the page. Primarily a development tool: real numbers from real machines/browsers instead of
extrapolating from JVM reports ("browser is ~10-30x slower"). Self-contained; resumable cold.

## Decisions already made

- **Do NOT touch the audio backend for the first version.** Run the existing harnesses on the FE main
  thread. The backend-capability design (benchmarks behind the comm link) is captured below as a
  later evolution — the page's surface (pick suite → progress → tables) survives that move unchanged.
- Dev tool first; user-facing framing ("your machine's numbers") can come later, if ever.

## What exists today (verified 2026-08-19)

Two harnesses:

1. **`audio_benchmark` module** — micro benchmarks (ignitors, effects, VoiceData copy, worklet
   serialization). Already multiplatform: harnesses in `commonMain`, runs on JS today
   (`./gradlew :audio_benchmark:jsBrowserDevelopmentRun`, see its README). `run(cases)` returns
   structured `Result` data classes — the page can render tables directly, no console scraping.
2. **`SongBenchmark`** (`src/jvmMain/kotlin/SongBenchmark.kt`) — the real one: compiles KlangScript
   song code, drives `KlangAudioRenderer.renderBlock`, times every block. JVM-only *by placement*,
   not by dependency.

Portability findings (all checked, not guessed):

- **The app's JS bundle already has `audio_be` on its classpath**: root module → `api(project(":klang"))`
  → the `:klang` aggregate `api`-exposes `:audio_be`, `:audio_fe`, `:audio_bridge`, `:klangscript`.
  No new plumbing for the song harness.
- `:audio_benchmark` is NOT a dependency of the app — one `implementation(project(":audio_benchmark"))`
  in the root's jsMain deps fixes that.
- `FrozenSongs.kt` (jvmMain) is pure embedded string constants, no file I/O. Moves to commonMain as-is.
- `SongBenchmarkCases.kt` (jvmMain) only imports `builtinsongs` (already commonMain). Moves as-is.
- `SongBenchmark.kt` has exactly one JVM-ism: `runBlocking` around the sample preload.
- All `String.format` reporting + file output lives in `SongBenchmarkMain.kt`, which stays jvmMain.

## Near-term plan (no backend changes, ~2 days)

### A. Micro suites (~free)

- Add `:audio_benchmark` dep to root jsMain.
- Call `runCase` per case from a coroutine with `yield()` between cases → live progress without
  noticeable freezes (each case is a few iterations of a few hundred 128-frame blocks).
- `WorkletSerializationBenchmark` is in audio_benchmark's jsMain → directly callable from the app.

### B. Song suites (a move + a suspend)

- Move `SongBenchmark.kt`, `SongBenchmarkCases.kt`, `FrozenSongs.kt` → `src/commonMain`.
- Make `run`/`renderPass` suspend; JVM CLI keeps its `runBlocking` wrapper in `SongBenchmarkMain`.
- In the browser, reuse the app's already-loaded `Samples` instance instead of `Samples.create`.
- **Add `yield()` every ~64 blocks + a progress callback.** One song case ≈ 97 s of audio
  (7 passes × 8 cycles @ 34.5 rpm) ≈ 5-30 s of compute in the browser — without yields the tab
  freezes for all of it. Yield outside the per-block timing so totals stay honest.

### C. UI (mechanical)

- `Nav.benchmarks = Static("/benchmarks")` + `mount()` in the MenuLayout block of `nav.kt`.
- `SidebarMenu`: `State.Benchmarks` object, item in `renderDefaultMenu()` (next to Samples Library /
  Credits), arm in `inferState()`, and add to the "More is selected" list (`State.Main ->` branch).
- `BenchmarksPage`: suite cards + Run / Run All, progress line (suite, case x/y, pass), result tables
  with the existing columns (onsets, medRTF, peakRTF, µs/cycle), "copy as markdown" button for parity
  with `docs/benchmarks/` reports.

### Caveats to put on the page

1. **Measures the main thread, not the worklet thread.** Same V8/JIT/code → relative numbers (ladder
   deltas, voice ranking) transfer well; absolute headroom differs somewhat.
2. **Tab must stay focused** — background tabs are throttled. Guard with `document.visibilityState`
   between cases.
3. **Timer resolution**: main-thread `performance.now()` ≈ 100 µs Chrome / 1 ms Firefox. `medianRtf`
   solid everywhere; label `peakBlockRtf` approximate (or hide) on coarse timers.
4. **Bundle size**: first real reference to `audio_be` from the main bundle grows the DCE'd output
   (the worklet copy is a separate artifact). Check before/after.
5. It renders offline into a `ShortArray` — the page makes **no sound**.

## Later evolution: benchmarks as a backend capability

Motivation: a future Wasm-in-worklet or native backend makes main-thread JS numbers meaningless — the
benchmark should run on whatever backend actually renders. Sketch (respects "BE stays cycle-free"):

- `renderPass` has a natural seam. **FE half**: compile KlangScript → `queryEvents` (cycles!) →
  `ScheduledVoice` list + oscillator/pipeline registrations + sample preloads. **BE half**: fresh
  renderer, schedule voices, render N blocks, time them — needs only audio_be commonMain.
- Protocol over `KlangCommLink`: `Cmd.RunBenchmark(job)` (voices, registrations, sampleRate,
  blockFrames, numBlocks, passes) + `Msg.BenchmarkProgress` / `Msg.BenchmarkResult`. Samples reuse the
  existing `Cmd.Sample.Complete` path. Micro suites become named suites over the same protocol (their
  harnesses build `VoiceData` by hand, no FE prep needed).
- Executor in audio_be commonMain (~150 lines, the ported bottom half of `renderPass`). Every host —
  JVM CLI, JS worklet, future Wasm/native — runs the identical executor. Same job on two backends =
  honest apples-to-apples comparison on real user machines.

### The audio-thread clock problem (why naive stepping fails)

**There is no time source on the audio thread** — worklet time is sample-based only (frame-derived;
see the worklet-clock-divergence note). Consequences and workarounds:

- "Run up to X **ms** per `process()` callback" is impossible. **Budget in blocks instead**: fixed
  quota of N benchmark blocks per callback (conservative 2-4, or FE-tuned).
- **FE-timed saturated batches**: with playback stopped, blown deadlines are inaudible. Worklet chews
  big slices per callback (≫ one quantum of work) → the audio system falls permanently behind and
  calls `process()` back-to-back → inter-callback gaps become negligible. FE times cmd→done with its
  own `performance.now()` across a seconds-long pass; comm latency amortizes to noise. Yields
  `medianRtf` for the real audio thread. `peakBlockRtf` is lost (no per-block stamps) — it was the
  shakiest browser number anyway.
- **Audio-clock slip test**: FE holds both clocks (`performance.now` + `AudioContext.currentTime`).
  Ramp the per-callback block quota while watching for the audio clock slipping against wall clock;
  the largest non-slipping quota measures **sustainable headroom on the actual realtime thread**.
  Arguably the best user-facing number: not "RTF 0.31" but "your machine can run 3.2× this song".
- 5-minute spike some day: does `Date.now()` work in `AudioWorkletGlobalScope`? It is an ECMAScript
  built-in (it's `performance` the spec withholds), so ms-resolution thread-local timing may exist —
  plenty for pass totals over seconds. Design must not depend on it.
- Worklet-specific problem: a native daemon has real clocks; Wasm-in-worklet is covered by the
  FE-timed schemes.
