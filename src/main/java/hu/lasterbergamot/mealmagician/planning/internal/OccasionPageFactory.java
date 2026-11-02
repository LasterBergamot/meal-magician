package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.catalog.MealSearch;
import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.MealView;
import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import hu.lasterbergamot.mealmagician.planning.MenuItemView;
import hu.lasterbergamot.mealmagician.planning.OccasionService;
import hu.lasterbergamot.mealmagician.planning.OccasionSummary;
import hu.lasterbergamot.mealmagician.planning.OccasionTemplateService;
import hu.lasterbergamot.mealmagician.planning.OccasionTemplateView;
import hu.lasterbergamot.mealmagician.planning.OccasionView;
import hu.lasterbergamot.mealmagician.shared.i18n.Messages;
import hu.lasterbergamot.mealmagician.shared.web.FormOption;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds the models of the Occasions pages: the list (the next occasion, the later ones, the past
 * ones, and the templates), the detail page with its menu, the template page, and the choices of
 * the kind list.
 *
 * <p>The date labels are formatted here in the reader's language, and "today" comes from the {@link
 * Clock} bean, so tests can fix the day. Meal names come from the catalog's search, which includes
 * archived meals, because an archived meal can still be on a menu.
 */
@Component
class OccasionPageFactory {

  private final OccasionService occasions;
  private final OccasionTemplateService templates;
  private final MealService meals;
  private final Messages messages;
  private final Clock clock;

  OccasionPageFactory(
      OccasionService occasions,
      OccasionTemplateService templates,
      MealService meals,
      Messages messages,
      Clock clock) {
    this.occasions = occasions;
    this.templates = templates;
    this.meals = meals;
    this.messages = messages;
    this.clock = clock;
  }

  /**
   * Builds the Occasions list. The occasions on or after today come first, earliest first; the
   * nearest one becomes the card. Those before today follow, newest first, so the last one planned
   * is at the top.
   *
   * @param userId the signed-in person's id
   * @param householdId the household whose occasions are listed
   * @return the list model; it may be empty
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   */
  OccasionsPage list(long userId, long householdId) {
    LocalDate today = LocalDate.now(clock);
    List<OccasionCard> upcoming = new ArrayList<>();
    List<OccasionCard> past = new ArrayList<>();
    for (OccasionSummary summary : occasions.list(userId, householdId)) {
      OccasionCard card = card(summary, today);
      if (summary.date().isBefore(today)) {
        past.add(card);
      } else {
        upcoming.add(card);
      }
    }
    Collections.reverse(past);
    OccasionCard next = upcoming.isEmpty() ? null : upcoming.remove(0);
    List<OccasionTemplateRow> rows =
        templates.list(userId, householdId).stream()
            .map(s -> new OccasionTemplateRow(s.id(), s.name(), s.type(), s.itemCount()))
            .toList();
    return new OccasionsPage(next, List.copyOf(upcoming), List.copyOf(past), rows);
  }

  /**
   * Builds one occasion template's page: its kind, notes and menu with the meal names, and a fresh
   * "Plan it again" form.
   *
   * @param userId the signed-in person's id
   * @param householdId the household
   * @param templateId the template to show
   * @return the page model, with an empty date field and no error
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws hu.lasterbergamot.mealmagician.planning.TemplateNotFoundException (404) if the template
   *     does not exist in this household
   */
  OccasionTemplatePage template(long userId, long householdId, long templateId) {
    OccasionTemplateView template = templates.find(userId, householdId, templateId);
    Map<Long, MealView> byId = mealsById(userId, householdId);
    List<MealLine> menu = new ArrayList<>();
    for (MenuItemView item : template.menu()) {
      MealView meal = byId.get(item.mealId());
      menu.add(new MealLine(item.mealId(), meal.name(), meal.archived()));
    }
    return new OccasionTemplatePage(
        template.id(),
        template.name(),
        template.type(),
        template.notes(),
        List.copyOf(menu),
        "",
        null);
  }

  /**
   * Builds one occasion's detail page: its values, and its menu with the meal names.
   *
   * @param userId the signed-in person's id
   * @param householdId the household
   * @param occasionId the occasion to show
   * @return the detail model, with the conflict banner off
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws hu.lasterbergamot.mealmagician.planning.OccasionNotFoundException (404) if the occasion
   *     does not exist in this household
   */
  OccasionDetailPage detail(long userId, long householdId, long occasionId) {
    OccasionView occasion = occasions.find(userId, householdId, occasionId);
    Map<Long, MealView> byId = mealsById(userId, householdId);
    int size = occasion.menu().size();
    List<MenuLine> menu = new ArrayList<>();
    for (MenuItemView item : occasion.menu()) {
      MealView meal = byId.get(item.mealId());
      menu.add(
          new MenuLine(
              item.id(), item.mealId(), meal.name(), meal.archived(), item.position(), size));
    }
    return new OccasionDetailPage(
        occasion.id(),
        occasion.name(),
        PlanDates.fullLabel(occasion.date(), messages.locale()),
        occasion.type(),
        occasion.notes(),
        List.copyOf(menu),
        occasion.version(),
        false,
        occasion.name(),
        null);
  }

  /**
   * The choices of the kind list, in the enum's order, labelled with the catalog's words.
   *
   * @return one option per {@link OccasionType}
   */
  List<FormOption> typeOptions() {
    List<FormOption> options = new ArrayList<>();
    for (OccasionType type : OccasionType.values()) {
      options.add(new FormOption(type.name(), messages.t(OccasionTypes.key(type))));
    }
    return List.copyOf(options);
  }

  private OccasionCard card(OccasionSummary summary, LocalDate today) {
    return new OccasionCard(
        summary.id(),
        summary.name(),
        PlanDates.fullLabel(summary.date(), messages.locale()),
        summary.type(),
        summary.itemCount(),
        summary.date().equals(today));
  }

  /**
   * Every meal of the household, archived ones included, keyed by id. The menu's meals are all from
   * this household (the database enforces it), so every lookup finds its meal.
   */
  private Map<Long, MealView> mealsById(long userId, long householdId) {
    Map<Long, MealView> byId = new HashMap<>();
    for (MealView meal : meals.search(userId, householdId, new MealSearch(null, null, true))) {
      byId.put(meal.id(), meal);
    }
    return byId;
  }
}
