package com.gearforge;

import com.gearforge.dps.SpecialAttack;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * A bank holds a dagger(p++), never a plain dragon dagger. Checking ownership against the one
 * canonical id told those players they owned no special attack weapon at all — which is what the
 * empty "nothing you own is worth speccing with" line was actually reporting.
 */
public class OwnedSpecVariantTest
{
	@Test
	public void everyPoisonedDragonDaggerIsStillADragonDagger()
	{
		List<Integer> daggers = idsNamed("DRAGON_DAGGER");

		assertFalse("No dragon dagger ids found", daggers.isEmpty());

		for (int id : daggers)
		{
			assertEquals("id " + id + " should be recognised as the dragon dagger spec",
				SpecialAttack.DRAGON_DAGGER, SpecialAttack.forItem(id));
		}
	}

	@Test
	public void thePoisonedAbyssalDaggersAreRecognisedToo()
	{
		for (int id : idsNamed("ABYSSAL_DAGGER"))
		{
			assertEquals(SpecialAttack.ABYSSAL_DAGGER, SpecialAttack.forItem(id));
		}
	}

	private static List<Integer> idsNamed(String prefix)
	{
		List<Integer> ids = new ArrayList<>();

		for (Field field : ItemID.class.getFields())
		{
			if (!Modifier.isStatic(field.getModifiers()) || field.getType() != int.class
				|| !field.getName().startsWith(prefix))
			{
				continue;
			}

			try
			{
				ids.add(field.getInt(null));
			}
			catch (IllegalAccessException ignored)
			{
				// Not readable, so not something the plugin could see either.
			}
		}

		return ids;
	}
}
