@import hu.lasterbergamot.mealmagician.shared.web.Theme
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param String id
<%-- The theme choice (plan-ui-redesign.md UI-D8): three buttons, Light, Dark and System (one
     layout/_theme_button each), in the phone's avatar menu and in the desktop sidebar. They are buttons
     and not links because nothing is sent to the server: the Alpine "theme" component in app.js writes
     the "mm_theme" cookie in the browser and flips <html data-theme> at once, and it sets aria-pressed
     on every theme button of the page, so the phone's and the desktop's copies stay in step. The
     server renders aria-pressed from the cookie, so the right button is marked on first paint. Each
     button has its icon above its label so that three choices fit the 232 px of the phone menu and the
     224 px of the sidebar; in the collapsed sidebar the labels become screen-reader-only and the buttons
     stack. id is unique per copy of this fragment. --%>
<div id="${id}" role="group" aria-label="${ctx.t("theme.label")}" x-data="theme"
     class="flex gap-1 [.is-collapsed_&]:flex-col">
  @template.layout._theme_button(ctx = ctx, choice = Theme.LIGHT, label = ctx.t("theme.light"))
  @template.layout._theme_button(ctx = ctx, choice = Theme.DARK, label = ctx.t("theme.dark"))
  @template.layout._theme_button(ctx = ctx, choice = Theme.SYSTEM, label = ctx.t("theme.system"))
</div>
