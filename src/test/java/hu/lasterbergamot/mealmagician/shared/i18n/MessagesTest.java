package hu.lasterbergamot.mealmagician.shared.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;

class MessagesTest {

  private final I18nConfig config = new I18nConfig();
  private final Messages messages = config.messages(config.messageSource());

  @AfterEach
  void resetLocale() {
    LocaleContextHolder.resetLocaleContext();
  }

  @Test
  void translatesIntoHungarianWhenTheRequestIsHungarian() {
    LocaleContextHolder.setLocale(Locale.of("hu"));

    assertThat(messages.t("nav.skip")).isEqualTo("Ugrás a tartalomra");
    assertThat(messages.language()).isEqualTo("hu");
  }

  @Test
  void translatesIntoEnglishWhenTheRequestIsEnglish() {
    LocaleContextHolder.setLocale(Locale.UK);

    assertThat(messages.t("nav.skip")).isEqualTo("Skip to content");
    assertThat(messages.language()).isEqualTo("en");
    assertThat(messages.locale()).isEqualTo(SupportedLocales.ENGLISH);
  }

  @Test
  void fillsPlaceholders() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);

    assertThat(messages.t("plan.week.title", "45")).isEqualTo("Week 45");
  }

  @Test
  void fallsBackToHungarianForUnsupportedLocales() {
    LocaleContextHolder.setLocale(Locale.GERMAN);

    assertThat(messages.t("nav.skip")).isEqualTo("Ugrás a tartalomra");
  }

  @Test
  void failsLoudlyForAMissingKey() {
    assertThatThrownBy(() -> messages.t("no.such.key")).isInstanceOf(NoSuchMessageException.class);
  }

  @Test
  void parsesOnlySupportedLanguageCodes() {
    assertThat(SupportedLocales.parse("hu")).contains(SupportedLocales.HUNGARIAN);
    assertThat(SupportedLocales.parse("en")).contains(SupportedLocales.ENGLISH);
    assertThat(SupportedLocales.parse("de")).isEmpty();
    assertThat(SupportedLocales.parse(null)).isEmpty();
  }
}
