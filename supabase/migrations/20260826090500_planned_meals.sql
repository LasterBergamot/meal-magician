-- planned_meals is the live calendar. A "week" is simply a date range over this
-- table (Mon–Sun), not a stored row — which keeps planning flexible and makes
-- future multi-slot / multi-week views trivial.

create table public.planned_meals (
  id uuid primary key default gen_random_uuid(),
  household_id uuid not null references public.households (id) on delete cascade,
  date date not null,
  slot public.meal_slot not null default 'lunch',
  meal_id uuid not null references public.meals (id) on delete cascade,
  position smallint not null default 0 check (position >= 0),
  created_by uuid default auth.uid() references auth.users (id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  -- One meal per (day, slot, position); position lets a slot hold several meals.
  unique (household_id, date, slot, position)
);

create index planned_meals_household_date_idx on public.planned_meals (household_id, date);
create index planned_meals_meal_idx on public.planned_meals (meal_id);

create trigger planned_meals_set_updated_at
  before update on public.planned_meals
  for each row execute function public.set_updated_at();
