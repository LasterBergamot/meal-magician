package hu.lasterbergamot.mealmagician.household.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CodeHasherTest {

  @Test
  void hashesToSha256Hex() {
    assertThat(CodeHasher.hash("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }

  @Test
  void aMissingCodeHashesLikeAnEmptyOne() {
    assertThat(CodeHasher.hash(null)).isEqualTo(CodeHasher.hash(""));
  }
}
