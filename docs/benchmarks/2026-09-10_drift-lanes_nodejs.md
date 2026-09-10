# Audio Benchmark Results

> **The DriftLanes run, Node (2026-09-10, `docs/tasks-archive/2026-09/20260910-drift-lanes-analog-spread.md`).**
> Kotlin/JS is where this change had to be measured, and it is where it first went wrong.
>
> **Read every drift row against `sine+analog` from the same run.** The plain sine has no drift
> lanes, so it carries only the machine and V8 state; two runs of the identical pre-change engine
> gave `supersaw_8v+analog` at 12.64 and 16.16 microseconds per block, so a single Node number here
> means little and a ratio means a lot.
>
> Row divided by `sine+analog`, same run:
>
> | row | pre-change engine (two runs) | first cut | with the values hoisted (two runs) |
> |-----|------------------------------|-----------|------------------------------------|
> | `supersaw_8v+analog` | 1.40, 1.56 | 1.63 | 1.39, 1.47 |
> | `sine-harmonics7+analog` | 3.66, 4.05 | 3.75 | 3.05, 3.16 |
> | `superpluck+analog` | 2.26, 2.15 | 2.54 | 2.24, 2.28 |
>
> The first cut read the per-block weights, flags and lane off the container INSIDE the sample
> loop, and on JS that put the stack and the pluck above both pre-change runs (about 16 and 12
> percent). Hoisting them into locals once per voice, and blending through one inline function that
> takes only locals, put both back inside the pre-change spread. The sine bank comes out ahead of
> where it started.
>
> The `+spread0` rows are cheaper here too, by 3 percent (supersaw), 8 percent (sine bank) and 11
> percent (superpluck), less of a saving than on the JVM.

- **Platform:** JS / Node.js/24
- **Sample rate:** 44100 Hz
- **Block size:** 128 frames
- **Warmup:** 10000 blocks
- **Measurement:** 5000 blocks x 3 iterations (median)

| Name | Voices | RTF | Render µs/block | Audio µs/block |
|------|-------:|----:|----------------:|---------------:|
| sine-harmonics7+analog | 1 | 0.010959 | 31.8071 | 2902.4943 |
| sine-harmonics7+analog+spread0 | 1 | 0.010047 | 29.1607 | 2902.4943 |
| sine-harmonics7 | 1 | 0.010013 | 29.0637 | 2902.4943 |
| sine-harmonics7-tree | 1 | 0.008706 | 25.2686 | 2902.4943 |
| sine-octaves6 | 1 | 0.008403 | 24.3890 | 2902.4943 |
| pluck+distort_8x | 1 | 0.007982 | 23.1689 | 2902.4943 |
| superpluck+analog | 1 | 0.007925 | 23.0027 | 2902.4943 |
| supersaw+lpf+adsr+reverb | 1 | 0.007911 | 22.9620 | 2902.4943 |
| superpluck+analog+spread0 | 1 | 0.007046 | 20.4505 | 2902.4943 |
| supersine | 1 | 0.006475 | 18.7943 | 2902.4943 |
| superpluck | 1 | 0.006406 | 18.5932 | 2902.4943 |
| supersaw_8v+analog | 1 | 0.005111 | 14.8356 | 2902.4943 |
| pluck+distort_4x | 1 | 0.005107 | 14.8218 | 2902.4943 |
| pluck+coarse_4x | 1 | 0.005001 | 14.5160 | 2902.4943 |
| supersaw_8v+analog+spread0 | 1 | 0.004956 | 14.3834 | 2902.4943 |
| sine+vibrato+tremolo | 1 | 0.004939 | 14.3353 | 2902.4943 |
| supersaw_16v | 1 | 0.004927 | 14.2992 | 2902.4943 |
| pluck+crush_4x | 1 | 0.004688 | 13.6057 | 2902.4943 |
| supertri | 1 | 0.004300 | 12.4821 | 2902.4943 |
| supersquare | 1 | 0.004087 | 11.8615 | 2902.4943 |
| supersaw+lpf+adsr | 1 | 0.004055 | 11.7710 | 2902.4943 |
| pluck+distort_2x | 1 | 0.003973 | 11.5322 | 2902.4943 |
| square+fm | 1 | 0.003967 | 11.5131 | 2902.4943 |
| supersaw | 1 | 0.003836 | 11.1332 | 2902.4943 |
| supersaw_8v | 1 | 0.003820 | 11.0872 | 2902.4943 |
| superramp | 1 | 0.003801 | 11.0312 | 2902.4943 |
| supersaw_4v | 1 | 0.003545 | 10.2902 | 2902.4943 |
| sine+analog | 1 | 0.003470 | 10.0723 | 2902.4943 |
| whitenoise | 1 | 0.003337 | 9.6870 | 2902.4943 |
| pinknoise | 1 | 0.003336 | 9.6815 | 2902.4943 |
| sine | 1 | 0.003324 | 9.6485 | 2902.4943 |
| brownnoise | 1 | 0.003206 | 9.3046 | 2902.4943 |
| triangle | 1 | 0.003099 | 8.9948 | 2902.4943 |
| dust | 1 | 0.003097 | 8.9876 | 2902.4943 |
| square | 1 | 0.003038 | 8.8174 | 2902.4943 |
| pulze | 1 | 0.002989 | 8.6751 | 2902.4943 |
| pluck | 1 | 0.002951 | 8.5641 | 2902.4943 |
| impulse | 1 | 0.002948 | 8.5560 | 2902.4943 |
| pluck+distort | 1 | 0.002919 | 8.4737 | 2902.4943 |
| sawtooth | 1 | 0.002914 | 8.4566 | 2902.4943 |
| supersaw_1v | 1 | 0.002879 | 8.3563 | 2902.4943 |
| zawtooth | 1 | 0.002827 | 8.2064 | 2902.4943 |
| ramp | 1 | 0.002803 | 8.1358 | 2902.4943 |


