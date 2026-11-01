package hu.lasterbergamot.mealmagician.identity.internal;

/** Where an entry of the admin page's list comes from, which decides whether it can be removed. */
enum AllowlistOrigin {
  /** Named in {@code MM_ADMIN_EMAILS}: configuration, so the page cannot remove it. */
  BOOTSTRAP("admin.allowlist.origin.bootstrap"),
  /** Added on the admin page. */
  ADMIN("admin.allowlist.origin.admin"),
  /** Added when a household owner invited the address. */
  INVITE("admin.allowlist.origin.invite");

  private final String messageKey;

  AllowlistOrigin(String messageKey) {
    this.messageKey = messageKey;
  }

  /**
   * The message that names this origin on the page.
   *
   * @return a key of the message bundles
   */
  String messageKey() {
    return messageKey;
  }
}
