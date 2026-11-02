@import hu.lasterbergamot.mealmagician.planning.internal.PlannedMeal
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param PlannedMeal meal
<%-- One planned meal: its name, an Archived badge when the meal left the library (the plan keeps
     it), and the slot's note. The whole item is a link to the slot editor: with JavaScript the Alpine
     sheetOpener opens the page-level slot sheet and htmx loads the editor into it; without it the link
     goes to the editor page. It sits inside a box that has a slot-* class, which gives bg-slot-soft
     and text-slot-ink their colours. data-slot-id is for the tests. --%>
<li data-slot-id="${meal.slotId()}"
    class="flex min-w-0 flex-col rounded-sm border-2 border-outline bg-slot-soft text-slot-ink">
  <a href="/plan/slots/${meal.slotId()}" x-data="sheetOpener" x-on:click="open" data-sheet="slot-sheet"
     hx-get="/plan/slots/${meal.slotId()}" hx-target="#slot-sheet-body"
     class="flex min-h-11 flex-col justify-center gap-1 rounded-sm px-2 py-1.5">
    <span class="sr-only">${ctx.t("plan.item.edit")}</span>
    <span class="break-words text-sm font-extrabold leading-5">${meal.mealName()}</span>
    @if(meal.archived())
      <span>@template.ui._badge(tone = "neutral", label = ctx.t("meal.archived"), icon = null)</span>
    @endif
    @if(meal.note() != null)
      <span class="break-words text-xs font-medium leading-4">${meal.note()}</span>
    @endif
  </a>
</li>
