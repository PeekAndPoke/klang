# Audio Benchmark Results

> **The DriftLanes run, JVM (2026-09-10, `docs/tasks-archive/2026-09/20260910-drift-lanes-analog-spread.md`).**
> Taken after the round-1 review, with the per-block values hoisted out of the sample loops.
> The six rows to read are the drift ones.
>
> **Against the engine before the change** (`main` at `0facbd1c`, swapped into this same tree and
> measured back to back, so both numbers come from the same machine state), microseconds per block:
> `sine+analog` 4.86 to 4.76, `supersaw_8v+analog` 6.81 to 6.90, `sine-harmonics7+analog` 18.77 to
> 17.44, `superpluck+analog` 10.67 to 10.48. `sine+analog` is the control: the plain sine has no
> drift lanes at all, so what it does between two runs is this machine's noise floor, and every
> drift row moved by less.
>
> **The `+spread0` rows are the other end of the knob**, where the own lanes are skipped and one
> shared walk drives every voice. Cheaper than their spread-1 twins here by 23 percent (supersaw),
> 16 percent (sine bank) and 9 percent (superpluck). The pluck's saving is the small one and it is
> near the noise: a second run of this same build had the pluck pair the other way round by 11
> percent, while the supersaw and bank pairs held. Its delay-line read dominates the lane
> arithmetic, so there is less to save.

- **Platform:** JVM 17.0.17 (Amazon.com Inc.) / Linux / AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics (16 cores)
- **Sample rate:** 44100 Hz
- **Block size:** 128 frames
- **Warmup:** 10000 blocks
- **Measurement:** 5000 blocks x 3 iterations (median)

