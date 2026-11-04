package hu.lasterbergamot.mealmagician.planning;

import java.time.LocalDate;

/**
 * One meal of an accepted assistant draft, ready to be written into the plan by {@link
 * PlanningService#applyDraft}. It names a library meal and a place in the week and nothing else:
 * the planner decides the position inside the day and leaves the note empty.
 *
 * @param date the day to plan the meal on; it must lie inside the week being written
 * @param slotType the slot type
 * @param mealId the library meal; it must belong to the household and must not be archived
 */
public record DraftSlot(LocalDate date, SlotType slotType, long mealId) {}
