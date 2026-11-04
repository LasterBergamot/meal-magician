package hu.lasterbergamot.mealmagician.assistant.internal;

import hu.lasterbergamot.mealmagician.assistant.AssistantService;
import hu.lasterbergamot.mealmagician.assistant.ChatTurn;
import hu.lasterbergamot.mealmagician.assistant.DraftGenerationException;
import hu.lasterbergamot.mealmagician.assistant.InvalidRequestException;
import hu.lasterbergamot.mealmagician.assistant.ModelUnavailableException;
import hu.lasterbergamot.mealmagician.assistant.SpendCapExceededException;
import hu.lasterbergamot.mealmagician.household.CurrentHousehold;
import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import hu.lasterbergamot.mealmagician.shared.i18n.SupportedLocales;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Locale;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The Wizard's chat page (#161, Appendix D.6): the page itself, one message at a time, and "New
 * conversation". The conversation is kept in the signed-in session ({@link ChatSession}) and never
 * in a table.
 *
 * <p>Rules shared by the routes:
 *
 * <ul>
 *   <li>Every route takes {@link CurrentHousehold}, so a person in no household gets 404 first. Any
 *       member may chat; the answers come only from the household's own data through the read-only
 *       tools.
 *   <li>The turns are stored <em>after</em> a successful answer only. A refused or failed message
 *       leaves the conversation as it was, and the member's text stays in the field to send again.
 *   <li>The page is 404 while the assistant is off ({@link AssistantGate}).
 * </ul>
 */
@Controller
@RequestMapping("/assistant")
class ChatController {

  /** The alert for an unavailable model; no retry button, because the text stays in the field. */
  private static final ErrorNotice UNAVAILABLE =
      new ErrorNotice("danger", "assistant.error.unavailable", false);

  private final AssistantService assistant;

  /**
   * Creates the controller.
   *
   * @param assistant the public face of the assistant
   */
  ChatController(AssistantService assistant) {
    this.assistant = assistant;
  }

  /**
   * The chat page with the conversation so far.
   *
   * @param household the household of the request (a person in none gets 404)
   * @param session the signed-in session, which holds the conversation
   * @param model receives the bubbles
   * @return the page
   */
  @GetMapping
  String page(CurrentHousehold household, HttpSession session, Model model) {
    model.addAttribute("bubbles", ChatBubble.of(ChatSession.read(session)));
    return "assistant/chat";
  }

  /**
   * Answers one message and shows the new bubbles. The reply is the htmx fragment that is appended
   * to the thread; the composer is replaced too, so the field is cleared on success and keeps the
   * text on an error.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param message what the member typed, at most 1 000 characters
   * @param session the signed-in session, which receives both turns after a good answer
   * @param response the response, which gets the error status when the message is refused
   * @param model receives what the fragment draws
   * @return the reply fragment; {@code 204} with no body for a blank message
   */
  @PostMapping("/chat")
  String send(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @RequestParam(required = false) String message,
      HttpSession session,
      HttpServletResponse response,
      Model model) {
    String typed = message == null ? "" : message;
    if (typed.isBlank()) {
      response.setStatus(HttpServletResponse.SC_NO_CONTENT);
      return null;
    }
    List<ChatBubble> bubbles = List.of();
    ErrorNotice notice = null;
    String errorKey = null;
    boolean emptyGone = false;
    try {
      String answer =
          assistant.chat(user.userId(), household.id(), locale(), ChatSession.read(session), typed);
      ChatSession.append(session, ChatTurn.user(typed), ChatTurn.assistant(answer));
      bubbles = ChatBubble.of(List.of(ChatTurn.user(typed), ChatTurn.assistant(answer)));
      emptyGone = true;
      typed = "";
    } catch (InvalidRequestException tooLong) {
      response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
      errorKey = "assistant.error.invalid";
    } catch (SpendCapExceededException cap) {
      response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      notice = ErrorNotice.cap();
    } catch (ModelUnavailableException | DraftGenerationException unavailable) {
      response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
      notice = UNAVAILABLE;
    }
    model.addAttribute("bubbles", bubbles);
    model.addAttribute("notice", notice);
    model.addAttribute("errorKey", errorKey);
    model.addAttribute("emptyGone", emptyGone);
    model.addAttribute("typed", typed);
    return "assistant/_chat_reply";
  }

  /**
   * "New conversation": forgets the turns of this session. The answer is the empty state, which
   * replaces the thread's content; there is no confirmation, because nothing is stored anyway.
   *
   * @param session the signed-in session
   * @param model receives the empty state's flag
   * @return the empty state, as an out-of-band swap
   */
  @PostMapping("/chat/reset")
  String reset(HttpSession session, Model model) {
    ChatSession.clear(session);
    model.addAttribute("shown", true);
    return "assistant/_chat_empty";
  }

  private Locale locale() {
    return SupportedLocales.normalize(LocaleContextHolder.getLocale());
  }
}
