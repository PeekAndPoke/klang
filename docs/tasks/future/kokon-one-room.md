# Kokon: one room instead of five

Status: **future, song mix, parked by the maintainer 2026-09-30** ("cut the number of different rooms
down, go for a master reverb and maybe some slight dedicated ones per line ... park this for later").

## Why

Kokon (`src/commonMain/kotlin/builtinsongs/Kokon.kt`) gives almost every line its own room: the arp
size 4, the melody 5, the swells 6, the wings 3, the last chord 6. A band plays in one room; five
rooms of different sizes may sound like five places at once, which the ear has to merge, a second
cause of the "strange feeling" besides the reverb that kept each room on its side (fixed 2026-09-30,
[`../../tasks-archive/2026-09/20260930-stereo-reverb.md`](../../tasks-archive/2026-09/20260930-stereo-reverb.md)).

## The idea

A master reverb as the shared room (`master(Katalyst(k => k.reverb(...)...))`), and a slight
dedicated reverb only on the lines that need their own depth. A/B by ear against the current mix.

## Related

The reverb models themselves: [`reverb-models.md`](reverb-models.md). The song's stereo placement was
chosen by ear on 2026-09-30 with the old two-room reverb; check it again once the room changes.
