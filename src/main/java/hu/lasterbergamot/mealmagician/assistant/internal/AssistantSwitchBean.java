package hu.lasterbergamot.mealmagician.assistant.internal;

import hu.lasterbergamot.mealmagician.assistant.AssistantService;
import hu.lasterbergamot.mealmagician.shared.web.AssistantSwitchSource;
import org.springframework.stereotype.Component;

/**
 * Publishes the assistant's on/off setting to the app shell (see {@link AssistantSwitchSource}), so
 * the navigation shows the Wizard's destination only while the assistant is on.
 */
@Component
class AssistantSwitchBean implements AssistantSwitchSource {

  private final AssistantService assistant;

  /**
   * Creates the bean.
   *
   * @param assistant says whether the assistant is on
   */
  AssistantSwitchBean(AssistantService assistant) {
    this.assistant = assistant;
  }

  @Override
  public boolean enabled() {
    return assistant.enabled();
  }
}
