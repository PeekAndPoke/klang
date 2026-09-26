# Phase 3 step 6, commit 3: the benchmark (2026-09-26)

A = 5bff7c5e (HEAD before step 6), B = 7255d41e (step 6 commits 1 and 2), both as `git archive` trees in the session's scratchpad with the SAME `audio_benchmark` module (the new `sawtooth_8v` case added to both). Runs interleaved
A, B, A, B; the first run per tree and platform was a build and warm-up run and is not counted
(`warm-*.txt`). Machine: the maintainer's AMD Ryzen 9 PRO 7940HS. Block 128 frames, 44.1 kHz (the
`IgnitorBenchmark` defaults: 10000 warm-up blocks, 5000 measured blocks, median of 3 iterations).

## The target

`docs/tasks/builtin-instruments.md:27`: "Today's number phase 3 must not regress: 9290 ns per block for 8
sustained saws." Lines 17 to 21: measured on the JVM, "8 sustained voices through the real renderer", a plain
`sound("saw")`, 1161 ns per block per voice. The spike's harness (`phase3-spike/`) is no longer on disk, so the
case is rebuilt as `IgnitorBenchmark.Case("sawtooth_8v", voiceCount = 8, voice("sawtooth"))`: 8 sustained
built-in saws (440 Hz + 2 Hz per voice, `AdsrDef.defaultSynth`) through `KlangAudioRenderer.renderBlock`.

## sawtooth_8v, us per block (render time for all 8 voices)

| platform | pair | A (5bff7c5e) | B (7255d41e) | B - A |
|---|---|---|---|---|
| JVM | 1 | 9.846 | 9.866 | +0.020 |
| JVM | 2 | 10.470 | 10.602 | +0.131 |
| JVM | 3 | 9.954 | 10.407 | +0.453 |
| JVM | 4 | 9.812 | 10.782 | +0.970 |
| JVM | 5 | 9.878 | 9.966 | +0.088 |
| JVM | 6 | 10.306 | 9.901 | -0.405 |
| Node.js | 1 | 16.903 | 17.799 | +0.896 |
| Node.js | 2 | 19.298 | 18.146 | -1.152 |
| Node.js | 3 | 18.003 | 19.725 | +1.722 |
| Node.js | 4 | 17.361 | 17.819 | +0.458 |
| Node.js | 5 | 18.941 | 18.285 | -0.656 |
| Node.js | 6 | 17.847 | 17.587 | -0.261 |

| platform | A mean (sd) | B mean (sd) | B / A | paired diff mean (sd), n = 6 | A / 9290 ns | B / 9290 ns |
|---|---|---|---|---|---|---|
| JVM | 10.044 (0.275) | 10.254 (0.395) | 1.021 | +0.209 (0.463), t about 1.1 | 1.081 | 1.104 |
| Node.js | 18.059 (0.915) | 18.227 (0.776) | 1.009 | +0.168 (1.061), t about 0.4 | 1.944 | 1.962 |

Per-voice marginal cost ((8v - 1v) / 7, the 1-voice `sawtooth` row from the same runs): JVM A 792 ns, B 780 ns;
Node.js A 1439 ns, B 1465 ns. The fixed per-block renderer cost (the 1-voice row minus one voice) is about
3.7 us on the JVM and 6.5 us on Node.js, the same on both trees.

Reading: within noise on both platforms. Both trees sit about 8 % above the spike's 9290 ns on the JVM, and so did
HEAD before step 6: the harness is not the spike's (unknown sample rate and renderer setup), so the ratio is a
harness offset, not a regression. The spike's predicted per-voice win (720 against 1161 ns) does not show: the
strip before step 6 already ran at about 790 ns per voice here (steps 5b's `EnvelopeCore` and the gate's
earlier work had made the strip cheaper since the spike), and the tree runs at the same cost.

## Note-on load: `runSongBenchmark --args=songs` (JVM)

`--args=frozen` is not a suite name (it falls through to `all()`); `songs` is the frozen-songs suite.

| row | column | A (3 runs) | B (3 runs) | B / A |
|---|---|---|---|---|
| Seltsamere Dinge (FULL frozen) | us/cycle | 104609.1, 104625.6, 104914.3 | 103541.0, 108234.6, 103897.1 | 1.005 |
| Seltsamere Dinge (FULL frozen) | medRTF | 0.05928, 0.05929, 0.05945 | 0.05867, 0.06133, 0.05888 | 1.005 |
| Seltsamere Dinge (FULL frozen) | peakRTF | 0.55984, 0.52544, 0.49823 | 0.53844, 0.56018, 0.54275 | 1.037 |
| Der Schmetterling (FULL frozen 09-25) | us/cycle | 159947.4, 159094.8, 157961.9 | 159661.0, 159774.0, 158045.7 | 1.001 |
| Der Schmetterling (FULL frozen 09-25) | medRTF | 0.08664, 0.08618, 0.08556 | 0.08648, 0.08654, 0.08561 | 1.001 |

Reading: within noise (one B outlier at pair 2). The per-voice slot-bag copy and the bigger tree to gate at
note-on do not show at song level.

A model artifact to record: the song benchmark's `work` column (`GraphCensus`) jumps from 67 to 252 on
Seltsamere Dinge and its `ns/s/pass` drops from 18.4 to 4.9, because `GraphCensus` does not model the gate: it
counts every `classic()` stage of every built-in as built. The measured time does not move. Either
`GraphCensus` learns the gate, or the column is read with that in mind from step 6 on.

## Verdict

Within noise on the JVM, on Node.js and on note-on load. No cause to name, nothing to stop for.
