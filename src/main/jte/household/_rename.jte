@import hu.lasterbergamot.mealmagician.household.internal.HouseholdsPage
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param HouseholdsPage page
<form id="rename-form" class="rounded-lg bg-white p-5 shadow-sm md:p-8" hx-post="/households/${page.current().id()}/rename" hx-target="this" hx-swap="outerHTML">
  <h2 class="text-xl font-bold text-brand-800">${ctx.t("household.rename.heading")}</h2>
  <label for="rename-name" class="mt-3 block font-medium">${ctx.t("household.name.label")}</label>
  <input id="rename-name" name="name" type="text" maxlength="100" required autocomplete="off" value="${page.renameName()}"
         class="mt-1 block min-h-11 w-full rounded border border-stone-400 px-3">
  @if(page.renameError() != null)
    <p class="mt-2 rounded bg-red-50 p-3 text-red-800" role="alert">${ctx.t(page.renameError())}</p>
  @endif
  <button type="submit" class="mt-4 inline-flex min-h-11 items-center justify-center rounded bg-brand-700 px-5 py-2 font-semibold text-white hover:bg-brand-800">${ctx.t("household.rename.submit")}</button>
</form>
