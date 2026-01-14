# Strudel Kotlin Port - Implementation History

This document tracks the features and functionality that have been successfully implemented in the Kotlin port of
Strudel.

## Core Pattern System

### VoiceValue Type System ✅ **NEW!**

**Major type system improvement** introducing type-safe value handling:

- **VoiceValue** sealed interface with `Num` and `Text` variants
- Type-safe value operations (replaces generic `Any?` for pattern values)
- Built-in arithmetic operators: `+`, `-`, `*`, `/`, `%`, `pow`
- Bitwise operators: `band`, `bor`, `bxor`, `shl` (left shift), `shr` (right shift)
- Comparison operators: `lt`, `gt`, `lte`, `gte`, `eq`, `ne`
- Logical operators: `and`, `or` (short-circuit evaluation)
- Unary operations: `log2()`
- Truthiness evaluation for conditional logic
- Custom JSON serialization support
- Automatic type coercion between numbers and strings

**Benefits:**

- Type safety for value operations
- Consistent behavior across arithmetic, comparison, and logical operations
- Foundation for complex value manipulations in patterns
- Clean interoperability between numeric and string values

### Pattern Types ✅

- **AtomicPattern** - Basic pattern building block for single events
- **SequencePattern** - Sequential pattern execution (`seq()`)
- **StackPattern** - Parallel pattern layering (`stack()`)
- **EuclideanPattern** - Euclidean rhythm gating (e.g., `bd(3,8)`, or with rotation `bd(3,8,1)`)
- **ChoicePattern** - Random selection between pattern alternatives (e.g., `"bd|hh"`)
- **DegradePattern** - Probabilistic event removal (e.g., `"bd?"` or `"bd?0.5"`)
- **ReversePattern** - Reverses pattern within each cycle (`rev()`)
- **StructPattern** - Structures pattern according to mask using 'x' markers (`struct()`)
- **ContextModifierPattern** - Modifies query context for downstream patterns
- **ArrangementPattern** - Multi-cycle pattern arrangement (`arrange()`)
- **TimeModifierPattern** - Speed/tempo modification
- **ControlPattern** - Applies control patterns to source patterns
- **ContinuousPattern** - Time-based continuous signal generation with context-aware range mapping

### QueryContext System ✅ **NEW!**

**Major architectural improvement** introducing a context-passing mechanism for pattern queries:

- **QueryContext** class with type-safe key-value storage for passing information down the pattern hierarchy
- **QueryContext.Key<T>** for type-safe context keys
- **QueryContext.Updater** with efficient copy-on-write updates to minimize allocations
- Conditional update methods: `set()`, `setIfAbsent()`, `setWhen()`
- **queryArcContextual()** method - all patterns now implement context-aware querying
- **ContextModifierPattern** - new pattern type for context manipulation
- Comprehensive test suite with 20+ tests covering all context operations

**Benefits:**

- **Transformation order independence** - patterns work correctly regardless of composition order (e.g.,
  `sine.slow(2).range(0,100)` == `sine.range(0,100).slow(2)`)
- Information flows down the pattern hierarchy without coupling patterns together
- Enables future features like scale context propagation, tempo inheritance, and more
- Clean separation of concerns - patterns don't need to know about wrapping patterns

**Impact:**

- ✅ Fixed `range()` to work with any transformation order
- ✅ Enables context-aware continuous patterns
- ✅ Foundation for future context-dependent features

### Rational Number Time System ✅

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
- `mini()` - Parses input as mini-notation (alias for `seq`)
- `stack()` - Plays multiple patterns simultaneously
- `arrange()` - Arranges patterns with specified durations
- `pickRestart()` - Alternates patterns per cycle (slowcat equivalent)
- `silence` - Empty/silent pattern
- `rest` - Rest event (alias for silence)

### DSL Architecture ✅

- **StrudelDsl** annotation marker
- **StrudelRegistry** for dynamic function/method lookup
- Property delegation system for DSL functions (`dslFunction`, `dslMethod`, `dslObject`)
- **Unified DSL patterns** - Many functions now work as pattern methods, string extensions, AND standalone functions
- Type-safe pattern creator/modifier system
- VoiceData mutation and merging framework
- Generic `dslPatternExtension` and `dslStringExtension` for triple-mode functions

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

- `fast(factor)` - Speed up patterns (works as pattern method, string extension, and standalone function)
- `slow(factor)` - Slow down patterns (works as pattern method, string extension, and standalone function)
- TimeModifierPattern implementation with cycle scaling

### Arithmetic Operations ✅

