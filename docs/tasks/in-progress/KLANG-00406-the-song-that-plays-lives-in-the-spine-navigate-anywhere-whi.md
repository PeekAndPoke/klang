# KLANG-00406: The song that plays lives in the Spine: navigate anywhere while it plays

> **Status (2026-11-01): IN PROGRESS.** Steps 1 to 3 done: `SongDeck` in the Spine, Klang Amp on it, the song page
> plays on it and its song plays on when the page is left. Next: step 4, the Motor's now-playing line and the Stop key.

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
4. The Motor's now-playing line and the Stop key from anywhere.
5. The per-playback gain in the engine (D1), with Klang-Main-Dev: the command, the ramped gain, the codec specs.
6. The code examples by D1: the song fades out over 2 s while one plays and in again after it; then the start page
   as a preview (D2), its fade-in on its own gain.

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
