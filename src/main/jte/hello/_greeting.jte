@import hu.lasterbergamot.mealmagician.shared.web.HelloView
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param HelloView hello
<%-- The part htmx replaces. The button asks the same URL again; because the request carries
     HX-Request, the controller answers with this fragment only. hx-target/hx-swap are always
     explicit (web-ui.md). --%>
<div id="greeting" class="mt-4 flex flex-col gap-3 sm:flex-row sm:items-center">
  <p class="text-lg">${ctx.t("hello.today", hello.today())}</p>
  <button type="button" hx-get="/" hx-target="#greeting" hx-swap="outerHTML"
          class="inline-flex min-h-11 items-center justify-center rounded bg-brand-700 px-4 font-semibold text-white hover:bg-brand-800 sm:ml-auto">${ctx.t("hello.refresh")}</button>
</div>
