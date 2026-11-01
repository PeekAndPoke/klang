# KLANG-00406: The song that plays lives in the Spine: navigate anywhere while it plays

> **Status (2026-11-01): DESIGNED.** Nothing built; decisions D1 to D4 open.

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
- **Exclusive, with one exception.** A new song on the stage stops the one before (one rule, KLANG-00389's typed
  ownership); live play (the MIDI rig, KLANG-00402) plays along and is stopped by nothing but a panic.
- **What lives where:** the deck and the song's state in the Spine; the edits in `SongStorage` (into
  `spine/storage/` with the other stores, KLANG-00389), as today saved while one types. The deck plays the code last
  sent to it, so a song edited and not yet updated plays on as it was, as on the page today.
- **A reload ends it.** Audio needs a user gesture after a reload, so the song does not go on by itself; the
  now-playing line could offer to start it again (D3).

## Decide before implementing

- **D1, a docs or tutorial example while a song plays:** it takes the stage and stops the song (proposal: one rule;
  the Motor's line then offers the song again), or it plays over the song, or the song pauses and comes back after
  the example.
- **D2, the start page's waiting song:** on the same deck (proposal), or kept the start page's own as a jingle.
- **D3, after a reload:** the now-playing line remembers the last song and offers to start it (proposal), or nothing.
- **D4, the names:** `SongDeck` for the stage; the song page's controller becomes a view of it.

## The work

After KLANG-00389's `Player` steps 1 and 2 (the move into the Spine, typed ownership):

1. `SongDeck` in the Spine with its specs (taking the stage, stopping, a live update, the exclusive rule and the
   live-play exception); `KlangCodePlaybackCtrl`'s logic moves into it, the `Player.status` leak fixed on the way.
2. The song page as a view: attach to the song that plays, take the stage, stop nothing on leave; the editor's
   highlights from the deck.
3. Klang Amp on the deck.
4. The Motor's now-playing line and the Stop key from anywhere.
5. The code examples and the start page by D1 and D2.

## Acceptance

- A song started on its page plays on while one reads the docs, opens a tutorial or another song's page.
- Back on its page, the editor shows the song that plays, its highlights running, and Update changes it live.
- From any page, the Motor shows what plays and stops it; the Stop key does too.
- Two songs never play at once; the MIDI rig plays along with either.

## History

- 2026-11-01: created from the maintainer's ask; the code read: `CodeSongPage` (its controller and its unmount),
  `KlangampDeck`, `KlangCodePlaybackCtrl`, the start page's and the code examples' controllers.
