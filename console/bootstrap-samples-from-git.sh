#!/bin/bash
# From-scratch rebuild of the sample mirror via git clones — the disaster-recovery path.
#
# Rebuilds ../../peekandpoke.github.io/klang/{uzu-drumkit,tidal-drum-machines,dirt-samples,
# vcsl,mridangam,piano} from the upstream git repos. Uses git transfers only (no
# raw.githubusercontent.com requests, so no 429 rate limits): manifests are taken from the
# clones themselves, rewritten (drop "_base", drop excluded banks), and every referenced
# file is copied over. VCSL uses a blob-filtered sparse checkout (~2.5 GB of 3.8 GB repo).
#
# The GM soundfont (felixroos/gm) is NOT rebuilt here — it is committed in the
# peekandpoke.github.io repo itself (originally produced by sprudel/jsbridge/extract-soundfonts.mjs).
#
# Day-to-day refresh + verification: ./gradlew runSampleMirror (SampleMirrorMain.kt) —
# keep its excludedKeys/alias table in sync with this script.
#
# NOTE: upstream drifts. A rebuild reflects upstream's CURRENT state, not necessarily the
# mirror you had — keep a copy of the deployed mirror (server + offline backup).
# Known drift handled below: some VCSL manifest entries still use the pre-reorganisation
# path (missing the "Membranophones/" prefix); we fall back to the new location.
set -e

WORK="${1:-/tmp/klang-sample-bootstrap}"
MIRROR="$(cd "$(dirname "$0")/../../peekandpoke.github.io/klang" && pwd)"

mkdir -p "$WORK"
cd "$WORK"

clone_shallow() {
    local url="$1" dir="$2"
    if [ -d "$dir/.git" ]; then
        echo "=== $dir already cloned, skipping (rm -rf $WORK/$dir to re-clone)"
    else
        echo "=== cloning $url"
        git clone --depth 1 "$url" "$dir"
    fi
}

clone_shallow https://github.com/tidalcycles/uzu-drumkit uzu-drumkit
clone_shallow https://github.com/ritchse/tidal-drum-machines tidal-drum-machines
clone_shallow https://github.com/tidalcycles/Dirt-Samples Dirt-Samples
clone_shallow https://github.com/yaxu/mrid mrid
clone_shallow https://github.com/felixroos/dough-samples dough-samples

echo "=== rewriting manifests + alias"
export WORK_DIR="$WORK" MIRROR_DIR="$MIRROR"
python3 - <<'EOF'
import json, os

WORK = os.environ["WORK_DIR"]
MIRROR = os.environ["MIRROR_DIR"]

# set dir -> manifest file inside the clones
MANIFESTS = {
    "uzu-drumkit": "uzu-drumkit/strudel.json",
    "tidal-drum-machines": "dough-samples/tidal-drum-machines.json",
    "dirt-samples": "dough-samples/Dirt-Samples.json",
    "vcsl": "dough-samples/vcsl.json",
    "mridangam": "dough-samples/mridangam.json",
    "piano": "dough-samples/piano.json",
}

# Keep in sync with SampleMirror.excludedKeys (uzu "brk" = Amen-break derivative)
EXCLUDED = {"uzu-drumkit": {"brk"}}

# Keep in sync with SampleMirror.drumMachineAliases
ALIASES = {
    "AJKPercusyn": "Percysyn", "AkaiLinn": "Linn", "AkaiMPC60": "MPC60",
    "AkaiXR10": "XR10", "AlesisHR16": "HR16", "AlesisSR16": "SR16",
    "BossDR110": "DR110", "BossDR220": "DR220", "BossDR55": "DR55",
    "BossDR550": "DR550", "CasioRZ1": "RZ1", "CasioSK1": "SK1",
    "CasioVL1": "VL1", "DoepferMS404": "MS404", "EmuDrumulator": "Drumulator",
    "EmuSP12": "SP12", "KorgDDM110": "DDM110", "KorgKPR77": "KPR77",
    "KorgKR55": "KR55", "KorgKRZ": "KRZ", "KorgM1": "M1",
    "KorgMinipops": "Minipops", "KorgPoly800": "Poly800", "KorgT3": "T3",
    "Linn9000": "9000", "LinnLM1": "LM1", "LinnLM2": "LM2",
    "MoogConcertMateMG1": "ConcertMateMG1", "OberheimDMX": "DMX",
    "RhodesPolaris": "Polaris", "RhythmAce": "Ace",
    "RolandCompurhythm1000": "Compurhythm1000", "RolandCompurhythm78": "Compurhythm78",
    "RolandCompurhythm8000": "Compurhythm8000", "RolandD110": "D110",
    "RolandD70": "D70", "RolandDDR30": "DDR30", "RolandJD990": "JD990",
    "RolandMC202": "MC202", "RolandMC303": "MC303", "RolandMT32": "MT32",
    "RolandR8": "R8", "RolandS50": "S50", "RolandSH09": "SH09",
    "RolandSystem100": "System100", "RolandTR505": "TR505", "RolandTR606": "TR606",
    "RolandTR626": "TR626", "RolandTR707": "TR707", "RolandTR727": "TR727",
    "RolandTR808": "TR808", "RolandTR909": "TR909", "SakataDPM48": "DPM48",
    "SequentialCircuitsDrumtracks": "CircuitsDrumtracks", "SequentialCircuitsTom": "CircuitsTom",
    "SergeModular": "Serge", "SimmonsSDS400": "SDS400", "SimmonsSDS5": "SDS5",
    "SoundmastersR88": "R88", "UnivoxMicroRhythmer12": "MicroRhythmer12",
    "ViscoSpaceDrum": "SpaceDrum", "XdrumLM8953": "LM8953", "YamahaRM50": "RM50",
    "YamahaRX21": "RX21", "YamahaRX5": "RX5", "YamahaRY30": "RY30", "YamahaTG33": "TG33",
}

