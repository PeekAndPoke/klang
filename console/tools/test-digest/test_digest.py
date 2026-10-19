#!/usr/bin/env python3
"""Digest a Gradle test run: which test tasks really ran, which specs produced fresh results,
how many tests failed, and which cases broke (from the console, not the XML).

  test_digest.py run [--expect SpecA,SpecB] [--log FILE] -- ./gradlew :core:jvmTest
  test_digest.py digest --since 2026-10-19T15:50:00Z [--log FILE] [--expect SpecA,SpecB]

Traps it guards against (see ultra/CLAUDE.md "Verification traps"):
- A filtered or cached run looks green. A spec counts as RAN only when its XML <testsuite timestamp>
  lies inside this run. A FROM-CACHE restore keeps the original timestamp, so it does not count,
  even though the file itself is new.
- kotest ignores --tests, so a filter says nothing about what ran. --expect names the specs that
  must have run; a missing or stale one fails the digest.
- The XML can name the wrong failing case. Counts come from the XML; case names come from the
  console log only.

- XML left behind by an earlier run counts only if it was written in this run: its file time must be
  after the start too, not only its <testsuite timestamp> (a test task that dies before rewriting its XML
  left the previous run's file, timestamped under 2 s earlier, to be read as fresh).
- Nothing ran is not green: no fresh results at all exits 5.

Exit codes: 0 all green, 1 test failures, 2 an expected spec missing or stale (or a foreign suite with
--own-package), 3 compile error, 4 the build failed for another reason, 5 nothing ran (no fresh results at
all, and nothing else went wrong). The highest of 1 to 4 wins; 5 only replaces a 0.

Standard library only. Copy it into your repo and adapt it; it is a first draft, not a dependency.
"""
import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone

SKIP_DIRS = {".git", "node_modules", ".gradle", ".idea", ".kotlin"}
TASK_LINE = re.compile(r"^> Task (:\S+)(?: (UP-TO-DATE|FROM-CACHE|NO-SOURCE|SKIPPED|FAILED))?\s*$")
FAILED_TEST = re.compile(r"^(\S.*?) > (.+) FAILED\s*$")
WHAT_WENT_WRONG_TASK = re.compile(r"Execution failed for task '(:[^']+)'")
COMPILE_ERROR = re.compile(r"^e: |^\S+\.(?:java|kt):\d+: error: ")
NO_TESTS = re.compile(r"No tests found for given includes")
CLOCK_SKEW = timedelta(seconds=2)


@dataclass
class Suite:
    task: str  # ":core:jvmTest"
    name: str  # "io.x.FooSpec" (platform suffix like "[jvm]" removed)
    tests: int
    failures: int
    errors: int
    skipped: int
    ran_at: datetime | None
    path: str
    written: float = 0.0  # the XML file's mtime (epoch seconds)
    messages: list = field(default_factory=list)  # failure/error messages and text, for regex checks


@dataclass
class ConsoleFailure:
    test: str  # "io.x.FooSpec > does a thing[jvm]"
    detail: list = field(default_factory=list)


@dataclass
class Log:
    tasks: dict = field(default_factory=dict)  # task -> outcome
    failures: list = field(default_factory=list)
    compile_errors: list = field(default_factory=list)
    failed_tasks: set = field(default_factory=set)
    no_tests_found: list = field(default_factory=list)
    build: str | None = None  # "SUCCESSFUL" | "FAILED"


def parse_time(value):
    if value is None or value == "":
        return None
    if re.fullmatch(r"\d+(\.\d+)?", value):
        return datetime.fromtimestamp(float(value), timezone.utc)
    t = datetime.fromisoformat(value.replace("Z", "+00:00"))
    return t if t.tzinfo else t.replace(tzinfo=timezone.utc)  # Gradle writes UTC


def parse_since(value):
    """--since must say its zone (Z, an offset) or be epoch seconds: a naive local time read as UTC is off
    by the machine's offset (two hours on a CEST machine), and everything reads STALE or RAN wrongly."""
    if not re.fullmatch(r"\d+(\.\d+)?", value) and not re.search(r"(Z|[+-]\d\d:?\d\d)$", value):
        raise argparse.ArgumentTypeError(f"--since {value!r} has no zone: add Z or an offset, or give epoch seconds")
    return parse_time(value)


