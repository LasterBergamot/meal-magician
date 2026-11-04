package hu.lasterbergamot.mealmagician.catalog;

import hu.lasterbergamot.mealmagician.catalog.internal.Ingredient;
import hu.lasterbergamot.mealmagician.catalog.internal.IngredientRepository;
import hu.lasterbergamot.mealmagician.catalog.internal.IngredientSeed;
import hu.lasterbergamot.mealmagician.catalog.internal.IngredientSeedRepository;
import hu.lasterbergamot.mealmagician.catalog.internal.Meal;
import hu.lasterbergamot.mealmagician.catalog.internal.MealIngredient;
import hu.lasterbergamot.mealmagician.catalog.internal.MealIngredientName;
import hu.lasterbergamot.mealmagician.catalog.internal.MealIngredientRepository;
import hu.lasterbergamot.mealmagician.catalog.internal.MealOccasionType;
import hu.lasterbergamot.mealmagician.catalog.internal.MealOccasionTypeRepository;
import hu.lasterbergamot.mealmagician.catalog.internal.MealRepository;
import hu.lasterbergamot.mealmagician.catalog.internal.MealTag;
import hu.lasterbergamot.mealmagician.catalog.internal.MealTagName;
import hu.lasterbergamot.mealmagician.catalog.internal.MealTagRepository;
import hu.lasterbergamot.mealmagician.catalog.internal.NameWriter;
import hu.lasterbergamot.mealmagician.catalog.internal.Tag;
import hu.lasterbergamot.mealmagician.catalog.internal.TagRepository;
import hu.lasterbergamot.mealmagician.catalog.internal.TagUse;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The meal library's public entry point: creating, quick-adding, editing, archiving and restoring
 * meals, searching them by name, tag and ingredient, and suggesting ingredients. The catalog
 * module's controllers and the planner call only this class.
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

  /** The most ingredient suggestions one call returns. */
  private static final int SUGGESTIONS_MAX = 50;

  /** The most tag suggestions one call returns; the pool is small and a phone list is short. */
  private static final int TAG_SUGGESTIONS_MAX = 10;

  private final MealRepository meals;
  private final TagRepository tags;
  private final MealTagRepository mealTags;
  private final IngredientRepository ingredients;
  private final MealIngredientRepository mealIngredients;
  private final IngredientSeedRepository ingredientSeeds;
  private final NameWriter names;
  private final MealOccasionTypeRepository occasionTypes;
  private final HouseholdService households;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  /**
   * Creates the service.
   *
   * @param meals persistence for meals
   * @param tags persistence for tags
   * @param mealTags persistence for the meal-tag links
   * @param ingredients persistence for ingredients
   * @param mealIngredients persistence for the meal-ingredient links
   * @param ingredientSeeds read access to the starter ingredient list
   * @param names the race-safe find-or-create for tags and ingredients
   * @param occasionTypes persistence for the occasion types a meal suits
   * @param households the household service, which does the membership check
   * @param events publishes {@link MealChanged}
   * @param clock the application clock, for the creation time
   */
  public MealService(
      MealRepository meals,
      TagRepository tags,
      MealTagRepository mealTags,
      IngredientRepository ingredients,
      MealIngredientRepository mealIngredients,
      IngredientSeedRepository ingredientSeeds,
      NameWriter names,
      MealOccasionTypeRepository occasionTypes,
      HouseholdService households,
      ApplicationEventPublisher events,
      Clock clock) {
    this.meals = meals;
    this.tags = tags;
    this.mealTags = mealTags;
    this.ingredients = ingredients;
    this.mealIngredients = mealIngredients;
    this.ingredientSeeds = ingredientSeeds;
    this.names = names;
    this.occasionTypes = occasionTypes;
    this.households = households;
    this.events = events;
    this.clock = clock;
  }

  /**
   * Adds a meal with its details.
   *
   * @param userId the {@code app_user} id of the person adding it
   * @param householdId the household the meal joins
   * @param details the name, notes, recipe link, tags and ingredients; {@code details} itself must
   *     not be {@code null}, but its fields may be
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
    List<String> tagNames = attachTags(householdId, meal, clean.tags());
    List<String> ingredientNames = attachIngredients(householdId, meal, clean.ingredients());
    List<OccasionType> types = attachOccasionTypes(householdId, meal, clean.occasionTypes());
    publish(meal);
    return view(meal, tagNames, types, ingredientNames);
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
   * Replaces a meal's details, its tag set, its ingredients and its occasion types. The tags are
   * the new complete set: a tag not in the list is removed from the meal, and a new one is created
   * for the household if it does not exist. The ingredients and the occasion types work the same
   * way (a removed ingredient stays in the household's list, only its link to the meal goes).
   *
   * <p>A concurrent edit of the same meal fails with JPA's optimistic-lock exception when the
   * change is written, because the meal's {@code version} has moved on. The page turns that into a
   * "someone else changed this, reload" message. Use {@link #edit(long, long, long, MealDetails,
   * long)} when the edit starts from a form that was opened earlier.
   *
   * @param userId the {@code app_user} id of the person editing
   * @param householdId the household the meal belongs to
   * @param mealId the meal to edit
   * @param details the new name, notes, recipe link, tags, occasion types and ingredients
   * @return the meal after the change
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws MealNotFoundException if the meal does not exist in this household
   * @throws InvalidMealException if a field breaks its rule; nothing is changed then
   * @throws org.springframework.dao.OptimisticLockingFailureException if someone else changed the
   *     meal at the same moment
   */
  public MealView edit(long userId, long householdId, long mealId, MealDetails details) {
    return edit(userId, householdId, mealId, details, null);
  }

  /**
   * Like {@link #edit(long, long, long, MealDetails)}, but refuses the change when the meal is no
   * longer at the version the form was opened with. The transaction's own lock check only catches
   * two edits that overlap in time; this catches the usual case, where someone else saved while the
   * form sat open in a browser tab.
   *
   * @param userId the {@code app_user} id of the person editing
   * @param householdId the household the meal belongs to
   * @param mealId the meal to edit
   * @param details the new name, notes, recipe link, tags, occasion types and ingredients
   * @param expectedVersion the {@link MealView#version()} the form was built from
   * @return the meal after the change
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws MealNotFoundException if the meal does not exist in this household (checked before the
   *     input, so a meal of another household is a 404 whatever was typed)
   * @throws InvalidMealException if a field breaks its rule; nothing is changed then
   * @throws org.springframework.dao.OptimisticLockingFailureException if the meal's version is not
   *     {@code expectedVersion}, or someone else changed it at the same moment
   */
  public MealView edit(
      long userId, long householdId, long mealId, MealDetails details, long expectedVersion) {
    return edit(userId, householdId, mealId, details, Long.valueOf(expectedVersion));
  }

  private MealView edit(
      long userId, long householdId, long mealId, MealDetails details, Long expectedVersion) {
    households.require(householdId, userId);
    // Looked up before the input is checked: another household's meal is a 404 even when the form
    // is also invalid, so a stranger learns nothing from the answer.
    Meal meal = findOwned(householdId, mealId);
    if (expectedVersion != null && meal.getVersion() != expectedVersion) {
      throw new OptimisticLockingFailureException(
          "Meal " + mealId + " is at version " + meal.getVersion() + ", not " + expectedVersion);
    }
    Normalized clean = normalize(details);
    meal.change(clean.name(), clean.notes(), clean.recipeUrl());
    List<String> tagNames = attachTags(householdId, meal, clean.tags());
    List<String> ingredientNames = attachIngredients(householdId, meal, clean.ingredients());
    List<OccasionType> types = attachOccasionTypes(householdId, meal, clean.occasionTypes());
    meals.saveAndFlush(meal);
    publish(meal);
    return view(meal, tagNames, types, ingredientNames);
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
    return viewOf(meal);
  }

  /**
   * Searches the library by name, tag and ingredient, in name order. Archived meals are left out
   * unless the search asks for them.
   *
   * @param userId the {@code app_user} id of the person searching
   * @param householdId the household to search
   * @param search the name text, tag, ingredient and archive flag; {@code search} itself must not
   *     be {@code null}
   * @return the matching meals; empty if nothing matches, or if the tag or ingredient does not
   *     exist in the household
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   */
  @Transactional(readOnly = true)
  public List<MealView> search(long userId, long householdId, MealSearch search) {
    households.require(householdId, userId);
    String pattern = MealRules.namePattern(search.text());
    Long tagId = null;
    if (search.tag() != null && !search.tag().isBlank()) {
      var tag = tags.findByHouseholdIdAndNameKey(householdId, MealRules.tagKey(search.tag()));
      if (tag.isEmpty()) {
        return List.of();
      }
      tagId = tag.get().getId();
    }
    Long ingredientId = null;
    if (search.ingredient() != null && !search.ingredient().isBlank()) {
      var ingredient =
          ingredients.findByHouseholdIdAndNameKey(
              householdId, MealRules.ingredientKey(search.ingredient()));
      if (ingredient.isEmpty()) {
        return List.of();
      }
      ingredientId = ingredient.get().getId();
    }
    Map<Long, List<String>> namesByMeal = tagNamesByMeal(householdId);
    Map<Long, List<String>> ingredientsByMeal = ingredientNamesByMeal(householdId);
    Map<Long, List<OccasionType>> typesByMeal = occasionTypesByMeal(householdId);
    return meals
        .search(householdId, pattern, tagId, ingredientId, search.includeArchived())
        .stream()
        .map(meal ->
            view(
                meal,
                namesByMeal.getOrDefault(meal.getId(), List.of()),
                typesByMeal.getOrDefault(meal.getId(), List.of()),
                ingredientsByMeal.getOrDefault(meal.getId(), List.of())))
        .toList();
  }

  /**
   * Suggestions for the ingredient picker: the household's own ingredients that match what was
   * typed, then entries of the starter list the household does not have yet. Matching ignores case
   * and accents, so "tojas" finds "tojás", and also finds a name that merely contains the text.
   * Names that start with the text come first. With nothing typed, the household's most used
   * ingredients come first and the starter list fills the rest.
   *
   * <p>Only the household's own ingredients are ever returned as {@code known}; another household's
   * list is never read. The starter list is global reference data.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household
   * @param query the text typed so far; {@code null} or blank means nothing typed
   * @param limit the most suggestions to return; values below 1 count as 1 and above 50 as 50
   * @return at most {@code limit} suggestions, the household's first; each ingredient appears once
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   */
  @Transactional(readOnly = true)
  public List<IngredientSuggestion> ingredientSuggestions(
      long userId, long householdId, String query, int limit) {
    households.require(householdId, userId);
    int max = Math.max(1, Math.min(limit, SUGGESTIONS_MAX));
    String key = query == null ? "" : MealRules.ingredientKey(query);
    String pattern = MealRules.keyPattern(key);
    String prefix = MealRules.keyPrefixPattern(key);
    List<Ingredient> own =
        ingredients.suggest(householdId, pattern, prefix, PageRequest.of(0, max));
    List<IngredientSuggestion> result = new ArrayList<>();
    Set<String> ownKeys = new HashSet<>();
    for (Ingredient ingredient : own) {
      ownKeys.add(ingredient.getNameKey());
      result.add(new IngredientSuggestion(ingredient.getName(), true));
    }
    int room = max - result.size();
    if (room > 0) {
      // Fewer than max own matches means the list above is complete, so skipping its keys hides
      // every seed entry the household already has. Ask for extra rows to survive that skipping.
      List<IngredientSeed> seeds =
          ingredientSeeds.search(pattern, prefix, PageRequest.of(0, room + ownKeys.size()));
      seeds.stream()
          .filter(seed -> !ownKeys.contains(seed.getNameKey()))
          .limit(room)
          .forEach(seed -> result.add(new IngredientSuggestion(seed.getName(), false)));
    }
    return result;
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

  /**
   * Suggestions for the Tags field of the meal form: the household's tags that are on at least one
   * meal (the same pool as {@link #tagNames}) whose name contains the typed text, most used first,
   * then alphabetical. Matching ignores case and accents, so "vegan" finds "Vegán". Nothing typed
   * lists the most used tags. The field stays free text: these are only offers, and a tag not
   * listed is created as before when the meal is saved.
   *
   * <p>The pool is read per household, so another household's tags are never returned. Folding
   * happens here and not in SQL because accent folding is not portable between H2 and Postgres, and
   * a household has only a handful of tags.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household
   * @param query the tag text typed so far; {@code null} or blank means nothing typed
   * @return at most 10 tag names in their stored spelling; empty when none matches
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   */
  @Transactional(readOnly = true)
  public List<String> tagSuggestions(long userId, long householdId, String query) {
    households.require(householdId, userId);
    String key = query == null ? "" : MealRules.tagSearchKey(query);
    return tags.findUsage(householdId).stream()
        .filter(use -> MealRules.tagSearchKey(use.name()).contains(key))
        .limit(TAG_SUGGESTIONS_MAX)
        .map(TagUse::name)
        .toList();
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
    return viewOf(meal);
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
        MealRules.tags(details.tags()),
        MealRules.occasionTypes(details.occasionTypes()),
        MealRules.ingredients(details.ingredients()));
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
      wantedTags.add(names.tag(householdId, entry.getKey(), entry.getValue()));
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

  /**
   * Makes the meal carry exactly the wanted ingredients, the same way as {@link #attachTags}:
   * ingredients are created for the household when they do not exist yet (race-safe, through the
   * {@link NameWriter}) and only the links that were added or dropped are written. A dropped
   * ingredient stays in the household's list.
   *
   * @return the ingredient names now on the meal, ordered by their folded key
   */
  private List<String> attachIngredients(long householdId, Meal meal, Map<String, String> wanted) {
    List<Ingredient> wantedIngredients = new ArrayList<>();
    for (Map.Entry<String, String> entry : wanted.entrySet()) {
      wantedIngredients.add(names.ingredient(householdId, entry.getKey(), entry.getValue()));
    }
    Set<Long> wantedIds =
        wantedIngredients.stream().map(Ingredient::getId).collect(Collectors.toSet());
    List<MealIngredient> current = mealIngredients.findLinks(householdId, meal.getId());
    Set<Long> currentIds =
        current.stream().map(link -> link.getId().getIngredientId()).collect(Collectors.toSet());
    mealIngredients.deleteAll(
        current.stream()
            .filter(link -> !wantedIds.contains(link.getId().getIngredientId()))
            .toList());
    mealIngredients.saveAll(
        wantedIngredients.stream()
            .filter(ingredient -> !currentIds.contains(ingredient.getId()))
            .map(ingredient -> new MealIngredient(householdId, meal.getId(), ingredient.getId()))
            .toList());
    return wantedIngredients.stream()
        .sorted(Comparator.comparing(Ingredient::getNameKey))
        .map(Ingredient::getName)
        .toList();
  }

  /**
   * Makes the meal carry exactly the wanted occasion types, writing only the links that were added
   * or dropped (the same difference approach as {@link #attachTags}).
   *
   * @return the types now on the meal, in enum order
   */
  private List<OccasionType> attachOccasionTypes(
      long householdId, Meal meal, Set<OccasionType> wanted) {
    List<MealOccasionType> current = occasionTypes.findLinks(householdId, meal.getId());
    Set<OccasionType> currentTypes =
        current.stream().map(link -> link.getId().getOccasionType()).collect(Collectors.toSet());
    occasionTypes.deleteAll(
        current.stream().filter(link -> !wanted.contains(link.getId().getOccasionType())).toList());
    occasionTypes.saveAll(
        wanted.stream()
            .filter(type -> !currentTypes.contains(type))
            .map(type -> new MealOccasionType(householdId, meal.getId(), type))
            .toList());
    return List.copyOf(wanted);
  }

  /** The occasion types of one stored meal, in enum order. */
  private List<OccasionType> occasionTypesOf(Meal meal) {
    return occasionTypes.findLinks(meal.getHouseholdId(), meal.getId()).stream()
        .map(link -> link.getId().getOccasionType())
        .sorted()
        .toList();
  }

  /** Every meal's occasion types in the household, grouped by meal, for the search results. */
  private Map<Long, List<OccasionType>> occasionTypesByMeal(long householdId) {
    Map<Long, List<OccasionType>> byMeal = new HashMap<>();
    for (MealOccasionType link : occasionTypes.findOfHousehold(householdId)) {
      byMeal
          .computeIfAbsent(link.getId().getMealId(), id -> new ArrayList<>())
          .add(link.getId().getOccasionType());
    }
    byMeal.values().forEach(Collections::sort);
    return byMeal;
  }

  /** Every meal's tag names in the household, grouped by meal, for the search results. */
  private Map<Long, List<String>> tagNamesByMeal(long householdId) {
    Map<Long, List<String>> byMeal = new HashMap<>();
    for (MealTagName row : mealTags.findTagNamesOfHousehold(householdId)) {
      byMeal.computeIfAbsent(row.mealId(), id -> new ArrayList<>()).add(row.name());
    }
    return byMeal;
  }

  /** Every meal's ingredient names in the household, grouped by meal, for the search results. */
  private Map<Long, List<String>> ingredientNamesByMeal(long householdId) {
    Map<Long, List<String>> byMeal = new HashMap<>();
    for (MealIngredientName row : mealIngredients.findIngredientNamesOfHousehold(householdId)) {
      byMeal.computeIfAbsent(row.mealId(), id -> new ArrayList<>()).add(row.name());
    }
    return byMeal;
  }

  private void publish(Meal meal) {
    events.publishEvent(new MealChanged(meal.getHouseholdId(), meal.getId()));
  }

  /** The view of a stored meal, reading its tags, occasion types and ingredients. */
  private MealView viewOf(Meal meal) {
    return view(
        meal,
        mealTags.findTagNames(meal.getHouseholdId(), meal.getId()),
        occasionTypesOf(meal),
        mealIngredients.findIngredientNames(meal.getHouseholdId(), meal.getId()));
  }

  private static MealView view(
      Meal meal,
      List<String> tagNames,
      List<OccasionType> occasionTypes,
      List<String> ingredientNames) {
    return new MealView(
        meal.getId(),
        meal.getName(),
        meal.getNotes(),
        meal.getRecipeUrl(),
        tagNames,
        occasionTypes,
        meal.isArchived(),
        meal.getVersion(),
        ingredientNames);
  }

  /** The cleaned input of one create or edit. */
  private record Normalized(
      String name,
      String notes,
      String recipeUrl,
      Map<String, String> tags,
      Set<OccasionType> occasionTypes,
      Map<String, String> ingredients) {}
}
