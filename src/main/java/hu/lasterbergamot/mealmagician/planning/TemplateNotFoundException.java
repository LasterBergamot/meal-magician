package hu.lasterbergamot.mealmagician.planning;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The plan template or occasion template does not exist <em>or</em> it belongs to another
 * household. One exception for both, so a stranger cannot tell which template ids exist (PLAN.md
 * D8). It answers with HTTP 404.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class TemplateNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param templateId the id that was asked for; kept out of the error page but useful in logs
   */
  public TemplateNotFoundException(long templateId) {
    super("No template " + templateId + " in this household");
  }
}
