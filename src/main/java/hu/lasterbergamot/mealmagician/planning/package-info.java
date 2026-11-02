/**
 * What the household eats and when: meal slots per date, day and week templates, and dated
 * occasions such as birthdays. This package is the module's public API; everything else goes in
 * {@code internal}.
 *
 * <p>The API is the weekly plan: {@link PlanningService} reads a week, adds a meal to a slot,
 * moves, reorders, removes and annotates slots, reads a single slot ({@code slot}), creates a meal
 * and plans it in one transaction ({@code quickAddAndPlan}), copies a day or a week ({@code
 * copyDay}, {@code copyWeek}) and lists recently planned meal ids for the library. Reuse and
 * occasions (Phase 4) sit beside it: {@link PlanTemplateService} saves, lists, renames, deletes and
 * applies day and week templates, {@link OccasionService} manages dated occasions and their menus,
 * and {@link OccasionTemplateService} saves an occasion's menu and starts new occasions from it.
 * Only {@link SlotPlanned} is published, once per new slot. Other modules use the root package
 * only.
 */
@ApplicationModule(displayName = "Planning")
package hu.lasterbergamot.mealmagician.planning;

import org.springframework.modulith.ApplicationModule;
