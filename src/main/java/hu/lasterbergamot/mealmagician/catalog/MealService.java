package hu.lasterbergamot.mealmagician.catalog;

import hu.lasterbergamot.mealmagician.catalog.internal.Meal;
import hu.lasterbergamot.mealmagician.catalog.internal.MealRepository;
import hu.lasterbergamot.mealmagician.catalog.internal.MealTag;
import hu.lasterbergamot.mealmagician.catalog.internal.MealTagName;
import hu.lasterbergamot.mealmagician.catalog.internal.MealTagRepository;
import hu.lasterbergamot.mealmagician.catalog.internal.Tag;
import hu.lasterbergamot.mealmagician.catalog.internal.TagRepository;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The meal library's public entry point: creating, quick-adding, editing, archiving and restoring
 * meals, and searching them by name and tag. The catalog module's controllers and the planner call
 * only this class.
 *
 * <p>Every method takes the asking person's id and the household id, and first checks that the
 * person belongs to the household through {@link HouseholdService#require}. A stranger therefore
 * gets {@link hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException} (404) before any
 * catalog query runs. A meal id from another household gives {@link MealNotFoundException} (404),
 * because every meal lookup is scoped to the household as well (PLAN.md D8). Members of any role
 * may use the library: nothing here is owner-only, so no 403 is possible.
 *
 * <p>Changes publish {@link MealChanged} inside their transaction. Reads never publish.
 */
@Service
@Transactional
public class MealService {

  private final MealRepository meals;
  private final TagRepository tags;
  private final MealTagRepository mealTags;
  private final HouseholdService households;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  /**
   * Creates the service.
   *
   * @param meals persistence for meals
   * @param tags persistence for tags
   * @param mealTags persistence for the meal-tag links
   * @param households the household service, which does the membership check
   * @param events publishes {@link MealChanged}
   * @param clock the application clock, for the creation time
   */
  public MealService(
      MealRepository meals,
      TagRepository tags,
      MealTagRepository mealTags,
      HouseholdService households,
      ApplicationEventPublisher events,
      Clock clock) {
    this.meals = meals;
    this.tags = tags;
    this.mealTags = mealTags;
    this.households = households;
    this.events = events;
    this.clock = clock;
  }

  /**
   * Adds a meal with its details.
   *
   * @param userId the {@code app_user} id of the person adding it
   * @param householdId the household the meal joins
   * @param details the name, notes, recipe link and tags; {@code details} itself must not be {@code
   *     null}, but its fields may be
   * @return the new meal as the library shows it
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws InvalidMealException if a field breaks its rule (see {@link MealProblem})
   */
  public MealView create(long userId, long householdId, MealDetails details) {
    households.require(householdId, userId);
    Normalized clean = normalize(details);
    Meal meal =
        meals.save(
            new Meal(
                householdId, clean.name(), clean.notes(), clean.recipeUrl(), Instant.now(clock)));
    List<String> names = attachTags(householdId, meal, clean.tags());
    publish(meal);
    return view(meal, names);
  }

  /**
   * Adds a meal from its name alone: the quick path for a phone, where the details can be added
   * later with {@link #edit}.
   *
   * @param userId the {@code app_user} id of the person adding it
   * @param householdId the household the meal joins
   * @param name the meal's name
   * @return the new meal, with no notes, link or tags
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws InvalidMealException with {@code NAME_REQUIRED} or {@code NAME_TOO_LONG}
   */
  public MealView quickAdd(long userId, long householdId, String name) {
    return create(userId, householdId, new MealDetails(name, null, null, List.of()));
  }

  /**
   * Replaces a meal's details and its tag set. The tags are the new complete set: a tag not in the
   * list is removed from the meal, and a new one is created for the household if it does not exist.
   *
   * <p>A concurrent edit of the same meal fails with JPA's optimistic-lock exception when the
   * change is written, because the meal's {@code version} has moved on. The page turns that into a
   * "someone else changed this, reload" message.
   *
   * @param userId the {@code app_user} id of the person editing
   * @param householdId the household the meal belongs to
   * @param mealId the meal to edit
   * @param details the new name, notes, recipe link and tags
   * @return the meal after the change
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws MealNotFoundException if the meal does not exist in this household
   * @throws InvalidMealException if a field breaks its rule; nothing is changed then
   * @throws org.springframework.dao.OptimisticLockingFailureException if someone else changed the
   *     meal at the same moment
   */
  public MealView edit(long userId, long householdId, long mealId, MealDetails details) {
    households.require(householdId, userId);
    Normalized clean = normalize(details);
    Meal meal = findOwned(householdId, mealId);
    meal.change(clean.name(), clean.notes(), clean.recipeUrl());
    List<String> names = attachTags(householdId, meal, clean.tags());
    meals.saveAndFlush(meal);
    publish(meal);
    return view(meal, names);
  }

  /**
   * Takes a meal out of the default library. Its history (past plans, ratings) is untouched, and
   * {@link #restore} brings it back. Archiving an archived meal changes nothing but still publishes
   * the event, which is harmless.
   *
   * @param userId the {@code app_user} id of the person archiving
   * @param householdId the household the meal belongs to
   * @param mealId the meal
   * @return the meal after the change
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws MealNotFoundException if the meal does not exist in this household
   */
  public MealView archive(long userId, long householdId, long mealId) {
    return setArchived(userId, householdId, mealId, true);
  }

  /**
   * Puts an archived meal back into the default library.
   *
   * @param userId the {@code app_user} id of the person restoring
   * @param householdId the household the meal belongs to
   * @param mealId the meal
   * @return the meal after the change
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws MealNotFoundException if the meal does not exist in this household
   */
  public MealView restore(long userId, long householdId, long mealId) {
    return setArchived(userId, householdId, mealId, false);
  }

  /**
   * Reads one meal.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household the meal belongs to
   * @param mealId the meal
   * @return the meal as the library shows it
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws MealNotFoundException if the meal does not exist in this household
   */
  @Transactional(readOnly = true)
  public MealView find(long userId, long householdId, long mealId) {
    households.require(householdId, userId);
    Meal meal = findOwned(householdId, mealId);
    return view(meal, mealTags.findTagNames(householdId, mealId));
  }

  /**
   * Searches the library by name and tag, in name order. Archived meals are left out unless the
   * search asks for them.
   *
   * @param userId the {@code app_user} id of the person searching
   * @param householdId the household to search
   * @param search the name text, tag and archive flag; {@code search} itself must not be {@code
   *     null}
   * @return the matching meals; empty if nothing matches, or if the tag does not exist
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   */
  @Transactional(readOnly = true)
  public List<MealView> search(long userId, long householdId, MealSearch search) {
    households.require(householdId, userId);
    String pattern = MealRules.namePattern(search.text());
    Long tagId = null;
    if (search.tag() != null && !search.tag().isBlank()) {
      Optional<Tag> tag =
          tags.findByHouseholdIdAndNameKey(householdId, MealRules.tagKey(search.tag()));
      if (tag.isEmpty()) {
        return List.of();
      }
      tagId = tag.get().getId();
    }
    Map<Long, List<String>> namesByMeal = tagNamesByMeal(householdId);
    return meals.search(householdId, pattern, tagId, search.includeArchived()).stream()
        .map(meal -> view(meal, namesByMeal.getOrDefault(meal.getId(), List.of())))
        .toList();
  }

  /**
   * The tags the household uses, for a filter list or autocompletion. Only tags on at least one
   * meal are listed.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household
   * @return the tag names ordered case-insensitively; empty when the household has none
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   */
  @Transactional(readOnly = true)
  public List<String> tagNames(long userId, long householdId) {
    households.require(householdId, userId);
    return tags.findInUse(householdId).stream().map(Tag::getName).toList();
  }

  private MealView setArchived(long userId, long householdId, long mealId, boolean archived) {
    households.require(householdId, userId);
    Meal meal = findOwned(householdId, mealId);
    if (archived) {
      meal.archive();
    } else {
      meal.restore();
    }
    meals.saveAndFlush(meal);
    publish(meal);
    return view(meal, mealTags.findTagNames(householdId, mealId));
  }

  /** The tenant-scoped lookup every change and read goes through. */
  private Meal findOwned(long householdId, long mealId) {
    return meals
        .findByIdAndHouseholdId(mealId, householdId)
        .orElseThrow(() -> new MealNotFoundException(mealId));
  }

  /**
   * Cleans every field of the input. Done before anything is written, so a refused meal leaves no
   * trace (for example, a half-created tag).
   */
  private static Normalized normalize(MealDetails details) {
    return new Normalized(
        MealRules.name(details.name()),
        MealRules.notes(details.notes()),
        MealRules.recipeUrl(details.recipeUrl()),
        MealRules.tags(details.tags()));
  }

  /**
   * Makes the meal carry exactly the wanted tags. Tags are created for the household when they do
   * not exist yet, and the links are changed by difference: only links that were added or dropped
   * are written, so an unchanged tag keeps its row.
   *
   * @return the tag names now on the meal, ordered
   */
  private List<String> attachTags(long householdId, Meal meal, Map<String, String> wanted) {
    List<Tag> wantedTags = new ArrayList<>();
    for (Map.Entry<String, String> entry : wanted.entrySet()) {
      Tag tag =
          tags.findByHouseholdIdAndNameKey(householdId, entry.getKey())
              .orElseGet(() -> tags.save(new Tag(householdId, entry.getValue(), entry.getKey())));
      wantedTags.add(tag);
    }
    Set<Long> wantedIds = wantedTags.stream().map(Tag::getId).collect(Collectors.toSet());
    List<MealTag> current = mealTags.findLinks(householdId, meal.getId());
    Set<Long> currentIds =
        current.stream().map(link -> link.getId().getTagId()).collect(Collectors.toSet());
    mealTags.deleteAll(
        current.stream().filter(link -> !wantedIds.contains(link.getId().getTagId())).toList());
    mealTags.saveAll(
        wantedTags.stream()
            .filter(tag -> !currentIds.contains(tag.getId()))
            .map(tag -> new MealTag(householdId, meal.getId(), tag.getId()))
            .toList());
    return wantedTags.stream().map(Tag::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
  }

  /** Every meal's tag names in the household, grouped by meal, for the search results. */
  private Map<Long, List<String>> tagNamesByMeal(long householdId) {
    Map<Long, List<String>> byMeal = new HashMap<>();
    for (MealTagName row : mealTags.findTagNamesOfHousehold(householdId)) {
      byMeal.computeIfAbsent(row.mealId(), id -> new ArrayList<>()).add(row.name());
    }
    return byMeal;
  }

  private void publish(Meal meal) {
    events.publishEvent(new MealChanged(meal.getHouseholdId(), meal.getId()));
  }

  private static MealView view(Meal meal, List<String> tagNames) {
    return new MealView(
        meal.getId(),
        meal.getName(),
        meal.getNotes(),
        meal.getRecipeUrl(),
        tagNames,
        meal.isArchived(),
        meal.getVersion());
  }

  /** The cleaned input of one create or edit. */
  private record Normalized(
      String name, String notes, String recipeUrl, Map<String, String> tags) {}
}
