# Sound Playground — Melody Candidates

**Date:** 2026-03-14
**Context:** Six Hats brainstorm (multiple rounds) for selecting melodies for the progressive sound synthesis
playground.
**Rounds:** Initial candidate generation + expanded brainstorm (gloves off, all genres, copyright parked)

---

## Core Design Principle

**Design for adults that kids also enjoy — not the other way around.**

Children's melodies (Twinkle Twinkle, Mary Had a Little Lamb) talk down to the audience and make adults disengage.
Melodies with genuine emotional weight (melancholy, tension, beauty, drama) respect the listener's intelligence — and
kids rise to meet them. Think Pixar, not PBS Kids. Miyazaki, not Teletubbies.

This means: no "safe" nursery rhymes. Pick melodies an adult would choose to listen to. If a melody wouldn't feel at
home in a film score, a jazz bar, or a concert hall, it doesn't belong in the playground.

---

## Selection Criteria

Each candidate scored 1-5 on 7 dimensions (max 35):

| # | Criterion                | What It Measures                                   |
|---|--------------------------|----------------------------------------------------|
| 1 | **Recognizability**      | Identifiable within 2-4 bars                       |
| 2 | **Loopability**          | Clean 4-8 bar loop point                           |
| 3 | **Harmonic Range**       | Enough pitch range / overtone variety for filters  |
| 4 | **Articulation Variety** | Mix of long/short notes for ADSR demonstration     |
| 5 | **Interval Spread**      | Varied intervals (steps + leaps) for FM modulation |
| 6 | **Note Spacing**         | Gaps between phrases for effects (reverb/delay)    |
| 7 | **Emotional Impact**     | Fills a distinct mood; makes users feel something  |

---

## Candidate List

### 1. Greensleeves (English traditional, ~1580)

- **Emotional color:** Melancholic, aching, bittersweet
- **Musical properties:** 3/4 waltz, minor-to-major shifts, dotted rhythms (long-short-short), intervals up to minor
  6th, held notes on beat 1
- **Synthesis strengths:** Rich harmonic content for filter sweeps; dotted rhythms naturally demo ADSR; mix of steps and
  leaps feeds FM well; 3/4 meter with held notes creates space for effects
- **Concerns:** 3/4 meter may feel less "modern"; sits within ~octave range
- **Gate test:** Osc: YES | Filter: YES | ADSR: YES | FM: YES | Effects: YES
- **Nominated by:** Red, Black, Yellow (all-rounder consensus pick)

### 2. Korobeiniki / "Tetris Theme" (Russian folk, 1861)

- **Emotional color:** Driving, melancholic, intense, nostalgic
- **Musical properties:** A minor, descending tetrachord, alternating 8th and quarter notes, intervals include
  2nds/3rds/4ths/5ths, phrase breaks at bar boundaries
- **Synthesis strengths:** Descending stepwise = continuous spectral sweep for filters; mixed note values = ADSR
  immediately audible; interval variety = ideal FM; natural rests for effects; loops perfectly in 4 bars
- **Concerns:** Tetris association may feel gimmicky
- **Gate test:** Osc: YES | Filter: YES | ADSR: YES | FM: YES | Effects: YES
- **Nominated by:** Black (technically strongest), Yellow, Red

### 3. In the Hall of the Mountain King (Grieg, 1875)

- **Emotional color:** Eerie, building tension, mischievous
- **Musical properties:** Chromatic ascending figure, march rhythm (even 8ths building to accented quarters), 4-bar
  repeating cell
- **Synthesis strengths:** Chromatic = filter sweep dream (every semitone activates different harmonics); march rhythm
  provides ADSR contrast; chromatic intervals produce maximally varied FM sidebands
- **Concerns:** Chromatic motion can sound muddy under heavy FM; rhythmically uniform in opening section (ADSR partial)
- **Gate test:** Osc: YES | Filter: YES | ADSR: PARTIAL | FM: YES (watch muddiness) | Effects: YES
- **Nominated by:** Yellow, Black, Green

