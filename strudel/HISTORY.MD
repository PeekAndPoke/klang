# Strudel Kotlin Port - Implementation History

This document tracks the features and functionality that have been successfully implemented in the Kotlin port of
Strudel.

## Core Pattern System

### Pattern Types ✅

- **AtomicPattern** - Basic pattern building block for single events
- **SequencePattern** - Sequential pattern execution (`seq()`)
- **StackPattern** - Parallel pattern layering (`stack()`)
- **EuclideanPattern** - Euclidean rhythm gating (e.g., `bd(3,8)`, or with rotation `bd(3,8,1)`)
- **ChoicePattern** - Random selection between pattern alternatives (e.g., `"bd|hh"`)
- **DegradePattern** - Probabilistic event removal (e.g., `"bd?"` or `"bd?0.5"`)
- **ReversePattern** - Reverses pattern within each cycle (`rev()`)
- **StructPattern** - Structures pattern according to mask using 'x' markers (`struct()`)
- **ArrangementPattern** - Multi-cycle pattern arrangement (`arrange()`)
- **TimeModifierPattern** - Speed/tempo modification
- **ControlPattern** - Applies control patterns to source patterns
- **ContinuousPattern** - Time-based continuous signal generation

### Rational Number Time System ✅ **NEW!**

**Major architectural improvement** replacing floating-point time coordinates with exact rational arithmetic:

- **Rational** class in `io.peekandpoke.klang.strudel.math` package
- All time coordinates (`begin`, `end`, `dur`) now use Rational instead of Double
- **Eliminates floating-point drift** that caused timing bugs in Euclidean patterns
- Full arithmetic operators: `+`, `-`, `*`, `/`, `%`, unary `-`
- Comparison support via `Comparable` interface
- Double interoperability operators for seamless integration
- Conversion methods: `toDouble()`, `toFloat()`, `toLong()`, `toInt()`
- Utility functions: `abs()`, `floor()`, `ceil()`, `frac()`, `simplified()`
- Helper methods: `Number.toRational()`, `fromDouble()`
- Serialization support for `kotlinx.serialization`
- Comprehensive test suite with 20+ tests

**Impact:**

- ✅ Fixed Euclidean pattern timing bug (premature event triggering)
- ✅ Mathematically exact time calculations
- ✅ No need for EPSILON workarounds
- ✅ Cleaner, more maintainable code

### Pattern Constructors ✅

- `seq()` - Sequences patterns within one cycle
- `stack()` - Plays multiple patterns simultaneously
- `arrange()` - Arranges patterns with specified durations
- `pickRestart()` - Alternates patterns per cycle (slowcat equivalent)
- `silence` - Empty/silent pattern
- `rest` - Rest event (alias for silence)

### DSL Architecture ✅

- **StrudelDsl** annotation marker
- **StrudelRegistry** for dynamic function/method lookup
- Property delegation system for DSL functions (`dslFunction`, `dslMethod`, `dslObject`)
- Type-safe pattern creator/modifier system
- VoiceData mutation and merging framework

## Mini-Notation Parser

### Implemented Syntax ✅

- **Space-separated sequencing** - `"bd hh sd"`
- **Sub-sequences** - `[...]` for grouping
- **Alternation** - `<...>` for cycle rotation
- **Parallel stacking** - `,` for simultaneous patterns
- **Speed multiplication** - `*n` to speed up patterns
- **Speed division** - `/n` to slow down patterns
- **Rests** - `~` or `-` for silence
- **Sample selection** - `:n` notation (e.g., `"bd:3"` selects sample variant 3)
- **Elongation** - `@n` extends note duration (e.g., `"c@3"` holds for 3 steps)
- **Replication** - `!n` replicates notes (e.g., `"c!2"` = `"c c"`)
- **Probabilistic removal** - `?` for random event removal (e.g., `"bd?"` or `"bd?0.5"`)
- **Random choice** - `|` for random selection between alternatives (e.g., `"bd|hh"`)
- **Literal tokens** - Any non-special character sequence

### Parser Features ✅

- Tokenizer with proper operator handling
- Recursive descent parser
- Expression composition (stack/sequence/alternation)
- Modifier application (fast/slow)
- Custom atom factories for extensibility

## Sound & Sample Control

### Sound Selection ✅

- `sound()` / `s()` - Sound/sample selection
- `bank()` - Sound bank selection
- `n()` - Note number or sample index

### Pitch & Note Control ✅

- `note()` - Musical note specification
- `n()` - Note number or sample index, with automatic scale integration
- Note-to-frequency conversion via `Tones.noteToFreq()`
- Scale-aware note indexing - `n()` automatically converts numeric indices to note names when scale is set

## Timing & Tempo

### Speed Control ✅

- `fast(factor)` - Speed up patterns
- `slow(factor)` - Slow down patterns
- TimeModifierPattern implementation with cycle scaling

### Pattern Transformation ✅

- `rev()` - Reverses pattern within each cycle, works in mini-notation and as chained method
- `struct(pattern)` - Structures source pattern according to mask pattern using 'x' markers (e.g.,
  `note("c e g").struct("x ~ x")`)

