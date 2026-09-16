# UI look: vintage, a little dirty, with real affordances

Status: **future / reference point, not scheduled.** Created 2026-09-16. Captures a design
conversation with the maintainer so the direction and the sources are not re-derived. Nothing here
is a decision yet; the decision happens on a style-guide page, tuned by eye.

## The brief, in the maintainer's words

"I want it to look somehow vintage and a little bit 'dirty'. Rounded edges not too round, while
still being able to tell buttons from labels. The gauges, icon buttons, and the things we do not
have yet: knobs, sliders, etc."

Fits the standing guideline "design for adults that kids also enjoy (Pixar, not PBS Kids)". What we
have today: dark palette (`--klang-bg-app` #191C22 and friends), gold accent #EBC773, blue accent
#528bff, Semantic UI as the base, `RoundGauge`, icon buttons. The gold already sits close to Moog
cream on black, so the direction builds on the palette rather than replacing it.

## The one rule that keeps it legible

**One light source, from above.**

- Raised things (buttons, knob caps, toggle levers) get a highlight on top and a shadow below.
- Flat things (labels, silkscreen text, section names) get neither. They are ink on the panel.
- Pressed or active things get an inset shadow and an indicator LED next to them.

With that convention, radii can stay small (2 to 4 px, a machined chamfer, never a pill) and
textures can be dirty without anyone mistaking a label for a control. The TR-808 is the proof:
rubber buttons with a bezel and a red LED, labels printed flat beside them.

## Where to look

### Hardware, the primary source

| Source | What it teaches |
|---|---|
| Moog Minimoog, Model D | Black panel, cream silkscreen, walnut cheeks, knurled knobs with a white index line. One warm accent on black, everything else greys. |
| Roland TR-808, TR-909 | Muted rubber buttons, each with a red LED. The strongest answer to "buttons versus labels". |
| Neve 1073, API 512 | Grey-blue or black modules, red, orange and cream knob caps. Few colours, carried by the knobs. |
| Tektronix scopes, Nagra, Studer, Revox | Engraved lettering, VU meters, toggle switches, brushed aluminium. The reference for gauges and meters. |
| EMS VCS3, ARP 2600 | The oddballs. "A little strange" without losing legibility. |
| Eurorack: Make Noise, Bastl, Ciat-Lonbarde, Erica Synths | Make Noise is the closest to "dirty but serious" (grungy black and white, hand-drawn feel). Bastl and Ciat-Lonbarde bring wood, hand lettering and play. Erica: black and red, chunky. |
| Braun, Dieter Rams (SK4, the calculators) | Not dirty, but the reference for restraint and for edges rounded without being soft. |

### Software that did it well

| Source | What it teaches |
|---|---|
| XLN RC-20 Retro Color, AudioThing (Wires, Reels, Speakers) | The deliberately dirty, lo-fi look: grain, worn edges, still readable. |
| u-he Repro-1, Diva | Skeuomorphic panels with visible wear, legible at a glance. |
| Soundtoys Decapitator, EchoBoy | Vintage hardware feel with restrained textures. |
| Arturia V Collection | Photo-real reproductions. Useful to sort details that matter from noise. |
| Reason's rack (flip it around) | Cables and screws on the back. Texture works when it implies a physical object. |
| Bram Bos (Ruismaker), Klevgrand | Small, warm, quirky, kid-friendly for adults. |
| Contrast cases: Vital, Bitwig, Ableton | Flat and clean. Look at them to decide what we do not want. |

### Broader culture and technique

- **"Cassette futurism"** as the search term. Alien (1979), Fallout's Pip-Boy, Signalis, Return of
  the Obra Dinn. Worn control panels with CRT glow.
- **NASA mission control, Soviet control rooms.** Toggle switches, guarded buttons, engraved plates.
- **Labels as physical objects.** Dymo embossed tape, masking tape with marker, paper tags. A Dymo
  strip is a strong device for a section header.
- **Type.** Futura and Helvetica on the panels, Eurostile for the retro-future, OCR-B and
  seven-segment (the DSEG font family) for readouts, IBM Plex Mono for the code side.

## Cheap dirt in CSS

- An SVG `feTurbulence` grain overlay at low opacity over the panel. The grain alone gets most of
  the way there.
- A soft vignette on panels and cards.
- Inset shadows, light from above, for pressed and active states.
- 2 to 4 px radii everywhere a control has an edge.
- Worn edges via a mask on section headers, sparingly.

## The practical next step, when this is picked up

A single style-guide page in the app with every control side by side on one panel: the existing
`RoundGauge` and icon buttons, plus mock knobs, sliders, toggles, LEDs and a VU meter. That page is
where palette, radii and grain get tuned by eye, the way the sound model is tuned by ear. Start from
a mood board of two or three panel directions (Moog cream on black, Neve grey-blue, Make Noise
grunge) if pictures should come before code.

Kraft UI conventions still apply (`/kraft-knowhow`: `.with()` for custom classes, `RoundGauge`
proportions). The global gold `a` rule in `klang.css` carries `!important` and will need a look
once links and buttons get their vintage treatment.
