package hu.lasterbergamot.mealmagician.planning.internal;

import java.util.List;

/**
 * The meals of one day of a template, under the day's heading. A day template has one of these; a
 * week template has one per weekday that holds a meal.
 *
 * @param label the heading: "The day" for a day template, or the weekday name for a week template
 * @param lines the meals of that day, in plan order (slot type, then place)
 */
public record TemplateDayLines(String label, List<TemplateLine> lines) {}
