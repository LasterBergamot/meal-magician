package hu.lasterbergamot.mealmagician.planning;

import static hu.lasterbergamot.mealmagician.household.HouseholdTestSupport.signedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.lasterbergamot.mealmagician.catalog.MealDetails;
import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.MealView;
import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Occasions pages through the whole stack (security, controllers, database, templates), as
 * issue #112 describes them. Anna owns "Annas"; Bela owns "Belas" and must never see Anna's
 * occasions (PLAN.md D8); the stranger is in no household. Texts are the Hungarian ones, the app's
 * default language. "Today" is the day of the {@link Clock} bean.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class OccasionPagesTest {

  @Autowired private MockMvc mvc;
  @Autowired private HouseholdService households;
  @Autowired private OccasionService occasions;
  @Autowired private MealService meals;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private Clock clock;

  private long anna;
  private long bela;
  private long stranger;
  private long annasHome;
  private long belasHome;

  @BeforeEach
  void people() {
    anna = HouseholdTestSupport.insertUser(jdbc, "anna");
    bela = HouseholdTestSupport.insertUser(jdbc, "bela");
    stranger = HouseholdTestSupport.insertUser(jdbc, "stranger");
    annasHome = households.create(anna, "Annas").id();
    belasHome = households.create(bela, "Belas").id();
  }

  private ResultActions asAnna(MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.with(signedInAs(anna)).with(csrf()));
  }

  private MockHttpServletRequestBuilder htmx(MockHttpServletRequestBuilder request) {
    return request.header("HX-Request", "true");
  }

  private OccasionView occasion(String name, LocalDate date) {
    return occasions.create(
        anna, annasHome, new OccasionDetails(name, date, OccasionType.BIRTHDAY, null));
  }

  private LocalDate today() {
    return LocalDate.now(clock);
  }

  // ---- the list ----

  @Test
  void anEmptyListInvitesTheFirstOccasion() throws Exception {
    asAnna(get("/occasions"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Még nincs alkalom")))
        .andExpect(content().string(containsString("href=\"/occasions/new\"")))
        .andExpect(content().string(containsString("aria-current=\"page\"")));
  }

  @Test
  void theNextOccasionIsTheCardAndTheLaterAndPastOnesAreRowsInOrder() throws Exception {
    OccasionView past = occasion("Régi buli", today().minusDays(9));
    OccasionView todays = occasion("Mai torta", today());
    OccasionView later = occasion("Havi kávé", today().plusMonths(1));
    OccasionView soon = occasion("Heti piknik", today().plusDays(7));

    String body =
        asAnna(get("/occasions"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Ma")))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Today is the next one, in the gold card, with the Today badge.
    assertThat(body.indexOf("Mai torta")).isGreaterThan(body.indexOf("A következő alkalom"));
    assertThat(body.indexOf("Mai torta")).isLessThan(body.indexOf("Később"));
    assertThat(body).contains("data-occasion-id=\"" + todays.id() + "\"");
    assertThat(body).contains("bg-accent-soft");
    // The later ones follow in date order, under Később.
    assertThat(body.indexOf("Heti piknik")).isGreaterThan(body.indexOf("Később"));
    assertThat(body.indexOf("Havi kávé")).isGreaterThan(body.indexOf("Heti piknik"));
    assertThat(body).contains("data-occasion-id=\"" + soon.id() + "\"");
    assertThat(body).contains("data-occasion-id=\"" + later.id() + "\"");
    // The past one is under Elmúlt alkalmak.
    assertThat(body.indexOf("Elmúlt alkalmak")).isLessThan(body.indexOf("Régi buli"));
    assertThat(body).contains("data-occasion-id=\"" + past.id() + "\"");
  }

  @Test
  void pastOccasionsAreListedNewestFirst() throws Exception {
    occasion("Régi", today().minusDays(30));
    occasion("Tegnapi", today().minusDays(1));

    String body = asAnna(get("/occasions")).andReturn().getResponse().getContentAsString();

    assertThat(body.indexOf("Tegnapi")).isLessThan(body.indexOf("Régi"));
  }

  @Test
  void anotherHouseholdsOccasionsAreNotListed() throws Exception {
    occasions.create(
        bela, belasHome, new OccasionDetails("Belas titok", today(), OccasionType.PARTY, null));

    asAnna(get("/occasions"))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("Belas titok"))));
  }

  @Test
  void aStrangerInNoHouseholdGetsNotFound() throws Exception {
    mvc.perform(get("/occasions").with(signedInAs(stranger))).andExpect(status().isNotFound());
  }

  // ---- create ----

  @Test
  void theFormOffersTheFourKindsAndADateField() throws Exception {
    String body =
        asAnna(htmx(get("/occasions/new")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"occasion-form\"")))
            .andExpect(content().string(containsString("type=\"date\"")))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(countOf(body, "<option")).isEqualTo(4);
  }

  @Test
  void aValidFormCreatesTheOccasionAndRedirectsToItsPage() throws Exception {
    asAnna(
            htmx(
                post("/occasions")
                    .param("name", "Szülinap")
                    .param("date", today().plusDays(10).toString())
                    .param("type", "PARTY")))
        .andExpect(status().isOk())
        .andExpect(header().string("HX-Redirect", containsString("/occasions/")));

    List<OccasionSummary> listed = occasions.list(anna, annasHome);
    assertThat(listed).hasSize(1);
    assertThat(listed.get(0).name()).isEqualTo("Szülinap");
    assertThat(listed.get(0).type()).isEqualTo(OccasionType.PARTY);
  }

  @Test
  void aBlankNameIsRefusedUnderTheNameField() throws Exception {
    asAnna(
            htmx(
                post("/occasions")
                    .param("name", "")
                    .param("date", "2026-12-24")
                    .param("type", "PARTY")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().string(containsString("Adj nevet az alkalomnak.")));
  }

  @Test
  void aMissingDateIsRefusedUnderTheDateFieldAndTheTypedNameIsKept() throws Exception {
    asAnna(
            htmx(
                post("/occasions")
                    .param("name", "Szenteste")
                    .param("date", "")
                    .param("type", "HOLIDAY")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().string(containsString("Válassz dátumot.")))
        .andExpect(content().string(containsString("value=\"Szenteste\"")));
  }

  @Test
  void aMalformedDateIsRefusedUnderTheDateFieldNotAServerError() throws Exception {
    asAnna(htmx(post("/occasions").param("name", "A").param("date", "x").param("type", "HOLIDAY")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().string(containsString("Válassz dátumot.")));
  }

  @Test
  void anUnknownKindIsRefusedUnderTheKindField() throws Exception {
    asAnna(
            htmx(
                post("/occasions")
                    .param("name", "A")
                    .param("date", "2026-12-24")
                    .param("type", "FOO")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().string(containsString("Válaszd ki, milyen alkalom ez.")));
  }

  @Test
  void tooLongNotesAreRefusedUnderTheNotesField() throws Exception {
    asAnna(
            htmx(
                post("/occasions")
                    .param("name", "A")
                    .param("date", "2026-12-24")
                    .param("type", "OTHER")
                    .param("notes", "x".repeat(2001))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().string(containsString("legfeljebb 2000")));
  }

  @Test
  void aRejectedFormWithoutHtmxIsTheWholePage() throws Exception {
    asAnna(post("/occasions").param("name", "").param("date", "2026-12-24").param("type", "PARTY"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().string(containsString("<html")))
        .andExpect(content().string(containsString("Adj nevet az alkalomnak.")));
  }

  // ---- detail ----

  @Test
  void theDetailPageShowsTheOccasionWithItsEmptyMenuAndActions() throws Exception {
    OccasionView created =
        occasions.create(
            anna,
            annasHome,
            new OccasionDetails(
                "Anna szülinapja", today().plusDays(3), OccasionType.PARTY, "Hozd a tortát"));

    asAnna(get("/occasions/" + created.id()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Anna szülinapja")))
        .andExpect(content().string(containsString("Buli")))
        .andExpect(content().string(containsString("Hozd a tortát")))
        .andExpect(content().string(containsString("id=\"menu\"")))
        .andExpect(content().string(containsString("Még nincs étel a menün.")))
        .andExpect(content().string(containsString("id=\"delete-occasion\"")))
        .andExpect(
            content().string(containsString("/occasions/" + created.id() + "/delete?version=0")));
  }

  @Test
  void theMenuListsTheMealsInOrderWithTheirPlace() throws Exception {
    OccasionView created = occasion("Vacsora", today());
    MealView soup =
        meals.create(anna, annasHome, new MealDetails("Lencsevleves", null, null, List.of()));
    OccasionView withMenu = occasions.addItem(anna, annasHome, created.id(), soup.id());

    asAnna(get("/occasions/" + created.id()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Lencsevleves")))
        .andExpect(
            content()
                .string(containsString("data-item-id=\"" + withMenu.menu().get(0).id() + "\"")));
  }

  @Test
  void anotherHouseholdsOccasionIsNotFoundOnEveryRoute() throws Exception {
    OccasionView belas =
        occasions.create(
            bela, belasHome, new OccasionDetails("Belas titok", today(), OccasionType.OTHER, null));
    String path = "/occasions/" + belas.id();

    asAnna(get(path)).andExpect(status().isNotFound());
    asAnna(get(path + "/edit")).andExpect(status().isNotFound());
    asAnna(
            post(path)
                .param("name", "Lopas")
                .param("date", "2026-12-24")
                .param("type", "OTHER")
                .param("version", "0"))
        .andExpect(status().isNotFound());
    asAnna(post(path + "/delete").param("version", "0")).andExpect(status().isNotFound());
  }

  // ---- edit ----

  @Test
  void theEditFormIsFilledWithTheStoredOccasion() throws Exception {
    OccasionView created = occasion("Régi név", today().plusDays(2));

    asAnna(htmx(get("/occasions/" + created.id() + "/edit")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("value=\"Régi név\"")))
        .andExpect(content().string(containsString("name=\"version\" value=\"0\"")));
  }

  @Test
  void aSavedEditRedirectsBackToThePage() throws Exception {
    OccasionView created = occasion("Régi név", today().plusDays(2));

    asAnna(
            htmx(
                post("/occasions/" + created.id())
                    .param("version", "0")
                    .param("name", "Új név")
                    .param("date", today().plusDays(4).toString())
                    .param("type", "HOLIDAY")))
        .andExpect(status().isOk())
        .andExpect(header().string("HX-Redirect", "/occasions/" + created.id()));

    assertThat(occasions.find(anna, annasHome, created.id()).name()).isEqualTo("Új név");
  }

  @Test
  void aStaleEditIsAConflictThatKeepsWhatWasTyped() throws Exception {
    OccasionView created = occasion("Régi név", today().plusDays(2));
    occasions.edit(
        anna,
        annasHome,
        created.id(),
        0,
        new OccasionDetails("Mások", created.date(), OccasionType.OTHER, null));

    asAnna(
            htmx(
                post("/occasions/" + created.id())
                    .param("version", "0")
                    .param("name", "Az én nevem")
                    .param("date", created.date().toString())
                    .param("type", "PARTY")))
        .andExpect(status().isConflict())
        .andExpect(content().string(containsString("Valaki más módosította ezt az alkalmat")))
        .andExpect(content().string(containsString("value=\"Az én nevem\"")));
  }

  @Test
  void aRejectedEditKeepsTheFormAndTheMessage() throws Exception {
    OccasionView created = occasion("Régi név", today().plusDays(2));

    asAnna(
            htmx(
                post("/occasions/" + created.id())
                    .param("version", "0")
                    .param("name", "")
                    .param("date", created.date().toString())
                    .param("type", "PARTY")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().string(containsString("Adj nevet az alkalomnak.")));
  }

  // ---- delete ----

  @Test
  void aDeleteRemovesTheOccasionAndRedirectsToTheList() throws Exception {
    OccasionView created = occasion("Törlendő", today().plusDays(2));

    asAnna(htmx(post("/occasions/" + created.id() + "/delete").param("version", "0")))
        .andExpect(status().isOk())
        .andExpect(header().string("HX-Redirect", "/occasions"));

    assertThat(occasions.list(anna, annasHome)).isEmpty();
  }

  @Test
  void aStaleDeleteWithHtmxRetargetsTheBanner() throws Exception {
    OccasionView created = occasion("Törlendő", today().plusDays(2));
    occasions.edit(
        anna,
        annasHome,
        created.id(),
        0,
        new OccasionDetails("Mások", created.date(), OccasionType.OTHER, null));

    asAnna(htmx(post("/occasions/" + created.id() + "/delete").param("version", "0")))
        .andExpect(status().isConflict())
        .andExpect(header().string("HX-Retarget", "#occasion-conflict"))
        .andExpect(header().string("HX-Reswap", "innerHTML"))
        .andExpect(content().string(containsString("Valaki más módosította ezt az alkalmat")));

    assertThat(occasions.list(anna, annasHome)).hasSize(1);
  }

  @Test
  void aStaleDeleteWithoutHtmxShowsTheDetailPageWithTheBanner() throws Exception {
    OccasionView created = occasion("Törlendő", today().plusDays(2));
    occasions.edit(
        anna,
        annasHome,
        created.id(),
        0,
        new OccasionDetails("Mások", created.date(), OccasionType.OTHER, null));

    asAnna(post("/occasions/" + created.id() + "/delete").param("version", "0"))
        .andExpect(status().isConflict())
        .andExpect(content().string(containsString("id=\"occasion-conflict\"")))
        .andExpect(content().string(containsString("Valaki más módosította ezt az alkalmat")))
        .andExpect(content().string(containsString("Mások")));
  }

  private static int countOf(String text, String piece) {
    int count = 0;
    for (int at = text.indexOf(piece); at >= 0; at = text.indexOf(piece, at + 1)) {
      count++;
    }
    return count;
  }
}
