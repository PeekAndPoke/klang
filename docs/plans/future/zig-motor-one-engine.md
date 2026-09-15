# The Zig Motor: one engine, every host

> **Status (2026-09-15): PLAN, far future, not started.** Sits behind the priority gate of
> `docs/tasks/future/high-performance-audio-backend.md` (finish engine work-streams, tutorials,
> launch, then hard performance). This plan supersedes that note's "keep Kotlin as the reference
> engine" idea: the maintainer's decision is **one backend implementation**. Once the Zig engine
> reaches parity, `audio_be/commonMain` is deleted. The frozen golden corpus takes over the
> reference role.
>
> Captured from a design conversation on 2026-09-15 so it is not re-derived cold. Self-contained.

## The one-sentence goal

**Write the Klang engine once, in Zig, so that the same source runs as Wasm in the browser worklet,
as Wasm or a native library on the JVM, and as a native daemon fed over WebSocket, with no second
engine implementation anywhere.**

## Where the seam is

Every live host of the Kotlin engine today talks to the same small surface. The worklet
(`audio_jsworklet/.../KlangAudioWorklet.kt`), the JVM backend (`audio_be/src/jvmMain/.../JvmAudioBackend.kt`),
the warmup runner and the song benchmark all do exactly this:

```
create(sampleRate, blockFrames)
setBackendStartTime(sec)
handle(cmd)                        // one decoded KlangCommLink.Cmd
renderBlock(cursorFrame, out)      // 128 stereo frames
poll feedback                      // Diagnostics, BackendReady, SampleReceived, ...
resetPostChain()
```

That surface **is** `PlaybackEngineDispatcher`, and it is the drop-in contract. The Zig core exports
precisely these functions. Everything below the dispatcher moves to Zig: routing by playback id,
engine lifecycle and draining, the warehouse, warmup with its `BackendReady`, diagnostics, the sample
store, the voice pipeline, cylinders, katalysts, master, all DSP. That is all of `audio_be/commonMain`
(107 files, about 22k lines as of 2026-09-15).

What stays Kotlin: `audio_bridge` (DSL values, `ScheduledVoice`, the ring buffer), `audio_fe`
(sample index and decoding), the hosts, the analyzer (browser `AnalyserNode` on the worklet output,
untouched), and everything on the frontend side of the wire (`BackendClockSync`, lookahead
scheduling, `ScheduledVoice.startTime`).

**The core never reads a clock or a transport itself.** Cursor frame and wall time are passed in by
the host, exactly as `renderBlock(cursorFrame, out)` and `performanceTimeMs` work today. This keeps
the core testable from `zig build test` and from a JVM Wasm runtime with a fake clock, and keeps the
worklet and the daemon on identical core code.

## Hosts

Every host needs the same four things and nothing else:

| Need         | Worklet                          | JVM                                  | Daemon (native)                    |
|--------------|----------------------------------|--------------------------------------|------------------------------------|
| Bytes in     | `postMessage` ArrayBuffer        | byte buffer into the runtime         | WebSocket frames                   |
| Audio out    | planar f32 to `outputs[0]`       | `SourceDataLine`                     | OS audio callback                  |
| Wall clock   | `Date.now()`                     | `System.nanoTime()`                  | OS clock                           |
| Feedback out | drain ring, `postMessage` back   | drain ring into `KlangCommLink`      | drain ring, WebSocket frame back   |

- **Worklet host** shrinks to instantiating the module (bytes passed through `processorOptions`,
  since worklets cannot fetch), calling `render` per quantum, and copying planar L and R out of
  linear memory. The current ShortArray interleave round trip disappears.
- **JVM host** loads the same `.wasm` through Chicory (pure Java, has an ahead-of-time compiler to
  bytecode, no native dependency). If offline render speed matters, the native library via the
  Panama FFM API is the same Zig source and the same C ABI, just a second loader. Still one engine.
- **Daemon** is a fourth host written in Zig, linking the core in-process. It adds a WebSocket
  server, the auth handshake and an OS audio layer. It adds **no protocol**: the frontend uses the
  same generated encoder whether the bytes go to a worklet port or a socket. Frontend-side clock
  sync and lookahead are unchanged; the daemon just sees events a little earlier than the worklet.
- The Kotlin class `PlaybackEngineDispatcher` survives only as a forwarding shim for the Kotlin
  hosts, so they compile unchanged. It goes when they go.

