@import java.util.Map
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
<%-- The install hint (#126): an info alert above the page that offers "Install" where the browser
     has a prompt (beforeinstallprompt) and shows "Share, then Add to Home Screen" on iPhone and iPad
     Safari. The Alpine component "installHint" (static/js/install-hint.js) decides what shows, via the
     hidden attributes; it starts hidden, so a browser without JavaScript or one that is already
     installed never sees it. Dismiss hides it for 30 days. data-install-hint is for the tests. --%>
<div data-install-hint x-data="installHint" x-bind:hidden="hidden" hidden class="mb-4 [&[hidden]]:hidden">
  @template.ui._alert(kind = "info", title = ctx.t("pwa.install.title"), message = ctx.t("pwa.install.message"), action = @`
    <p x-bind:hidden="iosHidden" hidden class="mb-2 text-sm font-medium leading-5">${ctx.t("pwa.install.ios.message")}</p>
    <div class="flex flex-wrap gap-2">
      <span x-bind:hidden="installHidden" hidden>
        @template.ui._button(variant = "primary", size = "sm", type = "button", label = ctx.t("pwa.install.button"), icon = null, busy = false, disabled = false, attrs = Map.of("x-on:click", "install"))
      </span>
      @template.ui._button(variant = "ghost", size = "sm", type = "button", label = ctx.t("pwa.install.dismiss"), icon = null, busy = false, disabled = false, attrs = Map.of("x-on:click", "dismiss"))
    </div>
  `)
</div>
