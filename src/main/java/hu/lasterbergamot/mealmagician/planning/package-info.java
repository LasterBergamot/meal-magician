/**
 * What the household eats and when: meal slots per date, day and week templates, and dated
 * occasions such as birthdays. This package is the module's public API; everything else goes in
 * {@code internal}.
 *
 * <p>Today the API is the weekly plan: {@link PlanningService} reads a week, adds a meal to a slot,
 * moves, reorders, removes and annotates slots, and lists recently planned meal ids for the
 * library. It publishes {@link SlotPlanned}. Other modules use the root package only.
 */
@ApplicationModule(displayName = "Planning")
package hu.lasterbergamot.mealmagician.planning;

import org.springframework.modulith.ApplicationModule;
