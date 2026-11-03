# KLANG-00408: OKLab helpers in the look editor: shades, spectrum steps, an accent and a contrast check

> **Status (2026-11-03): NOT SCHEDULED.** Asked for by the maintainer; an idea, not yet planned in detail.

## Why this exists

Making a look on the Looks page means picking about forty colours one by one, each with a colour field and a hex
field (`LooksPage.renderColor`). Colours that belong together (the grounds from deep to raised, the five spectrum
steps, an accent beside the metal) have to be matched by eye.

OKLab (Björn Ottosson, 2020; credited in `CREDITS.MD`) is a colour space in which equal steps in its numbers look like
equal steps to the eye: L for lightness, a for green to red, b for blue to yellow, and its round form OKLCH (lightness,
chroma, hue). Mixing or stepping colours there stays even and clean, where a plain RGB mix goes muddy.

Twice already a look was tuned this way by hand:

- The spectrum of the blue look: "make the color transition to the other color smooth. Keep the lowest and highest
  color as is and interpolate the brightness between" (the maintainer, 2026-10-29); the three steps between were worked
  out in OKLab outside the app (the comment in `KlangLooks.kt`).
- Copper's spectrum blend (2026-11-03): a plain RGB mix of its teal into its copper went grey-green, an OKLab mix runs
  through a warm grey. The conversion now lives in `Spectrumeter.kt` (`mixed`, `toOklab`, `fromOklab`), about 40
  lines from the published formulas.

The maintainer (2026-11-03), on what OKLab could do: "Could we integrate this to help the user to make new themes?",
then "File a ticket for the OKLab style helper for the theme builder please".

## Decide before implementing

- **D1: which helpers, and in which order.** Proposal: the four below, the spectrum steps first (it answers a request
  made twice), the contrast check second (it prevents a look nobody can read).
- **D2: a helper suggests, the user takes it.** Proposal: a helper fills its colours into the draft the editor already
  keeps, as a typed value would; nothing changes a look on its own, and the page's **Reset** drops it with the rest of the draft.
- **D3: where the colour maths lives.** Proposal: one small file in `klangui`'s `feel` package (`Oklab.kt`, plain
  functions: to and from OKLab and OKLCH, mix, a lightness step), moved there from `Spectrumeter.kt`, which then uses
  it. No library: the maths is a dozen lines of published formulas.
- **D4: CSS or Kotlin.** Browsers read `oklch()` and `color-mix(in oklab, ...)` themselves, but a look's values are
  hex and are shared as text (`KlangRole.Kind.Color`), so the helpers compute hex in Kotlin. CSS's own forms stay an
  option for derived shades inside the styles (KLANG-00337's rule: derived shades are computed in the styles, never
  stored as roles).

## The work

1. **The colour maths.** `Oklab.kt` in `klangui/src/jsMain/kotlin/feel/` (D3): hex to OKLab and back, OKLCH, a mix,
   clipping to what a screen shows; `Spectrumeter.kt` uses it instead of its own copy. Specs with values worked out
   independently (as `SpectrumeterSpec` does).
2. **Spectrum steps from two ends.** On the spectrum card: pick the quiet and the peak colour, the three between are
   filled in even steps of lightness, in OKLab. With the blend (`KlangRole.SpectrumBlend`) the cells already fade.
3. **Grounds from one colour.** On the ground card: pick one ground, the others (deep, panel, raised, sunken) are filled
   as lightness steps of it with its hue kept; the steps taken from the default look's spacing.
4. **An accent across the wheel.** Suggest the accent and the lit lamp at the hue opposite the metal, the way Copper
   pairs its copper with blue (hue about 25 and about 204).
5. **A contrast check.** A quiet note on a card when an ink is too close in lightness to the ground it sits on (text on
   panels, labels, the LCD's ink on its screen). A note only, never a block: a look may be dark on purpose.
6. The look editor's other parts stay as they are: the colour field and the hex field remain the way to set any one
   colour.

## Acceptance

- The maths is one file with specs; `Spectrumeter.kt` uses it and Copper's spectrum draws as before.
- Each helper fills the draft only, and the page's Reset takes it back.
- The spectrum helper, given the blue look's quiet and peak, gives steps close to the ones tuned by hand.
- The maintainer has tried the helpers on a new look.

## History

- 2026-11-03: created, from the maintainer's question after Copper's spectrum blend (commit f22222970).
