# The band on stage: inventory (2026-11-16)

What the prototype holds after two days of rounds with the maintainer, taken from the code as it stands. It is the
ground for the next step: sorting the movements into kinds and giving them one form. Nothing here is a plan yet;
section 9 lists what is uneven today, which is what a form has to settle.

Ticket: KLANG-00446. How to run it: `README.md`.

## 1. In one paragraph

A plain three.js page plays one recording of Der Schmetterling (9 min, 146 beats a minute) and shows six dark
figures with glowing eyes who play it: drums, bass, two guitars, marimba, trumpet. Every motion is computed each
frame from the time alone: from the song's note events, the beat, the recording's loudness and seeded chance. No
motion is keyframed and nothing is stored between frames, so any moment can be shown on its own and always looks
the same. About 2,500 lines: 2,070 of JavaScript in eleven files, 430 of Python for Blender.

## 2. The three parts

| Part | What it does | Where |
|------|--------------|-------|
| The figures | One skeleton of points with a thickness each becomes six bodies; exported as glTF with a bone per real joint | `blender/figures.py`, `models/*.glb` |
| The recording | One render of the song writes the audio and the events of that very render; the loudness is measured from the audio | `BandRecordingScratch.kt.txt`, `energy.py`, `ignored/` |
| The page | Stage, players, instruments, walk-in, camera | `web/` |

## 3. What drives a motion (the inputs)

| Input | What it is | Who uses it |
|-------|------------|-------------|
| Note events | Per tag: start, duration, pitch (note name or frequency), gain. 32,270 events, each sound doubled a few thousandths apart (left and right) and merged again | every player's playing; the camera (who plays now) |
| The beat | Time times 146 / 60; a cycle is a bar of four beats | the nod, the sway, the frontwoman's steps, the camera's rounds |
| Loudness | A value every 20 ms: the level, its mean over 1.5 s (`slow`), how far a moment is above that (`burst`); also three bands (low, mid, high) | the nod's depth, the body's dip on a burst. The bands are measured and unused |
| Own activity | How much a player plays right now: recent notes, fading | the eyes' glow; the string players' body; the frontwoman's lean |
| Chance | A number that belongs to a player and a moment, always the same one | every variation, the looks, the fits, the strolls |
| Show time | Runs from -15 s (the click) through 0 (the song starts) | the walk-in, the house lights |

What is missing as an input: dynamics. A part's gain is nearly constant in the recording, so nobody plays visibly
harder or softer.

## 4. The cast

| Player (key) | Plays the tags | Build | Height | Eyes | Pattern | Stands |
|--------------|----------------|-------|--------|------|---------|--------|
| Drummer (`drums`) | `kick`, `snare`, `hats`, `countin` | muscular: wide shoulders, deep chest, heavy arms | 1.85 m | orange | tiger bands | on a riser of 0.4 m, back, centre |
| Bass (`bass`) | `bass` | tall, thin, long neck, stooped; eyes set low | 1.96 m | blue | pinstripes | left, back row |
| Lead guitar (`guitar1`) | `guitar1` | slim, a little stooped, long hair | 1.82 m | red | a spiral | far left, front |
| Rhythm guitar (`guitar2`) | `guitar2`, `guitar3` | short and heavy, a belly, a flat cap | 1.67 m | amber | spots | far right, front |
| Marimba (`marimba`) | `lead` | a slender woman, long ponytail (with bones) | 1.73 m | teal | diamonds | right, back row |
| Trumpet, the frontwoman (`eimer`) | `trommel` | a woman, a little athletic, level shoulders, long neck, long rounded skull | 1.64 m | yellow-green | a woven braid | front, left of centre |

`shaker` is recorded and played by nobody. The key `eimer` is the trumpet player's from the time when `trommel`
was thought to be a bucket drum.

## 5. The movements, by kind

### 5.1 Playing: driven by the note events

| Movement | Who | How it goes |
|----------|-----|-------------|
| A stroke with a stick or mallet | drummer, marimba | The tip is on the head at the hit; after it a small bounce and a low hover; before the next a wind-up as high as the hit is strong; on the way the tip travels to the next place. The wind-up starts 0.34 s (mallets 0.26 s) before the hit |
| Holding the stick | drummer, marimba | Forearm and stick are one long bone from elbow to tip, so the hand stays in line with the forearm; the wrist bends up a little in the wind-up and snaps down into the hit; the stick is held a quarter up from its end |
| The kick | drummer | Both feet in turn when kicks follow within 0.45 s, else the right; a foot lifts just before its kick |
| Which drum | drummer | Snare left hand (fast runs both hands); hat and ride right hand; the open hat on a second, open hat; a crash or the open hat by the left hand when it is free |
| Which bar | marimba | By the note's pitch; the hands in turn, never crossing |
| The fretting hand | bass, guitars | One position on the neck (frets 3 to 6); the pitches are shared over the strings by how often they are played, so a higher note is a higher string; the hand glides to the next note over up to 0.32 s; across the neck by the strings' true distance |
| The picking hand | bass, guitars | At the played string (drawn 2.5 times as far apart as real); a single note picked small, a chord strummed across; down after a pause, else down and up in turn; guitarists from the wrist (the arm a fifth of the way), the bass from the arm |
| The trumpet | frontwoman | Up to the lips from 0.85 s before a phrase to 1.7 s after; the bell higher and the body back for a high passage, taken over half a second so no single note jerks her; valves by the note |

