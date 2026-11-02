@import java.util.Map
@import hu.lasterbergamot.mealmagician.identity.internal.AllowlistPage
@import hu.lasterbergamot.mealmagician.identity.internal.AllowlistRow
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param AllowlistPage page
<%-- The allowlist section (plan-ui-redesign.md section 8). htmx swaps all of it after an add or a
     remove, or a refused change (422 keeps the typed address and shows the message under the field,
     409 shows the bootstrap refusal). Two cards in a reading-width column: the add form (ui/_field +
     a primary button) and the list (a ui/_row per address: the address, a badge for where it comes
     from, when it was added, and for a removable one a danger button that opens a ui/_confirm). The
     confirm button posts the address held in a hidden input next to it (hx-include), so no address is
     ever pasted into an attribute value as JSON. Removing swaps this whole section, which closes the
     open dialog with it. --%>
<section id="allowlist" class="mx-auto flex w-full max-w-[var(--mm-reading)] flex-col gap-6">
  <div class="flex flex-col gap-2">
    <h1 class="font-display text-3xl font-semibold leading-9 text-ink">${ctx.t("admin.allowlist.heading")}</h1>
    <p class="text-base leading-6 text-ink-muted">${ctx.t("admin.allowlist.lead")}</p>
  </div>

  @template.ui._card(variant = "default", content = @`
    <h2 class="font-display text-[22px] font-semibold leading-7 text-ink">${ctx.t("admin.allowlist.add.heading")}</h2>
    <form class="flex flex-col gap-4" hx-post="/admin/allowlist" hx-target="#allowlist" hx-swap="outerHTML">
      @template.ui._field(id = "allowlist-email", name = "email", label = ctx.t("admin.allowlist.email.label"), type = "email", value = page.email(), hint = null, error = page.error() == null ? null : ctx.t(page.error()), required = true, disabled = false, attrs = Map.of("maxlength", "254", "autocomplete", "off"))
      <div>
        @template.ui._button(variant = "primary", size = "md", type = "submit", label = ctx.t("admin.allowlist.add"), icon = IconName.PLUS, busy = false, disabled = false, attrs = Map.of())
      </div>
    </form>
  `)

  @template.ui._card(variant = "default", content = @`
    <h2 class="font-display text-[22px] font-semibold leading-7 text-ink">${ctx.t("admin.allowlist.list.heading")}</h2>
    <ul class="-mx-3 flex flex-col">
      @for(AllowlistRow row : page.rows())
        @template.ui._row(selected = false, content = @`
          <div class="flex w-full flex-wrap items-center justify-between gap-x-4 gap-y-2 py-1">
            <div class="flex min-w-[12rem] flex-1 flex-col gap-1">
            <span class="break-all text-base font-extrabold leading-6 text-ink">${row.email()}</span>
            <span class="flex flex-wrap items-center gap-2">
              @template.ui._badge(tone = row.badgeTone(), label = ctx.t(row.originKey()), icon = null)
              @if(!row.added().isEmpty())
                <span class="text-xs font-bold leading-4 text-ink-muted">${row.added()}</span>
              @endif
            </span>
          </div>
          @if(row.removable())
            <input type="hidden" id="${row.domId()}-email" name="email" value="${row.email()}">
            @template.ui._confirm(id = row.domId() + "-confirm", title = ctx.t("admin.allowlist.remove.title", row.email()), message = ctx.t("admin.allowlist.remove.confirm"), cancelLabel = ctx.t("confirm.cancel"), confirmLabel = ctx.t("admin.allowlist.remove"),
              attrs = Map.of("hx-post", "/admin/allowlist/remove", "hx-include", "#" + row.domId() + "-email", "hx-target", "#allowlist", "hx-swap", "outerHTML"),
              trigger = @`
                @template.ui._button(variant = "danger", size = "md", type = "button", label = ctx.t("admin.allowlist.remove"), icon = IconName.TRASH, busy = false, disabled = false, attrs = Map.of("x-on:click", "show", "aria-label", ctx.t("admin.allowlist.remove.title", row.email())))
              `)
          @endif
          </div>
        `)
      @endfor
    </ul>
    <p class="text-sm leading-5 text-ink-muted">${ctx.t("admin.allowlist.hint")}</p>
  `)
</section>
