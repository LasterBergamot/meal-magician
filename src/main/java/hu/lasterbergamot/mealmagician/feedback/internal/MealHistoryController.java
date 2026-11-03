package hu.lasterbergamot.mealmagician.feedback.internal;

import hu.lasterbergamot.mealmagician.feedback.MealRatings;
import hu.lasterbergamot.mealmagician.feedback.RatingService;
import hu.lasterbergamot.mealmagician.household.CurrentHousehold;
import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import hu.lasterbergamot.mealmagician.shared.i18n.Messages;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * The "How it went" block of a meal's page (#132, D27): the thumbs counts, the average stars and
 * the newest ratings of one meal. The meal page is in the {@code catalog} module, which {@code
 * feedback} depends on and not the other way round, so the meal page loads this block over htmx
 * from here (the lazy container in {@code meal/detail.jte}).
 *
 * <p>The route answers with the fragment only. A plain visit gets the fragment too, which is fine
 * because nothing links to it except the meal page's own container.
 */
@Controller
@RequestMapping("/feedback")
class MealHistoryController {

  /** How many of the newest ratings the block lists (the acceptance criterion of #132). */
  static final int LATEST = 10;

  private final RatingService ratings;
  private final Messages messages;

  MealHistoryController(RatingService ratings, Messages messages) {
    this.ratings = ratings;
    this.messages = messages;
  }

  /**
   * Shows the block of one meal.
   *
   * <p>The feedback service is asked first: it checks the household and looks the meal up inside
   * it, so a meal of another household is a 404 before any rating is read, and a person in no
   * household gets 404 from {@link CurrentHousehold}.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param mealId the meal whose history is shown
   * @param model receives the {@link MealHistoryPage}
   * @return the {@code feedback/_meal_history} fragment
   * @throws hu.lasterbergamot.mealmagician.catalog.MealNotFoundException (404) if the meal does not
   *     exist in this household
   */
  @GetMapping("/meals/{mealId}")
  String history(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long mealId,
      Model model) {
    MealRatings found = ratings.forMeal(user.userId(), household.id(), mealId, LATEST);
    model.addAttribute("history", MealHistoryPage.of(found, messages.locale()));
    return "feedback/_meal_history";
  }
}
