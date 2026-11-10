#!/usr/bin/env python3
"""ui-wiring: lists how state moves through the UI code, file by file (KLANG-00421).

It reads Kotlin source as text (no build, no lock) and reports, for the UI's source roots:

- the HOLDERS: every global place that keeps state (a top-level `object`, a class's `companion object`, a public
  top-level `val` in the app), with what it keeps: stream sources, `var`s, mutable collections, browser storage;
- the CABLES: for every file, which holders it touches, each as `stream` (the reference sits in a
  `subscribingTo(...)`, `subscribeToStream`, `.subscribe` or `combinedWith` line) or `direct` (any other reference:
  a call, a read of the current value, a write), and which member of the holder it names;
- the FINDINGS: state kept outside the Spine, browser storage touched outside the storage rails, hand-written JSON,
  component-to-component references, raw DOM listeners, storage objects reached from the UI.

Usage:
    python3 -I console/tools/ui-wiring/ui_wiring.py [--root <tree>] [--out <dir>] [--file <path>]

    --out <dir>   also write `report.md` and `graph.json` there (the graph: files, holders, edges)
    --file <path> print what the tool sees in one file, for a hand check

What it cannot see: a stream handed on through a prop or a constructor (it shows as `props` or `local`), a holder
reached through an alias, and whether a `direct` reference reads or writes. Those are traced by hand.

The RAILS (which layer a file belongs to) and the ROOTS are this repo's own and sit at the top of the file.
"""

import argparse
import json
import os
import re
import sys
from collections import defaultdict

# The source roots read, relative to the repo root.
ROOTS = [
    "src/jsMain/kotlin",
    "src/commonMain/kotlin",
    "klangui/src/jsMain/kotlin",
    "klangscript-ui/src/jsMain/kotlin",
    "sprudel/src/jsMain/kotlin",
    "workshop/src/jsMain/kotlin",
]

# A file's rail: the first prefix that matches its path. The order of RAIL_ORDER is the cabinet's, bottom to top.
RAILS = [
    ("src/jsMain/kotlin/spine/storage/", "storage"),
    ("src/jsMain/kotlin/storage/", "storage"),
    ("src/commonMain/kotlin/storage/", "storage"),
    ("src/jsMain/kotlin/spine/", "spine"),
    ("src/jsMain/kotlin/layouts/", "layout"),
    ("src/jsMain/kotlin/pages/", "pages"),
    ("src/jsMain/kotlin/midi/", "pages"),
    ("src/jsMain/kotlin/visuals/", "pages"),
    ("src/jsMain/kotlin/comp/", "components"),
    ("src/jsMain/kotlin/codemirror/", "components"),
    ("src/jsMain/kotlin/", "app"),
    ("src/commonMain/kotlin/", "content"),
    ("klangui/", "library"),
    ("klangscript-ui/", "library"),
    ("sprudel/", "library"),
    ("workshop/", "library"),
]
RAIL_ORDER = ["storage", "spine", "app", "layout", "pages", "components", "library", "content"]

# Where state may live, and where browser storage may be touched, without a finding.
STATE_RAILS = {"spine", "storage"}
STORAGE_RAILS = {"storage"}

# Only the app's own top-level vals count as holders (the content's are songs and tutorials).
VAL_HOLDER_PREFIX = "src/jsMain/kotlin/"

# What a holder under the key's prefix can be seen from: the app's own code is seen only by the app.
SEEN_ONLY_FROM = {"src/jsMain/kotlin/": "src/jsMain/kotlin/"}