### 4. Danse Macabre — main theme (Saint-Saens, 1874)

- **Emotional color:** Macabre whimsy, eerie, thrilling
- **Musical properties:** Tritone-heavy intervals, chromatic runs, waltz rhythm, staccato-to-legato phrasing
- **Synthesis strengths:** Tritone = FM synthesis gold (maximally dissonant sidebands that transform dramatically);
  staccato/legato contrast = ADSR showcase; spaces between phrases for effects
- **Concerns:** Narrow pitch range limits filter sweep drama
- **Gate test:** Osc: YES | Filter: PARTIAL | ADSR: YES | FM: YES | Effects: YES
- **Nominated by:** Red, Green, Blue

### 5. Sakura Sakura (Japanese traditional, Edo period)

- **Emotional color:** Haunting, contemplative, bittersweet
- **Musical properties:** Pentatonic minor with semitones (In scale), slow pacing, ornamental grace notes, generous
  space between phrases
- **Synthesis strengths:** Half-step intervals create intentionally beautiful beating under FM (like temple bells); slow
  pace lets filter sweeps breathe; grace notes demo ADSR subtlety; generous spacing for effects
- **Concerns:** May not be instantly recognizable in all Western markets; slow tempo may feel static in early stages
- **Gate test:** Osc: YES | Filter: YES | ADSR: YES | FM: YES | Effects: YES
- **Nominated by:** Green, Yellow

### 6. Kalinka (Russian folk, Larionov, 1860)

- **Emotional color:** Reckless, joyful, infectious, building energy
- **Musical properties:** Contrasting verse (slow, legato, minor) and chorus (fast, staccato, accelerating), leaps of
  4ths and 5ths
- **Synthesis strengths:** Verse/chorus contrast = built-in articulation variety for ADSR; minor key with leaps feeds
  FM; descending phrases work for filter sweeps
- **Concerns:** The accelerando is integral to the feel — fixed tempo may flatten it; chorus repetition on two pitches
  may bore
- **Gate test:** Osc: YES | Filter: YES | ADSR: YES | FM: YES | Effects: PARTIAL (fast sections wash out)
- **Nominated by:** Red, Green, Yellow

### 7. Habanera from Carmen (Bizet, 1875)

- **Emotional color:** Seductive, dangerous, smoky, slinking
- **Musical properties:** Chromatic descending line, habanera rhythm (dotted-eighth-sixteenth), minor key
- **Synthesis strengths:** Chromatic descent = "filter sweep encoded in the melody" — layering actual filter sweep
  creates double-helix effect; dotted rhythm naturally demos short vs long envelopes
- **Concerns:** The descending chromatic line may be less recognizable than the aria's vocal melody
- **Gate test:** Osc: YES | Filter: YES | ADSR: YES | FM: YES | Effects: PARTIAL
- **Nominated by:** Green

### 8. Dies Irae (13th-century Gregorian chant)

- **Emotional color:** Existential dread, weight, gravity
- **Musical properties:** 4-note descending motif, narrow range (a fifth), slow stepwise motion
- **Synthesis strengths:** Narrow pitch range means filter sweeps dominate timbral change rather than competing with
  pitch; slow motion lets FM ratio changes be heard clearly on each note; heavily quoted in film scores (subconscious
  recognition)
- **Concerns:** May feel too slow/solemn; narrow range limits filter drama; not everyone will consciously recognize it
- **Gate test:** Osc: YES | Filter: PARTIAL | ADSR: PARTIAL (slow, uniform) | FM: YES | Effects: YES
- **Nominated by:** Green

### 9. Scarborough Fair (English traditional, medieval)

- **Emotional color:** Wistful, longing, pastoral
- **Musical properties:** Dorian mode, long held notes AND quick ornamental turns, intervals span full octave including
  4ths and 5ths, 3/4 time
