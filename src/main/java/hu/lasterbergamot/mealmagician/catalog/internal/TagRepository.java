package hu.lasterbergamot.mealmagician.catalog.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Persistence for tags. Tags are always looked up within one household. */
public interface TagRepository extends JpaRepository<Tag, Long> {

  /**
   * Finds a household's tag by its lower-case name, which is how two spellings of one tag meet.
   *
   * @param householdId the household
   * @param nameKey the lower-case name, as {@code MealRules.tagKey} produces it
   * @return the tag, or empty if the household has no tag with that name yet
   */
  Optional<Tag> findByHouseholdIdAndNameKey(long householdId, String nameKey);

  /**
   * The tags a household actually uses: those on at least one meal. A tag left behind by an edit is
   * not offered for filtering.
   *
   * @param householdId the household
   * @return the tags ordered by their lower-case name
   */
  @Query(
      """
      select t from Tag t
      where t.householdId = :householdId
        and exists (select mt.id from MealTag mt where mt.id.tagId = t.id)
      order by t.nameKey
      """)
  List<Tag> findInUse(long householdId);
}
