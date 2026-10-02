# A noise band from sines: `noiseBand(low, high, partials)`

Status: **future, idea, not started.** Asked for by the maintainer 2026-10-02. A follow-up to
[`sine-inharmonic-partials.md`](sine-inharmonic-partials.md): a noise band is one more generator of partials, so it
needs the partials bank first, or composes the sines by hand until it exists.

## Why

Der Schmetterling's snare thud was a pink noise band about 230 Hz wide and 50 ms long. Such a burst holds only about
bandwidth × duration ≈ 10 independent chunks, so every hit rolled new dice: about 6 dB of hit-to-hit loudness and
7.5 dB of peak (measured over 108 hits, 2026-09-30). Widening the band or summing several noises did not help (summing
independent noises is just another noise). The fix was 13 sines with fixed frequencies, gains and signs (commit
`20d74bb8`): a band that sounds like noise and is the same on every hit. They were fitted by hand. This task derives
them, so a song asks for a band instead of writing 13 lines.

**Where a cluster is needed at all:** narrow, short bands. Broadband noise averages itself out (the snare's crack,
white noise above 1.5 kHz, varies 0.8 dB per hit), and a full-range cluster would need about 60 partials for a problem
the noise sources do not have. `whitenoise`, `pinknoise` and `brownnoise` stay the tools for broad noise.

## The maths (worked out 2026-10-02, numbers from an offline study of the snare's thud)

**Frequencies: a geometric grid.** `f_k = low * (high / low) ^ (k / (N - 1))`, `k = 0 .. N - 1`. For 138 to 712 Hz
and 13 partials: a step of 2.37 semitones, within a few percent of the hand-picked set (whose biggest nudges moved
partials away from the snare head at 210 Hz, a song concern, not the method).

**How many: density per critical band.** The ear cannot resolve partials that share a critical band and hears a band
instead of a chord. With the ERB number `E(f) = 21.4 * log10(4.37 * f / 1000 + 1)` (Glasberg and Moore, quoted from
memory, verify), `N ≈ density * (E(high) - E(low))`. The thud spans 8.8 ERBs; 13 partials are 1.5 per ERB, which
sounded right. The threshold where it turns into a chord is untested.

**Gains: the power of each partial's slice.** Each partial stands for the noise power between its neighbours'
geometric midpoints: `g_k = sqrt(integral over the slice of S(f) df)`. For a colour `S(f) ∝ f^α` on the geometric grid
this is `g_k ∝ f_k ^ ((α + 1) / 2)`, a tilt of `3 dB * (α + 1)` per octave on the partials:

| colour | α | partial gains on a geometric grid |
|---|---|---|
| white | 0 | ∝ √f, +3 dB per octave |
| pink | −1 | equal |
| brown | −2 | ∝ 1/√f, −3 dB per octave |

So the colour is one knob, a tilt relative to pink. On a linear grid (equal Hz) the law is `g_k ∝ f_k ^ (α / 2)` and
white is the flat one; the geometric grid is the natural one for the ear. The engine's `brownnoise` flattens below a
corner set by its `depth`; matching it needs a shelf on the gains, not only a slope.

**Shaping stays with the filters.** Filters are linear, so an equal-gain (pink) cluster through the thud's bandpass
(230 Hz, Q 1) and highpass (140 Hz, two passes) has the spectrum of the noise through them: the derived gains
0.38 0.57 0.67 0.78 0.98 1.00 0.94 0.84 0.73 0.63 0.54 0.46 0.39 against the hand-fitted
0.52 0.68 0.65 0.83 0.94 1.00 0.84 0.75 0.69 0.59 0.49 0.47 0.36. The helper need not bake a filter into its gains.

**Level independent of N:** gains of `1 / sqrt(N)` keep the sum's RMS the same whatever the partial count.

**Phases: alternating signs.** Plain sines all start at zero and rise together: the onset spikes. Crest factor of the
thud's burst (0.5 ms attack, 12 ms decay), peak over RMS:

| phase law | crest |
|---|---|
| all zero | 19.9 dB |
| real noise (the old thud, rendered) | 15.5 dB |
| random phases, median | 15.1 dB |
| **alternating signs `+ - + - ...`** | **13.3 dB** |
| Schroeder `φ_k = π k² / N` (quoted from memory) | 13.3 dB |
| golden-angle phases | 13.3 dB |
| best of all 4096 sign patterns (the snare's search) | 11.5 dB |

Alternation is a closed form within 1.8 dB of the exhaustive search and beats real noise by 2 dB: neighbouring partials
cancel each other's rise at the onset. It needs no start-phase knob (a sign is a `mul(-1)`); free phases bring nothing
extra here.

## Shape of the idea

A bank on the sine builder, next to `harmonics`, `octaves`, `suboctaves` and the future `partials`:

```javascript
Osc.sine(x => x.fundamental(0).noiseBand(low = 140, high = 700, partials = 13))
  .bandpass(230, 1.0).highpass(140, 0.707, x => x.passes(2))
```

Frequencies on the geometric grid, gains `1 / sqrt(N)` tilted by the colour, alternating signs, all fixed at build:
no randomness, the same on every note. Until the partials bank exists, the same can be composed from plain sines.

## To decide before implementing

- **Hz or ratios.** The thud's partials follow the note (`Osc.freq().mul(...)`, so the snare's tuning moves them).
  A bank's partials are multiples of the sine's own frequency today; a band in absolute Hz is a different contract.
- **The colour knob's word and scale.** `whitenoise` has `color` (a tilt from −1 to 1); read its mapping to dB per
  octave first. If it fits, the band takes the same word and scale (parameter parity, `/dsl-design` §4).
- **Count or density.** `partials = 13` or a density per ERB; the count is simpler, the density keeps a band sounding
  the same when its edges move.
- **Jitter.** A regular grid might be heard as a chord (equal 2.37 semitone steps). A deterministic irregularity
  (low-discrepancy offsets) is the remedy if it is.
- **Evidence first.** Render the snare with the derived thud next to the hand-fitted one: spectrum, crest, hit-to-hit
  range, and the maintainer's ears on band against chord.
