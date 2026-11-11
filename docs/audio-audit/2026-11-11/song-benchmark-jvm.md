# Song CPU Benchmark on the JVM

- Runtime: `OpenJDK 64-Bit Server VM 17.0.17+10-LTS`
- Sample rate: 48000 Hz, block: 128 frames
- `medianRtf` = render/audio time (steady-state avg). `peakBlockRtf` = busiest single block (≈ worst render-cycle CPU).
- Lower is better. On JVM values are small; the browser worklet is ~10-30x slower, so multiply to relate to the 50% figure.

## Full frozen songs

| case | onsets | culled | voices | work | traffic | KiB | medRTF | peakRTF | µs/cycle | ns/smp/voice | ns/smp/pass |
|------|-------:|-------:|-------:|-----:|--------:|----:|-------:|--------:|---------:|-------------:|------------:|
| Der Schmetterling (FULL frozen 11-03) | 3521 | 314 | 11.0 | 346 | 1268 | 60 | 0.11744 | 0.85116 | 204241.0 | 222.4 | 7.1 |
| Seltsamere Dinge (FULL frozen 11-03) | 595 | 471 | 16.0 | 130 | 294 | 18 | 0.04605 | 0.59848 | 83733.6 | 60.0 | 7.4 |

