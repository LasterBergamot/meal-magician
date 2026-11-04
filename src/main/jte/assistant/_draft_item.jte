@import java.util.Map
@import hu.lasterbergamot.mealmagician.assistant.internal.DraftPage
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param DraftPage page
@param DraftPage.Item item
<%-- One library meal of a draft (plan-ui-redesign.md Appendix D.3): a ui/_row with the slot chip,
     the meal's name, the model's reason (two lines at most; it is untrusted text, so ${} escapes it
     and whitespace-pre-line keeps its line breaks) and a 44 px remove button. An idea the member
     has just added to the library comes back as this row with a check and a note, and takes focus
     (data-autofocus; app.js focuses it after the swap). Remove only edits the draft: the plan is
     untouched until Accept. --%>
@template.ui._row(selected = false, content = @`
  @template.ui._slot_chip(slot = item.slot(), label = ctx.t(item.slotLabelKey()))
  <div class="min-w-0 flex-1" tabindex="-1" data-autofocus="${item.justAdded()}">
    <p class="break-words font-extrabold leading-6">${item.name()}</p>
    @if(item.justAdded())
      <p class="flex items-center gap-1 text-sm font-bold text-success-ink">
        @template.ui._icon(name = IconName.CHECK, size = 16, filled = false)
        <span>${ctx.t("assistant.idea.added")}</span>
      </p>
    @endif
    @if(item.reason() != null)
      <p class="line-clamp-2 whitespace-pre-line text-sm text-ink-muted">${item.reason()}</p>
    @endif
  </div>
  @template.ui._button(variant = "ghost", size = "icon", type = "button", label = ctx.t("assistant.item.remove", item.name()), icon = IconName.X, busy = false, disabled = false,
    attrs = Map.of("hx-post", "/assistant/drafts/" + page.id() + "/items/" + item.id() + "/remove", "hx-target", "#assistant-sheet-body", "hx-swap", "innerHTML"))
`)