## Audio Parameters

### Amplitude & Dynamics ✅

- `gain()` - Volume control
- `pan()` - Stereo panning

### Envelopes (ADSR) ✅

- `attack()` - Attack time
- `decay()` - Decay time
- `sustain()` - Sustain level
- `release()` - Release time
- `adsr()` - Combined ADSR string notation (e.g., "0.1:0.1:0.5:0.2")

### Filters ✅

- `lpf(freq)` - Low-pass filter
- `hpf(freq)` - High-pass filter
- `bandf(freq)` / `bpf(freq)` - Band-pass filter
- `notchf(freq)` - Notch filter
- `resonance()` / `res()` - Filter resonance/Q factor

### Spatial Effects ✅

- `room()` - Reverb mix level
- `roomsize()` / `rsize()` - Reverb room size
- `delay()` - Delay mix level
- `delaytime()` - Delay time
- `delayfeedback()` - Delay feedback amount

### Distortion & Degradation ✅

- `distort()` - Distortion effect
- `crush()` - Bit crushing
- `coarse()` - Sample rate reduction

### Modulation ✅

- `vibrato()` / `vib()` - Vibrato frequency
- `vibratoMod()` / `vibmod()` - Vibrato depth
- `accelerate()` - Pitch glide/bend

### Synthesis Parameters ✅

- `unison()` / `uni()` - Voice count for supersaw
- `detune()` - Frequency spread for unison voices
- `spread()` - Pan spread for unison voices
- `density()` / `d()` - Oscillator density
- `legato()` / `clip()` - Note duration/legato

## Continuous Patterns (Oscillators)

### Waveforms ✅

- `sine` - Sine wave oscillator (0 to 1)
- `saw` - Sawtooth oscillator (-1 to 1)
- `isaw` - Inverse sawtooth (1 to -1)
- `tri` - Triangle oscillator (-1 to 1)
- `square` - Square wave oscillator (0 or 1)

### Signal Processing ✅

- `range(min, max)` - Rescale continuous pattern values

## Context & Routing

### Musical Context ✅

- `scale()` - Musical scale setting with automatic note conversion for numeric notes
- Scale name format support for both "C4:major" and "C4 major" notations
- Integration with `n()` for scale-based note generation

### Audio Routing ✅

- `orbit()` - Output bus routing

## Pattern Modifiers Architecture

### Modifier System ✅

- `dslPatternModifier` - Creates pattern parameter modifiers
- `dslPatternCreator` - Creates pattern constructors with mini-notation
- Mini-notation parsing for all modifiers
- Control pattern application system
- VoiceData mutation and combination

## Type System

### VoiceData Fields ✅

Comprehensive voice data structure supporting:

- `sound`, `bank`, `note`, `soundIndex`
- `freqHz`, `gain`, `pan`, `legato`
- `voices`, `freqSpread`, `panSpread`, `density`
- ADSR envelope (`adsr` object)
- Filter system with multiple filter types
- `resonance`, `distort`, `crush`, `coarse`
- `room`, `roomSize`
- `delay`, `delayTime`, `delayFeedback`
- `orbit`, `scale`
- `vibrato`, `vibratoMod`, `accelerate`
- Generic `value` field for control patterns

## Summary Statistics

**Total Implemented:**

- 70+ DSL functions/methods
- 12 core pattern types (including EuclideanPattern, ChoicePattern, DegradePattern, ReversePattern, StructPattern)
- 4 continuous oscillators
- 4 filter types
- Complete ADSR envelope system
- Full mini-notation parser with 13 operators/features (including Euclidean rhythms, probabilistic patterns)
- Control pattern system
- Comprehensive audio parameter support
- **Rational number time system** for exact arithmetic
- **Deterministic seeded randomness** for consistent pattern playback

**Architecture Highlights:**

- **Rational number time coordinates** - Exact arithmetic eliminates floating-point drift
- Property delegation for clean DSL
- Type-safe pattern construction
- Extensible voice data mutation system
- Parser with proper tokenization
- Immutable pattern design

**Recent Major Improvements (2026-01-08 to 2026-01-09):**

- ✅ **Rational Time System** - Replaced all Double time coordinates with exact Rational arithmetic
- ✅ **Euclidean Pattern Fix** - Fixed timing bug that caused premature event triggering
- ✅ **EPSILON Removal** - No longer needed thanks to exact arithmetic
- ✅ **Probabilistic Patterns** - Implemented `?` (degrade) and `|` (choice) operators with deterministic seeded
  randomness
- ✅ **Enhanced Testing** - Added comprehensive test suites for Rational numbers, ChoicePattern, and DegradePattern
- ✅ **Reverse Pattern** - Implemented `rev()` pattern transformation with time reversal per cycle
- ✅ **Struct Pattern** - Implemented `struct()` for masking patterns with structural markers ('x' notation)
- ✅ **Scale Integration** - Enhanced `n()` and `scale()` to work together, automatic note name conversion from indices
- ✅ **Square Wave Fix** - Corrected square oscillator output values
