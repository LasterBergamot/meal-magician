package hu.lasterbergamot.mealmagician.shared.web;

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
}
