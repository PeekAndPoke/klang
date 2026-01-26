# Strudel Kotlin Port - TODO List

This document lists all features from Strudel.cc that are not yet implemented in the Kotlin port, organized by priority
and category.

---

## Things to check

- [ ] check https://larkob.github.io/strudel/tutorial/ and produce new todos
- [ ] Review and validate all "missing" counts in the summary section


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
- [x] **`euclidish(pulses, steps, groove)`** / **`eish`** - Morphing Euclidean rhythm with groove parameter (
  0=euclidean, 1=even), now supports control patterns

### Pattern Creation ⭐⭐⭐

- [x] **`cat()`** - Concatenates patterns, each taking one full cycle
- [x] **`seq()`** - Sequence patterns (same as JS `sequence`)
- [x] **`mini()`** - Parses input as mini-notation (alias for `seq`)
- [x] **`stack()`** - Stack patterns (play simultaneously)
- [x] **`arrange()`** - Arrange patterns with custom durations
- [x] **`polyrhythm()`** - Alias for stack, creates polyrhythms by playing patterns simultaneously ✅ **IMPLEMENTED**
- [x] **`polymeter()`** - Aligns steps of patterns creating polymeters ✅ **IMPLEMENTED**
- [x] **`polymeterSteps()`** - Polymeter with step specification ✅ **IMPLEMENTED**
- [x] **`fastcat()`** - Concatenates patterns, all in one cycle ✅ **IMPLEMENTED**
- [x] **`slowcat()`** - Alias for cat, each pattern takes one cycle ✅ **IMPLEMENTED**
- [x] **`slowcatPrime()`** - Like slowcat but maintains relative timing ✅ **IMPLEMENTED**
- [x] **`stackLeft()`** / **`stackRight()`** / **`stackCentre()`** - Aligned stack variants ✅ **IMPLEMENTED**
- [x] **`stackBy(by, ...pats)`** - Stack with custom alignment (0=left, 0.5=center, 1=right) ✅ **IMPLEMENTED**
- [x] **`run(n)`** - Creates discrete pattern of numbers 0 to n-1, now supports control patterns
- [x] **`binary(n)`** - Creates binary pattern from number
- [x] **`binaryN(n, bits)`** - Binary pattern padded to n bits
- [x] **`binaryL(n)`** - Creates binary list pattern from number
- [x] **`binaryNL(n, bits)`** - Binary list pattern padded to n bits
- [x] **`sequenceP()`** - Pattern of patterns sequence (alias for seq) ✅ **IMPLEMENTED**
- [x] **`pure(value)`** - Creates atomic pattern with single value ✅ **IMPLEMENTED**
- [x] **`silence`** - Empty pattern
- [x] **`rest`** - Alias for silence

### Time Modification ⭐⭐⭐

- [x] **`fast(factor)`** - Speeds up pattern, now supports control patterns for dynamic speeding (alias: `density`)
- [x] **`slow(factor)`** - Slows down pattern, now supports control patterns for dynamic slowing (alias: `sparsity`)
- [x] **`early(cycles)`** - Nudge pattern earlier in time
- [x] **`late(cycles)`** - Nudge pattern later in time
- [x] **`rev(n)`** - Reverses pattern, now supports control patterns for dynamic reversal
- [x] **`euclid(pulses, steps)`** - Euclidean rhythm function, now supports control patterns (standalone, pattern
  method, string extension)
- [x] **`euclidRot(pulses, steps, rotation)`** / **`euclidrot`** - Euclidean with rotation, now supports control
  patterns (standalone, pattern method,
  string extension)
- [x] **`bjork([pulses, steps, rotation])`** - Euclidean with list parameters, now supports control patterns (
  standalone, pattern method, string
  extension)
- [x] **`euclidLegato(pulses, steps)`** - Holds pulses until next, now supports control patterns (standalone, pattern
  method, string extension)
- [x] **`euclidLegatoRot(pulses, steps, rotation)`** - Legato Euclidean with rotation, now supports control patterns (
  standalone, pattern method,
  string extension)
- [x] **`compress(start, end)`** - Compresses pattern into timespan, now supports control patterns for both parameters (
  standalone, pattern method, string extension)
