package hu.lasterbergamot.mealmagician.planning;

import java.time.LocalDate;

/**
 * A meal was put into a slot: added to a day, or moved to another day or slot type. Other modules
 * listen for it instead of calling the planner, so the planner does not need to know who cares.
 * Nothing listens yet.
 *
 * <p>Published inside the same transaction as the change, so a listener that must see only
 * committed data should use {@code @ApplicationModuleListener}, which runs after the commit.
 *
 * <p>Reordering, editing a note and removing a slot publish nothing: the meal's place in the plan
 * did not change in a way those listeners need, and a reorder would otherwise publish one event per
 * sibling.
 *
 * @param householdId the household the plan belongs to
 * @param slotId the slot that now holds the meal
 * @param date the day it is planned for
 * @param slotType the slot type it is planned in
 * @param mealId the meal planned there
 */
public record SlotPlanned(
    long householdId, long slotId, LocalDate date, SlotType slotType, long mealId) {}
