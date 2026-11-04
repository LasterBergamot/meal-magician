# AI Assistant

Meal Magician has an optional assistant, called **Varázsló / Wizard** in the app and marked by the wand
icon. It helps a household plan its meals. It is **off by default**: until the owner switches it on, the
app has no Google connection, needs no Google credentials and shows no Wizard button or tab. This page
explains what it does, what data it sees, what it costs, how to switch it off, how to widen what the
chat answers, and how the owner checks a model before choosing it. The decisions behind it are PLAN.md
D32 (the rules) and D33 (which model).

## What it does

Everything the assistant produces is a **proposal**. Nothing reaches the plan or the meal library until
a person presses a button.

| Where | What you get |
|---|---|
| Plan page, "Suggest my week" | A draft for the week, as a list of meals per day and slot. You can remove items, ask for changes ("less meat", "Thursday is busy"), then **Accept** (fill the empty slots, or replace the week after a confirm) or **Discard**. |
| Plan page, meal picker, "Ask the wizard for ideas" | Up to three library meals for that one slot. Planning one is the normal "add to slot". |
| Wizard tab (`/assistant`) | A chat about food, ingredients, the season in Hungary, menus and your own history. It politely declines anything else. The conversation lives in your browser session only. |
| An occasion's page, "Menüjavaslat" | Up to six library meals and three new ideas for the occasion's menu, based on earlier occasions of the same kind. |

Two words you will meet:

- A **draft** is the saved proposal for a week. It is not in the plan yet. Drafts are deleted after 7
  days.
- An **idea** is a dish that is not in your meal library yet. It appears as a dashed card (at most 5 per
  draft). "Add to meals" puts it in the library; an accepted draft never plans an idea by itself.

The assistant answers in the member's language (Hungarian or English). It prefers meals from your own
library, looks at what you ate in recent weeks, and uses the date and "Hungary, Central Europe" to judge
the season.

## What data it sees

The assistant can only **read**, through six fixed tools, and every tool is bound to the asking person's
household when it is created. No tool takes a household or user id, so the model cannot ask for another
household's data. A test checks this.

What leaves the app for Google Vertex AI (Gemini) in a request:

- meal names, notes, tags, ingredients and occasion types (the notes and names are also sent to be
  embedded for the meal search);
- planned meals with dates and slot types, and occasion names and dates;
- ratings (thumbs, stars) and **rating comments**;
- what the member typed (the request, a follow-up, a chat message).

What never leaves: display names and e-mail addresses (members appear as "member 1", "member 2"), the
recipe links, and anything about another household.

Things to know:

- A member can write a name into a note or a comment, and it would be sent. The request form and the
  chat page say that the text goes to Google Gemini.
- Google says it does not train on customer data without permission, but it may log prompts to watch for
  abuse. The processing region is the EU (`eu` for chat, `europe-west3` for the embeddings).
- **No dietary rules or allergies are stored.** The assistant is told never to claim that a meal is safe
  for an allergy or a diet. Check ingredients yourself.
- Everything a member typed is treated as data, not as an instruction. A meal named "ignore all rules"
  is just a name. The eval has a case for this.

## What it costs, and the caps

Gemini is billed by tokens (pieces of words). The chosen model is `gemini-3.5-flash` for chat at about
$1.65 per million input tokens and $9.90 per million output tokens (thinking tokens count as output), and
`gemini-embedding-001` for the meal search. A draft costs a few cents at most; real use is expected to be
cents per month.

- **Spend cap:** every household may spend **$10 a month** (`mm.ai.monthly-cap-usd`). The check is before
  each call, so a household at the cap gets a message and no call is made. Usage is added after each call
  in the `assistant_usage` table (tokens and micro-USD per household and month). A new month starts at
  zero by itself.
- **Vertex AI has no hard billing cap.** The app's check is the control, so keep the Google Cloud budget
  alert. Google's own bill is in the Cloud Console, Billing, filtered to "Vertex AI".
- **Timeouts:** every call stops after `mm.ai.timeout` (60 seconds), and a member can cancel a request.
- **Prices** come from the settings `AI_PRICE_IN` and `AI_PRICE_OUT`, so the table above must match the
  model (they are not guessed).

## How to turn it off

- **In production:** set the GitHub variable `AI_ENABLED` to anything but `true` (or delete it) and
  release again. The release workflow then passes no assistant settings to Cloud Run, the Wizard
  disappears from the navigation, and every `/assistant/**` address answers 404. Draft tables stay but
  are never read.
