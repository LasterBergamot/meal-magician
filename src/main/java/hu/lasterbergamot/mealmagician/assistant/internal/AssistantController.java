package hu.lasterbergamot.mealmagician.assistant.internal;

import hu.lasterbergamot.mealmagician.assistant.AcceptResult;
import hu.lasterbergamot.mealmagician.assistant.AssistantService;
import hu.lasterbergamot.mealmagician.assistant.DraftClosedException;
import hu.lasterbergamot.mealmagician.assistant.DraftGenerationException;
import hu.lasterbergamot.mealmagician.assistant.DraftView;
import hu.lasterbergamot.mealmagician.assistant.ModelUnavailableException;
import hu.lasterbergamot.mealmagician.assistant.SlotSuggestion;
import hu.lasterbergamot.mealmagician.assistant.SpendCapExceededException;
import hu.lasterbergamot.mealmagician.household.CurrentHousehold;
import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import hu.lasterbergamot.mealmagician.planning.CopyMode;
import hu.lasterbergamot.mealmagician.planning.InvalidPlanException;
import hu.lasterbergamot.mealmagician.planning.PlanConflictException;
import hu.lasterbergamot.mealmagician.planning.PlanningService;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import hu.lasterbergamot.mealmagician.shared.i18n.SupportedLocales;
import hu.lasterbergamot.mealmagician.shared.web.Htmx;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * The wizard's routes (#160): "Suggest my week" with its draft, follow-ups, accept and discard, and
 * the one-slot suggestions of the meal-pick sheet. Every route returns an htmx fragment.
 *
 * <p>This module cannot be imported by {@code planning}, so the plan page loads the wizard's
 * buttons from here with {@code hx-trigger="load"} ({@code /assistant/entry}, {@code
 * /assistant/slot}, {@code /assistant/notice}). {@link AssistantGate} turns every route into a 204
 * or a 404 while the assistant is off, so no handler checks.
 *
 * <p>Rules shared by all routes:
 *
 * <ul>
 *   <li>Every method takes {@link CurrentHousehold}, so a person in no household gets 404 first;
 *       any member may use every route.
 *   <li>A draft is read first (household-scoped), so another household's draft id is 404 before
 *       anything else is looked at.
 *   <li>What a member typed is at most 1 000 characters; longer is a 422 on the field and no model
 *       call is made. The quick-request chips are turned into text here ({@link QuickHints}).
 *   <li>The model's failures become a translated alert in the sheet and keep what the member typed:
 *       the spend cap is 429, an unavailable model 503, a draft that was already accepted 409.
 *   <li>The model's text is untrusted. It only reaches the page through JTE escaping.
 * </ul>
 */
@Controller
@RequestMapping("/assistant")
class AssistantController {

  /** The session attribute that carries the result of an accept to the refreshed plan page. */
  static final String ACCEPTED_ATTRIBUTE = AssistantController.class.getName() + ".accepted";

  private final AssistantService assistant;
  private final PlanningService planning;
  private final DraftPageFactory pages;
  private final MessageSource messages;
  private final Clock clock;

  AssistantController(
      AssistantService assistant,
      PlanningService planning,
      DraftPageFactory pages,
      MessageSource messages,
      Clock clock) {
    this.assistant = assistant;
    this.planning = planning;
    this.pages = pages;
    this.messages = messages;
    this.clock = clock;
  }

  /**
   * The "Suggest my week" button of the plan page, plus a hint line when the week has nothing
   * planned. The plan page loads this when it opens and when the stepper changes the week.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param week the week as {@code YYYY-Www}; missing or not a real week means the current week
   * @param model receives the week code and whether the week is empty
   * @return the button fragment ({@code 204} with no body while the assistant is off)
   */
  @GetMapping("/entry")
  String entry(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @RequestParam(required = false) String week,
      Model model) {
    LocalDate monday = WeekCodes.mondayOf(week, clock);
    model.addAttribute("weekCode", WeekCodes.codeOf(monday));
    model.addAttribute(
        "emptyWeek", planning.week(user.userId(), household.id(), monday).slots().isEmpty());
    return "assistant/_entry";
  }