- [ ] ~~**`compressSpan(span)`**~~ - **Won't implement**: Not documented, requires TimeSpan/Fraction types not available
  in KlangScript; use `compress(start, end)` instead
- [x] **`focus(start, end)`** - Like compress but keeps cycle timing, now supports control patterns for both
  parameters (
  standalone, pattern method, string extension)
- [ ] ~~**`focusSpan(span)`**~~ - **Won't implement**: Same reason as compressSpan; use `focus(start, end)` if
  implemented
- [x] **`zoom(start, end)`** - Plays portion of pattern stretched to cycle, now supports control patterns for both
  parameters (pattern method, string extension, typed
  function)
- [ ] ~~**`zoomArc(arc)`**~~ - **Won't implement**: Requires Arc type not available in KlangScript; use
  `zoom(start, end)` instead
- [x] **`gap(steps)`** - Create gap with n steps, now supports control patterns via slow()
- [x] **`fastGap(factor)`** - Speeds up with gaps between, now supports control patterns (standalone, pattern method,
  string extension, alias:
  densityGap)
- [x] **`swingBy(swing, n)`** - Creates swing/shuffle rhythm with adjustable timing and duration (custom implementation:
  long-short or short-long patterns based on positive/negative swing values, no overlaps)
- [x] **`swing(n)`** - Swing with default 1/3 swing amount (custom implementation using swingBy)
- [x] **`ply(n)`** - Repeats each event n times within its timespan, now supports control patterns (standalone, pattern
  method, string extension)
- [ ] **`plyWith(n, func)`** - Repeats with function applied
- [ ] **`plyForEach(n, func)`** - Repeats with function applied per repetition
- [x] **`hurry(factor)`** - Speeds up pattern and multiplies speed parameter, now supports control patterns (standalone,
  pattern method, string
  extension)
- [x] **`loopAt(cycles)`** - Fit sample to cycles
- [ ] **`loopAtCps(cps)`** - Fit sample to cycles per second

### Pattern Transformation ⭐⭐⭐

- [x] **`rev(n)`** - Reverses pattern in each cycle, supports control patterns for dynamic reversal
- [ ] **`revv()`** - Reverses pattern vertically (pitch inversion)
- [x] **`struct(pattern)`** - Structures pattern according to mask (uses 'x' markers)
- [x] **`structAll(pattern)`** - Like struct but keeps all overlapping events
- [x] **`mask(pattern)`** - Conditionally applies pattern elements (boolean masking)
- [x] **`maskAll(pattern)`** - Like mask but keeps all overlapping events
- [x] **`superimpose(function)`** - Layers modified pattern on top
- [x] **`layer(function)`** - Layers multiple function results
- [x] **`apply(function)`** - Alias for layer
- [ ] **`jux(function)`** - Apply function to right channel only
- [ ] **`juxBy(amount, function)`** - Jux with custom stereo width
- [ ] **`off(time, function)`** - Copies, shifts, and modifies pattern
- [ ] **`applyN(n, func)`** - Apply function n times
- [ ] **`when(condition, func)`** - Conditionally apply function
- [ ] **`within(start, end, func)`** - Apply function within time range
- [ ] **`brak()`** - Make every other cycle syncopated
- [ ] **`inv()` / `invert()`** - Invert pattern structure
- [ ] **`off`** - https://strudel.cc/workshop/pattern-effects/#pattern-effects

### Pattern Picking & Selection ⭐⭐⭐

- [x] **`chooseWith(selector, choices)`** - Choose based on selector pattern (standalone, pattern method, string
  extension)
- [x] **`chooseInWith(selector, choices)`** - Choose with inner structure preserved (standalone, pattern method, string
  extension)
