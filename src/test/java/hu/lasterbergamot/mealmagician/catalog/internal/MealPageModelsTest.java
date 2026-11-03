package hu.lasterbergamot.mealmagician.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import hu.lasterbergamot.mealmagician.catalog.InvalidMealException;
import hu.lasterbergamot.mealmagician.catalog.MealProblem;
import hu.lasterbergamot.mealmagician.catalog.MealSearch;
import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.MealView;
import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import hu.lasterbergamot.mealmagician.catalog.RecentMealsProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/** The small page models and the list factory of the meal pages, without a web layer. */
class MealPageModelsTest {

  private static MealView meal(long id, String name) {
    return new MealView(id, name, null, null, List.of(), List.of(), false, 0, List.of());
  }

  // ---- MealFilter ----

  @Test
  void aFilterTrimsItsTextAndKnowsOnlyTwoSorts() {
    MealFilter filter = MealFilter.of("  soup ", " vegan", false, "nonsense");

    assertThat(filter.q()).isEqualTo("soup");
    assertThat(filter.tag()).isEqualTo("vegan");
    assertThat(filter.sort()).isEqualTo(MealFilter.RECENT);
    assertThat(MealFilter.of(null, null, false, "name").sort()).isEqualTo(MealFilter.NAME);
    assertThat(MealFilter.of(null, null, false, null).q()).isEmpty();
  }

  @Test
  void aFilterIsActiveWhenAnythingNarrowsTheList() {
    assertThat(MealFilter.of(null, null, false, null).active()).isFalse();
    assertThat(MealFilter.of("a", null, false, null).active()).isTrue();
    assertThat(MealFilter.of(null, "a", false, null).active()).isTrue();
    assertThat(MealFilter.of(null, null, true, null).active()).isTrue();
  }

  @Test
  void linksKeepTheSearchTextAndLeaveDefaultsOut() {
    MealFilter filter = MealFilter.of("crème & sugar", "", false, null);

    assertThat(filter.href()).isEqualTo("/meals?q=cr%C3%A8me%20%26%20sugar");
    assertThat(MealFilter.of(null, null, false, null).href()).isEqualTo("/meals");
    assertThat(filter.hrefSort(MealFilter.NAME))
        .isEqualTo("/meals?q=cr%C3%A8me%20%26%20sugar&sort=name");
    assertThat(filter.hrefToggleArchived()).endsWith("&archived=true");
    assertThat(filter.hrefClear()).isEqualTo("/meals");
  }

  @Test
  void pressingTheActiveTagAgainClearsItAndAnotherTagReplacesIt() {
    MealFilter filter = MealFilter.of(null, "Vegan", false, null);

    assertThat(filter.hrefToggleTag("vegan")).isEqualTo("/meals");
    assertThat(filter.hrefToggleTag("Quick")).isEqualTo("/meals?tag=Quick");
  }

  // ---- MealsPage ----

  @Test
  void onlyAnUnfilteredEmptyListIsAnEmptyLibrary() {
    MealFilter none = MealFilter.of(null, null, false, null);
    MealFilter searched = MealFilter.of("x", null, false, null);

    assertThat(new MealsPage(none, List.of(), List.of(), "").libraryEmpty()).isTrue();
    assertThat(new MealsPage(searched, List.of(), List.of(), "").libraryEmpty()).isFalse();
    assertThat(new MealsPage(none, List.of(meal(1, "a")), List.of(), "").libraryEmpty()).isFalse();
  }

  // ---- MealsPageFactory ----

  @SuppressWarnings("unchecked")
  private static ObjectProvider<RecentMealsProvider> provider(RecentMealsProvider value) {
    ObjectProvider<RecentMealsProvider> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(value);
    return provider;
  }

  @Test
  void withoutAPlannerTheRecentSortListsTheNewestFirst() {
    MealService meals = mock(MealService.class);
    when(meals.search(1, 2, new MealSearch("", "", false)))
        .thenReturn(List.of(meal(1, "a"), meal(3, "c"), meal(2, "b")));
    when(meals.tagNames(1, 2)).thenReturn(List.of("vegan"));

    MealsPage page =
        new MealsPageFactory(meals, provider(null))
            .page(1, 2, MealFilter.of(null, null, false, null), "");

    assertThat(page.meals()).extracting(MealView::id).containsExactly(3L, 2L, 1L);
    assertThat(page.tags()).containsExactly("vegan");
  }

  @Test
  void recentlyPlannedMealsComeFirstInPlannedOrderAndAMealPlannedTwiceCountsOnce() {
    MealService meals = mock(MealService.class);
    when(meals.search(1, 2, new MealSearch("", "", false)))
        .thenReturn(List.of(meal(1, "a"), meal(2, "b"), meal(3, "c")));
    RecentMealsProvider recent = mock(RecentMealsProvider.class);
    when(recent.recentMealIds(1, 2, MealsPageFactory.RECENT_LIMIT)).thenReturn(List.of(1L, 2L, 1L));

    MealsPage page =
        new MealsPageFactory(meals, provider(recent))
            .page(1, 2, MealFilter.of(null, null, false, null), "");

    assertThat(page.meals()).extracting(MealView::id).containsExactly(1L, 2L, 3L);
  }

