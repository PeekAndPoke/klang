# Strudel Kotlin Port - TODO List

This document lists all features from Strudel.cc that are not yet implemented in the Kotlin port, organized by priority
and category.

---

## Things to check

- [ ] check https://larkob.github.io/strudel/tutorial/ and produce new todos

## 🔴 HIGH PRIORITY - Common Use Cases

### Mini-Notation Extensions

#### Rebustness amd User friendly error messages

- the parser must be rebust againts syntax errors
- it must always parse "something"
- but it should provide helpful error messages / warnings

#### Sample Selection ⭐⭐⭐

- [x] **`:n` notation** - Sample number selection in mini-notation (e.g., `"bd:3 hh:2"`)
- [x] **`-` (dash) rest** - Alternative to `~` for rests

#### Timing Modifiers ⭐⭐⭐

- [x] **`@n` elongation** - Extend note duration (e.g., `"c@3"` holds for 3 steps)
- [x] **`!n` replication** - Replicate notes (e.g., `"c!2"` = `"c c"`)

#### Probabilistic ⭐⭐⭐

- [x] **`?` random removal** - Probabilistic event removal (e.g., `"bd?"` or `"bd?0.5"`)
- [x] **`|` random choice** - Random selection between alternatives (e.g., `"bd|hh"`)

#### Euclidean Rhythms ⭐⭐⭐

- [x] **`(pulses, steps)` notation** - Euclidean rhythm in mini-notation (e.g., `"bd(3,8)"`) ✅ **FIXED WITH RATIONAL
  ARITHMETIC**
- [x] **`(pulses, steps, rotation)`** - With rotation parameter

### Pattern Creation ⭐⭐⭐

- [ ] **`cat()`** - Concatenates patterns, each taking one full cycle
  - [x] implement as top-level function
  - [ ] implement as chainable method on Pattern
- [ ] **`polymeter()`** - Aligns steps of patterns creating polymeters
- [ ] **`polymeterSteps()`** - Polymeter with step specification
- [ ] **`run(n)`** - Creates discrete pattern of numbers 0 to n-1
- [ ] **`binary(n)`** - Creates binary pattern from number
- [ ] **`binaryN(n, bits)`** - Binary pattern padded to n bits

### Time Modification ⭐⭐⭐

- [ ] **`early(cycles)`** - Nudge pattern earlier in time
- [ ] **`late(cycles)`** - Nudge pattern later in time
- [ ] **`euclid(pulses, steps)`** - Euclidean rhythm function
- [ ] **`euclidRot(pulses, steps, rotation)`** - Euclidean with rotation
- [ ] **`euclidLegato(pulses, steps)`** - Holds pulses until next
- [ ] **`compress(start, end)`** - Compresses pattern into timespan
- [ ] **`fastGap(factor)`** - Speeds up with gaps between
- [ ] **`swingBy(subdivision, offset)`** - Creates swing rhythm
- [ ] **`swing(subdivision)`** - Swing with default 1/3 offset
- [ ] **`ply(n)`** - Repeats each event n times

### Pattern Transformation ⭐⭐⭐

- [x] **`rev()`** - Reverses pattern in each cycle
- [ ] **`jux(function)`** - Apply function to right channel only
- [ ] **`juxBy(amount, function)`** - Jux with custom stereo width
- [ ] **`off(time, function)`** - Copies, shifts, and modifies pattern
- [ ] **`superimpose(function)`** - Layers modified pattern on top
- [ ] **`mask(pattern)`** - Conditionally applies pattern elements

### Tonal Functions ⭐⭐⭐

- [ ] **`transpose(semitones)`** - Transpose notes by semitones
- [ ] **`scaleTranspose(steps)`** - Transpose within scale
- [ ] **`chord()`** - Create chord patterns
- [ ] **`voicing()`** - Generate chord voicings with full parameters
- [ ] **`rootNotes(octave)`** - Extract root notes from chords

### Audio Effects ⭐⭐⭐

- [ ] **`lpq()`, `hpq()`, `bpq()`** - Dedicated Q/resonance for each filter
- [ ] **Filter envelopes** - `lpattack`, `lpdecay`, `lpsustain`, `lprelease` (and hp/bp variants)
- [ ] **`vowel(sound)`** - Vowel filter (a, e, i, o, u)
- [ ] **`phaser()`** - Phaser effect
- [ ] **`tremolo`** with full parameters:
    - `tremolosync()`, `tremolodepth()`, `tremoloskew()`
    - `tremolophase()`, `tremoloshape()`

### Sample Manipulation ⭐⭐⭐

- [ ] **`begin(pos)`** - Skip to sample position
- [ ] **`end(pos)`** - Cut sample at position
- [ ] **`speed(rate)`** - Playback speed (including negative)
- [ ] **`loop()`** - Loop sample
- [ ] **`loopAt(cycles)`** - Fit sample to cycles
- [ ] **`cut(group)`** - Stop playing samples in cutgroup
- [ ] **`chop(slices)`** - Granular slicing
- [ ] **`slice(index)`** - Trigger specific slice

