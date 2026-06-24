# Copyright Audit 06 — Attribution touch-ups

**Bucket A (no infringement) · 🟢 nice-to-have · docs/credits only**

## Context

A few places use third-party *public/permissive* material that is fine to use but under-credited. Adding the
attributions costs nothing and removes any "they didn't even credit it" optics. None of these block
relicensing.

## Items

1. **`chord-voicings` (felixroos, MIT)** — `tones/src/commonMain/kotlin/voicing/VoicingDictionary.kt` contains
   interval-dictionary literals (`lefthand`/`triads`) that are byte-identical to Strudel's `voicings.mjs`.
   Resolution: Strudel itself `import`s these from the **MIT** `chord-voicings` package, so the shared source
   is MIT, not Strudel's authorship (→ Bucket A). Action: add `chord-voicings` (felixroos, MIT) to the
   `tones/LICENSE` / `tones` notice so the provenance is explicit alongside the existing tonal.js credit.

2. **`fast_tanh` Padé approximant** — `audio_be/.../ClippingFunctions.kt` (`fastTanh`) uses the public-domain
   "27/9" Padé form `x(27 + x²)/(27 + 9x²)`, identical to many synths (and to Strudel `superdough/worklets.mjs`).
   Action: one-line `CREDITS.MD` mention of the musicdsp/KVR public-domain origin. (Not a legal exposure — just
   completeness.)

3. **PolyBLEP** — the band-limiting residual matches the standard Välimäki/KVR formula (Strudel cites the same
   source; Klang's `polyBlep` is currently dead code with no call sites). Action: ensure `CREDITS.MD` names the
   PolyBLEP source; consider deleting the unused `polyBlep` if it stays dead (separate cleanup).

## Verification

- `tones/LICENSE` lists tonal.js **and** chord-voicings.
- `CREDITS.MD` mentions the Padé tanh and PolyBLEP public sources.

## Done when

The permissive/public third-party material (chord-voicings, Padé tanh, PolyBLEP) is explicitly credited.