**Prerequisite refactor, in Kotlin, before any Zig:** `klang/.../KlangOfflineRenderer.kt` bypasses
the dispatcher and pokes registries and the scheduler directly. Move it onto `Cmd` like every other
host (register, upload, schedule). This removes the second engine API surface and is worth doing on
its own.

## Repository layout

- `motor/` as a top-level Zig package: `build.zig`, `src/`, tests, its own `MEMORY.md`.
- Two build targets from one source: `wasm32-freestanding` and a native shared library.
- **Toolchain wiring, simplest first:** commit the built `motor.wasm` (it is small), rebuild with a
  script, let CI verify it is up to date. Pulling a Zig toolchain into Gradle is unusual Gradle wiring
  under the complexity stone rule; consult before doing it, and only if the committed artifact
  becomes a real nuisance.
- No allocator after init. The engine grows linear memory once to a configured budget and runs
  fixed-buffer allocators. The existing resource warehouse with its shelves and `AllocationFailed`
  semantics maps onto that almost one to one.

## The wire

### Byte layout, generated from the Kotlin schema

Today the KSP codec (`:audio-wire-codec-ksp`) emits JS objects for structured clone. Wasm needs bytes
in linear memory, and the daemon needs bytes on a socket, so the wire becomes a compact binary
layout. The `@WireFormat` Kotlin classes stay the schema of record, since sprudel builds them. The
generator gets a second output: a Kotlin encoder to a byte buffer, and the Zig struct definitions
plus decoder, both stamped with the same schema hash.

This is build-time magic (cross-language generated sources) and the stone rule says consult. The
alternative is hand-written Zig structs plus a hand-written Kotlin binary encoder guarded by a
schema-hash test. With 83 `VoiceData` fields and a 2k-line `IgnitorDsl` tree, the recommendation is
the generator: schema drift between two languages is the main maintenance risk once there is one
engine.

### One decoder, always validating

The parked note planned a trust codec in-process and a checked one for the socket. In Zig, drop the
split. Bounds and tag checks on a compact layout are a few comparisons per field, and `ReleaseSafe`
gives most of them anyway. One decoder, no trust domain to reason about.

Length-prefixed fields are the right layout and are only dangerous when the decoder trusts the
length. The safety is structural, not per-field vigilance:

- **The decoder works on a bounded slice.** The transport gives the frame's total size (ArrayBuffer
  length, WebSocket frame length). Every read goes through one helper, `take(n)`, returning
  `error.Truncated` if `pos + n > frame.len`. The generator emits the call for every field; nobody
  hand-writes a read.
- **Lengths never drive allocation.** Anything variable decodes into fixed-capacity storage with
  `error.TooLong` past the cap.
- **Counts and depth get caps too.** Voice lists, the slot map, and above all the recursive
  `IgnitorDsl` tree (a nesting depth limit, because deep recursion on hostile input is a stack
  overflow instead of a buffer overrun).
- **The outer frame is capped by the transport.** The WebSocket server refuses frames above a fixed
  size before decoding starts.
- **A decode error drops the frame, never the engine.** Log, count it in diagnostics, keep rendering,
  the same policy as `SampleStore.AllocationFailed` today.

### No strings cross into the engine

Decision 2026-09-15: every name is mapped to an int id on the frontend. The engine keeps dense
tables with a fixed capacity per kind, bounds-checks every lookup, and refuses registration past the
cap. Ids are table indices, not trust. The only variable-size payload left is PCM.

| String on the wire today                                    | Replacement                                                                                          |
|-------------------------------------------------------------|------------------------------------------------------------------------------------------------------|
| `playbackId`                                                | small int assigned by the frontend at playback creation                                              |
| ignitor, katalyst, master names in `Register*` and the voice | sequential int per session; the frontend keeps `uniqueId -> id`; built-ins are pre-registered entries |
| `oscParams` keys                                            | slot index within the registered DSL tree, numbered at registration; `oscParams` becomes `[slot, value]` pairs |
| `bank`, `sound`, `soundIndex`, `note` on the voice           | one `sampleId` per resolved recording                                                                |
| `note`, `scale` on `VoiceData`                              | audit whether the engine reads them at all; they look like frontend leftovers                        |

