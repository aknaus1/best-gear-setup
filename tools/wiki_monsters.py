"""Generate the bundled OSRS Wiki monster snapshot.

Reads the OSRS Wiki infobox_monster bucket (CC BY-NC-SA 3.0) and writes
src/main/resources/com/bestgearsetup/wiki-monsters.json.gz in the plugin's monster JSON schema. Encounter
tags the Wiki has no field for (raid membership, Gauntlet, combat-class restrictions, absorption, boss)
and the version names the encounter rules expect are derived from the tables below; each entry is a
documented game fact, not copied data.

  python tools/wiki_monsters.py            # use the cached bucket download
  python tools/wiki_monsters.py --refresh  # download again (set BGS_WIKI_CONTACT; see tools/wiki_api.py)

A failed or implausibly small download raises and leaves both the cache and the bundled snapshot untouched.
"""
import argparse
import datetime
import gzip
import json
import pathlib
import re
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import wiki_api  # noqa: E402

CACHE = ROOT / "build" / "combat-reference"
OUT = ROOT / "src" / "main" / "resources" / "com" / "bestgearsetup" / "wiki-monsters.json.gz"
TOOL = "wiki_monsters.py"
# A refresh with fewer rows than this is a broken response, not the Wiki's ~3,000 monster variants.
MIN_MONSTER_ROWS = 1000
MIN_BOSSES = 50
FIELDS = ["page_name", "id", "name", "version_anchor", "default_version", "combat_level", "hitpoints",
          "attack_level", "strength_level", "defence_level", "ranged_level", "magic_level", "magic_attack_bonus",
          "stab_defence_bonus", "slash_defence_bonus", "crush_defence_bonus", "magic_defence_bonus",
          "range_defence_bonus", "light_range_defence_bonus", "standard_range_defence_bonus",
          "heavy_range_defence_bonus", "flat_armour", "size", "attribute", "elemental_weakness",
          "elemental_weakness_percent", "attack_speed", "attack_style", "max_hit", "slayer_category",
          "thrall_immune"]

ATTRIBUTE_NAMES = {"vampyre1": "vampyre (t1)", "vampyre2": "vampyre (t2)", "vampyre3": "vampyre (t3)"}
TOA = {"akkha", "akkha's shadow", "ba-ba", "kephri", "zebak", "elidinis' warden", "tumeken's warden",
       "obelisk (tombs of amascut)", "soldier scarab", "spitting scarab", "arcane scarab", "agile scarab",
       "baboon brawler", "baboon thrower", "baboon mage", "baboon shaman", "baboon thrall", "cursed baboon",
       "volatile baboon", "scarab swarm (tombs of amascut)"}
TOB = {"the maiden of sugadinti", "pestilent bloat", "nylocas ischyros", "nylocas toxobolos", "nylocas hagios",
       "nylocas vasilias", "nylocas prinkipas", "nylocas matomenos", "nylocas athanatos", "sotetseg", "xarpus",
       "verzik vitur", "blood spawn"}
GAUNTLET = re.compile(r"(crystalline|corrupted) (hunllef|bat|bear|dark beast|dragon|rat|scorpion|spider|unicorn|wolf)$")
# (page name pattern, version pattern or None, tag), from the Wiki encounter pages.
CLASS_TAGS = [
    (r"dusk", None, "melee only"), (r"glowing crystal", None, "melee only"),
    (r"eclipse moon", r"clone", "melee only"), (r"nylocas ischyros", None, "melee only"),
    (r"nylocas prinkipas", r"melee", "melee only"),
    (r"kolodion", None, "magic only"), (r"nylocas hagios", None, "magic only"),
    (r"nylocas prinkipas", r"mag", "magic only"),
    (r"nylocas toxobolos", None, "ranged only"), (r"nylocas prinkipas", r"rang", "ranged only"),
    (r"abyssal portal", None, "melee immune"), (r"(cave )?kraken", None, "melee immune"),
    (r"deathly (mage|ranger)", None, "melee immune"), (r"the leviathan", None, "melee immune"),
    (r"maggot king", r"far", "melee immune"), (r"tekton", None, "ranged immune"),
    (r"akkha|blood moon|blue moon|eclipse moon", None, "absorption"),
]
# Names the encounter rules expect where the Wiki versions name a spawn or location instead of the role.
ROLE_BY_ID = {
    7551: "great olm (head)", 7550: "great olm (mage hand)", 7552: "great olm (melee hand)",
    14009: "the hueycoatl (head)", 14014: "the hueycoatl (tail)",
}
WARDEN_VERSIONS = {"active": "phase 2", "damaged": "phase 3", "enraged": "phase 3 enraged"}
TOB_MODE_BY_ID = {**{i: "entry mode" for i in range(10830, 10837)}, **{i: "hard mode" for i in range(10847, 10854)}}
# Nylocas Vasilias changes colour; each colour has its own NPC id and accepts one combat class.
VASILIAS_COLOURS = {8355: "melee", 8356: "magic", 8357: "ranged", 10808: "melee", 10809: "magic", 10810: "ranged",
                    10787: "melee", 10788: "magic", 10789: "ranged"}
