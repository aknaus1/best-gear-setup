package com.bestgearsetup.calc;

import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import java.util.regex.Pattern;

/** Verbatim copies of the name rules moved into name-lists.json, kept so the parity test pins the JSON to them. */
final class LegacyNameRules
{
	private static final Pattern METAL_CROSSBOW =
		Pattern.compile("(bronze|blurite|iron|steel|mithril|adamant|rune|dragon) crossbow.*");

	// ---- AttackReach
	static int baseRange(GearItem weapon)
	{
		String name = weapon.getLowerCombatName().replace("corrupted ", "");
		switch (weapon.getSubcategory())
		{
			case "powered staff":
				if (name.startsWith("tumeken's shadow"))
				{
					return 10;
				}
				if (name.startsWith("eye of ayak"))
				{
					return 6;
				}
				if (name.equals("dawnbringer") || name.startsWith("trident of") || name.startsWith("sanguinesti staff")
					|| name.startsWith("holy sanguinesti staff") || name.startsWith("warped sceptre")
					|| name.startsWith("bone staff") || name.startsWith("thammaron's sceptre")
					|| name.startsWith("accursed sceptre") || name.startsWith("zorya's tome"))
				{
					return 7;
				}
				return 0;
			case "bow":
				if (name.contains("longbow") || name.contains(" comp bow") || name.startsWith("dark bow")
					|| name.startsWith("twisted bow") || name.startsWith("crystal bow")
					|| name.startsWith("bow of faerdhinen") || name.equals("ogre bow"))
				{
					return 10;
				}
				if (name.startsWith("3rd age bow") || name.startsWith("craw's bow") || name.startsWith("webweaver bow"))
				{
					return 9;
				}
				if (name.startsWith("venator bow") || name.startsWith("eclipse atlatl"))
				{
					return 6;
				}
				if (name.contains("shortbow") || name.equals("seercull") || name.startsWith("scorching bow"))
				{
					return 7;
				}
				if (name.equals("comp ogre bow"))
				{
					return 5;
				}
				return 0;
			case "crossbow":
				if (name.equals("crossbow"))
				{
					return 10;
				}
				if (name.startsWith("phoenix crossbow"))
				{
					return 5;
				}
				if (name.startsWith("dorgeshuun crossbow") || name.startsWith("ascension crossbows"))
				{
					return 6;
				}
				if (name.startsWith("karil's crossbow") || name.startsWith("armadyl crossbow")
					|| name.startsWith("zaryte crossbow") || name.startsWith("hunters'"))
				{
					return 8;
				}
				if (name.equals("light ballista") || name.equals("heavy ballista"))
				{
					return 9;
				}
				if (name.startsWith("dragon hunter crossbow"))
				{
					return 7;
				}
				if (METAL_CROSSBOW.matcher(name).matches())
				{
					return 7;
				}
				return 0;
			case "chinchompa":
				return 9;
			case "thrown":
				if (name.startsWith("toxic blowpipe") || name.startsWith("camphor blowpipe")
					|| name.startsWith("ironwood blowpipe") || name.startsWith("rosewood blowpipe"))
				{
					return 5;
				}
				if (name.startsWith("tonalztics of ralos"))
				{
					return weapon.getId() == 28919 || name.contains("uncharged") ? 6 : 7;
				}
				if (name.contains(" dart"))
				{
					return 3;
				}
				if (name.contains(" knife") || name.contains("thrownaxe") || name.contains("throwing axe"))
				{
					return 4;
				}
				return 0;
			default:
				return 0;
		}
	}

	static boolean flying(Monster monster)
	{
		int id = monster.getId();
		return monster.hasAttribute("flying") || id >= 3162 && id <= 3165
			|| id >= 3169 && id <= 3183 || id == 7037 || id == 12443 || id == 6492 || id == 15697
			|| id == 7850 || id == 7852 || id == 7853 || id == 7884 || id == 7885
			|| id == 15623 || id == 15635 || reachNamed(monster, "kree'arra")
			|| reachNamed(monster, "wingman skree") || reachNamed(monster, "flockleader geerin")
			|| reachNamed(monster, "flight kilisa") || reachNamed(monster, "aviansie")
			|| reachNamed(monster, "reanimated aviansie") || reachNamed(monster, "dawn");
	}

	static boolean reachNamed(Monster monster, String target)
	{
		return EncounterDamage.versionOf(monster.getLowerName().replace('\u2019', '\''), target);
	}

	// ---- DragonfireProtection
	static boolean isProtectiveShield(GearItem item)
	{
		String n = item == null ? "" : item.getLowerCombatName();
		return n.startsWith("anti-dragon shield") || n.startsWith("dragonfire shield")
			|| n.startsWith("dragonfire ward") || n.startsWith("ancient wyvern shield")
			|| n.equals("elemental shield") || n.equals("mind shield");
	}

	// ---- EncounterDamage
	static boolean demonbaneMelee(String weapon)
	{
		return weapon.startsWith("silverlight") || weapon.startsWith("darklight") || weapon.startsWith("arclight")
			|| weapon.startsWith("emberlight") || weapon.startsWith("bone claws") || weapon.startsWith("burning claws");
	}

