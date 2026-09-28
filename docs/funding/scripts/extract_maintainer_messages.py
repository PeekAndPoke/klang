#!/usr/bin/env python3
# Extracts the maintainer's own words from the local Claude Code transcripts.
#
# Output (written next to this script, in ../evidence/):
#   maintainer-messages.txt  every typed message, deduplicated, oldest first
#   maintainer-answers.md    every answer to a multiple-choice question (AskUserQuestion)
#
# What counts as "the maintainer's words":
#   - user entries that are not tool results, not meta, not sidechain (sub-agent) traffic
#   - no /compact prompts (often drafted by the agent), no command caveats, no task notifications
#   - messages over 2600 chars are truncated and marked: they are almost always pastes
#     (HTML, logs, another agent's summary), not the maintainer's words
#
# Timestamps are UTC, as stored in the transcripts.
#
# Usage: python3 extract_maintainer_messages.py [transcript_dir] [exclude_session_id ...]

import glob
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(HERE, "..", "evidence")
SRC = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser("~/.claude/projects/-opt-dev-peekandpoke-klang")
EXCLUDE = set(sys.argv[2:])
PASTE_LIMIT = 2600


def load_messages():
    out = []
    for f in sorted(glob.glob(os.path.join(SRC, "*.jsonl"))):
        sid = os.path.basename(f)[:-6]
        if sid in EXCLUDE:
            continue
        with open(f) as fh:
            for line in fh:
                try:
                    d = json.loads(line)
                except ValueError:
                    continue
                if d.get("type") != "user" or d.get("isMeta") or d.get("isSidechain"):
                    continue
                if d.get("toolUseResult") is not None:
                    continue
                if d.get("isCompactSummary") or d.get("isVisibleInTranscriptOnly"):
                    continue
                c = d.get("message", {}).get("content")
                if isinstance(c, list):
                    if any(isinstance(x, dict) and x.get("type") == "tool_result" for x in c):
                        continue
                    txt = "\n".join(x.get("text", "") for x in c if isinstance(x, dict) and x.get("type") == "text")
                else:
                    txt = c or ""
                t = txt.strip()
                if not t:
                    continue
                if t.startswith(("<local-command", "<command-name>", "<task-notification", "Caveat:", "/compact")):
                    continue
                if "This session is being continued from a previous conversation" in t[:200]:
                    continue
                if t.startswith("<system-reminder>") and t.endswith("</system-reminder>"):
                    continue
                out.append((d.get("timestamp", ""), sid, t))
    out.sort()
    seen = set()
    dedup = []
    for ts, sid, t in out:
        key = (ts, t[:200])
        if key in seen:
            continue
        seen.add(key)
        dedup.append((ts, sid, t))
    return dedup


def load_answers():
    out = []
    seen = set()
    for f in sorted(glob.glob(os.path.join(SRC, "*.jsonl"))):
        sid = os.path.basename(f)[:-6]
        if sid in EXCLUDE:
            continue
        with open(f) as fh:
            for line in fh:
                if '"answers"' not in line:
                    continue
                try:
                    d = json.loads(line)
                except ValueError:
                    continue
                r = d.get("toolUseResult")
                if not isinstance(r, dict) or "answers" not in r:
                    continue
                key = (d.get("timestamp"), json.dumps(r.get("answers"))[:200])
                if key in seen:
                    continue
                seen.add(key)
                out.append((d.get("timestamp", ""), sid, r))
    out.sort(key=lambda x: x[0])
    return out


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    msgs = load_messages()
    with open(os.path.join(OUT_DIR, "maintainer-messages.txt"), "w") as w:
        w.write("# The maintainer's typed messages, extracted from local Claude Code transcripts.\n")
        w.write("# Format: === <UTC timestamp> | <session id>, then the message verbatim.\n")
        w.write("# Messages over %d chars are truncated: they are pastes, not the maintainer's words.\n\n" % PASTE_LIMIT)
        for ts, sid, t in msgs:
            if len(t) > PASTE_LIMIT:
                t = t[:1200] + "\n[... TRUNCATED, %d chars total, likely paste ...]" % len(t)
            w.write("=== %s | %s\n%s\n\n" % (ts[:19], sid, t))
    answers = load_answers()
    with open(os.path.join(OUT_DIR, "maintainer-answers.md"), "w") as w:
        w.write("# Maintainer answers to multiple-choice questions\n\n")
        w.write("Extracted from AskUserQuestion results in the local transcripts. `Q` is the agent's question\n")
        w.write("(agent text, kept verbatim, including its punctuation). `A` is the option the maintainer picked\n")
        w.write("or the free text they typed. `(Recommended)` marks the option the agent recommended. Timestamps UTC.\n\n")
        for ts, sid, r in answers:
            w.write("## %s | %s\n\n" % (ts[:19], sid))
            for q, a in (r.get("answers") or {}).items():
                w.write("- **Q:** %s\n  - **A:** %s\n" % (q.replace("\n", " "), str(a).replace("\n", " ")))
            for q, v in (r.get("annotations") or {}).items():
                if isinstance(v, dict) and v.get("notes"):
                    w.write("  - **Notes:** %s\n" % v["notes"].replace("\n", " "))
            w.write("\n")
    print("messages: %d, answers: %d" % (len(msgs), len(answers)))


if __name__ == "__main__":
    main()
