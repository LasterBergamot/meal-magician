package hu.lasterbergamot.mealmagician.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The starter ingredient list that V12 loads. The keys are written out literally in the migration,
 * so this test recomputes every one with {@link MealRules#ingredientKey}: a wrongly folded key
 * would make the suggestions miss the name.
 */
@SpringBootTest
@ActiveProfiles("dev")
class IngredientSeedTest {

  @Autowired private JdbcTemplate jdbc;

  @Test
  void everySeedKeyIsTheFoldedFormOfItsName() {
    List<Map<String, Object>> rows =
        jdbc.queryForList("select name, name_key from ingredient_seed");

    assertThat(rows).isNotEmpty();
    for (Map<String, Object> row : rows) {
      assertThat(row.get("name_key"))
          .as("key of %s", row.get("name"))
          .isEqualTo(MealRules.ingredientKey((String) row.get("name")));
      assertThat(((String) row.get("name")).length()).isLessThanOrEqualTo(MealRules.INGREDIENT_MAX);
    }
  }

  @Test
  void theSeedHasAboutAHundredAndFiftyNamesUniqueByKey() {
    Integer total = jdbc.queryForObject("select count(*) from ingredient_seed", Integer.class);
    Integer distinct =
        jdbc.queryForObject("select count(distinct name_key) from ingredient_seed", Integer.class);

    assertThat(total).isBetween(120, 180);
    assertThat(distinct).isEqualTo(total);
  }
}
