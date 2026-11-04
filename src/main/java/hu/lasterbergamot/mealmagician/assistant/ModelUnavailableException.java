package hu.lasterbergamot.mealmagician.assistant;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The model could not give an answer: the call failed, took longer than {@code mm.ai.timeout}, or
 * returned something unusable (an embedding of the wrong size). Nothing is added to the household's
 * usage when it is thrown. A page catches it to say "try again later"; left uncaught it answers
 * with HTTP 503.
 *
 * <p>The message and the exception chain deliberately hold <strong>no</strong> text from the
 * request or from the provider's error: those can echo the prompt, and prompts are never logged.
 * Only the kind of failure is named.
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class ModelUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param reason a short, prompt-free description of what went wrong (for example "timed out")
   */
  public ModelUnavailableException(String reason) {
    super("The AI model is unavailable: " + reason);
  }
}
