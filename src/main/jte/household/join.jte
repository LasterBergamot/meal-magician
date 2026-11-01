@import hu.lasterbergamot.mealmagician.household.internal.JoinPage
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param JoinPage join
<%-- The page an invite link opens: confirm joining, or the reason the link does not work. --%>
@template.layout.base(ctx = ctx, title = ctx.t("join.title"), content = @`
  <section class="rounded-lg bg-white p-5 shadow-sm md:p-8">
    <h1 class="text-2xl font-bold text-brand-800">${ctx.t("join.heading")}</h1>
    @if(join.errorKey() != null)
      <p class="mt-3 rounded bg-red-50 p-3 text-red-800" role="alert">${ctx.t(join.errorKey())}</p>
      <a href="/" class="mt-4 inline-flex min-h-11 items-center underline">${ctx.t("join.home")}</a>
    @else
      <p class="mt-3 text-stone-700">${ctx.t("join.lead")}</p>
      <p class="mt-2 text-xl font-semibold">${join.householdName()}</p>
      <form class="mt-4" hx-post="/join">
        <input type="hidden" name="code" value="${join.code()}">
        <button type="submit" class="inline-flex min-h-11 items-center justify-center rounded bg-brand-700 px-5 py-2 font-semibold text-white hover:bg-brand-800">${ctx.t("join.submit")}</button>
      </form>
    @endif
  </section>
`)
