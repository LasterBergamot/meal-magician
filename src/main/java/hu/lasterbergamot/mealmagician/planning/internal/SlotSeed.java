package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.planning.SlotType;
import java.time.LocalDate;

/**
 * One slot waiting to be written by {@link SlotWriter}: the target date and slot type, the meal and
 * the note. The meal is already known to belong to the household, because it came from a slot or a
 * template item of that household.
 *
 * @param date the day to plan the meal on
 * @param slotType the slot type
 * @param mealId the library meal
 * @param note the note to copy, or {@code null} for none
 */
public record SlotSeed(LocalDate date, SlotType slotType, long mealId, String note) {}
