"""Generate the bundled OSRS Wiki equipment, spell, prayer and potion snapshot.

Inputs (CC BY-NC-SA 3.0 unless noted):
  * infobox_item joined with infobox_bonuses: stats, slot, category, speed, members, tradeable;
  * infobox_spell: spell level and spellbook;
  * item page wikitext: wear requirements stated in prose ("requires 70 Ranged to wield");
  * game cache item params 434..617 read offline by tools/spike/ReqDump.java (optional, not Wiki data);
  * RuneLite's item_variations.json (BSD), to fold stat-identical variants into one catalogue entry.

Everything the Wiki has no structured field for is a documented table below: weapon styles per category
(Wiki "Weapons/Types"), ammunition tiers ("Arrows", "Bolts", "Darts"), autocast lists ("Autocast"),
spell max hits ("Combat spells"), prayer and potion boosts (each prayer/potion page). Requirements also
pass through the curated family rules in tools/equipment_requirements.py.

Output: src/main/resources/com/bestgearsetup/wiki-gamedata.json.gz, in the plugin's equipment/weapon/
spell/prayer/potion JSON schema.

  python tools/wiki_equipment.py            # cached downloads in build/combat-reference
  python tools/wiki_equipment.py --refresh  # download again (set BGS_WIKI_CONTACT; see tools/wiki_api.py)
"""
import argparse
import collections
import datetime
import gzip
import json
import pathlib
import re
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parents[1]
CACHE = ROOT / "build" / "combat-reference"
OUT = ROOT / "src" / "main" / "resources" / "com" / "bestgearsetup" / "wiki-gamedata.json.gz"
TOOL = "wiki_equipment.py"
# Minimum plausible download sizes; smaller responses are treated as failures and the cache is kept.
MINIMUM = {"wiki-equipment-raw.json": 3000, "wiki-spells-bucket.json": 50, "wiki-item-categories.json": 1000,
           "wiki-item-pages.json": 1000}
VARIATIONS = [ROOT.parents[1] / "runelite-client-src" / "runelite-client" / "src" / "main" / "resources" / "item_variations.json"]
sys.path.insert(0, str(ROOT / "tools"))
import equipment_requirements  # noqa: E402
import wiki_api  # noqa: E402

ITEM_FIELDS = ["page_name", "page_name_sub", "item_name", "item_id", "version_anchor", "default_version",
               "is_members_only", "tradeable", "removal_date"]
BONUS_FIELDS = {"stab_bonus": "stab_attack_bonus", "slash_bonus": "slash_attack_bonus",
                "crush_bonus": "crush_attack_bonus", "ranged_bonus": "range_attack_bonus",
                "magic_bonus": "magic_attack_bonus", "melee_str": "strength_bonus",
                "ranged_str": "ranged_strength_bonus", "magic_str": "magic_damage_bonus",
                "prayer_bonus": "prayer_bonus", "stab_def": "stab_defence_bonus", "slash_def": "slash_defence_bonus",
                "crush_def": "crush_defence_bonus", "ranged_def": "range_defence_bonus",
                "magic_def": "magic_defence_bonus"}
EXTRA_FIELDS = ["equipment_slot", "weapon_attack_speed", "weapon_attack_range", "combat_style"]
SLOTS = {"head": "head", "cape": "cape", "neck": "neck", "ammo": "ammunition", "body": "body", "shield": "shield",
         "legs": "legs", "hands": "hands", "feet": "feet", "ring": "ring", "weapon": "weapon", "2h": "weapon"}
SKILLS = {"attack": 0, "defence": 1, "strength": 2, "hitpoints": 3, "ranged": 4, "prayer": 5, "magic": 6,
          "slayer": 18}
SKILL_NAMES = {"attack", "defence", "strength", "hitpoints", "ranged", "prayer", "magic", "slayer", "agility",
               "crafting", "fletching", "smithing", "herblore", "firemaking", "woodcutting", "mining", "farming",
               "construction", "hunter", "thieving", "runecraft", "fishing", "cooking", "sailing"}