STREAM_LINE = re.compile(r"subscribingTo\s*\(|subscribeToStream|\.subscribe\b|combinedWith\s*\(")
# (name, mark, own level only): a var or a collection inside a function is that function's, not the holder's state
STATE_MARKS = [
    ("stream source", re.compile(r"\bStreamSource\s*[<(]"), False),
    ("var", re.compile(r"^\s*(?:private |internal |protected |public |override |lateinit )*var\s+\w+", re.M), True),
    ("mutable collection",
     re.compile(r"\bmutable(?:List|Map|Set)Of\b|\bArrayDeque\s*[<(]|\bHashMap\s*[<(]"), True),
    ("browser storage", re.compile(r"\b(?:localStorage|sessionStorage|persistInLocalStorage)\b"), False),
    ("derived stream", re.compile(r":\s*Stream\s*<"), True),
]
BROWSER_STORAGE = re.compile(r"\b(?:localStorage|sessionStorage|persistInLocalStorage)\b")
MUTABLE_DECL = re.compile(r"\b(?:val|var)\s+(\w+)\s*(?::[^=\n]+)?=\s*(?:mutable(?:List|Map|Set)Of|ArrayDeque|HashMap)\b")
MANUAL_JSON = re.compile(
    r"\bJSON\.(?:parse|stringify)\b|\bbuildJsonObject\b|\bbuildJsonArray\b|\bJsonObject\s*\(|\bJsonArray\s*\(|"
    r"\bJsonPrimitive\s*\(|\.jsonObject\b|\.jsonArray\b|\.jsonPrimitive\b"
)
SERIALIZABLE = re.compile(r"@Serializable\b")
COMPONENT_REF = re.compile(r"\bComponentRef\b")
DOM_EVENT = re.compile(r"\b(?:window|document)\s*\.\s*(?:addEventListener|dispatchEvent)\b")
SUBSCRIBING = re.compile(r"subscribingTo\s*\(\s*([A-Za-z_][\w]*)")
BY_VALUE = re.compile(r"\bby\s+value\s*\(")
STREAM_SOURCE_DECL = re.compile(r"\b(?:val|var)\s+(\w+)\s*(?::[^=\n]+)?=\s*StreamSource\s*[<(]")
DERIVED_STREAM_DECL = re.compile(r"\b(?:val|var)\s+(\w+)\s*:\s*Stream\s*<")
COMPONENT_CLASS = re.compile(r"\bclass\s+\w+[^{]*:\s*[^{]*\b(?:Pure)?Component\s*[<(]")
TOP_OBJECT = re.compile(r"^(?:(?:internal|public|private|data)\s+)*object\s+(\w+)", re.M)
TOP_CLASS = re.compile(r"^(?:(?:internal|public|data|abstract|open|sealed|enum|value|private)\s+)*class\s+(\w+)", re.M)
TOP_VAL = re.compile(r"^(?:(?:internal|public)\s+)?(?:val|var)\s+(\w+)\b", re.M)
COMPANION = re.compile(r"\bcompanion\s+object\b[^{]*\{")
MEMBER_DECL = re.compile(r"^\s*(?:private |internal |protected |public |override |lateinit |const )*(val|var|fun)\s+"
                         r"(?:<[^>]+>\s*)?(?:[\w.<>?, ]+\.)?(\w+)", re.M)


CHAR_LITERAL = re.compile(r"'(?:\\u[0-9a-fA-F]{4}|\\.|[^'\\\n])'")


def template_end(text, i):
    """The index after the brace that closes the ${ whose expression starts at [i]."""
    depth = 1
    n = len(text)

    while i < n:
        ch = text[i]

        if ch == '"':
            i = string_end(text, i)
            continue

        if ch == "'" and CHAR_LITERAL.match(text, i):
            i = CHAR_LITERAL.match(text, i).end()
            continue

        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1

            if depth == 0:
                return i + 1

        i += 1

    return n


def string_end(text, i):
    """The index after the string literal that opens at [i]; a ${...} in it may hold braces and strings of its own."""
    n = len(text)
    triple = text.startswith('"""', i)
    j = i + (3 if triple else 1)

    while j < n:
        if text.startswith("${", j):
            j = template_end(text, j + 2)
        elif triple and text.startswith('"""', j):
            return j + 3
        elif not triple and text[j] == "\\":
            j += 2
        elif not triple and text[j] == '"':
            return j + 1
        elif not triple and text[j] == "\n":
            return j
        else:
            j += 1

    return n


