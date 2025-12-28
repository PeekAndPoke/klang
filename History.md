# Project history

## AI cost

- 300 EUR / year for JetBrains AI Ultimate
- 23-12-2025: bought 10 more AI credits -> 11.90 EUR
- 26-12-2025: bought 50 more AI credits -> 59.90 EUR

## Diary

### (2025-12-28) Strudel / Sound engine

- added crush effect
- added coarse effect

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