# Wiki "Weapons/Types": the combat options of each weapon category (name, attack type, stance).
STYLES = {
    "2h sword": ["chop,slash,accurate", "slash,slash,aggressive", "smash,crush,aggressive", "block,slash,defensive"],
    "axe": ["chop,slash,accurate", "hack,slash,aggressive", "smash,crush,aggressive", "block,slash,defensive"],
    "banner": ["lunge,stab,accurate", "swipe,slash,aggressive", "pound,crush,controlled", "block,stab,defensive"],
    "bladed staff": ["jab,stab,accurate", "swipe,slash,aggressive", "fend,crush,defensive", "spell,magic,magic"],
    "bludgeon": ["pound,crush,aggressive", "pummel,crush,aggressive", "smash,crush,aggressive"],
    "blunt": ["pound,crush,accurate", "pummel,crush,aggressive", "block,crush,defensive"],
    "bow": ["accurate,ranged,accurate", "rapid,ranged,rapid", "longrange,ranged,longrange"],
    "bulwark": ["pummel,crush,accurate", "block,none,none"],
    "chinchompa": ["short fuse,ranged,accurate", "medium fuse,ranged,rapid", "long fuse,ranged,longrange"],
    "claw": ["chop,slash,accurate", "slash,slash,aggressive", "lunge,stab,controlled", "block,slash,defensive"],
    "crossbow": ["accurate,ranged,accurate", "rapid,ranged,rapid", "longrange,ranged,longrange"],
    "flail": ["chop,slash,accurate", "slash,slash,aggressive", "block,slash,defensive"],
    "partisan": ["stab,stab,accurate", "lunge,stab,aggressive", "pound,crush,aggressive", "block,stab,defensive"],
    "pickaxe": ["spike,stab,accurate", "impale,stab,aggressive", "smash,crush,aggressive", "block,stab,defensive"],
    "polearm": ["jab,stab,controlled", "swipe,slash,aggressive", "fend,stab,defensive"],
    "polestaff": ["bash,crush,accurate", "pound,crush,aggressive", "block,crush,defensive"],
    "powered staff": ["accurate,magic,accurate", "accurate,magic,accurate", "longrange,magic,longrange"],
    "salamander": ["scorch,slash,aggressive", "flare,ranged,accurate", "blaze,magic,magic"],
    "scythe": ["reap,slash,accurate", "chop,slash,aggressive", "jab,crush,aggressive", "block,slash,defensive"],
    "slash sword": ["chop,slash,accurate", "slash,slash,aggressive", "lunge,stab,controlled", "block,slash,defensive"],
    "spear": ["lunge,stab,controlled", "swipe,slash,controlled", "pound,crush,controlled", "block,stab,defensive"],
    "spiked": ["pound,crush,accurate", "pummel,crush,aggressive", "spike,stab,controlled", "block,crush,defensive"],
    "stab sword": ["stab,stab,accurate", "lunge,stab,aggressive", "slash,slash,aggressive", "block,stab,defensive"],
    "staff": ["bash,crush,accurate", "pound,crush,aggressive", "focus,crush,defensive", "spell,magic,magic"],
    "thrown": ["accurate,ranged,accurate", "rapid,ranged,rapid", "longrange,ranged,longrange"],
    "unarmed": ["punch,crush,accurate", "kick,crush,aggressive", "block,crush,defensive", "spell,magic,magic"],
    "whip": ["flick,slash,accurate", "lash,slash,controlled", "deflect,slash,defensive"],
}
CATEGORY_ALIASES = {"chinchompas": "chinchompa"}

# Zero-stat equipment kept for encounter or effect rules; everything else without bonuses is cosmetic.
KEEP_WITHOUT_BONUSES = {"ring of recoil", "efaritay's aid", "bracelet of ethereum", "bracelet of slaughter",
                        "expeditious bracelet", "earmuffs", "facemask", "nose peg", "reinforced goggles",
                        "lit bug lantern", "saradomin banner", "zamorak banner", "swift blade", "atlatl dart",
                        "lightbearer"}
# Unusable or duplicate states; their working counterpart is a separate row.
DROP_NAME = re.compile(r"\((?:broken|inactive|deactivated|empty|unlit|damaged|beta|last man standing|au)\)| 0$|"
                       r"^(?:team-\d+ cape|.* heraldic (?:helm|kiteshield)|lunar staff - pt\d)$")
# Wiki categories of content outside the main game.
DROP_CATEGORY = re.compile(r"League$|^Grid Master$|^Beta items$|shelved content|^Emir's Arena$|"
                           r"^Discontinued content$")
# Leagues Reward Shop ornament kits ("soulreaper axe (o)") are main-game cosmetics, though the Wiki files them
# under the league; one is kept when RuneLite groups it with a kept main-game item.
LEAGUE_CATEGORY = re.compile(r"League$")
# Deadman Mode gear keeps a "(dmm)" marker so searches can leave it out unless asked for.
DEADMAN_CATEGORY = re.compile(r"^Deadman(?: Mode|: | seasonal items)")
DROP_PAGE = re.compile(r"\((?:Last Man Standing|beta|historical|Grid Master|Leagues)\)", re.I)
# Name noise that never changes an item's effect; stat-identical items whose names agree after removing it
# are one catalogue entry (charges, ornament kits, locked/poisoned/degraded/NMZ copies).
NOISE = re.compile(r"\s*\((?:\d+|l|o|or|g|t|cr|h\d|p\+{0,2}|kp|nz|deadman|uncharged|charged|used|new)\)"
                   r"|\s+(?:100|75|50|25|0)$|^(?:dyed |gilded |trimmed )")

ARROW_TIER = {"bronze": 1, "iron": 1, "steel": 5, "mithril": 20, "adamant": 30, "rune": 40, "amethyst": 50,
              "dragon": 60, "broad": 30}
# Wiki "Arrows": the best arrow each bow fires.
BOW_TIER = [(r"(?:short|long)bow|rain bow", 1), (r"oak (?:short|long)bow|signed oak bow", 5), (r"willow (?:short|long|comp )bow", 20),
            (r"maple (?:short|long)bow", 30), (r"yew (?:short|long|comp )bow", 40),
            (r"magic (?:shortbow(?: \(i\))?|longbow|comp bow)|seercull|bone shortbow", 50),
            (r"(?:corrupted )?dark bow(?: \(bh\))?|(?:corrupted )?twisted bow|3rd age bow|scorching bow|venator bow", 60)]
BOLT_TIER = {"bronze": 1, "opal": 1, "barbed": 1, "blurite": 16, "jade": 16, "iron": 26, "pearl": 26, "silver": 26,
             "steel": 31, "topaz": 31, "mithril": 36, "sapphire": 36, "emerald": 36, "adamant": 46, "ruby": 46,
             "diamond": 46, "runite": 61, "dragonstone": 61, "onyx": 61, "broad": 61, "amethyst broad": 61,
             "dragon": 64}
