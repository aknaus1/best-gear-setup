package com.bestgearsetup.ui;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import net.runelite.client.game.SpriteManager;

/**
 * Loads game sprites (slot tiles, empty-slot icons, prayer icons) once and notifies waiting
 * components on the Swing thread. Swing thread only.
 */
class SpriteCache
{
	private final SpriteManager spriteManager;
	private final Map<Integer, BufferedImage> images = new HashMap<>();
	private final Map<Integer, List<Runnable>> waiting = new HashMap<>();

	SpriteCache(SpriteManager spriteManager)
	{
		this.spriteManager = spriteManager;
	}

	/** The sprite if loaded; otherwise starts loading it and runs {@code onReady} when done. */
	BufferedImage get(int spriteId, Runnable onReady)
	{
		BufferedImage img = images.get(spriteId);
		if (img != null)
		{
			return img;
		}
		List<Runnable> list = waiting.get(spriteId);
		if (list != null)
		{
			list.add(onReady);
			return null;
		}
		list = new ArrayList<>();
		list.add(onReady);
		waiting.put(spriteId, list);
		spriteManager.getSpriteAsync(spriteId, 0, loaded -> SwingUtilities.invokeLater(() ->
		{
			if (loaded != null)
			{
				images.put(spriteId, loaded);
			}
			List<Runnable> callbacks = waiting.remove(spriteId);
			if (callbacks != null && loaded != null)
			{
				callbacks.forEach(Runnable::run);
			}
		}));
		return null;
	}
}
