@import java.util.Map
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext
@import hu.lasterbergamot.mealmagician.sharing.internal.SharePanel
@import hu.lasterbergamot.mealmagician.sharing.internal.ShareRow

@param ViewContext ctx
@param SharePanel panel
<%-- The week's share links, newest first, with status, expiry and who made each. #share-links is
     the swap region of Revoke: the response replaces the whole section, which also removes the
     open confirm dialog (a dialog inside a swapped region disappears with the swap). The list
     never holds a token or a hash; only the creating response shows an address. --%>
<section id="share-links" class="flex flex-col gap-3 border-t-2 border-dashed border-border pt-4">
  <h3 class="font-display text-xl font-semibold text-ink">${ctx.t("share.list.heading")}</h3>
  @if(panel.rows().isEmpty())
    <p class="rounded-md border-2 border-dashed border-border-strong p-4 text-ink-muted">${ctx.t("share.list.empty")}</p>
  @else
    <ul class="flex flex-col rounded-md border-2 border-outline">
      @for(ShareRow row : panel.rows())
        @template.ui._row(selected = false, content = @`
          <div class="flex w-full flex-col gap-3 py-1 md:flex-row md:items-center">
            <div class="flex min-w-0 flex-1 flex-col gap-1.5">
              <span class="flex flex-wrap items-center gap-2">
                @template.ui._badge(tone = row.tone(), label = ctx.t(row.statusKey()), icon = null)
                <span class="text-sm text-ink-muted">${ctx.t("share.list.until", row.expires())}</span>
              </span>
              <span class="text-sm text-ink-muted">
                @if(row.createdBy() != null)
                  ${ctx.t("share.list.by", row.createdBy())}
                @else
                  ${ctx.t("share.list.byUnknown")}
                @endif
              </span>
            </div>
            @if(row.revocable())
              <div class="flex">
                @template.ui._confirm(id = "share-revoke-" + row.id(), title = ctx.t("share.revoke.title"), message = ctx.t("share.revoke.confirm"), cancelLabel = ctx.t("confirm.cancel"), confirmLabel = ctx.t("share.revoke"),
                  attrs = Map.of("hx-post", "/plan/share/" + row.id() + "/revoke?week=" + panel.week(), "hx-target", "#share-links", "hx-swap", "outerHTML"),
                  trigger = @`@template.ui._button(variant = "danger", size = "md", type = "button", label = ctx.t("share.revoke"), icon = IconName.TRASH, busy = false, disabled = false, attrs = Map.of("x-on:click", "show"))`)
              </div>
            @endif
          </div>
        `)
      @endfor
    </ul>
  @endif
</section>
