package hu.lasterbergamot.mealmagician.assistant.internal;

import hu.lasterbergamot.mealmagician.assistant.AssistantService;
import hu.lasterbergamot.mealmagician.assistant.DraftGenerationException;
import hu.lasterbergamot.mealmagician.assistant.MenuSuggestions;
import hu.lasterbergamot.mealmagician.assistant.ModelUnavailableException;
import hu.lasterbergamot.mealmagician.assistant.SpendCapExceededException;
import hu.lasterbergamot.mealmagician.household.CurrentHousehold;
import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import hu.lasterbergamot.mealmagician.planning.OccasionService;
import hu.lasterbergamot.mealmagician.shared.i18n.SupportedLocales;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The wizard's routes on an occasion's page (#161, Appendix D.5): the "Suggest a menu" loader and
 * button, and "Add to library" for an idea. The page's own menu routes (add, remove, move) stay in
 * the planner; a library suggestion is added through them, so this class never edits a menu.
 *
 * <p>Rules shared by the routes: the occasion is read first, scoped to the household, so another
 * household's occasion is a 404 before anything else; {@link AssistantGate} answers the loader with
 * 204 while the assistant is off. Named apart from the planner's {@code OccasionMenuController} so
 * the two beans do not clash.
 */
@Controller
@RequestMapping("/assistant/occasions/{occasionId}")
class AssistantOccasionController {

  /** The alert for an unavailable model, with the "Try again" button inside the suggestion form. */
  private static final ErrorNotice UNAVAILABLE = ErrorNotice.unavailable();

  private final AssistantService assistant;
  private final OccasionService occasions;

  /**
   * Creates the controller.
   *
   * @param assistant the public face of the assistant
   * @param occasions the planner's occasion service, which checks the occasion belongs to the
   *     household
   */
  AssistantOccasionController(AssistantService assistant, OccasionService occasions) {
    this.assistant = assistant;
    this.occasions = occasions;
  }

  /**
   * The loader the occasion page fetches on opening: the "Suggest a menu" button, with no
   * suggestions yet.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param occasionId the occasion
   * @param model receives the occasion id and the empty state
   * @return the suggestion section ({@code 204} while the assistant is off)
   */
  @GetMapping("/entry")
  String entry(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long occasionId,
      Model model) {
    occasions.find(user.userId(), household.id(), occasionId);
    model.addAttribute("occasionId", occasionId);
    model.addAttribute("suggestions", null);
    model.addAttribute("notice", null);
    return "assistant/_menu_suggestions";
  }

  /**
   * Asks the wizard for library meals and ideas for this occasion's menu.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param occasionId the occasion
   * @param response the response, which gets the error status when the model cannot help
   * @param model receives the suggestions or the alert
   * @return the suggestion section
   */
  @PostMapping("/menu")
  String suggest(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long occasionId,
      HttpServletResponse response,
      Model model) {
    MenuSuggestions found = null;
    ErrorNotice notice = null;
    try {
      found = assistant.suggestMenu(user.userId(), household.id(), locale(), occasionId);
    } catch (SpendCapExceededException cap) {
      response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      notice = ErrorNotice.cap();
    } catch (ModelUnavailableException | DraftGenerationException unavailable) {
      response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
      notice = UNAVAILABLE;
    }
    model.addAttribute("occasionId", occasionId);
    model.addAttribute("suggestions", found);
    model.addAttribute("notice", notice);
    return "assistant/_menu_suggestions";
  }

  /**
   * Adds one idea to the meal library. The meal is not put on the menu: the member does that from
   * the library, through the planner's normal menu route.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param occasionId the occasion the idea came from
   * @param name the idea's name, as the suggestion showed it
   * @param tags the idea's tags, one parameter each
   * @param ingredients the idea's ingredients, one parameter each
   * @param model receives the name the meal was created with
   * @return the "added" line that replaces the idea card
   */
  @PostMapping("/ideas")
  String addIdea(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long occasionId,
      @RequestParam(required = false) String name,
      @RequestParam(name = "tag", required = false) List<String> tags,
      @RequestParam(name = "ingredient", required = false) List<String> ingredients,
      Model model) {
    String added =
        assistant.addMenuIdea(
            user.userId(),
            household.id(),
            occasionId,
            Optional.ofNullable(name).orElse(""),
            Optional.ofNullable(tags).orElse(List.of()),
            Optional.ofNullable(ingredients).orElse(List.of()));
    model.addAttribute("name", added);
    return "assistant/_menu_idea_added";
  }

  private Locale locale() {
    return SupportedLocales.normalize(LocaleContextHolder.getLocale());
  }
}
