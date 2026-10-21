#!/usr/bin/env python3
"""Build a review round's inputs: the full diff of a change, the delta since the previous round,
and a stats line, so reviewers and the coordinator look at the same lines.

  review_pack.py [--worktree DIR] [--base REF] [--label NAME] [--out DIR] [--dash-scan]
  review_pack.py list  [--label NAME]
  review_pack.py clean [--label NAME]

What goes into full.diff: everything between the merge base and the working tree as it is now,
committed or not, including untracked files that git does not ignore. Renames are detected (-M)
and shown with their content changes. Nothing in the worktree or its index is changed.
Left out: the review logs (.claude/reviews/, written during the review, so a later round would hand
the reviewers the previous round's findings), every path in skip.txt next to this script (the
repo's own list: one path per line, # comments), and every --skip PATH; they stay as committed.

How the delta works: each round's exact state is saved as a commit under
refs/review-pack/<label>/round-<n> (a private ref namespace, never on a branch). The next round
diffs against it, so the previous state is never reconstructed by hand.

Output, in --out (default: $TMPDIR/review-pack/<label>/round-<n>/):
  full.diff   files.txt (status and path)   delta.diff (from round 2 on)   stats.txt   dashes.txt
The stats line prints file counts for both diffs, so a wrong base or delta shows up as odd numbers.

Standard library only; needs git. A first draft to copy and adapt.
"""
import argparse
import os
import re
import subprocess
import sys
import tempfile

REF_ROOT = "refs/review-pack"
SKIP = [".claude/reviews/"]  # what the review-log tool writes
SKIP_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "skip.txt")


def repo_skips():
    try:
        with open(SKIP_FILE, encoding="utf-8") as f:
            return [l.split("#")[0].strip() for l in f if l.split("#")[0].strip()]
    except FileNotFoundError:
        return []
DASHES = {"\u2014": "em dash", "\u2013": "en dash"}  # escaped, so a dash scan of this file finds nothing


def git(*args, cwd, env=None, check=True):
    r = subprocess.run(["git", *args], cwd=cwd, env=env, capture_output=True, text=True)
    if check and r.returncode != 0:
        sys.exit(f"review-pack: git {' '.join(args)} failed: {r.stderr.strip()}")
    return r.stdout


def default_base(wt):
    for ref in ("main", "master"):
        if subprocess.run(["git", "rev-parse", "--verify", "-q", ref], cwd=wt, capture_output=True).returncode == 0:
            return ref
    sys.exit("review-pack: no main or master branch; pass --base")


def snapshot(wt, base_commit, label, round_no, skip):
    """Commit the working tree as it is (tracked, modified, untracked-not-ignored, except the skipped
    paths, which stay as in HEAD) without touching the real index, and keep it under a private ref."""
    with tempfile.TemporaryDirectory() as tmp:
        env = dict(os.environ, GIT_INDEX_FILE=os.path.join(tmp, "index"))
        git("read-tree", "HEAD", cwd=wt, env=env)
        git("add", "-A", "--", ".", *[f":(exclude){p}" for p in skip], cwd=wt, env=env)
        tree = git("write-tree", cwd=wt, env=env).strip()
    commit = git("commit-tree", tree, "-p", base_commit, "-m", f"review-pack {label} round {round_no}",
                 cwd=wt, env=dict(os.environ, GIT_AUTHOR_NAME="review-pack", GIT_AUTHOR_EMAIL="review-pack@local",
                                  GIT_COMMITTER_NAME="review-pack", GIT_COMMITTER_EMAIL="review-pack@local")).strip()
    git("update-ref", f"{REF_ROOT}/{label}/round-{round_no}", commit, cwd=wt)
    return commit


def rounds(wt, label):
    out = git("for-each-ref", "--format=%(refname)", f"{REF_ROOT}/{label}/", cwd=wt)
    nums = [int(m.group(1)) for line in out.split() if (m := re.search(r"/round-(\d+)$", line))]
    return sorted(nums)


def dash_lines(diff_text):
    """file:line for added lines holding an em or en dash."""
    found, path, line_no, old_left, new_left = [], None, 0, 0, 0
    for line in diff_text.splitlines():
        if old_left > 0 or new_left > 0:  # inside a hunk: count lines, so "++ x" content is not a header
            if line.startswith("+"):
                if path and (names := [n for ch, n in DASHES.items() if ch in line]):
                    found.append(f"{path}:{line_no}: {', '.join(names)}")
                line_no += 1
                new_left -= 1
            elif line.startswith("-"):
                old_left -= 1
            elif not line.startswith("\\"):  # "\ No newline at end of file"
                line_no += 1
                old_left -= 1
                new_left -= 1
            continue
        if line.startswith("+++ "):
            path = line[6:] if line.startswith("+++ b/") else None
        elif (m := re.match(r"@@ -\d+(?:,(\d+))? \+(\d+)(?:,(\d+))? @@", line)):
            old_left = int(m.group(1)) if m.group(1) is not None else 1
            line_no = int(m.group(2))
            new_left = int(m.group(3)) if m.group(3) is not None else 1
    return found