def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=4, ensure_ascii=False)

for set_name, rel in MANIFESTS.items():
    obj = json.load(open(os.path.join(WORK, rel)))
    excluded = EXCLUDED.get(set_name, set())
    rewritten = {k: v for k, v in obj.items() if k != "_base" and k not in excluded}
    write(os.path.join(MIRROR, set_name, "index.json"), rewritten)
    print(f"[{set_name}] manifest: {len(rewritten)} sounds" +
          (f" ({len(obj) - 1 - len(rewritten)} excluded)" if excluded else ""))

write(os.path.join(MIRROR, "tidal-drum-machines", "alias.json"), ALIASES)
print(f"[tidal-drum-machines] alias.json: {len(ALIASES)} aliases")
EOF

# VCSL: blob-filtered sparse checkout of exactly the referenced files
# (patterns include the "Membranophones/" drift fallback locations)
if [ -d VCSL/.git ]; then
    echo "=== VCSL already cloned, skipping (rm -rf $WORK/VCSL to re-clone)"
else
    echo "=== cloning VCSL (sparse, blob-filtered)"
    git clone --depth 1 --filter=blob:none --no-checkout https://github.com/sgossner/VCSL VCSL
    python3 - <<'EOF' > VCSL/.sparse-patterns
import json, os, urllib.parse
obj = json.load(open(os.path.join(os.environ["MIRROR_DIR"], "vcsl", "index.json")))
for v in obj.values():
    for e in (v if isinstance(v, list) else list(v.values())):
        if isinstance(e, str):
            rel = urllib.parse.unquote(e.lstrip("/"))
            print("/" + rel)
            print("/Membranophones/" + rel)  # drift fallback
EOF
    (
        cd VCSL
        git sparse-checkout set --no-cone --stdin < .sparse-patterns
        git checkout
    )
fi

echo "=== copying referenced files into the mirror tree"
python3 - <<'EOF'
import json, os, shutil, sys, urllib.parse

WORK = os.environ["WORK_DIR"]
MIRROR = os.environ["MIRROR_DIR"]

# set dir in mirror -> subdir inside the clone that the manifest "_base" pointed at
MAPPING = {
    "uzu-drumkit": "uzu-drumkit",
    "tidal-drum-machines": "tidal-drum-machines/machines",
    "dirt-samples": "Dirt-Samples",
    "vcsl": "VCSL",
    "mridangam": "mrid",
    "piano": "dough-samples/piano",
}

total_copied = total_skipped = 0
missing = []

for set_name, sub in MAPPING.items():
    obj = json.load(open(os.path.join(MIRROR, set_name, "index.json")))
    copied = skipped = 0

    for key, value in obj.items():
        entries = value if isinstance(value, list) else list(value.values()) if isinstance(value, dict) else []
        for entry in entries:
            if not isinstance(entry, str):
                continue
            rel = urllib.parse.unquote(entry.lstrip("/"))
            if any(s in ("..", "") for s in rel.split("/")):
                raise RuntimeError(f"Suspicious path: {entry}")
            candidates = [os.path.join(WORK, sub, rel)]
            if set_name == "vcsl":
                candidates.append(os.path.join(WORK, sub, "Membranophones", rel))  # drift fallback
            dest = os.path.join(MIRROR, set_name, rel)
            if os.path.exists(dest) and os.path.getsize(dest) > 0:
                skipped += 1
                continue
            src = next((c for c in candidates if os.path.exists(c)), None)
            if src is None:
                missing.append(f"[{set_name}] {entry}")
                continue
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            shutil.copyfile(src, dest)
            copied += 1

    total_copied += copied
    total_skipped += skipped
    print(f"{set_name}: copied {copied}, already present {skipped}")

print("")
print("==== Report ====")
print(f"copied: {total_copied}, already present: {total_skipped}, missing: {len(missing)}")
for m in missing[:50]:
    print(f"  MISSING {m}")
if missing:
    print("Missing files = upstream drift; fetch individually or check the repos by hand.")
sys.exit(1 if missing else 0)
EOF

echo ""
echo "Done. Verify with: ./gradlew runSampleMirror --args=\"--verify\""
echo "Upload with:      ./console/deploy-samples-finzo.sh"
