package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.planning.PlanTemplateSummary;
import hu.lasterbergamot.mealmagician.planning.TemplateScope;
import hu.lasterbergamot.mealmagician.shared.web.FormOption;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything the reuse sheet prints for one day or one week: what is planned there, where it can be
 * copied to, the form that saves it as a template and the templates that can be applied to it.
 *
 * @param scope whether the sheet is about a day or a week
 * @param date the day, or the week's Monday
 * @param heading the translated name of the day or week, for example {@code Hétfő nov. 2.}
 * @param range the translated first and last date of a week, or {@code null} for a day
 * @param mealCount how many meals are planned on the day or in the week
 * @param copyTarget the preselected target: a date for a day, a week code for a week
 * @param weekOptions the weeks a week can be copied to; empty for a day
 * @param templates the household's templates of the same scope
 * @param save the state of the save form
 */
public record ReusePage(
    TemplateScope scope,
    LocalDate date,
    String heading,
    String range,
    int mealCount,
    String copyTarget,
    List<FormOption> weekOptions,
    List<PlanTemplateSummary> templates,
    ReuseSave save) {

  /**
   * Tells whether the sheet is about a single day.
   *
   * @return {@code true} for a day, {@code false} for a week
   */
  public boolean forDay() {
    return scope == TemplateScope.DAY;
  }

  /**
   * Tells whether there is nothing to copy or save.
   *
   * @return {@code true} when no meal is planned
   */
  public boolean empty() {
    return mealCount == 0;
  }

  /**
   * The scope's name, sent back by the save form.
   *
   * @return {@code DAY} or {@code WEEK}
   */
  public String scopeName() {
    return scope.name();
  }

  /**
   * The date as the forms send it.
   *
   * @return {@code YYYY-MM-DD} of the day or of the week's Monday
   */
  public String dateValue() {
    return date.toString();
  }

  /**
   * The source of a copy as the copy form sends it.
   *
   * @return the date for a day, the week code (for example {@code 2026-W45}) for a week
   */
  public String fromValue() {
    return forDay() ? date.toString() : IsoWeek.containing(date).code();
  }

  /**
   * The address a copy form posts to.
   *
   * @param mode {@code MERGE} or {@code REPLACE}
   * @return for example {@code /plan/days/copy?mode=MERGE}
   */
  public String copyAction(String mode) {
    return (forDay() ? "/plan/days/copy" : "/plan/weeks/copy") + "?mode=" + mode;
  }

  /**
   * The address that applies a template.
   *
   * @param templateId the template
   * @param mode {@code MERGE} or {@code REPLACE}
   * @return for example {@code /plan/templates/7/apply?mode=MERGE}
   */
  public String applyAction(long templateId, String mode) {
    return "/plan/templates/" + templateId + "/apply?mode=" + mode;
  }

  /**
   * The address a replace-confirm posts to. It carries the date in the query because the confirm's
   * button sits outside any form.
   *
   * @param templateId the template
   * @return for example {@code /plan/templates/7/apply?mode=REPLACE&date=2026-11-02}
   */
  public String applyReplaceAction(long templateId) {
    return applyAction(templateId, "REPLACE") + "&date=" + date;
  }

  /**
   * The address of the week page this sheet belongs to.
   *
   * @return the week's address
   */
  public String weekHref() {
    return WeekPage.hrefOf(IsoWeek.containing(date));
  }

  /**
   * The key of the text shown when nothing is planned.
   *
   * @return {@code plan.reuse.empty.day} or {@code plan.reuse.empty.week}
   */
  public String emptyKey() {
    return key("empty." + part());
  }

  /**
   * The key of the copy section's heading.
   *
   * @return the key for the scope
   */
  public String copyHeadingKey() {
    return key("copy." + part() + ".heading");
  }

  /**
   * The key of the replace-confirm's title.
   *
   * @return the key for the scope
   */
  public String replaceTitleKey() {
    return key("replace." + part() + ".title");
  }

  /**
   * The key of the replace-confirm's message.
   *
   * @return the key for the scope
   */
  public String replaceMessageKey() {
    return key("replace." + part() + ".message");
  }

  /**
   * The key of the save section's heading.
   *
   * @return the key for the scope
   */
  public String saveHeadingKey() {
    return key("save." + part() + ".heading");
  }

  /**
   * The key of the apply section's heading.
   *
   * @return the key for the scope
   */
  public String applyHeadingKey() {
    return key("apply." + part() + ".heading");
  }

  /**
   * The key of the text shown when there is no template of this scope.
   *
   * @return the key for the scope
   */
  public String applyNoneKey() {
    return key("apply.none." + part());
  }

  /**
   * The key of the title of the confirm that replaces with a template.
   *
   * @return the key for the scope
   */
  public String applyReplaceTitleKey() {
    return key("apply.replace." + part() + ".title");
  }

  private String part() {
    return forDay() ? "day" : "week";
  }

  private static String key(String part) {
    return "plan.reuse." + part;
  }
}
