@import java.util.Map
@import hu.lasterbergamot.mealmagician.assistant.internal.DraftPage
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param DraftPage page
<%-- "Suggest my week", step 3: the draft (plan-ui-redesign.md Appendix D.3). Nothing here is in the
     plan yet. The days with items come first: library meals as rows, ideas as dashed cards (the
     model's names, reasons and ingredients are untrusted text, escaped by ${}). Then the follow-up
     form, and a sticky footer with the accept choice, Discard and the primary button.

     Accepting: "Fill empty slots" (MERGE) submits the form; "Replace the week" shows the confirm
     dialog instead, whose button posts mode=REPLACE. The choice is two radio buttons drawn like
     ui/_segmented (that component is a row of links and cannot post a value), and CSS picks which
     of the two buttons shows. Discard needs no confirmation: it removes nothing from the plan.

     The footer sticks at -bottom-5 because the sheet body has 1.25rem of bottom padding that sticky
     would otherwise leave visible under it.
     While a follow-up runs, the group/draft wrapper dims the days and shows the status line. --%>
<div id="assistant-draft" class="group/draft flex flex-col gap-5">
  <div class="flex flex-col gap-1">
    <h2 id="assistant-draft-title" tabindex="-1" data-autofocus="${!page.anyJustAdded()}" class="font-display text-xl font-semibold text-ink">${ctx.t("assistant.draft.title", page.range())}</h2>
    @if(page.summary() != null)
      <p class="whitespace-pre-line text-sm text-ink">${page.summary()}</p>
    @endif
    <p class="text-sm text-ink-muted">${ctx.t("assistant.draft.summary", String.valueOf(page.libraryCount()), String.valueOf(page.ideaCount()))}</p>
  </div>

  <div class="hidden group-has-[#assistant-refine-form.htmx-request]/draft:block">
    @template.assistant._waiting(ctx = ctx, skeleton = false, cancellable = false)
  </div>

  <div id="assistant-days" class="flex flex-col gap-4 group-has-[#assistant-refine-form.htmx-request]/draft:pointer-events-none group-has-[#assistant-refine-form.htmx-request]/draft:opacity-60">
    @if(page.empty())
      <p class="rounded-md border-2 border-dashed border-border-strong p-4 text-sm text-ink-muted">${ctx.t("assistant.draft.empty")}</p>
    @endif
    @for(DraftPage.Day day : page.days())
      <section aria-labelledby="${day.domId()}">
        @template.ui._card(variant = "default", content = @`
          <h3 id="${day.domId()}" class="font-display text-lg font-semibold text-ink">${day.label()}</h3>
          <ul class="flex flex-col">
            @for(DraftPage.Item item : day.items())
              @if(item.idea())
                @template.assistant._idea_card(ctx = ctx, page = page, item = item)
              @else
                @template.assistant._draft_item(ctx = ctx, page = page, item = item)
              @endif
            @endfor
          </ul>
        `)
      </section>
    @endfor
  </div>

  @template.assistant._refine(ctx = ctx, page = page)

  <form id="assistant-accept-form" hx-post="/assistant/drafts/${page.id()}/accept" hx-target="#assistant-sheet-body" hx-swap="innerHTML"
        class="group/accept sticky -bottom-5 -mx-5 -mb-5 flex flex-col gap-3 border-t-2 border-outline bg-surface-raised px-5 pt-3 pb-[calc(1.25rem+env(safe-area-inset-bottom))]">
    <fieldset class="flex flex-col gap-1.5">
      <legend class="text-sm font-extrabold leading-5 text-ink">${ctx.t("assistant.accept.mode")}</legend>
      <div class="inline-flex max-w-full flex-wrap gap-1 self-start rounded-full border-2 border-border-strong bg-surface-sunken p-1">
        <label class="group inline-flex min-h-11 cursor-pointer items-center gap-1.5 rounded-full border-2 border-transparent px-4 text-sm font-extrabold text-ink hover:bg-surface-raised has-[:checked]:border-outline has-[:checked]:bg-brand has-[:checked]:text-on-brand has-[:checked]:shadow-sticker-1 has-[:focus-visible]:outline-3 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-[var(--mm-focus-ring)]">
          <input id="assistant-mode-merge" type="radio" name="mode" value="MERGE" checked class="sr-only">
          <span class="hidden group-has-[:checked]:inline-flex">
            @template.ui._icon(name = IconName.CHECK, size = 20, filled = false)
          </span>
          <span>${ctx.t("assistant.accept.merge")}</span>
        </label>
        <label class="group inline-flex min-h-11 cursor-pointer items-center gap-1.5 rounded-full border-2 border-transparent px-4 text-sm font-extrabold text-ink hover:bg-surface-raised has-[:checked]:border-outline has-[:checked]:bg-brand has-[:checked]:text-on-brand has-[:checked]:shadow-sticker-1 has-[:focus-visible]:outline-3 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-[var(--mm-focus-ring)]">
          <input id="assistant-mode-replace" type="radio" name="mode" value="REPLACE" class="sr-only">
          <span class="hidden group-has-[:checked]:inline-flex">
            @template.ui._icon(name = IconName.CHECK, size = 20, filled = false)
          </span>
          <span>${ctx.t("assistant.accept.replace")}</span>
        </label>
      </div>
    </fieldset>
    <div class="flex items-center justify-between gap-3">
      @template.ui._button(variant = "ghost", size = "md", type = "button", label = ctx.t("assistant.discard"), icon = null, busy = false, disabled = false,
        attrs = Map.of("hx-post", "/assistant/drafts/" + page.id() + "/discard", "hx-target", "#assistant-sheet-body", "hx-swap", "innerHTML"))
      <span class="inline-flex group-has-[#assistant-mode-replace:checked]/accept:hidden">
        @template.ui._button(variant = "primary", size = "md", type = "submit", label = ctx.t("assistant.accept.submit"), icon = IconName.WAND, busy = false, disabled = !page.acceptable(), attrs = Map.of("id", "assistant-accept"))
      </span>
      <span class="hidden group-has-[#assistant-mode-replace:checked]/accept:inline-flex">
        @template.ui._confirm(id = "assistant-replace-confirm", title = ctx.t("assistant.accept.replace.confirm", page.range()), message = ctx.t("assistant.accept.replace.message"),
          cancelLabel = ctx.t("assistant.cancel"), confirmLabel = ctx.t("assistant.accept.replace"),
          attrs = Map.of("hx-post", "/assistant/drafts/" + page.id() + "/accept", "hx-target", "#assistant-sheet-body", "hx-swap", "innerHTML", "hx-vals", "{\"mode\":\"REPLACE\"}"),
          trigger = @`
            @template.ui._button(variant = "primary", size = "md", type = "button", label = ctx.t("assistant.accept.submit"), icon = IconName.WAND, busy = false, disabled = !page.acceptable(), attrs = Map.of("id", "assistant-accept-replace", "x-on:click", "show"))
          `)
      </span>
    </div>
  </form>
</div>