def strip_comments(text):
    """The text without comments, line breaks kept; strings (triple-quoted too) are left as they are."""
    out = []
    i, n = 0, len(text)

    while i < n:
        if text[i] == '"':
            end = string_end(text, i)
            out.append(text[i:end])
            i = end
        elif text[i] == "'" and CHAR_LITERAL.match(text, i):
            end = CHAR_LITERAL.match(text, i).end()
            out.append(text[i:end])
            i = end
        elif text.startswith("//", i):
            end = text.find("\n", i)
            i = n if end < 0 else end
        elif text.startswith("/*", i):
            end = text.find("*/", i + 2)
            end = n if end < 0 else end + 2
            out.append("\n" * text.count("\n", i, end))
            i = end
        else:
            out.append(text[i])
            i += 1

    return "".join(out)


def blank_literal(inner):
    """[inner] blanked, its line breaks kept, and the expression of each ${...} kept as code (its own strings blanked)."""
    out = []
    i, n = 0, len(inner)

    while i < n:
        if inner.startswith("${", i):
            end = template_end(inner, i + 2)
            out.append("  " + blank_strings(inner[i + 2:end - 1]) + " ")
            i = end
        else:
            out.append("\n" if inner[i] == "\n" else " ")
            i += 1

    return "".join(out)


def blank_strings(text):
    """The text with the inside of every string literal blanked, so a word in a string is not a reference."""
    out = []
    i, n = 0, len(text)

    while i < n:
        if text[i] == '"':
            end = string_end(text, i)
            quote = 3 if text.startswith('"""', i) else 1
            closed = end - quote >= i + quote and text.endswith('"' * quote, i, end)
            inner = text[i + quote:end - quote] if closed else text[i + quote:end]
            out.append('"' + blank_literal(inner) + '"')
            i = end
        elif text[i] == "'" and CHAR_LITERAL.match(text, i):
            end = CHAR_LITERAL.match(text, i).end()
            out.append("'" + " " * (end - i - 2) + "'")
            i = end
        else:
            out.append(text[i])
            i += 1

    return "".join(out)


