@import gg.jte.Content
@import java.util.Map

@param String id
@param String title
@param String message
@param String cancelLabel
@param String confirmLabel
@param Map<String, String> attrs
@param Content trigger
<%-- The confirm dialog (plan-ui-redesign.md section 5, UI-D6): a destructive question is always a
     centred alert dialog, on phones too, never a bottom sheet. It is ui/_sheet with centred = true,
     no X button and role = "alertdialog", so Escape, the backdrop, the focus trap and the focus
     return all work as for a sheet.

     title names the thing ("Remove Anna from Kovács household?"), message says what happens in one
     sentence. cancelLabel closes the dialog without doing anything. confirmLabel is the danger
     button; attrs are its extra attributes, the request the action sends (hx-post, hx-target), or
     x-on:click="hide" when nothing should be sent. The request's response decides what follows: a
     redirect or a swap that removes this dialog closes it, and a rejected request shows its error
     in the page. Put the trigger (a danger button) next to the thing it removes, and keep the dialog
     out of any region that htmx swaps, or the swap takes the open dialog with it. --%>
@template.ui._sheet(id = id, title = title, closeLabel = "", role = "alertdialog", centred = true, closable = false, trigger = trigger, body = @`
  <p class="text-ink-muted">${message}</p>
  <div class="mt-5 flex flex-wrap justify-end gap-3">
    @template.ui._button(variant = "secondary", size = "md", type = "button", label = cancelLabel, icon = null, busy = false, disabled = false, attrs = Map.of("x-on:click", "hide"))
    @template.ui._button(variant = "danger", size = "md", type = "button", label = confirmLabel, icon = null, busy = false, disabled = false, attrs = attrs)
  </div>
`)
