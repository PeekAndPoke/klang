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
`lprelease`, `lpenv`)

**Distortion & Degradation:**
`distort`/`dist`, `crush`, `coarse`

**Spatial Effects:**
`room`, `roomsize`/`rsize`, `delay`, `delaytime`, `delayfeedback`/`delayfb`/`dfb`

**Modulation:**
`vibrato`/`vib`, `vibratoMod`/`vibmod`, `accelerate`

**Synthesis:**
`unison`/`uni`, `detune`, `spread`, `density`/`d`

---

## 🎼 Tonal & Musical

**Note Control:**
`note`, `n`, `scale`, `transpose`

**Sound Selection:**
`sound`/`s`, `bank`, `begin`, `end`, `speed`, `loop`, `cut`, `slice`

**Routing:**
`orbit`

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

**Addons (Non-Strudel):**
`flipSign`, `oneMinusValue`, `not`

---

## 📊 Summary Statistics

**Total Implemented: ~216 features**

- 27+ core pattern types
- 39+ continuous oscillators and signal generators
- 20+ pattern picking & selection functions
- Complete mini-notation parser (13+ operators)
- 4 filter types with full envelope support
- Full ADSR envelope system
- Triple-mode DSL (pattern method + string extension + standalone function) for 50+ functions

**Major Milestones:**

- ✅ Rational time system eliminates floating-point drift
- ✅ QueryContext enables transformation order independence
- ✅ Control pattern support for dynamic parameters
- ✅ Deterministic seeded randomness
- ✅ Complete mini-notation with Euclidean rhythms
- ✅ Comprehensive pattern picking (83% complete)

---

## 🗓️ Recent Updates

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

**Audio Effects (~46 remaining):**

- Filter types (`vowel`, `ftype`)
- Pitch envelope (`pattack`, `pdecay`, `prelease`, `penv`)
- Tremolo, phaser, compressor, duck
- Audio engine integration for filter envelopes

**Pattern Operations (~20 remaining):**

- Time: `plyWith`, `plyForEach`, `loopAtCps`
- Transformation: `jux`, `juxBy`, `off`, `when`, `within`, `brak`
- Structure: `iter`, `iterBack`, `linger`, `ribbon`
- Echo: `echo`/`stut`, `echoWith`

**Sample Functions (9 remaining):**
`loopBegin`/`loopb`, `loopEnd`/`loope`, `chop`, `splice`, `striate`, `fit`, `scrub`
