# SprudelVoiceData clone() cost, grouped storage

> Console output of `runVoiceDataCopyBenchmark` (`audio_benchmark/src/commonMain/kotlin/VoiceDataCopyBenchmark.kt`),
> run 2026-09-16 via `./gradlew :audio_benchmark:jvmRun` and `:jsNodeProductionRun` (unfiltered runs; the
> microbenchmark prints before the ignitor tables) for the blog post
> `docs/blog/2026-06-06-twenty-allocations-per-note/`. The pre-grouping flat copy() of 2026-06-06 (820 ns/op on
> JS) and the rejected fastCopy (18,390 ns/op) are recorded in
> `docs/tasks-archive/2026-06/20260607-mutable-voicedata-optimization.md`.

```
=== SprudelVoiceData clone() cost — grouped storage (all optional clusters) ===
Platform: JVM 17.0.17 (Amazon.com Inc.) / Linux / AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics (16 cores)
Warmup 100000 ops; 300000 ops x 5 trials (median).
clone() = deep (groups copied); copy() = shallow (group refs shared — shown as the floor).

  leaf  (0 groups)      clone() : 110.25 ns/op   copy() : 118.93 ns/op
  voice (4 groups)      clone() : 53.91 ns/op   copy() : 41.62 ns/op
  full  (all 15 groups) clone() : 83.61 ns/op   copy() : 41.75 ns/op
  reference: pre-grouping flat 105-field copy()/clone() ≈ 820 ns/op (JS)
```

```
=== SprudelVoiceData clone() cost — grouped storage (all optional clusters) ===
Platform: JS / Node.js/24
Warmup 100000 ops; 300000 ops x 5 trials (median).
clone() = deep (groups copied); copy() = shallow (group refs shared — shown as the floor).

  leaf  (0 groups)      clone() : 78.33 ns/op   copy() : 85.25 ns/op
  voice (4 groups)      clone() : 179.89 ns/op   copy() : 85.99 ns/op
  full  (all 15 groups) clone() : 541.10 ns/op   copy() : 84.70 ns/op
  reference: pre-grouping flat 105-field copy()/clone() ≈ 820 ns/op (JS)
```
