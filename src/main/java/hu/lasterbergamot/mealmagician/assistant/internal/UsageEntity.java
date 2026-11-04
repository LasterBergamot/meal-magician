package hu.lasterbergamot.mealmagician.assistant.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One household's AI usage in one calendar month (table {@code assistant_usage}). The counters are
 * never changed by setting a field and saving: that would lose an update when two calls finish at
 * the same moment. {@link UsageRepository#add} does an atomic {@code x = x + n} instead, and this
 * entity is only used to read.
 */
@Entity
@Table(name = "assistant_usage")
@IdClass(UsageEntity.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UsageEntity {

  @Id
  @Column(name = "household_id", nullable = false, updatable = false)
  private long householdId;

  @Id
  // Quoted: MONTH is a reserved word in H2 (see V13__assistant_usage.sql).
  @Column(name = "\"month\"", nullable = false, updatable = false)
  private LocalDate month;

  @Column(name = "input_tokens", nullable = false, insertable = false, updatable = false)
  private long inputTokens;

  @Column(name = "output_tokens", nullable = false, insertable = false, updatable = false)
  private long outputTokens;

  @Column(name = "cost_micro_usd", nullable = false, insertable = false, updatable = false)
  private long costMicroUsd;

  /**
   * The composite primary key of {@link UsageEntity}.
   *
   * @param householdId the household
   * @param month the first day of the month
   */
  public record Key(long householdId, LocalDate month) implements Serializable {}
}
