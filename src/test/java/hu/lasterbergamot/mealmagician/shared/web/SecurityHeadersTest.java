package hu.lasterbergamot.mealmagician.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebSliceTest
class SecurityHeadersTest {

  @Autowired private MockMvc mvc;

  @Test
  void sendsAContentSecurityPolicyWithoutUnsafeKeywords() throws Exception {
    MvcResult result = mvc.perform(get("/")).andReturn();

    String csp = result.getResponse().getHeader("Content-Security-Policy");
    assertThat(csp)
        .isEqualTo(SecurityHeadersFilter.CONTENT_SECURITY_POLICY)
        .contains("default-src 'self'", "script-src 'self'", "frame-ancestors 'none'")
        .doesNotContain("unsafe-inline", "unsafe-eval", "*", "http:");
  }

  @Test
  void sendsTheOtherSecurityHeaders() throws Exception {
    mvc.perform(get("/"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Referrer-Policy", "same-origin"));
  }

  @Test
  void sendsTheHeadersOnStaticFilesAndErrorsToo() throws Exception {
    mvc.perform(get("/js/app.js"))
        .andExpect(
            header()
                .string("Content-Security-Policy", SecurityHeadersFilter.CONTENT_SECURITY_POLICY));
    mvc.perform(get("/does-not-exist"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"));
  }
}
