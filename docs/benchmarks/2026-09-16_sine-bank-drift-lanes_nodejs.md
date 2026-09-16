# Audio Benchmark Results

> **The sine partial bank and DriftLanes rows, today (2026-09-16), for the blog post
> `docs/blog/2026-09-07-loop-shape-beats-pass-count/`.** Three filtered runs of the ignitor
> benchmark (`KLANG_BENCH_FILTER` = `harmonics7`, `supersaw_8v`, `sine+`), back to back on one
> machine, their ignitor tables joined here; the effect sections are omitted. The engine is
> `v0.3.14` plus the songs since (the polynomial sine and the per-block drift are in). Read the
> drift rows against `sine+analog`, the control, as the 2026-09-10 files do.

- **Platform:** JS / Node.js/24
- **Sample rate:** 44100 Hz
- **Block size:** 128 frames
- **Warmup:** 10000 blocks
- **Measurement:** 5000 blocks x 3 iterations (median)

| Name | Voices | RTF | Render µs/block | Audio µs/block |
|------|-------:|----:|----------------:|---------------:|
| sine-harmonics7 | 1 | 0.007044 | 20.4463 | 2902.4943 |
| sine-harmonics7+analog+spread0 | 1 | 0.006907 | 20.0482 | 2902.4943 |
| sine-harmonics7+analog | 1 | 0.006879 | 19.9671 | 2902.4943 |
| sine-harmonics7-tree | 1 | 0.006267 | 18.1902 | 2902.4943 |
| supersaw_8v+analog | 1 | 0.004715 | 13.6845 | 2902.4943 |
| supersaw_8v+analog+spread0 | 1 | 0.004553 | 13.2137 | 2902.4943 |
| supersaw_8v | 1 | 0.004311 | 12.5113 | 2902.4943 |
| sine+vibrato+tremolo | 1 | 0.003932 | 11.4126 | 2902.4943 |
| sine+analog | 1 | 0.003529 | 10.2419 | 2902.4943 |
