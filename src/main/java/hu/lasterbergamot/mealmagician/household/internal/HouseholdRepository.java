package hu.lasterbergamot.mealmagician.household.internal;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for households. It is only ever called after a membership check ({@code
 * HouseholdService}); never expose {@code findById} to a request that has not passed one.
 */
public interface HouseholdRepository extends JpaRepository<Household, Long> {}
