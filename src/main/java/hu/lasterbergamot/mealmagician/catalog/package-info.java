/**
 * The household's meal library: meals, their tags and the occasion types they suit. This package is
 * the module's public API; everything else goes in {@code internal}.
 *
 * <p>Two decisions worth knowing. {@link hu.lasterbergamot.mealmagician.catalog.OccasionType} is
 * owned here, not by {@code planning}, because planning already depends on the catalog and the
 * other direction would be a module cycle. And {@link
 * hu.lasterbergamot.mealmagician.catalog.RecentMealsProvider} is an interface the catalog declares
 * and {@code planning} implements, so the library can list recently planned meals first without
 * calling the planner.
 */
@ApplicationModule(displayName = "Catalog")
package hu.lasterbergamot.mealmagician.catalog;

import org.springframework.modulith.ApplicationModule;
