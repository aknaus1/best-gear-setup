package com.bestgearsetup.data;

import static org.junit.Assert.assertEquals;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import org.junit.Test;

public class MonsterCopyTest
{
	@Test
	public void aCopyKeepsEveryStoredField() throws Exception
	{
		// Boss phases, raid scaling and special attacks all work on a copy; a field it drops (as poisons once was)
		// silently changes those targets, such as a trip's cure.
		Monster monster = new Monster();
		int n = 1;
		for (Field field : Monster.class.getDeclaredFields())
		{
			int modifiers = field.getModifiers();
			if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers))
				continue;
			field.setAccessible(true);
			Class<?> type = field.getType();
			if (type == int.class || type == Integer.class)
				field.set(monster, n++);
			else if (type == boolean.class)
				field.set(monster, true);
			else if (type == String.class)
				field.set(monster, "value " + n++);
			else if (type == java.util.List.class)
				field.set(monster, Collections.singletonList(new Monster.Attribute()));
			else if (type == java.util.Map.class)
				field.set(monster, Collections.singletonMap("slash", new Monster.MonsterAttack()));
			else
				throw new AssertionError("Give the test a value for " + field.getName());
		}
		assertEquals(monster, monster.copy());
	}
}
