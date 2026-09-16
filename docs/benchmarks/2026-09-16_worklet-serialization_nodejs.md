# Worklet ScheduledVoice serialization cost (per voice, JS)

> Console output of `runWorkletSerializationBenchmark` (`audio_benchmark/src/jsMain/kotlin/WorkletSerializationBenchmark.kt`),
> run 2026-09-16 via `KLANG_BENCH_FILTER=zzz-none ./gradlew :audio_benchmark:jsNodeProductionRun` for the blog
> post `docs/blog/2026-06-07-sixty-seven-microseconds-to-385-nanoseconds/`. The kotlinx baseline this codec
> replaced is gone from the codebase; the 2026-06-07 comparison is in
> `docs/tasks-archive/2026-06/20260607-worklet-codec-ksp.md`.

```
=== Worklet ScheduledVoice serialization cost (per voice, JS) ===
Platform: JS / Node.js/24
200000 ops x 5 trials (median). generated codec round-trips == original: true

KSP-generated wire codec (used by WorkletContract):
  encode (frontend send) : 848 ns/op
  structuredClone (postMessage)   : 11757 ns/op
  decode (worklet recv)  : 562 ns/op
```
