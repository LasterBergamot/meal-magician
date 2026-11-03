/**
 * Ratings that members give to meals they ate; this history is what the later assistant learns
 * from. {@link hu.lasterbergamot.mealmagician.feedback.RatingService} is the public entry point:
 * give, clear and read ratings per slot and per meal, and find the next meal to ask about. It
 * publishes {@link hu.lasterbergamot.mealmagician.feedback.RatingGiven} and {@link
 * hu.lasterbergamot.mealmagician.feedback.RatingWithdrawn}. The module depends on {@code planning},
 * {@code catalog} and {@code household}, never the reverse (D27). This package is the module's
 * public API; everything else goes in {@code internal}.
 */
@ApplicationModule(displayName = "Feedback")
package hu.lasterbergamot.mealmagician.feedback;

import org.springframework.modulith.ApplicationModule;
