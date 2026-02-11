# Project history

## Diary

### (2026-01-31) Strudel - Part/Whole Refactoring & Verification

**Implementation Phase (Morning):**
- Implemented part/whole structure for StrudelPatternEvent to match JavaScript Strudel behavior
- Added TimeSpan data class with helper methods (shift, scale, clipTo)
- Updated all pattern operations to preserve whole through clipping operations
- Added hasOnset() filter in StrudelPlayback to only play onset events
- Continuous patterns (sine, saw) now use whole = null and always play
- Updated JavaScript interop to extract both part and whole from JS events
- Critical fix: prevents incorrect event playback for clipped patterns
- Build status: ✅ Compiles successfully
- Test status: ✅ 96% passing (2374/2476 tests, 102 failures in timing edge cases)

**Verification Phase (Afternoon):**

- Created comprehensive verification plan for 33 pattern classes and 14 helper methods
- **Phase 1 Verification Completed** - verified 16 pattern classes and 7 helper methods
- Critical fix: Removed default value `= true` from BindPattern clip parameter (user-reported bug)
- Verified patterns: ControlPattern, ChoicePattern, StackPattern, GapPattern, SometimesPattern, RandLPattern,
  MapPattern, ReinterpretPattern, ContextModifierPattern, ContextRangeMapPattern, PropertyOverridePattern, EmptyPattern,
  and 4 others
- Verified helper methods: _bind, _outerJoin, _applyControl, _lift, _liftData, _liftValue, _liftNumericField
- Result: Zero part/whole violations found - all patterns follow correct principles
- Documentation: Updated implementation and verification plan documents with detailed results
- Next: Address 102 failing tests and add comprehensive unit test coverage

### (2026-01-28) Strudel - Audio Effects

- phaser
- tremolo
- vowel()
- custom warmth() for synth voices
- custom morse()

### (2026-01-28) Strudel - Tonal Functions

- tonal functions integration

### (2026-01-28) Klang - Filter Envelopes

- filter envelopes implementation

### (2026-01-27) Strudel - Pattern Refactoring

- major pattern refactoring
- pattern picking functions

### (2026-01-26) Strudel - DSL & Cleanup

- new dsl functions
- pattern picking functions
- pattern code cleanup (createEventList, RandLPattern, FilterPattern, StepsOverridePattern, AlignedPattern, GapPattern,
  ContextRangeMapPattern)
- preps for gc free implementation

### (2026-01-25) Strudel - Pattern Cleanup

- pattern code cleanup (TimeShiftPattern, FastGapPattern, HurryPattern)

### (2026-01-23) Klang Studio

- started Klang Studio
- TempoModifierPattern refactoring

### (2026-01-21) Strudel - Control Patterns

- unifying Pattern and PatternWithControl architecture
- compress(), ply(), fastGap(), hurry(), focus() with control patterns
- fix pan() (left = 0.0, right = 1.0)

### (2026-01-21) Klang - Voice Data Separation

- separating StrudelVoiceData from VoiceData

### (2026-01-21) Klang Notebook

- prototype working

### (2026-01-20) KlangStudio - Live Coding

- live coding editor highlighting working
- KlangScript source location tracking

### (2026-01-19) KlangStudio - Editor

- CodeMirror integration
- KlangScript source location tracking

### (2026-01-18) Klang - Infrastructure

- infra refactor
- KlangPlayer refactoring
- KlangStudio first steps to frontend

### (2026-01-17) KlangPlayer - Performance

- jvm max priority threads for audio backend
- surface refactoring
- Strudel sample manipulation (wip)

### (2026-01-16) Strudel - DSL

- pattern creation functions (polyrhythm, stackBy, stackLeft, stackRight, stackCentre, sequenceP)
- fastcat(), polymeter(), pure(), slowcat()
- control pattern support for many functions (firstOf, lastOf, brandBy, irand, euclidean patterns, segment, bite, slow,
  fast, gap, rev)
- MiniNotationParser cache

### (2026-01-15) Strudel - Euclidean & Binary

- binary(), binaryN(), binaryL(), binaryNL(), euclidish(), eish(), run()
- euclidean rhythm functions (bjork, euclid, euclidRot, euclidLegato, euclidLegatoRot)
- n() now populates soundIndex

