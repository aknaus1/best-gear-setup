"""Compare the calculator code with the OSRS Wiki DPS calculator (GPL-3.0) for licence review.

Downloads the audited reference revision's library sources into the ignored build/combat-reference/dps-calc
folder (read only; nothing is copied into the plugin), then reports:
  * aggregated token-window flags: runs of matching 8-token windows after names, strings and syntax
    noise are normalised away, filtered for arithmetic/control tokens;
  * comment-vocabulary flags: plugin comments sharing words with a reference comment.
Adjacent windows can match unrelated positions or files and are merged into a single plugin region.
The token count is not a contiguous reference-match length; its displayed reference location identifies
only one contributing window. Comment flags compare unordered vocabulary, not copied sentences.
A flagged region is a prompt for manual review, not a finding; an empty report is not proof of independence,
because a refactored translation can escape token matching.

  python tools/reference_similarity.py
"""
import collections
import json
import pathlib
import re
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
REVISION = "89c3e25b344aea90d0189746e4b5f73dde0f0383"
REPO = "weirdgloop/osrs-dps-calc"
CACHE = ROOT / "build" / "combat-reference" / "dps-calc"
OURS = ROOT / "src" / "main" / "java" / "com" / "bestgearsetup" / "calc"
TOKEN = re.compile(r"[A-Za-z_]\w*|\d+(?:\.\d+)?|\*\*|[<>=!]=|&&|\|\||[-+*/%<>=!?:]")
KEEP = {"if", "else", "return", "for", "while", "switch", "case", "true", "false", "null", "undefined"}
LOGIC = {"+", "-", "*", "/", "%", "<", ">", "<=", ">=", "==", "!=", "&&", "||", "?", "if", "return", "for"}
NOISE = {"Math", "trunc", "floor", "const", "let", "final", "int", "long", "double", "boolean", "new"}
STOP = set("the a an of to and is in for on it its as be by with or at this that are from not when if".split())
K = 8


def fetch():
    if not CACHE.exists():
        url = f"https://api.github.com/repos/{REPO}/git/trees/{REVISION}?recursive=1"
        tree = json.load(urllib.request.urlopen(url))["tree"]
        for entry in tree:
            path = entry["path"]
            if entry["type"] == "blob" and path.startswith(("src/lib/", "src/enums/", "src/types/", "src/utils")) \
                    and path.endswith(".ts") and ".test." not in path:
                target = CACHE / path
                target.parent.mkdir(parents=True, exist_ok=True)
                raw = f"https://raw.githubusercontent.com/{REPO}/{REVISION}/{path}"
                target.write_bytes(urllib.request.urlopen(raw).read())
    return sorted(CACHE.rglob("*.ts"))


def tokens(path):
    text = re.sub(r"/\*.*?\*/", lambda m: "\n" * m.group(0).count("\n"), path.read_text(encoding="utf-8"), flags=re.S)
    out = []
    for no, line in enumerate(text.split("\n"), 1):
        line = re.sub(r"//.*", "", line)
        line = re.sub(r"'[^']*'|\"[^\"]*\"|`[^`]*`", " S ", line)
        if re.match(r"\s*(import|package|@|export \{)", line):
            continue
        line = re.sub(r"[A-Za-z_]\w*(?:\s*\??\.\s*[A-Za-z_]\w*)+", "I", line)
        for t in TOKEN.findall(line):
            if t in NOISE:
                continue
            out.append(("I" if re.match(r"[A-Za-z_]", t) and t not in KEEP else t, no))
    return out


def structural(ref_files):
    ref = {p: tokens(p) for p in ref_files}
    index = {}
    for p, toks in ref.items():
        for i in range(len(toks) - K + 1):
            index.setdefault(tuple(t for t, _ in toks[i:i + K]), (p, i))
    found = []
    for p in sorted(OURS.rglob("*.java")):
        toks = tokens(p)
        hit = [None] * len(toks)
        for i in range(len(toks) - K + 1):
            where = index.get(tuple(t for t, _ in toks[i:i + K]))
            if where:
                for j in range(K):
                    hit[i + j] = hit[i + j] or where
        i = 0
        while i < len(toks):
            if hit[i] is None:
                i += 1
                continue
            j = i
            while j + 1 < len(toks) and hit[j + 1] is not None:
                j += 1
            region = [t for t, _ in toks[i:j + 1]]
            numbers = sum(1 for t in region if t[0].isdigit())
            if len(region) >= 14 and sum(1 for t in region if t in LOGIC) >= 4 and numbers >= 2:
                rp, ri = hit[i]
                found.append((len(region), f"{p.name}:{toks[i][1]}-{toks[j][1]}", f"{rp.name}:~{ref[rp][ri][1]}"))
            i = j + 1
    return sorted(found, reverse=True)


def comments(path):
    text = path.read_text(encoding="utf-8")
    for m in re.finditer(r"//([^\n]*)|/\*(.*?)\*/", text, re.S):
        words = set(re.findall(r"[a-z0-9]+", (m.group(1) or m.group(2) or "").lower())) - STOP
        if len(words) >= 4:
            yield text.count("\n", 0, m.start()) + 1, words


def comment_overlaps(ref_files):
    ref = [(p.name, line, words) for p in ref_files for line, words in comments(p)]
    found = []
    for p in sorted(OURS.rglob("*.java")):
        for line, words in comments(p):
            for name, rline, rwords in ref:
                shared = len(words & rwords)
                if shared >= 4 and shared / len(words | rwords) >= 0.45:
                    found.append((f"{p.name}:{line}", f"{name}:{rline}"))
    return found


def main():
    ref_files = fetch()
    print(f"Reference {REPO}@{REVISION[:7]}: {len(ref_files)} files")
    regions = structural(ref_files)
    print(f"\nAggregated token-window flags to review ({len(regions)}; not contiguous code matches):")
    for size, ours, theirs in regions:
        print(f"  {size:4d} tokens  {ours}  ~  {theirs}")
    overlaps = comment_overlaps(ref_files)
    print(f"\nComment-vocabulary flags ({len(overlaps)}; not sentence matches):")
    for ours, theirs in overlaps:
        print(f"  {ours}  ~  {theirs}")


if __name__ == "__main__":
    main()
