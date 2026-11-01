@import java.util.Map
@import hu.lasterbergamot.mealmagician.shared.web.HtmlAttributes
@import hu.lasterbergamot.mealmagician.shared.web.IconName

@param String label
@param boolean selected
@param Map<String, String> attrs
<%-- The filter chip (plan-ui-redesign.md section 5): a pill button that switches one filter on or
     off, such as a tag on the meal list. aria-pressed carries the state for screen readers. The
     selected state is a violet fill with a check icon, so it is never colour alone. It is 44 px
     tall (the touch minimum). attrs are extra attributes such as hx-get (Map.of() for none). A
     label that is not a control is a badge, not a chip. --%>
<button type="button"
        class="inline-flex min-h-11 shrink-0 items-center gap-1.5 whitespace-nowrap rounded-full border-2 border-outline px-4 font-sans text-sm font-extrabold leading-5 shadow-sticker-1 transition-[translate,box-shadow] duration-[var(--mm-duration-fast)] hover:-translate-x-px hover:-translate-y-px hover:shadow-sticker-2 active:translate-x-px active:translate-y-0.5 active:shadow-none
          @if(selected) bg-brand text-on-brand hover:bg-brand-hover
          @else bg-surface-raised text-ink hover:bg-surface-sunken
          @endif"
        aria-pressed="${selected ? "true" : "false"}"
        $unsafe{HtmlAttributes.render(attrs)}>
  @if(selected)
    @template.ui._icon(name = IconName.CHECK, size = 18, filled = false)
  @endif
  ${label}
</button>
