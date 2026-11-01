package hu.lasterbergamot.mealmagician.household;

import static hu.lasterbergamot.mealmagician.household.HouseholdTestSupport.signedInAs;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * The {@link CurrentHousehold} controller parameter, which the meal and planning modules will use:
 * a probe controller declares it and echoes what it received.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@Import(CurrentHouseholdArgumentTest.Probe.class)
class CurrentHouseholdArgumentTest {

  @Autowired private MockMvc mvc;
  @Autowired private HouseholdService service;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void aControllerReceivesTheVerifiedHousehold() throws Exception {
    long anna = HouseholdTestSupport.insertUser(jdbc, "anna");
    service.create(anna, "Annas");

    mvc.perform(get("/probe/current-household").with(signedInAs(anna)))
        .andExpect(status().isOk())
        .andExpect(content().string("Annas OWNER"));
  }

  @Test
  void aPersonWithoutAHouseholdGetsNotFound() throws Exception {
    long newcomer = HouseholdTestSupport.insertUser(jdbc, "newcomer");

    mvc.perform(get("/probe/current-household").with(signedInAs(newcomer)))
        .andExpect(status().isNotFound());
  }

  @TestConfiguration(proxyBeanMethods = false)
  @Controller
  static class Probe {
    @GetMapping("/probe/current-household")
    @ResponseBody
    String show(CurrentHousehold household) {
      return household.name() + " " + household.role();
    }
  }
}
