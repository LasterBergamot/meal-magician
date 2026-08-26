import { DEFAULT_MEAL_SLOT, MEAL_SLOTS } from "@meal-magician/core";

export default function HomePage() {
  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col items-center justify-center gap-6 p-8 text-center">
      <h1 className="text-4xl font-bold tracking-tight">🍽️ Meal Magician</h1>
      <p className="text-lg text-muted-foreground">
        Plan your household&apos;s lunches for the week — save meals, days, and weeks to reuse later.
      </p>
      <div className="rounded-lg border bg-card p-4 text-sm text-card-foreground">
        <p className="font-medium">Phase 0 scaffold is live.</p>
        <p className="text-muted-foreground">
          Default slot: <span className="font-mono">{DEFAULT_MEAL_SLOT}</span> · Available slots:{" "}
          <span className="font-mono">{MEAL_SLOTS.join(", ")}</span>
        </p>
      </div>
    </main>
  );
}
