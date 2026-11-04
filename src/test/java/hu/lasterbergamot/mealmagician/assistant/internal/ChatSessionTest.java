package hu.lasterbergamot.mealmagician.assistant.internal;

import static org.assertj.core.api.Assertions.assertThat;

import hu.lasterbergamot.mealmagician.assistant.ChatTurn;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class ChatSessionTest {

  @Test
  void aNewSessionHasNoConversation() {
    assertThat(ChatSession.read(new MockHttpSession())).isEmpty();
  }

  @Test
  void aValueOfAnotherTypeUnderTheAttributeIsNoConversation() {
    var session = new MockHttpSession();
    session.setAttribute(ChatSession.ATTRIBUTE, "not a list");

    assertThat(ChatSession.read(session)).isEmpty();
  }

  @Test
  void foreignItemsInTheListAreSkipped() {
    var session = new MockHttpSession();
    List<Object> mixed = new ArrayList<>();
    mixed.add("stray");
    mixed.add(ChatTurn.user("kept"));
    session.setAttribute(ChatSession.ATTRIBUTE, mixed);

    assertThat(ChatSession.read(session)).containsExactly(ChatTurn.user("kept"));
  }

  @Test
  void aQuestionAndItsAnswerAreAppendedInOrder() {
    var session = new MockHttpSession();

    ChatSession.append(session, ChatTurn.user("egy"), ChatTurn.assistant("kettő"));

    assertThat(ChatSession.read(session))
        .containsExactly(ChatTurn.user("egy"), ChatTurn.assistant("kettő"));
  }

  @Test
  void theConversationKeepsOnlyTheLastTwentyTurns() {
    var session = new MockHttpSession();
    for (int i = 1; i <= 12; i++) {
      ChatSession.append(session, ChatTurn.user("q" + i), ChatTurn.assistant("a" + i));
    }

    List<ChatTurn> turns = ChatSession.read(session);
    assertThat(turns).hasSize(ChatSession.MAX_TURNS);
    assertThat(turns.getFirst()).isEqualTo(ChatTurn.user("q3"));
    assertThat(turns.getLast()).isEqualTo(ChatTurn.assistant("a12"));
  }

  @Test
  void clearingForgetsEverything() {
    var session = new MockHttpSession();
    ChatSession.append(session, ChatTurn.user("x"), ChatTurn.assistant("y"));

    ChatSession.clear(session);

    assertThat(ChatSession.read(session)).isEmpty();
  }

  @Test
  void theStoredTurnsAreSerialisable() throws Exception {
    var turns = List.of(ChatTurn.user("ő"), ChatTurn.assistant("ok"));
    var bytes = new java.io.ByteArrayOutputStream();
    try (var out = new java.io.ObjectOutputStream(bytes)) {
      out.writeObject(new ArrayList<>(turns));
    }
    try (var in =
        new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()))) {
      assertThat((List<?>) in.readObject()).isEqualTo(turns);
    }
  }
}
