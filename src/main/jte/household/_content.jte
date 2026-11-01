@import hu.lasterbergamot.mealmagician.household.HouseholdRole
@import hu.lasterbergamot.mealmagician.household.HouseholdSummary
@import hu.lasterbergamot.mealmagician.household.internal.HouseholdsPage
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param HouseholdsPage page
<section id="households" class="space-y-6">
  <div class="rounded-lg bg-white p-5 shadow-sm md:p-8">
    <h1 class="text-2xl font-bold text-brand-800">${ctx.t("household.heading")}</h1>
    @if(page.households().isEmpty())
      <p class="mt-2 text-stone-700">${ctx.t("household.empty")}</p>
    @else
      <ul class="mt-4 divide-y divide-stone-200">
        @for(HouseholdSummary household : page.households())
          <li class="flex flex-wrap items-center justify-between gap-2 py-2">
            <span class="font-medium">${household.name()}</span>
            <span class="flex items-center gap-3 text-sm text-stone-600">
              <span>${household.role() == HouseholdRole.OWNER ? ctx.t("household.role.owner") : ctx.t("household.role.member")}</span>
              @if(page.current() != null && page.current().id() == household.id())
                <span class="rounded bg-brand-100 px-2 py-1 font-semibold text-brand-800" aria-current="true">${ctx.t("household.current")}</span>
                <a href="/households/members" class="inline-flex min-h-11 items-center underline">${ctx.t("household.members.link")}</a>
              @else
                <button type="button" class="inline-flex min-h-11 items-center rounded border border-brand-700 px-4 font-semibold text-brand-800 hover:bg-brand-50"
                        hx-post="/households/${household.id()}/switch">${ctx.t("household.switch")}</button>
              @endif
            </span>
          </li>
        @endfor
      </ul>
    @endif
  </div>

  @if(page.current() != null && page.current().isOwner())
    @template.household._rename(ctx = ctx, page = page)
  @endif

  @if(page.invites() != null)
    @template.household._invites(ctx = ctx, page = page)
  @endif

  @template.household._create(ctx = ctx, page = page)
</section>
