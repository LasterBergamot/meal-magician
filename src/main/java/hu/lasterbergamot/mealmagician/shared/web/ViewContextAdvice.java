package hu.lasterbergamot.mealmagician.shared.web;

import hu.lasterbergamot.mealmagician.shared.i18n.Messages;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Puts a {@link ViewContext} named {@code ctx} into the model of every controller, so no controller
 * has to remember to pass the translation helper to its templates.
 */
@ControllerAdvice
public class ViewContextAdvice {

  private final Messages messages;
  private final ObjectProvider<CsrfMetaSource> csrfSource;

  /**
   * Creates the advice.
   *
   * @param messages the translation helper
   * @param csrfSource the CSRF token source; absent until Spring Security is configured
   */
  public ViewContextAdvice(Messages messages, ObjectProvider<CsrfMetaSource> csrfSource) {
    this.messages = messages;
    this.csrfSource = csrfSource;
  }

  /**
   * Builds the context for one request.
   *
   * @param request the current request
   * @return the context, with {@code csrf == null} when there is no token source or no token
   */
  @ModelAttribute("ctx")
  public ViewContext viewContext(HttpServletRequest request) {
    CsrfMetaSource source = csrfSource.getIfAvailable();
    return new ViewContext(messages, source == null ? null : source.from(request));
  }
}