  @Test
  void theNameSortKeepsTheServicesOrderAndNeverAsksThePlanner() {
    MealService meals = mock(MealService.class);
    when(meals.search(1, 2, new MealSearch("", "", false)))
        .thenReturn(List.of(meal(1, "a"), meal(3, "c")));
    RecentMealsProvider recent = mock(RecentMealsProvider.class);

    MealsPage page =
        new MealsPageFactory(meals, provider(recent))
            .page(1, 2, MealFilter.of(null, null, false, "name"), "");

    assertThat(page.meals()).extracting(MealView::id).containsExactly(1L, 3L);
    verifyNoInteractions(recent);
  }

  // ---- MealForm ----

  @Test
  void aStoredMealFillsTheFormAndTheTagsBecomeOneText() {
    MealView stored =
        new MealView(
            7,
            "Cake",
            "n",
            "https://x.test",
            List.of("a", "b"),
            List.of(OccasionType.PARTY),
            false,
            3,
            List.of("egg"));

    MealForm form = MealForm.of(stored);

    assertThat(form.editing()).isTrue();
    assertThat(form.version()).isEqualTo(3);
    assertThat(form.tags()).isEqualTo("a, b");
    assertThat(form.has(OccasionType.PARTY)).isTrue();
    assertThat(form.has(OccasionType.OTHER)).isFalse();
  }

  @Test
  void missingNotesAndLinkBecomeEmptyTexts() {
    MealForm form = MealForm.of(meal(1, "Toast"));

    assertThat(form.notes()).isEmpty();
    assertThat(form.recipeUrl()).isEmpty();
  }

  @Test
  void theTypedTagsAreSplitAtCommasAndBlankPiecesDropped() {
    MealForm form = new MealForm(null, 0, "x", "", "", " a , ,b,, ", java.util.Set.of());

    assertThat(form.details().tags()).containsExactly("a", "b");
    assertThat(MealForm.blank().editing()).isFalse();
    assertThat(MealForm.named("Soup").name()).isEqualTo("Soup");
    assertThat(MealForm.blank().details().tags()).isEmpty();
  }

  // ---- MealFormPage and QuickAdd ----

  @Test
  void everyProblemPointsAtItsOwnField() {
    MealForm form = MealForm.blank();

    assertThat(rejection(form, MealProblem.NAME_REQUIRED).error("name")).isNotNull();
    assertThat(rejection(form, MealProblem.NAME_TOO_LONG).error("name")).isNotNull();
    assertThat(rejection(form, MealProblem.NOTES_TOO_LONG).error("notes")).isNotNull();
    assertThat(rejection(form, MealProblem.RECIPE_URL_INVALID).error("recipeUrl")).isNotNull();
    assertThat(rejection(form, MealProblem.TAG_INVALID).error("tags")).isNotNull();
    assertThat(rejection(form, MealProblem.TAG_INVALID).error("name")).isNull();
    assertThat(rejection(form, MealProblem.INGREDIENT_INVALID).error("ingredients")).isNotNull();
    assertThat(form.details().ingredients()).isEmpty();
    assertThat(MealFormPage.of(form).conflict()).isFalse();
    assertThat(MealFormPage.stale(form).conflict()).isTrue();
  }

  private static MealFormPage rejection(MealForm form, MealProblem problem) {
    return MealFormPage.rejected(form, new InvalidMealException(problem));
  }

  @Test
  void theFormPostsAndGoesBackToTheMealWhenEditingOtherwiseToTheList() {
    MealForm editing = MealForm.of(meal(7, "Cake"));

    assertThat(editing.action()).isEqualTo("/meals/7");
    assertThat(editing.backHref()).isEqualTo("/meals/7");
    assertThat(MealForm.blank().action()).isEqualTo("/meals");
    assertThat(MealForm.blank().backHref()).isEqualTo("/meals");
  }

  @Test
  void everyOccasionTypeHasALabelInBothLanguages() throws java.io.IOException {
    for (String bundle : List.of("/messages_hu.properties", "/messages_en.properties")) {
      java.util.Properties labels = new java.util.Properties();
      try (var in = MealPageModelsTest.class.getResourceAsStream(bundle);
          var reader = new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)) {
        labels.load(reader);
      }
      for (OccasionType type : OccasionType.values()) {
        assertThat(labels.getProperty(OccasionLabels.key(type)))
            .as("%s in %s", type, bundle)
            .isNotBlank();
      }
    }
  }

  @Test
  void aQuickAddFormKnowsWhichVariantItIs() {
    assertThat(QuickAdd.empty(QuickAdd.SHEET).inline()).isFalse();
    assertThat(QuickAdd.empty(QuickAdd.BAR).inline()).isTrue();
    assertThat(QuickAdd.variantOf(QuickAdd.SHEET)).isEqualTo(QuickAdd.SHEET);
    assertThat(QuickAdd.variantOf("anything else")).isEqualTo(QuickAdd.BAR);
    assertThat(QuickAdd.variantOf(null)).isEqualTo(QuickAdd.BAR);
  }
}
