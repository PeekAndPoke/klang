# Audio Benchmark Results

> **The DriftLanes run (2026-09-10, `docs/tasks-archive/2026-09/20260910-drift-lanes-analog-spread.md`).** Taken right
> after the drift lanes moved into one component and `analogSpread` landed on the super family.
> The rows to read are the four drift ones. Against the same harness on the commit before the
> change (`0facbd1c`): `sine+analog` 4.41 to 4.33, `supersaw_8v+analog` 6.35 to 6.22,
> `sine-harmonics7+analog` 17.18 to 15.66, so the spread-1 default did not get more expensive.
> The two new `+spread0` rows are the other end of the knob, where the own lanes are skipped and
> one shared walk drives every voice: cheaper than their spread-1 twins, as designed.

- **Platform:** JVM 17.0.17 (Amazon.com Inc.) / Linux / AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics (16 cores)
- **Sample rate:** 44100 Hz
- **Block size:** 128 frames
- **Warmup:** 10000 blocks
- **Measurement:** 5000 blocks x 3 iterations (median)

| Name | Voices | RTF | Render µs/block | Audio µs/block |
|------|-------:|----:|----------------:|---------------:|
| sine-harmonics7+analog | 1 | 0.005394 | 15.6556 | 2902.4943 |
| sine-harmonics7+analog+spread0 | 1 | 0.004379 | 12.7103 | 2902.4943 |
| sine-harmonics7-tree | 1 | 0.004189 | 12.1577 | 2902.4943 |
| sine-harmonics7 | 1 | 0.004128 | 11.9802 | 2902.4943 |
| supersine | 1 | 0.003513 | 10.1972 | 2902.4943 |
| sine-octaves6 | 1 | 0.003332 | 9.6714 | 2902.4943 |
| supersaw+lpf+adsr+reverb | 1 | 0.002947 | 8.5541 | 2902.4943 |
| superpluck | 1 | 0.002789 | 8.0947 | 2902.4943 |
| pluck+distort_8x | 1 | 0.002697 | 7.8278 | 2902.4943 |
| sine+vibrato+tremolo | 1 | 0.002619 | 7.6028 | 2902.4943 |
| supersaw_8v+analog | 1 | 0.002143 | 6.2210 | 2902.4943 |
| supersaw_16v | 1 | 0.001903 | 5.5241 | 2902.4943 |
| pluck+distort_4x | 1 | 0.001856 | 5.3862 | 2902.4943 |
| pluck+crush_4x | 1 | 0.001849 | 5.3664 | 2902.4943 |
| pluck+coarse_4x | 1 | 0.001793 | 5.2042 | 2902.4943 |
| supersaw_8v+analog+spread0 | 1 | 0.001640 | 4.7609 | 2902.4943 |
| supersaw+lpf+adsr | 1 | 0.001601 | 4.6468 | 2902.4943 |
| supertri | 1 | 0.001556 | 4.5159 | 2902.4943 |
| supersquare | 1 | 0.001536 | 4.4583 | 2902.4943 |
| square+fm | 1 | 0.001518 | 4.4067 | 2902.4943 |
| sine+analog | 1 | 0.001491 | 4.3265 | 2902.4943 |
| supersaw_8v | 1 | 0.001466 | 4.2551 | 2902.4943 |
| superramp | 1 | 0.001462 | 4.2426 | 2902.4943 |
| supersaw | 1 | 0.001453 | 4.2167 | 2902.4943 |
| pluck+distort_2x | 1 | 0.001445 | 4.1939 | 2902.4943 |
| sine | 1 | 0.001412 | 4.0989 | 2902.4943 |
| pinknoise | 1 | 0.001354 | 3.9285 | 2902.4943 |
| brownnoise | 1 | 0.001293 | 3.7538 | 2902.4943 |
| supersaw_4v | 1 | 0.001237 | 3.5909 | 2902.4943 |
| dust | 1 | 0.001226 | 3.5597 | 2902.4943 |
| impulse | 1 | 0.001121 | 3.2551 | 2902.4943 |
| whitenoise | 1 | 0.001119 | 3.2486 | 2902.4943 |
| pluck+distort | 1 | 0.001114 | 3.2346 | 2902.4943 |
| supersaw_1v | 1 | 0.001105 | 3.2061 | 2902.4943 |
| sawtooth | 1 | 0.001098 | 3.1869 | 2902.4943 |
| zawtooth | 1 | 0.001090 | 3.1632 | 2902.4943 |
| pluck | 1 | 0.001089 | 3.1614 | 2902.4943 |
| pulze | 1 | 0.001082 | 3.1397 | 2902.4943 |
| square | 1 | 0.001081 | 3.1381 | 2902.4943 |
| triangle | 1 | 0.001078 | 3.1280 | 2902.4943 |
| ramp | 1 | 0.001070 | 3.1059 | 2902.4943 |


