# The recording the demo plays

The demo app plays one recorded render of a song beside its recorded events. The three files are local and are
not committed (the audio alone is about 100 MB):

| File | What it is |
|------|------------|
| `schmetterling.wav` | the audio |
| `schmetterling-events.json` | every sounding event of that render, with its tags |
| `schmetterling-energy.json` | the audio's loudness over time, measured once |

They are made in `prototypes/band` (its README says how: the recording spec, then `energy.py`) and live in
`prototypes/band/ignored/`. Here they are links to those files:

```
cd klangviz-demo/src/jsMain/resources/recording
for f in schmetterling.wav schmetterling-events.json schmetterling-energy.json; do
  ln -sf ../../../../../prototypes/band/ignored/$f $f
done
```

Without them the demo shows the band playing test notes, without sound.
