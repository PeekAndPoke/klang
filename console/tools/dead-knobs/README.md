# dead-knobs

Probe 6 of KLANG-00420: a static check that every slot key a frontend door writes into
`VoiceData.ignitorParams` / `VoiceData.katalystParams` has an engine reader, and the reverse.
It reads the Kotlin sources only (no build, no lock).

```bash
python3 -I console/tools/dead-knobs/dead_knobs.py            # the report; exit 1 on (a), (c) or a writer it cannot name
python3 -I console/tools/dead-knobs/dead_knobs.py --list     # the whole key table: key, map, writers, readers
python3 -I console/tools/dead-knobs/dead_knobs.py --strict   # also fail on (b), (e), unused waivers
python3 -I console/tools/dead-knobs/tests/test_dead_knobs.py # the self-test
```

The sections, the waiver file and how keys are resolved are in the script's docstring. Exit 2 means the
tool could not see the layout it expects (no `Slots` objects, a slot helper that builds keys differently,
a malformed waiver); it never reports a clean run on a tree it could not read.

`tests/fixtures/panspread` is the unison door as it was before KLANG-00417 B7: `unison(pan)` wrote
`panSpread` and nothing read it. The self-test proves the tool reports it.
