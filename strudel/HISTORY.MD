# Strudel Kotlin Port - Implementation History

This document tracks major milestones and features implemented in the Kotlin port of Strudel.

---

## 🎯 Major Architectural Achievements

### Rational Number Time System ✅

Replaced floating-point time coordinates with exact rational arithmetic, eliminating timing drift bugs and enabling
mathematically exact pattern calculations. Fixed Euclidean pattern timing issues.

### QueryContext System ✅

Context-passing mechanism enabling transformation order independence. Patterns like `sine.slow(2).range(0,100)` now work
correctly regardless of composition order.

### VoiceValue Type System ✅

Type-safe value handling with sealed interface supporting arithmetic, bitwise, comparison, and logical operators.
Replaces generic `Any?` for pattern values.

### Control Pattern Support ✅

Dynamic parameters for tempo, structure, randomness, and conditionals. Most numerical functions accept control patterns
for evolving behaviors.

---

## 🎵 Mini-Notation Parser

**Complete implementation:**

- Basic: sequences, sub-sequences `[...]`, alternation `<...>`, parallel `,`, speed `*` `/`
- Rests: `~` and `-`
- Sample selection: `:n` notation
- Timing: elongation `@n`, replication `!n`
- Probabilistic: `?` removal, `|` choice
- Euclidean rhythms: `(pulses, steps)` with optional rotation
- Parser cache for performance optimization

---

## 🎨 Pattern Creation & Manipulation

**Core Constructors:**
`seq`, `mini`, `stack`, `cat`, `fastcat`, `slowcat`, `slowcatPrime`, `arrange`, `polymeter`, `polyrhythm`, `pure`,
`silence`/`rest`, `run`, `binary` family

**Time Modification:**
`fast`, `slow`, `early`, `late`, `rev`, `zoom`, `compress`, `focus`, `gap`, `fastGap`, `ply`, `hurry`, `loopAt`,
`swing`/`swingBy`

**Euclidean Rhythms:**
`euclid`, `euclidRot`, `bjork`, `euclidLegato`, `euclidLegatoRot`, `euclidish`/`eish` (all with control pattern support)

**Transformations:**
`struct`, `structAll`, `mask`, `maskAll`, `superimpose`, `layer`, `apply`, `palindrome`, `inside`, `outside`, `bite`,
`jux`, `juxBy`, `off`

---

## 🎲 Random & Probabilistic

**Event Manipulation:**
`degrade`, `degradeBy`, `undegrade`, `undegradeBy` (with pattern control)

**Conditional Transformations:**
`sometimes`, `sometimesBy`, `often`, `rarely`, `almostAlways`, `almostNever`, `always`, `never`, `someCycles`,
`someCyclesBy`

**Cycle-Based:**
`firstOf`/`every`, `lastOf` (with control pattern support)

**Filtering:**
`filter`, `filterWhen`, `bypass`

**Seeding:**
`seed`/`withSeed` for deterministic randomness

---

## 🎯 Pattern Picking & Selection

**Choose Functions (11):**
`choose`, `chooseWith`, `chooseIn`, `chooseInWith`, `chooseOut`, `choose2`, `chooseCycles`/`randcat`, `wchoose`,
`wchooseCycles`/`wrandcat`

**Pick Functions (20 total, 4 remaining):**

- **Inner Join**: `pick`, `pickmod` (maintains inner timing)
- **Outer Join**: `pickOut`, `pickmodOut` (selector timing)
- **Restart**: `pickRestart`, `pickmodRestart` (global restart from cycle 0)
- **Squeeze**: `inhabit`/`pickSqueeze`, `inhabitmod`/`pickmodSqueeze`, `squeeze` (compress into events)
- **Not Yet**: `pickReset`, `pickmodReset` (cycle-synchronized reset), `pickF`, `pickmodF` (function application)

---

## 🌊 Continuous Patterns & Oscillators

