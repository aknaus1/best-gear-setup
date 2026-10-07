package com.bestgearsetup.ui;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JLabel;
import java.awt.Color;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

/** Related controls; separate editors display these groups with always-open headings. */
class SidebarSection extends JPanel
{
	private final Accordion accordion;
	private final String title;
	private final JButton toggle = new JButton();
	private final JPanel content = new JPanel();
	private String summary = "";
	private JLabel heading;
	/** Always open; opening another section must not collapse it. */
	private boolean editor;

	/** Flat heading for a separate editor; no nested expand/collapse control. */
	void showAsEditor()
	{
		remove(toggle);
		editor = true;
		heading = new JLabel(title);
		BestGearSetupPanel.setSmall(heading, Color.WHITE);
		add(heading, BorderLayout.NORTH);
		content.setVisible(true);
	}

	private SidebarSection(Accordion accordion, String key, String title)
	{
		this.accordion = accordion;
		this.title = title;
		setName(key + "Section");
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(0, 0, 3, 0));
		toggle.setName(key + "Toggle");
		toggle.addActionListener(e -> setExpanded(!content.isVisible()));
		add(toggle, BorderLayout.NORTH);
		content.setName(key + "Content");
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.setBorder(new EmptyBorder(4, 0, 4, 0));
		add(content, BorderLayout.CENTER);
		setExpanded(false);
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

	private void setExpanded(boolean expanded)
	{
		if (expanded)
		{
			accordion.sections.stream().filter(section -> section != this && !section.editor)
				.forEach(section -> section.setExpanded(false));
		}
		content.setVisible(expanded);
		updateToggle();
		revalidate();
	}

	private void updateToggle()
	{
		toggle.setText((content.isVisible() ? "- " : "+ ") + title + summary);
		toggle.setToolTipText((content.isVisible() ? "Collapse " : "Expand ") + title);
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
