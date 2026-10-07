package com.bestgearsetup.calc;

import com.bestgearsetup.data.Monster;
import lombok.Builder;
import lombok.Value;

/**
 * Stat-draining special attacks landed before the fight, applied in the following order
 * (vulnerability, elder maul, dragon warhammer, emberlight, arclight, tonalztics, seercull,
 * eye of ayak, bandos godsword). Counts are successful hits; the godsword, seercull and ayak
 * values are total damage dealt, since they drain by the damage.
 */
@Value
@Builder(toBuilder = true)
public class SpecialAttacks
{
	public static final SpecialAttacks NONE = SpecialAttacks.builder().build();

	/** Vulnerability (-10% Defence) or, with the tome of water, -15%. */
	boolean vulnerability;
	boolean tomeOfWater;
	int elderMaul;
	int dragonWarhammer;
	int emberlight;
	int arclight;
	int tonalztics;
	/** Damage dealt with seercull specials: drains Magic level. */
	int seercullDamage;
	/** Damage dealt with eye of ayak specials: drains magic defence. */
	int ayakDamage;
	/** Damage dealt with bandos godsword specials: drains Defence, then Strength, Attack, Magic. */
	int bandosGodswordDamage;

	/** These drains plus one more landed special that dealt the given damage. */
	public SpecialAttacks landed(SpecialAttack.Drain drain, int damage)
	{
		switch (drain)
		{
			case DRAGON_WARHAMMER:
				return toBuilder().dragonWarhammer(dragonWarhammer + 1).build();
			case ELDER_MAUL:
				return toBuilder().elderMaul(elderMaul + 1).build();
			case ARCLIGHT:
				return toBuilder().arclight(arclight + 1).build();
			case EMBERLIGHT:
				return toBuilder().emberlight(emberlight + 1).build();
			case TONALZTICS:
				return toBuilder().tonalztics(tonalztics + 1).build();
			case BANDOS_GODSWORD:
				return toBuilder().bandosGodswordDamage(bandosGodswordDamage + damage).build();
			case SEERCULL:
				return toBuilder().seercullDamage(seercullDamage + damage).build();
			case EYE_OF_AYAK:
				return toBuilder().ayakDamage(ayakDamage + damage).build();
			default:
				return this;
		}
	}

	public boolean isAny()
	{
		return vulnerability || elderMaul > 0 || dragonWarhammer > 0 || emberlight > 0 || arclight > 0
			|| tonalztics > 0 || seercullDamage > 0 || ayakDamage > 0 || bandosGodswordDamage > 0;
	}

	/**
	 * A copy of the monster with these drains applied. Defence never drops below the monster's
	 * defence floor. Wiki effects:
	 * dragon warhammer -30% current Defence, elder maul -35% (both multiplicative);
 * arclight -5% + 1 of base Attack/Strength/Defence (10% + 1 vs demons), emberlight 5% + 1 (15% vs demons),
	 * both additive; tonalztics -1/8 of the target's Magic level from Defence per hit.
	 */
	public Monster apply(Monster base)
	{
		Monster m = base.copy();
		if (!isAny() || base.hasAttribute("absorption") && !CombatRules.toa(base))
		{
			return m;
		}
		boolean demon = base.hasAttribute("demon");
		int floor = Math.max(0, CombatRules.defenceFloor(base));
		int def = base.getDefenceLevel();

		if (vulnerability)
		{
			def = Math.max(floor, def * (tomeOfWater ? 85 : 90) / 100);
		}
		for (int i = 0; i < elderMaul; i++)
		{
			def = Math.max(floor, def - def * 35 / 100);
		}
		for (int i = 0; i < dragonWarhammer; i++)
		{
			def = Math.max(floor, def - def * 30 / 100);
		}
		int baseDef = base.getDefenceLevel();
		int att = base.getAttackLevel();
		int str = base.getStrengthLevel();
		for (int i = 0; i < emberlight; i++)
		{
			def -= baseDef * (demon ? 15 : 5) / 100 + 1;
			att -= base.getAttackLevel() * (demon ? 15 : 5) / 100 + 1;
			str -= base.getStrengthLevel() * (demon ? 15 : 5) / 100 + 1;
		}
		for (int i = 0; i < arclight; i++)
		{
			def -= baseDef * (demon ? 10 : 5) / 100 + 1;
			att -= base.getAttackLevel() * (demon ? 10 : 5) / 100 + 1;
			str -= base.getStrengthLevel() * (demon ? 10 : 5) / 100 + 1;
		}
		for (int i = 0; i < tonalztics; i++)
		{
			def -= base.getMagicLevel() / 8;
		}
		def = Math.max(floor, def);

		int magic = Math.max(0, base.getMagicLevel() - seercullDamage);
		int magicDef = base.getDefMagic() > 0 ? Math.max(0, base.getDefMagic() - ayakDamage) : base.getDefMagic();

		// Bandos godsword: Defence first, then Strength, (Prayer, not tracked), Attack, Magic.
		int bgs = bandosGodswordDamage;
		int d = Math.min(bgs, Math.max(0, def - floor));
		def -= d;
		bgs = def > 0 ? 0 : bgs - d;
		int s = Math.min(bgs, Math.max(0, str));
		str -= s;
		bgs -= s;
		int a = Math.min(bgs, Math.max(0, att));
		att -= a;
		bgs -= a;
		magic = Math.max(0, magic - bgs);

		m.setDefenceLevel(def);
		m.setAttackLevel(Math.max(0, att));
		m.setStrengthLevel(Math.max(0, str));
		m.setMagicLevel(magic);
		m.setDefMagic(Math.max(-64, magicDef));
		return m;
	}
}
