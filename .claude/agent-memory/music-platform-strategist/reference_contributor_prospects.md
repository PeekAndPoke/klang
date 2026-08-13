---
name: contributor-prospects
description: Living list of music-software projects and their makers (live coding, browser audio, music theory/notation, Kotlin audio) as potential Klang contributors — with outreach angle, activity status, and contact route
metadata:
  type: reference
---

# Contributor prospect list — music software makers

**Started 2026-08-13** (user sidequest: "in the search for contributors ... keep a list of other music software and
its makers ... so we might be able to contact them and ask them to join").

**This is a living list.** Extend it in future sessions. Every entry was link-verified at the date noted; handles and
activity decay, so re-verify before any actual outreach.

## Ground rules

- **NO OUTREACH YET.** List-keeping only. Contact timing sits behind the launch-phase gate (see
  [[project_disco_strategy]] — engine work-streams → tutorial quarter → launch). Approaching people before there is
  something worth joining wastes the one first impression each person gives you.
- **Licensing is a talking point, not a footnote.** Klang is AGPL v3. Most people on this list are FOSS-native so this
  reads as a plus, but anyone commercial (Splice, Audiotonix) will read AGPL as a blocker for their day job. Know which
  is which before writing.
- **Lineage before recruitment.** For anyone in the Tidal/Strudel family, the first contact is *credit and
  acknowledgment*, never a recruitment pitch — Klang's sprudel is downstream of their pattern language. Get that order
  wrong and the door closes permanently.
- **Ask for the smallest real thing.** "Would you look at this and tell me if we got your idea right" converts far
  better than "come contribute." Most of these people already maintain a project and have no spare capacity for a
  second one.

---

## Tier 1 — Closest neighbors (browser live coding, direct lineage)

### Klangmeister — Chris Ford
- **Maker:** Chris Ford — [github.com/ctford](https://github.com/ctford). ThoughtWorks, based in Barcelona.
- **Project:** [ctford/klangmeister](https://github.com/ctford/klangmeister) — "a musical scratchpad." Browser live
  coding: design synthesizers and compose in ClojureScript, no install. Embeds a ClojureScript compiler in the browser
  and wraps the Web Audio API in a purely functional layer. Also
  [ctford/leipzig](https://github.com/ctford/leipzig), the music theory/composition library it builds on.
- **Fit angle:** The nearest philosophical sibling Klang has. Same thesis — *make music theory legible through
  functional programming*, in a browser, with no install. He solved "embed a real language compiler in the browser"
  before we did, in a different language. The Leipzig library is exactly the music-theory layer Klang's wider platform
  vision needs. Also: he shares the German naming instinct (Klangmeister / Klangmotör).
- **Status:** Klangmeister appears low-activity — the flagship talks are from ~2016 (flatMap Oslo, FARM 2016 demo).
  **Verify current activity before contact.** A dormant project can mean "free capacity" or "moved on"; don't assume.
- **Contact route:** GitHub; conference-speaker profile; ThoughtWorks affiliation.

### Strudel — Felix Roos & Alex McLean
- **Makers:** Felix Roos — [github.com/felixroos](https://github.com/felixroos); Alex McLean (yaxu), also the creator of
  TidalCycles.
- **Project:** [strudel.cc](https://strudel.cc/) — web-based live coding of algorithmic patterns, a faithful port of
  TidalCycles to JavaScript. McLean ported the pattern representation from Haskell to JS in early 2022; Roos turned it
  into a working live coding system within weeks. **Development moved from GitHub to Codeberg
  ([codeberg.org/uzu/strudel](https://codeberg.org/uzu/strudel))** — note that, the GitHub repo is a redirect stub now.
- **Fit angle:** *The* most relevant group and the most delicate. Klang's sprudel is a Kotlin implementation of this
  pattern language; these are the people whose ideas the whole pattern layer rests on. First contact is credit,
  acknowledgment, and "here's what a strict, statically-typed, fixed-point-time implementation taught us" — the
  CycleTime work, the structural cycle-boundary bug class, the mini-notation attribute blocks. That's a genuine
  contribution *back*, and it's the only honest opening.
- **Status:** Very active, large community.
- **Contact route:** Codeberg; the TidalCycles/live-coding Discord and forums; Alex McLean via
  [algorithmicpattern.org](https://algorithmicpattern.org/).

### Mercury — Timo Hoogland
- **Maker:** Timo Hoogland — [github.com/tmhglnd](https://github.com/tmhglnd),
  [timohoogland.com](https://www.timohoogland.com/mercury-livecoding/). Live coder, music technologist, **educator**.
- **Project:** [mercury](https://github.com/tmhglnd/mercury) + [mercury-playground](https://github.com/tmhglnd/mercury-playground)
  (browser version) — a deliberately minimal, human-readable live coding language. Editor capped at 30 lines so all
  code stays visible. Also maintains [mercury-workshop](https://github.com/tmhglnd/mercury-workshop) and
  [live-coding-101](https://github.com/tmhglnd/live-coding-101).
- **Fit angle:** He has solved the *exact* problem Klang's tutorial quarter is about — how do you make a live coding
  language a beginner can actually read? The 30-line cap is a real design conviction, not a gimmick. His workshop
  repos are a model for the tutorial through-line. Strong fit for the education-facing half of the vision.
- **Status:** Active, multiple maintained repos, teaches workshops.
- **Contact route:** GitHub; personal site; live coding conference circuit (ICLC, Algorave).

### Estuary + Punctual — David Ogborn
- **Maker:** David Ogborn — [github.com/dktr0](https://github.com/dktr0), Professor at McMaster University.
- **Projects:** [estuary](https://github.com/dktr0/estuary) — zero-install, web-based **collaborative** platform for
  audiovisual live coding, hosting *multiple* live coding languages and supporting networked ensembles; live at
  [estuary.mcmaster.ca](https://estuary.mcmaster.ca). [Punctual](https://github.com/dktr0/Punctual) — browser-based
  audio-visual live coding language, now built as an Estuary "exolang."
- **Fit angle:** Two angles. (1) Estuary is explicitly a *platform for collaboration and learning* — the same
  positioning as Klang's wider vision, built with academic rigor and grant funding. (2) The **exolang** model is
  directly interesting: Estuary hosts other people's languages. If KlangScript could run inside Estuary, that's
  distribution without building a community from scratch. Also the strongest academic-credibility contact on this list.
- **Status:** Active, grant-funded (two SSHRC grants), institutionally backed.
- **Contact route:** GitHub; McMaster faculty page; ICLC (he is central to that conference community).

### Hydra — Olivia Jack
- **Maker:** Olivia Jack — [github.com/ojack](https://github.com/ojack).
- **Project:** [hydra-synth/hydra](https://github.com/hydra-synth/hydra) — live coding *visuals* in the browser, at
  [hydra.ojack.xyz](https://hydra.ojack.xyz). WebGL + WebRTC; each browser window is a node in a distributed video
  synthesizer; API modeled on analog modular synthesis.
- **Fit angle:** Not audio, and that is the point — Hydra is the default visual companion to browser live coding, and
  the analog-modular API metaphor rhymes hard with Motör's engine metaphor. Realistic ask is interop/pairing, not
  contribution. Also the single best-known example of a live coding tool with genuine mainstream reach.
- **Status:** Very active, huge community.
- **Contact route:** GitHub; hydra community Discord.

---

## Tier 2 — Language & engine builders (would understand Motör on sight)

### Sonic Pi — Sam Aaron
- **Maker:** Sam Aaron — [github.com/samaaron](https://github.com/samaaron),
  [sam.aaron.name](https://sam.aaron.name/). Live coder, educator, PhD, performing artist.
- **Project:** [sonic-pi-net/sonic-pi](https://github.com/sonic-pi-net/sonic-pi) — Ruby-based live coding environment,
  originally built at the Cambridge Computer Lab with the Raspberry Pi Foundation to teach computing *and* music in
  schools. Now also a performance-grade instrument. v5 shipped.
- **Fit angle:** The single most important reference point for the **school/child** half of Klang's age-span vision.
  He proved a live coding environment can go into classrooms at scale — that is the hardest, least-solved part of
  Klang's plan and he has a decade of hard-won evidence about what works with 11-year-olds. Even a conversation is
  worth more than a contributor. Note he is also the co-creator of Overtone (Clojure), so he overlaps with the
  ctford/Alda Clojure cluster.
- **Status:** Very active; Patreon-funded ([patreon.com/samaaron](https://www.patreon.com/samaaron)) — which also makes
  him a live case study for the Disco monetization ladder.
- **Contact route:** GitHub; Patreon; in-person at live coding events. High-profile, so expect a full inbox — the ask
  must be small and specific.

### Glicol — Qichao Lan
- **Maker:** Qichao Lan — [github.com/chaosprint](https://github.com/chaosprint).
- **Project:** [chaosprint/glicol](https://github.com/chaosprint/glicol) — "graph-oriented live coding language."
  Language *and* audio engine written in Rust, compiled to WebAssembly; runs in browsers, VST plugins, and on Bela.
  Sells on garbage-collection-free, memory-safe, sample-accurate real-time audio in the browser.
- **Fit angle:** He has already walked the road Klang has explicitly parked as far-future — the
  high-performance native/Wasm audio backend. Whenever that unparks, he is the person who knows where the bodies are
  buried (Wasm in the worklet, GC avoidance, sample-accurate scheduling). Also a language designer wrestling with the
  same "high-level sequencing on top of low-level synthesis" split that Motör's Ignitor/Katalyst layering addresses.
- **Status:** Active.
- **Contact route:** GitHub; he publishes academic papers, so also findable via research channels.

### Elementary Audio — Nick Thompson
- **Maker:** Nick Thompson — [github.com/nick-thompson](https://github.com/nick-thompson),
  [nickwritesablog.com](https://www.nickwritesablog.com/). Engineer at Splice; previously Syng, Facebook.
- **Project:** [Elementary](https://github.com/nick-thompson/elementary) — JavaScript runtime and framework for audio
  DSP, React-inspired and declarative; targets web, desktop, mobile, embedded Linux, and native plugin formats.
- **Fit angle:** The closest thing to a peer for the *DSP-authoring-surface* problem — his declarative framing of
  signal graphs is a direct intellectual neighbor to the Pipeline/Ignitor/Katalyst DSLs. His writing on
  functional/declarative audio is unusually good and worth reading before the Katalyst DSL is finalized, contributor
  or not.
- **Status:** Active. **Caveat: employed at Splice (commercial).** AGPL and a day job at a music-tech company make
  code contribution unlikely — treat as an advisor/reviewer prospect, not a committer.
- **Contact route:** GitHub; blog; he has done developer podcasts, so he is approachable publicly.

### Alda — Dave Yarwood
- **Maker:** Dave Yarwood — [github.com/daveyarwood](https://github.com/daveyarwood),
  [blog.djy.io](https://blog.djy.io/). Software engineer and trained multi-instrumentalist.
- **Project:** [alda-lang/alda](https://github.com/alda-lang/alda) — text-based music composition language; compose and
  play back from a text editor and the command line. Explicit design goal: *"for musicians who don't know how to
  program, and programmers who don't know how to music."*
- **Fit angle:** That design goal is almost word-for-word Klang's own audience thesis. He has been at it since 2012 and
  has opinions about notation-as-text ergonomics — directly relevant to mini-notation and the eventual
  score/instrument surfaces. Trained musician *and* engineer, which is the rare combination Klang needs.
- **Status:** Long-running; verify current cadence.
- **Contact route:** GitHub; blog.

---

## Tier 3 — Music theory, notation, education surface

*(These matter for the non-coder half of the platform vision — instruments, theory, tuning — which is currently the
thinnest part of the roadmap.)*

### Tonal.js — Daniel Gómez (danigb)
- **Maker:** [github.com/danigb](https://github.com/danigb); org at [tonaljs/tonal](https://github.com/tonaljs/tonal).
- **Project:** TypeScript music theory library — notes, intervals, chords, scales, modes, keys. Purely functional, no
  mutation, no sound: abstractions only.
- **Fit angle:** The theory layer Klang's "learn intervals, chords, scales" pillar needs, already thought through by
  someone who chose the same functional purity Klang's DSL favors. Not a port target (Klang is Kotlin) but the *domain
  modeling* is the valuable part, and a person who has modeled this well is rare.
- **Status:** Mature, widely used.

### VexFlow — Mohit Muthanna Cheppudira
- **Maker:** [github.com/0xfe](https://github.com/0xfe); project now at
  [vexflow/vexflow](https://github.com/vexflow/vexflow).
- **Project:** TypeScript library for rendering music notation and **guitar tablature**, to Canvas and SVG; created
  2010. Also VexTab, a text language for notation/tab.
- **Fit angle:** If Klang ever shows real notation or guitar tab — and the vision names guitar, recorder, piano — this
  is the reference implementation and its author has 15 years of engraving edge cases in his head. VexTab is also a
  precedent for text→notation, adjacent to mini-notation.
- **Status:** Mature; now community-maintained under an org, which usually means the original author has stepped back.
  Verify.

---

## Tier 4 — Kotlin / KMP audio (the ones who could actually commit code)

*(Highest practical contribution probability — same language, same platform targets, smallest onboarding cost.)*

### ktmidi — Atsushi Eno
- **Maker:** Atsushi Eno — [github.com/atsushieno](https://github.com/atsushieno).
- **Project:** [ktmidi](https://github.com/atsushieno/ktmidi) — Kotlin Multiplatform library for MIDI access and data
  processing, covering MIDI 1.0, MIDI 2.0 (UMP), MIDI-CI, SMF and SMF2. Builds for **Kotlin/JVM, Kotlin/JS and
  Kotlin/Native** — the same target matrix as Klang. Related: kmmk (virtual MIDI keyboard), kmdsp (MIDI player), plus
  Android audio plugin framework work.
- **Fit angle:** **Probably the strongest pure-contributor fit on this whole list.** Same language, same multiplatform
  targets, deep audio/MIDI domain expertise, long track record of maintaining KMP audio libraries in public. If Klang
  ever wants MIDI in (keyboard input, MIDI→mini-notation recording, which is already a parked Phase 3 tutorial item)
  or MIDI out, ktmidi is the obvious dependency and he is the obvious person to ask.
- **Status:** Active, prolific, publishes to Maven Central under `dev.atsushieno`.
- **Contact route:** GitHub issues/discussions; personal blog at atsushieno.github.io.

### pandaloop — lemcoder
- **Maker:** [github.com/lemcoder](https://github.com/lemcoder).
- **Project:** [pandaloop](https://github.com/lemcoder/pandaloop) — Kotlin Multiplatform audio (looping) library with a
  DSP module. Also KMP FluidSynth wrapper work (SF2/MIDI software synth across Android/iOS/macOS/Linux/Windows/JVM).
- **Fit angle:** Small project, same stack, same problem space. The FluidSynth/SF2 angle touches Klang's existing
  soundfont support (and its open soundfont-looping bug). Lower profile means genuinely reachable — a small
  maintainer is far likelier to say yes than a famous one.
- **Status:** Verify activity — small projects move fast or die fast.

### MWEngine — igorski
- **Maker:** [github.com/igorski](https://github.com/igorski).
- **Project:** [MWEngine](https://github.com/igorski/MWEngine) — low-latency audio engine and DSP library for Android
  in C++, exposing a Java/Kotlin API; sample playback plus built-in synthesis and processing; OpenSL and AAudio.
- **Fit angle:** Android/mobile is entirely absent from Klang's current surface, and mobile is where the
  child/teen/casual audiences actually are. Someone who has shipped low-latency Android audio knows the exact pain
  ahead. Relevant *later* — flag rather than pursue.
- **Status:** Long-running.

---

## To research and add in future sessions

Named from general knowledge, **not yet verified** — confirm handles and links before treating as entries:

- **TidalCycles** — Alex McLean (yaxu) as a first-class entry in his own right, separate from Strudel.
- **FoxDot** — Ryan Kirkbride (Python live coding, education-oriented).
- **Gibber** — Charlie Roberts (browser live coding + live coding education research).
- **Extempore** — Andrew Sorensen (cyberphysical/real-time systems live coding).
- **SuperCollider** community maintainers — the substrate under Tidal/Sonic Pi/FoxDot.
- **ORCA / Hundred Rabbits** — Devine Lu Linvega (esoteric sequencer, extraordinary design discipline).
- **Csound / web-Csound** — Steven Yi (kunstmusik).
- **ChucK** — Ge Wang, Stanford CCRMA (also the strongest academic music-education-tech contact anywhere).
- **Faust** — GRAME (Yann Orlarey, Stéphane Letz) — DSP language with web targets.
- **VCV Rack** — Andrew Belt; **BespokeSynth** — Ryan Challinor. Modular/engine-metaphor kinship with Motör.
- **abcjs** — Paul Rosen; **OpenSheetMusicDisplay** — notation/education surface.
- **musictheory.net** — Ricci Adams — the reference for browser music-theory *teaching* UX.
- **Web Audio Modules (WAM)** — Michel Buffa — plugin standard for the browser.
- **Tuner / ear-training apps** — nothing identified yet; this is a genuine blank spot and it maps onto one of the
  platform's named pillars. Worth a dedicated research pass.

## Open strategic questions attached to this list

1. **What is the actual ask?** Klang is a large, opinionated, AGPL Kotlin codebase with a strong existing design
   voice. Realistically, most people here are *advisors, reviewers, and interop partners*, not committers. Being
   honest about that up front will get better responses than a generic "join us."
2. **What does Klang give back?** The engine work (fixed-point CycleTime, the fundamental-lottery fix, the limiter
   min-hold construction, parameter parity) is publishable and useful to every project on this list. The blog is
   already producing exactly this material — it may be the real outreach vehicle, and it is warm rather than cold.
3. **Tier 4 is underweighted in the vision.** The Kotlin/KMP audio people are the only group that could contribute
   code cheaply, and there are very few of them. Worth its own research pass.
