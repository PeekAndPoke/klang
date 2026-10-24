#!/usr/bin/env python3
"""Mutation runner: prove a test goes RED for the right reason when the code is wrong.

  mutate.py run SPEC.json            # baseline, then each mutant: apply, test, restore, classify
  mutate.py restore BACKUP_DIR       # put files back after a hard kill (the path is printed at start)

SPEC.json:
  {
    "test_command": "./gradlew :core:jvmTest",            # string (bash -c) or list
    "mutants": [
      {"id": "cap-off", "file": "core/src/commonMain/kotlin/Pricing.kt",
       "old": "if (percent > 100) 100 else percent", "new": "percent",
       "expect_spec": "PricingSpec", "expect_message": "expected:<0>"}   # regex, matched against
    ]                                                        # the console and the XML failure text
  }

Verdicts:
  KILLED              the expected spec failed and the failure matches expect_message
  KILLED-UNVERIFIED   the expected spec failed, but no expect_message was given to check why
  KILLED-OTHER        the expected spec failed for a reason that does not match (inconclusive)
  KILLED-BY-COMPILE   the mutant does not compile (inconclusive, it proves nothing about the test)
  NOT-RUN             the expected spec produced no fresh results (cached, filtered, wrong task)
  SURVIVED            the expected spec stayed green: the test does not catch this change
  TIMED-OUT           the test run hit timeout_seconds (default 1800) and was killed (inconclusive:
                      the mutant may make a test hang; check by hand)
  NOT-APPLIED         "old" does not occur exactly once in the file, so nothing was changed

Safety:
- Before anything else every file is copied to a backup that mirrors its path (commonMain/x.kt and
  jvmMain/x.kt never collide). Restore is a byte copy from that backup, never git checkout, and every
  restore is verified by hash. Ctrl-C and SIGTERM restore before exiting.
- A baseline run (skip with --no-baseline) must show every expected spec ran and is green; otherwise
  every mutant would look "killed" by a test that was already failing.
- Hold the build lock for the WHOLE campaign, not per test command: the critical section is
  mutate -> build -> restore. For example: console/with-build-lock.sh python3 mutate.py run spec.json
  (and leave the lock wrapper out of test_command, or the nested lock waits for itself).

Exit codes: 0 every mutant KILLED, 1 any SURVIVED, 2 any inconclusive or NOT-APPLIED,
3 a restore could not be verified, or the final run on the restored code was not green (stop and check).

Standard library only; needs test_digest.py from ../test-digest. A first draft to copy and adapt.
"""
import argparse
import hashlib
import json
import os
import re
import shutil
import signal
import sys
import tempfile
from datetime import datetime, timezone

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "test-digest"))
import test_digest as td  # noqa: E402

MANIFEST = "manifest.json"
DEFAULT_TIMEOUT = 1800  # seconds per test run; spec-wide or per mutant via "timeout_seconds"


