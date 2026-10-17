package com.bestgearsetup;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/** TEMPORARY: samples the search thread's stacks while the saved-profile diagnostic runs. */
public class TmpToaProfileTest
{
	@Test public void profile() throws Exception
	{
		org.junit.Assume.assumeTrue(Boolean.getBoolean("bgs.tmpprofile"));
		Thread main = Thread.currentThread();
		Map<String, Integer> self = new HashMap<>();
		Map<String, Integer> inclusive = new HashMap<>();
		int[] samples = {0};
		long[] peakUsed = {0};
		volatile_flag[0] = true;
		Thread sampler = new Thread(() ->
		{
			while (volatile_flag[0])
			{
				StackTraceElement[] trace = main.getStackTrace();
				samples[0]++;
				Runtime rt = Runtime.getRuntime();
				peakUsed[0] = Math.max(peakUsed[0], rt.totalMemory() - rt.freeMemory());
				if (samples[0] % 2000 == 0)
				{
					long after = 0;
					for (java.lang.management.MemoryPoolMXBean pool : java.lang.management.ManagementFactory.getMemoryPoolMXBeans())
					{
						if (pool.getType() == java.lang.management.MemoryType.HEAP && pool.getCollectionUsage() != null) { after += pool.getCollectionUsage().getUsed(); }
					}
					System.out.println("heap after last GC MB=" + (after >> 20) + " at sample " + samples[0]);
				}
				boolean first = true;
				java.util.Set<String> seen = new java.util.HashSet<>();
				for (StackTraceElement e : trace)
				{
					String cls = e.getClassName();
					if (!cls.startsWith("com.bestgearsetup") || cls.contains("Test")) { continue; }
					String key = cls.substring(cls.lastIndexOf('.') + 1) + "." + e.getMethodName();
					if (first) { self.merge(key + ":" + e.getLineNumber(), 1, Integer::sum); first = false; }
					if (seen.add(key)) { inclusive.merge(key, 1, Integer::sum); }
				}
				try { Thread.sleep(5); } catch (InterruptedException ex) { return; }
			}
		});
		sampler.setDaemon(true);
		sampler.start();
		try { new PlankerCoxDiagnosticTest().savedProfile(); }
		finally
		{
			volatile_flag[0] = false;
			sampler.join();
			StringBuilder out = new StringBuilder("samples=" + samples[0] + "\n== inclusive\n");
			inclusive.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(70)
				.forEach(e -> out.append(String.format("%6.1f%% %s%n", 100.0 * e.getValue() / samples[0], e.getKey())));
			out.append("== self (top frame in plugin code)\n");
			self.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(50)
				.forEach(e -> out.append(String.format("%6.1f%% %s%n", 100.0 * e.getValue() / samples[0], e.getKey())));
			Files.createDirectories(Paths.get("build", "diagnostics"));
			Files.write(Paths.get("build", "diagnostics", System.getProperty("bgs.planker.label", "tmp") + "-profile.txt"), out.toString().getBytes());
		}
	}

	private static final boolean[] volatile_flag = {false};
}
