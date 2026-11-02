@import java.util.Map
@import hu.lasterbergamot.mealmagician.shared.web.HouseholdNav.HouseholdChoice
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param String id
@param boolean compact
<%-- The household switcher (plan-ui-redesign.md section 4), in both shells: a pill in the phone's top
     bar (compact = true) and a full-width row in the sidebar (compact = false). The button opens a
     sheet (ui/_sheet: a bottom sheet on a phone, a centred dialog on a desktop) listing only the
     person's own households. The current one is marked; each other one has a form that posts to
     /households/{id}/switch, which answers 404 for a household the person is not in. Nothing is
     shown when the shell has no household data. id must be unique on the page. --%>
@if(ctx.household() != null)
  @template.ui._sheet(id = id, title = ctx.t("nav.switcher"), closeLabel = ctx.t("sheet.close"), role = "dialog", centred = false, closable = true,
    trigger = @`
      <button type="button" x-on:click="show" aria-haspopup="dialog"
              class="inline-flex min-h-11 min-w-0 items-center gap-2 border-2 border-outline bg-brand-soft px-3 text-sm font-extrabold text-brand-ink shadow-sticker-1
                @if(compact) max-w-full rounded-full
                @else w-full rounded-md
                @endif">
        @template.ui._icon(name = IconName.HOME, size = 20, filled = false)
        <span class="truncate [.is-collapsed_&]:sr-only">${ctx.household().name() != null ? ctx.household().name() : ctx.t("household.create.link")}</span>
      </button>
    `,
    body = @`
      @if(!ctx.household().households().isEmpty())
      <ul class="mb-4 flex flex-col gap-2">
        @for(HouseholdChoice choice : ctx.household().households())
          <li>
            @if(choice.current())
              <div class="flex min-h-11 items-center justify-between gap-3 rounded-md border-2 border-outline bg-brand-soft px-3 text-brand-ink">
                <span class="min-w-0 truncate font-extrabold">${choice.name()}</span>
                <span class="inline-flex shrink-0 items-center gap-1 text-sm font-extrabold">
                  @template.ui._icon(name = IconName.CHECK, size = 20, filled = false)
                  <span>${ctx.t("household.current")}</span>
                </span>
              </div>
            @else
              <form method="post" action="/households/${choice.id()}/switch">
                @if(ctx.csrf() != null)
                  <input type="hidden" name="_csrf" value="${ctx.csrf().token()}">
                @endif
                <div class="flex min-h-11 items-center justify-between gap-3 rounded-md border-2 border-outline bg-surface-raised px-3">
                  <span class="min-w-0 truncate font-extrabold text-ink">${choice.name()}</span>
                  @template.ui._button(variant = "secondary", size = "sm", type = "submit", label = ctx.t("household.switch"), icon = null, busy = false, disabled = false, attrs = Map.of())
                </div>
              </form>
            @endif
          </li>
        @endfor
      </ul>
      @endif
      <a href="/households" class="inline-flex min-h-11 items-center font-extrabold text-brand-ink underline">${ctx.t("nav.switcher.manage")}</a>
    `)
@endif
