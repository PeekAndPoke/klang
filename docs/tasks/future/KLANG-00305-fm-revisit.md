# KLANG-00305: FM: a round with the maintainer

> **Status (2026-10-20): IDEA.**

Status: **future (maintainer, 2026-10-13): "we need to visit fm anyway, as I have no experience with it at all."**
A listening and design round on frequency modulation, together, before more FM work. Everything FM that is open or
was decided by default is collected here, so the round starts from one page.

## What stands today

- **An `fm` without an envelope keeps its depth through the release tail** (Q30, confirmed 2026-10-13): the node runs
  an envelope only when one is written, as the Ignitor door's `fm` always did; before pitch step 4, sprudel's strip
  dropped the depth at the gate. `note("c3").s("sine").fm(300, 1.4).release(0.5)`. Listening pair:
  `tmp/listening/pp-step4/`, heard 2026-10-17: "keep the new version".
- **`s("sgpad").fm(...)` keeps its pitch again** (Q31, fixed 2026-10-18, maintainer: "fix it now"). Heard
  2026-10-17 (`tmp/listening/pp-step4/sgpad-*.wav`): the after "sounds broken, at least very different": one
  modulator served both detuned saw layers and jumped a block of phase at every block boundary (block-size
  dependent, ledger E8). The fix rebuilds `sgpad`, no engine change: its second saw reads its pitch as
  `Freq * 0.1.semitones()` instead of `Saw().detune(0.1)`, the same frequency to the bit but no forking detune, so
  the FM serves one pitch, the note, as the strip did. `s("sgpad")` without fm is bit-identical; with fm it is the
  same at every block size and on the harmonic grid again (`SgpadFmSpec`; new file `sgpad-fixed.wav`, to be heard,
  measured in `sgpad.md`). No built-in has the shape now; an authored `(x + x.detune(7)).fm(m)` still has it (the
  author rule and the diagnostic below).
- **The fm diagnostic** (KLANG-00291 (fm above forking detune diagnostic),
  C6 of the 2026-10-10 baseline): the first editor diagnostic, for an fm above a forking `detune`.
- **FM's V8 allocations**: engine follow-ups 10c and 10d
  (KLANG-00290 (engine follow ups)), performance only. The knob reads
  are fixed (2026-10-19); open (item 30's class): one heap number per fm per block for the modulator's frequency,
  and one per wrapper whose outer mod reads the note, at any non-integral note. **Revisit in this round**
  (maintainer, 2026-10-19: FM-specific, so it belongs here): leave it as item 30's root box is left (decision D,
  bytes only, no measured speed or audio effect), or build item 30's option B (a tagged cell) for the modulator's
  frequency, the wrapper's pin and the root box together. The measurements and the ready remedy are in items 10c and
  10d.

## For the round

What FM is for in Klang's sound (bells, electric pianos, basses, metallic hits), a few listening examples on both
doors, and then which of the above matter.

## History

- 2026-10-20: numbered KLANG-00305 (was `docs/tasks/future/fm-revisit.md`).
