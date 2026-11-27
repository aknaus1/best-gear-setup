package com.bestgearsetup.ui;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.FontManager;

/** Related controls, drawn as a raised card with a collapsible header that shows a chevron. */
class SidebarSection extends JPanel
{
	/** Space below each card, outside its outline. */
	private static final int GAP = 4;

	private final Accordion accordion;
	private final String title;
	private final JButton toggle = new JButton();
	private final JPanel content = new JPanel();
	private String summary = "";
	/** What the section covers, shown under the expand / collapse hint. */
	private String description;

	private SidebarSection(Accordion accordion, String key, String title)
	{
		this.accordion = accordion;
		this.title = title;
		setName(key + "Section");
		setLayout(new BorderLayout());
		// The card is painted inside the border, leaving the gap below it in the panel's colour.
		setOpaque(false);
		setBorder(new CardBorder());
		toggle.setName(key + "Toggle");
		Controls.cardHeader(toggle);
		toggle.addActionListener(e -> setExpanded(!content.isVisible()));
		add(toggle, BorderLayout.NORTH);
		content.setName(key + "Content");
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setOpaque(false);
		content.setBorder(new EmptyBorder(2, 6, 8, 6));
		add(content, BorderLayout.CENTER);
		setExpanded(false);
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		Controls.paintFrame(g, getWidth(), getHeight() - GAP, Theme.color("card"), Theme.color("cardRim"));
	}

	JPanel getContent()
	{
		return content;
	}

	void setSummary(int count)
	{
		summary = count > 0 ? " (" + count + ")" : "";
		updateToggle();
	}

	void setToolTip(String description)
	{
		this.description = description;
		updateToggle();
	}

	void setExpanded(boolean expanded)
	{
		if (expanded && !accordion.independent)
		{
			accordion.sections.stream().filter(section -> section != this)
				.forEach(section -> section.setExpanded(false));
		}
		content.setVisible(expanded);
		updateToggle();
		revalidate();
		repaint();
	}

	private void updateToggle()
	{
		boolean open = content.isVisible();
		toggle.setText(title + summary);
		toggle.setIcon(new Controls.Chevron(open ? Controls.Chevron.DOWN : Controls.Chevron.RIGHT,
			open ? "cardEdge" : "chevron"));
		toggle.setFont(Controls.plain(open ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeFont()));
		String hint = (open ? "Collapse " : "Expand ") + title;
		toggle.setToolTipText(description == null ? hint : description);
		toggle.getAccessibleContext().setAccessibleDescription(open ? "Expanded" : "Collapsed");
	}

	/** An orange strip down the left of the open card (the outline is part of the frame), and the gap below it. */
	private final class CardBorder extends AbstractBorder
	{
		@Override
		public void paintBorder(Component c, Graphics g, int x, int y, int width, int height)
		{
			if (content.isVisible())
			{
				// Inside the chamfer's cut corners when there are any
				int inset = Theme.style("frame").equals("chamfer") ? Controls.corner() : 0;
				g.setColor(Theme.color("cardEdge"));
				g.fillRect(x + inset / 3, y + inset, 2, height - GAP - 2 * inset);
			}
		}

		@Override
		public Insets getBorderInsets(Component c)
		{
			return new Insets(1, 2, 1 + GAP, 1);
		}
	}

	/** A group of sections: only one open at a time, or each opened and collapsed on its own. */
	static class Accordion
	{
		private final List<SidebarSection> sections = new ArrayList<>();
		private final boolean independent;

		Accordion(boolean independent)
		{
			this.independent = independent;
		}

		/** Expand all and Collapse all buttons for an independent group. */
		JPanel expandCollapseAll(String key)
		{
			JPanel row = new JPanel(new GridLayout(1, 2, 4, 0));
			row.setName(key + "ExpandCollapseAll");
			row.setOpaque(false);
			row.setBorder(new EmptyBorder(0, 0, GAP, 0));
			for (boolean expand : new boolean[]{true, false})
			{
				JButton button = new JButton(expand ? "Expand all" : "Collapse all");
				button.setName(key + (expand ? "ExpandAll" : "CollapseAll"));
				Controls.secondary(button);
				button.setFocusable(false);
				button.addActionListener(e -> sections.forEach(section -> section.setExpanded(expand)));
				row.add(button);
			}
			return row;
		}

		SidebarSection create(String key, String title)
		{
			SidebarSection section = new SidebarSection(this, key, title);
			sections.add(section);
			return section;
		}
	}
}
