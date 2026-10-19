"""Klang's own docs rules, picked up by docs_lint.py (see .claude/skills/console-tools/SKILL.md).

Two rules, both from docs/tasks/in-progress/README.md ("what lives where", "a moved file takes every link
to it along"):

1. task-place: a task file under docs/tasks/ whose status line opens with a state that belongs elsewhere.
   "Status: DONE" (or closed, complete, finished, won't implement) belongs in docs/tasks-archive/<month>/;
   "Status: IN PROGRESS" (or active) belongs in docs/tasks/in-progress/; a file in in-progress/ whose
   status opens with "not started" belongs back in docs/tasks/. Only the opening word or two of the
   status value count, so "Next after the voice lifecycle (..., done)" is not read as done.

2. stale-path: a path to a task or plan written as text, not as a link, that does not resolve:
   `docs/tasks/foo.md`, `../tasks-archive/2026-09/2026...md`, `docs/plans/bar.md`. The generic check
   reads only Markdown link targets and skips inline code, where most of these mentions live. It
   reads the linted .md files and, on a whole-repo run (docs_lint.py with no paths, or with `.`), the
   KDoc and comments of every *.kt and *.kts file too: a move must fix those as well.
   Dated history keeps its words and is skipped: docs/tasks-archive/, docs/history/, files whose name
   starts with a date (2026-09-06-..., 20260927-...), */ref/memory-history.md, the build-lock log,
   DEV-DIARY.MD, the strategist's agent memory.
"""
import os
import re
import sys

TASKS = "docs/tasks/"
IN_PROGRESS = "docs/tasks/in-progress/"
INDEX_FILES = {"README.md", "NEXT-STEPS.md"}  # and every _name.md (index and register files)

STATUS_LINE = re.compile(r"^[>\s*-]*\**status\b[^:]*:\**\s*(.*)$", re.I)
DONE = re.compile(r"^(done|closed|complete|completed|finished|won'?t[ -]implement)\b", re.I)
ACTIVE = re.compile(r"^(in[ -]progress|active)\b", re.I)
NOT_STARTED = re.compile(r"^not[ -]started\b", re.I)

PATH_MENTION = re.compile(
    r"(?<![\w/.-])((?:\.\./)*(?:\./)?(?:docs/)?(?:tasks|tasks-archive|plans)/[\w./-]*?\.md)\b")
MD_LINK = re.compile(r"!?\[(?:[^\]\\]|\\.)*\]\([^)]*\)")
FENCE = re.compile(r"^\s{0,3}(```|~~~)")
HISTORY = re.compile(r"(^|/)(docs/tasks-archive|docs/history|\.claude/agent-memory)/"
                     r"|(^|/)(\d{4}-\d{2}-\d{2}|\d{8})-[^/]*$"
                     r"|/ref/memory-history\.md$|^\.claude/build-lock-log\.md$|^DEV-DIARY\.MD$")
CODE_EXT = (".kt", ".kts")
SKIP_DIRS = {".git", "node_modules", "build", ".gradle", ".idea", ".kotlin", "kotlin-js-store"}


def status_value(lines):
    """(line number, the text after 'Status:') of the first status line in the file's head, no bold marks."""
    for n, line in enumerate(lines[:60], 1):
        m = STATUS_LINE.match(line)
        if m:
            return n, re.sub(r"[*_`]", "", m.group(1)).strip(" .:")
    return None, None


def task_place(root, path, rel, lines):
    name = os.path.basename(rel)
    if not rel.startswith(TASKS) or name in INDEX_FILES or name.startswith("_"):
        return
    n, value = status_value(lines)
    if value is None:
        return
    if DONE.match(value):
        yield path, n, f"task-place: status '{value[:40]}' but the file is not in docs/tasks-archive/<month>/"
    elif ACTIVE.match(value) and not rel.startswith(IN_PROGRESS):
        yield path, n, f"task-place: status '{value[:40]}' but the file is not in {IN_PROGRESS}"
    elif NOT_STARTED.match(value) and rel.startswith(IN_PROGRESS):
        yield path, n, f"task-place: status '{value[:40]}' but the file is in {IN_PROGRESS}"


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
        for m in PATH_MENTION.finditer(line):
            if "..." in m.group(1):
                continue  # a placeholder (`docs/tasks/...md`), not a path
            if not resolves(root, path, m.group(1)):
                yield path, n, f"stale-path: {m.group(1)} does not exist (moved? see docs/tasks/in-progress/README.md)"


def code_files(root):
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
        yield from task_place(root, path, rel, lines)
        if not HISTORY.search(rel):
            yield from stale_paths(root, path, lines, markdown=True)
    if whole_repo_run(root):
        for path in code_files(root):
            yield from stale_paths(root, path, read_lines(path), markdown=False)
