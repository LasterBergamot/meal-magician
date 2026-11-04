@import java.util.Map
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param String typed
@param String errorKey
<%-- The composer (Appendix D.6): a text area with a visually hidden label, a primary send button, a
     counter that appears from 800 characters (the chatComposer component in app.js counts and sends
     with Ctrl or Cmd + Enter) and the caption that says the message goes to Google. It is sticky at the
     bottom: above the tab bar on a phone. While a request is in flight, hx-disabled-elt locks the field
     and the button and the waiting bubble shows (hx-indicator). An error keeps the typed text and shows
     its sentence under the field. The element carries hx-swap-oob, so a reply replaces the composer in
     place; on the page itself the attribute does nothing, because only a response is swapped. --%>
<div id="chat-composer" hx-swap-oob="true" class="sticky bottom-[calc(var(--mm-bottom-bar)+env(safe-area-inset-bottom))] z-10 border-t-2 border-outline bg-surface-raised py-3 md:bottom-0 md:rounded-lg md:border-2 md:px-4">
  <form id="chat-form" x-data="chatComposer" x-ref="form" hx-post="/assistant/chat" hx-target="#chat-thread" hx-swap="beforeend show:bottom" hx-indicator="#chat-waiting" hx-disabled-elt="find textarea, find button" class="flex flex-col gap-1.5">
    <label for="chat-message" class="sr-only">${ctx.t("assistant.chat.input")}</label>
    <div class="flex items-end gap-3">
      <textarea id="chat-message" name="message" rows="2" required aria-invalid="${errorKey != null ? "true" : "false"}"
                x-ref="message" x-on:input="count" x-on:keydown="send"
                class="block min-h-12 max-h-40 min-w-0 flex-1 resize-none rounded-md border-2 bg-surface-raised px-4 py-3 text-base leading-6 text-ink [field-sizing:content] placeholder:text-ink-muted
                  @if(errorKey != null) border-danger @else border-border-strong @endif">${typed}</textarea>
      @template.ui._button(variant = "primary", size = "icon", type = "submit", label = ctx.t("assistant.chat.send"), icon = IconName.RIGHT, busy = false, disabled = false, attrs = Map.of())
    </div>
    @if(errorKey != null)
      <p class="text-sm font-bold text-danger">${ctx.t(errorKey)}</p>
    @endif
    <p x-ref="count" data-format="${ctx.t("assistant.chat.count", "#")}" x-show="showCount" x-text="label" class="text-right text-xs text-ink-muted"></p>
    <p class="text-xs text-ink-muted">${ctx.t("assistant.chat.footnote")}</p>
  </form>
</div>