# Effect Benchmark Results

- **Platform:** JS / Node.js/24
- **Sample rate:** 44100 Hz
- **Block size:** 128 frames
- **Warmup:** 10000 blocks
- **Measurement:** 5000 blocks x 3 iterations (median)

Each case runs a single effect/filter `process()` repeatedly on a sine-wave-filled buffer.
RTF = render time / audio time. Lower is better (RTF < 1.0 = faster than real-time).

| Name | RTF | Render µs/block | Audio µs/block |
|------|----:|----------------:|---------------:|
| Reverb (default) | 0.003566 | 10.3489 | 2902.4943 |
| Body (wood, 8-band, mix0.5) | 0.002738 | 7.9476 | 2902.4943 |
| Vowel (a, 5-band, mix0.5) | 0.001952 | 5.6670 | 2902.4943 |
| Ignitor 2-tap + SVF x4 chain (guitar tail) | 0.001911 | 5.5468 | 2902.4943 |
| Ignitor SVF x4 chain (notch+hp+lp+lp) | 0.001514 | 4.3951 | 2902.4943 |
| Compressor (default) | 0.001377 | 3.9978 | 2902.4943 |
| EqCore 6 (2 taps + 4 serial) | 0.001157 | 3.3584 | 2902.4943 |
| SvfLPF (mod, 1k, q=1, analog=3) | 0.000888 | 2.5782 | 2902.4943 |
| SvfHPF (1k, q=1, analog=3) | 0.000856 | 2.4848 | 2902.4943 |
| Phaser (rate=0.5, depth=0.5) | 0.000841 | 2.4406 | 2902.4943 |
| SvfLPF (1k, q=1, analog=3) | 0.000822 | 2.3869 | 2902.4943 |
| Ignitor.svf LPF (env, 1k, q=1) | 0.000780 | 2.2644 | 2902.4943 |
| Ignitor.svf LPF (no env, 1k, q=1) | 0.000752 | 2.1835 | 2902.4943 |
| Compressor (limiter, 20:1) | 0.000713 | 2.0687 | 2902.4943 |
| EqCore 4-serial (guitar serial tail) | 0.000693 | 2.0127 | 2902.4943 |
| DelayLine (0.5s, fb=0.3) | 0.000557 | 1.6173 | 2902.4943 |
| Ignitor sine (bare source baseline) | 0.000474 | 1.3771 | 2902.4943 |
| SvfBPF (1k, q=1) | 0.000310 | 0.8991 | 2902.4943 |
| SvfHPF (1k, q=1) | 0.000304 | 0.8810 | 2902.4943 |
| SvfLPF (1k, q=1) | 0.000297 | 0.8633 | 2902.4943 |
| SvfNotch (1k, q=1) | 0.000284 | 0.8230 | 2902.4943 |
| Ducking (mono) | 0.000223 | 0.6475 | 2902.4943 |
| OnePoleLPF (1k) | 0.000212 | 0.6150 | 2902.4943 |
| EqCore 1-serial (LP 1k, q=1) | 0.000172 | 0.4998 | 2902.4943 |
| OnePoleHPF (1k) | 0.000168 | 0.4874 | 2902.4943 |
| Copy-only baseline (128f) | 0.000024 | 0.0708 | 2902.4943 |

