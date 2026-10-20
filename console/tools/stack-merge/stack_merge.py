#!/usr/bin/env python3
"""Stack several reviewed branches onto the base branch, in order, on a NEW integration branch,
resolving only conflicts where both sides purely inserted lines at the same spot.

  stack_merge.py --branches wt/a,wt/b,wt/c [--base main] [--name NAME]
                 [--test "console/with-build-lock.sh ./gradlew build" --expect FooSpec,BarSpec]

What it does:
- Creates a fresh worktree on a new branch `stack/<name>` at the base branch's tip.
- For each branch in order, cherry-picks its commits (merge base..branch) onto the stack, skipping a
  commit whose change is already there (same patch, e.g. a branch built on an earlier one). The
  original branches, main and every other worktree are never changed.
- A branch holding a merge commit is refused before anything is created: a merge commit's own
  changes cannot be cherry-picked, and skipping it would drop them. Rebase the branch first.
- On a conflict it resolves a block only when the base side is empty (not even a blank line), i.e.
  both sides only inserted lines at the same place, AND the two insertions share no line with a
  letter or digit in it (a shared line may be meant once or twice: a person decides). The one
  exception is an `import` line, kept once. Both insertions are kept, the earlier branch's first.
  Every resolution is printed. When both blocks start with the same number or heading (two new "47."
  items), it warns about a likely numbering clash and does not renumber.
- Any other conflict (a changed or deleted line, binary, modify/delete, rename) stops the run. The
  report says exactly which commits are on the stack and which are not, and how to recover; an
  incomplete stack is never to be landed.
- With --test, runs the command once at the end in the stack worktree via test-digest (and
  --expect). Wrap the command in your repo's build lock: the stack worktree may resolve its lock
  file per worktree.
- It never fast-forwards main and never pushes. The report prints the command to land the stack, run
  where the base branch is checked out (or a ref update guarded by the base's sha when it is nowhere).

Exit codes: 0 stacked (and tests green), 1 tests not green (or no test ran), 2 stopped on a conflict it
may not resolve, 3 usage or git error (a branch with a merge commit included). Standard library only; --test needs ../test-digest. A first draft to copy and adapt.
"""
import argparse
import os
import re
import subprocess
import sys
from datetime import datetime

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "test-digest"))

CONFLICT = re.compile(
    r"^<<<<<<< [^\n]*\n(?P<ours>.*?)^\|\|\|\|\|\|\| [^\n]*\n(?P<base>.*?)^=======\n(?P<theirs>.*?)^>>>>>>> [^\n]*\n",
    re.S | re.M)
LEAD = re.compile(r"^\s*(?:[-*]\s+)?(#{1,6}\s+\S+|\d+[.)]|\[\d+\]|\*\*\d+)")
CONTENT = re.compile(r"[^\W_]")  # a line with a letter or digit; one without (blank, "}", "---") is structure
IMPORT = re.compile(r"^\s*import\s+[\w.*]+(\s+as\s+\w+)?\s*;?\s*$")


class Stop(Exception):
    """Usage or git error (exit 3)."""


class ConflictStop(Stop):
    """A conflict the tool may not resolve (exit 2)."""


def git(*args, cwd, check=True):
    r = subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True)
    if check and r.returncode != 0:
        raise Stop(f"git {' '.join(args)} failed: {(r.stderr or r.stdout).strip()}")
    return r


def lead(block):
    first = next((l for l in block.splitlines() if l.strip()), "")
    m = LEAD.match(first)
    return m.group(1) if m else None