COLOUR_TAGS = {"melee": "melee only", "magic": "magic only", "ranged": "ranged only"}


def bucket(name, fields, cache_name, refresh, minimum):
    return wiki_api.cached(CACHE / cache_name, refresh, lambda: wiki_api.bucket_all(name, fields, TOOL),
                           minimum=minimum)


def category_members(title):
    pages, extra = [], {}
    while True:
        data = wiki_api.get_json({"action": "query", "list": "categorymembers", "cmtitle": title, "cmlimit": 500,
                                  **extra}, TOOL)
        members = data.get("query", {}).get("categorymembers")
        if not isinstance(members, list):
            raise wiki_api.WikiError(f"{title}: response has no category members: {json.dumps(data)[:300]}")
        pages += [p["title"] for p in members]
        if "continue" not in data:
            return pages
        extra = data["continue"]
        time.sleep(wiki_api.BATCH_PAUSE)


def category(title, cache_name, refresh, minimum):
    return wiki_api.cached(CACHE / cache_name, refresh, lambda: category_members(title), minimum=minimum)


def number(value, default=0):
    if isinstance(value, list):
        value = value[0] if value else None
    try:
        return int(str(value).replace(",", "").replace("+", "").strip())
    except (TypeError, ValueError):
        return default


def as_list(value):
    if value is None:
        return []
    return value if isinstance(value, list) else [value]


def attack_styles(row):
    styles = [s.strip() for s in as_list(row.get("attack_style")) if s and s.strip()]
    labelled, plain = {}, None
    for hit in (str(h) for h in as_list(row.get("max_hit"))):
        match = re.match(r"\s*(\d[\d,]*)\s*(?:\((.*?)\))?", hit)
        if not match:
            continue
        value = number(match.group(1))
        if match.group(2):
            labelled[match.group(2).strip().lower()] = value
        elif plain is None:
            plain = value
    out = {}
    for style in styles:
        key = style.lower()
        value = labelled.get(key)
        if value is None and key in ("stab", "slash", "crush"):
            value = labelled.get("melee")
        if value is None:
            value = plain if plain is not None else max(labelled.values(), default=0)
        out[key] = {"max_hit": value, "weighting": 1, "typeless": key == "typeless"}
    return out


def tags(page, version, attributes, bosses):
    lower = page.lower()
    version_lower = (version or "").lower()
    out = [ATTRIBUTE_NAMES.get(a, a) for a in attributes]
    if page in bosses:
        out.append("boss")
    if lower in TOA:
        out.append("tombs of amascut")
    if lower in TOB:
        out.append("theatre of blood")
    match = GAUNTLET.match(lower)
    if match:
        out.append(match.group(1))
    for name_pattern, version_pattern, tag in CLASS_TAGS:
        if re.fullmatch(name_pattern, lower) and (version_pattern is None or re.search(version_pattern, version_lower)):
            out.append(tag)
    return sorted(set(out))


