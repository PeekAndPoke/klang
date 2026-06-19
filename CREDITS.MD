# Credits

## Shout out to Strudel

**Klang exists because of Strudel.** What started as pure curiosity — *"how does this work?"* —
turned into a deep dive into the world of audio engines, pattern languages, and music synthesis.

Strudel's open-source code, its open sample libraries, and the excellent documentation at
[strudel.cc](https://strudel.cc) were invaluable in understanding the machinery behind it all.

What started as a copycat of Strudel is now evolving into its sibling — **Sprudel**.
Sharing the same roots and many of the same ideas, Sprudel takes the pattern language
in new directions as part of Klang.

And Strudel itself descends from **Tidal Cycles**, the project that started it all — see below.

A heartfelt thank you to the Strudel community for making all of this openly available.

- [https://strudel.cc](https://strudel.cc)
- [Strudel on Codeberg](https://codeberg.org/uzu/strudel)

## Tidal Cycles — The Roots

**Before Strudel, there was Tidal Cycles.** Strudel is a JavaScript reimagining of
**[Tidal Cycles](https://tidalcycles.org)** — the Haskell live coding environment created
by **Alex McLean** that pioneered the cyclic, pattern-based language at the heart of all of
this. The way patterns describe music in Strudel, and now in Sprudel, traces directly back
to Tidal.

Lineage: **Tidal Cycles → Strudel → Sprudel**.

- [tidalcycles.org](https://tidalcycles.org)
- [Tidal on GitHub](https://github.com/tidalcycles/Tidal)

## Filters & DSP

Klang's filters stand on the shoulders of the virtual-analog filter community:

- **"The Art of VA Filter Design" by Vadim Zavalishin** — the zero-delay-feedback (TPT)
  state-variable filter that powers Klang's lowpass, highpass, bandpass, and notch filters
- **[Cytomic](https://cytomic.com/technical-papers/) (Andrew Simper)** — the canonical
  trapezoidal-integrator SVF formulation that Klang's implementation follows
- **[Obxd](https://github.com/2DaT/Obxd) by Vadim Filatov (2DaT)** — inspiration for the
  analog-style "diode-pair" resonance saturation that gives the filters their warmth when
  driven (`analog > 0`). Klang's SVF topology and the way the nonlinearity is folded in are
  its own; the *idea* of steering resonance damping from a diode-pair model comes from here.

## DSP Algorithms & Techniques

Beyond the filters, Klang's voices and effects build on well-known DSP algorithms:

- **Freeverb** by Jezar at Dreampoint — the reverb (Schroeder/Moorer architecture, public domain)
- **Karplus–Strong** (Kevin Karplus & Alexander Strong) — plucked-string synthesis
- **PolyBLEP** (Välimäki et al.) — band-limited oscillator anti-aliasing
- **Super-saw** after Adam Szabo's *"How to Emulate the Super Saw"* (Roland JP-8000)
- **Chebyshev-polynomial waveshaping** — harmonic distortion
- **Perlin noise** (Ken Perlin) — organic drift and noise textures
- **Euclidean rhythms** via the Bjorklund algorithm — rhythm generation

## Music & Audio

Making music possible:

- **[Tonal.js](https://github.com/tonaljs/tonal)** — music theory library (ported to Kotlin in the :tones module)
- **[SoundFont2](https://www.npmjs.com/package/soundfont2)** — SoundFont parsing
- **[JLayer](http://www.javazoom.net/javalayer/javalayer.html)** — MP3 decoding on the JVM

## Samples & Soundfonts

Klang ships with a default catalogue of openly available samples and soundfonts.
Huge thanks to everyone who created, curated, and hosts them:

- **[TidalCycles Dirt-Samples](https://github.com/tidalcycles/Dirt-Samples)** — the classic live-coding sample
  collection
- **[uzu-drumkit](https://github.com/tidalcycles/uzu-drumkit)** — the default drum kit
- **Tidal Drum Machines** — emulation samples of classic drum machines
- **[Versilian Community Sample Library (VCSL)](https://github.com/sgossner/VCSL)** by Versilian Studios — CC0
  acoustic-instrument samples
- **[dough-samples](https://github.com/felixroos/dough-samples)** by Felix Roos — hosting and curation of the bundled
  sample sets (Dirt-Samples, VCSL, mridangam, piano, drum machines)
- **[todepond/samples](https://github.com/todepond/samples)** by Lu Wilson — drum-machine sample aliases
- **[WebAudioFont](https://github.com/surikov/webaudiofont)** by Sergey Surikov — source of the bundled General MIDI
  soundfont

## The Modern Browser

None of this would be possible without the incredible platform that modern browsers have become.
The performance and the richness of APIs available today are nothing short of amazing:

- **[Web Audio API](https://developer.mozilla.org/en-US/docs/Web/API/Web_Audio_API)** — real-time audio synthesis and
  processing
- **[AudioWorklet](https://developer.mozilla.org/en-US/docs/Web/API/AudioWorklet)** — high-performance audio processing
  on a dedicated thread
- **[Canvas API](https://developer.mozilla.org/en-US/docs/Web/API/Canvas_API)** — rendering oscilloscopes, waveforms,
  and visualizations
- **[Web Workers](https://developer.mozilla.org/en-US/docs/Web/API/Web_Workers_API)** — parallel processing without
  blocking the UI

## Graphics & Rendering

Visuals and editor effects are powered by these libraries (integrated via Kraft addons):

- **[PixiJS](https://pixijs.com)** — WebGL-accelerated rendering of live playback highlights in the editor
- **[Three.js](https://threejs.org)** — 3D graphics for the animated Motör background and visualizations

## The Editor

The live coding experience is powered by:

- **[CodeMirror 6](https://codemirror.net)** — the code editor at the heart of the coding experience
- **[Lezer](https://lezer.codemirror.net)** — parser generator powering syntax highlighting

## The Foundation

Klang is built on the shoulders of these giants:

- **[Kotlin](https://kotlinlang.org)** & **[Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)** —
  the language and multiplatform foundation that makes it possible to share code across platforms
- **[KotlinX Coroutines](https://github.com/Kotlin/kotlinx.coroutines)** — async programming made elegant
- **[KotlinX Serialization](https://github.com/Kotlin/kotlinx.serialization)** — data serialization
- **[Ktor](https://ktor.io)** — HTTP framework powering the backend
- **[Gradle](https://gradle.org)** — build automation
- **[Google KSP](https://github.com/google/ksp)** — Kotlin Symbol Processing for code generation
- **[Clikt](https://ajalt.github.io/clikt/)** — command-line interface framework for the CLI tools

## The UI

Looking good thanks to:

- **[Semantic UI](https://semantic-ui.com)** / **[Fomantic UI](https://fomantic-ui.com)** — the UI framework behind the
  interface
- **[CommonMark](https://github.com/commonmark/commonmark-java)** / **[Marked](https://marked.js.org)** — markdown
  rendering (JVM / JS)

## Kraft & Ultra

Klang's frontend and shared building blocks are built on **Kraft** and **Ultra** —
PeekAndPoke's own open-source Kotlin frameworks:

- **[Kraft](https://github.com/PeekAndPoke)** — the reactive UI framework (VDom, components, forms, routing)
  the entire interface is built with
- **[Ultra](https://github.com/PeekAndPoke)** — the foundational Kotlin libraries (html DSL, streams,
  common utilities, dependency injection, and more)

## The Runtime

Bridging worlds:

- **[GraalVM](https://www.graalvm.org)** — polyglot runtime used in early development
  to feed the audio engine with strudel events produced by the original JavaScript implementation

## Testing

Keeping things working:

- **[Kotest](https://kotest.io)** — Kotlin testing framework

## AI Assistance

A significant amount of Klang's code was written with the help of AI.
**[Claude](https://claude.ai)** by **[Anthropic](https://anthropic.com)** helped achieve
a lot of things in a very short time, making it possible for a small project to move fast
and punch above its weight. Working with Claude has been an overall delightful experience.
