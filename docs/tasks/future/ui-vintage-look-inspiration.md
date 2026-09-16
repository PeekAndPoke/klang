# UI look: vintage, a little dirty, dense as a game, with real affordances

Status: **future / reference point, not scheduled.** Created 2026-09-16. Captures a design
conversation with the maintainer so the direction and the sources are not re-derived. Nothing here
is a decision yet; the decision happens on a style-guide page, tuned by eye.

## The brief, in the maintainer's words

"I want it to look somehow vintage and a little bit 'dirty'. Rounded edges not too round, while
still being able to tell buttons from labels. The gauges, icon buttons, and the things we do not
have yet: knobs, sliders, etc."

Fits the standing guideline "design for adults that kids also enjoy (Pixar, not PBS Kids)". What we
have today: dark palette (`--klang-bg-app` #191C22 and friends), gold accent #EBC773, blue accent
#528bff, Semantic UI as the base, `RoundGauge`, icon buttons. The gold happens to sit close to Moog
cream on black, which is one possible starting point, not a constraint.

**Nothing in the current design is binding.** Maintainer, 2026-09-16: "the current design is in
no way binding... everything is open for discussion." That covers the palette, the accents, the
dark theme itself, Semantic UI as the base, the gauge proportions, the icon set and the type. A
proposal that throws any of it out is as welcome as one that builds on it. Only the brief above and
the legibility rule below are the fixed points, and even those are the maintainer's to revise.

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

## Second brief, 2026-09-16: dense, game-like, a full DAW in the browser

Maintainer: "i like the idea of computer game aesthetics somehow. We need a design that is very
dense, so that a full DAW can be displayed in the browser. The current editor only frontend is just
a small sub-set of what the product will become. The scripting is the low-level access to the
engine, but i imagine there to be many other (more traditional) layers of editing music."

Density and game aesthetics are not in tension. Density is the constraint that decides the most.

### Games that are dense and readable

| Source | What it teaches |
|---|---|
| Factorio | The gold standard: dense, iconographic, industrial, slightly dirty, legible for hundreds of hours. Small icons, tight grids, tooltips carry the detail. |
| Zachtronics (Shenzhen I/O, TIS-100, Exapunks) | Code as low-level access to hardware, datasheet and manual aesthetics. The closest analogue to "scripting is the low-level door". |
| FTL, Into the Breach, RimWorld, Dwarf Fortress (Steam UI) | Dense panels, warm palettes, pixel precision. |
| EVE Online, Elite Dangerous, Homeworld | Sci-fi HUD density; hierarchy through brightness, not size. |

### Music tools that already are dense games

| Source | What it teaches |
|---|---|
| Trackers: Renoise, Fasttracker II, LSDJ, SunVox, Dirtywave M8, Polyend Tracker | The precedent for dense music editing with a game feel. M8 and LSDJ do a whole workstation on a tiny screen. |
| Elektron sequencers | A full workstation on a grid of LED buttons; pages and parameter locks instead of screen space. |
| Reaper | The densest DAW, fully themeable. Study how it packs a mixer and a timeline. |
| Blender node editor, Unreal Blueprints | How a graph editor and dense inspectors coexist in one window. |

### What density implies

- **Two densities, not one.** Reading surfaces (docs, tutorials, resources) stay spacious. Working
  surfaces (editor, timeline, mixer, patch graph) go to 12 to 13 px type, a 4 px spacing grid,
  16 px icons, a condensed or mono face (IBM Plex Condensed, JetBrains Mono, Iosevka, Berkeley Mono).
- **Semantic UI will not survive the working surfaces.** Its padding and scale are built for
  marketing pages. A DAW surface needs its own compact component kit on Kraft. This is the largest
  single consequence of the brief and the reason the style-guide page should be built compact first.
- **Hierarchy through brightness and weight, not size.** At 12 px there is no room for a type scale;
  games solve it with contrast, colour and position.
- **The dirt scales down.** Grain and vignette survive density; worn edges and Dymo strips do not,
  they become chrome. Keep them for headers on the reading surfaces.

### Layers of editing, and the rule that binds them

Piano roll, step grid, tracker view, arrangement timeline, mixer, patch graph, notation over the
Klangbuch model. Scripting stays the door that reaches everything. The stone rule holds for every
layer: the engine is the horse, a frontend never gets DSP of its own, only seconds cross the wire.
Every layer is a view that maps onto existing engine components. This note is a reference for the
day the UI work starts, not platform or launch planning (sound first, `docs/tasks/_priorities.md`).

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
