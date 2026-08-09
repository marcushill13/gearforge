package com.gearforge;

import com.gearforge.data.Monster;
import com.gearforge.data.MonsterRepository;
import com.google.gson.Gson;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The target picker is a few thousand rows long, and the wiki data lists a row per NPC id, so the
 * same monster arrives many times over with nothing to tell the copies apart.
 */
public class MonsterRepositoryTest
{
	private final MonsterRepository repository = new MonsterRepository(new Gson());

	@Test
	public void noTwoEntriesShareADisplayName()
	{
		Set<String> seen = new HashSet<>();
		Set<String> duplicates = new HashSet<>();

		for (Monster monster : repository.all())
		{
			if (!seen.add(monster.displayName()))
			{
				duplicates.add(monster.displayName());
			}
		}

		assertEquals("Duplicate rows in the target picker: " + duplicates, 0, duplicates.size());
	}

	@Test
	public void formsAreOnlyShownWhenTheyDistinguishSomething()
	{
		for (Monster monster : repository.all())
		{
			if (monster.getVersion().isEmpty())
			{
				continue;
			}

			// A form survived, so there must be at least one sibling it is being told apart from.
			long siblings = repository.all().stream()
				.filter(other -> other.getName().equals(monster.getName()))
				.count();

			assertTrue(monster.displayName() + " keeps a form but has no sibling to differ from",
				siblings > 1);
		}
	}

	@Test
	public void theListIsStillUsefullyPopulated()
	{
		List<Monster> all = repository.all();

		assertFalse(all.isEmpty());
		assertFalse(repository.bosses().isEmpty());

		// Collapsing must not have eaten the things people actually search for.
		assertTrue(repository.search("Ammonite").stream()
			.anyMatch(monster -> monster.getName().equalsIgnoreCase("Ammonite Crab")));
		assertTrue(repository.search("Zulrah").size() > 1);
	}

	@Test
	public void entriesAreSortedSoScrollingIsPredictable()
	{
		List<Monster> all = repository.all();

		for (int i = 1; i < all.size(); i++)
		{
			assertTrue("Out of order at " + all.get(i).displayName(),
				all.get(i - 1).displayName().compareToIgnoreCase(all.get(i).displayName()) <= 0);
		}
	}

	/**
	 * The complaint that prompted this: twenty armoured zombies, differing only in which room they
	 * stand in. An ordinary monster gets one row, whatever the source data lists.
	 */
	@Test
	public void anOrdinaryMonsterGetsExactlyOneRow()
	{
		assertEquals(1, countNamed("Armoured zombie"));
		assertEquals(1, countNamed("Ankou"));
	}

	/**
	 * Bosses keep their forms, because a Zulrah colour really does defend differently and picking the
	 * wrong one gives you the wrong answer.
	 */
	@Test
	public void bossFormsSurvive()
	{
		assertTrue(countNamed("Zulrah") > 1);
	}

	@Test
	public void noOrdinaryMonsterNameAppearsTwice()
	{
		Set<String> seen = new HashSet<>();

		for (Monster monster : repository.all())
		{
			if (!monster.isBoss())
			{
				assertTrue("Two rows for " + monster.getName(), seen.add(monster.getName()));
			}
		}
	}

	private long countNamed(String name)
	{
		return repository.all().stream()
			.filter(monster -> monster.getName().equalsIgnoreCase(name))
			.count();
	}
}
