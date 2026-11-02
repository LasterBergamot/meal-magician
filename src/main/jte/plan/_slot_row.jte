@import hu.lasterbergamot.mealmagician.planning.internal.SlotGroup
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param SlotGroup group
<%-- One slot of a day in the phone's list: the slot chip, then the planned meals or an empty note.
     The slot-* class sets the colour variables that bg-slot-soft and text-slot-ink read; it is an
     @if chain so Tailwind finds every class. data-group is for the tests. --%>
<li data-group="${group.key()}"
    class="flex min-h-14 flex-col gap-2 rounded-md border-2 bg-surface-raised p-3
      @if(group.slotName().equals("BREAKFAST")) slot-breakfast
      @elseif(group.slotName().equals("LUNCH")) slot-lunch
      @elseif(group.slotName().equals("DINNER")) slot-dinner
      @elseif(group.slotName().equals("SNACK")) slot-snack
      @endif
      @if(group.empty()) border-dashed border-border-strong @else border-outline shadow-sticker-1 @endif">
  <div>
    @template.ui._slot_chip(slot = group.slotName(), label = ctx.t(group.labelKey()))
  </div>
  @template.plan._slot_items(ctx = ctx, group = group)
</li>
