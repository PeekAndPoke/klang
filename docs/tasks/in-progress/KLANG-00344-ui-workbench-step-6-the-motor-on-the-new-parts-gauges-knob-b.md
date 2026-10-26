# KLANG-00344: UI workbench, step 6: the Motor on the new parts (gauges, knob, blue trace, more spectrum columns)

> **Status (2026-10-26): IN PROGRESS.** The maintainer: "anything you can do in the meanwhile like adopting the new
> gauges?"
>
> **Part of:** KLANG-00334 (UI workbench: the Motor-centred layout, tool windows and the first-visit trail)

## Why this exists

The Motor still draws its gauges with `RoundGauge` and its volume with `VolumeKnob`'s own label ring, while the
kitchen sink's `KlangGauge` and `KlangKnob` (KLANG-00338) are approved. KLANG-00334 item 36 also asks for the
oscilloscope in blue (a theme role) and more spectrum columns.

## Decide before implementing

- **The three gauges** become `KlangGauge` as in the kitchen sink's "The Motor's three, with icons": cylinders
  (0 to 32), heat (0 to 1, hot from 0.85, shown in percent), voices (0 to 100, today's range); their sizes (50, 62,
  50 px) and the needle's smoothing (0.933) stay. Offline (no reading) is the gauge's own offline look.
- **The volume knob** becomes a `KlangKnob` (31 px, dark): the same master volume, its scale (`MasterVolumeScale`)
  and its "remember when the turn is over"; it gains the arrow keys and the focus ring. Its LED (online or not) goes:
  the gauges beside it show that.
- **Alt+V** puts the focus on the knob (the maintainer, 2026-10-26: "The volume knob could also use a shortcut like
  Alt+V maybe to select the knob"), so the arrows turn it; the action is asked of Klang-Main-Dev for
  `KlangKeyBindings`.
- **The start page's tint** (its gauges glow in the background lamp's colour, at half strength) is set as the theme's
  `--kl-gauge-glow` on the start page's wrapper, so `PlayerMiniStats` loses its two glow parameters.
- **The trace** reads a theme role of its own, `ScopeTrace` (`--kl-scope-trace`), so a look changes it. The accent's
  blue was tried first and "does not really work for the Osci"; the maintainer asked for gold, and gold means "try me"
  only, so brass near gold (`#c9a35a`, the maintainer's choice, 2026-10-26); **the spectrum** gets 28 columns (today 16).
- **The gauge itself** (klangui `KlangGauge`, so the kitchen sink too), the maintainer on the look (2026-10-26): "the
  "outlines" of the gauges could be slightly thicker" (the silver rim from 4% to 6% of the size; then "now the gauge
  outlines are too thick", so 5%) and "Could the gauges
  also appear as if they are cover by domed glass... ever so slightly ?" (a glass layer over the dial: light caught at
  the top of the dome, a touch of shade where it curves into the rim).
- **The needle moves by itself** (review round 1, MAJOR): `KlangGauge` smoothed only when its props changed, and the
  old `RoundGauge` got new props on every tick (a fresh lambda), the new one does not; so a reading that held still
  left the needle short of it (24 voices, then silence: "22" for good). Now the gauge steps its needle once per frame
  until it is there, and `PlayerMiniStats` loses its 60 fps ticker.
- **The colour follows the needle** (the maintainer: "the coloring of the gauges does not seem to react to the
  smoothed value but to the unsmoothed one. I see it flickering to red even though the value is shown at 50%"): hot is
  decided on the smoothed needle.
- **A gauge tells its name on hover** (`title`), as the old ones did (review round 1).
- **The trace colour to try**: the maintainer, on brass, "The Osci needs to be brighter... or matching the outline
  color of the gauges... not sure. we will need to experiment here"; a kitchen sink cell shows a live scope beside a
  gauge with six candidates, set on the look's Script layer (not saved, the Motor shows it too until a reload). A new
  look also redraws the idle trace at once (review round 1).
- **The spectrum names a column's frequencies on hover** (the maintainer: "hovering the Spectrumeter will show the
  frequency range of the hovered bucket "somewhere""): a small label over the floor, "120 to 180 Hz".
- `RoundGauge` stays for the start page's dyno run until the step where the Motor glides into its corner; the
  Motor's gauge functions (`comp/gauges.kt`) and the kitchen sink's "today" cells for them go.

## The work

1. `PlayerMiniStats`: the three `KlangGauge`; the knob; no glow parameters.
2. `VolumeKnob` on `KlangKnob`; Alt+V once the action exists.
3. `StartPage`: the tint as `--kl-gauge-glow`.
4. `Motor`: the trace from `Accent`, 28 spectrum columns.
5. Removals: `comp/gauges.kt`, the kitchen sink's "today" gauge and knob cells.
6. Review, a look in the browser.

## Acceptance

- The Motor's gauges and knob look as in the kitchen sink; the knob turns the master volume by drag and by arrows.
- The trace is blue and follows the look; the spectrum has 28 columns.
- The start page's gauges keep their tint.

## History

- 2026-10-26: created and started.