  /**
   * The first step of the sheet: the request form for a week.
   *
   * @param household the household of the request (a person in none gets 404 first)
   * @param week the week as {@code YYYY-Www}; missing or not a real week means the current week
   * @param model receives the {@link WeekSheetPage}
   * @return the sheet's content
   */
  @GetMapping("/week")
  String weekSheet(
      CurrentHousehold household, @RequestParam(required = false) String week, Model model) {
    LocalDate monday = WeekCodes.mondayOf(week, clock);
    model.addAttribute("page", WeekSheetPage.blank(WeekCodes.codeOf(monday), range(monday)));
    return "assistant/_week_sheet";
  }

  /**
   * Makes a draft from a request and shows it. The model call takes seconds; the form is showing
   * its waiting state meanwhile (CSS on htmx's {@code htmx-request} class).
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param week the week as {@code YYYY-Www}; missing or not a real week means the current week
   * @param request what the member typed, at most 1 000 characters
   * @param hint the ticked quick-request chips; unknown values are ignored
   * @param response the response, which gets the error status when the model cannot help
   * @param model receives the page
   * @return the draft; or the request form again with a 422 under the field (text too long, no
   *     model call), a 429 spend-cap alert or a 503 "unavailable" alert, each keeping the text
   */
  @PostMapping("/drafts")
  String create(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @RequestParam(required = false) String week,
      @RequestParam(required = false) String request,
      @RequestParam(required = false) String[] hint,
      HttpServletResponse response,
      Model model) {
    LocalDate monday = WeekCodes.mondayOf(week, clock);
    String weekCode = WeekCodes.codeOf(monday);
    Locale locale = locale();
    String typed = request == null ? "" : request;
    List<String> hints = QuickHints.known(hint);
    WeekSheetPage form = new WeekSheetPage(weekCode, range(monday), typed, hints, null, null);
    if (typed.length() > QuickHints.MAX_TYPED_LENGTH) {
      response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
      model.addAttribute(
          "page",
          new WeekSheetPage(weekCode, form.range(), typed, hints, "assistant.error.invalid", null));
      return "assistant/_week_sheet";
    }
    try {
      DraftView draft =
          assistant.draftWeek(
              user.userId(), household.id(), locale, monday, expanded(typed, hints, locale));
      model.addAttribute("page", pages.page(draft, locale, -1, "", List.of(), null, null));
      return "assistant/_draft";
    } catch (SpendCapExceededException cap) {
      response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      model.addAttribute("page", withNotice(form, ErrorNotice.cap()));
    } catch (ModelUnavailableException | DraftGenerationException unavailable) {
      response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
      model.addAttribute("page", withNotice(form, ErrorNotice.unavailable()));
    }
    return "assistant/_week_sheet";
  }

  /**
   * Shows a draft again (for example after a reload of the sheet).
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param id the draft
   * @param model receives the page
   * @return the draft
   */
  @GetMapping("/drafts/{id}")
  String show(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long id,
      Model model) {
    DraftView draft = assistant.draft(user.userId(), household.id(), id);
    model.addAttribute("page", pages.page(draft, locale(), -1, "", List.of(), null, null));
    return "assistant/_draft";
  }

