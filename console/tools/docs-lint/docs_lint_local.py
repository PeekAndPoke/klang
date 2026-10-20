"""Klang's own docs rule, picked up by docs_lint.py (see .claude/skills/console-tools/SKILL.md).

Whether a task file sits in the folder its status names is `console/tools/ticket/ticket.py check`'s job since the
ticket numbers (2026-10-20); this file's task-place rule went with them. One rule is left:

stale-path: a path to a task or plan written as text, not as a link, that does not resolve:
   `docs/tasks/foo.md`, `../tasks-archive/2026-09/2026...md`, `docs/plans/bar.md`. The generic check
   reads only Markdown link targets and skips inline code, where most of these mentions live. It
   reads the linted .md files and, on a whole-repo run (docs_lint.py with no paths, or with `.`), the
   KDoc and comments of every *.kt and *.kts file too: a move must fix those as well.
   Dated history keeps its words and is skipped: docs/tasks-archive/, docs/history/, files whose name
   starts with a date (2026-09-06-..., 20260927-...), */ref/memory-history.md, the build-lock log,
   DEV-DIARY.MD, the strategist's agent memory, and what the ticket sweep of 2026-10-20 left alone: the
   ledgers, docs/benchmarks/, docs/blog/, docs/funding/evidence/ and src/jsMain/resources/. The migration's
   "(was `old path`)" records are skipped too.
"""
import os
import re
import subprocess
import sys

PATH_MENTION = re.compile(
    r"(?<![\w/.-])((?:\.\./)*(?:\./)?(?:docs/)?(?:tasks|tasks-archive|plans)/[\w./-]*?\.md)\b")
MD_LINK = re.compile(r"!?\[(?:[^\]\\]|\\.)*\]\([^)]*\)")
FENCE = re.compile(r"^\s{0,3}(```|~~~)")
HISTORY = re.compile(r"(^|/)(docs/tasks-archive|docs/history|\.claude/agent-memory)/"
                     r"|(^|/)(\d{4}-\d{2}-\d{2}|\d{8})-[^/]*$"
                     r"|/ref/memory-history\.md$|^\.claude/build-lock-log\.md$|^DEV-DIARY\.MD$"
                     # the files the ticket sweep left alone (2026-10-20): dated records and published pages
                     r"|ledger[^/]*\.md$|^docs/(benchmarks|blog|funding/evidence)/|^src/jsMain/resources/")
CODE_EXT = (".kt", ".kts")
WAS_RECORD = re.compile(r"\(was `[^`]+`\)")
SKIP_DIRS = {".git", "node_modules", "build", ".gradle", ".idea", ".kotlin", "kotlin-js-store"}


def resolves(root, path, mention):
    if mention.startswith("docs/"):
        return os.path.exists(os.path.join(root, mention))
    here = os.path.normpath(os.path.join(os.path.dirname(path), mention))
    return os.path.exists(here) or os.path.exists(os.path.join(root, "docs", mention.lstrip("./")))


def stale_paths(root, path, lines, markdown):
    fenced = False
    for n, line in enumerate(lines, 1):
        if markdown and FENCE.match(line):
            fenced = not fenced
            continue
        if fenced:
            continue
        if markdown:
            line = MD_LINK.sub("", line)  # link targets are the generic check's job
        line = WAS_RECORD.sub("", line)  # "(was `old path`)": the ticket migration's record, which ticket find reads
        for m in PATH_MENTION.finditer(line):
            if "..." in m.group(1):
                continue  # a placeholder (`docs/tasks/...md`), not a path
            if not resolves(root, path, m.group(1)):
                yield path, n, f"stale-path: {m.group(1)} does not exist (moved? see docs/tasks/in-progress/README.md)"


def code_files(root):
    """Every *.kt and *.kts file git does not ignore (the scratch copies in tmp/ once read as 1,500 stale paths);
    outside a git work tree, a walk."""
    r = subprocess.run(["git", "ls-files", "-co", "--exclude-standard", "-z"], cwd=root, capture_output=True, text=True)
    if r.returncode == 0:
        yield from (os.path.join(root, f) for f in r.stdout.split("\0")
                    if f.endswith(CODE_EXT) and os.path.isfile(os.path.join(root, f)))
        return
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS and not d.startswith(".")
                       and not os.path.exists(os.path.join(dirpath, d, ".git"))]
        yield from (os.path.join(dirpath, f) for f in filenames if f.endswith(CODE_EXT))


def read_lines(path):
    with open(path, encoding="utf-8", errors="replace") as f:
        return f.read().splitlines()


def whole_repo_run(root):
    """docs_lint.py was given no paths, or the repo root itself (the hook cannot see its arguments)."""
    args, paths = sys.argv[1:], []
    while args:
        arg = args.pop(0)
        if arg == "--root":
            args = args[1:]
        elif not arg.startswith("-"):
            paths.append(arg)
    return not paths or any(os.path.abspath(p) == root for p in paths)


def checks(root, files):
    root = os.path.abspath(root)
    for path in files:
        rel = os.path.relpath(path, root).replace(os.sep, "/")
        lines = read_lines(path)
        if not HISTORY.search(rel):
            yield from stale_paths(root, path, lines, markdown=True)
    if whole_repo_run(root):
        for path in code_files(root):
            yield from stale_paths(root, path, read_lines(path), markdown=False)
