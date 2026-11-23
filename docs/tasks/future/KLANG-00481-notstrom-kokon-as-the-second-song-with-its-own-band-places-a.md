# KLANG-00481: notstrom: Kokon as the second song, with its own band, places and a way to switch

> **Status (2026-11-23): IDEA.** the maintainer, 2026-11-23: to wrap up the experiment, a second song with other instruments and other places; a plan and the points to decide
>
> **Part of:** KLANG-00447 (Live act: a band on a stage, played by the song)

## Why this exists

The maintainer, 2026-11-23, to wrap up the experiment of the live act (three of three friends liked it, one a former
metal guitarist: "the stadium-look painted a fat smile on my face"): "I would like to integrate Kokon as the second
song." And what that means at least:

1. "We need a way to switch between songs: in the drawer 'next song' / 'previous song' and showing the current title
   and rpm."
2. "The instrumentation is different in Kokon, so I would say, when the instrument changes for a musician, we
   'teleport' the current away and then 'teleport' the new one in."
3. "The musicians will be in other places on the stage maybe even sitting, so chairs would need to be teleported in
   as well or they sit in cross-legged position except for Wucht ... this would also mean that Rune cannot stroll
   around." (Today it is Sirene who strolls; which of the two is meant is asked below.)

And on the same day, of the visit spots that are written for this one band: "let us fix this later ... I want to wrap
up the experiment with a second song, different instruments, different positions in the stage, so we anyway need a new
way to represent all of this."

## What is there today (surveyed 2026-11-23, by reading the code)

- **Kokon** (`BuiltInSongs.kokon`): 19 rpm (a bar of 3.16 s; Der Schmetterling has 35 rpm, a bar of 1.71 s), 66
  cycles, about three and a half minutes, then it begins again. Ten guitar voices in four sounds (clean, bright, deep,
  heavy), a bass and drums; no marimba, no trumpet. Bass and drums play only in the second heavy block and at the end
  of the lift. Its melodies are Der Schmetterling's lead.
- **Kokon has no tags.** The band reads the song by tags, and what has none never reaches it: with Kokon as it is the
  band would stand still.
- **One song is written into the app:** `SHOW_SONG` and `SHOW_SCORE` are constants; the drawer knows no song; title
  and tempo are shown nowhere while a show runs.
- **A song's band is its score** (`Score`: cast, plot, eye contact): data, one so far. But the cast has a fixed shape
  (exactly one drummer, one marimba player, one trumpet player who strolls, and any number of strung players), and the
  plot must hold all six members.
- **A member's sheet is tied to their instrument:** Wucht, Sirene and Rune have no bearing for an instrument on a
  strap, and skills such as the stick twirl belong to a drummer.
