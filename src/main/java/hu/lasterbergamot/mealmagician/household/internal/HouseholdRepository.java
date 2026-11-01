package hu.lasterbergamot.mealmagician.household.internal;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/**
 * Persistence for households. It is only ever called after a membership check ({@code
 * HouseholdService}); never expose {@code findById} to a request that has not passed one.
 */
public interface HouseholdRepository extends JpaRepository<Household, Long> {

  /**
   * Loads a household and holds a database lock on its row until the transaction ends. Changes to
   * who is in charge take it first, so two owners leaving or handing over at the same moment run
   * one after the other and the second sees the first one's result, instead of both finding "there
   * is another owner".
   *
   * @param id the household id
   * @return the household, or empty if it does not exist
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select h from Household h where h.id = :id")
  Optional<Household> findForUpdate(long id);
}