- **Synthesis strengths:** Dorian mode = unusual harmonic color for filters; held notes + ornaments = excellent ADSR
  contrast; wide intervals for FM; natural breathing space for effects
- **Concerns:** 3/4 time (same as Greensleeves); may feel "samey" if used alongside other folk ballads
- **Gate test:** Osc: YES | Filter: YES | ADSR: YES | FM: YES | Effects: YES
- **Nominated by:** Black

### 10. Wayfaring Stranger (American folk, pre-1880)

- **Emotional color:** Deeply melancholic, sparse, lonely
- **Musical properties:** Minor key, slow deliberate phrasing, wide intervals, lots of breathing room
- **Synthesis strengths:** Slow pacing leaves room for effects tails; sustained notes expose FM timbral shifts; wide
  intervals give filter sweeps clear harmonic range
- **Concerns:** Very slow — may test patience in early interactive stages; not universally recognized outside Anglophone
  world
- **Gate test:** Osc: YES | Filter: YES | ADSR: PARTIAL (slow uniform rhythm) | FM: YES | Effects: YES
- **Nominated by:** Blue

### 11. Shave and a Haircut (American vaudeville, pre-1900)

- **Emotional color:** Cheeky, irreverent, playful
- **Musical properties:** Only 7 notes, dramatic rest before final 2-note resolution, octave leap in some arrangements
- **Synthesis strengths:** The pause = perfect stage for reverb/delay tails; rhythmic variety demos ADSR; extreme
  brevity means every parameter change is immediately audible
- **Concerns:** Too short for a full through-line melody? More of a jingle than a loop. May feel trivial.
- **Gate test:** Osc: YES | Filter: PARTIAL (only 7 notes) | ADSR: YES | FM: PARTIAL | Effects: YES
- **Nominated by:** Green

### 12. La Cucaracha — opening phrase (Mexican traditional, pre-1900)

- **Emotional color:** Raucous, joyful, zero pretension
- **Musical properties:** Repeated-note staccato opening, leaping chorus, call-and-response structure
- **Synthesis strengths:** Repeated staccato = perfect ADSR attack/decay test; leaping chorus exposes FM sidebands;
  call-and-response gives effects clear wet/dry moments
- **Concerns:** Culturally specific associations; may feel unserious
- **Gate test:** Osc: YES | Filter: PARTIAL | ADSR: YES | FM: YES | Effects: YES
- **Nominated by:** Green

### 13. Sailor's Hornpipe (English traditional, pre-1800)

- **Emotional color:** Playful, bouncy, energetic
- **Musical properties:** Rapid scalar runs, dotted rhythms, wide melodic range
- **Synthesis strengths:** Rapid runs showcase ADSR snappiness; wide range feeds filter sweeps; dotted rhythms create
  articulation variety
- **Concerns:** Very fast — may become wash under effects; rapid notes may not give FM enough time to be heard per-note
- **Gate test:** Osc: YES | Filter: YES | ADSR: YES | FM: PARTIAL (too fast) | Effects: PARTIAL (wash)
- **Nominated by:** Blue

### 14. When Johnny Comes Marching Home (American Civil War, 1863)

- **Emotional color:** Defiant melancholy, bittersweet momentum
- **Musical properties:** 6/8 time, minor key, arpeggiated structure, galloping rhythm alternating long/short notes
- **Synthesis strengths:** 6/8 gallop = natural ADSR variety; minor arpeggios span wide harmonic range for filters;
  strong contour survives heavy effects
- **Concerns:** Not universally recognized outside US/UK
- **Gate test:** Osc: YES | Filter: YES | ADSR: YES | FM: YES | Effects: YES
- **Nominated by:** Green

### 15. Taps (US military bugle call, 1862)

