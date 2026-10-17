package com.bestgearsetup.ui;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.HierarchyEvent;
import java.util.function.LongSupplier;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.Timer;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** Animation and elapsed time remain active even when completed-work progress is unchanged. */
final class SearchActivity extends JLabel
{
	private final LongSupplier clock;
	private final Timer animation = new Timer(125, event -> tick());
	private boolean running;
	private long started;
	private int frame;
	private String phase;

	SearchActivity() { this(System::nanoTime); }

	SearchActivity(LongSupplier clock)
	{
		this.clock = clock;
		setName("searchActivity");
		setFont(FontManager.getRunescapeSmallFont());
		setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		setIcon(new Spinner());
		setIconTextGap(6);
		setToolTipText("Search in progress. The percentage estimates completed comparisons. Adaptive searches discover extra work; comparison times vary.");
		setVisible(false);
		// Hidden sidebar/editor views need no animation; showing them resumes the same search's elapsed time.
		addHierarchyListener(event ->
		{
			if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0)
			{
				if (running && isShowing()) { animation.start(); }
				else { animation.stop(); }
			}
		});
	}

	void start()
	{
		running = true;
		started = clock.getAsLong();
		frame = 0;
		phase = "Preparing search";
		updateText();
		setVisible(true);
		if (isShowing()) { animation.start(); }
	}

	void stage(String message)
	{
		if (!running) { return; }
		phase = message;
		updateText();
	}

	void stop()
	{
		running = false;
		animation.stop();
		setVisible(false);
	}

	boolean isRunning() { return running; }

	void tick()
	{
		if (!running) { return; }
		frame = (frame + 1) % 8;
		updateText();
		repaint();
	}

	private void updateText()
	{
		long seconds = Math.max(0, clock.getAsLong() - started) / 1_000_000_000L;
		String elapsed = seconds < 60 ? seconds + "s" : seconds / 60 + "m " + seconds % 60 + "s";
		String text = "<html><div style='width:160px'>" + BestGearSetupPanel.escape(phase) + "… · " + elapsed + "</div></html>";
		if (!text.equals(getText())) { setText(text); }
	}

	private final class Spinner implements Icon
	{
		@Override public int getIconWidth() { return 14; }
		@Override public int getIconHeight() { return 14; }

		@Override public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			try
			{
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				for (int dot = 0; dot < 8; dot++)
				{
					g.setColor((dot + 8 - frame) % 8 < 3 ? ColorScheme.BRAND_ORANGE : ColorScheme.MEDIUM_GRAY_COLOR);
					double angle = dot * Math.PI / 4;
					g.fillOval(x + 5 + (int) Math.round(5 * Math.cos(angle)), y + 5 + (int) Math.round(5 * Math.sin(angle)), 3, 3);
				}
			}
			finally { g.dispose(); }
		}
	}
}
