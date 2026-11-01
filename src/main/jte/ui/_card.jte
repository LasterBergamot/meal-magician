@import gg.jte.Content

@param String variant
@param Content content
<%-- The card (plan-ui-redesign.md section 5): a raised surface with the 2 px outline, the medium
     sticker shadow and a 20 px radius. variant: default (surface-raised), accent (accent-soft; for
     occasions only) or selected (brand-soft; the current item, which also needs a Current badge and
     a check). The card is relative, so one link inside it can stretch over the whole card with
     after:absolute after:inset-0. Use one such link per card and no link inside a link. The padding
     is space-4 on a phone and space-6 from md. data-variant is for the tests. --%>
<div data-variant="${variant}"
     class="relative flex flex-col gap-3 rounded-lg border-2 border-outline p-4 text-ink shadow-sticker-2 md:p-6
       @if(variant.equals("accent")) bg-accent-soft
       @elseif(variant.equals("selected")) bg-brand-soft
       @else bg-surface-raised
       @endif">
  ${content}
</div>
