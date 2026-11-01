package hu.lasterbergamot.mealmagician.identity.internal;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to start the app in production with a half-configured security setup ("fail closed",
 * PLAN.md §4.4).
 *
 * <p>Without this, a forgotten Cloud Run variable would not crash anything obvious: the app would
 * come up pointing at {@code localhost}, or with nobody able to sign in, or behind the proxy with
 * wrong redirect URLs. Failing at startup with one message that lists <em>every</em> problem is far
 * easier to fix than discovering them one deploy at a time.
 *
 * <p>It is a {@link BeanFactoryPostProcessor} so that it runs <em>before</em> any bean such as the
 * datasource or Flyway is created. As an ordinary bean it would run after them, and a missing
 * database URL would surface as a confusing SQL error from Flyway instead of this message.
 */
@Component
@Profile("prod")
class ProductionConfigGuard implements BeanFactoryPostProcessor {

  /** Placeholder from {@code application.yml} that means "no value was provided". */
  static final String NOT_CONFIGURED = "not-configured";

  private static final String CLIENT = "spring.security.oauth2.client.registration.google.";

  /**
   * Checks the environment before any other bean exists.
   *
   * @param beanFactory the bean factory being prepared, used to reach the environment
   * @throws IllegalStateException listing every missing or invalid setting
   */
  @Override
  public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    var problems = problems(beanFactory.getBean(Environment.class));
    if (!problems.isEmpty()) {
      throw new IllegalStateException(
          "Refusing to start with the prod profile, fix these settings: "
              + String.join("; ", problems));
    }
  }

  /**
   * Lists everything wrong with the production settings.
   *
   * @param environment the Spring environment to inspect
   * @return one human-readable line per problem; empty when the configuration is complete
   */
  static List<String> problems(Environment environment) {
    var problems = new ArrayList<String>();
    if (isMissing(environment.getProperty(CLIENT + "client-id"))) {
      problems.add("GOOGLE_CLIENT_ID is not set");
    }
    if (isMissing(environment.getProperty(CLIENT + "client-secret"))) {
      problems.add("GOOGLE_CLIENT_SECRET is not set");
    }
    var admins = environment.getProperty("mm.admin-emails", "");
    if (admins.isBlank() || admins.replace(",", "").isBlank()) {
      problems.add("MM_ADMIN_EMAILS is not set (nobody could manage the allowlist)");
    }
    var datasourceUrl = environment.getProperty("spring.datasource.url", "");
    if (!datasourceUrl.startsWith("jdbc:postgresql:")) {
      problems.add("SPRING_DATASOURCE_URL must be a jdbc:postgresql: URL");
    }
    if (!"framework".equals(environment.getProperty("server.forward-headers-strategy"))) {
      problems.add(
          "server.forward-headers-strategy must be 'framework' (the hosting proxy terminates TLS)");
    }
    return problems;
  }

  private static boolean isMissing(String value) {
    return value == null || value.isBlank() || NOT_CONFIGURED.equals(value);
  }
}
