package hu.lasterbergamot.mealmagician.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenCodesTest {

  @Test
  void aGeneratedTokenIs43Base64UrlCharacters() {
    assertThat(TokenCodes.generate()).hasSize(43).matches("[A-Za-z0-9_-]+");
  }

  @Test
  void twoGeneratedTokensDiffer() {
    assertThat(TokenCodes.generate()).isNotEqualTo(TokenCodes.generate());
  }

  @Test
  void aGeneratedTokenIsWellFormed() {
    assertThat(TokenCodes.isWellFormed(TokenCodes.generate())).isTrue();
  }

  @Test
  void hashIsSha256LowerCaseHex() {
    assertThat(TokenCodes.hash("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        .hasSize(64);
  }

  @Test
  void aMissingTokenHashesLikeAnEmptyOne() {
    assertThat(TokenCodes.hash(null)).isEqualTo(TokenCodes.hash(""));
  }

  @Test
  void aNullTokenIsNotWellFormed() {
    assertThat(TokenCodes.isWellFormed(null)).isFalse();
  }

  @Test
  void aTokenOfTheWrongLengthIsNotWellFormed() {
    assertThat(TokenCodes.isWellFormed("a".repeat(42))).isFalse();
    assertThat(TokenCodes.isWellFormed("a".repeat(44))).isFalse();
    assertThat(TokenCodes.isWellFormed("")).isFalse();
  }

  @Test
  void aTokenWithACharacterOutsideBase64UrlIsNotWellFormed() {
    assertThat(TokenCodes.isWellFormed("a".repeat(42) + "+")).isFalse();
    assertThat(TokenCodes.isWellFormed("a".repeat(42) + "/")).isFalse();
    assertThat(TokenCodes.isWellFormed("a".repeat(42) + " ")).isFalse();
    assertThat(TokenCodes.isWellFormed("a".repeat(43) + "\n")).isFalse();
  }
}