- `add()` - Addition (pattern method, string extension, standalone function)
- `sub()` - Subtraction
- `mul()` - Multiplication
- `div()` - Division
- `mod()` - Modulo
- `pow()` - Power/exponentiation
- `log2()` - Base-2 logarithm (unary operation)

All arithmetic operations work on pattern values and can be applied as:

- Pattern methods: `note("0 2 4").add(5)`
- String extensions: `"0 2 4".add(5)`
- Standalone functions: `add(5, note("0 2 4"))`

### Bitwise Operations ✅

- `band()` - Bitwise AND
- `bor()` - Bitwise OR
- `bxor()` - Bitwise XOR
- `blshift()` - Bitwise left shift
- `brshift()` - Bitwise right shift

### Comparison & Logic Operations ✅

- Comparison: `lt()`, `gt()`, `lte()`, `gte()`, `eq()`, `ne()`
- Logical: `and()`, `or()` (short-circuit evaluation)
- All return numeric values (1.0 for true, 0.0 for false)

### Pattern Transformation ✅

- `rev()` - Reverses pattern within each cycle, works in mini-notation and as chained method
- `palindrome()` - Alternates forward and backward playback (combines pattern with its reverse)
- `struct(pattern)` - Structures source pattern according to mask pattern using 'x' markers (e.g.,
  `note("c e g").struct("x ~ x")`)
- `structAll(pattern)` - Applies structural masking to all events
- `mask(pattern)` - Conditionally applies pattern elements (boolean masking)
- `maskAll(pattern)` - Boolean masking for all events
- `superimpose(function)` - Layers modified pattern on top of original

### Probabilistic Patterns ✅

**Event Removal:**
- `degradeBy(amount)` - Randomly removes events with given probability (pattern method, string extension, with pattern
  control)
- `degrade()` - Randomly removes events with 50% probability (pattern method and string extension)
- `undegradeBy(amount)` - Inverse of degradeBy - randomly removes events (pattern method, string extension, with pattern
  control)
- `undegrade()` - Inverse of degrade - randomly removes events with 50% probability (pattern method and string
  extension)

**Conditional Transformations:**

- `sometimesBy(probability, fn)` - Randomly applies transformation with custom probability
- `sometimes(fn)` - Randomly applies transformation with 50% probability
- `often(fn)` - Frequently applies transformation (75% probability)
- `rarely(fn)` - Infrequently applies transformation (25% probability)
- `almostAlways(fn)` - Almost always applies transformation (90% probability)
- `almostNever(fn)` - Almost never applies transformation (10% probability)
- `always(fn)` - Always applies transformation (100% probability)
- `never(fn)` - Never applies transformation (0% probability)
- `someCyclesBy(probability, fn)` - Cycle-based random application with proper cycle-locking
- `someCycles(fn)` - Cycle-based random application with 50% probability

**Cycle-Based Conditional:**

- `firstOf(n, fn)` - Apply function on first cycle of every n cycles (pattern method, string extension)
- `every(n, fn)` - Alias for `firstOf` (pattern method, string extension)

## Arithmetic Addons (Non-Strudel Extensions) ✅

**These functions are NOT available in the original Strudel implementation**

- `negateValue()` - Negates the value field of voice data (pattern method, string extension)
- `oneMinusValue()` - Calculates 1.0 - value (pattern method, string extension)

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

### Signal Generators ✅

- `steady(value)` - Constant value continuous pattern
- `signal(callback)` - Continuous pattern from callback function `(Double) -> Double`
- `time` - Current time (in cycles) as continuous pattern
- `rand` - Random signal (unipolar, 0 to 1)
- `rand2` - Random signal (bipolar, -1 to 1)
- `irand(n)` - Random integers from 0 to n-1 (returns sequence pattern with n values)
- `brand` - Binary random (0 or 1, 50:50 probability)
- `brandBy(probability)` - Binary random with custom probability

### Waveforms ✅

**Unipolar (0 to 1):**
- `sine` - Sine wave oscillator (0 to 1)
- `cosine` - Cosine wave oscillator (0 to 1)
- `saw` - Sawtooth oscillator (0 to 1)
- `isaw` - Inverse sawtooth (1 to 0)
- `tri` - Triangle oscillator (0 to 1 to 0)
- `itri` - Inverse triangle (1 to 0 to 1)
- `square` - Square wave oscillator (0 or 1)

**Bipolar (-1 to 1):**

