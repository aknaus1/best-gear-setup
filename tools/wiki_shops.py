"""Generate the bundled shop-stock snapshot for the No GE setting from the OSRS Wiki.

Sources: the `storeline` bucket (one row per item a shop sells), the categories of each seller's page, and the
`infobox_item` bucket joined to `infobox_bonuses` (equipment pages and their item ids).

Output: src/main/resources/com/bestgearsetup/shop-stock.json, keyed by item id:
  * "coins": the cheapest coin price among the shops that stock the item;
  * "other": [currency, price] for an item no coin shop stocks, e.g. ["Tokkul", 60000].
Only item ids in the bundled equipment catalog (wiki-gamedata.json.gz, entries and their variants) are written.

Shop rules mirror Field Guide's generator, so both plugins agree on what a real shop is: rows paid in another game
mode's currency, rows whose note names a League, sellers in a Deadman, League, Beta, Grid Master, Quest Speedrunning,
shelved or discontinued category, reclaim-only sellers (Diango), rows the shop only buys (the 10,000,000,000 sell
price) and removed items are dropped. Unlike Field Guide, a row counts as stocked only when its default stock is at
least 1 or infinite: zero (sell-only) and unknown stock are dropped.

Usage (from the plugin folder): python -I tools/wiki_shops.py [--refresh]
Downloads need BGS_WIKI_CONTACT (sent in the User-Agent); without --refresh the cached downloads are reused.
"""
import argparse
import collections
import datetime
import gzip
import html
import json
import pathlib
import re
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parents[1]
CACHE = ROOT / "build" / "combat-reference"
CATALOG = ROOT / "src" / "main" / "resources" / "com" / "bestgearsetup" / "wiki-gamedata.json.gz"
OUT = ROOT / "src" / "main" / "resources" / "com" / "bestgearsetup" / "shop-stock.json"
TOOL = "wiki_shops.py"
sys.path.insert(0, str(ROOT / "tools"))
import wiki_api  # noqa: E402

STORELINE_FIELDS = ["page_name", "sold_by", "sold_item", "store_sell_price", "store_currency", "store_stock",
                    "store_notes"]
ITEM_FIELDS = ["page_name", "page_name_sub", "item_name", "item_id", "removal_date"]
# Minimum plausible download sizes; smaller responses are treated as failures and the cache is kept.
MINIMUM = {"wiki-storeline.json": 5000, "wiki-seller-categories.json": 300, "wiki-equipment-ids.json": 3000}

# --- Shop rules, as in Field Guide's tools/wiki_data.py -------------------------------------------------------
MODE_CURRENCIES = {"league points", "deadman points", "speedrun points", "event points"}
LEAGUE_NOTE = re.compile(r"League", re.I)
MODE_CATEGORY = re.compile(r" League$|^Deadman|^Grid Master$|^Quest Speedrunning$|^Beta|[Ss]helved content|"
                           r"^Discontinued content$")
RECLAIM_SELLERS = {"Diango"}
NOT_SOLD_PRICE = 10_000_000_000
INFINITE_STOCK = -1
SPACES = re.compile(r"\s+")


def norm_name(name):
    """A Wiki page name as stored: entities decoded, underscores as spaces, first letter upper case."""
    name = SPACES.sub(" ", html.unescape(str(name or "")).replace("_", " ")).strip()
    return name[:1].upper() + name[1:]


def plain(text):
    """Visible text of a short wikitext value: links as their label, no tags, bold or italics."""
    text = html.unescape(str(text or ""))
    text = re.sub(r"\[\[(?:[^\]|]*\|)?([^\]]*)\]\]", r"\1", text)
    text = re.sub(r"<[^>]*>", "", text).replace("'''", "").replace("''", "")
    return SPACES.sub(" ", text).strip()


def number(value):
    """An int from "1,000" or 1000; None when the value is not a whole number."""
    text = str(value if value is not None else "").replace(",", "").strip()
    return int(text) if re.fullmatch(r"\d+", text) else None


def parse_stock(text):
    """Default stock: an int, INFINITE_STOCK for "∞", None when unknown ("N/A", "", "Unknown")."""
    text = plain(text)
    if text.startswith("∞"):
        return INFINITE_STOCK
    return number(text)