### Continuous Signals ⭐⭐

- [ ] **`cosine`** / **`cosine2`** - Cosine wave oscillators
- [ ] **`saw2`**, **`sine2`**, **`tri2`**, **`square2`** - Bipolar (-1 to 1) versions
- [ ] **`rand`** / **`rand2`** - Random signals (unipolar/bipolar)
- [ ] **`perlin`** - Perlin noise
- [ ] **`irand(n)`** - Random integers from 0 to n-1
- [ ] **`brand`** - Binary random (0 or 1)
- [ ] **`brandBy(probability)`** - Binary random with custom probability
- [ ] **`.segment(rate)`** - Sample continuous signal at rate
- [ ] **`.slow(factor)`** - Slow down continuous patterns

---

## 🟡 MEDIUM PRIORITY - Advanced Features

### Pattern Operations

- [ ] **`palindrome()`** - Alternates forward/backward playback
- [ ] **`iter(subdivisions)`** - Plays pattern subdivisions in order
- [ ] **`iterBack(subdivisions)`** - Plays subdivisions in reverse
- [ ] **`zoom(start, end)`** - Plays portion of pattern
- [ ] **`linger(fraction)`** - Selects and repeats pattern fraction
- [ ] **`segment(segments)`** - Samples pattern at rate
- [ ] **`ribbon(offset, cycles)`** - Loops pattern section
- [ ] **`inside(cycles, operation)`** - Applies operation within cycle
- [ ] **`outside(cycles, operation)`** - Applies operation outside cycle
- [ ] **`stepcat()`** - Concatenate patterns proportionally
- [ ] **`layer(function)`** / **`apply(function)`** - Layers function result
- [ ] **`echo(times, delay, decay)`** - Echo with velocity decay
- [ ] **`echoWith(times, delay, function)`** / **`stutWith()`** - Echo with custom function
- [ ] **`add(value)`** - Adds numbers or notes to pattern

### Conditional & Probabilistic

- [ ] **`sometimes(function)`** - Randomly applies effects
- [ ] **`rarely(function)`** - Infrequently applies variations
- [x] **`degradeBy(amount)`** - Probabilistic event removal

### Sample Functions

- [ ] **`loopBegin()`** / **`loopEnd()`** - Loop point control
- [ ] **`striate(portions)`** - Progressive sample portions
- [ ] **`splice(index)`** - Slice with speed adjustment
- [ ] **`scrub()`** - Tape-like scrubbing
- [ ] **`fit()`** - Match sample to event duration

### Synthesis Parameters

- [ ] **`velocity()`** - MIDI velocity
- [ ] **`postgain()`** - Post-processing gain
- [ ] **Additive synthesis** - `partials`, `phases`
- [ ] **FM synthesis parameters**:
    - `fmh()` - FM harmonicity ratio
    - `fmattack()`, `fmdecay()`, `fmsustain()`, `fmenv()`
- [ ] **Pitch envelope** - `pattack`, `pdecay`, `prelease`, `penv`
- [ ] **Wavetable synthesis** - `wt_` prefix support with loop points
- [ ] **ZZFX synthesis** - Full parameter set (zrand, curve, slide, etc.)

### Noise Generators

- [ ] **`white`** - White noise
- [ ] **`pink`** - Pink noise
- [ ] **`brown`** - Brown noise
- [ ] **`crackle(density)`** - Crackle noise

### Audio Effects (Advanced)

- [ ] **`compressor()`** - Dynamic range compression
- [ ] **`duck()`** - Ducking/sidechain effect
- [ ] **`xfade()`** - Crossfade between patterns

### Tempo Control

- [ ] **`setcpm()`** / **`setcps()`** - Set cycles per minute/second
- [ ] **`cpm(tempo)`** - Play pattern at specific tempo

---

## 🟢 LOW PRIORITY - Nice to Have

### System Functions

- [ ] **`samples()`** - Load custom sample maps (URLs, GitHub, local)
- [ ] **`soundAlias()`** - Create custom sound aliases
- [ ] **`initStrudel()`** - Initialize Strudel environment
- [ ] **`hush()`** - Stop all playing patterns

### MIDI & OSC

- [ ] **`midi(outputName, options)`** - MIDI output connection
- [ ] **`midiin(inputName)`** - MIDI input for control changes
- [ ] **`midiport(outputName)`** - Select MIDI output device
- [ ] **`midichan(number)`** - MIDI channel selection (1-16)
- [ ] **`midicmd(command)`** - MIDI system messages (clock, start, stop)
- [ ] **MIDI Control** - `control`, `ccn`, `ccv`, `progNum`
- [ ] **MIDI SysEx** - `sysex`, `sysexid`, `sysexdata`
- [ ] **MIDI Bend/Touch** - `midibend`, `miditouch`
- [ ] **`Pattern.osc()`** - Send pattern as OSC messages