# Wiki "Bolts": the best bolt each crossbow fires.
CROSSBOW_TIER = [("bronze crossbow", 1), ("blurite crossbow", 16), ("iron crossbow", 26), ("steel crossbow", 31),
                 ("mithril crossbow", 36), ("adamant crossbow", 46), ("rune crossbow", 61),
                 (r"dragon crossbow|armadyl crossbow|zaryte crossbow|dragon hunter crossbow(?: \(b\))?|king's barrage", 64)]
DART_TIER = {"bronze": 1, "iron": 1, "steel": 5, "black": 10, "mithril": 20, "adamant": 30, "rune": 40,
             "amethyst": 50, "dragon": 60}
BLOWPIPE_TIER = [("camphor blowpipe", 20), ("ironwood blowpipe", 30), ("rosewood blowpipe", 40),
                 (r"toxic blowpipe|blazing blowpipe", 60)]
BRUTAL_TIER = {"bronze": 1, "iron": 1, "steel": 5, "black": 10, "mithril": 20, "adamant": 30, "rune": 40}
FIXED_AMMO = [(r"eclipse atlatl", r"atlatl dart"), (r"silvthrill ballista", r"silvthrill javelin"),
              (r"crossbow|phoenix crossbow", r"bronze bolts"), (r"karil's crossbow", r"bolt rack"),
              (r"dorgeshuun crossbow", r"bone bolts"), (r"hunters' crossbow", r"(?:long )?kebbit bolts"),
              (r"hunters' sunlight crossbow", r"(?:sunlight|moonlight) antler bolts"),
              (r"training bow", r"training arrows"), (r"ogre bow", r"ogre arrow|(?:bronze|iron|steel|black|mithril) brutal"),
              (r"comp ogre bow", r"ogre arrow|(?:bronze|iron|steel|black|mithril|adamant|rune) brutal"),
              (r"(?:heavy|light) ballista", r"(?:bronze|iron|steel|mithril|adamant|rune|amethyst|dragon|silvthrill) javelin"),
              (r"swamp lizard", r"guam tar"), (r"orange salamander", r"marrentill tar"),
              (r"red salamander", r"tarromin tar"), (r"black salamander", r"harralander tar"),
              (r"tecu salamander", r"irit tar")]

# Wiki "Combat spells" / each spell page: base max hit. Level and spellbook come from infobox_spell.
SPELL_MAX = {
    "Wind Strike": 2, "Water Strike": 4, "Earth Strike": 6, "Fire Strike": 8,
    "Wind Bolt": 9, "Water Bolt": 10, "Earth Bolt": 11, "Fire Bolt": 12,
    "Wind Blast": 13, "Water Blast": 14, "Earth Blast": 15, "Fire Blast": 16,
    "Wind Wave": 17, "Water Wave": 18, "Earth Wave": 19, "Fire Wave": 20,
    "Wind Surge": 21, "Water Surge": 22, "Earth Surge": 23, "Fire Surge": 24,
    "Crumble Undead": 15, "Iban Blast": 25, "Magic Dart": 15,
    "Saradomin Strike": 20, "Claws of Guthix": 20, "Flames of Zamorak": 20,
    "Smoke Rush": 14, "Shadow Rush": 15, "Blood Rush": 16, "Ice Rush": 17,
    "Smoke Burst": 18, "Shadow Burst": 19, "Blood Burst": 21, "Ice Burst": 22,
    "Smoke Blitz": 23, "Shadow Blitz": 24, "Blood Blitz": 25, "Ice Blitz": 26,
    "Smoke Barrage": 27, "Shadow Barrage": 28, "Blood Barrage": 29, "Ice Barrage": 30,
    "Ghostly Grasp": 12, "Skeletal Grasp": 17, "Undead Grasp": 24,
    "Inferior Demonbane": 16, "Superior Demonbane": 23, "Dark Demonbane": 30,
}
# Wiki "Autocast": which weapons cast which spells. Patterns are full-name matches.
ELEMENTAL = re.compile(r"(?:wind|water|earth|fire) (?:strike|bolt|blast|wave|surge)")
WAVES_ONLY = r"slayer's staff(?: \(e\))?|void knight mace"
NO_ELEMENTAL = r"skull sceptre(?: \(i\))?"
ANCIENT_STAVES = (r"ancient staff|(?:(?:smoke|shadow|blood|ice) )?ancient sceptre|thammaron's sceptre \(a\)|"
                  r"accursed sceptre \(a\)|blue moon spear|master wand|dragon hunter wand|kodai wand|"
                  r"(?:eldritch |volatile |corrupted volatile )?nightmare staff")
ARCEUUS_STAVES = (r"skull sceptre(?: \(i\))?|slayer's staff(?: \(e\))?|ahrim's staff|blue moon spear|"
                  r"(?:toxic )?staff of the dead|purging staff|master wand|kodai wand")
SPECIAL_SPELLS = {
    "Crumble Undead": r"skull sceptre \(i\)|slayer's staff(?: \(e\))?|(?:toxic )?staff of the dead|staff of light|"
                      r"staff of balance|void knight mace",
    "Magic Dart": r"slayer's staff(?: \(e\))?|(?:toxic )?staff of the dead|staff of light|staff of balance",
    "Iban Blast": r"iban's staff(?: \(u\))?",
    "Saradomin Strike": r"saradomin staff|staff of light",
    "Claws of Guthix": r"guthix staff|staff of balance|void knight mace",
    "Flames of Zamorak": r"zamorak staff|(?:toxic )?staff of the dead|thammaron's sceptre \(a\)|accursed sceptre \(a\)",
}

