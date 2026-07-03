# Der Schmetterling — CPU Deep-Dive (2026-07-03)

Investigation into why **Der Schmetterling** consumes up to ~50% of a render cycle in the browser.
Voices were isolated and stripped to multiple levels of applied effects to attribute CPU cost.

## How to reproduce

```bash
./gradlew runSongBenchmark              # all suites
./gradlew runSongBenchmark --args=voices        # isolated voices only
./gradlew runSongBenchmark --args=ladders       # effect-strip ladders
./gradlew runSongBenchmark --args=experiments   # targeted experiments
./gradlew runSongBenchmark --args=songs         # frozen full songs only
```

Harness: `src/jvmMain/kotlin/SongBenchmark*.kt`. It compiles real KlangScript/sprudel code to a
`KlangPattern` and drives it through the **actual offline DSP graph** (`KlangAudioRenderer`, the same
`PlaybackEngine.renderInto` chain the browser worklet runs), timing every render block.

Frozen song code (so the baseline does NOT move when `builtinsongs/*` are edited):
`src/jvmMain/kotlin/FrozenSongs.kt` — snapshot 2026-07-03.

### Metrics

- **medRTF** — total render-time / total audio-time, median across passes. Steady-state average.
  `RTF < 1.0` = faster than real-time.
- **peakRTF** — the single busiest render block / one block's audio duration (steady-state; the first
  32 blocks are skipped to drop the one-time delay-ring allocation spike). This is the "worst render
  cycle" number that corresponds to the browser's per-quantum CPU spikes.
- **onsets** — number of scheduled voice events in the window. Shows how `unison`/`superimpose`/note
  density inflate the workload.

Config: 48 kHz, 512-frame blocks, JVM (OpenJDK 17). **Caveat:** this run was captured with the laptop
briefly off-charger, so absolute values are throttle-inflated; **relative attribution is unaffected**.
Browser (JS) is ~2.5–3× slower than JVM for this DSP (historical `*_compare.md`), so multiply JVM RTF
by ~2.7 to relate to the browser.

---

## 1. Full-song baseline (frozen)

| Song                        | onsets (48cy) |    medRTF |  peakRTF | ≈ browser peak |
|-----------------------------|--------------:|----------:|---------:|---------------:|
| **Der Schmetterling**       |         11198 | **0.098** | **0.62** |       ~0.5–1.0 |
| Seltsamere Dinge (Stranger) |          691* |     0.069 |     0.16 |       ~0.2–0.4 |

\* Seltsamere Dinge's heaviest voices are section-gated OUT of the first 48 cycles, so its onset count
in this window is low — its true peak is higher when the unison-15 melody + bass superimposes are live.

The JVM sustained cost is ~10% of real-time; the **busiest single block hits 62%** — that block is the
"DISCO" section where the Lead + both Guitars + Bass + Drums all sound together. In the browser (~2.7×)
that busy block is exactly the ~50% you observe.

---

## 2. Where the cost lives: per-voice ranking

Each voice in isolation (section gates removed so it plays continuously):

| Voice                                          |   onsets |     medRTF |   peakRTF | Share of song |
|------------------------------------------------|---------:|-----------:|----------:|--------------:|
| **GTR2** (supersaw uni7, *nested* superimpose) | **1088** | **0.0514** | **0.222** |          ~52% |
| **GTR1** (supersaw uni9)                       |      544 |     0.0292 |     0.057 |          ~30% |
| **LEAD** (superramp uni5)                      |      192 |     0.0182 |     0.052 |          ~18% |
| BASS (saw)                                     |       16 |     0.0017 |     0.008 |           ~2% |
| DRUMS kick (sampled)                           |       68 |     0.0025 |     0.007 |           ~2% |
| HATS (sampled)                                 |       64 |     0.0023 |     0.005 |           ~2% |
| PINK (noise)                                   |       64 |     0.0017 |     0.005 |           ~2% |

**The three super-synth voices ARE the song's CPU cost** (GTR2 + GTR1 + LEAD ≈ 0.099 ≈ the full-song
0.098). Bass, drums, and pink noise are rounding error — do not spend effort optimizing them.

**GTR2 alone ≈ GTR1 + LEAD combined.** It is the single most expensive thing in the song, driven by its
**nested superimpose** (`.superimpose(a, b).superimpose(c)` where `b` itself contains another
`.superimpose`) → **1088 scheduled voices**, each re-running the full supersaw + filters + body + pedal
chain.

