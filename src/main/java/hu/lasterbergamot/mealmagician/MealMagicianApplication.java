package hu.lasterbergamot.mealmagician;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of Meal Magician, a private meal planner for one household.
 *
 * <p>The app is a Spring Modulith <em>modular monolith</em>: each direct sub-package of this
 * package ({@code identity}, {@code household}, {@code catalog}, ...) is an application module.
 * Spring Boot scans from here, so every module must stay below this package.
 */
@SpringBootApplication
public class MealMagicianApplication {

  /**
   * Starts the application.
   *
   * @param args command-line arguments, passed on to Spring Boot unchanged
   */
  public static void main(String[] args) {
    SpringApplication.run(MealMagicianApplication.class, args);
  }
}
