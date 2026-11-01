@import java.util.List
@import hu.lasterbergamot.mealmagician.shared.web.HtmlAttributes
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.SegmentOption

@param String label
@param List<SegmentOption> options
@param String selected
@param String target
<%-- The segmented control (plan-ui-redesign.md section 5): two to four segments, one of them
     selected. The segments are links, so without JavaScript each one loads its own page. label is
     the accessible name of the group, already translated. selected is the value of the segment
     shown as active; it gets a violet fill and a check icon, and aria-selected.
     target is a CSS selector (for example "#content") for htmx: when it is set, every segment
     fetches its href into that target and pushes the URL, so Back and reload work. When it is null
     the segments are plain links, which is what the style guide uses so that a click does not swap
     the page it is on. --%>
<div role="tablist" aria-label="${label}"
     class="inline-flex max-w-full flex-wrap gap-1 rounded-full border-2 border-border-strong bg-surface-sunken p-1">
  @for(SegmentOption option : options)
    @if(option.value().equals(selected))
      <a role="tab" href="${option.href()}" aria-selected="true"
         class="inline-flex min-h-11 items-center gap-1.5 rounded-full border-2 border-outline bg-brand px-4 text-sm font-extrabold text-on-brand shadow-sticker-1"
         $unsafe{HtmlAttributes.render(HtmlAttributes.htmxFetch(option.href(), target))}>
        @template.ui._icon(name = IconName.CHECK, size = 20, filled = false)
        <span>${option.label()}</span>
      </a>
    @else
      <a role="tab" href="${option.href()}" aria-selected="false"
         class="inline-flex min-h-11 items-center gap-1.5 rounded-full border-2 border-transparent px-4 text-sm font-extrabold text-ink hover:bg-surface-raised"
         $unsafe{HtmlAttributes.render(HtmlAttributes.htmxFetch(option.href(), target))}>
        <span>${option.label()}</span>
      </a>
    @endif
  @endfor
</div>
