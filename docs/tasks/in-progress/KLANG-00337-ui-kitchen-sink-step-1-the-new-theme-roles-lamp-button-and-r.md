# KLANG-00337: UI kitchen sink, step 1: the new theme roles, lamp, button and riveted panel

> **Status (2026-10-25): IN PROGRESS.** Step 1 of the UI workbench, started with the maintainer 2026-10-25
>
> **Part of:** KLANG-00334 (UI workbench)

## Why this exists

The maintainer, 2026-10-25: start the UI work with a `/debug/ui-kitchen-sink` page that demos every element, each
element its own Kraft component, every element represented in the theme, and a theme users can change and share
(KLANG-00334 items 24 to 28). The design canvas is `docs/design/2026-10-25-ui-workbench-canvas/`.

## Decide before implementing

- **Names** (proposed, the maintainer has not answered yet; renaming later is cheap): CSS variables `--kl-<role>`,
  the roles an enum `KlangRole`, the resolved theme `KlangLook`, its layers `KlangLookLayers`.
- **Gold means "try me" and nothing else.** The canvas drew the active drawer button's lamp gold too; the code
  gives "on" its own colour (`--kl-lamp-on`), so gold stays the one signal of the trail.

## The work

1. **Theme roles** (`klangui/feel/`): `KlangRole` (each role: its CSS variable, its kind, colour or length, and its
   default); `KlangLook` (the resolved values, readable by role or by variable name); `KlangLookLayers` (the stack:
   the defaults, then the user's layer, a song's, a script's; a higher layer wins, clearing one restores the one
   below). A value a layer brings is checked against its role's kind (a hex colour, or a length in px) and dropped
   when it does not fit: a shared theme is data and can never carry CSS text or a URL. `KlangTheme` applies the
   resolved look to `:root` as CSS variables and publishes it as a stream. The old `--klang-*` variables stay as they
   are in this step.
2. **Styles**: one stylesheet of component classes that read only `var(--kl-...)`, so a theme change repaints
   without a re-render. Derived shades (silver, domed, pressed) come from the roles through `color-mix()` and
   gradients, never as stored colours. The beacon pulse respects "reduce motion".
3. **Components** (`klangui/comp/`): `KlangLamp` (off, on, try, warning, critical), `KlangButton` (flat or domed,
   dark or silver, rectangle, pill or round; raised or pressed as a toggle; enabled, disabled or "later"; an
   optional lamp; the beacon), `KlangPanel` (the riveted frame of every popup).
4. **The page** `/debug/ui-kitchen-sink` (`src/jsMain/kotlin/pages/UiKitchenSinkPage.kt`): the theme's roles as
   swatches, then the components in the canvas's order and states, then today's components for comparison
   (`RoundGauge`, `VolumeKnob`, `LcdDisplay`, `YesNoModal`).
5. **Tests** (`klangui/src/jsTest/`, new files only; the existing specs there belong to KLANG-00325): the layers
   and the value check as pure specs, the theme writing `:root`, each component's states in `TestBed`. Then the
   review loop with mutation checks (light tier).

## Acceptance

- The page shows every role and every state of lamp, button and panel, and today's components beside them.
- Changing a role at run time repaints the components without a re-render.
- A layer value that is not a colour or a px length (for its role) is ignored.
- Specs green and mutation-checked; the review loop ends on a clean round.

## Review and checks (2026-10-25)

- **Round 1** (level 1, blind): 1 MAJOR, 8 MINOR. The MAJOR: the "reduce motion" rules lost to the main sheet,
  which mounts a frame later (fixed: one class more; ledger row in `/review-loop`'s `escape-ledger.md`, the lesson
  in `/kraft-knowhow`). MINORs fixed: the beacon wiped the cap's shadows (now a `::after` ring), gold headings on
  the page, a bypass of the value check (private constructor), the unused button `title`, the panel's `rivets`
  opt-out, a redundant redraw, two coverage gaps. Rejected: `KlangRole.ofCssVar` as surplus (the maintainer asked
  for roles by name, KLANG-00334 item 29).
- **Round 2** (level 2, two phases): clean; every round 1 fix holds. Two MINORs applied as one batch without a
  further round: a computed-style check of the beacon ring, and the reduce-motion spec comparing weights against
  the rules it stills instead of copying the selector text.
- **Mutation-checked** (light tier plus the two regression guards), each killed for the right reason: the layer
  order reversed; a negative px length accepted; `initialize()` skipping the look; the beacon on a disabled
  button; the On lamp drawing the Try class; a missing rivet; the beacon's and the lamp's stilling rule losing
  their extra class; the ring without `content`.
- **Tests:** 28 in six specs, green; the app compiles.

## History

- 2026-10-25: created and started.
- 2026-10-25: built, reviewed (two rounds, the second clean), mutation-checked, committed. Open: the maintainer's
  look at the page, and the names (`--kl-`, `KlangLook`).