# Each prayer's page: (name, book, accuracy %, damage %, prayer level, defence level).
PRAYERS = {
    "attack": [("clarity of thought", 5, 0, 7, 1), ("improved reflexes", 10, 0, 16, 1),
               ("incredible reflexes", 15, 0, 34, 1), ("chivalry", 15, 0, 60, 65), ("piety", 20, 0, 70, 70)],
    "strength": [("burst of strength", 5, 0, 4, 1), ("superhuman strength", 10, 0, 13, 1),
                 ("ultimate strength", 15, 0, 31, 1), ("chivalry", 18, 0, 60, 65), ("piety", 23, 0, 70, 70)],
    "ranged": [("sharp eye", 5, 5, 8, 1), ("hawk eye", 10, 10, 26, 1), ("eagle eye", 15, 15, 44, 1),
               ("deadeye", 18, 18, 62, 1), ("rigour", 20, 23, 74, 70)],
    "magic": [("mystic will", 5, 0, 9, 1), ("mystic lore", 10, 1, 27, 1), ("mystic might", 15, 2, 45, 1),
              ("mystic vigour", 18, 3, 63, 1), ("augury", 25, 4, 77, 70)],
}
# Each potion's page: boost = base + floor(level * percent / 100), by skill; attributes restrict access.
POTIONS = [
    (2428, "attack potion", {"attack": (3, 10)}, []), (113, "strength potion", {"strength": (3, 10)}, []),
    (2432, "defence potion", {"defence": (3, 10)}, []), (9739, "combat potion", {"attack": (3, 10), "strength": (3, 10)}, []),
    (2436, "super attack", {"attack": (5, 15)}, []), (2440, "super strength", {"strength": (5, 15)}, []),
    (2442, "super defence", {"defence": (5, 15)}, []),
    (12695, "super combat potion", {"attack": (5, 15), "strength": (5, 15), "defence": (5, 15)}, []),
    (2444, "ranging potion", {"ranged": (4, 10)}, []), (3040, "magic potion", {"magic": (4, 0)}, []),
    (22461, "bastion potion", {"ranged": (4, 10), "defence": (5, 15)}, []),
    (22449, "battlemage potion", {"magic": (4, 0), "defence": (5, 15)}, []),
    (26346, "ancient brew", {"magic": (2, 5)}, []), (27638, "forgotten brew", {"magic": (3, 8)}, []),
    (20724, "imbued heart", {"magic": (1, 10)}, []), (27641, "saturated heart", {"magic": (4, 10)}, []),
    (2450, "zamorak brew", {"attack": (2, 20), "strength": (2, 12)}, []),
    (6685, "saradomin brew", {"defence": (2, 20)}, []),
    (10020, "ruby harvest", {"attack": (4, 15)}, []), (10014, "black warlock", {"strength": (4, 15)}, []),
    (10018, "sapphire glacialis", {"defence": (4, 15)}, []),
    (1907, "wizard's mind bomb", {"magic": (2, 2)}, []), (5741, "mature wizard's mind bomb", {"magic": (3, 2)}, []),
    (25826, "lizardkicker", {"ranged": (4, 0)}, []), (7208, "wild pie", {"ranged": (4, 0)}, []),
    (20988, "overload (-)", {s: (4, 10) for s in ("attack", "strength", "defence", "ranged", "magic")}, ["xerician"]),
    (20992, "overload", {s: (5, 13) for s in ("attack", "strength", "defence", "ranged", "magic")}, ["xerician"]),
    (20996, "overload (+)", {s: (6, 16) for s in ("attack", "strength", "defence", "ranged", "magic")}, ["xerician"]),
    (11730, "overload (nmz)", {s: (5, 15) for s in ("attack", "strength", "defence", "ranged", "magic")}, ["nmz"]),
    (11722, "super ranging", {"ranged": (5, 15)}, ["nmz"]), (11726, "super magic potion", {"magic": (5, 15)}, ["nmz"]),
    (27345, "smelling salts", {s: (11, 16) for s in ("attack", "strength", "defence", "ranged", "magic")},
     ["tombs of amascut"]),
]


def get_json(params):
    return wiki_api.get_json(params, TOOL)


def cached(name, refresh, fetch):
    return wiki_api.cached(CACHE / name, refresh, fetch, minimum=MINIMUM.get(name, 1))


def fetch_equipment():
    fields = ",".join(repr(f) for f in ITEM_FIELDS + ["infobox_bonuses." + f
                                                       for f in list(BONUS_FIELDS.values()) + EXTRA_FIELDS])
    rows, offset = [], 0
    while True:
        query = (f"bucket('infobox_item').select({fields}).limit(500).offset({offset})"
                 ".where('infobox_bonuses.equipment_slot','!=',bucket.Null()).where('item_id','!=',bucket.Null())"
                 ".join('infobox_bonuses','infobox_bonuses.page_name_sub','infobox_item.page_name_sub')"
                 ".orderBy('page_name_sub','asc').run()")
        batch = wiki_api.bucket_rows(query, TOOL)
        rows += batch
        if len(batch) < 500:
            return rows
        offset += 500
        time.sleep(1)


def fetch_spells():
    query = "bucket('infobox_spell').select('page_name','spellbook','json','is_members_only').limit(500).run()"
    return wiki_api.bucket_rows(query, TOOL)


