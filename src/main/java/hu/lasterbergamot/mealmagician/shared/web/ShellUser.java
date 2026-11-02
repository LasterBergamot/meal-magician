package hu.lasterbergamot.mealmagician.shared.web;

import java.util.Locale;

/**
 * What the app shell (top bar, sidebar, avatar menu) shows about the signed-in person: their name,
 * from which the avatar's initials are made.
 *
 * <p>The name is what Google reported (or the e-mail address when Google gave no name). Templates
 * never see the Google {@code sub} or the e-mail address here, only what is needed on screen.
 *
 * @param displayName the name to show; {@code null} is stored as an empty string
 */
public record ShellUser(String displayName) {

  /** Keeps {@link #initials()} and the templates away from {@code null}. */
  public ShellUser {
    displayName = displayName == null ? "" : displayName.strip();
  }

  /**
   * The one or two letters on the avatar: the first letter of the first two words of the name,
   * upper-cased. A name with no letters (or none at all) gives a question mark, so the disc is
   * never empty.
   *
   * @return the initials, for example {@code TA} for "Török András" or {@code A} for "Anna"
   */
  public String initials() {
    StringBuilder initials = new StringBuilder();
    for (String word : displayName.split("\\s+")) {
      if (word.isEmpty() || initials.length() == 2) {
        continue;
      }
      initials.append(word.substring(0, word.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT));
    }
    return initials.isEmpty() ? "?" : initials.toString();
  }
}
