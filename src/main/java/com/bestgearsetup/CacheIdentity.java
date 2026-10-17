package com.bestgearsetup;

import com.bestgearsetup.data.GameData;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/** Deterministic value identities, including the installed calculation code and bundled data. */
final class CacheIdentity
{
	private static final Gson GSON = new Gson();
	private static final Map<GameData, String> DATA = Collections.synchronizedMap(new WeakHashMap<>());
	private static final String BUILD = build();

	private CacheIdentity() { }

	static String buildId() { return BUILD; }

	static String data(GameData data)
	{
		return DATA.computeIfAbsent(data, value -> digest(json(GSON.toJsonTree(value))));
	}

	static String digest(String text)
	{
		MessageDigest hash = hash();
		return hex(hash.digest(text.getBytes(StandardCharsets.UTF_8)));
	}

	static String value(Object value)
	{
		if (value == null) { return "null"; }
		if (value instanceof SearchInputs) { return ((SearchInputs) value).persistentKey(); }
		if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte
			|| value instanceof Boolean || value instanceof Double)
		{
			return value.getClass().getName() + ":" + value;
		}
		if (value instanceof Enum)
		{
			Enum<?> item = (Enum<?>) value;
			return item.getDeclaringClass().getName() + ":" + item.name();
		}
		boolean large = value instanceof Collection && ((Collection<?>) value).size() > LARGE_SIZE
			|| value instanceof Map && ((Map<?, ?>) value).size() > LARGE_SIZE;
		if (large)
		{
			String known = LARGE.get(value);
			if (known != null) { return known; }
		}
		String identity = structure(value);
		if (large)
		{
			// Searches share their price, ownership and exclusion snapshots, so remember their digests by content.
			identity = "large:" + digest(identity);
			LARGE.put(copy(value), identity);
		}
		return identity;
	}

	/** Collections whose digest is remembered by content: comparing contents costs far less than serialising them. */
	private static final int LARGE_SIZE = 64;
	private static final Map<Object, String> LARGE = Collections.synchronizedMap(new LinkedHashMap<Object, String>(16, 0.75f, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<Object, String> eldest)
		{
			return size() > 32;
		}
	});

	private static Object copy(Object value)
	{
		if (value instanceof Map) { return new HashMap<>((Map<?, ?>) value); }
		if (value instanceof Set) { return new HashSet<>((Set<?>) value); }
		return new ArrayList<>((Collection<?>) value);
	}

	private static String structure(Object value)
	{
		if (value instanceof Map)
		{
			List<String> entries = new ArrayList<>();
			((Map<?, ?>) value).forEach((key, item) -> entries.add(value(key) + "=" + value(item)));
			Collections.sort(entries);
			return "map" + GSON.toJson(entries);
		}
		if (value instanceof Collection)
		{
			Collection<?> items = (Collection<?>) value;
			if (value instanceof Set && !items.isEmpty() && items.stream().allMatch(Integer.class::isInstance))
			{
				// Item id sets, the bulk of every key: sort numerically rather than as strings.
				int[] ids = items.stream().mapToInt(Integer.class::cast).sorted().toArray();
				return "intset" + java.util.Arrays.toString(ids);
			}
			List<String> entries = items.stream().map(CacheIdentity::value).collect(Collectors.toList());
			if (value instanceof Set) { Collections.sort(entries); }
			return (value instanceof Set ? "set" : "list") + GSON.toJson(entries);
		}
		return value.getClass().getName() + ":" + json(GSON.toJsonTree(value));
	}

	/** Gson object member order is not a stable identity; sort members recursively. */
	private static String json(JsonElement element)
	{
		return ordered(element).toString();
	}

	private static JsonElement ordered(JsonElement element)
	{
		if (element.isJsonObject())
		{
			JsonObject sorted = new JsonObject();
			element.getAsJsonObject().entrySet().stream().sorted(Map.Entry.comparingByKey())
				.forEach(entry -> sorted.add(entry.getKey(), ordered(entry.getValue())));
			return sorted;
		}
		if (element.isJsonArray())
		{
			com.google.gson.JsonArray values = new com.google.gson.JsonArray();
			element.getAsJsonArray().forEach(item -> values.add(ordered(item)));
			return values;
		}
		return element;
	}

	private static String build()
	{
		try
		{
			MessageDigest hash = hash();
			List<URL> roots = Collections.list(CacheIdentity.class.getClassLoader().getResources("com/bestgearsetup"));
			if (roots.isEmpty())
			{
				URL own = CacheIdentity.class.getResource("CacheIdentity.class");
				if (own != null) { roots.add(new URL(own.toString().replace("CacheIdentity.class", ""))); }
			}
			roots.sort(Comparator.comparing(URL::toString));
			int hashed = 0;
			for (URL root : roots)
			{
				if ("file".equals(root.getProtocol()))
				{
					Path base = Paths.get(root.toURI());
					List<Path> files;
					try (java.util.stream.Stream<Path> paths = Files.walk(base))
					{ files = paths.filter(Files::isRegularFile).sorted().collect(Collectors.toList()); }
					for (Path file : files)
					{
						// Test fixtures are not part of the installed plugin's compatibility identity.
						if (base.toString().replace('\\', '/').contains("/classes/java/test/")) { continue; }
						update(hash, base.relativize(file).toString().replace('\\', '/'), Files.newInputStream(file));
						hashed++;
					}
				}
				else if ("jar".equals(root.getProtocol()))
				{
					JarURLConnection connection = (JarURLConnection) root.openConnection();
					connection.setUseCaches(false);
					try (JarFile jar = connection.getJarFile())
					{
						List<JarEntry> entries = jar.stream().filter(entry -> !entry.isDirectory()
							&& entry.getName().startsWith("com/bestgearsetup/")).sorted(Comparator.comparing(JarEntry::getName)).collect(Collectors.toList());
						for (JarEntry entry : entries) { update(hash, entry.getName().substring(18), jar.getInputStream(entry)); hashed++; }
					}
				}
			}
			// An unrecognised resource scheme hashes nothing, which would give every release the same identity.
			if (hashed == 0) { throw new IOException("Missing plugin classes"); }
			return hex(hash.digest());
		}
		catch (Exception e)
		{
			// A compatibility identity must never silently accept another installation's saved results.
			return "session-" + java.util.UUID.randomUUID();
		}
	}

	private static void update(MessageDigest hash, String name, InputStream input) throws IOException
	{
		hash.update(name.getBytes(StandardCharsets.UTF_8));
		hash.update((byte) 0);
		try (InputStream stream = input)
		{
			byte[] buffer = new byte[8192];
			int length;
			while ((length = stream.read(buffer)) != -1) { hash.update(buffer, 0, length); }
		}
	}

	private static MessageDigest hash()
	{
		try { return MessageDigest.getInstance("SHA-256"); }
		catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
	}

	private static String hex(byte[] bytes)
	{
		StringBuilder result = new StringBuilder();
		for (byte value : bytes) { result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255)); }
		return result.toString();
	}
}
