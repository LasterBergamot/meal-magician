package hu.lasterbergamot.mealmagician.planning;

import java.time.LocalDate;

/**
 * One planned meal as a share-link visitor sees it. Deliberately minimal (PLAN.md D28): no ids, no
 * note, no rating, nothing that names a household or a member.
 *
 * @param date the day
 * @param slotType the part of the day
 * @param position the place among the meals of that day and slot type, counted from 0
 * @param mealName the name of the meal
 */
public record SharedMeal(LocalDate date, SlotType slotType, int position, String mealName) {}
