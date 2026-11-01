package hu.lasterbergamot.mealmagician.household.internal;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers the {@link CurrentHouseholdArgumentResolver} with Spring MVC. */
@Configuration(proxyBeanMethods = false)
class HouseholdWebConfig implements WebMvcConfigurer {

  private final CurrentHouseholdArgumentResolver currentHousehold;

  HouseholdWebConfig(CurrentHouseholdArgumentResolver currentHousehold) {
    this.currentHousehold = currentHousehold;
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(currentHousehold);
  }
}
