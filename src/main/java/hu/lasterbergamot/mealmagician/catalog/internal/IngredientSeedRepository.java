package hu.lasterbergamot.mealmagician.catalog.internal;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Read access to the starter ingredient list. The list is global reference data with no household,
 * so nothing here is tenant-scoped; the caller decides which entries the household already has.
 */
public interface IngredientSeedRepository extends JpaRepository<IngredientSeed, Long> {

  /**
   * The seed entries whose key contains the typed text, those that start with it first.
   *
   * @param pattern a {@code LIKE} pattern over the key, escaped with {@code !}
   * @param prefixPattern a {@code LIKE} pattern that matches the same text only at the start
   * @param page how many to return
   * @return the best matches first
   */
  @Query(
      """
      select s from IngredientSeed s
      where s.nameKey like :pattern escape '!'
      order by case when s.nameKey like :prefixPattern escape '!' then 0 else 1 end, s.nameKey
      """)
  List<IngredientSeed> search(String pattern, String prefixPattern, Pageable page);
}
