# Strudel Kotlin Port - Pending Work

---

## Things to Check

- [ ] Check https://larkob.github.io/strudel/tutorial/ and produce new todos
- [ ] Review and validate all "missing" counts in the summary section

---

## 🔴 HIGH PRIORITY - Common Use Cases

### Mini-Notation Extensions

#### Robustness and User-Friendly Error Messages

- [ ] The parser must be robust against syntax errors
- [ ] It must always parse "something"
- [ ] It should provide helpful error messages / warnings

### Audio Effects

#### Waveshaping / Distortion

- [ ] **`distort()`** - Check https://strudel.cc/learn/effects/#distort for additional parameters (especially 3rd "
  shape" parameter); see `strudel/agent-tasks/distortion-parameters.md`

#### Other

- [ ] **`xfade()`** - Cross-fade between signals (0-1)

### Sample Manipulation

- [ ] **`chop(n)`** - Chop sample into n slices for granular synthesis
- [ ] **`striate(portions)`** - Partition samples into portions, trigger progressively
- [ ] **`fit()`** - Match sample to event duration (for rhythmic loops)
- [ ] **`scrub(position, speed)`** - Tape-style scrubbing with position/speed control

---

## 🟡 MEDIUM PRIORITY - Advanced Features

### Step-Based Pattern Operations

- [ ] **`replicate(factor)`** - Replicate pattern factor times
- [ ] **`expand(factor)`** - Expand pattern by factor (alias: `s_expand`)
- [ ] **`contract(factor)`** - Contract pattern by factor (alias: `s_contract`)
- [ ] **`shrink(factor)`** - Shrink pattern by factor (alias: `s_taper`)
- [ ] **`shrinklist(amounts)`** - Shrink with list of amounts (alias: `s_taperlist`)
- [ ] **`grow(factor)`** - Grow pattern by factor
- [ ] **`tour(...patterns)`** - Tour through patterns (alias: `s_tour`)
- [ ] **`zip(...patterns)`** - Zip patterns together (alias: `s_zip`)

### Synthesis Parameters

- [ ] **`partials(list)`** - Additive synthesis partials
- [ ] **`phases(list)`** - Additive synthesis phases
- [ ] **Wavetable synthesis** - `wt_` prefix support with loop points
- [ ] **`arp()`** - Arpeggiator patterns
- [ ] **`arpWith(func)`** - Arpeggiator with custom function

### Audio Effects (Advanced)

- [ ] **`xfade(pos, b)`** - Crossfade between patterns
- [ ] **`morph(from, to, by)`** - Morph between patterns

### Audio Engine Enhancements

- [ ] **Convolution Reverb (FFT-based)** - Implement true convolution reverb to support `iResponse()` with impulse
  response samples. Current implementation uses Freeverb (algorithmic reverb). Requires FFT-based convolution processing
  and IR loading/management. Significantly larger task than current reverb.

### Tempo Control

- [ ] **`setcpm()`** / **`setcps()`** - Set cycles per minute/second
- [ ] **`cpm(tempo)`** - Play pattern at specific tempo

---

## 🟢 LOW PRIORITY - Nice to Have

### System Functions

- [ ] **`samples()`** - Load custom sample maps (URLs, GitHub, local)
- [ ] **`soundAlias()`** - Create custom sound aliases

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
