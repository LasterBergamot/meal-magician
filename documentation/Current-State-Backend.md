# Current State: Backend

What exists in `main` today: the build skeleton, the database, the `identity` module (sign-in), the
`household` module (create, rename, switch, invites, members), the `catalog` module (the meal
library, #66 and #88), the `planning` module (the weekly plan, reuse, occasions and their templates,
and the week page, `planning/internal/PlanController`), the `feedback` module (ratings and the meal
history, #124, #128, #132) and the `sharing` module (share links, the print view and the public
share page, #125, #129, #131). Phases 0 to 5 are built, and so is the `assistant` module (Phase 7, #157 to #162, below): the
Gemini connection and spend cap, the meal search, the draft logic, the week, slot, chat and occasion
menu pages, and the eval harness. It is off by default; see [[AI Assistant]].

- **Application class** `MealMagicianApplication` starts Spring Boot; everything lives below its
  package `hu.lasterbergamot.mealmagician`.
- **Modules** (Spring Modulith): `identity`, `household`, `catalog`, `planning`, `feedback`,
  `sharing`, `assistant` and the open shared kernel `shared`. Each has a `package-info.java` that says what the
  module owns; `catalog` and `planning` have their services, `catalog` also has the meal pages and `planning` the week
  page;
  `feedback` has the ratings backend and `sharing` the share links with their public page (both
  below). The rule: a module's root package is its public API,
  everything else goes in `internal/` and other modules may not import it.
  `ModularityTests` fails the build on a violation and writes module docs to
  `build/spring-modulith-docs`.
- **The assistant (#157 to #162), module `assistant`:** the foundation (#157) comes first below, then
  the features that sit on it. It is **off by default** (`mm.ai.enabled=false`): then no Google
  client exists, no credentials are needed and the app starts as fast as before.
  - **Spring AI on Vertex AI.** `spring-ai-starter-model-google-genai` (chat) and
    `...-embedding`, Spring AI 2.0.2 (BOM in `gradle/libs.versions.toml`), connect to Gemini with the
    Cloud Run runtime account, so there is no API key. `application.yml` switches Spring AI's
    models off through one nested placeholder driven only by `mm.ai.enabled`, and excludes the
    embedding starter's connection auto-configuration (it has no switch and would open a Google
    client at startup); `AiConfig` builds that one bean when the assistant is on.
  - **`ModelGateway`** is the only class that calls Spring AI's `ChatModel` and `EmbeddingModel`,
    and the only place household data could leave the app. Every call: cap checked first, a
    timeout (`mm.ai.timeout`), usage read from the provider (thinking tokens count as output) and
    added to the ledger. It also runs the tool loop: the Gemini model only asks for tools, so the
    gateway runs them with `ToolCallingManager`, calls again and stops after 12 calls, metering each
    one. A whole request may take at most 120 seconds (`mm.ai.request-timeout`); each call's own
    limit is the smaller of `mm.ai.timeout` and what is left of that budget. The in-app spend cap is
    $5 per household and month (`mm.ai.monthly-cap-usd`). It logs nothing, and its failures name only the kind of error, because a
    provider's message can echo the prompt.
  - **Spend cap.** `UsageLedger` keeps `assistant_usage` (V13): tokens and micro-USD per household
    and calendar month (Europe/Budapest, so a new month starts at zero by itself). A household at
    or over `mm.ai.monthly-cap-usd` (default 10.00) gets `SpendCapExceededException` and no call is
    made. Usage is added with one `UPDATE x = x + n` (row created with `ON CONFLICT DO NOTHING`
    first) in a transaction of its own, so concurrent calls lose nothing. Cost is rounded up per
    call. The check is before the call, so two simultaneous calls can overshoot the cap by one call.
  - **Scripted model (#160).** The profile `fake-ai` (`application-fake-ai.yml`, `FakeAiConfig`)
    switches the assistant on and replaces the two Spring AI models with `ScriptedChatModel` and
    `ScriptedEmbeddingModel`, so the real gateway, ledger, generator and controllers run with no
    Google. The chat model calls the prompt's own `searchMeals` tool (so the ids are real), keeps the
    request texts it received, and can be slowed (`mm.ai.fake.delay`) or made to fail. It is used by
    the tests, by `compose.e2e.yml` and for a local look; `ProductionConfigGuard` refuses it in
    production.
  - **Meal search index (#158).** `EmbeddingIndex` finds the household's meals that fit a request
    ("something light with vegetables") without sending the library to the model. The vectors live
    in `meal_embedding` (V14, Postgres only, `vector(768)`), written by `EmbeddingStore` with
    `JdbcClient`. Every row carries `household_id`, and a composite foreign key to `meal` makes the
    database refuse a cross-household row and delete the row with its meal. **It stays current
    without events or a scheduler:** Cloud Run throttles the CPU after a response and nothing may
    wake the app on a timer (D11), so `sync(userId, householdId)` runs inside the assistant's own
    request. It builds one text per active meal (`MealDocument`: name, tags, ingredients, occasion
    types, notes, at most 2 000 characters; **no ratings and no recipe link**, so the text changes
    only when the meal does), hashes it with the model id and vector size, and embeds only meals
    whose stored hash differs or is missing, in batches of 50 through `ModelGateway` (so the spend
    cap and timeout apply). Rows of archived or removed meals are deleted; a changed model id or
    size changes every hash and re-embeds everything. A per-household lock makes two simultaneous
    requests embed once. `search(userId, householdId, query, limit)` embeds the query and returns
    meal ids by cosine distance, always `WHERE household_id = ?`, never archived meals; it does not
    sync (the caller decides). There is no vector index: a household has hundreds of meals, so a
    scan is exact and fast. Meal text (names, notes, ingredients) goes to Vertex to be embedded and
    is never logged. The table is derived data and can be rebuilt; the H2 profile has no such table,
    so the assistant is unavailable without Postgres.
  - **Tools and drafts (#159).** The model can read the household's data through six tools in
    `AssistantTools`, one object per request that is built for one person and one household. Those two
    ids are fixed in its constructor: **no tool has a household or user parameter**, so the model
    cannot ask for another household (a test checks every tool's parameters and schema, and that
    each returns nothing when the data is in another household), and **no tool writes anything**.
    The tools are `recentWeeks` (up to 8 weeks), `searchMeals` (semantic, via the index, up to 15,
    with tags and ingredients; the first call of a request syncs the index), `mealDetails` (up to
    15), `householdPreferences` (liked, disliked and comments per member, from
    `RatingService.recent`), `upcomingOccasions` (up to 90 days) and `currentDate` (with
    "Hungary, Central Europe" so the model can judge the season). Every limit is clamped.
  - **What is anonymised.** Members are "member 1", "member 2" ... (`Pseudonyms`, numbered by user
    id); a member who left is "a former member". A display name or an e-mail address is never put
    into a prompt or a tool result. What does go to Google: meal names, notes, tags, ingredients,
    occasion names, dates, slot types, ratings and the **rating comments** and the member's request
    as typed (a member can write a name into those; the privacy note says so). All member text is
    data: the system prompt says so, the request and follow-ups are put into the prompt as quoted
    JSON strings, and the model can only read. The system prompt also forbids claiming that a meal is
    safe for an allergy (no dietary rules are stored) and asks for the answer in the member's
    language (Hungarian when `Locale` is `hu`, else English).
  - **How a draft is made.** `AssistantService.draftWeek(userId, householdId, locale, anyDay,
    request)` checks membership and the length (1 200 characters at most, else
    `InvalidRequestException` and no model call), then `DraftGenerator` sends the prompt through
    `ModelGateway` (cap and timeout apply; the call runs outside any database transaction) with
    the tools attached. The answer is JSON in the text (`DraftProposal`: a summary and items that are
    either `{date, slotType, mealId}` or `{date, slotType, idea{name, reason, tags, ingredients}}`);
    tool calling and JSON output work together on the chosen model, and the schema comes from Spring
    AI's `BeanOutputConverter`. `DraftValidator` then checks every item: a `mealId` that is not an
    active meal of the household, a date outside the week and an idea without a name are dropped,
    ideas are capped at 5, texts are trimmed to their column sizes. If ids were invalid or the answer
    was not usable JSON the model is told what was wrong and asked **once** more; a second failure
    throws `DraftGenerationException` and nothing is stored. Invented ids never reach storage.
  - **Stored drafts.** `assistant_draft` and `assistant_draft_item` (V15, common): a draft has its
    household, the week's Monday, a status (OPEN or ACCEPTED), the member's request (up to 1 200
    characters), the conversation as JSON and the summary. An item is a library meal **or** an idea
    (a check enforces exactly one), with composite foreign keys that keep it in the draft's household
    and refuse a meal of another one. A draft is kept for 7 days: `DraftStore` deletes older ones when
    a new draft is stored (no scheduler), so the request text and conversation also appear in
    backups until then. Any member may use any draft of the household.
  - **Using a draft.** `refine` sends a follow-up (same length limit) with the draft as it stands,
    including what members removed by hand, and replaces the items; `removeItem` takes one out;
    `addIdeaToLibrary` creates the meal through `MealService.create` (name, tags, ingredients) and
    turns the item into a library item; `discard` deletes the draft; `draft` reads it. `accept(…,
    CopyMode)` closes the draft with one conditional update (OPEN to ACCEPTED, so a second accept is
    a `DraftClosedException`, 409) and writes the library items through `PlanningService.applyDraft`
    in the same transaction, so a refused write (a week that changed, a meal archived since) leaves
    the draft open. Ideas that are not in the library are dropped and counted in `AcceptResult`;
    they never reach the plan by themselves.
  - **`PlanningService.applyDraft` and `RatingService.recent`.** `applyDraft(userId, householdId,
    weekMonday, List<DraftSlot>, CopyMode)` is the assistant's only door to the plan: it refuses an
    empty list, a date outside the week (`OUTSIDE_WEEK`), an archived meal (`MEAL_ARCHIVED`) and a
    meal of another household (404) before writing anything, then writes through the same
    `SlotWriter` as copying (merge appends; replace empties Monday to Sunday first, D26) and
    publishes one `SlotPlanned` per slot. `RatingService.recent(userId, householdId, limit)` returns
    the household's latest ratings of any meal, newest first, with comments, former members dropped
    (limit 1 to 100).
  - **Public API:** `AssistantService.enabled()`, `usageThisMonth(userId, householdId)` (members
    only; 404 while off) and the draft methods above (`draftWeek`, `refine`, `removeItem`,
    `addIdeaToLibrary`, `accept`, `discard`, `draft`; all 404 while the assistant is off). `ProductionConfigGuard`
    fails a `prod` start with the assistant on and a project, location, model id or price missing.
  - **The chat (#161).** `AssistantService.chat(userId, householdId, locale, history, message)` answers
    one message with `ChatService`: the topic policy of `mm.ai.chat-scope` (`food-and-plan`, the text
    in `resources/assistant/chat-policy-food.txt`; an unknown scope fails the start), the stored turns
    and the read-only tools of the drafts. A message over 1 000 characters is refused before any model
    call (`InvalidRequestException`). The history is `ChatTurn` records (serialisable) under the session
    attribute `mm.assistant.chat` (`ChatSession`, at most 20 turns, the oldest dropped); the controller
    stores both turns only after a successful answer. Nothing goes to a table.
  - **Occasion menus (#161).** `AssistantService.suggestMenu(userId, householdId, locale, occasionId)`
    (`MenuSuggester`) reads the occasion and the menus of up to five earlier occasions of its type
    (within five years), asks the model once (no retry: a bad answer is `DraftGenerationException`),
    keeps the live library meal ids (at most six) and up to three ideas. It never writes a menu.
    `addMenuIdea` creates the library meal for an idea. The loader and the routes are
    `AssistantOccasionController` (the name differs from the planner's `OccasionMenuController`, so the
    two beans do not clash) and `ChatController` for `/assistant`, `/assistant/chat` and its reset.
  - **The eval harness (#162).** It is test code, not product code: the package
    `src/test/java/.../assistant/eval` and the files in `src/test/resources/eval/`. It compares chat
    models on 16 cases against an invented Hungarian household (`household.json`, about 40 meals, three
    members, three weeks of rated history). `EvalRunner` is a plain `main`, started by the Gradle task
    `aiEval` (`task ai:eval -- --models <id,id>`), and it is **not** part of `task test` or CI because it calls the
    real model. `EvalHarness` starts the real application against the Testcontainers Postgres of the
    integration tests (`AbstractPostgresIntegrationTest.POSTGRES`), loads the household through the real
    services, moves the clock to each case's date and calls `AssistantService`; tokens and cost are read
    from the same `UsageLedger`, and the ledger's cap is set very high so that the runner's own
    `--budget-usd` limit is what stops a model. `Checks` holds the deterministic checks (real meal ids,
    no neighbouring repeats, "less meat", occasions, language, idea cap, seasonal and the rest) as pure
    functions with a unit test on a good and a bad sample each (`ChecksTest`); `Report` writes
    `build/eval/<timestamp>.md`. `EvalHarnessTest` (integration tag) runs the whole harness with the
    scripted model, so it needs Docker but no Google. The owner reviews the report and signs off the
    model (PLAN.md D33); how to run it is in [[AI Assistant]].
- **Guard tests (#14)** enforce the conventions so nobody has to remember them:
  `EveryEndpointNeedsSignInTests` calls every mapped controller endpoint anonymously and fails if
  one outside `SecurityConfig.PUBLIC_PATHS` answers with anything but a redirect to the login page
  (a new controller is covered automatically); `DocsToolingTests` checks the agent docs (see
  [[Current State: Everything Else]]); `UiGuardTest` covers templates and message bundles
  ([[Current State: Frontend]]). Each has tests that run the check on a deliberately broken example.
  `TenantIsolationTests` (#44) is the tenant guard: it finds every controller method that takes a
  `CurrentHousehold` (or a `{householdId}` path variable) and fails if it is not registered in
  `TenantEndpoints`; for each registered endpoint it then checks that a person in no household gets
  404, that the owner of *another* household gets 404 for ids that are not theirs, that a plain
  member gets 403 on owner-only actions and that the real owner is let through (so the path is
  real). The household endpoints keep their detailed behaviour tests (`HouseholdPagesTest`).
- **Households (#41), module `household`:** `HouseholdService` is the public entry point
  (create, list, rename, and the membership lookup `find`/`require`). Every method takes the
  asking person's id and checks membership itself: another household gives
  `HouseholdNotFoundException` (404, the same answer as "does not exist", so nothing leaks) and a
  member doing an owner-only action gives `OwnerOnlyException` (403). The creator becomes OWNER;
  a household name is 1 to 100 characters. `CurrentHouseholdResolver` finds the household of a
  request: the id lives in the session but is re-checked against `membership` on **every**
  request, falling back to the person's first household. Controllers of other modules get it
  with a `CurrentHousehold` parameter (an argument resolver; a person with no household gets
  404) and pass `id()` to every repository call. `household/internal` holds the entities
  (`Household` with `@Version`, `Membership`), the repositories and `HouseholdController`.
  `shared.web.HouseholdNavSource` is how the header learns the household without `shared`
  depending on `household` (the same trick as `CsrfMetaSource`).
- **Invites (#42), module `household`:** an owner invites an e-mail address and shares a
  `/join/<code>` link through any messenger (no e-mail is sent). `InviteService` is the public entry
  point: `create` (owner only; validates the address, adds it to the allowlist with source `INVITE`
  through `identity.AllowlistEditor`, returns the raw code once), `list`, `revoke`, `preview` and
  `join`. The owner picks a lifetime of 2 hours, 24 hours (default) or 3 days.
  - **Why it is safe:** the code is 256 random bits (`SecureRandom`); the `invite` table (V5) stores
    only its SHA-256 (`CodeHasher`), so a leaked row is no link. Joining needs the signed-in Google
    e-mail to equal the invited one (checked first, so a link that is not yours reveals nothing
    else). An invite works once: `markUsed` is a guarded `UPDATE` that reports how many rows it
    changed, so two simultaneous clicks cannot both win.
  - **Refusals:** unknown code 404, other Google account 403, revoked, used or expired 410; each has
    its own message on the page. Owner-only actions give 403 to members and 404 to strangers, and
    another household's invite id is 404.
  - **Where the code appears:** only in the one response that creates the invite and in the link
    itself, so it can show up in access logs of whatever sits in front of the app (and, briefly, in
    the session's saved request while someone signs in). That is why it expires and is single use.
    It is never logged by the app.
  - **Revoking** keeps the address on the allowlist (removing it is the admin page, #45, below).
  - `/join/**` needs a session like every page: an anonymous visitor is sent to `/login` and
  Spring Security returns them to the link after Google sign-in. Not rate limited yet (Phase 6).
- **Members (#43), module `household`:** `MemberService` is the public entry point. `list` (any
  member; owners first, then by name; names and addresses come from `identity.UserDirectory`, which
  hands out `UserProfile` records instead of entities), `remove` (owner only), `leave` (any member)
  and `transferOwnership` (owner only).
  - **Rules:** the last owner cannot leave (`MemberRefusedException` with `LAST_OWNER`, HTTP 409
    outside the page); an owner cannot remove or hand over to themselves (`SELF`, so leaving is the
    one way out); a person who is not in the household is `MemberNotFoundException` (404), the same
    for "never was" and "is in another household". Members get 403 on remove and transfer,
    strangers 404.
  - **Transfer is a hand-over:** the chosen member becomes OWNER and the asking owner a plain
    MEMBER in one transaction, so the household is never without an owner. Co-owners can still
    exist (the leave rule counts owners), but nothing in the app creates them.
  - **Race safety:** each action first takes a `PESSIMISTIC_WRITE` lock on the household row
    (`HouseholdRepository.findForUpdate`) and only then checks the caller's role, so two owners
    leaving at the same moment run one after the other and the second is refused. The deletes and
    role changes are bulk `UPDATE`/`DELETE` statements that report a row count (0 means "not in this
    household"). `MemberConcurrencyTest` covers it on H2, `PostgresMemberTests` runs the queries on
    Postgres.
  - **Losing access** needs no code: `CurrentHouseholdResolver` re-checks the membership on every
    request, so a removed person's next request for that household is 404 (or falls back to their
    other household).
- **Meal library (#66), module `catalog`:** `MealService` is the public entry point: `create`,
  `quickAdd` (name only, for a phone), `edit`, `archive` and `restore`, `find`, `search` (part of
  the name, a tag and/or an ingredient, ignoring case, archived meals left out unless asked for),
  `tagNames`, `tagSuggestions` and `ingredientSuggestions`.
  Every method takes the asking person's id and the household id, and first checks membership
  through `HouseholdService.require`, so a stranger gets 404. A meal of another household is 404 as
  well, because every meal lookup is scoped by household. Every member may use the library, so
  nothing here is owner-only and nothing answers 403. The pages that call these methods (the
  library, the form, the quick add) are described in [[Current State: Frontend]].
  - **Data (V6):** `meal` (name up to 200 characters, notes and recipe link up to 2000, `archived`,
    `version` for optimistic locking), `tag` (one set per household; `name_key` is the lower-case
    name, so "Vegan" and "vegan" are one tag) and `meal_tag`. Each `meal_tag` row carries
    `household_id` too, and two composite foreign keys require its meal and its tag to belong to
    that household, so the database itself refuses a link across households.
  - **Rules** (`MealRules`, package-private): a name is required; a recipe link must be `http` or
    `https`, because the link is shown as a clickable anchor and a `javascript:` link would run
    code; blank notes or links are stored as "none"; a tag is 1 to 50 characters. A refused input
    throws `InvalidMealException` with a `MealProblem` and nothing is written (not even a tag). The
    pages choose the message for each problem (`MealFormPage`).
  - **Edits** replace the whole tag set by difference: a tag that stays keeps its link row, so the
    database only sees the links that were added or dropped. A tag created by an edit is created
    for the household, and a tag no meal uses any more is not offered by `tagNames`.
  - **Occasion types (V8):** `meal_occasion_type` has one row per (meal, occasion type): `meal_id`,
    `occasion_type` (`VARCHAR(10)`, with a `CHECK` that allows only the four values) and
    `household_id`. Its primary key is (`meal_id`, `occasion_type`), and it has the same composite
    foreign key to `meal` as `meal_tag` in V6, so the database refuses a link across households. The
    rows are removed with their meal (`ON DELETE CASCADE`). Unlike a tag, a type has no table of its
    own: the enum is the list.
  - **The enum** `catalog.OccasionType` (BIRTHDAY, HOLIDAY, PARTY, OTHER) lives in `catalog`, not in
    `planning`, because `planning` already depends on `catalog`. If the enum were in `planning`,
    the catalog would have to import it to store a meal's types, and Spring Modulith would see a
    module cycle. Phase 4's occasions import the same enum from `catalog`. The owner agreed to
    this on 2026-11-02 (issue #88). A new constant needs a new migration, because the `CHECK` lists
    the values. The words shown come from the `meal.occasion.*` message keys (`OccasionLabels`), not
    from the enum.
  - **Occasion types on the views:** `MealDetails` has an `occasionTypes` collection that, on an
    edit, is the complete set, like the tags: a type left out is removed, and `null` means none.
    `MealView` carries the types in enum order. The four-argument `MealDetails` constructor is for a
    meal with no types.
  - **Edit with a version check:** `edit(userId, householdId, mealId, details, expectedVersion)`
    refuses the change when the meal is no longer at the version the form was opened with, by
    throwing `OptimisticLockingFailureException`, which the page turns into 409. The `@Version`
    check at save only catches two saves that overlap in time. The common case is someone saving
    while a form sits open in another tab, and that is what the version check catches. The
    four-argument `edit` does no such check.
  - **Recently used first:** `RecentMealsProvider` (in `catalog`) answers "which meals were
    planned lately". `planning.internal.PlanningRecentMeals` implements it by calling
    `PlanningService.recentlyPlannedMealIds`. This is dependency inversion: the module arrow stays
    `planning` -> `catalog`, so there is no cycle. `MealsPageFactory` asks for up to 500 meal ids
    for the default sort, and puts the planned meals first (most recently planned first), then the
    other meals, newest first. Where no provider exists (a catalog-only test), the sort is newest
    first.
  - **Events:** each create, edit, archive and restore publishes `MealChanged(householdId,
    mealId)` in the same transaction. Reads publish nothing. Nothing listens to it: the assistant's
    meal search index does not use events (Cloud Run throttles the CPU after a response) and syncs
    itself by content hash at the start of each assistant request instead.
  - **Ingredients (V12, #155, D31):** a meal has a list of ingredient names, no amounts.
    `ingredient` is per household like `tag`, but its `name_key` also drops accents (and lower-cases),
    so "Tojás", "tojas" and " TOJÁS " are one row and the first spelling typed is the one shown.
    `meal_ingredient` links them, with `household_id` and the same two composite foreign keys as
    `meal_tag`. `MealDetails` and `MealView` carry `ingredients` (a name is 1 to 60 characters and
    a meal has at most 30; otherwise `InvalidMealException` with `INGREDIENT_INVALID`); on an edit
    the list is the complete set, and a dropped ingredient only loses its link, it stays in the
    household's list. `MealSearch` has an `ingredient` filter (case and accents ignored). The
    four-argument `MealDetails` and three-argument `MealSearch` constructors still mean "no
    ingredients" and "no ingredient filter".
  - **Ingredient suggestions:** `MealService.ingredientSuggestions(userId, householdId, query,
    limit)` feeds the picker (#156). It returns the household's own ingredients that match first
    (names starting with the text, then the more used, then alphabetical), then entries of the
    starter list that the household does not have yet, at most `limit` (1 to 50). The text is
    folded with the same key, so "tojas" finds "tojás". `IngredientSuggestion(name, known)` says
    which kind each line is. The starter list is the table `ingredient_seed` (about 160 common
    Hungarian ingredient names loaded by V12): global, read-only reference data and the only table
    without a `household_id`. A test recomputes every seed key with `MealRules.ingredientKey`.
  - **Tag suggestions (#174):** `MealService.tagSuggestions(userId, householdId, query)` feeds the
    Tags field of the meal form. The pool is the household's tags that are on at least one meal (the
    same rule as `tagNames`), read with `TagRepository.findUsage` (one query, a `TagUse` row per tag
    with the number of meals). It keeps the tags whose name contains the typed text, ignoring case
    **and accents** (`MealRules.tagSearchKey`, so "vegan" finds "Vegán"), most used first, then by
    name, at most 10. The folding is done in Java because accent folding is not portable between H2
    and Postgres and a household has few tags. Only the search folds accents: a tag's identity is
    still `MealRules.tagKey` (lower case, accents kept), so "vegan" and "vegán" typed by hand stay
    two tags; picking the suggestion is what prevents the near-duplicate. `GET /meals/tags?q=` is
    `TagOptionsController`, member-only and household-scoped like the ingredient route (a person in
    no household gets 404), registered in `TenantEndpoints`.
  - **Race-safe creation:** `NameWriter` finds or creates a tag or ingredient. The old way (look up,
    then save) failed when two people created the same new name at once: the second insert broke the
    unique key, and on Postgres that aborts the whole transaction, so the person got a server error.
    `NameWriter` inserts with `ON CONFLICT DO NOTHING` and looks the row up afterwards, so the
    loser waits for the winner and then simply finds its row. It stays in the caller's
    transaction, so a refused meal leaves no stray tag or ingredient. `PostgresNameRaceTests` forces the
    overlap and fails without the fix.
  - **Known gaps:** none at the moment. Until the picker (#156) the meal form sends no ingredients,
    so saving a meal through the form clears its ingredients.
- **Weekly plan (#80), module `planning`:** `PlanningService` is the public entry point. `week`
  returns the ISO week (Monday to Sunday) that holds any given date, with its slots ordered by date,
  slot type (breakfast, lunch, dinner, snack) and place. `add` puts a library meal on a date and slot
  type, at the end of that group. `move` changes the date and slot type and joins the end of the new
  group. `reorder` moves a slot inside its group (a place below 0 or past the end is clamped).
  `editNote` sets or clears the note, `remove` takes a slot out and `recentlyPlannedMealIds` lists the
  meals planned on or before today, most recent first, each once. `slot` reads one slot (the editor
  opens on it, #98), and `quickAddAndPlan` creates a library meal from a name and plans it in one
  transaction (`MealService.quickAdd`, then `add`): a rejected name (`InvalidMealException`) leaves
  neither a meal nor a slot, and a good one publishes `MealChanged` and `SlotPlanned`. Every method checks membership
  through `HouseholdService.require` (a stranger gets 404). A slot of another household is
  `SlotNotFoundException` (404), and `add` asks `MealService.find`, so another household's meal is
  `MealNotFoundException` (404).
  - **Data (V7):** `meal_slot` has one row per planned meal: the `date`, the `slot_type`, a
    `position` counted from 0 inside its (household, date, slot type) group, the `meal_id`, an
    optional `note` of up to 500 characters and a `version`. The composite foreign key
    `(meal_id, household_id)` to `meal` makes the database refuse a meal of another household, as
    `meal_tag` does in V6. The positions stay contiguous: a move, a remove or a reorder renumbers the
    group. They are not unique on purpose, because a reorder rewrites several rows at once; two
    adds at the same moment can share a place, and ties are ordered by id, so the week stays
    deterministic.
  - **Versions and conflicts:** `move`, `reorder`, `editNote` and `remove` take the version the person
    last saw. An older version is refused before anything is written, and so is a write the database
    refuses because the row changed in the meantime. Both raise `PlanConflictException` (409). A
    renumbered sibling gets a new version too, so a page showing an old week conflicts as well.
  - **Archiving and deleting a meal:** archiving never touches the plan. A slot keeps its archived
    meal: the week still shows it, the slot can still be moved or removed, and only the library's
    default list stops offering the meal. A meal that is still planned cannot be deleted at all: the
    foreign key has no `ON DELETE` clause, so the database refuses and no plan entry disappears
    silently. Nothing in the app deletes a meal today. Deleting a whole household removes its plan and
    its meals with it (tested on Postgres).
  - **Events:** `SlotPlanned(householdId, slotId, date, slotType, mealId)` is published on `add`, and
    on `move` when the slot really changes place. Reordering, editing a note and removing publish
    nothing. Nothing listens yet, and PLAN §4.1 names no listener for it.
  - **What the pages call:** a `SlotView` carries the meal id, not the name, so the week view calls
    `week(...)` and gets the names from one
    `MealService.search(…, new MealSearch(null, null, true))`, joined by meal id. "Recently used
    first" reaches `recentlyPlannedMealIds(userId, householdId, limit)` through
    `RecentMealsProvider` (see the meal library above) and sorts the library by that order, with
    meals not in the list last.
  - **Not modelled yet:** a slot with free text instead of a library meal (PLAN §4.2 sketches a
    `free_text` column; Phase 3 adds meals inline through the library, so it is not needed for it yet).
- **Copy and plan templates (#109), module `planning`:** `PlanningService.copyDay` and `copyWeek`
  copy a day or an ISO week onto another, and `PlanTemplateService` saves a day or a week as a named
  template (scope DAY or WEEK), lists, finds, renames, deletes and applies it. Copy and apply share
  one writer, `planning.internal.SlotWriter`, so the two modes mean the same thing everywhere
  (`CopyMode`, PLAN D26). **MERGE** appends each copied meal after the meals already in the same date
  and slot type, keeping the source order. **REPLACE** first removes every slot in the whole target
  range (one date for a day, Monday to Sunday for a week, including slot types the source has
  nothing in), then writes the copy from place 0, so the target becomes a copy of the source. Copying
  onto the same day or week is refused with `SAME_TARGET`, and an empty source day, week or menu with
  `NOTHING_TO_COPY`, both before anything is written, so a replace can never silently wipe a day.
  Notes are copied with the slot, archived meals are copied as they are, and one `SlotPlanned` is
  published per new slot (no new event types). A lost race in a replace is a `PlanConflictException`.
  - **Data (V9):** `plan_template` (name, scope, `version`) and `plan_template_item` (`day_offset` 0
    to 6, slot type, place, meal, note). Templates are frozen: they can be renamed (the version is
    checked, `TemplateConflictException`, 409) or deleted, never edited. Items go with their template
    by `ON DELETE CASCADE`.
  - **Who and tenancy:** every member may do everything (D26); nothing is owner-only. Every method
    checks membership first, another household's template is `TemplateNotFoundException` (404), and
    the composite foreign keys `(template_id, household_id)` and `(meal_id, household_id)` make the
    database refuse a cross-household link whatever the code does. The `meal` side has no
    `ON DELETE`, so a meal a template still uses cannot be deleted.
- **Occasions (#109), module `planning`:** `OccasionService` creates, finds, lists, edits and deletes
  dated occasions (name 1 to 100 characters, date, `catalog.OccasionType`, notes up to 2000), lists
  the ones between two days (`between`, for the week strip) and manages the menu: `addItem`,
  `quickAddItem` (looks the occasion up first, then creates the meal through `MealService.quickAdd`),
  `removeItem` and `moveItem` (the place is clamped, the rest is renumbered). Editing and deleting
  take the version (`OccasionConflictException`, 409). A menu item has no note and no version:
  adding only appends, and removing or moving an item that is gone is `MenuItemNotFoundException`
  (404), like another household's occasion (`OccasionNotFoundException`) or meal
  (`MealNotFoundException`). Data: `occasion` and `occasion_item` (V9), with composite foreign keys
  to the parent and to `meal`. Every member may do everything (D26). Nothing is published.
- **Occasion templates (#109), module `planning`:** `OccasionTemplateService` saves an occasion's
  type, notes and menu as a named template (`saveFromOccasion`; an empty menu is
  `NOTHING_TO_COPY`), lists and finds them, `use` creates a new occasion on a date with a copy of the
  menu, and `delete` removes one. There is no rename in v1, so delete takes no version (the
  `version` column is there for a later rename); occasions made from a template stay when it is
  deleted. Data: `occasion_template` and `occasion_template_item` (V9). Every member may do
  everything (D26), and the composite keys guard the tenant as above.
- **Ratings (#124), module `feedback`:** `RatingService` is the public entry point; the pages that
  use it are in #128 and the meal page's block in #132. A member rates a planned slot with a **thumb** (UP or DOWN, required), optional
  **stars** (1 to 5) and an optional **comment** of at most 500 characters (`RatingRules.check`
  trims it, and a blank comment is stored as `null`). Methods:
  - `give(userId, householdId, slotId, input)` rates a slot dated today or earlier (a later one is
    `InvalidRatingException` with `NOT_YET`; the other problems are `THUMB_REQUIRED`,
    `STARS_OUT_OF_RANGE`, `COMMENT_TOO_LONG`). Rating again **updates** the one row of that member
    and slot; it never adds a second. Two tabs giving at once end the same way: on Postgres a
    failed insert aborts its transaction, so the write lives in `feedback.internal.RatingWriter`
    (its own short transaction) and `give` retries it once, which then finds the winner's row.
    `give` therefore runs with `SUPPORTS` and not inside a transaction of its own.
  - `clear` deletes the caller's own rating (found by slot id, user and household, so it works
    after the slot is gone) and returns whether anything was deleted.
  - `forSlot` lists every current member's rating of a slot (`mine` flags the caller's own);
    `forMeal` gives a meal's thumbs up and down, the mean of the stars and the newest ratings;
    `nextToRate(userId, householdId, today, lookbackDays)` finds the latest slot of the last days
    the caller has not rated yet, calling `PlanningService.week` once per Monday it spans.
  - **Events:** `RatingGiven` (a new rating and an update alike, without the comment) and
    `RatingWithdrawn` (only when `clear` really deleted a row), both published inside the
    transaction.
  - **Privacy:** every member sees every rating, by display name only; `RatingView` has no e-mail
    field. Only the author can change or clear a rating, because every write is keyed by the
    caller's own id. Another household's slot or meal is 404, as everywhere (D8).
  - **Data (V10):** table `rating` with a composite foreign key `(meal_id, household_id)` to `meal`
    and **no foreign key to `meal_slot`**, so a rating outlives its slot (D26's Replace removes
    slots); the rating copies the slot's date, type and meal for that reason. Unique
    `(slot_id, user_id)`. A rated meal can no longer be deleted (the library only archives).
  - **Module direction (D27):** `feedback` depends on `planning`, `catalog` and `household`, never
    the reverse; the plan pages will load its fragments over htmx.
- **Meal history (#132), `feedback/internal/MealHistoryController`:** `GET /feedback/meals/{mealId}`
  answers the "How it went" block of a meal's page: the thumbs up and down counts, the mean of the
  stars (one decimal, omitted when nobody gave stars) and the newest 10 ratings by display name. The
  meal page (`meal/detail.jte`) does not render it itself: it holds an empty container that loads
  this fragment on `load` with htmx, so the meal page stays fast and `catalog` never imports
  `feedback`. The route first asks `RatingService.forMeal`, which looks the meal up inside the
  household, so another household's meal is a 404 before any rating is read. Ratings of a slot
  that was removed still count and show, because a rating keeps its own date and meal. The block
  holds no e-mail address; it is registered in `TenantEndpoints` as a member's GET.
- **Share links (#125, #129), module `sharing`:** a member makes a read-only link to one ISO week of
  the household's plan and sends it by any messenger; whoever opens it sees the week without
  signing in (PLAN.md D28). Table `share_link` (V11) stores only the SHA-256 hash of the token.
  - **Token:** 256 random bits from `SecureRandom`, 43 base64url characters
    (`shared.security.TokenCodes`). The raw token is returned once by `ShareLinkService.create` and
    is never stored, logged or put in an exception message.
  - **Rules:** a link covers one household and one week, expires after 1, 7 (default) or 30 days,
    is active only while `revoked_at IS NULL AND expires_at > now` (checked in the lookup query),
    and a household may have 10 active links. Any member may create and revoke; another
    household's link is 404. Old expired rows are purged when a link is created (no scheduler, D11).
  - **The data:** `ShareLinkService.open(token)` is the one method without a signed-in person. It
    reads through `planning.SharedWeekReader`, an *unchecked reader*: it skips the membership check
    on purpose, because nobody is signed in on that route. Its result type holds only dates, slot
    types, meal names and occasion names: no notes, ids, ratings, member or household names. Two
    guards keep it narrow: `UncheckedReadersTest` (ArchUnit) fails if any module but `sharing`
    uses it, and `catalog.MealNameLookup` (the unchecked meal-name reader it calls) is limited to
    `planning` (see `.claude/rules/java-modules.md`).
  - **The public route, `GET /share/{token}` (`sharing.internal.PublicShareController`):** the only
    unauthenticated business route. `SecurityConfig.PUBLIC_GET_PATHS` opens exactly one path
    segment of base64url characters, for GET only; `/share/x/y`, `/share/x/`, `/share/x.json`,
    `/share` and any POST still go to the sign-in page. The route writes nothing: no counter, no
    session, no cookie, and the page has no CSRF token (`csrfMetaSource` returns `null` under
    `/share/`, because reading the token would start a session).
  - **One answer for every bad token:** a malformed, unknown, expired or revoked token gets the
    same 404, from the same template, with the same headers; a malformed one never reaches the
    database. Every answer carries `X-Robots-Tag: noindex, nofollow, noarchive`,
    `Referrer-Policy: no-referrer` and `Cache-Control: no-store`, besides the usual CSP.
    `/robots.txt` (a static file) disallows everything and the service worker never caches `/share/`.
  - **Throttle:** one global in-memory fixed window (`shared.security.RateLimiter`, 120 requests a
    minute, `mm.share.rate-per-minute`); the next request gets 429 with `Retry-After`. It is global
    because the client address is not reliable behind Cloud Run's proxy; per-address limiting comes
    with Phase 6's invite and join limiter, which reuses the class.
  - **Page:** `layout/bare.jte` + `sharing/week.jte` show seven days, only the meal types that hold
    a meal (a dash for an empty day), the occasions, and a Print button. `SharedWeekPage` is the view model.
  - **Members' side (#131):** `ShareController` (`/plan/share`) creates, lists and revokes links and
    `PrintController` serves `GET /plan/print`; see "Print and share links" in `Current-State-Frontend.md`.
    `ShareLinkView` now carries `createdBy` (the creator's user id) so the list can show a name.
  - **Logs:** the application never logs the token (a test captures the log around valid, unknown,
    expired, revoked and malformed requests). Cloud Run's own request log does contain the path
    `/share/<token>`; this is accepted (D28), because only the owner reads the logs and links expire
    and can be revoked. A Cloud Logging exclusion for `/share/` is possible later, but it is a
    Google Cloud change and needs the owner's OK.
- **`shared.security`** holds the two helpers the Phase 5 code shares: `TokenCodes` (random
  bearer tokens: 256 bits, base64url, SHA-256 for storage; `CodeHasher` for invites delegates to it)
  and `RateLimiter` (a fixed one-minute window, used by the public share route). `TokenCodes` is in
  `shared` because `household` (invite codes) and `sharing` (share links) both need it; the limiter
  lives there so the Phase 6 invite limiter can reuse it.
- **`shared.ClockConfig`** provides the one `Clock` bean (Europe/Budapest). Code asks it for
  "today" instead of calling `LocalDate.now()`, so tests can fix the date.
- **Config:** `application.yml` turns on virtual threads and graceful shutdown, and tells JTE to
  use its precompiled templates.
- **Web plumbing (#13), in `shared`:** `shared.i18n` (`Messages` is the `t("key")` helper,
  `I18nConfig` the message bundles and the `mm_lang` cookie locale resolver, `SupportedLocales`)
  and `shared.web` (`SecurityHeadersFilter` and `CsrfMetaSource`, `ViewContext` + `ViewContextAdvice`, `Htmx`,
  `AppErrorController` and `ManifestController`). They are
  described from the UI side in [[Current State: Frontend]].
- **Database (#11):** Spring Data JPA (Hibernate set to `ddl-auto: validate`, so it only checks
  the schema) plus **Flyway**, which owns the schema. Entities arrive with the modules that need them
  (identity's came with #12, the meal library's with #66).
  - Migrations live in `src/main/resources/db/migration/`. `common/` holds portable SQL that
    runs on H2 and Postgres; `postgresql/` holds Postgres-only SQL. Version numbers are shared by
    both folders. Today: `V1__init.sql` (an empty baseline), `postgresql/V2__pgvector.sql`
    (`CREATE EXTENSION vector`, used by the assistant's `meal_embedding` table from V14) and `V3__identity.sql`
    (`app_user`, `allowed_email` and the Spring Session tables), `V4__household.sql`
    (`household` with a `version` column, and `membership` with the role OWNER or MEMBER),
    `V5__invite.sql` (`invite`, which stores only the hash of its code), `V6__catalog.sql`
    (`meal`, `tag` and `meal_tag`, see the meal library below), `V7__planning.sql` (`meal_slot`,
    see the weekly plan below), `V8__meal_occasion_type.sql` (`meal_occasion_type`, see the meal
    library below) and `V9__reuse_and_occasions.sql` (plan templates, occasions and occasion
    templates with their items, see the copy, occasion and template bullets above),
    `V10__rating.sql` (`rating`, see the ratings bullet above), `V11__share_link.sql`
    (`share_link`, see the share links bullet above) and `V12__ingredient.sql` (`ingredient`,
    `meal_ingredient` and the starter list `ingredient_seed`, see the meal library above).
  - **Profiles:** `dev` points at in-memory H2 in PostgreSQL mode; `postgres` points at the
    compose Postgres (override with `SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD`) and adds the
    `postgresql/` folder. Use them together as `dev,postgres`. With no profile there is no
    datasource configured; the `prod` profile (Cloud Run) takes it from environment variables.
  - **Connection pool:** at most 5 connections and `minimum-idle: 0`, with idle ones closed
    after a minute, so nothing keeps the Neon database awake (D11).

- **Identity (#12):** nothing is reachable without signing in. Why this design: the app holds a
  household's private data, so the default must be "closed".
  - **Sign-in:** Google OIDC through Spring Security's `oauth2Login`. People are identified by
    Google's stable `sub`, not their e-mail (e-mails change), and the e-mail must be *verified*.
  - **Allowlist:** after Google confirms who someone is, `AllowlistedOidcUserService` checks that
    their e-mail is in `MM_ADMIN_EMAILS` (instance admins, from the environment) or in the
    `allowed_email` table. Otherwise they get a **403** and **no `app_user` row** is created. E-mails
    are compared lower-case. Changing the table needs no redeploy: instance admins use the admin
    page below.
  - **Allowlist admin page (#45):** `/admin/allowlist` (`identity/internal/AllowlistAdminController`
    and `AllowlistAdminService`). `SecurityConfig` lets only people with `ROLE_ADMIN` reach
    `/admin/**`, so a signed-in non-admin gets 403 and an anonymous visitor goes to `/login`; the
    service itself does not check who asks and is package-private. The list shows the bootstrap
    admins from `MM_ADMIN_EMAILS` first (fixed: a remove is refused with 409, because only a
    redeploy can change configuration), then the `allowed_email` rows with their source (`ADMIN` =
    added here, `INVITE` = added by a household invite). Adding normalizes the address and rejects
    one that is badly shaped or already allowed (422); removing deletes the row, so the person's
    **next sign-in** is refused, and their live sessions end at once (#59): in the same
    transaction the service finds the sessions whose principal is their Google `sub` (through the
    `app_user` row with that address) and deletes them, so their next request goes to sign-in.
    Nobody else's sessions are touched. Removing an address does not touch households, memberships
    or the person's data. The admin flag is read at sign-in, so a change of
    `MM_ADMIN_EMAILS` reaches a signed-in admin only at their next sign-in.
  - **Users:** `app_user` (Google `sub`, e-mail, name, UI locale `hu`/`en`, created at). The e-mail
    and name are refreshed at every sign-in. The principal that controllers receive is
    `identity.SignedInUser` (public API of the module): `@AuthenticationPrincipal SignedInUser user`
    gives `userId()`, `locale()` and `isAdmin()`.
  - **Sessions:** Spring Session JDBC (`spring_session*` tables), so a restart, a redeploy or the
    app waking from sleep does not sign anyone out. They last 30 days. The cookie is
    `HttpOnly`, `SameSite=Lax` and, in prod, `Secure`. Expired sessions are deleted at sign-in
    (`SessionCleanupOnSignIn`) instead of by Spring's timer, because a timer would poll the
    database and keep the app and the database from scaling to zero.
  - **Rules** (`identity/internal/SecurityConfig`): everything needs a session except the explicit
    `PUBLIC_PATHS` (login page, error page, the language switch `POST /language`, health check, static files, PWA files, `/robots.txt`) and the GET-only share page `PUBLIC_GET_PATHS` (above). CSRF stays on.
    An anonymous browser request is redirected to `/login`; an anonymous **htmx** request gets
    `401` + `HX-Redirect: /login`, so a click after the session expired navigates the whole page
    instead of swapping a login form into a fragment. Responses carry `X-Frame-Options: DENY`,
    `Referrer-Policy: same-origin` and `nosniff`.
  - **Fail closed in production:** `ProductionConfigGuard` stops the `prod` profile from starting,
    before any other bean exists, unless the Google client id and secret, `MM_ADMIN_EMAILS`, a
    `jdbc:postgresql:` datasource and `forward-headers-strategy=framework` are all set. It lists
    every missing setting in one message. Without it a forgotten Cloud Run variable would fail in a
    confusing place, or worse, start half-open.
  - Setup for a developer: [[Auth Setup]].

The planned modules, data model and request flow are in [[Roadmap: Backend]] and `PLAN.md` §4.