def fetch_pages(titles):
    pages = {}
    titles = sorted(titles)
    for i in range(0, len(titles), 50):
        data = get_json({"action": "query", "prop": "revisions", "rvprop": "content", "rvslots": "main",
                         "titles": "|".join(titles[i:i + 50])})
        for page in wiki_api.query_pages(data).values():
            revisions = page.get("revisions")
            if revisions:
                pages[page["title"]] = revisions[0]["slots"]["main"]["*"]
        time.sleep(1)
    return pages


def fetch_categories(titles):
    categories = collections.defaultdict(list)
    titles = sorted(titles)
    for i in range(0, len(titles), 50):
        extra = {}
        while True:
            data = get_json({"action": "query", "prop": "categories", "cllimit": "max",
                             "titles": "|".join(titles[i:i + 50]), **extra})
            for page in wiki_api.query_pages(data).values():
                categories[page["title"]] += [c["title"].split(":", 1)[1] for c in page.get("categories", [])]
            if "continue" not in data:
                break
            extra = data["continue"]
        time.sleep(1)
    return categories


def number(value):
    try:
        return float(value)
    except (TypeError, ValueError):
        return 0.0


def bonuses(row):
    out = {}
    for key, field in BONUS_FIELDS.items():
        value = number(row.get("infobox_bonuses." + field))
        out[key] = value if key == "magic_str" else int(value)
    return out


def ids_of(row):
    return [int(i) for i in row.get("item_id") or [] if str(i).isdigit()]


def display_name(row):
    name = (row.get("item_name") or row["page_name"]).lower().strip()
    page = row["page_name"]
    if re.search(r"\(Deadman Mode\)|\(Deadman\)$", page) and "(dmm)" not in name and "(deadman)" not in name:
        name += " (dmm)"
    return name


def norm(name):
    previous = None
    while previous != name:
        previous, name = name, NOISE.sub("", name).strip()
    return name


def selector(patterns):
    return re.compile("(?:" + patterns + ")")


# --- Wear requirements -------------------------------------------------------------------------------------

REQ_SENTENCE = re.compile(r"[^.\n]*\b(?:requir[a-z]*|must have|having|needs?)\b[^.\n]*", re.I)
VERB = re.compile(r"\b(?:(?:wield|wear|equip)(?:s|ed|ing|ped|ping)?|worn|to use(?! (?:the|its|their) special))\b", re.I)
GATE = re.compile(r"\brequir[a-z]*\b|\bmust have\b|\bhaving\b|\blevel of\b|\bneeds?\b", re.I)
# "The runner hat is ... armour that requires 45 Defence and is rewarded from ..." states the gate without a verb.
SUBJECT = re.compile(r"\s*(?:it|the|an?)\b[^.]{0,120}?\brequires?\b", re.I)
# Sentences about something other than this item's wear gate: quest history, comparisons, other items.
NOT_WEAR = re.compile(r"\b(?:quest required|quest requirements|despite|poll|previously|formerly|originally|"
                      r"prior to|instead of|in comparison|enhanced|no requirements?)\b", re.I)
CREATION = re.compile(r"\bto (?:create|make|craft|fletch|smith|imbue|enchant|repair|charge|upgrade|combine|cast|"
                      r"obtain|unlock|enter|access|complete|buy|purchase)\b",
                      re.I)
SKILL_WORDS = "Attack|Strength|Defence|Ranged|Range|Magic|Prayer|Hitpoints|Slayer|Agility|Crafting|Fishing"
TOKEN = re.compile(r"\{\{SCP\|([A-Za-z]+)\|(\d+)|(?<![+-])\b(\d{1,2})\b|\[\[([A-Za-z]+)(?:\|[^\]]*)?\]\]|\b(" + SKILL_WORDS + r")\b")
LEVEL_OF = re.compile(r"(\[\[[A-Za-z]+(?:\|[^\]]*)?\]\]|\b(?:" + SKILL_WORDS + r")\b) level of (\d{1,2})")


def levels_in(segment):
    """Skill levels in "70 [[Attack]], [[Magic]], and [[Ranged]]"-style text; a level carries to later skills."""
    found, level = {}, None
    for scp_skill, scp_level, number_text, link, word in TOKEN.findall(segment):
        if scp_skill:
            skill, level = scp_skill, int(scp_level)
        elif number_text:
            level = int(number_text)
            continue
        else:
            skill = link or word
        skill = skill.lower()
        skill = "ranged" if skill == "range" else skill
        if level and skill in SKILL_NAMES and 1 < level <= 99:
            found[skill] = max(found.get(skill, 1), level)
    return found


def prose_requirements(text):
    """Wear levels from the first sentence that states them ("requires 70 Ranged to wield")."""
    if not text:
        return {}
    # Multi-word links name quests and items ("[[Dragon Slayer I]]"), never a skill.
    body = re.sub(r"<ref[^>]*>.*?</ref>|<ref[^>]*/>|\{\{(?!SCP)[^{}]*\}\}|'{2,}|\[\[[^\]|]*\s[^\]]*\]\]", "", text,
                  flags=re.S)
    for sentence in REQ_SENTENCE.findall(LEVEL_OF.sub(r"\2 \1", body)):
        if sentence.lstrip().startswith("|") or NOT_WEAR.search(sentence) or not GATE.search(sentence):
            continue
        verbs = list(VERB.finditer(sentence))
        if verbs:
            before, after = sentence[:verbs[-1].start()], sentence[verbs[-1].end():]
        elif SUBJECT.match(sentence):
            before, after = sentence, ""
        else:
            continue
        if sentence.lstrip().lower().startswith("to "):
            before, after = after, ""
        # The levels nearest the gate: "..., requiring 46 Ranged to" or "50 Defence ... are required to".
        parts = GATE.split(before)
        if CREATION.search(parts[-1]):
            continue
        found = levels_in(parts[-1]) or levels_in(" ".join(parts[:-1])) or levels_in(after)
        if found:
            return found
    return {}


