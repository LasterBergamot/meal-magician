@import hu.lasterbergamot.mealmagician.identity.internal.AllowlistPage
@import hu.lasterbergamot.mealmagician.identity.internal.AllowlistRow
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param AllowlistPage page
<%-- The allowlist section. htmx swaps all of it after an add or a remove, or a refused change. --%>
<section id="allowlist" class="rounded-lg bg-white p-5 shadow-sm md:p-8">
  <h1 class="text-2xl font-bold text-brand-800">${ctx.t("admin.allowlist.heading")}</h1>
  <p class="mt-2 text-stone-700">${ctx.t("admin.allowlist.lead")}</p>
  <form class="mt-4" hx-post="/admin/allowlist" hx-target="#allowlist" hx-swap="outerHTML">
    <label for="allowlist-email" class="block font-medium">${ctx.t("admin.allowlist.email.label")}</label>
    <input id="allowlist-email" name="email" type="email" maxlength="254" required autocomplete="off" value="${page.email()}"
           class="mt-1 block min-h-11 w-full rounded border border-stone-400 px-3">
    <button type="submit" class="mt-4 inline-flex min-h-11 items-center justify-center rounded bg-brand-700 px-5 py-2 font-semibold text-white hover:bg-brand-800">${ctx.t("admin.allowlist.add")}</button>
  </form>
  @if(page.error() != null)
    <p class="mt-3 rounded bg-red-50 p-3 text-red-800" role="alert">${ctx.t(page.error())}</p>
  @endif
  <ul class="mt-6 divide-y divide-stone-200">
    @for(AllowlistRow row : page.rows())
      <li class="flex flex-wrap items-center justify-between gap-2 py-2">
        <span class="min-w-0 break-all font-medium">${row.email()}</span>
        <span class="flex flex-wrap items-center gap-3 text-sm text-stone-600">
          <span>${ctx.t(row.originKey())}</span>
          @if(!row.added().isEmpty())
            <span>${row.added()}</span>
          @endif
          @if(row.removable())
            <form hx-post="/admin/allowlist/remove" hx-confirm="${ctx.t("admin.allowlist.remove.confirm")}" hx-target="#allowlist" hx-swap="outerHTML">
              <input type="hidden" name="email" value="${row.email()}">
              <button type="submit" class="inline-flex min-h-11 items-center rounded border border-red-700 px-4 font-semibold text-red-800 hover:bg-red-50">${ctx.t("admin.allowlist.remove")}</button>
            </form>
          @endif
        </span>
      </li>
    @endfor
  </ul>
  <p class="mt-4 text-sm text-stone-600">${ctx.t("admin.allowlist.hint")}</p>
</section>
