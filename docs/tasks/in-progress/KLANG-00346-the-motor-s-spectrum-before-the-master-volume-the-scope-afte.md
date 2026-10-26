# KLANG-00346: The Motor's spectrum before the master volume, the scope after it

> **Status (2026-10-26): IN PROGRESS.** The maintainer: "One addition on the global volume setting ... i think it
> should only influence the osci but not the spectrum"
>
> **Part of:** KLANG-00334 (UI workbench: the Motor-centred layout, tool windows and the first-visit trail)

## Why this exists

KLANG-00330 put the master volume as a gain between the worklet and the one analyser both visualisations read, so
"the scope and the spectrum follow the volume" (the maintainer, 2026-10-25, asked once more when building). Now the
spectrum should show the song as it is made, whatever the volume; the scope keeps showing what the speakers get.

## Decide before implementing

- **Two analysers, one each side of the gain**: the worklet, the spectrum's analyser, the volume, the scope's
  analyser, the speakers. An analyser only listens and passes the sound on, so the sound is unchanged. This replaces
  KLANG-00330's decision for the spectrum; the scope's part of it stands.
- `JsAudioAnalyzer` takes two taps: the waveform from the scope's analyser, the FFT from the spectrum's. An analyser
  that cannot be made is left out of the graph; its view stays dark, the sound takes the same way.

## The work

1. `JsAudioBackend`: the two analysers and the graph; `JsAudioAnalyzer` with its two taps.
2. A look in the browser (the maintainer, 2026-10-26: "yes it works i tested it in the browser"); one review.

## Acceptance

Turning the volume down shrinks the scope's trace, not the spectrum's columns; the sound is as before.

## Review and checks (2026-10-26)

- **Round 1** (level 1, blind, code and audio): no findings. The graph with or without each analyser, the sound path
  unchanged, no delay, teardown as before (a new backend per start, the context's close releases both), the two
  readers (the scope, the spectrum) each on its tap.
- No test: the graph is built inside the backend's run against a live AudioContext; the maintainer's browser test is
  the check.

## History

- 2026-10-26: created and started; the maintainer tested it in the browser.
- 2026-10-26: reviewed (clean), committed, done.
