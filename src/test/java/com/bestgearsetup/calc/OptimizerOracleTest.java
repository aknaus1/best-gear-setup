package com.bestgearsetup.calc;

import static org.junit.Assert.assertTrue;
import com.bestgearsetup.DiaryRewards;
import com.bestgearsetup.ItemCosts;
import com.bestgearsetup.OwnershipRules;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Spell;
import com.bestgearsetup.data.WikiGameData;
import com.bestgearsetup.data.WikiMonsters;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Compares the Best-depth search with an independent exhaustive search over small generated banks drawn from the
 * real catalogue: every eligible weapon, allowed stance / spell, compatible ammunition and combination of the other
 * slots. Ownership, budgets, the expensive-item cap, locks, exclusions, experience filters and ammunition quantities
 * are re-implemented here from their documented rules rather than taken from the optimizer. Each bank is searched in
 * two item orders.
 *
 * <p>Legality, the oracle's completeness and the candidate pruning are exact checks. The search itself is a bounded
 * local search, so a few banks whose optimum needs three or four equipment changes at once still end below it; those
 * are listed and bounded rather than forbidden (see {@link #MAX_MISS_RATE}). Set BGS_ORACLE_CASES and
 * BGS_ORACLE_FROM to run another range of generated banks, and BGS_ORACLE_VERBOSE to print every bank.
 */
public class OptimizerOracleTest
{
	/** Absolute: the accuracy and max-hit metrics weight their primary value by 1e6, leaving DPS as the tie-break. */
	private static final double EPS = 1e-7;
	/**
	 * Share of banks allowed to end at a local optimum below the exhaustive one, and the largest DPS shortfall. On
	 * banks 1-6,000 the search that picked the stance / spell apart from the gear (with single-stat pruning) missed
	 * 60 (1.0%), some by over 5%; this one misses 7 (0.12%), by at most 1.85%.
	 */
	private static final double MAX_MISS_RATE = 0.005;
	private static final double MAX_MISS_GAP = 0.025;
	private static final Slot[] ARMOUR = {
		Slot.HEAD, Slot.CAPE, Slot.NECK, Slot.BODY, Slot.LEGS, Slot.HANDS, Slot.FEET, Slot.RING, Slot.SHIELD,
	};
	private static final AttackStyle.Type[] TABS = {
		AttackStyle.Type.STAB, AttackStyle.Type.SLASH, AttackStyle.Type.CRUSH, AttackStyle.Type.RANGED,
		AttackStyle.Type.MAGIC, AttackStyle.Type.ATLATL,
	};
	private static final String[] MONSTERS = {
		"chaos dwarf", "hill giant", "black demon (level 172)", "ankou (level 75)", "general graardor",
		"fire giant (level 86)", "black knight (hostile, male)",
	};
	/** Effect-bearing sets, injected whole so their bonuses can apply. */
	private static final String[][] MELEE_SETS = {
		{"void melee helm", "void knight top", "void knight robe", "void knight gloves"},
		{"obsidian helmet", "obsidian platebody", "obsidian platelegs", "toktz-xil-ak", "berserker necklace"},
		{"dharok's helm", "dharok's platebody", "dharok's platelegs", "dharok's greataxe"},
		{"verac's helm", "verac's brassard", "verac's plateskirt", "verac's flail"},
		{"inquisitor's great helm", "inquisitor's hauberk", "inquisitor's plateskirt", "inquisitor's mace"},
		{"blood moon helm", "blood moon chestplate", "blood moon tassets", "dual macuahuitl"},
		{"salve amulet", "salve amulet (e)", "amulet of strength", "amulet of glory"},
		{"rune defender", "dragon defender", "fire cape", "mythical cape"},
	};
	private static final String[][] RANGED_SETS = {
		{"void ranger helm", "void knight top", "void knight robe", "void knight gloves"},
		{"karil's coif", "karil's leathertop", "karil's leatherskirt", "karil's crossbow", "amulet of the damned"},
		{"crystal helm", "crystal body", "crystal legs", "bow of faerdhinen"},
		{"toxic blowpipe", "salve amulet(ei)", "necklace of anguish"},
	};
	private static final String[][] ATLATL_SETS = {
		{"eclipse moon helm", "eclipse moon chestplate", "eclipse moon tassets", "eclipse atlatl"},
		{"amulet of strength", "berserker ring (i)", "fire cape", "mythical cape"},
	};
	private static final String[][] MAGIC_SETS = {
		{"void mage helm", "void knight top", "void knight robe", "void knight gloves"},
		{"ahrim's hood", "ahrim's robetop", "ahrim's robeskirt", "ahrim's staff", "amulet of the damned"},
		{"tome of fire", "staff of fire", "occult necklace", "imbued saradomin cape"},
		{"blue moon helm", "blue moon chestplate", "blue moon tassets", "blue moon spear"},
	};
	private static final long[] PRICES = {
		0, 500, 12_000, 99_999, 100_000, 100_001, 900_000, 4_000_000, ItemCosts.UNKNOWN,
	};
	private static final int[] DEGRADED = {29047, 29043, 29045, 29035, 29031, 29033};

	private static GameData catalogue;
	private static WikiMonsters monsters;

	@BeforeClass
	public static void load() throws Exception
	{
		Gson gson = new Gson();
		OwnershipRules.init(gson);
		catalogue = WikiGameData.get(gson).gameData(gson);
		catalogue.addVariantItems(gson, id ->
		{
			switch (id)
			{
				case 29035: return "Eclipse moon helm";
				case 29031: return "Eclipse moon chestplate";
				case 29033: return "Eclipse moon tassets";
				case 29047: return "Blood moon helm";
				case 29043: return "Blood moon chestplate";
				case 29045: return "Blood moon tassets";
				default: return null;
			}
		}, id -> false);
		monsters = WikiMonsters.get(gson);
	}

	@Test
	public void bestDepthMatchesExhaustiveSearch()
	{
		String env = System.getenv("BGS_ORACLE_CASES");
		int cases = env == null ? 1500 : Integer.parseInt(env);
		String from = System.getenv("BGS_ORACLE_FROM");
		int first = from == null ? 1 : Integer.parseInt(from);
		boolean verbose = System.getenv("BGS_ORACLE_VERBOSE") != null;
		List<String> errors = new ArrayList<>();
		List<String> misses = new ArrayList<>();
		Set<Integer> missedBanks = new HashSet<>();
		double worstGap = 0;
		long evaluations = 0;
		for (int seed = first; seed < first + cases; seed++)
		{
			Case c = generate(seed);
			Oracle oracle = new Oracle(c);
			Oracle.Best best = oracle.search();
			evaluations += oracle.evaluations;
			if (verbose)
			{
				System.out.println("seed " + seed + " " + c + " evaluations " + oracle.evaluations + " best " + best.score
					+ "\n    " + describe(best.loadout));
			}
			for (int order = 0; order < 2; order++)
			{
				List<GearItem> items = new ArrayList<>(c.pool);
				if (order > 0)
				{
					Collections.shuffle(items, new Random(seed * 31L + order));
				}
				GameData data = TestData.gameData(items, catalogue.getSpells());
				Optimizer optimizer = new Optimizer(data, c.ctx, c.settings, c.owned::contains,
					id -> c.owned.contains(id) ? c.quantities.getOrDefault(id, Long.MAX_VALUE) : 0, c::price);
				String label = "seed " + seed + " order " + order + " " + c + ": ";
				if (order == 0)
				{
					// Pruning must keep an optimum: compare the optimum over the kept candidates.
					Oracle pruned = new Oracle(c);
					pruned.restrict(optimizer.candidates(c.cls));
					Oracle.Best kept = pruned.search();
					if (kept.score < best.score - EPS)
					{
						errors.add(label + String.format(Locale.ROOT, "pruning lost the optimum: %.9f of %.9f",
							kept.score, best.score) + "\n    oracle " + describe(best.loadout));
					}
				}
				List<SetupResult> results = optimizer.optimize(c.cls, () -> false);
				if (results.isEmpty())
				{
					if (best.loadout != null)
					{
						errors.add(label + "no result, oracle " + best.score + "\n    oracle " + describe(best.loadout));
					}
					continue;
				}
				Loadout found = results.get(0).getLoadout();
				double score = oracle.metric(DpsCalculator.calculate(found, c.ctx));
				String detail = "\n    found  " + describe(found) + "\n    oracle " + describe(best.loadout);
				String illegal = oracle.illegal(found);
				if (illegal != null)
				{
					errors.add(label + "illegal result (" + illegal + ")" + detail);
				}
				else if (best.loadout == null || score > best.score + EPS)
				{
					errors.add(label + String.format(Locale.ROOT, "score %.9f above oracle %.9f (oracle incomplete)",
						score, best.score) + detail);
				}
				else if (score < best.score - EPS)
				{
					double gap = 1 - dps(found, c) / dps(best.loadout, c);
					worstGap = Math.max(worstGap, gap);
					missedBanks.add(seed);
					misses.add(label + String.format(Locale.ROOT, "score %.9f below oracle %.9f (DPS %.4f%% lower)",
						score, best.score, 100 * gap) + detail);
				}
			}
		}
		System.out.println("oracle cases=" + cases + " evaluations=" + evaluations + " errors=" + errors.size()
			+ " missed banks=" + missedBanks.size() + String.format(Locale.ROOT, " worst DPS gap=%.4f%%", 100 * worstGap));
		errors.forEach(System.out::println);
		misses.forEach(System.out::println);
		assertTrue(String.join("\n", errors), errors.isEmpty());
		assertTrue(missedBanks.size() + " of " + cases + " banks ended below the exhaustive optimum:\n"
			+ String.join("\n", misses), missedBanks.size() <= MAX_MISS_RATE * cases);
		assertTrue(String.format(Locale.ROOT, "a search ended %.2f%% below the optimum DPS:%n", 100 * worstGap)
			+ String.join("\n", misses), worstGap <= MAX_MISS_GAP);
	}

	private static double dps(Loadout l, Case c)
	{
		return DpsCalculator.calculate(l, c.ctx).getDps();
	}

	private static String describe(Loadout l)
	{
		if (l == null)
		{
			return "none";
		}
		StringBuilder sb = new StringBuilder();
		sb.append(l.getStyle()).append(l.getSpell() == null ? "" : " " + l.getSpell().getName());
		for (Slot s : Slot.values())
		{
			if (l.get(s) != null)
			{
				sb.append(", ").append(s).append('=').append(l.get(s).getName()).append(" #").append(l.get(s).getId());
			}
		}
		if (l.getLoadedAmmo() != null)
		{
			sb.append(", loaded=").append(l.getLoadedAmmo().getName());
		}
		return sb.toString();
	}

	// ------------------------------------------------------------ generated cases

	private static final class Case
	{
		AttackStyle.Type tab;
		CombatClass cls;
		CombatContext ctx;
		PlayerLevels levels;
		OptimizerSettings settings;
		final List<GearItem> pool = new ArrayList<>();
		final Set<Integer> owned = new HashSet<>();
		final Map<Integer, Long> quantities = new HashMap<>();
		final Map<Integer, Long> prices = new HashMap<>();
		String monster;

		long price(GearItem item)
		{
			return prices.getOrDefault(item.getId(), ItemCosts.UNKNOWN);
		}

		@Override
		public String toString()
		{
			return tab + " vs " + monster + " " + settings.getMode() + (settings.getMode() == SearchMode.BUDGET
				? " " + settings.getBudget() : "") + (settings.isWildernessRiskLimited()
				? " cap " + settings.getMaxExpensiveItems() : "") + " calc " + settings.getCalcMode()
				+ (settings.getLocks().isEmpty() ? "" : " locks " + settings.getLocks().keySet())
				+ (settings.getExcluded().isEmpty() ? "" : " excl " + settings.getExcluded())
				+ (settings.getAmmoCount() > 0 ? " ammo " + settings.getAmmoCount() : "")
				+ " xp " + (settings.isAttackXp() ? "A" : "") + (settings.isStrengthXp() ? "S" : "")
				+ (settings.isDefenceXp() ? "D" : "") + " pool " + pool.size();
		}
	}

	private static Case generate(int seed)
	{
		Random r = new Random(seed);
		Case c = new Case();
		c.tab = TABS[(seed - 1) % TABS.length];
		c.cls = c.tab == AttackStyle.Type.MAGIC ? CombatClass.MAGIC
			: c.tab == AttackStyle.Type.RANGED || c.tab == AttackStyle.Type.ATLATL ? CombatClass.RANGED : CombatClass.MELEE;
		c.monster = MONSTERS[r.nextInt(MONSTERS.length)];
		Monster monster = monsters.monster(c.monster);
		c.levels = new PlayerLevels(level(r), level(r), level(r), level(r), level(r), level(r), level(r), level(r));
		boolean wilderness = c.monster.equals("chaos dwarf") || r.nextInt(4) == 0;
		c.ctx = new CombatContext(monster, c.levels, false, r.nextBoolean(), r.nextBoolean() ? TestData.piety() : null)
			.withModifiers(CombatModifiers.builder().wilderness(wilderness).build());

		// Weapons for the tab, then 4-6 armour slots of relevant items; magic keeps its spell lists small.
		Set<GearItem> pool = new LinkedHashSet<>();
		List<GearItem> weapons = new ArrayList<>();
		for (GearItem w : catalogue.getItems(Slot.WEAPON))
		{
			// Mostly wearable picks; a few unwearable ones exercise the requirement filter.
			if (plain(w) && servesTab(w, c.tab) && (c.levels.canEquip(w) || r.nextInt(8) == 0))
			{
				weapons.add(w);
			}
		}
		if (weapons.isEmpty())
		{
			for (GearItem w : catalogue.getItems(Slot.WEAPON))
			{
				if (plain(w) && servesTab(w, c.tab))
				{
					weapons.add(w);
				}
			}
		}
		int weaponCount = 2 + r.nextInt(c.cls == CombatClass.MAGIC ? 2 : 3);
		for (int i = 0; i < weaponCount; i++)
		{
			pool.add(weapons.get(r.nextInt(weapons.size())));
		}
		String[][] sets = c.tab == AttackStyle.Type.MAGIC ? MAGIC_SETS : c.tab == AttackStyle.Type.RANGED ? RANGED_SETS
			: c.tab == AttackStyle.Type.ATLATL ? ATLATL_SETS : MELEE_SETS;
		if (r.nextInt(5) < 3)
		{
			for (String name : sets[r.nextInt(sets.length)])
			{
				GearItem item = named(name, r);
				if (item != null && (item.getSlot() != Slot.WEAPON || servesTab(item, c.tab)))
				{
					pool.add(item);
				}
			}
		}
		List<Slot> slots = new ArrayList<>(Arrays.asList(ARMOUR));
		Collections.shuffle(slots, r);
		int active = (c.cls == CombatClass.MAGIC ? 4 : 5) + r.nextInt(3);
		for (Slot slot : slots.subList(0, active))
		{
			List<GearItem> relevant = new ArrayList<>();
			for (GearItem item : catalogue.getItems(slot))
			{
				if (plain(item) && relevant(item, c.tab) && (c.levels.canEquip(item) || r.nextInt(8) == 0))
				{
					relevant.add(item);
				}
			}
			int n = 1 + r.nextInt(slot == Slot.SHIELD || c.cls == CombatClass.MAGIC ? 2 : 3);
			for (int i = 0; i < n && !relevant.isEmpty(); i++)
			{
				pool.add(relevant.get(r.nextInt(relevant.size())));
			}
		}
		// Ammunition for every weapon that needs it.
		for (GearItem w : new ArrayList<>(pool))
		{
			if (w.getSlot() == Slot.WEAPON && !w.getAmmunition().isEmpty())
			{
				List<Integer> ids = new ArrayList<>(w.getAmmunition());
				Collections.shuffle(ids, r);
				int added = 0;
				for (int id : ids)
				{
					GearItem a = catalogue.getItem(Slot.AMMO, id);
					if (a != null && plain(a) && added < 2)
					{
						pool.add(a);
						added++;
					}
				}
			}
		}
		trim(pool, c, r);
		c.pool.addAll(pool);

		for (GearItem item : c.pool)
		{
			c.prices.put(item.getId(), PRICES[r.nextInt(PRICES.length)]);
			if (r.nextInt(10) < 7)
			{
				c.owned.add(item.getId());
				if (WeaponRules.consumedPerAttack(item))
				{
					c.quantities.put(item.getId(), r.nextBoolean() ? 50L : 5_000L);
				}
			}
		}

		OptimizerSettings.OptimizerSettingsBuilder s = OptimizerSettings.builder().depth(SearchDepth.BEST)
			.styles(EnumSet.of(c.tab)).spellbooks(new HashSet<>(Arrays.asList("standard", "ancient", "arceuus")))
			.resultsPerClass(1);
		switch (r.nextInt(4))
		{
			case 0:
				s.mode(SearchMode.OWNED_ONLY);
				break;
			case 1:
				s.mode(SearchMode.BUDGET).budget(0);
				break;
			case 2:
				s.mode(SearchMode.BUDGET).budget(r.nextBoolean() ? 100_000 : 5_000_000);
				break;
			default:
				s.mode(SearchMode.UNLIMITED);
				break;
		}
		if (wilderness || r.nextInt(3) == 0)
		{
			s.wildernessRiskLimited(true).maxExpensiveItems(r.nextInt(6) == 0 ? 0 : 1 + r.nextInt(4))
				.expensiveItemThreshold(100_000);
		}
		int calc = r.nextInt(8);
		s.calcMode(calc == 0 ? CalcMode.MAX_HIT : calc == 1 ? CalcMode.ACCURACY : CalcMode.DPS);
		s.attackXp(r.nextInt(5) > 0).strengthXp(r.nextInt(5) > 0).defenceXp(r.nextInt(5) > 0);
		if (r.nextInt(4) == 0)
		{
			s.ammoCount(r.nextBoolean() ? 100 : 1_000);
		}
		if (r.nextInt(4) == 0)
		{
			Map<Slot, SlotLock> locks = new EnumMap<>(Slot.class);
			GearItem item = c.pool.get(r.nextInt(c.pool.size()));
			if (item.getSlot() != Slot.WEAPON && item.getSlot() != Slot.AMMO)
			{
				locks.put(item.getSlot(), r.nextBoolean() ? SlotLock.item(item.getId()) : SlotLock.empty());
			}
			s.locks(locks);
		}
		if (r.nextInt(4) == 0)
		{
			s.excluded(Collections.singleton(c.pool.get(r.nextInt(c.pool.size())).getId()));
		}
		c.settings = s.build();
		return c;
	}

	private static int level(Random r)
	{
		return 60 + r.nextInt(40);
	}

	/** Bound the exhaustive search: drop armour from the largest slot until the enumeration is small. */
	private static void trim(Set<GearItem> pool, Case c, Random r)
	{
		while (true)
		{
			Map<Slot, Integer> counts = new EnumMap<>(Slot.class);
			int options = 0;
			for (GearItem item : pool)
			{
				if (item.getSlot() == Slot.WEAPON)
				{
					options += c.cls == CombatClass.MAGIC ? 40 : 4;
				}
				else if (item.getSlot() != Slot.AMMO)
				{
					counts.merge(item.getSlot(), 1, Integer::sum);
				}
			}
			long product = 1;
			Slot largest = null;
			for (Map.Entry<Slot, Integer> e : counts.entrySet())
			{
				product *= e.getValue() + 1;
				if (largest == null || e.getValue() > counts.get(largest))
				{
					largest = e.getKey();
				}
			}
			if (product * options <= 150_000 || largest == null)
			{
				return;
			}
			List<GearItem> inSlot = new ArrayList<>();
			for (GearItem item : pool)
			{
				if (item.getSlot() == largest)
				{
					inSlot.add(item);
				}
			}
			pool.remove(inSlot.get(r.nextInt(inSlot.size())));
		}
	}

	/** Ordinary equipment whose availability follows the plain ownership / purchase rules. */
	private static boolean plain(GearItem item)
	{
		return !item.isDmmEquipment() && !item.isBetaEquipment() && !item.isBountyHunterEquipment()
			&& !item.isLeagueEquipment() && !DiaryRewards.isReward(item.getId())
			&& !OwnershipRules.requiresOwnership(item.getId()) && item.isTradeable();
	}

	private static GearItem named(String name, Random r)
	{
		List<GearItem> matches = new ArrayList<>();
		for (Slot slot : Slot.values())
		{
			for (GearItem item : catalogue.getItems(slot))
			{
				if (item.getName().equalsIgnoreCase(name) && !item.isDmmEquipment() && !item.isBetaEquipment()
					&& !item.isBountyHunterEquipment() && !item.isLeagueEquipment() && !DiaryRewards.isReward(item.getId()))
				{
					matches.add(item);
				}
			}
		}
		if (matches.isEmpty())
		{
			return null;
		}
		// Prefer the degraded identities when they exist, so ownership of exactly that variant is exercised.
		for (GearItem item : matches)
		{
			if (Arrays.stream(DEGRADED).anyMatch(id -> id == item.getId()) && r.nextBoolean())
			{
				return item;
			}
		}
		return matches.get(r.nextInt(matches.size()));
	}

	private static boolean servesTab(GearItem weapon, AttackStyle.Type tab)
	{
		if (weapon.getSubcategory().equals("salamander"))
		{
			return false;
		}
		for (String raw : weapon.getStyles())
		{
			AttackStyle s = AttackStyle.parse(raw);
			if (s != null && WeaponRules.tabType(weapon, s) == tab)
			{
				return tab != AttackStyle.Type.MAGIC || s.isAutocast() || WeaponRules.isPoweredStaff(weapon);
			}
		}
		return false;
	}

	private static boolean relevant(GearItem item, AttackStyle.Type tab)
	{
		switch (tab)
		{
			case STAB:
				return item.getStabBonus() > 0 || item.getMeleeStr() > 0;
			case SLASH:
				return item.getSlashBonus() > 0 || item.getMeleeStr() > 0;
			case CRUSH:
				return item.getCrushBonus() > 0 || item.getMeleeStr() > 0;
			case RANGED:
				return item.getRangedBonus() > 0 || item.getRangedStr() > 0;
			case ATLATL:
				return item.getRangedBonus() > 0 || item.getMeleeStr() > 0;
			default:
				return item.getMagicBonus() > 0 || item.getMagicStr() > 0;
		}
	}

	// ------------------------------------------------------------ the oracle

	private static final class Oracle
	{
		private final Case c;
		private final OptimizerSettings s;
		private Map<Slot, List<GearItem>> kept;
		long evaluations;

		/** Only enumerate these armour candidates (weapons and ammunition stay unrestricted). */
		void restrict(Map<Slot, List<GearItem>> candidates)
		{
			kept = candidates;
		}

		Oracle(Case c)
		{
			this.c = c;
			this.s = c.settings;
		}

		static final class Best
		{
			Loadout loadout;
			double score = Double.NEGATIVE_INFINITY;
			long cost = Long.MAX_VALUE;
		}

		double metric(DpsResult r)
		{
			switch (s.getCalcMode())
			{
				case ACCURACY:
					return r.getDps() <= 0 ? 0 : r.getAccuracy() * 1e6 + r.getDps();
				case MAX_HIT:
					return r.getMaxHit() * 1e6 + r.getDps();
				default:
					return r.getDps();
			}
		}

		private boolean owns(GearItem item)
		{
			if (c.owned.contains(item.getId()))
			{
				return true;
			}
			for (int variant : item.getOwnershipVariants())
			{
				if (c.owned.contains(variant))
				{
					return true;
				}
			}
			return false;
		}

		/** Units held of the item and its equivalent variants. */
		private long held(GearItem item)
		{
			Set<Integer> ids = new LinkedHashSet<>(item.getOwnershipVariants());
			ids.add(item.getId());
			long held = 0;
			for (int id : ids)
			{
				long more = c.owned.contains(id) ? c.quantities.getOrDefault(id, Long.MAX_VALUE) : 0;
				held = held > Long.MAX_VALUE - more ? Long.MAX_VALUE : held + more;
			}
			return held;
		}

		/** Units of the requested ammunition still to buy. */
		private long shortfall(GearItem item)
		{
			if (s.getAmmoCount() <= 0 || !WeaponRules.consumedPerAttack(item))
			{
				return 0;
			}
			return Math.max(0, s.getAmmoCount() - held(item));
		}

		/** Excluded, unwearable, or not obtainable alone under the search mode. */
		boolean usable(GearItem item)
		{
			if (s.getExcluded().contains(item.getId()) || !c.levels.canEquip(item)
				|| !CombatRules.equipmentAllowed(c.ctx.getMonster(), item))
			{
				return false;
			}
			if (s.getMode() == SearchMode.UNLIMITED || owns(item) && shortfall(item) == 0)
			{
				return true;
			}
			if (s.getMode() == SearchMode.OWNED_ONLY || OwnershipRules.requiresOwnership(item.getId())
				|| !item.isTradeable() && !ItemCosts.hasTradableComponents(item.getId()))
			{
				return false;
			}
			long price = c.price(item);
			if (price < 0)
			{
				return s.getAmmoCount() <= 0 && WeaponRules.consumedPerAttack(item);
			}
			return price <= s.getBudget();
		}

		long cost(GearItem item)
		{
			if (item == null)
			{
				return 0;
			}
			long price = Math.max(0, c.price(item));
			if (WeaponRules.consumedPerAttack(item))
			{
				return price * shortfall(item);
			}
			return owns(item) ? 0 : price;
		}

		boolean expensive(GearItem item)
		{
			if (item == null)
			{
				return false;
			}
			long price = c.price(item);
			return price < 0 || price + ItemCosts.pvpRepairCost(item) >= s.getExpensiveItemThreshold();
		}

		/** Why the loadout breaks a rule, or null if it is a legal answer. */
		String illegal(Loadout l)
		{
			GearItem weapon = l.getWeapon();
			if (weapon == null || !options(weapon).contains(new Option(l.getStyle(), l.getSpell())))
			{
				return "option " + l.getStyle() + " / " + l.getSpell();
			}
			long cost = cost(l.getLoadedAmmo());
			int expensive = 0;
			for (Slot slot : Slot.values())
			{
				GearItem item = l.get(slot);
				SlotLock lock = s.getLocks().get(slot);
				if (lock != null && !(slot == Slot.SHIELD && weapon.isTwoHanded()))
				{
					// An unusable locked item leaves the slot empty.
					GearItem locked = lock.getKind() == SlotLock.Kind.ITEM ? find(slot, lock.getItemId()) : null;
					if (item != (locked != null && usable(locked) ? locked : null))
					{
						return "lock " + slot;
					}
				}
				if (item == null)
				{
					continue;
				}
				if (!usable(item))
				{
					return "unusable " + item.getName();
				}
				cost += cost(item);
				expensive += expensive(item) ? 1 : 0;
			}
			if (weapon.isTwoHanded() && l.get(Slot.SHIELD) != null)
			{
				return "shield with two-handed weapon";
			}
			GearItem ammo = WeaponRules.loadsAmmo(weapon) ? l.getLoadedAmmo() : l.get(Slot.AMMO);
			boolean needsAmmo = !weapon.getAmmunition().isEmpty();
			if (needsAmmo && (ammo == null || !weapon.getAmmunition().contains(ammo.getId()) || !usable(ammo)))
			{
				return "ammunition";
			}
			if (s.getMode() == SearchMode.BUDGET && cost > s.getBudget())
			{
				return "cost " + cost;
			}
			if (s.isWildernessRiskLimited() && expensive > s.getMaxExpensiveItems())
			{
				return "expensive " + expensive;
			}
			return null;
		}

		List<Option> options(GearItem weapon)
		{
			List<Option> out = new ArrayList<>();
			Set<String> seen = new HashSet<>();
			for (String raw : weapon.getStyles())
			{
				AttackStyle style = AttackStyle.parse(raw);
				if (style == null || WeaponRules.classOf(style) != c.cls || !seen.add(style.getType() + "/" + style.getStance())
					|| !s.getStyles().contains(WeaponRules.tabType(weapon, style))
					|| !AttackReach.canReach(c.ctx.getMonster(), weapon, style, s.getTargetDistance()))
				{
					continue;
				}
				String stance = style.getStance();
				boolean controlled = style.isMelee() && stance.equals("controlled");
				if (!s.isAttackXp() && style.isMelee() && (stance.equals("accurate") || controlled)
					|| !s.isStrengthXp() && style.isMelee() && (stance.equals("aggressive") || controlled)
					|| !s.isDefenceXp() && (style.isMelee() && (stance.equals("defensive") || controlled)
						|| stance.equals("longrange")))
				{
					continue;
				}
				if (c.cls != CombatClass.MAGIC)
				{
					out.add(new Option(style, null));
				}
				else if (style.isAutocast())
				{
					for (Spell spell : catalogue.getSpells())
					{
						String name = spell.getName().toLowerCase(Locale.ROOT);
						String book = spell.getSpellbook() == null ? "" : spell.getSpellbook().toLowerCase(Locale.ROOT);
						if (spell.getMaxHit() > 0 && spell.getLevel() <= c.ctx.getMagic() && s.getSpellbooks().contains(book)
							&& spell.castableWith(weapon.getId())
							&& (!name.contains("demonbane") || c.ctx.getMonster().hasAttribute("demon"))
							&& (!name.equals("crumble undead") || c.ctx.getMonster().hasAttribute("undead")))
						{
							out.add(new Option(style, spell));
						}
					}
				}
				else if (WeaponRules.isPoweredStaff(weapon) && WeaponRules.poweredStaffMaxHit(weapon, c.ctx.getMagic()) > 0)
				{
					out.add(new Option(style, null));
				}
			}
			return out;
		}

		Best search()
		{
			Best best = new Best();
			if (!Optimizer.classAllowed(c.ctx.getMonster(), c.cls))
			{
				return best;
			}
			SlotLock weaponLock = s.getLocks().get(Slot.WEAPON);
			for (GearItem weapon : c.pool)
			{
				if (weapon.getSlot() != Slot.WEAPON || !usable(weapon)
					|| weaponLock != null && weaponLock.getItemId() != weapon.getId())
				{
					continue;
				}
				SlotLock shieldLock = s.getLocks().get(Slot.SHIELD);
				if (weapon.isTwoHanded() && shieldLock != null && shieldLock.getKind() != SlotLock.Kind.EMPTY)
				{
					continue;
				}
				List<Option> options = options(weapon);
				if (options.isEmpty())
				{
					continue;
				}
				List<GearItem> ammo = new ArrayList<>();
				if (weapon.getAmmunition().isEmpty())
				{
					ammo.add(null);
				}
				else
				{
					for (GearItem a : c.pool)
					{
						if (a.getSlot() == Slot.AMMO && weapon.getAmmunition().contains(a.getId()) && usable(a))
						{
							ammo.add(a);
						}
					}
				}
				List<List<GearItem>> choices = new ArrayList<>();
				List<Slot> slots = new ArrayList<>();
				for (Slot slot : ARMOUR)
				{
					List<GearItem> list = new ArrayList<>();
					SlotLock lock = s.getLocks().get(slot);
					if (slot == Slot.SHIELD && weapon.isTwoHanded())
					{
						list.add(null);
					}
					else if (lock != null)
					{
						GearItem locked = lock.getKind() == SlotLock.Kind.ITEM ? find(slot, lock.getItemId()) : null;
						list.add(locked != null && usable(locked) ? locked : null);
					}
					else
					{
						list.add(null);
						for (GearItem item : c.pool)
						{
							if (item.getSlot() == slot && usable(item)
								&& (kept == null || kept.getOrDefault(slot, Collections.emptyList()).contains(item)))
							{
								list.add(item);
							}
						}
					}
					slots.add(slot);
					choices.add(list);
				}
				for (GearItem a : ammo)
				{
					Loadout l = new Loadout();
					l.set(Slot.WEAPON, weapon);
					if (WeaponRules.loadsAmmo(weapon))
					{
						l.setLoadedAmmo(a);
					}
					else
					{
						l.set(Slot.AMMO, a);
					}
					long cost = cost(weapon) + cost(a);
					int expensive = (expensive(weapon) ? 1 : 0) + (a != null && !WeaponRules.loadsAmmo(weapon) && expensive(a) ? 1 : 0);
					enumerate(l, slots, choices, 0, cost, expensive, options, best);
				}
			}
			return best;
		}

		private GearItem find(Slot slot, int id)
		{
			for (GearItem item : c.pool)
			{
				if (item.getSlot() == slot && item.getId() == id)
				{
					return item;
				}
			}
			return null;
		}

		private void enumerate(Loadout l, List<Slot> slots, List<List<GearItem>> choices, int index, long cost,
			int expensive, List<Option> options, Best best)
		{
			if (s.getMode() == SearchMode.BUDGET && cost > s.getBudget()
				|| s.isWildernessRiskLimited() && expensive > s.getMaxExpensiveItems())
			{
				return;
			}
			if (index == slots.size())
			{
				for (Option o : options)
				{
					l.setStyle(o.getStyle());
					l.setSpell(o.getSpell());
					evaluations++;
					DpsResult r = DpsCalculator.calculate(l, c.ctx);
					if (r.getDps() <= 0)
					{
						continue;
					}
					double score = metric(r);
					if (score > best.score + EPS
						|| Math.abs(score - best.score) <= EPS && cost < best.cost)
					{
						best.score = score;
						best.cost = cost;
						best.loadout = l.copy();
					}
				}
				return;
			}
			Slot slot = slots.get(index);
			for (GearItem item : choices.get(index))
			{
				l.set(slot, item);
				enumerate(l, slots, choices, index + 1, cost + cost(item), expensive + (expensive(item) ? 1 : 0),
					options, best);
			}
			l.set(slot, null);
		}
	}

	private static final class Option
	{
		final AttackStyle style;
		final Spell spell;

		Option(AttackStyle style, Spell spell)
		{
			this.style = style;
			this.spell = spell;
		}

		AttackStyle getStyle()
		{
			return style;
		}

		Spell getSpell()
		{
			return spell;
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof Option && java.util.Objects.equals(style, ((Option) o).style) && spell == ((Option) o).spell;
		}

		@Override
		public int hashCode()
		{
			return java.util.Objects.hashCode(style);
		}
	}
}
