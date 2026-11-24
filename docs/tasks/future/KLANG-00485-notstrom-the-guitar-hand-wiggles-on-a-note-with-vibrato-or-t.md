# KLANG-00485: notstrom the guitar hand wiggles on a note with vibrato or tremolo

> **Status (2026-11-24): IDEA.** The maintainer's note for later, 2026-11-24.

## Why this exists

The maintainer (2026-11-24): "when a note has a vibrato or tremolo or both the guitar hand must do slight wiggling
movement while holding the note. The amount of wiggling has to match the amount of tremolo / vibrato".

## What is there today

The band reads a song's events with their time, length, tags, note, gain, sound and pan (`RecordedEvent`, read by
`SongReading` from the playback's cycles); a string player gets them as `Strum`s. Neither carries a vibrato or a
tremolo, so the band does not know which notes have one, nor how much.

## Decide before implementing

- Which hand: the fretting hand on the neck is where a player makes a vibrato; "the guitar hand" may mean that one,
  or the strumming one for a tremolo. The maintainer's to say.
- What "the amount" reads from: the vibrato's depth (semitones) and rate, the tremolo's depth and rate, as the
  voice carries them. They have to reach the band first: two or four more fields on the events it reads.
- Whether the wiggle keeps the vibrato's own rate, or only its depth at a rate that looks right.
- By the direction of KLANG-00480 a move like this is a skill on a member's sheet.

## The work

Not planned yet.

## Acceptance

Not planned yet.

## History

- 2026-11-24: created from the maintainer's words.
