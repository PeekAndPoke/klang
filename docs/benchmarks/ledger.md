# The ledger

One row per piece per run of `./gradlew runSongBenchmark --args=ledger`, appended, never edited by hand.
A piece is a FROZEN instrument text (`FrozenPieces.kt`), so within one piece and one machine the engine is
the only thing that moves between rows. Columns: `voices` rendering voices per block (median); `work` passes
over the block per block (median, summed over the voices, from `GraphCensus`); `traffic` block-buffer reads
and writes per sample (median); `KiB` state the voices hold (busiest block); `ns/smp/voice` render time per
sample per rendering voice; `ns/smp/pass` render time per sample per pass, the engine's cost per unit of work.
`voices` and the two `ns/smp/*` columns are comparable among harness rows only.
The census counts the voice's IGNITOR graph; the strip's own stages (a pipeline filter, the orbit effects, the
master) are not in it, and a sample voice counts as one pass, while the render time in the numerator of both
`ns/smp/*` columns covers all of it: those two columns compare an engine against itself on ONE piece, never one
piece against another. JVM, 48000 Hz, 128-frame blocks.
The detail file of each run sits next to this one.
Rows whose engine says "transplant" were measured on an engine that predates the ledger, by carrying the frozen
pieces into a worktree at that tag (the recipe is in `docs/plans/blog-optimization-series.md`, section 2.3); they
carry only the columns the old benchmark had (onsets, medRTF, peakRTF) and were pasted from the worktree's detail
files (`*_v0.3.12-transplant.md`), the one kind of row not written by the harness itself.

