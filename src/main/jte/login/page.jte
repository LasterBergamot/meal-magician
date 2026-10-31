@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param boolean failed
@param boolean signedOut
<%-- The sign-in page: one link to Google (a GET link, so form-action 'self' is not involved). --%>
@template.layout.base(ctx = ctx, title = ctx.t("login.title"), content = @`
  <section class="mx-auto max-w-md rounded-lg bg-white p-5 text-center shadow-sm md:p-8">
    <h1 class="text-2xl font-bold text-brand-800">${ctx.t("login.heading")}</h1>
    <p class="mt-2 text-stone-700">${ctx.t("login.lead")}</p>
    @if(failed)
      <p class="mt-4 rounded bg-red-50 p-3 text-red-800" role="alert">${ctx.t("login.error")}</p>
    @endif
    @if(signedOut)
      <p class="mt-4 rounded bg-green-50 p-3 text-green-800" role="status">${ctx.t("login.signedOut")}</p>
    @endif
    <a href="/oauth2/authorization/google" class="mt-6 inline-flex min-h-11 items-center justify-center rounded bg-brand-700 px-5 py-2 font-semibold text-white hover:bg-brand-800">${ctx.t("login.google")}</a>
  </section>
`)
