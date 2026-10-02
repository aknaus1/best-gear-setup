package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

/**
 * Plugin Hub distributes the JAR alone (and replaces build.gradle), so the licence and data notices are
 * resources; they must stay identical to the repository copies.
 */
public class DistributionNoticesTest
{
	private static String normalise(String text)
	{
		return text.replace("\r\n", "\n");
	}

	private static void assertBundled(String repositoryFile) throws IOException
	{
		String resource = "/META-INF/" + repositoryFile;
		try (InputStream in = DistributionNoticesTest.class.getResourceAsStream(resource))
		{
			assertNotNull(resource + " is missing from the JAR resources", in);
			String bundled = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			String original = new String(Files.readAllBytes(Paths.get(repositoryFile)), StandardCharsets.UTF_8);
			assertEquals("src/main/resources" + resource + " differs from " + repositoryFile + "; copy it again",
				normalise(original), normalise(bundled));
		}
	}

	@Test
	public void licenceIsBundled() throws IOException
	{
		assertBundled("LICENSE");
	}

	@Test
	public void thirdPartyNoticesAreBundled() throws IOException
	{
		assertBundled("THIRD_PARTY_NOTICES.md");
	}
}
