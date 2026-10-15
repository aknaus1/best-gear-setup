package com.bestgearsetup.calc;

import com.bestgearsetup.calc.ProtectionPrayers.Advice;
import com.bestgearsetup.calc.ProtectionPrayers.Overhead;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import java.util.Arrays;
import java.util.Collections;

/** Encounter mechanics that cannot be inferred from an infobox's attack styles or maximum hit. */
final class EncounterPrayers
{
	private EncounterPrayers() { }

	static Advice equipped(Monster monster, SetupResult setup, boolean meleeRange, ProtectionPrayers.Context context)
	{
		String n = monster.getLowerName();
		if (specialMode(n)) { return null; }
		Loadout loadout = setup.getLoadout();
		if (named(n, "king black dragon"))
		{
			GearItem weapon = loadout.getWeapon();
			GearItem shield = weapon == null || weapon.isTwoHanded() ? null : loadout.get(Slot.SHIELD);
			boolean close = meleeRange || context != null && context.getSettings().getTargetDistance() == 0;
			boolean useMelee = close && DragonfireProtection.isFireShield(shield)
				&& (context == null || context.getLevels().getPrayer() >= Overhead.MELEE.getLevel()
					|| context.getLevels().getPrayer() < Overhead.MAGIC.getLevel());
			Overhead prayer = useMelee ? Overhead.MELEE : Overhead.MAGIC;
			String note = useMelee ? "With this fire shield, fight KBD adjacent and keep Melee, even with a crossbow. "
				+ "This reduces the frequency of special breaths. "
				: "Keep Magic with a shieldless / two-handed setup or when fighting away from KBD. "
					+ "A fire shield and adjacent position allow Melee once unlocked. ";
			note += "Use antifire; special breaths can still deal chip damage. Normal breath timing doesn't allow reactive style switches.";
			if (context != null && !DragonfireProtection.allowed(monster, loadout, context.getSettings().toBuilder()
				.requireFireProtection(true).protectMagic(prayer == Overhead.MAGIC).build(), context.getLevels()))
			{
				note += " The selected shield / potion combination is insufficient for the search's fire requirement.";
			}
			return rule("King_Black_Dragon/Strategies", note, prayer);
		}
		if (named(n, "araxxor") && context != null && !meleeRange)
		{
			long magic = IncomingDamage.defenceRoll(loadout, context.getLevels(), Overhead.MAGIC, "magic");
			long ranged = IncomingDamage.defenceRoll(loadout, context.getLevels(), Overhead.MISSILES, "ranged");
			return rule("Araxxor", "Outside melee range Araxxor attacks your weaker ranged / magic defence. At the "
				+ "search's base levels, this armour calls for " + (magic < ranged ? "Magic" : "Missiles")
				+ "; switch to Melee when he approaches. Prefer fighting adjacent with Melee. Dodge acid and cleaves; "
				+ "defensive boosts can change the distant style he chooses.", magic < ranged ? Overhead.MAGIC : Overhead.MISSILES, Overhead.MELEE);
		}
		GearItem boots = loadout.get(Slot.FEET);
		if (named(n, "sarachnis") && meleeRange && boots != null && boots.getLowerCombatName().startsWith("aranea boots"))
		{
			return rule("Sarachnis/Strategies", "Keep Melee while adjacent. Your aranea boots prevent the web, so her "
				+ "usual web-driven ranged prayer swap isn't required if you remain in melee reach. Magic spawns are separate attackers.", Overhead.MELEE);
		}
		return null;
	}

