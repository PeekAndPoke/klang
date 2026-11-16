# The band on stage: inventory (2026-11-16, after the sorting)

What the prototype holds, taken from the code as it stands. It is the ground for the next step: sorting the
movements into kinds and giving them one form. Nothing here is a plan yet; section 9 lists what is uneven today,
which is what a form has to settle.

Ticket: KLANG-00446. How to run it and which file holds what: `README.md`.

## 1. In one paragraph

A plain three.js page plays one recording of Der Schmetterling (9 min, 146 beats a minute) and shows six dark
figures with glowing eyes who play it: drums, bass, two guitars, marimba, trumpet. Every motion is computed each
frame from the time and one seed alone: from the song's note events, the beat, the recording's loudness and seeded
chance. No motion is keyframed and nothing is stored between frames, so any moment can be shown on its own, and a
seed always gives the same performance. Each opening of the page takes a new seed. About 3,200 lines: 2,680 of
JavaScript in eighteen files, 530 of Python for Blender.

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
| Chance | A number that belongs to a player, a moment and the show's seed, always the same one (`hash01` in `util.js`) | every variation, the looks, the fits, the strolls, the lights' turns, the camera |
| The seed | One number per performance (`?seed=`, else new on every opening) | everything that is chance; never the playing |
| Show time | Runs from -15 s (the click) through 0 (the song starts) | the walk-in, the house lights |

What is missing as an input: dynamics. A part's gain is nearly constant in the recording, so nobody plays visibly
harder or softer.

## 4. The cast

| Player (key) | Plays the tags | Build | Eyes | Pattern | Stands |
|--------------|----------------|-------|------|---------|--------|
| Drummer (`drums`) | `kick`, `snare`, `hats`, `countin` | muscular: wide shoulders, deep chest, heavy arms, a short thick neck | orange | tiger bands | on a riser of 0.4 m, back, centre |
| Bass (`bass`) | `bass` | tall, thin, long neck, stooped; eyes set low | blue | pinstripes | left, back row |
| Lead guitar (`guitar1`) | `guitar1` | slim, a little stooped; long hair worn open (a mane, and strands combed back as a pattern) | red | a spiral | far left, front |
| Rhythm guitar (`guitar2`) | `guitar2`, `guitar3` | short and heavy, a belly, a flat cap | amber | spots | far right, front |
| Marimba (`marimba`) | `lead` | a slender woman, long ponytail (with bones) | teal | diamonds | right, back row |
| Trumpet, the frontwoman (`eimer`) | `trommel` | a woman, a little athletic, level shoulders, long neck, a long rounded skull | yellow-green, rounder | a woven braid | front, left of centre |

`shaker` is recorded and played by nobody. The key `eimer` is the trumpet player's from the time when `trommel`
was thought to be a bucket drum.

The cloth: each body's pattern shows in how it throws back the light, not in its colour; the skin is grainy and
glitters (one grain in seven a spangle, the two women one in five), the spangles in the ground and hardly on the
pattern. Lit from the front, the ground goes dark and the pattern stays bright. The skin's weights are blended
round every joint, so limbs bend without a kink.

The heads are the open point: noses are missing and the chins are faint (drawn out of the skin). A try to model
whole heads with a face was taken back (the characters lost their heads); it is in the history (`fbe5dcbe4`).

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

### 5.3 Always there: driven by chance alone

| Movement | How it goes |
|----------|-------------|
| Wandering | The head turns and tilts and the trunk leans a little, each on its own slow wave, a new aim every few seconds |
| Breath | The belly out and in, the chest a little with it, 4 to 6 % of its girth; 14 to 19 breaths a minute, each at their own pace; in faster than out; deeper when the band is loud |
| The eyes' waver | The glow drifts by smooth noise, two layers, by a third up and down, each pair on its own |

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
| The stroll | frontwoman | In breaks of more than 30 s, from 12 s after her last note: rounds of 36 to 58 s out from her place and back, a wait of 9 s between; in about two rounds of five she comes right up to the camera; slowing right down every nine seconds without stopping; a step every two beats, hips dipping on the beat, shoulders against hips, arms swinging; she faces where she goes (her back to the audience on the way back) and turns at 57 degrees a second at most |

### 5.7 Attention: where someone looks

| Movement | Who | How it goes |
|----------|-----|-------------|
| At the playing | marimba, string players | The head toward where the hands are (a fixed turn for the string players) |
| At the viewer | rhythm guitar | About every 20 s on average for 3 to 5 s: head up and to the camera, the nod held back |
| At each other | ten pairs | About every 27 s on average: one glances over (a second), the other notices one to three seconds later, then they share a look of about a second with a small nod to each other; a head turns a good half of the way, never more than 70 degrees; the frontwoman never while she plays |

### 5.8 Fits

The rhythm guitarist's head takes 30 % of its share most of the time and all of it in a fit; a slow mood decides,
about 7 % of the song in five fits.

### 5.9 What the things do

| Thing | What it does |
|-------|--------------|
| Cymbals | Swing after a hit and settle |
| Kick drum | A small jolt on a kick |
| Marimba | Floats without legs and hovers slowly; a struck bar dips and glows in her eye colour |
| Strings | The played string alone lights, and fades as its sound does: an exponential fall from the pluck, a quicker one once the note ends |
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

The layers are applied in this order each frame (`frame()` in `main.js`, `playBand()` in `band.js`): the walk-in's
place, the player's own pose (with the wandering inside it), the walk-in's legs, eye contact, the eyes' waver,
breath. The lights and the camera follow.

