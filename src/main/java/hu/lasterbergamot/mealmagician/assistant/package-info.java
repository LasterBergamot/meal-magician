/**
 * The AI assistant (PLAN.md D32, Phase 7): drafts and suggestions from the household's own meal
 * history, produced by Gemini on Vertex AI. This package is the module's public API; everything
 * else goes in {@code internal}.
 *
 * <p>This first step (#157) only lays the foundation: {@link
 * hu.lasterbergamot.mealmagician.assistant.AssistantService} says whether the assistant is on and
 * reads a household's monthly usage, and {@code internal} holds the one class that talks to the
 * model, the spend ledger and the settings. The assistant is <strong>off by default</strong>
 * ({@code mm.ai.enabled=false}); while it is off nothing here calls Google, needs credentials or
 * answers a request.
 *
 * <p>Two exceptions are part of the API so that pages can turn them into a message: {@link
 * hu.lasterbergamot.mealmagician.assistant.SpendCapExceededException} (the household has used up
 * its monthly allowance; no call was made) and {@link
 * hu.lasterbergamot.mealmagician.assistant.ModelUnavailableException} (the call failed or timed
 * out; nothing was charged).
 */
@ApplicationModule(displayName = "Assistant")
package hu.lasterbergamot.mealmagician.assistant;

import org.springframework.modulith.ApplicationModule;
