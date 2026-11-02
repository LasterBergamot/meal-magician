@import hu.lasterbergamot.mealmagician.planning.internal.MenuLine
@import hu.lasterbergamot.mealmagician.planning.internal.OccasionDetailPage
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param OccasionDetailPage page
<%-- The menu of an occasion, in order, read-only (#112). Each meal links to its page; an archived
     meal says so with a badge, because it can still be on a menu. #113 adds the move and remove
     actions to these rows. --%>
<h2 id="menu-title" class="font-display text-[22px] font-semibold leading-7 text-ink">${ctx.t("occasion.menu.heading")}</h2>
@if(page.menu().isEmpty())
  <p class="rounded-md border-2 border-dashed border-border-strong p-4 text-ink-muted">${ctx.t("occasion.menu.empty")}</p>
@else
  <ol class="flex flex-col gap-2">
    @for(MenuLine line : page.menu())
      <li data-item-id="${line.itemId()}" class="flex flex-wrap items-center gap-2 rounded-md border-2 border-outline bg-surface-raised px-3 py-2">
        <a href="${line.mealHref()}" class="font-extrabold text-ink hover:text-brand">${line.mealName()}</a>
        @if(line.archived())
          @template.ui._badge(tone = "neutral", label = ctx.t("meal.archived"), icon = null)
        @endif
      </li>
    @endfor
  </ol>
@endif