## 7. What makes a player a person (the settings)

### 7.1 How they move with the music (`CHARACTERS` in `band.js`, read by `Mover` in `strike.js`)

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
| The pairs for eye contact | `PAIRS` in `life.js` | all |
| The body pattern, hair as a pattern, the share of glitter | `SKINS`, `HAIR`, `GLITTER` in `band.js`; the patterns in `pattern.js` | all |
| Eye colour, size and height; the build (about 20 numbers); extras (hair, cap, bust, nose, skull) | `FIGURES` in `blender/figures.py` | all |
| Place on the stage, the walk-in's way and time | `PLACES`, `ENTRANCES` in `band.js` | all |

## 8. Stage, lights, camera, intro

| Part | What is there |
|------|---------------|
| Stage | A dark floor, a riser, the band in an arc; faint contact shadows under kit and riser only |
| The wall | Six metres behind the riser. Under the back light an ornament (medallions) in its own faint glow, darker to the sides; under the front lamp a woven wire mesh (the frontwoman's braid, laid flat), with the band's shadows on it |
| Back light | Four spots: blue from back left, red from back right, a cool one from the back centre, a weak warm one from the front; three cast shadows |
| The front lamp | One warm lamp on the floor before the stage, looking up: for 8 bars in every 40 it takes over and throws the band's shadows big onto the wall |
| The stroboscope | The front lamp, while the bass drum runs fast (eight hits or more in a row, each under a quarter second apart; six stretches in the recording, the long ones 6.6 s): on the sixteenths (about ten flashes a second), all other lamps off. A public showing needs a warning (photosensitive epilepsy); `?nostrobe` |
| Analog lamps | Each lamp wanders by about a tenth, shivers faintly and sags by a sixth about every 20 s. The lamps do not follow the music yet |
| Camera | Mostly the total. Rounds of 48 bars (79 s): 36 on the total, three looks of three bars with cuts between (a player who plays then, or another angle; never still, a very slight slide), back by a cut, in about a third of the rounds by a slow dolly zoom. The seed picks who, from where and when |
| Intro | The stage nearly dark until the click; on it the lamps come up at once and the players walk in over 15 s; the marimba and the kit are in place |
| Controls | Click starts; space pauses; left and right jump 10 s (right skips the intro); the seed and an fps counter in the corner |

## 9. What is uneven today

The form has to settle these. Each grew from one request at a time. The sorting of 2026-11-16 (the code split into
modules, shared helpers, dead code out, proven by identical stills) did not touch them: it changed no behaviour.

1. **Every player writes its own body.** The drummer, the string players, the marimba player and the frontwoman
   each have their own lines for hips, trunk and head, with their own base numbers, and each maps the nod onto the
   parts in its own way. The same movement (the give in the knees) has four spellings.
2. **Two sets of personal settings.** The string players have `style` (`lean`, `bang`, `bounce`) beside the
   character's `parts`; both scale the same things.
3. **Playing or not is known to two of four.** The marimba player and the frontwoman relax; the drummer and the
   string players never do, though the song has breaks of a minute for them. The two "is she playing" curves have
   the same shape and other numbers.
4. **Three ways to walk.** The frontwoman's stroll has its own legs; the walk-in steps the legs of whatever pose
   there is; the drummer has a third, plain walking pose. Only the gait's phase is the same.
5. **Abilities belong to one player.** Only the rhythm guitarist can look at the viewer, only the frontwoman can
   stroll, though neither is tied to an instrument.
6. **Attention has no order of rank.** A fixed turn to the instrument, the wandering, the look at the viewer and
   eye contact are applied one after the other, and the last one wins.
7. **Own activity is counted four times**, each player in its own way (other windows, other fades), for the eyes
   and for the body.
8. **Chance is one function now, its seeds are not one scheme.** Every draw goes through `hash01`, but each place
   mixes its own numbers into it (a player's seed chosen by hand, a slot, the show's seed).
9. **The beat and the loudness are everyone's, dynamics are no one's.** The three bands are measured and unused;
   the notes carry no strength.
10. **Names.** `eimer` for the trumpet player; `strike.js` holds the nod, the characters and the seeded chance as
    well as the strokes.
11. **State outside the frame.** Three values are set by one module and read by others: the sound's energy and
    the show's seed (`strike.js`), how dark the cloth's ground is (`pattern.js`).

Known and left as they are (fixing them would change what is seen):

- "click to start" vanishes at once; a fade was meant. Starting with the right arrow leaves it on the screen.
- The frontwoman's turns during a stroll are worked out for 25 minutes of it at most (the song is nine).
- Her guard against eye contact while she plays looks at when her notes start, not at how long they sound.

## 10. Not checked, and known rough spots

Everything was judged by the maintainer in motion and by the agent in stills only. Never seen in motion by the one
who wrote it: the walk-in (feet may slide, the step onto the riser is a lift), the stroll's turns, the eye
contact, the camera's calmer schedule, the wrist strum, the string choice. Known: the guitars are ovals with a
horn; no fingers, so things hang from the end of a hand; the fretting is plausible, not real; the pinstripes run
into a star on the back; the trumpet's way up from her side is a straight line; the heads have no noses and faint chins; the recording is a cut at nine
minutes, not an ending.

## 11. Not started

The lamps following the music; throwing things at the players and the audible mistake; an ending; the port into
the app in Kotlin; giving the movements one form (section 9).
