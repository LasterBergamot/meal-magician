package hu.lasterbergamot.mealmagician.assistant.internal;

import hu.lasterbergamot.mealmagician.assistant.DraftStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One assistant draft of a week (table {@code assistant_draft}): who asked, for which week, what
 * was asked, the conversation so far and the model's one-line summary. The proposed meals are in
 * {@link DraftItemEntity}. A draft belongs to a household and is purged after seven days.
 *
 * <p>The status is moved from OPEN to ACCEPTED only by {@link DraftRepository#claim}, a single
 * conditional statement, so two accepts at the same moment cannot both win.
 */
@Entity
@Table(name = "assistant_draft")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DraftEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "household_id", nullable = false, updatable = false)
  private long householdId;

  @Column(name = "created_by", nullable = false, updatable = false)
  private long createdBy;

  @Column(name = "week_start", nullable = false, updatable = false)
  private LocalDate weekStart;

  @Enumerated(EnumType.STRING)
  // Never updated through the entity: only DraftRepository.claim changes it, atomically.
  @Column(nullable = false, updatable = false)
  private DraftStatus status;

  @Column(name = "request_text", nullable = false)
  private String requestText;

  @Column(nullable = false)
  private String conversation;

  private String summary;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Version private long version;

  /**
   * Creates an open draft.
   *
   * @param householdId the household
   * @param createdBy the {@code app_user} id of the person who asked
   * @param weekStart the Monday of the week
   * @param requestText the member's request (at most 1 200 characters)
   * @param conversation the turns so far as JSON
   * @param summary the model's summary, or {@code null}
   * @param createdAt when the draft was made
   */
  public DraftEntity(
      long householdId,
      long createdBy,
      LocalDate weekStart,
      String requestText,
      String conversation,
      String summary,
      Instant createdAt) {
    this.householdId = householdId;
    this.createdBy = createdBy;
    this.weekStart = weekStart;
    this.status = DraftStatus.OPEN;
    this.requestText = requestText;
    this.conversation = conversation;
    this.summary = summary;
    this.createdAt = createdAt;
  }

  /**
   * Replaces the conversation and summary after a follow-up request.
   *
   * @param conversation the turns so far, including the new ones, as JSON
   * @param summary the model's new summary, or {@code null}
   */
  public void refined(String conversation, String summary) {
    this.conversation = conversation;
    this.summary = summary;
  }
}