	static boolean abyssalWeapon(String weapon)
	{
		return weapon.startsWith("abyssal whip") || weapon.startsWith("abyssal tentacle")
			|| weapon.startsWith("abyssal dagger") || weapon.startsWith("abyssal bludgeon");
	}

	// ---- EncounterImmunities
	static boolean vampyrebaneMelee(String weapon, boolean tierThree)
	{
		return weapon.startsWith("ivandis flail")
			|| weapon.startsWith("blisterwood sickle") || weapon.startsWith("blisterwood flail")
			|| weapon.startsWith("hallowed flail") || weapon.equals("sunspear")
			|| !tierThree && weapon.startsWith("rod of ivandis");
	}

	static boolean silverMelee(String weapon)
	{
		return weapon.startsWith("silver sickle") || weapon.contains("emerald sickle")
			|| weapon.contains("ruby sickle") || weapon.equals("blessed axe") || weapon.equals("wolfbane")
			|| weapon.startsWith("silverlight") || weapon.startsWith("darklight") || weapon.startsWith("arclight")
			|| weapon.startsWith("ivandis flail") || weapon.startsWith("rod of ivandis")
			|| weapon.startsWith("blisterwood") || weapon.startsWith("hallowed flail");
	}

	static boolean broadAmmo(String ammo)
	{
		return ammo.startsWith("broad ") || ammo.startsWith("amethyst broad ") || ammo.startsWith("seeking broad ");
	}

	// ---- EquipmentEffects
	static int vampyrePercent(String weapon)
	{
		return weapon.startsWith("blisterwood flail") || weapon.startsWith("hallowed flail")
			|| weapon.startsWith("blisterwood stake") ? 125 : weapon.equals("sunspear") ? 150
			: weapon.startsWith("blisterwood sickle") ? 115 : weapon.startsWith("ivandis flail") ? 120
			: weapon.startsWith("rod of ivandis") ? 110 : -1;
	}

	static int pickaxeBonus(String weapon)
	{
		return weapon.startsWith("bronze") || weapon.startsWith("iron") ? 1
			: weapon.startsWith("steel") ? 6 : weapon.startsWith("black") ? 11
			: weapon.startsWith("mithril") ? 21 : weapon.startsWith("adamant") ? 31
			: weapon.startsWith("rune") || weapon.startsWith("gilded") ? 41 : 61;
	}

	static boolean twoHit(String name)
	{
		return name.startsWith("torag's hammers") || name.equals("sulphur blades")
			|| name.equals("glacial temotli") || name.equals("earthbound tecpatl");
	}

	static boolean ratWeapon(String name)
	{
		return name.equals("bone mace") || name.equals("bone shortbow") || name.equals("bone staff");
	}

	static boolean wildernessWeapon(String name, CombatClass cls)
	{
		switch (cls)
		{
			case MELEE:
				return name.startsWith("ursine chainmace") || name.startsWith("viggora's chainmace");
			case RANGED:
				return name.startsWith("craw's bow") || name.startsWith("webweaver bow");
			default:
				return name.startsWith("thammaron's sceptre") || name.startsWith("accursed sceptre");
		}
	}

	// ---- WeaponRules
	static int salamanderBonus(String name)
	{
		return name.equals("swamp lizard") ? 56 : name.equals("orange salamander") ? 59
			: name.equals("red salamander") ? 77 : name.equals("black salamander") ? 92
			: name.equals("tecu salamander") ? 104 : -1;
	}

	static boolean recoveryCapeName(String name)
	{
		return name.startsWith("ava's attractor") || name.startsWith("ava's accumulator")
			|| name.startsWith("ava's assembler") || name.startsWith("assembler max cape")
			|| name.startsWith("masori assembler") || name.startsWith("dizana's quiver")
			|| name.startsWith("blessed dizana's quiver");
	}

	// ---- StatusEffects
	static boolean venomHelm(String head)
	{
		return head.startsWith("serpentine helm") || head.startsWith("tanzanite helm") || head.startsWith("magma helm");
	}

	// ---- WildernessTargets
	private static final java.util.Set<String> WILD_NAMES = new java.util.HashSet<>(java.util.Arrays.asList(
		"mammoth", "lava dragon", "elder chaos druid",
		"callisto", "artio", "venenatis", "venenatis' spiderling (monster)", "spindel", "spindel's spiderling",
		"scorpia", "scorpia's guardian", "scorpia's offspring (monster)",
		"chaos elemental", "chaos fanatic", "crazy archaeologist"));
	private static final String[] WILD_PREFIXES = {"revenant ", "vet'ion", "calvar'ion"};
	private static final String[] WILD_TAGS = {"(vet'ion)", "(calvar'ion)"};

