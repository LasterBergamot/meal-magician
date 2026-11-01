package hu.lasterbergamot.mealmagician.household;

/**
 * One line of "my households": enough to list a household and switch to it.
 *
 * @param id the household id
 * @param name the household's display name
 * @param role the asking person's role in it
 */
public record HouseholdSummary(long id, String name, HouseholdRole role) {}
