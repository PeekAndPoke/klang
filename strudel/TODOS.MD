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

- [x] **`cat()`** - Concatenates patterns, each taking one full cycle
- [ ] **`polymeter()`** - Aligns steps of patterns creating polymeters
- [ ] **`polymeterSteps()`** - Polymeter with step specification
- [ ] **`fastcat()`** - Concatenates patterns, all in one cycle
- [ ] **`slowcat()`** - Alias for cat, each pattern takes one cycle
- [ ] **`slowcatPrime()`** - Like slowcat but maintains relative timing
- [ ] **`stackLeft()`** / **`stackRight()`** / **`stackCentre()`** - Aligned stack variants
- [ ] **`stackBy(by, ...pats)`** - Stack with custom alignment (0=left, 0.5=center, 1=right)
- [ ] **`run(n)`** - Creates discrete pattern of numbers 0 to n-1
- [ ] **`binary(n)`** - Creates binary pattern from number
- [ ] **`binaryN(n, bits)`** - Binary pattern padded to n bits
- [ ] **`sequenceP()`** - Pattern of patterns sequence
- [ ] **`pure(value)`** - Creates atomic pattern with single value

### Time Modification ⭐⭐⭐

- [x] **`fast(factor)`** - Speeds up pattern (alias: `density`)
- [x] **`slow(factor)`** - Slows down pattern (alias: `sparsity`)
- [ ] **`early(cycles)`** - Nudge pattern earlier in time
- [ ] **`late(cycles)`** - Nudge pattern later in time
- [ ] **`euclid(pulses, steps)`** - Euclidean rhythm function
- [ ] **`euclidRot(pulses, steps, rotation)`** - Euclidean with rotation
- [ ] **`euclidLegato(pulses, steps)`** - Holds pulses until next
- [ ] **`compress(start, end)`** - Compresses pattern into timespan
- [ ] **`compressSpan(span)`** - Compresses pattern into span
- [ ] **`focus(start, end)`** - Like compress but keeps cycle timing
- [ ] **`focusSpan(span)`** - Focus with span parameter
- [ ] **`zoom(start, end)`** - Plays portion of pattern stretched to cycle
- [ ] **`zoomArc(arc)`** - Zoom with arc parameter
- [ ] **`fastGap(factor)`** - Speeds up with gaps between
- [ ] **`swingBy(subdivision, offset)`** - Creates swing rhythm
- [ ] **`swing(subdivision)`** - Swing with default 1/3 offset
- [ ] **`ply(n)`** - Repeats each event n times
- [ ] **`plyWith(n, func)`** - Repeats with function applied
- [ ] **`plyForEach(n, func)`** - Repeats with function applied per repetition
- [ ] **`hurry(factor)`** - Speeds up and shortens events
- [ ] **`loopAt(cycles)`** - Fit sample to cycles
- [ ] **`loopAtCps(cps)`** - Fit sample to cycles per second

### Pattern Transformation ⭐⭐⭐

- [x] **`rev()`** - Reverses pattern in each cycle
- [ ] **`revv()`** - Reverses pattern vertically (pitch inversion)
- [x] **`struct(pattern)`** - Structures pattern according to mask (uses 'x' markers)
- [ ] **`jux(function)`** - Apply function to right channel only
- [ ] **`juxBy(amount, function)`** - Jux with custom stereo width
- [ ] **`off(time, function)`** - Copies, shifts, and modifies pattern
- [x] **`superimpose(function)`** - Layers modified pattern on top
- [x] **`mask(pattern)`** - Conditionally applies pattern elements (boolean masking)
- [x] **`apply(function)`** - Alias for layer
- [ ] **`applyN(n, func)`** - Apply function n times
- [ ] **`when(condition, func)`** - Conditionally apply function
- [ ] **`within(start, end, func)`** - Apply function within time range
- [ ] **`brak()`** - Make every other cycle syncopated
- [ ] **`inv()` / `invert()`** - Invert pattern structure

### Tonal Functions ⭐⭐⭐

- [ ] **`transpose(semitones)`** - Transpose notes by semitones
- [ ] **`scaleTranspose(steps)`** - Transpose within scale
- [ ] **`chord()`** - Create chord patterns
- [ ] **`voicing()`** - Generate chord voicings with full parameters
- [ ] **`rootNotes(octave)`** - Extract root notes from chords

### Arithmetic & Math Functions ⭐⭐⭐

- [x] **`add()`** - Addition operation
- [x] **`sub()`** - Subtraction operation
- [x] **`mul()`** - Multiplication operation
- [x] **`div()`** - Division operation
- [x] **`mod()`** - Modulo operation
- [x] **`pow()`** - Power/exponentiation operation
- [x] **`log2()`** - Base-2 logarithm (unary)

### Bitwise Operators ⭐

- [x] **`band()`** - Bitwise AND
- [x] **`bor()`** - Bitwise OR
- [x] **`bxor()`** - Bitwise XOR
- [x] **`blshift()`** - Bitwise left shift
- [x] **`brshift()`** - Bitwise right shift

