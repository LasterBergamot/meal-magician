package hu.lasterbergamot.mealmagician.shared.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.web.util.HtmlUtils;

/**
 * Turns a map of extra attributes (such as {@code hx-post} and {@code hx-confirm}) into the text
 * that goes inside a start tag. The UI fragments that take attributes, such as {@code ui/_button},
 * call this and write the result with {@code $unsafe{...}}.
 *
 * <p>JTE cannot loop over a map inside a tag, so the template cannot do it itself. Writing the
 * result unescaped is safe only because this class escapes every value and refuses any name that is
 * not a plain attribute name: a template or a caller cannot use the map to inject markup.
 */
public final class HtmlAttributes {

  /** A name made of letters, digits and {@code - _ : .}, starting with a letter. */
  private static final Pattern NAME = Pattern.compile("[a-zA-Z][a-zA-Z0-9_:.-]*");

  private HtmlAttributes() {}

  /**
   * Renders the attributes, each with a leading space, ready to follow a tag name.
   *
   * @param attributes attribute name to value; pass {@code Map.of()} for none. Values are escaped
   *     for an attribute in double quotes; a {@code null} value is not allowed
   * @return for example {@code hx-post="/meals"}; the empty string for an empty map
   * @throws IllegalArgumentException if a name is not a plain attribute name, for example one
   *     containing a quote or a space
   */
  public static String render(Map<String, String> attributes) {
    StringBuilder rendered = new StringBuilder();
    attributes.forEach((name, value) -> {
      if (!NAME.matcher(name).matches()) {
        throw new IllegalArgumentException("Not an attribute name: " + name);
      }
      rendered
          .append(' ')
          .append(name)
          .append("=\"")
          .append(HtmlUtils.htmlEscape(value))
          .append('"');
    });
    return rendered.toString();
  }

  /**
   * Builds the {@code aria-describedby} attribute that links a form control to its hint and to its
   * error text, so a screen reader reads them after the label. The ids follow the pattern of the
   * {@code ui/_field} and {@code ui/_select} fragments: {@code <fieldId>-hint} and {@code
   * <fieldId>-error}. The hint id comes first, then the error id.
   *
   * <p>It returns a map rather than the text so a template can pass it straight to {@link
   * #render(Map)}; when the control has neither a hint nor an error, the map is empty and no
   * attribute is written at all (an empty {@code aria-describedby} would point at nothing).
   *
   * @param fieldId the id of the input or select the attribute is written on
   * @param hasHint whether the fragment renders a hint element with id {@code <fieldId>-hint}
   * @param hasError whether the fragment renders an error element with id {@code <fieldId>-error}
   * @return {@code aria-describedby} mapped to the rendered ids separated by a space, for example
   *     {@code "name-hint name-error"}; an empty map when neither flag is set
   */
  public static Map<String, String> describedBy(String fieldId, boolean hasHint, boolean hasError) {
    List<String> ids = new ArrayList<>();
    if (hasHint) {
      ids.add(fieldId + "-hint");
    }
    if (hasError) {
      ids.add(fieldId + "-error");
    }
    return ids.isEmpty() ? Map.of() : Map.of("aria-describedby", String.join(" ", ids));
  }

  /**
   * Builds the htmx attributes that make a link fetch its address into a target instead of
   * navigating: {@code hx-get} with the address, {@code hx-target} with the selector, and {@code
   * hx-push-url} so the browser's address bar and Back button follow the swap.
   *
   * <p>A null target means "no htmx": the link stays a plain link, which is what the style guide
   * uses so a click does not swap the page it is on. The check lives here, not in the template,
   * because the template only passes attributes through.
   *
   * @param url the address the link fetches, which is also its {@code href}
   * @param target a CSS selector such as {@code "#content"}, or null for a plain link
   * @return the three htmx attributes; an empty map when {@code target} is null
   */
  public static Map<String, String> htmxFetch(String url, String target) {
    if (target == null) {
      return Map.of();
    }
    return Map.of("hx-get", url, "hx-target", target, "hx-push-url", "true");
  }
}