def sha(path):
    with open(path, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()


class Backup:
    """Path-mirrored copies of every file a campaign touches, plus a manifest for `restore`."""

    def __init__(self, root, files):
        self.root = root
        self.dir = tempfile.mkdtemp(prefix=f"mutate-{os.path.basename(root)}-")
        self.files = {}
        for rel in sorted(set(files)):
            src, dst = os.path.join(root, rel), os.path.join(self.dir, "files", rel)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copy2(src, dst)
            self.files[rel] = sha(src)
            if sha(dst) != self.files[rel]:
                raise RuntimeError(f"backup of {rel} does not match the original")
        with open(os.path.join(self.dir, MANIFEST), "w") as f:
            json.dump({"root": root, "files": self.files}, f, indent=1)

    def restore(self, rel):
        # The bytes only, not copy2: setting the timestamps needs the file's owner, and in the per-user build
        # (MC-00031) a source file can belong to the IDE's user. The hash check below proves the content.
        shutil.copyfile(os.path.join(self.dir, "files", rel), os.path.join(self.root, rel))
        return sha(os.path.join(self.root, rel)) == self.files[rel]

    def restore_all(self):
        return all([self.restore(rel) for rel in self.files])


def restore_from(backup_dir):
    with open(os.path.join(backup_dir, MANIFEST)) as f:
        m = json.load(f)
    ok = True
    for rel, digest in m["files"].items():
        dst = os.path.join(m["root"], rel)
        # The bytes only, as in Backup.restore: the timestamps need the file's owner.
        shutil.copyfile(os.path.join(backup_dir, "files", rel), dst)
        same = sha(dst) == digest
        ok &= same
        print(f"{'restored' if same else 'MISMATCH'}  {rel}")
    return 0 if ok else 3


def classify(d, mutant, log_text, exit_code=None, timeout=DEFAULT_TIMEOUT):
    spec = mutant.get("expect_spec")
    if exit_code == td.TIMED_OUT:
        return "TIMED-OUT", f"killed after {mutant.get('timeout_seconds', timeout)} s"
    if d.log and (d.log.compile_errors or any("compile" in t.lower() for t in d.log.failed_tasks)):
        first = d.log.compile_errors[0] if d.log.compile_errors else "a compile task failed"
        return "KILLED-BY-COMPILE", first[:160]
    suites = [s for s in d.fresh if td.spec_matches(s.name, spec)]
    if not suites:
        return "NOT-RUN", f"{spec} produced no fresh results"
    failed = sum(s.failures + s.errors for s in suites)
    if failed == 0:
        return "SURVIVED", f"{spec}: {sum(s.tests for s in suites)} tests, all green"
    pattern = mutant.get("expect_message")
    if not pattern:
        return "KILLED-UNVERIFIED", f"{spec}: {failed} failed; no expect_message to check the reason"
    simple = spec.rsplit(".", 1)[-1]
    console = "\n".join(f.test + "\n" + "\n".join(f.detail) for f in (d.log.failures if d.log else [])
                        if simple in f.test)
    xml_text = "\n".join(msg for s in suites for msg in s.messages)
    if re.search(pattern, console) or re.search(pattern, xml_text):
        return "KILLED", f"{spec}: {failed} failed, message matches /{pattern}/"
    hint = (d.log.failures[0].detail[:1] if d.log and d.log.failures else []) or [xml_text.strip()[:120]]
    return "KILLED-OTHER", f"{spec}: {failed} failed, but not with /{pattern}/; got: {hint[0][:120]}"


def run_campaign(spec_path, root, baseline=True, log_dir=None, final_run=True):
    with open(spec_path) as f:
        spec = json.load(f)
    default_cmd = spec.get("test_command")
    default_timeout = spec.get("timeout_seconds", DEFAULT_TIMEOUT)
    mutants = spec["mutants"]
    log_dir = log_dir or tempfile.mkdtemp(prefix="mutate-logs-")
    os.makedirs(log_dir, exist_ok=True)
    results, applicable = [], []

    for m in mutants:  # preflight: nothing is touched yet
        path = os.path.join(root, m["file"])
        if not os.path.isfile(path):
            results.append((m, "NOT-APPLIED", "file not found"))
            continue
        with open(path, encoding="utf-8") as f:
            n = f.read().count(m["old"])
        if n != 1 or m["old"] == m["new"]:
            why = "old text equals new text" if m["old"] == m["new"] else f"old text occurs {n} times, must be exactly 1"
            results.append((m, "NOT-APPLIED", why))
        elif not (m.get("test_command") or default_cmd) or not m.get("expect_spec"):
            results.append((m, "NOT-APPLIED", "needs test_command and expect_spec"))
        else:
            applicable.append(m)

    backup = Backup(root, [m["file"] for m in applicable])
    print(f"mutate: backup of {len(backup.files)} file(s) in {backup.dir}")
    print(f"mutate: after a hard kill, run: {os.path.abspath(__file__)} restore {backup.dir}")

    def on_signal(signum, _frame):
        ok = backup.restore_all()  # first, so a SIGKILL that follows soon cannot leave a mutant behind
        # then stop the test command: it runs in its own process group, which this signal did not reach,
        # and exiting with it alive would release the build lock while Gradle still runs
        td.stop_running()
        print(f"\nmutate: interrupted, files restored {'and verified' if ok else 'BUT NOT VERIFIED'}", file=sys.stderr)
        sys.exit(130 if ok else 3)

    signal.signal(signal.SIGINT, on_signal)
    signal.signal(signal.SIGTERM, on_signal)

    if baseline and applicable:
        for cmd in sorted({json.dumps(m.get("test_command") or default_cmd) for m in applicable}):
            specs = sorted({m["expect_spec"] for m in applicable if json.dumps(m.get("test_command") or default_cmd) == cmd})
            print(f"mutate: baseline {json.loads(cmd)}  expecting {', '.join(specs)} green")
            since = datetime.now(timezone.utc)
            exit_code, text = td.run_and_capture(json.loads(cmd), os.path.join(log_dir, "baseline.log"), quiet=True,
                                                 cwd=root, timeout=default_timeout)
            d = td.digest(root, since, text, specs, exit_code)
            if d.exit_code != 0 or exit_code == td.TIMED_OUT:
                print(td.render(d, root, exit_code, os.path.join(log_dir, "baseline.log")))
                print("mutate: baseline is not clean (see above); no mutant was applied.")
                shutil.rmtree(backup.dir, ignore_errors=True)  # nothing was mutated
                return 2, results

    verified = True
    for m in applicable:
        path = os.path.join(root, m["file"])
        with open(path, encoding="utf-8") as f:
            original = f.read()
        log_path = os.path.join(log_dir, f"{re.sub(r'[^A-Za-z0-9_.-]', '_', m['id'])}.log")
        print(f"mutate: {m['id']}  {m['file']}")
        since = datetime.now(timezone.utc)
        try:
            with open(path, "w", encoding="utf-8") as f:
                f.write(original.replace(m["old"], m["new"], 1))
            exit_code, text = td.run_and_capture(m.get("test_command") or default_cmd, log_path, quiet=True,
                                                 cwd=root, timeout=m.get("timeout_seconds", default_timeout))
        finally:
            restored = backup.restore(m["file"])
        if not restored:
            results.append((m, "RESTORE-FAILED", f"{m['file']} does not match its backup; see {backup.dir}"))
            verified = False
            break
        d = td.digest(root, since, text, [m["expect_spec"]], exit_code)
        verdict, detail = classify(d, m, text, exit_code, default_timeout)
        results.append((m, verdict, detail + f"  (log: {log_path})"))

    all_ok = verified and backup.restore_all()

    # The last mutant's failing XML would otherwise linger and read as fresh failures to a later
    # digest. One run on the restored code replaces it and proves the restore left the tests green.
    final_green = None
    if final_run and all_ok and any(v not in ("NOT-APPLIED",) for _, v, _ in results):
        final_green = True
        for cmd in sorted({json.dumps(m.get("test_command") or default_cmd) for m in applicable}):
            specs = sorted({m["expect_spec"] for m in applicable if json.dumps(m.get("test_command") or default_cmd) == cmd})
            since = datetime.now(timezone.utc)
            exit_code, text = td.run_and_capture(json.loads(cmd), os.path.join(log_dir, "final.log"), quiet=True,
                                                 cwd=root, timeout=default_timeout)
            d = td.digest(root, since, text, specs, exit_code)
            final_green &= d.exit_code == 0
            if d.exit_code != 0:
                print(td.render(d, root, exit_code, os.path.join(log_dir, "final.log")))
    order = {m["id"]: i for i, m in enumerate(mutants)}
    results.sort(key=lambda r: order[r[0]["id"]])
    print()
    print(f"{'mutant':<24} {'verdict':<18} detail")
    for m, verdict, detail in results:
        print(f"{m['id']:<24} {verdict:<18} {detail}")
    print(f"\nAll {len(backup.files)} mutated file(s) restored byte-identical: {'yes' if all_ok else 'NO, check ' + backup.dir}")
    if final_green is not None:
        print(f"Final run on the restored code: {'green, test results are fresh again' if final_green else 'NOT GREEN, see above'}")
    if all_ok:
        shutil.rmtree(backup.dir, ignore_errors=True)
    verdicts = {v for _, v, _ in results}
    code = 3 if not all_ok or final_green is False else 1 if "SURVIVED" in verdicts else 0 if verdicts <= {"KILLED"} else 2
    return code, results


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    sub = p.add_subparsers(dest="mode", required=True)
    r = sub.add_parser("run")
    r.add_argument("spec")
    r.add_argument("--root", default=None, help="repo root (default: git top level)")
    r.add_argument("--no-baseline", action="store_true", help="skip the unmutated baseline run")
    r.add_argument("--logs", default=None, help="directory for per-mutant build logs")
    r.add_argument("--json", default=None, help="also write the results to this JSON file")
    r.add_argument("--no-final-run", action="store_true", help="skip the run on the restored code (leaves the last mutant's XML behind)")
    s = sub.add_parser("restore")
    s.add_argument("backup_dir")
    a = p.parse_args(argv)
    if a.mode == "restore":
        return restore_from(a.backup_dir)
    root = os.path.abspath(a.root or td.git_root())
    code, results = run_campaign(a.spec, root, baseline=not a.no_baseline, log_dir=a.logs, final_run=not a.no_final_run)
    if a.json:
        with open(a.json, "w") as f:
            json.dump([{"id": m["id"], "file": m["file"], "verdict": v, "detail": d} for m, v, d in results], f, indent=1)
    return code


if __name__ == "__main__":
    sys.exit(main())