- **Emotional color:** Solemn, reverent, ethereal
- **Musical properties:** 24 notes from only 4 pitches (arpeggiated triad), very sparse
- **Synthesis strengths:** Minimal pitch content = pure timbre lens. Every synthesis parameter change is immediately
  audible because the melody "does less" so the synthesis "does more."
- **Concerns:** Very slow, very solemn — may kill the playful mood; not recognized globally
- **Gate test:** Osc: YES | Filter: PARTIAL (only 4 pitches) | ADSR: PARTIAL (uniform durations) | FM: PARTIAL (limited
  intervals) | Effects: YES
- **Nominated by:** Green

---

## Gate Test Summary

Candidates passing ALL 5 stages (no PARTIAL):

| Candidate                           | Osc | Filter | ADSR | FM | Effects | Emotional Color    |
|-------------------------------------|-----|--------|------|----|---------|--------------------|
| **Greensleeves**                    | Y   | Y      | Y    | Y  | Y       | Melancholic        |
| **Korobeiniki**                     | Y   | Y      | Y    | Y  | Y       | Driving/intense    |
| **Sakura Sakura**                   | Y   | Y      | Y    | Y  | Y       | Haunting           |
| **Scarborough Fair**                | Y   | Y      | Y    | Y  | Y       | Wistful            |
| **When Johnny Comes Marching Home** | Y   | Y      | Y    | Y  | Y       | Defiant melancholy |

Candidates passing 4 of 5 (one PARTIAL):

| Candidate                     | Partial On | Emotional Color  |
|-------------------------------|------------|------------------|
| **Hall of the Mountain King** | ADSR       | Eerie/building   |
| **Danse Macabre**             | Filter     | Macabre whimsy   |
| **Kalinka**                   | Effects    | Reckless joy     |
| **Habanera (Carmen)**         | Effects    | Seductive danger |
| **La Cucaracha**              | Filter     | Raucous joy      |
| **Wayfaring Stranger**        | ADSR       | Deep melancholy  |

---

## Key Insights from the Discussion

**Black Hat's critical finding:** The most common failure mode is **uniform rhythm**. Melodies people think of as "
simple and recognizable" (Twinkle Twinkle, Ode to Joy, Mary Had a Little Lamb) fail because uniform note durations make
ADSR changes inaudible and small intervals make FM changes imperceptible. Recognizability and pedagogical fitness are
often opposing forces.

**Green Hat's surprise insight:** "Dignity is the enemy of a good playground." Silly, cheeky, or emotionally extreme
melodies (Danse Macabre, La Cucaracha, Shave and a Haircut) may serve the playground better than "respectable" choices
because they disarm intimidation.

**Yellow Hat's value frame:** Familiarity is a pedagogical accelerator — when users already know how a melody should
sound, every parameter change becomes self-explanatory. But the melody must have enough musical complexity to actually
expose the parameter.

**Red Hat's emotional truth:** The melody should make you FEEL something before you touch a single knob. Melancholic and
haunting melodies (Greensleeves, Sakura) create more emotional engagement than "neutral" ones.

## Expanded Candidate Pool (Round 2 — All Genres)

The second brainstorm round opened up to film scores, synth classics, video game themes, jazz, tango, world music —
copyright parked for now. Key new candidates:

### Film / Anime Scores

| Melody                      | Source                          | Emotional Color      | Synth Fitness                                                                            |
|-----------------------------|---------------------------------|----------------------|------------------------------------------------------------------------------------------|
| **Merry Go Round of Life**  | Howl's Moving Castle (Hisaishi) | Soaring, bittersweet | Detuned oscillators = orchestral width; slow ADSR attack mimics strings swelling         |
| **One Summer's Day**        | Spirited Away (Hisaishi)        | Crystalline sadness  | Sine wave = naked vulnerability. Best for effects stage. Weak on raw oscillators.        |
| **Comptine d'un autre été** | Amélie (Tiersen)                | Aching nostalgia     | Beautiful with filter sweeps + reverb, but lifeless on raw oscillators — piano-dependent |

