package com.bestgearsetup.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Related controls, drawn as a raised card. A collapsible card's header shows a chevron; separate
 * editors show their groups as always-open cards with a plain heading.
 */
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
	private JLabel heading;
	/** Always open; opening another section must not collapse it. */
	private boolean editor;

	/** Always open, for a separate editor: a plain heading and no expand/collapse control. */
	void showAsEditor()
	{
		remove(toggle);
		editor = true;
		heading = new JLabel(title + summary);
		heading.setFont(Controls.plain(FontManager.getRunescapeBoldFont()));
		heading.setForeground(Color.WHITE);
		heading.setBorder(new EmptyBorder(6, 7, 2, 6));
		add(heading, BorderLayout.NORTH);
		content.setVisible(true);
		updateToggle();
	}

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
		content.setBackground(Controls.CARD);
		content.setBorder(new EmptyBorder(2, 6, 8, 6));
		add(content, BorderLayout.CENTER);
		setExpanded(false);
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		g.setColor(Controls.CARD);
		g.fillRect(0, 0, getWidth(), getHeight() - GAP);
	}

	JPanel getContent()
	{
		return content;
	}

	void setSummary(int count)
	{
		summary = count > 0 ? " (" + count + ")" : "";
		updateToggle();
		if (heading != null)
		{
			heading.setText(title + summary);
		}
	}

	void setToolTip(String description)
	{
		this.description = description;
		updateToggle();
	}

	void setExpanded(boolean expanded)
	{
		if (expanded)
		{
			accordion.sections.stream().filter(section -> section != this && !section.editor)
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
			open ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR));
		toggle.setFont(Controls.plain(open ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeFont()));
		String hint = (open ? "Collapse " : "Expand ") + title;
		toggle.setToolTipText(description == null ? hint : "<html>" + hint + "<br>" + BestGearSetupPanel.escape(description) + "</html>");
		toggle.getAccessibleContext().setAccessibleDescription(open ? "Expanded" : "Collapsed");
	}

	/** The card's outline, an orange strip down the left of the open card, and the gap below it. */
	private final class CardBorder extends AbstractBorder
	{
		@Override
		public void paintBorder(Component c, Graphics g, int x, int y, int width, int height)
		{
			int cardHeight = height - GAP;
			g.setColor(Controls.CARD_OUTLINE);
			g.drawRect(x, y, width - 1, cardHeight - 1);
			if (content.isVisible() && !editor)
			{
				g.setColor(ColorScheme.BRAND_ORANGE);
				g.fillRect(x, y, 2, cardHeight);
			}
		}

		@Override
		public Insets getBorderInsets(Component c)
		{
			return new Insets(1, 2, 1 + GAP, 1);
		}
	}

	/** Shared across fight, potion and gear sections so only one options panel is open at a time. */
	static class Accordion
	{
		private final List<SidebarSection> sections = new ArrayList<>();

		SidebarSection create(String key, String title)
		{
			SidebarSection section = new SidebarSection(this, key, title);
			sections.add(section);
			return section;
		}
	}
}
