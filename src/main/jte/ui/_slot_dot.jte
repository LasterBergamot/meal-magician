@import hu.lasterbergamot.mealmagician.shared.web.IconName

@param String slot
@param String label
@param String size
<%-- The dot of one meal type (plan-ui-redesign.md sections 3.5 and 5). slot is BREAKFAST, LUNCH,
     DINNER or SNACK; any other value draws no colour. size: sm is a 12 px solid mark for a legend
     that has the word next to it; lg is a 36 px disc with the slot icon, for a lone slot marker.
     label: when it is blank the dot is decorative (aria-hidden), because the word beside it says
     the meal; when it is not blank the dot is an image named by the label, for a dot that stands
     alone. The mark is a fill in the slot colour, never text. data-slot is for the tests. --%>
<span data-slot="${slot}" role="${label.isBlank() ? "presentation" : "img"}" aria-hidden="${label.isBlank() ? "true" : "false"}" aria-label="${label}"
      class="inline-flex shrink-0 items-center justify-center rounded-full border-2 border-outline
        @if(size.equals("lg")) size-9 bg-slot-soft text-slot-ink
        @else size-3
        @endif
        @if(slot.equals("BREAKFAST")) slot-breakfast
        @elseif(slot.equals("LUNCH")) slot-lunch
        @elseif(slot.equals("DINNER")) slot-dinner
        @elseif(slot.equals("SNACK")) slot-snack
        @endif
        @if(size.equals("sm")) bg-slot
        @endif">
  @if(size.equals("lg"))
    @if(slot.equals("BREAKFAST"))
      @template.ui._icon(name = IconName.BREAKFAST, size = 20, filled = false)
    @elseif(slot.equals("LUNCH"))
      @template.ui._icon(name = IconName.LUNCH, size = 20, filled = false)
    @elseif(slot.equals("DINNER"))
      @template.ui._icon(name = IconName.DINNER, size = 20, filled = false)
    @elseif(slot.equals("SNACK"))
      @template.ui._icon(name = IconName.SNACK, size = 20, filled = false)
    @endif
  @endif
</span>
