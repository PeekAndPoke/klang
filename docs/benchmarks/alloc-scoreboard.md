# The allocation scoreboard

Two rows per run of the allocation check (`KLANG_BENCH_MODE=alloc ./gradlew :audio_benchmark:jvmRun`,
also the `alloc` step of `console/full-check.sh`): one for V8, one for the JVM. Appended by the driver,
never edited by hand. A cell is the median bytes per block and the median µs per block; a `!` marks a
breach of `audio_benchmark/alloc-budgets.txt`. The V8 µs run with a 1 MB semi-space, so they include
more GC than a worklet: comparable between rows, not with the real-time factor benchmarks.
The cases are `AllocationCases` in `audio_benchmark`, at middle C (261.63 Hz).

## Since 2026-10-10

| date | commit | machine | platform | runtime | saw | sawC | C-offrel | C-env3 | super7 | super7a | sgpad-vib | whitecolor | C-dist | tube4x | C-full | E-sawC-sus | E-noisesaw-sus | E-C-rel05-n16 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 2026-10-10 | d7a455e4-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | v8 | node v22.12.0 | 30 B 0.73 | 42 B 1.39 | 31 B 2.38 | 61 B 7.66 | 17 B 3.17 | 21 B 4.09 | 67 B 4.82 | 17 B 1.15 | 51 B 1.90 | 72 B 5.64 | 253 B 7.67 | 83 B 5.78 | 77 B 7.48 | 1293 B 34.70 |
| 2026-10-10 | d7a455e4-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | jvm | JVM 17.0.17 | 0 B 0.25 | 0 B 0.77 | 0 B 1.30 | 0 B 4.69 | 0 B 1.68 | 0 B 2.13 | 0 B 2.51 | 0 B 0.41 | 0 B 1.11 | 0 B 3.40 | 0 B 5.77 | 0 B 3.07 | 0 B 4.07 | 409 B 18.56 |
| 2026-10-10 | d7a455e4-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | v8 | node v22.12.0 | 37 B 0.80 | 32 B 1.46 | 30 B 2.58 | 84 B 8.50 | 21 B 3.47 | 15 B 4.60 | 67 B 5.29 | 5 B 1.30 | 47 B 2.02 | 72 B 6.24 | 200 B 8.36 | 78 B 6.48 | 88 B 8.18 | 1294 B 38.21 |
| 2026-10-10 | d7a455e4-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | jvm | JVM 17.0.17 | 0 B 0.28 | 0 B 0.86 | 0 B 1.50 | 0 B 5.46 | 0 B 1.97 | 0 B 2.43 | 0 B 2.86 | 0 B 0.95 | 0 B 1.22 | 0 B 3.77 | 0 B 6.41 | 0 B 3.52 | 0 B 4.66 | 409 B 21.19 |
| 2026-10-10 | d7a455e4-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | v8 | node v22.12.0 | 30 B 0.84 | 26 B 1.44 | 27 B 2.52 | 83 B 8.40 | 21 B 3.50 | 26 B 4.64 | 62 B 5.23 | 20 B 1.33 | 47 B 2.03 | 73 B 6.16 | 212 B 8.29 | 77 B 6.36 | 77 B 8.02 | 1287 B 38.01 |
| 2026-10-10 | d7a455e4-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | jvm | JVM 17.0.17 | 0 B 0.27 | 0 B 0.84 | 0 B 1.46 | 0 B 5.26 | 0 B 1.87 | 0 B 2.35 | 0 B 2.79 | 0 B 0.96 | 0 B 1.39 | 0 B 3.69 | 0 B 6.30 | 0 B 3.45 | 0 B 4.71 | 409 B 20.65 |
| 2026-10-10 | d7a455e4-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | v8 | node v22.12.0 | 31 B 0.85 | 31 B 1.47 | 31 B 2.56 | 83 B 8.47 | 16 B 3.50 | 15 B 4.48 | 62 B 5.27 | 9 B 1.25 | 46 B 2.08 | 72 B 6.27 | 233 B 8.39 | 83 B 6.24 | 72 B 8.56 | 1298 B 37.45 |
| 2026-10-10 | d7a455e4-dirty | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | jvm | JVM 17.0.17 | 0 B 0.28 | 0 B 0.90 | 0 B 1.48 | 0 B 5.23 | 0 B 1.89 | 0 B 2.45 | 0 B 2.82 | 0 B 0.97 | 0 B 1.23 | 0 B 3.84 | 0 B 6.16 | 0 B 3.42 | 0 B 4.57 | 409 B 21.35 |
| 2026-10-10 | 7ddb8806 | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | v8 | node v22.12.0 | 26 B 0.71 | 11 B 1.40 | 20 B 2.57 | 62 B 8.48 | 16 B 3.58 | 16 B 4.61 | 31 B 5.24 | 10 B 1.29 | 31 B 1.99 | 52 B 6.28 | 191 B 8.41 | 67 B 6.45 | 72 B 8.07 | 1104 B 37.32 |
| 2026-10-10 | 7ddb8806 | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | jvm | JVM 17.0.17 | 0 B 0.30 | 0 B 0.97 | 0 B 1.67 | 0 B 5.95 | 0 B 2.18 | 0 B 2.80 | 0 B 3.31 | 0 B 1.09 | 0 B 1.41 | 0 B 4.39 | 0 B 7.29 | 0 B 4.05 | 0 B 5.27 | 409 B 23.49 |
| 2026-10-11 | 201c3273 | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | v8 | node v22.12.0 | 15 B 0.72 | 15 B 1.49 | 15 B 2.85 | 61 B 8.50 | 22 B 3.60 | 16 B 4.70 | 36 B 5.42 | 5 B 1.28 | 30 B 1.97 | 51 B 6.19 | 37 B 8.41 | 67 B 6.26 | 67 B 8.22 | 1114 B 37.64 |
| 2026-10-11 | 201c3273 | AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics | jvm | JVM 17.0.17 | 0 B 0.32 | 0 B 0.99 | 0 B 1.67 | 0 B 5.97 | 0 B 2.20 | 0 B 2.80 | 0 B 3.25 | 0 B 1.09 | 0 B 1.41 | 0 B 4.45 | 0 B 7.27 | 0 B 3.98 | 0 B 5.33 | 409 B 23.95 |
