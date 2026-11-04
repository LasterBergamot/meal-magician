package hu.lasterbergamot.mealmagician.assistant.internal;

import hu.lasterbergamot.mealmagician.assistant.DraftItemView;
import hu.lasterbergamot.mealmagician.assistant.DraftView;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Turns the assistant's draft into the view model the sheet draws: days with dates formatted for
 * the member's language, slot chips and the counts of the summary line. Formatting lives here and
 * not in the templates (web-ui.md). It reads nothing from the database.
 */
@Component
class DraftPageFactory {

  /**
   * Builds the page of a draft.
   *
   * @param draft the draft from {@code AssistantService}
   * @param locale the member's language, for the dates
   * @param justAddedItemId the idea the member has just added to the library, or {@code -1}
   * @param refineText what is in the follow-up box
   * @param refineHints the ticked chips of the follow-up form
   * @param refineError the follow-up field's error message key, or {@code null}
   * @param notice an alert for the follow-up area, or {@code null}
   * @return the page
   */
  DraftPage page(
      DraftView draft,
      Locale locale,
      long justAddedItemId,
      String refineText,
      List<String> refineHints,
      String refineError,
      ErrorNotice notice) {
    DateTimeFormatter dayFormat = DateTimeFormatter.ofPattern("EEEE, MMM d", locale);
    Map<LocalDate, List<DraftPage.Item>> byDay = new LinkedHashMap<>();
    int library = 0;
    int ideas = 0;
    for (DraftItemView item : draft.items()) {
      if (item.idea()) {
        ideas++;
      } else {
        library++;
      }
      byDay
          .computeIfAbsent(item.date(), day -> new ArrayList<>())
          .add(
              new DraftPage.Item(
                  item.id(),
                  item.idea(),
                  item.slotType().name(),
                  slotLabelKey(item.slotType()),
                  item.idea() ? item.ideaName() : item.mealName(),
                  item.ideaReason(),
                  item.ideaIngredients().isEmpty()
                      ? null
                      : String.join(", ", item.ideaIngredients()),
                  item.id() == justAddedItemId));
    }
    List<DraftPage.Day> days = new ArrayList<>();
    byDay.forEach((date, items) ->
        days.add(new DraftPage.Day("draft-day-" + date, dayFormat.format(date), items)));
    return new DraftPage(
        draft.id(),
        WeekCodes.codeOf(draft.weekStart()),
        range(draft.weekStart(), locale),
        draft.summary(),
        library,
        ideas,
        days,
        refineText,
        refineHints,
        refineError,
        notice);
  }

  /**
   * A week's dates for a heading, for example {@code 2026. nov. 2. – 2026. nov. 8.}.
   *
   * @param monday the week's Monday
   * @param locale the member's language
   * @return the first and last day, formatted
   */
  static String range(LocalDate monday, Locale locale) {
    DateTimeFormatter format =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale);
    return format.format(monday) + " – " + format.format(monday.plusDays(6));
  }

  /**
   * The message key of a slot type's word.
   *
   * @param type the slot type
   * @return for example {@code slot.dinner}
   */
  static String slotLabelKey(SlotType type) {
    return "slot." + type.name().toLowerCase(Locale.ROOT);
  }
}