# Effect Benchmark Results

- **Platform:** JVM 17.0.17 (Amazon.com Inc.) / Linux / AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics (16 cores)
- **Sample rate:** 44100 Hz
- **Block size:** 128 frames
- **Warmup:** 10000 blocks
- **Measurement:** 5000 blocks x 3 iterations (median)

Each case runs a single effect/filter `process()` repeatedly on a sine-wave-filled buffer.
RTF = render time / audio time. Lower is better (RTF < 1.0 = faster than real-time).

| Name | RTF | Render µs/block | Audio µs/block |
|------|----:|----------------:|---------------:|
| Body (wood, 8-band, mix0.5) | 0.001472 | 4.2722 | 2902.4943 |
| Reverb (default) | 0.001230 | 3.5688 | 2902.4943 |
| Ignitor 2-tap + SVF x4 chain (guitar tail) | 0.001071 | 3.1080 | 2902.4943 |
| EqCore 6 (2 taps + 4 serial) | 0.000976 | 2.8329 | 2902.4943 |
| Ignitor SVF x4 chain (notch+hp+lp+lp) | 0.000968 | 2.8107 | 2902.4943 |
| Vowel (a, 5-band, mix0.5) | 0.000917 | 2.6626 | 2902.4943 |
| Compressor (default) | 0.000859 | 2.4933 | 2902.4943 |
| SvfLPF (mod, 1k, q=1, analog=3) | 0.000811 | 2.3548 | 2902.4943 |
| SvfHPF (1k, q=1, analog=3) | 0.000701 | 2.0358 | 2902.4943 |
| SvfLPF (1k, q=1, analog=3) | 0.000692 | 2.0092 | 2902.4943 |
| EqCore 4-serial (guitar serial tail) | 0.000613 | 1.7805 | 2902.4943 |
| Phaser (rate=0.5, depth=0.5) | 0.000568 | 1.6489 | 2902.4943 |
| Ignitor.svf LPF (env, 1k, q=1) | 0.000502 | 1.4566 | 2902.4943 |
| Ignitor.svf LPF (no env, 1k, q=1) | 0.000483 | 1.4009 | 2902.4943 |
| Compressor (limiter, 20:1) | 0.000424 | 1.2301 | 2902.4943 |
| Ignitor sine (bare source baseline) | 0.000314 | 0.9100 | 2902.4943 |
| DelayLine (0.5s, fb=0.3) | 0.000254 | 0.7358 | 2902.4943 |
| SvfHPF (1k, q=1) | 0.000167 | 0.4852 | 2902.4943 |
| SvfBPF (1k, q=1) | 0.000166 | 0.4811 | 2902.4943 |
| SvfLPF (1k, q=1) | 0.000165 | 0.4792 | 2902.4943 |
| EqCore 1-serial (LP 1k, q=1) | 0.000160 | 0.4638 | 2902.4943 |
| SvfNotch (1k, q=1) | 0.000158 | 0.4592 | 2902.4943 |
| Ducking (mono) | 0.000099 | 0.2859 | 2902.4943 |
| OnePoleLPF (1k) | 0.000093 | 0.2706 | 2902.4943 |
| OnePoleHPF (1k) | 0.000066 | 0.1922 | 2902.4943 |
| Copy-only baseline (128f) | 0.000007 | 0.0189 | 2902.4943 |