- **Only the drummer sits** (on the kit's stool, with a pose of his own). There is no chair, no cross-legged pose, and
  nobody ever leaves or appears: every member walks in once.
- **What knows where everyone stands:** the camera's own looks at Wucht and Rune and its arcs, the stroll's visit
  spots, the pairs of the eye contact, the riser (fixed in the stage's middle), and four specs that copy the places.
- **A show is built once:** stage, band, camera and clock are made when the component mounts, from one show and one
  score; "a new show needs a new component". A show that is left gives everything up, the song stops.

## A proposal (Klang-Research, 2026-11-23; nothing of it is decided)

In steps, each one something to look at:

1. **A programme of songs, and the drawer.** The app holds a list (song and score together) and which one plays; the
   drawer shows the title and the tempo with "previous song" and "next song". A change of song ends the running show
   and begins the next: a new component, as the code asks for today. With step 1 alone Kokon plays and the band
   stands still, on Der Schmetterling's places.
2. **Kokon's tags and Kokon's score.** The song's voices get tags (the song's file is not this session's to edit:
   see the points below); a second score names who plays which voices and where they are.
3. **A cast of any shape.** A score seats members at instruments freely: no marimba, no trumpet, four guitars. A
   member who takes another instrument needs what it asks of their sheet (a bearing for a strap, a guitar of their
   own); a sheet's skills that belong to another instrument rest.
4. **Sitting.** A cross-legged pose for a strung player (the instrument on the lap), as data of the plot: who sits,
   who stands. Whoever sits does not stroll. Wucht keeps his stool.
5. **Teleporting.** A member appears and vanishes by an effect instead of walking: at the change of song the band of
   the old song teleports away, the stage stays dark for the moment the new show needs, and the new band teleports
   in at its places with its instruments. Whether the whole stage is built anew behind that moment (the plain way,
   proposed) or only the band is swapped is a matter of how it looks, not of what the viewer is told.
6. **The camera and the rest that knows the places:** looks at someone sitting, no arc that assumes a marimba, visit
   spots and pairs from the score.

Step 1 and 2 are small. Step 3 and 4 are the work. Step 6 is where "a new way to represent all of this" is decided:
what a score has to say so that nothing else names a member or a place.

## Decided (the maintainer, 2026-11-23)

1. **Nobody strolls in Kokon.** "I would like to try a different setting on the stage where they sit in a closer half
   circle, since this is a slow song. So no-one can stroll."
2. **Who plays what.** "I would give Sirene a guitar too here. Also it would be nice if the dialogue between soar and
   spin would be played in turns by Sirene and Rune. Also chug and wings play at the same time, so they need to be
   distributed between Skalpell and Klaue. Not sure what to do with beat, but I think no-one needs to play it."
   Taken from that, the rest this session's choice until the maintainer says otherwise:

   | Member | Plays | Voices (tags) |
   |---|---|---|
   | Sirene | guitar, the melody | sing, soar, chime |
   | Rune | guitar, the thread | spin, strum |
   | Skalpell | guitar | wings, swell |
   | Klaue | guitar | chug, strike |
   | Beben | bass | bass |
   | Wucht | drums | kick, snare, hats |
   | nobody | | beat |

3. **The tags are in the song** (`c57345f83`: every voice carries a tag with its own name).
4. **Wucht and Beben "meditate"** while they have nothing to play (the first two minutes).
5. **Cross-legged, and they walk.** "They would need to walk to their new positions and sit ... or get up when
   changing to the next song. So basically we need a model for where everyone's home-position is in each song and a
   planner for in between the songs."
6. **Only the instruments teleport, not the members.** "On the teleport ... I would only teleport the instruments not
   the members."
7. **Step by step:** "we start with a rough draft for the transition and refine."

What follows from 5 and 6: a change of song does not end the show and build another (step 1's first way, which
stays as the rough draft until the next is there). The stage stays; the members get up, walk from the old song's
homes to the new song's and sit or stand there; their instruments vanish and the new ones appear. So the band has
to outlive its song: a member's home, posture and instrument are the song's (the score's), and something plans the
way between two scores.

## The work, as it stands

1. Done: the programme of songs and the drawer (`2ec18953d`); Kokon's tags (`c57345f83`).
2. **A cast of any shape:** a score seats members at instruments freely (no marimba, no trumpet, four guitars); Sirene
   and Rune get a bearing and a guitar. Then Kokon's score with its places, everyone standing at first: the band plays
   Kokon.
3. **Sitting:** the cross-legged pose with the instrument on the lap, and "meditating" for whoever has nothing to
   play; a home says whether its member sits.
4. **The way between two songs, a rough draft:** the members get up, walk to their new homes, sit; the instruments
   teleport. First as plainly as it can be done, then refined.
5. **The camera and the rest that knows the places** read them from the score.

Places to choose from: `prototypes/band/ignored/previews/kokon/places-ABC.png` (three half circles, from above).

## Decide before implementing (all answered above; kept for the record)

1. **Who strolls, and who may not?** "Rune cannot stroll around": today Sirene strolls, with her trumpet. Kokon has no
   trumpet. Is it Sirene who then stays in her place, or does Rune get a part of her own?
2. **Who plays what in Kokon?** Ten guitar voices, a bass, drums, and six members. A first idea: Skalpell the bright
   ones (sing, soar, swell), Klaue the heavy ones (chug, wings, strike), Rune the clean ones (spin, strum, chime),
   Sirene the deep one (beat), Beben the bass, Wucht the drums. So Sirene and Rune change instrument, and they are the
   two who teleport away and come back with a guitar.
3. **Who adds the tags to Kokon?** The song's file belongs to the songs' own work (another session has worked on its
   hi-hats). Either the maintainer or that session adds a tag to each voice, or this session is told it may.
4. **Wucht and Beben before their part.** Bass and drums are silent for the first two minutes. Do they sit there and
   wait (with idle moves), or do they teleport in when the heavy block with the drums begins?
5. **Sitting: cross-legged or chairs, and who?** Cross-legged needs no chair to model and none to teleport; it needs
   one new pose. All four guitarists, or only some?
6. **The places.** A plot for Kokon is needed: for example the four guitarists sitting in a shallow arc at the front,
   Beben standing behind them to one side, Wucht on his riser. A sketch can be made as a still to choose from.
7. **How the change of song looks:** the plain way (the old band teleports away, a dark moment, the new band teleports
   in) or something more.

## The work

To be planned once the points are decided; step 1 can begin before.

## Acceptance

- In the drawer the title and the tempo of the song that plays, and "previous song" and "next song".
- Kokon plays with its own band: every voice has a member whose hands play it, at Kokon's places, sitting where the
  score says so.
- A change of song and back leaves nothing behind (no context, no listener, no sound).
- Der Schmetterling's show is as it was.

## History

- 2026-11-23: created.
- 2026-11-23: the maintainer's decisions; step 1 (programme and drawer) and the tags done.
