# Tutorial Master Plan

**Date:** 2026-04-07 | **Status:** Under consideration

## Philosophy

- Beginner-friendly SHORT tutorials for each topic first; higher levels combine concepts.
- Every concept-tutorial teaches through a genre. Genre showcases ("Make a ...") build complete pieces.
- Some topics (Ignitor DSL, FM synthesis) are inherently advanced — no beginner version needed.

## Design Principles

**The tutorials must not feel mechanical.** A little surprise here and there makes them breathe.

1. **Natural fit first** — The genre is where the concept genuinely shines. Genres CAN repeat — multiple acid house or
   jazz tutorials is fine. Don't force-fit for uniqueness.
2. **Aim for breadth, don't force it** — Genre diversity is a goal, not a constraint.
3. **Teach through the genre** — The genre isn't wallpaper; it's the reason the concept exists.
4. **Versatility as proof** — The full tutorial list should read like a world tour of music.
5. **Intentional genre misfits welcome** — A rock riff on flute and harp, metal on marimba, jazz on chiptune. Deliberate
   mismatches are playful and teach that patterns transcend their "correct" instruments.
6. **Beginner-friendly first** — Even genres like Acid House or Grime get an accessible introduction.

---

## Coverage Gap Summary

Of ~180 implemented concepts, only ~14% are well-covered by existing tutorials. ~67% have zero coverage.

### Critical Gaps (no beginner tutorial exists)

| Gap                             | Concepts                                           | Impact                                      |
|---------------------------------|----------------------------------------------------|---------------------------------------------|
| Continuous signals & modulation | `sine`, `saw`, `rand`, `range()`, `segment()`      | How you make sound MOVE — biggest gap       |
| Randomness & probability        | `sometimes()`, `choose()`, `degradeBy()`, `seed()` | What makes music feel alive                 |
| Sample manipulation             | `begin()`, `end()`, `speed()`, `cut()`, `slice()`  | Everyone using samples needs this           |
| Pattern arithmetic              | `add()`, `mul()`, `sub()`                          | Dynamic transposition, velocity scaling     |
| Swing & groove                  | `swing()`, `swingBy()`, `early()`, `late()`        | Makes beats feel human                      |
| Euclidean rhythms               | `euclid()`, `bjork()`                              | Only in Pro mini-notation, not as functions |
| Orbit system                    | `orbit()`, cylinders                               | Used everywhere, never explained            |
| struct/mask                     | `struct()`, `mask()`                               | Core "separate rhythm from pitch" technique |
| Probability family              | `sometimes()`, `often()`, `rarely()`               | Most musical functions, completely absent   |

### ADV-ONLY Gaps (need beginner intro)

`jux()`, `superimpose()`, `layer()`, `rev()`, `off()`, `vibrato()`, `tremolo()`, `phaser()`

### Missing Effects & Synthesis

Filter envelopes, pitch envelope, FM synthesis, `compressor()`, `duckorbit()`, `vowel()`, `distortshape()`,
`iresponse()`, `mode()`, `bank()`, `freq()`, `accelerate()`, `unison()`, `detune()`

### Missing Pattern Functions

`early()`/`late()`, `iter()`/`iterBack()`, `palindrome()`, `within()`, `echo()`/`stut()`, `polymeter()`, `squeeze()`/
`bite()`, `choose()`, `inhabit()`, `morse()`, `seed()`

### Covered Well (no tutorial needed)

`sound()`, `note()`, `stack()`, `fast()`/`slow()`, `every()`, `cat()`, `arrange()`, `scale()`, `chord()`, `voicing()`,
`transpose()`, `lpf()`, `hpf()`, `gain()`, `pan()`, `delay()`, `room()`, `adsr()`, `shuffle()`/`scramble()`, `[]`
grouping, `<>` alternation

---

## New Concept Tutorials (36)

### Phase 1: Critical Beginner Gaps — Make FIRST

