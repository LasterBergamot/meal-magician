package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.catalog.MealSearch;
import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.MealView;
import hu.lasterbergamot.mealmagician.planning.PlanTemplateItemView;
import hu.lasterbergamot.mealmagician.planning.PlanTemplateService;
import hu.lasterbergamot.mealmagician.planning.PlanTemplateSummary;
import hu.lasterbergamot.mealmagician.planning.PlanTemplateView;
import hu.lasterbergamot.mealmagician.planning.TemplateScope;
import hu.lasterbergamot.mealmagician.shared.i18n.Messages;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Builds the two template pages' models: the list of templates, and one template with its meals. It
 * reads the templates from {@link PlanTemplateService} and the meal names from {@link MealService},
 * and formats the weekday headings in the reader's language.
 *
 * <p>The templates keep only meal ids, so the names come from one search of the whole library
 * (archived meals included), as on the week page. Both services check that the person is a member,
 * so a stranger never gets past the first call.
 */
@Component
class TemplatePageFactory {

  /**
   * A Monday, so adding a day offset to it gives the right weekday name for each offset 0 to 6.
   * Only the weekday name is used, so the year is arbitrary.
   */
  private static final LocalDate REFERENCE_MONDAY = LocalDate.of(2024, 1, 1);

  private final PlanTemplateService templates;
  private final MealService meals;
  private final Messages messages;

  /**
   * Creates the factory.
   *
   * @param templates the plan template service, which does the household check and the lookups
   * @param meals the meal service, for the names of the meals on a template
   * @param messages the translated texts and the reader's locale
   */
  TemplatePageFactory(PlanTemplateService templates, MealService meals, Messages messages) {
    this.templates = templates;
    this.meals = meals;
    this.messages = messages;
  }

  /**
   * Builds the template list, with the day templates and the week templates in two groups.
   *
   * @param userId the signed-in person's id
   * @param householdId the household whose templates are listed
   * @return the list page; both groups are empty when the household has no templates
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   */
  TemplatesPage list(long userId, long householdId) {
    List<PlanTemplateSummary> all = templates.list(userId, householdId);
    List<TemplateRow> days =
        all.stream()
            .filter(summary -> summary.scope() == TemplateScope.DAY)
            .map(TemplatePageFactory::row)
            .toList();
    List<TemplateRow> weeks =
        all.stream()
            .filter(summary -> summary.scope() == TemplateScope.WEEK)
            .map(TemplatePageFactory::row)
            .toList();
    return new TemplatesPage(days, weeks);
  }

  /**
   * Builds one template's page: its meals grouped by day in plan order, with the rename card ready
   * to show the stored name.
   *
   * @param userId the signed-in person's id
   * @param householdId the household the template must belong to
   * @param templateId the template to show
   * @return the page model, with no rename error and no conflict
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws hu.lasterbergamot.mealmagician.planning.TemplateNotFoundException if the template is
   *     not in this household
   */
  TemplatePage detail(long userId, long householdId, long templateId) {
    PlanTemplateView view = templates.find(userId, householdId, templateId);
    // A planned meal cannot be deleted while a template holds it (a foreign key in V9), so every
    // meal id on the template has a name here.
    Map<Long, MealView> byId =
        meals.search(userId, householdId, new MealSearch(null, null, true)).stream()
            .collect(Collectors.toMap(MealView::id, Function.identity()));
    Locale locale = messages.locale();
    // The items arrive in plan order, so a LinkedHashMap keeps the days in that order too.
    Map<Integer, List<TemplateLine>> byOffset = new LinkedHashMap<>();
    for (PlanTemplateItemView item : view.items()) {
      MealView meal = byId.get(item.mealId());
      byOffset
          .computeIfAbsent(item.dayOffset(), offset -> new ArrayList<>())
          .add(new TemplateLine(item.slotType().name(), meal.name(), meal.archived(), item.note()));
    }
    List<TemplateDayLines> days =
        byOffset.entrySet().stream()
            .map(entry ->
                new TemplateDayLines(
                    dayLabel(view.scope(), entry.getKey(), locale), entry.getValue()))
            .toList();
    return new TemplatePage(
        view.id(), view.name(), view.scope(), days, view.version(), view.name(), null, false);
  }

  /** The heading of one day: the word for the one day, or the weekday name in the week. */
  private String dayLabel(TemplateScope scope, int dayOffset, Locale locale) {
    if (scope == TemplateScope.DAY) {
      return messages.t("plan.templates.day.only");
    }
    return PlanDates.dayName(REFERENCE_MONDAY.plusDays(dayOffset), locale);
  }

  private static TemplateRow row(PlanTemplateSummary summary) {
    return new TemplateRow(summary.id(), summary.name(), summary.itemCount());
  }
}