- `sine2` - Sine wave oscillator (-1 to 1)
- `cosine2` - Cosine wave oscillator (-1 to 1)
- `saw2` - Sawtooth oscillator (-1 to 1)
- `isaw2` - Inverse sawtooth (1 to -1)
- `tri2` - Triangle oscillator (-1 to 1 to -1)
- `itri2` - Inverse triangle (-1 to 1 to -1)
- `square2` - Square wave oscillator (-1 or 1)

**Noise:**

- `perlin` - Perlin noise generator (0 to 1) with seeded permutation table for deterministic output
- `berlin` - Berlin noise generator (0 to 1) - sawtooth-based noise, conceived as a joke but surprisingly useful

### Signal Processing ✅

- `range(min, max)` - Rescale continuous pattern values (transformation order independent via QueryContext)

## Context & Routing

### Musical Context ✅

- `scale()` - Musical scale setting with automatic note conversion for numeric notes
- Scale name format support for both "C4:major" and "C4 major" notations
- Integration with `n()` for scale-based note generation

### Audio Routing ✅

- `orbit()` - Output bus routing

## Random & Seeding ✅

- `seed(n)` - Set random seed for deterministic pattern generation (works as pattern method and string extension)

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

- 126+ DSL functions/methods (including arithmetic, bitwise, comparison, and logical operators)
- 16 core pattern types (including EuclideanPattern, ChoicePattern, SometimesPattern, ReversePattern, StructPattern,
  ContextModifierPattern, TimeShiftPatternWithControl)
- 25 continuous oscillators and signal generators (steady, signal, time, sine, sine2, cosine, cosine2, saw, saw2, isaw,
  isaw2, tri, tri2, itri, itri2, square, square2, perlin, berlin, rand, rand2, irand, brand, brandBy)
- 4 filter types
- Complete ADSR envelope system
- Full mini-notation parser with 13 operators/features (including Euclidean rhythms, probabilistic patterns)
- Control pattern system with pattern-controlled degradation and time shifting
- Comprehensive audio parameter support
- **Rational number time system** for exact arithmetic
- **Deterministic seeded randomness** for consistent pattern playback via `seed()`
- **Advanced random seeding** with improved hashing algorithm and varargs support

**Architecture Highlights:**

- **VoiceValue type system** - Sealed interface for type-safe value operations with arithmetic, bitwise, comparison, and
  logical operators
- **QueryContext system** - Context-passing enables transformation order independence and future context-dependent
  features
- **Rational number time coordinates** - Exact arithmetic eliminates floating-point drift
- **Unified DSL patterns** - Triple-mode functions work as pattern methods, string extensions, and standalone functions
- Property delegation for clean DSL
- Type-safe pattern construction
- Extensible voice data mutation system
- Parser with proper tokenization
- Immutable pattern design

**Recent Major Improvements (2026-01-08 to 2026-01-11):**

- ✅ **VoiceValue Type System** - Major type system improvement with sealed interface for type-safe value operations (
  arithmetic, bitwise, comparison, logical)
- ✅ **Arithmetic Operations** - Complete set of arithmetic functions: `add()`, `sub()`, `mul()`, `div()`, `mod()`,
  `pow()`, `log2()`
- ✅ **Bitwise Operations** - Full bitwise operator support: `band()`, `bor()`, `bxor()`, `blshift()`, `brshift()`
- ✅ **Comparison & Logic** - Comparison operators (`lt()`, `gt()`, `lte()`, `gte()`, `eq()`, `ne()`) and logical
  operators (`and()`, `or()`)
- ✅ **Unified DSL Patterns** - Major DSL refactoring enabling triple-mode functions (pattern method + string extension +
  standalone function) for 50+ functions
- ✅ **Pattern Transformations** - Added `palindrome()`, `structAll()`, `maskAll()`, `superimpose()`
- ✅ **QueryContext System** - Major architectural improvement introducing context-passing for transformation order
  independence and enabling future context-dependent features (scale propagation, tempo inheritance, etc.)
- ✅ **Rational Time System** - Replaced all Double time coordinates with exact Rational arithmetic
- ✅ **Euclidean Pattern Fix** - Fixed timing bug that caused premature event triggering
- ✅ **EPSILON Removal** - No longer needed thanks to exact arithmetic
- ✅ **Probabilistic Patterns** - Implemented `?` (degrade) and `|` (choice) operators with deterministic seeded
  randomness
- ✅ **Enhanced Testing** - Added comprehensive test suites for Rational numbers, ChoicePattern, DegradePattern,
  QueryContext, and VoiceValue
