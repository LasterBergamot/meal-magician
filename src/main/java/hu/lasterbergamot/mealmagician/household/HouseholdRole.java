package hu.lasterbergamot.mealmagician.household;

/**
 * What a person may do inside one household (PLAN.md D8). The same person can be an owner of one
 * household and a plain member of another.
 */
public enum HouseholdRole {
  /** Manages the household: renames it, invites and removes members, hands over ownership. */
  OWNER,
  /** Plans meals and rates them, but cannot manage the household itself. */
  MEMBER
}