- [x] **`choose(...choices)`** - Random choice from list (standalone, pattern method, string extension)
- [x] **`chooseOut(...choices)`** - Alias for choose (standalone, pattern method, string extension)
- [x] **`chooseIn(...choices)`** - Random choice with inner structure (standalone, pattern method, string extension)
- [x] **`choose2(...choices)`** - Choice with bipolar selector -1..1 (pattern method, string extension)
- [x] **`chooseCycles(...choices)`** - Pick one per cycle (standalone, pattern method, string extension)
- [x] **`randcat(...choices)`** - Alias for chooseCycles (standalone, pattern method, string extension)
- [x] **`wchoose(...[value, weight])`** - Weighted random choice (standalone, pattern method, string extension)
- [x] **`wchooseCycles(...[value, weight])`** - Weighted choice per cycle (standalone, pattern method, string extension)
- [x] **`wrandcat(...[value, weight])`** - Alias for wchooseCycles (standalone, pattern method, string extension)
- [ ] **`pick(lookup, pat)`** - Picks patterns/values from list (by index) or lookup table (by name)
- [ ] **`pickmod(lookup, pat)`** - Like pick but wraps around when index exceeds list size
- [ ] **`pickF(lookup, funcs, pat)`** - Use pattern of numbers to pick which function to apply
- [ ] **`pickmodF(lookup, funcs, pat)`** - Like pickF but wraps around
- [ ] **`pickOut(lookup, pat)`** - Like pick but uses outerJoin instead of innerJoin
- [ ] **`pickmodOut(lookup, pat)`** - Like pickOut but wraps around
- [ ] **`pickRestart(lookup, pat)`** - Like pick but restarts chosen pattern when triggered
- [ ] **`pickmodRestart(lookup, pat)`** - Like pickRestart but wraps around
- [ ] **`pickReset(lookup, pat)`** - Like pick but resets chosen pattern when triggered
- [ ] **`pickmodReset(lookup, pat)`** - Like pickReset but wraps around
- [ ] **`inhabit(lookup, pat)`** / **`pickSqueeze(lookup, pat)`** - Picks and squeezes cycles into target pattern
- [ ] **`inhabitmod(lookup, pat)`** / **`pickmodSqueeze(lookup, pat)`** - Like inhabit but wraps around
- [ ] **`squeeze(pat, xs)`** - Pick from list via index, compress selected to fit event duration

### Tonal Functions ⭐⭐⭐

- [x] **`note()`** - Set note values
- [x] **`n()`** - Set note index or sample number
- [ ] **`freq()`** - Set frequencies in hz
- [x] **`scale()`** - Set scale for note resolution
- [x] **`sound()` / `s()`** - Set sound/sample bank
- [x] **`bank()`** - Set sample bank
- [x] **`transpose(semitones)`** - Transpose notes by semitones
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
- [x] **`eqt()`** - Truthiness equality comparison
- [x] **`ne()`** - Not equal comparison
- [x] **`net()`** - Truthiness not equal comparison
- [x] **`and()`** - Logical AND
- [x] **`or()`** - Logical OR

### Audio Effects ⭐⭐⭐

#### Filter Effects

- [x] **`lpf()`** - Low pass filter (aliases: `cutoff`, `ctf`, `lp`)
- [x] **`lpq()`** - Low pass filter Q/resonance (alias: `resonance`, `res`)
- [x] **`hpf()`** - High pass filter (aliases: `hp`, `hcutoff`)
- [x] **`hpq()`** - High pass filter Q/resonance (aliases: `hresonance`, `hres`)
- [x] **`bpf()`** - Band pass filter (aliases: `bandf`, `bp`)
- [x] **`bpq()`** - Band pass filter Q/resonance (alias: `bandq`)
- [x] **`notchf()`** - Notch filter
- [x] **`nresonance()`** - Notch filter resonance (alias: `nres`)
- [ ] **`ftype()`** - Filter type (12db, ladder, 24db)
- [ ] **`vowel()`** - Vowel formant filter (a, e, i, o, u, ae, aa, oe, ue, y, uh, un, en, an, on)

#### Filter Envelopes - Lowpass

- [x] **`lpattack()`** - Low pass filter envelope attack time (alias: `lpa`)
- [x] **`lpdecay()`** - Low pass filter envelope decay time (alias: `lpd`)
- [x] **`lpsustain()`** - Low pass filter envelope sustain level (alias: `lps`)
- [x] **`lprelease()`** - Low pass filter envelope release time (alias: `lpr`)
- [x] **`lpenv()`** - Low pass filter envelope amount (alias: `lpe`)

