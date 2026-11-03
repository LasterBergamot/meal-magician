package hu.lasterbergamot.mealmagician.feedback;

import static hu.lasterbergamot.mealmagician.household.HouseholdTestSupport.signedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import hu.lasterbergamot.mealmagician.planning.PlanningService;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import hu.lasterbergamot.mealmagician.planning.SlotView;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * The rating pages through the whole stack: the "How did it go?" card, its snooze, the rating form
 * and the slot sheet's rating section (#128, D27). Anna owns "Annas" with Cili as a plain member;
 * Bela owns "Belas" and must never reach Anna's slots (PLAN.md D8). Texts are the Hungarian ones.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class RatingPagesTest {

  @Autowired private MockMvc mvc;
  @Autowired private HouseholdService households;
  @Autowired private MealService meals;
  @Autowired private PlanningService planning;
  @Autowired private RatingService ratings;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private Clock clock;

  private long anna;
  private long bela;
  private long cili;
  private long annasHome;
  private long belasHome;
  private long mealId;
  private LocalDate today;
  private SlotView yesterdaysLunch;

  @BeforeEach
  void people() {
    anna = HouseholdTestSupport.insertUser(jdbc, "anna");
    bela = HouseholdTestSupport.insertUser(jdbc, "bela");
    cili = HouseholdTestSupport.insertUser(jdbc, "cili");
    annasHome = households.create(anna, "Annas").id();
    belasHome = households.create(bela, "Belas").id();
    jdbc.update(
        "insert into membership (household_id, user_id, role, joined_at)"
            + " values (?, ?, 'MEMBER', now())",
        annasHome,
        cili);
    mealId = meals.quickAdd(anna, annasHome, "Gulyásleves").id();
    today = LocalDate.now(clock);
    yesterdaysLunch =
        planning.add(anna, annasHome, today.minusDays(1), SlotType.LUNCH, mealId, null);
  }

  private ResultActions as(long userId, MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.with(signedInAs(userId)).with(csrf()));
  }

  private ResultActions htmx(long userId, MockHttpServletRequestBuilder request) throws Exception {
    return as(userId, request.header("HX-Request", "true"));
  }

  private String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  private String slotUrl(SlotView slot) {
    return "/feedback/slots/" + slot.id();
  }

  // ---- the prompt card ----

  @Test
  void thePromptOffersTheLatestUnratedMealWithALinkToItsSlotSheet() throws Exception {
    String card = body(htmx(anna, get("/feedback/prompt")).andExpect(status().isOk()));

    assertThat(card)
        .contains("Hogy sikerült?")
        .contains("Gulyásleves")
        .contains("href=\"/plan/slots/" + yesterdaysLunch.id() + "\"")
        .contains("hx-post=\"/feedback/prompt/snooze\"")
        .doesNotContain("<html");
  }

  @Test
  void thePromptIsEmptyWhenTheSnoozeCookieIsToday() throws Exception {
    htmx(anna, get("/feedback/prompt").cookie(new Cookie("mm_rating_snooze", today.toString())))
        .andExpect(status().isOk())
        .andExpect(content().string(""));
  }

  @Test
  void aSnoozeFromAnEarlierDayDoesNotHideThePrompt() throws Exception {
    htmx(
            anna,
            get("/feedback/prompt")
                .cookie(new Cookie("mm_rating_snooze", today.minusDays(1).toString())))
        .andExpect(content().string(containsString("Gulyásleves")));
  }

  @Test
  void thePromptIsEmptyOnceTheMemberRatedTheMeal() throws Exception {
    ratings.give(anna, annasHome, yesterdaysLunch.id(), new RatingInput(Thumb.UP, null, null));

    htmx(anna, get("/feedback/prompt")).andExpect(content().string(""));
  }

  @Test
  void thePromptStillAsksAnotherMemberWhoHasNotRated() throws Exception {
    ratings.give(anna, annasHome, yesterdaysLunch.id(), new RatingInput(Thumb.UP, null, null));

    htmx(cili, get("/feedback/prompt")).andExpect(content().string(containsString("Gulyásleves")));
  }

  @Test
  void thePromptIgnoresMealsThatAreStillToCome() throws Exception {
    jdbc.update("delete from meal_slot");
    planning.add(anna, annasHome, today.plusDays(3), SlotType.DINNER, mealId, null);

    htmx(anna, get("/feedback/prompt")).andExpect(content().string(""));
  }

  @Test
  void snoozingSetsADateOnlyCookieForTheRestOfTheDay() throws Exception {
    htmx(anna, post("/feedback/prompt/snooze"))
        .andExpect(status().isOk())
        .andExpect(content().string(""))
        .andExpect(header().string("Set-Cookie", containsString("mm_rating_snooze=" + today + ";")))
        .andExpect(header().string("Set-Cookie", containsString("Path=/")))
        .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")))
        .andExpect(header().string("Set-Cookie", containsString("Max-Age=86400")))
        .andExpect(header().string("Set-Cookie", not(containsString("Secure"))));
  }

  @Test
  void snoozingOverHttpsMarksTheCookieSecure() throws Exception {
    as(anna, post("/feedback/prompt/snooze").secure(true))
        .andExpect(header().string("Set-Cookie", containsString("Secure")));
  }

  // ---- the form ----

  @Test
  void theFormOfAPastSlotIsEmptyAndHasNoClearButton() throws Exception {
    String form = body(htmx(anna, get(slotUrl(yesterdaysLunch))).andExpect(status().isOk()));

    assertThat(form)
        .contains("id=\"rating-form\"")
        .contains("hx-target=\"this\"")
        .contains("name=\"thumb\" value=\"UP\"")
        .contains("name=\"thumb\" value=\"DOWN\"")
        .contains("Még senki más nem értékelte.")
        .doesNotContain("Értékelésem törlése")
        .doesNotContain("<html");
  }

  @Test
  void withoutHtmxTheFormIsAWholePageWithABackLink() throws Exception {
    as(anna, get(slotUrl(yesterdaysLunch)))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<html")))
        .andExpect(content().string(containsString("Gulyásleves")))
        .andExpect(content().string(containsString("id=\"rating-form\"")))
        .andExpect(content().string(containsString("Vissza a héthez")));
  }

  @Test
  void savingKeepsThumbStarsAndCommentAndAsksThePromptToReload() throws Exception {
    String form =
        body(
            htmx(
                    anna,
                    post(slotUrl(yesterdaysLunch))
                        .param("thumb", "UP")
                        .param("stars", "4")
                        .param("comment", "Finom"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Trigger", "ratingSaved")));

    assertThat(form)
        .contains("id=\"rating-form\"")
        .contains("Köszönjük, az értékelés mentve.")
        .contains("Értékelésem törlése")
        .containsPattern("name=\"thumb\" value=\"UP\" checked")
        .containsPattern("name=\"stars\" value=\"4\" checked")
        .contains(">Finom</textarea>");
    assertThat(ratings.forSlot(anna, annasHome, yesterdaysLunch.id()))
        .singleElement()
        .satisfies(rating -> {
          assertThat(rating.thumb()).isEqualTo(Thumb.UP);
          assertThat(rating.stars()).isEqualTo(4);
          assertThat(rating.comment()).isEqualTo("Finom");
        });
  }

  @Test
  void aSecondSaveChangesTheSameRatingAndSwitchingToNoStarsWorks() throws Exception {
    htmx(anna, post(slotUrl(yesterdaysLunch)).param("thumb", "UP").param("stars", "4"));
    htmx(anna, post(slotUrl(yesterdaysLunch)).param("thumb", "DOWN").param("stars", ""))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("name=\"thumb\" value=\"DOWN\" checked")));

    assertThat(ratings.forSlot(anna, annasHome, yesterdaysLunch.id()))
        .singleElement()
        .satisfies(rating -> {
          assertThat(rating.thumb()).isEqualTo(Thumb.DOWN);
          assertThat(rating.stars()).isNull();
        });
  }

  @Test
  void theSavedRatingIsPrefilledWhenTheFormIsOpenedAgain() throws Exception {
    ratings.give(anna, annasHome, yesterdaysLunch.id(), new RatingInput(Thumb.DOWN, 2, "Sós"));

    String form = body(htmx(anna, get(slotUrl(yesterdaysLunch))).andExpect(status().isOk()));

    assertThat(form)
        .containsPattern("name=\"thumb\" value=\"DOWN\" checked")
        .containsPattern("name=\"stars\" value=\"2\" checked")
        .contains(">Sós</textarea>")
        .contains("Értékelésem törlése");
  }

  @Test
  void aSaveWithoutAThumbIsRefusedAndKeepsTheTypedComment() throws Exception {
    String form =
        body(
            htmx(anna, post(slotUrl(yesterdaysLunch)).param("comment", "Majdnem"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(header().doesNotExist("HX-Trigger")));

    assertThat(form)
        .contains("id=\"rating-form\"")
        .contains("Válassz hüvelykujjat felfelé vagy lefelé.")
        .contains(">Majdnem</textarea>")
        .doesNotContain("Köszönjük");
    assertThat(ratings.forSlot(anna, annasHome, yesterdaysLunch.id())).isEmpty();
  }

  @Test
  void aThumbThatIsNeitherUpNorDownIsTreatedAsMissing() throws Exception {
    htmx(anna, post(slotUrl(yesterdaysLunch)).param("thumb", "SIDEWAYS"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().string(containsString("Válassz hüvelykujjat")));
  }

  @Test
  void starsOutOfRangeOrNotANumberAreRefusedWith422() throws Exception {
    for (String stars : new String[] {"9", "0", "abc", "99999999999"}) {
      htmx(anna, post(slotUrl(yesterdaysLunch)).param("thumb", "UP").param("stars", stars))
          .andExpect(status().isUnprocessableContent())
          .andExpect(content().string(containsString("A csillag 1 és 5 között lehet")));
    }
  }

  @Test
  void aCommentOf501CharactersIsRefusedAndKeptInTheForm() throws Exception {
    String comment = "x".repeat(501);

    htmx(anna, post(slotUrl(yesterdaysLunch)).param("thumb", "UP").param("comment", comment))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().string(containsString("legfeljebb 500 karakter lehet")))
        .andExpect(content().string(containsString(comment)));
  }

  @Test
  void aSlotAfterTodayCannotBeRatedYet() throws Exception {
    SlotView future =
        planning.add(anna, annasHome, today.plusDays(2), SlotType.DINNER, mealId, null);

    htmx(anna, post(slotUrl(future)).param("thumb", "UP"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().string(containsString("attól a naptól lehet értékelni")));
  }

  @Test
  void withoutHtmxASavedRatingAnswersWithTheWholePage() throws Exception {
    as(anna, post(slotUrl(yesterdaysLunch)).param("thumb", "UP"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<html")))
        .andExpect(content().string(containsString("Köszönjük")));
  }

  @Test
  void clearingRemovesTheRatingAndAsksThePromptToReload() throws Exception {
    ratings.give(anna, annasHome, yesterdaysLunch.id(), new RatingInput(Thumb.UP, 5, "Jó"));

    String form =
        body(
            htmx(anna, post(slotUrl(yesterdaysLunch) + "/clear"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Trigger", "ratingSaved")));

    assertThat(form).contains("id=\"rating-form\"").doesNotContain("Értékelésem törlése");
    assertThat(ratings.forSlot(anna, annasHome, yesterdaysLunch.id())).isEmpty();
  }

  @Test
  void clearingWhenThereIsNothingToClearStillShowsTheEmptyForm() throws Exception {
    htmx(anna, post(slotUrl(yesterdaysLunch) + "/clear"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("id=\"rating-form\"")));
  }

  // ---- the others ----

  @Test
  void otherMembersRatingsAreShownByNameNeverByEmail() throws Exception {
    ratings.give(cili, annasHome, yesterdaysLunch.id(), new RatingInput(Thumb.DOWN, 2, "Túl sós"));
    ratings.give(anna, annasHome, yesterdaysLunch.id(), new RatingInput(Thumb.UP, null, null));

    String form = body(htmx(anna, get(slotUrl(yesterdaysLunch))).andExpect(status().isOk()));

    assertThat(form)
        .contains("data-rating-author=\"cili\"")
        .contains("Túl sós")
        .doesNotContain("data-rating-author=\"anna\"")
        .doesNotContain("@example.com");
  }

  @Test
  void aRatingWithoutStarsOrCommentShowsOnlyTheNameAndThumb() throws Exception {
    ratings.give(cili, annasHome, yesterdaysLunch.id(), new RatingInput(Thumb.UP, null, null));

    String form = body(htmx(anna, get(slotUrl(yesterdaysLunch))));

    assertThat(form).contains("data-rating-author=\"cili\"").doesNotContain("Még senki más");
  }

  // ---- the plan's slot sheet ----

  @Test
  void theSlotSheetOfAPastSlotLoadsTheRatingSectionLazily() throws Exception {
    htmx(anna, get("/plan/slots/" + yesterdaysLunch.id()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("hx-get=\"" + slotUrl(yesterdaysLunch) + "\"")))
        .andExpect(content().string(containsString("hx-trigger=\"load\"")));
  }

  @Test
  void theSlotSheetOfTodaysSlotAlsoGetsTheRatingSection() throws Exception {
    SlotView now = planning.add(anna, annasHome, today, SlotType.DINNER, mealId, null);

    htmx(anna, get("/plan/slots/" + now.id()))
        .andExpect(content().string(containsString("id=\"rating-section\"")));
  }

  @Test
  void theSlotSheetOfAFutureSlotHasNoRatingSection() throws Exception {
    SlotView future =
        planning.add(anna, annasHome, today.plusDays(2), SlotType.DINNER, mealId, null);

    htmx(anna, get("/plan/slots/" + future.id()))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("rating-section"))))
        .andExpect(content().string(not(containsString("/feedback/slots/"))));
  }

  @Test
  void thePlanPageHasTheContainerThatLoadsThePrompt() throws Exception {
    as(anna, get("/plan"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("id=\"rating-prompt\"")))
        .andExpect(content().string(containsString("hx-get=\"/feedback/prompt\"")))
        .andExpect(content().string(containsString("ratingSaved from:body")));
  }

  // ---- another household ----

  @Test
  void anotherHouseholdsSlotIsNotFoundForEveryRatingRoute() throws Exception {
    as(bela, get(slotUrl(yesterdaysLunch))).andExpect(status().isNotFound());
    as(bela, post(slotUrl(yesterdaysLunch)).param("thumb", "UP")).andExpect(status().isNotFound());
    as(bela, post(slotUrl(yesterdaysLunch) + "/clear")).andExpect(status().isNotFound());

    assertThat(ratings.forSlot(anna, annasHome, yesterdaysLunch.id())).isEmpty();
  }

  @Test
  void anotherHouseholdsRatingsNeverReachThePrompt() throws Exception {
    long belasMeal = meals.quickAdd(bela, belasHome, "Belas titok").id();
    planning.add(bela, belasHome, today.minusDays(1), SlotType.DINNER, belasMeal, null);

    htmx(anna, get("/feedback/prompt"))
        .andExpect(content().string(not(containsString("Belas titok"))));
  }
}
