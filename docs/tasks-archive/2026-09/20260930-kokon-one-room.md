# Kokon: one room instead of five

Status: **DONE 2026-09-30** (`cc6e2684`), the same day it was parked: the maintainer asked for "a master
reverb that might feel like a concert hall". Was: parked ("cut the number of different rooms down, go
for a master reverb and maybe some slight dedicated ones per line ... park this for later").

## Why

Kokon (`src/commonMain/kotlin/builtinsongs/Kokon.kt`) gives almost every line its own room: the arp
size 4, the melody 5, the swells 6, the wings 3, the last chord 6. A band plays in one room; five
rooms of different sizes may sound like five places at once, which the ear has to merge, a second
cause of the "strange feeling" besides the reverb that kept each room on its side (fixed 2026-09-30,
[`20260930-stereo-reverb.md`](20260930-stereo-reverb.md)).

## The idea

A master reverb as the shared room (`master(Katalyst(k => k.reverb(...)...))`), and a slight
dedicated reverb only on the lines that need their own depth. A/B by ear against the current mix.

## Related

The reverb models themselves: [`future/reverb-models.md`](../../tasks/future/reverb-models.md). The song's stereo placement was
chosen by ear on 2026-09-30 with the old two-room reverb; check it again once the room changes.

## Outcome

A master reverb is the hall, `reverb(0.25, 7, 4500)` first in the master chain: 2.07 s measured (the size
table in the music-writing reference), warm. The lines play dry into it; the swells keep a slight room of
their own (wet 0.2, size 6). Measured against the version before: the reverb sits a steady 18 to 19 dB
under the dry sound in every part (19 to 25 before, the break almost dry), part levels within 0.1 dB, the
width up to 1 dB wider in the quiet parts, and the arp's attacks unchanged (0.9 to 1.2 dB over what
rings, full mix without the drunk timing).