#### Filter Envelopes - Highpass

- [x] **`hpattack()`** - High pass filter envelope attack time (alias: `hpa`)
- [x] **`hpdecay()`** - High pass filter envelope decay time (alias: `hpd`)
- [x] **`hpsustain()`** - High pass filter envelope sustain level (alias: `hps`)
- [x] **`hprelease()`** - High pass filter envelope release time (alias: `hpr`)
- [x] **`hpenv()`** - High pass filter envelope amount (alias: `hpe`)

#### Filter Envelopes - Bandpass

- [x] **`bpattack()`** - Band pass filter envelope attack time (alias: `bpa`)
- [x] **`bpdecay()`** - Band pass filter envelope decay time (alias: `bpd`)
- [x] **`bpsustain()`** - Band pass filter envelope sustain level (alias: `bps`)
- [x] **`bprelease()`** - Band pass filter envelope release time (alias: `bpr`)
- [x] **`bpenv()`** - Band pass filter envelope amount (alias: `bpe`)

#### Pitch Envelope

- [ ] **`pattack()`** - Pitch envelope attack time (alias: `patt`)
- [ ] **`pdecay()`** - Pitch envelope decay time (alias: `pdec`)
- [ ] **`prelease()`** - Pitch envelope release time (alias: `prel`)
- [ ] **`penv()`** - Pitch envelope amount in semitones
- [ ] **`pcurve()`** - Pitch envelope curve (0=linear, 1=exponential)
- [ ] **`panchor()`** - Pitch envelope range anchor (0-1)

#### Waveshaping / Distortion

- [x] **`distort()`** - Distortion effect (alias: `dist`)
- [x] **`crush()`** - Bit crusher effect (1-16 bits)
- [x] **`coarse()`** - Sample rate reduction
- [ ] **`shape()`** - Wave distortion amount

#### Amplitude Modulation / Tremolo

- [ ] **`tremolosync()`** - Tremolo modulation speed in cycles (alias: `tremsync`)
- [ ] **`tremolodepth()`** - Tremolo depth of modulation (alias: `tremdepth`)
- [ ] **`tremoloskew()`** - Tremolo waveform shape 0-1 (alias: `tremskew`)
- [ ] **`tremolophase()`** - Tremolo phase offset in cycles (alias: `tremphase`)
- [ ] **`tremoloshape()`** - Tremolo waveform type: tri, square, sine, saw, ramp (alias: `tremshape`)

#### Dynamics

- [ ] **`velocity()`** - Volume level 0-1, multiplied with gain
- [ ] **`postgain()`** - Post-effect gain boost
- [ ] **`compressor()`** - Dynamic range compression "threshold:ratio:knee:attack:release"

#### Panning

- [ ] **`jux()`** - Apply function to right channel only
- [ ] **`juxBy()`** - Adjustable stereo width jux (alias: `juxby`; 0=mono, 1=full stereo)

#### Global Effects - Reverb

- [x] **`room()`** - Reverb mix level (0-1)
- [x] **`roomsize()`** - Reverb room size (0-10) (alias: `rsize`) **MISSING ALIASES: `sz`, `size`**
- [ ] **`roomfade()`** - Reverb fade time in seconds (alias: `rfade`)
- [ ] **`roomlp()`** - Reverb lowpass start frequency in Hz (alias: `rlp`)
- [ ] **`roomdim()`** - Reverb lowpass frequency at -60dB in Hz (alias: `rdim`)
- [ ] **`iresponse()`** - Impulse response sample (alias: `ir`)

#### Global Effects - Delay

- [x] **`delay()`** - Delay signal level (0-1)
- [x] **`delaytime()`** - Delay duration
- [x] **`delayfeedback()`** - Delay feedback amount (aliases: `delayfb`, `dfb`)

#### Global Effects - Phaser

