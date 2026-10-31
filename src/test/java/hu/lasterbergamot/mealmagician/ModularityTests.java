package hu.lasterbergamot.mealmagician;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTests {

  private final ApplicationModules modules = ApplicationModules.of(MealMagicianApplication.class);

  @Test
  void modulesOnlyUseEachOthersPublicApi() {
    modules.verify();
  }

  @Test
  void generatesModuleDocumentation() {
    new Documenter(modules).writeDocumentation();
  }
}
