"""Generate the bundled monster status-immunity resource from the OSRS Wiki.

Reads poison, venom and freeze resistance and burn immunity from the infobox_monster bucket (CC BY-NC-SA 3.0)
and writes src/main/resources/com/bestgearsetup/status-immunities.json, keyed by NPC id and by variant name.

  python tools/wiki_status_immunities.py            # use the cached bucket download in build/combat-reference
  python tools/wiki_status_immunities.py --refresh  # download again (set BGS_WIKI_CONTACT; see tools/wiki_api.py)
"""
import argparse
import datetime
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import wiki_api  # noqa: E402
CACHE = ROOT / "build" / "combat-reference"
STATUS_RESOURCE = ROOT / "src" / "main" / "resources" / "com" / "bestgearsetup" / "status-immunities.json"
BURN_LEVELS = {"Not immune": 0, "Immune (weak)": 1, "Immune (normal)": 2, "Immune (strong)": 3}


def resistance(value):
    """Wiki resistance text to a percentage; -1 unknown, -2 venom becomes poison."""
    if value is None:
        return -1
    if value == "Poisons":
        return -2
    digits = "".join(ch for ch in str(value) if ch.isdigit())
    return min(100, int(digits)) if digits else -1


def status_immunities(refresh):
    """Poison/venom/freeze resistance and burn immunity of every Wiki monster variant (infobox_monster bucket)."""
    path = CACHE / "status-immunities-wiki.json"
    fields = ["page_name", "id", "name", "version_anchor", "poison_resistance", "venom_resistance",
              "freeze_resistance", "burn_immune"]
    # Validated and only replaced after a complete download; see tools/wiki_api.py.
    rows = wiki_api.cached(path, refresh, lambda: wiki_api.bucket_all("infobox_monster", fields, "wiki_status_immunities.py"),
                           minimum=1000)
    by_id, by_name = {}, {}
    for row in rows:
        record = [resistance(row.get("poison_resistance")), resistance(row.get("venom_resistance")),
                  resistance(row.get("freeze_resistance")), BURN_LEVELS.get(row.get("burn_immune"), -1)]
        for npc in row.get("id") or []:
            if str(npc).isdigit():
                by_id.setdefault(npc, record)
        by_name.setdefault(row["page_name"].lower(), []).append(record)
    names = {name: records[0] for name, records in by_name.items() if all(r == records[0] for r in records)}
    wiki_api.write_atomic(STATUS_RESOURCE, json.dumps({
        "source": "Old School RuneScape Wiki infobox_monster bucket (CC BY-NC-SA 3.0): poison_resistance, "
                  "venom_resistance, freeze_resistance, burn_immune",
        "retrievedUtc": datetime.datetime.fromtimestamp(path.stat().st_mtime, datetime.timezone.utc).isoformat(),
        "format": "[poison %, venom % (-2 = becomes poison), freeze %, burn immunity 0-3]; -1 = no data",
        "ids": by_id, "names": names}, separators=(",", ":"), sort_keys=True) + "\n")
    print(f"Status immunities: {len(rows)} variants, {len(by_id)} NPC ids, {len(names)} names")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--refresh", action="store_true", help="Download the Wiki bucket again")
    status_immunities(parser.parse_args().refresh)


if __name__ == "__main__":
    main()
