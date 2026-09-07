# Audio Benchmark Results

- **Platform:** JVM 17.0.17 (Amazon.com Inc.) / Linux / AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics (16 cores)
- **Sample rate:** 44100 Hz
- **Block size:** 128 frames
- **Warmup:** 10000 blocks
- **Measurement:** 5000 blocks x 3 iterations (median)

| Name | Voices | RTF | Render µs/block | Audio µs/block |
|------|-------:|----:|----------------:|---------------:|
| sine-harmonics7-tree | 1 | 0.008152 | 23.6598 | 2902.4943 |
| sine-harmonics7 | 1 | 0.007647 | 22.1954 | 2902.4943 |
| supersine | 1 | 0.006718 | 19.4988 | 2902.4943 |
| sine-octaves6 | 1 | 0.005929 | 17.2086 | 2902.4943 |
| pluck+crush_4x | 1 | 0.005614 | 16.2945 | 2902.4943 |
| pluck+distort_8x | 1 | 0.005307 | 15.4032 | 2902.4943 |
| superpluck | 1 | 0.005165 | 14.9909 | 2902.4943 |
| sine+vibrato+tremolo | 1 | 0.005136 | 14.9072 | 2902.4943 |
| pluck+coarse_4x | 1 | 0.003997 | 11.6026 | 2902.4943 |
| supersaw+lpf+adsr+reverb | 1 | 0.003985 | 11.5671 | 2902.4943 |
| pluck+distort_4x | 1 | 0.003474 | 10.0845 | 2902.4943 |
| supersaw_16v | 1 | 0.003385 | 9.8259 | 2902.4943 |
| sine | 1 | 0.002740 | 7.9543 | 2902.4943 |
| supersaw+lpf+adsr | 1 | 0.002627 | 7.6250 | 2902.4943 |
| square+fm | 1 | 0.002539 | 7.3706 | 2902.4943 |
| superramp | 1 | 0.002439 | 7.0794 | 2902.4943 |
| pluck+distort_2x | 1 | 0.002391 | 6.9385 | 2902.4943 |
| supertri | 1 | 0.002383 | 6.9168 | 2902.4943 |
| supersaw_8v | 1 | 0.002376 | 6.8962 | 2902.4943 |
| supersquare | 1 | 0.002339 | 6.7901 | 2902.4943 |
| supersaw | 1 | 0.002212 | 6.4199 | 2902.4943 |
| sawtooth | 1 | 0.001942 | 5.6360 | 2902.4943 |
| square | 1 | 0.001926 | 5.5910 | 2902.4943 |
| supersaw_4v | 1 | 0.001881 | 5.4587 | 2902.4943 |
| pinknoise | 1 | 0.001737 | 5.0426 | 2902.4943 |
| pluck+distort | 1 | 0.001649 | 4.7852 | 2902.4943 |
| brownnoise | 1 | 0.001629 | 4.7278 | 2902.4943 |
| dust | 1 | 0.001616 | 4.6912 | 2902.4943 |
| pluck | 1 | 0.001536 | 4.4593 | 2902.4943 |
| zawtooth | 1 | 0.001531 | 4.4442 | 2902.4943 |
| supersaw_1v | 1 | 0.001506 | 4.3719 | 2902.4943 |
| whitenoise | 1 | 0.001490 | 4.3256 | 2902.4943 |
| impulse | 1 | 0.001482 | 4.3019 | 2902.4943 |
| ramp | 1 | 0.001473 | 4.2754 | 2902.4943 |
| triangle | 1 | 0.001465 | 4.2517 | 2902.4943 |
| pulze | 1 | 0.001428 | 4.1443 | 2902.4943 |


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
| Body (wood, 8-band, mix0.5) | 0.001540 | 4.4709 | 2902.4943 |
| Reverb (default) | 0.001418 | 4.1160 | 2902.4943 |
| Ignitor 2-tap + SVF x4 chain (guitar tail) | 0.001125 | 3.2654 | 2902.4943 |
| EqCore 6 (2 taps + 4 serial) | 0.001063 | 3.0862 | 2902.4943 |
| Ignitor SVF x4 chain (notch+hp+lp+lp) | 0.001019 | 2.9583 | 2902.4943 |
| Vowel (a, 5-band, mix0.5) | 0.000966 | 2.8038 | 2902.4943 |
| Compressor (default) | 0.000845 | 2.4533 | 2902.4943 |
| SvfLPF (mod, 1k, q=1, analog=3) | 0.000820 | 2.3805 | 2902.4943 |
| SvfLPF (1k, q=1, analog=3) | 0.000718 | 2.0829 | 2902.4943 |
| SvfHPF (1k, q=1, analog=3) | 0.000706 | 2.0505 | 2902.4943 |
| EqCore 4-serial (guitar serial tail) | 0.000635 | 1.8426 | 2902.4943 |
| Phaser (rate=0.5, depth=0.5) | 0.000611 | 1.7725 | 2902.4943 |
| Ignitor.svf LPF (no env, 1k, q=1) | 0.000574 | 1.6650 | 2902.4943 |
| Ignitor.svf LPF (env, 1k, q=1) | 0.000561 | 1.6297 | 2902.4943 |
| Compressor (limiter, 20:1) | 0.000444 | 1.2874 | 2902.4943 |
| Ignitor sine (bare source baseline) | 0.000338 | 0.9817 | 2902.4943 |
| SvfBPF (1k, q=1) | 0.000181 | 0.5263 | 2902.4943 |
| DelayLine (0.5s, fb=0.3) | 0.000178 | 0.5172 | 2902.4943 |
| SvfHPF (1k, q=1) | 0.000178 | 0.5170 | 2902.4943 |
| SvfLPF (1k, q=1) | 0.000177 | 0.5150 | 2902.4943 |
| EqCore 1-serial (LP 1k, q=1) | 0.000177 | 0.5124 | 2902.4943 |
| SvfNotch (1k, q=1) | 0.000170 | 0.4938 | 2902.4943 |
| OnePoleLPF (1k) | 0.000112 | 0.3239 | 2902.4943 |
| Ducking (mono) | 0.000105 | 0.3043 | 2902.4943 |
| OnePoleHPF (1k) | 0.000086 | 0.2499 | 2902.4943 |
| Copy-only baseline (128f) | 0.000005 | 0.0147 | 2902.4943 |

