# KLANG-00404: Bookmarks: keep the docs pages one comes back to, and show them somewhere

> **Status (2026-11-01): IDEA.** Nothing designed yet.

## Why this exists

The maintainer, 2026-11-01, while settling what the Motor menu's "last opened" shows (KLANG-00403): "I would only add
the Songs and Tutorial to the 'recently open'... For the docs and such we might think about a bookmark system and
display the bookmarks 'somewhere'".

Docs pages are looked up, not worked in: a list of the last ones read fills with pages passed on the way. What one
wants back is the few pages one chose to keep.

## Open questions

- **What can be bookmarked:** docs entries (a Lexikon word, a Sprudel or Stdlib function, a KlangScript or manual
  page) first; also songs, lessons, sample banks, a place in a long page?
- **How:** a small bookmark key on each page's heading (and a key binding), toggled.
- **Where they show ("somewhere"):** a group in the Motor menu (KLANG-00403, beside "Pick up where you left off"),
  the docs column, the search results, or more than one of these.
- **Order and limits:** the order kept by hand (drag, as the loadouts) or the newest first; a cap or none.
- **Where they live:** global state, so in the Spine (KLANG-00389), saved in its storage, next to the record of what
  was opened (KLANG-00403's `Recents`).

## History

- 2026-11-01: created from the maintainer's idea.