### Synth-Native Classics

| Melody          | Source                          | Emotional Color        | Synth Fitness                                                                                                                |
|-----------------|---------------------------------|------------------------|------------------------------------------------------------------------------------------------------------------------------|
| **Axel F**      | Beverly Hills Cop (Faltermeyer) | Cocky, playful swagger | **Born for synthesis.** Every stage transforms it recognizably. Square wave + punchy ADSR + filter resonance = rubber bounce |
| **Blue Monday** | New Order                       | Cool, relentless       | THE synth riff. Each stage reveals how the iconic sound was built                                                            |
| **Sandstorm**   | Darude                          | Euphoric, meme energy  | 3-note sawtooth lead. Simplicity becomes enormity through synthesis                                                          |

### Jazz / Latin / World

| Melody           | Source                          | Emotional Color       | Synth Fitness                                                                                        |
|------------------|---------------------------------|-----------------------|------------------------------------------------------------------------------------------------------|
| **Libertango**   | Piazzolla                       | Passionate, dangerous | Staccato ADSR, PWM = accordion quality, HP filter keeps it cutting                                   |
| **Take Five**    | Brubeck                         | Sophisticated, cool   | 5/4 time is ear-catching; sax melody range perfect for filter sweeps                                 |
| **Misirlou**     | Traditional (Dick Dale version) | Intense, urgent       | Phrygian dominant mode — fills the Middle Eastern gap. Filter resonance on quarter-tones is electric |
| **Mas Que Nada** | Jorge Ben                       | Syncopated joy        | Bossa nova hook sounds incredible through filter sweeps                                              |
| **Malaika**      | Swahili traditional             | Tender, haunting      | Pentatonic — transforms beautifully through every stage. The African "Scarborough Fair"              |

### Video Game / Cross-Generational

| Melody              | Source                            | Emotional Color     | Synth Fitness                                                                                   |
|---------------------|-----------------------------------|---------------------|-------------------------------------------------------------------------------------------------|
| **Baba Yetu**       | Civilization IV (Christopher Tin) | Triumphant, human   | Pentatonic call-and-response works on simple waveforms. Nothing else delivers joy at this scale |
| **Megalovania**     | Undertale (Toby Fox)              | Determined, intense | Square-wave native. Every synthesis stage bites. Generation-bridging                            |
| **Lupin III Theme** | (Yuji Ohno)                       | Jazz-funk swagger   | Walking bassline in oscillators, brass stabs through filters, wah-guitar in FM                  |

### Iconic Riffs

| Melody                | Source        | Emotional Color    | Synth Fitness                                                               |
|-----------------------|---------------|--------------------|-----------------------------------------------------------------------------|
| **Seven Nation Army** | White Stripes | Anthemic, massive  | 7 notes. Monophonic. Already sounds like a synth line. FM makes it enormous |
| **Inspector Gadget**  | (Saban)       | Playful, chromatic | Chromatic runs, staccato hits, filter-sweep native                          |

### Black Hat's Critical Filter

> "Half of the emotionally compelling picks are 'sounds beautiful on piano' picks, not 'sounds beautiful on a
> synthesizer' picks. The gate test isn't 'does this melody move people?' — it's 'does this melody move people when played
> on a raw oscillator with a filter sweep?'"

**Killed by this test:** Comptine d'un autre été, One Summer's Day — both are piano-dependent. Beautiful through reverb,
lifeless on a saw wave.

**Passed with flying colors:** Axel F (synth-native), Libertango (staccato rhythmic drive), Misirlou (modal intensity),
Megalovania (chiptune DNA), Seven Nation Army (monophonic power).

---

## Complete Candidate Scorecard

All 30+ proposed melodies rated across the 5 synthesis stages. Rating: **Y** = strong fit, **P** = partial (works but
not ideal), **N** = poor fit.

Sorted by overall synthesis fitness (number of Y ratings), then by emotional impact.