- [ ] **`phaser()`** - Phaser modulation speed (alias: `ph`)
- [ ] **`phaserdepth()`** - Phaser effect amount 0-1, default 0.75 (aliases: `phd`, `phasdp`)
- [ ] **`phasercenter()`** - Phaser center frequency in Hz, default 1000 (alias: `phc`)
- [ ] **`phasersweep()`** - Phaser LFO sweep range in Hz, default 2000 (alias: `phs`)

#### Global Effects - Duck / Sidechain

- [ ] **`duckorbit()`** - Target orbit number for ducking (alias: `duck`)
- [ ] **`duckattack()`** - Duck return-to-normal time in seconds (alias: `duckatt`)
- [ ] **`duckdepth()`** - Ducking amount (0-1)

#### Other

- [ ] **`xfade()`** - Cross-fade between signals (0-1)
- [x] **`orbit()`** - Global effect context grouping **MISSING ALIAS: `o`**

### Sample Manipulation ⭐⭐⭐

- [x] **`begin(pos)`** - Skip to sample position (0..1)
- [x] **`end(pos)`** - Cut sample at position (0..1)
- [x] **`speed(rate)`** - Playback speed (including negative)
- [x] **`loop()`** - Loop sample
- [ ] **`loopBegin()` / `loopb()`** - Set loop start point within sample
- [ ] **`loopEnd()` / `loope()`** - Set loop end point within sample
- [x] **`loopAt(cycles)`** - Fit sample to cycles
- [ ] **`loopAtCps(cps)`** - Fit sample to cycles per second
- [x] **`cut(group)`** - Stop playing samples in cutgroup
- [ ] **`chop(n)`** - Chop sample into n slices for granular synthesis
- [x] **`slice(n, index)`** - Play slice index from n slices
- [ ] **`splice(n, index)`** - Like slice but adjusts speed to match step duration
- [ ] **`striate(portions)`** - Partition samples into portions, trigger progressively
- [ ] **`fit()`** - Match sample to event duration (for rhythmic loops)
- [ ] **`scrub(position, speed)`** - Tape-style scrubbing with position/speed control
### Continuous Signals ⭐⭐

- [x] **`steady(value)`** - Constant value continuous pattern
- [x] **`signal(callback)`** - Continuous pattern from callback function
- [x] **`time`** - Current time (in cycles) as continuous pattern
- [x] **`sine`** - Sine wave oscillator (0 to 1)
- [x] **`sine2`** - Sine wave oscillator (-1 to 1)
- [x] **`cosine`** - Cosine wave oscillator (0 to 1)
- [x] **`cosine2`** - Cosine wave oscillator (-1 to 1)
- [x] **`saw`** - Sawtooth wave (0 to 1)
- [x] **`saw2`** - Sawtooth wave (-1 to 1)
- [x] **`isaw`** - Inverse sawtooth (1 to 0)
- [x] **`isaw2`** - Inverse sawtooth (1 to -1)
- [x] **`tri`** - Triangle wave (0 to 1 to 0)
- [x] **`tri2`** - Triangle wave (-1 to 1 to -1)
- [x] **`itri`** - Inverse triangle wave (1 to 0 to 1)
- [x] **`itri2`** - Inverse triangle wave (-1 to 1 to -1)
- [x] **`square`** - Square wave (0 or 1)
- [x] **`square2`** - Square wave (-1 or 1)
- [x] **`perlin`** - Perlin noise with seeded permutation table
- [x] **`berlin`** - Berlin noise (sawtooth-based noise, conceived as a joke but surprisingly useful)
- [x] **`range(min, max)`** - Scale continuous pattern to range
- [x] **`rand`** - Random signal (unipolar, 0 to 1)
- [x] **`rand2`** - Random signal (bipolar, -1 to 1)
- [x] **`brand`** - Binary random (0 or 1, 50:50 probability)
- [x] **`brandBy(probability)`** - Binary random with custom probability, now supports control patterns
- [x] **`irand(n)`** - Random integers from 0 to n-1 (continuous pattern), now supports control patterns
- [x] **`randL(n)`** - Creates list of random numbers of length n (sequence pattern), now supports control patterns
- [x] **`.segment(rate)`** - Sample continuous signal at rate, now supports control patterns (pattern method, string
  extension, typed function, alias:
  `seg`)
