@import gg.jte.Content
@import java.util.Map
@import hu.lasterbergamot.mealmagician.shared.web.IconName

@param String id
@param String title
@param String closeLabel
@param String role
@param boolean centred
@param boolean closable
@param Content trigger
@param Content body
<%-- The sheet (plan-ui-redesign.md sections 5 and 6.3): a native <dialog> opened with showModal() and
     driven by the Alpine component "sheet" in static/js/app.js. Phones get a bottom sheet with a
     grabber that can be dragged down to close it; from md up it is a centred dialog. centred = true
     makes it a centred dialog on every size with no grabber (the confirm dialog uses that).

     id must be unique on the page. title is the heading and names the dialog for screen readers.
     closeLabel is the sr-only label of the X button (ctx.t("sheet.close")); it is shown only when
     closable = true. role is "dialog", or "alertdialog" for a confirmation.

     trigger is the control that opens the sheet, for example a ui/_button with x-on:click="show".
     It must be inside this component (it is): focus returns to its first focusable control when the
     sheet closes. body is the content; htmx may load into it, and a sheet is never opened from a
     sheet.

     Escape closes it (the browser's cancel), so does the X, a click on the backdrop and a swipe.
     Tab cycles inside it. Nothing here is hand-written CSS: colours and shadows are tokens, and
     the starting and transition states come from Tailwind's starting: variant. Classes are written
     out in full, so Tailwind finds them. --%>
<div x-data="sheet" class="contents">
  <span x-ref="opener" class="contents">${trigger}</span>
  <dialog x-ref="dialog" id="${id}" role="${role}" aria-labelledby="${id}-title"
          class="max-w-none overflow-visible bg-transparent p-0 text-ink backdrop:bg-scrim transition-[translate,opacity] duration-[var(--mm-duration-base)] starting:opacity-0
            @if(centred)
              m-auto w-[min(100%_-_2rem,32rem)] starting:translate-y-4
            @else
              mt-auto w-full starting:translate-y-full md:m-auto md:w-[min(100%_-_2rem,32rem)] md:starting:translate-y-4
            @endif">
    <div class="flex max-h-[88dvh] flex-col bg-surface-raised shadow-sticker-3
      @if(centred)
        rounded-xl border-2 border-outline
      @else
        rounded-t-xl border-x-2 border-t-2 border-outline md:rounded-xl md:border-2
      @endif">
      @if(!centred)
        <div x-ref="grabber" aria-hidden="true" class="flex h-11 shrink-0 touch-none items-center justify-center md:hidden">
          <span class="h-1.5 w-12 rounded-full bg-border-strong"></span>
        </div>
      @endif
      <div class="flex shrink-0 items-start gap-3 px-5 pt-4 pb-2">
        <h2 id="${id}-title" class="min-w-0 flex-1 font-display text-2xl font-semibold text-ink">${title}</h2>
        @if(closable)
          @template.ui._button(variant = "ghost", size = "icon", type = "button", label = closeLabel, icon = IconName.X, busy = false, disabled = false, attrs = Map.of("x-on:click", "hide"))
        @endif
      </div>
      <div class="min-h-0 flex-1 overflow-y-auto overscroll-contain px-5 pt-2 pb-5">
        ${body}
      </div>
    </div>
  </dialog>
</div>
