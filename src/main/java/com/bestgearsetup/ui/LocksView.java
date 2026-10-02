package com.bestgearsetup.ui;

import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.calc.LockStatus;
import com.bestgearsetup.data.GameData;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The slot locks a search used: what each slot is locked to, what the lock does, and, in red, any lock
 * that could not be applied and why, with a link to remove it.
 */
class LocksView extends JPanel
{
	private static final Color LOCKED = new Color(0xFF, 0x98, 0x1F);
	private static final Color BLOCKED = new Color(0xFF, 0x4D, 0x4D);
	/** CSS width of a row's text, beside the item icon and the Unlock link. */
	private static final int TEXT_WIDTH = 105;

	LocksView(List<LockStatus> locks, BestGearSetupPlugin plugin, ItemManager itemManager)
	{
		long blocked = locks.stream().filter(LockStatus::isBlocking).count();
		boolean blocking = blocked > 0;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(blocking ? BLOCKED : LOCKED),
			new EmptyBorder(3, 4, 3, 4)));
		JLabel header = new JLabel(!blocking ? "Locks" : blocked == 1 ? "Locks (1 can't be used)"
			: "Locks (" + blocked + " can't be used)");
		header.setFont(FontManager.getRunescapeBoldFont());
		header.setForeground(blocking ? BLOCKED : LOCKED);
		add(inset(header));
		for (LockStatus lock : locks)
		{
			add(inset(row(lock, plugin, itemManager)));
		}
	}

	private static JPanel row(LockStatus lock, BestGearSetupPlugin plugin, ItemManager itemManager)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(2, 0, 2, 0));
		row.setName("lock" + lock.getSlot().name());

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(36, 32));
		if (lock.getItem() != null && itemManager != null)
		{
			itemManager.getImage(lock.getItem().getId()).addTo(icon);
		}
		row.add(icon, BorderLayout.WEST);

		StringBuilder text = new StringBuilder("<b>").append(lock.getSlot().getDisplayName()).append(":</b> ")
			.append(BestGearSetupPanel.escape(target(lock)));
		if (lock.isBlocking())
		{
			text.append("<br><font color='#FF6B6B'>Can't be used: ").append(BestGearSetupPanel.escape(lock.getProblem()))
				.append(".</font>");
		}
		if (lock.getEffect() != null)
		{
			text.append("<br>").append(BestGearSetupPanel.escape(lock.getEffect()));
		}
		JLabel label = new JLabel(BestGearSetupPanel.html(text.toString(), TEXT_WIDTH));
		BestGearSetupPanel.setSmall(label, Color.WHITE);
		row.add(label, BorderLayout.CENTER);

		JLabel unlock = new JLabel("Unlock");
		BestGearSetupPanel.setSmall(unlock, ColorScheme.LIGHT_GRAY_COLOR);
		unlock.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		unlock.setToolTipText("Remove this lock and search again");
		unlock.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				plugin.setLock(lock.getSlot(), null);
			}
		});
		row.add(unlock, BorderLayout.EAST);
		return row;
	}

	private static JPanel inset(java.awt.Component c)
	{
		JPanel p = BestGearSetupPanel.left(c);
		p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		return p;
	}

	private static String target(LockStatus lock)
	{
		switch (lock.getLock().getKind())
		{
			case EMPTY:
				return "kept empty";
			case FILL:
				return "always filled";
			default:
				return lock.getItem() == null ? "item " + lock.getLock().getItemId() : GameData.titleCase(lock.getItem().getName());
		}
	}
}