### Visualization

- [ ] **`pianoroll(options)`** / **`punchcard(options)`** - Scrolling visualization
- [ ] **`spiral(options)`** - Spiral visualization
- [ ] **`scope(options)`** / **`tscope(options)`** - Oscilloscope
- [ ] **`pitchwheel(options)`** - Frequency visualization
- [ ] **`spectrum(options)`** - Spectrum analyzer
- [ ] **`markcss(style)`** - CSS override for events

### Hydra Integration

- [ ] **`H(pattern)`** - Use pattern as Hydra input
- [ ] **`initHydra(options)`** - Initialize Hydra
- [ ] Hydra function integration

### UI Controls

- [ ] **`slider()`** - Inline draggable slider element

### Interactive Input

- [ ] **`mouseX`** / **`mouseY`** - Mouse position signals (0-1)
- [ ] Device motion sensors integration

---

## 🔵 ARCHITECTURAL IMPROVEMENTS

### Pattern System Enhancements

- [x] **Rational number time coordinates** - ✅ **IMPLEMENTED** - Replaces Double with exact rational arithmetic for all
  time calculations, eliminating floating-point drift and timing bugs
- [ ] **Negative time querying** - Support for `early()` functionality
- [ ] **Pattern queryArc optimization** - Cache/memoization for repeated queries
- [ ] **Event metadata** - Track event IDs, origins for debugging

### Mini-Notation Parser

- [ ] **Better error messages** - Show position and context
- [ ] **Nested modifier support** - Multiple `*` and `/` operators
- [ ] **Whitespace flexibility** - Handle tabs, newlines better
- [ ] **Comment support** - `//` or `#` for inline comments

### Type System

- [ ] **Pattern type hierarchy** - Distinguish discrete vs continuous patterns
- [ ] **Value type safety** - Type-safe value field in VoiceData
- [ ] **Filter collection API** - Better filter management methods

### Performance

- [ ] **Lazy evaluation** - Defer pattern computation
- [ ] **Event pooling** - Reuse event objects
- [ ] **Query optimization** - Minimize redundant queryArc calls

### Developer Experience

- [ ] **Pattern debugging** - Pretty-print pattern structure
- [ ] **Event inspector** - Log events with formatting
- [ ] **Pattern validator** - Check for common mistakes
- [ ] **Documentation generator** - Auto-generate DSL docs from code

---

## 📊 Missing Feature Summary

### By Category:

- **Mini-Notation**: 2 features missing (6 completed: `:n`, `-`, `@n`, `!n`, `?`, `|`)
- **Pattern Creation**: 6 functions
- **Time Modification**: 10 functions
- **Pattern Transformation**: 15 functions
- **Tonal Functions**: 5 functions
- **Audio Effects**: 10+ parameters
- **Sample Manipulation**: 12 functions
- **Continuous Signals**: 11 signals/modifiers
- **Synthesis**: 15+ parameters
- **MIDI/OSC**: 15+ functions
- **Visualization**: 6 tools
- **System Functions**: 4 functions

### Total Missing: ~123 features (7 completed in Phase 1 & 2)

---

## 🎯 Recommended Implementation Order

### Phase 1: Core Functionality (Weeks 1-2)

1. Mini-notation extensions (`:n`, `?`, `|`, `@`, `!`)
2. Euclidean rhythms (mini-notation + functions)
3. Basic pattern operations (`rev`, `cat`, `early`, `late`)
4. Sample manipulation (`begin`, `end`, `speed`, `cut`)

### Phase 2: Musical Features (Weeks 3-4)

5. Tonal functions (`transpose`, `scaleTranspose`, `chord`)
6. More time modifiers (`compress`, `swing`, `ply`)
7. Pattern transformations (`jux`, `off`, `superimpose`)
8. Additional continuous signals (`rand`, `perlin`, bipolar versions)

### Phase 3: Advanced Audio (Weeks 5-6)

9. Complete filter envelopes
10. Advanced effects (`tremolo`, `phaser`, `compressor`)
11. Extended synthesis parameters (FM, wavetable)
12. Sample slicing (`chop`, `slice`, `striate`)

### Phase 4: Integration (Weeks 7-8)

13. MIDI output support
14. Pattern operations (`palindrome`, `iter`, `zoom`)
15. Conditional functions (`sometimes`, `rarely`, `degradeBy`)
16. Tempo control functions

### Phase 5: Nice-to-Have (Future)

17. MIDI input
18. OSC support
19. Visualization tools
20. Hydra integration

---

## 💡 Notes

- Many missing features share similar patterns and could be implemented in batches
- The DSL architecture is solid and extensible - adding new functions is straightforward
- Focus on features that are commonly used in Strudel patterns (see priority markers ⭐)
- Some features (like visualization) may require platform-specific implementations
- MIDI/OSC may need additional platform dependencies
