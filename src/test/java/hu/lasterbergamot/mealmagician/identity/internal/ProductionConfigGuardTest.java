package hu.lasterbergamot.mealmagician.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

class ProductionConfigGuardTest {

  private static MockEnvironment validEnvironment() {
    return new MockEnvironment()
        .withProperty("spring.security.oauth2.client.registration.google.client-id", "id")
        .withProperty("spring.security.oauth2.client.registration.google.client-secret", "secret")
        .withProperty("mm.admin-emails", "boss@example.com")
        .withProperty("spring.datasource.url", "jdbc:postgresql://db:5432/mm")
        .withProperty("server.forward-headers-strategy", "framework");
  }

  @Test
  void completeConfigurationPasses() {
    assertThatCode(() -> check(validEnvironment())).doesNotThrowAnyException();
    assertThat(ProductionConfigGuard.problems(validEnvironment())).isEmpty();
  }

  @Test
  void missingClientIdIsReported() {
    assertProblem(
        validEnvironment()
            .withProperty("spring.security.oauth2.client.registration.google.client-id", ""),
        "GOOGLE_CLIENT_ID");
  }

  @Test
  void placeholderClientIdIsReported() {
    assertProblem(
        validEnvironment()
            .withProperty(
                "spring.security.oauth2.client.registration.google.client-id", "not-configured"),
        "GOOGLE_CLIENT_ID");
  }

  @Test
  void absentClientSecretIsReported() {
    var environment =
        new MockEnvironment()
            .withProperty("spring.security.oauth2.client.registration.google.client-id", "id")
            .withProperty("mm.admin-emails", "boss@example.com")
            .withProperty("spring.datasource.url", "jdbc:postgresql://db:5432/mm")
            .withProperty("server.forward-headers-strategy", "framework");
    assertProblem(environment, "GOOGLE_CLIENT_SECRET");
  }

  @Test
  void missingAdminEmailsAreReported() {
    assertProblem(validEnvironment().withProperty("mm.admin-emails", ""), "MM_ADMIN_EMAILS");
  }

  @Test
  void onlySeparatorsInAdminEmailsAreReported() {
    assertProblem(validEnvironment().withProperty("mm.admin-emails", " , "), "MM_ADMIN_EMAILS");
  }

  @Test
  void nonPostgresDatasourceIsReported() {
    assertProblem(
        validEnvironment().withProperty("spring.datasource.url", "jdbc:h2:mem:x"),
        "SPRING_DATASOURCE_URL");
  }

  @Test
  void missingDatasourceIsReported() {
    assertThat(ProductionConfigGuard.problems(new MockEnvironment()))
        .anyMatch(p -> p.contains("SPRING_DATASOURCE_URL"));
  }

  @Test
  void missingForwardHeadersStrategyIsReported() {
    assertProblem(
        validEnvironment().withProperty("server.forward-headers-strategy", "none"),
        "forward-headers-strategy");
  }

  @Test
  void everyProblemIsListedInOneMessage() {
    assertThatThrownBy(() -> check(new MockEnvironment()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("GOOGLE_CLIENT_ID")
        .hasMessageContaining("GOOGLE_CLIENT_SECRET")
        .hasMessageContaining("MM_ADMIN_EMAILS")
        .hasMessageContaining("SPRING_DATASOURCE_URL")
        .hasMessageContaining("forward-headers-strategy");
  }

  /** Runs the guard the way Spring does: against a bean factory that knows the environment. */
  private static void check(MockEnvironment environment) {
    var beanFactory = new DefaultListableBeanFactory();
    beanFactory.registerSingleton("environment", environment);
    new ProductionConfigGuard().postProcessBeanFactory(beanFactory);
  }

  private static void assertProblem(MockEnvironment environment, String expected) {
    assertThat(ProductionConfigGuard.problems(environment))
        .hasSize(1)
        .first()
        .asString()
        .contains(expected);
  }
}
