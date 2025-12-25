# Filter Control

You have bakedFilters, but common use cases require dynamic control over the filter parameters per event (Low Pass, High
Pass, Band Pass).
Implementation Plan:

1. Ensure your AudioFilter implementation allows setting parameters at the start of the voice.
2. Map Strudel properties:
    - lpf (Low Pass Filter cutoff)
    - hpf (High Pass Filter cutoff)
    - bandf (Band Pass Filter cutoff)
    - resonance (Q factor)

# Global Effects (Distortion / Compression)

Once Stereo and Reverb are done, "coloring" the sound is next.

- Distortion (shape): Can be applied per voice or per Orbit. A simple tanh (hyperbolic tangent) waveshaper on the output
  signal works wonders for that gritty sound.
- Vowel Filter (vowel): A formant filter that mimics human speech (a, e, i, o, u). This is a signature Tidal feature.

-> sine wave osc is "clipping" on note-change with gain > 0.7
-> soft-clipping implemented but not solving it fully

# Sliding notes (pitch bend)

Should be possible now that we have LFO / Vibrato.

////////////////////////////////////////////////////////////////////////////////////////////  
// DONE   
////////////////////////////////////////////////////////////////////////////////////////////

(x) (2025-12-25) Modulation: Vibrato

(x) (2025-12-25) Stereo Output & Panning

(x) (2025-12-25) Reverb

(x) (2025-12-25) Distortion


