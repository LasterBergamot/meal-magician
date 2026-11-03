@import java.util.List
@import java.util.Map
@import hu.lasterbergamot.mealmagician.shared.web.FormOption
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext
@import hu.lasterbergamot.mealmagician.sharing.internal.SharePanel

@param ViewContext ctx
@param SharePanel panel
<%-- The content of the share sheet (and of the no-JavaScript page) for one week: what is shared, a
     form that makes a link, the new link (only in the one response that creates it) and the week's
     links. #share-panel is the one swap region of the create form, so a refused create (422, the
     cap of active links) replaces it with its message and keeps the chosen expiry. Revoking
     swaps only #share-links (sharing/_share_list). The sheet is page-level, outside #plan-week. --%>
<div id="share-panel" class="flex flex-col gap-5">
  <div class="flex flex-col gap-1">
    <p class="font-bold text-ink">${panel.title()}</p>
    <p class="text-sm text-ink-muted">${panel.range()}</p>
    <p class="text-sm text-ink-muted">${ctx.t("share.sheet.hint")}</p>
  </div>

  @if(panel.link() != null)
    @template.sharing._share_created(ctx = ctx, link = panel.link())
  @endif

  <form method="post" action="/plan/share" hx-post="/plan/share" hx-target="#share-panel" hx-swap="outerHTML" class="flex flex-col gap-3">
    @if(ctx.csrf() != null)
      <input type="hidden" name="_csrf" value="${ctx.csrf().token()}">
    @endif
    <input type="hidden" name="week" value="${panel.week()}">
    @template.ui._select(id = "share-expiry", name = "expiry", label = ctx.t("share.expiry.label"), options = List.of(new FormOption("1d", ctx.t("share.expiry.1d")), new FormOption("7d", ctx.t("share.expiry.7d")), new FormOption("30d", ctx.t("share.expiry.30d"))), selected = panel.expiry(), hint = null, error = panel.error() == null ? null : ctx.t(panel.error()), disabled = false, attrs = Map.of())
    <div>
      @template.ui._button(variant = "primary", size = "md", type = "submit", label = ctx.t("share.create"), icon = IconName.SHARE, busy = false, disabled = false, attrs = Map.of())
    </div>
  </form>

  @template.sharing._share_list(ctx = ctx, panel = panel)
</div>