def block_at(text, open_brace):
    """The text between the brace at [open_brace] and its partner."""
    depth = 0

    for i in range(open_brace, len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1

            if depth == 0:
                return text[open_brace + 1:i]

    return text[open_brace + 1:]


def body_after(text, start):
    """The braces block that starts the declaration at [start], or '' when it has none before the next blank line."""
    brace = text.find("{", start)
    stop = text.find("\n\n", start)

    if brace < 0 or (0 <= stop < brace):
        return ""

    return block_at(text, brace)


def own_level(body):
    """[body] without what sits inside its nested braces: the declarations of the object itself."""
    out = []
    depth = 0

    for ch in body:
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
        elif depth == 0 or ch == "\n":
            out.append(ch)

    return "".join(out)


def state_kinds(body):
    own = own_level(body)

    return [name for name, mark, own_only in STATE_MARKS if mark.search(own if own_only else body)]


def state_members(body):
    """Names declared in [body] that keep state: a stream source, or a var."""
    own = own_level(body)
    names = set(STREAM_SOURCE_DECL.findall(own))
    names.update(DERIVED_STREAM_DECL.findall(own))
    names.update(MUTABLE_DECL.findall(own))

    for kind, name in MEMBER_DECL.findall(own):
        if kind == "var":
            names.add(name)

    return names


def rail_of(rel):
    for prefix, rail in RAILS:
        if rel.startswith(prefix):
            return rail

    return "other"


def read_tree(root):
    """{rel path: code without comments, strings blanked} for every Kotlin file under the roots."""
    files = {}

    for src in ROOTS:
        base = os.path.join(root, src)

        for folder, _, names in os.walk(base):
            for name in sorted(names):
                if name.endswith(".kt"):
                    path = os.path.join(folder, name)
                    rel = os.path.relpath(path, root).replace(os.sep, "/")

                    with open(path, encoding="utf-8") as fh:
                        files[rel] = blank_strings(strip_comments(fh.read()))

    return files


def find_holders(files):
    """{name: holder}: the global places that keep state. A holder: file, rail, kind, keeps, members (or None: any)."""
    holders = {}

    for rel, code in files.items():
        rail = rail_of(rel)

        for m in TOP_OBJECT.finditer(code):
            body = body_after(code, m.end())
            keeps = state_kinds(body)

            if keeps:
                holders[m.group(1)] = {"file": rel, "rail": rail, "kind": "object", "keeps": keeps, "members": None}

        for m in TOP_CLASS.finditer(code):
            body = body_after(code, m.end())
            comp = COMPANION.search(body)

            if comp is None:
                continue

            members = state_members(block_at(body, comp.end() - 1))

            if members and m.group(1) not in holders:
                holders[m.group(1)] = {
                    "file": rel, "rail": rail, "kind": "companion",
                    "keeps": state_kinds(block_at(body, comp.end() - 1)), "members": sorted(members),
                }

        if rel.startswith(VAL_HOLDER_PREFIX):
            for m in TOP_VAL.finditer(code):
                line = code[m.start():code.find("\n", m.start())]

                if "private" in line.split(m.group(1))[0] or "const " in line:
                    continue

                holders.setdefault(m.group(1), {
                    "file": rel, "rail": rail, "kind": "val", "keeps": ["top-level value"], "members": None,
                })

    return holders


def references(code, name, holder):
    """[(line number, member, 'stream' or 'direct')] for every reference to the holder [name] in [code]."""
    if holder["kind"] == "val":
        pattern = re.compile(r"(?<![\w.])" + re.escape(name) + r"\b(?:\s*\.\s*(\w+))?(?=\s*[.),{\n(]|\s*$)", re.M)
    elif holder["kind"] == "companion":
        members = "|".join(re.escape(x) for x in holder["members"])
        pattern = re.compile(r"(?<![\w.])" + re.escape(name) + r"\s*\.\s*(" + members + r")\b")
    else:
        pattern = re.compile(r"(?<![\w.])" + re.escape(name) + r"\b(?:\s*\.\s*(\w+))?")

    lines = code.split("\n")
    starts = [0]

    for line in lines:
        starts.append(starts[-1] + len(line) + 1)

    found = []
    row = 0

    for m in pattern.finditer(code):
        while starts[row + 1] <= m.start():
            row += 1

        line = lines[row]

        if re.match(r"\s*(?:import|package)\b", line):
            continue

        if re.search(r"\b(?:object|class|val|var|fun)\s+" + re.escape(name) + r"\b", line):
            continue

        # A stream cable: the reference's own line subscribes, or the line before opens a subscribingTo( it sits in
        before = lines[row - 1] if row > 0 else ""
        stream = bool(STREAM_LINE.search(line)) or bool(re.search(r"subscribingTo\s*\(\s*$", before))
        found.append((row + 1, m.group(1) or "", "stream" if stream else "direct"))

    return found


def file_facts(rel, code, holders):
    """What one file does with state: the numbers and names the report is made of. An import is not a use."""
    code = re.sub(r"^\s*import\b[^\n]*", "", code, flags=re.M)
    roots = defaultdict(int)

    for root in SUBSCRIBING.findall(code):
        if root == "props":
            roots["props"] += 1
        elif root in holders:
            roots["holder"] += 1
        elif root == "router":
            roots["router"] += 1
        else:
            roots["local"] += 1

    return {
        "rail": rail_of(rel),
        "component": bool(COMPONENT_CLASS.search(code)),
        "subscribing": dict(roots),
        "by_value": len(BY_VALUE.findall(code)),
        "stream_sources": STREAM_SOURCE_DECL.findall(code),
        "stream_source_uses": len(re.findall(r"\bStreamSource\s*[<(]", code)),
        "browser_storage": len(BROWSER_STORAGE.findall(code)),
        "manual_json": len(MANUAL_JSON.findall(code)),
        "serializable": len(SERIALIZABLE.findall(code)),
        "component_ref": len(COMPONENT_REF.findall(code)),
        "dom_events": len(DOM_EVENT.findall(code)),
    }


def can_see(reader, holder_file):
    """Whether the module of [reader] can name a holder kept in [holder_file]."""
    for prefix, only_from in SEEN_ONLY_FROM.items():
        if holder_file.startswith(prefix):
            return reader.startswith(only_from)

    return True


def analyse(root):
    files = read_tree(root)
    holders = find_holders(files)
    facts = {rel: file_facts(rel, code, holders) for rel, code in files.items()}
    edges = []

    for rel, code in files.items():
        for name, holder in holders.items():
            if holder["file"] == rel and holder["kind"] != "companion":
                continue

            if name not in code or not can_see(rel, holder["file"]):
                continue

            # A top-level value's name is an everyday word (version): a file with a name of its own is not a reader
            if holder["kind"] == "val" and re.search(
                    r"\b(?:val|var)\s+" + re.escape(name) + r"\b|\b" + re.escape(name) + r"\s*:", code):
                continue

            for line, member, kind in references(code, name, holder):
                edges.append({"from": rel, "to": name, "member": member, "kind": kind, "line": line})

    return files, holders, facts, edges


def findings(holders, facts, edges):
    """The lists a person reads: each entry (path, what)."""
    out = defaultdict(list)

    for name, h in sorted(holders.items()):
        if h["rail"] not in STATE_RAILS:
            out["state held outside the Spine"].append((h["file"], f"{name} ({h['kind']}): {', '.join(h['keeps'])}"))

    for rel, f in sorted(facts.items()):
        if f["stream_source_uses"] and f["rail"] not in STATE_RAILS:
            names = ", ".join(f["stream_sources"]) or "unnamed"
            out["stream sources made outside the Spine"].append((rel, f"{f['stream_source_uses']}: {names}"))

        if f["browser_storage"] and f["rail"] not in STORAGE_RAILS:
            out["browser storage touched outside the storage rail"].append((rel, f"{f['browser_storage']} references"))

        if f["manual_json"]:
            out["hand-written JSON"].append((rel, f"{f['manual_json']} references"))

        if f["component_ref"]:
            out["component references (a parent's own child, or lateral: read which)"].append((rel, f"{f['component_ref']} references"))

        if f["dom_events"]:
            out["raw DOM listeners"].append((rel, f"{f['dom_events']} references"))

    reached = defaultdict(set)

    for e in edges:
        h = holders[e["to"]]
        from_rail = facts[e["from"]]["rail"]

        if h["rail"] == "storage" and from_rail not in STATE_RAILS and from_rail != "app":
            reached[(e["from"], e["to"])].add(e["kind"])

    for (rel, name), kinds in sorted(reached.items()):
        out["storage reached from the UI"].append((rel, f"{name} ({', '.join(sorted(kinds))})"))

    return out


def rail_matrix(holders, facts, edges):
    """{(from rail, to rail): {'stream': files, 'direct': files}} counted in file-to-holder pairs."""
    pairs = defaultdict(lambda: {"stream": set(), "direct": set()})

    for e in edges:
        key = (facts[e["from"]]["rail"], holders[e["to"]]["rail"])
        pairs[key][e["kind"]].add((e["from"], e["to"]))

    return {key: {kind: len(v) for kind, v in kinds.items()} for key, kinds in pairs.items()}


def render(holders, facts, edges):
    lines = ["# ui-wiring", ""]
    by_rail = defaultdict(list)

    for rel, f in facts.items():
        by_rail[f["rail"]].append(f)

    lines += ["## The rails", "", "| Rail | Files | Components | subscribingTo | by value | Stream sources | Storage refs |",
              "|---|---|---|---|---|---|---|"]

    for rail in RAIL_ORDER + sorted(set(by_rail) - set(RAIL_ORDER)):
        fs = by_rail.get(rail, [])

        if not fs:
            continue

        lines.append("| {} | {} | {} | {} | {} | {} | {} |".format(
            rail, len(fs), sum(f["component"] for f in fs),
            sum(sum(f["subscribing"].values()) for f in fs), sum(f["by_value"] for f in fs),
            sum(f["stream_source_uses"] for f in fs), sum(f["browser_storage"] for f in fs)))

    totals = defaultdict(int)

    for f in facts.values():
        for key, count in f["subscribing"].items():
            totals[key] += count

    lines += ["", "What a `subscribingTo` reads: " + ", ".join(f"{k} {v}" for k, v in sorted(totals.items())) + ".", ""]

    lines += ["## The holders", "", "| Holder | Rail | Kind | Keeps | Read as a stream by | Reached directly by | File |",
              "|---|---|---|---|---|---|---|"]
    per = defaultdict(lambda: {"stream": set(), "direct": set()})

    for e in edges:
        per[e["to"]][e["kind"]].add(e["from"])

    def order(item):
        name, h = item
        return (RAIL_ORDER.index(h["rail"]) if h["rail"] in RAIL_ORDER else 99, name)

    for name, h in sorted(holders.items(), key=order):
        lines.append("| `{}` | {} | {} | {} | {} | {} | `{}` |".format(
            name, h["rail"], h["kind"], ", ".join(h["keeps"]),
            len(per[name]["stream"]), len(per[name]["direct"]), h["file"]))

    lines += ["", "## Rail to rail (file-to-holder pairs)", "", "| From | To | Stream | Direct |", "|---|---|---|---|"]

    for (src, dst), kinds in sorted(rail_matrix(holders, facts, edges).items()):
        lines.append(f"| {src} | {dst} | {kinds['stream']} | {kinds['direct']} |")

    lines += ["", "## Findings", ""]

    for title, entries in findings(holders, facts, edges).items():
        lines += [f"### {title} ({len(entries)})", ""]
        lines += [f"- `{rel}`: {what}" for rel, what in entries]
        lines.append("")

    lines += ["## Every holder's members, and who names them", ""]
    members = defaultdict(lambda: defaultdict(lambda: defaultdict(set)))

    for e in edges:
        members[e["to"]][e["member"] or "(itself)"][e["kind"]].add(e["from"])

    for name, h in sorted(holders.items(), key=order):
        if not members[name]:
            continue

        lines += [f"### `{name}` ({h['rail']})", ""]

        for member, kinds in sorted(members[name].items()):
            parts = [f"{kind} from {len(fs)}: " + ", ".join(sorted(os.path.basename(x)[:-3] for x in fs))
                     for kind, fs in sorted(kinds.items())]
            lines.append(f"- `{member}`: " + "; ".join(parts))

        lines.append("")

    return "\n".join(lines) + "\n"


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--root", default=".")
    parser.add_argument("--out")
    parser.add_argument("--file")
    args = parser.parse_args(argv)

    files, holders, facts, edges = analyse(args.root)

    if args.file:
        rel = args.file.replace(os.sep, "/")

        if rel not in facts:
            print(f"ui-wiring: {rel} is not under the roots", file=sys.stderr)
            return 2

        print(json.dumps(facts[rel], indent=2))

        for e in edges:
            if e["from"] == rel:
                name = e["to"] + ("." + e["member"] if e["member"] else "")
                print(f"  line {e['line']}: {name} ({e['kind']})")

        return 0

    report = render(holders, facts, edges)

    if args.out:
        os.makedirs(args.out, exist_ok=True)

        with open(os.path.join(args.out, "report.md"), "w", encoding="utf-8") as fh:
            fh.write(report)

        with open(os.path.join(args.out, "graph.json"), "w", encoding="utf-8") as fh:
            json.dump({"holders": holders, "files": facts, "edges": edges}, fh, indent=1, sort_keys=True)

    found = findings(holders, facts, edges)
    print(f"ui-wiring: {len(facts)} files, {len(holders)} holders, {len(edges)} references")

    for title, entries in found.items():
        print(f"  {title}: {len(entries)}")

    if args.out:
        print(f"  written: {os.path.join(args.out, 'report.md')}, graph.json")
    else:
        print("  (give --out <dir> for the full report)")

    return 0


if __name__ == "__main__":
    sys.exit(main())
