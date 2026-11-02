@param String initials
@param String size
@param String tone = "gold"
<%-- The avatar (plan-ui-redesign.md section 5): a disc with the person's one or two initials. The
     caller computes the initials and passes them in. size: sm (32 px) or md (44 px). tone: gold (the
     default, a gold fill with on-accent text) or soft (brand-soft with brand-ink text, a validated
     contrast pair). Gold is a fill with on-accent text on it, never text itself. Use soft where gold is
     already spent: the app shell's top bar has the gold current-tab outline, and section 3.1 allows
     one gold highlight per screen. The avatar is decorative (aria-hidden), because the name next to it
     says who it is; a bare avatar used as a menu button needs its own label on that button. --%>
<span aria-hidden="true" data-tone="${tone}"
      class="inline-flex shrink-0 items-center justify-center rounded-full border-2 border-outline font-display font-semibold leading-none
        @if(tone.equals("soft")) bg-brand-soft text-brand-ink
        @else bg-accent text-on-accent
        @endif
        @if(size.equals("md")) size-11 text-base
        @else size-8 text-xs
        @endif">${initials}</span>
