package hu.lasterbergamot.mealmagician;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class MealMagicianApplicationTests {

  @Autowired private ApplicationContext context;

  @Test
  void contextStartsWithAllModulesWired() {
    assertThat(context.getBeansOfType(java.time.Clock.class)).hasSize(1);
  }
}
