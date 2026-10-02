"""Build the curated wear-requirement table (src/main/resources/com/bestgearsetup/equipment-requirements.json).

No requests are made. Each selector is restricted to an equipment slot and a full name match (or explicit IDs).
Run: python tools/equipment_requirements.py
"""
import argparse
import json
import pathlib
import re
import urllib.parse

ROOT = pathlib.Path(__file__).resolve().parents[1]
RESOURCE = ROOT / "src/main/resources/com/bestgearsetup/equipment-requirements.json"
SKILLS = {"attack": 0, "defence": 1, "strength": 2, "hitpoints": 3,
          "ranged": 4, "prayer": 5, "magic": 6, "slayer": 18}
RULES = []
SUFFIX = r"(?:\s*\((?:t|g|h[1-5]|i|e|ei|or|p(?:\+{1,2})?|dark|light|dusk|shattered|\d+)\))*"


def rule(label, slots, pattern, requirements, source, *, ids=None, note="", combat=0, review=False):
    RULES.append({"label": label, "slots": slots.split(), "pattern": pattern + SUFFIX,
                  "ids": ids or [], "requirements": requirements, "combat": combat,
                  "source": "https://oldschool.runescape.wiki/w/" + urllib.parse.quote(source.replace(" ", "_"), safe="/_"),
                  "note": note, "reviewRequired": review})