def drop_reason(row, categories):
    """Why a storeline row is not a stocked shop sale, or None when it is."""
    seller = norm_name(row.get("sold_by"))
    currency = plain(row.get("store_currency"))
    price = number(row.get("store_sell_price"))
    stock = parse_stock(row.get("store_stock"))
    if currency.lower() in MODE_CURRENCIES:
        return "other game mode or event currency"
    if LEAGUE_NOTE.search(plain(row.get("store_notes"))):
        return "note names a League"
    if any(MODE_CATEGORY.search(c) for c in categories.get(seller, [])):
        return "seller is in an other-game-mode category"
    if seller in RECLAIM_SELLERS:
        return "reclaim-only seller (Diango)"
    if price is None or price >= NOT_SOLD_PRICE:
        return "not sold (the shop only buys it)"
    if stock is None:
        return "unknown default stock"
    if stock == 0:
        return "zero default stock"
    return None


class ItemIds:
    """Equipment item ids by Wiki name: exact page#version, then display name, then a single-item page."""

    def __init__(self, rows):
        self.by_sub = collections.defaultdict(set)
        self.by_name = collections.defaultdict(set)
        self.page_names = collections.defaultdict(set)
        self.by_page = collections.defaultdict(set)
        self.removed = set()
        for row in rows:
            ids = {int(v) for v in as_list(row.get("item_id")) if str(v).strip().isdigit()}
            sub, page, name = norm_name(row.get("page_name_sub")), norm_name(row.get("page_name")), plain(row.get("item_name"))
            if row.get("removal_date"):
                self.removed.add(sub.lower())
                continue
            self.by_sub[sub.lower()] |= ids
            self.by_name[name.lower()] |= ids
            self.page_names[page.lower()].add(name.lower())
            self.by_page[page.lower()] |= ids

    def find(self, sold_item):
        """(how, ids) for a storeline item name; how is None when it names no live equipment."""
        full = norm_name(sold_item).lower()
        page = full.partition("#")[0].strip()
        if full in self.by_sub:
            return "page#version", self.by_sub[full]
        if full in self.by_name:
            return "display name", self.by_name[full]
        if "#" not in full and len(self.page_names.get(page, ())) == 1:
            return "single-item page", self.by_page[page]
        return None, set()


def as_list(value):
    return value if isinstance(value, list) else [] if value is None else [value]


def catalog():
    """{entry id: (name, [ids])} of the bundled equipment catalog; ids are the entry's own id and its variants."""
    data = json.loads(gzip.decompress(CATALOG.read_bytes()).decode("utf-8"))
    entries = {}
    for item in [i for items in data["equipment"].values() for i in items] + data["weapons"]:
        ids = [item["id"]] + [v for v in item.get("variants") or [] if v != item["id"]]
        entries.setdefault(item["id"], (item["name"], ids))
    return entries


def currency_name(row):
    """The row's currency; a shop's own "Points" are named after the shop ("Slayer Rewards points")."""
    currency = plain(row.get("store_currency")) or "Coins"
    return f"{norm_name(row.get('sold_by'))} points" if currency.lower() == "points" else currency


def offers(rows, categories, items):
    """Stocked offers by item id: [(currency, price, seller, sold item)], plus drop and join statistics."""
    found = collections.defaultdict(list)
    stats = {"dropped": collections.Counter(), "joined": collections.Counter(), "unmatched": collections.Counter()}
    for row in rows:
        reason = drop_reason(row, categories)
        how, ids = (None, set()) if reason else items.find(row.get("sold_item"))
        if not reason and how is None and norm_name(row.get("sold_item")).lower() in items.removed:
            reason = "item removed from the game"
        if reason:
            stats["dropped"][reason] += 1
            continue
        if how is None:
            stats["unmatched"][norm_name(row.get("sold_item"))] += 1
            continue
        stats["joined"][how] += 1
        currency = currency_name(row)
        for item_id in ids:
            found[item_id].append((currency, number(row.get("store_sell_price")), norm_name(row.get("sold_by")),
                                   norm_name(row.get("sold_item"))))
    return found, stats