	static boolean wildernessOnly(String name)
	{
		if (name == null)
		{
			return false;
		}
		String lower = name.toLowerCase(java.util.Locale.ROOT);
		if (WILD_NAMES.contains(lower))
		{
			return true;
		}
		for (String prefix : WILD_PREFIXES)
		{
			if (lower.startsWith(prefix))
			{
				return true;
			}
		}
		for (String tag : WILD_TAGS)
		{
			if (lower.endsWith(tag))
			{
				return true;
			}
		}
		return false;
	}

	// ---- Optimizer
		static final String[] SPECIAL_PREFIXES = {
		"void ", "elite void", "slayer helmet", "black mask", "salve amulet", "obsidian helmet",
		"obsidian platebody", "obsidian platelegs", "berserker necklace", "inquisitor's", "crystal helm",
		"crystal body", "crystal legs", "eclipse moon",
		"blood moon", "dharok's", "verac's", "ahrim's", "karil's", "amulet of the damned", "crystal blessing",
		"virtus", "tome of", "chaos gauntlets", "brimstone ring", "confliction gauntlets", "amulet of avarice",
		"efaritay's aid", "elemental amulet", "amulet of air", "amulet of water", "amulet of earth", "amulet of fire",
		"saradomin cape", "guthix cape", "zamorak cape", "imbued saradomin", "imbued guthix", "imbued zamorak",
		"serpentine helm", "tanzanite helm", "magma helm", "dizana's", "blessed dizana's",
	};

	static boolean specialName(String n)
	{
		for (String p : SPECIAL_PREFIXES)
		{
			if (n.startsWith(p))
			{
				return true;
			}
		}
		return false;
	}

	static boolean magicOnly(String name)
	{
		return name.startsWith("virtus ") || name.startsWith("tome of ") || name.startsWith("elemental amulet")
			|| name.startsWith("amulet of air") || name.startsWith("amulet of water") || name.startsWith("amulet of earth")
			|| name.startsWith("amulet of fire") || name.startsWith("chaos gauntlets")
			|| name.startsWith("confliction gauntlets") || name.startsWith("brimstone ring");
	}

	// ---- ItemCosts
	static final java.util.Map<String, Long> REPAIR_COSTS = repairCosts();

		private static java.util.Map<String, Long> repairCosts()
	{
		java.util.Map<String, Long> costs = new java.util.HashMap<>();
		repair(costs, 150_000, "fire cape", "fire max cape", "fighter torso");
		repair(costs, 225_000, "infernal cape", "infernal max cape");
		repair(costs, 240_000, "ava's assembler", "assembler max cape", "masori assembler",
			"masori assembler max cape", "dragon defender");
		repair(costs, 96_000, "imbued guthix cape", "imbued saradomin cape", "imbued zamorak cape");
		repair(costs, 99_000, "imbued guthix max cape", "imbued saradomin max cape", "imbued zamorak max cape");
		repair(costs, 1_000, "bronze defender");
		repair(costs, 2_000, "iron defender");
		repair(costs, 2_500, "steel defender");
		repair(costs, 5_000, "black defender");
		repair(costs, 15_000, "mithril defender");
		repair(costs, 25_000, "adamant defender");
		repair(costs, 35_000, "rune defender");
		repair(costs, 600_000, "avernic defender");
		repair(costs, 160_000, "void melee helm", "void mage helm", "void ranger helm");
		repair(costs, 180_000, "void knight top", "void knight robe");
		repair(costs, 250_000, "elite void top", "elite void robe");
		repair(costs, 120_000, "void knight gloves");
		repair(costs, 20_000, "void knight mace", "penance skirt");
		repair(costs, 25_000, "guthix halo", "saradomin halo", "zamorak halo", "armadyl halo", "bandos halo",
			"seren halo", "ancient halo", "brassica halo");
		repair(costs, 45_000, "fighter hat", "ranger hat", "healer hat");
		repair(costs, 40_005, "runner hat");
		repair(costs, 10_500, "barronite mace");
		repair(costs, 200_000, "blood ancient sceptre", "smoke ancient sceptre", "ice ancient sceptre",
			"shadow ancient sceptre");
		repair(costs, 270_000, "dizana's quiver");
		repair(costs, 400_000, "blessed dizana's quiver", "dizana's max cape");
		return java.util.Collections.unmodifiableMap(costs);
	}

	private static void repair(java.util.Map<String, Long> costs, long fee, String... names)
	{
		for (String name : names)
		{
			costs.put(name, fee);
		}
	}

	static long repairFee(String name)
	{
		if (name.startsWith("decorative armour"))
		{
			return 5_000;
		}
		return REPAIR_COSTS.getOrDefault(name, 0L);
	}

	// ---- GearItem
	static boolean leagueName(String lower)
	{
		for (String name : new String[]{"the dogsword", "drygore blowpipe", "amulet of the monarchs", "emperor ring", "devil's element", "nature's reprisal", "gloves of the damned", "crystal blessing", "sunlight spear", "sunlit bracers", "thunder khopesh", "thousand-dragon ward"})
		{
			if (lower.equals(name) || lower.startsWith(name + " ("))
			{
				return true;
			}
		}
		return false;
	}
}
