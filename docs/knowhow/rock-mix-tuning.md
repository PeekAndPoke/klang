# Rock mix tuning — the spectral map, and guitar mids/presence pockets

Collected from the Der Schmetterling mixing sessions (2026-08, klang-ai workspace). This is
*mixing* knowledge, not engine knowledge — where energy belongs in a rock/metal mix, and how to
place multiple distorted guitars so each is defined without being loud.

## The rock curve — what is pronounced, what is taken back

Reference frame: deviations from a roughly-pink (equal energy per octave) balance. Rock's
signature deviations, band by band:

| region | treatment | why |
|---|---|---|
| 30–60 Hz | present but **tight**, never huge | Only kick + bass fundamentals. Excess reads soft and slow. Highpass everything else out. |
| 60–120 Hz | **pronounced** — the punch region | Kick body, bass power. Where "heavy" physically lives. |
| 120–250 Hz | **taken back** — the mud region | Guitar low ends, snare body, bass harmonics all collide here. Excess reads *boxy / garage demo* — untreated rooms boost exactly this band, which is why amateur recordings share that sound. |
| 250–500 Hz | dipped a few dB — "cardboard" | Small cuts here buy more clarity than boosts anywhere else. |
| 500 Hz–1 kHz | roughly neutral | Nothing famous happens here. |
| 1–2 kHz | slightly forward | Guitar crunch begins; snare attack. |
| 2–4 kHz | **pronounced — the signature band** | Pick attack, guitar bite, snare crack, aggression. A rock mix without 2–4 kHz sounds polite. Also the harshness band — a knife edge. |
| 4–8 kHz | present, then the cab cliff ~5 kHz | Definition and edge; real cabs die above ~5 kHz (klang: the double lowpass IS the cab). Cymbals own the rest. |
| 8–16 kHz | moderate | Cymbal air. Classic rock is darker up top than modern pop; air is polish, not power. |

**The classic trap:** the "smiley face" EQ (big lows + big highs + scooped mids) sounds huge on a
*solo* guitar and vanishes in a *mix* — guitars exist in the mids. Mixes keep the mids and carve
the mud instead.

## Definition beats loudness — the pocket principle

An instrument is "defined" when it owns a frequency slot no neighbor boosts. Then it reads
clearly at LOWER level. The two mechanisms:

1. **Mirror EQ** — cut instrument A where instrument B needs to speak (or simply don't boost
   there). Cheaper than any boost.
2. **Staggered emphasis centers** — when several similar instruments (four supersaw guitars…)
   each carry a `mids` and `presence` band, the exact frequencies matter less than that **each
   voice owns a different one**. Four boosts on the same center = four spotlights on one spot =
   everything must be loud to be heard.

### Worked assignment (Der Schmetterling, four supersaw guitars)

| voice | role | midsHz | presenceHz | reasoning |
|---|---|---|---|---|
| low riff guitar | ~70–110 Hz fundamentals | **650** | **2200** | Low guitar defines through "chunk", not sizzle; 650 = classic low-guitar growl slot, safely above the mud band. Lowest presence of the group. |
| counter-melody guitar | mid register | **1100** | **2600** | Melody intelligibility lives ~1 kHz — the 1.1k pocket is what lets it be turned DOWN. |
| rhythm chords guitar | mid | **900** | **3000** | Between the other two. |
| lead | top | none (body deliberately thin) | **3500** | The lead owns the TOP of the presence range — the "cut" slot no rhythm guitar touches. |

- **Q matters as much as center:** at Q 0.5 a bandpass is ~2 octaves wide and all pockets
  overlap anyway. Q ≈ 0.9 makes the slots real. Past ~1.2, narrow boosts on distorted guitars
  start to honk.
- Real-world anchor: in a two-guitar band the low rhythm guitar is voiced darker (Mesa-style)
  and the other brighter (Marshall-style upper-mids) — 2200 vs 3000 is that convention.

## Related lessons from the same sessions

- **Bass stays dry.** Reverb on bass smears low-mids straight into the mud band. Crisp bass and
  a clean 120–250 region are the same battle from two sides.
- **Speakers vs headphones:** headphones reproduce 30 Hz effortlessly; a stereo deck's woofer
  dies below ~55 Hz, a laptop below ~200 Hz. A mix whose low end lives in the fundamentals
  sounds huge on headphones and empty on speakers. Fixes that put energy where speakers can play
  it: **bass saturation** (asymmetric shapes — `tube`, `diode`, `stomp` — generate the even
  harmonics; symmetric ones like `gentle`/`soft` generate none) and the **pitch-tracking
  highpass resonance trick** (`hpq` > 0.707 puts the filter's resonant peak ON the note's
  fundamental: at cutoff, gain = Q, so hpq 0.9 ≈ −0.9 dB where 0.55 was −5.2 dB — measured
  +6 dB at 79 Hz on the low guitar from that one change).
- **A fixed notch in a melodic voice's fundamental range is a footgun**: notes whose fundamental
  lands on the notch get quieter than their neighbors — pitch-dependent unevenness. Prefer a
  shallow wide dip (subtract a scaled bandpass: `signal.minus(signal.bandpass(f, 1.0).mul(0.3))`)
  or place the notch only on voices whose fundamentals sit safely below it.

## How to verify a tuning change

Render and measure (klang-ai `scripts/specdist.py`): 1/3-octave table vs pink, 16-cycle windows.
Check the changed region moved AND its neighbors did not; per-voice isolation renders show
whether each guitar now peaks in its own third-octave band — that is the direct "definition"
metric. Ears decide, the table referees.
