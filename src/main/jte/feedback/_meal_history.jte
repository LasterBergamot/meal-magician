@import hu.lasterbergamot.mealmagician.feedback.Thumb
@import hu.lasterbergamot.mealmagician.feedback.internal.MealHistoryPage
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param MealHistoryPage history
<%-- The body of a meal's "How it went" block (D27, #132). The meal page loads it over htmx into
     #meal-history-body, so this fragment holds only the body, not the heading. Thumbs up and down are
     counts with the icon and a screen-reader word; thumb down is the up icon turned upside down
     (rotate-180), as in feedback/_rate. The average is shown only when somebody gave stars. The rows are
     the newest ratings by display name; the page model never carries an e-mail address. A meal with no
     thumbs shows only the empty line. --%>
@if(!history.hasRatings())
  <p class="text-sm text-ink-muted">${ctx.t("rating.meal.empty")}</p>
@else
  <div class="flex flex-wrap items-center gap-2">
    <span class="inline-flex items-center gap-1.5 rounded-full border-2 border-outline bg-success-soft px-3 py-1 text-sm font-extrabold text-success-ink">
      @template.ui._icon(name = IconName.UP, size = 18, filled = false)
      <span data-count="up">${history.ups()}</span>
      <span class="sr-only">${ctx.t("rating.thumb.up")}</span>
    </span>
    <span class="inline-flex items-center gap-1.5 rounded-full border-2 border-outline bg-danger-soft px-3 py-1 text-sm font-extrabold text-danger-ink">
      <span class="inline-flex rotate-180">@template.ui._icon(name = IconName.UP, size = 18, filled = false)</span>
      <span data-count="down">${history.downs()}</span>
      <span class="sr-only">${ctx.t("rating.thumb.down")}</span>
    </span>
    @if(history.hasAverage())
      <span class="inline-flex items-center gap-1.5 rounded-full border-2 border-outline bg-accent-soft px-3 py-1 text-sm font-extrabold text-accent-ink">
        @template.ui._icon(name = IconName.STAR, size = 18, filled = false)
        <span data-average>${history.averageLabel()}</span>
        <span class="sr-only">${ctx.t("rating.meal.average.unit")}</span>
      </span>
    @endif
  </div>

  @if(!history.rows().isEmpty())
    <div class="flex flex-col gap-2">
      <h3 class="text-sm font-extrabold leading-5 text-ink">${ctx.t("rating.meal.latest")}</h3>
      <ul class="flex flex-col gap-2">
        @for(MealHistoryPage.Row row : history.rows())
          <li data-rating-author="${row.authorName()}" class="flex flex-col gap-1 rounded-md border-2 border-border bg-surface-raised p-3">
            <div class="flex flex-wrap items-center gap-2">
              <span class="font-extrabold text-ink">${row.authorName()}</span>
              <span class="text-sm text-ink-muted">${row.dateLabel()}</span>
              @if(row.thumb() == Thumb.UP)
                <span class="inline-flex items-center text-brand-ink">
                  @template.ui._icon(name = IconName.UP, size = 20, filled = false)
                  <span class="sr-only">${ctx.t("rating.thumb.up")}</span>
                </span>
              @else
                <span class="inline-flex items-center text-danger-ink">
                  <span class="inline-flex rotate-180">@template.ui._icon(name = IconName.UP, size = 20, filled = false)</span>
                  <span class="sr-only">${ctx.t("rating.thumb.down")}</span>
                </span>
              @endif
              @if(row.stars() != null)
                <span class="inline-flex items-center gap-1 text-sm font-extrabold text-accent-ink">
                  <span>${row.stars()}</span>
                  @template.ui._icon(name = IconName.STAR, size = 16, filled = false)
                  <span class="sr-only">${ctx.t("rating.stars.unit")}</span>
                </span>
              @endif
            </div>
            @if(row.comment() != null)
              <p class="break-words text-sm text-ink">${row.comment()}</p>
            @endif
          </li>
        @endfor
      </ul>
    </div>
  @endif
@endif
