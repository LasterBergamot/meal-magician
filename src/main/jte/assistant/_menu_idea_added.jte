@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param String name
<%-- The line that replaces an idea card once it is in the library (Appendix D.3's "added" state). It
     says nothing about the menu: the member still adds the meal from the library. --%>
<li class="flex items-center gap-2 rounded-lg border-2 border-outline bg-surface-raised px-3 py-2 text-ink">
  @template.ui._icon(name = IconName.CHECK, size = 20, filled = false)
  <span class="min-w-0 break-words font-extrabold">${name}</span>
  <span class="text-sm text-ink-muted">${ctx.t("assistant.idea.added")}</span>
</li>
