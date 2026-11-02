@import java.util.Map
@import hu.lasterbergamot.mealmagician.household.internal.MemberRow
@import hu.lasterbergamot.mealmagician.household.internal.MembersPage
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param MembersPage page
<%-- The members section. htmx swaps all of it after a remove or hand-over, or a refused action
     (409, shown as a danger alert). Every destructive button opens a ui/_confirm that names the
     person; the swap that answers the request removes the open dialog, which closes it. The avatars
     use the soft tone: the phone's tab bar already spends the screen's one gold highlight on the
     current tab. --%>
<section id="members" class="flex flex-col gap-6">
  <h1 class="font-display text-3xl font-semibold text-ink">${ctx.t("household.members.heading", page.household().name())}</h1>
  @if(page.error() != null)
    @template.ui._alert(kind = "danger", title = ctx.t("household.members.error.title"), message = ctx.t(page.error()), action = null)
  @endif
  @template.ui._card(variant = "default", content = @`
    <ul class="flex flex-col">
      @for(MemberRow row : page.rows())
        @template.ui._row(selected = false, content = @`
          <div class="flex w-full flex-col gap-3 py-1 md:flex-row md:items-center">
            <div class="flex min-w-0 flex-1 items-center gap-3">
              @template.ui._avatar(initials = row.initials(), size = "md", tone = "soft")
              <div class="flex min-w-0 flex-1 flex-col">
                <span class="break-words font-sans text-base font-extrabold leading-6">${row.name()}@if(row.self()) <span class="text-sm font-medium text-ink-muted">(${ctx.t("household.members.you")})</span>@endif</span>
                <span class="break-all text-sm text-ink-muted">${row.email()}</span>
              </div>
              @if(row.owner())
                @template.ui._badge(tone = "brand", label = ctx.t("household.role.owner"), icon = null)
              @else
                @template.ui._badge(tone = "neutral", label = ctx.t("household.role.member"), icon = null)
              @endif
            </div>
            @if(row.manageable())
              <div class="flex flex-wrap gap-2">
                @if(!row.owner())
                  @template.ui._confirm(id = "transfer-" + row.userId(), title = ctx.t("household.members.transfer.title", row.name()), message = ctx.t("household.members.transfer.confirm"), cancelLabel = ctx.t("confirm.cancel"), confirmLabel = ctx.t("household.members.transfer"),
                    attrs = Map.of("hx-post", "/households/members/" + row.userId() + "/transfer", "hx-target", "#members", "hx-swap", "outerHTML"),
                    trigger = @`@template.ui._button(variant = "secondary", size = "md", type = "button", label = ctx.t("household.members.transfer"), icon = null, busy = false, disabled = false, attrs = Map.of("x-on:click", "show"))`)
                @endif
                @template.ui._confirm(id = "remove-" + row.userId(), title = ctx.t("household.members.remove.title", row.name()), message = ctx.t("household.members.remove.confirm"), cancelLabel = ctx.t("confirm.cancel"), confirmLabel = ctx.t("household.members.remove"),
                  attrs = Map.of("hx-post", "/households/members/" + row.userId() + "/remove", "hx-target", "#members", "hx-swap", "outerHTML"),
                  trigger = @`@template.ui._button(variant = "danger", size = "md", type = "button", label = ctx.t("household.members.remove"), icon = IconName.TRASH, busy = false, disabled = false, attrs = Map.of("x-on:click", "show"))`)
              </div>
            @endif
          </div>
        `)
      @endfor
    </ul>
    <div class="flex flex-col items-start gap-2 border-t-2 border-dashed border-border pt-4">
      @template.ui._confirm(id = "leave", title = ctx.t("household.members.leave.title", page.household().name()), message = ctx.t("household.members.leave.confirm"), cancelLabel = ctx.t("confirm.cancel"), confirmLabel = ctx.t("household.members.leave"),
        attrs = Map.of("hx-post", "/households/members/leave", "hx-target", "#members", "hx-swap", "outerHTML"),
        trigger = @`@template.ui._button(variant = "danger", size = "md", type = "button", label = ctx.t("household.members.leave"), icon = IconName.LOGOUT, busy = false, disabled = false, attrs = Map.of("x-on:click", "show"))`)
      @if(page.household().isOwner())
        <p class="text-sm text-ink-muted">${ctx.t("household.members.leave.hint")}</p>
      @endif
    </div>
  `)
</section>
