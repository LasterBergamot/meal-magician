@import java.util.Map
@import hu.lasterbergamot.mealmagician.planning.internal.OccasionDetailPage
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param OccasionDetailPage page
<%-- One occasion (#112): its name, kind and date, the notes, the menu (read-only here; #113 makes it
     editable), and the actions Edit and Delete. Delete is behind a confirm dialog and posts with
     htmx to /occasions/{id}/delete?version=N. A stale delete answers 409 with the banner in
     #occasion-conflict, which is empty (and hidden) unless the page was drawn after such a delete. --%>
@template.layout.base(ctx = ctx, title = page.name(), content = @`
  <section class="flex flex-col gap-6">
    <div>
      @template.meal._link_button(variant = "ghost", href = "/occasions", label = ctx.t("occasion.back"), icon = IconName.LEFT)
    </div>

    <div id="occasion-conflict" class="empty:hidden">@if(page.conflict())@template.occasion._conflict(ctx = ctx, reloadHref = page.href())@endif</div>

    <div class="flex flex-wrap items-center gap-3">
      <h1 class="min-w-0 break-words font-display text-3xl font-semibold text-ink">${page.name()}</h1>
      @template.ui._badge(tone = "accent", label = ctx.t(page.typeKey()), icon = null)
    </div>

    <p class="font-bold text-ink">${page.dateLabel()}</p>

    @if(page.notes() != null)
      @template.ui._card(variant = "default", content = @`
        <dl class="flex flex-col gap-4">
          <div>
            <dt class="text-sm font-extrabold text-ink">${ctx.t("occasion.detail.notes")}</dt>
            <dd class="mt-1 whitespace-pre-line break-words text-ink">${page.notes()}</dd>
          </div>
        </dl>
      `)
    @endif

    <section id="menu" aria-labelledby="menu-title" class="flex flex-col gap-3">
      @template.occasion._menu(ctx = ctx, page = page)
    </section>

    <div class="flex flex-wrap gap-3">
      @template.meal._link_button(variant = "secondary", href = page.editHref(), label = ctx.t("occasion.edit"), icon = IconName.EDIT)
      @template.ui._confirm(id = "delete-occasion", title = ctx.t("occasion.delete.title", page.name()), message = ctx.t("occasion.delete.message"),
        cancelLabel = ctx.t("confirm.cancel"), confirmLabel = ctx.t("occasion.delete.submit"),
        attrs = Map.of("hx-post", page.deleteAction()),
        trigger = @`@template.ui._button(variant = "danger", size = "md", type = "button", label = ctx.t("occasion.delete"), icon = IconName.TRASH, busy = false, disabled = false, attrs = Map.of("x-on:click", "show"))`)
    </div>
  </section>
`)
