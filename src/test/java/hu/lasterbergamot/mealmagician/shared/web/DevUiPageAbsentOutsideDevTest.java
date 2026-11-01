package hu.lasterbergamot.mealmagician.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The style guide exists only in the {@code dev} profile (DevUiController): without it the URL is
 * simply not mapped, so a production build has no page to expose.
 */
@WebSliceTest
class DevUiPageAbsentOutsideDevTest {

  @Autowired private MockMvc mvc;

  @Test
  void theStyleGuideIsNotMappedWithoutTheDevProfile() throws Exception {
    mvc.perform(get("/dev/ui")).andExpect(status().isNotFound());
  }
}
