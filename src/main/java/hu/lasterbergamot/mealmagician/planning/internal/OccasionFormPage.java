package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.planning.InvalidPlanException;
import hu.lasterbergamot.mealmagician.shared.web.FormOption;
import java.util.List;

/**
 * The model of the occasion form page and fragment: the typed values, the choices of the kind list,
 * the one field that is wrong (if any) with its message key, and whether the form is stale.
 *
 * <p>Only one field can be wrong at a time, because the service stops at the first broken rule.
 *
 * @param form what the person typed, or the stored values
 * @param typeOptions the kinds to choose from, labelled from the catalog's words
 * @param errorField the field that was refused ({@code name}, {@code date}, {@code type} or {@code
 *     notes}), or {@code null} when none was
 * @param errorKey the message key to show under that field, or {@code null}
 * @param conflict whether someone else changed the occasion after the form was opened (HTTP 409)
 */
public record OccasionFormPage(
    OccasionForm form,
    List<FormOption> typeOptions,
    String errorField,
    String errorKey,
    boolean conflict) {

  /**
   * A form without errors.
   *
   * @param form the values to show
   * @param typeOptions the kinds to choose from
   * @return the page model
   */
  public static OccasionFormPage of(OccasionForm form, List<FormOption> typeOptions) {
    return new OccasionFormPage(form, typeOptions, null, null, false);
  }

  /**
   * The form drawn again because the service refused a field. The message key is chosen from the
   * refusal's problem, so the words stay in the message bundles.
   *
   * @param form what the person typed
   * @param typeOptions the kinds to choose from
   * @param refused the refusal, which names the broken rule
   * @return the page model with the message key under the offending field
   */
  public static OccasionFormPage rejected(
      OccasionForm form, List<FormOption> typeOptions, InvalidPlanException refused) {
    return switch (refused.problem()) {
      case NAME_REQUIRED -> with(form, typeOptions, "name", "occasion.error.name.required");
      case NAME_TOO_LONG -> with(form, typeOptions, "name", "occasion.error.name.long");
      case DATE_REQUIRED -> with(form, typeOptions, "date", "occasion.error.date");
      case TYPE_REQUIRED -> with(form, typeOptions, "type", "occasion.error.type");
      case NOTES_TOO_LONG -> with(form, typeOptions, "notes", "occasion.error.notes.long");
      // The other problems belong to meal plans and templates, so they never reach this form.
      // The name is the safest field to point at if one ever does.
      default -> with(form, typeOptions, "name", "occasion.error.name.required");
    };
  }

  /**
   * The form drawn again because it was opened before someone else changed the occasion.
   *
   * @param form what the person typed
   * @param typeOptions the kinds to choose from
   * @return the page model with the conflict warning and no field error
   */
  public static OccasionFormPage stale(OccasionForm form, List<FormOption> typeOptions) {
    return new OccasionFormPage(form, typeOptions, null, null, true);
  }

  /**
   * The message key shown under a field.
   *
   * @param field {@code name}, {@code date}, {@code type} or {@code notes}
   * @return the key to pass to {@code ctx.t}, or {@code null} if this field has no error
   */
  public String error(String field) {
    return field.equals(errorField) ? errorKey : null;
  }

  /**
   * The heading of the page: a new occasion or editing one.
   *
   * @return the message key for the title and the h1
   */
  public String titleKey() {
    return form.editing() ? "occasion.form.title.edit" : "occasion.form.title.new";
  }

  private static OccasionFormPage with(
      OccasionForm form, List<FormOption> typeOptions, String field, String key) {
    return new OccasionFormPage(form, typeOptions, field, key, false);
  }
}
