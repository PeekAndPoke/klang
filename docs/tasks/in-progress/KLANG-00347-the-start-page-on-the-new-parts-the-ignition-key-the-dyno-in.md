# KLANG-00347: The start page on the new parts: the ignition key, the dyno in the Motor's gauges, the result panel

> **Status (2026-10-26): IN PROGRESS.** The maintainer: "Can we continue with the StartPage instead?"; "But before you
> do let us sharpen the StartPage design"
>
> **Part of:** KLANG-00334 (UI workbench: the Motor-centred layout, tool windows and the first-visit trail)

## Why this exists

The start page is the last screen still drawn the old way: a Fomantic power button, a text boot screen, the old
`RoundGauge` with its own 16 ms ticker, a frosted card with a white Fomantic button. The epic's board G imagines it as
starting the Motor (the ignition, the dyno run, the Motor settling into its corner). This step does the first two on
the new parts; the glide into the corner needs one shell for every page (epic item 25) and comes after it.

## Today (read from `StartPage.kt`)

Five screens over the lamp-lit `MotorBackground` plate, KLANGMOTOR engraved in it:

1. **Off:** the plate at 7%, a round power button.
2. **Browser check:** on a phone or outside Chrome only; a Fomantic warning card, "Continue Anyway".
3. **Booting:** "..:: Starting Motor ::.." for at least 2.5 s while the worklet loads and the plate prepares.
4. **Dyno test:** the lamp powers on, "Dynamometer Test", 3 rounds; `RoundGauge`s for Round (70), CPU (90) and
   Active Voices (70).
5. **Result:** a frosted card (the tier from God-Tier to Struggling, its message, the safe voices, "Average from n
   test runs"), a white "Make Music Now" button; under it the Motor's row, the scope and the spectrum. The click plays
   a jingle, then opens the first built-in song.

## Decided (the maintainer, 2026-10-26)

- **The ignition key** replaces the power button and the boot screen: board G's silver knob cap without a ring, OFF,
  ON and START printed around it, the gold "try me" beacon on it while it waits. **One click turns it** (also Enter
  or Space): the key goes to ON, a **glow lamp** burns while the Motor loads (the worklet, the plate), like a diesel's
  glow plug, in **amber** (`LampWarning`; gold stays "try me" only); when it goes out the key springs to START by
  itself and the dyno run begins. The click is the one the browser needs before it plays audio.
- **The dyno run shows the Motor's own three gauges**, big: cylinders, heat, voices, as `KlangGauge`, **130 px in the
  middle and 109 px beside it** (the Motor's 62 to 52, scaled), so the glide later is a pure shrink. Heat is the
  render's load (the benchmark's real-time factor), voices the active voices. The round count (1 of 3) is three lamps.
- **The benchmark spreads its voices over the cylinders** (voice n on cylinder n mod 16), so the cylinders gauge
  sweeps like the others, and the load is closer to a real song's (a reverb per cylinder). The measured voice counts
  move, so the tiers get a fresh look by measurement, reported to the maintainer before they change.
- **The result keeps today's content on klangui parts**: a riveted `KlangPanel`, the tier as a lamp in its colour,
  its message (the jokes stay), the safe voices, a `KlangButton`; the jingle, then the first song, as today.
- **The browser warning** becomes a klangui dialog with rivets instead of the Fomantic card (proposed, not objected).

## The work

1. **klangui: the ignition key** (`KlangIgnition`, a new part): the cap, the positions, the beacon, the glow lamp, one
   click to turn; its spec; a section in the kitchen sink.
2. **The benchmark** spreads its voices over the cylinders and reports them; measured before and after, the tier
   thresholds looked at with the maintainer.
3. **The start page** on the new parts: the key, the dyno on `KlangGauge` (Ultra's `smooth()` for the needles, the
   rounds as lamps), the result panel, the warning dialog. `RoundGauge` goes when nothing uses it any more.
4. The review loop; the maintainer's look in the browser.

## Not in this step

- The Motor gliding into its corner and one shell for every page (epic items 25 and board G's third part).
- The first-visit trail.

## Acceptance

- The start page runs key, glow lamp, dyno and result on the new parts, by mouse and by keyboard.
- The specs of the new part green and mutation-checked; the review loop ends clean.

## Where it stands (parked 2026-10-26 for the IntelliJ restart)

- **Done, uncommitted, not reviewed yet:** item 1, the key: `klangui/comp/KlangIgnition.kt`, its styles in
  `KlangControlStyles.kt` (ignition, ignitionCap, ignitionTurn, ignitionLabel, ignitionLabelCurrent, the
  reduce-motion lines), `KlangIgnitionSpec` (4 rows, green), the kitchen sink cell in `UiKitchenSinkPage.kt` (a live
  key, then OFF, ON, START at 100 px). The maintainer has not looked at the key yet.
- **In the same uncommitted styles file:** the tiles' text at the top (`tile`: `justify-content: flex-start`), the
  maintainer confirmed it in the browser ("Ok now it works").
- **Next:** the maintainer's look at the key; then item 2 (the benchmark's voices over 16 cylinders, measured before
  and after, the tiers to the maintainer before they change); then item 3; the review loop and the mutation checks
  for the key and the tiles together.
- **Open beside it:** the IDE exclusions (`build.gradle.kts`, commits cd6bf5b2 and e6a84dbc): after the maintainer's
  Gradle sync, check with the IDE's file search that `.build-claude/`, `.gradle-claude/`, `tmp/` and
  `buildSrc/.build-claude/` are excluded (the last one may need `buildSrc/build.gradle.kts`); only then tell Mission
  Control it belongs in the project template and in every project (the maintainer's word). `sprudel/jsbridge/tmp`
  is not excluded; offered.

## History

- 2026-10-26: created; the design sharpened with the maintainer (the key, the gauges, the cylinders, the lamp, the
  sizes, the result).
