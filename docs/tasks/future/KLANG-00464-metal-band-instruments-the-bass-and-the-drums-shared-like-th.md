# KLANG-00464: Metal band instruments: the bass and the drums shared like the guitar rig

> **Status (2026-11-18): IDEA.** 
>
> **Part of:** KLANG-00459 (Metal band instruments: one band, the same instruments in every metal song)

## Why this exists

The band is more than its guitars. For the metal songs to be played "by the same band on the same instruments",
the bass and the drums are shared the way KLANG-00463 (the shared guitar rig) shares the rig.

## What is known

Der Schmetterling has a bass, a kick (`metalKick`) and a snare (`metalSnare`) built in the song, and takes its hats
and shaker from samples.

UNVERIFIED: what Kokon has for the same roles, and how far the two songs' versions differ. Nobody has compared
them for this ticket.

## Decide before implementing

- Which instruments belong to the band, and which stay a song's own.
- Whether KLANG-00460 (the level contract) applies to them as well: a kick and a snare are single sources, not
  chains of parts, so "the same level in and out" may reduce to "a known level out".

## Acceptance

Not written yet: it follows the decisions above and KLANG-00463.

## History

- 2026-11-18: created, from the Der Schmetterling session of 2026-11-17 and 18.
