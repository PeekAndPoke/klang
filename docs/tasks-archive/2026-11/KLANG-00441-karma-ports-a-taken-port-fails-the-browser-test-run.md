# KLANG-00441: Karma ports: a taken port fails the browser test run

> **Status (2026-11-13): DONE.** The probe is in all 17 port files; tried green with the port free and red with it held (tones).

## Why this exists

Every module with browser tests has a fixed Karma port (10031 to 10047, since 2026-10-19), so that one project's
ChromeHeadless never connects to another project's Karma server. Ultra found (ULTRA-00230, ultra 79125ef7) that the
fixed port alone does not hold: when the port is taken, Karma moves on to the next free one without a word, and that
port belongs to another module. Mission Control passed the finding on.

## Decide before implementing

Nothing open. Klang has one checkout that builds, so there is no slot per checkout as in ultra, and the 17 files
are edited as they are: no generator tool.

## The work

Each `<module>/karma.config.d/karma-port.js` (17 files) probes its port before it sets it: a child process tries to
listen on the port, and where it cannot, the file throws with the port and the module's name. The file is read
synchronously, which is why the probe is a child process. The ports are unchanged; list them with
`grep -r "var port" --include=karma-port.js .`.

## Acceptance

- With the port free, a module's browser tests run as before: `:tones:jsBrowserTest`, 394 tests, 0 failed.
- With the port held by another process, the run fails at once and says why: `:tones:jsBrowserTest` with 10046
  held ended in `BUILD FAILED` after 3 s with "Karma port 10046 of klang module "tones" is in use".
- Not run: the other 16 modules with the probe in place. Their files differ from the tested one in the port and the
  name only (all 17 pass `node --check`).

## History

- 2026-11-13: created.
- 2026-11-13: DONE. The probe is in all 17 port files; tried green with the port free and red with it held (tones).