- [x] **`.slow(factor)`** - Slow down patterns, now supports control patterns for dynamic slowing
- [x] **`toBipolar()`** - Convert 0-1 range to -1..1 (pattern method, string extension)
- [x] **`fromBipolar()`** - Convert -1..1 range to 0-1 (pattern method, string extension)
- [x] **`perlin2`** - Bipolar Perlin noise (-1 to 1)
- [x] **`berlin2`** - Bipolar Berlin noise (-1 to 1)
- [x] **`rangex(min, max)`** - Exponential range scaling (unipolar 0-1 to min-max with exponential curve)
- [x] **`range2(min, max)`** - Bipolar range scaling (-1 to 1 to min-max)
- [x] **`round()`** - Round values to nearest integer
- [x] **`floor()`** - Floor values to integers
- [x] **`ceil()`** - Ceil values to integers
- [x] **`ratio()`** - Divide numbers via list notation using ":" (e.g., "5:4" = 1.25)

### Random & Seeding ⭐⭐⭐

- [x] **`seed(n)`** / **`withSeed(n)`** - Set random seed for deterministic pattern generation (pattern method, string
  extension)
- [x] **`randrun(n)`** - Creates shuffled sequence 0..n-1 (changes per cycle), now supports control patterns
- [x] **`shuffle(n)`** - Slices pattern into n parts, plays in random order, now supports control patterns (pattern
  method, string extension)
- [x] **`scramble(n)`** - Slices pattern into n parts, plays at random with repetition, now supports control patterns (
  pattern method, string
  extension)

---

## 🟡 MEDIUM PRIORITY - Advanced Features

### Pattern Operations

- [x] **`palindrome()`** - Alternates forward/backward playback
- [ ] **`iter(subdivisions)`** - Plays pattern subdivisions in order
- [ ] **`iterBack(subdivisions)`** - Plays subdivisions in reverse (alias: `iterback`)
- [ ] **`linger(fraction)`** - Selects and repeats pattern fraction
- [ ] **`ribbon(offset, cycles)`** - Loops pattern section (alias: `rib`)
- [x] **`inside(factor, operation)`** - Applies operation within cycle (slows pattern, applies function, then speeds
  back up)
- [x] **`outside(factor, operation)`** - Applies operation outside cycle (speeds pattern, applies function, then slows
  back down)
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

- [x] **`sometimesBy(prob, fn)`** - Randomly applies transformation with given probability (pattern method, string
  extension)
- [x] **`sometimes(function)`** - Randomly applies effects (50% probability)
- [x] **`often(function)`** - Frequently applies variations (75% probability)
- [x] **`rarely(function)`** - Infrequently applies variations (25% probability)
- [x] **`almostAlways(function)`** - Almost always applies variations (90% probability)
- [x] **`almostNever(function)`** - Almost never applies variations (10% probability)
- [x] **`always(function)`** - Always applies transformation (100% probability)
- [x] **`never(function)`** - Never applies transformation (0% probability)
- [x] **`degradeBy(amount)`** - Probabilistic event removal (pattern method, string extension, with pattern control)
- [x] **`degrade()`** - Probabilistic event removal with 50% probability (pattern method and string extension)
- [x] **`undegradeBy(amount)`** - Inverse of degradeBy - probabilistic event removal (pattern method, string extension,
  with pattern control)
- [x] **`undegrade()`** - Inverse of degrade - probabilistic event removal with 50% probability (pattern method and
  string extension)
- [x] **`someCyclesBy(prob, fn)`** - Cycle-based random application with proper cycle-locking (pattern method, string
  extension)
- [x] **`someCycles(fn)`** - Cycle-based random application with 50% probability (pattern method, string extension)
- [x] **`firstOf(n, func)`** - Apply function on first of every n cycles, now supports control patterns (pattern method,
  string extension)
- [x] **`every(n, func)`** - Alias for `firstOf`, now supports control patterns (pattern method, string extension)
- [x] **`lastOf(n, func)`** - Apply function on last of every n cycles, now supports control patterns (pattern method,
  string extension)