**Pitched samples are the easy case.** The sound provider in `audio_fe` already picks the recording
for a note, and the upload carries that recording's own `pitchHz`; the engine only computes a
playback rate from voice frequency and that pitch. So the engine never needed the note, only "which
recording". Today `SampleStore` is keyed by the full request tuple including the note string, so two
neighbouring notes that resolve to the same recording upload and hold it twice. An id per resolved
recording fixes that as a side effect: many notes, one id, one PCM. It also closes the "unique by
convention" hole the `SampleRequest` KDoc warns about, since the frontend now owns the one catalog.

**The one protocol change: sample loading becomes eager.** Today it is lazy (unknown key reaches the
engine, engine answers `RequestSample`, frontend loads and uploads). With ids the frontend resolves
before it schedules. Resolution is an index lookup, not a download, so it can happen at pattern
compile time; the frontend already awaits `SampleReceived` before playing and the offline renderer
already preloads. `RequestSample` disappears; an unknown id is silence plus a diagnostics counter.

**Sample upload skips the chunk copy.** The host asks the engine for a buffer, `sample_alloc(frames)`
with a declared frame count checked against the cap, and writes PCM straight into linear memory.
That removes the per-chunk audio-thread copy in `SampleStore` as well.

**Built-in vocabulary.** Today `registerDefaults()` in the backend builds the built-ins from DSL
values and the warmup vocabulary uses them. If names never cross the wire, "built-in" means "the
frontend registers these DSL trees at session start" and the engine knows node types only. The warmup
then needs its own small set of DSL trees defined in Zig; they are plain structs.

## Decisions to make before the port

1. **f32 inside the engine.** The wire keeps Double; Zig converts at the boundary. Halves sample
   memory and doubles SIMD lanes. `ANTI_DENORMAL` stays, since Wasm has no flush-to-zero flag.
2. **Ignitor DSL execution.** Interpret the decoded tree as today, or flatten it into a node array
   at registration. Flattening is where most of the Wasm speed win over Kotlin/JS would come from
   and is far easier to design in than to retrofit.
3. **Acceptance bar for sound.** f32 versus f64 and different transcendental implementations
   guarantee small differences, so bit-equality with the Kotlin engine is off the table. Decide up
   front: a tolerance on the golden corpus plus ear checks on the frozen songs.

## Tests: what the port gives up and how it is covered

`audio_be` has 153 test files as of 2026-09-15. Split three ways:

- **DSP primitive tests** move to Zig and run under `zig build test`.
- **Behaviour tests** that already drive the dispatcher with `Cmd` and `renderBlock` (scheduler,
  culling, replace semantics, warehouse stats) keep running as Kotlin `jvmTest` against the Wasm
  engine through the JVM runtime. This is the strongest argument for making the dispatcher the seam.
- **Sound** is covered by a frozen golden corpus rendered from the Kotlin engine *before it is
  deleted*: the frozen songs (`FrozenSongs.kt`) plus one short script per ignitor, katalyst, filter
  and master chain, with fixed seeds. Compared with the tolerance from decision 3.

## Sequence

1. Refactor `KlangOfflineRenderer` onto `Cmd`. Kotlin only, independently valuable.
2. Map names to ids on the frontend and make sample loading eager. Kotlin only, independently
   valuable (dedups pitched recordings, removes `RequestSample`).
3. Freeze the golden corpus from the Kotlin engine.
4. Spike: two voices plus the master chain in Zig behind the dispatcher shim, byte wire generated,
   measured in the real worklet on a frozen song. Round-trip latency and CPU headroom, as the parked
   note prescribes. Only continue if the headroom is real.
5. Port the rest, both engines A/B'd against the same command stream in `jvmTest` until parity.
6. Delete `audio_be/commonMain`. Kotlin keeps the hosts and the shim.
7. Daemon host, with the WebSocket security work from the parked note (bind localhost, token
   handshake, origin check, frame cap).

## Anchors

- `docs/tasks/future/high-performance-audio-backend.md`: the parked note with the performance
  reasoning, the three backend paths and the daemon security model. This plan is its "path A plus B
  from one source" concretisation.
- `audio_be/.../PlaybackEngineDispatcher.kt`: the seam.
- `audio_be/.../AudioBackendContext.kt`: `RENDER_QUANTUM_FRAMES`, and the shared-per-backend state
  the Zig core owns.
- `audio_bridge/.../infra/KlangCommLink.kt`: the protocol; `audio_bridge/.../SampleRequest.kt`: the
  key that becomes `sampleId`.
- `audio-wire-codec-ksp/`: the generator that grows a byte layout and a Zig target.
- `klang/.../KlangOfflineRenderer.kt`: the host to move onto `Cmd` first.
