package hu.lasterbergamot.mealmagician.assistant.internal;

import static hu.lasterbergamot.mealmagician.household.HouseholdTestSupport.signedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
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

/**
 * The Wizard's chat page through the whole stack, with the scripted model of the {@code fake-ai}
 * profile: nothing here calls Google. The conversation lives in the session, which the test carries
 * from request to request by its cookie, the way a browser does. Texts are the Hungarian ones, the
 * default language of the tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "fake-ai"})
class AssistantChatPagesTest {

  @Autowired private MockMvc mvc;
  @Autowired private HouseholdService households;
  @Autowired private UsageLedger ledger;
  @Autowired private ScriptedChatModel model;
  @Autowired private JdbcTemplate jdbc;

  private long anna;
  private long annasHome;

  @BeforeEach
  void aMemberWithAHousehold() {
    model.reset();
    anna = HouseholdTestSupport.insertUser(jdbc, "chat-anna");
    annasHome = households.create(anna, "Chat Annas").id();
  }

  @AfterEach
  void cleanUp() {
    jdbc.update("delete from assistant_usage where household_id = ?", annasHome);
    jdbc.update("delete from household where id = ?", annasHome);
    jdbc.update("delete from app_user where id = ?", anna);
  }

  private ResultActions asAnna(MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.with(signedInAs(anna)).with(csrf()).header("HX-Request", "true"));
  }

  /**
   * Starts a browser-like conversation: the page load makes the app create the session, and the
   * cookie it sets is what later requests send back. Spring Session finds sessions by that cookie,
   * so a {@code MockHttpSession} passed to MockMvc would not carry the conversation.
   */
  private Cookie newConversation() throws Exception {
    Cookie cookie = asAnna(get("/assistant")).andReturn().getResponse().getCookie("SESSION");
    assertThat(cookie).as("the app sets a session cookie").isNotNull();
    return cookie;
  }

  private ResultActions ask(Cookie session, String message) throws Exception {
    return asAnna(post("/assistant/chat").cookie(session).param("message", message));
  }

  private static String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  @Test
  void theNavigationListsTheWizardWhileTheAssistantIsOn() throws Exception {
    String plan = body(mvc.perform(get("/plan").with(signedInAs(anna))).andExpect(status().isOk()));

    assertThat(plan).contains("href=\"/assistant\"").contains("Varázsló");
  }

  @Test
  void theChatPageOpensWithItsEmptyStateAndStarterChips() throws Exception {
    String page =
        body(mvc.perform(get("/assistant").with(signedInAs(anna))).andExpect(status().isOk()));

    assertThat(page)
        .contains("Kérdezz az ételekről")
        .contains("value=\"Mi van most szezonban?\"")
        .contains("Ötletek vasárnapi ebédre")
        .contains("id=\"chat-composer\"")
        .contains("x-data=\"chatComposer\"");
  }

  @Test
  void aStarterChipAnswersItsTextAsTheMessage() throws Exception {
    Cookie session = newConversation();

    String reply = body(ask(session, "Mi van most szezonban?").andExpect(status().isOk()));

    assertThat(reply).contains("Scripted answer: Mi van most szezonban?").contains("chat-empty");
  }

  @Test
  void theAnswerIsShownAndTheNextPromptCarriesTheFirstExchange() throws Exception {
    Cookie session = newConversation();

    String first = body(ask(session, "Mit főzzünk?").andExpect(status().isOk()));
    ask(session, "És holnap?").andExpect(status().isOk());

    assertThat(first).contains("Scripted answer: Mit főzzünk?");
    assertThat(model.prompts()).hasSize(2);
    assertThat(model.prompts().get(1))
        .contains("Scripted answer: Mit főzzünk?")
        .contains("\"És holnap?\"");
  }

  @Test
  void theTopicPolicyIsInTheSystemPrompt() throws Exception {
    ask(newConversation(), "Mi van most szezonban?").andExpect(status().isOk());

    assertThat(model.prompts().getFirst())
        .contains("Topic policy: food and the meal plan only.")
        .contains("Quoted household data")
        .contains("in Hungarian");
  }

  @Test
  void newConversationForgetsTheTurns() throws Exception {
    Cookie session = newConversation();
    ask(session, "Mit főzzünk?").andExpect(status().isOk());

    String reset =
        body(asAnna(post("/assistant/chat/reset").cookie(session)).andExpect(status().isOk()));
    String page = body(mvc.perform(get("/assistant").cookie(session).with(signedInAs(anna))));
    ask(session, "Új kérdés").andExpect(status().isOk());

    assertThat(reset).contains("id=\"chat-empty\"").doesNotContain("Scripted answer");
    assertThat(page).contains("Kérdezz az ételekről").doesNotContain("Scripted answer");
    assertThat(model.prompts().getLast()).doesNotContain("Mit főzzünk?");
  }

  @Test
  void theHistoryKeepsTheLastTwentyTurns() throws Exception {
    Cookie session = newConversation();
    for (int i = 1; i <= 12; i++) {
      ask(session, "msg-" + i).andExpect(status().isOk());
    }

    assertThat(model.prompts()).hasSize(12);
    assertThat(model.prompts().get(11))
        .contains("\"msg-12\"")
        .contains("\"msg-2\"")
        .doesNotContain("\"msg-1\"");
  }

  @Test
  void aMessageOfOneThousandCharactersIsAcceptedButOneThousandAndOneIsRefusedWithoutACall()
      throws Exception {
    Cookie session = newConversation();
    ask(session, "a".repeat(1000)).andExpect(status().isOk());
    int calls = model.requests().size();

    String refused =
        body(ask(session, "a".repeat(1001)).andExpect(status().isUnprocessableEntity()));

    assertThat(refused).contains("Legfeljebb 1000 karakter lehet.");
    assertThat(model.requests()).hasSize(calls);
  }

  @Test
  void aFailedCallLeavesTheHistoryUnchangedAndShowsTheAlert() throws Exception {
    Cookie session = newConversation();
    ask(session, "első").andExpect(status().isOk());
    model.setFailing(true);

    String failed = body(ask(session, "bukott").andExpect(status().isServiceUnavailable()));
    model.setFailing(false);
    ask(session, "második").andExpect(status().isOk());

    assertThat(failed).contains("A varázsló most nem érhető el.");
    assertThat(model.prompts().getLast())
        .contains("Scripted answer: első")
        .doesNotContain("bukott");
  }

  @Test
  void anEmptyAnswerIsTreatedAsUnavailable() throws Exception {
    model.setChatReply("   ");

    String failed =
        body(ask(newConversation(), "semmi").andExpect(status().isServiceUnavailable()));

    assertThat(failed).contains("A varázsló most nem érhető el.");
  }

  @Test
  void theSpendCapStopsTheCallWithItsOwnMessage() throws Exception {
    ledger.record(annasHome, 10_000_000, 10_000_000);

    String capped = body(ask(newConversation(), "kérdés").andExpect(status().isTooManyRequests()));

    assertThat(capped).contains("Elfogyott a varázsló e havi kerete.");
    assertThat(model.requests()).isEmpty();
  }

  @Test
  void aBlankOrMissingMessageSendsNothing() throws Exception {
    Cookie session = newConversation();

    asAnna(post("/assistant/chat").cookie(session).param("message", "   "))
        .andExpect(status().isNoContent());
    asAnna(post("/assistant/chat").cookie(session)).andExpect(status().isNoContent());

    assertThat(model.requests()).isEmpty();
  }

  @Test
  void modelMarkupIsShownAsEscapedText() throws Exception {
    model.setChatReply("<b>hello</b>\nsecond line");

    String reply = body(ask(newConversation(), "hello").andExpect(status().isOk()));

    assertThat(reply)
        .contains("&lt;b&gt;hello&lt;/b&gt;")
        .doesNotContain("<b>hello")
        .contains("second line");
  }
}
