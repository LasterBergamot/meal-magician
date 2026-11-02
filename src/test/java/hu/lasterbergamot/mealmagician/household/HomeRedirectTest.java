package hu.lasterbergamot.mealmagician.household;

import static hu.lasterbergamot.mealmagician.household.HouseholdTestSupport.signedInAs;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The address {@code /} shows no page of its own: it sends a signed-in person to the week plan, or
 * to the households page when they belong to no household yet.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class HomeRedirectTest {

  @Autowired private MockMvc mvc;
  @Autowired private HouseholdService service;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void aPersonWithAHouseholdIsSentToThePlan() throws Exception {
    long anna = HouseholdTestSupport.insertUser(jdbc, "anna");
    service.create(anna, "Annas");

    mvc.perform(get("/").with(signedInAs(anna)))
        .andExpect(status().isFound())
        .andExpect(redirectedUrl("/plan"));
  }

  @Test
  void aPersonWithoutAHouseholdIsSentToTheHouseholdsPage() throws Exception {
    long newcomer = HouseholdTestSupport.insertUser(jdbc, "newcomer");

    mvc.perform(get("/").with(signedInAs(newcomer)))
        .andExpect(status().isFound())
        .andExpect(redirectedUrl("/households"));
  }

  @Test
  void anAnonymousVisitorIsSentToSignIn() throws Exception {
    mvc.perform(get("/")).andExpect(status().isFound()).andExpect(redirectedUrl("/login"));
  }
}