def rule_for(item, slot):
    matches = [r for r in equipment_requirements.RULES if slot in r["slots"]
               and (item["id"] in r["ids"] if r["ids"] else re.fullmatch(r["pattern"], item["name"]))]
    if len(matches) > 1:
        raise ValueError(f"overlapping rules for {item['name']}: {[r['label'] for r in matches]}")
    return matches[0] if matches else None


def requirements(item, slot, cache_reqs, pages, page_name):
    """Cache params, prose and curated rules combined; the strictest level wins, rules may clear prose."""
    cache = {name: level for sid, level in cache_reqs.get(str(item["id"]), {}).get("reqs", {}).items()
             for name, skill in SKILLS.items() if int(sid) == skill}
    prose = prose_requirements(pages.get(page_name))
    rule = rule_for(item, slot)
    levels = collections.Counter()
    sources = []
    if cache:
        sources.append("cache")
        for skill, level in cache.items():
            levels[skill] = max(levels[skill], level)
    # The stricter of cache and prose wins: a few cache parameters lag the stated level (master wand 55 vs 60).
    combat_prose = {k: v for k, v in prose.items() if k in SKILLS}
    if combat_prose and not (rule and not rule["requirements"]):
        sources.append("wiki-text")
        for skill, level in combat_prose.items():
            levels[skill] = max(levels[skill], level)
    combat = 0
    if rule:
        sources.append("rule:" + rule["label"])
        if not rule["requirements"]:
            levels = collections.Counter({k: v for k, v in cache.items()})
        for skill, level in rule["requirements"].items():
            levels[skill] = max(levels[skill], level)
        combat = rule["combat"]
    if item["id"] == 13237:
        levels.pop("attack", None)  # the offline cache's pegasian Attack pair is not a wear gate
    for skill in SKILLS:
        item[skill + "_req"] = max(1, levels.get(skill, 1))
    item["combatReq"] = combat
    other = {k: v for k, v in prose.items() if k not in SKILLS}
    return sources, other


# --- Catalogue -----------------------------------------------------------------------------------------------

# Ornament kits that prefix the name; other prefixes fold only within a RuneLite variation group.
ORNAMENT_PREFIXES = ("holy ", "sanguine ", "radiant ", "twisted ")


def collapse(rows, variation_of):
    """One entry per item: stat-identical copies (charges, ornament kits, locked, poisoned, degraded, NMZ,
    recolours such as "holy scythe of vitur" or "red slayer helmet (i)") fold into the plain item.
    Same-stat items with their own effects ("blood ancient sceptre", "eldritch nightmare staff") stay."""
    buckets = collections.OrderedDict()
    for row in rows:
        slot = row.get("infobox_bonuses.equipment_slot")
        key = (SLOTS[slot], tuple(sorted(bonuses(row).items())), row.get("infobox_bonuses.combat_style"),
               row.get("infobox_bonuses.weapon_attack_speed"), slot == "2h")
        buckets.setdefault(key, collections.OrderedDict()).setdefault(norm(display_name(row)), []).append(row)

    def rank(row):
        name = display_name(row)
        return (name != norm(name), row.get("default_version") is None, len(name), min(ids_of(row)))
    out = []
    for groups in buckets.values():
        entries = {}
        for members in groups.values():
            members.sort(key=rank)
            entries[display_name(members[0])] = (members[0], {i for m in members for i in ids_of(m)})

        def related(name, base):
            row, base_row = entries[name][0], entries[base][0]
            group = variation_of.get(ids_of(row)[0])
            return name.endswith(" " + base) and (name.startswith(ORNAMENT_PREFIXES)
                                                  or group is not None and group == variation_of.get(ids_of(base_row)[0]))

        def base_of(name):
            base = next((b for b in entries if b != name and related(name, b)), None)
            return name if base is None else base_of(base)
        for name, (row, ids) in entries.items():
            base = base_of(name)
            if base != name:
                entries[base][1].update(ids)
        for name, (row, ids) in entries.items():
            if base_of(name) == name:
                out.append((row, sorted(ids)))
    return out


def ammo_names(weapon):
    n = weapon["name"]
    for pattern, ammo in FIXED_AMMO:
        if re.fullmatch(pattern, n):
            return lambda a: re.fullmatch(ammo, a) is not None
    for pattern, tier in BOW_TIER:
        if re.fullmatch(pattern, n):
            def arrows(a, tier=tier):
                m = re.fullmatch(r"(?:seeking )?(bronze|iron|steel|mithril|adamant|rune|amethyst|dragon) arrows?|"
                                 r"(?:seeking )?(broad) arrows", a)
                return m is not None and ARROW_TIER[m.group(1) or m.group(2)] <= tier
            return arrows
    for pattern, tier in CROSSBOW_TIER:
        if re.fullmatch(pattern, n):
            def bolts(a, tier=tier):
                m = re.fullmatch(r"(amethyst broad|broad|[a-z]+?)(?: dragon)? bolts(?: \(e\))?", a)
                if m is None or m.group(1) not in BOLT_TIER:
                    return False
                dragon_gem = " dragon bolts" in a
                return (64 if dragon_gem else BOLT_TIER[m.group(1)]) <= tier
            return bolts
    for pattern, tier in BLOWPIPE_TIER:
        if re.fullmatch(pattern, n):
            def darts(a, tier=tier):
                m = re.fullmatch(r"([a-z]+) dart", a)
                return m is not None and DART_TIER.get(m.group(1), 99) <= tier
            return darts
    return None


