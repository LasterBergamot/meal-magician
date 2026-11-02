package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import hu.lasterbergamot.mealmagician.planning.OccasionDetails;
import hu.lasterbergamot.mealmagician.planning.OccasionView;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * The values of the occasion form exactly as the person typed them, so a rejected form (422) is
 * drawn again with nothing lost. The date and the kind stay text here: a value that is not a date
 * or a kind is not an exception, it becomes {@code null} in {@link #details()} and the service then
 * refuses it as a field error.
 *
 * @param id the occasion being edited, or {@code null} for a new one
 * @param version the occasion's version when the form was opened; {@code 0} for a new one
 * @param name the typed name
 * @param date the typed date, as {@code YYYY-MM-DD}
 * @param type the typed kind, as the enum name ({@code BIRTHDAY}, {@code HOLIDAY}, {@code PARTY} or
 *     {@code OTHER})
 * @param notes the typed notes
 */
public record OccasionForm(
    Long id, long version, String name, String date, String type, String notes) {

  /**
   * A blank form for a new occasion. The kind starts at birthday, the first choice of the list.
   *
   * @return the empty form
   */
  public static OccasionForm blank() {
    return new OccasionForm(null, 0, "", "", OccasionType.BIRTHDAY.name(), "");
  }

  /**
   * The form for editing a stored occasion.
   *
   * @param occasion the occasion as the service returns it
   * @return the form filled with its values; a missing note becomes an empty text
   */
  public static OccasionForm of(OccasionView occasion) {
    return new OccasionForm(
        occasion.id(),
        occasion.version(),
        occasion.name(),
        occasion.date().toString(),
        occasion.type().name(),
        occasion.notes() == null ? "" : occasion.notes());
  }

  /**
   * Whether the form edits a stored occasion.
   *
   * @return {@code false} for a new occasion
   */
  public boolean editing() {
    return id != null;
  }

  /**
   * Where the form posts to: the occasion's own address when editing, the list for a new one.
   *
   * @return {@code /occasions/{id}} or {@code /occasions}
   */
  public String action() {
    return editing() ? "/occasions/" + id : "/occasions";
  }

  /**
   * Where Cancel and the back link go: the occasion's page when editing, the list for a new one.
   *
   * @return {@code /occasions/{id}} or {@code /occasions}
   */
  public String backHref() {
    return action();
  }

  /**
   * The values to hand to {@code OccasionService}. A date or kind that does not parse becomes
   * {@code null}, which the service refuses with a field error, so a typed-in URL or a hand-edited
   * form cannot cause a server error page.
   *
   * @return the details, not yet validated
   */
  public OccasionDetails details() {
    return new OccasionDetails(name, parseDate(date), parseType(type), notes);
  }

  /**
   * Reads the typed date. The browser's date picker sends {@code YYYY-MM-DD}; a blank or malformed
   * text is {@code null} so the service can report it under the date field.
   *
   * @param raw the typed text, possibly {@code null}
   * @return the day, or {@code null} when nothing usable was typed
   */
  public static LocalDate parseDate(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(raw);
    } catch (DateTimeParseException notADate) {
      return null;
    }
  }

  /**
   * Reads the typed kind by its enum name. Anything else is {@code null}, which the service refuses
   * under the kind field.
   *
   * @param raw the typed text, possibly {@code null}
   * @return the kind, or {@code null} when it names none
   */
  public static OccasionType parseType(String raw) {
    if (raw == null) {
      return null;
    }
    try {
      return OccasionType.valueOf(raw);
    } catch (IllegalArgumentException unknown) {
      return null;
    }
  }
}
