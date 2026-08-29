# RealtimePlaybackCtrl + MidiConnector — DRAFT for discussion

**Status:** DRAFT v2 (2026-08-29), nothing built. Supersedes the first draft (which put the
held-note bookkeeping in the playback controller — wrong seam). Sibling of
`docs/tasks/midi-keyboard-playground.md`; v1's ignitor editor lands on top of this.

## The three things, and what each one is NOT

| Piece | Owns | Deliberately does NOT know |
|---|---|---|
| `RealtimePlaybackCtrl` | player startup, the durable playback, forwarding voices, stopping by liveId, teardown | what a note is, what a voice sounds like, MIDI |
| `MidiConnector` | devices, byte parsing, **liveId assignment**, note→liveId map, emitting OFFs (release, retrigger, panic, unplug) | audio, `VoiceData`, the player |
| `MidiPlaygroundPage` | the translation: event → `VoiceData` (shape, sound, params) and the wiring | how either of the above works inside |

The controller is a **pipe**, not a manager: an id goes in with a voice, the same id stops it.
All per-note intelligence sits in the connector; all taste sits in the page.

## RealtimePlaybackCtrl

### State — reuse `Player.Status`, do not invent a second enum

How the song side does it today: `Player.Status` (`NOT_LOADED` / `LOADING` / `READY`) is the
global enum; `KlangCodePlaybackCtrl.State` is a flat data class of booleans and mirrors it once
in `init` as `isPlayerLoading = (status == LOADING)`; `CodeSongPage` nests ifs on the flags.

That mirror **collapses three states into one boolean** and loses `NOT_LOADED` vs `READY`. The
song page can afford it because its Play button doubles as the engine-start gesture, so it never
has to say "not started". Our page does — "Start Engine" is its own screen. So we keep the same
shape (flat State data class, mirrored once in `init`, one subscription for consumers) but carry
the **enum itself** instead of a lossy boolean. No new concept, one less lie.

The single thing `Player.Status` cannot answer is "does THIS controller's playback exist yet" —
`READY` can already be true when the page is entered after playing a song. That is one own flag.

```kotlin
class RealtimePlaybackCtrl(private val playbackName: String) {

    data class State(
        /** Mirrored from [Player.status] — the same enum the song page's ctrl mirrors. */
        val playerStatus: Player.Status,
        /** True once this controller's durable playback exists and can take voices. */
        val isReady: Boolean,
        /** Player startup has no failure state today (see the note below); local only. */
        val error: String? = null,
    )

    val state: Stream<State>

    /** Idempotent. Call from the "Start Engine" button (browsers need a user gesture). */
    fun start()

    /** Mint an id for a voice about to be started. Unique per playback. */
    fun nextLiveId(): Int

    /** Fire a voice now. [gateDurSec] null = held until [stopVoice]. No-op unless Ready. */
    fun startVoice(liveId: Int, data: VoiceData, gateDurSec: Double? = null)

    /** Release the voice(s) with [liveId] into their ADSR tail. Unknown id = no-op. */
    fun stopVoice(liveId: Int)

    /** Register a custom ignitor on this playback (v1's editor uses it). */
    fun registerIgnitor(name: String, dsl: IgnitorDsl)

    /** Stops the playback and releases everything the engine still holds. Call from onUnmount. */
    fun tearDown()
}
```

Inside: `Player.ensure().await()` → `KlangPlayer.createRealtimePlayback(playbackName)` (which
already mangles to `"custom-$name"` and is idempotent per name), then thin delegation.

One small engine-side addition needed: `KlangRealtimeVoicePlayback.startVoice` currently mints
the liveId itself and returns it. Add an overload that ACCEPTS one
(`startVoice(liveId, data, gateDurSec)`), because with the connector assigning ids the mint has
to happen before the voice exists. Keep the minting counter on the playback (it is per-playback
and monotonic — that is what `nextLiveId()` exposes), so two connectors on one controller can
never collide.

## MidiConnector

```kotlin
class MidiConnector(private val nextLiveId: () -> Int) {

    enum class Status { Unsupported, Requesting, Denied, Ready }

    data class State(val status: Status, val devices: List<String>, val error: String? = null)

    val state: Stream<State>
    val events: Stream<Event?>

    /** Exhaustively mappable onto the controller — that is the whole point of the shape. */
    sealed interface Event {
        /** A key went down. [liveId] is already minted; the page decides what it sounds like. */
        data class NoteOn(
            val liveId: Int,
            val channel: Int,
            val note: Int,
            val velocity: Int,
            val tsMs: Double,
        ) : Event

        /** A key came up, OR a retrigger replaced a still-held note. */
        data class NoteOff(
            val liveId: Int,
            val channel: Int,
            val note: Int,
            val tsMs: Double,
        ) : Event

        /** Panic CC (120/123), device unplug, or teardown — every id the connector still holds. */
        data class AllNotesOff(val liveIds: List<Int>, val reason: Reason) : Event

        /** Everything else: v1 maps these onto oscparams. */
        data class ControlChange(
            val channel: Int,
            val cc: Int,
            val value: Int,
            val tsMs: Double,
        ) : Event
    }

    enum class Reason { PanicCc, DeviceDisconnected, TearDown }

    fun start()
    fun tearDown()
}
```

