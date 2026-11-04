package hu.lasterbergamot.mealmagician.assistant;

import java.util.List;

/**
 * What the wizard suggests for one occasion's menu (#161, Appendix D.5): library meals the menu can
 * take with one click, and ideas that are not in the library yet. Nothing here changes the menu or
 * the library; a member must add each one.
 *
 * @param meals up to six library meals, best first; each id is a live meal of the household
 * @param ideas up to three meals that are not in the library yet, best first
 */
public record MenuSuggestions(List<MenuMeal> meals, List<MenuIdea> ideas) {

  /**
   * A library meal the menu could take.
   *
   * @param mealId the library meal's id
   * @param mealName its name, as the library has it
   */
  public record MenuMeal(long mealId, String mealName) {}

  /**
   * A meal the library does not have yet. It can be added to the library, and then to a menu; it is
   * never added by itself.
   *
   * @param name the cleaned name, never empty
   * @param reason one sentence on why it suits the occasion, or an empty text
   * @param tags the cleaned tags; possibly empty
   * @param ingredients the cleaned ingredient names; possibly empty
   */
  public record MenuIdea(String name, String reason, List<String> tags, List<String> ingredients) {}
}
