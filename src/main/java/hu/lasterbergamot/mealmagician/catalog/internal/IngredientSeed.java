package hu.lasterbergamot.mealmagician.catalog.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One entry of the starter ingredient list that the picker suggests from. Global, read-only
 * reference data loaded by the migration: it belongs to no household, so it is the one catalog
 * table without a {@code household_id}. Nothing in the application writes to it.
 */
@Entity
@Table(name = "ingredient_seed")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IngredientSeed {

  @Id private Long id;

  @Column(nullable = false, updatable = false)
  private String name;

  @Column(name = "name_key", nullable = false, updatable = false)
  private String nameKey;
}
