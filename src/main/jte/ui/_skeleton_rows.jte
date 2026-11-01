@param int rows
@param String label
<%-- The loading placeholder for a list (plan-ui-redesign.md section 5): rows grey rows that mimic
     the rows that will arrive (an avatar disc and two bars). Use it when an htmx load takes longer
     than about 300 ms, with hx-indicator on the region. The region is aria-busy and a role="status"
     with a screen-reader label, so the wait is announced once; the bars are aria-hidden. Each row
     pulses, and motion-reduce stops it. label comes from ctx.t, and rows is how many to draw. --%>
<div aria-busy="true" role="status"
     class="rounded-lg border-2 border-outline bg-surface-raised shadow-sticker-2">
  <span class="sr-only">${label}</span>
  <div aria-hidden="true" class="flex flex-col">
    @for(int i = 0; i < rows; i++)
      <div class="flex min-h-[60px] items-center gap-3 border-t-2 border-dashed border-border px-3 py-2 first:border-t-0 animate-pulse motion-reduce:animate-none">
        <span class="size-8 shrink-0 rounded-full bg-surface-sunken"></span>
        <span class="flex min-w-0 flex-1 flex-col gap-2">
          <span class="h-3 w-2/3 rounded-full bg-surface-sunken"></span>
          <span class="h-2.5 w-1/3 rounded-full bg-surface-sunken"></span>
        </span>
      </div>
    @endfor
  </div>
</div>
