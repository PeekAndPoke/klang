# KLANG-00456: Live act: its structure: members, instruments, the stage plot, and what is tied to the song

> **Status (2026-11-17): IN PROGRESS.** 
>
> **Part of:** KLANG-00447 (Live act: a band on a stage, played by the song)

## Why this exists

The live act grew fast (KLANG-00446) and its code is cut along the lines it grew by. The maintainer asked for a
review round (2026-11-17): "how can we categorize the things we now have, or how can we improve on the factorization
we currently have?" What was found:

- `band/common` mixes four kinds of thing: the skeleton and how it moves, the players, the dress, and each
  instrument's geometry inside its player's file (630 to 700 lines each).
- A member of the band is scattered over eight places: the figure script, five maps in `Notstrom.kt`, the camera's
  looks, a parameter of the string player.
- Audience, terraces and sky sit in `stage/` with the floor, the wall and the fog.
- The wall's ornament lives with the figures' cloth; the sound files lie loose in the root; some thirty page
  switches are read wherever they are needed and listed nowhere.
- What is tied to the one song ("Der Schmetterling") is mixed in with what any song would need.

## Decide before implementing

Decided by the maintainer, 2026-11-17:

1. All four steps: stage and theatre apart (with the wall's ornament and the sound files moved); a sheet per
   member; a folder per instrument; one list of the page's switches.
2. A member's sheet holds only what is the character's own: "name, figure, character, extras ... so basically only
   things intrinsic to the character". Not in it: "everything song related, which instrument / part they play".
   Where they stand goes "into a different thing, like Bühnenaufstellung ... for now hardcoded".
3. "I would model each instrument individually, so split guitar and bass please." Each instrument has "its own
   folder, with geometry, and planning code, also announcing what the musician needs to equip, like the mallets on
   the marimba".
4. "Go as far as you can. Things that are currently hardcoded against Schmetterling need to be moved into a
   refactorme or similar place. This would then be a good starting point for thinking about how to decouple the
   whole live act from songs and how to bridge the gap."

Open, for after this ticket: how a song says which of its parts is which instrument, and who plays it (the bridge
between a song and the stage). `refactorme` is the list of what that bridge has to carry.

## The work

Every step is a move: the same stills before and after, compared point by point, and all specs.

1. Packages: `sound/` (the song's loudness and sharpness), `stage/` (floor, wall with its ornament, riser, lamps,
   fog) and `theatre/` (audience, terraces, sky).
2. The page's switches as one list, each with what it does; every read goes through it.
3. Members: a sheet each (name, figure, dress, character, extras) in a file of its own; the band is its six
   sheets.
4. The stage plot (the Bühnenaufstellung): who stands where and how they walk in; hardcoded.
5. Instruments: a folder each (drums, marimba, guitar, bass, trumpet) with the instrument itself, the plan of how
   it is played (plain functions with specs, as far as they can be drawn out: KLANG-00451), the player's pose, and
   what the musician holds for it.
6. `refactorme/`: what is tied to "Der Schmetterling": which member plays which part on which instrument, the
   song's part names, the test notes, and whatever else turns up.

## Acceptance

The stills are the same as before the ticket, all specs are green, and `refactorme/` names every place the live act
knows the one song.

## History

- 2026-11-17: created.
- 2026-11-17: steps 1 and 2 done. `sound/` (Music, Energy, LiveLoudness, Sharpness), `theatre/` (Crowd, CrowdPlaces, NightSky, Sky) and `stage/Wall.kt` (out of the cloth patterns' file); the page's 27 switches are one list (`Switch`, with a spec that pins the keys), and a switch can no longer be read without being on it. All twelve stills the same point for point, all specs green.
- 2026-11-17: steps 3 to 6 done, as moves and cuts with no change of behaviour (14 stills the same point for point after each part; reviewed against the old files line by line, clean). `band/members/` (a sheet each: dress, character, bearing, extras; Rune's snakes and Klaue's glance are extras of their sheets), `band/StagePlot.kt` (places, walk-in), `band/figure/`, `band/dress/`, `band/instruments/` with a folder each for drums, marimba, guitar, bass and trumpet and `strings/` for what guitar and bass share, and `refactorme/` (the cast, the song's tags, the test notes). Left where they are, tuned on the song but speaking of bars and players: the choreography's and the light plan's bar numbers, the two bars of count-in, who plays seldom, the trumpet's phrase timings.
