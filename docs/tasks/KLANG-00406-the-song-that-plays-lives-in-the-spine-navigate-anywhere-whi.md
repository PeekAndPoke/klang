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
- **Only real songs are on the deck** (the maintainer, 2026-11-01: "This is only true for a 'real' song, not for code
  examples"). A song page or Klang Amp puts a song on it; a code example in the docs or a tutorial is a playback of
  its own, owned by its page as today, and ends when its page goes.
- **A new song stops the one before.** Live play (the MIDI rig, KLANG-00402) plays along with anything and is stopped
  by nothing but a panic.
- **A code example makes the song step aside, and the song comes back** (D1; the maintainer asked: "should it just
  fade out and when the example is done fade in again?"). Proposal:
  - **Out:** the song's voices still waiting are cleared and the sounding ones released, so it ends on its own
    release tails, a fade without a new gain in the engine (the wire has no per-playback gain today, checked
    against `KlangCommLink.Cmd`).
  - **Meanwhile:** the song's clock runs on (its scheduler is asked as ever, KLANG-00399's `tick`) but sends no
    voices: it costs nothing to render, which matters on a phone.
  - **Back:** when no example plays any more, the song comes in again at its next cycle, in time, as if it had played
    on; its control state is there (the scheduler's catch-up keeps it).
  - Should the release tails sound too abrupt: a per-playback gain in the engine for a real fade, its own ticket.
- **The logic and the state of all this live in the Spine** (the maintainer, 2026-11-01: "Logic and state handling
  for this should then also live in the spine"): which song is on the deck, which examples play, whether the song
  stands aside, when it comes back. The pages only ask (play this example, stop it) and show what the Spine says.
- **What lives where:** the deck and the song's state in the Spine; the edits in `SongStorage` (into
  `spine/storage/` with the other stores, KLANG-00389), as today saved while one types. The deck plays the code last
  sent to it, so a song edited and not yet updated plays on as it was, as on the page today.
- **A reload ends it.** Audio needs a user gesture after a reload, so the song does not go on by itself; the
  now-playing line could offer to start it again (D3).

## Decide before implementing

- **D1, a code example while a song plays:** the song steps aside and comes back in time after the example
  (proposal, as above), or it stops, or a real fade with an engine gain from the start.
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
5. The code examples by D1: the Spine sets the song aside while one plays and brings it back in time; then the start
   page by D2.

## Acceptance

- A song started on its page plays on while one reads the docs, opens a tutorial or another song's page.
- Back on its page, the editor shows the song that plays, its highlights running, and Update changes it live.
- From any page, the Motor shows what plays and stops it; the Stop key does too.
- Two songs never play at once; the MIDI rig plays along with either.
- A code example started while a song plays: the song steps aside, and when the example stops it comes back at its
  next cycle, in time.

## History

- 2026-11-01: created from the maintainer's ask; the code read: `CodeSongPage` (its controller and its unmount),
  `KlangampDeck`, `KlangCodePlaybackCtrl`, the start page's and the code examples' controllers. The same day: only
  real songs on the deck; a code example sets the song aside and it comes back in time (D1 proposal); all of it
  decided and kept in the Spine (the maintainer's word).
