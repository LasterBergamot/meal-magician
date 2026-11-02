package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.planning.SlotType;
import hu.lasterbergamot.mealmagician.shared.web.FormOption;
import java.time.LocalDate;
import java.util.List;

/**
 * What the slot editor prints: one planned meal with its note, the choices for moving it and its
 * place among the meals of the same day and slot type.
 *
 * @param slotId the slot's id, which the action addresses build on
 * @param version the slot's version; every form sends it back, so a stale editor is refused
 * @param mealId the planned meal, for the link to the library
 * @param mealName the meal's name
 * @param archived whether the meal has left the library (the plan keeps it)
 * @param date the day the slot is on now
 * @param slotType the slot type it is in now
 * @param dayLabel the translated day, for example {@code Hétfő, nov. 2.}
 * @param position the slot's place in its group, counted from 0
 * @param groupSize how many meals its group has
 * @param dayOptions the seven days of the slot's week, for the move form
 * @param slotOptions the four slot types, for the move form
 * @param note the note as the field shows it; empty when there is none
 * @param noteError the message key of a rejected note, or {@code null}
 */
public record SlotEditor(
    long slotId,
    long version,
    long mealId,
    String mealName,
    boolean archived,
    LocalDate date,
    SlotType slotType,
    String dayLabel,
    int position,
    int groupSize,
    List<FormOption> dayOptions,
    List<FormOption> slotOptions,
    String note,
    String noteError) {

  /**
   * The address a form of this editor posts to.
   *
   * @param verb {@code note}, {@code move}, {@code reorder} or {@code remove}
   * @return for example {@code /plan/slots/7/note}
   */
  public String action(String verb) {
    return "/plan/slots/" + slotId + "/" + verb;
  }

  /**
   * The remove address with the version in the query string. The confirm dialog's button sits
   * outside any form, so the version travels in the URL.
   *
   * @return for example {@code /plan/slots/7/remove?version=3}
   */
  public String removeAction() {
    return action("remove") + "?version=" + version;
  }

  /**
   * The address of the meal in the library.
   *
   * @return for example {@code /meals/12}
   */
  public String mealHref() {
    return "/meals/" + mealId;
  }

  /**
   * The day as the select's selected value.
   *
   * @return for example {@code 2026-11-04}
   */
  public String dateValue() {
    return date.toString();
  }

  /**
   * The slot type as the select's selected value.
   *
   * @return {@code BREAKFAST}, {@code LUNCH}, {@code DINNER} or {@code SNACK}
   */
  public String slotName() {
    return slotType.name();
  }

  /**
   * The message key of the slot type's word.
   *
   * @return for example {@code slot.lunch}
   */
  public String labelKey() {
    return SlotGroup.labelKeyOf(slotType);
  }

  /**
   * Tells whether a meal sits before this one in its group.
   *
   * @return {@code true} when the slot is not the first
   */
  public boolean canMoveUp() {
    return position > 0;
  }

  /**
   * Tells whether a meal sits after this one in its group.
   *
   * @return {@code true} when the slot is not the last
   */
  public boolean canMoveDown() {
    return position < groupSize - 1;
  }

  /**
   * The place one step earlier.
   *
   * @return {@code position - 1}
   */
  public int upPosition() {
    return position - 1;
  }

  /**
   * The place one step later.
   *
   * @return {@code position + 1}
   */
  public int downPosition() {
    return position + 1;
  }

  /**
   * The address of the week page that holds this slot, for the "back" link of the no-JavaScript
   * page.
   *
   * @return for example {@code /plan?week=2026-W45}
   */
  public String weekHref() {
    return WeekPage.hrefOf(IsoWeek.containing(date));
  }

  /**
   * The editor after a refused note: it keeps what the person typed and shows the message. The
   * version is the one the form posted, so a retry is judged against what the person last saw.
   *
   * @param typed the note as typed
   * @param postedVersion the version the rejected form carried
   * @return a copy with the typed note, the {@code plan.editor.note.long} error and that version
   */
  public SlotEditor rejectedNote(String typed, long postedVersion) {
    return new SlotEditor(
        slotId,
        postedVersion,
        mealId,
        mealName,
        archived,
        date,
        slotType,
        dayLabel,
        position,
        groupSize,
        dayOptions,
        slotOptions,
        typed,
        "plan.editor.note.long");
  }
}
