package hu.lasterbergamot.mealmagician.assistant.internal;

import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Wires the assistant's settings and, only when the assistant is on, the Google connection of the
 * embedding model.
 *
 * <p><strong>Why the embedding connection is built here.</strong> Spring AI's starter for chat is
 * switchable ({@code spring.ai.model.chat}), but its embedding connection auto-configuration
 * ({@code GoogleGenAiEmbeddingConnectionAutoConfiguration}) has no switch: it opens a Vertex AI
 * client at startup and fails without a project id and Google credentials. That would break the app
 * while the assistant is off, and the app must start with no Google credentials then (it also has
 * to boot fast on Cloud Run). {@code application.yml} therefore excludes that one
 * auto-configuration, and this class supplies the same bean under the same switch as everything
 * else, {@code mm.ai.enabled}. The embedding <em>model</em> itself is still created by Spring AI's
 * own auto-configuration from this connection.
 *
 * <p>Credentials are never configured: on Cloud Run the Google client uses the runtime service
 * account (Application Default Credentials), and locally {@code gcloud auth application-default
 * login}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
class AiConfig {

  /**
   * The embedding connection, present only while {@code mm.ai.enabled=true} and not in the {@code
   * fake-ai} profile (the scripted assistant of #160 opens no Google client).
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(prefix = "mm.ai", name = "enabled", havingValue = "true")
  @Profile("!fake-ai")
  @EnableConfigurationProperties(GoogleGenAiEmbeddingConnectionProperties.class)
  static class EmbeddingConnection {

    /**
     * The Vertex AI connection used by the embedding model.
     *
     * @param properties Spring AI's embedding connection settings
     * @return the connection; creating it opens the Google client
     */
    @Bean
    GoogleGenAiEmbeddingConnectionDetails googleGenAiEmbeddingConnectionDetails(
        GoogleGenAiEmbeddingConnectionProperties properties) {
      return GoogleGenAiEmbeddingConnectionDetails.builder()
          .projectId(properties.getProjectId())
          .location(properties.getLocation())
          .build();
    }
  }
}
