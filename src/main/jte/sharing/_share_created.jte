@import java.util.Map
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param String link
<%-- The new share link, shown once. The raw token is not stored anywhere, so this response is the
     only chance to copy it (PLAN.md D28); the controller marks it Cache-Control: no-store. The
     input is read-only and selectable, the Copy button uses the Alpine clipboard component
     (navigator.clipboard, with a select-and-copy fallback), and the status line below is a live
     region that says "Copied". --%>
@template.ui._alert(kind = "success", title = ctx.t("share.created.title"), message = ctx.t("share.created.hint"), action = @`
  <div x-data="clipboard" class="flex flex-col gap-2">
    <label for="share-link-url" class="sr-only">${ctx.t("share.created.label")}</label>
    <input id="share-link-url" x-ref="field" type="text" readonly value="${link}"
           class="block h-12 w-full rounded-md border-2 border-border-strong bg-surface-raised px-4 font-mono text-sm text-ink">
    <div class="flex flex-wrap items-center gap-3">
      @template.ui._button(variant = "secondary", size = "md", type = "button", label = ctx.t("share.created.copy"), icon = IconName.COPY, busy = false, disabled = false, attrs = Map.of("x-on:click", "copy"))
      <p x-ref="status" x-text="message" data-copied="${ctx.t("share.created.copied")}" role="status" class="text-sm font-bold"></p>
    </div>
  </div>
`)
