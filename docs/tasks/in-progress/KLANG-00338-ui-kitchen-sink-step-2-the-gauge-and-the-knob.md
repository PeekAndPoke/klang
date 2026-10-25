# KLANG-00338: UI kitchen sink, step 2: the gauge and the knob

> **Status (2026-10-25): IN PROGRESS.** Step 2 of the UI workbench, the maintainer's go 2026-10-25
>
> **Part of:** KLANG-00334 (UI workbench)

## Why this exists

Step 2 of the UI workbench after KLANG-00337 (the theme roles, lamp, button, riveted panel). The maintainer asked for
gauges with icons, knobs with a label on top or below or an icon, and roundish surfaces (KLANG-00334 items 16 to
18); the design is on the canvas, board "Kitchen sink", sections "Gauges" and "Knobs".

## Decide before implementing

- **The knob's pointer is not gold.** The canvas drew it gold; gold means "try me" and nothing else (KLANG-00337).
  The pointer is the cap's ink: white on a dark cap, the metal ink on a silver one.
- **The Motor keeps today's gauges and volume knob in this step.** Switching it over is its own step, after the
  maintainer has seen the new parts.
- **The knob turns a position from 0 to 1.** The caller maps it to its own value (the volume's loudness scale, a
  cutoff); the knob does not know units.

## The work

1. **Roles** for what the gauge needs and no role covers: the arc, the ticks, the glow. The needle reads the strong
   ink, the face the sunken ground, the rim and the hub the metal, the hot zone the critical lamp.
2. **`KlangGauge`** (`klangui/comp/`): a silver rim, a face with eleven ticks and a red zone from `hotFrom`, the arc,
   the needle, an icon or a text label, the value text. Offline (no value): no glow, the needle at rest. Online: the
   glow. Hot (the value in the red zone): the arc and the glow turn critical. The needle is smoothed over new values
   as `RoundGauge` does. A `meter` for screen readers.
3. **`KlangKnob`** (`klangui/comp/`): a cap (dark or silver, domed), a pointer, or an icon on the cap with the
   pointer as a dot at the rim; an optional value ring; a label on top or below; the value text. Turned by dragging
   up and down (pointer capture, as `VolumeKnob`), and by the keys a `slider` takes (arrows, Home, End). Today's
   `VolumeKnob` becomes a preset of it when the Motor switches over; until then the drag code lives in both.
4. **The page**: a gauge section (the Motor's three with icons, the states, the sizes) and a knob section (the
   label positions, the icon cap, the finishes, a knob that drives a gauge), beside today's parts.
5. **Tests** (`klangui/src/jsTest/kotlin/comp/`, new files): the gauge's states, the needle's direction at the ends
   and the middle, the red zone, the smoothing; the knob's drag, keys, coercion, its variants. The review loop,
   mutation checks (light tier).

## Acceptance

- The page shows every state of the gauge and every variant of the knob, beside today's parts.
- A knob is usable by mouse, finger and keyboard, and announced as a slider; a gauge is announced as a meter.
- Specs green and mutation-checked; the review loop ends on a clean round.

## The maintainer's look decisions while it was built (2026-10-25)

- Save space: the knob's label sits against the dial, the value right under the label ("the hz should be right
  under the label"), and a knob can carry its label on top and its value below.
- The gauge's icon: smaller (13 % of the size, a text label 11 %) and lower, tried at 36 % ("too low") and settled at
  32 % ("yes good").
- The icon on a knob's cap: smaller (22 %), so the dot at the rim keeps its room.
- Focus on a knob: a ring, as before, but thin so it does not reach the label; a coloured pointer instead was tried
  and was too faint. A 1 px outline looked pixelated in Chrome on Linux (a round outline is drawn without
  anti-aliasing), so the ring is a 1.5 px shadow on the dial's edge, with a transparent outline for forced colours.

## Review and checks (2026-10-25)

- **Round 1** (level 1, blind): 1 MAJOR, 5 MINOR. The MAJOR: one NaN or infinite reading latched the gauge's needle
  (fixed: a non-finite reading counts as none; ledger row, the lesson in `/kraft-knowhow`). MINORs fixed: going
  offline snaps the needle to rest, no red zone on a range without a span or at a NaN hotFrom, the knob's announced
  value rounded, the offline meter's value, a wrong cell name.
- **Round 2** (level 2, two phases): clean; every round 1 fix holds. Its MINORs applied as one batch without a
  further round: hot decided in the range's units (a hotFrom outside the range), the announced value held inside
  the range, a literal expected arc in the knob spec, a long line, two KDoc slips.
- **Found on the way:** today's `RoundGauge` took the previous props in `onNextProps` (Kraft hands new, old), so the
  Motor's needles followed one update behind. Fixed (one line); the Motor's look is otherwise unchanged.
- **Mutation-checked**, each killed for the right reason: the arc's large-arc flag, the hot boundary, a NaN reading
  passing, offline keeping the needle, the props order swapped, a zero span turning hot, the drag inverted, ArrowUp
  turning down, the announced value truncated, an icon keeping the pointer, the caption order, hot by clamped
  fractions, the announced value unclamped.
- **Tests:** klangui's 59, the nine specs of steps 1 and 2 among them, green; the app compiles.

## History

- 2026-10-25: created and started.
- 2026-10-25: built, reviewed (two rounds, the second clean), mutation-checked, committed. Open: the maintainer's look
  at the thin focus ring.
