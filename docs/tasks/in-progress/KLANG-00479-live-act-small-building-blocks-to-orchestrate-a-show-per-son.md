# KLANG-00479: Live act: small building blocks to orchestrate a show per song

> **Status (2026-11-21): IN PROGRESS.** The four decisions are taken; step 1 (the song and the cast leave the code) begins.
>
> **Part of:** KLANG-00447 (Live act: a band on a stage, played by the song)

## Why this exists

The maintainer, 2026-11-21: "I want to build a set of small building blocks for everything we have here, to be able
to orchestrate these per song so we can generate different moods for different songs." And: "make a plan on how to
factor all of this into small reusable units, especially the things that can be 'equipped' as skills. While also
keeping an open eye for future additions and interdependencies between the characters, their skills, the camera".

Today the live act plays one song with one band on one stage, and it does that well. Most of what it does is already
a pure function of the show's time, the seed and the song. But what a member, the camera and the stage do is
configured in a dozen different styles, and several places know this one song and this one band by name. This plan
turns what exists into one small vocabulary, step by step, with the show's picture unchanged after every step.

## What we have (the inventory, in short)

The full facts, with files and names, were collected on 2026-11-21 (388 lines; kept in the coordinator's scratchpad
as `inventory/raw-inventory.md` until the maintainer says where it should live).

| Family | Already a block | Still wired in |
|---|---|---|
| Characters | The member's sheet (`Member`: dress, character, bearing, skills), six sheets, the figure files | The list of members is an enum (`Player`), so every `when` names all six. Wildness is on the sheet but drives nothing while the show runs. |
| Character skills | Timed looks (`CameraStare`, `CameraGlance`, `BandGlance`, `InwardStare`, `CrowdGaze`) and idles (`StickTwirl`, `TuneUp`, `CapTug` and the rest) are data on the sheet with one timing type (`SkillTiming`) | The acting is inside each instrument's pose class, with no common contract: looks work only on the trumpet and the strings, idles only where a pose happens to know the kind. Skill-like things outside the system: Sirene's stroll and its kinds (plain, edge, camera, close, visit), the walk-in, straightening up, eye contact (a flag; its timing and pairs are global), `CountIn` and `CrossedMallets` (picked out by type for the marimba only), breath, the eyes' waver and warmth, the hair tail, the true selves in the shadow (configured three different ways). |
| Camera | Placements as data (`ANGLES`, `LOOKS`, floor shots), round kinds (`Looks`, `Arc`, `Circle`, `Flight`, `Reverse`), the way back by cut or dolly | One class (`Choreography`) holds all moves and their constants in bars; tables are keyed by this band's members and tuned to this stage plot; it names the cast ("who plays seldom", "who strolls"). It chooses by seed, bars and who plays; it never reads the music's energy. |
| Scenery | The look's variants as enums (stage, floor, haze, world, ghosts, stone and so on), the lamps' moods and rigs as tables, pure functions of the time of day (`WorldLight`) | The variants are static choices by address or key. What runs over time (moods every 28 bars, rigs, the front lamp's shadow stretch, the strobe on bass drum runs, the opening, the drift) is one fixed plan (`LightPlan`) in constants. Only the strobe listens to the music. |
| What drives it | `Show` (seed, bars, energy), `Song` with its parts, `Switches` | The song and the cast: `SchmetterlingCast`, `SchmetterlingTags`, the stage plot, the app's song choice. |

How things depend on each other today (each is a direct reach from one class into another):

| Who | Reads | For |
|---|---|---|
| Sirene's stroll | the camera's home shot; the others' places | how near she may come; whom she visits |
| The camera | who plays in a window; who strolls; the stage plot | whom to show; cut instead of dolly; keeping clear of the others |
| Members' looks | where the camera is (one frame late); the others' eyes; the crowd's spots | camera stare, band glance, crowd gaze |
| Eye contact | her notes | not while she plays |
| Sirene's eyes in her shadow | the front lamp; the camera; her gaze | where and when the eyes show |
| Haze, cold world, underworld | the front lamp | they follow it |
| Crowd, nod, breath | the music's energy | how alive they are |
| The strobe | the bass drum's runs | when it flashes |

## The idea: three performers, one kind of skill

1. **Three performers, each with a sheet.** A *member* (as today), the *camera operator*, and the *stage* (scenery and
   lights, the "stage hand"). A sheet holds what is intrinsic (a member's body and character; the operator's taste,
   for example how often he cuts or dollies; the stage's set: fence or wall, mirror floor, stone) and a list of
   equipped skills.
2. **A skill is two things: what and when.** *What*: a kind with its own few numbers (a camera stare's rise and fall,
   an arc's width, a mood's two colours). *When*: one shared vocabulary of triggers, which replaces today's styles:
   - **by chance in slots** (today's `SkillTiming`): every n seconds a chance, a hold, a cooldown;
   - **by the part**: in the player's pauses, before a phrase, while a stretch is free (count-in, crossed mallets,
     the stroll, idles);
   - **by the song's form**: in rounds of bars (the camera's 48, the moods' 28, the shadows' 8 in 40);
   - **by what sounds**: a run of bass drum hits, the energy above a level (the strobe today; open for more);
   - **always** (breath, the eyes' waver, the nod);
   - **on a cue**: at a named moment of this song, written in the song's score ("at bar 64 the ghosts").
   A skill keeps being a pure function of time, seed and song wherever it is today: that is what makes a show
   repeatable and a still testable.
3. **A skill claims what it uses.** A member has a few channels (where they stand, the body's turn, the gaze, each
   hand); the camera is one channel; the stage has several (the world, the back lamps' mood, the rig, the front
   lamp, the floor's other world, the haze). Two skills that claim the same channel are ranked by their order on the
   sheet, as the looks are today. This is the one rule that settles "the stroll owns her place and gaze", "an idle
   needs a free hand", "one camera move at a time", and it gives a future skill a clear place to fit in.
4. **Performers do not reach into each other: they read the board.** One small set of facts about the frame, written
   by whoever owns the fact and read by anyone: who plays now and lately, who walks and where, where the camera is
   and whom it shows, how bright the front lamp is, the time of day. Every dependency in the table above becomes a
   read of the board. A new dependency (the camera favours whoever is strolling; the lamps warm when two members hold
   eye contact; a member notices the camera on them) is then a new reader, not a new knot. The frame's order stays
   fixed and written down (band, then stage, then camera), with the one known lateness: the band sees the camera
   where it was a frame ago.
5. **A song has a score.** Plain data that binds it all for one song: the cast (who plays which part), the stage plot
   (places, entrances), each performer's equipped skills with their numbers, the palette (which moods and worlds this
   song may use) and the cues. Der Schmetterling's score is exactly today's show. A second song with another score is
   the proof that the blocks are blocks.

Kept plain on purpose (stone rule: complexity is the enemy): sheets and scores are Kotlin data classes and lists, no
code generation, no script language of their own, no registry by reflection. A skill kind is a sealed class with an
exhaustive `when` where it is acted, as today, so the compiler names every place a new kind must be handled.

## The work, in steps

Every step leaves the show as it is: stills of fixed seeds and times before and after must agree, and the specs
that pin behaviour stay green. Each step is a ticket of its own when it is taken up.

1. **The song and the cast leave the code.** A `Cast` (who plays which part and tags) and the stage plot become data
   handed to the band, the camera, the lights and the eye contact. Nothing outside the score names
   `SchmetterlingCast` or a member. The `refactorme` folders go.
2. **One vocabulary for "when".** The trigger types above, as plain data with their pure functions, grown out of
   `SkillTiming` and the part's pauses. Moved onto it first, each with its numbers on the sheet: eye contact (its
   timing and partners), straightening up, the count-in, the crossed mallets.
3. **One contract for a member's pose.** Every instrument's player offers the same channels (place, turn, gaze,
   hands) and says which of them its playing holds at a moment. Looks and idles are then acted in one place for all
   instruments, and a spec fails where a sheet equips a skill its pose cannot show (today it is silently ignored).
   The big pose classes shrink to the playing itself.
4. **The walks become skills.** The stroll with its kinds (edge walk, walk to the camera, visit) and the walk-in are
   equipped, with their numbers on the sheet and the plot; any member who can leave their place could have one.
5. **The true self is one thing.** A member's shadow shapes (snakes, eyes in the shadow, later wings, horns, a
   sword) are named on the sheet in one way and made in one place, for the show and for the Meet page alike.
