@import hu.lasterbergamot.mealmagician.shared.web.IconName

@param String variant
@param String href
@param String label
@param IconName icon
<%-- A link that looks like a ui/_button (md size). ui/_button always draws a <button>, which cannot
     navigate, and a link must not be wrapped in a button, so this fragment repeats the primary,
     secondary and ghost classes for an <a>. variant: primary, secondary or ghost. icon may be null.
     Gap for the design system (ui/_button could take an href); until then keep these classes in step
     with ui/_button. --%>
<a href="${href}"
   class="inline-flex min-h-11 items-center justify-center gap-2 whitespace-nowrap rounded-md px-4 font-sans text-sm font-extrabold leading-5 transition-[translate,box-shadow,background-color] duration-[var(--mm-duration-fast)]
     @if(variant.equals("primary")) border-2 border-outline bg-brand text-on-brand shadow-sticker-1 hover:-translate-x-px hover:-translate-y-px hover:bg-brand-hover hover:shadow-sticker-2 active:translate-x-px active:translate-y-0.5 active:shadow-none
     @elseif(variant.equals("secondary")) border-2 border-outline bg-surface-raised text-ink shadow-sticker-1 hover:-translate-x-px hover:-translate-y-px hover:bg-surface-sunken hover:shadow-sticker-2 active:translate-x-px active:translate-y-0.5 active:shadow-none
     @else border-2 border-transparent bg-transparent text-brand hover:bg-brand-soft hover:text-brand-ink
     @endif">
  @if(icon != null)
    @template.ui._icon(name = icon, size = 20, filled = false)
  @endif
  <span>${label}</span>
</a>
