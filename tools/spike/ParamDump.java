import java.io.File;
import java.util.Map;
import java.util.TreeMap;
import net.runelite.cache.ItemManager;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.fs.Store;

/** Spike: print item params for gear whose wear requirements are known, to find the requirement params. */
public class ParamDump
{
	public static void main(String[] args) throws Exception
	{
		try (Store store = new Store(new File(args[0])))
		{
			store.load();
			ItemManager items = new ItemManager(store);
			items.load();
			// Known: whip 70 Att; AGS 75 Att; tbow 75 Rng; bandos chestplate 65 Def; ancestral hat 75 Mag + 65 Def;
			// void top 42 Att/Str/Def/HP/Rng/Mag + 22 Pray; serpentine helm 75 Def; toxic blowpipe 75 Rng;
			// rune platebody 40 Def; bronze sword (no requirement); dragon claws 60 Att; elysian 75 Def + 75 Pray.
			int[] ids = {13237, 1091, 1123, 1426, 4097, 4587, 11840, 2503, 12596, 1704, 22325};
			for (int id : ids)
			{
				ItemDefinition d = items.getItem(id);
				Map<Integer, Object> params = d.params == null ? new TreeMap<>() : new TreeMap<>(d.params);
				System.out.println(id + " " + d.name + " " + params);
			}
		}
	}
}