### (2026-01-15) Strudel - DSL

- random choice functions

### (2026-01-14) KlangScript

- comparison expressions
- array functions with { ... return x } bodies

### (2026-01-14) Strudel - DSL

- random functions and patterns
- continuous patterns
- filter functions

### (2026-01-13) Strudel - Fights with floating point numbers

- tried to use a full Rational implementation, but failed
- switched back to the fix-point approach with a small hack in SequencePattern

### (2026-01-12) Strudel - DSL

- more strudel dsl functions
- continuous patterns

### (2026-01-11) Strudel - DSL

- more strudel dsl functions

### (2026-01-10) Strudel - Cleanup and refactoring

- streamlining the api surface of all strudel functions
  - making them extension on StrudelPattern
  - making them extension on String
  - making all of this available to KlangScript as well
- Generalized StructurePattern so cover struct(), structAll(), mask(), maskAll(), etc...

### (2026-01-09) Strudel - New features

- compatibility tests
  - we compile patterns with GraalJS and compare the event sequence to the native KlangScript+Strudel result
  - very helpful!
- ensured compatibility of continuous patterns with strudel
- added struct()
- added mask()
- finally implemented n() ... "stranger things" now perfect
  - missing is lpenv, perlin, superimpose

### (2026-01-08) Strudel - New features

- cat() function
- rev() function
- palindrome() function

### (2026-01-08) Strudel - Tests

- tests for all implemented patterns
- fighting with floating point drift
- introduced fixed floating point math -> Rational.kt
- fixed euclidean pattern implementation

### (2026-01-07) Strudel - Using KlangScript as runtime

- porting strudel dsl to the new KlangScript runtime
- some refactoring on KlangScript kotlin interop and object / method registration
- enhancing MiniNotation parser with more features
  - euclidean patterns

### (2026-01-07) KlangScript

- working on KlangScript StdLib
- adding common math functions
- adding common string functions
- adding common list functions
- adding common object functions

### (2026-01-06) KlangScript

- creating a Javascript-ish interpreter language called KlangScript
- making heavy use of Claude-Agent
- parser based on better-parse
- kotlin interop with klang-script
- multiple refactoring and cleanup rounds

### (2026-01-05) Tones

- ported @tonaljs to kotlin into package "tones"
- heavy use of "Junie" for the port and gemini for cleanup and refactorings

### (2026-01-04) Strudel Code Parser

- starting to build the parser, now that many of the strudel functions are implemented

### (2026-01-03) Strudel DSL

- started implementing the Strudel DSL
- figuring out a generic way to "register" all functions
- first implementation of control patterns

### (2026-01-03) Soundfonts

- fixed the "start in the middle of the sound" issue by simplifying the adsr logic and ScheduledVoice
- rewrite of makeVoice() in the VoiceScheduler, by removing "lateFrames" logic
  - result: more straight scheduling math

### (2026-01-02) Soundfonts

- done with the rewrite on the SampleIndex and loading sounds on demand
- TODO: bring back the sound index to generic sounds
- sound index working for soundfonts
- working on understanding how loops and adsr work in soundfonts
- rework of the adsr logic when creating voices
- issues with playing soundfont voices ... "start in the middle of the sound" issue

### (2026-01-01) Soundfonts

- started to add soundfont support
- requires a rewrite of the SampleIndex and how the sample are loaded on demand

### (2025-12-29) Voice / Strudel

- implemented legato / clip

### (2025-12-28) Idea

When the strudel-parsr is ready build an llm tool that:

- you can talk to, to generate pattern
- you can saw things like "make it sound more 8-bit"
- the information is coming from the pattern-function definitions
- do we need a RAG model?

### (2025-12-28) Strudel / Sound engine

- added crush effect
- added coarse effect
- added accelerate / glisando modulation

### (2025-12-28) JS Audio Worklet - smoother audio samples upload to the backed

- Working on splitting the audio samples into smaller chunks
- ok, not the chunk size was the issue
- the issue was using kotlinx.serialization for FloatArray transfer between the
  main JS thread and the AudioWorklet thread
- wrote custom serialization code in class WorkletContract
- no more stutter when sending 32 kb blocks!

### (2025-12-27) JS Audio Worklet - stuttering when uploading samples

