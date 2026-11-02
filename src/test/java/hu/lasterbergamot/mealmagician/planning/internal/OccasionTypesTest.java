package hu.lasterbergamot.mealmagician.planning.internal;

import static org.assertj.core.api.Assertions.assertThat;

import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class OccasionTypesTest {

  @Test
  void theKeyIsTheCatalogsLowerCaseLabelKey() {
    assertThat(OccasionTypes.key(OccasionType.BIRTHDAY)).isEqualTo("meal.occasion.birthday");
  }

  @Test
  void everyTypeHasALabelInBothBundles() throws IOException {
    Properties hu = bundle("messages_hu.properties");
    Properties en = bundle("messages_en.properties");
    for (OccasionType type : OccasionType.values()) {
      assertThat(hu.getProperty(OccasionTypes.key(type))).as("hu label of %s", type).isNotBlank();
      assertThat(en.getProperty(OccasionTypes.key(type))).as("en label of %s", type).isNotBlank();
    }
  }

  private static Properties bundle(String name) throws IOException {
    Properties properties = new Properties();
    try (InputStream in = OccasionTypesTest.class.getResourceAsStream("/" + name)) {
      assertThat(in).as("bundle %s on the classpath", name).isNotNull();
      properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
    return properties;
  }
}
