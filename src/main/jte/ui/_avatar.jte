@param String initials
@param String size
<%-- The avatar (plan-ui-redesign.md section 5): a gold disc with the person's one or two initials.
     The caller computes the initials and passes them in. size: sm (32 px) or md (44 px). Gold is a
     fill with on-accent text on it, never text itself. The avatar is decorative (aria-hidden),
     because the name next to it says who it is; a bare avatar used as a menu button needs its own
     label on that button. --%>
<span aria-hidden="true"
      class="inline-flex shrink-0 items-center justify-center rounded-full border-2 border-outline bg-accent font-display font-semibold leading-none text-on-accent
        @if(size.equals("md")) size-11 text-base
        @else size-8 text-xs
        @endif">${initials}</span>