- it turns out that sending big chunks to the AudioWorklet leads to stuttering
- we have to send audio samples in smaller chunks
- So for JS the flow will be:
  - event fetcher sends Cmd.Sample.Complete command
  - JsKlangPlayerBackend splits these into multiple Cmd.Sample.Chunk commands
  - VoiceScheduler combines these

### (2025-12-27) General Architecture struggle

- after struggling with the general multiplatform architecture -> rewrite of audio backends
- resulted in much cleaner and easy to read code, fewer indirection
- we now have a platform specific klangPlayer() function that handles all the wiring
- strudelPlayer() is now defined in the commonMain and takes advantage of the platform specific klangPlayer()

### (2025-12-26) Project structure

- Organization into audio-frontend and audio-backend modules
- Strudel has its own module
  - leads to better separation in general
- Splitting StrudelPlayer into separate concerns:
  - StrudelPlayer
  - StrudelPlayerState
  - StrudelEventsFetcher
  - StrudelAudioLoop

### (2025-12-26) Browser backend

- figuring out how to implement the audio-backend in the browser
- Having trouble with KotlinJS compiler bugs getting the AudioWorkletProcessor
  - found hacky workarounds
- Also tried Wasm ... more problems... will try again later, once the JS backend works

### (2025-12-25) Setting up project for Kotlin Multiplatform

- Created a Kotlin Multiplatform project.
- separating thing into a :core and a very thing :dsp module
- Idea arises: Strudel is just one sound event provider
  - with the correct structure we could build e.g. a midi-player on top of the architecture

### (2025-12-25) Strudel: Distortion

- Implemented distortion and added it to the Strudel engine.

### (2025-12-25) Reverb

- Implemented reverb and added it to the Strudel engine.

### (2025-12-25) Stereo Output & Panning

- Implemented stereo output and panning.
- Before sound generation was only mono.

### (2025-12-24) Strudel: Orbits

- implemented orbits in the Strudel engine.
- allows for separate delay lines for multiple voices on different orbits.

### (2025-12-24) Strudel: Delay

- Added first version of a single mono delay line to the Strudel engine.

### (2025-12-24) Strudel: Envelope (Adsr)

- implemented ADSR envelope generation in the Strudel Voices.

### (2025-12-23) Sound sample banks for melodic sounds

- There are percussive and melodic sound banks
    - Percussive example: https://raw.githubusercontent.com/felixroos/dough-samples/main/tidal-drum-machines.json
    - Melodic example: https://raw.githubusercontent.com/felixroos/dough-samples/main/piano.json

- Thank you @felixroos for the sample files!

- All samples can be played at a pitch / note... added this
- Melodic sound banks have multiple sound files for different pitches
    - add finding the best sound file for a given pitch

### (2025-12-23) Strudel: Added SampleVoice for percussive sounds

- added playback of sound samples in the Strudel engine

### (2025-12-23) Sound sample banks

- started working on sound sample banks
- implemented downloading percussive sound index files
- implemented downloading sound files
- added download caching
- settled on code organization for the sound banks

### (2025-12-23) Refactor and cleanup

- Short refactoring and cleanup session

### (2025-12-23) Did not understand n() vs note()

- fought many hours hunting a bug that does not exist
- n() and note() are different functions in Strudel.
- I used note() and expected it to behave like n()
- The good thing: the js bridge got way simpler in the process

### (2025-12-23) Modulation: Vibrato

- Implemented parameter arrays for frequency modulation.
- Refactored the oscillators to use the parameter arrays.
- Hooked up Strudel Vibrato to the SynthVoice and SampleVoice.

## (2025-12-23) First steps to the Strudel engine

- started working on the Strudel engine
- fetching events (haps) from strudel-js through the GraalVM bridge
- Voice generation
- timed Voice playback
- rendering audio from voices

## (2025-12-22) Oscillator

- more work on oscillators
- first working version for sine, square, saw, triangle, supersaw wave form
- bad performance though

## (2025-12-21) First step in audio synthesis

- First steps to synthesize audio by implementing a simple oscillator.
- Still fighting with the GraalVM bridge to the strudel-js engine.

## (2025-12-20) Started

- Started the project.
- Using GraalVM to run strudel-js (@strudel/core) on the jvm for sound event generation
- Figured out how to run javascript on the jvm
