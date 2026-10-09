package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

	private static String read(String resource) throws IOException
	{
		try (InputStream in = DistributionNoticesTest.class.getResourceAsStream(resource))
		{
			assertNotNull(resource + " is missing", in);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static void assertBundled(String repositoryFile) throws IOException
	{
		String resource = "/META-INF/" + repositoryFile;
		// build.gradle copies the repository files into the test resources under /repository.
		assertEquals("src/main/resources" + resource + " differs from " + repositoryFile + "; copy it again",
			normalise(read("/repository/" + repositoryFile)), normalise(read(resource)));
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
