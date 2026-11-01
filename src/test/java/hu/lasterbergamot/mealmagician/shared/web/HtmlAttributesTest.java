package hu.lasterbergamot.mealmagician.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class HtmlAttributesTest {

  @Test
  void rendersNothingForNoAttributes() {
    assertThat(HtmlAttributes.render(Map.of())).isEmpty();
  }

  @Test
  void rendersEachAttributeWithALeadingSpace() {
    assertThat(HtmlAttributes.render(Map.of("hx-post", "/meals"))).isEqualTo(" hx-post=\"/meals\"");
  }

  @Test
  void escapesQuotesAndAmpersandsInValues() {
    assertThat(HtmlAttributes.render(Map.of("hx-confirm", "Remove \"Anna\" & go?")))
        .isEqualTo(" hx-confirm=\"Remove &quot;Anna&quot; &amp; go?\"");
  }

  @Test
  void refusesANameThatIsNotAPlainAttributeName() {
    assertThatThrownBy(() -> HtmlAttributes.render(Map.of("onclick\" x=\"", "go()")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("onclick");
  }

  @Test
  void refusesANameThatStartsWithADigit() {
    assertThatThrownBy(() -> HtmlAttributes.render(Map.of("1st", "x")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
