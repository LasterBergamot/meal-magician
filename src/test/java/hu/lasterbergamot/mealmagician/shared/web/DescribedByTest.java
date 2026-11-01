package hu.lasterbergamot.mealmagician.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DescribedByTest {

  @Test
  void writesNoAttributeWhenThereIsNeitherHintNorError() {
    assertThat(HtmlAttributes.describedBy("name", false, false)).isEmpty();
  }

  @Test
  void pointsAtTheHintWhenThereIsOnlyAHint() {
    assertThat(HtmlAttributes.describedBy("name", true, false))
        .isEqualTo(Map.of("aria-describedby", "name-hint"));
  }

  @Test
  void pointsAtTheErrorWhenThereIsOnlyAnError() {
    assertThat(HtmlAttributes.describedBy("name", false, true))
        .isEqualTo(Map.of("aria-describedby", "name-error"));
  }

  @Test
  void listsTheHintBeforeTheErrorWhenThereAreBoth() {
    assertThat(HtmlAttributes.describedBy("name", true, true))
        .isEqualTo(Map.of("aria-describedby", "name-hint name-error"));
  }

  @Test
  void rendersTheAttributeWithItsLeadingSpace() {
    assertThat(HtmlAttributes.render(HtmlAttributes.describedBy("name", true, true)))
        .isEqualTo(" aria-describedby=\"name-hint name-error\"");
  }
}