def resolve_file(path):
    """Resolve pure-insertion blocks in place. Returns (resolutions, refusal or None)."""
    try:
        with open(path, encoding="utf-8", errors="surrogateescape") as f:
            text = f.read()
    except OSError as e:  # e.g. rename/rename: the conflicted path is not in the working tree
        return [], f"cannot read the conflicted file ({e.strerror})"
    if "<<<<<<< " not in text:
        return [], "conflicted but has no text conflict markers (binary, modify/delete or rename)"
    resolutions = []

    def fix(m):
        if m.group("base") != "":  # any base line, a blank one too, is a change, not an insertion
            raise Stop("a block changes or deletes existing lines")
        ours, theirs = m.group("ours"), m.group("theirs")
        # diff3 shows the whole blocks, without git's trimming of common lines, so a line in both is visible
        # here: it may be one addition (an import, a commit already on the stack) or two (each test's
        # "@Test"). Only import lines are decided (kept once); any other shared line stops.
        shared = {l.strip() for l in ours.splitlines() if CONTENT.search(l)} & \
                 {l.strip() for l in theirs.splitlines() if CONTENT.search(l)}
        other = sorted(l for l in shared if not IMPORT.match(l))
        if other:
            raise Stop(f"both insertions hold the line {other[0][:80]!r}: one addition or two? decide by hand")
        dropped = [l for l in theirs.splitlines(keepends=True) if l.strip() in shared]
        theirs = "".join(l for l in theirs.splitlines(keepends=True) if l.strip() not in shared)
        warn = f"kept once (in both): {', '.join(l.strip() for l in dropped)}" if dropped else None
        if lead(ours) and lead(ours) == lead(theirs):
            clash = f"both insertions start with {lead(ours)!r}: possible numbering clash, check by hand"
            warn = f"{warn}; {clash}" if warn else clash
        line = text[:m.start()].count("\n") + 1
        resolutions.append((line, ours, theirs, warn))
        return ours + theirs

    try:
        new = CONFLICT.sub(fix, text)
    except Stop as e:
        return resolutions, str(e)
    if "<<<<<<< " in new or ">>>>>>> " in new:
        return resolutions, "conflict markers it cannot parse"
    with open(path, "w", encoding="utf-8", errors="surrogateescape") as f:
        f.write(new)
    return resolutions, None


def stack_worktree(repo, name):
    """klang: <main checkout>-worktrees/stack-<name>, the sibling directory where every klang worktree lives
    (/project-layout, "Worktrees"; inside the repo a worktree puts a second copy of every class into the IDE's
    index), whichever worktree the tool is started from."""
    common = git("rev-parse", "--path-format=absolute", "--git-common-dir", cwd=repo).stdout.strip()
    main = os.path.dirname(common)
    return os.path.join(os.path.dirname(main), f"{os.path.basename(main)}-worktrees", f"stack-{name}")


def base_checkout(repo, base):
    """The worktree that has the base branch checked out, or None."""
    path = None
    for line in git("worktree", "list", "--porcelain", cwd=repo).stdout.splitlines():
        if line.startswith("worktree "):
            path = line[len("worktree "):]
        elif line == f"branch refs/heads/{base}":
            return path
    return None


