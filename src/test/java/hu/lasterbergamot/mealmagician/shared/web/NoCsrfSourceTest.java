package hu.lasterbergamot.mealmagician.shared.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Until Spring Security supplies a {@link CsrfMetaSource}, the layout has no CSRF tags. */
@WebSliceTest
class NoCsrfSourceTest {

  @Autowired private MockMvc mvc;

  @Test
  void rendersNoCsrfMetaTagsWithoutATokenSource() throws Exception {
    mvc.perform(get("/")).andExpect(content().string(not(containsString("_csrf"))));
  }
}
