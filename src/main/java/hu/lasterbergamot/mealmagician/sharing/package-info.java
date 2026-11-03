/**
 * Read-only share links to one week of the plan. This package is the module's public API;
 * everything else goes in {@code internal}.
 *
 * <p>{@link hu.lasterbergamot.mealmagician.sharing.ShareLinkService} creates, lists and revokes
 * links for household members, and {@code open} turns a token into the shared week for the public
 * page without a signed-in person (PLAN.md D28). The public route (#129) and the management pages
 * (#131) call this package; both are merged.
 */
@ApplicationModule(displayName = "Sharing")
package hu.lasterbergamot.mealmagician.sharing;

import org.springframework.modulith.ApplicationModule;
