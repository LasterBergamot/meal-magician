/**
 * The AI assistant (PLAN.md D32, Phase 7): drafts and suggestions from the household's own meal
 * history, produced by Gemini on Vertex AI. This package is the module's public API; everything
 * else goes in {@code internal}.
 *
 * <p>{@link hu.lasterbergamot.mealmagician.assistant.AssistantService} says whether the assistant
 * is on, reads a household's monthly usage and (#159) runs the week drafts: make one, refine it,
 * trim it, add an idea to the library, accept it into the plan or discard it. {@code internal}
 * holds the one class that talks to the model, the spend ledger, the meal search, the read-only
 * tools the model may call, the prompt builder and the stored drafts. The assistant is <strong>off
 * by default</strong> ({@code mm.ai.enabled=false}); while it is off nothing here calls Google,
 * needs credentials or answers a request.
 *
 * <p>Two exceptions are part of the API so that pages can turn them into a message: {@link
 * hu.lasterbergamot.mealmagician.assistant.SpendCapExceededException} (the household has used up
 * its monthly allowance; no call was made) and {@link
 * hu.lasterbergamot.mealmagician.assistant.ModelUnavailableException} (the call failed or timed
 * out; nothing was charged). Draft calls can also throw {@code InvalidRequestException} (text over
 * 1 200 characters), {@code DraftGenerationException} (the model's answer was unusable twice),
 * {@code DraftNotFoundException} and {@code DraftClosedException} (already accepted).
 */
@ApplicationModule(displayName = "Assistant")
package hu.lasterbergamot.mealmagician.assistant;

import org.springframework.modulith.ApplicationModule;
