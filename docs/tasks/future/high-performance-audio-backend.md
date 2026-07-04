# High-performance audio backend (native / Wasm) — closing the gap to native DAWs

Status: **far-future / ideation — explicitly NOT scheduled.** Created 2026-07-04. Deliberately parked behind
the current priority order (see below). This is a conceptual capture of a design conversation, written down
while the context was fresh so it isn't re-derived from scratch. Self-contained; resumable cold. Nothing
here should be started until *after* launch.

> **Priority gate (do not skip).** The user's ordering is: **1) finish the open engine work-streams →
> 2) the quarter of tutorials → 3) bring everything online (launch) → 4) *then* hard performance.**
     > "Sound first." Hard performance is late-game. The higher-leverage near-term lever is reducing *absolute*
     > work (orbit-level body/vowel: done; voice culling: next) — that helps every backend, native included.
     > See memory notes `project_sound_first`, `project_perf_native_backend`, `project_song_cpu_benchmark`.

## One-line goal

Give Klang a path to native-DAW-class DSP performance (order-of-magnitude headroom over the browser
AudioWorklet) **without** throwing away the web-first product — by moving the DSP engine to a native or
WebAssembly core behind Klang's existing `KlangCommLink` command protocol.

## Why native DAWs (e.g. Ableton) feel ~10× faster

Not magic — a stack of factors that **multiply**. Rough magnitudes for a heavy DSP graph:

