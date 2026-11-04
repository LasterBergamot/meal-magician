package hu.lasterbergamot.mealmagician.assistant.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.springframework.context.MessageSource;

/**
 * The four quick-request chips of the week sheet ("less meat", "quick meals", ...) and how they
 * become text for the model.
 *
 * <p>The browser sends only the chip's key ({@code lessMeat}); the controller looks the phrase up
 * in the member's language ({@code assistant.hint.<key>}) and appends it to the typed request. An
 * unknown key is ignored, so a hand-edited form cannot make the server look up an arbitrary message
 * or put arbitrary text next to the member's own. Every phrase is at most 50 characters in both
 * bundles (a test checks it), so the typed request (at most 1 000) plus four phrases and their
 * separators stays within the 1 200 characters the service accepts.
 */
public final class QuickHints {

  /** The chip keys, in the order the sheet shows them. */
  public static final List<String> KEYS = List.of("lessMeat", "quick", "vegStew", "leftovers");

  /** The longest request a member may type, before the chips add their phrases. */
  static final int MAX_TYPED_LENGTH = 1000;

  private static final String SEPARATOR = ". ";

  private QuickHints() {}

  /**
   * Keeps the known chip keys of a form post.
   *
   * @param posted the posted {@code hint} values, or {@code null} when none were sent
   * @return the known keys, each once, in the sheet's order; unknown values are dropped
   */
  static List<String> known(String[] posted) {
    if (posted == null) {
      return List.of();
    }
    List<String> sent = List.of(posted);
    return KEYS.stream().filter(sent::contains).toList();
  }

  /**
   * Builds the text the model gets: the typed request followed by the phrase of each chip.
   *
   * @param typed what the member typed, at most {@value #MAX_TYPED_LENGTH} characters; may be empty
   * @param hints known chip keys, see {@link #known}
   * @param phrase turns a chip key into its phrase in the member's language
   * @return the typed text and the phrases joined with a full stop; empty when both are empty
   */
  static String expand(String typed, List<String> hints, Function<String, String> phrase) {
    List<String> parts = new ArrayList<>();
    String trimmed = typed.strip();
    if (!trimmed.isEmpty()) {
      parts.add(trimmed);
    }
    hints.forEach(hint -> parts.add(phrase.apply(hint)));
    return String.join(SEPARATOR, parts);
  }

  /**
   * Looks a chip's phrase up in the member's language.
   *
   * @param messages the message bundles
   * @param locale the member's language
   * @param key a known chip key
   * @return the phrase, for example {@code Less meat}
   */
  static String phrase(MessageSource messages, Locale locale, String key) {
    return messages.getMessage("assistant.hint." + key, null, locale);
  }
}