| #   | Title                           | Level    | Concepts                                    | Genre                  | Why this genre                                                                                                  |
|-----|---------------------------------|----------|---------------------------------------------|------------------------|-----------------------------------------------------------------------------------------------------------------|
| H1  | Make It Move — Signals          | Beginner | `sine`, `saw`, `rand`, `range()`            | **Synthpop**           | Synthpop IS modulation — pulsing filters, opening pads (Depeche Mode, Kraftwerk)                                |
| H2  | Roll the Dice — Randomness      | Beginner | `choose()`, `degradeBy()`, `?`              | **Minimal Techno**     | Built on repetition with micro-variation (Richie Hawtin). Spare textures make every random event audible        |
| H3  | Chop and Screw — Samples        | Beginner | `begin()`, `end()`, `speed()`, `cut()`      | **Trip-Hop**           | Built on slowing vinyl, trimming snippets (Massive Attack, Portishead). Makes sample manipulation feel artistic |
| H4  | Groove and Swing                | Beginner | `swing()`, `swingBy()`, `early()`, `late()` | **Boom-Bap Hip-Hop**   | MPC swing IS the boom-bap sound (DJ Premier, J Dilla). Most dramatic swing-on/off A/B of any genre              |
| H5  | Euclidean Beats                 | Beginner | `euclid()`, `euclidRot()`, `bjork()`        | **Afrobeat**           | Not metaphor — Toussaint proved West African bell patterns ARE euclidean. `euclid(5,8)` = Ghanaian bell         |
| H6  | Pattern Templates — struct/mask | Beginner | `struct()`, `mask()`                        | **Reggaeton**          | The dembow rhythm IS a `struct()` template applied to every instrument. One rhythm, many sounds                 |
| H7  | The Sometimes Family            | Beginner | `sometimes()`, `often()`, `rarely()`        | **Jazz**               | Jazz improvisation IS probability. `sometimes(add(7))` = occasionally add a 7th, like a real pianist            |
| H8  | Orbits Explained                | Beginner | `orbit()`, cylinders, per-orbit effects     | **Dub Reggae**         | King Tubby invented dub by routing through effect buses. `orbit()` IS a dub mixing desk                         |
| H9  | Pattern Math                    | Beginner | `add()`, `mul()`, `sub()`                   | **Neo-Soul**           | Reharmonizing with `add()` is how neo-soul keyboardists work (D'Angelo, Glasper)                                |
| H10 | Mini-Notation Power-Ups         | Beginner | `*n`, `/n`, `?`, `\|`, `@n`, `!n`           | **UK Garage / 2-Step** | Skippy, irregular beats — `*n` doubles hats, `?` drops kicks, `@2` elongates bass                               |

### Phase 2: Beginner Intros for Advanced Concepts

| #  | Title                   | Level    | Concepts                             | Genre                | Why this genre                                                                                          |
|----|-------------------------|----------|--------------------------------------|----------------------|---------------------------------------------------------------------------------------------------------|
| M1 | Stereo Tricks — jux/off | Beginner | `jux()`, `juxBy()`, `off()`          | **Shoegaze**         | MBV's wall of sound IS `jux(rev)` — forwards + backwards in stereo                                      |
| M2 | Layer It Up             | Beginner | `superimpose()`, `layer()`           | **Gospel**           | Choir harmonizing = `superimpose(transpose(7))`. From solo voice to congregation                        |
| M3 | Modulation Effects      | Beginner | `vibrato()`, `tremolo()`, `phaser()` | **Psychedelic Rock** | Hendrix's phaser, Floyd's tremolo — these effects were invented by the psych movement                   |
| M4 | Reverse and Rotate      | Beginner | `rev()`, `iter()`, `palindrome()`    | **Baroque**          | Bach wrote retrogrades (`rev`), canons (`iter`), palindromes. 300-year-old techniques with modern names |

### Phase 3: Intermediate Depth

| #   | Title                 | Level        | Concepts                              | Genre            | Why this genre                                                                  |
|-----|-----------------------|--------------|---------------------------------------|------------------|---------------------------------------------------------------------------------|
| M5  | Dynamic Filters       | Intermediate | `lpenv()`, `lpadsr()`, filter ADSR    | **Acid House**   | TB-303 squelch IS a filter envelope. The genre was born from this one parameter |
| M6  | Scale Modes           | Intermediate | `mode()`, `scale()` modes             | **Flamenco**     | Phrygian mode = instant Spain. Most viscerally clear mode demonstration         |
| M7  | Pitch Bends and Drops | Intermediate | `accelerate()`, `penv()`, `panchor()` | **Grime**        | Wiley's "Eskimo" sound = pitch envelopes on bass. Every hit dives               |
| M8  | Sidechain Pumping     | Intermediate | `duckorbit()`, `duckattack()`         | **French House** | Daft Punk's Alesis 3630 pumping defined the genre                               |
| L13 | Sound Banks           | Beginner     | `bank()`, browsing sounds             | **Reggae**       | Reggae has iconic, specific sounds — teaches why banks matter                   |

### Phase 4: Specialized Topics

| #   | Title              | Level        | Concepts                         | Genre             | Why this genre                                                         |
|-----|--------------------|--------------|----------------------------------|-------------------|------------------------------------------------------------------------|
| L1  | Sample Slicing     | Intermediate | `slice()`, `splice()`, `loop()`  | **Jungle**        | Slicing the Amen break at 160 BPM IS jungle                            |
| L2  | Compressor         | Intermediate | `compressor()`                   | **Punk Rock**     | Punk is LOUD — compression as weapon, not subtlety                     |
| L3  | Convolution Reverb | Intermediate | `iresponse()`, `ir()`            | **Neo-Classical** | Nils Frahm's music lives and dies by the space (cathedral vs bedroom)  |
| L4  | Vowel Sounds       | Intermediate | `vowel()`                        | **Funk**          | Wah-wah pedal IS a formant sweep. "Wacka-wacka" = vowel cycling        |
| L5  | Distortion Shapes  | Intermediate | `distortshape()`, all 9 shapes   | **Industrial**    | NIN treats distortion as composition. 9 shapes = 9 different beasts    |
| L6  | Seeds              | Intermediate | `seed()`, `withSeed()`           | **Krautrock**     | Motorik beat = seeded pattern. Generative but reproducible (NEU!, Can) |
| L7  | within() Trick     | Intermediate | `within()`, `inside()`           | **Bossa Nova**    | Guitar pattern: bass in first half, chord in second = `within()`       |
| L10 | Echo Patterns      | Intermediate | `echo()`, `stut()`, `stutWith()` | **Dancehall**     | "Ba-ba-ba-badman" stutters define the genre                            |
| L12 | Morse Code Music   | Beginner     | `morse()`                        | **Chiptune**      | Morse = bleeps, chiptune = bleeps. Fun, playful, great for kids        |
| L14 | Frequency Land     | Intermediate | `freq()`                         | **Drone / Doom**  | Raw Hz — beating frequencies, harmonic series, physics as music        |

### Phase 5: Power User / Inherently Advanced

| #   | Title                        | Level    | Concepts                          | Genre           | Why this genre                                                                |
|-----|------------------------------|----------|-----------------------------------|-----------------|-------------------------------------------------------------------------------|
| L8  | Polymeter                    | Advanced | `polymeter()`, `polymeterSteps()` | **Balkan Folk** | Odd meters (7/8, 11/8) layered against 4/4. Polymeter IS the default here     |
| L9  | Advanced Picking             | Advanced | `inhabit()`, `pickSqueeze()`      | **Salsa**       | Section switching (montuno→mambo→coro) = `inhabit()`. Conducting a Latin band |
| L11 | Squeeze and Bite             | Advanced | `squeeze()`, `bite()`, `press()`  | **Glitch**      | Autechre-style time fracturing. These functions ARE glitch tools              |
| A1  | Ignitor DSL                  | Pro      | Ignitor tree, Param/Const/Freq    | **Synthwave**   | Building a Juno-106 in code. Synth worship as design goal                     |
| A2  | FM Synthesis                 | Pro      | `fmh()`, `fmenv()`, FM theory     | **80s Pop**     | DX7 = 80s pop. "THAT'S how you make that electric piano!"                     |
| A3  | Super-Oscillator Masterclass | Advanced | SuperSaw, voices, freqSpread      | **Trance**      | JP-8000 supersaw IS trance. Every euphoric breakdown for a decade             |
| A4  | Physical Modeling            | Advanced | Pluck, SuperPluck, Karplus-Strong | **Bluegrass**   | KS simulates plucked strings. Banjo, guitar, mandolin — can it sound real?    |

---

## Genre Showcase Tutorials ("Make a ...")

Complete pieces combining multiple concepts. Created AFTER their prerequisites exist.

### Beginner Showcases

| #  | Title                | Genre   | Combines                           | After    |
|----|----------------------|---------|------------------------------------|----------|
| G1 | Make a Reggae Groove | Reggae  | sound, stack, gain, orbit, offbeat | H8       |
| G2 | Make a Hip-Hop Beat  | Hip-Hop | swing, samples, gain, pan          | H4       |
| G3 | Make a House Track   | House   | 4-on-floor, stack, lpf, fast       | existing |

### Intermediate Showcases

| #   | Title                  | Genre       | Combines                              | After    |
|-----|------------------------|-------------|---------------------------------------|----------|
| G4  | Make an Acid Beat      | Acid House  | filter env, distort, swing, resonance | M5       |
| G5  | Make a D&B Banger      | Drum & Bass | fast, samples, slice, hpf             | L1, H3   |
| G6  | Make a Dub Plate       | Dub         | orbit, delay, room, duck              | H8, M8   |
| G7  | Make a Trap Beat       | Trap        | fast hats (*n), 808 sub, swing        | H4, H10  |
| G8  | Make a Bossa Nova      | Bossa Nova  | within, chord, voicing, legato        | L7       |
| G9  | Make It Funky          | Funk        | vowel, swing, gain dynamics           | L4, H4   |
| G10 | Make a Synthwave Track | Synthwave   | supersaw, adsr, delay, arpeggios      | existing |
| G11 | Make a Trip-Hop Piece  | Trip-Hop    | samples, speed, slow, room            | H3       |

### Advanced Showcases

| #   | Title                   | Genre             | Combines                                | After   |
|-----|-------------------------|-------------------|-----------------------------------------|---------|
| G12 | Make a String Quartet   | Classical Chamber | Pluck, chord, voicing, legato, rev      | A4, M4  |
| G13 | Make a Metal Riff       | Metal             | distort(hard), fast, power chords, comp | L5, L2  |
| G14 | Let It Rock             | Rock              | distort, stack, gain, drum grooves      | L5      |
| G15 | Make a Jazz Combo       | Jazz              | sometimes, chord, voicing, swing, jux   | H7, H4  |
| G16 | Make a Jungle Track     | Jungle            | slice, fast, stut, echo, breaks         | L1, L10 |
| G17 | Make a Trance Anthem    | Trance            | SuperSaw, filter sweep, arrange, duck   | A3, M8  |
| G18 | Make an Afrobeat Jam    | Afrobeat          | euclid, polymeter, stack, percussion    | H5, L8  |
| G19 | Make a Cinematic Score  | Film Score        | legato, room, ir, slow attacks, arrange | L3      |
| G20 | Make a Dancehall Riddim | Dancehall         | stut, echo, struct, samples, swing      | L10, H6 |

### Pro Showcases

| #   | Title                    | Genre          | Combines                               | After          |
|-----|--------------------------|----------------|----------------------------------------|----------------|
| G21 | Make a Complete EDM Drop | EDM/Big Room   | arrange, duck, supersaw, filter, every | multiple       |
| G22 | Make Generative Ambient  | Ambient        | signals, seed, sometimes, room, ir     | H1, H7, L6, L3 |
| G23 | Make an FM Piano Ballad  | Ballad/80s Pop | FM, chord, voicing, room, arrange      | A2             |
| G24 | Make a Live Coding Set   | Live Coding    | every, sometimes, jux, superimpose     | multiple       |

---

## Cross-Genre Fusion Ideas (Future)

| Title                | Genres Combined        | Prerequisites |
|----------------------|------------------------|---------------|
| Afro-Acid            | Afrobeat + Acid House  | H5, M5        |
| Dub Step-by-Step     | Dub Reggae + Grime     | H8, M7        |
| Bossa Beats          | Bossa Nova + Lo-Fi     | L7, existing  |
| Gospel Trance        | Gospel + Trance        | M2, A3        |
| Punk Funk            | Punk + Funk            | L2, L4        |
| Balkan Breaks        | Balkan Folk + Jungle   | L8, L1        |
| Neo-Classical Glitch | Neo-Classical + Glitch | L3, L11       |
| Synthwave Jazz       | Synthwave + Jazz       | A1, H7        |

---

## Genre Coverage (60+ genres across 97 tutorials)

**Existing (37 tuts):** Ambient (5+), Generative/IDM (4+), Electronica (4+), Techno/House (3), Lo-Fi Hip-Hop, Cinematic,
Dub Techno, Jazz, Deep House, Glitch Hop, Breakbeat

**New concept tutorials add:** Synthpop, Minimal Techno, Trip-Hop, Boom-Bap, Afrobeat, Reggaeton, Jazz, Dub Reggae,
Neo-Soul, UK Garage, Shoegaze, Gospel, Psych Rock, Baroque, Acid House, Flamenco, Grime, French House, Reggae, Jungle,
Punk, Neo-Classical, Funk, Industrial, Krautrock, Bossa Nova, Balkan Folk, Salsa, Dancehall, Glitch, Synthwave, 80s Pop,
Trance, Bluegrass, Chiptune, Drone/Doom

**Genre showcases add:** Hip-Hop, House, Drum & Bass, Trap, Dub, Classical Chamber, Metal, Rock, Film Score, EDM/Big
Room, Ballad, Live Coding

## Remaining Uncovered Concepts

These didn't get dedicated tutorials — fold into related tutorials or leave for future:

`s_cat()`/`timeCat()`, `stackLeft/Right/Centre/By`, `pure()`, `gap()`, `run()`/`binary()`, `pickF()`, `pace()`/
`steps()`, `take()`/`drop()`, `repeatCycles()`, `linger()`, `ribbon()`, `clip()`, `postgain()`, advanced reverb params (
`roomfade`, `roomlp`, `roomdim`), bitwise operators, `compress()`/`focus()`/`zoom()`
