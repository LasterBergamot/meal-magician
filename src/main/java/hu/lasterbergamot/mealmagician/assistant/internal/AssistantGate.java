package hu.lasterbergamot.mealmagician.assistant.internal;

import hu.lasterbergamot.mealmagician.assistant.AssistantDisabledException;
import hu.lasterbergamot.mealmagician.assistant.AssistantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The one place that makes every {@code /assistant/**} route disappear while the assistant is off
 * ({@code mm.ai.enabled=false}, the default).
 *
 * <p>The routes that other modules' pages load on their own (the plan page's "Suggest my week"
 * button, the pick sheet's "ask the wizard" row, the "accepted" notice) answer {@code 204 No
 * Content} with no body, which htmx swaps in as nothing, so the host page shows no button, no hint
 * and no error. Every other route answers 404, exactly like a page that does not exist. Doing it in
 * an interceptor means no handler can forget the check, and it runs before the controller's
 * arguments are resolved, so nothing is read for a request that is going to be refused.
 */
@Configuration(proxyBeanMethods = false)
class AssistantGate implements WebMvcConfigurer {

  /** The routes a host page loads by itself; they answer 204 while the assistant is off. */
  private static final Set<String> QUIET_GET_PATHS =
      Set.of("/assistant/entry", "/assistant/slot", "/assistant/notice");

  private final AssistantService assistant;

  /**
   * Creates the gate.
   *
   * @param assistant says whether the assistant is on
   */
  AssistantGate(AssistantService assistant) {
    this.assistant = assistant;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new Check()).addPathPatterns("/assistant/**");
  }

  /** Answers 204 or 404 and stops the request while the assistant is off. */
  private final class Check implements HandlerInterceptor {

    @Override
    public boolean preHandle(
        HttpServletRequest request, HttpServletResponse response, Object handler) {
      if (assistant.enabled()) {
        return true;
      }
      if ("GET".equals(request.getMethod()) && QUIET_GET_PATHS.contains(request.getRequestURI())) {
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        return false;
      }
      throw new AssistantDisabledException();
    }
  }
}
