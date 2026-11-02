@import hu.lasterbergamot.mealmagician.planning.internal.SlotGroup
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param SlotGroup group
<%-- One cell of the desktop table: the meals of one day and slot type, or an empty note. The slot-*
     class sets the colour variables that bg-slot-soft and text-slot-ink read in the meal items.
     data-group is for the tests. --%>
<div data-group="${group.key()}"
     class="flex min-h-24 flex-col gap-1.5 rounded-md border-2 bg-surface-raised p-1.5
       @if(group.slotName().equals("BREAKFAST")) slot-breakfast
       @elseif(group.slotName().equals("LUNCH")) slot-lunch
       @elseif(group.slotName().equals("DINNER")) slot-dinner
       @elseif(group.slotName().equals("SNACK")) slot-snack
       @endif
       @if(group.empty()) border-dashed border-border-strong @else border-outline @endif">
  @template.plan._slot_items(ctx = ctx, group = group)
</div>
