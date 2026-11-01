package hu.lasterbergamot.mealmagician.shared.web;

/**
 * What the page header shows about the household the person is working in.
 *
 * @param name the household's display name, or {@code null} for a signed-in person who has no
 *     household yet (the header then invites them to create one)
 */
public record HouseholdNav(String name) {}
