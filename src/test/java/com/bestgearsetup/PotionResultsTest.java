package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Potion;
import com.bestgearsetup.data.WikiGameData;
import java.util.List;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Test;

public class PotionResultsTest
{
	@Test
	public void combinedPotionResultsPreserveBothBoostedSkills() throws Exception
	{
		GameData data = WikiGameData.get(RuneLiteAPI.GSON).gameData(RuneLiteAPI.GSON);
		BestGearSetupConfig config = new BestGearSetupConfig()
		{
			@Override
			public String potionMelee()
			{
				return PotionOptions.Melee.SUPER_COMBAT_POTION.value();
			}
		};
		Monster glough = new Monster();
		glough.setName("glough");
		List<Potion> melee = new BestGearSetupPlugin().potionsUsed(data, PlayerLevels.maxed(), glough,
			p -> true, config).get(CombatClass.MELEE);
		assertEquals(2, melee.size());
		assertEquals("attack", melee.get(0).getSkill());
		assertEquals("strength", melee.get(1).getSkill());
		assertEquals(melee.get(0).getId(), melee.get(1).getId());
	}
}
