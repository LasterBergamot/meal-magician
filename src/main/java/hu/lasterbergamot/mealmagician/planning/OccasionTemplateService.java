package hu.lasterbergamot.mealmagician.planning;

import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.planning.internal.Occasion;
import hu.lasterbergamot.mealmagician.planning.internal.OccasionItem;
import hu.lasterbergamot.mealmagician.planning.internal.OccasionItemRepository;
import hu.lasterbergamot.mealmagician.planning.internal.OccasionRepository;
import hu.lasterbergamot.mealmagician.planning.internal.OccasionTemplate;
import hu.lasterbergamot.mealmagician.planning.internal.OccasionTemplateItem;
import hu.lasterbergamot.mealmagician.planning.internal.OccasionTemplateItemRepository;
import hu.lasterbergamot.mealmagician.planning.internal.OccasionTemplateRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Occasion templates: the menu of an occasion saved under a name, so a new occasion on any date can
 * start from it. A template keeps the occasion's type and notes and its menu, but no date.
 *
 * <p>Every method takes the asking person's id and the household id, and first checks membership
 * through {@link HouseholdService#require}, so a stranger gets 404 before anything is read. A
 * template or occasion of another household gives {@link TemplateNotFoundException} or {@link
 * OccasionNotFoundException} (404). Every member may do everything here; nothing is owner-only
 * (PLAN.md D26).
 *
 * <p>There is no rename in v1, so deleting takes no version. Occasions made from a template do not
 * depend on it and stay when it is deleted. Nothing is published.
 */
@Service
@Transactional
public class OccasionTemplateService {

  private final OccasionTemplateRepository templates;
  private final OccasionTemplateItemRepository templateItems;
  private final OccasionRepository occasions;
  private final OccasionItemRepository items;
  private final HouseholdService households;
  private final Clock clock;

  /**
   * Creates the service.
   *
   * @param templates persistence for occasion templates
   * @param templateItems persistence for their menu items
   * @param occasions persistence for occasions, read on save and written on use
   * @param items persistence for occasion menu items
   * @param households the household service, which does the membership check
   * @param clock the application clock, for the creation time
   */
  public OccasionTemplateService(
      OccasionTemplateRepository templates,
      OccasionTemplateItemRepository templateItems,
      OccasionRepository occasions,
      OccasionItemRepository items,
      HouseholdService households,
      Clock clock) {
    this.templates = templates;
    this.templateItems = templateItems;
    this.occasions = occasions;
    this.items = items;
    this.households = households;
    this.clock = clock;
  }

  /**
   * Saves an occasion as a template: its type, its notes and its menu in menu order.
   *
   * @param userId the {@code app_user} id of the person saving
   * @param householdId the household
   * @param occasionId the occasion to save
   * @param name the template's name; trimmed, 1 to 100 characters
   * @return the new template
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws OccasionNotFoundException if the occasion does not exist in this household
   * @throws InvalidPlanException with {@code NAME_REQUIRED} or {@code NAME_TOO_LONG}, or with
   *     {@code NOTHING_TO_COPY} if the menu is empty; nothing is written then
   */
  public OccasionTemplateView saveFromOccasion(
      long userId, long householdId, long occasionId, String name) {
    households.require(householdId, userId);
    Occasion occasion =
        occasions
            .findByIdAndHouseholdId(occasionId, householdId)
            .orElseThrow(() -> new OccasionNotFoundException(occasionId));
    String clean = PlanRules.name(name);
    List<OccasionItem> menu =
        items.findByOccasionIdAndHouseholdIdOrderByPositionAscIdAsc(occasionId, householdId);
    if (menu.isEmpty()) {
      throw new InvalidPlanException(PlanProblem.NOTHING_TO_COPY);
    }
    OccasionTemplate template =
        templates.save(
            new OccasionTemplate(
                householdId, clean, occasion.getType(), occasion.getNotes(), Instant.now(clock)));
    List<OccasionTemplateItem> saved = new ArrayList<>();
    for (int place = 0; place < menu.size(); place++) {
      saved.add(
          templateItems.save(
              new OccasionTemplateItem(
                  template.getId(), householdId, place, menu.get(place).getMealId())));
    }
    return view(template, saved);
  }

  /**
   * Lists the household's occasion templates.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household
   * @return the templates sorted by name ignoring case, then id; empty if there are none
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   */
  @Transactional(readOnly = true)
  public List<OccasionTemplateSummary> list(long userId, long householdId) {
    households.require(householdId, userId);
    Map<Long, Long> counts =
        templateItems.findByHouseholdId(householdId).stream()
            .collect(
                Collectors.groupingBy(OccasionTemplateItem::getTemplateId, Collectors.counting()));
    return templates.findByHouseholdId(householdId).stream()
        .sorted(
            Comparator.comparing(OccasionTemplate::getName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(OccasionTemplate::getId))
        .map(t ->
            new OccasionTemplateSummary(
                t.getId(), t.getName(), t.getType(), counts.getOrDefault(t.getId(), 0L).intValue()))
        .toList();
  }

  /**
   * Reads one occasion template with its menu.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household
   * @param templateId the template
   * @return the template
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws TemplateNotFoundException if the template does not exist in this household
   */
  @Transactional(readOnly = true)
  public OccasionTemplateView find(long userId, long householdId, long templateId) {
    households.require(householdId, userId);
    OccasionTemplate template = findOwned(householdId, templateId);
    return view(
        template,
        templateItems.findByTemplateIdAndHouseholdIdOrderByPositionAscIdAsc(
            templateId, householdId));
  }

  /**
   * Creates an occasion from a template: the template's name, type and notes on the given date,
   * with a copy of its menu (new item ids, same meals).
   *
   * @param userId the {@code app_user} id of the person using the template
   * @param householdId the household
   * @param templateId the template
   * @param date the day of the new occasion
   * @return the new occasion with its menu
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws TemplateNotFoundException if the template does not exist in this household
   * @throws InvalidPlanException with {@code DATE_REQUIRED} if {@code date} is {@code null};
   *     nothing is written then
   */
  public OccasionView use(long userId, long householdId, long templateId, LocalDate date) {
    households.require(householdId, userId);
    OccasionTemplate template = findOwned(householdId, templateId);
    if (date == null) {
      throw new InvalidPlanException(PlanProblem.DATE_REQUIRED);
    }
    Occasion occasion =
        occasions.save(
            new Occasion(
                householdId,
                template.getName(),
                date,
                template.getType(),
                template.getNotes(),
                Instant.now(clock)));
    List<OccasionItem> menu = new ArrayList<>();
    for (OccasionTemplateItem source :
        templateItems.findByTemplateIdAndHouseholdIdOrderByPositionAscIdAsc(
            templateId, householdId)) {
      menu.add(
          items.save(
              new OccasionItem(
                  occasion.getId(), householdId, source.getPosition(), source.getMealId())));
    }
    return OccasionViews.occasion(occasion, menu);
  }

  /**
   * Deletes an occasion template. Its menu goes with it through {@code ON DELETE CASCADE}. There is
   * no version check because a template cannot be edited in v1. Occasions made from it stay.
   *
   * @param userId the {@code app_user} id of the person deleting
   * @param householdId the household
   * @param templateId the template
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws TemplateNotFoundException if the template does not exist in this household
   */
  public void delete(long userId, long householdId, long templateId) {
    households.require(householdId, userId);
    templates.delete(findOwned(householdId, templateId));
  }

  /** The tenant-scoped lookup every method goes through. */
  private OccasionTemplate findOwned(long householdId, long templateId) {
    return templates
        .findByIdAndHouseholdId(templateId, householdId)
        .orElseThrow(() -> new TemplateNotFoundException(templateId));
  }

  private static OccasionTemplateView view(
      OccasionTemplate template, List<OccasionTemplateItem> menu) {
    return new OccasionTemplateView(
        template.getId(),
        template.getName(),
        template.getType(),
        template.getNotes(),
        menu.stream()
            .map(i -> new MenuItemView(i.getId(), i.getPosition(), i.getMealId()))
            .toList());
  }
}
