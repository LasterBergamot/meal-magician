package hu.lasterbergamot.mealmagician.catalog.internal;

import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.household.CurrentHousehold;
import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The list behind the Tags field of the meal form: {@code GET /meals/tags?q=} returns the
 * household's matching tags as {@code <li role="option">} rows for htmx to put into the field's
 * listbox ({@code ui/_suggest_field}).
 *
 * <p>It answers with a fragment only, never a page, and it only suggests: the field stays free
 * text. Like {@link IngredientOptionsController} it is open to every member and scoped to the
 * {@link CurrentHousehold}, so another household's tags are never listed and a person in no
 * household gets a 404.
 */
@Controller
@RequestMapping("/meals/tags")
class TagOptionsController {

  private static final String FRAGMENT = "meal/_tag_options";

  private final MealService meals;

  TagOptionsController(MealService meals) {
    this.meals = meals;
  }

  /**
   * Lists the household's tags that match the typed text, most used first.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param q the tag text typed so far (the segment between two commas); blank lists the most used
   *     tags
   * @param model receives the {@link TagOptions}
   * @return the options fragment; its body is empty when no tag matches
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException (404) if the person
   *     is not a member of the household
   */
  @GetMapping
  String options(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @RequestParam(defaultValue = "") String q,
      Model model) {
    model.addAttribute(
        "options", new TagOptions(meals.tagSuggestions(user.userId(), household.id(), q)));
    return FRAGMENT;
  }
}
