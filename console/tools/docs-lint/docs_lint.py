#!/usr/bin/env python3
"""Docs linter core: relative Markdown links resolve, and (optionally) no em or en dashes.

  docs_lint.py [PATH ...] [--forbid-dashes] [--check-anchors] [--root DIR]

PATH defaults to the repo root; directories are searched for *.md. Prints "file:line: problem"
and exits 1 when anything is found, so it can run as a pre-commit hook.

Repo-specific rules (task status vs folder, ledger rows, the next free question number, …) go in
a file named docs_lint_local.py next to this one. It defines

    def checks(root, files):          # files: list of absolute paths to the .md files linted
        yield path, line, "message"   # zero or more findings

and is picked up automatically. Keep the generic part here unchanged so it can be compared with
the other repos' copies.

What it does not do: a link that resolves says nothing about whether the text around it is right.
Standard library only. A first draft to copy and adapt.
"""
import argparse
import importlib.util
import os
import re
import subprocess
import sys

SKIP_DIRS = {".git", "node_modules", "build", ".gradle", ".idea", ".kotlin", "kotlin-js-store"}
INLINE_LINK = re.compile(r"!?\[(?:[^\]\\]|\\.)*\]\(\s*<?([^)\s>]+)>?(?:\s+\"[^\"]*\")?\s*\)")
REF_LINK = re.compile(r"^\s{0,3}\[[^\]]+\]:\s*<?(\S+?)>?\s*(?:[\"'(].*)?$")  # a title must be quoted
FENCE = re.compile(r"^\s{0,3}(```|~~~)")
INLINE_CODE = re.compile(r"`+[^`]*`+")
DASHES = {"\u2014": "em dash", "\u2013": "en dash"}  # escaped, so a dash scan of this file finds nothing


def listed_files(top):
    """Every file under `top` that git does not ignore (tracked or not), or None outside a git work tree.
    klang: git-ignored scratch copies (tmp/) once drowned a whole-repo run in 4,700 findings (2026-10-20)."""
    r = subprocess.run(["git", "ls-files", "-co", "--exclude-standard", "-z"], cwd=top, capture_output=True, text=True)
    if r.returncode != 0:
        return None
    files = (os.path.abspath(os.path.join(top, f)) for f in r.stdout.split("\0") if f and not f.endswith("/"))
    return [f for f in files if os.path.isfile(f)]  # a tracked file deleted from disk is listed too


def md_files(paths):
    for p in paths:
        if os.path.isfile(p):
            yield os.path.abspath(p)
            continue
        listed = listed_files(p)
        if listed is not None:
            yield from (f for f in listed if f.lower().endswith(".md"))
            continue
        for dirpath, dirnames, filenames in os.walk(p):
            dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS
                           and not os.path.exists(os.path.join(dirpath, d, ".git"))]  # nested checkouts
            yield from (os.path.abspath(os.path.join(dirpath, f)) for f in filenames if f.lower().endswith(".md"))


def prose_lines(text):
    """(line number, line without inline code), skipping fenced code blocks."""
    fenced = False
    for n, line in enumerate(text.splitlines(), 1):
        if FENCE.match(line):
            fenced = not fenced
            continue
        if not fenced:
            yield n, INLINE_CODE.sub("", line)


def slug(heading):
    """GitHub-style anchor for a heading (close enough for links written by hand)."""
    s = re.sub(r"[^\w\- ]", "", heading.strip().lower())
    return s.replace(" ", "-")


def anchors(path, cache={}):
    if path not in cache:
        with open(path, encoding="utf-8", errors="replace") as f:
            cache[path] = {slug(m.group(1)) for n, line in prose_lines(f.read())
                           if (m := re.match(r"^#{1,6}\s+(.*?)\s*#*\s*$", line))}
    return cache[path]


def check_file(path, forbid_dashes, check_anchors):
    with open(path, encoding="utf-8", errors="replace") as f:
        text = f.read()
    for n, line in prose_lines(text):
        targets = [m.group(1) for m in INLINE_LINK.finditer(line)]
        if (m := REF_LINK.match(line)):
            targets.append(m.group(1))
        for target in targets:
            if re.match(r"^[a-z][a-z0-9+.-]*:", target, re.I) or target.startswith("//"):
                continue  # http:, mailto:, ...
            file_part, _, anchor = target.partition("#")
            dest = path if not file_part else os.path.normpath(os.path.join(os.path.dirname(path), file_part))
            if file_part and not os.path.exists(dest):
                yield path, n, f"broken link: {target}"
            elif check_anchors and anchor and dest.lower().endswith(".md") and slug(anchor) not in anchors(dest):
                yield path, n, f"missing anchor: {target}"
        if forbid_dashes:
            for ch, name in DASHES.items():
                if ch in line:
                    yield path, n, f"{name} ({line.count(ch)}x)"


def local_checks(root, files):
    here = os.path.join(os.path.dirname(os.path.abspath(__file__)), "docs_lint_local.py")
    if not os.path.exists(here):
        return []
    spec = importlib.util.spec_from_file_location("docs_lint_local", here)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return list(mod.checks(root, files))


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("paths", nargs="*")
    p.add_argument("--root", default=None, help="repo root (default: git top level)")
    p.add_argument("--forbid-dashes", action="store_true", help="report em and en dashes in prose")
    p.add_argument("--check-anchors", action="store_true", help="also check #anchors in links to .md files")
    a = p.parse_args(argv)
    try:
        root = a.root or subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True,
                                        text=True, check=True).stdout.strip()
    except (subprocess.CalledProcessError, FileNotFoundError):
        root = os.getcwd()
    files = sorted(set(md_files(a.paths or [root])))
    findings = [f for path in files for f in check_file(path, a.forbid_dashes, a.check_anchors)]
    findings += local_checks(root, files)
    for path, n, msg in findings:
        print(f"{os.path.relpath(path, root)}:{n}: {msg}")
    print(f"docs-lint: {len(files)} file(s), {len(findings)} problem(s)", file=sys.stderr)
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
