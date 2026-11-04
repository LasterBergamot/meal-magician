package hu.lasterbergamot.mealmagician.assistant.internal;

import static hu.lasterbergamot.mealmagician.household.HouseholdTestSupport.signedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * The wizard's routes while the assistant is off ({@code mm.ai.enabled=false}, the default): the
 * pieces a host page loads by itself answer {@code 204} with no body, so the plan page shows
 * nothing, and every other route answers 404 as if it did not exist ({@link AssistantGate}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class AssistantOffPagesTest {

  @Autowired private MockMvc mvc;
  @Autowired private HouseholdService households;
  @Autowired private JdbcTemplate jdbc;

  private long user;

  @BeforeEach
  void aMemberWithAHousehold() {
    user = HouseholdTestSupport.insertUser(jdbc, "wiz-off");
    households.create(user, "Offs");
  }

  private MockHttpServletRequestBuilder signed(MockHttpServletRequestBuilder request) {
    return request.with(signedInAs(user)).with(csrf()).header("HX-Request", "true");
  }

  @Test
  void theEntryAnswersNoContent() throws Exception {
    var response =
        mvc.perform(signed(get("/assistant/entry").param("week", "2026-W44")))
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse();

    assertThat(response.getContentAsString()).isEmpty();
  }

  @Test
  void thePickSheetsRowAndTheNoticeAnswerNoContent() throws Exception {
    var slot =
        mvc.perform(
                signed(
                    get("/assistant/slot").param("date", "2026-11-02").param("slotType", "LUNCH")))
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse();
    var notice =
        mvc.perform(signed(get("/assistant/notice")))
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse();

    assertThat(slot.getContentAsString()).isEmpty();
    assertThat(notice.getContentAsString()).isEmpty();
  }

  @Test
  void everyOtherRouteIsNotFound() throws Exception {
    mvc.perform(signed(get("/assistant/week").param("week", "2026-W44")))
        .andExpect(status().isNotFound());
    mvc.perform(signed(post("/assistant/drafts").param("request", "x")))
        .andExpect(status().isNotFound());
    mvc.perform(signed(get("/assistant/drafts/1"))).andExpect(status().isNotFound());
    mvc.perform(signed(post("/assistant/drafts/1/accept"))).andExpect(status().isNotFound());
    mvc.perform(signed(post("/assistant/slot").param("date", "2026-11-02")))
        .andExpect(status().isNotFound());
  }

  @Test
  void aPostToAQuietPathIsNotFoundToo() throws Exception {
    // Only GET is quiet: a POST to /assistant/slot is an action, not a page load.
    mvc.perform(signed(post("/assistant/slot"))).andExpect(status().isNotFound());
  }

  @Test
  void thePlanPageStillRendersAndShowsNothingOfTheWizard() throws Exception {
    String page =
        mvc.perform(signed(get("/plan")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(page).doesNotContain("id=\"assistant-sheet\"").doesNotContain("Heti javaslat");
  }

  @Test
  void theChatAndTheOccasionRoutesAreOffToo() throws Exception {
    mvc.perform(signed(get("/assistant"))).andExpect(status().isNotFound());
    mvc.perform(signed(post("/assistant/chat").param("message", "x")))
        .andExpect(status().isNotFound());
    mvc.perform(signed(post("/assistant/chat/reset"))).andExpect(status().isNotFound());
    mvc.perform(signed(post("/assistant/occasions/1/menu"))).andExpect(status().isNotFound());
    mvc.perform(signed(post("/assistant/occasions/1/ideas").param("name", "x")))
        .andExpect(status().isNotFound());
  }

  @Test
  void theOccasionLoaderAnswersNoContentWhileTheAssistantIsOff() throws Exception {
    var response =
        mvc.perform(signed(get("/assistant/occasions/1/entry")))
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse();

    assertThat(response.getContentAsString()).isEmpty();
  }

  @Test
  void theNavigationHasNoWizardWhileTheAssistantIsOff() throws Exception {
    String page =
        mvc.perform(signed(get("/plan")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(page).doesNotContain("href=\"/assistant\"");
  }
}