| Factor                                                                             | Realistic win                     | Native-only, or reachable in-browser?                                           |
|------------------------------------------------------------------------------------|-----------------------------------|---------------------------------------------------------------------------------|
| **Multi-core** (Klang = one worklet thread; DAWs use all cores)                    | 4–8×                              | In-browser possible (Workers + SharedArrayBuffer), but genuinely hard           |
| **SIMD** (AVX2 = 8×f32/instr; recursive IIR filters don't vectorise)               | 2–4× aggregate                    | WASM SIMD = 128-bit (4×f32); native AVX2/512 wider                              |
| **Native codegen vs V8** (no boxing, no deopt, full inlining)                      | 2.5–3×                            | WASM recovers most of this                                                      |
| **f32 instead of Klang's all-`Double`** (½ memory bw, 2× SIMD lanes)               | 1.5–2×                            | Only pays off off-JS — f32 in JS = the `Math.fround` tax (why Klang chose f64)  |
| **Denormal flush via CPU flag** (FTZ/DAZ set once vs per-sample `flushDenormal()`) | 1.1–1.5× on filter-heavy code     | **Native-only** — neither JS nor WASM can portably set the FPU control register |
| **No GC / cache-friendly layout**                                                  | 1.2–2× (mostly worst-case/jitter) | Partly reachable                                                                |

3× (codegen) × 2× (SIMD+f32) × 2× (a couple cores) ≈ 12× before you even reach 8 cores. **Biggest levers by
far: multi-core and SIMD** — and neither is fundamentally "native vs browser," they're engine-architecture.

## The architecture idea (the good one)

A **native audio daemon** running as a drop-in local process on the same machine. The browser UI sends
**control EVENTS over WebSocket** — note-ons, param changes, `KlangCommLink.Cmd` messages — **not audio**.
The daemon does all DSP and hands PCM straight to the OS audio layer (CoreAudio / WASAPI / ALSA). Audio never
returns to the browser.

**Why this works (the key insight):**

- Events are **musical-rate**, not audio-rate — hundreds/sec at most, kB/s, versus 48 000 samples/sec of an
  audio stream. So the transport's bandwidth/latency needs are trivial by comparison.
- Klang already **schedules ahead**: `ScheduledVoice.startTime` timestamps every voice, and both the worklet
  and the offline renderer play events *at their timestamp*, not on arrival. With a lookahead window
  (send events ~20–50 ms before play time), **WebSocket jitter is fully absorbed → sample-accurate
  playback.** This is exactly how network MIDI / OSC / Ableton Link work.
- TCP reliability is a **feature** for control events (never drop a note-on) — the opposite of streaming audio,
  where TCP head-of-line blocking would be fatal.

**Common misconception to avoid:** you cannot "replace the worklet with a WebSocket" for *output* — the browser
can only make sound via Web Audio. Streaming rendered audio *back* over WS would just add a jittery network hop
in front of a worklet. The daemon idea is different and sound precisely because it keeps audio out of the wire
and hands it to the OS directly.

**Why it fits Klang's existing architecture (almost suspiciously well):** the frontend already speaks an
abstract `KlangCommLink` Cmd/Feedback protocol to *a* backend — today the AudioWorklet, via the KSP trust-codec
(`audio_be/.../WorkletContract.kt`, `:audio-wire-codec-ksp`). A native daemon is just a **second transport
behind the same contract**. So you can keep both and select at runtime:

- **no daemon** → fall back to the WASM/JS worklet (zero-install, works everywhere),
- **daemon present** → route events to native (full perf, for power users).

→ **web-first, native-optional, graceful degradation.** The per-playback dispatcher + Cmd protocol are the seam
it plugs into.

## Wire security — a cost SPECIFIC to the open-wire daemon

The current `@WireFormat` KSP codec is a **trust codec**: fast (67 µs → 385 ns/op) because it's a compact binary
layout with direct field reads **and** cuts corners (unchecked lengths, trusts the buffer). That's safe only
because it's in-process/closed. A WebSocket is an **open boundary** → it needs a **hardened, validating**
decoder.

But this is smaller than it looks:

- The speed came from the **layout**, not from skipping checks. Adding bounds/length/tag/size validation is a
  few comparisons per field (~385 ns → ~1 µs) — still far faster than JSON, and the WS/OS cost dwarfs it
  anyway. **The "cut corners for speed" rationale evaporates once there's a socket in front of it.**
- **Threat model** = a malicious web page opening `ws://localhost:PORT` (+ any local process), not the whole
  network. Standard, well-trodden mitigations: bind `127.0.0.1`; require an **auth-token/password handshake**
  the legit UI holds (mint a random token on launch / a local pairing step — no token, no session); check the
  `Origin` header; **validate + size-cap every frame**, never crash on garbage. A **memory-safe daemon
  language** (Rust, or Zig with disciplined bounds-checks) is a genuine plus on an untrusted boundary.
- Clean design = **two trust domains, two decoders**: keep the trust-codec for the in-process/worklet path
  (unchanged, fast); add a validating decoder for the wire. Share the binary **format**, differ only in the
  decode path. Since the codec is already generated from `@WireFormat` annotations, emitting **both** a trust-
  and a checked-variant from the same schema is a natural extension of the KSP generator.

**Kicker:** this cost is unique to the open-wire daemon. Tauri / in-process native (and Zig→Wasm-in-worklet)
have **no untrusted boundary** — same trust domain, IPC/FFI or shared memory — so they keep the fast trust-codec
untouched. This is a real line-item *against* the daemon and *toward* the in-bundle approaches.

## Three backend paths (same idea, different ceilings)

| Path                                                                                       | Perf ceiling                                   | Install          | Untrusted wire?                       | Notes                                                                                                          |
|--------------------------------------------------------------------------------------------|------------------------------------------------|------------------|---------------------------------------|----------------------------------------------------------------------------------------------------------------|
| **A. Engine → WASM in the worklet** (Zig/Rust/Kotlin-Wasm)                                 | ~2–4× over Kotlin/JS                           | zero (stays web) | no                                    | Capped by WASM 128-bit SIMD + no FTZ; no multi-core. Same target as the earlier Kotlin/Wasm feasibility spike. |
| **B. Native local daemon** (events over WS, OS audio out)                                  | ~10× (multicore + AVX + FTZ + f32 + direct HW) | daemon           | **yes** (needs hardened codec + auth) | Web UI stays a pure hosted website; native is an optional local accelerator.                                   |
| **C. Native desktop app** (Tauri/Electron: web UI + native core in ONE bundle, IPC not WS) | ~10× (same as B)                               | one app          | no                                    | Cleaner than B (no separate daemon, no socket, no untrusted wire) **if** a desktop app is acceptable.          |

B's *unique* advantage over C is keeping the UI a pure hosted website (share-by-URL, instant load) with native
as optional. Otherwise C is the cleaner variant of the same native win.

## Honest costs / challenges (all paths that reimplement the engine)

1. **Reimplement all of `audio_be` in the native/Wasm language** — oscillators, filters, effects, cylinders, the
   voice pipeline. The big one. Silver lining: the golden tests + the song benchmark (`runSongBenchmark`,
   `SongBenchmark*.kt`) become a **cross-engine conformance harness** (same events on Kotlin vs native, compare
   output). Keep Kotlin `audio_be` as the offline/reference/test engine; native becomes canonical for realtime.
2. **(B only) Ship + auto-update a daemon**, plus the localhost-WS security work above (`https` page →
   `ws://localhost` needs `wss` + a local cert or the browser's localhost exception).
3. **Clock sync + lookahead** — sync the FE musical clock to the native audio playhead (Klang already does a
   version with `BackendClockSync`; see also `project_worklet_clock_divergence`), and pick a lookahead: small
   for live-tweaked params, larger for scheduled notes. That liveness-vs-jitter-immunity knob is the one real
   design tradeoff, and it's well-understood.
4. **Oscilloscope / visual feedback** now needs analyzer data back over WS at ~60 fps (low rate, easy) — or a
   lightweight FE-side monitor tap.
5. **Two engines to keep behaviorally in sync** (mitigated by making native canonical + Kotlin the reference).

## Language notes

- **Zig**: excellent WASM support (`wasm32-freestanding` to embed in a worklet — export `render(ptr, frames)` +
  shared linear memory, `@Vector` + `simd128`; `wasm32-wasi` for CLI/server). No GC, manual allocators, tiny
  output — ideal for real-time DSP, and its "you manage everything, no hidden control flow" model matches the
  discipline Klang already enforces in Kotlin (no boxed types, block-based, no hot-path alloc). Gentler on-ramp
  than Rust for a C/C++ background — no borrow-checker fight (which is genuinely annoying for shared-mutable
  audio *graphs*). Caveat: pre-1.0, fast-moving std + `build.zig`. (Andrew Kelley started Zig to build a DAW.)
- **Rust**: also WASM-capable, bigger/more-stable ecosystem; borrow checker adds friction for DSP graphs.
- **Zig cannot emit JVM bytecode.** Zig→JVM only via (1) native lib + Java 22 Panama FFM API (fast, separate
  artifact) or (2) Zig→WASM run in-JVM via Chicory / GraalWasm. Notably: **one Zig(/Rust)→WASM module is the
  single artifact that could run in BOTH the browser worklet and the JVM** (via a JVM WASM runtime), replacing
  `audio_be` on both KMP targets — trading some JVM speed for one engine instead of two.

## De-risk plan (when eventually pursued)

Do **not** port the whole engine first. Thin spike:

1. Reuse the `KlangCommLink.Cmd` protocol; implement just a couple of voices + the master chain in the target
   (native or Wasm).
2. Wire up the transport (WS + lookahead scheduling for B; worklet+wasm glue for A) and FE↔engine clock sync.
3. Measure the real **round-trip latency** and **CPU on a heavy song** (e.g. the frozen Der Schmetterling /
   Seltsamere Dinge from the benchmark) end-to-end.
4. Only if the latency feels live and the CPU headroom is real → commit to porting the full engine.

## Key files / anchors (for whoever picks this up)

- `audio_bridge/.../infra/KlangCommLink.kt` — Cmd/Feedback protocol (the backend contract).
- `audio_be/.../WorkletContract.kt` + `:audio-wire-codec-ksp` (`@WireFormat`) — the current trust-codec.
- `audio_bridge/.../ScheduledVoice.kt` (`startTime`) — the lookahead-scheduling substrate.
- `audio_be/` — the engine to port; `KlangAudioRenderer` / `PlaybackEngine` / `Cylinders` / `voices/`.
- `SongBenchmark*.kt` / `FrozenSongs.kt` — the cross-engine conformance + perf harness.
- Memory: `project_perf_native_backend`, `project_sound_first`, `project_worklet_clock_divergence`,
  `project_song_cpu_benchmark`.
