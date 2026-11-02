package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.planning.CopyMode;
import hu.lasterbergamot.mealmagician.planning.PlanConflictException;
import hu.lasterbergamot.mealmagician.planning.SlotPlanned;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * The one place that writes copied slots, used by copying a day or week and by applying a plan
 * template, so "merge" and "replace" mean the same thing everywhere (PLAN.md D26). It has no
 * transaction of its own: it joins the caller's, so a failed replace leaves nothing half-written.
 * It does not check membership; the calling service already did.
 */
@Component
public class SlotWriter {

  private final MealSlotRepository slots;
  private final ApplicationEventPublisher events;

  /**
   * Creates the writer.
   *
   * @param slots persistence for planned slots
   * @param events publishes {@link SlotPlanned}
   */
  public SlotWriter(MealSlotRepository slots, ApplicationEventPublisher events) {
    this.slots = slots;
    this.events = events;
  }

  /**
   * Writes the seeds as new slots and publishes one {@link SlotPlanned} per new slot.
   *
   * <p>{@link CopyMode#MERGE} appends each seed after the meals already in the same date and slot
   * type, keeping the order of the list. {@link CopyMode#REPLACE} first deletes every slot in the
   * whole range {@code first} to {@code last}, including slot types the seeds have nothing in, and
   * then writes the seeds from position 0. The delete is flushed at once, so a slot that someone
   * else changed meanwhile surfaces here as a conflict and not at commit.
   *
   * <p>The caller must pass a non-empty list in source order (the services refuse an empty source
   * before calling), and every seed's date must lie inside the range.
   *
   * @param householdId the household whose plan is written
   * @param first the first date of the target range, inclusive
   * @param last the last date of the target range, inclusive
   * @param seeds the slots to write, in source order
   * @param mode merge or replace
   * @return how many slots were written, which is {@code seeds.size()}
   * @throws PlanConflictException if a replace finds that a slot of the range changed meanwhile
   */
  public int write(
      long householdId, LocalDate first, LocalDate last, List<SlotSeed> seeds, CopyMode mode) {
    if (mode == CopyMode.REPLACE) {
      slots.deleteAll(slots.findByHouseholdIdAndSlotDateBetween(householdId, first, last));
      try {
        slots.flush();
      } catch (OptimisticLockingFailureException clash) {
        throw new PlanConflictException(first, last);
      }
    }
    Map<String, Integer> next = new HashMap<>();
    for (SlotSeed seed : seeds) {
      String key = seed.date() + "/" + seed.slotType();
      int position =
          next.computeIfAbsent(
              key,
              k ->
                  slots
                      .findByHouseholdIdAndSlotDateAndSlotTypeOrderByPositionAscIdAsc(
                          householdId, seed.date(), seed.slotType())
                      .size());
      MealSlot saved =
          slots.save(
              new MealSlot(
                  householdId, seed.date(), seed.slotType(), position, seed.mealId(), seed.note()));
      next.put(key, position + 1);
      events.publishEvent(
          new SlotPlanned(householdId, saved.getId(), seed.date(), seed.slotType(), seed.mealId()));
    }
    return seeds.size();
  }
}
