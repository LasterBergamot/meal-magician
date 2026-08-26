-- Reusable day and week templates. A template is a named, saved arrangement of
-- meals that can be applied onto the live calendar (planned_meals).

-- ----- Day templates -----
create table public.day_templates (
  id uuid primary key default gen_random_uuid(),
  household_id uuid not null references public.households (id) on delete cascade,
  name text not null check (char_length(btrim(name)) between 1 and 100),
  created_by uuid default auth.uid() references auth.users (id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index day_templates_household_id_idx on public.day_templates (household_id);

create trigger day_templates_set_updated_at
  before update on public.day_templates
  for each row execute function public.set_updated_at();

create table public.day_template_items (
  id uuid primary key default gen_random_uuid(),
  day_template_id uuid not null references public.day_templates (id) on delete cascade,
  slot public.meal_slot not null default 'lunch',
  meal_id uuid not null references public.meals (id) on delete cascade,
  position smallint not null default 0 check (position >= 0),
  unique (day_template_id, slot, position)
);

create index day_template_items_template_idx on public.day_template_items (day_template_id);
create index day_template_items_meal_idx on public.day_template_items (meal_id);

-- ----- Week templates -----
create table public.week_templates (
  id uuid primary key default gen_random_uuid(),
  household_id uuid not null references public.households (id) on delete cascade,
  name text not null check (char_length(btrim(name)) between 1 and 100),
  created_by uuid default auth.uid() references auth.users (id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index week_templates_household_id_idx on public.week_templates (household_id);

create trigger week_templates_set_updated_at
  before update on public.week_templates
  for each row execute function public.set_updated_at();

create table public.week_template_items (
  id uuid primary key default gen_random_uuid(),
  week_template_id uuid not null references public.week_templates (id) on delete cascade,
  day_of_week smallint not null check (day_of_week between 1 and 7), -- 1 = Monday
  slot public.meal_slot not null default 'lunch',
  meal_id uuid not null references public.meals (id) on delete cascade,
  position smallint not null default 0 check (position >= 0),
  unique (week_template_id, day_of_week, slot, position)
);

create index week_template_items_template_idx on public.week_template_items (week_template_id);
create index week_template_items_meal_idx on public.week_template_items (meal_id);
