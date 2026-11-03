package hu.lasterbergamot.mealmagician.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.feedback.internal.Rating;
import hu.lasterbergamot.mealmagician.feedback.internal.RatingRepository;
import hu.lasterbergamot.mealmagician.feedback.internal.RatingWriter;
import hu.lasterbergamot.mealmagician.household.HouseholdRole;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.MemberService;
import hu.lasterbergamot.mealmagician.household.MemberView;
import hu.lasterbergamot.mealmagician.planning.PlanningService;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import hu.lasterbergamot.mealmagician.planning.SlotView;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * The lost-race branch of {@link RatingService#give}: another tab inserted the row first, the
 * unique key refused ours, and the write is retried once. A real race is covered on Postgres
 * ({@code PostgresRatingTests}); this test drives the same branch deterministically with mocks.
 */
class RatingServiceRaceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-03T09:00:00Z"), ZoneId.of("Europe/Budapest"));

  private final SlotView slot =
      new SlotView(7, LocalDate.of(2026, 11, 2), SlotType.LUNCH, 0, 3, null, 0);
  private final RatingInput input = new RatingInput(Thumb.DOWN, 2, "cold");
  private final PlanningService planning = mock(PlanningService.class);
  private final MemberService members = mock(MemberService.class);
  private final RatingWriter writer = mock(RatingWriter.class);
  private final Rating saved = mock(Rating.class);

  private RatingService service() {
    when(planning.slot(1, 2, 7)).thenReturn(slot);
    when(saved.getId()).thenReturn(99L);
    when(saved.getUserId()).thenReturn(1L);
    when(members.list(2, 1))
        .thenReturn(List.of(new MemberView(1, "Anna", "anna@example.com", HouseholdRole.OWNER)));
    return new RatingService(
        mock(RatingRepository.class),
        planning,
        mock(MealService.class),
        mock(HouseholdService.class),
        members,
        mock(ApplicationEventPublisher.class),
        writer,
        CLOCK);
  }

  @Test
  void whenTheFirstWriteLosesTheRaceItIsRetriedOnce() {
    RatingService service = service();
    when(writer.upsert(1, 2, slot, input))
        .thenThrow(new DataIntegrityViolationException("uq_rating_slot_user"))
        .thenReturn(saved);

    RatingView view = service.give(1, 2, 7, input);

    verify(writer, times(2)).upsert(1, 2, slot, input);
    assertThat(view.id()).isEqualTo(99);
    assertThat(view.authorName()).isEqualTo("Anna");
  }
}
