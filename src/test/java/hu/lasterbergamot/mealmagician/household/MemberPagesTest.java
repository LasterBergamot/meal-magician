package hu.lasterbergamot.mealmagician.household;

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * The members page through the whole stack. Anna owns "Annas" with Bela and Cili as plain members,
 * Dora owns "Doras" and Emil belongs nowhere.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class MemberPagesTest {

  @Autowired private MockMvc mvc;
  @Autowired private HouseholdService households;
  @Autowired private JdbcTemplate jdbc;

  private long anna;
  private long bela;
  private long cili;
  private long dora;
  private long emil;
  private long annas;

  @BeforeEach
  void people() {
    anna = HouseholdTestSupport.insertUser(jdbc, "anna");
    bela = HouseholdTestSupport.insertUser(jdbc, "bela");
    cili = HouseholdTestSupport.insertUser(jdbc, "cili");
    dora = HouseholdTestSupport.insertUser(jdbc, "dora");
    emil = HouseholdTestSupport.insertUser(jdbc, "emil");
    annas = households.create(anna, "Annas").id();
    households.create(dora, "Doras");
    for (long member : new long[] {bela, cili}) {
      jdbc.update(
          "insert into membership (household_id, user_id, role, joined_at) values (?, ?, 'MEMBER', now())",
          annas,
          member);
    }
  }

  private ResultActions act(long as, String path, boolean htmx) throws Exception {
    var request = post(path).with(signedInAs(as)).with(csrf());
    if (htmx) {
      request = request.header("HX-Request", "true");
    }
    return mvc.perform(request);
  }

  @Test
  void theOwnerSeesEveryoneWithRemoveAndTransferButtons() throws Exception {
    mvc.perform(get("/households/members").with(signedInAs(anna)))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<html")))
        .andExpect(content().string(containsString("anna@example.com")))
        .andExpect(content().string(containsString("bela@example.com")))
        .andExpect(content().string(containsString("cili@example.com")))
        .andExpect(content().string(containsString("(te)")))
        .andExpect(content().string(containsString("/households/members/" + bela + "/remove")))
        .andExpect(content().string(containsString("/households/members/" + bela + "/transfer")))
        .andExpect(content().string(not(containsString("/households/members/" + anna + "/"))))
        .andExpect(content().string(containsString("/households/members/leave")))
        .andExpect(content().string(containsString("Az utolsó tulajdonos nem léphet ki")));
  }

  @Test
  void aPlainMemberSeesTheListAndLeaveButOnlyThat() throws Exception {
    mvc.perform(get("/households/members").with(signedInAs(bela)))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("anna@example.com")))
        .andExpect(content().string(not(containsString("/remove"))))
        .andExpect(content().string(not(containsString("/transfer"))))
        .andExpect(content().string(containsString("/households/members/leave")))
        .andExpect(content().string(not(containsString("Az utolsó tulajdonos nem léphet ki"))));
  }

  @Test
  void anHtmxRequestGetsJustTheSection() throws Exception {
    mvc.perform(get("/households/members").with(signedInAs(anna)).header("HX-Request", "true"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("id=\"members\"")))
        .andExpect(content().string(not(containsString("<html"))))
        .andExpect(header().string("Vary", "HX-Request"));
  }

  @Test
  void aPersonWithoutAHouseholdGetsNotFound() throws Exception {
    mvc.perform(get("/households/members").with(signedInAs(emil))).andExpect(status().isNotFound());
    act(emil, "/households/members/leave", true).andExpect(status().isNotFound());
  }

  @Test
  void theHouseholdsPageLinksToTheMembers() throws Exception {
    mvc.perform(get("/households").with(signedInAs(anna)))
        .andExpect(content().string(containsString("href=\"/households/members\"")));
  }

  @Test
  void removingAMemberShowsTheShorterList() throws Exception {
    act(anna, "/households/members/" + bela + "/remove", true)
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("bela@example.com"))))
        .andExpect(content().string(containsString("cili@example.com")))
        .andExpect(content().string(not(containsString("<html"))));

    assertThat(households.find(annas, bela)).isEmpty();
  }

  @Test
  void aRemovedMemberGetsNotFoundAtOnce() throws Exception {
    mvc.perform(get("/households/members").with(signedInAs(bela))).andExpect(status().isOk());

    act(anna, "/households/members/" + bela + "/remove", true);

    mvc.perform(get("/households/members").with(signedInAs(bela))).andExpect(status().isNotFound());
    act(bela, "/households/" + annas + "/switch", true).andExpect(status().isNotFound());
  }

  @Test
  void aPlainMemberMayNotRemoveOrTransfer() throws Exception {
    act(bela, "/households/members/" + cili + "/remove", true).andExpect(status().isForbidden());
    act(bela, "/households/members/" + cili + "/transfer", true).andExpect(status().isForbidden());
    assertThat(households.find(annas, cili)).isPresent();
  }

  @Test
  void ownersCannotRemoveOrTransferToThemselves() throws Exception {
    act(anna, "/households/members/" + anna + "/remove", true)
        .andExpect(status().isConflict())
        .andExpect(content().string(containsString("Ezt magaddal itt nem teheted meg")));
    act(anna, "/households/members/" + anna + "/transfer", true)
        .andExpect(status().isConflict())
        .andExpect(content().string(containsString("Ezt magaddal itt nem teheted meg")));
  }

  @Test
  void someoneFromAnotherHouseholdIsNotFound() throws Exception {
    act(anna, "/households/members/" + dora + "/remove", true).andExpect(status().isNotFound());
    act(anna, "/households/members/" + dora + "/transfer", true).andExpect(status().isNotFound());
    assertThat(households.find(annas, anna)).isPresent();
  }

  @Test
  void transferMakesTheOldOwnerAMemberAndTheListShowsIt() throws Exception {
    act(anna, "/households/members/" + bela + "/transfer", true)
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("/remove"))))
        .andExpect(content().string(not(containsString("Az utolsó tulajdonos nem léphet ki"))));

    assertThat(households.require(annas, bela).isOwner()).isTrue();
    assertThat(households.require(annas, anna).isOwner()).isFalse();
    act(anna, "/households/members/" + cili + "/remove", true).andExpect(status().isForbidden());
  }

  @Test
  void theLastOwnerLeavingIsRefusedWithAClearMessage() throws Exception {
    act(anna, "/households/members/leave", true)
        .andExpect(status().isConflict())
        .andExpect(content().string(containsString("id=\"members\"")))
        .andExpect(content().string(containsString("Te vagy az egyetlen tulajdonos")))
        .andExpect(content().string(containsString("role=\"alert\"")));

    assertThat(households.require(annas, anna).isOwner()).isTrue();
  }

  @Test
  void aMemberLeavesAndIsSentToTheHouseholdsPage() throws Exception {
    act(bela, "/households/members/leave", true)
        .andExpect(status().isOk())
        .andExpect(header().string("HX-Redirect", "/households"));

    assertThat(households.find(annas, bela)).isEmpty();
    mvc.perform(get("/households/members").with(signedInAs(bela))).andExpect(status().isNotFound());
  }

  @Test
  void aNormalLeaveRequestRedirectsWithSeeOther() throws Exception {
    act(bela, "/households/members/leave", false)
        .andExpect(status().isSeeOther())
        .andExpect(header().string("Location", "/households"));
  }

  @Test
  void aNormalRemoveRequestGetsTheWholePage() throws Exception {
    act(anna, "/households/members/" + bela + "/remove", false)
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<html")));
  }

  @Test
  void aMemberWhoLeavesFallsBackToTheirOtherHousehold() throws Exception {
    households.create(bela, "Belas");

    act(bela, "/households/members/leave", true).andExpect(status().isOk());

    mvc.perform(get("/households/members").with(signedInAs(bela)))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Belas")));
  }
}
