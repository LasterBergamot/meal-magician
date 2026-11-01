@import hu.lasterbergamot.mealmagician.shared.web.IconName

@param String slot
@param String label
<%-- The chip of one meal type (plan-ui-redesign.md sections 3.5 and 5). slot is BREAKFAST, LUNCH,
     DINNER or SNACK, in that order of the day; any other value draws no colour and no icon, so a
     typo shows up in the style guide. label is the word from ctx.t("slot.<type>"). The chip always
     has the icon and the word, because colour alone never says which meal it is. It is a span: a
     chip that is a control is a ui/_chip. The slot-* class sets the colour variables that the
     bg-slot-soft and text-slot-ink utilities read. data-slot is for the tests. --%>
<span data-slot="${slot}"
      class="inline-flex min-h-7 items-center gap-1.5 whitespace-nowrap rounded-full border-2 border-outline bg-slot-soft px-3 py-0.5 font-sans text-sm font-extrabold leading-5 text-slot-ink shadow-sticker-1
        @if(slot.equals("BREAKFAST")) slot-breakfast
        @elseif(slot.equals("LUNCH")) slot-lunch
        @elseif(slot.equals("DINNER")) slot-dinner
        @elseif(slot.equals("SNACK")) slot-snack
        @endif">
  @if(slot.equals("BREAKFAST"))
    @template.ui._icon(name = IconName.BREAKFAST, size = 18, filled = false)
  @elseif(slot.equals("LUNCH"))
    @template.ui._icon(name = IconName.LUNCH, size = 18, filled = false)
  @elseif(slot.equals("DINNER"))
    @template.ui._icon(name = IconName.DINNER, size = 18, filled = false)
  @elseif(slot.equals("SNACK"))
    @template.ui._icon(name = IconName.SNACK, size = 18, filled = false)
  @endif
  ${label}
</span>
