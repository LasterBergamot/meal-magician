package hu.lasterbergamot.mealmagician.assistant.internal;

import hu.lasterbergamot.mealmagician.household.MemberView;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Who is "member 1", "member 2" ... in a prompt. The assistant never sends a display name or an
 * e-mail address to the model (PLAN.md D32): it talks about "member 2" instead. The numbers follow
 * the members' ids, so they stay the same from one request to the next as long as nobody joins or
 * leaves.
 *
 * <p>The record keeps only ids and labels. It is built from {@link MemberView}s but copies nothing
 * personal out of them, so even its {@code toString} cannot leak a name.
 *
 * @param labels the label of each current member, by {@code app_user} id
 */
public record Pseudonyms(Map<Long, String> labels) {

  /** What someone who is no longer in the household is called. */
  static final String FORMER_MEMBER = "a former member";

  /**
   * Numbers the current members of a household in id order.
   *
   * @param members the household's members, in any order
   * @return the labels "member 1" to "member n"
   */
  public static Pseudonyms of(List<MemberView> members) {
    List<Long> ids =
        members.stream().map(MemberView::userId).sorted(Comparator.naturalOrder()).toList();
    Map<Long, String> labels = new HashMap<>();
    for (int i = 0; i < ids.size(); i++) {
      labels.put(ids.get(i), "member " + (i + 1));
    }
    return new Pseudonyms(Map.copyOf(labels));
  }

  /**
   * The label for one person.
   *
   * @param userId the {@code app_user} id
   * @return "member N" for a current member, otherwise "a former member" (never a real name)
   */
  public String label(long userId) {
    return labels.getOrDefault(userId, FORMER_MEMBER);
  }
}
