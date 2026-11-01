package hu.lasterbergamot.mealmagician.household;

/** Why a join link was refused. Each value has its own message so the person knows what to do. */
public enum InviteProblem {
  /** No invite has this code (mistyped or never existed). */
  UNKNOWN,
  /** The invite is for another Google account than the one signed in. */
  WRONG_EMAIL,
  /** The owner revoked it. */
  REVOKED,
  /** It was already used. */
  USED,
  /** Its time ran out. */
  EXPIRED
}