def build(row, ids, name, bosses, extra_tags=()):
    page = row["page_name"]
    standard = number(row.get("standard_range_defence_bonus"), None)
    if standard is None:
        standard = number(row.get("range_defence_bonus"))
    weakness = (row.get("elemental_weakness") or "").strip().lower() or None
    attributes = tags(page, row.get("version_anchor"), as_list(row.get("attribute")), bosses) + list(extra_tags)
    return {
        "id": ids[0], "ids": ids, "name": name, "level_cb": number(row.get("combat_level")),
        "level_hp": number(row.get("hitpoints")), "level_attack": number(row.get("attack_level")),
        "level_strength": number(row.get("strength_level")), "level_defence": number(row.get("defence_level")),
        "level_magic": number(row.get("magic_level")), "level_ranged": number(row.get("ranged_level")),
        "agg_magic": number(row.get("magic_attack_bonus")),
        "def_stab": number(row.get("stab_defence_bonus")), "def_slash": number(row.get("slash_defence_bonus")),
        "def_crush": number(row.get("crush_defence_bonus")), "def_magic": number(row.get("magic_defence_bonus")),
        "def_ranged": standard,
        "def_light": number(row.get("light_range_defence_bonus"), None),
        "def_heavy": number(row.get("heavy_range_defence_bonus"), None),
        "flat": number(row.get("flat_armour"), None),
        "weakness_type": weakness, "weakness": number(row.get("elemental_weakness_percent")) if weakness else 0,
        "size": max(1, number(row.get("size"), 1)),
        "task": bool(as_list(row.get("slayer_category"))),
        "immune_thrall": str(row.get("thrall_immune")).lower() == "immune",
        "boss": page in bosses,
        "attributes": [{"name": t} for t in sorted(set(attributes))],
        "attackStyles": attack_styles(row),
        # Bucket BOOLEAN fields are present (empty string) when true and absent when false.
        "default_version": row.get("default_version") is not None,
    }


def records(row, bosses):
    ids = [int(i) for i in as_list(row.get("id")) if str(i).isdigit()]
    version = row.get("version_anchor")
    if not ids or number(row.get("hitpoints"), None) is None or version and "challenge mode" in version.lower():
        # Challenge Mode variants come from the CoX scaling option applied to the normal stats.
        return []
    page = row["page_name"].lower()
    name = page + (f" ({version.lower()})" if version else "")
    if ids[0] in ROLE_BY_ID:
        name = ROLE_BY_ID[ids[0]]
    elif "warden" in page and version and version.lower() in WARDEN_VERSIONS:
        name = f"{page} ({WARDEN_VERSIONS[version.lower()]})"
    elif ids[0] in TOB_MODE_BY_ID:
        name += f" ({TOB_MODE_BY_ID[ids[0]]})"
    if page == "nylocas vasilias":
        colours = [i for i in ids if i in VASILIAS_COLOURS]
        suffix = f" ({version.lower()})" if version else ""
        return [build(row, [i], f"nylocas vasilias ({VASILIAS_COLOURS[i]}){suffix}", bosses,
                      [COLOUR_TAGS[VASILIAS_COLOURS[i]]]) for i in colours]
    return [build(row, ids, name, bosses)]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--refresh", action="store_true")
    args = parser.parse_args()
    rows = bucket("infobox_monster", FIELDS, "wiki-monsters-full.json", args.refresh, MIN_MONSTER_ROWS)
    bosses = set(category("Category:Bosses", "wiki-bosses.json", args.refresh, MIN_BOSSES))
    monsters = [record for row in rows for record in records(row, bosses)]
    if len(monsters) < MIN_MONSTER_ROWS:
        raise SystemExit(f"Only {len(monsters)} monster variants built; keeping the existing {OUT.name}")
    # The list and per-variant lookup are keyed by name, so names must be unique.
    monsters.sort(key=lambda m: (not m["default_version"], m["name"], m["id"]))
    seen = set()
    for record in monsters:
        if record["name"] in seen:
            record["name"] = f"{record['name']} (npc {record['id']})"
        seen.add(record["name"])
    payload = {"source": "Old School RuneScape Wiki infobox_monster bucket and Category:Bosses (CC BY-NC-SA 3.0)",
               "retrievedUtc": datetime.datetime.fromtimestamp((CACHE / "wiki-monsters-full.json").stat().st_mtime,
                                                               datetime.timezone.utc).isoformat(),
               "monsters": monsters}
    text = json.dumps(payload, separators=(",", ":"), sort_keys=True)
    wiki_api.write_atomic(OUT, gzip.compress(text.encode("utf-8")), "wb")
    print(f"Wiki monsters: {len(monsters)} variants from {len(rows)} rows; {OUT.stat().st_size} bytes")


if __name__ == "__main__":
    main()
