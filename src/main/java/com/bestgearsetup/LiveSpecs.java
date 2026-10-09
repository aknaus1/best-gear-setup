package com.bestgearsetup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.plugins.specialcounter.SpecialCounterUpdate;
import net.runelite.client.plugins.specialcounter.SpecialWeapon;
import net.runelite.client.util.Text;

/**
 * Specials that landed on the target being fought, from the local player and from RuneLite party members running
 * the Special Attack Counter. A search of that target uses them in place of the planned pre-fight preparation;
 * saved settings are never changed. Own specials are matched to their hitsplat the way the Special Attack Counter
 * does. Client thread only, except {@link #overrides}.
 */
final class LiveSpecs
{
	static final String AUTO_KEY = "autoSpecs";
	/** Preparation keys this tracker fills, with each setting's upper bound. */
	private static final Map<String, Integer> LIMITS = new LinkedHashMap<>();

	static
	{
		LIMITS.put("specElderMaul", 10);
		LIMITS.put("specDwh", 10);
		LIMITS.put("specEmberlight", 20);
		LIMITS.put("specArclight", 20);
		LIMITS.put("specTonalztics", 20);
		LIMITS.put("specBgsDamage", 2000);
		LIMITS.put("specSeercullDamage", 2000);
		LIMITS.put("specAyakDamage", 2000);
	}

	private final Client client;
	private final ClientThread clientThread;
	private final PartyService party;
	private final BestGearSetupConfig config;
	/** Called with the tracked target's name whenever what landed on it changes. */
	private final Consumer<String> changed;

	private final Map<String, Integer> landed = new HashMap<>();
	private int targetIndex = -1;
	/** Lower-case name of the tracked target; read by searches off the client thread. */
	private volatile String targetName;
	private volatile Map<String, Object> snapshot = new HashMap<>();

	private int energy = -1;
	private long hitpointsXp = -1;
	private int hitpointsXpCycle = -1;
	private SpecialWeapon weapon;
	private NPC specTarget;
	private boolean specXp;
	private int hitsplatTick;
	private final List<Hitsplat> hitsplats = new ArrayList<>();