def build(refresh):
    raw = cached("wiki-equipment-raw.json", refresh, fetch_equipment)
    spell_rows = cached("wiki-spells-bucket.json", refresh, fetch_spells)
    variations_path = next((p for p in VARIATIONS if p.exists()), None)
    variation_of = {}
    if variations_path:
        for base, ids in json.loads(variations_path.read_text(encoding="utf-8")).items():
            for i in ids:
                variation_of[i] = base
    else:
        print("item_variations.json not found; variants are folded by name only", file=sys.stderr)
    cache_path = CACHE / "cache-reqs.json"
    cache_reqs = json.loads(cache_path.read_text(encoding="utf-8")) if cache_path.exists() else {}
    if not cache_reqs:
        print("cache-reqs.json not found; requirements come from Wiki text and rules only", file=sys.stderr)
    equipment_requirements.RULES.clear()
    equipment_requirements.families()

    rows = []
    for row in raw:
        ids = ids_of(row)
        slot = row.get("infobox_bonuses.equipment_slot")
        if not ids or slot not in SLOTS or row.get("removal_date") or DROP_PAGE.search(row["page_name_sub"]):
            continue
        name = display_name(row)
        if DROP_NAME.search(name) and not name.startswith("tome of"):
            continue
        stats = bonuses(row)
        if SLOTS[slot] == "weapon":
            category = (row.get("infobox_bonuses.combat_style") or "").lower()
            category = CATEGORY_ALIASES.get(category, category)
            if category not in STYLES or number(row.get("infobox_bonuses.weapon_attack_speed")) <= 0:
                continue
            if not any(stats.values()) and category not in ("staff", "bladed staff") and name not in KEEP_WITHOUT_BONUSES:
                continue
        elif not any(stats.values()) and name not in KEEP_WITHOUT_BONUSES:
            continue
        rows.append(row)

    titles = sorted({row["page_name"] for row in rows})
    categories = cached("wiki-item-categories.json", refresh, lambda: fetch_categories(titles))

    def dropped(row):
        return [c for c in categories.get(row["page_name"], []) if DROP_CATEGORY.search(c)]
    kept_groups = {variation_of.get(ids_of(row)[0]) for row in rows if not dropped(row)} - {None}
    rows = [row for row in rows if not dropped(row)
            or all(LEAGUE_CATEGORY.search(c) for c in dropped(row)) and variation_of.get(ids_of(row)[0]) in kept_groups]
    titles = sorted({row["page_name"] for row in rows})
    pages = cached("wiki-item-pages.json", refresh, lambda: fetch_pages(titles))
    missing = [t for t in titles if t not in pages]
    if missing and not refresh:
        pages.update(fetch_pages(missing))
        wiki_api.write_atomic(CACHE / "wiki-item-pages.json", json.dumps(pages))

    weapons, equipment, provenance = [], collections.defaultdict(list), {}
    names = collections.Counter()
    entries = collapse(rows, variation_of)
    for row, _ in entries:
        names[(display_name(row), SLOTS[row["infobox_bonuses.equipment_slot"]])] += 1
    for row, variants in entries:
        slot = SLOTS[row["infobox_bonuses.equipment_slot"]]
        name = display_name(row)
        if names[(name, slot)] > 1:
            name = disambiguate(row, name)
        if (any(DEADMAN_CATEGORY.search(c) for c in categories.get(row["page_name"], []))
                and not re.search(r"\((?:dmm|deadman)\)|^deadman's |^corrupted ", name)):
            name += " (dmm)"
        item = {"id": ids_of(row)[0], "name": name, "variants": variants,
                "members": row.get("is_members_only") is not None, "tradeable": row.get("tradeable") is not None,
                "price": 0, **bonuses(row)}
        sources, other = requirements(item, slot, cache_reqs, pages, row["page_name"])
        provenance[item["id"]] = {"sources": sources, "otherSkills": other, "page": row["page_name_sub"]}
        if slot == "weapon":
            category = CATEGORY_ALIASES.get(row["infobox_bonuses.combat_style"].lower(),
                                            row["infobox_bonuses.combat_style"].lower())
            item.update({"subcategory": category, "attack_speed": int(row["infobox_bonuses.weapon_attack_speed"]),
                         "two_handed": row["infobox_bonuses.equipment_slot"] == "2h", "styles": STYLES[category],
                         "ammunition": []})
            weapons.append(item)
        else:
            equipment[slot].append(item)

    # Ornament and recolour variants (same RuneLite variation, same stats) share the base item's wear gate,
    # which the cache and Wiki text usually state only for the base.
    shared = collections.defaultdict(list)
    for slot, items in [("weapon", weapons)] + list(equipment.items()):
        for item in items:
            key = variation_of.get(item["id"])
            if key:
                shared[(key, slot, tuple(item[k] for k in BONUS_FIELDS))].append(item)
    for members in shared.values():
        for m in members:
            # "holy scythe of vitur" inherits from "scythe of vitur"; "slayer's staff (e)" is a different item.
            bases = [n for n in members if n is not m and m["name"].endswith(n["name"])]
            for n in bases:
                for skill in SKILLS:
                    if m[skill + "_req"] < n[skill + "_req"]:
                        m[skill + "_req"] = n[skill + "_req"]
                        provenance[m["id"]]["sources"].append("variant of " + n["name"])
                m["combatReq"] = max(m["combatReq"], n["combatReq"])

    # Darts are thrown weapons and blowpipe ammunition; list them in both slots as the game data did.
    for weapon in list(weapons):
        if re.fullmatch(r"[a-z]+ dart", weapon["name"]):
            equipment["ammunition"].append({k: v for k, v in weapon.items()
                                            if k not in ("styles", "subcategory", "attack_speed", "two_handed",
                                                         "ammunition")})
    ammo = equipment["ammunition"]
    seen = collections.Counter((x["name"], s) for s, items in [("weapon", weapons)] + list(equipment.items())
                               for x in items)
    clashes = [k for k, v in seen.items() if v > 1]
    if clashes:
        print(f"duplicate names: {clashes}", file=sys.stderr)
    for weapon in weapons:
        accepts = ammo_names(weapon)
        if accepts:
            weapon["ammunition"] = sorted({a["id"] for a in ammo if accepts(a["name"])})

    spells = []
    for row in spell_rows:
        title = row["page_name"]
        if title not in SPELL_MAX:
            continue
        info = json.loads(row.get("json") or "{}")
        book = {"normal": "standard"}.get(row["spellbook"], row["spellbook"])
        spells.append({"name": title.lower(), "spellbook": book, "max": SPELL_MAX[title],
                       "level": int(info.get("level") or 1), "attack_speed": 5,
                       "spell_weapons": [str(i) for i in autocasters(title, book, weapons)]})
    spells.sort(key=lambda s: (s["level"], s["name"]))
    missing_spells = [t for t in SPELL_MAX if t.lower() not in {s["name"] for s in spells}]
    if missing_spells:
        raise ValueError(f"spells missing from infobox_spell: {sorted(missing_spells)}")

    prayers = {category: [{"name": n, "prayerbook": "standard", "boost": acc,
                           "strengthBoost": dmg if category in ("ranged", "magic") else 0,
                           "prayerLevel": level, "defenceLevel": defence}
                          for n, acc, dmg, level, defence in entries]
               for category, entries in PRAYERS.items()}
    potions = collections.defaultdict(list)
    for pid, name, boosts, attributes in POTIONS:
        for skill, (base, percent) in boosts.items():
            potions[skill].append({"id": pid, "name": name, "base_increase": base, "percentage_increase": percent,
                                   "skill": skill, "attributes": attributes})
    retrieved = datetime.datetime.fromtimestamp((CACHE / "wiki-equipment-raw.json").stat().st_mtime,
                                                datetime.timezone.utc).isoformat()
    payload = {"source": "Old School RuneScape Wiki infobox_item, infobox_bonuses and infobox_spell buckets and "
                         "item pages (CC BY-NC-SA 3.0); game cache item parameters; curated tables",
               "retrievedUtc": retrieved, "weapons": weapons, "equipment": equipment, "spells": spells,
               "prayers": prayers, "potions": potions}
    text = json.dumps(payload, separators=(",", ":"), sort_keys=True)
    wiki_api.write_atomic(OUT, gzip.compress(text.encode("utf-8")), "wb")
    (CACHE / "wiki-equipment-provenance.json").write_text(json.dumps(provenance, indent=1), encoding="utf-8")
    print(f"Wiki game data: {len(weapons)} weapons, {sum(len(v) for v in equipment.values())} other items, "
          f"{len(spells)} spells; {OUT.stat().st_size} bytes")
    return payload, provenance


