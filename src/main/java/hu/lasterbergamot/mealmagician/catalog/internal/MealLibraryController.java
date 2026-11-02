package hu.lasterbergamot.mealmagician.catalog.internal;

import hu.lasterbergamot.mealmagician.catalog.InvalidMealException;
import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.household.CurrentHousehold;
import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import hu.lasterbergamot.mealmagician.shared.web.Htmx;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The meal library pages: the searchable list, one meal's detail page, the phone's quick add, and
 * archive and restore. The create and edit form is {@link MealFormController}.
 *
 * <p>Every method takes a {@link CurrentHousehold}, the verified household of the request, and
 * passes its id on, so a meal of another household is a 404 and a person in no household gets a 404
 * too (PLAN.md D8). All of it is open to every member: nothing in the library is owner-only.
 *
 * <p>The list URL serves a whole page and, for htmx, just the results fragment, so typing in the
 * search box swaps only the list. Posts end with {@link Htmx#redirect}; a rejected quick add
 * answers {@code 422} with its own form.
 */
@Controller
@RequestMapping("/meals")
class MealLibraryController {

  private static final String LIST_PATH = "/meals";

  private final MealService meals;
  private final MealsPageFactory pages;

  MealLibraryController(MealService meals, MealsPageFactory pages) {
    this.meals = meals;
    this.pages = pages;
  }

  /**
   * Shows the household's meals, filtered and sorted by the query parameters.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param q part of a meal name
   * @param tag a tag the meals must have
   * @param archived whether archived meals are shown too
   * @param sort {@code name} for alphabetical; anything else is "recent"
   * @param request the current request
   * @param response the response, which receives {@code Vary: HX-Request}
   * @param model receives the {@link MealsPage}
   * @return the whole page, or only the results for htmx
   */
  @GetMapping
  String list(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String tag,
      @RequestParam(defaultValue = "false") boolean archived,
      @RequestParam(required = false) String sort,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    MealFilter filter = MealFilter.of(q, tag, archived, sort);
    model.addAttribute("page", pages.page(user.userId(), household.id(), filter, ""));
    model.addAttribute("quickSheet", QuickAdd.empty(QuickAdd.SHEET));
    model.addAttribute("quickBar", QuickAdd.empty(QuickAdd.BAR));
    return Htmx.view(request, response, "meal/_results", "meal/list");
  }

  /**
   * Shows one meal with its notes, recipe link, tags and occasion types.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param mealId the meal
   * @param model receives the meal
   * @return the detail page
   * @throws hu.lasterbergamot.mealmagician.catalog.MealNotFoundException (404) if the meal does not
   *     exist in this household
   */
  @GetMapping("/{mealId}")
  String detail(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long mealId,
      Model model) {
    model.addAttribute("meal", meals.find(user.userId(), household.id(), mealId));
    return "meal/detail";
  }

  /**
   * Adds a meal from its name alone and goes back to the list.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param name the typed name
   * @param form which quick-add form posted ({@link QuickAdd#SHEET} or {@link QuickAdd#BAR})
   * @param request the current request
   * @param response the response
   * @param model receives the form model when the name is rejected
   * @return a redirect to the list; for a rejected name, with {@code 422}, the quick-add form for
   *     htmx, or the full create form with the name filled in when there is no htmx to swap the
   *     small form
   */
  @PostMapping("/quick")
  String quickAdd(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @RequestParam(defaultValue = "") String name,
      @RequestParam(name = "form", required = false) String form,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    try {
      meals.quickAdd(user.userId(), household.id(), name);
      return Htmx.redirect(request, response, LIST_PATH);
    } catch (InvalidMealException refused) {
      response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
      response.addHeader("Vary", Htmx.REQUEST_HEADER);
      if (Htmx.isRequest(request)) {
        MealFormPage rejected = MealFormPage.rejected(MealForm.named(name), refused);
        model.addAttribute(
            "quick", new QuickAdd(QuickAdd.variantOf(form), name, rejected.error("name")));
        return "meal/_quick";
      }
      model.addAttribute("page", MealFormPage.rejected(MealForm.named(name), refused));
      return "meal/form";
    }
  }

  /**
   * Takes a meal out of the default list. Its history stays; it can be restored.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param mealId the meal
   * @param request the current request
   * @param response the response
   * @return a redirect to the list
   * @throws hu.lasterbergamot.mealmagician.catalog.MealNotFoundException (404) if the meal does not
   *     exist in this household
   */
  @PostMapping("/{mealId}/archive")
  String archive(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long mealId,
      HttpServletRequest request,
      HttpServletResponse response) {
    meals.archive(user.userId(), household.id(), mealId);
    return Htmx.redirect(request, response, LIST_PATH);
  }

  /**
   * Puts an archived meal back into the default list.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param mealId the meal
   * @param request the current request
   * @param response the response
   * @return a redirect to the meal's detail page
   * @throws hu.lasterbergamot.mealmagician.catalog.MealNotFoundException (404) if the meal does not
   *     exist in this household
   */
  @PostMapping("/{mealId}/restore")
  String restore(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long mealId,
      HttpServletRequest request,
      HttpServletResponse response) {
    meals.restore(user.userId(), household.id(), mealId);
    return Htmx.redirect(request, response, LIST_PATH + "/" + mealId);
  }
}
