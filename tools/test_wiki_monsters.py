"""Offline tests for tools/wiki_monsters.py: python -m unittest discover -s tools -p "test_*.py" """
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import wiki_monsters  # noqa: E402


def row(**fields):
    base = {"page_name": "Test monster", "id": ["1"], "hitpoints": 100, "attack_level": 50, "defence_level": 50}
    return {**base, **fields}


class PoisonsTest(unittest.TestCase):
    def test_venom_link_is_venom(self):
        self.assertEqual("venom", wiki_monsters.poisons("Yes ([[venom]])"))
        self.assertEqual("venom", wiki_monsters.poisons("Yes (Venom)"))

    def test_yes_or_a_poison_max_hit_is_poison(self):
        self.assertEqual("poison", wiki_monsters.poisons("Yes"))
        self.assertEqual("poison", wiki_monsters.poisons("Yes (8)"))
        self.assertEqual("poison", wiki_monsters.poisons("8"))
        self.assertEqual("poison", wiki_monsters.poisons(["yes"]))

    def test_no_blank_or_missing_is_none(self):
        for value in ("No", "", "0", None, [], "N/A"):
            self.assertIsNone(wiki_monsters.poisons(value), value)

    def test_record_carries_the_field_only_when_the_monster_poisons(self):
        venomous = wiki_monsters.records(row(poisonous="Yes ([[venom]])"), set())[0]
        self.assertEqual("venom", venomous["poisons"])
        harmless = wiki_monsters.records(row(poisonous="No"), set())[0]
        self.assertNotIn("poisons", harmless)
        unknown = wiki_monsters.records(row(), set())[0]
        self.assertNotIn("poisons", unknown)


if __name__ == "__main__":
    unittest.main()