| #  | Melody                              | Source / Origin          | Emotional Color         | Osc | Filter | ADSR | FM | Effects | Score | Rights               |
|----|-------------------------------------|--------------------------|-------------------------|-----|--------|------|----|---------|-------|----------------------|
| 1  | **Axel F**                          | Beverly Hills Cop        | Playful swagger         | Y   | Y      | Y    | Y  | Y       | 5/5   | Licensed             |
| 2  | **Greensleeves**                    | English trad. ~1580      | Melancholic, aching     | Y   | Y      | Y    | Y  | Y       | 5/5   | Public domain        |
| 3  | **Korobeiniki (Tetris)**            | Russian folk, 1861       | Driving, intense        | Y   | Y      | Y    | Y  | Y       | 5/5   | Public domain        |
| 4  | **Sakura Sakura**                   | Japanese trad., Edo      | Haunting, contemplative | Y   | Y      | Y    | Y  | Y       | 5/5   | Public domain        |
| 5  | **Scarborough Fair**                | English trad., medieval  | Wistful, pastoral       | Y   | Y      | Y    | Y  | Y       | 5/5   | Public domain        |
| 6  | **Libertango**                      | Piazzolla, 1974          | Passionate, dangerous   | Y   | Y      | Y    | Y  | Y       | 5/5   | Licensed             |
| 7  | **Megalovania**                     | Undertale, 2015          | Determined, intense     | Y   | Y      | Y    | Y  | Y       | 5/5   | Licensed             |
| 8  | **Misirlou**                        | Trad. (Dick Dale ver.)   | Urgent, fiery           | Y   | Y      | Y    | Y  | Y       | 5/5   | Public domain (trad) |
| 9  | **When Johnny Comes Marching Home** | American Civil War, 1863 | Defiant melancholy      | Y   | Y      | Y    | Y  | Y       | 5/5   | Public domain        |
| 10 | **Malaika**                         | Swahili trad.            | Tender, haunting        | Y   | Y      | Y    | Y  | Y       | 5/5   | Public domain        |
| 11 | **Hall of the Mountain King**       | Grieg, 1875              | Building tension, eerie | Y   | Y      | P    | Y  | Y       | 4/5   | Public domain        |
| 12 | **Danse Macabre**                   | Saint-Saens, 1874        | Macabre whimsy          | Y   | P      | Y    | Y  | Y       | 4/5   | Public domain        |
| 13 | **Habanera (Carmen)**               | Bizet, 1875              | Seductive danger        | Y   | Y      | Y    | Y  | P       | 4/5   | Public domain        |
| 14 | **Kalinka**                         | Russian folk, 1860       | Reckless joy            | Y   | Y      | Y    | Y  | P       | 4/5   | Public domain        |
| 15 | **Baba Yetu**                       | Civ IV, Christopher Tin  | Triumphant, human       | Y   | P      | P    | P  | Y       | 4/5   | Licensed             |
| 16 | **Take Five**                       | Brubeck, 1959            | Sophisticated, cool     | Y   | Y      | Y    | P  | Y       | 4/5   | Licensed             |
| 17 | **Seven Nation Army**               | White Stripes, 2003      | Anthemic, massive       | Y   | Y      | P    | Y  | Y       | 4/5   | Licensed             |
| 18 | **La Cucaracha**                    | Mexican trad.            | Raucous joy             | Y   | P      | Y    | Y  | Y       | 4/5   | Public domain        |
| 19 | **Blue Monday**                     | New Order, 1983          | Cool, relentless        | Y   | Y      | Y    | P  | Y       | 4/5   | Licensed             |
| 20 | **Inspector Gadget**                | Saban, 1983              | Playful, chromatic      | Y   | Y      | Y    | Y  | P       | 4/5   | Licensed             |
| 21 | **Lupin III Theme**                 | Yuji Ohno, 1977          | Jazz-funk swagger       | Y   | Y      | P    | Y  | Y       | 4/5   | Licensed             |
| 22 | **Mas Que Nada**                    | Jorge Ben, 1963          | Syncopated joy          | Y   | Y      | P    | P  | Y       | 3/5   | Licensed             |
| 23 | **Mundian To Bach Ke**              | Panjabi MC, 1998         | Driving, dhol energy    | Y   | Y      | P    | P  | Y       | 3/5   | Licensed             |
| 24 | **Sandstorm**                       | Darude, 1999             | Euphoric, meme          | Y   | Y      | P    | Y  | P       | 3/5   | Licensed             |
| 25 | **Merry Go Round of Life**          | Howl's Castle, Hisaishi  | Soaring, bittersweet    | P   | P      | Y    | P  | P       | 3/5   | Licensed             |
| 26 | **Dies Irae**                       | Gregorian, 13th c.       | Existential dread       | Y   | P      | P    | Y  | Y       | 3/5   | Public domain        |
| 27 | **Wayfaring Stranger**              | American folk, pre-1880  | Deeply melancholic      | Y   | Y      | P    | Y  | Y       | 4/5   | Public domain        |
| 28 | **Sailor's Hornpipe**               | English trad.            | Bouncy, energetic       | Y   | Y      | Y    | P  | P       | 3/5   | Public domain        |
| 29 | **Nokia Tune (Gran Vals)**          | Tarrega, 1902            | Nostalgic, meme         | Y   | P      | P    | P  | P       | 2/5   | Public domain        |
| 30 | **Comptine d'un autre été**         | Amélie, Tiersen, 2001    | Aching nostalgia        | P   | P      | P    | N  | Y       | 2/5   | Licensed             |
| 31 | **One Summer's Day**                | Spirited Away, Hisaishi  | Crystalline sadness     | P   | P      | Y    | P  | Y       | 2/5   | Licensed             |
| 32 | **Philip Glass Glassworks**         | Glass, 1982              | Hypnotic, minimal       | P   | Y      | P    | P  | Y       | 2/5   | Licensed             |
| 33 | **Donna Lee**                       | Charlie Parker, 1947     | Bebop virtuosity        | Y   | Y      | P    | Y  | P       | 3/5   | Licensed             |
| 34 | **Shave and a Haircut**             | Vaudeville, pre-1900     | Cheeky, irreverent      | Y   | P      | Y    | P  | Y       | 3/5   | Public domain        |
| 35 | **Taps**                            | US military, 1862        | Solemn, reverent        | Y   | P      | P    | P  | Y       | 2/5   | Public domain        |
| 36 | **Ode to Joy**                      | Beethoven, 1824          | Triumphant, uplifting   | Y   | Y      | N    | N  | P       | 2/5   | Public domain        |
| 37 | **Twinkle Twinkle**                 | Traditional, ~1761       | Innocent, simple        | Y   | N      | N    | P  | P       | 1/5   | Public domain        |
| 38 | **Mary Had a Little Lamb**          | Traditional              | Childlike               | P   | N      | N    | N  | N       | 0/5   | Public domain        |

