package hu.lasterbergamot.mealmagician.assistant;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The assistant is switched off ({@code mm.ai.enabled=false}, the default) and something asked it
 * to work. Left uncaught it answers with HTTP 404, so a route of the assistant looks the same as a
 * page that does not exist; the UI normally never gets this far because its entry points render
 * nothing while the assistant is off.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class AssistantDisabledException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** Creates the exception. */
  public AssistantDisabledException() {
    super("The assistant is switched off");
  }
}
