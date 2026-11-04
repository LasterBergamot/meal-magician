package hu.lasterbergamot.mealmagician.assistant.internal;

import static org.assertj.core.api.Assertions.assertThat;

import hu.lasterbergamot.mealmagician.household.HouseholdRole;
import hu.lasterbergamot.mealmagician.household.MemberView;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Who is "member N" in a prompt. Plain values, no database. */
class PseudonymsTest {

  private static MemberView member(long id, String name) {
    return new MemberView(id, name, name.toLowerCase() + "@example.com", HouseholdRole.MEMBER);
  }

  @Test
  void membersAreNumberedInUserIdOrderWhateverTheListOrder() {
    var names =
        Pseudonyms.of(List.of(member(30, "Cili"), member(10, "Anna"), member(20, "Zsuzsa")));

    assertThat(names.label(10)).isEqualTo("member 1");
    assertThat(names.label(20)).isEqualTo("member 2");
    assertThat(names.label(30)).isEqualTo("member 3");
  }

  @Test
  void aPersonWhoLeftIsAFormerMemberNotAName() {
    var names = Pseudonyms.of(List.of(member(10, "Anna")));

    assertThat(names.label(99)).isEqualTo("a former member");
  }

  @Test
  void noDisplayNameOrAddressAppearsAnywhereInToString() {
    var names = Pseudonyms.of(List.of(member(10, "Anna"), member(20, "Zsuzsa")));

    assertThat(names.toString())
        .doesNotContain("Anna")
        .doesNotContain("Zsuzsa")
        .doesNotContain("example.com")
        .contains("member 1");
  }
}
