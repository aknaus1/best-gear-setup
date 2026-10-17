package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

public class CacheIdentityTest
{
	private static final String PACKAGE = "com/bestgearsetup/";

	@Test public void classpathFingerprintIsPersistent()
	{
		String id = CacheIdentity.buildId();
		assertFalse("Installed plugins must retain a persistent build identity", id.startsWith("session-"));
		assertTrue(id.matches("[0-9a-f]{64}"));
		assertEquals(id, fingerprint(new HashMap<>()));
	}

	@Test public void fingerprintTracksBundledData()
	{
		String first = fingerprint(Map.of(PACKAGE + "slayer-targets.json", bytes("first catalogue")));
		assertEquals(first, fingerprint(Map.of(PACKAGE + "slayer-targets.json", bytes("first catalogue"))));
		assertNotEquals(first, fingerprint(Map.of(PACKAGE + "slayer-targets.json", bytes("changed catalogue"))));
	}

	@Test public void fingerprintTracksClassesReachableFromThePlugin() throws Exception
	{
		String name = PACKAGE + "calc/Optimizer.class";
		byte[] code;
		try (InputStream in = CacheIdentity.class.getResourceAsStream("/" + name)) { code = in.readAllBytes(); }
		byte[] changed = java.util.Arrays.copyOf(code, code.length + 1);
		assertNotEquals(fingerprint(new HashMap<>()), fingerprint(Map.of(name, changed)));
	}

	@Test public void missingCodeOrDataGivesASessionIdentity()
	{
		Map<String, byte[]> noData = new HashMap<>();
		noData.put(PACKAGE + "wiki-gamedata.json.gz", null);
		assertTrue(fingerprint(noData).startsWith("session-"));
		Map<String, byte[]> noPlugin = new HashMap<>();
		noPlugin.put(PACKAGE + "BestGearSetupPlugin.class", null);
		String session = fingerprint(noPlugin);
		assertTrue(session.startsWith("session-"));
		assertNotEquals(session, fingerprint(noPlugin));
	}

	@Test public void identityCoversEveryBundledClassAndDataFile() throws Exception
	{
		Path main = Paths.get(CacheIdentity.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		Set<String> classes;
		try (Stream<Path> files = Files.walk(main.resolve(PACKAGE)))
		{
			classes = files.filter(file -> file.toString().endsWith(".class"))
				.map(file -> main.relativize(file).toString().replace('\\', '/').replaceFirst("\\.class$", ""))
				.collect(Collectors.toCollection(TreeSet::new));
		}
		assertEquals("Every compiled plugin class must be reachable from the plugin entry point",
			classes, new TreeSet<>(CacheIdentity.classes(CacheIdentity.BUNDLED).keySet()));

		Path resources = Paths.get("src/main/resources").resolve(PACKAGE);
		Set<String> data;
		try (Stream<Path> files = Files.list(resources))
		{
			data = files.map(file -> file.getFileName().toString()).collect(Collectors.toCollection(TreeSet::new));
		}
		assertEquals("Every bundled data file must be part of the build identity",
			data, new TreeSet<>(CacheIdentity.DATA_RESOURCES));
	}

	private static byte[] bytes(String text)
	{
		return text.getBytes(StandardCharsets.UTF_8);
	}

	/** CacheIdentity's build identity with some classpath resources overridden; a null value hides a resource. */
	private static String fingerprint(Map<String, byte[]> overrides)
	{
		return CacheIdentity.build(name ->
		{
			if (!overrides.containsKey(name)) { return CacheIdentity.BUNDLED.apply(name); }
			byte[] content = overrides.get(name);
			return content == null ? null : new ByteArrayInputStream(content);
		});
	}
}
