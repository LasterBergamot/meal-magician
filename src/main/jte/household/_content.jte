@import hu.lasterbergamot.mealmagician.household.HouseholdRole
@import hu.lasterbergamot.mealmagician.household.HouseholdSummary
@import hu.lasterbergamot.mealmagician.household.internal.HouseholdsPage
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param HouseholdsPage page
<%-- The households page (plan-ui-redesign.md section 8): the h1, a card of rows (one per household,
     the current one selected with a Current badge and a check), then the owner's rename and invite
     cards and the create card. Switching households lives in the app shell's switcher, so a row
     has no switch button. The Members link sits on the current row only. --%>
<section id="households" class="flex flex-col gap-6">
  <h1 class="font-display text-3xl font-semibold text-ink">${ctx.t("household.heading")}</h1>

  @if(page.households().isEmpty())
    @template.ui._empty(icon = IconName.HOME, heading = ctx.t("household.empty.heading"), message = ctx.t("household.empty"), action = null)
  @else
    @template.ui._card(variant = "default", content = @`
      <ul class="flex flex-col">
        @for(HouseholdSummary household : page.households())
          @template.ui._row(selected = page.current() != null && page.current().id() == household.id(), content = @`
            <div class="flex min-w-0 flex-1 flex-col gap-1.5 py-1">
              <span class="break-words font-sans text-lg font-extrabold leading-6">${household.name()}</span>
              <span class="flex flex-wrap items-center gap-2">
                @if(household.role() == HouseholdRole.OWNER)
                  @template.ui._badge(tone = "brand", label = ctx.t("household.role.owner"), icon = null)
                @else
                  @template.ui._badge(tone = "neutral", label = ctx.t("household.role.member"), icon = null)
                @endif
                @if(page.current() != null && page.current().id() == household.id())
                  <span aria-current="true">
                    @template.ui._badge(tone = "brand", label = ctx.t("household.current"), icon = IconName.CHECK)
                  </span>
                @endif
              </span>
            </div>
            @if(page.current() != null && page.current().id() == household.id())
              <a href="/households/members"
                 class="inline-flex min-h-11 shrink-0 items-center gap-1 rounded-md px-3 font-sans text-sm font-extrabold text-brand-ink hover:bg-surface-sunken">
                ${ctx.t("household.members.link")}
                @template.ui._icon(name = IconName.RIGHT, size = 20, filled = false)
              </a>
            @endif
          `)
        @endfor
      </ul>
    `)
  @endif

  @if(page.current() != null && page.current().isOwner())
    @template.household._rename(ctx = ctx, page = page)
  @endif

  @if(page.invites() != null)
    @template.household._invites(ctx = ctx, page = page)
  @endif

  @template.household._create(ctx = ctx, page = page)
</section>
