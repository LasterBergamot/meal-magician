package hu.lasterbergamot.mealmagician.sharing;

/** Where a share link stands, worked out from its timestamps when a member looks at the list. */
public enum ShareLinkStatus {
  /** Can still be opened. */
  ACTIVE,
  /** Its time ran out. */
  EXPIRED,
  /** A member withdrew it. */
  REVOKED
}