def iso(t):
    return t.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ") if t else "-"


def strip_platform(name):
    return re.sub(r"\[[^\]]*\]$", "", name).strip()


def parse_log(text):
    log = Log()
    current = None
    for line in text.splitlines():
        m = TASK_LINE.match(line)
        if m:
            task, outcome = m.group(1), m.group(2) or "EXECUTED"
            log.tasks[task] = "FAILED" if outcome == "FAILED" else outcome
            if outcome == "FAILED":
                log.failed_tasks.add(task)
            current = None
            continue
        m = FAILED_TEST.match(line)
        if m:
            current = ConsoleFailure(f"{m.group(1)} > {m.group(2)}")
            log.failures.append(current)
            continue
        if current is not None and line.startswith((" ", "\t")) and line.strip():
            if len(current.detail) < 6:
                current.detail.append(line.strip())
            continue
        current = None
        if COMPILE_ERROR.match(line):
            log.compile_errors.append(line.strip())
        m = WHAT_WENT_WRONG_TASK.search(line)
        if m:
            log.failed_tasks.add(m.group(1))
            log.tasks[m.group(1)] = "FAILED"
        if NO_TESTS.search(line):
            log.no_tests_found.append(line.strip())
        if line.startswith("BUILD SUCCESSFUL"):
            log.build = "SUCCESSFUL"
        elif line.startswith("BUILD FAILED"):
            log.build = "FAILED"
    return log


def task_of(root, results_dir):
    """<root>/a/b/build/test-results/jvmTest -> ':a:b:jvmTest'"""
    rel = os.path.relpath(results_dir, root).split(os.sep)
    i = rel.index("build")
    module = ":".join(rel[:i])
    return f":{module}:{rel[-1]}" if module else f":{rel[-1]}"


def scan_suites(root):
    suites = []
    for dirpath, dirnames, filenames in os.walk(root):
        # another checkout nested inside this one (e.g. .claude/worktrees/*) has results of its own
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS
                       and not os.path.exists(os.path.join(dirpath, d, ".git"))]
        if os.sep + os.path.join("build", "test-results") + os.sep not in dirpath + os.sep:
            continue
        for name in filenames:
            if not (name.startswith("TEST-") and name.endswith(".xml")):
                continue
            path = os.path.join(dirpath, name)
            try:
                top = ET.parse(path).getroot()
            except ET.ParseError:
                continue  # half-written by a running build
            # Gradle writes one <testsuite> per file; vitest writes a <testsuites> root holding many,
            # with the timestamps on the children
            elements = [top] if top.tag == "testsuite" else top.findall("testsuite")
            for el in elements:
                suite = Suite(
                    task=task_of(root, dirpath), name=strip_platform(el.get("name", name[5:-4])),
                    tests=int(el.get("tests", 0)), failures=int(el.get("failures", 0)),
                    errors=int(el.get("errors", 0)), skipped=int(el.get("skipped", 0)),
                    ran_at=parse_time(el.get("timestamp") or top.get("timestamp")), path=path,
                    written=os.path.getmtime(path))
                for bad in el.iter():
                    if bad.tag in ("failure", "error"):
                        suite.messages.append((bad.get("message") or "") + "\n" + (bad.text or ""))
                suites.append(suite)
    return suites


def spec_matches(suite_name, wanted):
    # "io.x.FooSpec" by its simple name; vitest's "src/Graph.spec.ts" by its file name
    return suite_name == wanted or suite_name.endswith(("." + wanted, "/" + wanted))


@dataclass
class Digest:
    since: datetime
    log: Log | None
    fresh: list
    stale: list
    expected: list  # (name, status, suites)
    exit_code: int
    foreign: list = field(default_factory=list)  # (suite, why): results that likely belong to another project


def class_name(suite):
    """The suite's class name without the Kotlin/JS task prefix: Kotlin/JS names a browser suite
    'jsBrowserTest.io.x.FooSpec' (and Node 'jsNodeTest.io.x.FooSpec'), the JVM plain 'io.x.FooSpec'.
    (From Spross-Main-Dev, b8f9aa1.)"""
    task = suite.task.rsplit(":", 1)[-1]
    return suite.name[len(task) + 1:] if suite.name.startswith(task + ".") else suite.name


