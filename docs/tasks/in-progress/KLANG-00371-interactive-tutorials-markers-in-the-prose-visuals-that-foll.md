# KLANG-00371: Interactive tutorials: markers in the prose, visuals that follow the player (a light running along the ADSR curve)

> **Status (2026-10-27): IN PROGRESS.** the real curves and The Bend of a Note landed; next two pictures for the loudness and the filter envelope
>
> **Part of:** KLANG-00334 (UI workbench: the Motor-centred layout, tool windows and the first-visit trail)

## Why this exists

The maintainer, 2026-10-27, on the ADSR picture in "The Shape of a Note": "It would be cool to have interactive
elements in the Tutorials. So we would need some "marker" in the markdown that references the interactive content.
Then we could listen for the events of the player and animate each note on the adsr curve... like a bright light
following the curve while the sound plays".

Today a tutorial's visual (`Block.Visual.Adsr`, `AdsrVisual`) is a still picture drawn from the same values as the
code beside it (lint-enforced: picture and code cannot drift); the example player (`PlayableCodeExample`) plays and
knows each sound event as it is scheduled (the highlights in the code use them).

## Decide before implementing

- The marker: in the markdown itself (a fenced block or a short tag such as `{{adsr:example-2}}` that names the
  example it follows), or kept as a block of its own beside the markdown as today (`Block.Visual`), linked to an
  example by its place or a name. A marker in the markdown keeps the prose and the picture together; it needs a
  syntax the curriculum lint and marked both leave alone.
- What follows the player: each note a light that runs along the curve from its start to its release, as long as the
  note sounds, several at once for overlapping notes; dimmed when nothing plays.
- Where the events come from: the player's voice events (`KlangPlaybackSignal.VoicesScheduled`, as the code
  highlights use them), with their start time and their gate; the curve's shape from the same values as today.
- Which other visuals follow (later): a filter's cutoff, a pattern's steps lighting in the cycle, the cycle itself.
- The engine stays the horse (CLAUDE.md, stone): the visual only reads the events the player already sends; no DSP
  of its own.

## Decided (the maintainer, 2026-10-27)

- **A component of its own, fed by a stream of ADSR signals** (the maintainer: "a component for it that expects a
  stream of "Adsr signals" so the using code needs to map something and pass it through the stream. The stream must
  not deduplicate"): it lives in klangui and knows nothing of audio. A signal is one note: its start and gate end
  (wall-clock seconds, as the code highlights compare them), its attack, decay, sustain and release. A
  `StreamSource` set directly passes every value on; a replayed old signal (a new subscriber gets the last value) is
  over already and draws nothing.
- **Any envelope, not only the loudness** ("We have multiple adsr on an event. The main adsr plus filter adsrs"):
  a voice event carries them in `ignitorParams` under `<door>.<stage>` (`adsr.*`, `lpf.*`, `hpf.*`, `bpf.*`,
  `penv.*`, `fm.*`; defaults in `IgnitorDslClassic.kt`). A mapper from a voice event to a signal takes the door
  to read and gives nothing when that envelope is off for the note; two components side by side follow two doors.
- **The picture follows the example right after it**, the pairing the curriculum lint already checks. The tutorial
  page makes a stream per picture and hands it to that example. An explicit name only when a picture has to sit
  apart from its code. `Block.Visual.Adsr` gets the door it follows, the loudness by default.
- **The curve reshapes** to the values that actually play (the reader edits the code and presses Update); before
  the first note it shows the values the tutorial wrote.
- **The light: a point** ("The ones with the dot lights look best", over a lit stretch of the curve and a filling
  area; the sketch is the board "KLANG-00371: the ADSR light" in the Learn design canvas). A white point with a
  blue halo per sounding note, several at once when notes overlap; the plateau is crossed in the time the note is
  held; nothing playing, the curve dims.
- **Scope:** "The Shape of a Note" first; every tutorial with an ADSR picture gets it with the same picture.
- **Every example of Sound Design Basics that writes an envelope shows it** (the maintainer: "make sure that all
  parts of the "Sounds design basics" have the Adsr visualization present", then, for The Four Waveforms and
  Filters, "these do not define adsrs and the concept is only introduced in the next lesson": a picture only
  where its first player writes its envelope).
- **One player per alternative, examples multi-line, in all lessons** ("If there are multiple code block add
  multiple players instead of the comments"); the picture follows every player right after it, and reshapes to
  the one that plays. How to write them: `/tutorial-writing`.
- **Next:** the stages drawn in the engine's real curves (exponential by default, or what `adsrCurves` writes;
  the maintainer chose "The real curves" over the schematic straight lines of KLANG-00145's flux ruling), a new
  lesson on envelope curves right after "The Shape of a Note", and a lesson with the loudness and the filter
  envelope as two pictures side by side (two pictures in a row then both follow the players after them).

## The work

1. klangui: the ADSR component (curve, lights, dim), its signal type; specs.
2. The mapper from a voice event to a signal, per door; the defaults from the slots; specs (JVM where it can).
3. The tutorial page: a stream per ADSR picture, handed to the example after it; `PlayableCodeExample` maps its
   voice events into it.
4. `Block.Visual.Adsr` with its door; the curriculum lint as it is.
5. Specs, mutation checks, the review loop; the maintainer's look in the browser.

## History

- 2026-10-27: created from the maintainer's idea while the Learn pages were built.
- 2026-10-27: decided with the maintainer (a component fed by a stream of signals, any envelope, the point of light); started.
- 2026-10-27: IN PROGRESS. first slice: the ADSR picture follows the player
- 2026-10-27: the light, a picture before every example of Sound Design Basics that writes an envelope, one player per alternative in all 17 lessons, the Copy key; two review rounds (the second clean), mutation-checked.
- 2026-10-27: the stages drawn in the engine's own curves, The Bend of a Note (lesson 6), a lint that every note a picture follows reaches its sustain; two review rounds (the second clean), mutation-checked.
