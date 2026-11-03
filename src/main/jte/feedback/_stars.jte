@import hu.lasterbergamot.mealmagician.feedback.internal.RatingForm
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param RatingForm form
<%-- The optional stars of a rating: a "no stars" choice and 1 to 5, all radio inputs named stars. Each
     choice is a 44 px touch target; the chosen one is filled with the accent tokens and has a check
     mark through its outline, so colour is never the only cue. The radios are visually hidden but
     focusable (peer), and the focus ring is drawn on the label next to them. --%>
<fieldset class="flex flex-col gap-2">
  <legend class="text-sm font-extrabold leading-5 text-ink">${ctx.t("rating.stars.legend")}</legend>
  <div class="flex flex-wrap gap-2">
    <label class="relative">
      <input type="radio" name="stars" value="" checked="${form.noStars()}" class="peer sr-only">
      <span class="inline-flex min-h-11 cursor-pointer items-center rounded-md border-2 border-border-strong bg-surface-raised px-3 text-sm font-extrabold text-ink peer-checked:border-outline peer-checked:bg-accent-soft peer-checked:text-accent-ink peer-focus-visible:outline-3 peer-focus-visible:outline-solid peer-focus-visible:outline-offset-2 peer-focus-visible:outline-focus-ring">${ctx.t("rating.stars.none")}</span>
    </label>
    @for(int count = 1; count <= 5; count++)
      <label class="relative">
        <input type="radio" name="stars" value="${count}" checked="${form.hasStars(count)}" class="peer sr-only">
        <span class="inline-flex min-h-11 min-w-11 cursor-pointer items-center justify-center gap-1 rounded-md border-2 border-border-strong bg-surface-raised px-3 text-sm font-extrabold text-ink peer-checked:border-outline peer-checked:bg-accent-soft peer-checked:text-accent-ink peer-focus-visible:outline-3 peer-focus-visible:outline-solid peer-focus-visible:outline-offset-2 peer-focus-visible:outline-focus-ring">
          <span>${count}</span>
          @template.ui._icon(name = IconName.STAR, size = 18, filled = false)
          <span class="sr-only">${ctx.t("rating.stars.unit")}</span>
        </span>
      </label>
    @endfor
  </div>
</fieldset>