def stack(repo, base, branches, name, out, progress):
    wt = stack_worktree(repo, name)
    branch = f"stack/{name}"
    git("worktree", "add", "-b", branch, wt, base, cwd=repo)
    progress.update(wt=wt, branch=branch, base_sha=git("rev-parse", base, cwd=repo).stdout.strip())
    out(f"stack-merge: {branch} in {wt}, from {base} @ {progress['base_sha'][:7]}")
    applied = progress["applied"]
    for i, b in enumerate(branches):
        mb = git("merge-base", base, b, cwd=repo).stdout.strip()
        every = git("rev-list", "--reverse", "--no-merges", f"{mb}..{b}", cwd=repo).stdout.split()
        # a commit whose change the stack (or the base) already holds, by patch: e.g. b built on an earlier branch
        commits = git("rev-list", "--reverse", "--no-merges", "--cherry-pick", "--right-only",
                      f"HEAD...{b}", cwd=wt).stdout.split()
        progress.update(current=b, done=[], todo=list(commits), rest=branches[i + 1:])
        out(f"\n== {b}: {len(commits)} commit(s)")
        for c in every:
            if c not in commits:
                out(f"   same      {git('log', '-1', '--format=%h %s', c, cwd=repo).stdout.strip()}"
                    f" (the same change is already on the stack)")
        if not commits:
            out("   nothing to apply (already on the base or the stack?)")
        for c in commits:
            subject = git("log", "-1", "--format=%h %s", c, cwd=repo).stdout.strip()
            r = git("-c", "merge.conflictStyle=diff3", "cherry-pick", "-x", c, cwd=wt, check=False)
            if r.returncode == 0:
                out(f"   ok        {subject}")
                progress["done"].append(progress["todo"].pop(0))
                continue
            # Judge from git's state, never from its message (which also holds subjects and paths)
            picking = git("rev-parse", "-q", "--verify", "CHERRY_PICK_HEAD", cwd=wt, check=False).returncode == 0
            files = git("diff", "--name-only", "--diff-filter=U", cwd=wt).stdout.split()
            staged = git("diff", "--cached", "--quiet", cwd=wt, check=False).returncode != 0
            if picking and not files and not staged:
                git("cherry-pick", "--skip", cwd=wt)
                out(f"   empty     {subject} (its change is already on the stack)")
                progress["done"].append(progress["todo"].pop(0))
                continue
            if not picking or not files:
                git("cherry-pick", "--abort", cwd=wt, check=False)
                raise Stop(f"{b}, commit {subject}: cherry-pick failed without a conflict: {(r.stderr or r.stdout).strip()[:300]}")
            for rel in files:
                stages = {line.split()[2] for line in git("ls-files", "-u", "--", rel, cwd=wt).stdout.splitlines()}
                refusal = None
                if "1" not in stages:
                    refusal, resolutions = "both sides added this file (no common base)", []
                else:
                    resolutions, refusal = resolve_file(os.path.join(wt, rel))
                if refusal:
                    git("cherry-pick", "--abort", cwd=wt, check=False)
                    raise ConflictStop(f"{b}, commit {subject}: {rel}: {refusal}.")
                for line, ours, theirs, warn in resolutions:
                    out(f"   resolved  {rel}:{line}  kept both insertions (stack first, then {b})")
                    for tag, block in (("stack", ours), (b, theirs)):
                        for l in block.rstrip("\n").splitlines()[:4]:
                            out(f"               {tag:>10} | {l[:110]}")
                    if warn:
                        out(f"             ! {warn}")
                git("add", rel, cwd=wt)
            if git("diff", "--cached", "--quiet", cwd=wt, check=False).returncode == 0:
                # the resolution kept only what the stack holds (an import kept once): the commit is empty
                git("cherry-pick", "--skip", cwd=wt)
                out(f"   empty     {subject} (after the resolution, its change is already on the stack)")
                progress["done"].append(progress["todo"].pop(0))
                continue
            git("-c", "core.editor=true", "cherry-pick", "--continue", cwd=wt)
            out(f"   merged    {subject}")
            progress["done"].append(progress["todo"].pop(0))
        applied.append(b)
    progress.update(current=None, todo=[], rest=[])
    return wt, branch, applied


