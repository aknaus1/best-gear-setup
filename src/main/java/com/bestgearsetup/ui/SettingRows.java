package com.bestgearsetup.ui;

import com.bestgearsetup.BestGearSetupConfig;
import com.bestgearsetup.BestGearSetupPlugin;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.ColorJButton;
import net.runelite.client.util.ColorUtil;

/**
 * Editor rows for config items. Names, descriptions, types, ranges and units come from the
 * {@link ConfigItem} annotations through RuneLite's config descriptor, so they are declared only once;
 * values are read through each setting's getter. Rows are transparent, so they take the colour of the
 * panel or card that holds them.
 */
final class SettingRows
{
	private final BestGearSetupPlugin plugin;
	private final BestGearSetupConfig config;
	private final Map<String, ConfigItemDescriptor> descriptors = new HashMap<>();
	/** One width for every spinner in a panel, so the column lines up whatever each one's maximum is. */
	private final int spinnerColumns;
	/** Runs after a change is saved, e.g. to show or hide dependent rows. */
	private final Runnable onChange;
	private final Map<String, Setting> settings = new LinkedHashMap<>();
	private final Map<String, JComponent> controls = new HashMap<>();
	private final Map<String, JPanel> rows = new HashMap<>();
	private boolean syncing;

	SettingRows(BestGearSetupPlugin plugin, BestGearSetupConfig config, Collection<ConfigItemDescriptor> items,
		int spinnerColumns, Runnable onChange)
	{
		this.plugin = plugin;
		this.config = config;
		this.spinnerColumns = spinnerColumns;
		this.onChange = onChange;
		for (ConfigItemDescriptor descriptor : items)
		{
			descriptors.put(descriptor.key(), descriptor);
		}
	}

	static Setting setting(String key, Function<BestGearSetupConfig, Object> getter)
	{
		return new Setting(key, getter);
	}

