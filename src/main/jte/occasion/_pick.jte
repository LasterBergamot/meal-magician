@import java.util.Map
@import hu.lasterbergamot.mealmagician.catalog.MealView
@import hu.lasterbergamot.mealmagician.planning.internal.OccasionPickPage
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param OccasionPickPage page
<%-- The content of the occasion's pick sheet (and of the no-JavaScript page): which meal goes on the
     menu. A copy of plan/_pick without the slot chip and the day. The search box refetches this same
     fragment and swaps only #occasion-pick-results (hx-select), so the box keeps focus while typing.
     Each library meal is a one-button form that posts to the menu; below the list the "Create and
     add" form makes a new meal. The texts are the plan's plan.pick.* keys. --%>
<div class="flex flex-col gap-4">
  @template.ui._search(id = "occasion-pick-q", name = "q", label = ctx.t("plan.pick.search.label"), value = page.query(), placeholder = ctx.t("plan.pick.search.placeholder"), disabled = false,
    attrs = Map.of("hx-get", page.pickHref(), "hx-trigger", "input changed delay:300ms, search", "hx-target", "#occasion-pick-results", "hx-select", "#occasion-pick-results", "hx-swap", "outerHTML", "hx-sync", "this:replace"))
  <div id="occasion-pick-results" class="flex flex-col gap-2">
    @if(page.libraryEmpty())
      <p class="text-sm text-ink-muted">${ctx.t("plan.pick.empty")}</p>
    @elseif(page.meals().isEmpty())
      <p class="text-sm text-ink-muted">${ctx.t("plan.pick.nomatch")}</p>
    @else
      <ul aria-label="${ctx.t("plan.pick.list.label")}" class="flex flex-col gap-2">
        @for(MealView meal : page.meals())
          <li>
            <form method="post" action="${page.menuAction()}" hx-post="${page.menuAction()}" class="contents">
              @if(ctx.csrf() != null)
                <input type="hidden" name="_csrf" value="${ctx.csrf().token()}">
              @endif
              <input type="hidden" name="mealId" value="${meal.id()}">
              <button type="submit" class="flex min-h-11 w-full items-center justify-between gap-3 rounded-md border-2 border-outline bg-surface-raised px-4 py-2 text-left font-extrabold text-ink shadow-sticker-1 hover:bg-brand-soft hover:text-brand-ink">
                <span class="min-w-0 break-words">${meal.name()}</span>
                @template.ui._icon(name = IconName.PLUS, size = 20, filled = false)
              </button>
            </form>
          </li>
        @endfor
      </ul>
      @if(page.more())
        <p class="text-sm text-ink-muted">${ctx.t("plan.pick.more", String.valueOf(OccasionPickPage.LIMIT))}</p>
      @endif
    @endif
  </div>
  @template.occasion._pick_quick(ctx = ctx, page = page)
</div>
