package hu.lasterbergamot.mealmagician.planning;

import static hu.lasterbergamot.mealmagician.household.HouseholdTestSupport.signedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import java.time.LocalDate;
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
 * The occasion template pages through the whole stack (issue #113): the template page, "Plan it
 * again", delete, and the Templates section of the Occasions list. Anna owns "Annas"; Bela owns
 * "Belas" and must never reach Anna's templates (PLAN.md D8). Texts are the Hungarian ones.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class OccasionTemplatePagesTest {

  @Autowired private MockMvc mvc;
  @Autowired private HouseholdService households;
  @Autowired private OccasionService occasions;
  @Autowired private OccasionTemplateService templates;
  @Autowired private MealService meals;
  @Autowired private JdbcTemplate jdbc;

  private long anna;
  private long bela;
  private long annasHome;
  private long sourceId;
  private long templateId;

  @BeforeEach
  void people() {
    anna = HouseholdTestSupport.insertUser(jdbc, "anna");
    bela = HouseholdTestSupport.insertUser(jdbc, "bela");
    annasHome = households.create(anna, "Annas").id();
    households.create(bela, "Belas");
    sourceId =
        occasions
            .create(
                anna,
                annasHome,
                new OccasionDetails(
                    "Karácsony", LocalDate.of(2030, 12, 24), OccasionType.HOLIDAY, "Sok sütit"))
            .id();
    occasions.addItem(anna, annasHome, sourceId, meals.quickAdd(anna, annasHome, "Halászlé").id());
    occasions.addItem(anna, annasHome, sourceId, meals.quickAdd(anna, annasHome, "Bejgli").id());
    templateId = templates.saveFromOccasion(anna, annasHome, sourceId, "Karácsonyi menü").id();
  }

  private ResultActions asAnna(MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.with(signedInAs(anna)).with(csrf()));
  }

  private ResultActions htmx(MockHttpServletRequestBuilder request) throws Exception {
    return asAnna(request.header("HX-Request", "true"));
  }

  @Test
  void theTemplatePageShowsItsKindNotesMenuAndActions() throws Exception {
    asAnna(get("/occasions/templates/" + templateId))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Karácsonyi menü")))
        .andExpect(content().string(containsString("Sablon")))
        .andExpect(content().string(containsString("Ünnep")))
        .andExpect(content().string(containsString("Sok sütit")))
        .andExpect(content().string(containsString("Halászlé")))
        .andExpect(content().string(containsString("Bejgli")))
        .andExpect(content().string(containsString("id=\"occasion-use-form\"")))
        .andExpect(content().string(containsString("id=\"delete-occasion-template\"")));
  }

  @Test
  void anotherHouseholdsTemplateIsNotFoundOnEveryRoute() throws Exception {
    String path = "/occasions/templates/" + templateId;

    mvc.perform(get(path).with(signedInAs(bela))).andExpect(status().isNotFound());
    mvc.perform(post(path + "/use").param("date", "2031-01-01").with(signedInAs(bela)).with(csrf()))
        .andExpect(status().isNotFound());
    mvc.perform(post(path + "/delete").with(signedInAs(bela)).with(csrf()))
        .andExpect(status().isNotFound());
    assertThat(templates.list(anna, annasHome)).hasSize(1);
  }

  @Test
  void planningItAgainCreatesAnOccasionWithTheSameNameKindNotesAndMenu() throws Exception {
    String redirect =
        htmx(post("/occasions/templates/" + templateId + "/use").param("date", "2031-12-24"))
            .andExpect(status().isOk())
            .andExpect(header().string("HX-Redirect", containsString("/occasions/")))
            .andReturn()
            .getResponse()
            .getHeader("HX-Redirect");

    long newId = Long.parseLong(redirect.substring("/occasions/".length()));
    OccasionView created = occasions.find(anna, annasHome, newId);
    assertThat(created.name()).isEqualTo("Karácsonyi menü");
    assertThat(created.date()).isEqualTo(LocalDate.of(2031, 12, 24));
    assertThat(created.type()).isEqualTo(OccasionType.HOLIDAY);
    assertThat(created.notes()).isEqualTo("Sok sütit");
    assertThat(created.menu()).hasSize(2);
  }

  @Test
  void aMissingOrMalformedDateIsRefusedUnderTheField() throws Exception {
    htmx(post("/occasions/templates/" + templateId + "/use"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().string(containsString("id=\"occasion-use-form\"")))
        .andExpect(content().string(containsString("Válassz dátumot.")));
    htmx(post("/occasions/templates/" + templateId + "/use").param("date", "x"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().string(containsString("value=\"x\"")))
        .andExpect(content().string(containsString("Válassz dátumot.")));
  }

  @Test
  void aRejectedDateWithoutHtmxIsTheWholeTemplatePage() throws Exception {
    asAnna(post("/occasions/templates/" + templateId + "/use"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().string(containsString("<html")))
        .andExpect(content().string(containsString("Válassz dátumot.")));
  }

  @Test
  void deletingTheTemplateKeepsTheOccasionItCameFrom() throws Exception {
    htmx(post("/occasions/templates/" + templateId + "/delete"))
        .andExpect(status().isOk())
        .andExpect(header().string("HX-Redirect", "/occasions"));

    assertThat(templates.list(anna, annasHome)).isEmpty();
    assertThat(occasions.find(anna, annasHome, sourceId).menu()).hasSize(2);
  }

  @Test
  void theListShowsTheTemplatesSection() throws Exception {
    asAnna(get("/occasions"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("occasion-templates-title")))
        .andExpect(
            content().string(containsString("data-occasion-template-id=\"" + templateId + "\"")))
        .andExpect(content().string(containsString("Ételek a menün: 2")));
  }

  @Test
  void aHouseholdWithOnlyTemplatesSeesTheEmptyStateAndTheTemplates() throws Exception {
    occasions.delete(anna, annasHome, sourceId, 0);

    asAnna(get("/occasions"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Még nincs alkalom")))
        .andExpect(
            content().string(containsString("data-occasion-template-id=\"" + templateId + "\"")));
  }
}
