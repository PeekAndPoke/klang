# KLANG-00406: The song that plays lives in the Spine: navigate anywhere while it plays

> **Status (2026-11-02): IN PROGRESS.** Steps 1 to 5 done: `SongDeck` in the Spine, Klang Amp on it, the song page
> plays on it and its song plays on when the page is left, the status bar and the Stop key from anywhere, the
> per-playback gain in the engine (agreed with Klang-Main-Dev), its fade linear (the maintainer's choice), and step 6's
> first half: a code example fades the song out and in (`Previews`). Next: the start page's waiting song as a preview
> (D2). Open for the maintainer: what the Stop key stops while an example plays over a faded song.

## Why this exists

The maintainer, 2026-11-01: "We should also redesign who owns the currently playing song. I think it would be very nice
for the user to be able to navigate around, f.e. in the docs while the song keeps playing. So the current song must
live in the Spine as well".

How it is today:

- **The page owns the song.** `CodeSongPage` makes its own `KlangCodePlaybackCtrl` from its `SongStorage` and calls
  `ctrl.stop()` when it unmounts: leaving the song page silences it.
- **A second owner already exists.** `KlangampDeck` (`visuals/`) holds one exclusive controller for Klang Amp's
  playlist, made once, and follows what plays through `Player.nowPlaying`.
- **More controllers:** the start page's waiting song, and one per `PlayableCodeExample` in the docs and tutorials.
  Who stops whom depends on who set `.exclusive()` (KLANG-00389's sweep, "Player", point 4), and every controller
  leaks a `Player.status` subscription (KLANG-00389, "Problems found").

## The design

**One stage for songs, in the Spine.** `SongDeck` (D4) is made once with the Spine, next to `Player`, the
collection point of the app's global state (KLANG-00389). It owns the song that plays: which song it is (a built-in
or yours, by its id, with its title), the code it plays, its RPM, its state (loading, playing, stopped), its errors,
its signals and how long it has been heard. It is the one controller where today each page makes its own.

- **The song page becomes a view and an editor of the deck.** Opened on the song that plays, it attaches: the
  editor's highlights follow the deck's signals, Play / Update sends the edited code to the deck (a live update as
  today), Stop stops it. Opened on another song, it shows that one; its Play takes the stage from the song that
  played. Leaving the page stops nothing.
- **Klang Amp plays on the same deck.** `KlangampDeck` keeps its playlist and its "last song" and hands the playing
  to `SongDeck`; its own controller goes.
- **What plays is always in reach.** The Motor (in the column, on every page) gets a now-playing line: the song's
  title and time, play and stop; a click on the title opens its page. The Stop key (`KlangKeyBindings.Stop`,
  Ctrl/Cmd+Shift+.) stops it from anywhere while no editor holds the key. The Motor menu (KLANG-00403) shows it at
  the top of "Pick up where you left off".
- **Only real songs are on the deck** (the maintainer, 2026-11-01: "This is only true for a 'real' song, not for code
  examples"). A song page or Klang Amp puts a song on it; a code example in the docs or a tutorial is a playback of
  its own, owned by its page as today, and ends when its page goes.
- **A new song stops the one before.** Live play (the MIDI rig, KLANG-00402) plays along with anything and is stopped
  by nothing but a panic.
- **A code example fades the song out, and in again after it** (D1, decided, the maintainer, 2026-11-01: "i would
  not step aside, there might be a one minute long release tail... fade out over 2 seconds and in over 2 seconds
  again"):
  - **Out:** the song's gain goes to silence over 2 s; the song plays on underneath, in time, its voices and tails
    included.
  - **In:** when no example plays any more, its gain comes back over 2 s.
  - **Needs a per-playback gain in the engine:** the wire has none today (checked against `KlangCommLink.Cmd`). A new
    command (the playback, a target gain, a ramp in seconds) and a gain with a ramp on each playback's output before
    the house master. It is engine and wire work: `audio_be`, `audio_bridge` and the codec specs, by the engine's
    rules (`audio/CLAUDE.md`), agreed with Klang-Main-Dev, who works there.
  - **The cost:** the faded song still renders. On a phone that is the song's load plus the example's; measure it
    before deciding anything else.
  - **The same gain serves the start page** (D2): its waiting song fades in on its own gain instead of the engine's
    output volume, which ends one of the two writers of the volume KLANG-00389 found ("One writer of the volume").
- **The logic and the state of all this live in the Spine** (the maintainer, 2026-11-01: "Logic and state handling
  for this should then also live in the spine"): which song is on the deck, which examples play, whether the song
  is faded out, when it fades in. The pages only ask (play this example, stop it) and show what the Spine says.
- **What lives where:** the deck and the song's state in the Spine; the edits in `SongStorage` (into
  `spine/storage/` with the other stores, KLANG-00389), as today saved while one types. The deck plays the code last
  sent to it, so a song edited and not yet updated plays on as it was, as on the page today.
- **A reload ends it.** Audio needs a user gesture after a reload, so the song does not go on by itself; the
  now-playing line could offer to start it again (D3).

## SongDeck

The one owner of the real song that plays. In short, today's `KlangCodePlaybackCtrl` made once and kept by the
Spine, with the song's identity and the fade added:

- **What it holds:** which song (its id, built-in or yours, its title), the code it plays, the RPM, its state
  (stopped, compiling, playing), the compile errors, the playback's signals (what the editor's highlights and the
  visuals follow), how long the song has been heard.
- **What one can ask it:** `play(song, code, rpm)` puts a song on (and stops the song before it); `update(code, rpm)`
  changes the song that plays, live, as the song page's Update does today; `stop()`. From the lane's rules, not from
  pages: `fadeOut()` and `fadeIn()` (2 s, D1).
- **What it does itself:** compiles the code to a pattern (as `KlangCodePlaybackCtrl.runPlay` does today) and reports
  the errors; starts the playback on the `Player` and ends it; publishes to `nowPlaying`.
- **Who uses it:** the song page (opened on the song that plays it shows it and edits it; opened on another, its Play
  calls `play`), Klang Amp's playlist (`play` with a built-in song), the Motor's now-playing line (title, time, stop),
  the Stop key, the visuals.

- **As built (step 2):** the deck holds the controller that plays (`onDeck`: the song's id, the controller, whether
  a page put it on): `play(song)` for Klang Amp, `play(songId, ctrl)` for a song page (its Play and its Update),
  `stop()`, `ctrlFor(songId)` and `show(ctrl)` for the page. No `update`: the page's Update plays its controller on the
  deck again, live.

## The Spine's playbacks

The maintainer, 2026-11-01: "So we would need a spine.playbacks object or similar. This would container
mainPlayback?, realtimePlayback, something else?". What plays in the app today, read from the code:

| Plays | Where today | Lane |
|---|---|---|
| a song on its page | `CodeSongPage`'s own `KlangCodePlaybackCtrl` | `song` |
| a song from Klang Amp's playlist | `KlangampDeck`'s controller | `song` |
| a code example in the docs or a tutorial | `PlayableCodeExample`'s controller | `previews` |
| a sample from the library | `SamplesLibraryPage.playSample`, `playOnce` | `sounds` |
| a jingle | `jingles.kt`, `DevJinglesPage` | `sounds` |
| a sound effect on a button (none yet) | | `sounds` |
| the start page's waiting song | `StartPage`'s `waitingSong` | `previews` (D2) |
| a key on the MIDI keyboard | `RealtimePlaybackCtrl`, `createRealtimePlayback` | `live` |

So `Spine.playbacks` (`Playbacks`, next to `Player`, which keeps the engine itself: its start, the samples, the
volume, the diagnostics) holds four lanes and the rules between them:

```kotlin
object Playbacks {
    /** The real song: one at a time, plays on while one navigates (SongDeck). */
    val song: SongDeck

    /** What a page plays until it is stopped: a code example, the start page's waiting song. One at a time. */
    val previews: Previews

    /** One-shots that end by themselves: a sample, a jingle, a button's sound effect. Over everything, many at once. */
    val sounds: Sounds

    /** Live play: the MIDI rig's realtime voices (KLANG-00402). Plays along with anything. */
    val live: LivePlay

    /** Everything audible now, for the Motor's line, the visuals and Klang Amp (replaces Player.nowPlaying). */
    val nowPlaying: Stream<NowPlaying>

    /** Every lane quiet at once: the Panic key, CC 120 / 123. */
    fun panic()
}
```

- **The rules, in one place:** a new song stops the song before it; a new preview stops the preview before it; while a
  preview plays the song is faded out, playing on, and fades in after it (D1); sounds play over everything, several
  at once, and fade nothing (D5); live plays along with everything and stops for nothing but a panic.
- **Handles, not `Any`:** a page asks a lane and gets a handle back (play, stop, its state as a stream); the handle
  ends with its owner (a preview's page unmounting stops it). This is KLANG-00389's typed ownership (its `Player`
  plan, step 2) in place of `requestExclusivePlayback(self: Any, ...)` and the casts.
- **Names:** `song` rather than "main" (it is the real song, and only that), `live` rather than "realtime" (the epic's
  word, KLANG-00363), `previews` for what plays until stopped, `sounds` for the one-shots.
- **The MIDI rig** (KLANG-00402, `MidiRig`) keeps its sources, routing and instruments, and plays through
  `playbacks.live`.

## Decide before implementing

- **D1, decided:** a 2 s fade out and a 2 s fade in on a per-playback gain (above).
- **D2, decided:** the start page's waiting song is a preview (the maintainer: "yes would make sense if you see no
  reason why this might be wrong"). One case to watch: a real song already playing when one comes back to the start
  page. Proposal: then the waiting song does not start, the real song plays on (and the dyno's run, which measures
  the machine, would measure the song's load too). Done with step 2 (its review found the case
  easy to reach now, by the browser's back button from a playing song page): with a song on the deck the waiting song
  does not start and the volume does not fade under it. The dyno's run still measures with the song playing.
- **D3, decided: not now** (the maintainer: "No not now"). After a reload nothing is offered.
- **D4, decided:** `SongDeck` (the maintainer: "SongDeck is fine as a name"; what it does: "SongDeck", below).
- **D5, decided: a one-shot fades nothing** (the maintainer: "no it should not, because imagine we have
  sound-effects when clicking a button"). So the one-shots get a lane of their own, `sounds`, beside `previews`:
  `Playbacks` with `song`, `previews`, `sounds`, `live` (above).

## The work

After KLANG-00389's `Player` steps 1 and 2 (the move into the Spine, typed ownership):

1. `SongDeck` in the Spine with its specs (taking the stage, stopping, a live update, the exclusive rule and the
   live-play exception); `KlangCodePlaybackCtrl`'s logic moves into it, the `Player.status` leak fixed on the way.
   Done 2026-11-01, with step 3: `Playbacks` (the stage, out of `Player`) holds `song`, the deck; the deck keeps the
   `KlangCodePlaybackCtrl` on it (one of its own until step 2), which moved to the Spine with a `release()`; Klang Amp
   plays on the deck. The specs run on a `KlangPlayer` without a backend (`PlaybackEngine`, the seam).
2. The song page as a view: attach to the song that plays, take the stage, stop nothing on leave; the editor's
   highlights from the deck. Done 2026-11-01: the page's Play puts its own controller on the deck
   (`SongDeck.play(songId, ctrl)`); opened again on that song it takes the controller back (`ctrlFor`), its editor,
   highlights, cycle and Update going on. A controller is released when neither the deck nor a page holds it
   (`show`). A song Klang Amp put on is not taken by its page, which shows the user's version (Klang Amp plays the
   originals); its Play takes the stage. KLANG-00398's race fixed with it: a stop after the Play wins over a start
   still waiting for the engine.
3. Klang Amp on the deck (done with step 1).
4. The Motor's now-playing line and the Stop key from anywhere. Done 2026-11-01: `NowPlayingLine` under the Motor
   on every page (the time, the title opening its page, a stop key), dim with nothing on; the Stop key binds there
   (on the song's page its own Stop answers first). The time is the deck's own playback's, from one clock in the Spine
   (`Playbacks.songLine`) that ticks only while the song plays; Klang Amp's two 250 ms timers went to
   `Playbacks.heardSeconds`. Not yet: the line at the top of the Motor menu's "Pick up where you left off".
   2026-11-02, at the maintainer's word, after a full-width bar, one under the centre and a field in the column: the
   line became the `StatusBar`, LCD text on the Motor's screen floor (`Motoer`'s `display`): time, title, a stop key on
   the right; with no song the engine's version and revision roll through it. It fades out on the playing song's own
   page. The page is framed, thin, its corners cut.
5. The per-playback gain in the engine (D1), with Klang-Main-Dev: the command, the ramped gain, the codec specs.
   Done 2026-11-02 ("As built", below).
6. The code examples by D1: the song fades out over 2 s while one plays and in again after it; then the start page
   as a preview (D2), its fade-in on its own gain.
   First half done 2026-11-02: `Playbacks.previews` (`Previews`), the code examples' lane; a code example's controller
   is a preview (`KlangCodePlaybackCtrl.Builder.preview`, in place of `exclusive`). It stops another preview and what
   else holds the stage, fades the song on the deck to 0 over 2 s, and when none plays any more the song fades in over
   2 s and takes the stage back. A preview that fails to start counts as stopped; a song put on the deck stops the
   preview, also one still starting; a song leaving the deck goes back to full gain; the gain asked for while a song
   still starts reaches its playback before its first sound. Klang Amp follows its song on the deck while an example
   holds the stage. Open: the Stop key over a faded song (it stops the song, not the example one hears); a faded song's
   Update or rename would republish it to the stage (unreachable today: no example beside a song page's editor).

## Step 5: the per-playback gain (proposal to Klang-Main-Dev, 2026-11-02)

Read from the code on 2026-11-02: `KlangCommLink.Cmd` (no gain command), `PlaybackEngineDispatcher` (the engines sum
into one `mix`, then `MasterStage`), `PlaybackEngine.renderInto` (a straight path into the mix, or the engine's own
`bus` while a master chain or the stop release is in play), `TailRelease` and `KnobGlide` (the ramps it has).

- **The wire:** `Cmd.SetGain(playbackId, gain: Double, rampSec: Double)`, `@WireName("set-gain")`, a round-trip row
  in `WireCodecRoundTripSpec`. Seconds only (the stone rule). The ramp starts when the backend receives it, as
  `StartRealtimeVoice` does: a fade needs no musical time.
- **The dispatcher:** `engines[cmd.playbackId]?.setGain(...)`, an EXISTING engine only, as `StopRealtimeVoice` and
  `ReplaceVoices` do. Through `engineFor` it would resume a stopped engine (`scheduledAgain`), so a fade-in sent to a
  song that just stopped would revive it.
- **The engine:** a gain on the engine's output, after its master chain and before the sum into the mix (so the house
  limiter sees the faded song, and the song's own master chain hears it at full level: the sound stays itself, only
  quieter). At gain 1 and settled it takes the straight path as today, byte-identical; otherwise the own-bus path, the
  gain applied to the bus before `addFrom`, or before `TailRelease.addReleased` while releasing. A ramp per sample, in
  whole blocks with an exact landing (`KnobGlide`'s law), its span from the command rather than fixed at
  construction. A new target mid-ramp starts from where the gain IS. A non-finite gain or ramp is ignored, the stored
  values substituted (the cached-config rule); no clamp otherwise (the Motor stays raw).
- **The frontend:** `KlangPlayback` gets `setGain(gain, rampSec)`; the pattern scheduler keeps the last value and
  sends it again after its first `ScheduleVoices` of a (re)start, since a disposed engine comes back at 1. The Spine
  (step 6) is its only caller.
- **Specs:** `audio_be` at the mandatory tier: unity is byte-identical; a ramp lands exactly on its target at the
  block it names; a second engine at gain 1 is untouched; a gain sent to a stopped playback revives nothing; NaN is
  ignored. Each mutation-checked.
- **Open for Klang-Main-Dev:** the ramp's shape (linear in amplitude is the plain choice: -6 dB at half time; linear
  in dB sounds more even over 2 s, one `exp` per sample as `TailRelease` pays); and who builds it: Klang-Main-Dev, or
  Klang-Research under Klang-Main-Dev's review with the audio reviewer.

**Klang-Main-Dev's answer (2026-11-02):** agreed; Klang-Research builds it, Klang-Main-Dev reviews. Four points, all
in: quietness is measured before the gain (a spec row: stopped at gain 0, a playback lives as long as at gain 1); no
full-level first block at a start; a gain during a stop release documented; the ramp's shape goes to the maintainer.

**As built (2026-11-02), where it differs from the proposal:**

- **The start's gain goes first.** The worklet handles each message in its own task, so a render can fall between two
  messages, and a fresh playback sounds one block after its first voices. So the frontend sends its gain BEFORE the
  first `ScheduleVoices` (one port keeps the order), and the dispatcher creates the engine for a `SetGain` when none is
  filed under the id (as the registrations do), never resuming a stopped one. Klang-Main-Dev's condition: the
  frontend's playback is the one gate. It keeps every gain and sends only while started (a lock orders it against
  the start and the stop on the JVM), so no `SetGain` follows the stop's `Cleanup` and creates an engine nothing stops.
- **An engine still releasing a stopped playback** is detached by a `SetGain`, as a schedule does, and the gain goes to
  a fresh engine: the release fades on undisturbed (review round 1, both reviewers).
- **The ramp:** `PlaybackGain`, a position ramped linearly in whole blocks, per sample, landing exactly; the gain is
  the position, linear in amplitude (the maintainer, 2026-11-02, by ear: "The 2-second fade linear"; Klang-Main-Dev
  had proposed p³). The law lives in `gainOf` and `positionOf` alone.
- **Not yet:** a fade-in asked for right after a start runs while the playback still loads; step 6 asks for it once the
  playback has begun (`KlangCyclicPlayback.setGain`'s KDoc).

## Acceptance

- A song started on its page plays on while one reads the docs, opens a tutorial or another song's page.
- Back on its page, the editor shows the song that plays, its highlights running, and Update changes it live.
- From any page, the Motor shows what plays and stops it; the Stop key does too.
- Two songs never play at once; the MIDI rig plays along with either.
- A code example started while a song plays: the song fades out over 2 s and plays on silently; when the example
  stops, it fades in over 2 s, in time.

## History

- 2026-11-01: created from the maintainer's ask; the code read: `CodeSongPage` (its controller and its unmount),
  `KlangampDeck`, `KlangCodePlaybackCtrl`, the start page's and the code examples' controllers. The same day: only
  real songs on the deck; a code example sets the song aside and it comes back in time (D1 proposal); all of it
  decided and kept in the Spine (the maintainer's word). Then the Spine's `Playbacks` with three lanes (`song`,
  `previews`, `live`), on the maintainer's question. D1 decided (a 2 s fade each way, a per-playback gain in the
  engine), D2 (the waiting song a preview), D3 (not now). D4 (`SongDeck`) and D5 (one-shots fade nothing, their own
  lane `sounds`) decided.
- 2026-11-01: IN PROGRESS, step 1 with step 3 (the maintainer: "Yes go ahead"): `Playbacks`, `SongDeck`,
  `PlaybackEngine`; `KlangCodePlaybackCtrl` in the Spine with `release()`; Klang Amp on the deck, its seconds read
  from the stage (the two components that fed it no longer do).
- 2026-11-01: step 2 (the maintainer: "ok please go ahead"): the song page plays on the deck, its song plays on when
  the page is left, and the page opened again goes on with it; `SongDeck` keeps the controller on it rather than one of
  its own, `update` gone (the page's Update is a Play of the controller on the deck). KLANG-00398 fixed with it.
- 2026-11-01: step 2 tested by hand by the maintainer: "the song keeps playing when i navigate around and starting
  another song stop the first one and the second one starts playing".
- 2026-11-01: step 4 (the maintainer: "Ok please go ahead"): the now-playing line, the Stop key from anywhere, one clock
  for the time heard; in the kitchen sink and the look editor's LCD card.
- 2026-11-02: step 5 proposed to Klang-Main-Dev (the maintainer: "Ok please continue", to the question whether to):
  `Cmd.SetGain`, an existing engine only, a ramped gain on the engine's output before the mix.
- 2026-11-02: step 5 built (Klang-Main-Dev: "Please build it; I'll review"): `Cmd.SetGain`, `PlaybackGain`, the gain
  on each engine's output after its master chain, the frontend's gate. One review round, clean (coding, audio, and the
  status bar's). The same day, at the maintainer's word: the now-playing line became the `StatusBar` across the
  window's foot, the page framed above it in the bar's black, its top corners cut.
- 2026-11-02: step 5 reviewed by Klang-Main-Dev (b53d8a6e2): no CRITICAL or MAJOR, no action. The p³ law and the
  wire command wait for the maintainer's word, which Klang-Main-Dev put to them.
- 2026-11-02: the status bar settled on the Motor's screen as LCD text (the maintainer: "try the lcd"), hidden with a
  fade on the playing song's own page; the workbench's slot for it removed again. Review round clean, MINORs applied.
- 2026-11-02: the fade's law decided by the maintainer: linear ("The 2-second fade linear"); `PlaybackGain`'s law made
  linear. Step 6 started (the maintainer: "Please continue").

- 2026-11-02: step 6, first half: the code examples fade the song out and in (`Previews`). Two review rounds (round 1:
  a failed example start left the song silent, fixed; round 2 clean), every new row mutation-checked.