	/** Build the row editing this setting, named {@code key + "Row"}; its control is named after the key. */
	JPanel add(Setting entry)
	{
		ConfigItemDescriptor descriptor = descriptors.get(entry.key);
		if (descriptor == null)
		{
			throw new IllegalStateException("Missing setting " + entry.key);
		}
		ConfigItem item = descriptor.getItem();
		Object type = descriptor.getType();
		JComponent control;
		JPanel row;
		if (type == boolean.class)
		{
			JCheckBox box = new JCheckBox(item.name());
			Controls.checkBox(box);
			box.setOpaque(false);
			box.setForeground(Color.WHITE);
			box.setFont(FontManager.getRunescapeSmallFont());
			box.addActionListener(e -> save(item, box.isSelected()));
			control = box;
			row = BestGearSetupPanel.left(box);
			row.setOpaque(false);
		}
		else if (type instanceof Class && ((Class<?>) type).isEnum())
		{
			JComboBox<Object> choice = new JComboBox<>(((Class<?>) type).getEnumConstants());
			Controls.field(choice);
			choice.addActionListener(e -> save(item, choice.getSelectedItem()));
			control = choice;
			row = captioned(item, choice);
		}
		else if (type == String.class)
		{
			JTextField field = new JTextField();
			Controls.field(field);
			Runnable commit = () ->
			{
				if (!field.getText().equals(entry.getter.apply(config)))
				{
					save(item, field.getText());
				}
			};
			field.addActionListener(e -> commit.run());
			field.addFocusListener(new FocusAdapter()
			{
				@Override
				public void focusLost(FocusEvent e)
				{
					commit.run();
				}
			});
			control = field;
			row = captioned(item, field);
		}
		else if (type == Color.class)
		{
			ColorJButton swatch = new ColorJButton("", Color.BLACK);
			swatch.setFocusable(false);
			swatch.setFont(FontManager.getRunescapeSmallFont());
			swatch.addActionListener(e -> plugin.pickColor(swatch, swatch.getColor(), item.name(),
				color -> showColor(swatch, color), color -> save(item, color)));
			control = swatch;
			row = inline(item, descriptor, swatch);
		}
		else
		{
			boolean decimal = type == double.class;
			Range range = descriptor.getRange();
			int min = range == null ? 0 : range.min();
			int max = range == null ? Integer.MAX_VALUE : range.max();
			JSpinner spinner = new JSpinner(decimal ? new SpinnerNumberModel(0.0, min, max, 0.1)
				: new SpinnerNumberModel(min, min, max, 1));
			spinner.setEditor(new JSpinner.NumberEditor(spinner, decimal ? "0.0#" : "#"));
			((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setColumns(spinnerColumns);
			Controls.field(spinner);
			spinner.addChangeListener(e -> save(item, decimal ? spinner.getValue()
				: (Object) ((Number) spinner.getValue()).intValue()));
			control = spinner;
			row = inline(item, descriptor, spinner);
		}
		control.setName(item.keyName());
		control.setToolTipText(item.description());
		row.setName(entry.key + "Row");
		settings.put(entry.key, entry);
		controls.put(entry.key, control);
		rows.put(entry.key, row);
		return row;
	}

	JPanel row(String key)
	{
		return rows.get(key);
	}

	JComponent control(String key)
	{
		JComponent control = controls.get(key);
		if (control == null)
		{
			throw new IllegalStateException("Missing setting " + key);
		}
		return control;
	}

	boolean selected(String key)
	{
		return ((JCheckBox) control(key)).isSelected();
	}

	/** Show the saved values without saving them again. */
	void refresh()
	{
		syncing = true;
		try
		{
			for (Setting entry : settings.values())
			{
				Object value = entry.getter.apply(config);
				JComponent control = control(entry.key);
				if (control instanceof JCheckBox)
				{
					((JCheckBox) control).setSelected((boolean) value);
				}
				else if (control instanceof JComboBox)
				{
					((JComboBox<?>) control).setSelectedItem(value);
				}
				else if (control instanceof JTextField)
				{
					((JTextField) control).setText((String) value);
				}
				else if (control instanceof ColorJButton)
				{
					showColor((ColorJButton) control, (Color) value);
				}
				else
				{
					((JSpinner) control).setValue(value);
				}
			}
		}
		finally
		{
			syncing = false;
		}
	}

	private void save(ConfigItem item, Object value)
	{
		if (!syncing)
		{
			plugin.setConfig(item.keyName(), value);
			onChange.run();
		}
	}

	private static void showColor(ColorJButton swatch, Color color)
	{
		swatch.setColor(color);
		swatch.setText("#" + ColorUtil.colorToHexCode(color).toUpperCase(Locale.ROOT));
	}

	/** A caption above a full-width control. */
	private static JPanel captioned(ConfigItem item, JComponent control)
	{
		JPanel row = new JPanel(new BorderLayout(4, 2));
		row.setOpaque(false);
		JLabel label = Controls.caption(item.name());
		label.setLabelFor(control);
		row.add(label, BorderLayout.NORTH);
		row.add(control, BorderLayout.CENTER);
		return spaced(row);
	}

	/** A label beside a compact control such as a spinner or color swatch. */
	private static JPanel inline(ConfigItem item, ConfigItemDescriptor descriptor, JComponent control)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		Units units = descriptor.getUnits();
		JLabel label = new JLabel(units == null ? item.name() : item.name() + " (" + units.value().trim() + ")");
		label.setLabelFor(control);
		BestGearSetupPanel.setSmall(label, Color.WHITE);
		label.setToolTipText(item.description());
		row.add(label, BorderLayout.CENTER);
		row.add(control, BorderLayout.EAST);
		return spaced(row);
	}

	/** Check boxes carry their own padding; other rows get a matching gap above. */
	private static JPanel spaced(JPanel row)
	{
		JPanel wrap = BestGearSetupPanel.left(row);
		wrap.setOpaque(false);
		wrap.setBorder(new EmptyBorder(3, 0, 1, 0));
		return wrap;
	}

	static final class Setting
	{
		private final String key;
		private final Function<BestGearSetupConfig, Object> getter;

		private Setting(String key, Function<BestGearSetupConfig, Object> getter)
		{
			this.key = key;
			this.getter = getter;
		}

		String getKey()
		{
			return key;
		}
	}
}
