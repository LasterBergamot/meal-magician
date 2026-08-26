/**
 * Meal slots. The UI focuses on lunch for now, but the full set is defined here
 * (and mirrored by the `meal_slot` DB enum) so breakfast/dinner/snack can be
 * enabled later without a migration.
 */
export const MEAL_SLOTS = ["breakfast", "lunch", "dinner", "snack"] as const;
export type MealSlot = (typeof MEAL_SLOTS)[number];
export const DEFAULT_MEAL_SLOT: MealSlot = "lunch";

/** Household member roles. The creator is an admin; admins can promote other members. */
export const MEMBER_ROLES = ["admin", "member"] as const;
export type MemberRole = (typeof MEMBER_ROLES)[number];

/** ISO day-of-week: Monday = 1 ... Sunday = 7. Used by week templates and the planner grid. */
export const DAYS_OF_WEEK = [1, 2, 3, 4, 5, 6, 7] as const;
export type DayOfWeek = (typeof DAYS_OF_WEEK)[number];