def snapshot(found, entries):
    """The coin and other-currency tables for the catalog's ids, and the folds: entries stocked only through a
    variant id."""
    known = {i for _, ids in entries.values() for i in ids}
    coins, other = {}, {}
    for item_id in sorted(found):
        if item_id not in known:
            continue
        sales = found[item_id]
        coin = [price for currency, price, _, _ in sales if currency.lower() == "coins"]
        if coin:
            coins[str(item_id)] = min(coin)
        else:
            currency, price = min((currency, price) for currency, price, _, _ in sales)
            other[str(item_id)] = [currency, price]
    folds = []
    for entry_id, (name, ids) in sorted(entries.items()):
        if str(entry_id) not in coins and str(entry_id) not in other:
            via = [i for i in ids if str(i) in coins or str(i) in other]
            if via:
                folds.append((name, entry_id, via))
    return coins, other, folds


def fetch_items():
    rows, offset = [], 0
    fields = ",".join(repr(f) for f in ITEM_FIELDS)
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
        time.sleep(wiki_api.BATCH_PAUSE)


def fetch_categories(titles):
    """Category names (without the namespace) of each title, 50 titles per request."""
    categories = {title: [] for title in titles}
    titles = sorted(titles)
    for i in range(0, len(titles), 50):
        extra = {}
        while True:
            data = wiki_api.get_json({"action": "query", "prop": "categories", "cllimit": "max", "formatversion": 2,
                                      "titles": "|".join(titles[i:i + 50]), **extra}, TOOL)
            pages = data.get("query", {}).get("pages") if isinstance(data, dict) else None
            if not isinstance(pages, list):
                raise wiki_api.WikiError(f"Query response has no page list: {json.dumps(data)[:300]}")
            for page in pages:
                categories.setdefault(page["title"], []).extend(
                    c["title"].split(":", 1)[1] for c in page.get("categories", []))
            if "continue" not in data:
                break
            extra = data["continue"]
            time.sleep(wiki_api.BATCH_PAUSE)
        time.sleep(wiki_api.BATCH_PAUSE)
    return categories


def cached(name, refresh, fetch):
    return wiki_api.cached(CACHE / name, refresh, fetch, minimum=MINIMUM[name])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--refresh", action="store_true", help="download again instead of using the cache")
    args = parser.parse_args()
    rows = cached("wiki-storeline.json", args.refresh,
                  lambda: wiki_api.bucket_all("storeline", STORELINE_FIELDS, TOOL))
    sellers = sorted({norm_name(r.get("sold_by")) for r in rows if r.get("sold_by")})
    categories = cached("wiki-seller-categories.json", args.refresh, lambda: fetch_categories(sellers))
    items = ItemIds(cached("wiki-equipment-ids.json", args.refresh, fetch_items))
    found, stats = offers(rows, categories, items)
    entries = catalog()
    coins, other, folds = snapshot(found, entries)
    retrieved = datetime.datetime.fromtimestamp((CACHE / "wiki-storeline.json").stat().st_mtime,
                                                datetime.timezone.utc).isoformat()
    out = {"source": "Old School RuneScape Wiki storeline and infobox_item buckets (CC BY-NC-SA 3.0), "
                     "generated by tools/wiki_shops.py",
           "retrievedUtc": retrieved,
           "coins": dict(sorted(coins.items(), key=lambda kv: int(kv[0]))),
           "other": dict(sorted(other.items(), key=lambda kv: int(kv[0])))}
    text = json.dumps(out, indent=0, separators=(",", ":")) + "\n"
    wiki_api.write_atomic(OUT, text)
    known = {i for _, ids in entries.values() for i in ids}
    sold_ids = collections.defaultdict(set)
    for item_id, sales in found.items():
        for sale in sales:
            sold_ids[sale[3]].add(item_id)
    not_catalog = sorted(name for name, ids in sold_ids.items() if not ids & known)
    print(f"{len(rows)} storeline rows; kept {sum(stats['joined'].values())} equipment rows "
          f"({dict(stats['joined'])}); dropped {dict(stats['dropped'])}")
    print(f"{sum(stats['unmatched'].values())} kept rows name no live equipment page "
          f"({len(stats['unmatched'])} distinct items: food, runes, tools and other non-equipment)")
    print(f"{len(coins)} item ids priced in coins, {len(other)} in other currencies only; "
          f"{OUT.stat().st_size} bytes written to {OUT.relative_to(ROOT)}")
    print(f"{len(not_catalog)} stocked equipment items have no catalog id: {', '.join(not_catalog)}")
    print(f"{len(folds)} catalog entries are stocked only through a variant id:")
    for name, entry_id, via in folds:
        print(f"  {name} ({entry_id}) via {via}")


if __name__ == "__main__":
    main()
