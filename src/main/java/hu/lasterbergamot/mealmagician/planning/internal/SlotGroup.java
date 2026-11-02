package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.planning.SlotType;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * The meals planned for one slot type on one day: one box on the week page. A box with no meals is
 * still a group, because the page draws it as an empty slot.
 *
 * @param date the day
 * @param type the slot type
 * @param meals the planned meals in their order; empty when nothing is planned
 */
public record SlotGroup(LocalDate date, SlotType type, List<PlannedMeal> meals) {

  /**
   * The message key of a slot type's word.
   *
   * @param type the slot type
   * @return for example {@code slot.snack}
   */
  public static String labelKeyOf(SlotType type) {
    return "slot." + type.name().toLowerCase(Locale.ROOT);
  }

  /**
   * The message key of this group's slot type.
   *
   * @return for example {@code slot.lunch}, to pass through {@code ctx.t}
   */
  public String labelKey() {
    return labelKeyOf(type);
  }

  /**
   * The slot type's name, which the {@code ui/_slot_chip} component takes.
   *
   * @return {@code BREAKFAST}, {@code LUNCH}, {@code DINNER} or {@code SNACK}
   */
  public String slotName() {
    return type.name();
  }

  /**
   * The message key of this group's "add" control.
   *
   * @return for example {@code plan.slot.add.lunch}
   */
  public String addKey() {
    return "plan.slot.add." + type.name().toLowerCase(Locale.ROOT);
  }

  /**
   * The address that loads the pick sheet for this day and slot type. Without JavaScript it is a
   * link to a page of its own.
   *
   * @return for example {@code /plan/pick?date=2026-11-02&slot=LUNCH}
   */
  public String pickHref() {
    return "/plan/pick?date=" + date + "&slot=" + type.name();
  }

  /**
   * Tells whether nothing is planned here.
   *
   * @return {@code true} when there are no meals
   */
  public boolean empty() {
    return meals.isEmpty();
  }

  /**
   * A stable key for the box, written to {@code data-group} so tests can find it.
   *
   * @return the date and slot type, for example {@code 2026-11-02/LUNCH}
   */
  public String key() {
    return date + "/" + type.name();
  }
}
