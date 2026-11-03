package hu.lasterbergamot.mealmagician.catalog.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
   * Inserts a tag unless the household already has one with the same key. A lost race between two
   * people adding the same new tag is therefore not an error and, unlike a failed insert, does not
   * abort a Postgres transaction. The row is not loaded into the persistence context: look it up
   * afterwards.
   *
   * @param householdId the household
   * @param name the name as typed first, already cleaned
   * @param nameKey the lower-case key of {@code name}
   * @return 1 if the row was inserted, 0 if the household already had it
   */
  @Modifying
  @Query(
      value =
          """
          insert into tag (household_id, name, name_key)
          values (:householdId, :name, :nameKey)
          on conflict do nothing
          """,
      nativeQuery = true)
  int insertIfAbsent(long householdId, String name, String nameKey);

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
