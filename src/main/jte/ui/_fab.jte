@import java.util.Map
@import hu.lasterbergamot.mealmagician.shared.web.HtmlAttributes
@import hu.lasterbergamot.mealmagician.shared.web.IconName

@param IconName icon
@param String label
@param String type
@param boolean floating
@param Map<String, String> attrs
<%-- The add button (plan-ui-redesign.md section 5): a 58 px gold round button with the outline and
     the largest sticker shadow. floating = true pins it above the bottom tab bar on phones
     (bottom = the bar's height plus 16 px) and hides it from md up, where the same action is a
     primary button in the header. floating = false is the plain in-flow version, used on the
     style guide. Gold is a fill only, so the icon is on-accent. label is sr-only. --%>
<button type="${type}"
        class="inline-flex size-[58px] shrink-0 items-center justify-center rounded-full border-2 border-outline bg-accent text-on-accent shadow-sticker-3 transition-[translate,box-shadow] duration-[var(--mm-duration-fast)] hover:-translate-x-px hover:-translate-y-px active:translate-x-px active:translate-y-[3px] active:shadow-none disabled:pointer-events-none disabled:opacity-50
          @if(floating) fixed right-4 bottom-[calc(var(--mm-bottom-bar)_+_16px)] z-20 md:hidden @endif"
        $unsafe{HtmlAttributes.render(attrs)}>
  @template.ui._icon(name = icon, size = 28, filled = false)
  <span class="sr-only">${label}</span>
</button>