### Comparison & Logic Operators ⭐⭐

- [x] **`lt()`** - Less than comparison
- [x] **`gt()`** - Greater than comparison
- [x] **`lte()`** - Less than or equal comparison
- [x] **`gte()`** - Greater than or equal comparison
- [x] **`eq()`** - Equality comparison
- [ ] **`eqt()`** - Truthiness equality comparison
- [x] **`ne()`** - Not equal comparison
- [ ] **`net()`** - Truthiness not equal comparison
- [x] **`and()`** - Logical AND
- [x] **`or()`** - Logical OR

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
- [x] **`perlin`** - Perlin noise
- [ ] **`irand(n)`** - Random integers from 0 to n-1
- [ ] **`brand`** - Binary random (0 or 1)
- [ ] **`brandBy(probability)`** - Binary random with custom probability
- [ ] **`.segment(rate)`** - Sample continuous signal at rate (alias: `seg`)
- [ ] **`.slow(factor)`** - Slow down continuous patterns
- [ ] **`toBipolar()`** - Convert 0-1 range to -1-1
- [ ] **`fromBipolar()`** - Convert -1-1 range to 0-1
- [x] **`range(min, max)`** - Scale continuous pattern to range
- [ ] **`rangex(min, max)`** - Exponential range scaling
- [ ] **`range2(min, max)`** - Bipolar range scaling
- [ ] **`ratio()`** - Get frequency ratio

---

## 🟡 MEDIUM PRIORITY - Advanced Features

### Pattern Operations

- [x] **`palindrome()`** - Alternates forward/backward playback
- [ ] **`iter(subdivisions)`** - Plays pattern subdivisions in order
- [ ] **`iterBack(subdivisions)`** - Plays subdivisions in reverse (alias: `iterback`)
- [ ] **`linger(fraction)`** - Selects and repeats pattern fraction
- [ ] **`ribbon(offset, cycles)`** - Loops pattern section (alias: `rib`)
- [ ] **`inside(cycles, operation)`** - Applies operation within cycle
- [ ] **`outside(cycles, operation)`** - Applies operation outside cycle
- [ ] **`stepcat(...timepats)`** - Concatenate patterns proportionally (aliases: `timecat`, `timeCat`, `s_cat`)
- [ ] **`stepalt(...groups)`** - Alternate between pattern groups (alias: `s_alt`)
- [ ] **`echo(times, delay, decay)`** - Echo with velocity decay (alias: `stut`)
- [ ] **`echoWith(times, delay, function)`** - Echo with custom function (aliases: `echowith`, `stutWith`, `stutwith`)
- [ ] **`press()`** - Press pattern (squash events)
- [ ] **`pressBy(amount)`** - Press pattern by amount
- [ ] **`chunk(n, func)`** - Apply function to chunks (aliases: `slowchunk`, `slowChunk`)
- [ ] **`fastchunk(n, func)`** - Apply function to fast chunks (alias: `fastChunk`)
- [ ] **`chunkinto(n, func)`** - Chunk into n sections (alias: `chunkInto`)
- [ ] **`chunkbackinto(n, func)`** - Chunk backwards into n sections (alias: `chunkBackInto`)
- [ ] **`chunkback(n, func)`** - Chunk backwards (alias: `chunkBack`)

### Conditional & Probabilistic

- [ ] **`sometimes(function)`** - Randomly applies effects
- [ ] **`rarely(function)`** - Infrequently applies variations
- [x] **`degradeBy(amount)`** - Probabilistic event removal
- [ ] **`firstOf(n, func)`** - Apply function on first of every n cycles (alias: `every`)
- [ ] **`lastOf(n, func)`** - Apply function on last of every n cycles
- [ ] **`filter(test)`** - Filter events by test function
- [ ] **`filterWhen(test)`** - Filter events when test is true at start time
- [ ] **`bypass()`** - Conditionally bypass pattern

### Step-Based Pattern Operations ⭐⭐

- [ ] **`pace(targetSteps)`** - Adjust pattern to target number of steps (alias: `steps`)
- [ ] **`take(n)`** - Take first n steps (alias: `s_add`)
- [ ] **`drop(n)`** - Drop first n steps (alias: `s_sub`)
- [ ] **`extend(factor)`** - Extend pattern by factor (alias: `s_extend`)
- [ ] **`replicate(factor)`** - Replicate pattern factor times
- [ ] **`expand(factor)`** - Expand pattern by factor (alias: `s_expand`)
- [ ] **`contract(factor)`** - Contract pattern by factor (alias: `s_contract`)
- [ ] **`shrink(factor)`** - Shrink pattern by factor (alias: `s_taper`)
- [ ] **`shrinklist(amounts)`** - Shrink with list of amounts (alias: `s_taperlist`)
- [ ] **`grow(factor)`** - Grow pattern by factor
- [ ] **`tour(...patterns)`** - Tour through patterns (alias: `s_tour`)
- [ ] **`zip(...patterns)`** - Zip patterns together (alias: `s_zip`)
- [ ] **`bite(n, index)`** - Extract nth slice from pattern
- [ ] **`repeatCycles(n)`** - Repeat pattern for n cycles

