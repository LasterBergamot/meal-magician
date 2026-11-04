package hu.lasterbergamot.mealmagician.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

  private static MockEnvironment assistantOn() {
    return validEnvironment()
        .withProperty("mm.ai.enabled", "true")
        .withProperty("spring.ai.google.genai.project-id", "my-project")
        .withProperty("spring.ai.google.genai.location", "eu")
        .withProperty("spring.ai.google.genai.chat.model", "gemini-3.5-flash")
        .withProperty("spring.ai.google.genai.embedding.location", "europe-west3")
        .withProperty("spring.ai.google.genai.embedding.text.model", "gemini-embedding-001")
        .withProperty("mm.ai.price-input-per-million-usd", "1.65")
        .withProperty("mm.ai.price-output-per-million-usd", "9.90");
  }

  @Test
  void assistantOffNeedsNoAiSettings() {
    assertThat(ProductionConfigGuard.problems(validEnvironment())).isEmpty();
    assertThat(
            ProductionConfigGuard.problems(
                validEnvironment().withProperty("mm.ai.enabled", "false")))
        .isEmpty();
  }

  @Test
  void assistantOnWithEverythingSetPasses() {
    assertThat(ProductionConfigGuard.problems(assistantOn())).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "spring.ai.google.genai.project-id, AI_PROJECT_ID",
    "spring.ai.google.genai.location, AI_LOCATION",
    "spring.ai.google.genai.chat.model, AI_CHAT_MODEL",
    "spring.ai.google.genai.embedding.location, AI_EMBEDDING_LOCATION",
    "spring.ai.google.genai.embedding.text.model, AI_EMBEDDING_MODEL",
    "mm.ai.price-input-per-million-usd, AI_PRICE_IN",
    "mm.ai.price-output-per-million-usd, AI_PRICE_OUT"
  })
  void assistantOnWithAMissingSettingNamesIt(String property, String variable) {
    assertThat(ProductionConfigGuard.problems(assistantOn().withProperty(property, "")))
        .hasSize(1)
        .first()
        .asString()
        .contains(variable)
        .contains(property);
    assertThatThrownBy(() -> check(assistantOn().withProperty(property, " ")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(property);
  }

  @Test
  void theScriptedAssistantProfileIsRefused() {
    var scripted = validEnvironment();
    scripted.setActiveProfiles("prod", "fake-ai");
    var real = validEnvironment();
    real.setActiveProfiles("prod");

    assertProblem(scripted, "fake-ai");
    assertThat(ProductionConfigGuard.problems(real)).isEmpty();
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