def package_root(name, depth=3):
    """'io.peekandpoke.klang.sprudel.FooSpec' -> 'io.peekandpoke.klang'; None for path-style names."""
    parts = name.split(".")
    return ".".join(parts[:depth]) if len(parts) > depth and "/" not in name else None


def find_foreign(fresh, own_package=None):
    """Suites that ran in this window but look like another project's. Kotlin/JS browser tests from two
    projects at once can connect a browser to the other project's Karma server (seen on 2026-10-19)."""
    found = []
    if own_package:
        found = [(s, f"not under {own_package}") for s in fresh
                 if "/" not in s.name and "." in class_name(s)  # a runner's own suite ("Kotest") has no package
                 and not class_name(s).startswith(own_package + ".")]
        return found
    by_task = {}
    for s in fresh:
        if (root := package_root(class_name(s))):
            by_task.setdefault(s.task, {}).setdefault(root, []).append(s)
    for task, roots in by_task.items():
        if len(roots) > 1:
            main = max(roots, key=lambda r: len(roots[r]))
            found += [(s, f"package {r} in a task otherwise under {main}") for r, ss in roots.items() if r != main for s in ss]
    return found


def digest(root, since, log_text=None, expect=(), build_exit=None, own_package=None):
    log = parse_log(log_text) if log_text is not None else None
    suites = scan_suites(root)
    cutoff = since - CLOCK_SKEW

    def in_run(s):  # the timestamp (not a cached restore) AND the file time (not a leftover of the last run)
        return bool(s.ran_at and s.ran_at >= cutoff and s.written >= since.timestamp())

    fresh = [s for s in suites if in_run(s)]
    stale = [s for s in suites if not in_run(s)]
    if log and log.tasks:
        # The log is the authority on what ran: a task it shows as restored or skipped did not run,
        # and neither did one it does not name at all (e.g. the build stopped before reaching it),
        # however recent its XML timestamps are (back-to-back runs can be under a second apart).
        fresh_ok = [s for s in fresh if log.tasks.get(s.task) in ("EXECUTED", "FAILED")]
        stale += [s for s in fresh if s not in fresh_ok]
        fresh = fresh_ok
    expected = []
    for want in expect:
        f = [s for s in fresh if spec_matches(s.name, want)]
        st = [s for s in stale if spec_matches(s.name, want)]
        expected.append((want, "RAN" if f else "STALE" if st else "MISSING", f or st))
    code = 0
    if any(s.failures or s.errors for s in fresh) or (log and log.failures):
        code = 1
    if any(status != "RAN" for _, status, _ in expected):
        code = 2
    if log and (log.compile_errors or any("compile" in t.lower() for t in log.failed_tasks)):
        code = 3
    elif code == 0 and ((log and log.build == "FAILED") or (build_exit not in (None, 0))):
        code = 4
    foreign = find_foreign(fresh, own_package)
    if own_package and foreign and code < 2:
        code = 2  # another project's results counted here: this run proves nothing
    if code == 0 and not fresh:
        code = 5  # nothing ran: an UP-TO-DATE task, a filter that matched nothing, a command that runs no test
    return Digest(since, log, fresh, stale, expected, code, foreign)


def is_test_task(task):
    """':core:jvmTest' yes; ':core:cleanJvmTest', ':core:compileTestKotlinJvm', ':testClasses' no."""
    name = task.rsplit(":", 1)[-1]
    return bool(re.search(r"[Tt]est$", name)) and not re.match(r"(clean|compile|process)", name) \
        and not re.search(r"Classes|Report|Sources", name)


def test_tasks(d):
    tasks = {}
    for s in d.fresh:
        t = tasks.setdefault(s.task, {"suites": 0, "tests": 0, "failures": 0, "errors": 0, "skipped": 0})
        t["suites"] += 1
        for k in ("tests", "failures", "errors", "skipped"):
            t[k] += getattr(s, k)
    if d.log:
        for task, outcome in d.log.tasks.items():
            # klang: :m:jsTest is an aggregate; its results are :m:jsBrowserTest's, listed on their own
            aggregate = task.endswith(":jsTest") and task[:-len("jsTest")] + "jsBrowserTest" in d.log.tasks
            if is_test_task(task) and not aggregate:
                tasks.setdefault(task, None)
    return tasks