**Waveforms (unipolar 0-1):**
`sine`, `cosine`, `saw`, `isaw`, `tri`, `itri`, `square`

**Waveforms (bipolar -1 to 1):**
`sine2`, `cosine2`, `saw2`, `isaw2`, `tri2`, `itri2`, `square2`

**Noise:**
`perlin`, `perlin2`, `berlin`, `berlin2` (seeded for determinism)

**Random:**
`rand`, `rand2`, `brand`, `brandBy`, `irand`, `randL`, `randrun`, `shuffle`, `scramble`

**Utilities:**
`steady`, `signal`, `time`, `range`, `rangex`, `range2`, `segment`/`seg`, `toBipolar`, `fromBipolar`, `round`, `floor`,
`ceil`, `ratio`

---

## 🎛️ Audio Parameters & Effects

**Amplitude & Dynamics:**
`gain`, `pan`, `legato`/`clip`

**ADSR Envelopes:**
`attack`, `decay`, `sustain`, `release`, `adsr`

**Filters:**
`lpf`/`cutoff`/`ctf`/`lp`, `hpf`/`hp`/`hcutoff`, `bpf`/`bp`, `notchf` with resonance: `lpq`/`resonance`/`res`, `hpq`/
`hresonance`/`hres`, `bpq`/`bandq`, `nresonance`/`nres`

**Filter Envelopes:**
Complete ADSR envelopes for lowpass, highpass, bandpass, and notch filters (e.g., `lpattack`, `lpdecay`, `lpsustain`,
`lprelease`, `lpenv`) with full audio engine integration

**Distortion & Degradation:**
`distort`/`dist`, `crush`, `coarse`

**Spatial Effects:**
`room`, `roomsize`/`rsize`, `delay`, `delaytime`, `delayfeedback`/`delayfb`/`dfb`

**Modulation:**
`vibrato`/`vib`, `vibratoMod`/`vibmod`, `accelerate`

**Synthesis:**
`unison`/`uni`, `detune`, `spread`, `density`/`d`, `warmth` (KLANG extension for oscillator brightness control)

---

## 🎼 Tonal & Musical

**Note Control:**
`note`, `n`, `freq`, `scale`, `transpose`, `scaleTranspose`

**Harmonic Functions:**
`chord` (112 chord types - all Strudel notations), `voicing` (stateful voice leading), `rootNotes` (bass extraction)

**Sound Selection:**
`sound`/`s`, `bank`, `begin`, `end`, `speed`, `loop`, `cut`, `slice`

**Routing:**
`orbit`