What it encapsulates (all currently inline in the page): Web MIDI access + hot-plug re-hook,
the vel-0-note-on-is-note-off rule, the (channel, note) → liveId map, retrigger emitting a
`NoteOff` for the old id before the `NoteOn` for the new one, panic CCs and device unplug
becoming `AllNotesOff`.

## The page after this

```kotlin
private val ctrl = RealtimePlaybackCtrl(playbackName = "midi-playground")
private val midi = MidiConnector(nextLiveId = ctrl::nextLiveId)

init {
    lifecycle {
        onMount {
            midi.start()
            midi.events.subscribeToStream { evt -> if (evt != null) handle(evt) }
        }
        onUnmount {
            midi.tearDown()
            ctrl.tearDown()
        }
    }
}

/** THE translation. Voice shape, sound and params live here — page taste, nothing else's. */
private fun voiceFor(evt: MidiConnector.Event.NoteOn): VoiceData = VoiceData.empty.copy(
    sound = "supersaw",
    freqHz = Midi.midiToFreq(evt.note.toDouble()),
    velocity = evt.velocity / 127.0,
)

private fun handle(evt: MidiConnector.Event): Unit = when (evt) {
    is MidiConnector.Event.NoteOn -> ctrl.startVoice(evt.liveId, voiceFor(evt))
    is MidiConnector.Event.NoteOff -> ctrl.stopVoice(evt.liveId)
    is MidiConnector.Event.AllNotesOff -> evt.liveIds.forEach { ctrl.stopVoice(it) }
    is MidiConnector.Event.ControlChange -> Unit  // v1
}

override fun VDom.render() {
    val s = ctrl.state()
    when {
        // The engine is up: devices, keys, log — and in v1 the editor pane.
        s.isReady -> renderPlayground()
        // Spinner ON the button, exactly like CodeSongPage's Play button does it.
        s.playerStatus == Player.Status.LOADING -> renderStartEngineButton(loading = true)
        // The user gesture the browser requires — an honest state, not a suspended context.
        else -> renderStartEngineButton(loading = false)
    }
}
```

`handle` is an exhaustive `when` **expression** (house rule), so a new event variant fails the
build until the page decides what it means.

## What this buys

1. **The AudioContext gesture problem disappears by design.** "Start Engine" IS the gesture;
   `Status.NotStarted` is the honest initial state instead of a silently suspended context. The
   deep-link cold start parked in the playground doc is closed by the shape, not by a workaround.
2. **The two-maps wart goes away**: one (channel, note) → liveId map inside the connector; the
   UI's held-key display becomes a projection of connector state rather than a second map that
   can disagree.
3. **The MIDI byte logic becomes testable** (vel-0, panic CCs, retrigger ordering) — it is
   currently unreachable by any spec.
4. **v1 has its seam**: the editor compiles → `ctrl.registerIgnitor(name, dsl)`, and `voiceFor`
   swaps `sound = "supersaw"` for the registered name. Neither controller changes.

## Open questions (small)

1. **Voice display.** The page currently renders `pressed` (note → velocity). Simplest: keep a
   tiny page-level map updated in `handle` (it is UI state, not audio state), or expose
   `heldNotes` as connector state. Lean: connector state — one truth, and the page stays thin.
2. **`ControlChange` now or later?** It costs one variant to emit it today and saves touching
   the connector in v1. Lean: emit it now, ignore it in the page.
3. **Does `tearDown` stop the playback or leave it warm?** Stopping is tidy (the engine drains);
   leaving it warm makes re-entering the page instant. Lean: stop — `Cmd.Cleanup` now releases
   held voices, and `createRealtimePlayback` rebuilds cheaply on return.
4. **Name**: `RealtimePlaybackCtrl` (mirrors `KlangRealtimeVoicePlayback`) — or
   `RealtimeVoiceCtrl`, since it forwards voices rather than managing a "playback"?
5. **`Player` has no failure state.** `Player.ensure()` launches a coroutine and completes a
   deferred; if creation throws, the deferred never completes and `status` stays `LOADING`
   forever — the "Start Engine" button would spin indefinitely with no message. Our controller
   can carry a local `error` (as sketched) and stop pretending, but the honest fix is a
   `Player.Status.FAILED` + a caught exception in `ensure()`. That touches shared code the song
   page also uses, so: worth doing now, or note it and move on?

## Build order

1. `RealtimePlaybackCtrl` + the `startVoice(liveId, …)` overload on the playback; page keeps its
   inline MIDI code and just calls the controller. Provable by ear, no behavior change.
2. `MidiConnector`; page reduces to `voiceFor` + `handle` + render.
3. "Start Engine" gate on `ctrl.state`.
4. v1 editor pane on `registerIgnitor` + `voiceFor`.