- [x] **`filter(test)`** - Filter events by test function (pattern method, string extension, standalone function)
- [x] **`filterWhen(test)`** - Filter events when test is true at start time (pattern method, string extension,
  standalone function)
- [x] **`bypass(condition)`** - Conditionally bypass pattern (pattern method, string extension, standalone function)

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
- [x] **`bite(n, indices)`** - Slices pattern and plays according to index pattern, now supports control patterns (
  pattern method, string extension)
- [ ] **`repeatCycles(n)`** - Repeat pattern for n cycles

### Sample Functions (Advanced)

**Note:** Basic sample functions are listed in HIGH PRIORITY - Sample Manipulation section above.

- [ ] **`loopBegin()` / `loopb()`** - Loop point control
- [ ] **`loopEnd()` / `loope()`** - Loop point control
- [ ] **`striate(portions)`** - Progressive sample portions
- [ ] **`splice(n, index)`** - Slice with speed adjustment
- [ ] **`scrub(position, speed)`** - Tape-like scrubbing
- [ ] **`fit()`** - Match sample to event duration
- [ ] **`chop(n)`** - Chop sample into n slices

### Synthesis Parameters

- [x] **`gain()`** - Amplitude/volume
- [x] **`pan()`** - Stereo panning
- [x] **`legato()` / `clip()`** - Note length
- [x] **`vibrato()` / `vib()`** - Vibrato frequency
- [x] **`vibratoMod()` / `vibmod()`** - Vibrato depth
- [x] **`accelerate()`** - Pitch/speed acceleration
- [x] **`unison()` / `uni()`** - Number of voices
- [x] **`detune()`** - Frequency spread
- [x] **`spread()`** - Pan spread
- [x] **`density()` / `d()`** - Voice density
- [x] **`orbit()`** - Audio routing
- [x] **ADSR envelope**: `attack()`, `decay()`, `sustain()`, `release()`, `adsr()`
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
- [/] **`initStrudel()`** - Initialize Strudel environment (partially done via `initStrudelLang()`)
- [X] **`hush()`** - Stop all playing patterns
- [/] **`isPattern(thing)`** - Check if value is a pattern (can check with `is StrudelPattern` but no dedicated
  function)
- [/] **`reify(thing)`** - Convert value to pattern (partially via `toPattern()` helper)
- [ ] **`nothing`** - Empty pattern with 0 steps (different from `silence` which has implied duration)
- [x] **`pure(value)`** - Create atomic pattern with single value (via `AtomicPattern.pure`)

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

### Arithmetic Addons (Non-Strudel) ✅

**These functions are NOT available in the original Strudel implementation**

- [x] **`flipSign()`** - Flips the sign of numerical values (renamed from `negateValue`) (pattern method, string
  extension)
- [x] **`oneMinusValue()`** - Calculates 1.0 - value (pattern method, string extension)
- [x] **`not()`** - Boolean NOT operation on values (pattern method, string extension)

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
- [ ] **Pattern queryArc optimization** - Cache/memoization for repeated queries
- [ ] **Event metadata** - Track event IDs, origins for debugging

### Mini-Notation Parser ✅

- [x] **Simple cache** - Cache parsed patterns to avoid re-parsing identical input ✅ **IMPLEMENTED**
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

- **Mini-Notation**: ✅ 0 missing (8 completed: `:n`, `-`, `@n`, `!n`, `?`, `|`, euclidean, cache)
- **Pattern Creation**: ✅ 0 missing (21 completed: `seq`, `mini`, `sequenceP`, `stack`, `cat`, `arrange`, `silence`/
  `rest`, `run`,
  `binary`, `binaryN`, `binaryL`, `binaryNL`, `fastcat`, `slowcat`, `slowcatPrime`, `polymeter`, `polymeterSteps`,
  `polyrhythm`,
  `stackLeft`, `stackRight`, `stackCentre`, `stackBy`, `pure`)
- **Time Modification**: ~3 missing, 3 won't implement (20 completed: `fast`, `slow`, `early`, `late`, `gap`, `zoom`,
  `compress`, `focus`, `ply`,
  `hurry`, `fastGap`, `euclid`, `swing`, `swingBy`, `loopAt`,
  `euclidRot`, `euclidLegato`, `euclidLegatoRot`, `bjork`, `euclidish`; many with control pattern support; won't
  implement: `compressSpan`, `focusSpan`, `zoomArc` - require types not in KlangScript)
