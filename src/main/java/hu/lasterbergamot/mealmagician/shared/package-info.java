/**
 * Shared kernel: small building blocks that every module may use, such as the application clock. It
 * is an <em>open</em> module, so other modules may depend on all of its types. Keep business logic
 * out of it; if something belongs to one domain, put it in that module instead.
 */
@ApplicationModule(displayName = "Shared kernel", type = ApplicationModule.Type.OPEN)
package hu.lasterbergamot.mealmagician.shared;

import org.springframework.modulith.ApplicationModule;
