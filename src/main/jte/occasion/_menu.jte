@import java.util.Map
@import hu.lasterbergamot.mealmagician.planning.internal.MenuLine
@import hu.lasterbergamot.mealmagician.planning.internal.OccasionDetailPage
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param OccasionDetailPage page
<%-- The menu of an occasion, in order (#112, #113). Each meal links to its page; an archived meal
     says so with a badge, because it can still be on a menu. "Add a meal" opens the page-level pick
     sheet (occasion-pick-sheet, in detail.jte) through the Alpine sheetOpener; the link also works
     without JavaScript. A line has Move up and Move down forms (only where the move is possible, with
     no icon, because the UP icon is a thumbs-up) and a Remove button behind a confirm. Every success
     redirects to this page, which reloads, so no swap ever removes an open confirm. --%>
<div class="flex flex-wrap items-center justify-between gap-3">
  <h2 id="menu-title" class="font-display text-[22px] font-semibold leading-7 text-ink">${ctx.t("occasion.menu.heading")}</h2>
  <a id="occasion-add-meal" href="${page.pickHref()}" x-data="sheetOpener" x-on:click="open" data-sheet="occasion-pick-sheet"
     hx-get="${page.pickHref()}" hx-target="#occasion-pick-sheet-body"
     class="inline-flex min-h-11 items-center gap-2 rounded-md border-2 border-dashed border-border-strong px-4 font-extrabold text-brand hover:bg-brand-soft hover:text-brand-ink">
    @template.ui._icon(name = IconName.PLUS, size = 20, filled = false)
    <span>${ctx.t("occasion.menu.add")}</span>
  </a>
</div>
@if(page.menu().isEmpty())
  <p class="rounded-md border-2 border-dashed border-border-strong p-4 text-ink-muted">${ctx.t("occasion.menu.empty")}</p>
@else
  <ol aria-label="${ctx.t("occasion.menu.list.label")}" class="flex flex-col gap-2">
    @for(MenuLine line : page.menu())
      <li data-item-id="${line.itemId()}" class="flex flex-wrap items-center gap-2 rounded-md border-2 border-outline bg-surface-raised px-3 py-2">
        <span class="flex min-w-0 flex-1 flex-wrap items-center gap-2">
          <a href="${line.mealHref()}" class="min-w-0 break-words font-extrabold text-ink hover:text-brand">${line.mealName()}</a>
          @if(line.archived())
            @template.ui._badge(tone = "neutral", label = ctx.t("meal.archived"), icon = null)
          @endif
        </span>
        <div class="flex flex-wrap items-center gap-2">
          @if(line.canMoveUp())
            <form method="post" action="${page.moveAction(line.itemId())}" hx-post="${page.moveAction(line.itemId())}" class="contents">
              @if(ctx.csrf() != null)
                <input type="hidden" name="_csrf" value="${ctx.csrf().token()}">
              @endif
              <input type="hidden" name="position" value="${line.upPosition()}">
              @template.ui._button(variant = "secondary", size = "sm", type = "submit", label = ctx.t("occasion.menu.up"), icon = null, busy = false, disabled = false, attrs = Map.of())
            </form>
          @endif
          @if(line.canMoveDown())
            <form method="post" action="${page.moveAction(line.itemId())}" hx-post="${page.moveAction(line.itemId())}" class="contents">
              @if(ctx.csrf() != null)
                <input type="hidden" name="_csrf" value="${ctx.csrf().token()}">
              @endif
              <input type="hidden" name="position" value="${line.downPosition()}">
              @template.ui._button(variant = "secondary", size = "sm", type = "submit", label = ctx.t("occasion.menu.down"), icon = null, busy = false, disabled = false, attrs = Map.of())
            </form>
          @endif
          @template.ui._confirm(id = "remove-item-" + line.itemId(), title = ctx.t("occasion.menu.remove.title", line.mealName()), message = ctx.t("occasion.menu.remove.message"),
            cancelLabel = ctx.t("confirm.cancel"), confirmLabel = ctx.t("occasion.menu.remove.submit"),
            attrs = Map.of("hx-post", page.removeAction(line.itemId())),
            trigger = @`@template.ui._button(variant = "ghost", size = "sm", type = "button", label = ctx.t("occasion.menu.remove"), icon = IconName.TRASH, busy = false, disabled = false, attrs = Map.of("x-on:click", "show"))`)
        </div>
      </li>
    @endfor
  </ol>
@endif