def render(d, root, build_exit=None, log_path=None):
    out = [f"test-digest  since {iso(d.since)}  root {root}"]
    if d.log or build_exit is not None:
        status = d.log.build if d.log and d.log.build else "unknown"
        out.append(f"Build: {status}" + (f" (exit {build_exit})" if build_exit is not None else "")
                   + (f"   log: {log_path}" if log_path else ""))
    if d.log and d.log.compile_errors:
        out.append(f"Compile errors: {len(d.log.compile_errors)}  (first: {d.log.compile_errors[0][:160]})")
    elif d.log and any("compile" in t.lower() for t in d.log.failed_tasks):
        out.append("Compile task failed: " + ", ".join(sorted(t for t in d.log.failed_tasks if "compile" in t.lower())))
    out.append("Test tasks:")
    tasks = test_tasks(d)
    if not tasks:
        out.append("  (none ran in this window)")
    for task in sorted(tasks):
        t, outcome = tasks[task], (d.log.tasks.get(task, "?") if d.log else "ran")
        if t:
            out.append(f"  {task:<40} {outcome:<11} {t['suites']:>3} suites {t['tests']:>5} tests "
                       f"{t['failures']:>3} failed {t['errors']:>3} errors {t['skipped']:>3} skipped")
        else:
            why = {"FROM-CACHE": "restored from cache, not run", "UP-TO-DATE": "up to date, not run",
                   "NO-SOURCE": "no test sources", "SKIPPED": "skipped"}.get(outcome, "no fresh results")
            out.append(f"  {task:<40} {outcome:<11} {why}")
    if d.log and d.log.no_tests_found:
        out.append(f"  ! {d.log.no_tests_found[0]}")
    if d.expected:
        out.append("Expected specs:")
        for want, status, suites in d.expected:
            if status == "RAN":
                n, bad = sum(s.tests for s in suites), sum(s.failures + s.errors for s in suites)
                out.append(f"  {want:<40} RAN      {n} tests, {bad} failed  ({', '.join(sorted({s.task for s in suites}))})")
            elif status == "STALE":
                last = max((s.ran_at for s in suites if s.ran_at), default=None)
                out.append(f"  {want:<40} STALE    last ran {iso(last)}, not in this run")
            else:
                out.append(f"  {want:<40} MISSING  no results at all")
    if d.foreign:
        out.append("Foreign suites (results that look like another project's; parallel browser tests can cross over):")
        out.extend(f"  ! {s.task} {s.name}: {why}" for s, why in d.foreign[:20])
    if d.log and d.log.failures:
        out.append("Failing cases (from the console):")
        for f in d.log.failures[:40]:
            out.append(f"  {f.test}")
            out.extend(f"      {line[:200]}" for line in f.detail[:3])
    elif any(s.failures or s.errors for s in d.fresh):
        out.append("Suites with failures (case names: read the console; the XML can name the wrong case):")
        out.extend(f"  {s.task} {s.name}: {s.failures} failed, {s.errors} errors" for s in d.fresh if s.failures or s.errors)
    totals = {k: sum(getattr(s, k) for s in d.fresh) for k in ("tests", "failures", "errors")}
    cached = [t for t, o in (d.log.tasks.items() if d.log else []) if o in ("FROM-CACHE", "UP-TO-DATE") and t in tasks]
    ran = sum(1 for _, st, _ in d.expected if st == "RAN")
    out.append(f"Summary: {totals['tests']} tests, {totals['failures']} failed, {totals['errors']} errors "
               f"in {len({s.task for s in d.fresh})} task(s)"
               + (f"; {len(cached)} test task(s) not run (cache/up-to-date)" if cached else "")
               + (f"; expected {ran}/{len(d.expected)} ran" if d.expected else "")
               + f"  -> exit {d.exit_code}")
    return "\n".join(out)


def as_json(d):
    return {
        "since": iso(d.since), "exit_code": d.exit_code,
        "build": d.log.build if d.log else None,
        "compile_errors": d.log.compile_errors if d.log else [],
        "tasks": {t: v for t, v in test_tasks(d).items()},
        "task_outcomes": d.log.tasks if d.log else {},
        "expected": [{"spec": w, "status": st, "tests": sum(s.tests for s in ss),
                      "failed": sum(s.failures + s.errors for s in ss)} for w, st, ss in d.expected],
        "foreign": [{"task": s.task, "suite": s.name, "why": why} for s, why in d.foreign],
        "console_failures": [{"test": f.test, "detail": f.detail} for f in (d.log.failures if d.log else [])],
    }


