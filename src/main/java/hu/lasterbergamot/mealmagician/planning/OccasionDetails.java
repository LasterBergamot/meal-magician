package hu.lasterbergamot.mealmagician.planning;

import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import java.time.LocalDate;

/**
 * What an occasion form sends. Any field may be {@code null}: {@link OccasionService} checks them
 * and throws {@link InvalidPlanException} with the matching {@link PlanProblem}.
 *
 * @param name the typed name; trimmed, 1 to 100 characters
 * @param date the day of the occasion
 * @param type the kind of occasion
 * @param notes the typed notes; blank means none, at most 2000 characters
 */
public record OccasionDetails(String name, LocalDate date, OccasionType type, String notes) {}