	static Advice recommend(Monster monster, CombatClass setup, boolean meleeRange)
	{
		String n = monster.getLowerName();
		// Special modes have their own attack rules; don't silently apply normal-game advice to them.
		if (specialMode(n))
		{
			if (named(n, "cerberus") || named(n, "kalphite queen") || named(n, "tztok-jad")
				|| named(n, "phantom muspah") || named(n, "sol heredit") || named(n, "kree'arra")
				|| named(n, "general graardor") || named(n, "commander zilyana") || named(n, "k'ril tsutsaroth")
				|| named(n, "king black dragon"))
			{
				return new Advice(null, Collections.emptyList(), "Special-mode attack timing and protection mechanics "
					+ "haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice "
					+ "must not be assumed.", true);
			}
			return null;
		}
		if (n.startsWith("penance ")) { return rule("Barbarian_Assault", "Prayers are disabled in Barbarian Assault. "
			+ "Use armour, healing and the encounter's role mechanics instead of an overhead."); }
		if (named(n, "earth warrior champion")) { return rule("Champions%27_Challenge", "This champion's challenge "
			+ "disallows Prayer; protection overheads cannot be used."); }
		if (named(n, "tztok-jad") || named(n, "jaltok-jad"))
		{
			return meleeRange ? rule("TzHaar_Fight_Cave/Strategies", "Keep Melee between attacks: his melee has no warning. "
				+ "Switch to Magic or Missiles from the raised-legs / stomp animation, then return to Melee.",
				Overhead.MELEE, Overhead.MAGIC, Overhead.MISSILES)
				: rule("TzHaar_Fight_Cave/Strategies", "Stay outside melee range. Switch to Magic for raised legs and "
					+ "Missiles for the stomp before the delayed hit; melee has no reaction window.", Overhead.MAGIC, Overhead.MISSILES);
		}
		if (named(n, "the nightmare") || named(n, "phosani's nightmare"))
		{
			return rule(n.startsWith("phosani") ? "Phosani%27s_Nightmare/Strategies" : "The_Nightmare/Strategies", "Read each attack animation: claws mean Melee, pink petals mean Magic, "
				+ "and the contortion means Missiles. React during the wind-up. Melee threatens the tank and nearby players. "
				+ "The curse remaps prayer buttons; Phosani P3 disables correct prayers at attack start, so reactivate during the wind-up.",
				Overhead.MELEE, Overhead.MAGIC, Overhead.MISSILES);
		}
		if (named(n, "scurrius"))
		{
			return rule("Scurrius", "Keep Melee against tail swipes while adjacent. Switch to Magic for electricity and "
				+ "Missiles for flying fur before their projectiles hit; dodge falling bricks. All setups must follow the fight's attacks.",
				Overhead.MELEE, Overhead.MAGIC, Overhead.MISSILES);
		}
		if (named(n, "the leviathan"))
		{
			return rule("The_Leviathan/Strategies", "Pray before projectile impact: blue Magic, green Missiles, orange Melee. "
				+ "Melee orbs reach you at distance too. Dodge boulders and special attacks; don't use armour to choose a fixed overhead.",
				Overhead.MAGIC, Overhead.MISSILES, Overhead.MELEE);
		}
		if (named(n, "the whisperer"))
		{
			return rule("The_Whisperer/Strategies", "Switch Magic / Missiles to match each volley before impact, including "
				+ "mixed volleys later in the fight. Freeze her after specials to prevent melee; dodge tentacles and use the special's safe areas.",
				Overhead.MAGIC, Overhead.MISSILES);
		}
		if (named(n, "doom of mokhaiotl"))
		{
			String note = "Pray before impact: blue Magic, green Missiles, red Melee. Melee projectiles occur from delve 2 "
				+ "and can reach you at distance. Restore the overhead after rock explosions disable it; dodge debris and specials. "
				+ "Standing next to Doom also exposes you to a prayer-piercing tongue attack.";
			return n.contains("(delve 1)") && !meleeRange ? rule("Doom_of_Mokhaiotl/Strategies", note, Overhead.MAGIC, Overhead.MISSILES)
				: rule("Doom_of_Mokhaiotl/Strategies", note, Overhead.MAGIC, Overhead.MISSILES, Overhead.MELEE);
		}
		if (named(n, "yama"))
		{
			return meleeRange ? rule("Yama/Strategies", "Follow Yama's attack cues and current ranged / magic cycle; "
				+ "use Melee for his close-range attack. Protection reduces rather than eliminates all damage, especially in P3. Dodge specials.",
				Overhead.MELEE, Overhead.MAGIC, Overhead.MISSILES)
				: rule("Yama/Strategies", "Follow Yama's ranged / magic attack cues, switching to the matching overhead. "
					+ "Stay outside melee range and dodge shadow crashes / waves; P3 can still chip through prayer.", Overhead.MAGIC, Overhead.MISSILES);
		}
		if (named(n, "alchemical hydra"))
		{
			return rule("Alchemical_Hydra/Strategies", "Count three ordinary attacks of each style, then switch Magic / Missiles "
				+ "before the next attack. Specials don't count. In the extinguished phase alternate after every ordinary attack. "
				+ "The first style is random; don't wait for ordinary projectiles to land.", Overhead.MAGIC, Overhead.MISSILES);
		}
		if (named(n, "crystalline hunllef") || named(n, "corrupted hunllef"))
		{
			return rule("The_Gauntlet/Strategies", "Keep Missiles for the initial ranged cycle; switch after each four boss "
				+ "attacks using the style-change cue. Keep the correct prayer active when the attack launches, not at impact. "
				+ "Reactivate prayers after the disabling attack; dodge floor hazards and tornadoes.", Overhead.MISSILES, Overhead.MAGIC);
		}
		if (named(n, "cerberus"))
		{
			return rule("Cerberus/Strategies", "Keep Magic for ordinary attacks; their prayer is checked at launch. "
				+ "Her predictable triple attack is Magic, Missiles, Melee. For summoned souls, read the left-to-right colours "
				+ "and activate each matching prayer before that ghost attacks. This is not reactive switching for random ordinary attacks.",
				Overhead.MAGIC, Overhead.MISSILES, Overhead.MELEE);
		}
		if (named(n, "demonic gorilla"))
		{
			return rule("Demonic_gorilla/Strategies", "Keep the prayer matching its current style. It changes style after three "
				+ "unsuccessful attacks; prepare for the next style before it attacks. Melee may be unannounced, including "
				+ "when it approaches a ranged setup. Dodge falling rocks.", Overhead.MAGIC, Overhead.MISSILES, Overhead.MELEE);
		}
		if (named(n, "tormented demon"))
		{
			return rule("Tormented_Demon/Strategies", "Keep the prayer matching the demon's current attack style. "
				+ "It changes attack style after its fire-bomb special; dodge the bombs and read the next style cue. "
				+ "It can approach to melee regardless of your weapon. Its own protection prayer is a separate mechanic.",
				Overhead.MAGIC, Overhead.MISSILES, Overhead.MELEE);
		}
		if (named(n, "manticore"))
		{
			return rule("Fortis_Colosseum/Strategies", "Read the charged orb order and activate each matching prayer BEFORE "
				+ "its projectile launches, one tick apart. Standard order is Magic / Missiles or Missiles / Magic, then Melee; "
				+ "the melee orb reaches you at distance. Mantimayhem can change the pattern. Don't react to mid-flight orbs.",
				Overhead.MAGIC, Overhead.MISSILES, Overhead.MELEE);
		}
		if (named(n, "jal-ak"))
		{
			return rule("Jal-Ak", "The blob reads your overhead three ticks before its attack and chooses the counter style. "
				+ "Change to the matching prayer before projectile launch; don't camp one overhead. In melee range "
				+ "it can also melee, so keep space while solving the wave.", Overhead.MAGIC, Overhead.MISSILES, Overhead.MELEE);
		}
		if (named(n, "phantom muspah") || named(n, "strange creature") && !n.contains("shadows of custodia"))
		{
			if (n.contains("(teleporting)")) { return rule("Phantom_Muspah/Strategies", "No overhead blocks the teleport / "
				+ "lightning hazards; move to safety. Resume the appropriate form's prayer when ordinary attacks restart."); }
			if (n.contains("(melee)")) { return rule("Phantom_Muspah/Strategies", "Use Melee during the melee form, even "
				+ "with a ranged weapon: it approaches you. Freeze / kite or step back before it attacks to prevent "
				+ "prayer-piercing damage; switch when the form changes.", Overhead.MELEE); }
			return rule("Phantom_Muspah/Strategies", "Keep Missiles for ordinary ranged attacks, checked at animation start. "
				+ "Switch to Magic immediately on the charged purple-orb wind-up, BEFORE launch, then return to Missiles. "
				+ "Switch to Melee for melee form; the prayer-shield phase also needs prayer-draining attacks.", Overhead.MISSILES, Overhead.MAGIC);
		}
		if (named(n, "araxxor"))
		{
			return rule("Araxxor/Strategies", "Keep Melee while in melee range, including the enraged phase. Outside melee "
				+ "range he uses Magic / Missiles against your weaker defence and approaches you; return to Melee when "
				+ "he reaches you. Acid, cleaves and araxytes require movement.", Overhead.MELEE);
		}
		if (named(n, "venenatis") || named(n, "spindel"))
		{
			return meleeRange ? rule("Venenatis/Strategies", "Keep Melee while next to the spider. At distance follow its "
				+ "ranged / magic attack cycle, changing before the next attack. Kill spiderlings to restore prayer "
				+ "effectiveness and avoid the prayer-draining web.", Overhead.MELEE)
				: rule("Venenatis/Strategies", "At distance follow the spider's current Magic / Missiles attack cycle; "
					+ "change before the next attack. If adjacent, use Melee. Kill spiderlings and avoid the web; don't "
					+ "assume every ordinary projectile can be reacted to mid-flight.", Overhead.MAGIC, Overhead.MISSILES, Overhead.MELEE);
		}
		if (named(n, "balance elemental"))
		{
			Overhead current = n.contains("(ranged)") ? Overhead.MISSILES : n.contains("(magic)") ? Overhead.MAGIC : Overhead.MELEE;
			return rule("While_Guthix_Sleeps", "Follow the visible current attack form, not the projectile's appearance. "
				+ "The balance elemental gives a short reaction window (shorter below half health); all three "
				+ "protection prayers may be needed as forms change.", current,
				current == Overhead.MELEE ? Overhead.MAGIC : Overhead.MELEE,
				current == Overhead.MISSILES ? Overhead.MAGIC : Overhead.MISSILES);
		}
		if (named(n, "nex"))
		{
			return rule("Nex/Strategies", "Pray by phase and who Nex targets: Magic for smoke / blood / ice, Missiles for "
				+ "shadow, and Melee when tanking close-range attacks or the final phase. Kiting and melee distancing change "
				+ "which attacks you receive. Her attacks pierce prayers; don't switch reactively against every random projectile.",
				Overhead.MAGIC, Overhead.MISSILES, Overhead.MELEE);
		}
		if (named(n, "maggot king"))
		{
			return rule("Maggot_King/Strategies", "Start with Missiles; switch Magic / Missiles by the post-screech phase. "
				+ "Turn OFF overheads during the white-aura screech or it damages you repeatedly. Later, dodge the melee slams "
				+ "or use Melee to reduce their damage. Don't choose a fixed prayer from the merged infobox max hits.",
				Overhead.MISSILES, Overhead.MAGIC, Overhead.MELEE);
		}
		if (named(n, "galvek"))
		{
			return rule("Dragon_Slayer_II", setup == CombatClass.MELEE
				? "The tank-armour melee method keeps Magic throughout the fight. Use a fire shield plus antifire, "
					+ "restore prayer after pink breath, and dodge fireballs, traps, waves and rock tombs."
				: "Use Magic in the fire phase and Missiles from the air phase onward. Change by phase rather than "
					+ "reacting to ordinary projectiles. Use a fire shield plus antifire and dodge the lethal specials.",
				setup == CombatClass.MELEE || n.contains("(fire)") ? Overhead.MAGIC : Overhead.MISSILES);
		}
		if (named(n, "the mimic")) { return rule("The_Mimic", "Keep Melee against the Mimic even while using ranged "
			+ "or magic. Move away from thrown sweets and keep attacking the boss; its summoned minions are separate attackers.", Overhead.MELEE); }
		if (named(n, "glough")) { return rule("Monkey_Madness_II", "Use the early-phase safespots; overheads don't "
			+ "block his second-phase ranged damage. In the final phase use Melee when close and Magic at distance, "
			+ "with movement to manage his push / pull attacks.", meleeRange ? Overhead.MELEE : Overhead.MAGIC); }
		if (named(n, "shellbane gryphon"))
		{
			return rule("Shellbane_Gryphon/Strategies", "Keep Melee and equip a tortugan shield: without it, melee hits "
				+ "pierce protection prayer. Occasional ranged hits remain; dodge corrosive spit, whirlwind and knockback specials.", Overhead.MELEE);
		}
		if (named(n, "the hueycoatl"))
		{
			return rule("The_Hueycoatl/Strategies", "For minimum damage, match each feather's colour with Magic, Missiles "
				+ "or Melee before impact, including while attacking body or tail. In teams, an assigned pillar prayer may "
				+ "take priority to maintain the damage buff. Dodge lightning and tail shockwaves.", Overhead.MAGIC, Overhead.MISSILES, Overhead.MELEE);
		}
		if (named(n, "zulrah"))
		{
			if (n.contains("magma")) { return rule("Zulrah/Strategies", "Magma form's directed attacks aren't blocked by overheads; move "
				+ "after it targets your tile. Resume the appropriate prayer when the next form appears."); }
			return rule("Zulrah/Strategies", n.contains("tanzanite")
				? "Keep Magic in tanzanite form; random ranged attacks cannot reliably be prayer-switched reactively. "
					+ "Read the rotation and change overhead before the next form's attack."
				: "Keep Missiles in ordinary serpentine phases. In the rotation's Jad phase, alternate Magic / Missiles "
					+ "BEFORE each launch according to the known rotation; do not wait for impact. The infobox form alone doesn't identify Jad phase.",
				n.contains("tanzanite") ? Overhead.MAGIC : Overhead.MISSILES);
		}
		if (named(n, "sarachnis"))
		{
			return rule("Sarachnis/Strategies", "Use Melee while she is next to you and Missiles when her web separates you. "
				+ "Change before the next attack, not in reaction to an ordinary projectile. Aranea boots prevent the web "
				+ "and can remove the need for this switch; magic spawns are separate attackers.",
				meleeRange ? Overhead.MELEE : Overhead.MISSILES, meleeRange ? Overhead.MISSILES : Overhead.MELEE);
		}
		if (named(n, "callisto") || named(n, "artio"))
		{
			return rule("Callisto/Strategies", "Keep Missiles while freezing and keeping the bear away; use Melee if it "
				+ "reaches you (melee pierces prayer). Switch to Magic for the visible knockback orb before impact, then "
				+ "return to the ordinary attack prayer. Avoid traps.", meleeRange ? Overhead.MELEE : Overhead.MISSILES,
				Overhead.MAGIC, meleeRange ? Overhead.MISSILES : Overhead.MELEE);
		}
		if (named(n, "vardorvis"))
		{
			return rule("Vardorvis/Strategies", "Keep Melee for Vardorvis; switch to Missiles for the head's green projectile "
				+ "before it hits, then return to Melee. Dodge axes; missed head attacks disable protection prayers.", Overhead.MELEE, Overhead.MISSILES);
		}
		if (named(n, "vet'ion") || named(n, "calvar'ion"))
		{
			return rule("Vet%27ion/Strategies", "Use Melee for the summoned skeletal hellhounds. The boss's lightning and "
				+ "shield bash must be dodged; this overhead does not block those attacks.", Overhead.MELEE);
		}
		if (named(n, "kephri"))
		{
			return rule("Kephri", "No overhead blocks Kephri's ground-targeted fireballs: step away from the shadow. "
				+ "Eggs and dung also require movement. Use the matching overhead for any attacking scarab bodyguard instead.");
		}
		if (named(n, "sol heredit"))
		{
			return rule("Sol_Heredit", "Only use Melee for the timed triple parry, activating it just before each spear hit. "
				+ "Praying early makes Sol disable it. His ordinary spear / shield AoE is typeless and must be dodged; "
				+ "an overhead is not needed for those attacks.", Overhead.MELEE);
		}
		if (named(n, "zalcano")) { return rule("Zalcano", "Overheads don't protect against Zalcano's hazards; dodge rockfalls, "
			+ "avoid red symbols and move away from explosive rocks."); }
		if (named(n, "corporeal beast")) { return rule("Corporeal_Beast", "Keep Magic; it only reduces his magic damage, "
			+ "rather than fully blocking it. Avoid melee and stomp range; move away from the dark core.", Overhead.MAGIC); }
		if (named(n, "chaos elemental")) { return rule("Chaos_Elemental/Strategies", "Keep Magic. The ordinary projectile's "
			+ "damage style cannot be identified visually, so reactive switching is not possible.", Overhead.MAGIC); }
		if (named(n, "kalphite queen")) { return rule("Kalphite_Queen/Strategies", "Keep Magic in both forms. Magic and "
			+ "ranged attacks bypass the usual accuracy roll, so armour alone cannot make them miss; Missiles is a "
			+ "similar alternative in solo fights. Her own overheads indicate defensive bonuses, not your prayer choice.", Overhead.MAGIC); }
		if (named(n, "basilisk knight") || named(n, "basilisk sentinel")) { return rule("Basilisk_Knight/Strategies", "Keep "
			+ "Magic; tank ordinary melee with armour or keep out of melee reach. A mirror / V's shield is still required.", Overhead.MAGIC); }
		if (named(n, "lizardman shaman") && !n.contains("chambers of xeric")) { return rule("Lizardman_shaman/Strategies", "Keep Missiles against the ranged spit; "
			+ "stay clear of melee, jumps and purple spawns. Shayzien armour and poison protection are separate requirements.", Overhead.MISSILES); }
		if (named(n, "kree'arra")) { return rule("Kree%27arra/Strategies", "Keep Missiles against Kree'arra's main attack. "
			+ "His ranged-magic attack deals magic damage despite its ranged accuracy; normal armour accuracy estimates "
			+ "do not describe all of his attack mechanics.", Overhead.MISSILES); }
		if (named(n, "general graardor")) { return rule("General_Graardor/Strategies", meleeRange
			? "Keep Melee while tanking Graardor. Armour covers ranged hits; non-tanks in teams generally use Missiles. "
				+ "Minions are separate attackers and require different priorities after the boss dies."
			: "Keep Missiles while safely kiting / freezing Graardor. If he reaches you, use Melee. Minions remain "
				+ "separate attackers; this assumes the movement method keeps his melee out of reach.", meleeRange ? Overhead.MELEE : Overhead.MISSILES); }
		if (named(n, "commander zilyana")) { return rule("Commander_Zilyana/Strategies", "While safely kiting Zilyana, ranged "
			+ "setups use Magic and magic setups use Missiles to cover the more dangerous minion for that armour. "
			+ "Zilyana herself only attacks when she reaches melee distance; don't stand still to tank her rapid attacks.",
			setup == CombatClass.MAGIC ? Overhead.MISSILES : meleeRange ? Overhead.MELEE : Overhead.MAGIC); }
		if (named(n, "k'ril tsutsaroth")) { return rule("K%27ril_Tsutsaroth/Strategies", meleeRange
			? "Solo / conventional melee tanks keep Melee, but it can trigger his prayer-smash special. A team tank "
				+ "in high melee-defence armour can use Magic instead. Restore prayer after smashes."
			: "Use Missiles when safely binding / kiting K'ril to cover the ranged minion. The boss only attacks "
				+ "in melee distance; switch to Melee if reached. Bodyguard damage and team roles affect the choice.",
			meleeRange ? Overhead.MELEE : Overhead.MISSILES); }
		if (named(n, "duke sucellus")) { return rule("Duke_Sucellus/Strategies", meleeRange
			? "Keep Melee and step behind the pillar after each attack to avoid the slam. Prayer only reduces slam "
				+ "damage; dodge gaze, icicles and gas."
			: "Keep Magic if fighting outside melee reach; Duke uses magic there. His intended melee method uses "
				+ "Melee and pillar movement. Specials require movement.", meleeRange ? Overhead.MELEE : Overhead.MAGIC); }
		if (named(n, "dawn")) { return rule("Grotesque_Guardians/Strategies", "Keep Missiles against Dawn and avoid falling "
			+ "debris / her freezing projectile. Switch to Melee when moving to Dusk.", Overhead.MISSILES); }
		if (named(n, "dusk")) { return rule("Grotesque_Guardians/Strategies", "Keep Melee while attacking Dusk up close; "
			+ "in his final form use Missiles if separated from him, then return to Melee when adjacent. Dodge specials.",
			meleeRange || n.contains("first form") ? Overhead.MELEE : Overhead.MISSILES); }
		if (named(n, "obor")) { return rule("Obor", "Keep Missiles for the usual melee or snare-and-magic method. "
			+ "It reduces rather than completely blocks his ranged damage; keep food and maintain distance when snaring.", Overhead.MISSILES); }
		if (named(n, "branda the fire queen") || named(n, "eldric the ice king")) { return rule("Royal_Titans", "Keep Melee "
			+ "against the titans' stomp, including the arena-wide stomp in the later phase. When only one titan "
			+ "remains it can pierce prayer. Spells and elementals require movement / target changes.", Overhead.MELEE); }
		if (named(n, "amoxliatl")) { return rule("Amoxliatl", "Keep Magic and restore it during the wind-up after her "
			+ "prayer-disabling attack. It mitigates the initial spikes / icicle, but persistent pools require movement.", Overhead.MAGIC); }
		return null;
	}

	private static boolean named(String name, String base)
	{
		return name.equals(base) || name.startsWith(base + " (");
	}

	private static boolean specialMode(String name)
	{
		return name.contains("deadman") || name.contains("echo") || name.contains("pvm arena") || name.contains("nightmare zone");
	}

	private static Advice rule(String page, String note, Overhead... prayers)
	{
		return new Advice(prayers.length == 0 ? null : prayers[0],
			Collections.unmodifiableList(Arrays.asList(prayers)), note).withSource("https://oldschool.runescape.wiki/w/" + page);
	}
}