def shortstat(wt, a, b):
    s = git("diff", "--shortstat", "-M", a, b, cwd=wt).strip()
    return s or "no changes"


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("mode", nargs="?", default="pack", choices=["pack", "list", "clean"])
    p.add_argument("--worktree", default=".", help="the worktree under review (default: current directory)")
    p.add_argument("--base", default=None, help="branch the change goes into (default: main, else master)")
    p.add_argument("--label", default=None, help="name for this change's rounds (default: current branch)")
    p.add_argument("--out", default=None, help="output directory for this round")
    p.add_argument("--skip", action="append", default=[], metavar="PATH",
                   help=f"leave this path out of the diffs (repeatable; always left out: {', '.join(SKIP)} and skip.txt)")
    p.add_argument("--dash-scan", action="store_true", help="list added lines with em or en dashes")
    a = p.parse_args(argv)
    wt = git("rev-parse", "--show-toplevel", cwd=os.path.abspath(a.worktree)).strip()
    label = a.label or git("rev-parse", "--abbrev-ref", "HEAD", cwd=wt).strip().replace("/", "-")
    done = rounds(wt, label)

    if a.mode == "list":
        for n in done:
            ref = f"{REF_ROOT}/{label}/round-{n}"
            print(f"round {n}: {git('rev-parse', '--short', ref, cwd=wt).strip()}  {ref}")
        return 0
    if a.mode == "clean":
        for n in done:
            git("update-ref", "-d", f"{REF_ROOT}/{label}/round-{n}", cwd=wt)
        print(f"review-pack: removed {len(done)} round ref(s) for {label}")
        return 0

    base = a.base or default_base(wt)
    merge_base = git("merge-base", base, "HEAD", cwd=wt).strip()
    round_no = (done[-1] + 1) if done else 1
    skip = SKIP + repo_skips() + a.skip
    current = snapshot(wt, merge_base, label, round_no, skip)
    out = a.out or os.path.join(tempfile.gettempdir(), "review-pack", label, f"round-{round_no}")
    os.makedirs(out, exist_ok=True)

    full = git("diff", "-M", "--no-color", merge_base, current, cwd=wt)
    files = git("diff", "-M", "--name-status", merge_base, current, cwd=wt)
    with open(os.path.join(out, "full.diff"), "w") as f:
        f.write(full)
    with open(os.path.join(out, "files.txt"), "w") as f:
        f.write(files)
    n_files = len(files.splitlines())
    untracked = git("ls-files", "--others", "--exclude-standard", "--", ".", *[f":(exclude){p}" for p in skip],
                    cwd=wt).split("\n")
    n_untracked = len([u for u in untracked if u])
    stats = [f"review-pack {label} round {round_no}  base {base} @ {merge_base[:10]}  state {current[:10]}",
             f"full:  {n_files} file(s), {shortstat(wt, merge_base, current)}  ({n_untracked} untracked file(s) included)"]
    generated = [u for u in untracked if re.search(r"(^|/)(build|\.gradle|node_modules|\.kotlin)/", u)]
    if generated:
        stats.append(f"! {len(generated)} untracked file(s) under build/, .gradle/ or node_modules/ are in the diff "
                     f"(e.g. {generated[0]}): is a .gitignore entry missing?")
    if done:
        prev = f"{REF_ROOT}/{label}/round-{done[-1]}"
        delta = git("diff", "-M", "--no-color", prev, current, cwd=wt)
        delta_files = git("diff", "-M", "--name-status", prev, current, cwd=wt)
        with open(os.path.join(out, "delta.diff"), "w") as f:
            f.write(delta)
        stats.append(f"delta: {len(delta_files.splitlines())} file(s) since round {done[-1]}, {shortstat(wt, prev, current)}")
        if git("merge-base", base, prev, cwd=wt).strip() != merge_base:
            stats.append("! the merge base moved since the previous round (rebased?): the delta includes upstream changes")
    if a.dash_scan:
        dashes = dash_lines(full)
        with open(os.path.join(out, "dashes.txt"), "w") as f:
            f.write("\n".join(dashes) + ("\n" if dashes else ""))
        stats.append(f"dashes: {len(dashes)} added line(s) with an em or en dash" + (" (see dashes.txt)" if dashes else ""))
    with open(os.path.join(out, "stats.txt"), "w") as f:
        f.write("\n".join(stats) + "\n")
    print("\n".join(stats))
    print(f"out:   {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
