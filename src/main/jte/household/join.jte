@import java.util.Map
@import hu.lasterbergamot.mealmagician.household.internal.JoinPage
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param JoinPage join
<%-- The page an invite link opens: a centred card to confirm joining, or the reason the link does
     not work (a danger alert and a way back). --%>
@template.layout.base(ctx = ctx, title = ctx.t("join.title"), content = @`
  <section class="mx-auto flex w-full max-w-lg flex-col gap-6">
    <h1 class="font-display text-3xl font-semibold text-ink">${ctx.t("join.heading")}</h1>
    @template.ui._card(variant = "default", content = @`
      @if(join.errorKey() != null)
        @template.ui._alert(kind = "danger", title = ctx.t("join.error.title"), message = ctx.t(join.errorKey()), action = null)
        <a href="/" class="inline-flex min-h-11 items-center gap-1 self-start rounded-md px-3 font-sans text-sm font-extrabold text-brand-ink hover:bg-surface-sunken">
          @template.ui._icon(name = IconName.LEFT, size = 20, filled = false)
          ${ctx.t("join.home")}
        </a>
      @else
        <p class="text-ink-muted">${ctx.t("join.lead")}</p>
        <p class="break-words font-display text-2xl font-semibold leading-8 text-ink">${join.householdName()}</p>
        <form hx-post="/join">
          <input type="hidden" name="code" value="${join.code()}">
          @template.ui._button(variant = "primary", size = "lg", type = "submit", label = ctx.t("join.submit"), icon = IconName.CHECK, busy = false, disabled = false, attrs = Map.of())
        </form>
      @endif
    `)
  </section>
`)