**Reading the table:**

- **5/5** = works brilliantly across all synthesis stages — top candidates
- **4/5** = strong with one soft spot — excellent alternates
- **3/5** = good for specific stages, weaker elsewhere — stage-specific showcases
- **2/5** = emotionally compelling but synth-hostile — piano/orchestral pieces that need effects to shine
- **1/5 and below** = fail the gate test — nursery rhymes with uniform rhythm and narrow range

---

## Final Tiered Recommendation

### Tier 1: "Ship With These" — 7 Melodies, 7 Emotional Colors

| # | Melody                        | Emotional Slot   | Rights        | Why This One                                                                        |
|---|-------------------------------|------------------|---------------|-------------------------------------------------------------------------------------|
| 1 | **Greensleeves**              | Melancholic      | Public domain | Safest all-rounder. Passes all 5 synthesis gates. Universally known. Aching beauty. |
| 2 | **Hall of the Mountain King** | Building tension | Public domain | Progressive complexity demo. Chromatic = filter dream. Eerie march rhythm.          |
| 3 | **Sakura Sakura**             | Haunting         | Public domain | Highest "whoa" factor. FM = temple bells. Culturally distinctive.                   |
| 4 | **Axel F**                    | Playful swagger  | Licensed      | Synth-native. Born for this. Every stage transforms it recognizably.                |
| 5 | **Habanera (Carmen)**         | Seductive        | Public domain | Chromatic descent = filter sweep baked into the melody. Smoky danger.               |
| 6 | **Dies Irae**                 | Eerie/cinematic  | Public domain | 4-note motif everyone knows subconsciously (film scores). Pure timbre lens.         |
| 7 | **Baba Yetu**                 | Triumphant       | Licensed      | Nothing else delivers this joy. Pentatonic call-and-response = synth-friendly.      |

