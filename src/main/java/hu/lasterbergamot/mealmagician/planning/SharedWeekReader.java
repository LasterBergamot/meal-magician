package hu.lasterbergamot.mealmagician.planning;

import hu.lasterbergamot.mealmagician.catalog.MealNameLookup;
import hu.lasterbergamot.mealmagician.planning.internal.MealSlot;
import hu.lasterbergamot.mealmagician.planning.internal.MealSlotRepository;
import hu.lasterbergamot.mealmagician.planning.internal.OccasionRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An <strong>unchecked reader</strong> for the public share-link page (PLAN.md D28). It reads one
 * week of a household's plan without asking who is calling. That breaks the usual rule that every
 * public service method checks household membership, so it is kept deliberately narrow and an
 * ArchUnit test ({@code UncheckedReadersTest}) lets only the {@code sharing} module use it.
 *
 * <p>Who may call it: only {@code sharing.ShareLinkService}, and only with a household id and week
 * read from a share link that is active. Who is the caller on the public route? Nobody: the token
 * is the proof. Why it exists: {@link PlanningService#week} needs a signed-in user.
 *
 * <p>What it returns is limited by type, not by care: {@link SharedWeek} has no ids, notes or
 * household fields. It is read-only and writes nothing.
 */
@Service
@Transactional(readOnly = true)
public class SharedWeekReader {

  private static final Comparator<MealSlot> SLOT_ORDER =
      Comparator.comparing(MealSlot::getSlotDate)
          .thenComparing(MealSlot::getSlotType)
          .thenComparingInt(MealSlot::getPosition)
          .thenComparing(MealSlot::getId);

  private final MealSlotRepository slots;
  private final OccasionRepository occasions;
  private final MealNameLookup mealNames;

  /**
   * Creates the reader.
   *
   * @param slots persistence for planned slots
   * @param occasions persistence for occasions
   * @param mealNames the catalog's unchecked meal-name reader
   */
  public SharedWeekReader(
      MealSlotRepository slots, OccasionRepository occasions, MealNameLookup mealNames) {
    this.slots = slots;
    this.occasions = occasions;
    this.mealNames = mealNames;
  }

  /**
   * Reads the seven days from {@code weekStart} for one household. Does <em>no</em> membership
   * check.
   *
   * @param householdId the household whose plan to read, taken from a stored share link
   * @param weekStart the Monday of the week
   * @return the meals (by date, slot type, position, id) and the occasions (by date) of the week;
   *     both lists are empty when nothing is planned
   */
  public SharedWeek read(long householdId, LocalDate weekStart) {
    LocalDate sunday = weekStart.plusDays(6);
    List<MealSlot> week =
        slots.findByHouseholdIdAndSlotDateBetween(householdId, weekStart, sunday).stream()
            .sorted(SLOT_ORDER)
            .toList();
    Set<Long> mealIds = week.stream().map(MealSlot::getMealId).collect(Collectors.toSet());
    Map<Long, String> names = mealNames.names(householdId, mealIds);
    List<SharedMeal> meals =
        week.stream()
            .map(slot ->
                new SharedMeal(
                    slot.getSlotDate(),
                    slot.getSlotType(),
                    slot.getPosition(),
                    names.get(slot.getMealId())))
            .toList();
    List<SharedOccasion> sharedOccasions =
        occasions
            .findByHouseholdIdAndOccasionDateBetweenOrderByOccasionDateAscIdAsc(
                householdId, weekStart, sunday)
            .stream()
            .map(o -> new SharedOccasion(o.getOccasionDate(), o.getName(), o.getType()))
            .toList();
    return new SharedWeek(weekStart, meals, sharedOccasions);
  }
}
