package hu.lasterbergamot.mealmagician.sharing;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * A member addressed a share link that does not exist <em>or</em> belongs to another household. It
 * is one exception for both cases, so another household's link ids cannot be probed. It answers
 * with HTTP 404.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ShareLinkNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param linkId the id that was asked for
   */
  public ShareLinkNotFoundException(long linkId) {
    super("No share link " + linkId + " in this household");
  }
}