- **Locally:** leave `MM_AI_ENABLED` unset. `task serve` runs without it.
- **To look at it without Google:** use the `fake-ai` profile, which replaces Gemini with a scripted
  model (for tests and screenshots only; production refuses to start with it).

Setting it up in the first place (Vertex AI API, the role, the GitHub variables) is
[[Hosting Setup]], Part 9.

## How to widen the chat scope

The chat answers about food and the meal plan only. The rule is a text file the model reads,
`src/main/resources/assistant/chat-policy-food.txt`, picked by the setting `mm.ai.chat-scope` (default
`food-and-plan`).

To allow more topics:

1. Add a policy file next to it, for example `chat-policy-general.txt`.
2. Add a constant for it to the `ChatScope` enum (`assistant/internal/ChatScope.java`) with a name.
3. Set `mm.ai.chat-scope` to that name. An unknown name stops the app from starting, on purpose.
   `release.yml` does not pass this setting today, so a deploy change is part of the work.

The policy is a prompt, not a security control. The tools behind the chat stay read-only and bound to the
household whatever the text says.

## How to run the eval

The eval compares chat models on a fixed set of cases before the owner chooses one. It **calls the real
Gemini models and costs money**, and the answers differ from run to run, so it never runs in CI or in
`task test`. It is a plain program in the test source set (`assistant/eval`), started by hand:

```bash
gcloud auth application-default login          # your own login; no key is ever stored
export AI_PROJECT_ID=<project id> AI_LOCATION=eu AI_EMBEDDING_MODEL=gemini-embedding-001 \
       AI_EMBEDDING_LOCATION=europe-west3
task ai:eval -- --models gemini-3.5-flash,<second model> \
  --price-in gemini-3.5-flash=1.65,<second model>=<usd> --price-out gemini-3.5-flash=9.90,<second model>=<usd>
```

- `task ai:eval -- --help` lists the options and needs no login and no Docker. Without `--models` it
  prints the usage and stops with exit code 2.
- It needs **Docker** (a throw-away Postgres with pgvector runs in Testcontainers). Without Docker, or
  without a Google login or one of the variables, it says so and stops. It never touches the real
  database.
- `--runs` (default 3) repeats each case, because a model's answers vary. `--cases a,b` runs some cases.
  `--budget-usd` (default 2.00) stops a model once its runs add up to that much; the households of the
  eval are throw-away ones with the ledger's own cap set very high, so this limit is what protects you.
- The data is an **invented Hungarian household** (about 40 meals, three made-up members with ratings,
  `src/test/resources/eval/household.json`). Never put real household data there. The runs go through the
  real services (`AssistantService`, the prompts, the tools, the meal search, the validator), with only
  the clock moved to each case's date. Tokens and cost come from the same usage ledger the app uses.
- 16 cases (`cases.json`): a plain week, "less meat", a busy Thursday, a birthday on Saturday, a
  Christmas week, leftover rice, no mushrooms, an English request, a meal whose name gives orders, a
  request for three weeks, a January and a July week, one-slot and occasion-menu suggestions, and two
  chat messages (one off-topic).
- The checks are **deterministic**: no model judges another model. They test only real, non-archived
  meal ids; no meal twice on neighbouring days (unless the case allows it); "less meat" lowering the meat
  share against the plain week; an occasion's date having a matching meal or an idea; the summary being
  in the requested language (a stop-word count); at most 5 ideas and none in the plan; a few seasonal
  cases against a short hand-written out-of-season list; and the off-topic reply being a short refusal.
  Each check has a unit test on a good and a bad sample (`ChecksTest`), so the checker itself is proven
  without a model.
- The report is printed and written to `build/eval/<timestamp>.md`: per model the pass rate, the share of
  runs that named an invented meal id, tokens, estimated cost (total and per case) and latency, then a
  pass count per case and the failed checks with their messages. It holds invented meal names and counts
  only, never a project id or a credential.

**The eval report is the owner's to review.** The owner reads it, picks the chat model, and PLAN.md D33
records the choice (model id, region, prices). Until then D33 is provisional: `gemini-3.5-flash` in the
`eu` multi-region, with the embeddings in `europe-west3`. Newer models appear all the time, so check
that a model is offered in the region before you add it to the comparison ([[References]] has the
locations page).

## Where it lives in the code

`assistant` is a Spring Modulith module. Its public face is `AssistantService`; `ModelGateway` is the
only class that talks to Gemini, and it applies the cap, the timeout and the metering. The details are in
[[Current State: Backend]] and [[Current State: Frontend]]; a walk through one request is in the
[[Newcomer Guide]].
