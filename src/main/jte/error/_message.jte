@import hu.lasterbergamot.mealmagician.shared.web.ErrorView
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param ErrorView error
@param boolean standalone = false
<%-- The error box. Inside a full page (standalone) its headline is the page's h1; as an htmx
     fragment it lands in the layout's #error-slot and uses an h2. The keys come from
     AppErrorController; a test checks that they all exist. --%>
<div class="rounded-lg border border-red-200 bg-red-50 p-5 text-red-900">
  @if(standalone)
    <h1 class="text-2xl font-bold">${ctx.t(error.titleKey())}</h1>
  @else
    <h2 class="text-lg font-bold">${ctx.t(error.titleKey())}</h2>
  @endif
  <p class="mt-2">${ctx.t(error.messageKey())}</p>
  <a href="/" class="mt-4 inline-flex min-h-11 items-center rounded bg-brand-700 px-4 font-semibold text-white hover:bg-brand-800">${ctx.t("error.home")}</a>
</div>
