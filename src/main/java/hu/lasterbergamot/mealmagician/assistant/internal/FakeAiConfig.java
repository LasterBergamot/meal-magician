package hu.lasterbergamot.mealmagician.assistant.internal;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Wires a scripted model in place of Gemini when the {@code fake-ai} profile is active, so the
 * whole assistant (gateway, spend ledger, draft generator, controllers) runs for real and only the
 * model is faked. Used by the tests, by the browser tests (compose.e2e.yml) and by {@code task
 * serve} when someone wants to look at the assistant without a Google account.
 *
 * <p>The profile's own settings file ({@code application-fake-ai.yml}) switches the assistant on,
 * sets made-up prices and turns off Spring AI's Google auto-configuration. {@code
 * ProductionConfigGuard} refuses to start under {@code prod} with this profile.
 */
@Configuration(proxyBeanMethods = false)
@Profile("fake-ai")
class FakeAiConfig {

  /**
   * The scripted chat model.
   *
   * @param delay {@code mm.ai.fake.delay}: how long each call waits, so the waiting state can be
   *     seen (default none)
   * @return the model
   */
  @Bean
  ScriptedChatModel scriptedChatModel(@Value("${mm.ai.fake.delay:0s}") Duration delay) {
    return new ScriptedChatModel(delay);
  }

  /**
   * The scripted embedding model.
   *
   * @param properties the assistant settings, for the vector size
   * @return the model
   */
  @Bean
  ScriptedEmbeddingModel scriptedEmbeddingModel(AiProperties properties) {
    return new ScriptedEmbeddingModel(properties.embeddingDimensions());
  }
}
