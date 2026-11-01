@import hu.lasterbergamot.mealmagician.household.internal.MemberRow
@import hu.lasterbergamot.mealmagician.household.internal.MembersPage
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param MembersPage page
<%-- The members section. htmx swaps all of it after a remove or hand-over, or a refused action. --%>
<section id="members" class="rounded-lg bg-white p-5 shadow-sm md:p-8">
  <h1 class="text-2xl font-bold text-brand-800">${ctx.t("household.members.heading", page.household().name())}</h1>
  @if(page.error() != null)
    <p class="mt-3 rounded bg-red-50 p-3 text-red-800" role="alert">${ctx.t(page.error())}</p>
  @endif
  <ul class="mt-4 divide-y divide-stone-200">
    @for(MemberRow row : page.rows())
      <li class="flex flex-wrap items-center justify-between gap-2 py-2">
        <span class="min-w-0">
          <span class="font-medium">${row.name()}</span>
          @if(row.self())
            <span class="text-sm text-stone-600">(${ctx.t("household.members.you")})</span>
          @endif
          <span class="block break-all text-sm text-stone-600">${row.email()}</span>
        </span>
        <span class="flex flex-wrap items-center gap-3 text-sm text-stone-600">
          <span>${row.owner() ? ctx.t("household.role.owner") : ctx.t("household.role.member")}</span>
          @if(row.manageable())
            @if(!row.owner())
              <button type="button" class="inline-flex min-h-11 items-center rounded border border-brand-700 px-4 font-semibold text-brand-800 hover:bg-brand-50"
                      hx-post="/households/members/${row.userId()}/transfer" hx-confirm="${ctx.t("household.members.transfer.confirm")}"
                      hx-target="#members" hx-swap="outerHTML">${ctx.t("household.members.transfer")}</button>
            @endif
            <button type="button" class="inline-flex min-h-11 items-center rounded border border-red-700 px-4 font-semibold text-red-800 hover:bg-red-50"
                    hx-post="/households/members/${row.userId()}/remove" hx-confirm="${ctx.t("household.members.remove.confirm")}"
                    hx-target="#members" hx-swap="outerHTML">${ctx.t("household.members.remove")}</button>
          @endif
        </span>
      </li>
    @endfor
  </ul>
  <div class="mt-6 border-t border-stone-200 pt-4">
    <button type="button" class="inline-flex min-h-11 items-center rounded border border-red-700 px-4 font-semibold text-red-800 hover:bg-red-50"
            hx-post="/households/members/leave" hx-confirm="${ctx.t("household.members.leave.confirm")}"
            hx-target="#members" hx-swap="outerHTML">${ctx.t("household.members.leave")}</button>
    @if(page.household().isOwner())
      <p class="mt-2 text-sm text-stone-600">${ctx.t("household.members.leave.hint")}</p>
    @endif
  </div>
</section>
