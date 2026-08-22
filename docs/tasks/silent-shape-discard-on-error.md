# A typo in a shape function silently discards the WHOLE shape

Found 2026-08-21 while diagnosing a reported `.tap()` bug in Der Schmetterling's lead. The tap
was innocent; this was the actual cause, and it cost a debugging session.

## What happens

`PatternMapperFn.chain` and `patternMapper` (`sprudel/src/commonMain/kotlin/lang/lang_helpers.kt`,
the `catch (e: Exception)` at ~:147 and ~:171) catch any exception thrown while a pattern mapper
runs, `println` a stack trace, and return the **input pattern unchanged**.

So one bad call anywhere in a shape function discards **every** transformation in it, not just the
failing one. Measured with a probe:

```
shape = x => x.gain(0.25).sound("saw").oscp("mids", 0.0)   -> sound=Named(saw) gain=0.25 oscParams={mids=0.0}
shape = x => x.gain(0.25).sound("saw").ocsp("mids", 0.0)   -> sound=null       gain=null  oscParams=null
```

`ocsp` is a typo for `oscp`. The note still plays, at the registry's default sound, with no gain,
no envelope, no filters, no osc params.

## Why it is hard to spot

- It does **not** fail the build, and it does **not** fail `BuiltInSongsSmokeTest` — the song
  compiles to a valid pattern. The failure only happens when the pattern is QUERIED.
- Compiled standalone the same call throws a clear `KlangScriptTypeError: Native type
  'ControlPattern' has no method 'ocsp'. Available methods: ...`. Inside a shape function applied
  via `.apply(...)`, that error is swallowed.
- The only signal is a `println` stack trace. In the browser that goes to the JS console, which a
  musician mixing by ear is not watching.
- The audible result is not silence, which would be obvious. It is a note that plays with a
  plausible-sounding default, so it reads as "my instrument sounds wrong" rather than "my code is
  broken".

## Why the catch exists

Presumably live-coding resilience: a bad edit mid-performance should not kill the audio. That is
a good goal and the fix should keep it. The problem is not the catching, it is that the failure
is invisible where the user is looking.

## Options, roughly in order of preference

1. **Surface it in the editor.** The intellisense/error channel already exists for compile errors;
   route query-time mapper failures there too, so a broken shape shows up where the user is
   typing. Keeps the audio alive, removes the invisibility.
2. **Fail the unknown-method case loudly at compile time.** A misspelled method is a static
   property of the source, not a runtime condition; `ControlPattern` already produces a good
   error message with suggestions. Catching it during a live query is what turns it into a
   mystery.
3. **At minimum, make the log identify the song and the call site** rather than printing a raw
   stack trace, and consider a one-shot visible warning per unique failure so it is not lost in
   console noise.

Do not simply remove the catch without replacing it: that trades a silent failure for a dead
audio thread mid-performance, which is worse.