6. **The board.** Introduced with step 3 and filled as the steps need it; at this step the remaining direct reaches
   (stroll to camera, camera to cast, shadow eyes to lamp) are replaced by reads.
7. **The camera operator.** The placements become a library (kept apart from the moves); the moves (`Looks`, `Arc`,
   `Circle`, `Flight`, `Reverse`, the floor shots, the dolly back) become skills with weights and triggers on the
   operator's sheet. Another operator with other skills gives another film of the same show.
8. **The stage hand.** The set (static choices) apart from the skills over time: a mood, a rig change, the front
   lamp's shadow stretch, the strobe on runs, a world for a while (night, ember, ghosts), the opening, the
   underworld's moment. `LightPlan`'s tables become the default equipment.
9. **The score.** One data object per song, Der Schmetterling's first; then a second song as the proof, with its own
   cast, palette and cues.

Steps 1 to 3 untangle and are needed by all the rest. Steps 4 and 5, 7 and 8 are independent of each other after
that and can be taken in any order, or side by side. Step 9 can begin thin right after step 1 and grow.

## Kept in view for later

- The music's energy as a trigger for the camera and the lamps (neither reads it today).
- Wildness as a real number: for example how readily a member takes a chance, so one figure tunes many skills.
- Skills between two members (a duel, a shared look, standing back to back): two sheets, one claim on both.
- More bands and more figures: `Player` as an enum is the last thing that names the six; it becomes data when a
  second band needs it, not before.
- The Meet page reading the same sheets (it already reads name, instrument and wildness).

## Decided (the maintainer, 2026-11-21)

1. **Where it lives:** "i will decide later how we do the split, for now we stay in this repo". The steps keep the
   cut in view all the same: the machinery apart from the band, the scores and the app, so the split stays cheap.
2. **Moods:** "we keep the mood selection as it currently is": chosen by the seed inside what the score allows.
3. **How far the tidying goes:** "tidy up as much as you can. I want to get things in order so a review round has an
   easier job": step 3 covers all four instruments, and each step leaves its area in order (no half-moved code).
4. **The energy for camera and lamps:** "No not now, but it needs to be kept in mind while factoring": the trigger
   vocabulary has its place for it ("by what sounds"), nothing reads it yet.

## Acceptance

- Der Schmetterling's show from its score is the same picture as today's, for fixed seeds, at every step.
- A second song runs with a different cast, palette and cues without a change to any performer's code.
- No file outside a score or a sheet names a song, and none outside the band's own package names a member.
- A skill equipped where it cannot be shown fails a spec.

## History

- 2026-11-21: created, from the inventory of the same day.
- 2026-11-21: IN PROGRESS. The four decisions are taken; step 1 (the song and the cast leave the code) begins.