TIMED_OUT = 124
RUNNING = []  # the commands run_and_capture is running now, so a signal handler can stop them (stop_running)


def stop(proc):
    """Stop a command started by run_and_capture and everything in its process group. Gradle runs tests in its
    daemon, outside that group: SIGINT (Ctrl-C) makes the client cancel the build there, which stops the test
    JVMs. Escalate only if it is ignored."""
    import signal
    for sig, grace in ((signal.SIGINT, 30), (signal.SIGTERM, 10), (signal.SIGKILL, 0)):
        try:
            os.killpg(proc.pid, sig)
        except ProcessLookupError:
            return
        try:
            proc.wait(grace)
            return
        except subprocess.TimeoutExpired:
            continue


def stop_running():
    for proc in list(RUNNING):
        stop(proc)


def run_and_capture(cmd, log_path, quiet=False, cwd=None, timeout=None):
    """Run cmd (a list, or one string for bash -c) in cwd, in its own process group, echoing and saving its
    output. Returns (exit, text); exit is TIMED_OUT (124) when it was killed after `timeout` seconds."""
    import threading
    argv = ["bash", "-c", cmd] if isinstance(cmd, str) else cmd
    lines, timed_out = [], threading.Event()
    with open(log_path, "w") as log:
        proc = subprocess.Popen(argv, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1,
                                cwd=cwd, start_new_session=True)
        RUNNING.append(proc)

        def kill():
            timed_out.set()
            stop(proc)
        timer = threading.Timer(timeout, kill) if timeout else None
        if timer:
            timer.start()
        try:
            for line in proc.stdout:
                log.write(line)
                lines.append(line)
                if not quiet:
                    sys.stdout.write(line)
            proc.wait()
        finally:
            if timer:
                timer.cancel()
            RUNNING.remove(proc)
    return (TIMED_OUT if timed_out.is_set() else proc.returncode), "".join(lines)


def git_root():
    try:
        return subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True, check=True).stdout.strip()
    except (subprocess.CalledProcessError, FileNotFoundError):
        return os.getcwd()


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    sub = p.add_subparsers(dest="mode", required=True)
    for name in ("run", "digest"):
        s = sub.add_parser(name)
        s.add_argument("--root", default=None, help="repo root to scan (default: git top level)")
        s.add_argument("--expect", default="", help="comma-separated spec names that must have run")
        s.add_argument("--json", action="store_true", help="print JSON instead of text")
        s.add_argument("--log", default=None, help="Gradle console log (digest: read it; run: write it here)")
        s.add_argument("--own-package", default=None,
                       help="e.g. io.peekandpoke.spross: any fresh suite outside it fails the digest (exit 2)")
    sub.choices["digest"].add_argument("--since", required=True, type=parse_since,
                                       help="run start: ISO time with Z or an offset, or epoch seconds")
    sub.choices["run"].add_argument("--quiet", action="store_true", help="don't echo the build output")
    sub.choices["run"].add_argument("cmd", nargs=argparse.REMAINDER, help="-- command to run")
    a = p.parse_args(argv)
    root = os.path.abspath(a.root or git_root())
    expect = [e.strip() for e in a.expect.split(",") if e.strip()]
    build_exit, log_path, text = None, a.log, None
    if a.mode == "run":
        cmd = a.cmd[1:] if a.cmd[:1] == ["--"] else a.cmd
        if not cmd:
            p.error("run needs a command after --")
        log_path = log_path or tempfile.mkstemp(prefix="test-digest-", suffix=".log")[1]
        since = datetime.now(timezone.utc)
        build_exit, text = run_and_capture(cmd, log_path, quiet=a.quiet or a.json, cwd=root)
    else:
        since = a.since
        if log_path:
            with open(log_path, errors="replace") as f:
                text = f.read()
    d = digest(root, since, text, expect, build_exit, a.own_package)
    print(json.dumps(as_json(d), indent=2) if a.json else "\n" + render(d, root, build_exit, log_path))
    return d.exit_code


if __name__ == "__main__":
    sys.exit(main())
