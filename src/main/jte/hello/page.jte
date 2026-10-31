@import hu.lasterbergamot.mealmagician.shared.web.HelloView
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param HelloView hello
@template.layout.base(ctx = ctx, title = ctx.t("hello.title"), content = @`
  <section class="rounded-lg bg-white p-5 shadow-sm md:p-8">
    <h1 class="text-2xl font-bold text-brand-800 md:text-3xl">${ctx.t("hello.heading")}</h1>
    <p class="mt-2 text-stone-700">${ctx.t("hello.lead")}</p>
    @template.hello._greeting(ctx = ctx, hello = hello)
  </section>
`)
