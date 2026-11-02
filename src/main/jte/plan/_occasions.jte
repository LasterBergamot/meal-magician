@import hu.lasterbergamot.mealmagician.planning.internal.OccasionChip
@import hu.lasterbergamot.mealmagician.planning.internal.WeekPage
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param WeekPage page
<%-- The occasion strip: the week's occasions (birthdays, holidays) as gold-soft links to their pages,
     above the days. Nothing is drawn for a week without occasions. data-occasion-id is for tests. --%>
@if(!page.occasions().isEmpty())
  <ul aria-label="${ctx.t("plan.occasions.label")}" class="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
    @for(OccasionChip o : page.occasions())
      <li>
        <a href="${o.href()}" data-occasion-id="${o.id()}" class="flex min-h-11 items-center gap-3 rounded-md border-2 border-outline bg-accent-soft px-3 py-2 text-accent-ink shadow-sticker-1 hover:shadow-sticker-2">
          @template.ui._icon(name = IconName.GIFT, size = 20, filled = false)
          <span class="font-extrabold">${o.name()}</span>
          <span class="text-sm">${o.dayLabel()}</span>
          @template.ui._badge(tone = "neutral", label = ctx.t(o.typeKey()), icon = null)
        </a>
      </li>
    @endfor
  </ul>
@endif
