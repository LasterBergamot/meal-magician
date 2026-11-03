package hu.lasterbergamot.mealmagician.sharing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import hu.lasterbergamot.mealmagician.sharing.ShareExpiry;
import hu.lasterbergamot.mealmagician.sharing.ShareLinkStatus;
import hu.lasterbergamot.mealmagician.sharing.ShareLinkView;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The small models of the share sheet: row mapping, panel states, the link address, week codes. */
class ShareModelsTest {

  private static final ZoneId ZONE = ZoneId.of("Europe/Budapest");
  private static final Instant CREATED = Instant.parse("2026-11-02T10:00:00Z");
  private static final Instant EXPIRES = Instant.parse("2026-11-09T10:30:00Z");
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-04T09:00:00Z"), ZoneOffset.UTC);

  private static ShareLinkView view(ShareLinkStatus status, Long createdBy) {
    return new ShareLinkView(7, LocalDate.of(2026, 11, 2), status, CREATED, EXPIRES, createdBy);
  }

  @Test
  void anActiveLinkIsASuccessBadgeThatCanBeRevoked() {
    ShareRow row = ShareRow.of(view(ShareLinkStatus.ACTIVE, 1L), Map.of(1L, "Anna"), ZONE);

    assertThat(row.id()).isEqualTo(7);
    assertThat(row.statusKey()).isEqualTo("share.status.active");
    assertThat(row.tone()).isEqualTo("success");
    assertThat(row.revocable()).isTrue();
    assertThat(row.createdBy()).isEqualTo("Anna");
  }

  @Test
  void anExpiredLinkIsNeutralAndCannotBeRevoked() {
    ShareRow row = ShareRow.of(view(ShareLinkStatus.EXPIRED, 1L), Map.of(1L, "Anna"), ZONE);

    assertThat(row.statusKey()).isEqualTo("share.status.expired");
    assertThat(row.tone()).isEqualTo("neutral");
    assertThat(row.revocable()).isFalse();
  }

  @Test
  void aRevokedLinkIsADangerBadgeAndCannotBeRevokedAgain() {
    ShareRow row = ShareRow.of(view(ShareLinkStatus.REVOKED, 1L), Map.of(1L, "Anna"), ZONE);

    assertThat(row.statusKey()).isEqualTo("share.status.revoked");
    assertThat(row.tone()).isEqualTo("danger");
    assertThat(row.revocable()).isFalse();
  }

  @Test
  void theExpiryIsShownInTheGivenTimeZone() {
    ShareRow row = ShareRow.of(view(ShareLinkStatus.ACTIVE, 1L), Map.of(1L, "Anna"), ZONE);

    assertThat(row.expires()).isEqualTo("2026-11-09 11:30");
  }

  @Test
  void aCreatorWhoIsNoLongerAMemberHasNoName() {
    assertThat(ShareRow.of(view(ShareLinkStatus.ACTIVE, 2L), Map.of(1L, "Anna"), ZONE).createdBy())
        .isNull();
    assertThat(
            ShareRow.of(view(ShareLinkStatus.ACTIVE, null), Map.of(1L, "Anna"), ZONE).createdBy())
        .isNull();
  }

  @Test
  void aCreatedPanelCarriesTheLinkAndNoError() {
    SharePanel panel =
        new SharePanel("2026-W45", "Week 45", "range", List.of(), "30d", "share.limit", null);

    SharePanel created = panel.created("https://x/share/abc");

    assertThat(created.link()).isEqualTo("https://x/share/abc");
    assertThat(created.error()).isNull();
    assertThat(created.expiry()).isEqualTo("30d");
  }

  @Test
  void aRejectedPanelCarriesTheMessageTheExpiryAndNoLink() {
    SharePanel panel =
        new SharePanel(
            "2026-W45", "Week 45", "range", List.of(), "7d", null, "https://x/share/abc");

    SharePanel rejected = panel.rejected("share.limit", "1d");

    assertThat(rejected.link()).isNull();
    assertThat(rejected.error()).isEqualTo("share.limit");
    assertThat(rejected.expiry()).isEqualTo("1d");
  }

  @Test
  void theLinkAddressIsTheBasePlusTheEncodedTokenAsOnePathSegment() {
    assertThat(SharePanel.linkFor("https://meal.example", "abc-DEF_123"))
        .isEqualTo("https://meal.example/share/abc-DEF_123");
    assertThat(SharePanel.linkFor("https://meal.example", "a/b c"))
        .isEqualTo("https://meal.example/share/a%2Fb%20c");
  }

  @Test
  void aWeekCodeIsReadBackToItsMonday() {
    assertThat(ShareForm.mondayOf("2026-W45", CLOCK)).isEqualTo(LocalDate.of(2026, 11, 2));
    assertThat(ShareForm.mondayOf("2026-W53", CLOCK)).isEqualTo(LocalDate.of(2026, 12, 28));
    assertThat(ShareForm.mondayOf("2026-W01", CLOCK)).isEqualTo(LocalDate.of(2025, 12, 29));
  }

  @Test
  void aMissingMalformedOrImpossibleWeekIsTheCurrentWeek() {
    LocalDate current = LocalDate.of(2026, 11, 2);

    assertThat(ShareForm.mondayOf(null, CLOCK)).isEqualTo(current);
    assertThat(ShareForm.mondayOf("bad", CLOCK)).isEqualTo(current);
    assertThat(ShareForm.mondayOf("2026-W5", CLOCK)).isEqualTo(current);
    assertThat(ShareForm.mondayOf("2026-W00", CLOCK)).isEqualTo(current);
    assertThat(ShareForm.mondayOf("2021-W53", CLOCK)).isEqualTo(current);
    assertThat(ShareForm.mondayOf("2026-W54", CLOCK)).isEqualTo(current);
  }

  @Test
  void theFormReadsTheExpiryAndDefaultsToSevenDays() {
    assertThat(ShareForm.of("2026-W45", "30d", CLOCK).expiry()).isEqualTo(ShareExpiry.DAYS_30);
    assertThat(ShareForm.of("2026-W45", "1d", CLOCK).expiry()).isEqualTo(ShareExpiry.DAY_1);
    assertThat(ShareForm.of("2026-W45", "nope", CLOCK).expiry()).isEqualTo(ShareExpiry.DAYS_7);
    assertThat(ShareForm.of("2026-W45", null, CLOCK).expiry()).isEqualTo(ShareExpiry.DAYS_7);
  }

  @Test
  void aMondayHasACodeAndANumberInItsWeekBasedYear() {
    assertThat(ShareForm.codeOf(LocalDate.of(2026, 11, 2))).isEqualTo("2026-W45");
    assertThat(ShareForm.codeOf(LocalDate.of(2025, 12, 29))).isEqualTo("2026-W01");
    assertThat(ShareForm.numberOf(LocalDate.of(2025, 12, 29))).isEqualTo(1);
    assertThat(ShareForm.numberOf(LocalDate.of(2026, 11, 2))).isEqualTo(45);
  }
}
