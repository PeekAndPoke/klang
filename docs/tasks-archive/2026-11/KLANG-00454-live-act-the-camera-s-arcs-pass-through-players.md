# KLANG-00454: Live act: the camera's arcs pass through players

> **Status (2026-11-18): DONE.** Each arc set for the stage plot (Arcs.kt: a part of the round and a distance of its own for Wucht, Rune, Beben, Skalpell); the camera and its line of sight stay clear of every other place, and the drummer's arc cuts back to the total. LooksSpec holds the arcs against the places. Not covered: the frontwoman's strolls.
>
> **Part of:** KLANG-00447 (Live act: a band on a stage, played by the song)

## Why this exists

Since 2026-11-17 a look at one player keeps its camera 1.3 m from where the others stand (`clearOf`). The arcs round
a player (`arcBy` in `Choreography.kt`) are not covered, and some pass through other players (worked out from the
places and the arcs by the reviewer, not yet seen in a picture):

- the arc round Wucht ends 7 cm from Beben's place, at the height of his head, and the way back starts there;
- Rune's arc starts 13 cm from Sirene's place;
- Beben's arc passes 0.39 m from Sirene's place.

About one round in five is an arc.

## Decide before implementing

- How: shorter arcs, arcs further out, or arcs of another shape. Pushing an arc's camera away from a player it
  passes, as the looks are pushed, would swing it half round that player.

## The work

1. Pictures of the three arcs at the moments named, to see what the audience sees.
2. Reshape the arcs so every point of them is clear of every other player's place.
3. A spec over the arcs against the places, a check for the future. It needs the arcs and the places as plain data
   in `commonMain`.

## Acceptance

No arc comes nearer than the looks' clearing to a player it does not go round, and the spec says so.

## History

- 2026-11-17: created.
- 2026-11-18: DONE. Each arc set for the stage plot (Arcs.kt: a part of the round and a distance of its own for Wucht, Rune, Beben, Skalpell); the camera and its line of sight stay clear of every other place, and the drummer's arc cuts back to the total. LooksSpec holds the arcs against the places. Not covered: the frontwoman's strolls.
