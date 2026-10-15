package com.bestgearsetup.calc;

import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Approximate ordinary damage blocked by each overhead; special encounters take precedence over this estimate. */
final class IncomingDamage
{
	private static final Pattern PENETRATION = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*%\\s*prayer penetration");
	private IncomingDamage() { }

	static Map<ProtectionPrayers.Overhead, Double> byPrayer(Monster monster, Loadout loadout, PlayerLevels levels,
		boolean meleeRange)
	{
		if (monster.getAttackStyles() == null || levels == null) { return Collections.emptyMap(); }
		Map<ProtectionPrayers.Overhead, Double> out = new EnumMap<>(ProtectionPrayers.Overhead.class);
		boolean meleeOnly = monster.getAttackStyles().keySet().stream()
			.allMatch(s -> ProtectionPrayers.protectedBy(s).equals(Collections.singleton(ProtectionPrayers.Overhead.MELEE)));
		for (Map.Entry<String, Monster.MonsterAttack> entry : monster.getAttackStyles().entrySet())
		{
			Monster.MonsterAttack attack = entry.getValue();
			if (attack == null || attack.isTypeless()) { continue; }
			String style = entry.getKey().toLowerCase(Locale.ROOT);
			Set<ProtectionPrayers.Overhead> prayers = ProtectionPrayers.protectedBy(style);
			// Merged styles have no per-attack accuracy or damage; keep the explicit max-hit fallback.
			if (prayers.size() > 1 || style.contains("<br")) { return Collections.emptyMap(); }
			for (ProtectionPrayers.Overhead prayer : prayers)
			{
				if (prayer == ProtectionPrayers.Overhead.MELEE && !meleeRange && !meleeOnly) { continue; }
				ProtectionPrayers.Overhead accuracyStyle = style.contains("magical ") ? ProtectionPrayers.Overhead.MAGIC
					: style.contains("ranged magic") || style.contains("ranged melee") ? ProtectionPrayers.Overhead.MISSILES : prayer;
				int level = accuracyStyle == ProtectionPrayers.Overhead.MAGIC ? monster.getMagicLevel()
					: accuracyStyle == ProtectionPrayers.Overhead.MISSILES ? monster.getRangedLevel() : monster.getAttackLevel();
				if (level <= 0 || attack.getMaxHit() <= 0) { return Collections.emptyMap(); }
				Integer bonus = accuracyStyle == ProtectionPrayers.Overhead.MAGIC ? Integer.valueOf(monster.getMagicAttackBonus())
					: accuracyStyle == ProtectionPrayers.Overhead.MISSILES ? monster.getRangedAttackBonus() : monster.getMeleeAttackBonus();
				long attackRoll = (long) (level + 9) * ((bonus == null ? 0 : bonus) + 64);
				long defenceRoll = defenceRoll(loadout, levels, accuracyStyle, style);
				double weight = attack.getWeighting() > 0 && Double.isFinite(attack.getWeighting()) ? attack.getWeighting() : 1;
				Matcher penetration = PENETRATION.matcher(style);
				double blocked = penetration.find() ? Math.max(0, 1 - Double.parseDouble(penetration.group(1)) / 100.0) : 1;
				out.merge(prayer, blocked * weight * HitChance.single(attackRoll, defenceRoll) * attack.getMaxHit() / 2.0, Double::sum);
			}
		}
		return out;
	}

	static long defenceRoll(Loadout loadout, PlayerLevels levels, ProtectionPrayers.Overhead accuracyStyle, String style)
	{
		int stance = loadout.getStyle() == null ? 0 : "defensive".equals(loadout.getStyle().getStance())
			|| "longrange".equals(loadout.getStyle().getStance()) ? 3 : "controlled".equals(loadout.getStyle().getStance()) ? 1 : 0;
		int effective = accuracyStyle == ProtectionPrayers.Overhead.MAGIC
			? (int) (levels.getMagic() * 0.7 + levels.getDefence() * 0.3) + 8 : levels.getDefence() + stance + 8;
		return (long) effective * (defenceBonus(loadout, accuracyStyle, style) + 64);
	}

	private static int defenceBonus(Loadout loadout, ProtectionPrayers.Overhead prayer, String style)
	{
		int stab = 0, slash = 0, crush = 0, ranged = 0, magic = 0;
		for (Slot slot : Slot.values())
		{
			GearItem item = loadout.get(slot);
			if (item == null || slot == Slot.SHIELD && loadout.getWeapon() != null && loadout.getWeapon().isTwoHanded())
			{
				continue;
			}
			stab += item.getStabDef();
			slash += item.getSlashDef();
			crush += item.getCrushDef();
			ranged += item.getRangedDef();
			magic += item.getMagicDef();
		}
		if (prayer == ProtectionPrayers.Overhead.MAGIC) { return magic; }
		if (prayer == ProtectionPrayers.Overhead.MISSILES) { return ranged; }
		if (style.contains("stab")) { return stab; }
		if (style.contains("slash")) { return slash; }
		if (style.contains("crush")) { return crush; }
		// The snapshot sometimes says only "melee"; conservatively use the weakest total melee defence.
		return Math.min(stab, Math.min(slash, crush));
	}
}
