package hu.lasterbergamot.mealmagician.catalog.internal;

import hu.lasterbergamot.mealmagician.catalog.InvalidMealException;
import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import hu.lasterbergamot.mealmagician.household.CurrentHousehold;
import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import hu.lasterbergamot.mealmagician.shared.web.Htmx;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The create and edit form of a meal: name, notes, recipe link, tags and occasion types.
 *
 * <p>A rejected form answers {@code 422} (or {@code 409} when someone else saved the meal while the
 * form was open) with the form fragment for htmx, or the whole page otherwise, and keeps what was
 * typed. A saved form ends with {@link Htmx#redirect}. Like the rest of the library it is open to
 * every member and scoped to the {@link CurrentHousehold}.
 */
@Controller
@RequestMapping("/meals")
class MealFormController {

  private static final String FRAGMENT = "meal/_form";
  private static final String PAGE = "meal/form";

  private final MealService meals;

  MealFormController(MealService meals) {
    this.meals = meals;
  }

  /**
   * Shows the empty form for a new meal.
   *
   * @param household the household of the request (the page is household data's gateway, so the
   *     person must have one)
   * @param request the current request
   * @param response the response, which receives {@code Vary: HX-Request}
   * @param model receives the form model
   * @return the form page, or only the form for htmx
   */
  @GetMapping("/new")
  String blank(
      CurrentHousehold household,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    model.addAttribute("page", MealFormPage.of(MealForm.blank()));
    return Htmx.view(request, response, FRAGMENT, PAGE);
  }

  /**
   * Adds a meal with all its details.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param name the typed name
   * @param notes the typed notes
   * @param recipeUrl the typed recipe link
   * @param tags the typed tags, separated by commas
   * @param occasion the ticked occasion types
   * @param request the current request
   * @param response the response
   * @param model receives the form model when a field is rejected
   * @return a redirect to the list, or the form with {@code 422} and the message under the field
   */
  @PostMapping
  String create(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @RequestParam(defaultValue = "") String name,
      @RequestParam(defaultValue = "") String notes,
      @RequestParam(defaultValue = "") String recipeUrl,
      @RequestParam(defaultValue = "") String tags,
      @RequestParam(required = false) List<OccasionType> occasion,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    MealForm form = new MealForm(null, 0, name, notes, recipeUrl, tags, types(occasion));
    try {
      meals.create(user.userId(), household.id(), form.details());
      return Htmx.redirect(request, response, "/meals");
    } catch (InvalidMealException refused) {
      response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
      model.addAttribute("page", MealFormPage.rejected(form, refused));
      return Htmx.view(request, response, FRAGMENT, PAGE);
    }
  }

  /**
   * Shows the form filled with a stored meal.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param mealId the meal to edit
   * @param request the current request
   * @param response the response, which receives {@code Vary: HX-Request}
   * @param model receives the form model
   * @return the form page, or only the form for htmx
   * @throws hu.lasterbergamot.mealmagician.catalog.MealNotFoundException (404) if the meal does not
   *     exist in this household
   */
  @GetMapping("/{mealId}/edit")
  String edit(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long mealId,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    MealForm form = MealForm.of(meals.find(user.userId(), household.id(), mealId));
    model.addAttribute("page", MealFormPage.of(form));
    return Htmx.view(request, response, FRAGMENT, PAGE);
  }

  /**
   * Saves the changes to a meal.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param mealId the meal to change
   * @param version the version the form was opened with
   * @param name the typed name
   * @param notes the typed notes
   * @param recipeUrl the typed recipe link
   * @param tags the typed tags, separated by commas
   * @param occasion the ticked occasion types
   * @param request the current request
   * @param response the response
   * @param model receives the form model when the change is rejected
   * @return a redirect to the meal's page, or the form with {@code 422} (a field is wrong) or
   *     {@code 409} (someone else saved first)
   * @throws hu.lasterbergamot.mealmagician.catalog.MealNotFoundException (404) if the meal does not
   *     exist in this household, whatever was typed
   */
  @PostMapping("/{mealId}")
  String update(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long mealId,
      @RequestParam(defaultValue = "0") long version,
      @RequestParam(defaultValue = "") String name,
      @RequestParam(defaultValue = "") String notes,
      @RequestParam(defaultValue = "") String recipeUrl,
      @RequestParam(defaultValue = "") String tags,
      @RequestParam(required = false) List<OccasionType> occasion,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    MealForm form = new MealForm(mealId, version, name, notes, recipeUrl, tags, types(occasion));
    try {
      meals.edit(user.userId(), household.id(), mealId, form.details(), version);
      return Htmx.redirect(request, response, "/meals/" + mealId);
    } catch (InvalidMealException refused) {
      response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
      model.addAttribute("page", MealFormPage.rejected(form, refused));
    } catch (OptimisticLockingFailureException stale) {
      response.setStatus(HttpServletResponse.SC_CONFLICT);
      model.addAttribute("page", MealFormPage.stale(form));
    }
    return Htmx.view(request, response, FRAGMENT, PAGE);
  }

  private static Set<OccasionType> types(List<OccasionType> ticked) {
    EnumSet<OccasionType> types = EnumSet.noneOf(OccasionType.class);
    if (ticked != null) {
      types.addAll(ticked);
    }
    return types;
  }
}
