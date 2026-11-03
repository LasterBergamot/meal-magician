package hu.lasterbergamot.mealmagician.feedback.internal;

import hu.lasterbergamot.mealmagician.feedback.InvalidRatingException;
import hu.lasterbergamot.mealmagician.feedback.RatingService;
import hu.lasterbergamot.mealmagician.household.CurrentHousehold;
import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import hu.lasterbergamot.mealmagician.shared.web.Htmx;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The rating pages (D27): the "How did it go?" card of the plan page and the rating form of a slot.
 * The plan page cannot import {@code feedback} ({@code feedback} depends on {@code planning}), so
 * it loads both over htmx from these routes.
 *
 * <p>Rules shared by all routes:
 *
 * <ul>
 *   <li>Every method takes {@link CurrentHousehold} first, so a person in no household gets 404
 *       before anything else, and a slot of another household is 404 because the services look it
 *       up inside the household.
 *   <li>The form values are parsed leniently ({@link RatingForm}) and the service has the last
 *       word, so a bad value is a 422 with the form shown again and never a 500.
 *   <li>A save or a clear answers with the form itself plus {@code HX-Trigger: ratingSaved}, which
 *       makes the plan page's prompt card reload. There is no redirect: the form swaps itself.
 * </ul>
 */
@Controller
@RequestMapping("/feedback")
class RatingController {

  /** The cookie that hides the prompt card for the rest of the day; it holds an ISO date only. */
  static final String SNOOZE_COOKIE = "mm_rating_snooze";

  /** The htmx event the plan page's prompt container listens for. */
  static final String SAVED_EVENT = "ratingSaved";

  private final RatingService ratings;
  private final RatingPageFactory pages;
  private final Clock clock;

  RatingController(RatingService ratings, RatingPageFactory pages, Clock clock) {
    this.ratings = ratings;
    this.pages = pages;
    this.clock = clock;
  }

  /**
   * Shows the "How did it go?" card, or nothing.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param snoozed the snooze cookie, or {@code null}
   * @param response the response
   * @param model receives the {@link PromptPage}
   * @return the card fragment; an empty 200 when nothing is left to rate in the last 7 days or the
   *     cookie says the person pressed "Not now" today
   */
  @GetMapping("/prompt")
  String prompt(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @CookieValue(name = SNOOZE_COOKIE, required = false) String snoozed,
      HttpServletResponse response,
      Model model) {
    LocalDate today = LocalDate.now(clock);
    response.addHeader("Vary", "HX-Request");
    if (today.toString().equals(snoozed)) {
      return empty(response);
    }
    Optional<PromptPage> card = pages.prompt(user.userId(), household.id(), today);
    if (card.isEmpty()) {
      return empty(response);
    }
    model.addAttribute("prompt", card.get());
    return "feedback/_prompt";
  }

  /**
   * Hides the card until tomorrow by setting the snooze cookie to today's date.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param request the request, to see whether the connection is secure
   * @param response the response, which receives the cookie
   * @return an empty 200, which htmx swaps into the prompt container
   */
  @PostMapping("/prompt/snooze")
  String snooze(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      HttpServletRequest request,
      HttpServletResponse response) {
    ResponseCookie cookie =
        ResponseCookie.from(SNOOZE_COOKIE, LocalDate.now(clock).toString())
            .path("/")
            .sameSite("Lax")
            .secure(request.isSecure())
            .maxAge(Duration.ofDays(1))
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    return empty(response);
  }

  /**
   * Shows the rating form of a slot.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param slotId the slot
   * @param request the current request
   * @param response the response, which receives {@code Vary: HX-Request}
   * @param model receives the {@link RatingPage}
   * @return the form for htmx, otherwise a whole page
   * @throws hu.lasterbergamot.mealmagician.planning.SlotNotFoundException (404) if the slot is not
   *     in this household
   */
  @GetMapping("/slots/{slotId}")
  String form(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long slotId,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    model.addAttribute("page", pages.page(user.userId(), household.id(), slotId));
    return Htmx.view(request, response, "feedback/_rate", "feedback/rate");
  }

  /**
   * Gives or changes the person's rating of a slot.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param slotId the slot
   * @param thumb {@code UP} or {@code DOWN}
   * @param stars {@code 1} to {@code 5}, or empty
   * @param comment the comment, or empty
   * @param request the current request
   * @param response the response
   * @param model receives the {@link RatingPage}
   * @return the form with a thanks line and {@code HX-Trigger: ratingSaved}; 422 with the form, the
   *     typed values and a message when the rating is refused (no thumb, stars out of range,
   *     comment too long, or a slot after today)
   * @throws hu.lasterbergamot.mealmagician.planning.SlotNotFoundException (404) if the slot is not
   *     in this household; the service looks it up before it judges the values
   */
  @PostMapping("/slots/{slotId}")
  String give(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long slotId,
      @RequestParam(required = false) String thumb,
      @RequestParam(required = false) String stars,
      @RequestParam(required = false) String comment,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    RatingForm typed = RatingForm.parse(thumb, stars, comment);
    try {
      ratings.give(user.userId(), household.id(), slotId, typed.toInput());
      response.setHeader("HX-Trigger", SAVED_EVENT);
      model.addAttribute("page", pages.saved(user.userId(), household.id(), slotId));
    } catch (InvalidRatingException refused) {
      response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
      model.addAttribute(
          "page",
          pages.rejected(
              user.userId(),
              household.id(),
              slotId,
              typed,
              RatingPage.errorKeyOf(refused.problem())));
    }
    return Htmx.view(request, response, "feedback/_rate", "feedback/rate");
  }

  /**
   * Takes the person's rating of a slot back.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param slotId the slot
   * @param request the current request
   * @param response the response
   * @param model receives the {@link RatingPage}
   * @return the empty form and {@code HX-Trigger: ratingSaved}, so the meal may be asked about
   *     again
   * @throws hu.lasterbergamot.mealmagician.planning.SlotNotFoundException (404) if the slot is not
   *     in this household; it is read first because {@code clear} alone would answer "nothing to
   *     clear" for a foreign id
   */
  @PostMapping("/slots/{slotId}/clear")
  String clear(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long slotId,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    pages.page(user.userId(), household.id(), slotId);
    ratings.clear(user.userId(), household.id(), slotId);
    response.setHeader("HX-Trigger", SAVED_EVENT);
    model.addAttribute("page", pages.page(user.userId(), household.id(), slotId));
    return Htmx.view(request, response, "feedback/_rate", "feedback/rate");
  }

  /** Completes the response with an empty HTML body, so the container's {@code :empty} applies. */
  private static String empty(HttpServletResponse response) {
    response.setContentType("text/html;charset=UTF-8");
    return null;
  }
}
