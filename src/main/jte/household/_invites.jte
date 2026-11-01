@import hu.lasterbergamot.mealmagician.household.internal.HouseholdsPage
@import hu.lasterbergamot.mealmagician.household.internal.InviteRow
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param HouseholdsPage page
<%-- The owner's invite section. htmx swaps this whole section after creating or revoking an invite.
     The link is only rendered in the response that creates the invite: the code is not stored. --%>
<section id="invites" class="rounded-lg bg-white p-5 shadow-sm md:p-8">
  <h2 class="text-xl font-bold text-brand-800">${ctx.t("household.invite.heading")}</h2>
  @if(page.invites().link() != null)
    <div class="mt-3 rounded bg-brand-50 p-3" role="status">
      <p class="font-medium">${ctx.t("household.invite.link.heading", page.invites().linkEmail())}</p>
      <label for="invite-link" class="sr-only">${ctx.t("household.invite.link.label")}</label>
      <input id="invite-link" type="text" readonly value="${page.invites().link()}"
             class="mt-2 block min-h-11 w-full rounded border border-stone-400 bg-white px-3 font-mono text-sm">
      <p class="mt-2 text-sm text-stone-700">${ctx.t("household.invite.link.hint")}</p>
    </div>
  @endif
  <form class="mt-4" hx-post="/households/invites" hx-target="#invites" hx-swap="outerHTML">
    <label for="invite-email" class="block font-medium">${ctx.t("household.invite.email.label")}</label>
    <input id="invite-email" name="email" type="email" maxlength="254" required autocomplete="off" value="${page.invites().email()}"
           class="mt-1 block min-h-11 w-full rounded border border-stone-400 px-3">
    <label for="invite-expiry" class="mt-3 block font-medium">${ctx.t("household.invite.expiry.label")}</label>
    <select id="invite-expiry" name="expiry" class="mt-1 block min-h-11 w-full rounded border border-stone-400 bg-white px-3">
      <option value="2h" selected="${"2h".equals(page.invites().expiry())}">${ctx.t("household.invite.expiry.2h")}</option>
      <option value="24h" selected="${"24h".equals(page.invites().expiry())}">${ctx.t("household.invite.expiry.24h")}</option>
      <option value="3d" selected="${"3d".equals(page.invites().expiry())}">${ctx.t("household.invite.expiry.3d")}</option>
    </select>
    @if(page.invites().error() != null)
      <p class="mt-2 rounded bg-red-50 p-3 text-red-800" role="alert">${ctx.t(page.invites().error())}</p>
    @endif
    <button type="submit" class="mt-4 inline-flex min-h-11 items-center justify-center rounded bg-brand-700 px-5 py-2 font-semibold text-white hover:bg-brand-800">${ctx.t("household.invite.submit")}</button>
  </form>

  <h3 class="mt-6 text-lg font-semibold">${ctx.t("household.invite.list.heading")}</h3>
  @if(page.invites().rows().isEmpty())
    <p class="mt-2 text-stone-700">${ctx.t("household.invite.list.empty")}</p>
  @else
    <ul class="mt-2 divide-y divide-stone-200">
      @for(InviteRow row : page.invites().rows())
        <li class="flex flex-wrap items-center justify-between gap-2 py-2">
          <span class="min-w-0 break-all font-medium">${row.email()}</span>
          <span class="flex flex-wrap items-center gap-3 text-sm text-stone-600">
            <span>${ctx.t(row.statusKey())}</span>
            <span>${ctx.t("household.invite.until", row.expires())}</span>
            @if(row.revocable())
              <button type="button" class="inline-flex min-h-11 items-center rounded border border-red-700 px-4 font-semibold text-red-800 hover:bg-red-50"
                      hx-post="/households/invites/${row.id()}/revoke" hx-target="#invites" hx-swap="outerHTML">${ctx.t("household.invite.revoke")}</button>
            @endif
          </span>
        </li>
      @endfor
    </ul>
  @endif
</section>
