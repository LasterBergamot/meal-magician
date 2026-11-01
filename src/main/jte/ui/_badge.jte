@import hu.lasterbergamot.mealmagician.shared.web.IconName

@param String tone
@param String label
@param IconName icon
<%-- The badge (plan-ui-redesign.md section 5): a short label for a state such as Owner, Member,
     Today or Current. A badge is never a control, so it is a span, not a button. tone: neutral
     (Member), brand (Owner, Current), accent (Today: a gold fill with on-accent text, because gold
     is never text), success, warning, danger or info. icon is an optional IconName drawn before the
     label, or null. The word carries the meaning, so the icon is decorative; a Current badge always
     gets a check. data-tone is for the tests and the style guide. Every class is written out in full. --%>
<span data-tone="${tone}"
      class="inline-flex items-center gap-1 whitespace-nowrap rounded-full border-2 border-outline px-2.5 py-0.5 font-sans text-xs font-bold leading-4 tracking-[0.02em]
        @if(tone.equals("neutral")) bg-surface-sunken text-ink
        @elseif(tone.equals("brand")) bg-brand-soft text-brand-ink
        @elseif(tone.equals("accent")) bg-accent text-on-accent
        @elseif(tone.equals("success")) bg-success-soft text-success-ink
        @elseif(tone.equals("warning")) bg-warning-soft text-warning-ink
        @elseif(tone.equals("danger")) bg-danger-soft text-danger-ink
        @elseif(tone.equals("info")) bg-info-soft text-info-ink
        @endif">
  @if(icon != null)
    @template.ui._icon(name = icon, size = 14, filled = false)
  @endif
  ${label}
</span>
