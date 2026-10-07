package com.bestgearsetup.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class SidebarSectionTest
{
	@Test
	public void openingAnotherSectionClosesThePreviousOneWithoutLosingItsSummary() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			SidebarSection.Accordion accordion = new SidebarSection.Accordion();
			SidebarSection fight = accordion.create("fightConditions", "Fight conditions");
			SidebarSection gear = accordion.create("gearRules", "Locks & exclusions");
			gear.setSummary(3);
			JButton fightToggle = (JButton) fight.getComponent(0);
			JButton gearToggle = (JButton) gear.getComponent(0);
			assertFalse(fight.getContent().isVisible());
			assertFalse(gear.getContent().isVisible());
			fightToggle.doClick();
			assertTrue(fight.getContent().isVisible());
			gearToggle.doClick();
			assertFalse(fight.getContent().isVisible());
			assertTrue(gear.getContent().isVisible());
			assertEquals("- Locks & exclusions (3)", gearToggle.getText());
			fightToggle.doClick();
			assertFalse(gear.getContent().isVisible());
			assertEquals("+ Locks & exclusions (3)", gearToggle.getText());
			fightToggle.doClick();
			assertFalse(fight.getContent().isVisible());
		});
	}
}
