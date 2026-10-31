package hu.lasterbergamot.mealmagician.identity.internal;

/** Why an address is on the allowlist (PLAN.md D6, D9). */
public enum AllowedEmailSource {
  /** Added by an instance admin on the admin page. */
  ADMIN,
  /** Added automatically when a household owner invited the address. */
  INVITE
}
