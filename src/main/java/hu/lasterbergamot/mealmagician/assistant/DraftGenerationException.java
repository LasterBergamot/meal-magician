package hu.lasterbergamot.mealmagician.assistant;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The model answered, but not with a draft the server could accept, even after one automatic retry:
 * the answer was not the expected JSON, or it kept naming meals that are not in the library.
 * Nothing was stored. A page says "try again" or "rephrase"; left uncaught it answers with HTTP
 * 502.
 *
 * <p>The message names the kind of failure only, never the model's text, which could echo the
 * prompt.
 */
@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class DraftGenerationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param reason a short, prompt-free description of what was wrong with the answer
   */
  public DraftGenerationException(String reason) {
    super("The assistant could not make a usable draft: " + reason);
  }
}