- ✅ **Reverse Pattern** - Implemented `rev()` pattern transformation with time reversal per cycle
- ✅ **Struct Pattern** - Implemented `struct()` for masking patterns with structural markers ('x' notation)
- ✅ **Scale Integration** - Enhanced `n()` and `scale()` to work together, automatic note name conversion from indices
- ✅ **Square Wave Fix** - Corrected square oscillator output values
- ✅ **Perlin Noise** - Implemented 1D Perlin noise generator with `perlin` continuous pattern
- ✅ **DSL Type System Simplification** - Removed generic type parameters from `dslMethod`, cleaner architecture with
  `Any?` handling
- ✅ **Transformation Order Independence** - `range()` now works correctly regardless of composition order with tempo
  modifiers

**Recent Improvements (2026-01-13):**

- ✅ **Extended Continuous Patterns** - Added 11 new continuous oscillators and signal generators:
  - `steady(value)` - Constant value patterns
  - `signal(callback)` - Custom callback-based patterns
  - `time` - Current cycle time as pattern
  - Bipolar oscillators: `sine2`, `cosine2`, `saw2`, `isaw2`, `tri2`, `itri2`, `square2`
  - Unipolar additions: `cosine`, `itri`
- ✅ **Bug Fixes** - Fixed `isaw2`, `tri2`, and `itri2` implementations, added missing `@StrudelDsl` annotation to
  `itri2`
- ✅ **Comprehensive Testing** - Added complete test coverage for all continuous patterns with `.range()` tests in
  `LangContinuousPatternsSpec`
- ✅ **JS Compatibility Tests** - Added example patterns for all new oscillators in `JsCompatTestData`

**Recent Improvements (2026-01-14):**

- ✅ **Random Signal Generators** - Added 4 new random continuous patterns:
    - `rand` - Random signal (unipolar, 0 to 1) with improved seeding using `getSeededRandom()`
    - `rand2` - Random signal (bipolar, -1 to 1)
    - `brand` - Binary random (0 or 1, 50:50 probability) with improved seeding
    - `brandBy(probability)` - Binary random with custom probability and improved seeding
- ✅ **Random Seeding** - Moved `seed()` function from `lang_continuous.kt` to dedicated `lang_random.kt` file:
    - `seed(n)` - Set random seed for deterministic pattern generation
    - Works as pattern method and string extension
    - Enables reproducible random patterns across queries
- ✅ **Improved Random Seeding Architecture** - Enhanced `QueryContext.getSeededRandom()`:
    - Now accepts varargs for better seed combination: `getSeededRandom(seed, vararg seeds)`
    - Uses improved hashing algorithm for better random distribution
    - Applied to `ChoicePattern`, `DegradePattern`, `rand`, and `brandBy`
- ✅ **Probabilistic Pattern Enhancements**:
    - `degradeBy()` - Now available as pattern method, string extension, AND with pattern control
    - `degrade()` - New convenience function with 50% probability (pattern method and string extension)
    - `DegradePatternWithControl` - New pattern type accepting a pattern as probability control
- ✅ **Pattern Control Architecture** - New `TimeShiftPatternWithControl` pattern type for advanced time shifting
- ✅ **Code Organization** - Created new `lang_random.kt` file for random-related DSL functions
- ✅ **Noise Generators**:
  - `perlin` - Enhanced with seeded permutation table for deterministic noise generation
  - `berlin` - New sawtooth-based noise generator (0 to 1), conceived as a joke but surprisingly useful
  - Both use `PerlinNoise` and `BerlinNoise` classes with runtime permutation generation
  - Improved `PerlinNoise` with better gradient function and scaling
- ✅ **Inverse Degradation Functions**:
  - `undegradeBy(amount)` - Inverse of degradeBy, keeps events that degradeBy would remove
  - `undegrade()` - Convenience function with 50% probability
  - Both support pattern control for dynamic probability
  - Enhanced `DegradePattern` and `DegradePatternWithControl` with inverted mode
  - Complementary behavior: `degradeBy(x)` and `undegradeBy(1-x)` partition events perfectly
- ✅ **Conditional Transformation Functions**:
  - `sometimesBy(probability, fn)` - Core function for probabilistic transformations
  - `sometimes(fn)` - 50% probability shorthand
  - `often(fn)` - 75% probability for frequent application
  - `rarely(fn)` - 25% probability for rare application
  - `almostAlways(fn)` - 90% probability
  - `almostNever(fn)` - 10% probability
  - `always(fn)` - 100% probability (always applies transformation)
  - `never(fn)` - 0% probability (never applies transformation)
  - `someCyclesBy(probability, fn)` and `someCycles(fn)` - Cycle-based randomness with proper cycle-locking