	LiveSpecs(Client client, ClientThread clientThread, PartyService party, BestGearSetupConfig config,
		Consumer<String> changed)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.party = party;
		this.config = config;
		this.changed = changed;
	}

	/**
	 * The preparation values for a search of this monster: every tracked special, so planned ones that did not
	 * land read zero. Empty when the monster isn't the target being fought.
	 */
	Map<String, Object> overrides(String monsterName)
	{
		String name = targetName;
		return name != null && sameTarget(name, monsterName) ? snapshot : new HashMap<>();
	}

	/** Whether a catalogue name ("verzik vitur (phase 2)") names the live NPC ("verzik vitur"). */
	static boolean sameTarget(String npcName, String monsterName)
	{
		String monster = monsterName.toLowerCase(Locale.ROOT);
		return monster.equals(npcName) || monster.startsWith(npcName + " (");
	}

	/** The preparation key a weapon's special fills, or null when it isn't modelled. */
	static String key(SpecialWeapon weapon)
	{
		switch (weapon)
		{
			case ELDER_MAUL: return "specElderMaul";
			case DRAGON_WARHAMMER: return "specDwh";
			case EMBERLIGHT: return "specEmberlight";
			case ARCLIGHT: return "specArclight";
			case TONALZTICS_OF_RALOS: return "specTonalztics";
			case BANDOS_GODSWORD: return "specBgsDamage";
			case SEERCULL: return "specSeercullDamage";
			case EYE_OF_AYAK: return "specAyakDamage";
			default: return null;
		}
	}

	/**
	 * What one special adds to its setting: damage for draining-by-damage weapons, landed hits for Tonalztics
	 * (already counted by the Special Attack Counter), otherwise one per successful hit.
	 */
	static int amount(SpecialWeapon weapon, int hit)
	{
		return weapon.isDamage() ? Math.max(0, hit) : hit > 0 ? 1 : 0;
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarpId() != VarPlayerID.SA_ENERGY)
		{
			return;
		}
		int previous = energy;
		energy = event.getValue();
		if (previous == -1 || energy >= previous)
		{
			return;
		}
		// Player and NPC updates follow this event, so the target is read once they have run.
		int serverTick = client.getTickCount();
		clientThread.invokeLater(() ->
		{
			Player player = client.getLocalPlayer();
			SpecialWeapon used = usedWeapon();
			if (player == null || used == null || !(player.getInteracting() instanceof NPC))
			{
				return;
			}
			weapon = used;
			specTarget = (NPC) player.getInteracting();
			specXp = hitpointsXpCycle == client.getGameCycle();
			hitsplats.clear();
			WorldArea area = specTarget.getWorldArea();
			hitsplatTick = serverTick + (area == null ? 1 : used.getHitDelay(area.distanceTo(player.getWorldLocation())));
		});
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() != Skill.HITPOINTS)
		{
			return;
		}
		if (hitpointsXp != -1 && event.getXp() > hitpointsXp)
		{
			hitpointsXpCycle = client.getGameCycle();
		}
		hitpointsXp = event.getXp();
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (event.getHitsplat().isMine() && specTarget != null && event.getActor() == specTarget
			&& hitsplatTick == client.getTickCount())
		{
			hitsplats.add(event.getHitsplat());
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (weapon == null || specTarget == null)
		{
			return;
		}
		int tick = client.getTickCount();
		if (weapon == SpecialWeapon.ELDER_MAUL)
		{
			// Its hitsplat comes late; the Hitpoints experience of the spec tick shows whether it landed.
			land(weapon, specXp ? 1 : 0, specTarget);
		}
		else if (tick == hitsplatTick)
		{
			if (weapon == SpecialWeapon.TONALZTICS_OF_RALOS)
			{
				if (hitsplats.size() < 2)
				{
					return;
				}
				int count = hitsplats.size();
				land(weapon, Math.min(hitsplats.get(count - 1).getAmount(), 1)
					+ Math.min(hitsplats.get(count - 2).getAmount(), 1), specTarget);
			}
			else
			{
				if (hitsplats.isEmpty())
				{
					return;
				}
				// The weapon's hitsplat comes after same-tick ones such as vengeance or thralls.
				land(weapon, hitsplats.get(hitsplats.size() - 1).getAmount(), specTarget);
			}
		}
		else if (tick < hitsplatTick)
		{
			return;
		}
		weapon = null;
		specTarget = null;
		hitsplats.clear();
	}

	@Subscribe
	public void onSpecialCounterUpdate(SpecialCounterUpdate event)
	{
		PartyMember local = party.getLocalMember();
		// Own specials are tracked directly.
		if (local == null || local.getMemberId() == event.getMemberId() || event.getWorld() != client.getWorld())
		{
			return;
		}
		clientThread.invoke(() ->
		{
			NPC target = client.getTopLevelWorldView().npcs().byIndex(event.getNpcIndex());
			if (target != null)
			{
				land(event.getWeapon(), event.getHit(), target);
			}
		});
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (event.getNpc().getIndex() == targetIndex)
		{
			end();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			end();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (BestGearSetupConfig.GROUP.equals(event.getGroup()) && AUTO_KEY.equals(event.getKey()) && !config.autoSpecs())
		{
			end();
		}
	}

	private void land(SpecialWeapon used, int hit, NPC target)
	{
		String key = key(used);
		if (key == null || !config.autoSpecs() || target.getName() == null)
		{
			return;
		}
		if (target.getIndex() != targetIndex)
		{
			landed.clear();
			targetIndex = target.getIndex();
			targetName = Text.removeTags(target.getName()).toLowerCase(Locale.ROOT).trim();
		}
		landed.merge(key, amount(used, used.computeHit(hit, target)), Integer::sum);
		Map<String, Object> values = new HashMap<>();
		LIMITS.forEach((k, limit) -> values.put(k, Math.min(limit, landed.getOrDefault(k, 0))));
		snapshot = values;
		changed.accept(targetName);
	}

	/** Stop tracking; the next search of the target uses the planned preparation again. */
	void end()
	{
		String name = targetName;
		landed.clear();
		targetIndex = -1;
		targetName = null;
		snapshot = new HashMap<>();
		weapon = null;
		specTarget = null;
		hitsplats.clear();
		if (name != null)
		{
			changed.accept(name);
		}
	}

	private SpecialWeapon usedWeapon()
	{
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		Item item = worn == null ? null : worn.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		if (item == null)
		{
			return null;
		}
		for (SpecialWeapon candidate : SpecialWeapon.values())
		{
			for (int id : candidate.getItemID())
			{
				if (id == item.getId())
				{
					return candidate;
				}
			}
		}
		return null;
	}
}