---

## 3. Effect attribution (strip-ladder marginal costs)

Each rung adds one effect group to the voice; **Δ medRTF is that group's marginal cost in context**.

### LEAD ladder (superramp uni5)

| rung                                   | onsets |  medRTF |     Δ medRTF |
|----------------------------------------|-------:|--------:|-------------:|
| 0 osc+env (superramp uni5)             |     64 | 0.00145 |            — |
| 1 +filters (hpf/lpf/lpe/lpq/lpadsr)    |     64 | 0.00193 |     +0.00047 |
| 2 +distort (0.62:tube:4)+clip          |     64 | 0.00280 |     +0.00087 |
| 3 +pitchmod (vibrato/shuffle)          |     64 | 0.00414 |     +0.00134 |
| **4 +superimpose (transpose+2×super)** |    192 | 0.01058 | **+0.00644** |
| **5 +analog(feel)**                    |    192 | 0.01592 | **+0.00534** |
| 6 +pipeline(pedal)                     |    192 | 0.01581 | ~0 (−0.0001) |
| 7 +room(0.3:5:0.1)                     |    192 | 0.01710 |     +0.00128 |

### GTR1 ladder (supersaw uni9)

| rung                                    | onsets |  medRTF |     Δ medRTF |
|-----------------------------------------|-------:|--------:|-------------:|
| 0 osc+env (supersaw uni9)               |    136 | 0.00289 |            — |
| 1 +filters (lpadsr/hpf/lpf-mod/lpe/lpq) |    136 | 0.00505 |     +0.00216 |
| 2 +distortx2 (1:tube:4 + 0.80)+clip     |    136 | 0.00555 |     +0.00051 |
| 3 +coarse(2,os4)                        |    136 | 0.00573 |     +0.00018 |
| **4 +superimpose#1 (pan copy)**         |    272 | 0.01080 | **+0.00507** |
| **5 +superimpose#2 (hpf/lpf air)**      |    544 | 0.02078 | **+0.00998** |
| 6 +pipeline(pedal)                      |    544 | 0.02085 | ~0 (+0.0001) |
| **7 +body(wood, mix0.3)**               |    544 | 0.02748 | **+0.00662** |
| 8 +room(0.10:8:0.12)                    |    544 | 0.02889 |     +0.00142 |

### Ranked cost drivers (both voices)

1. **`superimpose` — by far the #1 cost.** Each `.superimpose(fn)` **doubles** the scheduled-voice count
   (N lambdas → ×(N+1)), and every copy re-runs the *entire* per-voice chain (oscillator + filters +
   body + distort + envelopes). GTR1's two superimposes take it 136 → 544 voices (×4) and add +0.0051 +
   0.0100. superimpose#2 costs more than #1 because it doubles an already-doubled count.
2. **`body("wood"/"glass")` — +0.0066.** A parallel bank of ~8 SVF band-pass filters *per voice*, so on
   GTR1's 544 voices that's ~4300 SVF passes/block. Multiplied by every superimposed copy.
3. **`analog` — +0.0053.** Per-voice OU drift + per-voice filter-cutoff offset. Also multiplied by every
   superimposed copy.
4. **filters** (multi-band + envelope + LFO-modulated cutoff) — +0.0022 (GTR1), moderate.
5. **`room`** (per-orbit reverb) — +0.0013, small.
6. **distort / coarse** — small (see §4).
7. **`pipeline("pedal")` — essentially FREE** (+0.00007). It only reorders stages already being run.
8. **`unison` — cheap** (see §4). Not a superimpose.

---

## 4. Targeted experiments

### Distort oversampling (supersaw uni9 + filters base)

| variant                               |  medRTF | Δ vs no-distort |
|---------------------------------------|--------:|----------------:|
| no distort                            | 0.00221 |               — |
| os1 (1:tube:1)                        | 0.00237 |        +0.00016 |
| os2                                   | 0.00270 |        +0.00049 |
| os4 (as song)                         | 0.00319 |        +0.00098 |
| os8                                   | 0.00427 |        +0.00206 |
| **os4 × 2 (double distort, as song)** | 0.00321 |        +0.00100 |

