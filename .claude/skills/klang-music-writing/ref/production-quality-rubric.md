# Production Quality Rubric — the seven categories, with anchors

What each review category means, where its boundary with the neighboring categories is, and
what a 10, a 5, and a 1 sound like — each anchored by a real commercial reference production.

Source: Gemini 2.5 Pro explaining its own 1–10 scoring rubric (2026-08, during the Der
Schmetterling review series), lightly edited. The definitions and anchors proved stable across
sessions; the numeric scores did not (see "How to use scores" at the end).

## Calibration

- The 1–10 scale is calibrated against **professionally produced, released commercial music**.
- A competent but unmastered home production typically scores **4–5 overall** — "a strong demo:
  solid musical ideas that lack the final stages of technical polish."
- **Overall is not an average.** It is a weakest-link weighted judgment: one severely deficient
  category drags the overall score down disproportionately, because it compromises the whole
  listening experience.

## The categories

| Category | Measures | Does NOT measure | 10/10 reference |
|---|---|---|---|
| timbre_quality | Intrinsic richness of each individual sound | How sounds fit together (palette), their EQ in the mix (spectral) | Daft Punk — *Get Lucky* |
| onset_consistency | Clarity, punch, consistency of note/hit attacks (transients) | Musical timing or rhythm | Noisia — *Stigma* |
| spectral_balance | The whole-mix EQ profile, lows to highs | Individual instrument EQ ("the forest, not the trees") | Justice — *D.A.N.C.E.* |
| stereo_image | Left-right placement, width, depth | The quality of the sounds themselves | Deadmau5 — *Strobe* |
| dynamics_mastering | Micro-punch of hits AND macro contrast between sections; mastering loudness | — | Skrillex — *Bangarang* |
| palette_coherence | Whether all sounds share one musical universe | Individual sound quality (timbre) | Kraftwerk — *The Man-Machine* |
| overall | Holistic professional polish | — (weakest-link weighted synthesis) | "radio- or film-ready" |

## Anchor descriptions per category

### timbre_quality

- **10** — Rich, detailed, pleasing sounds whose complexity invites repeated listening. *Get
  Lucky*: every instrument has an expensive-sounding texture and character.
- **5** — Functional, stock-sounding patches. Recognizable, not broken, but without character or
  depth. Common in demos and stock music libraries.
- **1** — Harsh, thin, unpleasant: aliasing, digital noise, raw basic waveforms with no
  processing.

### onset_consistency

- **10** — Every transient razor-sharp and impactful; the rhythm feels tight and powerful.
  *Stigma*: drum hits of surgical precision.
- **5** — Transients present but soft: kicks "clicky" instead of "thumpy," snares a "whoosh"
  instead of a "crack." Timing fine, starts blurry.
- **1** — Transients completely smeared (slow attacks everywhere, excessive reverb); sluggish
  and rhythmically undefined.

Note: this category is about *transient punch*, drums first. A low score usually means the kick
and snare attacks, not pitch or phase problems in the synth voices.

### spectral_balance

- **10** — Full, clear, balanced spectrum: deep controlled bass, present midrange, smooth airy
  highs; translates to any system. *D.A.N.C.E.*: powerful and full with no range masking
  another.
- **5** — Listenable but flawed: boxy/honky low-mid buildup (200–500 Hz), missing sub (<80 Hz),
  dull top (<10 kHz). The classic untreated-room mix.
- **1** — Unlistenable on most systems: painful buildup (all 2–4 kHz) or all mud.

### stereo_image

- **10** — Wide, deep, immersive; elements close, far, and moving — a 3D soundstage. *Strobe*:
  an enormous, evolving stereo field.
- **5** — Basic functional stereo: some panning, but the mix feels centered; one stereo reverb
  provides "wash" rather than placement.
- **1** — Entirely mono.

### dynamics_mastering

- **10** — Punchy hits plus real tension and release between sections. *Bangarang*: extreme
  micro-punch alongside huge verse-to-drop shifts.
- **5** — Loud but flat: commercial volume achieved at the expense of punch; every section has
  the same energy.
- **1** — "Sausaged": a solid brick of over-limiting — or the opposite, far too quiet and
  unmastered.

### palette_coherence

- **10** — A curated sonic world; every sound feels designed for this track. *The Man-Machine*:
  one family of custom sounds, a unified iconic aesthetic.
- **5** — Functional but generic: sounds don't clash but read as unrelated presets; no unifying
  identity.
- **1** — Sounds clash horribly (hyper-real acoustic guitar + chiptune + death-metal kick).

### overall

- **10** — World-class: technically flawless, sonically engaging, emotionally impactful.
- **5** — A strong demo: clear ideas, no catastrophic errors, but lacking the clarity, power,
  and polish of a professional release.
- **1** — A rough, unlistenable sketch; technical flaws obscure the musicality.

## How to use scores from an LLM reviewer

Learned the hard way during the Der Schmetterling series (v1–v21):

1. **Single-run scores are dice.** An A/A test (same MP3, same prompt, two fresh sessions)
   returned overall 6 vs 4, spectral_balance 6 vs 3, dynamics 6 vs 3. Per-category noise is
   ±2–3 points. Track *repeated qualitative claims* across runs, not numbers.
2. **Review blind, always.** Telling the reviewer "this is an updated version" anchors it: it
   will report issues "resolved" for a four-line diff. Fresh session, audio only, no code.
3. **Never trust low-end or dynamics claims without measurement.** "Missing sub-bass" and
   "limiter pumping" appear in nearly every review regardless of what the spectrum analyzer and
   envelope measurements show. Verify against a spectral-balance table and a crest/pumping
   measurement before acting.
4. **Structural claims are the good stuff.** "The whole mix lives in a 100 Hz–5 kHz box,"
   "the two leads occupy the same band," "the arrangement never breathes" — these repeat across
   sessions and framings, match measurement, and point at real decisions.