- ✅ **Major Refactoring - New `SometimesPattern` Architecture**:
  - Unified pattern type replacing `DegradePattern`, `DegradePatternWithControl`, and `StackPattern`-based
    implementation
  - Three factory methods: `applyOnMatch`, `discardOnMatch`, `discardOnMiss`
  - Supports both fixed probability values and dynamic probability patterns
  - `seedStrategy` parameter allows per-event or per-cycle randomness
  - Proper cycle-locking for `someCycles` family via `seedStrategy: { it.begin.floor() }`
  - Eliminates need for separate degraded/undegraded streams - handles routing internally
  - Pattern control support for `degradeBy` and `undegradeBy` via mini-notation parsing
- ✅ **Arithmetic Addon Functions** (Non-Strudel extensions):
  - Created new `lang_arithmetic_addons.kt` module in `lang/addons/` package
  - `negateValue()` - Negates the value field (useful for inverting control signals)
  - `oneMinusValue()` - Calculates 1.0 - value (used internally by `undegradeBy` for probability inversion)
  - Registered via `strudelLangArithmeticAddonsInit` in main init function
- ✅ **Random Seeding Improvements**:
    - Changed `QueryContext.random` from `Key<Random>` to `randomSeed` as `Key<Long>` for better seed management
    - Updated `getRandom()` to create Random instance from seed on demand
    - Enhanced `seed()` function to accept Long values via new `asLongOrNull()` helper
    - `seed()` now supports removing seed: calling `seed()` with null removes the seed from context
    - Added `QueryContext.Updater.remove()` method for removing context keys
- ✅ **New Pattern Functions**:
    - `irand(n)` - Random integer sequence generator (0 to n-1), returns `SequencePattern` with n atomic values
    - Uses per-event seeded randomness based on fractional position within cycle
    - `mini()` - Alias for `seq()`, parses input as mini-notation for convenience
- ✅ **ReinterpretPattern Enhancements**:
    - Enhanced `reinterpret()` to accept `QueryContext` parameter: `reinterpret((evt, ctx) -> ...)`
    - Maintains backward compatibility with single-parameter version: `reinterpret((evt) -> ...)`
    - Used by `irand()` implementation for context-aware event transformation
    - Refactored to use companion object factory methods instead of public constructor
- ✅ **Cycle-Based Conditional Functions**:
    - `firstOf(n, fn)` - Apply function on first cycle of every n cycles
    - `every(n, fn)` - Alias for `firstOf`
    - Uses `ArrangementPattern` to create repeating n-cycle patterns (transformed, original, original, ...)
    - Works as pattern method and string extension
- ✅ **Code Organization**:
    - Renamed `lang_choice.kt` to `lang_conditional.kt` for better categorization
    - Updated init function from `strudelLangChoiceInit` to `strudelLangConditionalInit`
- ✅ **Helper Function Enhancements**:
  - Added `asLongOrNull()` for safe Long conversion
  - Enhanced `DslPatternMethod` with overload: `invoke(p1: Any, block: (StrudelPattern) -> StrudelPattern)` - accepts
    any type, not just Number
  - Updated `defaultModifier` documentation to clarify it populates `VoiceData.value`
- ✅ **Comprehensive Testing** - Added multiple test files:
    - `LangSeedSpec.kt` with 7 tests covering seed functionality
    - `LangRandomSpec.kt` with tests for random signal generators
    - `LangDegradeBySpecTest.kt` with tests for degradeBy/degrade functions
  - `LangUndegradeBySpec.kt` with 10 tests covering undegradeBy/undegrade including complementarity tests
    - `LangSometimesBySpec.kt` with 11 tests covering all conditional transformation functions (sometimesBy, sometimes,
      often, rarely, almostAlways, almostNever, always, never, someCyclesBy, someCycles)
    - `LangSomeCyclesBySpec.kt` with tests validating proper cycle-locking behavior (no mixed cycles within single cycle
      period)
    - `LangFirstOfSpec.kt` with 7 tests covering firstOf/every functionality
    - `LangArithmeticAddonsSpec.kt` with tests for negateValue and oneMinusValue
    - `DegradePatternWithControlSpec.kt` for pattern-controlled degradation
    - `TimeShiftPatternWithControlSpec.kt` for pattern-controlled time shifting
  - `PerlinNoiseSpec.kt` - Tests for Perlin noise implementation
  - `BerlinNoiseSpec.kt` - Tests for Berlin noise implementation
  - Enhanced `LangContinuousPatternsSpec.kt` with perlin/berlin tests covering seed consistency, range validation, and
    stability