**Chord Notation Support:**
All 90 Strudel chord notations including: major (^, M), minor (m, -), diminished (o, dim), half-diminished (h, ø),
suspended (sus, sus2, sus4), altered dominants (7#9b5, 7b9#9, 7alt), and complex extensions (maj7#11, m^9, -M7, etc.)

---

## 🔢 Arithmetic & Logic

**Arithmetic:**
`add`, `sub`, `mul`, `div`, `mod`, `pow`, `log2`

**Bitwise:**
`band`, `bor`, `bxor`, `blshift`, `brshift`

**Comparison:**
`lt`, `gt`, `lte`, `gte`, `eq`, `eqt`, `ne`, `net`

**Logical:**
`and`, `or`

**Arithmetic Addons (Non-Strudel):**
`flipSign`, `oneMinusValue`, `not`

**Structural Addons (Non-Strudel):**
`morse` (creates Morse code rhythm patterns from text)

---

## 📊 Summary Statistics

**Total Implemented: ~225 features**

- 27+ core pattern types
- 39+ continuous oscillators and signal generators
- 20+ pattern picking & selection functions
- Complete mini-notation parser (13+ operators)
- 4 filter types with full envelope support and audio engine integration
- Full ADSR envelope system for both amplitude and filters
- 11 complete tonal functions (note, n, freq, scale, sound/s, bank, transpose, scaleTranspose, chord, voicing,
  rootNotes) with 112 chord types
- Triple-mode DSL (pattern method + string extension + standalone function) for 50+ functions
- Custom Klang extensions (warmth, morse, arithmetic addons)

**Major Milestones:**

- ✅ Rational time system eliminates floating-point drift
- ✅ QueryContext enables transformation order independence
- ✅ Control pattern support for dynamic parameters
- ✅ Deterministic seeded randomness
- ✅ Complete mini-notation with Euclidean rhythms
- ✅ Comprehensive pattern picking (83% complete)
- ✅ Filter envelope modulation fully integrated in audio engine
- ✅ Advanced tonal functions with chord support and voice leading
- ✅ Comprehensive chord notation - All 90 Strudel chord notations supported (112 chord types)
- ✅ Custom Klang extensions (warmth, morse rhythm patterns)

---

## 🗓️ Recent Updates

### 2026-01-28: Comprehensive Chord Notation Support

**Chord Parsing Fixes:** Fixed 22 chord notation issues to support all 90 Strudel chord notations. Fixed critical
tokenization bug where 'Csus' was incorrectly parsed as C-sharp + 'us' due to Note.tokenize() treating 's' as a sharp
indicator. Added special handling in Chord.tokenize() to detect 'sus' chord patterns.

**Missing Aliases Added:** Extended chord dictionary with 13 missing aliases:

- Major chords with capital M prefix: M9, M13, M7#5, M9#11
- Minor/major chords with caret notation: m^7, m^9
- Minor chords with dash notation: -M7, -M9, -add9
- Standalone minor flat sixth: mb6, -b6

**New Chord Types:** Added 6 completely new chord type definitions:

- mb6/-b6 (minor flat sixth): 1P 3m 6m
- h9 (half-diminished ninth): 1P 3m 5d 7m 9M
- 7#9b5 (dominant sharp ninth flat fifth): 1P 3M 5d 7m 9A
- 7b9b5 (dominant flat ninth flat fifth): 1P 3M 5d 7m 9m
- 7susadd3 (suspended fourth seventh with third): 1P 3M 4P 5P 7m
- 7b13sus (suspended fourth seventh flat thirteenth): 1P 4P 5P 7m 13m

**Wrong Voicing Corrections:** Fixed C7#9b5 and C7b9b5 which were incorrectly grouped with 7#9#11 and 7b9#11
respectively, causing them to return natural fifth (G) instead of flat fifth (Gb). Split these into separate chord type
entries with correct intervals.

**Chord Dictionary Growth:** Expanded from 106 to 112 chord types, now supporting all major chord notation styles (^, M,
m, -, o, h) and complex altered dominants.

**Test Coverage:** Updated LangVoicingSpec tests to match implementation behavior (chord property removed after voicing
to match JS Strudel). All tests passing: 388 tones tests, 2088 strudel tests. Added comprehensive test suite for all 90
chord notations.

See: `docs/agent-tasks/chord-parsing-fixes-implemented.md` for complete implementation details.

### 2026-01-28: Advanced Tonal Functions

**Chord Support:** Implemented `chord()` function for creating polyphonic chord patterns. Expands chord names (like "
Cmaj7", "Dm7", "F/A") into multiple simultaneous note events using the tones library's chord definitions. Uses
BindPattern and StackPattern for proper polyphonic expansion while preserving timing and other voice properties.

**Voice Leading:** Added `voicing()` function with stateful voice leading algorithm for smooth chord transitions.
Applies proper voice leading to chord progressions within a specified range (default C3-C5), using the tones library's
Voicing.get() for optimal note selection. Maintains voicing state across events for musically natural progressions.

**Scale Transposition:** Implemented `scaleTranspose()` for diatonic transposition within scales. Transposes notes by
scale degrees rather than semitones, with proper octave wrapping. Includes control pattern support for dynamic
transposition values and falls back to chromatic transposition when no scale is set.

**Root Note Extraction:** Added `rootNotes()` utility for extracting root notes from chord patterns with optional octave
specification. Simplifies bass line creation from chord progressions.

**Data Model Extension:** Added `chord: String?` property to StrudelVoiceData for maintaining harmonic context. This
enables downstream functions to access chord information while preserving pattern composition capabilities.

All functions implemented with triple-mode DSL support (standalone function, pattern extension, string extension) and
comprehensive test coverage (41 tests across 4 test suites).

### 2026-01-28: Audio Engine & Custom Extensions

**Filter Envelope Audio Integration:** Completed audio engine implementation for filter envelope modulation.
Control-rate envelope calculation now modulates tunable filter cutoff frequencies during voice rendering in both
SynthVoice and SampleVoice.

**Warmth Parameter (KLANG Extension):** Added `warmth()` DSL function for oscillator brightness control. Implements
one-pole low-pass filtering wrapped around oscillators (0=bright/raw, 1=warm/filtered). Includes comprehensive tests
covering DSL usage, wrapper functionality, and oscillator creation integration.

**Morse Code Patterns (KLANG Extension):** Implemented `morse()` function that converts text to Morse code rhythm
patterns using standard timing (dot=1 step, dash=3 steps, with proper gaps). Available as standalone function, pattern
method, and string extension with full test coverage.

### 2026-01-27: Pattern Picking Complete

Implemented `pickOut`, `pickmodOut`, `pickRestart`, `pickmodRestart`, `squeeze` - bringing pattern picking to 83%
complete (20/24 functions).

### 2026-01-16: Control Pattern Initiative

Added control pattern support to all numerical functions (tempo, structure, Euclidean, random). Major expressiveness
upgrade.

### 2026-01-15: Binary & Euclidean Morph

Implemented `euclidish`/`eish` for morphing rhythms, complete `binary` family (`run`, `binary`, `binaryN`, `binaryL`,
`binaryNL`).

### 2026-01-14: Random & Probabilistic

Complete random signal generators (`rand`, `rand2`, `brand`, `brandBy`), conditional transformations (`sometimes`,
`often`, `rarely`, etc.), cycle-based functions (`firstOf`, `lastOf`), event filtering.

### 2026-01-13: Continuous Patterns Expansion

Added 11 oscillators (bipolar variants, `steady`, `signal`, `time`), noise generators (`perlin`, `berlin`), signal
processing utilities.

### 2026-01-11: Arithmetic & Operators

Complete arithmetic (`add`, `sub`, `mul`, `div`, `mod`, `pow`, `log2`), bitwise (`band`, `bor`, `bxor`, `blshift`,
`brshift`), comparison (`lt`, `gt`, etc.), and logical (`and`, `or`) operators.

### 2026-01-08: Major Architecture Updates

- VoiceValue type system for type-safe value operations
- QueryContext system for transformation order independence
- Rational time system replacing floating-point coordinates
- Unified DSL patterns (triple-mode functions)

---

## 🎯 Next Priorities

**Pattern Picking (4 remaining):**

- `pickReset`, `pickmodReset` - cycle-synchronized reset
- `pickF`, `pickmodF` - function application variants

**Audio Effects (~45 remaining):**

- Filter types (`vowel`, `ftype`)
- Pitch envelope (`pattack`, `pdecay`, `prelease`, `penv`)
- Tremolo, phaser, compressor, duck

**Pattern Operations (~20 remaining):**

- Time: `plyWith`, `plyForEach`, `loopAtCps`
- Transformation: `jux`, `juxBy`, `off`, `when`, `within`, `brak`
- Structure: `iter`, `iterBack`, `linger`, `ribbon`
- Echo: `echo`/`stut`, `echoWith`

**Sample Functions (9 remaining):**
`loopBegin`/`loopb`, `loopEnd`/`loope`, `chop`, `splice`, `striate`, `fit`, `scrub`
