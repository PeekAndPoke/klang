# Audio Benchmark Results

> **The sine partial bank and DriftLanes rows, today (2026-09-16), for the blog post
> `docs/blog/2026-09-07-loop-shape-beats-pass-count/`.** Three filtered runs of the ignitor
> benchmark (`KLANG_BENCH_FILTER` = `harmonics7`, `supersaw_8v`, `sine+`), back to back on one
> machine, their ignitor tables joined here; the effect sections are omitted. The engine is
> `v0.3.14` plus the songs since (the polynomial sine and the per-block drift are in). Read the
> drift rows against `sine+analog`, the control, as the 2026-09-10 files do.

- **Platform:** JVM 17.0.17 (Amazon.com Inc.) / Linux / AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics (16 cores)
- **Sample rate:** 44100 Hz
- **Block size:** 128 frames
- **Warmup:** 10000 blocks
- **Measurement:** 5000 blocks x 3 iterations (median)

| Name | Voices | RTF | Render µs/block | Audio µs/block |
|------|-------:|----:|----------------:|---------------:|
| sine-harmonics7+analog | 1 | 0.003122 | 9.0602 | 2902.4943 |
| sine-harmonics7+analog+spread0 | 1 | 0.002937 | 8.5251 | 2902.4943 |
| sine-harmonics7-tree | 1 | 0.002925 | 8.4908 | 2902.4943 |
| sine-harmonics7 | 1 | 0.002636 | 7.6509 | 2902.4943 |
| supersaw_8v+analog | 1 | 0.002090 | 6.0673 | 2902.4943 |
| supersaw_8v+analog+spread0 | 1 | 0.001916 | 5.5612 | 2902.4943 |
| supersaw_8v | 1 | 0.001860 | 5.3974 | 2902.4943 |
| sine+vibrato+tremolo | 1 | 0.002617 | 7.5971 | 2902.4943 |
| sine+analog | 1 | 0.001867 | 5.4184 | 2902.4943 |
