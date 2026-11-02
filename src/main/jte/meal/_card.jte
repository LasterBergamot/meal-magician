@import hu.lasterbergamot.mealmagician.catalog.MealView
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param MealView meal
<%-- One meal in the list: its name (one stretched link to the detail page), a short look at the
     notes, its tags and an Archived badge. Recipe links are not shown here: a link inside the
     stretched link is not allowed (plan-ui-redesign.md section 5, Card). --%>
<li>
  @template.ui._card(variant = "default", content = @`
    <div class="flex items-start justify-between gap-2">
      <h2 class="min-w-0 break-words font-display text-xl font-semibold leading-7 text-ink"><a href="/meals/${meal.id()}" class="after:absolute after:inset-0 after:rounded-lg">${meal.name()}</a></h2>
      @if(meal.archived())
        @template.ui._badge(tone = "neutral", label = ctx.t("meal.archived"), icon = null)
      @endif
    </div>
    @if(meal.notes() != null)
      <p class="line-clamp-2 text-sm text-ink-muted">${meal.notes()}</p>
    @endif
    @if(!meal.tags().isEmpty())
      <ul class="flex flex-wrap gap-1.5">
        @for(String tag : meal.tags())
          <li>@template.ui._badge(tone = "brand", label = tag, icon = null)</li>
        @endfor
      </ul>
    @endif
  `)
</li>