### 5.2 With the beat: driven by the beat, as strong as the sound

| Movement | Parts | How it goes |
|----------|-------|-------------|
| The nod | head, neck | Down on a beat, round on both sides, a little faster down than up; each beat of the bar has its weight; depth by the loudness of the last 1.5 s; never by a single loud moment |
| The lean into the beat | trunk | The same curve, plus a dip on a sudden burst |
| The give | knees (the hips go down) | The same curve, plus the burst |
| The sway | hips side to side, chest tilting against | A slow wave, a bar per side (string players and the frontwoman only) |

### 5.3 Wandering: driven by chance alone

The head turns and tilts and the trunk leans a little, each on its own slow wave, a new aim every few seconds.

### 5.4 Playing or not: a change of state

| Movement | Who | How it goes |
|----------|-----|-------------|
| Relaxed arms | marimba | Arms hang, mallets down, upright, feet closer; into position from three cycles before her first note, ready a good cycle ahead; down again 1.4 s after her last |
| The trumpet at her side | frontwoman | The right arm hangs loose with the trumpet by her thigh, the left arm free |
| Idle, but ready | drummer, string players | Nothing: the sticks hover over the drums and the hands stay on the instrument however long the break is |

### 5.5 Reaching: the body follows the hands

| Movement | Who | How it goes |
|----------|-----|-------------|
| Weight from leg to leg | marimba | The arms reach first; only where the playing lies beyond 0.2 m to a side do the hips follow, by what is beyond, and by where the hands play over two seconds |
| Turning to the kit | drummer | The chest turns a little toward where the sticks aim |

### 5.6 Going somewhere

| Movement | Who | How it goes |
|----------|-----|-------------|
| The walk-in | all | From off the stage to the place within about six seconds, one after another from the click on; the legs step by the way made; the string players walk in playing position, the drummer with hanging arms |
| Sitting down | drummer | A blend from the walking pose to the seated one over 1.6 s once he is at the stool; onto the riser by a plain lift near the end of his way |
| The stroll | frontwoman | In breaks of more than 30 s, from 12 s after her last note: rounds of 36 to 58 s out from her place and back, a wait of 9 s between; slowing right down every nine seconds without stopping; a step every two beats, hips dipping on the beat, shoulders against hips, arms swinging; she turns at 40 degrees a second at most and never shows her back |

### 5.7 Attention: where someone looks

| Movement | Who | How it goes |
|----------|-----|-------------|
| At the playing | marimba, string players | The head toward where the hands are (a fixed turn for the string players) |
| At the viewer | rhythm guitar | About every 20 s on average for 3 to 5 s: head up and to the camera, the nod held back |
| At each other | ten pairs | About every 15 s on average for 2 to 3.5 s: one looks, the other answers 0.35 s later; a head turns at most 70 degrees; the frontwoman never while she plays |

### 5.8 Fits

The rhythm guitarist's head takes 30 % of its share most of the time and all of it in a fit; a slow mood decides,
about 7 % of the song in five fits.

### 5.9 What the things do

| Thing | What it does |
|-------|--------------|
| Cymbals | Swing after a hit and settle |
| Kick drum | A small jolt on a kick |
| Marimba | Floats without legs and hovers slowly; a struck bar dips and glows in her eye colour |
| Strings | The played string alone lights: a flash, a hold at 30 % while the note sounds, a quick fade |
| Trumpet | Its bell glows while a note sounds |
| Eyes | Glow with how much their player plays right now |

## 6. Which part of the body is driven by what

| Part | Drummer | String players | Marimba | Frontwoman |
|------|---------|----------------|---------|------------|
| Place on the stage | walk-in | walk-in | walk-in | walk-in, stroll |
| Hips (place) | seated; bob with the beat | give with the beat; sway | weight shift; give | give; sway; the walk's dip |
| Trunk | fixed lean; the beat; turned to the kit | a lean of their own; the beat; sway | upright or bent to play; the beat; turned to the playing | back with the tune; the beat; the walk's turn |
| Neck and head | the beat; wandering; eye contact | the beat; wandering; a fixed turn to the neck; the look at the viewer; eye contact | the beat; wandering; toward the playing; eye contact | the beat (little); wandering; turned across the stage to play; eye contact |
| Arms and hands | the strokes | fretting and picking | the strokes, or hanging | the trumpet, or hanging and swinging |
| Legs and feet | on the pedals, the kicks; walking in | planted, wide; walking in | planted wide, or narrow at rest; walking in | planted; stepping in time on a stroll; walking in |

The layers are applied in this order each frame: the player's own pose, the wandering, the walk-in's legs, eye
contact. The order is written out in `main.js` and nowhere stated as a rule.

## 7. What makes a player a person (the settings)

### 7.1 How they move with the music (`CHARACTERS` in `main.js`, read by `Mover` in `strike.js`)