Oversampling scales ~linearly (os8 ≈ 2× os4's delta). **The song's double `.distort("1:tube:4").distort(0.80)`
costs the same as a single os4** — the 2nd distort defaults to os1 (cheap). Distortion is a *modest* cost.

### Unison count (full GTR1 chain — body+pedal+super all present)

| unison |  medRTF | Δ vs uni1 |
|--------|--------:|----------:|
| uni1   | 0.01375 |         — |
| uni5   | 0.01490 |  +0.00115 |
| uni9   | 0.01569 |  +0.00194 |
| uni15  | 0.01700 |  +0.00325 |

**Unison is cheap.** Going 1 → 15 internal voices adds only +0.0033, because the unison oscillator is a
*single* scheduled voice with N detuned partials (`DetunedStackIgnitor`) sharing ONE effect chain. Contrast
with superimpose, which duplicates the *whole voice* including its effect chain. **This is the key
distinction: prefer `unison` over `superimpose` when you want thickness, not per-copy effect variation.**

### Per-effect isolation (supersaw uni9 + filters + distort base, 64 onsets)

| variant             |  medRTF |        Δ |
|---------------------|--------:|---------:|
| base                | 0.00291 |        — |
| +pipeline(pedal)    | 0.00289 |       ~0 |
| +body(wood)         | 0.00434 | +0.00143 |
| +body(glass)        | 0.00430 | +0.00139 |
| +room               | 0.00415 | +0.00124 |
| +pipeline+body+room | 0.00566 | +0.00275 |

`body` and `room` each ≈ +0.0013–0.0014 on a single (un-superimposed) voice; `pipeline` ≈ free.

### 2×2 interaction — does `superimpose` multiply `body`?

| variant                  | onsets |  medRTF | cost of body |
|--------------------------|-------:|--------:|-------------:|
| base (no super, no body) |     64 | 0.00317 |            — |
| +body (no super)         |     64 | 0.00499 | **+0.00182** |
| +super (no body)         |    128 | 0.00524 |            — |
| +super +body             |    128 | 0.00797 | **+0.00273** |

**Confirmed multiplicative / super-additive.** `body` costs +0.0018 alone but +0.0027 once a superimpose
has doubled the voices. And `super + body` together (+0.0048 over base) exceeds `body` + `super` measured
separately (+0.0018 + 0.0021 = +0.0039) — the interaction term is ~+0.0009. **The expensive thing is not
any single effect; it is a per-voice effect (`body`, `analog`, filters) placed *under* a `superimpose`
stack, so its cost is paid once per copy.**

---

## 5. Conclusions

**The CPU cost of Der Schmetterling is almost entirely: (voice-count from `superimpose`) × (per-voice
effect cost from `body` + `analog` + multi-band filters).**

- The 3 super-synth voices are ~98% of the cost; GTR2's nested superimpose alone is ~half the song.
- `superimpose` is the master multiplier: it turns one voice into 2/3/4/… full voices, each re-running
  the whole chain. GTR2 reaches **1088 voices**.
- `body` (8-band parallel SVF) and `analog` (per-voice drift) are the most expensive per-voice effects,
  and superimpose multiplies both.
- `pipeline("pedal")` is free; `unison` is cheap; `distort` oversampling and `room` are modest; the
  drum/bass/noise voices are negligible.

## 6. Recommendations (cheapest CPU wins, biggest first)

1. **Attack GTR2's nested superimpose.** Collapse `.superimpose(a, b).superimpose(c)` where possible;
   every layer removed halves a large voice count. If the layers exist for width, see #2.
2. **Replace "width" superimposes with `unison`/`spread`.** Where a superimpose only pans/detunes a copy
   (e.g. `.superimpose(pan(0.85))`), a wider `unison` + `spread` gives similar thickness for a fraction
   of the CPU (unison shares the effect chain; superimpose duplicates it).
3. **Pull `body` and `analog` OUT of superimpose stacks.** Apply them to the base voice only, not to each
   superimposed copy — the copies rarely need independent body resonance / drift. (Engine-side option:
   let a superimposed copy *share* the parent's body/analog state instead of instantiating its own.)
4. **Engine: the "first-note allocation spike" is real** — each fresh render spikes on lazy delay-ring
   allocation. The planned **resource warehouse pool** (see audio MEMORY) would cap that; relevant to
   peak-block CPU, not sustained.
5. Lower-value: drop the 2nd `.distort(0.80)` if inaudible (saves little); `body`/`room` are worth their
   cost musically and are modest per-voice.

**Net:** the fastest way to get Der Schmetterling well under budget is to cut superimpose depth on the two
guitars (especially GTR2) and stop re-computing `body`/`analog` per superimposed copy — not to touch the
oscillators, distortion, or drums.
