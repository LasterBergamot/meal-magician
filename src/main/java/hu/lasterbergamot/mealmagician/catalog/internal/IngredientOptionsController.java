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
 * The list behind the ingredient picker ({@code ui/_combobox}): {@code GET /meals/ingredients?q=}
 * returns the matching ingredients as {@code <li role="option">} rows for htmx to put into the
 * picker's listbox.
 *
 * <p>It answers with a fragment only, never a page: nothing links to it and a person who opens it
 * by hand just sees the rows. Like the rest of the library it is open to every member and scoped to
 * the {@link CurrentHousehold}, so another household's ingredients are never listed and a person in
 * no household gets a 404.
 */
@Controller
@RequestMapping("/meals/ingredients")
class IngredientOptionsController {

  /** The most rows the list shows; a phone screen holds fewer, and typing narrows it anyway. */
  static final int LIMIT = 20;

  private static final String FRAGMENT = "meal/_ingredient_options";

  private final MealService meals;

  IngredientOptionsController(MealService meals) {
    this.meals = meals;
  }

  /**
   * Lists the ingredients that match the typed text, plus an "Add" row for a new name.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param q the text typed so far; blank lists the household's most used ingredients first
   * @param model receives the {@link IngredientOptions}
   * @return the options fragment
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
        "options",
        IngredientOptions.of(
            q, meals.ingredientSuggestions(user.userId(), household.id(), q, LIMIT)));
    return FRAGMENT;
  }
}