# Versions that are the item's usual working state keep the plain name when two rows share it.
PLAIN_VERSIONS = {"", "charged", "active", "activated", "normal", "new", "undamaged"}


def disambiguate(row, name):
    """Name a variant whose in-game name another catalogue entry with different stats also uses."""
    page = row["page_name"].lower()
    version = (row.get("version_anchor") or row["page_name_sub"].partition("#")[2]).lower()
    if page != name and page.split(" (")[0] == name.split(" (")[0]:
        return page.replace("(the gauntlet)", "(gauntlet)")
    if version in PLAIN_VERSIONS:
        return name
    return f"{name} ({version})"


def autocasters(title, book, weapons):
    name = title.lower()
    out = []
    for w in weapons:
        if not any(",magic,magic" in s and s.startswith("spell") for s in w["styles"]):
            continue
        n = w["name"]
        if book == "standard" and ELEMENTAL.fullmatch(name):
            ok = not re.fullmatch(NO_ELEMENTAL, n) and (not re.fullmatch(WAVES_ONLY, n)
                                                         or re.search(r"wave|surge", name) is not None)
            ok = ok and w["subcategory"] in ("staff", "bladed staff")
        elif book == "ancient":
            ok = re.fullmatch(ANCIENT_STAVES, n) is not None
        elif book == "arceuus":
            ok = re.fullmatch(ARCEUUS_STAVES, n) is not None
        else:
            ok = title in SPECIAL_SPELLS and re.fullmatch(SPECIAL_SPELLS[title], n) is not None
        if ok:
            out.append(w["id"])
    return sorted(out)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--refresh", action="store_true")
    args = parser.parse_args()
    build(args.refresh)


if __name__ == "__main__":
    main()