| date | engine | machine | piece | onsets | culled | voices | work | traffic | KiB | medRTF | peakRTF | ns/smp/voice | ns/smp/pass |
|------|--------|---------|-------|-------:|-------:|-------:|-----:|--------:|----:|-------:|--------:|-------------:|------------:|
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | guitar melody (rig) @ frozen 2026-09-16 | 136 | 0 | 1.0 | 48 | 212 | 6 | 0.01612 | 0.11382 | 335.9 | 7.0 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | guitars rhythm (rig) @ frozen 2026-09-16 | 272 | 0 | 2.0 | 114 | 606 | 21 | 0.03746 | 0.15432 | 390.2 | 6.8 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | marimba @ frozen 2026-09-16 | 128 | 0 | 6.0 | 108 | 240 | 13 | 0.02443 | 0.12088 | 84.8 | 4.7 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | trommel @ frozen 2026-09-16 | 34 | 7 | 5.0 | 120 | 360 | 9 | 0.03355 | 0.17993 | 139.8 | 5.8 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | bass @ frozen 2026-09-16 | 32 | 0 | 1.0 | 7 | 13 | 0 | 0.00450 | 0.03204 | 93.7 | 13.4 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | drums (samples) @ frozen 2026-09-16 | 303 | 257 | 6.0 | 6 | 6 | 0 | 0.01712 | 0.06722 | 59.4 | 59.4 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | guitar melody (rig) @ live | 136 | 0 | 1.0 | 48 | 212 | 6 | 0.01481 | 0.07893 | 308.6 | 6.4 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | guitars rhythm (rig) @ live | 272 | 0 | 2.0 | 114 | 606 | 21 | 0.03445 | 0.12158 | 358.9 | 6.3 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | marimba @ live | 128 | 0 | 6.0 | 108 | 240 | 13 | 0.02764 | 0.11568 | 96.0 | 5.3 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | trommel @ live | 34 | 7 | 5.0 | 120 | 360 | 9 | 0.03373 | 0.09569 | 140.5 | 5.9 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | bass @ live | 32 | 0 | 1.0 | 7 | 13 | 0 | 0.00416 | 0.02025 | 86.8 | 12.4 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | drums (samples) @ live | 303 | 257 | 6.0 | 6 | 6 | 0 | 0.01593 | 0.05078 | 55.3 | 55.3 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | guitar melody (rig) @ frozen 2026-09-16 | 136 | 0 | 1.0 | 48 | 212 | 6 | 0.01539 | 0.09898 | 320.7 | 6.7 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | guitars rhythm (rig) @ frozen 2026-09-16 | 272 | 0 | 2.0 | 114 | 606 | 21 | 0.03559 | 0.16792 | 370.8 | 6.5 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | marimba @ frozen 2026-09-16 | 128 | 0 | 6.0 | 108 | 240 | 13 | 0.03452 | 1.20063 | 119.9 | 6.7 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | trommel @ frozen 2026-09-16 | 34 | 7 | 5.0 | 120 | 360 | 9 | 0.03365 | 0.18622 | 140.2 | 5.8 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | bass @ frozen 2026-09-16 | 32 | 0 | 1.0 | 7 | 13 | 0 | 0.00451 | 0.03658 | 93.9 | 13.4 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | drums (samples) @ frozen 2026-09-16 | 303 | 257 | 6.0 | 6 | 6 | 0 | 0.01726 | 0.06422 | 59.9 | 59.9 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | guitar melody (rig) @ live | 136 | 0 | 1.0 | 48 | 212 | 6 | 0.01454 | 0.06976 | 303.0 | 6.3 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | guitars rhythm (rig) @ live | 272 | 0 | 2.0 | 114 | 606 | 21 | 0.03238 | 0.12395 | 337.3 | 5.9 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | marimba @ live | 128 | 0 | 6.0 | 108 | 240 | 13 | 0.02382 | 0.07333 | 82.7 | 4.6 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | trommel @ live | 34 | 7 | 5.0 | 120 | 360 | 9 | 0.03210 | 0.08216 | 133.8 | 5.6 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | bass @ live | 32 | 0 | 1.0 | 7 | 13 | 0 | 0.00420 | 0.02388 | 87.6 | 12.5 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | drums (samples) @ live | 303 | 257 | 6.0 | 6 | 6 | 0 | 0.01588 | 0.04893 | 55.1 | 55.1 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | guitar melody (rig) @ frozen 2026-09-16 | 136 | 0 | 1.0 | 48 | 212 | 6 | 0.01652 | 0.11118 | 344.1 | 7.2 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | guitars rhythm (rig) @ frozen 2026-09-16 | 272 | 0 | 2.0 | 114 | 606 | 21 | 0.03849 | 0.20797 | 401.0 | 7.0 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | marimba @ frozen 2026-09-16 | 128 | 0 | 6.0 | 108 | 240 | 13 | 0.02897 | 0.16524 | 100.6 | 5.6 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | trommel @ frozen 2026-09-16 | 34 | 7 | 5.0 | 120 | 360 | 9 | 0.03589 | 0.17835 | 149.5 | 6.2 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | bass @ frozen 2026-09-16 | 32 | 0 | 1.0 | 7 | 13 | 0 | 0.00588 | 0.06939 | 122.5 | 17.5 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | drums (samples) @ frozen 2026-09-16 | 303 | 257 | 6.0 | 6 | 6 | 0 | 0.01828 | 0.08700 | 63.5 | 63.5 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | guitar melody (rig) @ live | 136 | 0 | 1.0 | 48 | 212 | 6 | 0.01948 | 0.10123 | 405.7 | 8.5 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | guitars rhythm (rig) @ live | 272 | 0 | 2.0 | 114 | 606 | 21 | 0.04660 | 0.65618 | 485.5 | 8.5 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | marimba @ live | 128 | 0 | 6.0 | 108 | 240 | 13 | 0.02370 | 0.07855 | 82.3 | 4.6 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | trommel @ live | 34 | 7 | 5.0 | 120 | 360 | 9 | 0.03253 | 0.08713 | 135.5 | 5.6 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | bass @ live | 32 | 0 | 1.0 | 7 | 13 | 0 | 0.00443 | 0.02501 | 92.2 | 13.2 |
| 2026-09-16 | v0.3.14-9-g8fc5b579-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | drums (samples) @ live | 303 | 257 | 6.0 | 6 | 6 | 0 | 0.01628 | 0.04902 | 56.5 | 56.5 |
| 2026-09-16 | v0.3.12 (transplant, median of 3) | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | guitar melody (rig) @ frozen 2026-09-16 | 136 | n/a | n/a | n/a | n/a | n/a | 0.01961 | 0.09939 | n/a | n/a |
| 2026-09-16 | v0.3.12 (transplant, median of 3) | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | guitars rhythm (rig) @ frozen 2026-09-16 | 272 | n/a | n/a | n/a | n/a | n/a | 0.04352 | 0.18441 | n/a | n/a |
| 2026-09-16 | v0.3.12 (transplant, median of 3) | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | marimba @ frozen 2026-09-16 | 128 | n/a | n/a | n/a | n/a | n/a | 0.03388 | 0.15977 | n/a | n/a |
| 2026-09-16 | v0.3.12 (transplant, median of 3) | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | trommel @ frozen 2026-09-16 | 34 | n/a | n/a | n/a | n/a | n/a | 0.06037 | 0.20375 | n/a | n/a |
| 2026-09-16 | v0.3.12 (transplant, median of 3) | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | bass @ frozen 2026-09-16 | 32 | n/a | n/a | n/a | n/a | n/a | 0.01009 | 0.05980 | n/a | n/a |
| 2026-09-16 | v0.3.12 (transplant, median of 3) | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | drums (samples) @ frozen 2026-09-16 | 303 | n/a | n/a | n/a | n/a | n/a | 0.03968 | 0.12884 | n/a | n/a |
