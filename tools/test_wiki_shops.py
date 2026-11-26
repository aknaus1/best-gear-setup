"""Offline tests for tools/wiki_shops.py: python -m unittest discover -s tools -p "test_*.py" """
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import wiki_shops  # noqa: E402


def row(item="Rune platebody", seller="Oziach", price="65000", currency="Coins", stock="2", notes=""):
    return {"sold_item": item, "sold_by": seller, "store_sell_price": price, "store_currency": currency,
            "store_stock": stock, "store_notes": notes}


class StockRuleTest(unittest.TestCase):
    def test_stock_must_be_at_least_one_or_infinite(self):
        self.assertEqual(wiki_shops.parse_stock("5"), 5)
        self.assertEqual(wiki_shops.parse_stock("1,000"), 1000)
        self.assertEqual(wiki_shops.parse_stock("∞"), wiki_shops.INFINITE_STOCK)
        self.assertIsNone(wiki_shops.parse_stock("N/A"))
        self.assertIsNone(wiki_shops.drop_reason(row(stock="1"), {}))
        self.assertIsNone(wiki_shops.drop_reason(row(stock="∞"), {}))
        self.assertEqual(wiki_shops.drop_reason(row(stock="0"), {}), "zero default stock")
        self.assertEqual(wiki_shops.drop_reason(row(stock="N/A"), {}), "unknown default stock")
        self.assertEqual(wiki_shops.drop_reason(row(stock=""), {}), "unknown default stock")

    def test_shop_rules_match_field_guide(self):
        self.assertIsNotNone(wiki_shops.drop_reason(row(currency="League points"), {}))
        self.assertIsNotNone(wiki_shops.drop_reason(row(currency="Deadman points"), {}))
        self.assertIsNotNone(wiki_shops.drop_reason(row(notes="Only in [[Leagues V|Raging Echoes League]]"), {}))
        self.assertIsNotNone(wiki_shops.drop_reason(row(seller="Nigel"), {"Nigel": ["Deadman Mode"]}))
        self.assertIsNotNone(wiki_shops.drop_reason(row(seller="Diango"), {}))
        self.assertIsNotNone(wiki_shops.drop_reason(row(price="10,000,000,000"), {}))
        # Other currencies are real shops.
        self.assertIsNone(wiki_shops.drop_reason(row(currency="Tokkul"), {"Oziach": ["Shops"]}))


class PricingTest(unittest.TestCase):
    ITEMS = wiki_shops.ItemIds([
        {"page_name": "Rune platebody", "page_name_sub": "Rune platebody", "item_name": "Rune platebody",
         "item_id": ["1127"]},
        {"page_name": "Toktz-xil-ul", "page_name_sub": "Toktz-xil-ul", "item_name": "Toktz-xil-ul",
         "item_id": ["6522"]},
        {"page_name": "Black mask", "page_name_sub": "Black mask#(10)", "item_name": "Black mask (10)",
         "item_id": ["8901"]},
        {"page_name": "Black mask", "page_name_sub": "Black mask#Uncharged", "item_name": "Black mask",
         "item_id": ["8921"]},
        {"page_name": "Old cape", "page_name_sub": "Old cape", "item_name": "Old cape", "item_id": ["1"],
         "removal_date": "1 January 2020"},
    ])
    ENTRIES = {1127: ("rune platebody", [1127, 2615]), 6522: ("toktz-xil-ul", [6522]),
               8901: ("black mask (10)", [8901]), 8921: ("black mask", [8921])}

    def test_names_join_by_page_version_then_display_name(self):
        self.assertEqual(self.ITEMS.find("Black mask#(10)"), ("page#version", {8901}))
        self.assertEqual(self.ITEMS.find("Black mask"), ("display name", {8921}))
        self.assertEqual(self.ITEMS.find("Rune_platebody"), ("page#version", {1127}))
        self.assertEqual(self.ITEMS.find("Lobster"), (None, set()))

    def test_cheapest_coin_price_wins_and_other_currencies_are_kept_when_no_coin_shop_stocks_it(self):
        rows = [row(price="65000"), row(seller="Horvik", price="70000"), row(seller="Tz shop", currency="Tokkul"),
                row(item="Toktz-xil-ul", seller="TzHaar-Hur-Tel", price="375", currency="Tokkul"),
                row(item="Toktz-xil-ul", seller="Oziach", price="10", stock="0"),
                row(item="Old cape"), row(item="Lobster")]
        found, stats = wiki_shops.offers(rows, {}, self.ITEMS)
        coins, other, folds = wiki_shops.snapshot(found, self.ENTRIES)
        self.assertEqual(coins, {"1127": 65000})
        self.assertEqual(other, {"6522": ["Tokkul", 375]})
        self.assertEqual(folds, [])
        self.assertEqual(stats["dropped"]["item removed from the game"], 1)
        self.assertEqual(stats["unmatched"]["Lobster"], 1)

    def test_shop_points_are_named_after_the_shop(self):
        self.assertEqual(wiki_shops.currency_name(row(seller="Slayer Rewards", currency="Points")),
                         "Slayer Rewards points")
        self.assertEqual(wiki_shops.currency_name(row(currency="Castle wars ticket")), "Castle wars ticket")

    def test_an_entry_stocked_only_through_a_variant_is_listed_as_a_fold(self):
        found, _ = wiki_shops.offers([row()], {}, self.ITEMS)
        coins, _, folds = wiki_shops.snapshot(found, {2615: ("rune platebody (g)", [2615, 1127])})
        self.assertEqual(coins, {"1127": 65000})
        self.assertEqual(folds, [("rune platebody (g)", 2615, [1127])])


if __name__ == "__main__":
    unittest.main()
