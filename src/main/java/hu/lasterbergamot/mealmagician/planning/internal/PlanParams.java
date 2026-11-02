package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.planning.SlotType;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Strict parsing of the request parameters of the planner's edit routes. A date, a slot type or an
 * id that is missing or malformed is a client mistake, so each method answers with {@link #bad()}
 * (HTTP 400) rather than guessing or leaking a stack trace.
 *
 * <p>The controllers take these values as plain strings and parse them here, <em>after</em> the
 * household-scoped slot has been read. That order matters: the tenant guard sends no parameters, so
 * a person from another household must meet the 404 of the slot lookup before any 400.
 */
final class PlanParams {

  private PlanParams() {}

  /**
   * Parses a day written as {@code YYYY-MM-DD}.
   *
   * @param raw the request parameter, possibly {@code null}
   * @return the day
   * @throws ResponseStatusException with status 400 if it is missing or not a real date
   */
  static LocalDate date(String raw) {
    if (raw == null) {
      throw bad();
    }
    try {
      return LocalDate.parse(raw);
    } catch (DateTimeParseException notADate) {
      throw bad();
    }
  }

  /**
   * Parses a slot type by its enum name, for example {@code LUNCH}.
   *
   * @param raw the request parameter, possibly {@code null}
   * @return the slot type
   * @throws ResponseStatusException with status 400 if it is missing or names no slot type
   */
  static SlotType slotType(String raw) {
    if (raw == null) {
      throw bad();
    }
    try {
      return SlotType.valueOf(raw);
    } catch (IllegalArgumentException unknown) {
      throw bad();
    }
  }

  /**
   * Parses an id.
   *
   * @param raw the request parameter, possibly {@code null}
   * @return the number
   * @throws ResponseStatusException with status 400 if it is missing or not a whole number
   */
  static long id(String raw) {
    if (raw == null) {
      throw bad();
    }
    try {
      return Long.parseLong(raw);
    } catch (NumberFormatException notANumber) {
      throw bad();
    }
  }

  /**
   * The exception every parser throws.
   *
   * @return a 400 Bad Request
   */
  static ResponseStatusException bad() {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST);
  }
}
