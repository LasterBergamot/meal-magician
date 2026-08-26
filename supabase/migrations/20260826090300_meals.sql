-- Meals: the household's reusable library. Every meal a household enters lives
-- here and is available to add to any day/week. Recipes/ingredients are out of
-- scope for now; when added, a nullable meals.recipe_id FK slots in additively.

create table public.meals (
  id uuid primary key default gen_random_uuid(),
  household_id uuid not null references public.households (id) on delete cascade,
  name text not null check (char_length(btrim(name)) between 1 and 200),
  description text,
  notes text,
  created_by uuid default auth.uid() references auth.users (id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- One meal name per household (case-insensitive) keeps the library tidy; the
-- data-access layer can upsert on this conflict instead of creating duplicates.
create unique index meals_household_name_key on public.meals (household_id, lower(name));
create index meals_household_id_idx on public.meals (household_id);

create trigger meals_set_updated_at
  before update on public.meals
  for each row execute function public.set_updated_at();
