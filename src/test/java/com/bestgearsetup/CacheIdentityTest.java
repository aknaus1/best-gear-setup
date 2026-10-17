package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CacheIdentityTest
{
	@Rule public final TemporaryFolder temporary = new TemporaryFolder();

	@Test public void localJarFingerprintIsStableAndTracksBundledData() throws Exception
	{
		Path jar = temporary.newFolder("plugin with spaces").toPath().resolve("plugin.jar");
		writeJar(jar, "first catalogue");
		String first = fingerprint(jar);
		assertFalse("Packaged plugins must retain a persistent build identity", first.startsWith("session-"));
		assertEquals(first, fingerprint(jar));
		writeJar(jar, "changed catalogue");
		assertNotEquals(first, fingerprint(jar));
	}

	private static void writeJar(Path path, String data) throws Exception
	{
		try (JarOutputStream jar = new JarOutputStream(java.nio.file.Files.newOutputStream(path)))
		{
			for (String name : new String[]{"CacheIdentity.class", "CacheIdentity$1.class"})
			{
				jar.putNextEntry(new JarEntry("com/bestgearsetup/" + name));
				try (InputStream source = CacheIdentity.class.getResourceAsStream(name)) { source.transferTo(jar); }
				jar.closeEntry();
			}
			jar.putNextEntry(new JarEntry("com/bestgearsetup/catalogue.json"));
			jar.write(data.getBytes(StandardCharsets.UTF_8));
			jar.closeEntry();
		}
	}

	private static String fingerprint(Path path) throws Exception
	{
		URL file = path.toUri().toURL();
		URL root = new URL("jar:" + file.toExternalForm() + "!/com/bestgearsetup/");
		try (URLClassLoader loader = new URLClassLoader(new URL[]{file}, CacheIdentityTest.class.getClassLoader())
		{
			@Override public Enumeration<URL> getResources(String name) throws java.io.IOException
			{
				return "com/bestgearsetup".equals(name) ? Collections.enumeration(Collections.singleton(root))
					: super.getResources(name);
			}

			@Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException
			{
				if (!name.startsWith("com.bestgearsetup.CacheIdentity")) { return super.loadClass(name, resolve); }
				synchronized (getClassLoadingLock(name))
				{
					Class<?> type = findLoadedClass(name);
					if (type == null) { type = findClass(name); }
					if (resolve) { resolveClass(type); }
					return type;
				}
			}
		})
		{
			Class<?> type = loader.loadClass("com.bestgearsetup.CacheIdentity");
			Method method = type.getDeclaredMethod("buildId");
			method.setAccessible(true);
			return (String) method.invoke(null);
		}
	}
}