  /**
   * Changes the draft after a follow-up message.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param id the draft
   * @param message the follow-up, at most 1 000 characters
   * @param hint the ticked quick-request chips; unknown values are ignored
   * @param request the current request
   * @param response the response
   * @param model receives the page
   * @return the changed draft; the same draft with a 422 under the field (text too long, no model
   *     call), a 429 or 503 alert (the text is kept), or the conflict banner (409) when the draft
   *     was accepted meanwhile
   */
  @PostMapping("/drafts/{id}/refine")
  String refine(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long id,
      @RequestParam(required = false) String message,
      @RequestParam(required = false) String[] hint,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    DraftView current = assistant.draft(user.userId(), household.id(), id);
    Locale locale = locale();
    String typed = message == null ? "" : message;
    List<String> hints = QuickHints.known(hint);
    if (typed.length() > QuickHints.MAX_TYPED_LENGTH) {
      response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
      model.addAttribute(
          "page", pages.page(current, locale, -1, typed, hints, "assistant.error.invalid", null));
      return "assistant/_draft";
    }
    return guarded(
        user,
        household,
        response,
        model,
        () -> {
          try {
            DraftView changed =
                assistant.refine(
                    user.userId(), household.id(), locale, id, expanded(typed, hints, locale));
            model.addAttribute("page", pages.page(changed, locale, -1, "", List.of(), null, null));
          } catch (SpendCapExceededException cap) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            model.addAttribute(
                "page", pages.page(current, locale, -1, typed, hints, null, ErrorNotice.cap()));
          } catch (ModelUnavailableException | DraftGenerationException unavailable) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            model.addAttribute(
                "page",
                pages.page(current, locale, -1, typed, hints, null, ErrorNotice.unavailable()));
          }
          return "assistant/_draft";
        });
  }

  /**
   * Takes one item out of the draft.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param id the draft
   * @param itemId the item
   * @param response the response
   * @param model receives the page
   * @return the draft without the item; the conflict banner (409) if the draft was accepted
   */
  @PostMapping("/drafts/{id}/items/{itemId}/remove")
  String remove(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long id,
      @PathVariable long itemId,
      HttpServletResponse response,
      Model model) {
    return guarded(
        user,
        household,
        response,
        model,
        () -> {
          DraftView draft = assistant.removeItem(user.userId(), household.id(), id, itemId);
          model.addAttribute("page", pages.page(draft, locale(), -1, "", List.of(), null, null));
          return "assistant/_draft";
        });
  }

  /**
   * Adds an idea to the meal library. The card becomes a normal row with a note.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param id the draft
   * @param itemId the idea item
   * @param response the response
   * @param model receives the page
   * @return the draft with the idea as a library meal; the conflict banner (409) if the draft was
   *     accepted
   */
  @PostMapping("/drafts/{id}/items/{itemId}/idea")
  String addIdea(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long id,
      @PathVariable long itemId,
      HttpServletResponse response,
      Model model) {
    return guarded(
        user,
        household,
        response,
        model,
        () -> {
          DraftView draft = assistant.addIdeaToLibrary(user.userId(), household.id(), id, itemId);
          model.addAttribute(
              "page", pages.page(draft, locale(), itemId, "", List.of(), null, null));
          return "assistant/_draft";
        });
  }

  /**
   * Writes the draft's library meals into the plan and sends the browser to the week. The result
   * (slots written, ideas left out) rides in the session to the {@code /assistant/notice} fragment,
   * which the refreshed plan page loads.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param id the draft
   * @param mode {@code MERGE} (fill empty slots, the default) or {@code REPLACE} (replace the week)
   * @param request the current request
   * @param response the response
   * @param session the session that carries the result to the plan page
   * @param model receives the page when the plan refuses the draft
   * @return a redirect to the week ({@code HX-Refresh} too when the browser is already there); 422
   *     with an alert if the plan refuses the draft; the conflict banner (409) if the draft was
   *     accepted meanwhile or a replace lost a race
   * @throws ResponseStatusException (400) if {@code mode} is not {@code MERGE} or {@code REPLACE}
   */
  @PostMapping("/drafts/{id}/accept")
  String accept(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long id,
      @RequestParam(required = false) String mode,
      HttpServletRequest request,
      HttpServletResponse response,
      HttpSession session,
      Model model) {
    DraftView draft = assistant.draft(user.userId(), household.id(), id);
    CopyMode copyMode = copyMode(mode);
    return guarded(
        user,
        household,
        response,
        model,
        () -> {
          try {
            AcceptResult result = assistant.accept(user.userId(), household.id(), id, copyMode);
            session.setAttribute(
                ACCEPTED_ATTRIBUTE, result.slotsWritten() + ":" + result.droppedIdeas());
            String url = "/plan?week=" + WeekCodes.codeOf(draft.weekStart());
            Htmx.redirect(request, response, url);
            if (isSamePage(request.getHeader("HX-Current-URL"), url)) {
              response.setHeader("HX-Refresh", "true");
            }
            return null;
          } catch (InvalidPlanException refused) {
            response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
            model.addAttribute(
                "page",
                pages.page(draft, locale(), -1, "", List.of(), null, ErrorNotice.acceptRefused()));
            return "assistant/_draft";
          }
        });
  }

  /**
   * Deletes the draft and closes the sheet. Nothing was written to the plan, so there is nothing to
   * confirm.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param id the draft
   * @return an empty answer with {@code HX-Trigger: assistant-close}, which {@code app.js} turns
   *     into closing the sheet
   */
  @PostMapping("/drafts/{id}/discard")
  ResponseEntity<Void> discard(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long id) {
    assistant.discard(user.userId(), household.id(), id);
    return ResponseEntity.ok().header("HX-Trigger", "assistant-close").build();
  }

  /**
   * The wizard's row in the meal-pick sheet: "Ask the wizard for ideas". Loaded when the pick sheet
   * opens.
   *
   * @param household the household of the request
   * @param date the slot's day, {@code YYYY-MM-DD}
   * @param slotType the slot type
   * @param model receives the page
   * @return the ask row ({@code 204} with no body while the assistant is off)
   * @throws ResponseStatusException (400) if {@code date} or {@code slotType} is missing or invalid
   */
  @GetMapping("/slot")
  String slotAsk(
      CurrentHousehold household,
      @RequestParam(required = false) String date,
      @RequestParam(required = false) String slotType,
      Model model) {
    LocalDate day = parseDate(date);
    SlotType type = parseSlotType(slotType);
    model.addAttribute("page", SlotSuggestionPage.ask(day.toString(), type.name()));
    return "assistant/_slot";
  }

  /**
   * Asks the model for up to three library meals for one slot.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param date the slot's day, {@code YYYY-MM-DD}
   * @param slotType the slot type
   * @param response the response, which gets the error status when the model cannot help
   * @param model receives the page
   * @return the suggestions, each with the pick sheet's own "plan this" form; or the ask row again
   *     with a 429 or 503 alert
   * @throws ResponseStatusException (400) if {@code date} or {@code slotType} is missing or invalid
   */
  @PostMapping("/slot")
  String slotSuggest(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @RequestParam(required = false) String date,
      @RequestParam(required = false) String slotType,
      HttpServletResponse response,
      Model model) {
    LocalDate day = parseDate(date);
    SlotType type = parseSlotType(slotType);
    try {
      List<SlotSuggestion> found =
          assistant.suggestSlot(user.userId(), household.id(), locale(), day, type);
      model.addAttribute(
          "page",
          SlotSuggestionPage.answer(
              day.toString(),
              type.name(),
              found.stream()
                  .map(s -> new SlotSuggestionPage.Row(s.mealId(), s.mealName(), s.reason()))
                  .toList()));
    } catch (SpendCapExceededException cap) {
      response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      model.addAttribute(
          "page", SlotSuggestionPage.failed(day.toString(), type.name(), ErrorNotice.cap()));
    } catch (ModelUnavailableException | DraftGenerationException unavailable) {
      response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
      model.addAttribute(
          "page",
          SlotSuggestionPage.failed(day.toString(), type.name(), ErrorNotice.unavailable()));
    }
    return "assistant/_slot";
  }

  /**
   * The success note the plan page shows after a draft was accepted. It is read once: the next
   * request finds nothing.
   *
   * @param session the session that carries the result of the accept
   * @param response the response, which gets 204 when there is nothing to tell
   * @param model receives the two counts
   * @return the note, or {@code 204} with no body when there is nothing to tell
   */
  @GetMapping("/notice")
  String notice(HttpSession session, HttpServletResponse response, Model model) {
    Object value = session.getAttribute(ACCEPTED_ATTRIBUTE);
    if (value == null) {
      response.setStatus(HttpServletResponse.SC_NO_CONTENT);
      return null;
    }
    session.removeAttribute(ACCEPTED_ATTRIBUTE);
    String[] counts = value.toString().split(":");
    model.addAttribute("slots", counts[0]);
    model.addAttribute("dropped", counts[1]);
    return "assistant/_accepted";
  }

  // ---- helpers ----

  /**
   * Runs an action on an open draft and turns "the draft was already accepted" and "the plan
   * changed under a replace" into the plan page's conflict banner (409), the existing pattern for a
   * stale edit: it lands in {@code #plan-conflict}, which closes the sheet.
   */
  private String guarded(
      SignedInUser user,
      CurrentHousehold household,
      HttpServletResponse response,
      Model model,
      Supplier<String> action) {
    try {
      return action.get();
    } catch (DraftClosedException closed) {
      return conflict(user, household, closed.draftId(), response, model);
    } catch (PlanConflictException lostRace) {
      return conflictBanner(response, model, null);
    }
  }

  private String conflict(
      SignedInUser user,
      CurrentHousehold household,
      long draftId,
      HttpServletResponse response,
      Model model) {
    DraftView draft = assistant.draft(user.userId(), household.id(), draftId);
    return conflictBanner(response, model, "/plan?week=" + WeekCodes.codeOf(draft.weekStart()));
  }

  private static String conflictBanner(
      HttpServletResponse response, Model model, String reloadHref) {
    response.setStatus(HttpServletResponse.SC_CONFLICT);
    response.setHeader("HX-Retarget", "#plan-conflict");
    response.setHeader("HX-Reswap", "innerHTML");
    model.addAttribute("reloadHref", reloadHref == null ? "/plan" : reloadHref);
    return "plan/_conflict";
  }

  private Locale locale() {
    return SupportedLocales.normalize(LocaleContextHolder.getLocale());
  }

  private String range(LocalDate monday) {
    return DraftPageFactory.range(monday, locale());
  }

  private String expanded(String typed, List<String> hints, Locale locale) {
    return QuickHints.expand(typed, hints, key -> QuickHints.phrase(messages, locale, key));
  }

  private static WeekSheetPage withNotice(WeekSheetPage form, ErrorNotice notice) {
    return new WeekSheetPage(
        form.weekCode(), form.range(), form.request(), form.hints(), null, notice);
  }

  private static CopyMode copyMode(String mode) {
    if (mode == null || "MERGE".equals(mode)) {
      return CopyMode.MERGE;
    }
    if ("REPLACE".equals(mode)) {
      return CopyMode.REPLACE;
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mode");
  }

  private static LocalDate parseDate(String date) {
    try {
      return LocalDate.parse(date);
    } catch (DateTimeParseException | NullPointerException bad) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date");
    }
  }

  private static SlotType parseSlotType(String slotType) {
    try {
      return SlotType.valueOf(slotType);
    } catch (IllegalArgumentException | NullPointerException bad) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "slotType");
    }
  }

  /**
   * Tells whether the browser's current address is the target's path and query. Going to the same
   * address would not reload the page, so the accept adds {@code HX-Refresh} then (the same rule
   * the planner uses for its own redirects).
   */
  static boolean isSamePage(String currentUrl, String target) {
    if (currentUrl == null) {
      return false;
    }
    try {
      URI current = URI.create(currentUrl);
      String here =
          current.getRawQuery() == null
              ? current.getRawPath()
              : current.getRawPath() + "?" + current.getRawQuery();
      return here.equals(target);
    } catch (IllegalArgumentException notAnAddress) {
      return false;
    }
  }
}
