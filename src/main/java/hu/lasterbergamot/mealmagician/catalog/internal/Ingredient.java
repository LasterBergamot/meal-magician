package hu.lasterbergamot.mealmagician.catalog.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An ingredient a household puts on its meals, such as "tojás" or "liszt": a name only, no amount.
 * Ingredients belong to one household: the same word in two households is two rows. {@code nameKey}
 * is the name with accents removed and in lower case, and is what makes "Tojás", "tojas" and "
 * TOJÁS " the same ingredient, while {@code name} keeps the spelling typed first.
 *
 * <p>The application never constructs an {@code Ingredient}: rows are inserted by {@link
 * IngredientRepository#insertIfAbsent} (through {@link NameWriter}) so that two people adding the
 * same new ingredient cannot fail each other, and are only read back as entities.
 */
@Entity
@Table(name = "ingredient")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ingredient {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "household_id", nullable = false, updatable = false)
  private long householdId;

  @Column(nullable = false, updatable = false)
  private String name;

  @Column(name = "name_key", nullable = false, updatable = false)
  private String nameKey;
}
