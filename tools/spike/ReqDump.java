import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import net.runelite.cache.ItemManager;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.fs.Store;

/**
 * Spike: dump wear requirements (skill index -> level) and name/members/tradeable for every item with
 * any requirement or equipment stat params. Requirement pairs: 434/436, 435/437, 191/613, 579/614,
 * 610/615, 611/616, 612/617 (skill param / level param).
 */
public class ReqDump
{
	private static final int[][] PAIRS = {{434, 436}, {435, 437}, {191, 613}, {579, 614}, {610, 615}, {611, 616}, {612, 617}};

	public static void main(String[] args) throws Exception
	{
		Map<Integer, Map<String, Object>> out = new TreeMap<>();
		try (Store store = new Store(new File(args[0])))
		{
			store.load();
			ItemManager items = new ItemManager(store);
			items.load();
			for (ItemDefinition d : items.getItems())
			{
				if (d.params == null)
				{
					continue;
				}
				Map<Integer, Integer> reqs = new TreeMap<>();
				for (int[] pair : PAIRS)
				{
					Object skill = d.params.get(pair[0]);
					Object level = d.params.get(pair[1]);
					if (skill instanceof Integer && level instanceof Integer)
					{
						reqs.merge((Integer) skill, (Integer) level, Math::max);
					}
				}
				Map<String, Object> row = new LinkedHashMap<>();
				row.put("name", d.name);
				row.put("members", d.members);
				row.put("tradeable", d.geTradeable);
				row.put("reqs", reqs);
				Map<Integer, Object> stats = new TreeMap<>();
				for (int p = 0; p <= 14; p++)
				{
					if (d.params.containsKey(p))
					{
						stats.put(p, d.params.get(p));
					}
				}
				if (d.params.containsKey(299))
				{
					stats.put(299, d.params.get(299));
				}
				row.put("stats", stats);
				out.put(d.id, row);
			}
		}
		Gson gson = new GsonBuilder().create();
		try (Writer w = new OutputStreamWriter(new FileOutputStream(args[1]), StandardCharsets.UTF_8))
		{
			gson.toJson(out, w);
		}
		System.out.println("items with params: " + out.size());
	}
}
