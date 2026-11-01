@import java.util.Map
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
<%-- Section of the style guide (/dev/ui) for the sheet and the confirm dialog (R3). Each example is
     a real ui/_sheet or ui/_confirm: the trigger opens it, the X, Cancel and Escape close it, and the
     browser tests (SheetAndConfirmE2ETest) drive exactly these elements. The confirm button here
     only closes the dialog; in the app it sends the request. --%>
<section id="dev-overlays" class="flex flex-col gap-6 rounded-lg border-2 border-outline bg-surface-raised p-4 shadow-sticker-2 md:p-6">
  <div class="flex flex-col gap-2">
    <h2 class="font-display text-2xl font-semibold text-ink">${ctx.t("dev.overlay.heading")}</h2>
    <p class="max-w-[720px] text-ink-muted">${ctx.t("dev.overlay.lead")}</p>
  </div>

  <div class="flex flex-col items-start gap-1">
    <span class="text-xs font-bold text-ink-muted">${ctx.t("dev.overlay.sheet.label")}</span>
    @template.ui._sheet(id = "dev-sheet", title = ctx.t("dev.overlay.sheet.title"), closeLabel = ctx.t("sheet.close"), role = "dialog", centred = false, closable = true,
      trigger = @`@template.ui._button(variant = "secondary", size = "md", type = "button", label = ctx.t("dev.overlay.sheet.open"), icon = null, busy = false, disabled = false, attrs = Map.of("x-on:click", "show"))`,
      body = @`
        <p class="text-ink-muted">${ctx.t("dev.overlay.sheet.body")}</p>
        <div class="mt-4 flex flex-wrap gap-3">
          @template.ui._button(variant = "primary", size = "md", type = "button", label = ctx.t("ui.demo.save"), icon = null, busy = false, disabled = false, attrs = Map.of())
          @template.ui._button(variant = "secondary", size = "md", type = "button", label = ctx.t("ui.demo.add"), icon = null, busy = false, disabled = false, attrs = Map.of())
        </div>
      `)
  </div>

  <div class="flex flex-col items-start gap-1">
    <span class="text-xs font-bold text-ink-muted">${ctx.t("dev.overlay.confirm.label")}</span>
    @template.ui._confirm(id = "dev-confirm", title = ctx.t("dev.overlay.confirm.title"), message = ctx.t("dev.overlay.confirm.message"), cancelLabel = ctx.t("confirm.cancel"), confirmLabel = ctx.t("dev.overlay.confirm.submit"), attrs = Map.of("x-on:click", "hide"),
      trigger = @`@template.ui._button(variant = "danger", size = "md", type = "button", label = ctx.t("dev.overlay.confirm.open"), icon = null, busy = false, disabled = false, attrs = Map.of("x-on:click", "show"))`)
  </div>
</section>
