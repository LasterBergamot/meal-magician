package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.planning.PlanProblem;
import hu.lasterbergamot.mealmagician.planning.TemplateScope;
import java.util.List;

/**
 * One template's page: its name and scope, its meals grouped by day, and the state of the rename
 * card (what the person typed and which error, if any, to show under it). The delete form needs the
 * version, so the page carries it too.
 *
 * <p>The record is immutable. {@link #withRename} and {@link #withConflict} return a copy, which is
 * how the controller turns the page into a rejected form without rebuilding it by hand.
 *
 * @param id the template's id
 * @param name the stored name, shown as the heading
 * @param scope whether the template covers one day or one week
 * @param days the meals, grouped by day, in plan order
 * @param version the optimistic-lock version the rename and delete forms send back
 * @param renameName the name the rename field shows: the stored name, or what was typed when it was
 *     rejected
 * @param renameError the message key of the rename error, or {@code null} when there is none
 * @param conflict whether a delete was refused as stale, so the page shows the banner
 */
public record TemplatePage(
    long id,
    String name,
    TemplateScope scope,
    List<TemplateDayLines> days,
    long version,
    String renameName,
    String renameError,
    boolean conflict) {

  /**
   * The message key of the stale-version error. A rename refused as stale is not the typed name's
   * fault, so it is shown as a warning above the form and not under the field.
   */
  public static final String CONFLICT_KEY = "plan.templates.conflict.message";

  /**
   * The address of this template's page.
   *
   * @return for example {@code /plan/templates/7}
   */
  public String href() {
    return "/plan/templates/" + id;
  }

  /**
   * The address the rename form posts to.
   *
   * @return for example {@code /plan/templates/7/rename}
   */
  public String renameAction() {
    return href() + "/rename";
  }

  /**
   * The address the delete confirm posts to. The version goes in the query string, so the confirm
   * dialog needs no hidden input.
   *
   * @return for example {@code /plan/templates/7/delete?version=3}
   */
  public String deleteAction() {
    return href() + "/delete?version=" + version;
  }

  /**
   * The message key of the scope's badge.
   *
   * @return {@code plan.templates.scope.day} or {@code plan.templates.scope.week}
   */
  public String scopeKey() {
    return scope == TemplateScope.DAY ? "plan.templates.scope.day" : "plan.templates.scope.week";
  }

  /**
   * Tells whether the rename was refused as stale. Such an error goes in the warning above the
   * form, not under the field.
   *
   * @return {@code true} when {@link #renameError} is {@link #CONFLICT_KEY}
   */
  public boolean staleRename() {
    return CONFLICT_KEY.equals(renameError);
  }

  /**
   * The error to show under the rename field. A stale version is excluded because it has its own
   * warning above the form.
   *
   * @return the message key to show under the field, or {@code null} for none
   */
  public String fieldError() {
    return renameError == null || staleRename() ? null : renameError;
  }

  /**
   * A copy of this page for a rejected rename: the typed name stays in the field and the error is
   * set.
   *
   * @param typed what the person typed, kept as it was
   * @param errorKey the message key of the error, or {@link #CONFLICT_KEY}
   * @return the copy
   */
  public TemplatePage withRename(String typed, String errorKey) {
    return new TemplatePage(id, name, scope, days, version, typed, errorKey, conflict);
  }

  /**
   * A copy of this page for a delete refused as stale, which shows the banner above the meals.
   *
   * @return the copy, with {@link #conflict} set
   */
  public TemplatePage withConflict() {
    return new TemplatePage(id, name, scope, days, version, renameName, renameError, true);
  }

  /**
   * The message key for a refused template name.
   *
   * @param problem the rule the name broke, from {@code PlanTemplateService}
   * @return the key of the "too long" message for {@code NAME_TOO_LONG}, otherwise the key of the
   *     "required" message
   */
  public static String renameErrorKey(PlanProblem problem) {
    return problem == PlanProblem.NAME_TOO_LONG
        ? "plan.templates.name.long"
        : "plan.templates.name.required";
  }
}
