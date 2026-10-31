/**
 * Households, their members and roles, and e-mail-bound invites. Also resolves the household the
 * current request works on, which every other module uses to scope its data. This package is the
 * module's public API; everything else goes in {@code internal}.
 */
@ApplicationModule(displayName = "Household")
package hu.lasterbergamot.mealmagician.household;

import org.springframework.modulith.ApplicationModule;
