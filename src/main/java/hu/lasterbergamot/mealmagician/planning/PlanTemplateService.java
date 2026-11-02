package hu.lasterbergamot.mealmagician.planning;

import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.planning.internal.PlanTemplate;
import hu.lasterbergamot.mealmagician.planning.internal.PlanTemplateItem;
import hu.lasterbergamot.mealmagician.planning.internal.PlanTemplateItemRepository;
import hu.lasterbergamot.mealmagician.planning.internal.PlanTemplateRepository;
import hu.lasterbergamot.mealmagician.planning.internal.SlotSeed;
import hu.lasterbergamot.mealmagician.planning.internal.SlotWriter;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plan templates: a day or a week of the plan saved under a name and applied again later. A
 * template is frozen, so it can be renamed or deleted but not edited.
 *
 * <p>Every method takes the asking person's id and the household id, and first checks membership
 * through {@link HouseholdService#require}, so a stranger gets 404 before anything is read. A
 * template of another household gives {@link TemplateNotFoundException} (404). Every member may do
 * everything here; nothing is owner-only (PLAN.md D26). Rename and delete take the version the
 * caller last saw; an older version, or a lost race, gives {@link TemplateConflictException} (409).
 * The composite foreign keys of V9 also make the database refuse an item that points at another
 * household's meal.
 */
@Service
@Transactional
public class PlanTemplateService {

  /** Day offset, then slot type in the order the enum declares, then place, then id. */
  private static final Comparator<PlanTemplateItem> ITEM_ORDER =
      Comparator.comparingInt(PlanTemplateItem::getDayOffset)
          .thenComparing(item -> item.getSlotType().ordinal())
          .thenComparingInt(PlanTemplateItem::getPosition)
          .thenComparing(PlanTemplateItem::getId);

  private final PlanTemplateRepository templates;
  private final PlanTemplateItemRepository items;
  private final PlanningService planning;
  private final SlotWriter writer;
  private final HouseholdService households;
  private final Clock clock;

  /**
   * Creates the service.
   *
   * @param templates persistence for templates
   * @param items persistence for template items
   * @param planning the plan, read when a day or week is saved as a template
   * @param writer the shared writer that applying a template uses
   * @param households the household service, which does the membership check
   * @param clock the application clock, for the creation time
   */
  public PlanTemplateService(
      PlanTemplateRepository templates,
      PlanTemplateItemRepository items,
      PlanningService planning,
      SlotWriter writer,
      HouseholdService households,
      Clock clock) {
    this.templates = templates;
    this.items = items;
    this.planning = planning;
    this.writer = writer;
    this.households = households;
    this.clock = clock;
  }

  /**
   * Saves a day or a week of the plan as a template. For {@link TemplateScope#DAY} only {@code
   * anyDay} is saved; for {@link TemplateScope#WEEK} the whole Monday to Sunday week that contains
   * it, each meal remembering its weekday as an offset from Monday.
   *
   * @param userId the {@code app_user} id of the person saving
   * @param householdId the household whose plan it is
   * @param scope a day or a week
   * @param anyDay the day to save, or any day of the week to save
   * @param name the template's name; trimmed, 1 to 100 characters
   * @return the new template with its items
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws InvalidPlanException with {@code NAME_REQUIRED} or {@code NAME_TOO_LONG}, or with
   *     {@code NOTHING_TO_COPY} if the day or week is empty; nothing is written then
   */
  public PlanTemplateView save(
      long userId, long householdId, TemplateScope scope, LocalDate anyDay, String name) {
    households.require(householdId, userId);
    String clean = PlanRules.name(name);
    PlanWeek week = planning.week(userId, householdId, anyDay);
    List<SlotView> source =
        week.slots().stream()
            .filter(slot -> scope == TemplateScope.WEEK || slot.date().equals(anyDay))
            .toList();
    if (source.isEmpty()) {
      throw new InvalidPlanException(PlanProblem.NOTHING_TO_COPY);
    }
    PlanTemplate template =
        templates.save(new PlanTemplate(householdId, clean, scope, Instant.now(clock)));
    Map<String, Integer> next = new HashMap<>();
    List<PlanTemplateItem> saved =
        source.stream()
            .map(slot -> {
              int dayOffset =
                  scope == TemplateScope.DAY
                      ? 0
                      : (int) ChronoUnit.DAYS.between(week.monday(), slot.date());
              int position = next.merge(dayOffset + "/" + slot.slotType(), 1, Integer::sum) - 1;
              return items.save(
                  new PlanTemplateItem(
                      template.getId(),
                      householdId,
                      dayOffset,
                      slot.slotType(),
                      position,
                      slot.mealId(),
                      slot.note()));
            })
            .toList();
    return view(template, saved);
  }

  /**
   * Lists the household's plan templates of both scopes.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household
   * @return the templates sorted by name ignoring case, then id; empty if there are none
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   */
  @Transactional(readOnly = true)
  public List<PlanTemplateSummary> list(long userId, long householdId) {
    households.require(householdId, userId);
    Map<Long, Long> counts =
        items.findByHouseholdId(householdId).stream()
            .collect(Collectors.groupingBy(PlanTemplateItem::getTemplateId, Collectors.counting()));
    return templates.findByHouseholdId(householdId).stream()
        .sorted(
            Comparator.comparing(PlanTemplate::getName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(PlanTemplate::getId))
        .map(t ->
            new PlanTemplateSummary(
                t.getId(),
                t.getName(),
                t.getScope(),
                counts.getOrDefault(t.getId(), 0L).intValue(),
                t.getVersion()))
        .toList();
  }

  /**
   * Reads one template with its items.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household
   * @param templateId the template
   * @return the template as it is now
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws TemplateNotFoundException if the template does not exist in this household
   */
  @Transactional(readOnly = true)
  public PlanTemplateView find(long userId, long householdId, long templateId) {
    households.require(householdId, userId);
    PlanTemplate template = findOwned(householdId, templateId);
    return view(template, items.findByTemplateIdAndHouseholdId(templateId, householdId));
  }

  /**
   * Gives a template a new name. Nothing else about a template can change.
   *
   * @param userId the {@code app_user} id of the person renaming
   * @param householdId the household
   * @param templateId the template
   * @param expectedVersion the version the person last saw
   * @param name the new name; trimmed, 1 to 100 characters
   * @return the template after the rename
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws TemplateNotFoundException if the template does not exist in this household
   * @throws TemplateConflictException if the template changed since {@code expectedVersion}
   * @throws InvalidPlanException with {@code NAME_REQUIRED} or {@code NAME_TOO_LONG}; nothing is
   *     written then
   */
  public PlanTemplateView rename(
      long userId, long householdId, long templateId, long expectedVersion, String name) {
    households.require(householdId, userId);
    PlanTemplate template = findOwned(householdId, templateId);
    checkVersion(template, expectedVersion);
    template.rename(PlanRules.name(name));
    flush(template);
    return view(template, items.findByTemplateIdAndHouseholdId(templateId, householdId));
  }

  /**
   * Deletes a template. Its items go with it through {@code ON DELETE CASCADE} in the database.
   * Plans that were made from the template are not touched.
   *
   * @param userId the {@code app_user} id of the person deleting
   * @param householdId the household
   * @param templateId the template
   * @param expectedVersion the version the person last saw
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws TemplateNotFoundException if the template does not exist in this household
   * @throws TemplateConflictException if the template changed since {@code expectedVersion}
   */
  public void delete(long userId, long householdId, long templateId, long expectedVersion) {
    households.require(householdId, userId);
    PlanTemplate template = findOwned(householdId, templateId);
    checkVersion(template, expectedVersion);
    templates.delete(template);
    flush(template);
  }

  /**
   * Applies a template to the plan. A day template fills {@code target}; a week template fills the
   * Monday to Sunday week that contains {@code target}, each meal on its weekday. {@link
   * CopyMode#MERGE} appends after the meals already there; {@link CopyMode#REPLACE} first empties
   * the whole target day or week. Publishes one {@link SlotPlanned} per new slot.
   *
   * @param userId the {@code app_user} id of the person applying
   * @param householdId the household whose plan it is
   * @param templateId the template
   * @param target the day to fill, or any day of the week to fill
   * @param mode merge or replace
   * @return how many slots were written
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws TemplateNotFoundException if the template does not exist in this household
   * @throws PlanConflictException if a replace finds that the target changed meanwhile
   */
  public int apply(
      long userId, long householdId, long templateId, LocalDate target, CopyMode mode) {
    households.require(householdId, userId);
    PlanTemplate template = findOwned(householdId, templateId);
    List<PlanTemplateItem> ordered =
        items.findByTemplateIdAndHouseholdId(templateId, householdId).stream()
            .sorted(ITEM_ORDER)
            .toList();
    if (template.getScope() == TemplateScope.DAY) {
      List<SlotSeed> seeds =
          ordered.stream()
              .map(i -> new SlotSeed(target, i.getSlotType(), i.getMealId(), i.getNote()))
              .toList();
      return writer.write(householdId, target, target, seeds, mode);
    }
    LocalDate monday = target.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    List<SlotSeed> seeds =
        ordered.stream()
            .map(i ->
                new SlotSeed(
                    monday.plusDays(i.getDayOffset()), i.getSlotType(), i.getMealId(), i.getNote()))
            .toList();
    return writer.write(householdId, monday, monday.plusDays(6), seeds, mode);
  }

  /** The tenant-scoped lookup every method goes through. */
  private PlanTemplate findOwned(long householdId, long templateId) {
    return templates
        .findByIdAndHouseholdId(templateId, householdId)
        .orElseThrow(() -> new TemplateNotFoundException(templateId));
  }

  /** Refuses a change made on an old copy of the template, before anything is written. */
  private static void checkVersion(PlanTemplate template, long expectedVersion) {
    if (template.getVersion() != expectedVersion) {
      throw new TemplateConflictException(template.getId());
    }
  }

  /**
   * Writes the pending change now, so that a lost race surfaces inside the service call as {@link
   * TemplateConflictException} and not at commit.
   */
  private void flush(PlanTemplate template) {
    try {
      templates.flush();
    } catch (OptimisticLockingFailureException clash) {
      throw new TemplateConflictException(template.getId());
    }
  }

  private static PlanTemplateView view(PlanTemplate template, List<PlanTemplateItem> found) {
    List<PlanTemplateItemView> views =
        found.stream()
            .sorted(ITEM_ORDER)
            .map(i ->
                new PlanTemplateItemView(
                    i.getId(),
                    i.getDayOffset(),
                    i.getSlotType(),
                    i.getPosition(),
                    i.getMealId(),
                    i.getNote()))
            .toList();
    return new PlanTemplateView(
        template.getId(), template.getName(), template.getScope(), views, template.getVersion());
  }
}