### Sample Functions

- [ ] **`loopBegin()`** / **`loopEnd()`** - Loop point control
- [ ] **`striate(portions)`** - Progressive sample portions
- [ ] **`splice(index)`** - Slice with speed adjustment
- [ ] **`scrub()`** - Tape-like scrubbing
- [ ] **`fit()`** - Match sample to event duration
- [ ] **`chop(n)`** - Chop sample into n slices
- [ ] **`slice(n, index)`** - Play slice index from n slices

### Synthesis Parameters

- [ ] **`velocity()`** - MIDI velocity
- [ ] **`postgain()`** - Post-processing gain
- [ ] **`partials(list)`** - Additive synthesis partials
- [ ] **`phases(list)`** - Additive synthesis phases
- [ ] **FM synthesis parameters**:
    - `fmh()` - FM harmonicity ratio
    - `fmattack()`, `fmdecay()`, `fmsustain()`, `fmenv()`
- [ ] **Pitch envelope** - `pattack`, `pdecay`, `prelease`, `penv`
- [ ] **Wavetable synthesis** - `wt_` prefix support with loop points
- [ ] **ZZFX synthesis** - Full parameter set (zrand, curve, slide, etc.)
- [ ] **`arp()`** - Arpeggiator patterns
- [ ] **`arpWith(func)`** - Arpeggiator with custom function

### Noise Generators

- [ ] **`white`** - White noise
- [ ] **`pink`** - Pink noise
- [ ] **`brown`** - Brown noise
- [ ] **`crackle(density)`** - Crackle noise

### Audio Effects (Advanced)

- [ ] **`compressor()`** - Dynamic range compression
- [ ] **`duck()`** - Ducking/sidechain effect
- [ ] **`xfade(pos, b)`** - Crossfade between patterns
- [ ] **`morph(from, to, by)`** - Morph between patterns
- [ ] **`hsla(h, s, l, a)`** - Create HSLA color pattern
- [ ] **`hsl(h, s, l)`** - Create HSL color pattern
- [ ] **`round()`** - Round values to integers
- [ ] **`floor()`** - Floor values to integers
- [ ] **`ceil()`** - Ceil values to integers

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
- [ ] **`isPattern(thing)`** - Check if value is a pattern
- [ ] **`reify(thing)`** - Convert value to pattern
- [ ] **`gap(steps)`** - Create gap with n steps
- [ ] **`nothing`** - Empty pattern with 0 steps
- [ ] **`pure(value)`** - Create atomic pattern with single value

### Pattern Binding Functions

- [ ] **`bind(func)`** - Bind function to pattern
- [ ] **`innerBind(func)`** - Inner bind operation
- [ ] **`outerBind(func)`** - Outer bind operation
- [ ] **`squeezeBind(func)`** - Squeeze bind operation
- [ ] **`stepBind(func)`** - Step-based bind operation
- [ ] **`polyBind(func)`** - Poly bind operation
- [ ] **`set(value)`** - Set value on pattern
- [ ] **`keep(test)`** - Keep events matching test
- [ ] **`keepif(test)`** - Keep if test passes
- [ ] **`withValue(func)`** - Apply function to values
- [ ] **`func(f)`** - Apply arbitrary function
- [ ] **`collect()`** - Collect pattern events
- [ ] **`tag(tag)`** - Tag pattern for identification
- [ ] **`onTriggerTime(func)`** - Execute function on trigger time

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

- **Mini-Notation**: 0 features missing ✅ (8 completed: `:n`, `-`, `@n`, `!n`, `?`, `|`, `(pulses,steps)`,
  `(pulses,steps,rotation)`)
- **Pattern Creation**: ~13 functions
- **Time Modification**: ~23 functions
- **Pattern Transformation**: ~15 functions
- **Tonal Functions**: 5 functions
- **Arithmetic & Math**: 0 missing ✅ (7 completed)
- **Bitwise Operators**: 0 missing ✅ (5 completed)
- **Comparison & Logic**: 2 functions (`eqt`, `net`)
- **Audio Effects**: 15+ parameters
- **Sample Manipulation**: 14 functions
- **Continuous Signals**: ~15 signals/modifiers
- **Synthesis**: 17+ parameters
- **Step-Based Operations**: ~14 functions
- **Conditional & Probabilistic**: ~10 functions
- **Pattern Binding**: ~15 functions
- **MIDI/OSC**: 15+ functions
- **Visualization**: 6 tools
- **System Functions**: ~13 functions

### Total Estimated: ~180 features remaining

### Completed So Far: ~30 features (Mini-notation, arithmetic, bitwise, comparison, basic patterns)

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
