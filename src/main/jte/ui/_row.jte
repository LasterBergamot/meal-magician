@import gg.jte.Content

@param boolean selected
@param Content content
<%-- One row of a list (plan-ui-redesign.md section 5): a list item at least 60 px tall, with a
     dashed separator above it (the first row has none). A list is a ul inside a ui/_card, and each
     item is a row, so the row is an li and must sit in a ul. selected = brand-soft; a selected row
     also needs a Current badge with a check, so the colour is never the only cue. One stretched
     link per row, and no link inside a link (the row is relative for that). --%>
<li class="relative flex min-h-[60px] items-center gap-3 border-t-2 border-dashed border-border px-3 py-2 text-ink first:border-t-0
  @if(selected) bg-brand-soft
  @endif">
  ${content}
</li>