| Name | Voices | RTF | Render µs/block | Audio µs/block |
|------|-------:|----:|----------------:|---------------:|
| sine-harmonics7+analog | 1 | 0.006136 | 17.8090 | 2902.4943 |
| sine-harmonics7+analog+spread0 | 1 | 0.005169 | 15.0042 | 2902.4943 |
| sine-harmonics7-tree | 1 | 0.004863 | 14.1139 | 2902.4943 |
| sine-harmonics7 | 1 | 0.004589 | 13.3202 | 2902.4943 |
| supersine | 1 | 0.003856 | 11.1932 | 2902.4943 |
| superpluck+analog | 1 | 0.003640 | 10.5649 | 2902.4943 |
| sine-octaves6 | 1 | 0.003626 | 10.5256 | 2902.4943 |
| supersaw+lpf+adsr+reverb | 1 | 0.003335 | 9.6797 | 2902.4943 |
| superpluck+analog+spread0 | 1 | 0.003325 | 9.6503 | 2902.4943 |
| pluck+distort_8x | 1 | 0.003201 | 9.2914 | 2902.4943 |
| superpluck | 1 | 0.003004 | 8.7199 | 2902.4943 |
| sine+vibrato+tremolo | 1 | 0.002867 | 8.3205 | 2902.4943 |
| supersaw_8v+analog | 1 | 0.002377 | 6.9006 | 2902.4943 |
| pluck+crush_4x | 1 | 0.002142 | 6.2166 | 2902.4943 |
| pluck+distort_4x | 1 | 0.002102 | 6.1018 | 2902.4943 |
| pluck+coarse_4x | 1 | 0.002098 | 6.0895 | 2902.4943 |
| supersaw_16v | 1 | 0.002036 | 5.9103 | 2902.4943 |
| supersquare | 1 | 0.001941 | 5.6340 | 2902.4943 |
| supersaw_8v+analog+spread0 | 1 | 0.001841 | 5.3427 | 2902.4943 |
| supertri | 1 | 0.001833 | 5.3211 | 2902.4943 |
| supersaw+lpf+adsr | 1 | 0.001817 | 5.2732 | 2902.4943 |
| supersaw_8v | 1 | 0.001689 | 4.9033 | 2902.4943 |
| superramp | 1 | 0.001686 | 4.8927 | 2902.4943 |
| pluck+distort_2x | 1 | 0.001668 | 4.8415 | 2902.4943 |
| supersaw | 1 | 0.001663 | 4.8262 | 2902.4943 |
| sine+analog | 1 | 0.001639 | 4.7563 | 2902.4943 |
| square+fm | 1 | 0.001627 | 4.7237 | 2902.4943 |
| sine | 1 | 0.001543 | 4.4784 | 2902.4943 |
| pinknoise | 1 | 0.001539 | 4.4676 | 2902.4943 |
| supersaw_4v | 1 | 0.001469 | 4.2647 | 2902.4943 |
| dust | 1 | 0.001380 | 4.0057 | 2902.4943 |
| brownnoise | 1 | 0.001357 | 3.9392 | 2902.4943 |
| zawtooth | 1 | 0.001313 | 3.8108 | 2902.4943 |
| triangle | 1 | 0.001282 | 3.7198 | 2902.4943 |
| pluck+distort | 1 | 0.001280 | 3.7149 | 2902.4943 |
| whitenoise | 1 | 0.001278 | 3.7085 | 2902.4943 |
| impulse | 1 | 0.001277 | 3.7057 | 2902.4943 |
| square | 1 | 0.001257 | 3.6475 | 2902.4943 |
| ramp | 1 | 0.001242 | 3.6047 | 2902.4943 |
| supersaw_1v | 1 | 0.001233 | 3.5786 | 2902.4943 |
| sawtooth | 1 | 0.001209 | 3.5092 | 2902.4943 |
| pulze | 1 | 0.001200 | 3.4832 | 2902.4943 |
| pluck | 1 | 0.001198 | 3.4781 | 2902.4943 |


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
| Body (wood, 8-band, mix0.5) | 0.001576 | 4.5741 | 2902.4943 |
| Reverb (default) | 0.001518 | 4.4070 | 2902.4943 |
| Ignitor 2-tap + SVF x4 chain (guitar tail) | 0.001147 | 3.3285 | 2902.4943 |
| EqCore 6 (2 taps + 4 serial) | 0.001078 | 3.1282 | 2902.4943 |
| Ignitor SVF x4 chain (notch+hp+lp+lp) | 0.001038 | 3.0125 | 2902.4943 |
| Vowel (a, 5-band, mix0.5) | 0.000974 | 2.8262 | 2902.4943 |
| Compressor (default) | 0.000887 | 2.5741 | 2902.4943 |
| SvfLPF (mod, 1k, q=1, analog=3) | 0.000810 | 2.3519 | 2902.4943 |
| SvfLPF (1k, q=1, analog=3) | 0.000721 | 2.0922 | 2902.4943 |
| SvfHPF (1k, q=1, analog=3) | 0.000719 | 2.0863 | 2902.4943 |
| Phaser (rate=0.5, depth=0.5) | 0.000708 | 2.0562 | 2902.4943 |
| EqCore 4-serial (guitar serial tail) | 0.000640 | 1.8590 | 2902.4943 |
| Ignitor.svf LPF (env, 1k, q=1) | 0.000554 | 1.6091 | 2902.4943 |
| Ignitor.svf LPF (no env, 1k, q=1) | 0.000544 | 1.5777 | 2902.4943 |
| Compressor (limiter, 20:1) | 0.000465 | 1.3498 | 2902.4943 |
| Ignitor sine (bare source baseline) | 0.000352 | 1.0211 | 2902.4943 |
| SvfBPF (1k, q=1) | 0.000188 | 0.5459 | 2902.4943 |
| SvfHPF (1k, q=1) | 0.000181 | 0.5257 | 2902.4943 |
| EqCore 1-serial (LP 1k, q=1) | 0.000176 | 0.5120 | 2902.4943 |
| DelayLine (0.5s, fb=0.3) | 0.000175 | 0.5082 | 2902.4943 |
| SvfLPF (1k, q=1) | 0.000175 | 0.5073 | 2902.4943 |
| SvfNotch (1k, q=1) | 0.000174 | 0.5055 | 2902.4943 |
| OnePoleLPF (1k) | 0.000105 | 0.3047 | 2902.4943 |
| Ducking (mono) | 0.000104 | 0.3025 | 2902.4943 |
| OnePoleHPF (1k) | 0.000079 | 0.2283 | 2902.4943 |
| Copy-only baseline (128f) | 0.000007 | 0.0212 | 2902.4943 |

