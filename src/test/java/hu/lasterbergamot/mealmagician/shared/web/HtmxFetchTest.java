package hu.lasterbergamot.mealmagician.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class HtmxFetchTest {

  @Test
  void writesNoAttributesForAPlainLink() {
    assertThat(HtmlAttributes.htmxFetch("/meals?view=week", null)).isEmpty();
  }

  @Test
  void fetchesTheAddressIntoTheTargetAndPushesTheUrl() {
    assertThat(HtmlAttributes.htmxFetch("/meals?view=week", "#content"))
        .isEqualTo(
            Map.of(
                "hx-get", "/meals?view=week",
                "hx-target", "#content",
                "hx-push-url", "true"));
  }

  @Test
  void rendersTheAttributesEscaped() {
    assertThat(HtmlAttributes.render(HtmlAttributes.htmxFetch("/a?x=1&y=\"2\"", "#content")))
        .contains("hx-get=\"/a?x=1&amp;y=&quot;2&quot;\"")
        .contains("hx-push-url=\"true\"");
  }
}
