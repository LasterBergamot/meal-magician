package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.planning.PlanTemplateService;
import hu.lasterbergamot.mealmagician.planning.PlanTemplateSummary;
import hu.lasterbergamot.mealmagician.planning.PlanningService;
import hu.lasterbergamot.mealmagician.planning.TemplateScope;
import hu.lasterbergamot.mealmagician.shared.i18n.Messages;
import hu.lasterbergamot.mealmagician.shared.web.FormOption;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Builds the {@link ReusePage}: counts the meals of the day or week, lists the weeks a week can be
 * copied to, and picks the household's templates of the matching scope. Both services check that
 * the person is a member of the household, so a stranger never gets past the first call.
 */
@Component
class ReusePageFactory {

  /** How many weeks, starting with the current one, a week can be copied to. */
  private static final int WEEKS_AHEAD = 12;

  private final PlanningService planning;
  private final PlanTemplateService templates;
  private final Messages messages;
  private final Clock clock;

  ReusePageFactory(
      PlanningService planning, PlanTemplateService templates, Messages messages, Clock clock) {
    this.planning = planning;
    this.templates = templates;
    this.messages = messages;
    this.clock = clock;
  }

  /**
   * Builds the sheet for one day.
   *
   * @param userId the signed-in person's id
   * @param householdId the household whose plan it is
   * @param date the day
   * @param save the state of the save form
   * @return the page model, whose copy target is the same weekday one week later
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   */
  ReusePage day(long userId, long householdId, LocalDate date, ReuseSave save) {
    Locale locale = messages.locale();
    int mealCount =
        (int)
            planning.week(userId, householdId, date).slots().stream()
                .filter(slot -> slot.date().equals(date))
                .count();
    String heading =
        messages.t("plan.day", PlanDates.dayName(date, locale), PlanDates.dateLabel(date, locale));
    return new ReusePage(
        TemplateScope.DAY,
        date,
        heading,
        null,
        mealCount,
        date.plusDays(7).toString(),
        List.of(),
        templatesOf(userId, householdId, TemplateScope.DAY),
        save);
  }

  /**
   * Builds the sheet for one week.
   *
   * @param userId the signed-in person's id
   * @param householdId the household whose plan it is
   * @param week the week
   * @param save the state of the save form
   * @return the page model, with up to twelve target weeks starting at the current one (the shown
   *     week itself is left out) and the first of them preselected
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   */
  ReusePage week(long userId, long householdId, IsoWeek week, ReuseSave save) {
    Locale locale = messages.locale();
    int mealCount = planning.week(userId, householdId, week.monday()).slots().size();
    String heading = messages.t("plan.week.title", String.valueOf(week.number()));
    String range =
        messages.t(
            "plan.week.range",
            PlanDates.mediumDate(week.monday(), locale),
            PlanDates.mediumDate(week.sunday(), locale));
    IsoWeek current = IsoWeek.containing(LocalDate.now(clock));
    List<FormOption> options = new ArrayList<>();
    for (int i = 0; i < WEEKS_AHEAD; i++) {
      IsoWeek target = new IsoWeek(current.monday().plusWeeks(i));
      if (!target.equals(week)) {
        options.add(
            new FormOption(
                target.code(),
                messages.t(
                    "plan.reuse.week.option",
                    String.valueOf(target.number()),
                    PlanDates.mediumDate(target.monday(), locale),
                    PlanDates.mediumDate(target.sunday(), locale))));
      }
    }
    return new ReusePage(
        TemplateScope.WEEK,
        week.monday(),
        heading,
        range,
        mealCount,
        options.getFirst().value(),
        options,
        templatesOf(userId, householdId, TemplateScope.WEEK),
        save);
  }

  private List<PlanTemplateSummary> templatesOf(
      long userId, long householdId, TemplateScope scope) {
    return templates.list(userId, householdId).stream()
        .filter(template -> template.scope() == scope)
        .toList();
  }
}
