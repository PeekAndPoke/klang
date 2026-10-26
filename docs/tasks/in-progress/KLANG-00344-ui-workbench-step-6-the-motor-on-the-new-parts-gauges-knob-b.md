# KLANG-00344: UI workbench, step 6: the Motor on the new parts (gauges, knob, the trace's colour, more spectrum columns)

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
  only, so brass near gold (`#c9a35a`); then, from the kitchen sink's candidates, the rims' silver: "make the Osci
  the same color as the outlines of the gauges" (`#c9ced6`, as `MetalMid`, 2026-10-26), and "When the them is set to
  copper the Osc should be copper like the gauges": a role may follow another (`KlangRole.follows`), the trace
  follows `MetalMid` while no look sets it; **the spectrum** gets 28 columns (today 16).
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
  frequency range of the hovered bucket "somewhere""): a small label, "120 to 180 Hz". Placed by the maintainer's
  look: "do not make it follow the mouse ... center align it", not under the pointer, "Bit lower otherwise it is
  covered by the Osci", "Put it vertically centered": centred, in the middle of the spectrum's part under the scope.
- **The spectrum's columns are pixel exact** (the maintainer: "make the buckets in the spectrum pixel exact in
  positioning and width. This means some pixels might become unused and we put half of them left as padding"): each
  column the same whole number of pixels, the rest as padding, half on the left (`spectrumColumns`, which the hover
  reads too, so the label names the column under the pointer and nothing over the padding).
- **The knob's circle and the open Motor button, by role** (the maintainer: the knob's circle should match the gauges'
  outlines in every look, as in copper, and the pressed Motor button be bronze in copper; "These color need to follow
  from the theme color roles"; "Do both to try it"): the knob's arc reads a new role `KnobArc` that follows
  `MetalMid` (material, as the rims; it read `AccentMuted` before, which copper happened to set); the open Motor
  button stays a signal (`LampOn`, selected), and the copper look gives `LampOn` a bronze, `#e8b28a`, lighter than the
  warning's copper (`#d19a66`) so the two stay apart. The rule behind it is in KLANG-00334 item 42. Then all three
  looks took their metal's light for "selected"; "a bit too plain", and "the blue-nes must be adjusted per theme": a
  blue per look, opposite its metal's hue (Default `#528bff`, Copper `#4fa3d9`, Cream `#8099d6`; KLANG-00334 item 42).
  The maintainer: "The blues match well now".
- **The trace as a swinging string** (the maintainer: "draw the wareform twice. Brighter on top, like with the gauge
  outlines and darker below, keep the black behind them"): the path is stroked three times, the black band, the
  trace's colour darkened a little lower, then lightened on top; both shades come from the trace's colour, so they
  follow the look. "the color contrast needs to be wider": darkened 55% 1.5 px lower, lightened 35% (was 35%, 1 px,
  20%). Then "it appears a bit blurry" and "needs a more metallic look": the canvas draws at the screen's own
  resolution (its backing store times `devicePixelRatio`, the drawing in CSS pixels), and the string has three tones
  across its thickness as the rims do: its shade (55% darker, 1 px lower), its body (the trace's colour) and a thin
  highlight along the top (0.45 of the width, 0.5 px up); the dark band 2 px lower, a shadow; then "The contrast
  could still be stronger": the shade 70% darker, the highlight 55% brighter, the shadow 40% black.
- **Alt+V** is wired (Klang-Main-Dev's `VolumeFocus`, 70770094): it puts the focus on the knob. To hand-test on
  Windows Firefox, where Alt+V is the View menu's key (Klang-Main-Dev's note).
- **The oscilloscope cleaned up** (the maintainer: "remove the thing where it goes full width on click ... no longer
  needed was just a toy. Cleanup the whole component"; "The click cursor still shows in the osci and should not"):
  the expanded overlay, its flattened history and prefix sums, the time warp and the mirrored halves, the click and
  the pointer cursor go; the trace, its centre line, its ease-off towards the ends and its afterglow stay, with the
  maintainer's own edits (width 1.5, the shadow at 0.25, no gold highlight). 688 lines to 293.
- **Esc for the panels has one path** (Klang-Main-Dev's 025f56a3: an Esc a field ignores passes on to the key
  manager): the drawer's and the floating window's own Esc handlers go, so one Esc always closes the newest open
  panel, also with the focus in a field or in the editor.
- **The panels' guard goes** (KLANG-00341's scaffolding): with key priorities in (70770094), a code tool's Esc stands
  above a panel's, so `nothingInFront()`, its spec rows and the `FrontGrip` helper are removed; Alt+M has no guard.
- `RoundGauge` stays for the start page's dyno run until the step where the Motor glides into its corner; the
  Motor's gauge functions (`comp/gauges.kt`) and the kitchen sink's "today" cells for them go.

## The work

1. `PlayerMiniStats`: the three `KlangGauge`; the knob; no glow parameters.
2. `VolumeKnob` on `KlangKnob`; Alt+V once the action exists.
3. `StartPage`: the tint as `--kl-gauge-glow`.
4. `Motor`: the trace from its own role `ScopeTrace` (following the metal), 28 spectrum columns.
5. Removals: `comp/gauges.kt`, the kitchen sink's "today" gauge and knob cells.
6. Review, a look in the browser.

## Acceptance

- The Motor's gauges and knob look as in the kitchen sink; the knob turns the master volume by drag and by arrows.
- The trace has the rims' metal colour and follows the look; the spectrum has 28 columns.
- The start page's gauges keep their tint.

## Review and checks (2026-10-26)

- **Round 1** (level 2, blind): 1 MAJOR, 2 MINOR, all fixed. The MAJOR: the needles froze short of a steady reading
  (`KlangGauge` smoothed only on changed props; the old `RoundGauge` got a fresh lambda each tick); now the gauge runs
  its own frames. The MINORs: the gauges' hover names (`title`), the idle trace's colour on a look change. Ledger row
  and lesson: none, the MAJOR came from carrying a component over, as `/kraft-knowhow`'s key boundary lesson says.
- **Round 2** (level 3, two phases): clean, 2 MINOR and 6 NIT, all applied: the needle's pace (one step a frame,
  sized by the time, the share kept per 10 ms, as the old Motor moved), the offline row by frames, the KDocs and the
  ticket's stale "blue", one rim inset, the kitchen sink cell's name, a NaN-proof done check; its reconcile's two
  traps (a timestamp kept across a rest: reset per glide, a row; a share of 1 at no elapsed time: holds).
- **Round 3** (level 2, two phases, everything since the first commit and the later role changes): clean, 2 MINOR
  applied (the `/kraft-knowhow` lesson on keys in front rewritten to the priority rule; the following roles take
  `MetalMid`'s default instead of repeating it), 1 NIT rejected (a row's expected points from `Dial.point`: it claims
  timing, and the offline row's literals guard the geometry).
- **Mutation-checked**, each killed for the right reason: the needle without its own frames, hot on the raw
  reading, the offline needle gliding, the timestamp kept across a rest, a role following nothing.
- **Tests:** klangui's 119, green; the app compiles. The app module's own (the router, `spectrumColumns`,
  `rangeLabel`) wait for KLANG-00343.

## History

- 2026-10-26: created and started.
- 2026-10-26: reviewed in three rounds (the third clean), committed.
