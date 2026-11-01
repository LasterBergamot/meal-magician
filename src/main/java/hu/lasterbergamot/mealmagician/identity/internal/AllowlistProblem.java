package hu.lasterbergamot.mealmagician.identity.internal;

/** The reasons the admin page refuses to change the allowlist, each with the text to show. */
enum AllowlistProblem {
  /** The text is not shaped like an e-mail address. */
  INVALID_EMAIL("admin.allowlist.error.invalid"),
  /** The address can already sign in, either from the table or from {@code MM_ADMIN_EMAILS}. */
  ALREADY_ALLOWED("admin.allowlist.error.exists"),
  /** The address is a bootstrap admin, which only a redeploy with new configuration can remove. */
  BOOTSTRAP_ADMIN("admin.allowlist.error.bootstrap");

  private final String messageKey;

  AllowlistProblem(String messageKey) {
    this.messageKey = messageKey;
  }

  /**
   * The message to show next to the form.
   *
   * @return a key of the message bundles
   */
  String messageKey() {
    return messageKey;
  }
}
