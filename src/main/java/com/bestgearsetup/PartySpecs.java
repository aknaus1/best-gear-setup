package com.bestgearsetup;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.plugins.specialcounter.SpecialCounterUpdate;

/**
 * Party members' specials, read from the updates RuneLite's Special Attack Counter shares over RuneLite Party.
 * This is the only class that uses that plugin's classes: if they are missing or have changed, registering or
 * handling an update fails with a {@link LinkageError}, party specials stop, and {@link LiveSpecs} keeps tracking
 * the player's own. Updates only arrive while the Special Attack Counter runs here and for the sender.
 */
@Slf4j
final class PartySpecs
{
	private final Client client;
	private final ClientThread clientThread;
	private final PartyService party;
	private final LiveSpecs specs;
	private boolean broken;

	PartySpecs(Client client, ClientThread clientThread, PartyService party, LiveSpecs specs)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.party = party;
		this.specs = specs;
	}

	@Subscribe
	public void onSpecialCounterUpdate(SpecialCounterUpdate event)
	{
		if (broken)
		{
			return;
		}
		try
		{
			PartyMember local = party.getLocalMember();
			// Own specials are tracked directly.
			if (local == null || local.getMemberId() == event.getMemberId() || event.getWorld() != client.getWorld())
			{
				return;
			}
			int npcIndex = event.getNpcIndex();
			String weapon = event.getWeapon().name();
			int hit = event.getHit();
			clientThread.invoke(() -> specs.partySpec(npcIndex, weapon, hit));
		}
		catch (LinkageError e)
		{
			broken = true;
			log.warn("Special Attack Counter updates changed; party specs are off, own specs are still tracked", e);
		}
	}
}
