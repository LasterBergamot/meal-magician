package hu.lasterbergamot.mealmagician.assistant.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.google.auth.oauth2.GoogleCredentials;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The switch that keeps Spring AI inert, and the embedding connection that replaces Spring AI's
 * switchless one. No Google client is ever really created here: the Google credential lookup is
 * replaced, because a test must not need Google credentials (or the network).
 */
class AiConfigTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer());

  @Test
  void withTheAssistantOffEverySpringAiModelIsNone() {
    runner.run(context -> {
      var env = context.getEnvironment();
      assertThat(env.getProperty("spring.ai.model.chat")).isEqualTo("none");
      assertThat(env.getProperty("spring.ai.model.embedding.text")).isEqualTo("none");
      assertThat(env.getProperty("mm.ai.enabled")).isEqualTo("false");
    });
  }

  @Test
  void withTheAssistantOnSpringAiUsesGoogleGenAi() {
    runner
        .withPropertyValues("mm.ai.enabled=true")
        .run(context -> {
          var env = context.getEnvironment();
          assertThat(env.getProperty("spring.ai.model.chat")).isEqualTo("google-genai");
          assertThat(env.getProperty("spring.ai.model.embedding.text")).isEqualTo("google-genai");
        });
  }

  @Test
  void theEmbeddingLocationFollowsTheChatLocationUnlessSetApart() {
    runner
        .withPropertyValues("AI_LOCATION=eu", "AI_PROJECT_ID=p")
        .run(context -> {
          var env = context.getEnvironment();
          assertThat(env.getProperty("spring.ai.google.genai.location")).isEqualTo("eu");
          assertThat(env.getProperty("spring.ai.google.genai.embedding.location")).isEqualTo("eu");
          assertThat(env.getProperty("spring.ai.google.genai.embedding.project-id")).isEqualTo("p");
        });
    runner
        .withPropertyValues("AI_LOCATION=eu", "AI_EMBEDDING_LOCATION=europe-west3")
        .run(context ->
            assertThat(
                    context
                        .getEnvironment()
                        .getProperty("spring.ai.google.genai.embedding.location"))
                .isEqualTo("europe-west3"));
  }

  @Test
  void theEmbeddingConnectionIsOnlyBuiltWhenTheAssistantIsOn() {
    var configured =
        runner
            .withUserConfiguration(AiConfig.class)
            .withPropertyValues(
                "spring.ai.google.genai.embedding.project-id=p",
                "spring.ai.google.genai.embedding.location=europe-west3");
    configured.run(context ->
        assertThat(context)
            .doesNotHaveBean(
                org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails
                    .class));
    try (var google = mockStatic(GoogleCredentials.class)) {
      var credentials = mock(GoogleCredentials.class);
      when(credentials.createScoped(any(String[].class))).thenReturn(credentials);
      google.when(GoogleCredentials::getApplicationDefault).thenReturn(credentials);
      configured
          .withPropertyValues("mm.ai.enabled=true")
          .run(context -> {
            assertThat(context)
                .hasSingleBean(
                    org.springframework.ai.google.genai.embedding
                        .GoogleGenAiEmbeddingConnectionDetails.class);
            var details =
                context.getBean(
                    org.springframework.ai.google.genai.embedding
                        .GoogleGenAiEmbeddingConnectionDetails.class);
            assertThat(details.getProjectId()).isEqualTo("p");
            assertThat(details.getLocation()).isEqualTo("europe-west3");
          });
    }
  }
}
