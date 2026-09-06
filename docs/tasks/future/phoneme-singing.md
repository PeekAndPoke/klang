# Phoneme singing (`sing()`)

> **Status: idea, not started.** Restored 2026-09-06 from the maintainer's session memory during
> the memory housekeeping (`docs/housekeeping/2026-09-06-memory-and-rules-inventory.md`). This file
> is the ONLY surviving record: the M1 grin test was implemented 2026-07-05 on branch
> `singing-like-a-robot`, which was deleted unmerged, and the original plan doc was never committed.
> If the idea is picked up again, recreate plan and M1 from the findings below.

Robot / caricature singing: a fixed, learnable "mouth" bank, tuned by ear. Fits the caricature
sound model (2 to 4 acoustic tells, sparse fill, non-moving target).

## What M1 had shipped (lost)

- `Phoneme` enum in `audio_bridge`, 13 entries: vowels a/e/i/o/u (ids 0 to 4, all routed to one
  shared larynx child) and consonants k/l/r/t/s/m/n/ŋ (ids 5 to 12).
- A `sing` bank (`audio_be/ignitor/SingBank.kt`) plus a demo song "Singing Like A Robot".
- Balance pass 1 (RMS-verified): plosives mul 2.5, voiced consonants 0.4 to 0.45, larynx 1.0. The
  vowel formant filter eats larynx level while voiced consonants bypass it, so raw equal levels
  sound backwards.

## Key findings

- **`vowel()` is orbit-level** (KatalystFormantEffect on the summed orbit mix, owner-voice
  configured), so one singer per orbit: two vowel lines on one orbit fight over the mouth.
  Consonants passing through their orbit's vowel filter give free coarticulation.
- **Binding needs continuous voicing (by ear).** Sequential phoneme slices sound disconnected,
  "not speech". The working shape: the vowel core is ONE unbroken note per syllable, consonants are
  OVERLAID in a parallel lane on the same orbit (k+l rides the vowel onset, the ŋ coda overlaps the
  vowel release, plosive closures are leading rests). `sing()` must therefore emit overlay
  structure, not sequential slices. Side fix from that work: weighted rests `~@3` dropped mods in
  `MnPatternToSprudelPattern` (fixed, regression test).

## Load-bearing decisions (do not re-litigate)

- One ignitor per phoneme (about 25 consonants); consonant x vowel combinations are temporal
  (sequencing), not timbral, so no diphone explosion.
- ONE built-in sound named `sing` ("mouth" rejected): consonants plus a neutral larynx child in a
  single `Osc.variants` bank keyed by the enum. The larynx has no baked character (no crush); color
  comes downstream via crush()/body()/lpf(). A future `Osc.sing(larynx = ...)` derivation could
  override slots.
- Register knob is `singer("bass" ...)`, not `voice()` ("voice" collides with Voice/VoiceData).
  It composes with plain `vowel()` (same "bass:a" table prefix).
- Shared `Phoneme` enum in `audio_bridge` with an explicit append-only `id: Int` (never ordinal),
  the same Int-to-Phoneme map on both wire ends; `VoiceData.soundIndex: Int?` unchanged, no codec
  or schema bump. Note the later project rule: new wire distinctions prefer sealed `@WireName`
  types over enums (`/dsl-design` §7); re-check this choice when picking the work up.
- `sing()` is a sprudel pattern-level macro (squeezeJoin syllable expansion); the MVP needs zero
  new VoiceData fields. Vowels reuse the existing vowel()/vowelMix()/vowelFloor() tables.
- DSL: two aligned patterns, `note(...).sing("klaŋ [ʔo di] oː")` primary; a closed form `c3=klaŋ`
  is later sugar. A syllable is the rhythmic unit; IPA stress marks become gain accents.
- M1 gate: a hand-built grin test (8 phonemes, one hardcoded word) BEFORE building `sing()`.
- Related but separate: `soundLabel` named sound indices for open vocabularies (`guitar:muted`);
  phonemes never need it.
