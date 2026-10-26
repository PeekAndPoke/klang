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
- **The benchmark fills the cylinders along its ramp** (voice n on cylinder min(n / 10, 15), a new cylinder every 10
  voices, all 16 at 160), so the cylinders gauge climbs with the voices, and the load is closer to a real song's (a
  reverb per cylinder). A first try spread the voices round robin over 16 from the start: the gauge pinned at 16
  (round 1 of the review). Measured in headless Chrome, interleaved: about 0.84 of the voices on one cylinder; the
  tier thresholds are 0.85 of the old ones, 300, 265, 230, 195, 160, 130 (the maintainer: "The tier threshold are
  good").
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

- The Motor gliding into its corner: dropped (the maintainer, 2026-10-26: "We will not do the thing that the motor
  flies to the lower corner ... that would be over the top"). One shell for every page (epic item 25) stays open for
  its other reasons.
- The first-visit trail.

## Acceptance

- The start page runs key, glow lamp, dyno and result on the new parts, by mouse and by keyboard.
- The specs of the new part green and mutation-checked; the review loop ends clean.

## What the step became (2026-10-26, the maintainer's look by eye)

- The key at 130 px, its positions further out; under it "Turn the key to start the Motor", the spinner beside
  "Warming up", and the version stamp (only on this step).
- From START on, one layout that stays: the Motor's three gauges (109, 130, 109), a panel in the dialog's look with
  the test (spinner, round lamps, live voices) and then the tier and the average, "Make Music Now" in its key row
  (silver, 18 px, waiting until the test is done). The key fades out while the run fades in (0.8 s). The Motor row,
  scope and spectrum at the bottom are gone.
- The background: the light stands on the centre, shines in the look's metal and flickers slowly; one panel centred;
  KLANGMOTOR lower (25 % of the height); the plate in the look's ground, built at once and shown in a dim standby
  light while the key waits; black behind it; a circular vignette to black at the edges. The dead scan and the random
  light colour are gone.
- New parts: `KlangSpinner`; a fine grain on every domed cap (the maintainer: "it could use a little noise").

## Review and checks (2026-10-26)

- **Key, round 1** (level 1): clean, 3 MINOR and 1 NIT applied (b32fb099).
- **Start page, round 1** (level 1): 1 MAJOR (the cylinders gauge pinned at 16, fixed by filling along the ramp, the
  maintainer's choice), 2 MINOR (the tier lamp: the maintainer kept one blue; a KDoc reworded), 3 NIT, fixed.
- **Start page, round 2** (level 2, two phases): clean; 4 MINOR applied (the plate's colour taken raw like the canvas
  textures, three decoded it as sRGB and made it ten times darker; no light swell over the disabled key; the
  spinner's label as hidden text in the status; this ticket's decision record), 5 NIT applied (stale comments, an
  import, the dead scan, the panel's height for the longest tier); the title halo after a live look change left (no
  look picker on the start page).
- **Mutation-checked:** the key 7 mutants, the spinner 3, all killed for the right reason.
- **Tests:** klangui 122 green; the app compiles.

## History

- 2026-10-26: created; the design sharpened with the maintainer (the key, the gauges, the cylinders, the lamp, the
  sizes, the result).
- 2026-10-26: the key committed (b32fb099); the start page tuned with the maintainer, reviewed in two rounds (the
  second clean), committed.
