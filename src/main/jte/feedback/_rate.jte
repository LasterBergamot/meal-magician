@import java.util.Map
@import hu.lasterbergamot.mealmagician.feedback.RatingView
@import hu.lasterbergamot.mealmagician.feedback.Thumb
@import hu.lasterbergamot.mealmagician.feedback.internal.RatingPage
@import hu.lasterbergamot.mealmagician.shared.web.HtmlAttributes
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param RatingPage page
<%-- The rating form of one slot, and the other members' ratings under it (D27). It swaps itself
     (hx-target="this", outerHTML): a save re-renders it with a thanks line, a refusal returns it with
     422, the message and the typed values. The thumb is a pair of radio inputs drawn as large touch
     targets; thumb down is the up icon turned upside down (rotate-180), never a second icon. Clear
     is a submit button with its own formaction, so it works without JavaScript too. Others are
     listed by display name; the page model never carries an e-mail address. --%>
<form id="rating-form" method="post" action="${page.action()}" hx-post="${page.action()}" hx-target="this" hx-swap="outerHTML" novalidate
      class="flex flex-col gap-4">
  @if(ctx.csrf() != null)
    <input type="hidden" name="_csrf" value="${ctx.csrf().token()}">
  @endif
  <h3 class="font-display text-xl font-semibold text-ink">${ctx.t("rating.question")}</h3>

  @if(page.errorKey() != null)
    @template.ui._alert(kind = "danger", title = ctx.t("rating.error.title"), message = ctx.t(page.errorKey()), action = null)
  @endif

  <fieldset class="flex flex-col gap-2">
    <legend class="text-sm font-extrabold leading-5 text-ink">${ctx.t("rating.thumb.legend")}</legend>
    <div class="grid grid-cols-2 gap-3">
      <label class="relative">
        <input type="radio" name="thumb" value="UP" checked="${page.form().up()}" class="peer sr-only">
        <span class="flex min-h-14 cursor-pointer items-center justify-center gap-2 rounded-md border-2 border-border-strong bg-surface-raised px-3 text-ink peer-checked:border-outline peer-checked:bg-brand-soft peer-checked:text-brand-ink peer-focus-visible:outline-3 peer-focus-visible:outline-solid peer-focus-visible:outline-offset-2 peer-focus-visible:outline-focus-ring">
          @template.ui._icon(name = IconName.UP, size = 28, filled = false)
          <span class="sr-only">${ctx.t("rating.thumb.up")}</span>
        </span>
      </label>
      <label class="relative">
        <input type="radio" name="thumb" value="DOWN" checked="${page.form().down()}" class="peer sr-only">
        <span class="flex min-h-14 cursor-pointer items-center justify-center gap-2 rounded-md border-2 border-border-strong bg-surface-raised px-3 text-ink peer-checked:border-outline peer-checked:bg-danger-soft peer-checked:text-danger-ink peer-focus-visible:outline-3 peer-focus-visible:outline-solid peer-focus-visible:outline-offset-2 peer-focus-visible:outline-focus-ring">
          <span class="inline-flex rotate-180">@template.ui._icon(name = IconName.UP, size = 28, filled = false)</span>
          <span class="sr-only">${ctx.t("rating.thumb.down")}</span>
        </span>
      </label>
    </div>
  </fieldset>

  @template.feedback._stars(ctx = ctx, form = page.form())

  <div class="flex flex-col gap-1.5">
    <label for="rating-comment" class="text-sm font-extrabold leading-5 text-ink">${ctx.t("rating.comment")}</label>
    <textarea id="rating-comment" name="comment" rows="3" maxlength="500"
              $unsafe{HtmlAttributes.render(HtmlAttributes.describedBy("rating-comment", true, false))}
              class="block w-full rounded-md border-2 border-border-strong bg-surface-raised px-4 py-3 text-base text-ink">${page.form().comment()}</textarea>
    <p id="rating-comment-hint" class="text-sm text-ink-muted">${ctx.t("rating.comment.hint")}</p>
  </div>

  <div class="flex flex-wrap items-center gap-3">
    @template.ui._button(variant = "primary", size = "md", type = "submit", label = ctx.t("rating.save"), icon = null, busy = false, disabled = false, attrs = Map.of())
    @if(page.rated())
      @template.ui._button(variant = "ghost", size = "md", type = "submit", label = ctx.t("rating.clear"), icon = null, busy = false, disabled = false,
        attrs = Map.of("hx-post", page.clearAction(), "formaction", page.clearAction()))
    @endif
  </div>

  @if(page.saved())
    <p role="status" class="flex items-center gap-2 rounded-md border-2 border-outline bg-success-soft p-3 text-sm font-extrabold text-success-ink">
      @template.ui._icon(name = IconName.CHECK, size = 20, filled = false)
      <span>${ctx.t("rating.thanks")}</span>
    </p>
  @endif

  <div class="flex flex-col gap-2 border-t-2 border-dashed border-border pt-4">
    <h4 class="text-sm font-extrabold leading-5 text-ink">${ctx.t("rating.others")}</h4>
    @if(page.hasOthers())
      <ul class="flex flex-col gap-2">
        @for(RatingView other : page.others())
          <li data-rating-author="${other.authorName()}" class="flex flex-col gap-1 rounded-md border-2 border-border bg-surface-raised p-3">
            <div class="flex flex-wrap items-center gap-2">
              <span class="font-extrabold text-ink">${other.authorName()}</span>
              @if(other.thumb() == Thumb.UP)
                <span class="inline-flex items-center text-brand-ink">
                  @template.ui._icon(name = IconName.UP, size = 20, filled = false)
                  <span class="sr-only">${ctx.t("rating.thumb.up")}</span>
                </span>
              @else
                <span class="inline-flex items-center text-danger-ink">
                  <span class="inline-flex rotate-180">@template.ui._icon(name = IconName.UP, size = 20, filled = false)</span>
                  <span class="sr-only">${ctx.t("rating.thumb.down")}</span>
                </span>
              @endif
              @if(other.stars() != null)
                <span class="inline-flex items-center gap-1 text-sm font-extrabold text-accent-ink">
                  <span>${other.stars()}</span>
                  @template.ui._icon(name = IconName.STAR, size = 16, filled = false)
                  <span class="sr-only">${ctx.t("rating.stars.unit")}</span>
                </span>
              @endif
            </div>
            @if(other.comment() != null)
              <p class="break-words text-sm text-ink">${other.comment()}</p>
            @endif
          </li>
        @endfor
      </ul>
    @else
      <p class="text-sm text-ink-muted">${ctx.t("rating.others.none")}</p>
    @endif
  </div>
</form>