- **Pattern Operations**: ~9 missing (4 completed: `rev`, `palindrome`, `inside`, `outside`)
- **Pattern Transformation**: ~11 missing (7 completed: `struct`, `mask`, `superimpose`, `layer`, `apply`, `structAll`,
  `maskAll`)
- **Tonal Functions**: ~4 missing (6 completed: `note`, `n`, `scale`, `sound`, `bank`, `transpose`)
- **Arithmetic & Math**: ✅ 0 missing (7 completed)
- **Bitwise Operators**: ✅ 0 missing (5 completed)
- **Comparison & Logic**: 2 missing (8 completed)
- **Audio Effects**: ~46 missing + 2 partial (15 completed: filters with full aliases, filter resonance, distortion,
  reverb, delay; partial: roomsize missing sz/size aliases, orbit missing o alias)
- **Sample Manipulation**: 9 missing (6 completed: `begin`, `end`, `speed`, `loop`, `loopAt`, `cut`, `slice`; 9 missing:
  `loopBegin/loopb`, `loopEnd/loope`, `loopAtCps`, `chop`, `splice`, `striate`, `fit`, `scrub`)
- **Continuous Signals**: ✅ 0 missing (41 completed, many with control pattern support: `steady`, `signal`, `time`,
  `sine`, `sine2`, `cosine`, `cosine2`,
  `saw`, `saw2`, `isaw`, `isaw2`, `tri`, `tri2`, `itri`, `itri2`, `square`, `square2`, `perlin`, `perlin2`, `berlin`,
  `berlin2`, `range`, `rangex`, `range2`,
  `rand`, `rand2`, `irand` (control patterns), `randL` (control patterns), `brand`, `brandBy` (control patterns),
  `segment` (control patterns), `seg`, `toBipolar`, `fromBipolar`, `round`, `floor`, `ceil`, `ratio`)
- **Synthesis Parameters**: ~13 missing (16 completed: gain, pan, ADSR, vibrato, unison, etc.)
- **Step-Based Operations**: ~13 missing (1 completed with control pattern support: `bite`)
- **Conditional & Probabilistic**: ✅ 0 missing (20 completed, with control pattern support: `sometimesBy`, `sometimes`,
  `often`, `rarely`,
  `almostAlways`, `almostNever`, `always`, `never`, `someCyclesBy`, `someCycles`, `degradeBy`, `degrade`, `undegradeBy`,
  `undegrade`, `firstOf` (control patterns), `every` (control patterns), `lastOf` (control patterns), `filter`,
  `filterWhen`, `bypass`)
- **Arithmetic Addons (Non-Strudel)**: 0 missing (3 completed: `flipSign`, `oneMinusValue`, `not`)
- **Pattern Binding**: ~15 missing (0 completed)
- **MIDI/OSC**: ~15 missing (0 completed)
- **Visualization**: ~6 missing (0 completed)
- **System Functions**: ~11 missing (2 completed: `hush`, `pure`; 3 partial)

- **Pattern Picking & Selection**: ~13 missing (11 completed: `chooseWith`, `chooseInWith`, `choose`, `chooseOut`,
  `chooseIn`, `choose2`, `chooseCycles`, `randcat`, `wchoose`, `wchooseCycles`, `wrandcat`)

### Total Estimated: ~92 features remaining (3 marked "won't implement")

### Completed So Far: ~207 features! 🎉

**Note:** Audio Effects section has been reorganized with detailed categorization. Many new functions identified from
Strudel documentation.

**Major achievements:**

- ✅ Complete mini-notation support with parser cache
- ✅ All arithmetic, bitwise, and comparison operators
- ✅ Core pattern creation and transformation
- ✅ Comprehensive audio effects and synthesis parameters
- ✅ Full ADSR envelope support
- ✅ Continuous signal generators
- ✅ **Control pattern support for numerical functions** - Major initiative completed!

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