def families():
    # Explicit equipment nouns avoid black mask/black d'hide and dragon hunter weapons.
    armour = r"(?:chainbody|platebody|platelegs|plateskirt|full helm|med helm|helm|kiteshield|sq shield|shield|boots)"
    weapons = r"(?:2h sword|axe|felling axe|battleaxe|cane|claws|dagger|hasta|longsword|mace|pickaxe|scimitar|spear|sword)"
    thrown = r"(?:dart|knife|thrownaxe)"
    for metal, level in [("bronze", 1), ("iron", 1), ("steel", 5), ("black", 10),
                         ("white", 10), ("mithril", 20), ("adamant", 30), ("rune", 40), ("dragon", 60)]:
        source = metal.title() + " equipment"
        rule(metal + " armour", "head body legs shield feet", metal + " " + armour,
             {"defence": level}, source)
        rule(metal + " melee weapons", "weapon", metal + " " + weapons, {"attack": level}, source)
        rule(metal + " thrown weapons", "weapon ammunition", metal + " " + (r"(?:dart|knife)" if metal == "dragon" else thrown),
             {"ranged": level}, source)
        rule(metal + " warhammer", "weapon", metal + " warhammer", {"strength": level}, "Warhammer",
             note="Metal warhammers use Strength, not Attack, after the 2021 rebalance.")
        if metal != "dragon":
            rule(metal + " defender", "shield", metal + " defender",
                 {"attack": level, "defence": level}, "Defender")
        rule(metal + " halberd", "weapon", metal + " halberd",
             {"attack": level, "strength": max(1, level // 2) if level >= 10 else 1}, metal.title() + " halberd",
             review=metal == "steel", note="Steel Strength requirement needs direct confirmation." if metal == "steel" else "")
    rule("dragon thrownaxe", "weapon", "dragon thrownaxe", {"ranged": 61}, "Dragon thrownaxe")
    rule("white gloves", "hands", "white gloves", {"defence": 10}, "White gloves")

    gods = r"(?:ancient|armadyl|bandos|guthix|saradomin|zamorak)"
    rule("rune god armour", "head body legs shield", gods + r" (?:full helm|platebody|platelegs|plateskirt|kiteshield)",
         {"defence": 40}, "Rune god armour")
    rule("gilded melee armour", "head body legs shield feet", r"gilded (?:full helm|platebody|platelegs|plateskirt|kiteshield|boots)",
         {"defence": 40}, "Gilded armour")
    rule("dragonstone armour", "head body legs hands feet", "dragonstone " + armour,
         {"defence": 40}, "Dragonstone armour")
    rule("blessed d'hide upper armour", "head body feet shield", gods + r" (?:coif|d'hide (?:body|boots|shield))",
         {"defence": 40, "ranged": 70}, "Blessed dragonhide armour")
    rule("blessed d'hide lower armour", "legs hands", gods + r" (?:chaps|bracers)",
         {"ranged": 70}, "Blessed dragonhide armour",
         note="Chaps and bracers lost their Defence requirement in September 2021.")
    for colour, level in [("green", 40), ("blue", 50), ("red", 60), ("black", 70)]:
        rule(colour + " d'hide body/shield", "body shield", colour + r" d'hide (?:body|shield)",
             {"defence": 40, "ranged": level}, colour.title() + " dragonhide armour")
        rule(colour + " d'hide chaps/vambraces", "legs hands", colour + r" (?:d'hide (?:chaps|vambraces)|spiky vambraces)",
             {"ranged": level}, colour.title() + " dragonhide armour")
    rule("god vestment robes", "body legs", gods + r" robe (?:top|legs)", {"prayer": 20}, "Vestment set")
    rule("god vestment mitres", "head", gods + " mitre", {"prayer": 40, "magic": 40}, "Vestment set")
    rule("god vestment cloaks", "cape", gods + " cloak", {"prayer": 40}, "Vestment set")
    rule("god vestment stoles/croziers", "neck weapon", gods + r" (?:stole|crozier)", {"prayer": 60}, "Vestment set")

    for prefix, defence, magic in [("mystic", 20, 40), ("enchanted", 20, 40), ("splitbark", 40, 40),
                                    ("swampbark", 50, 50), ("bloodbark", 60, 60), ("infinity", 25, 50),
                                    ("dagon'hai", 40, 70), ("lunar", 40, 65)]:
        nouns = r"(?:hat|hood|helm|robe top|robe bottom|top|bottom|body|legs|torso|gloves|gauntlets|boots|cape|amulet|ring)"
        rule(prefix + " armour", "head body legs hands feet cape neck ring", prefix + " " + nouns,
             {"defence": defence, "magic": magic}, "Armour/Magic armour")
    rule("robes of darkness", "head body legs hands feet", r"(?:hood|robe top|robe bottom|gloves|boots) of darkness",
         {"defence": 20, "magic": 40}, "Robes of darkness")
    rule("skeletal armour", "head body legs", r"skeletal (?:helm|top|bottoms)",
         {"defence": 40, "magic": 40}, "Skeletal armour")
    rule("spined armour", "head body legs", r"spined (?:helm|body|chaps)",
         {"defence": 40, "ranged": 40}, "Spined armour")
    rule("rock-shell armour", "head body legs", r"rock-shell (?:helm|plate|legs)",
         {"defence": 40}, "Rock-shell armour")
    rule("granite armour", "head body legs feet shield", r"granite (?:helm|body|legs|boots|shield)",
         {"defence": 50, "strength": 50}, "Granite equipment")
    rule("samurai armour", "head body legs hands feet", r"samurai (?:kasa|shirt|greaves|gloves|boots)",
         {"defence": 35}, "Samurai armour")
    rule("shayzien armour", "head body legs hands feet", r"shayzien (?:helm|body|platebody|greaves|gloves|boots)",
         {"defence": 20}, "Shayzien armour")
    rule("frog-leather armour", "body legs feet", r"frog-leather (?:body|chaps|boots)",
         {"defence": 25, "ranged": 25}, "Frog-leather armour")
    rule("studded body", "body", "studded body", {"defence": 20, "ranged": 20}, "Studded body")
    rule("studded chaps", "legs", "studded chaps", {"ranged": 20}, "Studded chaps")
    rule("hardleather body", "body", "hardleather body", {"defence": 10}, "Hardleather body")

    for wood, level in [("oak", 5), ("willow", 20), ("maple", 30), ("yew", 40), ("magic", 50)]:
        rule(wood + " bows", "weapon", wood + r" (?:shortbow|longbow|comp bow)",
             {"ranged": level}, wood.title() + " shortbow")
    for metal, level in [("bronze", 1), ("blurite", 16), ("iron", 26), ("steel", 31),
                         ("mithril", 36), ("adamant", 46), ("rune", 61), ("dragon", 64)]:
        rule(metal + " crossbow", "weapon", metal + " crossbow", {"ranged": level}, metal.title() + " crossbow")
    rule("elemental battlestaves", "weapon", r"(?:(?:air|water|earth|fire|lava|mud|steam|smoke|mist|dust) )?battlestaff",
         {"attack": 30, "magic": 30}, "Battlestaff")
    rule("elemental mystic staves", "weapon", r"mystic (?:air|water|earth|fire|lava|mud|steam|smoke|mist|dust) staff",
         {"attack": 40, "magic": 40}, "Mystic staff")
    for name, magic in [("beginner wand", 45), ("apprentice wand", 50), ("teacher wand", 55), ("master wand", 60)]:
        rule(name, "weapon", name, {"magic": magic}, name.title())

    for name, skill in [("attack", "attack"), ("strength", "strength"), ("defence", "defence"),
                        ("ranging", "ranged"), ("magic", "magic"), ("prayer", "prayer"),
                        ("hitpoints", "hitpoints"), ("slayer", "slayer")]:
        rule(name + " skillcape", "cape", name + " cape", {skill: 99}, "Cape of Accomplishment")
    # Max capes need 99 in every skill; the eight modelled combat skills are a necessary subset.
    rule("max capes", "cape", r"(?:[a-z']+ )*max cape", {skill: 99 for skill in SKILLS}, "Max cape",
         note="Noncombat skills and total level are not modelled; the combat subset is still enforced.")
    rule("ava's accumulator", "cape", "ava's accumulator", {"ranged": 50}, "Ava's accumulator",
         note="Awarded or upgraded only at 50 Ranged; an acquisition gate rather than a wear check.")
    # Noncombat capes, diary rewards and quest rewards stay in the audit until access is modelled.
    rule("black masks", "head", "black mask", {"defence": 10, "strength": 20}, "Black mask", combat=40)
    rule("pegasian boots exception", "feet", "pegasian boots", {"defence": 75, "ranged": 75}, "Pegasian boots",
         ids=[13237], review=True, note="Replace the cache's spurious Attack 75 pair; runtime equip check still pending.")
    # All eight skill fields are explicitly cleared for these reviewed quest/creation-only cases.
    rule("salve amulets", "neck", "salve amulet", {}, "Salve amulet",
         note="Haunted Mine/Tarn unlocks are separate; their quest/creation levels are not wear levels.")


def build():
    RULES.clear()
    families()
    labels = [r["label"] for r in RULES]
    if len(set(labels)) != len(labels):
        raise ValueError("duplicate rule labels")
    for r in RULES:
        re.compile(r["pattern"])
        if not set(r["requirements"]).issubset(SKILLS):
            raise ValueError(r["label"])
    payload = {"schema": 1, "scope": "Eight combat skills plus combat level; quest, diary and noncombat skill gates are not resolved.",
               "rules": RULES}
    RESOURCE.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(RULES)} rules to {RESOURCE.name}")


if __name__ == "__main__":
    argparse.ArgumentParser(description=__doc__).parse_args()
    build()