def stop_report(repo, progress, branches, name, out):
    """After a stop: exactly what is on the stack and what is not, and a recovery that cannot land it half done."""
    wt, branch = progress.get("wt"), progress.get("branch")
    if not wt:
        return
    short = lambda c: git("log", "-1", "--format=%h %s", c, cwd=repo).stdout.strip()
    cur = progress.get("current")
    out("\n  The stack is INCOMPLETE: never land it as it is.")
    on = [f"{b} (all)" for b in progress["applied"]]
    if cur and progress["done"]:
        on.append(f"{cur}: " + "; ".join(short(c) for c in progress["done"]))
    out(f"  on the stack:     {', '.join(on) or 'nothing beyond the base'}")
    out("  NOT on the stack:")
    if cur:
        out(f"    {cur}: " + "; ".join(short(c) for c in progress["todo"]) + "  (the first is the one that stopped)")
    for b in progress.get("rest", []):
        out(f"    {b} (not started)")
    others = [b for b in branches if b != cur]
    out("  Recover, either:")
    out(f"    a) remove the stack and run again without {cur or 'the branch'}:")
    out(f"       git -C {repo} worktree remove --force {wt} && git -C {repo} branch -D {branch}")
    if others:
        out(f"       stack_merge.py --branches {','.join(others)} ...")
    if cur and progress["todo"]:
        out(f"    b) finish it by hand in {wt}: git -c merge.conflictStyle=diff3 cherry-pick -x {progress['todo'][0][:10]},")
        out("       resolve, git cherry-pick --continue; then, in this order: "
            + " ".join(c[:10] for c in progress["todo"][1:]) + (" (cherry-pick -x each)" if progress["todo"][1:] else ""))
        if progress.get("rest"):
            out(f"       and then every commit of {', '.join(progress['rest'])}, branch by branch;")
        out("       then run the tests in the stack worktree before anyone lands it.")


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("--branches", required=True, help="comma-separated, in landing order")
    p.add_argument("--base", default=None, help="branch to stack onto (default: main, else master)")
    p.add_argument("--name", default=datetime.now().strftime("%Y%m%d-%H%M%S"))
    p.add_argument("--repo", default=".", help="any worktree of the repo (default: current directory)")
    p.add_argument("--test", default=None, help="command to run once at the end, in the stack worktree")
    p.add_argument("--expect", default="", help="specs the test run must include (passed to test-digest)")
    p.add_argument("--own-package", default=None, help="passed to test-digest: a fresh suite outside it is foreign")
    a = p.parse_args(argv)
    out = print
    progress = {"applied": [], "done": [], "todo": [], "rest": []}
    branches = []
    try:
        repo = git("rev-parse", "--show-toplevel", cwd=os.path.abspath(a.repo)).stdout.strip()
        base = a.base or next((r for r in ("main", "master")
                               if git("rev-parse", "--verify", "-q", r, cwd=repo, check=False).returncode == 0), None)
        if not base:
            raise Stop("no main or master branch; pass --base")
        branches = [b.strip() for b in a.branches.split(",") if b.strip()]
        for b in branches:
            git("rev-parse", "--verify", "-q", b, cwd=repo)
            mb = git("merge-base", base, b, cwd=repo).stdout.strip()
            merges = git("rev-list", "--merges", f"{mb}..{b}", cwd=repo).stdout.split()
            if merges:
                raise Stop(f"{b} holds {len(merges)} merge commit(s) (e.g. {merges[0][:10]}, a merge of {base} into it?). "
                           f"A merge commit's own changes cannot be cherry-picked and would be lost; rebase {b} onto "
                           f"{base} (or squash it) and run again. Nothing was created.")
        wt, branch, applied = stack(repo, base, branches, a.name, out, progress)
    except Stop as e:
        out(f"\nstack-merge: STOPPED. {e}")
        stop_report(repo, progress, branches, a.name, out)
        return 2 if isinstance(e, ConflictStop) else 3
    code = 0
    if a.test:
        import test_digest as td
        out(f"\n== tests: {a.test}")
        os.chdir(wt)
        own = ["--own-package", a.own_package] if a.own_package else []
        # not green unless test-digest says 0: 5 (nothing ran) included
        code = 0 if td.main(["run", "--root", wt, "--expect", a.expect, *own, "--quiet", "--", "bash", "-c", a.test]) == 0 else 1
    out(f"\nstack-merge: {len(applied)} of {len(branches)} branch(es) stacked on {branch}" + (
        "" if not a.test else f"; tests {'green' if code == 0 else 'NOT green'}"))
    out(f"  inspect:  git -C {wt} log --oneline {base}..{branch}")
    checkout = base_checkout(repo, base)
    if checkout:  # where the base branch is checked out, never the checkout the tool ran from
        out(f"  land:     git -C {checkout} merge --ff-only {branch}      (fails if {base} moved; your call)")
    else:
        out(f"  land:     git -C {repo} update-ref refs/heads/{base} {branch} {progress['base_sha']}")
        out(f"            ({base} is checked out nowhere; the update refuses if {base} moved from {progress['base_sha'][:7]}; your call)")
    out(f"  clean up: git -C {repo} worktree remove {wt} && git -C {repo} branch -D {branch}")
    return code


if __name__ == "__main__":
    sys.exit(main())
