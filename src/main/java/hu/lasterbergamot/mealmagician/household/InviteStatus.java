package hu.lasterbergamot.mealmagician.household;

/** Where an invite stands, worked out from its timestamps when the owner looks at the list. */
public enum InviteStatus {
  /** Can still be used. */
  PENDING,
  /** Somebody joined with it; an invite works once. */
  USED,
  /** The owner withdrew it. */
  REVOKED,
  /** Its time ran out. */
  EXPIRED
}