**Emotional range covered:** melancholic — building — haunting — playful — seductive — eerie — triumphant

**Legal baseline:** 5 public domain + 2 licensed. If licensing fails: swap Axel F -> Korobeiniki (playful, public
domain), Baba Yetu -> Scarborough Fair (uplifting modal, public domain).

### Tier 2: "Strong Alternates" — Fill Gaps or Swap In

| Melody                   | Fills Gap / Replaces                   | Rights               |
|--------------------------|----------------------------------------|----------------------|
| **Korobeiniki (Tetris)** | Playful fallback for Axel F            | Public domain        |
| **Misirlou**             | Intensity/urgency, Middle Eastern mode | Public domain (trad) |
| **Libertango**           | Passionate/dangerous, rhythmic drive   | Licensed             |
| **Danse Macabre**        | Eerie alternate for Dies Irae          | Public domain        |
| **Take Five**            | Sophisticated/cool, 5/4 rhythm         | Licensed             |
| **Scarborough Fair**     | Wistful/modal, fallback for Baba Yetu  | Public domain        |
| **Megalovania**          | Gaming generation bridge               | Licensed             |

### Tier 3: Phase 2 / Themed Packs

- **Ghibli Pack:** Merry Go Round of Life, One Summer's Day (license together)
- **Synth Classics Pack:** Blue Monday, Sandstorm, Inspector Gadget
- **World Music Pack:** Malaika, Mas Que Nada, Mundian To Bach Ke, Kalinka
- **Gaming Pack:** Megalovania, Lupin III, Korobeiniki
- **Riff Pack:** Seven Nation Army, Donna Lee, Philip Glass Glassworks

---

## Key Insights (All Rounds Combined)

1. **"Design for adults that kids also enjoy"** — This single principle eliminated all nursery rhymes and reframed every
   choice. The best candidates (Greensleeves, Sakura, Danse Macabre, Libertango) have genuine emotional weight that
   respects the listener.

2. **Synth-native melodies outperform piano-native melodies** — Black Hat's critical filter: "Does this melody move
   people when played on a raw oscillator with a filter sweep?" Beautiful piano pieces (Comptine, One Summer's Day) fail
   this test. Axel F, Libertango, Megalovania pass it.

3. **Emotional variety is non-negotiable** — A playground with 7 melancholic waltzes teaches synthesis but bores users.
   Tier 1 covers: melancholic, building, haunting, playful, seductive, eerie, triumphant. No two fill the same slot.

4. **Cultural breadth matters** — Sakura, Habanera, Baba Yetu, Misirlou ensure the playground doesn't feel exclusively
   Western European. Phase 2 packs (Malaika, Mas Que Nada, Mundian To Bach Ke) expand further.

5. **"Cultural reflexes" are powerful** — Green Hat's insight: some melodies bypass taste entirely and hit shared
   memory (Tetris, Axel F, Seven Nation Army). These create instant engagement regardless of musical background.
