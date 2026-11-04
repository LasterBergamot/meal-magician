@import java.util.Map
@import hu.lasterbergamot.mealmagician.assistant.internal.DraftPage
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param DraftPage page
@param DraftPage.Item item
<%-- One idea of a draft (plan-ui-redesign.md Appendix D.3): a meal the model made up that is not in
     the library yet. "Not real yet" is drawn as a dashed outline with no shadow, the same cue as an
     empty slot. It carries the Idea badge with the wand, the slot chip, the name, the reason and the
     ingredients (all model text: escaped, plain), "Add to meals" and a remove button. An idea that is
     not added is left out when the draft is accepted. --%>
<li class="py-2">
  <div data-idea class="flex flex-col gap-2 rounded-lg border-2 border-dashed border-outline bg-surface-raised p-3">
    <div class="flex flex-wrap items-center gap-2">
      @template.ui._badge(tone = "brand", label = ctx.t("assistant.idea.badge"), icon = IconName.WAND)
      @template.ui._slot_chip(slot = item.slot(), label = ctx.t(item.slotLabelKey()))
    </div>
    <p class="break-words font-extrabold leading-6">${item.name()}</p>
    @if(item.reason() != null)
      <p class="whitespace-pre-line text-sm text-ink-muted">${item.reason()}</p>
    @endif
    @if(item.ingredients() != null)
      <p class="text-xs text-ink-muted">${ctx.t("assistant.idea.ingredients", item.ingredients())}</p>
    @endif
    <div class="flex items-center justify-between gap-2">
      @template.ui._button(variant = "secondary", size = "md", type = "button", label = ctx.t("assistant.idea.add"), icon = IconName.PLUS, busy = false, disabled = false,
        attrs = Map.of("hx-post", "/assistant/drafts/" + page.id() + "/items/" + item.id() + "/idea", "hx-target", "#assistant-sheet-body", "hx-swap", "innerHTML"))
      @template.ui._button(variant = "ghost", size = "icon", type = "button", label = ctx.t("assistant.item.remove", item.name()), icon = IconName.X, busy = false, disabled = false,
        attrs = Map.of("hx-post", "/assistant/drafts/" + page.id() + "/items/" + item.id() + "/remove", "hx-target", "#assistant-sheet-body", "hx-swap", "innerHTML"))
    </div>
  </div>
</li>
