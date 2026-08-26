-- Row-Level Security for every household-scoped table.
--
-- Rule of thumb: a row is visible/writable when auth.uid() is a member of the
-- row's household; admin-only actions additionally require is_household_admin().
-- Rows are never created by direct client INSERT where a bootstrap problem
-- exists (e.g. households, initial membership) — those go through the
-- SECURITY DEFINER RPCs in the next migration.

-- ============================ profiles ============================
alter table public.profiles enable row level security;

create policy "profiles: select self or co-members"
  on public.profiles for select to authenticated
  using (id = auth.uid() or public.shares_household(id));

create policy "profiles: insert own"
  on public.profiles for insert to authenticated
  with check (id = auth.uid());

create policy "profiles: update own"
  on public.profiles for update to authenticated
  using (id = auth.uid())
  with check (id = auth.uid());

-- ============================ households ============================
alter table public.households enable row level security;

-- No INSERT policy on purpose: households are created via create_household(),
-- which also inserts the creator's admin membership atomically.
create policy "households: members select"
  on public.households for select to authenticated
  using (public.is_household_member(id));

create policy "households: admins update"
  on public.households for update to authenticated
  using (public.is_household_admin(id))
  with check (public.is_household_admin(id));

create policy "households: admins delete"
  on public.households for delete to authenticated
  using (public.is_household_admin(id));

-- ======================= household_members =======================
alter table public.household_members enable row level security;

create policy "members: select co-members"
  on public.household_members for select to authenticated
  using (public.is_household_member(household_id));

create policy "members: admins add"
  on public.household_members for insert to authenticated
  with check (public.is_household_admin(household_id));

create policy "members: admins update roles"
  on public.household_members for update to authenticated
  using (public.is_household_admin(household_id))
  with check (public.is_household_admin(household_id));

-- Admins can remove anyone; a member can remove (leave) themselves.
create policy "members: admins remove or self-leave"
  on public.household_members for delete to authenticated
  using (public.is_household_admin(household_id) or user_id = auth.uid());

-- ======================= household_invites =======================
alter table public.household_invites enable row level security;

create policy "invites: members select"
  on public.household_invites for select to authenticated
  using (public.is_household_member(household_id));

create policy "invites: admins insert"
  on public.household_invites for insert to authenticated
  with check (public.is_household_admin(household_id));

create policy "invites: admins update"
  on public.household_invites for update to authenticated
  using (public.is_household_admin(household_id))
  with check (public.is_household_admin(household_id));

create policy "invites: admins delete"
  on public.household_invites for delete to authenticated
  using (public.is_household_admin(household_id));

-- ============================== meals ==============================
alter table public.meals enable row level security;

create policy "meals: members select"
  on public.meals for select to authenticated
  using (public.is_household_member(household_id));

create policy "meals: members insert"
  on public.meals for insert to authenticated
  with check (public.is_household_member(household_id) and created_by = auth.uid());

create policy "meals: members update"
  on public.meals for update to authenticated
  using (public.is_household_member(household_id))
  with check (public.is_household_member(household_id));

create policy "meals: members delete"
  on public.meals for delete to authenticated
  using (public.is_household_member(household_id));

-- ========================== day_templates ==========================
alter table public.day_templates enable row level security;

create policy "day_templates: members select"
  on public.day_templates for select to authenticated
  using (public.is_household_member(household_id));

create policy "day_templates: members insert"
  on public.day_templates for insert to authenticated
  with check (public.is_household_member(household_id) and created_by = auth.uid());

create policy "day_templates: members update"
  on public.day_templates for update to authenticated
  using (public.is_household_member(household_id))
  with check (public.is_household_member(household_id));

create policy "day_templates: members delete"
  on public.day_templates for delete to authenticated
  using (public.is_household_member(household_id));

alter table public.day_template_items enable row level security;

-- Items inherit their household from the parent template.
create policy "day_template_items: members all"
  on public.day_template_items for all to authenticated
  using (exists (
    select 1 from public.day_templates t
    where t.id = day_template_id and public.is_household_member(t.household_id)
  ))
  with check (exists (
    select 1 from public.day_templates t
    where t.id = day_template_id and public.is_household_member(t.household_id)
  ));

-- ========================== week_templates ==========================
alter table public.week_templates enable row level security;

create policy "week_templates: members select"
  on public.week_templates for select to authenticated
  using (public.is_household_member(household_id));

create policy "week_templates: members insert"
  on public.week_templates for insert to authenticated
  with check (public.is_household_member(household_id) and created_by = auth.uid());

create policy "week_templates: members update"
  on public.week_templates for update to authenticated
  using (public.is_household_member(household_id))
  with check (public.is_household_member(household_id));

create policy "week_templates: members delete"
  on public.week_templates for delete to authenticated
  using (public.is_household_member(household_id));

alter table public.week_template_items enable row level security;

create policy "week_template_items: members all"
  on public.week_template_items for all to authenticated
  using (exists (
    select 1 from public.week_templates t
    where t.id = week_template_id and public.is_household_member(t.household_id)
  ))
  with check (exists (
    select 1 from public.week_templates t
    where t.id = week_template_id and public.is_household_member(t.household_id)
  ));

-- ========================== planned_meals ==========================
alter table public.planned_meals enable row level security;

create policy "planned_meals: members select"
  on public.planned_meals for select to authenticated
  using (public.is_household_member(household_id));

create policy "planned_meals: members insert"
  on public.planned_meals for insert to authenticated
  with check (public.is_household_member(household_id) and created_by = auth.uid());

create policy "planned_meals: members update"
  on public.planned_meals for update to authenticated
  using (public.is_household_member(household_id))
  with check (public.is_household_member(household_id));

create policy "planned_meals: members delete"
  on public.planned_meals for delete to authenticated
  using (public.is_household_member(household_id));
