@import java.util.List
@import java.util.Map
@import hu.lasterbergamot.mealmagician.household.internal.HouseholdsPage
@import hu.lasterbergamot.mealmagician.household.internal.InviteRow
@import hu.lasterbergamot.mealmagician.shared.web.FormOption
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param HouseholdsPage page
<%-- The owner's invite card. htmx swaps this whole section after creating or revoking an invite.
     The link is only rendered in the response that creates the invite: the code is not stored.
     Revoking asks first in a ui/_confirm; the response replaces the section, which closes the dialog. --%>
<section id="invites">
  @template.ui._card(variant = "default", content = @`
    <h2 class="font-display text-[22px] font-semibold leading-7 text-ink">${ctx.t("household.invite.heading")}</h2>
    @if(page.invites().link() != null)
      @template.ui._alert(kind = "success", title = ctx.t("household.invite.link.heading", page.invites().linkEmail()), message = ctx.t("household.invite.link.hint"), action = @`
        <label for="invite-link" class="sr-only">${ctx.t("household.invite.link.label")}</label>
        <input id="invite-link" type="text" readonly value="${page.invites().link()}"
               class="block h-12 w-full rounded-md border-2 border-border-strong bg-surface-raised px-4 font-mono text-sm text-ink">
      `)
    @endif
    <form class="flex flex-col gap-4" hx-post="/households/invites" hx-target="#invites" hx-swap="outerHTML">
      @template.ui._field(id = "invite-email", name = "email", label = ctx.t("household.invite.email.label"), type = "email", value = page.invites().email(), hint = null, error = page.invites().error() == null ? null : ctx.t(page.invites().error()), required = true, disabled = false, attrs = Map.of("maxlength", "254", "autocomplete", "off"))
      @template.ui._select(id = "invite-expiry", name = "expiry", label = ctx.t("household.invite.expiry.label"), options = List.of(new FormOption("2h", ctx.t("household.invite.expiry.2h")), new FormOption("24h", ctx.t("household.invite.expiry.24h")), new FormOption("3d", ctx.t("household.invite.expiry.3d"))), selected = page.invites().expiry(), hint = null, error = null, disabled = false, attrs = Map.of())
      <div>
        @template.ui._button(variant = "primary", size = "md", type = "submit", label = ctx.t("household.invite.submit"), icon = null, busy = false, disabled = false, attrs = Map.of())
      </div>
    </form>

    <h3 class="mt-2 font-sans text-lg font-extrabold leading-6 text-ink">${ctx.t("household.invite.list.heading")}</h3>
    @if(page.invites().rows().isEmpty())
      <p class="rounded-md border-2 border-dashed border-border-strong p-4 text-ink-muted">${ctx.t("household.invite.list.empty")}</p>
    @else
      <ul class="flex flex-col rounded-md border-2 border-outline">
        @for(InviteRow row : page.invites().rows())
          @template.ui._row(selected = false, content = @`
            <div class="flex w-full flex-col gap-3 py-1 md:flex-row md:items-center">
              <div class="flex min-w-0 flex-1 flex-col gap-1.5">
                <span class="break-all font-sans text-base font-extrabold leading-6">${row.email()}</span>
                <span class="flex flex-wrap items-center gap-2">
                  @if(row.revocable())
                    @template.ui._badge(tone = "info", label = ctx.t(row.statusKey()), icon = null)
                  @elseif("household.invite.status.used".equals(row.statusKey()))
                    @template.ui._badge(tone = "success", label = ctx.t(row.statusKey()), icon = null)
                  @else
                    @template.ui._badge(tone = "neutral", label = ctx.t(row.statusKey()), icon = null)
                  @endif
                  <span class="text-sm text-ink-muted">${ctx.t("household.invite.until", row.expires())}</span>
                </span>
              </div>
              @if(row.revocable())
                <div class="flex">
                @template.ui._confirm(id = "revoke-" + row.id(), title = ctx.t("household.invite.revoke.title", row.email()), message = ctx.t("household.invite.revoke.confirm"), cancelLabel = ctx.t("confirm.cancel"), confirmLabel = ctx.t("household.invite.revoke"),
                  attrs = Map.of("hx-post", "/households/invites/" + row.id() + "/revoke", "hx-target", "#invites", "hx-swap", "outerHTML"),
                  trigger = @`@template.ui._button(variant = "danger", size = "md", type = "button", label = ctx.t("household.invite.revoke"), icon = IconName.TRASH, busy = false, disabled = false, attrs = Map.of("x-on:click", "show"))`)
                </div>
              @endif
            </div>
          `)
        @endfor
      </ul>
    @endif
  `)
</section>
