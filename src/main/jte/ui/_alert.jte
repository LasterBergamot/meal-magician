@import gg.jte.Content
@import hu.lasterbergamot.mealmagician.shared.web.IconName

@param String kind
@param String title
@param String message
@param Content action
<%-- The alert (plan-ui-redesign.md section 5): an icon, a bold title and one sentence, with an
     optional action (a ui/_button, or null). kind: success (check icon) and info (info icon) are
     role="status", which a screen reader reads out politely; warning and danger (alert icon) are
     role="alert", which interrupts. The 409 conflict is a warning with a Reload button. The icon and
     the title name the kind, so the colour is never the only cue. There are no floating toasts.
     data-kind is for the tests. --%>
<div data-kind="${kind}" role="${kind.equals("warning") || kind.equals("danger") ? "alert" : "status"}"
     class="flex items-start gap-3 rounded-md border-2 border-outline p-4 leading-6
       @if(kind.equals("success")) bg-success-soft text-success-ink
       @elseif(kind.equals("info")) bg-info-soft text-info-ink
       @elseif(kind.equals("warning")) bg-warning-soft text-warning-ink
       @elseif(kind.equals("danger")) bg-danger-soft text-danger-ink
       @endif">
  @if(kind.equals("success"))
    @template.ui._icon(name = IconName.CHECK, size = 24, filled = false)
  @elseif(kind.equals("info"))
    @template.ui._icon(name = IconName.INFO, size = 24, filled = false)
  @else
    @template.ui._icon(name = IconName.ALERT, size = 24, filled = false)
  @endif
  <div class="flex min-w-0 flex-1 flex-col gap-2">
    <p class="font-sans text-base font-extrabold leading-6">${title}</p>
    <p class="text-sm font-medium leading-5">${message}</p>
    @if(action != null)
      <div class="pt-1">${action}</div>
    @endif
  </div>
</div>