| Setting | Meaning | Range in use |
|---------|---------|--------------|
| `depth` | how deep the nod is | 0.5 (marimba) to 1.0 (drummer) |
| `beats` | how much each of the bar's four beats counts | every beat (rhythm guitar) to one and three (bass, marimba) |
| `lag` | late or early, in beats | -0.03 (eager) to 0.07 (late) |
| `soft` | how round the nod is, in beats | 0.10 to 0.18 |
| `chance` | how much a nod's depth and timing vary | 0.12 (bass) to 0.6 (lead guitar) |
| `skip` | how often a nod is left out | 0 to 0.18 |
| `look`, `tilt`, `lean` | how far head and trunk wander | look 0.05 to 0.30 rad |
| `parts` | which parts take the beat: `head`, `trunk`, `knees`, `sway`, a factor each | 0.5 to 1.5 |
| `steady` | how little the sound's energy changes the movement | 0, or 0.9 (bass) |
| `fits` | the head goes all the way only in fits | rhythm guitar only |

The six as set: the drummer the engine (every beat, from the trunk, a touch ahead); the bass the steady one (late,
one and three, from knees and hips, always the same); the lead guitar loose (uneven, looks about, leans his trunk
in); the rhythm guitar the headbanger in fits; the marimba player composed (small nods, quiet below the neck); the
frontwoman the balanced one (every part alike, on time, little chance).

### 7.2 Other settings per player

| Setting | Where | Who has it |
|---------|-------|------------|
| `lean`, `bang`, `bounce`: base lean, how hard the head goes, how much the knees give | the string players' `style` | bass, guitars |
| `glance` at the viewer | the string players' `style` | rhythm guitar |
| The pairs for eye contact | `PAIRS` in `main.js` | all |
| The body pattern | `SKINS` in `main.js`, six patterns in `pattern.js` | all |
| Eye colour, size and height; the build (about 20 numbers); extras (hair, cap, bust, nose, skull) | `FIGURES` in `blender/figures.py` | all |
| Place on the stage, the walk-in's way and time | `PLACES`, `ENTRANCES` in `main.js` | all |

## 8. Stage, lights, camera, intro

| Part | What is there |
|------|---------------|
| Stage | A dark floor, a dim backdrop, a riser; the band in an arc; faint contact shadows under kit and riser only |
| Lamps | Four spots: blue from back left, red from back right, a cool one from the back centre, a weak warm one from the front; three cast shadows. Each wanders by about a tenth, shivers faintly and sags by a sixth about every 20 s. They do not follow the music yet |
| Camera | Mostly the total. Rounds of 48 bars (79 s): 36 on the total, three looks of three bars with cuts between (a player who plays then, or another angle), back by a cut, in every third round by a slow dolly zoom |
| Intro | The stage nearly dark until the click; on it the lamps come up at once and the players walk in over 15 s; the marimba and the kit are in place |
| Controls | Click starts; space pauses; left and right jump 10 s (right skips the intro); an fps counter |

## 9. What is uneven today

The form has to settle these. Each grew from one request at a time.

1. **Every player writes its own body.** The drummer, the string players, the marimba player and the frontwoman
   each have their own lines for hips, trunk and head, with their own base numbers, and each maps the nod onto the
   parts in its own way. The same movement (the give in the knees) has four spellings.
2. **Two sets of personal settings.** The string players have `style` (`lean`, `bang`, `bounce`) beside the
   character's `parts`; both scale the same things.
3. **Playing or not is known to two of four.** The marimba player and the frontwoman relax; the drummer and the
   string players never do, though the song has breaks of a minute for them.
4. **Three ways to walk.** The frontwoman's stroll has its own legs; the walk-in steps the legs of whatever pose
   there is; the drummer has a third, plain walking pose.
5. **Abilities belong to one player.** Only the rhythm guitarist can look at the viewer, only the frontwoman can
   stroll, though neither is tied to an instrument.
6. **Attention has no order of rank.** A fixed turn to the instrument, the wandering, the look at the viewer and
   eye contact are applied one after the other, and the last one wins.
7. **Own activity is counted four times**, each player in its own way, for the eyes and for the body.
8. **Chance has three spellings** (in `strike.js`, `strings.js`, `main.js`) with seeds chosen by hand.
9. **The beat and the loudness are everyone's, dynamics are no one's.** The three bands are measured and unused;
   the notes carry no strength.
10. **Names.** `eimer` for the trumpet player; `strike.js` holds the nod and the characters as well as the strokes.

## 10. Not checked, and known rough spots

Everything was judged by the maintainer in motion and by the agent in stills only. Never seen in motion by the one
who wrote it: the walk-in (feet may slide, the step onto the riser is a lift), the stroll's turns, the eye
contact, the camera's calmer schedule, the wrist strum, the string choice. Known: the guitars are ovals with a
horn; no fingers, so things hang from the end of a hand; the fretting is plausible, not real; the pinstripes run
into a star on the back; the trumpet's way up from her side is a straight line; the recording is a cut at nine
minutes, not an ending.

## 11. Not started

The lamps following the music; throwing things at the players and the audible mistake; an ending; the port into
the app in Kotlin.
