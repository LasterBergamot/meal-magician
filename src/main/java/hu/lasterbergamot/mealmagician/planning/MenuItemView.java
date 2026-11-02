package hu.lasterbergamot.mealmagician.planning;

/**
 * One meal on an occasion's or an occasion template's menu. It carries the meal's id and not its
 * name; pages get names from the catalog.
 *
 * @param id the item's id, used in the remove and move actions
 * @param position the place on the menu, counted from 0
 * @param mealId the library meal; it may be archived
 */
public record MenuItemView(long id, int position, long mealId) {}
